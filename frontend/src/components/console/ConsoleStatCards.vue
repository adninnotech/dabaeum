<template>
  <!-- 콘솔 숫자 카드 — 불러오는 동안 0 을 보이면 '강의 0개'로 읽혀 자리표시(skeleton)를 둔다 -->
  <div class="row q-col-gutter-md">
    <div v-for="item in items" :key="item.label" :class="colClass">
      <!-- to 가 있으면 누르면 그 화면으로 — 숫자를 보고 목록을 보려면 메뉴로 다시 가야 했다 -->
      <component :is="item.to ? 'router-link' : 'div'" :to="item.to || undefined" class="stat-card__host">
        <q-card flat bordered :class="{ 'stat-card--link': item.to }">
          <q-card-section>
            <div class="stat-card__label">{{ item.label }}</div>
            <q-skeleton v-if="loading" type="text" width="48px" height="32px" />
            <div v-else class="stat-card__value">{{ item.value }}</div>
          </q-card-section>
        </q-card>
      </component>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ConsoleStatCards',
  props: {
    /** [{ label, value, to? }] */
    items: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    colClass: { type: String, default: 'col-6 col-md-3' },
  },
};
</script>

<style scoped>
.stat-card__host {
  display: block;
  color: inherit;
  text-decoration: none;
  border-radius: 8px;
}

.stat-card--link {
  transition: border-color 120ms ease-out;
}

.stat-card__host:hover .stat-card--link {
  border-color: var(--db-primary);
}

.stat-card__host:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.stat-card__label {
  font-size: 13px;
  color: var(--db-text-sub);
}

.stat-card__value {
  font-size: 24px;
  font-weight: 700;
  line-height: 32px;
  color: var(--db-text-title);
  font-variant-numeric: tabular-nums;
}
</style>
