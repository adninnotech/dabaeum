<template>
  <!-- 강좌 찾기 '대구 다른 기관 강좌' 탭 — 검색어는 다배움 탭과 같이 쓰고, 요일·시간대·접수 중·무료·구·군·정렬은 이 탭 것.
       교육 대상·수업 방식은 값이 한쪽에 몰려(지역주민 421/492, 오프라인 491/492) 거르는 의미가 없어 두지 않는다 -->
  <section class="pc-browser" aria-label="대구 다른 기관 강좌">
    <header class="pc-browser__bar">
      <q-input
        :model-value="query"
        outlined
        dense
        clearable
        placeholder="강좌명, 운영기관 검색"
        class="pc-browser__search"
        :maxlength="QUERY_MAX"
        @update:model-value="$emit('update:query', $event || '')"
        @keyup="onKeyup"
        @clear="$emit('submit')"
      >
        <template #append>
          <q-btn flat round dense icon="search" aria-label="검색" @click="$emit('submit')" />
        </template>
      </q-input>
      <q-select
        :model-value="sort"
        outlined
        dense
        emit-value
        map-options
        :options="SORT_OPTIONS"
        class="pc-browser__sort"
        aria-label="정렬"
        @update:model-value="setFilter('psort', $event)"
      />
      <q-btn
        v-if="$q.screen.xs"
        outline
        no-caps
        color="primary"
        icon="tune"
        class="pc-browser__filter-btn"
        :label="filterCount ? `필터 ${filterCount}` : '필터'"
        :aria-label="filterCount ? `필터, ${filterCount}개 적용됨` : '필터'"
        @click="filterOpen = true"
      />
    </header>

    <PublicCourseFilters
      v-if="!$q.screen.xs"
      :days="days"
      :time-of-day="timeOfDay"
      :receipt-open="receiptOpen"
      :free-only="freeOnly"
      @toggle-day="toggleDay"
      @set="setFilter"
    />

    <!-- 폰: 요일·시간대·접수 중·무료를 [필터] 창으로 — 펼쳐 두면 첫 화면 300px 를 차지해 카드가 안 보였다 -->
    <!-- no-route-dismiss: 조건은 주소에 남겨서 고를 때마다 주소가 바뀐다 — 기본값이면 그때마다 창이 닫혔다 -->
    <q-dialog v-model="filterOpen" position="bottom" no-route-dismiss>
      <q-card class="pc-browser__sheet">
        <header class="pc-browser__sheet-head">
          <h2 class="pc-browser__sheet-title">필터</h2>
          <q-btn v-if="filterCount" flat dense no-caps color="primary" label="초기화" @click="clearSheetFilters" />
          <q-btn v-close-popup flat round dense icon="close" aria-label="닫기" />
        </header>
        <PublicCourseFilters
          stacked
          :days="days"
          :time-of-day="timeOfDay"
          :receipt-open="receiptOpen"
          :free-only="freeOnly"
          @toggle-day="toggleDay"
          @set="setFilter"
        />
        <q-btn
          v-close-popup
          unelevated
          no-caps
          color="primary"
          class="pc-browser__sheet-apply"
          :loading="loading"
          :label="`강좌 ${total}건 보기`"
        />
      </q-card>
    </q-dialog>

    <!-- 구·군 — 건수가 0이거나 아직 모르는 곳은 숨긴다(고른 곳은 남겨 풀 수 있게) -->
    <div class="pc-browser__regions" role="group" aria-label="구·군">
      <button
        v-for="chip in regionChips"
        :key="chip.value"
        type="button"
        class="pc-browser__chip"
        :class="{ 'pc-browser__chip--on': chip.value === region }"
        :aria-pressed="chip.value === region ? 'true' : 'false'"
        @click="setFilter('region', chip.value)"
      >
        {{ chip.label }}<span v-if="chip.count !== null" class="pc-browser__chip-count">{{ chip.count }}</span>
      </button>
    </div>

    <div class="pc-browser__summary">
      <!-- 위 범위 전환·'전체' 칩에 이미 전체 수가 있다 — 좁혔을 때만 보인다 -->
      <span v-if="hasFilters" aria-live="polite">강좌 <strong>{{ loading && !rows.length ? '-' : total }}</strong>건</span>
      <span class="pc-browser__note">신청은 각 운영기관 홈페이지에서 합니다</span>
    </div>

    <AppErrorBanner v-if="errorMessage" :message="errorMessage">
      <template #action>
        <q-btn flat dense no-caps color="negative" label="다시 시도" @click="load" />
      </template>
    </AppErrorBanner>
    <AppEmptyState v-else-if="!loading && !rows.length" :description="emptyText">
      <template v-if="hasFilters" #action>
        <q-btn unelevated no-caps color="primary" label="조건 초기화" @click="resetFilters" />
      </template>
    </AppEmptyState>
    <div v-else class="pc-browser__grid" :class="{ 'pc-browser__grid--loading': loading }">
      <PublicCourseCard v-for="course in rows" :key="course.id" :course="course" />
    </div>

    <button v-if="hasMore" type="button" class="pc-browser__more" :disabled="loadingMore" @click="loadMore">
      {{ loadingMore ? '불러오는 중…' : '더보기' }}
      <span class="pc-browser__more-count">{{ rows.length }}/{{ total }}</span>
      <q-icon name="expand_more" size="24px" />
    </button>
  </section>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import PublicCourseCard from '@/components/public-course/PublicCourseCard.vue';
