<template>
  <q-page padding>
    <AppPageHeader
      title="수강 관리"
    >
      <template #actions>
        <q-btn outline no-caps color="primary" icon="refresh" label="새로고침" :loading="loading" @click="loadRows" />
        <q-btn unelevated no-caps color="primary" icon="person_add" label="대리 수강신청" @click="openProxy" />
      </template>
    </AppPageHeader>
    <AppInstitutionMissingBanner :show="showInstitutionMissingBanner" />
    <AppErrorBanner :message="errorMessage">
      <template #action>
        <q-btn flat dense no-caps color="negative" label="다시 시도" @click="loadRows" />
      </template>
    </AppErrorBanner>

    <div class="row q-col-gutter-md q-mb-md">
      <div class="col-12 col-md-4">
        <q-select
          v-model="selectedCourseId"
          outlined
          dense
          emit-value
          map-options
          :options="courseFilterOptions"
          label="강의"
        />
      </div>
      <!-- 처리 끝난 신청까지 섞여 할 일이 묻혔다 — 기본은 처리 대기(신청·대기).
           대시보드 '수강 신청 대기'가 ?status=APPLIED 로 연다. 바꾸면 주소에 남긴다 -->
      <div class="col-12 col-md-3">
        <q-select
          :model-value="statusFilter"
          outlined
          dense
          emit-value
          map-options
          :options="STATUS_FILTERS"
          label="상태"
          @update:model-value="setStatusFilter"
        />
      </div>
      <!-- 신청자·강의명 검색(QA ENR-17). 서버 목록 API 에 검색이 없어 받아 둔 목록에서 거른다. 주소(?q=)에 남는다 -->
      <div class="col-12 col-md-5">
        <q-input
          :model-value="searchText"
          outlined
          dense
          clearable
          debounce="200"
          label="신청자·강의명 검색"
          @update:model-value="setSearchText"
        >
          <template #prepend><q-icon name="search" /></template>
        </q-input>
      </div>
    </div>

    <!-- 표·폰 카드·일괄 처리는 강사 '수강 신청 관리'와 같은 컴포넌트. 기관은 신청 취소도 한다 -->
    <EnrollmentDecisionTable
      v-model:selected="checked"
      :rows="filteredRows"
      :loading="loading"
      :empty-text="emptyText"
      :busy-id="busyId"
      :bulk-saving="bulkSaving"
      cancellable
      withdrawable
      @approve="approve"
      @reject="openReject"
      @cancel="confirmCancel"
      @withdraw="withdraw"
      @bulk-approve="bulkApprove"
      @bulk-reject="openBulkReject"
    >
      <template #note>
        <p v-if="capped" class="capped-note">최근 신청 {{ PAGE_SIZE }}건만 보여 줍니다. 강의를 골라 강의 상세의 '수강 신청 현황'에서 전체를 볼 수 있습니다.</p>
      </template>
    </EnrollmentDecisionTable>

    <ProxyEnrollmentDialog
      v-model="showProxy"
      :institution-id="institutionId || ''"
      :course-options="courseOptions"
      :initial-course-id="selectedCourseId"
      @enrolled="loadRows"
    />
    <!-- 거절: 사유가 수강신청에 기록된다 — 한 건·일괄 모두 같은 창, 실패하면 닫히지 않는다 -->
    <CorrectionReasonDialog
      v-model="rejectOpen"
      :title="rejectMode === 'bulk' ? '수강신청 일괄 거절' : '수강신청 거절'"
      :target="rejectTargetLabel"
      :notes="REJECT_NOTES"
      confirm-label="거절"
      reason-label="거절 사유"
      reason-hint="수강신청 기록에 남습니다."
      :action="runReject"
      @done="onRejected"
    />
  </q-page>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import AppInstitutionMissingBanner from '@/components/AppInstitutionMissingBanner.vue';
import AppPageHeader from '@/components/AppPageHeader.vue';
import CorrectionReasonDialog from '@/components/console/CorrectionReasonDialog.vue';
import EnrollmentDecisionTable from '@/components/console/EnrollmentDecisionTable.vue';
import ProxyEnrollmentDialog from '@/components/console/ProxyEnrollmentDialog.vue';
import enrollmentDecisionPage from '@/mixins/enrollment-decision-page';
import institutionScope from '@/mixins/institution-scope';
import { cancelEnrollment } from '@/services/enrollment-api';
import { listInstitutionCourses, listInstitutionEnrollments } from '@/services/institution-api';
import { confirmDialog } from '@/utils/confirm-dialog';
import { formatDateTimeFields } from '@/utils/datetime';
import { DECIDABLE_STATUSES, enrollmentBadgeLabel } from '@/utils/enrollment-decision';
import { formatCourseOption } from '@/utils/status';
import { formatUserDisplayName } from '@/utils/user-display';

const PAGE_SIZE = 100;
const COURSE_PAGE_SIZE = 100;
const STATUS_FILTERS = [
  { label: '처리 대기', value: 'PENDING' },
  { label: '수강 확정', value: 'APPROVED' },
  { label: '거절', value: 'REJECTED' },
  { label: '취소·철회', value: 'CLOSED' },
  { label: '전체', value: 'ALL' },
];
const FILTER_STATUSES = {
  PENDING: DECIDABLE_STATUSES,
  APPROVED: ['APPROVED'],
  REJECTED: ['REJECTED'],
  CLOSED: ['CANCELLED', 'WITHDRAWN'],
};

