<template>
  <q-page class="learning-page">
    <div class="learner-container learning-body">
      <header class="learning-header">
        <h1 class="learning-header__title">학습관리</h1>
      </header>

      <LearnerTabs :tabs="TAB_LABELS" :model-value="activeLabel" @update:model-value="selectTab">
        <template v-if="tab === 'interests'" #right>
          <q-select
            v-model="interestSort"
            outlined
            dense
            emit-value
            map-options
            :options="INTEREST_SORTS"
            class="learning-sort__select"
          />
        </template>
      </LearnerTabs>

      <!-- 탭마다 칩·검색의 순서와 모양이 달랐다(강좌: 칩→검색, 문의: 검색→칩, 수강평: 칩 옆 다른 검색칸) —
           모든 탭을 칩 → 검색 한 문법으로. 목록이 비어 있으면(검색 중이 아니면) 둘 다 숨긴다 -->
      <template v-if="showFilters">
        <div v-if="tabChips.length" class="learning-chips">
          <button
            v-for="chip in tabChips"
            :key="chip.value"
            type="button"
            class="learning-chip"
            :class="{ active: chip.value === activeChip }"
            :aria-pressed="chip.value === activeChip ? 'true' : 'false'"
            @click="setChip(chip.value)"
          >
            {{ chip.label }}
            <span class="learning-chip__count">{{ chip.count }}</span>
          </button>
        </div>

        <div class="learning-search">
          <q-input
            v-model="searchText"
            outlined
            dense
            clearable
            :placeholder="searchPlaceholder"
            class="learning-search__input"
            @keyup.enter="applySearch"
            @clear="clearSearch"
          >
            <!-- 밝은 파랑 [검색] 버튼이 강좌의 [수강평 쓰기]와 같은 무게로 보였다 — 칸 안 돋보기로(강좌 찾기와 같게) -->
            <template #append>
              <q-btn flat round dense icon="search" aria-label="검색" @click="applySearch" />
            </template>
          </q-input>
        </div>
      </template>

      <!-- 목록 하나라도 못 받으면 알리고 다시 시도하게 한다 (MyEnrollmentsPanel 과 같은 문법) -->
      <AppErrorBanner :message="errorMessage">
        <template #action>
          <q-btn flat color="negative" label="다시 시도" :loading="loading" @click="loadAll" />
        </template>
      </AppErrorBanner>

      <!-- ① 나의 강의 -->
      <template v-if="tab === 'courses'">
        <div class="learning-list">
          <!-- 불러오기 실패는 "없습니다"가 아니다 — 실패한 목록은 빈 안내를 그리지 않는다 -->
          <AppEmptyState
            v-if="!courseRows.length && !failedLists.courses"
            description="수강 중인 강좌가 없습니다."
            action-label="강좌 찾기"
            action-to="/courses"
          />
          <LearningCourseRow
            v-for="row in courseRows"
            :key="row.key"
            :row="row"
            @open="openEnrollment(row)"
            @open-course="openCourse(row.courseId)"
          />
        </div>
      </template>

      <!-- 수강 신청 — 승인 전 단계(신청·대기)와 취소·철회까지. 목록은 패널이 따로 읽는다 -->
      <MyEnrollmentsPanel v-else-if="tab === 'applications'" />

      <!-- ② 관심 강의 -->
      <template v-else-if="tab === 'interests'">
        <div class="learning-list">
          <AppEmptyState
            v-if="!interestRows.length && !failedLists.interests"
            description="관심 강좌가 없습니다."
            action-label="강좌 찾기"
            action-to="/courses"
          />
          <LearningCourseRow
            v-for="row in interestRows"
            :key="row.key"
            :row="row"
            variant="interest"
            @open="openCourse(row.courseId)"
            @open-course="openCourse(row.courseId)"
            @open-enrollment="openEnrollment(row)"
            @remove="dropInterest(row)"
          />
        </div>
      </template>

      <!-- ③ 나의 문의 — 펼침·상세 불러오기는 패널이 든다 -->
      <MyInquiriesPanel
        v-else-if="tab === 'inquiries'"
        :rows="inquiryRows"
        :total-count="inquiries.length"
        :query="query"
        :failed="failedLists.inquiries"
      />

      <!-- ④ 나의 수강평 -->
      <MyReviewsPanel
        v-else
        :rows="reviewRows"
        :writable-courses="writableCourses"
        :query="query"
        :failed="failedLists.reviews"
      />
    </div>

    <q-inner-loading :showing="loading">
      <q-spinner color="primary" size="32px" />
    </q-inner-loading>
  </q-page>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import LearnerTabs from '@/components/LearnerTabs.vue';
