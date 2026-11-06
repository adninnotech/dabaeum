<template>
  <q-page class="search-all">
    <div class="learner-container search-all__body">
      <!-- 포털형 검색창: 가운데 크게. 검색 전엔 화면 위쪽 1/3 쯤에 두고, 결과가 나오면 위로 붙는다 -->
      <section class="search-hero" :class="{ 'search-hero--idle': !query }">
        <h1 v-if="!query" class="search-hero__title">무엇을 찾고 계세요?</h1>
        <PortalSearchBox v-model="draft" autofocus @submit="submit" />
        <p v-if="tooShort" class="search-hero__note">검색어를 {{ SEARCH_MIN_LENGTH }}자 이상 입력해 주세요.</p>
        <!-- 검색 전 화면이 비어 있었다 — 강좌 분류 대분류로 바로 간다 -->
        <nav v-if="!query && categories.length" class="search-cats" aria-label="강좌 분류 바로가기">
          <router-link
            v-for="name in categories"
            :key="name"
            :to="{ path: '/courses', query: { tab: name } }"
            class="search-cats__chip"
          >
            {{ name }}
          </router-link>
        </nav>
      </section>

      <template v-if="query && !tooShort">
        <h1 class="search-all__title">
          <span>"<span class="search-all__q">{{ query }}</span>" 검색 결과</span>
          <span class="search-all__total">{{ formatCount(grandTotal) }}건</span>
        </h1>

        <!-- 결과가 있는 종류만 탭으로 — '기관 0'·'공지사항 0' 같은 빈 탭이 같은 무게로 섰다 -->
        <LearnerTabs
          v-if="grandTotal || loading"
          :tabs="tabLabels"
          :model-value="activeLabel"
          @update:model-value="selectTab"
        />

        <AppErrorBanner v-if="errorMessage" :message="errorMessage" />
        <section v-else class="search-all__panel" :aria-busy="loading">
          <AppEmptyState v-if="!loading && !grandTotal" :description="`'${query}' 검색 결과가 없어요.`">
            <template #action>
              <q-btn outline no-caps color="primary" label="강좌 찾기" to="/courses" />
              <q-btn outline no-caps color="primary" label="인기 강좌" to="/courses/popular" />
            </template>
          </AppEmptyState>

          <!-- 전체: 종류마다 앞 몇 건씩 한 화면에 — 예전엔 탭을 하나씩 눌러야 다른 종류를 봤다 -->
          <template v-else-if="activeKey === 'all'">
            <section v-for="group in overviewGroups" :key="group.key" class="search-group">
              <header class="search-group__head">
                <h2 class="search-group__title">
                  {{ group.label }} <span class="search-group__count">{{ formatCount(group.total) }}</span>
                </h2>
                <button v-if="group.total > group.items.length" type="button" class="search-group__more" @click="activeKey = group.key">
                  {{ group.label }} 더보기
                </button>
              </header>
              <SearchResultList :kind="group.key" :items="group.items" :query="query" />
            </section>
          </template>

          <template v-else>
            <AppEmptyState
              v-if="!loading && !activeItems.length"
              :description="`'${query}'에 맞는 ${activeTab.label} 결과가 없어요.`"
            />
            <SearchResultList v-else :kind="activeKey" :items="activeItems" :query="query" />

            <div v-if="canLoadMore || showCourseLink" class="search-all__more">
              <q-btn
                v-if="canLoadMore"
                flat
                no-caps
                color="primary"
                icon-right="expand_more"
                :label="`더보기 (${formatCount(activeTotal - activeItems.length)}건 더)`"
                :loading="loading"
                @click="loadMore"
              />
              <router-link v-else :to="{ path: '/courses', query: { q: query } }" class="search-all__more-link">
                강좌 찾기에서 {{ formatCount(activeTotal) }}건 모두 보기
              </router-link>
            </div>
          </template>
        </section>
      </template>
    </div>
  </q-page>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import LearnerTabs from '@/components/LearnerTabs.vue';
import PortalSearchBox from '@/components/PortalSearchBox.vue';
import SearchResultList from '@/components/search/SearchResultList.vue';
import { listCourseClassifications } from '@/services/classification-api';
import {
  SEARCH_MAX_LENGTH,
  SEARCH_MAX_LIMIT,
  SEARCH_MIN_LENGTH,
  searchAll,
} from '@/services/search-api';
import { listFaqs } from '@/services/support-api';