/** 주소 ?status — 대시보드는 APPLIED 로 연다(처리 대기로 본다) */
function filterFromQuery(value) {
  if (value === 'APPLIED' || value === 'WAITLISTED') return 'PENDING';
  return STATUS_FILTERS.some((option) => option.value === value) ? value : 'PENDING';
}

export default {
  name: 'InstitutionEnrollmentsPage',
  components: {
    AppErrorBanner,
    AppInstitutionMissingBanner,
    AppPageHeader,
    CorrectionReasonDialog,
    EnrollmentDecisionTable,
    ProxyEnrollmentDialog,
  },
  mixins: [institutionScope, enrollmentDecisionPage],
  data() {
    return {
      PAGE_SIZE,
      STATUS_FILTERS,
      statusFilter: filterFromQuery(this.$route.query.status),
      searchText: typeof this.$route.query.q === 'string' ? this.$route.query.q : '',
      selectedCourseId: null,
      loading: false,
      errorMessage: '',
      rows: [],
      capped: false,
      courses: [],
      showProxy: false,
    };
  },
  computed: {
    courseOptions() {
      return this.courses.map((item) => ({ label: formatCourseOption(item), value: item.id }));
    },
    courseFilterOptions() {
      return [{ label: '전체', value: null }, ...this.courseOptions];
    },
    filteredRows() {
      const statuses = FILTER_STATUSES[this.statusFilter];
      const needle = this.searchText.trim().toLowerCase();
      return this.rows.filter(
        (row) =>
          (!this.selectedCourseId || row.courseId === this.selectedCourseId) &&
          (!statuses || statuses.includes(row.status)) &&
          (!needle || `${row.applicantLabel} ${row.courseTitle}`.toLowerCase().includes(needle)),
      );
    },
    emptyText() {
      return this.statusFilter === 'PENDING' ? '처리할 수강 신청이 없습니다.' : '조건에 맞는 수강 신청이 없습니다.';
    },
  },
  watch: {
    // 대시보드에서 다시 들어오거나 뒤로가기로 주소만 바뀌어도 필터를 맞춘다
    '$route.query.status'(value) {
      this.statusFilter = filterFromQuery(value);
    },
    '$route.query.q'(value) {
      this.searchText = typeof value === 'string' ? value : '';
    },
    // 머리의 기관 선택기로 기관을 바꾸면 그 기관 것으로 다시 읽는다
    institutionId(next, prev) {
      if (next === prev) return;
      this.selectedCourseId = null;
      this.loadRows();
    },
  },
  mounted() {
    this.loadRows();
  },
  methods: {
    setSearchText(value) {
      this.searchText = value || '';
      this.$router.replace({ query: { ...this.$route.query, q: this.searchText.trim() || undefined } });
    },
    setStatusFilter(value) {
      this.statusFilter = value;
      this.$router.replace({ query: { ...this.$route.query, status: value === 'PENDING' ? undefined : value } });
    },
    toRow(row) {
      return {
        ...row,
        // 기관 관리자는 소속 강의 신청을 모두 처리한다
        canDecide: true,
        applicantLabel: formatUserDisplayName(row),
        statusLabel: enrollmentBadgeLabel(row),
        appliedAtText: formatDateTimeFields(row, 'appliedAt', 'createdAt'),
        processedAtText: formatDateTimeFields(row, 'processedAt', 'approvedAt', 'rejectedAt', 'cancelledAt', 'withdrawnAt'),
      };
    },
    async loadRows() {
      // 기관이 없으면 비운다 — 예전엔 전체 강좌의 신청을 모아 기관 콘솔에 남의 기관 신청이 섞였다
      if (!this.institutionId) {
        this.rows = [];
        this.courses = [];
        return;
      }
      this.loading = true;
      this.errorMessage = '';
      try {
        const [page, coursesPage] = await Promise.all([
          listInstitutionEnrollments(this.institutionId, { page: 0, size: PAGE_SIZE }),
          listInstitutionCourses(this.institutionId, { page: 0, size: COURSE_PAGE_SIZE }),
        ]);
        this.rows = (page.data || []).map(this.toRow);
        this.capped = (page.page?.totalElements || 0) > PAGE_SIZE;
        this.courses = coursesPage.data || [];
      } catch (error) {
        this.rows = [];
        this.errorMessage = error.message || '수강 신청을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    async confirmCancel(row) {
      const ok = await confirmDialog(this.$q, {
        title: '수강신청 취소',
        message: `${row.applicantLabel || '이 신청자'}의 수강신청을 취소합니다. 되돌릴 수 없습니다.`,
        ok: '신청 취소',
        color: 'negative',
        cancel: '닫기',
      });
      if (!ok) return;
      try {
        await cancelEnrollment(row.id);
        this.$q.notify({ type: 'positive', message: `${row.applicantLabel} 님의 수강신청을 취소했습니다.`, position: 'top' });
        await this.loadRows();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      }
    },
    openProxy() {
      this.showProxy = true;
    },
  },
};
</script>

<style scoped>
.capped-note {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--db-text-sub);
  word-break: keep-all;
}
</style>