import LearningCourseRow from '@/components/learning/LearningCourseRow.vue';
import MyEnrollmentsPanel from '@/components/learning/MyEnrollmentsPanel.vue';
import MyInquiriesPanel from '@/components/learning/MyInquiriesPanel.vue';
import MyReviewsPanel from '@/components/learning/MyReviewsPanel.vue';
import { listMyInquiries } from '@/services/inquiry-api';
import {
  listLearningCourses,
  listMyInterests,
  listMyReviews,
  removeInterest,
  addInterest,
} from '@/services/learner-api';
import { getMyReviewSummary } from '@/services/review-api';
import { metaLine, toCourseCard } from '@/utils/course-fields';

const TABS = [
  { key: 'courses', label: '나의 강좌', placeholder: '강좌명, 기관명 검색' },
  { key: 'applications', label: '수강 신청', placeholder: '' },
  { key: 'interests', label: '관심 강좌', placeholder: '강좌명, 기관명 검색' },
  { key: 'inquiries', label: '나의 문의', placeholder: '제목, 강좌명 검색' },
  { key: 'reviews', label: '나의 수강평', placeholder: '강좌명, 내용 검색' },
];
const TAB_LABELS = TABS.map((item) => item.label);
const DEFAULT_TAB = TABS[0].key;

// 실패 안내에 쓰는 목록 이름 — 어떤 목록이 비어 보이는지 알려 준다
const LIST_LABELS = {
  courses: '나의 강좌',
  interests: '관심 강좌',
  inquiries: '나의 문의',
  reviews: '나의 수강평',
  summary: '수강평 요약',
};
const NO_FAILED_LISTS = Object.freeze({
  courses: false,
  interests: false,
  inquiries: false,
  reviews: false,
  summary: false,
});

// 별점 칩을 보일 최소 수강평 수
const REVIEW_CHIP_MIN = 6;

const INTEREST_SORTS = [
  { label: '최신순', value: 'latest' },
  // 관심 강좌 응답엔 모집 마감일이 없다 — 마감 임박이 아니라 모집 상태 순이라 이름을 사실대로
  { label: '모집중 먼저', value: 'deadline' },
];

