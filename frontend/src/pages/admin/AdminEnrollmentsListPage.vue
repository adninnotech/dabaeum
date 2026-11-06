<template>
  <q-page padding>
    <!-- 예전엔 '모집 강의 목록'(GET /courses)이라 수강 건을 볼 수 없었다(QA ENR-18).
         이제 전 기관의 수강신청(GET /admin/enrollments)을 한 표로 보고 기관·상태·검색으로 좁힌다 -->
    <AppPageHeader title="수강 신청 관리">
      <template #actions>
        <q-btn outline no-caps color="primary" icon="refresh" label="새로고침" :loading="loading" @click="loadRows" />
      </template>
    </AppPageHeader>
    <AppErrorBanner :message="errorMessage">
      <template #action>
        <q-btn flat dense no-caps color="negative" label="다시 시도" @click="loadRows" />
      </template>
    </AppErrorBanner>

    <div class="row q-col-gutter-md q-mb-md">
      <div class="col-12 col-md-4">
        <q-select
          :model-value="institutionId"
          outlined
          dense
          emit-value
          map-options
          use-input
          input-debounce="0"
          :options="filteredInstitutionOptions"
          :loading="institutionsLoading"
          label="기관"
          @filter="filterInstitutions"
          @update:model-value="setInstitution"
        />
      </div>
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
      <div class="col-12 col-md-5">
        <q-input
          :model-value="searchText"
          outlined
          dense
          clearable
          debounce="300"
          label="강의명·수강자명 검색"
          @update:model-value="setSearchText"
        >
          <template #prepend><q-icon name="search" /></template>
        </q-input>
      </div>
    </div>

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
        <p v-if="capped" class="capped-note">
          조건에 맞는 신청 중 최근 {{ PAGE_SIZE }}건만 보여 줍니다. 기관·상태·검색으로 좁혀 주세요.
        </p>
      </template>
    </EnrollmentDecisionTable>

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
import AppPageHeader from '@/components/AppPageHeader.vue';
import CorrectionReasonDialog from '@/components/console/CorrectionReasonDialog.vue';
import EnrollmentDecisionTable from '@/components/console/EnrollmentDecisionTable.vue';
import enrollmentDecisionPage from '@/mixins/enrollment-decision-page';
import { listAdminEnrollments } from '@/services/admin-api';
import { cancelEnrollment } from '@/services/enrollment-api';
import { listInstitutions } from '@/services/institution-api';
import { confirmDialog } from '@/utils/confirm-dialog';
import { formatDateTimeFields } from '@/utils/datetime';
import { DECIDABLE_STATUSES, enrollmentBadgeLabel } from '@/utils/enrollment-decision';
import { toInstitutionOptions } from '@/utils/institution-option';
import { formatUserDisplayName } from '@/utils/user-display';

// 서버 Size 상한
const PAGE_SIZE = 100;
const INSTITUTION_OPTION_LIMIT = 100;
const STATUS_FILTERS = [
  { label: '처리 대기', value: 'PENDING' },
  { label: '수강 확정', value: 'APPROVED' },
  { label: '거절', value: 'REJECTED' },
  { label: '취소·철회', value: 'CLOSED' },
  { label: '전체', value: 'ALL' },
];
// 서버 status 는 한 값만 받는다 — 한 값이면 서버에서, 여러 값이면 받아서 거른다
const FILTER_STATUSES = {
  PENDING: DECIDABLE_STATUSES,
  APPROVED: ['APPROVED'],
  REJECTED: ['REJECTED'],
  CLOSED: ['CANCELLED', 'WITHDRAWN'],
};

function filterFromQuery(value) {
  if (value === 'APPLIED' || value === 'WAITLISTED') return 'PENDING';
  return STATUS_FILTERS.some((option) => option.value === value) ? value : 'PENDING';
}

const queryText = (value) => (typeof value === 'string' ? value : '');

