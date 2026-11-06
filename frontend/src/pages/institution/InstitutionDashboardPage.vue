<template>
  <q-page padding>
    <!-- 어느 기관인지 화면에 없었다 — 여러 기관을 맡으면 지금 어느 기관을 보는지 몰랐다 -->
    <AppPageHeader title="기관 대시보드" :subtitle="headerSubtitle">
      <template #actions>
        <q-btn
          outline
          no-caps
          color="primary"
          icon="refresh"
          label="새로고침"
          aria-label="대시보드 새로고침"
          :loading="loading"
          :disable="!institutionId"
          @click="loadDashboard"
        />
      </template>
    </AppPageHeader>

    <AppInstitutionMissingBanner :show="showInstitutionMissingBanner" />
    <!-- 플랫폼 관리자는 소속 기관이 없을 수 있다 — 빈 '-' 카드 대신 무엇을 해야 하는지 알린다 -->
    <p v-if="!institutionId && isAdmin" class="pick-note" role="status">
      <q-icon name="info" size="18px" aria-hidden="true" />
      콘솔 상단에서 기관을 선택하면 그 기관의 현황을 보여 줍니다.
    </p>
    <AppErrorBanner :message="errorMessage">
      <template #action>
        <q-btn flat dense no-caps color="negative" label="다시 시도" @click="loadDashboard" />
      </template>
    </AppErrorBanner>

    <template v-if="institutionId">
      <!-- 처리할 일: 건수와 처리 화면을 한 번에. 못 센 항목은 지어내지 않는다 -->
      <ConsoleTodoList
        class="q-mb-md"
        :items="todoItems"
        :loading="loading"
        @retry="loadDashboard"
      />

      <!-- 여섯 장 중 넷이 강사 숫자(전체·활성·비활성·탈퇴)라 운영 숫자가 뒤로 밀렸다 — 운영에 쓰는 넷만.
           강사 상태별 숫자는 강사 관리 화면에서 본다 -->
      <ConsoleStatCards :items="cards" :loading="loading && !stats" col-class="col-6 col-md-4" />
    </template>
  </q-page>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import AppInstitutionMissingBanner from '@/components/AppInstitutionMissingBanner.vue';
import AppPageHeader from '@/components/AppPageHeader.vue';
import ConsoleStatCards from '@/components/console/ConsoleStatCards.vue';
import ConsoleTodoList from '@/components/console/ConsoleTodoList.vue';
import institutionScope from '@/mixins/institution-scope';
import {
  getInstitutionDashboard,
  listInstitutionCourses,
  listInstitutionEnrollments,
} from '@/services/institution-api';
import { listInstitutionInstructorApplications } from '@/services/instructor-api';

/** 페이지 응답의 전체 건수. 못 받았으면 null — 0으로 바꿔 '할 일 없음'처럼 보이게 하지 않는다 */
function totalOf(page) {
  const total = page?.page?.totalElements; // PageMeta 는 응답의 page 에 있다(meta 는 ApiMeta)
  return Number.isFinite(total) ? total : null;
}

function emptyTodoCounts() {
  return { appliedEnrollments: null, draftCourses: null, pendingInstructors: null };
}