export default {
  name: 'LearningPage',
  components: {
    AppEmptyState,
    AppErrorBanner,
    LearnerTabs,
    LearningCourseRow,
    MyEnrollmentsPanel,
    MyInquiriesPanel,
    MyReviewsPanel,
  },
  data() {
    return {
      TAB_LABELS,
      INTEREST_SORTS,
      tab: this.resolveTab(this.$route.query.tab),
      loading: false,
      errorMessage: '',
      // 목록별 불러오기 실패 여부 — 실패한 목록은 빈 안내 대신 오류 배너만 보인다
      failedLists: { ...NO_FAILED_LISTS },
      searchText: '',
      query: '',
      courses: [],
      interests: [],
      inquiries: [],
      reviews: [],
      courseFilter: 'ALL',
      interestSort: 'latest',
      inquiryFilter: 'ALL',
      reviewFilter: 'ALL',
      reviewSummary: null,
      removingId: '',
    };
  },
  computed: {
    activeLabel() {
      return TABS.find((item) => item.key === this.tab)?.label || TAB_LABELS[0];
    },
    tabChips() {
      if (this.tab === 'courses') return this.courseChips;
      if (this.tab === 'inquiries') return this.inquiryFilters;
      // 수강평은 수료한 강좌 수만큼이라 보통 몇 개 — 대부분 '0'인 별점 칩 6개는 넉넉할 때만
      if (this.tab === 'reviews') return this.reviews.length >= REVIEW_CHIP_MIN ? this.reviewFilters : [];
      return [];
    },
    activeChip() {
      return { courses: this.courseFilter, inquiries: this.inquiryFilter, reviews: this.reviewFilter }[this.tab];
    },
    /* 받은 목록이 0개면(검색 중이 아니면) 칩·검색이 쓸모없다 — 관심 0개에 검색칸, 수강평 0개에 '0' 칩 6개가 떴다 */
    showFilters() {
      if (this.tab === 'applications') return false;
      if (this.query) return true;
      const source = { courses: this.courses, interests: this.interests, inquiries: this.inquiries, reviews: this.reviews };
      return (source[this.tab] || []).length > 0;
    },
    searchPlaceholder() {
      return TABS.find((item) => item.key === this.tab)?.placeholder || '검색';
    },
    courseChips() {
      const rows = this.courses;
      return [
        { label: '전체', value: 'ALL', count: rows.length },
        {
          label: '진행 중',
          value: 'IN_PROGRESS',
          count: rows.filter((row) => row.status === 'IN_PROGRESS').length,
        },
        {
          label: '수료',
          value: 'COMPLETED',
          count: rows.filter((row) => row.status === 'COMPLETED').length,
        },
      ];
    },
    courseRows() {
      return this.courses
        .filter((row) => this.courseFilter === 'ALL' || row.status === this.courseFilter)
        .map((row) => ({
          key: row.enrollmentId || row.courseId,
          courseId: row.courseId,
          enrollmentId: row.enrollmentId,
          title: row.title || '-',
          ...toCourseCard(row),
          metaLine: metaLine(row),
          status: row.status,
          startDate: row.startDate,
          endDate: row.endDate,
          // 수강평은 강좌당 1개 — 이미 쓴 강의는 [수강평 쓰기]를 안 그린다
          reviewed: this.reviews.some((review) => review.courseId === row.courseId),
          // 목록 응답이 진도를 싣고 온다. sessionCount 분모는 목록 값을 쓴다(00-common §2-2)
          progress: {
            attendedCount: row.attendedCount ?? 0,
            sessionCount: row.sessionCount ?? 0,
            progressPercent: row.progressPercent ?? 0,
          },
        }));
    },
    interestRows() {
      const rows = this.interests
        .map((row) => ({
          key: row.id,
          interestId: row.id,
          courseId: row.courseId,
          title: row.courseTitle || row.title || '-',
          ...toCourseCard(row),
          metaLine: metaLine(row),
          courseStatus: row.courseStatus || '',
          createdAt: row.createdAt,
          // 관심 담은 뒤 수강 중인 강좌 — '신청하러 가기' 대신 나의 강좌로
          ...this.myCourseOf(row.courseId),
        }));
      if (this.interestSort !== 'deadline') {
        return rows.sort((a, b) => String(b.createdAt || '').localeCompare(a.createdAt || ''));
      }
      // 마감임박순 — Interest 응답에 recruitEndDate 가 없다. courseStatus 로 대체한다:
      // 모집중 → 모집마감 → 그 외, 같은 그룹은 최신순
      const rank = (s) => (s === 'RECRUITING' ? 0 : s === 'RECRUITMENT_CLOSED' ? 1 : 2);
      return rows.sort(
        (a, b) =>
          rank(a.courseStatus) - rank(b.courseStatus) ||
          String(b.createdAt || '').localeCompare(a.createdAt || ''),
      );
    },
    // 답변 상태 한 기준으로 — 예전엔 '답변 완료'와 유형('수강/학습')이 섞였고 '답변 대기'가 없었다
    inquiryFilters() {
      const rows = this.inquiries;
      const answered = rows.filter((row) => row.status === 'ANSWERED').length;
      return [
        { label: '전체', value: 'ALL', count: rows.length },
        { label: '답변 대기', value: 'WAITING', count: rows.length - answered },
        { label: '답변 완료', value: 'ANSWERED', count: answered },
      ];
    },
    inquiryRows() {
      return this.inquiries
        .filter((row) => {
          if (this.inquiryFilter === 'ANSWERED') return row.status === 'ANSWERED';
          if (this.inquiryFilter === 'WAITING') return row.status !== 'ANSWERED';
          return true;
        })
        .map((row) => ({
          ...row,
          ...toCourseCard(row),
          metaLine: metaLine(row),
        }));
    },
    reviewFilters() {
      const d = this.reviewSummary?.distribution;
      const count = (star) => (d ? (d[`star${star}`] ?? 0) : 0);
      return [
        { label: '전체', value: 'ALL', count: this.reviewSummary?.totalCount ?? 0 },
        ...[5, 4, 3, 2, 1].map((rating) => ({
          label: `★${rating}`,
          value: String(rating),
          count: count(rating),
        })),
      ];
    },
    reviewRows() {
      return this.reviews
        .filter((row) => this.reviewFilter === 'ALL' || String(row.rating) === this.reviewFilter)
        .map((row) => {
          const rating = Number(row.rating) || 0;
          return {
            id: row.id,
            courseId: row.courseId,
            title: row.courseTitle || '-',
            ...toCourseCard(row),
            metaLine: metaLine(row),
            rating,
            score: rating.toFixed(1),
            content: row.content || '내용 없음',
            createdAt: row.createdAt,
          };
        });
    },
    /* 수료했는데 아직 수강평을 안 쓴 강좌 — 나의 강좌 목록(수료)과 내 수강평으로 고른다(요약 응답엔 수만 있다) */
    writableCourses() {
      const reviewed = new Set(this.reviews.map((review) => review.courseId));
      return this.courses
        .filter((row) => row.status === 'COMPLETED' && row.courseId && !reviewed.has(row.courseId))
        .map((row) => ({ courseId: row.courseId, title: row.title || row.courseTitle || '-' }));
    },
  },
  watch: {
    // 뒤로/앞으로·새로고침·링크 공유 → 탭 복원. 탭 클릭 시의 push는 selectTab이 한다
    '$route.query.tab'(value) {
      this.tab = this.resolveTab(value);
    },
  },
  created() {
    // ?tab=zzz 같은 값이 주소창에 남아 공유·북마크되지 않게 정규화한다 (교정은 히스토리에 쌓지 않는다)
    const requested = this.$route.query.tab;
    if (requested !== undefined && requested !== this.tab) {
      this.$router.replace({ query: { ...this.$route.query, tab: this.tab } });
    }
    this.loadAll();
  },
  methods: {
    resolveTab(value) {
      return TABS.some((item) => item.key === value) ? value : DEFAULT_TAB;
    },
    selectTab(label) {
      const found = TABS.find((item) => item.label === label);
      if (!found) return;
      this.tab = found.key;
      // 탭마다 검색 대상이 달라 검색어를 들고 넘어가면 결과가 0이 되어 혼란스럽다 — 비운다
      const hadQuery = !!this.query;
      this.searchText = '';
      this.query = '';
      // 사용자가 누른 전환만 히스토리에 쌓는다 (뒤로가기가 /learning 안에서 돌게)
      if (this.$route.query.tab !== found.key) {
        this.$router.push({ query: { ...this.$route.query, tab: found.key } });
      }
      // 검색 중이 아니었으면 이미 전체 목록을 들고 있다 — 탭 전환만으로 5콜을 다시 쏘지 않는다
      return hadQuery ? this.loadAll() : undefined;
    },
    setChip(value) {
      if (this.tab === 'courses') this.courseFilter = value;
      else if (this.tab === 'inquiries') this.inquiryFilter = value;
      else if (this.tab === 'reviews') this.reviewFilter = value;
    },
    applySearch() {
      this.query = (this.searchText || '').trim();
      return this.loadAll();
    },
    clearSearch() {
      this.searchText = '';
      this.query = '';
      return this.loadAll();
    },
    openCourse(courseId) {
      if (courseId) this.$router.push(`/courses/${courseId}`);
    },
    /* 나의 강의 행은 내 수강 상세(진도·출결)로 간다. enrollmentId 가 없으면 강좌 페이지로 대신 보낸다 */
    openEnrollment(row) {
      if (row?.enrollmentId) {
        this.$router.push(`/enrollment/${row.enrollmentId}`);
        return;
      }
      this.openCourse(row?.courseId);
    },
    async loadAll() {
      this.loading = true;
      this.errorMessage = '';
      const q = this.query || undefined;
      // 하나가 실패해도 나머지는 그린다 — 실패를 null 로 삼키면 "없습니다"로 보여 오해를 산다
      const keys = ['courses', 'interests', 'inquiries', 'reviews', 'summary'];
      const results = await Promise.allSettled([
        listLearningCourses({ page: 0, size: 100, q }),
        listMyInterests({ page: 0, size: 100, q }),
        listMyInquiries({ page: 0, size: 100, q }),
        listMyReviews({ page: 0, size: 100, q }),
        getMyReviewSummary(),
      ]);
      const byKey = Object.fromEntries(keys.map((key, index) => [key, results[index]]));
      const listOf = (key) => {
        const result = byKey[key];
        const rows = result.status === 'fulfilled' ? result.value?.data : null;
        return Array.isArray(rows) ? rows : [];
      };
      this.courses = listOf('courses');
      this.interests = listOf('interests');
      this.inquiries = listOf('inquiries');
      this.reviews = listOf('reviews');
      this.reviewSummary = byKey.summary.status === 'fulfilled' ? byKey.summary.value : null;
      this.failedLists = Object.fromEntries(
        keys.map((key) => [key, byKey[key].status === 'rejected']),
      );
      this.errorMessage = this.failureMessage(keys.filter((key) => this.failedLists[key]), byKey);
      this.loading = false;
    },
    failureMessage(failedKeys, byKey) {
      if (!failedKeys.length) return '';
      const names = failedKeys.map((key) => LIST_LABELS[key]).join(', ');
      const reason = byKey[failedKeys[0]].reason?.message;
      return `${names} 목록을 불러오지 못했습니다.${reason ? ` (${reason})` : ''}`;
    },
    /** 관심 강좌가 나의 강좌에도 있으면 — 수강 상태(수료 여부)와 상세로 갈 enrollmentId */
    myCourseOf(courseId) {
      const mine = this.courses.find((course) => course.courseId === courseId);
      return mine
        ? { enrolled: true, enrollmentId: mine.enrollmentId || '', enrolledStatus: mine.status || '' }
        : { enrolled: false, enrollmentId: '', enrolledStatus: '' };
    },
    async restoreInterest(removed) {
      if (!removed?.courseId) return;
      try {
        const created = await addInterest(removed.courseId);
        // 새 관심 id 로 같은 자리에 되살린다 — 화면 정보는 지운 행 것을 그대로 쓴다
        this.interests = [{ ...removed, id: created?.id || removed.id }, ...this.interests];
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error?.message || '관심 강좌에 다시 담지 못했습니다.' });
      }
    },
    async dropInterest(row) {
      if (!row.interestId || this.removingId) return;
      this.removingId = row.interestId;
      try {
        await removeInterest(row.interestId);
        const removed = this.interests.find((item) => item.id === row.interestId);
        this.interests = this.interests.filter((item) => item.id !== row.interestId);
        // 누르는 즉시 사라져 되돌릴 길이 없었다 — 다시 담기
        this.$q.notify({
          type: 'info',
          message: '관심 강좌에서 뺐습니다.',
          actions: [{ label: '되돌리기', color: 'white', handler: () => this.restoreInterest(removed) }],
        });
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error?.message || '관심 해제에 실패했습니다.',
        });
      } finally {
        this.removingId = '';
      }
    },
  },
};
</script>

