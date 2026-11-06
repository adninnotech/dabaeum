<template>
  <ConsoleListPage
    v-model:page="page"
    v-model:page-size="pageSize"
    title="수강 현황 관리"
    list-title="수강 현황"
    unit="건"
    empty-text="수강 내역이 없습니다."
    search-placeholder="강의명, 수강자명으로 검색"
    :stats="stats"
    :columns="columns"
    :rows="pagedRows"
    :loading="loading"
    :error="errorMessage"
    :total="filteredRows.length"
    :filters="filters"
    row-clickable
    @search="onSearch"
    @row-click="goAttendance"
  >
    <template #cell-user="{ row }">
      <div class="cell-user">
        <div class="cell-user__avatar">{{ initial(row.userName) }}</div>
        <div class="cell-lines">
          <span class="cell-title">{{ row.userName || '-' }}</span>
          <span class="cell-note cell-note--sub">{{ formatDateKst(row.appliedAt, '-') }}</span>
        </div>
      </div>
    </template>

    <template #cell-course="{ row }">
      <div class="cell-lines">
        <RouterLink :to="`/admin/courses/${row.courseId}`" class="cell-title cell-link">
          {{ row.courseTitle || '-' }}
        </RouterLink>
        <span class="cell-note cell-note--sub">{{ row.institutionName || '-' }}</span>
      </div>
    </template>

    <!-- 기간 문자열이 두 줄로 접히면 행이 64를 넘는다 -->
    <template #cell-period="{ row }">
      <span class="nowrap">{{ period(row.startDate, row.endDate) }}</span>
    </template>

    <template #cell-status="{ row }">
      <StatusBadge :label="PHASE[row.phase].label" :color="PHASE[row.phase].color" />
    </template>

    <template #cell-progress="{ row }">
      <div class="progress-cell">
        <span class="cell-note cell-note--sub">{{ progressLabel(row) }}</span>
        <q-linear-progress :value="(row.progressPercent ?? 0) / 100" size="6px" rounded color="primary" />
      </div>
    </template>
    <template #cell-recent="{ row }">{{ formatDateKst(row.lastStudiedAt, '-') }}</template>

    <template #row-actions="{ row }">
      <q-item v-close-popup clickable @click="goAttendance(row)">
        <q-item-section>출결 상세</q-item-section>
      </q-item>
      <!-- 이수 확정은 수업이 시작된 강의에서만 뜻이 있다 — 예정·취소 행엔 뺀다 -->
      <q-item v-if="canConfirm(row)" v-close-popup clickable @click="confirmCompletion(row)">
        <q-item-section>이수 확정</q-item-section>
      </q-item>
    </template>
  </ConsoleListPage>
</template>

<script>
import ConsoleListPage from '@/components/console/ConsoleListPage.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import consoleList from '@/mixins/console-list';
import { listAdminEnrollments } from '@/services/admin-api';
import { confirmCompletion } from '@/services/completion-api';
import { getCourse, listCourses } from '@/services/course-api';
import { confirmDialog } from '@/utils/confirm-dialog';
import { formatUserDisplayName } from '@/utils/user-display';
import { listInstitutions } from '@/services/institution-api';
import { formatDateKst } from '@/utils/datetime';
import { EDUCATION_TYPE_OPTIONS_ALL, formatCourseOption } from '@/utils/status';
import { toInstitutionOptions } from '@/utils/institution-option';

// 목록(100개)에 없는 강의를 따로 받을 때 한 번에 보내는 수
const COURSE_FETCH_CONCURRENCY = 5;

const COLUMNS = [
  { key: 'user', label: '수강생', width: 218 },
  { key: 'course', label: '수강 강의' },
  { key: 'period', label: '수강 기간', width: 210, align: 'center' },
  { key: 'status', label: '상태', width: 103, align: 'center' },
  { key: 'progress', label: '학습 진행율', width: 178, align: 'center' },
  { key: 'recent', label: '최근 학습일', width: 139, align: 'center' },
];

