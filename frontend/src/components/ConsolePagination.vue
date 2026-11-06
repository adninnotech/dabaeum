<template>
  <div class="console-pagination">
    <div class="console-pagination__left">
      <span class="console-pagination__count">총 {{ total }}{{ unit }}</span>
      <q-select
        v-if="pageSizeOptions.length"
        :model-value="pageSize"
        outlined
        dense
        emit-value
        map-options
        :options="sizeOptions"
        class="console-pagination__size"
        @update:model-value="$emit('update:pageSize', $event)"
      />
      <span v-else class="console-pagination__count">· {{ pageSize }}개씩 보기</span>
    </div>

    <q-pagination
      :model-value="modelValue"
      :max="maxPage"
      :max-pages="$q.screen.xs ? 3 : 5"
      direction-links
      color="primary"
      unelevated
      class="console-pagination__pages"
      @update:model-value="$emit('update:modelValue', $event)"
    />
  </div>
</template>

<script>
export default {
  name: 'ConsolePagination',
  props: {
    modelValue: { type: Number, default: 1 },
    total: { type: Number, default: 0 },
    pageSize: { type: Number, default: 10 },
    unit: { type: String, default: '건' },
    /** 비어 있으면 "N개씩 보기" 텍스트, 값이 있으면 셀렉트로 바뀐다 */
    pageSizeOptions: { type: Array, default: () => [] },
  },
  emits: ['update:modelValue', 'update:pageSize'],
  computed: {
    maxPage() {
      return Math.max(1, Math.ceil(this.total / this.pageSize));
    },
    sizeOptions() {
      return this.pageSizeOptions.map((size) => ({ label: `${size}개씩 보기`, value: size }));
    },
  },
};
</script>

<style scoped>
/* 폰: 쪽이 많으면(접속 기록 33쪽) 번호 줄이 화면 밖으로 나가 페이지가 가로로 밀렸다 — 번호를 줄이고 줄바꿈을 허용 */
.console-pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.console-pagination__left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.console-pagination__count {
  font-size: 14px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.console-pagination__size {
  width: 132px; /* "10개씩 보기"가 "10개씩 ..."으로 잘리지 않게 */
}

.console-pagination__size :deep(.q-field__control) {
  height: 36px;
  min-height: 36px;
  border-radius: 6px;
}

.console-pagination__pages :deep(.q-btn) {
  min-width: 32px;
  height: 32px;
  border-radius: 6px;
  color: var(--db-text-sub);
}

.console-pagination__pages :deep(.q-btn--unelevated) {
  border: 1px solid var(--db-border);
}

.console-pagination__pages :deep(.q-btn--unelevated.bg-primary) {
  border-color: var(--db-primary);
  color: var(--db-on-color);
}
</style>