<style scoped>
.learning-page {
  position: relative;
  padding: 24px 0;
  background: var(--db-bg-page);
}

.learning-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.learning-header {
  display: flex;
  align-items: center;
  height: 51px;
  /* 왼쪽 선을 아래 탭과 맞춘다 — 좌우 여백이 있으면 제목만 안쪽으로 들어갔다 */
  padding: 10px 0;
}

.learning-header__title {
  margin: 0;
  font-size: 26px;
  line-height: 31px;
  font-weight: 700;
  color: var(--db-text-body);
}

/* 검색 — 모든 탭이 같은 칸(칸 안 돋보기). 예전 밝은 파랑 [검색] 버튼·수강평 전용 48px 칸은 걷어냈다 */
.learning-search {
  padding: 8px 0;
}

.learning-search__input :deep(.q-field__control) {
  height: 40px;
  min-height: 40px;
  border-radius: 6px;
}

.learning-search__input :deep(.q-field__marginal) {
  height: 40px;
}

/* 필터 칩 (나의 강의) */
/* 칩 — 폰에선 줄바꿈 대신 옆으로 밀어 본다(수강평 별점 칩 6개가 페이지를 954px 로 늘렸다) */
.learning-chips {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 60px;
  padding: 10px 0;
  overflow-x: auto;
  scrollbar-width: none;
}

