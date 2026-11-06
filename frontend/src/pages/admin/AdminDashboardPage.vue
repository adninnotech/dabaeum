<template>
  <q-page class="dashboard">
    <h1 class="dashboard__title">대시보드</h1>

    <AppErrorBanner :message="errorMessage">
      <template #action>
        <q-btn flat dense no-caps color="negative" label="다시 시도" @click="load" />
      </template>
    </AppErrorBanner>

    <!-- 처리할 일: 숫자만 보여 주지 않고 바로 그 화면(필터·탭 지정)으로 보낸다. 못 센 항목은 지어내지 않는다 -->
    <ConsoleTodoList :items="todoItems" :loading="loading" @retry="load" />

    <!-- KPI 6장 — 불러오는 동안 '-'가 보이면 '0'·'없음'처럼 읽혀 자리표시(skeleton)를 둔다 -->
    <ConsoleStatCards :items="kpis" :loading="loading && !dashboard" col-class="col-6 col-sm-4 col-lg-3" />

    <div class="summary-grid">
      <section v-for="group in summaryGroups" :key="group.title" class="summary-card">
        <h2 class="summary-card__title">{{ group.title }}</h2>
        <div class="summary-card__body">
          <div v-if="!group.rows.length" class="summary-card__empty">데이터가 없습니다.</div>
          <div v-for="row in group.rows" :key="row.label" class="summary-card__row">
            <span class="summary-card__label">{{ row.label }}</span>
            <span class="summary-card__value">{{ row.value }}</span>
          </div>
        </div>
      </section>
    </div>

    <div class="list-grid">
      <section class="list-card">
        <header class="list-card__head">
          <h2 class="summary-card__title">최근 가입 회원</h2>
          <q-btn
            flat
            no-caps
            class="btn-sm list-card__more"
            label="전체 회원 보기 >"
            @click="goMembers"
          />
        </header>
        <div class="list-card__body">
          <AppEmptyState
            v-if="!recentMembers.length"
            compact
            description="최근 가입 회원이 없습니다."
          />
          <!-- 누르면 그 회원 상세로. 역할 태그는 뺐다 — 응답의 role 이 가입 때 역할(늘 학습자)이라
               강사·기관 관리자도 '학습자'로 보였다 -->
          <router-link
            v-for="member in recentMembers"
            :key="member.userId"
            :to="`/admin/students/${member.userId}`"
            class="member-row row-link"
          >
            <div class="cell-user__avatar">{{ initial(member.name) }}</div>
            <div class="cell-lines">
              <span class="cell-title">{{ member.name || '-' }}</span>
              <span class="cell-note cell-note--sub">
                가입 {{ formatDateTimeKst(member.createdAt, '-') }}
              </span>
            </div>
          </router-link>
        </div>
      </section>

      <section class="list-card">
        <header class="list-card__head">
          <h2 class="summary-card__title">최근 공지사항</h2>
          <q-btn
            flat
            no-caps
            class="btn-sm list-card__more"
            label="전체 보기 >"
            @click="goNotices"
          />
        </header>
        <div class="list-card__body">
          <!-- 공지를 못 불러온 것을 '등록된 공지사항이 없습니다'로 보였다 — 따로 알린다 -->
          <div v-if="noticesFailed" class="list-card__error" role="alert">
            공지사항을 불러오지 못했습니다.
            <q-btn flat dense no-caps color="primary" label="다시 시도" @click="load" />
          </div>
          <AppEmptyState v-else-if="!notices.length" compact description="등록된 공지사항이 없습니다." />
          <router-link
            v-for="notice in notices"
            :key="notice.id"
            :to="`/admin/notices/${notice.id}/edit`"
            class="notice-row row-link"
          >
            <q-icon name="campaign" size="20px" class="notice-row__icon" />
            <span class="notice-row__title">{{ notice.title || '-' }}</span>
            <span class="cell-note cell-note--sub">{{ formatDateKst(notice.createdAt, '-') }}</span>
          </router-link>
        </div>
      </section>
    </div>

    <q-inner-loading :showing="loading">
      <q-spinner color="primary" size="32px" />
    </q-inner-loading>
  </q-page>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import ConsoleStatCards from '@/components/console/ConsoleStatCards.vue';
import ConsoleTodoList from '@/components/console/ConsoleTodoList.vue';
import { getPlatformDashboard } from '@/services/admin-api';
import { listCourses } from '@/services/course-api';
import { listAdminNotices } from '@/services/notice-api';
import { formatDateKst, formatDateTimeKst } from '@/utils/datetime';

