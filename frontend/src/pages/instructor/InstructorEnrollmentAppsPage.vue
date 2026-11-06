<template>
  <q-page padding>
    <AppPageHeader
      title="수강 신청 관리"
      show-refresh
      :loading="loading"
      @refresh="loadRows"
    />
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
      <!-- 처리 끝난 신청까지 섞여 할 일이 묻혔다 — 기본은 처리 대기(신청·대기)만 -->
      <div class="col-12 col-md-3">
        <q-select
          v-model="statusFilter"
          outlined
          dense
          emit-value
          map-options
          :options="STATUS_FILTERS"
          label="상태"
        />
      </div>
    </div>

    <!-- 표·폰 카드·일괄 처리는 기관 '수강 관리'와 같은 컴포넌트 -->
    <EnrollmentDecisionTable
      v-model:selected="checked"
      :rows="filteredRows"
      :loading="loading"
      :empty-text="emptyText"
      :busy-id="busyId"
      :bulk-saving="bulkSaving"
      withdrawable
      @approve="approve"
      @withdraw="withdraw"
      @reject="openReject"
      @bulk-approve="bulkApprove"
      @bulk-reject="openBulkReject"
    >
      <template #note>
        <p v-if="cappedText" class="capped-note">{{ cappedText }}</p>
      </template>
    </EnrollmentDecisionTable>
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
import AppPageHeader from '@/components/AppPageHeader.vue';
import CorrectionReasonDialog from '@/components/console/CorrectionReasonDialog.vue';
import EnrollmentDecisionTable from '@/components/console/EnrollmentDecisionTable.vue';
import enrollmentDecisionPage from '@/mixins/enrollment-decision-page';
import instructorConsoleCourses from '@/mixins/instructor-console-courses';
import { listCourseEnrollments } from '@/services/enrollment-api';
import { formatDateTimeFields } from '@/utils/datetime';
import { DECIDABLE_STATUSES, enrollmentBadgeLabel } from '@/utils/enrollment-decision';
import { formatCourseOption } from '@/utils/status';
import { formatUserDisplayName } from '@/utils/user-display';

const COURSE_PAGE_SIZE = 50;
const ENROLLMENT_PAGE_SIZE = 100;
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

export default {
  name: 'InstructorEnrollmentAppsPage',
  components: { AppPageHeader, AppErrorBanner, CorrectionReasonDialog, EnrollmentDecisionTable },
  mixins: [instructorConsoleCourses, enrollmentDecisionPage],
  data() {
    return {
      STATUS_FILTERS,
      statusFilter: 'PENDING',
      loading: false,
      errorMessage: '',
      rows: [],
      courses: [],
      // 서버가 준 것보다 실제가 많은지 — 말없이 잘리지 않게 알린다
      coursesCapped: false,
      cappedCourseTitles: [],
      // 대시보드 강의별 칩에서 ?courseId 로 오면 그 강의만
      selectedCourseId: typeof this.$route.query.courseId === 'string' ? this.$route.query.courseId : null,
    };
  },
  computed: {
    courseFilterOptions() {
      return [
        { label: '전체', value: null },
        ...(this.courses || []).map((item) => ({ label: formatCourseOption(item), value: item.id })),
      ];
    },
    filteredRows() {
      const statuses = FILTER_STATUSES[this.statusFilter];
      return this.rows.filter(
        (row) =>
          (!this.selectedCourseId || row.courseId === this.selectedCourseId) &&
          (!statuses || statuses.includes(row.status)),
      );
    },
    emptyText() {
      return this.statusFilter === 'PENDING' ? '처리할 수강 신청이 없습니다.' : '조건에 맞는 수강 신청이 없습니다.';
    },
    cappedText() {
      const parts = [];
      if (this.coursesCapped) parts.push(`최근 강의 ${COURSE_PAGE_SIZE}개`);
      if (this.cappedCourseTitles.length) {
        parts.push(`${this.cappedCourseTitles.join(', ')} 강의의 최근 신청 ${ENROLLMENT_PAGE_SIZE}건`);
      }
      return parts.length ? `${parts.join(', ')}만 보여 줍니다. 나머지는 강의 상세의 '수강 신청 현황'에서 확인해 주세요.` : '';
    },
  },
  mounted() {
    this.loadRows();
  },
  methods: {
    toRow(row, course, canDecide) {
      return {
        ...row,
        courseTitle: course.title,
        courseId: course.id,
        canDecide,
        applicantLabel: formatUserDisplayName(row),
        statusLabel: enrollmentBadgeLabel(row),
        appliedAtText: formatDateTimeFields(row, 'appliedAt', 'createdAt'),
        processedAtText: formatDateTimeFields(row, 'processedAt', 'approvedAt', 'rejectedAt', 'cancelledAt', 'withdrawnAt'),
      };
    },
    async loadCourseRows(course, failed, capped) {
      // 관리자 열람은 모든 강의, 강사는 주강사로 배정된 강의만 결정할 수 있다
      const canDecide = this.consoleAsAdmin || course.instructorRole === 'MAIN';
      try {
        const page = await listCourseEnrollments(course.id, { page: 0, size: ENROLLMENT_PAGE_SIZE });
        if ((page.page?.totalElements || 0) > ENROLLMENT_PAGE_SIZE) capped.push(course.title || '이름 없는 강의');
        return (page.data || []).map((row) => this.toRow(row, course, canDecide));
      } catch {
        // 예전엔 조용히 빈 목록으로 넘어가 그 강의가 '신청 없음'처럼 보였다
        failed.push(course.title || '이름 없는 강의');
        return [];
      }
    },
    async loadRows() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const courses = await this.fetchConsoleCourses({ page: 0, size: COURSE_PAGE_SIZE });
        this.courses = courses.data || [];
        this.coursesCapped = (courses.page?.totalElements || 0) > COURSE_PAGE_SIZE;
        const failed = [];
        const capped = [];
        const chunks = await Promise.all(this.courses.map((course) => this.loadCourseRows(course, failed, capped)));
        this.rows = chunks.flat();
        this.cappedCourseTitles = capped;
        if (failed.length) this.errorMessage = `다음 강의의 신청을 불러오지 못했습니다: ${failed.join(', ')}.`;
      } catch (error) {
        this.rows = [];
        this.errorMessage = error.message || '수강 신청을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
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
