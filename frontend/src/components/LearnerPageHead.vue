<template>
  <!-- 학습자 화면 공통 머리 — 학습지갑·학습관리와 같은 큰 h1 + 한 줄 설명.
       콘솔식 text-h5 제목과 "← 지갑" flat 버튼이 학습자 화면마다 달랐던 것을 한 곳으로 모은다 -->
  <header class="learner-head">
    <router-link v-if="back && back.to" :to="back.to" class="learner-head__back">
      <q-icon name="arrow_back" size="16px" aria-hidden="true" />
      <span>{{ back.label || '이전' }}</span>
    </router-link>
    <div class="learner-head__row">
      <div class="learner-head__text">
        <h1 class="learner-head__title">{{ title }}</h1>
        <p v-if="subtitle" class="learner-head__sub">{{ subtitle }}</p>
      </div>
      <div v-if="$slots.actions" class="learner-head__actions">
        <slot name="actions" />
      </div>
    </div>
  </header>
</template>

<script>
export default {
  name: 'LearnerPageHead',
  props: {
    title: {
      type: String,
      required: true,
    },
    subtitle: {
      type: String,
      default: '',
    },
    /** { label, to } — 상위 화면으로 돌아가는 길. 없으면 그리지 않는다 */
    back: {
      type: Object,
      default: null,
    },
  },
};
</script>

<style scoped>
.learner-head {
  margin-bottom: 16px;
}

/* 버튼이 아니라 빵부스러기 같은 작은 글자 링크 — 제목보다 눈에 띄지 않게 */
.learner-head__back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 8px;
  font-size: 14px;
  color: var(--db-text-sub);
  text-decoration: none;
}

.learner-head__back:hover,
.learner-head__back:focus-visible {
  color: var(--db-accent);
  text-decoration: underline;
}

.learner-head__row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.learner-head__text {
  min-width: 0;
}

/* WalletPage .wallet-head__title 과 같은 크기 — 학습자 화면 제목을 한 문법으로 */
.learner-head__title {
  margin: 0;
  font-size: 26px;
  line-height: 31px;
  font-weight: 700;
  color: var(--db-text-body);
  word-break: keep-all;
  overflow-wrap: anywhere;
}

/* 폰에서 '남겨 주 / 세요.' 처럼 낱말 가운데서 꺾였다 */
.learner-head__sub {
  margin: 6px 0 0;
  font-size: 14px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.learner-head__actions {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

@media (max-width: 599px) {
  .learner-head__row {
    flex-wrap: wrap;
    align-items: flex-start;
  }

  .learner-head__actions {
    flex-shrink: 1;
    min-width: 0;
  }
}
</style>
