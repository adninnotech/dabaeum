<template>
  <q-page class="pub-pf">
    <div class="learner-container pub-pf__body">
      <!-- 만료·폐기·없는 링크는 모두 404 — 서버가 구분하지 않으므로 화면도 한 문구로 -->
      <section v-if="notFound" class="pub-pf__gone" role="status">
        <q-icon name="o_link_off" size="40px" class="pub-pf__gone-icon" />
        <h1 class="pub-pf__gone-title">열 수 없는 포트폴리오입니다</h1>
        <p class="pub-pf__gone-text">
          공유 기간이 끝났거나 공유한 사람이 링크를 폐기했습니다. 공유한 사람에게 새 링크를 요청해 주세요.
        </p>
        <router-link to="/" class="pub-pf__home">다배움 홈으로</router-link>
      </section>

      <AppErrorBanner v-else-if="errorMessage" :message="errorMessage" />

      <template v-else-if="portfolio">
        <header class="pub-pf__head">
          <p class="pub-pf__eyebrow">다배움 학습 포트폴리오</p>
          <h1 class="pub-pf__name">{{ portfolio.name }}</h1>
          <p class="pub-pf__sub">
            수료증 {{ portfolio.credentials.length }}건 · 학습배지 {{ portfolio.badges.length }}개
          </p>
          <q-btn outline no-caps color="primary" icon="o_print" label="인쇄 · PDF 저장" class="pub-pf__print no-print" @click="print" />
        </header>
        <PortfolioRecords :credentials="portfolio.credentials" :badges="portfolio.badges" />
        <p class="pub-pf__foot">
          이 페이지는 본인이 고른 수료증·배지만 보여 줍니다. 수료증은 "진위 확인"으로 발급 상태를 확인할 수 있습니다.
        </p>
      </template>
    </div>

    <q-inner-loading :showing="loading">
      <q-spinner color="primary" size="32px" />
    </q-inner-loading>
  </q-page>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import PortfolioRecords from '@/components/portfolio/PortfolioRecords.vue';
import { getPublicPortfolio } from '@/services/portfolio-api';

/* 공개 포트폴리오(설계서 §6) — 로그인 없이 연다. 이름·수료증·배지만 온다(연락처·생년월일 없음). 열 때마다 조회 수가 오른다 */
export default {
  name: 'PublicPortfolioPage',
  components: { AppErrorBanner, PortfolioRecords },
  data() {
    return { portfolio: null, loading: false, notFound: false, errorMessage: '' };
  },
  watch: {
    '$route.params.token': {
      immediate: true,
      handler() {
        this.load();
      },
    },
  },
  methods: {
    print() {
      window.print();
    },
    async load() {
      const token = String(this.$route.params.token || '');
      this.loading = true;
      this.notFound = false;
      this.errorMessage = '';
      try {
        this.portfolio = await getPublicPortfolio(token);
      } catch (error) {
        this.portfolio = null;
        if (error?.status === 404) this.notFound = true;
        else this.errorMessage = error?.message || '포트폴리오를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
.pub-pf {
  position: relative;
  padding: 32px 0;
  background: var(--db-bg-page);
}

.pub-pf__body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  /* .learner-container 의 max-width: 100% 를 덮지 않는다 — 폰에서 가로로 넘친다 */
  max-width: min(880px, 100%);
}

.pub-pf__head {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 24px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.pub-pf__eyebrow {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-accent);
}

.pub-pf__name {
  margin: 0;
  font-size: 28px;
  line-height: 36px;
  font-weight: 700;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.pub-pf__sub {
  margin: 0;
  font-size: 14px;
  color: var(--db-text-sub);
}

.pub-pf__print {
  margin-top: 12px;
  border-radius: 6px;
}

.pub-pf__foot {
  margin: 0;
  padding: 0 4px;
  font-size: 13px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.pub-pf__gone {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 56px 24px;
  text-align: center;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.pub-pf__gone-icon {
  color: var(--db-text-sub);
}

.pub-pf__gone-title {
  margin: 0;
  font-size: 20px;
  line-height: 28px;
  font-weight: 700;
  color: var(--db-text-title);
}

.pub-pf__gone-text {
  max-width: 420px;
  margin: 0;
  font-size: 14px;
  line-height: 21px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.pub-pf__home {
  margin-top: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-accent);
}

@media print {
  :global(.q-header),
  :global(.q-footer),
  :global(.q-drawer) {
    display: none !important;
  }

  :global(.q-page-container) {
    padding: 0 !important;
  }

  .pub-pf {
    padding: 0;
    background: var(--db-bg-white);
  }

  .no-print {
    display: none !important;
  }
}
</style>
