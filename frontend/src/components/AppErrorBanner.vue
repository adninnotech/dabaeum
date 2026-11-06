<template>
  <!-- 목록·상세 불러오기 실패 공통 배너 — 토큰 색, 화면낭독기 알림, 오른쪽에 "다시 시도" 같은 동작 자리 -->
  <div v-if="message" class="app-error" role="alert">
    <q-icon name="error_outline" size="20px" class="app-error__icon" aria-hidden="true" />
    <span class="app-error__text">{{ message }}</span>
    <div v-if="$slots.action" class="app-error__action">
      <slot name="action" />
    </div>
  </div>
</template>

<script>
export default {
  name: 'AppErrorBanner',
  props: {
    message: {
      type: String,
      default: '',
    },
  },
};
</script>

<style scoped>
.app-error {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding: 12px 14px;
  border: 1px solid color-mix(in srgb, var(--db-error) 25%, transparent);
  border-radius: 8px;
  background: color-mix(in srgb, var(--db-error) 6%, var(--db-bg-white));
  color: var(--db-error);
  font-size: 14px;
  line-height: 1.5;
}

.app-error__icon {
  flex-shrink: 0;
}

.app-error__text {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}

.app-error__action {
  flex-shrink: 0;
}
</style>
