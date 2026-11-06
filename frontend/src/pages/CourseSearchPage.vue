<template>
  <q-page class="search-page">
    <div class="learner-container">
      <header class="search-page__header">
        <h1 class="search-page__title">강좌 찾기</h1>
        <router-link to="/courses/popular" class="search-page__popular">
          <q-icon name="o_local_fire_department" size="18px" />인기 강좌
        </router-link>
      </header>

      <!-- 범위 — 다배움 강좌 / 대구 다른 기관 강좌(공공데이터). 예전엔 공공 강좌가 목록 맨 아래(폰 2,000px 밑)에 붙어
           있는 줄도 몰랐고 거를 방법도 없었다. 검색어는 두 범위가 같이 쓰고 건수를 나란히 보인다 -->
      <div class="search-scope" role="tablist" aria-label="강좌 범위">
        <button
          v-for="item in scopeItems"
          :key="item.value"
          type="button"
          role="tab"
          class="search-scope__item"
          :class="{ 'search-scope__item--on': scope === item.value }"
          :aria-selected="scope === item.value ? 'true' : 'false'"
          @click="setScope(item.value)"
        >
          {{ item.label }}<span class="search-scope__count">{{ item.count ?? '-' }}</span>
        </button>
      </div>

      <PublicCourseBrowser
        v-if="scope === PUBLIC_SCOPE"
        v-model:query="query"
        :applied-query="appliedQuery"
        @submit="onSearchSubmit"
      />

      <template v-else>
      <!-- 탭 = 표준 강좌 분류 대분류(설계서 §12). 중·소분류는 오른쪽 "세부 분류"로 좁힌다 -->
      <LearnerTabs :tabs="tabs" :model-value="activeTab" @update:model-value="selectTab" />

      <section class="search-main">
        <header class="search-main__header">
          <q-input
            v-model="query"
            outlined
            dense
            clearable
            placeholder="강좌명, 기관명, 강사명 검색"
            class="search-input"
            :class="{ 'search-input--shrink': subClassificationOptions.length }"
            @keyup.enter="onSearchSubmit"
            @clear="onSearchSubmit"
          >
            <!-- 정렬·수업 방식은 고르면 바로 걸리는데 검색어만 Enter 를 알아야 했다(폰은 더) -->
            <template #append>
              <q-btn flat round dense icon="search" aria-label="검색" @click="onSearchSubmit" />
            </template>
          </q-input>
          <q-select
            v-model="sort"
            outlined
            dense
            emit-value
            map-options
            :options="SORT_OPTIONS"
            class="search-select"
          />
          <q-select
            v-model="educationType"
            outlined
            dense
            emit-value
            map-options
            clearable
            label="수업 방식"
            :options="EDUCATION_TYPE_OPTIONS_ALL"
            class="search-select"
          />
          <q-select
            v-if="subClassificationOptions.length"
            v-model="subClassification"
            outlined
            dense
            emit-value
            map-options
            clearable
            label="세부 분류"
            :options="subClassificationOptions"
            class="search-select search-select--wide"
          />
        </header>

        <AppErrorBanner v-if="errorMessage" :message="errorMessage" />

        <!-- 몇 개가 나왔는지, 지난 강좌를 섞어 볼지 — 예전엔 수도, 신청 가능 여부도 안 보였다 -->
        <div class="search-summary">
          <span class="search-summary__count" aria-live="polite">
            강좌 <strong>{{ loading ? '-' : filtered.length }}</strong>개
          </span>
          <q-toggle
            :model-value="recruitingOnly"
            dense
            label="모집중만"
            class="search-summary__toggle"
            @update:model-value="setRecruitingOnly"
          />
        </div>

        <AppEmptyState v-if="!loading && !filtered.length" :description="emptyDescription">
          <template v-if="hiddenClosedCount || hasConditions || publicTotal" #action>
            <q-btn
              v-if="hiddenClosedCount"
              outline
              no-caps
              color="primary"
              :label="`모집 끝난 강좌 ${hiddenClosedCount}개 보기`"
              @click="setRecruitingOnly(false)"
            />
            <q-btn v-if="hasConditions" unelevated no-caps color="primary" label="조건 초기화" @click="resetConditions" />
            <!-- 다배움엔 없어도 다른 기관 강좌가 있으면 바로 넘어가게 -->
            <q-btn
              v-if="publicTotal"
              outline
              no-caps
              color="primary"
              :label="`대구 다른 기관 강좌 ${publicTotal}건 보기`"
              @click="setScope(PUBLIC_SCOPE)"
            />
          </template>
        </AppEmptyState>
        <div v-else class="search-grid" :class="{ 'search-grid--loading': loading }">
          <CourseCard
            v-for="course in visible"
            :key="course.id"
            :course="course"
            :interested="isInterested(course.id)"
            fluid
            @toggle-interest="toggleInterestFor"
          />
        </div>

        <button v-if="hasMore" type="button" class="search-more" @click="showMore">
          더보기 <span class="search-more__count">{{ visible.length }}/{{ filtered.length }}</span>
          <q-icon name="expand_more" size="24px" />
        </button>
      </section>
      </template>
    </div>

    <!-- 첫 로딩만 가린다 — 조건을 바꿀 땐 목록만 흐리게(.search-grid--loading) -->
    <q-inner-loading :showing="scope !== PUBLIC_SCOPE && loading && !courses.length">
      <q-spinner color="primary" size="32px" />
    </q-inner-loading>
  </q-page>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import CourseCard from '@/components/course/CourseCard.vue';
