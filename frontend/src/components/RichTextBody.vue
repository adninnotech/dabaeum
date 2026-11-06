<template>
  <!-- eslint-disable-next-line vue/no-v-html -- sanitizeRichText 로 허용 태그만 남긴 본문 -->
  <div class="rich-body" v-html="safeHtml" />
</template>

<script>
import { sanitizeRichText } from '@/utils/rich-text';

/* 서버가 정제해 보낸 리치텍스트 본문을 한 번 더 정제해 읽기 좋게 보인다 — 콘텐츠 페이지·공지 상세 */
export default {
  name: 'RichTextBody',
  props: {
    html: { type: String, default: '' },
  },
  computed: {
    safeHtml() {
      return sanitizeRichText(this.html || '');
    },
  },
};
</script>

<style scoped>
.rich-body {
  max-width: 70ch;
  font-size: 16px;
  line-height: 1.75;
  color: var(--db-text-body);
  /* '상세 강의 소 / 개'처럼 낱말 중간에서 꺾였다 — 낱말 단위로(아주 긴 낱말만 쪼갠다) */
  word-break: keep-all;
  overflow-wrap: anywhere;
}

/* Quasar 전역 h1~h6(6rem 등)을 본문 크기로 되돌린다 */
.rich-body :deep(h1),
.rich-body :deep(h2),
.rich-body :deep(h3),
.rich-body :deep(h4) {
  margin: 1.4em 0 0.5em;
  font-weight: 700;
  letter-spacing: normal;
  color: var(--db-text-title);
}

.rich-body :deep(h1) {
  font-size: 24px;
  line-height: 32px;
}

.rich-body :deep(h2) {
  font-size: 20px;
  line-height: 28px;
}

.rich-body :deep(h3),
.rich-body :deep(h4) {
  font-size: 17px;
  line-height: 25px;
}

.rich-body :deep(:first-child) {
  margin-top: 0;
}

.rich-body :deep(p) {
  margin: 0 0 0.9em;
}

.rich-body :deep(img) {
  max-width: 100%;
  height: auto;
}
</style>