/** GET /admin/dashboard 는 배열이 아니라 객체를 준다 — 화면 순서·라벨을 여기서 정한다 */
const KPI_FIELDS = [
  { key: 'userCount', label: '전체 회원', unit: '명' },
  { key: 'instructorCount', label: '강사 수', unit: '명' },
  { key: 'institutionCount', label: '운영 기관 수', unit: '개' },
  { key: 'courseCount', label: '전체 강의 수', unit: '개' },
  { key: 'courseInProgressCount', label: '진행 중 강의', unit: '개' },
  { key: 'enrollmentCount', label: '누적 수강 수', unit: '건' },
  // 지금 COMPLETED 인 수료·지금 ISSUED 인 배지(폐기 제외), 전체 누적 — 대시보드에 없어 수료·배지 현황을 볼 수 없었다(QA #34)
  { key: 'completionCount', label: '누적 수료', unit: '건' },
  { key: 'badgeCount', label: '발급 배지', unit: '개' },
];

// 강사·기관 신청 승인은 한 화면(탭)에서 처리한다 — ?tab= 으로 해당 탭을 바로 연다
const APPROVALS_PATH = '/admin/approvals';

const SUMMARY_GROUPS = [
  {
    source: 'memberSummary',
    title: '회원 현황 요약',
    fields: [
      { key: 'learnerCount', label: '학습자' },
      { key: 'instructorCount', label: '강사' },
      { key: 'institutionAdminCount', label: '기관 관리자' },
      { key: 'withdrawnCount', label: '탈퇴' },
      // 최근 30일(고정) 가입자 수. Figma 학생회원관리에 같은 카드가 이미 있어 대시보드에만 둔다.
      { key: 'recentSignupCount', label: '최근 가입(30일)' },
    ],
  },
  // 신청 대기(approvalSummary) 건수는 '처리할 일'에만 둔다 — 같은 숫자가 두 번 보이지 않게
  {
    source: 'courseSummary',
    title: '강의 현황 요약',
    fields: [
      { key: 'total', label: '전체' },
      { key: 'inProgress', label: '진행 중' },
      { key: 'recruiting', label: '모집 중' },
      { key: 'completed', label: '종료' },
    ],
  },
  {
    // 한 강사가 여러 기관에 속해도 COUNT(DISTINCT user_id)로 한 번만 센다 — 기관별 대시보드의
    // 강사 수 합과 여기 total이 달라도 정상이다(계약 설명, 2026-09-18).
    source: 'instructorSummary',
    title: '강사 현황 요약',
    fields: [
      { key: 'total', label: '전체' },
      { key: 'active', label: '활성' },
      { key: 'inactive', label: '비활성' },
      { key: 'withdrawn', label: '탈퇴' },
    ],
  },
  {
    // INACTIVE·SUSPENDED가 inactive 하나로 묶인다(계약 설명).
    source: 'institutionSummary',
    title: '기관 현황 요약',
    fields: [
      { key: 'total', label: '전체' },
      { key: 'active', label: '활성' },
      { key: 'inactive', label: '비활성' },
    ],
  },
];

/** 페이지 응답의 전체 건수. 못 받았으면 null — 0으로 바꿔 '할 일 없음'처럼 보이게 하지 않는다 */
function totalOf(page) {
  const total = page?.page?.totalElements; // PageMeta 는 응답의 page 에 있다(meta 는 ApiMeta)
  return Number.isFinite(total) ? total : null;
}

/** 0은 0으로, 없으면 "-" */
function display(value) {
  return value === null || value === undefined ? '-' : value;
}