/* 나의 강의·나의 문의·나의 수강평 3탭 공통 규격 (52 결정 — Figma 3종은 복붙 불일치) */
.learning-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 14px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--db-text-sub);
  white-space: nowrap;
  cursor: pointer;
}

/* 활성 배경은 토큰 유지 — Figma #eff6ff는 Tailwind 기본색이라 5단계 때 치환한 결정 */
.learning-chip.active {
  background: var(--db-accent-soft);
  border-color: var(--db-accent);
  color: var(--db-accent);
  font-weight: 500;
}

.learning-chip__count {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 9999px;
  background: var(--db-bg-section);
  font-size: 13px;
  font-weight: 600;
  color: var(--db-text-sub);
}

/* 목록 */
.learning-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.learning-sort__select {
  width: 120px;
}

.learning-sort__select :deep(.q-field__control) {
  height: 36px;
  min-height: 36px;
  padding: 0 8px;
  border-radius: 6px;
}

/* Quasar outlined 기본 테두리는 rgba(0,0,0,.24) */
.learning-sort__select :deep(.q-field__control):before {
  border-color: var(--db-border-light);
}

/* 문의·수강평 공용 패널 */
.learning-panel {
  background: var(--db-bg-white);
  /* 행과 같은 이유로 outline — border면 안쪽이 1198.4라 6칼럼 합 1152가 1.6px 넘친다 */
  outline: 1px solid var(--db-border);
  outline-offset: -1px;
  border-radius: 8px;
}

</style>