import LearnerTabs from '@/components/LearnerTabs.vue';
import PublicCourseBrowser from '@/components/public-course/PublicCourseBrowser.vue';
import courseInterest from '@/mixins/course-interest';
import { listCourseClassifications } from '@/services/classification-api';
import { listCourses, listPopularCourses } from '@/services/course-api';
import { listPublicCourses } from '@/services/public-course-api';
import { decorateCourseRows } from '@/services/course-rows';
import { isLearnerVisibleCourse, isRecruitOpen } from '@/utils/course-fields';
import { daysFromToday } from '@/utils/datetime';
import { EDUCATION_TYPE_OPTIONS_ALL } from '@/utils/status';

const ALL_TAB = '전체';
const PUBLIC_SCOPE = 'public';
// 공공 강좌 탭이 주소에 남기는 조건 — 이 페이지의 syncQuery 가 지우지 않게
const PUBLIC_QUERY_KEYS = ['region', 'free', 'psort', 'open', 'days', 'time'];
// 공공 강좌 검색어 최대 길이(서버 422)
const PUBLIC_QUERY_MAX = 100;
const PAGE_SIZE = 12;

const SORT_OPTIONS = [
  { label: '최신순', value: 'latest' },
  { label: '모집마감임박순', value: 'deadline' },
  { label: '인기순', value: 'popular' },
];

// 인기 API 최대치 — 이 안에 없는 강좌는 점수 0으로 최신순 뒤에 붙는다
const POPULAR_LIMIT = 50;

// 목록 조회 최대치(load 의 size) — URL 의 more 가 이보다 크면 의미가 없다
const MAX_VISIBLE = 100;

/** URL 쿼리 한 값을 문자열로 — 배열·빈 값은 없는 것으로 본다 */
function queryText(value) {
  return typeof value === 'string' && value.trim() ? value.trim() : '';
}

/** 뒤로가기·"목록"으로 돌아왔을 때 보던 조건을 되살리려고 URL 쿼리를 읽는다. 모르는 값은 기본값으로 */
function filtersFromQuery(query) {
  const sort = queryText(query.sort);
  const type = queryText(query.type);
  const more = Number.parseInt(queryText(query.more), 10);
  return {
    query: queryText(query.q),
    activeTab: queryText(query.tab) || ALL_TAB,
    sort: SORT_OPTIONS.some((item) => item.value === sort) ? sort : 'latest',
    educationType: EDUCATION_TYPE_OPTIONS_ALL.some((item) => item.value === type) ? type : null,
    subClassification: queryText(query.sub) || null,
    // 기본은 신청할 수 있는 강좌만 — ?all=1 이면 마감·진행중·종료도
    recruitingOnly: queryText(query.all) !== '1',
    visibleCount: Number.isFinite(more) && more > PAGE_SIZE ? Math.min(more, MAX_VISIBLE) : PAGE_SIZE,
    scope: queryText(query.scope) === PUBLIC_SCOPE ? PUBLIC_SCOPE : '',
  };
}

function byLatest(a, b) {
  return String(b.createdAt || '').localeCompare(a.createdAt || '');
}