export default {
  name: 'InstitutionDashboardPage',
  components: { AppErrorBanner, AppInstitutionMissingBanner, AppPageHeader, ConsoleStatCards, ConsoleTodoList },
  mixins: [institutionScope],
  data() {
    return {
      loading: false,
      errorMessage: '',
      stats: null,
      // 모집 중 강의 수 — 대시보드 통계엔 없어 목록 건수로 센다(못 세면 null)
      recruitingCount: null,
      todoCounts: emptyTodoCounts(),
    };
  },
  computed: {
    institutionName() {
      return (this.myInstitutions || []).find((item) => item.institutionId === this.institutionId)?.name || '';
    },
    headerSubtitle() {
      // 어느 기관인지만 — '운영 현황입니다' 같은 설명은 제목과 같은 말이라 뺐다
      return this.institutionName || '';
    },
    cards() {
      const s = this.stats || {};
      return [
        { label: '활성 강사', value: s.instructorActive ?? '-', to: { path: '/institution/instructors', query: { tab: 'roster' } } },
        { label: '강의', value: s.courseCount ?? '-', to: '/institution/courses' },
        { label: '모집 중 강의', value: this.recruitingCount ?? '-', to: { path: '/institution/courses', query: { status: 'RECRUITING' } } },
        // 승인·거절까지 센 전체 건수 — '수강 신청'만으론 처리 대기 건수로 읽혔다(대기는 위 처리할 일)
        { label: '전체 수강 신청', value: s.enrollmentCount ?? '-', to: '/institution/enrollments' },
        // 지금 APPROVED 인 수강(철회·취소 제외)과 지금 COMPLETED 인 수료 — 철회해도 승인 수가 줄지 않았다(QA #22·#31·#33)
        { label: '수강 확정', value: s.approvedEnrollmentCount ?? '-', to: { path: '/institution/enrollments', query: { status: 'APPROVED' } } },
        { label: '수료', value: s.completionCount ?? '-', to: '/institution/completion' },
      ];
    },
    // 건수와 같은 조건으로 연다 — 세는 기준(status)과 여는 목록이 어긋나면 숫자와 목록이 다르다
    todoItems() {
      const counts = this.todoCounts;
      return [
        {
          key: 'applied-enrollments',
          label: '수강 신청 대기',
          count: counts.appliedEnrollments,
          to: { path: '/institution/enrollments', query: { status: 'APPLIED' } },
        },
        {
          key: 'draft-courses',
          label: '공개 대기 강의',
          count: counts.draftCourses,
          to: { path: '/institution/courses', query: { status: 'DRAFT' } },
        },
        {
          key: 'pending-instructors',
          label: '강사 신청 대기',
          count: counts.pendingInstructors,
          to: { path: '/institution/instructors', query: { tab: 'applications', status: 'PENDING' } },
        },
      ];
    },
  },
  watch: {
    // 관리자가 콘솔 상단에서 기관을 바꾸면 그 기관 기준으로 다시 센다
    institutionId() {
      this.loadDashboard();
    },
  },
  mounted() {
    this.loadDashboard();
  },
  methods: {
    async loadDashboard() {
      if (!this.institutionId) {
        this.stats = null;
        this.todoCounts = emptyTodoCounts();
        return;
      }
      const institutionId = this.institutionId;
      this.loading = true;
      this.errorMessage = '';
      try {
        // 건수만 필요하니 size=1 로 totalElements 만 받는다. 할 일 건수 실패는 본문을 막지 않는다
        const [stats, enrollments, drafts, applications, recruiting] = await Promise.all([
          getInstitutionDashboard(institutionId),
          listInstitutionEnrollments(institutionId, { page: 0, size: 1, status: 'APPLIED' }).catch(() => null),
          listInstitutionCourses(institutionId, { page: 0, size: 1, status: 'DRAFT' }).catch(() => null),
          listInstitutionInstructorApplications(institutionId, { page: 0, size: 1, status: 'PENDING' }).catch(
            () => null,
          ),
          listInstitutionCourses(institutionId, { page: 0, size: 1, status: 'RECRUITING' }).catch(() => null),
        ]);
        // 기다리는 사이 기관을 바꿨으면 늦게 온 이전 기관 응답은 버린다
        if (institutionId !== this.institutionId) return;
        this.stats = stats;
        this.recruitingCount = totalOf(recruiting);
        this.todoCounts = {
          appliedEnrollments: totalOf(enrollments),
          draftCourses: totalOf(drafts),
          pendingInstructors: totalOf(applications),
        };
      } catch (error) {
        this.stats = null;
        this.todoCounts = emptyTodoCounts();
        this.errorMessage = error.message || '대시보드를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
.pick-note {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 16px;
  padding: 12px 14px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
  background: var(--db-bg-active);
  font-size: 14px;
  color: var(--db-text-body);
}
</style>
