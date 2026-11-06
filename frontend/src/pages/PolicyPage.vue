<template>
  <!-- 약관·개인정보처리방침 공개 열람 — /policies/usage, /policies/privacy. 항상 최신 게시본 -->
  <q-page class="policy">
    <div class="learner-container policy__body">
      <nav class="policy__tabs" aria-label="약관 종류">
        <router-link
          v-for="tab in TABS"
          :key="tab.slug"
          :to="`/policies/${tab.slug}`"
          class="policy__tab"
          :class="{ 'policy__tab--active': tab.slug === slug }"
          :aria-current="tab.slug === slug ? 'page' : undefined"
        >
          {{ tab.label }}
        </router-link>
      </nav>

      <AppErrorBanner v-if="errorMessage" :message="errorMessage" />

      <article v-else-if="terms" class="policy__article">
        <h1 class="policy__title">{{ terms.title }}</h1>
        <p class="policy__meta">
          <span>버전 {{ terms.version }}</span>
          <span>시행 {{ formatDateKst(terms.updatedAt) }}</span>
        </p>
        <div class="policy__text">{{ terms.body }}</div>
      </article>

      <section v-else-if="!loading" class="policy__article policy__article--empty" role="status">
        <p>아직 게시된 {{ currentTab.label }}이 없습니다.</p>
      </section>
    </div>

    <q-inner-loading :showing="loading">
      <q-spinner color="primary" size="32px" />
    </q-inner-loading>
  </q-page>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import { getTermsContent } from '@/services/support-api';
import { formatDateKst } from '@/utils/datetime';

const TABS = [
  { slug: 'usage', type: 'USAGE', label: '이용약관' },
  { slug: 'privacy', type: 'PRIVACY', label: '개인정보처리방침' },
];

export default {
  name: 'PolicyPage',
  components: { AppErrorBanner },
  data() {
    return { TABS, terms: null, loading: false, errorMessage: '' };
  },
  computed: {
    slug() {
      return TABS.some((tab) => tab.slug === this.$route.params.type) ? this.$route.params.type : 'usage';
    },
    currentTab() {
      return TABS.find((tab) => tab.slug === this.slug);
    },
  },
  watch: {
    slug: { immediate: true, handler: 'load' },
  },
  methods: {
    formatDateKst,
    async load() {
      this.loading = true;
      this.errorMessage = '';
      this.terms = null;
      try {
        this.terms = await getTermsContent(this.currentTab.type);
      } catch (error) {
        // 아직 한 번도 게시하지 않았으면 404 — 빈 안내로
        if (error?.status !== 404) this.errorMessage = error?.message || '약관을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
.policy {
  position: relative;
  padding: 32px 0 48px;
  background: var(--db-bg-page);
}

.policy__body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  /* .learner-container 의 max-width: 100% 를 덮지 않는다 */
  max-width: min(880px, 100%);
}

.policy__tabs {
  display: flex;
  gap: 24px;
  border-bottom: 1px solid var(--db-border);
}

.policy__tab {
  padding: 10px 0;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  font-size: 16px;
  font-weight: 600;
  color: var(--db-text-sub);
  text-decoration: none;
}

.policy__tab--active {
  border-bottom-color: var(--db-primary);
  color: var(--db-primary);
}

.policy__tab:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.policy__article {
  padding: 32px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.policy__article--empty p {
  margin: 0;
  font-size: 14px;
  color: var(--db-text-sub);
}

.policy__title {
  margin: 0;
  font-size: 26px;
  line-height: 34px;
  font-weight: 700;
  letter-spacing: normal;
  color: var(--db-text-title);
}

.policy__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  margin: 8px 0 24px;
  font-size: 14px;
  color: var(--db-text-sub);
}

.policy__text {
  max-width: 70ch;
  font-size: 15px;
  line-height: 1.8;
  color: var(--db-text-body);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

@media (max-width: 599px) {
  .policy__article {
    padding: 20px 16px;
  }

  .policy__title {
    font-size: 21px;
    line-height: 29px;
  }
}
</style>
