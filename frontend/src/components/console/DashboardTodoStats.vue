<template>
  <!-- 대시보드 '할 일' 숫자 — 전체·모집중·진행중처럼 한 번 보면 끝나는 숫자 대신 오늘 처리할 것.
       할 일이 있으면 숫자를 색으로, 없으면 회색. 누르면 그 일을 하는 화면으로 -->
  <nav class="todo-stats" aria-label="할 일">
    <component
      :is="item.to ? 'router-link' : 'div'"
      v-for="item in items"
      :key="item.key"
      :to="item.to || undefined"
      class="todo-stats__item"
      :class="{ 'todo-stats__item--active': Number(item.value) > 0, 'todo-stats__item--link': item.to }"
    >
      <span class="todo-stats__label">
        <q-icon :name="item.icon" size="18px" aria-hidden="true" />
        {{ item.label }}
      </span>
      <span class="todo-stats__value">
        <strong>{{ item.value ?? '-' }}</strong>
        <span v-if="item.value != null" class="todo-stats__unit">{{ item.unit }}</span>
      </span>
      <span v-if="item.hint" class="todo-stats__hint">{{ item.hint }}</span>
    </component>
  </nav>
</template>

<script>
export default {
  name: 'DashboardTodoStats',
  props: {
    /** [{ key, label, icon, value(null=모름), unit, hint?, to? }] */
    items: { type: Array, default: () => [] },
  },
};
</script>

<style scoped>
.todo-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 220px), 1fr));
  gap: 12px;
}

.todo-stats__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding: 14px 18px;
  border: 1px solid var(--db-border);
  border-radius: 12px;
  background: var(--db-bg-white);
  color: var(--db-text-body);
  text-decoration: none;
}

.todo-stats__item--link:hover {
  border-color: var(--db-accent);
}

.todo-stats__item--link:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.todo-stats__label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-sub);
}

.todo-stats__value {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
}

.todo-stats__value strong {
  font-size: 28px;
  font-weight: 800;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
  color: var(--db-text-sub);
}

.todo-stats__unit {
  font-size: 14px;
  color: var(--db-text-sub);
}

/* 할 일이 있는 칸만 — 숫자를 진하게, 왼쪽에 색 띠 */
.todo-stats__item--active {
  border-left: 4px solid var(--db-accent);
}

.todo-stats__item--active .todo-stats__value strong {
  color: var(--db-accent);
}

.todo-stats__item--active .todo-stats__label {
  color: var(--db-text-title);
}

/* 폰: 한 줄에 하나씩이면 네 칸이 첫 화면을 다 써 '오늘 수업'이 밀렸다 — 2×2 */
@media (max-width: 599px) {
  .todo-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .todo-stats__item {
    padding: 10px 12px;
  }

  .todo-stats__label {
    font-size: 13px;
  }

  .todo-stats__value strong {
    font-size: 24px;
  }
}

.todo-stats__hint {
  font-size: 13px;
  color: var(--db-text-sub);
  overflow-wrap: anywhere;
}
</style>