const FIRST_LIMIT = 5;
// '전체' 요약에서 종류마다 보일 건수
const OVERVIEW_COUNT = 3;
const ALL_TAB = { key: 'all', label: '전체' };
// FAQ 는 수십 건 — 답변을 찾으려고 한 번에 받는다
const FAQ_PAGE_SIZE = 100;

const TABS = [
  { key: 'courses', label: '강좌' },
  { key: 'institutions', label: '기관' },
  { key: 'notices', label: '공지사항' },
  { key: 'faqs', label: '자주 묻는 질문' },
];

/* 통합검색(설계서 §5) — 피그마에 없어 강좌 찾기의 탭·검색줄 톤을 따른다.
 * 첫 조회는 탭당 5건, "더보기"는 같은 API 를 탭당 20건으로 다시 부른다. 강좌가 20건을 넘으면 강좌 찾기로 넘긴다 */
export default {
  name: 'SearchPage',
  components: { AppEmptyState, AppErrorBanner, LearnerTabs, PortalSearchBox, SearchResultList },
  data() {
    return {
      SEARCH_MIN_LENGTH,
      SEARCH_MAX_LENGTH,
      draft: '',
      activeKey: 'all',
      result: null,
      limit: FIRST_LIMIT,
      loading: false,
      errorMessage: '',
      // 검색 결과의 FAQ 엔 답이 없다 — 펼쳐 보이려고 한 번 받아 맞춘다
      faqCache: null,
      categories: [],
    };
  },
  computed: {
    query() {
      return String(this.$route.query.q || '').trim();
    },
    tooShort() {
      return this.$route.query.q !== undefined && this.query.length < SEARCH_MIN_LENGTH;
    },
    grandTotal() {
      return TABS.reduce((sum, tab) => sum + (this.result?.[tab.key]?.total || 0), 0);
    },
    // '전체' + 결과가 있는 종류만
    tabs() {
      const kinds = TABS.filter((tab) => (this.result?.[tab.key]?.total || 0) > 0).map((tab) => ({
        ...tab,
        fullLabel: `${tab.label} ${this.formatCount(this.result[tab.key].total)}`,
      }));
      return [{ ...ALL_TAB, fullLabel: `전체 ${this.formatCount(this.grandTotal)}` }, ...kinds];
    },
    overviewGroups() {
      return TABS.filter((tab) => (this.result?.[tab.key]?.total || 0) > 0).map((tab) => ({
        ...tab,
        total: this.result[tab.key].total,
        items: this.itemsOf(tab.key).slice(0, OVERVIEW_COUNT),
      }));
    },
    tabLabels() {
      return this.tabs.map((tab) => tab.fullLabel);
    },
    activeTab() {
      return this.tabs.find((tab) => tab.key === this.activeKey) || this.tabs[0];
    },
    activeLabel() {
      return this.activeTab.fullLabel;
    },
    activeItems() {
      return this.itemsOf(this.activeKey);
    },
    activeTotal() {
      return this.result?.[this.activeKey]?.total || 0;
    },
    canLoadMore() {
      if (this.activeKey === 'all') return false;
      return this.activeItems.length < this.activeTotal && this.limit < SEARCH_MAX_LIMIT;
    },
    showCourseLink() {
      return this.activeKey === 'courses' && this.activeItems.length < this.activeTotal;
    },
  },
  created() {
    this.loadCategories();
  },
  watch: {
    '$route.query.q': {
      immediate: true,
      handler() {
        this.draft = this.query;
        this.limit = FIRST_LIMIT;
        this.load();
      },
    },
  },
  methods: {
    formatCount(value) {
      return Number(value || 0).toLocaleString('ko-KR');
    },
    /** FAQ 는 답을 붙여 펼칠 수 있게 */
    itemsOf(key) {
      const items = this.result?.[key]?.items || [];
      if (key !== 'faqs') return items;
      return items.map((item) => ({
        ...item,
        answer: this.faqCache?.find((row) => row.id === item.id)?.answer || '답변을 불러오는 중이거나 찾지 못했습니다. 고객센터에서 확인해 주세요.',
      }));
    },
    async loadFaqAnswers() {
      if (this.faqCache || !this.result?.faqs?.total) return;
      try {
        this.faqCache = (await listFaqs({ size: FAQ_PAGE_SIZE })).data || [];
      } catch {
        this.faqCache = [];
      }
    },
    async loadCategories() {
      try {
        const tree = await listCourseClassifications();
        this.categories = (tree || []).map((node) => node.name).filter(Boolean);
      } catch {
        this.categories = [];
      }
    },
    submit() {
      const q = String(this.draft || '').trim();
      if (q === this.query) {
        this.load();
        return;
      }
      this.$router.push({ path: '/search', query: { q } });
    },
    selectTab(label) {
      const tab = this.tabs.find((item) => item.fullLabel === label);
      if (tab) this.activeKey = tab.key;
    },
    // 새 검색은 '전체' 요약부터
    pickFirstTab() {
      this.activeKey = 'all';
    },
    async load() {
      if (this.query.length < SEARCH_MIN_LENGTH) {
        this.result = null;
        return;
      }
      this.loading = true;
      this.errorMessage = '';
      try {
        const isFirst = this.limit === FIRST_LIMIT;
        this.result = await searchAll(this.query, { limit: this.limit });
        if (isFirst) this.pickFirstTab();
        this.loadFaqAnswers();
      } catch (error) {
        this.result = null;
        this.errorMessage =
          error?.status === 400
            ? `검색어는 ${SEARCH_MIN_LENGTH}~${SEARCH_MAX_LENGTH}자로 입력해 주세요.`
            : error?.message || '검색하지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    loadMore() {
      this.limit = SEARCH_MAX_LIMIT;
      this.load();
    },
  },
};
</script>

<style scoped>
.search-all {
  padding: 24px 0;
  background: var(--db-bg-page);
}

.search-all__body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 24px 16px 12px;
}