import PublicCourseFilters from '@/components/public-course/PublicCourseFilters.vue';
import { getPublicCourseFacets, listPublicCourses } from '@/services/public-course-api';
import { PUBLIC_OPER_DAYS, PUBLIC_REGION_LABEL, PUBLIC_TIME_OPTIONS, parseOperDays } from '@/utils/public-course';

// 웹은 세 줄 카드 8줄, 폰은 한 줄 12장 — 12건씩이면 웹에서 492건에 41번을 눌러야 했다
const PAGE_SIZE = 24;
const PAGE_SIZE_PHONE = 12;
// 서버 q 최대 길이 — 넘기면 422
const QUERY_MAX = 100;

// '접수 중 먼저' = 서버 receiptStatus: 접수 중(마감 임박 순) → 접수 예정 → 접수 기간 모름 → 접수 마감.
// 예전 receiptEndDate,desc 는 마감된 강좌가 모름보다 앞에 와 첫 화면 12장 중 7장이 '접수 마감'이었다
const SORT_OPTIONS = [
  { label: '접수 중 먼저', value: 'receipt', api: 'receiptStatus,asc' },
  { label: '개강일 순', value: 'start', api: 'edcStartDate,asc' },
  { label: '이름순', value: 'name', api: 'lectureName,asc' },
];
const DEFAULT_SORT = SORT_OPTIONS[0].value;
const REGION_CODES = Object.keys(PUBLIC_REGION_LABEL);
// 칩 순서는 고정 — 건수 순으로 두면 '무료만'·검색어를 바꿀 때마다 칩이 자리를 옮겨 고른 칩을 놓쳤다.
// 강좌가 많은 순(2026-10 수집 기준), 나머지는 코드 순
const REGION_ORDER = ['26', '29', '14', '11'];
const REGION_CHIP_CODES = [...REGION_ORDER, ...REGION_CODES.filter((code) => !REGION_ORDER.includes(code))];

