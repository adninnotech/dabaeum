<template>
  <!-- 신청하면 어떻게 되는지 — 강사 신청·기관 등록 신청 공통 -->
  <ol class="apply-steps" aria-label="진행 순서">
    <li v-for="(step, index) in steps" :key="step" class="apply-steps__item">
      <span class="apply-steps__no" aria-hidden="true">{{ index + 1 }}</span>
      <span class="apply-steps__text">{{ step }}</span>
    </li>
  </ol>
</template>

<script>
export default {
  name: 'ApplySteps',
  props: {
    /** ['신청', '기관 검토', '승인되면 강사 콘솔'] */
    steps: { type: Array, default: () => [] },
  },
};
</script>

<style scoped>
.apply-steps {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  margin: 0;
  padding: 14px 16px;
  list-style: none;
  border-radius: 8px;
  background: var(--db-bg-subtle);
}

.apply-steps__item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--db-text-body);
  word-break: keep-all;
}

.apply-steps__no {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  font-size: 13px;
  font-weight: 700;
  color: var(--db-on-color);
  background: var(--db-primary);
}

@media (max-width: 599px) {
  .apply-steps {
    flex-direction: column;
  }
}
</style>