export default {
  name: 'CourseSearchPage',
  components: { AppEmptyState, AppErrorBanner, CourseCard, LearnerTabs, PublicCourseBrowser },
  mixins: [courseInterest],
  data() {
    // created 가 아니라 여기서 채워야 educationType·subClassification watcher 가 재조회·더보기 초기화를 하지 않는다
    const initial = filtersFromQuery(this.$route.query);
    return {
      loading: false,
      errorMessage: '',
      courses: [],
      popularityScores: null,
      query: initial.query,
      // 지금 목록이 실제로 반영한 검색어 — 입력 중인 글자를 URL 에 싣지 않으려고 따로 둔다
      appliedQuery: initial.query,
      sort: initial.sort,
      educationType: initial.educationType,
      activeTab: initial.activeTab,
      classificationTree: [],
      subClassification: initial.subClassification,
      recruitingOnly: initial.recruitingOnly,
      // 늦게 온 응답이 나중 조건의 결과를 덮지 않게
      loadSeq: 0,
      suppressWatch: false,
      visibleCount: initial.visibleCount,
      scope: initial.scope,
      // 대구 다른 기관 강좌 수(검색어만 반영) — 범위 전환에 건수로 보인다
      publicTotal: null,
      PUBLIC_SCOPE,
      PAGE_SIZE,
      SORT_OPTIONS,
      EDUCATION_TYPE_OPTIONS_ALL,
    };
  },
  computed: {
    // 분류 목록을 못 받으면 "전체" 탭만 남는다 — 강좌 목록은 그대로 보인다
    tabs() {
      return [ALL_TAB, ...this.classificationTree.map((node) => node.name)];
    },
    activeRoot() {
      return this.classificationTree.find((node) => node.name === this.activeTab) || null;
    },
    // 대분류 탭 아래 중분류·소분류. 소분류는 "중분류 > 소분류"로 적어 어느 갈래인지 보이게 한다
    subClassificationOptions() {
      return (this.activeRoot?.children || []).flatMap((middle) => [
        { label: middle.name, value: middle.code },
        ...(middle.children || []).map((leaf) => ({
          label: `${middle.name} > ${leaf.name}`,
          value: leaf.code,
        })),
      ]);
    },
    classificationFilter() {
      return this.subClassification || this.activeRoot?.code || undefined;
    },
    // 검색어(q)·수업 방식(educationType)·분류(classificationCode)는 서버가 이미 걸러 온다 — 다시 거르지 않는다.
    filtered() {
      // 비공개·취소 강좌는 학습자 목록에서 뺀다 (서버가 DRAFT 를 내려주는 문제 방어)
      return this.sortRows(
        // 상태만 보면 마감일이 지난 강좌(서버 상태 전환이 늦다)가 '모집중만'에 남았다
        this.visibleCourses.filter((course) => !this.recruitingOnly || isRecruitOpen(course)),
      );
    },
    visibleCourses() {
      return this.courses.filter((course) => isLearnerVisibleCourse(course));
    },
    // '모집중만' 때문에 가려진 수 — 0개일 때 [모집 끝난 강좌 n개 보기]
    hiddenClosedCount() {
      return this.recruitingOnly ? this.visibleCourses.length - this.filtered.length : 0;
    },
    hasConditions() {
      return Boolean(this.appliedQuery || this.educationType || this.subClassification || this.activeTab !== ALL_TAB);
    },
    emptyDescription() {
      if (this.hiddenClosedCount) return '지금 모집 중인 강좌가 없습니다.';
      if (this.appliedQuery) return `'${this.appliedQuery}'에 맞는 강좌가 없습니다.`;
      return '조건에 맞는 강좌가 없습니다.';
    },
    visible() {
      return this.filtered.slice(0, this.visibleCount);
    },
    hasMore() {
      return this.filtered.length > this.visibleCount;
    },
    scopeItems() {
      return [
        { value: '', label: '다배움 강좌', count: this.loading ? null : this.filtered.length },
        { value: PUBLIC_SCOPE, label: '대구 다른 기관 강좌', count: this.publicTotal },
      ];
    },
  },
  watch: {
    sort(value) {
      this.resetVisible();
      if (value === 'popular') this.loadPopularity();
      this.syncQuery();
    },
    educationType: 'onFilterChange',
    subClassification: 'onFilterChange',
    // 이 화면에 있는 채로 머리 전체 메뉴(분야)를 누르면 같은 컴포넌트에 주소만 바뀐다 — 주소를 다시 읽는다.
    // syncQuery 가 남긴 주소는 지금 조건과 같아 그냥 지나간다
    '$route.query'(query) {
      if (this.$route.path !== '/courses') return;
      const next = filtersFromQuery(query);
      const same =
        next.activeTab === this.activeTab &&
        next.subClassification === this.subClassification &&
        next.scope === this.scope &&
        next.query === this.appliedQuery;
      if (!same) this.applyRouteFilters(next);
    },
  },
  async created() {
    // 통합검색 "강좌 찾기에서 모두 보기"·기관 결과의 ?q= 와 이 화면이 남긴 필터는 data() 에서 이미 읽었다
    // 하트가 서버의 실제 관심 상태를 보여 주게 한다 (비로그인이면 빈 상태)
    this.loadMyInterests();
    if (this.sort === 'popular') this.loadPopularity();
    const classifications = this.loadClassifications();
    // 대분류 탭을 되살렸으면 분류 코드를 알아야 걸러 받을 수 있다 — 분류 목록을 기다린다
    if (this.activeTab !== ALL_TAB && !this.subClassification) await classifications;
    this.load();
  },
  methods: {
    /** 주소에서 읽은 조건으로 바꾸고 한 번만 다시 받는다(값마다 watcher 가 재조회하지 않게) */
    async applyRouteFilters(next) {
      this.suppressWatch = true;
      this.query = next.query;
      this.activeTab = next.activeTab;
      this.educationType = next.educationType;
      this.subClassification = next.subClassification;
      this.recruitingOnly = next.recruitingOnly;
      this.visibleCount = next.visibleCount;
      this.scope = next.scope;
      if (this.sort !== next.sort) this.sort = next.sort;
      await this.$nextTick();
      this.suppressWatch = false;
      if (this.activeTab !== ALL_TAB && !this.classificationTree.length) await this.loadClassifications();
      this.load();
    },
    setScope(value) {
      if (value === this.scope) return;
      this.scope = value;
      this.syncQuery();
    },
    /** 범위 전환 건수 — 실패하면 '-' 로 둔다(탭 안에서 오류를 보인다) */
    async loadPublicTotal(seq) {
      try {
        const result = await listPublicCourses({ q: this.appliedQuery.slice(0, PUBLIC_QUERY_MAX) || undefined, page: 0, size: 1 });
        if (seq === this.loadSeq) this.publicTotal = result.page?.totalElements ?? null;
      } catch {
        if (seq === this.loadSeq) this.publicTotal = null;
      }
    },
    setRecruitingOnly(value) {
      this.recruitingOnly = value;
      this.resetVisible();
      this.syncQuery();
    },
    /** 검색어·탭·수업 방식·세부 분류를 비우고 한 번만 다시 받는다 */
    resetConditions() {
      this.query = '';
      this.activeTab = ALL_TAB;
      // watcher 가 각각 재조회하지 않게 값만 바꾸고 아래에서 한 번 부른다
      this.suppressWatch = true;
      this.educationType = null;
      this.subClassification = null;
      this.$nextTick(() => {
        this.suppressWatch = false;
      });
      this.onSearchSubmit();
    },
    resetVisible() {
      this.visibleCount = PAGE_SIZE;
    },
    showMore() {
      this.visibleCount = Math.min(this.visibleCount + PAGE_SIZE, MAX_VISIBLE);
      this.syncQuery();
    },
    onFilterChange() {
      if (!this.suppressWatch) this.onSearchSubmit();
    },
    /** 검색어·수업 방식은 서버 파라미터라 값이 바뀌면 재조회한다(Enter·클리어·필터 변경 시) */
    onSearchSubmit() {
      this.resetVisible();
      this.load();
      this.syncQuery();
    },
    /**
     * 조건을 URL 에 남긴다 — 강좌 상세에서 뒤로 오면 같은 탭·검색·정렬·더보기 위치로 돌아온다.
     * push 가 아니라 replace: 필터를 바꿀 때마다 방문 기록이 쌓이면 뒤로가기가 필터만 되감는다
     */
    syncQuery() {
      const entries = [
        ['q', this.appliedQuery],
        ['tab', this.activeTab !== ALL_TAB ? this.activeTab : ''],
        ['sort', this.sort !== 'latest' ? this.sort : ''],
        ['type', this.educationType || ''],
        ['sub', this.subClassification || ''],
        ['all', this.recruitingOnly ? '' : '1'],
        ['more', this.visibleCount > PAGE_SIZE ? String(this.visibleCount) : ''],
        ['scope', this.scope],
        // 다배움 탭으로 갔다 와도 공공 강좌 조건이 남게 늘 싣는다
        ...PUBLIC_QUERY_KEYS.map((key) => [key, queryText(this.$route.query[key])]),
      ].filter(([, value]) => value);
      const next = Object.fromEntries(entries);
      const current = this.$route.query;
      const same =
        Object.keys(current).length === entries.length &&
        entries.every(([key, value]) => current[key] === value);
      if (same) return;
      // 라우터 scrollBehavior 가 늘 맨 위로 올린다 — 쿼리만 바꾼 것이니 보던 자리를 지킨다 (더보기 직후 등)
      const top = window.scrollY;
      this.$router
        .replace({ query: next })
        .then(() => this.$nextTick())
        .then(() => window.scrollTo({ top, left: 0 }))
        .catch((error) => {
          // 쿼리만 남기는 일이라 실패해도 목록은 그대로다 — 콘솔에만 남긴다
          console.warn('[CourseSearchPage] 검색 조건을 URL 에 남기지 못했습니다', error);
        });
    },
    selectTab(tab) {
      if (tab === this.activeTab) return;
      this.activeTab = tab;
      // 세부 분류가 바뀌면 watcher 가 다시 부른다 — 이미 비어 있으면 여기서 부른다
      if (this.subClassification) this.subClassification = null;
      else this.onSearchSubmit();
    },
    async loadClassifications() {
      try {
        this.classificationTree = await listCourseClassifications();
      } catch {
        this.classificationTree = [];
      }
    },
    sortRows(rows) {
      if (this.sort === 'popular') {
        const scores = this.popularityScores || {};
        return [...rows].sort((a, b) => (scores[b.id] || 0) - (scores[a.id] || 0) || byLatest(a, b));
      }
      if (this.sort !== 'deadline') return [...rows].sort(byLatest);
      // 마감이 가까운 순, 날짜 없는 건 뒤로
      return [...rows].sort((a, b) => {
        const left = daysFromToday(a.recruitEndDate);
        const right = daysFromToday(b.recruitEndDate);
        if (left === null) return 1;
        if (right === null) return -1;
        return left - right;
      });
    },
    /** 인기순을 처음 고를 때 한 번 받는다(매시 집계라 화면에 있는 동안 다시 받지 않는다). 실패하면 최신순으로 보인다 */
    async loadPopularity() {
      if (this.popularityScores) return;
      try {
        const list = await listPopularCourses({ limit: POPULAR_LIMIT });
        this.popularityScores = Object.fromEntries(
          list.filter((item) => item.course?.id).map((item) => [item.course.id, item.popularity?.score || 0]),
        );
      } catch {
        this.popularityScores = {};
        this.$q.notify({ type: 'warning', message: '인기 정보를 불러오지 못해 최신순으로 보여 드립니다.', position: 'top' });
      }
    },
    async load() {
      const seq = ++this.loadSeq;
      this.loading = true;
      this.errorMessage = '';
      this.appliedQuery = (this.query || '').trim();
      this.loadPublicTotal(seq);
      try {
        const result = await listCourses({
          page: 0,
          size: 100,
          q: this.query || undefined,
          educationType: this.educationType || undefined,
          classificationCode: this.classificationFilter,
        });
        const rows = await decorateCourseRows(Array.isArray(result.data) ? result.data : []);
        if (seq === this.loadSeq) this.courses = rows;
      } catch (error) {
        if (seq !== this.loadSeq) return;
        this.errorMessage = error?.message || '강좌를 불러오지 못했습니다.';
        this.courses = [];
      } finally {
        if (seq === this.loadSeq) this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
/* 범위 전환 — 분류 탭(밑줄)과 구분되게 알약 모양 둘 */
.search-scope {
  display: flex;
  gap: 8px;
  margin: 0 0 16px;
}

.search-scope__item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 18px;
  border: 1px solid var(--db-border);
  border-radius: 20px;
  background: var(--db-bg-white);
  font: inherit;
  font-size: 15px;
  font-weight: 500;
  color: var(--db-text-body);
  cursor: pointer;
  white-space: nowrap;
}

.search-scope__item:hover {
  border-color: var(--db-accent);
}

.search-scope__item:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.search-scope__item--on {
  border-color: var(--db-accent-fill);
  background: var(--db-accent-fill);
  color: var(--db-on-color);
  font-weight: 700;
}

.search-scope__count {
  font-size: 14px;
  font-weight: 500;
  opacity: 0.8;
}

@media (max-width: 599px) {
  .search-scope__item {
    flex: 1 1 0;
    min-width: 0;
    justify-content: center;
    padding: 0 10px;
    font-size: 14px;
  }
}

.search-page {
  position: relative;
  padding: 24px 0;
  background: var(--db-bg-page);
}

.search-page__header {
  display: flex;
  align-items: center;
  height: 64px;
  /* 아래 범위 전환·패널과 왼쪽 선을 맞춘다(예전 16px 들여 제목만 안쪽에 있었다) */
  padding: 12px 0;
}

.search-page__popular {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 40px;
  margin-left: auto;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  color: var(--db-accent);
  text-decoration: none;
}

.search-page__popular:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.search-page__title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--db-text-title);
}

.search-main {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 24px;
  padding: 10px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.search-main__header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  min-height: 64px;
  padding: 12px 16px;
}

.search-input {
  width: 697px;
  max-width: 100%;
}

/* 세부 분류가 붙으면 피그마 고정폭(697)을 풀어 한 줄에 둔다 */
.search-input--shrink {
  flex: 1 1 240px;
  width: auto;
}

.search-input :deep(.q-field__control) {
  height: 40px;
  min-height: 40px;
  border-radius: 6px;
}

.search-input :deep(.q-field__control):before {
  border-color: var(--db-border);
}

.search-select {
  width: 120px;
}

/* 세부 분류는 "중분류 > 소분류" 라벨이 길다 */
.search-select--wide {
  width: 240px;
}

.search-select :deep(.q-field__control) {
  height: 40px;
  min-height: 40px;
  border-radius: 6px;
}

/* 칸 폭을 채운다 — 예전엔 260px 고정 4칸을 가운데 놓아 검색줄보다 23px 안쪽에서 시작했다 */
.search-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  padding: 0 16px;
  transition: opacity 0.15s ease;
}

