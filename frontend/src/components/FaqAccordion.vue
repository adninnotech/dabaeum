<template>
  <ul class="faq">
    <!-- 자주 묻는 질문 — 홈과 고객센터가 같이 쓴다(예전엔 고객센터만 Quasar 기본 목록이라 같은 FAQ 가 다르게 보였다).
         Q·A 로 질문과 답을 나누고 한 번에 하나만 펼친다. 루트를 ul 하나로 두려고 설명을 안에 둔다 -->
    <li v-for="faq in faqs" :key="faq.id" class="faq__item">
      <button
        :id="`${idPrefix}-q-${faq.id}`"
        type="button"
        class="faq__question"
        :aria-expanded="openFaqId === faq.id ? 'true' : 'false'"
        :aria-controls="`${idPrefix}-a-${faq.id}`"
        @click="toggleFaq(faq.id)"
      >
        <span class="faq__mark" aria-hidden="true">Q</span>
        <span class="faq__question-text">{{ faq.question }}</span>
        <q-icon
          name="expand_more"
          size="20px"
          class="faq__chevron"
          :class="{ 'faq__chevron--open': openFaqId === faq.id }"
        />
      </button>
      <!-- 높이 전환은 CSS(grid 0fr→1fr) — q-slide-transition 은 JS 로 높이를 재서
           답 칸 아래 여백이 끝에 툭 튀어 버벅여 보였다 -->
      <div
        :id="`${idPrefix}-a-${faq.id}`"
        role="region"
        :aria-labelledby="`${idPrefix}-q-${faq.id}`"
        class="faq__panel"
        :class="{ 'faq__panel--open': openFaqId === faq.id }"
        :inert="openFaqId !== faq.id || undefined"
      >
        <div class="faq__panel-inner">
          <div class="faq__answer">
            <span class="faq__mark faq__mark--answer" aria-hidden="true">A</span>
            <p class="faq__answer-text">{{ faq.answer }}</p>
          </div>
        </div>
      </div>
    </li>
  </ul>
</template>

<script>
export default {
  name: 'FaqAccordion',
  props: {
    /** [{ id, question, answer }] — 서버 순서(sortOrder) 그대로 */
    faqs: { type: Array, default: () => [] },
    /** 한 화면에 둘 이상 놓일 때 id 가 겹치지 않게 */
    idPrefix: { type: String, default: 'faq' },
  },
  data() {
    return { openFaqId: null };
  },
  methods: {
    toggleFaq(id) {
      this.openFaqId = this.openFaqId === id ? null : id;
    },
  },
};
</script>

<style scoped>
.faq {
  margin: 0;
  padding: 0;
  list-style: none;
}

.faq__item + .faq__item {
  border-top: 1px solid var(--db-border);
}

/* 공지 제목과 같은 왼쪽 선·글자 크기, 누르는 높이 44px 이상 */
.faq__question {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 48px;
  padding: 8px 0;
  font: inherit;
  text-align: left;
  color: var(--db-text-title);
  background: none;
  border: 0;
  cursor: pointer;
}

.faq__question:hover .faq__question-text {
  color: var(--db-accent);
}

.faq__question:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
  border-radius: 4px;
}

.faq__question-text {
  flex: 1;
  min-width: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
  word-break: keep-all;
  overflow-wrap: anywhere;
}

.faq__mark {
  flex-shrink: 0;
  width: 20px;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--db-accent);
}

.faq__mark--answer {
  color: var(--db-text-sub);
}

.faq__chevron {
  flex-shrink: 0;
  color: var(--db-text-sub);
  transition: transform 0.2s ease;
}

.faq__chevron--open {
  transform: rotate(180deg);
}

.faq__panel {
  display: grid;
  grid-template-rows: 0fr;
  visibility: hidden;
  transition:
    grid-template-rows 0.2s ease,
    visibility 0.2s;
}

.faq__panel--open {
  grid-template-rows: 1fr;
  visibility: visible;
}

/* 아래 여백은 margin 이 아니라 안쪽 빈 줄 — 높이 안에 들어가야 끝에 튀지 않는다 */
.faq__panel-inner {
  min-height: 0;
  overflow: hidden;
}

.faq__panel-inner::after {
  content: '';
  display: block;
  height: 12px;
}

/* 답은 연한 바탕 칸 — 질문과 한눈에 나뉘게 */
.faq__answer {
  display: flex;
  gap: 10px;
  padding: 12px;
  background: var(--db-bg-subtle);
  border-radius: 6px;
}

.faq__answer .faq__mark {
  width: 20px;
}

.faq__answer-text {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--db-text-body);
  white-space: pre-wrap;
  word-break: keep-all;
  overflow-wrap: anywhere;
}

@media (prefers-reduced-motion: reduce) {
  .faq__chevron,
  .faq__panel {
    transition: none;
  }
}
</style>