/**
 * b-ops-spec.md:16 — 배지·통계·상태 필터는 수강신청(enrollment) 상태가 아니라 강의(course)
 * 상태 축이다. /admin/enrollments 응답엔 courseStatus 가 없어 load()에서 조인한다.
 */
const PHASE = {
  IN_PROGRESS: { label: '수강 중', color: 'var(--db-status-blue)' },
  COMPLETED: { label: '수강 종료', color: 'var(--db-status-green)' },
  UPCOMING: { label: '수강 예정', color: 'var(--db-status-orange)' },
  CANCELLED: { label: '취소', color: 'var(--db-status-pink)' },
  // 강의 정보를 못 찾은 행 — 예전엔 '수강 예정'·기간 '-'로 틀리게 보였다
  UNKNOWN: { label: '확인 불가', color: 'var(--db-status-gray)' },
};

function enrollmentPhase(courseStatus) {
  if (!courseStatus) return 'UNKNOWN';
  if (courseStatus === 'CANCELLED') return 'CANCELLED';
  if (courseStatus === 'COMPLETED') return 'COMPLETED';
  if (courseStatus === 'IN_PROGRESS') return 'IN_PROGRESS';
  return 'UPCOMING'; // RECRUITING · RECRUITMENT_CLOSED (그 외 미확인 값도 안전하게 이 쪽으로)
}

/**
 * 검색·필터 상태를 /admin/enrollments 쿼리로 조립한다. 상태(phase)는 강의 status 축이라
 * 서버 enrollment status 파라미터로 보내지 않는다 — institutionId·courseId·educationType·q 만 보낸다.
 */
export function buildEnrollmentQuery(search, page = 0, size = 100) {
  const filters = search?.filters || {};
  return {
    page,
    size,
    institutionId: filters.institutionId || undefined,
    courseId: filters.courseId || undefined,
    educationType: filters.educationType || undefined,
    q: search?.q || undefined,
  };
}

