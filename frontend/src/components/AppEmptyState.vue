<template>
  <div class="empty-state" :class="{ 'empty-state--compact': compact, 'empty-state--inline': inline }">
    <div v-if="title" class="empty-state__title">{{ title }}</div>
    <div v-if="description" class="empty-state__desc">{{ description }}</div>
    <!-- 빈 화면에서 끝나지 않게 다음 행동을 둔다. 버튼 하나면 props, 그 이상이면 action 슬롯 -->
    <div v-if="$slots.action || actionLabel" class="empty-state__action">
      <slot name="action">
        <q-btn unelevated no-caps color="primary" :label="actionLabel" :to="actionTo || undefined" />
      </slot>
    </div>
  </div>
</template>

<script>
export default {
  name: 'AppEmptyState',
  props: {
    title: { type: String, default: '' },
    description: { type: String, default: '' },
    /** 탭 안에서는 세로 여백을 40으로 줄인다 */
    compact: { type: Boolean, default: false },
    /** 홈 섹션처럼 여럿이 이어지는 곳 — 문구와 버튼을 한 줄 띠로(빈 섹션마다 200px 넘게 비었다) */
    inline: { type: Boolean, default: false },
    /** 기본 행동 버튼 문구 — 비우면 버튼을 그리지 않는다 */
    actionLabel: { type: String, default: '' },
    /** 기본 행동 버튼이 여는 경로 (router-link to 와 같은 형식) */
    actionTo: { type: [String, Object], default: '' },
  },
};
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 80px 0;
  text-align: center;
}

.empty-state--compact {
  padding: 40px 0;
}

.empty-state--inline {
  flex-direction: row;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 12px 16px;
  padding: 18px 20px;
  border-radius: 8px;
  background: var(--db-bg-subtle);
  text-align: left;
}

.empty-state--inline .empty-state__desc {
  flex: 1 1 240px;
  font-size: 15px;
  word-break: keep-all;
}

.empty-state--inline .empty-state__action {
  margin-top: 0;
}

.empty-state__title {
  font-size: 24px;
  font-weight: 700;
  color: var(--db-text-dark);
}

.empty-state__desc {
  font-size: 16px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.empty-state__action {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-top: 8px;
}
</style>
