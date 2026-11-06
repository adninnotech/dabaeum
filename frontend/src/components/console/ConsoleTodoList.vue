<template>
  <!-- 세 콘솔 대시보드가 같은 모양으로 쓰는 '처리할 일' — 남은 일만 크게, 끝난 일은 한 줄로 접는다 -->
  <section class="console-todo" aria-label="처리할 일">
    <header class="console-todo__head">
      <h2 class="console-todo__title">처리할 일</h2>
      <q-spinner v-if="loading" color="primary" size="16px" />
      <!-- 못 센 항목이 있을 때만 — 전체 새로고침 없이 건수만 다시 부르게 한다 -->
      <q-btn
        v-else-if="hasUnknown"
        flat
        dense
        no-caps
        size="sm"
        icon="refresh"
        label="다시 시도"
        class="console-todo__retry"
        @click="$emit('retry')"
      />
    </header>

    <p v-if="allDone" class="console-todo__empty">지금 처리할 일이 없습니다.</p>

    <template v-else>
      <div v-if="openItems.length" class="console-todo__list">
        <component
          :is="item.to ? 'router-link' : 'div'"
          v-for="item in openItems"
          :key="item.key"
          :to="item.to || undefined"
          class="console-todo__item"
          :class="{
            'console-todo__item--active': item.count > 0,
            'console-todo__item--link': !!item.to,
          }"
        >
          <span class="console-todo__label">{{ item.label }}</span>
          <span class="console-todo__count">{{ countText(item.count) }}</span>
          <q-icon v-if="item.to" name="chevron_right" size="18px" />
        </component>
      </div>

      <!-- 0건은 상자로 늘어놓으면 할 일처럼 보여 한 줄로만 남긴다 -->
      <div v-if="doneItems.length" class="console-todo__done">
        <span>처리 완료:</span>
        <template v-for="(item, index) in doneItems" :key="item.key">
          <span v-if="index > 0" aria-hidden="true">·</span>
          <router-link v-if="item.to" :to="item.to" class="console-todo__done-link">
            {{ item.label }}
          </router-link>
          <span v-else>{{ item.label }}</span>
        </template>
      </div>
    </template>

    <slot name="extra" />
  </section>
</template>

<script>
export default {
  name: 'ConsoleTodoList',
  props: {
    /** [{ key, label, count, to }] — count 가 null 이면 아직 못 셌거나 실패(0건과 구분) */
    items: { type: Array, default: () => [] },
    /** 건수를 세는 중 — 이때 null 은 실패가 아니라 '확인 중' */
    loading: { type: Boolean, default: false },
  },
  emits: ['retry'],
  computed: {
    /** 남은 일(1건 이상)과 못 센 일 — 둘 다 사람이 봐야 한다 */
    openItems() {
      return this.items.filter((item) => item.count === null || item.count > 0);
    },
    doneItems() {
      return this.items.filter((item) => item.count === 0);
    },
    hasUnknown() {
      return this.items.some((item) => item.count === null);
    },
    /** 모두 0건으로 확인됐을 때만 — 못 센 항목이 하나라도 있으면 '없다'고 단정하지 않는다 */
    allDone() {
      return this.items.length > 0 && this.items.every((item) => item.count === 0);
    },
  },
  methods: {
    countText(count) {
      if (count !== null) return `${count}건`;
      return this.loading ? '확인 중' : '불러오지 못함';
    },
  },
};
</script>

<style scoped>
.console-todo {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.console-todo__head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 24px;
}

.console-todo__title {
  margin: 0;
  font-size: 16px;
  line-height: 1.5;
  font-weight: 700;
  letter-spacing: normal;
  color: var(--db-text-body);
}

.console-todo__retry {
  color: var(--db-accent);
}

.console-todo__empty {
  margin: 0;
  font-size: 14px;
  color: var(--db-text-sub);
}

.console-todo__list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.console-todo__item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid var(--db-border);
  border-radius: 6px;
  color: var(--db-text-sub);
  text-decoration: none;
}

.console-todo__item--link:hover,
.console-todo__item--link:focus-visible {
  border-color: var(--db-accent);
}

/* 할 일이 남은 항목만 강조 */
.console-todo__item--active {
  color: var(--db-text-title);
  border-color: var(--db-accent);
}

.console-todo__label {
  font-size: 14px;
}

/* 숫자 폭을 고정해 건수가 바뀌어도 상자 폭이 흔들리지 않게 */
.console-todo__count {
  font-size: 16px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.console-todo__item--active .console-todo__count {
  color: var(--db-accent);
}

.console-todo__done {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--db-text-sub);
}

.console-todo__done-link {
  color: inherit;
  text-decoration: none;
}

.console-todo__done-link:hover,
.console-todo__done-link:focus-visible {
  color: var(--db-accent);
  text-decoration: underline;
}
</style>