/* 다시 받는 동안 목록만 흐리게 — 화면 전체를 가리지 않는다 */
.search-grid--loading {
  opacity: 0.5;
  pointer-events: none;
}

.search-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 16px;
  font-size: 14px;
  color: var(--db-text-body);
}

.search-summary__count strong {
  font-weight: 700;
  color: var(--db-text-title);
  font-variant-numeric: tabular-nums;
}

.search-summary__toggle {
  font-size: 14px;
  color: var(--db-text-body);
}

.search-more__count {
  font-size: 14px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.search-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 16px 0;
  background: none;
  border: none;
  font-size: 16px;
  font-weight: 400;
  color: var(--db-accent);
  cursor: pointer;
}

@media (max-width: 1279px) {
  .search-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 899px) {
  .search-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (prefers-reduced-motion: reduce) {
  .search-grid {
    transition: none;
  }
}

/* 폰: 260 고정 카드가 왼쪽에 붙고 오른쪽이 비었다 — 1열로 폭을 채운다.
 * 제목·검색줄 여백도 탭(본문 16 선)과 맞춘다 */
@media (max-width: 599px) {
  .search-page__header {
    padding: 12px 0;
  }

  /* 흰 상자 안에 카드가 또 테두리를 갖고 있어 좌우 여백이 두 겹이었다 — 폰에선 바깥 상자를 걷는다 */
  .search-main {
    padding: 0;
    background: transparent;
    border: 0;
  }

  .search-main__header {
    padding: 8px 0;
  }

  .search-input {
    width: 100%;
  }

  .search-select {
    flex: 1 1 0;
    min-width: 0;
  }

  .search-select--wide {
    flex-basis: 100%;
  }

  .search-grid {
    grid-template-columns: minmax(0, 1fr);
    padding: 0;
  }

  .search-summary {
    padding: 0;
  }
}
</style>