export default {
  name: 'PublicCourseBrowser',
  components: { AppEmptyState, AppErrorBanner, PublicCourseCard, PublicCourseFilters },
  props: {
    /** 입력 중인 검색어 — 강좌 찾기 페이지와 같이 쓴다 */
    query: { type: String, default: '' },
    /** 실제로 반영한 검색어 */
    appliedQuery: { type: String, default: '' },
  },
  emits: ['update:query', 'submit'],
  data() {
    return {
      QUERY_MAX,
      SORT_OPTIONS,
      filterOpen: false,
      rows: [],
      total: 0,
      page: 0,
      regionCounts: {},
      allCount: null,
      loading: false,
      loadingMore: false,
      errorMessage: '',
      loadSeq: 0,
      countSeq: 0,
    };
  },
  computed: {
    // 구·군·무료·정렬은 주소에 둔다 — 상세에서 뒤로 오면 같은 목록
    region() {
      const value = this.$route.query.region;
      return REGION_CODES.includes(value) ? value : '';
    },
    freeOnly() {
      return this.$route.query.free === '1';
    },
    receiptOpen() {
      return this.$route.query.open === '1';
    },
    days() {
      return parseOperDays(this.$route.query.days);
    },
    timeOfDay() {
      const value = this.$route.query.time;
      return PUBLIC_TIME_OPTIONS.some((item) => item.value === value) ? value : '';
    },
    sort() {
      const value = this.$route.query.psort;
      return SORT_OPTIONS.some((item) => item.value === value) ? value : DEFAULT_SORT;
    },
    q() {
      return this.appliedQuery.trim().slice(0, QUERY_MAX) || undefined;
    },
    baseParams() {
      return {
        q: this.q,
        free: this.freeOnly ? true : undefined,
        receiptOpen: this.receiptOpen ? true : undefined,
        operDay: this.days.length ? this.days.join(',') : undefined,
        timeOfDay: this.timeOfDay || undefined,
      };
    },
    listParams() {
      const option = SORT_OPTIONS.find((item) => item.value === this.sort);
      return {
        ...this.baseParams,
        regionCode: this.region || undefined,
        sort: option.api,
      };
    },
    regionChips() {
      const regions = REGION_CHIP_CODES.map((code) => ({
        value: code,
        label: PUBLIC_REGION_LABEL[code],
        count: this.regionCounts[code] ?? null,
      }))
        .filter((chip) => chip.value === this.region || chip.count > 0);
      return [{ value: '', label: '전체', count: this.allCount }, ...regions];
    },
    pageSize() {
      return this.$q.screen.xs ? PAGE_SIZE_PHONE : PAGE_SIZE;
    },
    /** [필터] 창 안 조건 수 — 요일은 하나로 센다 */
    filterCount() {
      return [this.days.length, this.timeOfDay, this.receiptOpen, this.freeOnly].filter(Boolean).length;
    },
    hasFilters() {
      return Boolean(this.q || this.region || this.freeOnly || this.receiptOpen || this.days.length || this.timeOfDay);
    },
    hasMore() {
      return !this.errorMessage && this.rows.length < this.total;
    },
    emptyText() {
      return this.q ? `'${this.q}'에 맞는 대구 다른 기관 강좌가 없습니다.` : '조건에 맞는 대구 다른 기관 강좌가 없습니다.';
    },
  },
  watch: {
    listParams: 'load',
    baseParams: 'loadCounts',
  },
  created() {
    this.load();
    this.loadCounts();
  },
  methods: {
    onKeyup(event) {
      if (event.key === 'Enter') this.$emit('submit');
    },
    setFilter(key, value) {
      const next = { ...this.$route.query, [key]: value || undefined };
      this.$router.replace({ query: next }).catch((error) => {
        console.warn('[PublicCourseBrowser] 조건을 주소에 남기지 못했습니다', error);
      });
    },
    toggleDay(day) {
      const next = this.days.includes(day) ? this.days.filter((item) => item !== day) : [...this.days, day];
      // 월→일 순으로 남겨 주소가 고른 순서마다 달라지지 않게
      this.setFilter('days', PUBLIC_OPER_DAYS.filter((item) => next.includes(item)).join(','));
    },
    clearSheetFilters() {
      const next = { ...this.$route.query, days: undefined, time: undefined, open: undefined, free: undefined };
      this.$router.replace({ query: next }).catch((error) => {
        console.warn('[PublicCourseBrowser] 조건을 주소에 남기지 못했습니다', error);
      });
    },
    resetFilters() {
      const next = { ...this.$route.query, region: undefined, free: undefined, open: undefined, days: undefined, time: undefined, q: undefined };
      this.$emit('update:query', '');
      this.$router.replace({ query: next }).then(() => this.$emit('submit'));
    },
    async fetchPage(page) {
      return listPublicCourses({ ...this.listParams, page, size: this.pageSize });
    },
    async load() {
      const seq = ++this.loadSeq;
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await this.fetchPage(0);
        if (seq !== this.loadSeq) return;
        this.rows = result.data;
        this.total = result.page?.totalElements ?? result.data.length;
        this.page = 0;
      } catch (error) {
        if (seq !== this.loadSeq) return;
        this.rows = [];
        this.total = 0;
        this.errorMessage = error?.message || '대구 다른 기관 강좌를 불러오지 못했습니다.';
      } finally {
        if (seq === this.loadSeq) this.loading = false;
      }
    },
    async loadMore() {
      const seq = this.loadSeq;
      this.loadingMore = true;
      try {
        const result = await this.fetchPage(this.page + 1);
        if (seq !== this.loadSeq) return;
        // 그사이 새로 받은 데이터로 같은 강좌가 겹칠 수 있다
        const seen = new Set(this.rows.map((row) => row.id));
        this.rows = [...this.rows, ...result.data.filter((row) => !seen.has(row.id))];
        this.page += 1;
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error?.message || '더 불러오지 못했습니다.', position: 'top' });
      } finally {
        this.loadingMore = false;
      }
    },
    /** 구·군별 건수 — 같은 조건으로 facets 한 번(구·군 조건은 빼고 센다). 실패하면 건수 없이 칩을 숨긴다 */
    async loadCounts() {
      const seq = ++this.countSeq;
      try {
        const facets = await getPublicCourseFacets(this.baseParams);
        if (seq !== this.countSeq) return;
        this.allCount = facets?.total ?? null;
        this.regionCounts = Object.fromEntries((facets?.regions || []).map((item) => [item.regionCode, item.count]));
      } catch (error) {
        if (seq !== this.countSeq) return;
        console.warn('[PublicCourseBrowser] 구·군별 건수를 불러오지 못했습니다', error);
        this.allCount = null;
        this.regionCounts = {};
      }
    },
  },
};
</script>