export default {
  name: 'AdminDashboardPage',
  components: { AppEmptyState, AppErrorBanner, ConsoleStatCards, ConsoleTodoList },
  data() {
    return {
      loading: false,
      errorMessage: '',
      dashboard: null,
      notices: [],
      noticesFailed: false,
      // 공개 대기(DRAFT) 강의 수 — 대시보드 응답에 없어 GET /courses?status=DRAFT 의 totalElements 로 센다
      draftCourseCount: null,
    };
  },
  computed: {
    kpis() {
      const source = this.dashboard?.kpis || {};
      return KPI_FIELDS.map((field) => ({
        label: field.label,
        value: `${display(source[field.key])}${source[field.key] === null || source[field.key] === undefined ? '' : field.unit}`,
      }));
    },
    summaryGroups() {
      return SUMMARY_GROUPS.map((group) => {
        const source = this.dashboard?.[group.source] || {};
        return {
          title: group.title,
          rows: group.fields.map((field) => ({
            label: field.label,
            value: display(source[field.key]),
          })),
        };
      });
    },
    /** 대시보드가 못 왔으면(오류) 신청 대기 항목은 null → '불러오지 못함' */
    todoItems() {
      const approval = this.dashboard?.approvalSummary || null;
      const pick = (key) => (Number.isFinite(approval?.[key]) ? approval[key] : null);
      return [
        {
          key: 'instructor-applications',
          label: '강사 신청 대기',
          count: pick('instructorApplicationPending'),
          to: { path: APPROVALS_PATH, query: { tab: 'instructor' } },
        },
        {
          key: 'institution-applications',
          label: '기관 등록 신청 대기',
          count: pick('institutionApplicationPending'),
          to: { path: APPROVALS_PATH, query: { tab: 'institution' } },
        },
        {
          key: 'draft-courses',
          label: '공개 대기 강의',
          count: this.draftCourseCount,
          to: { path: '/admin/courses', query: { status: 'DRAFT' } },
        },
      ];
    },
    recentMembers() {
      return (this.dashboard?.recentMembers || []).slice(0, 5);
    },
  },
  created() {
    this.load();
  },
  methods: {
    formatDateKst,
    formatDateTimeKst,
    initial(name) {
      return (name || '?').trim().charAt(0).toUpperCase();
    },
    // 최근 가입엔 강사·기관 관리자도 섞인다 — 학생 목록이 아니라 전체 회원 목록으로
    goMembers() {
      this.$router.push('/admin/users');
    },
    goNotices() {
      this.$router.push('/admin/notices');
    },
    async load() {
      this.loading = true;
      this.errorMessage = '';
      try {
        // 건수만 필요하니 size=1 로 totalElements 만 받는다. 실패해도 대시보드 본문은 막지 않는다
        const [dashboard, notices, drafts] = await Promise.all([
          getPlatformDashboard(),
          listAdminNotices({ page: 0, size: 5 }).catch(() => null),
          listCourses({ page: 0, size: 1, status: 'DRAFT' }).catch(() => null),
        ]);
        this.dashboard = dashboard;
        this.notices = Array.isArray(notices?.data) ? notices.data : [];
        this.noticesFailed = notices === null;
        this.draftCourseCount = totalOf(drafts);
      } catch (error) {
        this.errorMessage = error?.message || '대시보드를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
.dashboard {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 48px 40px 20px;
  background: var(--db-bg-page);
}

.dashboard__title {
  display: flex;
  align-items: center;
  height: 58px;
  margin: 0;
  padding: 10px 0;
  font-size: 32px;
  line-height: 1.2;
  font-weight: 700;
  color: var(--db-text-title);
}

.summary-grid,
.list-grid {
  display: grid;
  gap: 16px;
}

/* 신청 현황 카드를 처리할 일로 옮겨 요약 카드가 넷 — 3열이면 한 장이 홀로 남는다. 넓으면 4열, 좁으면 2열 */
.summary-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

@media (max-width: 1439px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.list-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.summary-card,
.list-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  /* 높이 320px 고정에 안쪽 스크롤이라 다섯 줄짜리 요약도 스크롤 막대가 생겼다 — 내용만큼 */
  padding: 20px 16px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.summary-card__title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--db-text-body);
}

.summary-card__body,
.list-card__body {
  flex: 1;
  min-height: 0;
}

.row-link {
  border-radius: 6px;
  color: inherit;
  text-decoration: none;
}

.row-link:hover,
.row-link:focus-visible {
  background: var(--db-bg-subtle);
}

.list-card__error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
  font-size: 14px;
  color: var(--db-error);
}

.summary-card__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 12px 0;
  border-bottom: 1px solid var(--db-border);
}

.summary-card__row:last-child {
  border-bottom: none;
}

.summary-card__label {
  font-size: 16px;
  font-weight: 700;
  color: var(--db-text-title);
}

.summary-card__value {
  font-size: 16px;
  font-weight: 400;
  color: var(--db-text-title);
}

.summary-card__empty {
  padding: 12px 0;
  font-size: 14px;
  color: var(--db-text-sub);
}

.list-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.list-card__more {
  color: var(--db-accent);
}

.member-row,
.notice-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 0;
}

.notice-row__icon {
  color: var(--db-text-sub);
}

.notice-row__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 16px;
  color: var(--db-text-title);
}

@media (max-width: 1279px) {
  .summary-grid,
  .list-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

/* 폰: 좌우 40px 여백·32px 제목이 본문 폭을 먹었다 */
@media (max-width: 599px) {
  .dashboard {
    padding: 20px 16px 16px;
  }

  .dashboard__title {
    height: auto;
    font-size: 24px;
  }
}
</style>