export default {
  name: 'AdminEnrollmentsListPage',
  components: { AppErrorBanner, AppPageHeader, CorrectionReasonDialog, EnrollmentDecisionTable },
  mixins: [enrollmentDecisionPage],
  data() {
    return {
      PAGE_SIZE,
      STATUS_FILTERS,
      rows: [],
      loading: false,
      errorMessage: '',
      capped: false,
      statusFilter: filterFromQuery(this.$route.query.status),
      institutionId: queryText(this.$route.query.institutionId) || null,
      searchText: queryText(this.$route.query.q),
      institutionOptions: [],
      institutionFilter: '',
      institutionsLoading: false,
    };
  },
  computed: {
    filteredInstitutionOptions() {
      const needle = this.institutionFilter.toLowerCase();
      const options = needle
        ? this.institutionOptions.filter((option) => option.label.toLowerCase().includes(needle))
        : this.institutionOptions;
      return [{ label: '전체 기관', value: null }, ...options];
    },
    filteredRows() {
      const statuses = FILTER_STATUSES[this.statusFilter];
      return statuses ? this.rows.filter((row) => statuses.includes(row.status)) : this.rows;
    },
    emptyText() {
      return this.statusFilter === 'PENDING' ? '처리할 수강 신청이 없습니다.' : '조건에 맞는 수강 신청이 없습니다.';
    },
  },
  watch: {
    '$route.query'(query) {
      this.statusFilter = filterFromQuery(query.status);
      const institutionId = queryText(query.institutionId) || null;
      const searchText = queryText(query.q);
      if (institutionId === this.institutionId && searchText === this.searchText) return;
      this.institutionId = institutionId;
      this.searchText = searchText;
      this.loadRows();
    },
  },
  mounted() {
    this.loadInstitutions();
    this.loadRows();
  },
  methods: {
    replaceQuery(patch) {
      this.$router.replace({ query: { ...this.$route.query, ...patch } });
    },
    setStatusFilter(value) {
      this.statusFilter = value;
      this.replaceQuery({ status: value === 'PENDING' ? undefined : value });
      this.loadRows();
    },
    setInstitution(value) {
      this.institutionId = value || null;
      this.replaceQuery({ institutionId: this.institutionId || undefined });
      this.loadRows();
    },
    setSearchText(value) {
      this.searchText = String(value || '');
      this.replaceQuery({ q: this.searchText.trim() || undefined });
      this.loadRows();
    },
    filterInstitutions(text, update) {
      update(() => {
        this.institutionFilter = String(text || '').trim();
      });
    },
    async loadInstitutions() {
      this.institutionsLoading = true;
      try {
        const page = await listInstitutions({ page: 0, size: INSTITUTION_OPTION_LIMIT, sort: 'name,asc' });
        this.institutionOptions = toInstitutionOptions(page.data || []);
      } catch {
        // 기관 목록이 없어도 전체 목록은 본다
        this.institutionOptions = [];
      } finally {
        this.institutionsLoading = false;
      }
    },
    toRow(row) {
      return {
        ...row,
        canDecide: true,
        applicantLabel: formatUserDisplayName(row),
        courseTitle: row.institutionName ? `${row.courseTitle} · ${row.institutionName}` : row.courseTitle,
        statusLabel: enrollmentBadgeLabel(row),
        appliedAtText: formatDateTimeFields(row, 'appliedAt', 'createdAt'),
        processedAtText: formatDateTimeFields(row, 'processedAt', 'approvedAt', 'rejectedAt', 'cancelledAt', 'withdrawnAt'),
      };
    },
    async loadRows() {
      const statuses = FILTER_STATUSES[this.statusFilter];
      this.loading = true;
      this.errorMessage = '';
      try {
        const page = await listAdminEnrollments({
          page: 0,
          size: PAGE_SIZE,
          status: statuses?.length === 1 ? statuses[0] : undefined,
          institutionId: this.institutionId || undefined,
          q: this.searchText.trim() || undefined,
        });
        this.rows = (page.data || []).map(this.toRow);
        this.capped = (page.page?.totalElements || 0) > PAGE_SIZE;
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
  },
};
</script>

<style scoped>
.capped-note {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--db-text-sub);
}
</style>
