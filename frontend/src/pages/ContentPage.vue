<template>
  <q-page class="content-page">
    <div class="learner-container content-page__body">
      <section v-if="notFound" class="content-page__gone" role="status">
        <h1 class="content-page__gone-title">페이지를 찾을 수 없습니다</h1>
        <p class="content-page__gone-text">주소가 바뀌었거나 아직 공개되지 않은 페이지입니다.</p>
        <router-link to="/" class="content-page__home">홈으로</router-link>
      </section>

      <AppErrorBanner v-else-if="errorMessage" :message="errorMessage" />

      <article v-else-if="page" class="content-page__article">
        <h1 class="content-page__title">{{ page.title }}</h1>
        <p class="content-page__date">최종 수정 {{ formatDateKst(page.updatedAt) }}</p>
        <RichTextBody :html="page.bodyHtml || ''" />
      </article>
    </div>

    <q-inner-loading :showing="loading">
      <q-spinner color="primary" size="32px" />
    </q-inner-loading>
  </q-page>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import RichTextBody from '@/components/RichTextBody.vue';
import { getPublicContentPage } from '@/services/cms-api';
import { formatDateKst } from '@/utils/datetime';

/* 공개 콘텐츠 페이지(설계서 §7) — /pages/:slug. 없거나 초안이면 404 */
export default {
  name: 'ContentPage',
  components: { AppErrorBanner, RichTextBody },
  data() {
    return { page: null, loading: false, notFound: false, errorMessage: '' };
  },
  watch: {
    '$route.params.slug': {
      immediate: true,
      handler() {
        this.load();
      },
    },
  },
  methods: {
    formatDateKst,
    async load() {
      this.loading = true;
      this.notFound = false;
      this.errorMessage = '';
      try {
        this.page = await getPublicContentPage(String(this.$route.params.slug || ''));
      } catch (error) {
        this.page = null;
        if (error?.status === 404) this.notFound = true;
        else this.errorMessage = error?.message || '페이지를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
.content-page {
  position: relative;
  padding: 32px 0;
  background: var(--db-bg-page);
}

.content-page__body {
  /* .learner-container 의 max-width: 100% 를 덮지 않는다 — 폰에서 가로로 넘친다 */
  max-width: min(880px, 100%);
}

.content-page__article,
.content-page__gone {
  padding: 32px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.content-page__title {
  margin: 0;
  font-size: 28px;
  line-height: 36px;
  font-weight: 700;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.content-page__date {
  margin: 6px 0 24px;
  font-size: 14px;
  color: var(--db-text-sub);
}

.content-page__gone {
  padding: 32px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.content-page__title {
  margin: 0;
  font-size: 28px;
  line-height: 36px;
  font-weight: 700;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.content-page__date {
  margin: 6px 0 24px;
  font-size: 14px;
  color: var(--db-text-sub);
}

.content-page__html {
  max-width: 70ch;
  font-size: 16px;
  line-height: 1.75;
  color: var(--db-text-body);
  overflow-wrap: anywhere;
}

/* Quasar 전역 h1~h6(6rem 등)을 본문 크기로 되돌린다 */
.content-page__html :deep(h1),
.content-page__html :deep(h2),
.content-page__html :deep(h3),
.content-page__html :deep(h4) {
  margin: 1.4em 0 0.5em;
  font-weight: 700;
  letter-spacing: normal;
  color: var(--db-text-title);
}

.content-page__html :deep(h1) {
  font-size: 24px;
  line-height: 32px;
}

.content-page__html :deep(h2) {
  font-size: 20px;
  line-height: 28px;
}

.content-page__html :deep(h3),
.content-page__html :deep(h4) {
  font-size: 17px;
  line-height: 25px;
}

.content-page__html :deep(:first-child) {
  margin-top: 0;
}

.content-page__html :deep(p) {
  margin: 0 0 0.9em;
}

.content-page__html :deep(img) {
  max-width: 100%;
  height: auto;
}

.content-page__gone {
  text-align: center;
}

.content-page__gone-title {
  margin: 0;
  font-size: 20px;
  line-height: 28px;
  font-weight: 700;
  color: var(--db-text-title);
}

.content-page__gone-text {
  margin: 8px 0 16px;
  font-size: 14px;
  color: var(--db-text-sub);
}

.content-page__home {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-accent);
}

@media (max-width: 599px) {
  .content-page__article,
  .content-page__gone {
    padding: 20px 16px;
  }

  .content-page__title {
    font-size: 22px;
    line-height: 30px;
  }
}
</style>