export default {
  name: 'AdminEnrollmentStatusPage',
  components: { ConsoleListPage, StatusBadge },
  mixins: [consoleList],
  data() {
    return {
      columns: COLUMNS,
      serverSearch: true,
      PHASE,
      institutionOptions: [],
      courseOptions: [],
    };
  },
  computed: {
    /**
     * institutionId·courseId·educationType·q 는 서버가 이미 걸렀다 — 재필터하지 않는다.
     * phase(강의 상태)만 서버 파라미터가 없어(00-common §3, 강의 축이라 enrollment status와
     * 다르다) 클라이언트에서 거른다.
     */
    filteredRows() {
      const phase = this.search.filters?.phase;
      if (!phase) return this.rows;
      return this.rows.filter((row) => row.phase === phase);
    },
    pagedRows() {
      const start = (this.page - 1) * this.pageSize;
      return this.filteredRows.slice(start, start + this.pageSize);
    },
    filters() {
      return [
        {
          key: 'phase',
          label: '상태',
          options: Object.entries(PHASE).map(([value, item]) => ({ label: item.label, value })),
        },
        { key: 'educationType', label: '수업 방식', options: EDUCATION_TYPE_OPTIONS_ALL },
        { key: 'institutionId', label: '기관', options: this.institutionOptions },
        { key: 'courseId', label: '강의', options: this.courseOptions },
      ];
    },
    stats() {
      const byPhase = (phase) => this.rows.filter((row) => row.phase === phase).length;
      const learners = new Set(this.rows.map((row) => row.userId).filter(Boolean));
      return [
        { label: this.statLabel('전체 수강생'), value: learners.size, unit: '명' },
        { label: this.statLabel('수강 중'), value: byPhase('IN_PROGRESS'), unit: '건' },
        { label: this.statLabel('수강 종료'), value: byPhase('COMPLETED'), unit: '건' },
        { label: this.statLabel('수강 예정'), value: byPhase('UPCOMING'), unit: '건' },
      ];
    },
  },
  created() {
    this.load();
    this.loadFilterOptions();
  },
  methods: {
    formatDateKst,
    initial(name) {
      return (name || '?').trim().charAt(0).toUpperCase();
    },
    period(from, to) {
      if (!from && !to) return '-';
      return `${formatDateKst(from, '-')} ~ ${formatDateKst(to, '-')}`;
    },
    progressLabel(row) {
      const value = row.progressPercent ?? '-';
      return value === '-' ? value : `${value}%`;
    },
    goAttendance(row) {
      this.$router.push({
        path: '/admin/attendance',
        query: { courseId: row.courseId, userId: row.userId },
      });
    },
    onSearch(payload) {
      this.search = payload;
      this.applyServerSearch();
    },
    async loadFilterOptions() {
      const [institutionPage, coursePage] = await Promise.all([
        listInstitutions({ page: 0, size: 100 }).catch(() => ({ data: [] })),
        listCourses({ page: 0, size: 100 }).catch(() => ({ data: [] })),
      ]);
      this.institutionOptions = toInstitutionOptions(institutionPage.data);
      this.courseOptions = (coursePage.data || []).map((item) => ({
        label: formatCourseOption(item),
        value: item.id,
      }));
    },
    /**
     * /admin/enrollments 응답(InstitutionEnrollment)에는 수강 기간(startDate·endDate)도
     * 강의 상태(courseStatus)도 없다 — 강좌 목록을 한 번 더 불러(행마다 아니다, 00-common §3)
     * courseId로 붙인다. phase 는 그 강의 상태에서 파생한다(b-ops-spec.md:16).
     */
    async load() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const [result, coursePage] = await Promise.all([
          listAdminEnrollments(buildEnrollmentQuery(this.search)),
          listCourses({ page: 0, size: 100 }).catch(() => ({ data: [] })),
        ]);
        const courseById = new Map((coursePage.data || []).map((course) => [course.id, course]));
        await this.fillMissingCourses(result.data || [], courseById);
        this.rememberTotal(result);
        this.rows = (result.data || []).map((row) => {
          const course = courseById.get(row.courseId);
          return {
            ...row,
            startDate: course?.startDate || null,
            endDate: course?.endDate || null,
            phase: enrollmentPhase(course?.status),
          };
        });
      } catch (error) {
        this.handleLoadError(error, '수강 현황 조회 실패');
      } finally {
        this.loading = false;
      }
    },
    /** 강의 목록(최근 100개)에 없는 강의는 하나씩 받아 채운다 — 못 받으면 '확인 불가'로 남긴다 */
    async fillMissingCourses(rows, courseById) {
      const missing = [...new Set(rows.map((row) => row.courseId).filter((id) => id && !courseById.has(id)))];
      for (let i = 0; i < missing.length; i += COURSE_FETCH_CONCURRENCY) {
        const batch = missing.slice(i, i + COURSE_FETCH_CONCURRENCY);
        const courses = await Promise.all(batch.map((id) => getCourse(id).catch(() => null)));
        courses.filter(Boolean).forEach((course) => courseById.set(course.id, course));
      }
    },
    canConfirm(row) {
      return row.phase === 'IN_PROGRESS' || row.phase === 'COMPLETED';
    },
    async confirmCompletion(row) {
      const name = formatUserDisplayName(row) || '이 수강생';
      const ok = await confirmDialog(this.$q, {
        title: '이수 확정',
        message: `${name}의 '${row.courseTitle || '강의'}' 이수를 확정합니다. 확정하면 조건에 맞는 배지가 발급되고, 되돌리려면 확정 취소가 필요합니다.`,
        ok: '이수 확정',
        cancel: '닫기',
      });
      if (!ok) return;
      try {
        await confirmCompletion(row.id);
        this.$q.notify({ type: 'positive', message: `${name}의 이수를 확정했습니다.`, position: 'top' });
        await this.load();
      } catch (error) {
        this.notifyError(error, '이수 확정에 실패했습니다.');
      }
    },
  },
};
</script>

<style scoped>
.nowrap {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.cell-link {
  text-decoration: none;
}

.cell-link:hover {
  text-decoration: underline;
}

/* 두 줄 셀과 같은 38 — 이 셀이 더 크면 행이 64를 넘는다 */
.progress-cell {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  height: 38px;
}
</style>