.search-hero--idle {
  padding-top: clamp(48px, 16vh, 160px);
  padding-bottom: 48px;
}

.search-hero__title {
  margin: 0 0 6px;
  font-size: 32px;
  line-height: 1.25;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--db-text-title);
  text-align: center;
}

.search-hero__note {
  margin: 0;
  font-size: 14px;
  color: var(--db-text-sub);
  text-align: center;
}

@media (max-width: 599px) {
  .search-hero--idle {
    padding-top: 40px;
  }

  .search-hero__title {
    font-size: 24px;
  }
}

.search-all__title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
  margin: 8px 0 0;
  padding: 0 16px;
  font-size: 20px;
  line-height: 28px;
  font-weight: 700;
  color: var(--db-text-body);
  overflow-wrap: anywhere;
}

.search-all__q {
  color: var(--db-accent);
}

.search-all__total {
  font-size: 15px;
  font-weight: 500;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.search-all__panel {
  padding: 8px 16px 16px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-top: 0;
  border-radius: 0 0 8px 8px;
}

.search-all__more {
  display: flex;
  justify-content: center;
  padding-top: 8px;
}

.search-all__more-link {
  padding: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-accent);
}

/* '전체' 요약 — 종류마다 머리 + 앞 몇 건 */
.search-group + .search-group {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--db-border);
}

.search-group__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 4px 2px;
}

.search-group__title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--db-text-title);
}

.search-group__count {
  margin-left: 4px;
  font-weight: 500;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.search-group__more {
  padding: 4px;
  border: 0;
  background: none;
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-accent);
  cursor: pointer;
}

.search-group__more:focus-visible {
  outline: 2px solid var(--db-accent);
  border-radius: 4px;
}

/* 검색 전 분류 바로가기 */
.search-cats {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  max-width: 640px;
  margin-top: 8px;
}

.search-cats__chip {
  padding: 8px 14px;
  border: 1px solid var(--db-border);
  border-radius: 999px;
  font-size: 14px;
  color: var(--db-text-body);
  background: var(--db-bg-white);
  text-decoration: none;
}

.search-cats__chip:hover,
.search-cats__chip:focus-visible {
  border-color: var(--db-accent);
  color: var(--db-accent);
}

@media (max-width: 599px) {
  .search-all__title {
    padding-left: 0;
    padding-right: 0;
  }

  .search-all__panel {
    padding: 4px 10px 12px;
  }
}
</style>