<style scoped>
.pc-browser {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 24px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.pc-browser__bar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pc-browser__search {
  flex: 1;
  min-width: 0;
}

.pc-browser__sort {
  width: 150px;
}

.pc-browser__filter-btn {
  flex-shrink: 0;
  height: 40px;
}

.pc-browser__sheet {
  display: flex;
  flex-direction: column;
  gap: 20px;
  width: 100%;
  padding: 16px 16px calc(16px + env(safe-area-inset-bottom));
  border-radius: 16px 16px 0 0;
}

.pc-browser__sheet-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pc-browser__sheet-title {
  flex: 1;
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--db-text-title);
}

.pc-browser__sheet-apply {
  height: 48px;
  font-size: 16px;
}

.pc-browser__regions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pc-browser__chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 16px;
  border: 1px solid var(--db-border);
  border-radius: 20px;
  background: var(--db-bg-white);
  font: inherit;
  font-size: 14px;
  color: var(--db-text-body);
  cursor: pointer;
  white-space: nowrap;
}

.pc-browser__chip:hover {
  border-color: var(--db-accent);
}

.pc-browser__chip:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.pc-browser__chip--on {
  border-color: var(--db-accent);
  background: var(--db-accent-soft);
  color: var(--db-accent);
  font-weight: 700;
}

.pc-browser__chip-count {
  font-size: 13px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.pc-browser__chip--on .pc-browser__chip-count {
  color: var(--db-accent);
}

.pc-browser__summary {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 4px 16px;
  font-size: 14px;
  color: var(--db-text-body);
}

.pc-browser__summary strong {
  color: var(--db-accent);
}

.pc-browser__note {
  font-size: 14px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.pc-browser__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  transition: opacity 0.15s;
}

.pc-browser__grid--loading {
  opacity: 0.5;
}

.pc-browser__more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 48px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
  background: var(--db-bg-white);
  font: inherit;
  font-size: 15px;
  color: var(--db-text-body);
  cursor: pointer;
}

.pc-browser__more:disabled {
  cursor: default;
  opacity: 0.6;
}

.pc-browser__more-count {
  font-size: 14px;
  color: var(--db-text-sub);
}

@media (max-width: 1023px) {
  .pc-browser__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

/* 폰: 검색 한 줄, 정렬·무료만 한 줄. 구·군 칩은 가로로 밀어 본다(여러 줄로 쌓이면 목록이 밀린다) */
@media (max-width: 599px) {
  .pc-browser {
    padding: 0;
    border: 0;
    background: none;
  }

  .pc-browser__bar {
    flex-wrap: wrap;
  }

  .pc-browser__search {
    flex: 1 1 100%;
  }

  .pc-browser__sort {
    flex: 1;
    width: auto;
  }


  .pc-browser__regions {
    flex-wrap: nowrap;
    overflow-x: auto;
    margin: 0 -16px;
    padding: 0 16px 2px;
    scrollbar-width: none;
  }

  .pc-browser__regions::-webkit-scrollbar {
    display: none;
  }

  .pc-browser__grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 8px;
  }
}
</style>
