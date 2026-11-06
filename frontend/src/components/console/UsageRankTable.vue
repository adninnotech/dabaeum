<template>
  <div class="rank" :class="{ 'rank--busy': loading }">
    <h3 class="rank__title">{{ title }}</h3>
    <AppErrorBanner v-if="error" :message="error" />
    <p v-else-if="!rows.length" class="rank__empty">{{ emptyText }}</p>
    <div v-else class="rank__wrap">
      <table class="rank__table">
        <thead>
          <tr>
            <th scope="col" class="rank__no">순위</th>
            <th scope="col">{{ nameLabel }}</th>
            <th scope="col" class="rank__num">{{ valueLabel }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, index) in rows" :key="row.key">
            <td class="rank__no">{{ index + 1 }}</td>
            <th scope="row" class="rank__name">
              {{ row.name }}
              <span v-if="row.note" class="rank__note">{{ row.note }}</span>
            </th>
            <td class="rank__num">{{ format(row.value) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';

/* 이용 통계 순위 표(검색어·배너) — rows: [{ key, name, value, note? }] 를 받은 순서대로 그린다 */
export default {
  name: 'UsageRankTable',
  components: { AppErrorBanner },
  props: {
    title: { type: String, required: true },
    rows: { type: Array, default: () => [] },
    nameLabel: { type: String, required: true },
    valueLabel: { type: String, required: true },
    emptyText: { type: String, default: '이 기간에 데이터가 없습니다.' },
    loading: { type: Boolean, default: false },
    error: { type: String, default: '' },
  },
  methods: {
    format(value) {
      return Number(value || 0).toLocaleString('ko-KR');
    },
  },
};
</script>

<style scoped>
.rank {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  transition: opacity 120ms ease-out;
}

.rank--busy {
  opacity: 0.5;
}

.rank__title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-title);
}

.rank__empty {
  margin: 0;
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.rank__wrap {
  max-height: 420px;
  overflow: auto;
}

.rank__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.rank__table th,
.rank__table td {
  padding: 6px 8px;
  border-bottom: 1px solid var(--db-chart-grid);
  text-align: left;
  font-weight: 400;
  color: var(--db-text-body);
}

.rank__table thead th {
  position: sticky;
  top: 0;
  background: var(--db-bg-white);
  font-weight: 600;
  color: var(--db-text-title);
}

.rank__no {
  width: 48px;
  font-variant-numeric: tabular-nums;
  color: var(--db-text-sub) !important;
}

.rank__name {
  overflow-wrap: anywhere;
}

.rank__note {
  margin-left: 4px;
  font-size: 12px;
  color: var(--db-text-sub);
}

.rank__num {
  width: 96px;
  text-align: right !important;
  font-variant-numeric: tabular-nums;
}

@media (prefers-reduced-motion: reduce) {
  .rank {
    transition: none;
  }
}
</style>
