<template>
  <nav class="console-tabs">
    <button
      v-for="item in items"
      :key="item.value"
      type="button"
      class="console-tabs__item"
      :class="{ active: item.value === modelValue }"
      @click="$emit('update:modelValue', item.value)"
    >
      {{ item.label }}
    </button>
  </nav>
</template>

<script>
export default {
  name: 'ConsoleTabs',
  props: {
    /** [{ label, value }] */
    items: { type: Array, required: true },
    modelValue: { type: String, required: true },
  },
  emits: ['update:modelValue'],
};
</script>

<style scoped>
.console-tabs {
  display: flex;
  gap: 40px;
  height: 64px;
  padding: 0 25px;
  border-bottom: 1px solid var(--db-border);
  /* 탭이 화면보다 길면(폰) 페이지가 아니라 탭 줄만 가로로 민다 */
  overflow-x: auto;
  scrollbar-width: none;
}

.console-tabs::-webkit-scrollbar {
  display: none;
}

.console-tabs__item {
  height: 48px;
  padding: 15px 0;
  background: none;
  border: none;
  border-bottom: 1px solid var(--db-border);
  font-size: 18px;
  font-weight: 700;
  color: var(--db-text-sub);
  white-space: nowrap;
  flex-shrink: 0;
  cursor: pointer;
}

.console-tabs__item.active {
  border-bottom-color: var(--db-primary);
  color: var(--db-primary);
}

@media (max-width: 599px) {
  .console-tabs {
    gap: 24px;
    height: 52px;
    padding: 0 4px;
  }

  .console-tabs__item {
    height: 44px;
    padding: 12px 0;
    font-size: 16px;
  }
}
</style>
