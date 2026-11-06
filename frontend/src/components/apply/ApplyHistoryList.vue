<template>
  <!-- 신청 이력 — 넓은 표 대신 줄마다 카드(폰에서 표가 넘쳤다). 상세 창 없이 한 줄에 다 -->
  <section class="apply-history" aria-labelledby="apply-history-title">
    <h2 id="apply-history-title" class="apply-history__title">내 신청 이력</h2>
    <p v-if="error" class="apply-history__error" role="alert">{{ error }}</p>
    <p v-else-if="!loading && !items.length" class="apply-history__empty">신청 이력이 없습니다.</p>
    <ul v-else class="apply-history__list">
      <li v-for="item in items" :key="item.id" class="apply-history__item">
        <div class="apply-history__top">
          <span class="apply-history__name">{{ item.title }}</span>
          <StatusBadge :status="item.status" />
        </div>
        <span class="apply-history__meta">{{ item.meta }}</span>
        <p v-if="item.note" class="apply-history__note" :class="{ 'apply-history__note--bad': item.status === 'REJECTED' }">
          {{ item.note }}
        </p>
      </li>
    </ul>
    <q-inner-loading :showing="loading && !items.length" />
  </section>
</template>

<script>
import StatusBadge from '@/components/StatusBadge.vue';

export default {
  name: 'ApplyHistoryList',
  components: { StatusBadge },
  props: {
    /** [{ id, title, status, meta, note }] */
    items: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    error: { type: String, default: '' },
  },
};
</script>

<style scoped>
.apply-history {
  position: relative;
  padding: 20px;
  border: 1px solid var(--db-border);
  border-radius: 12px;
  background: var(--db-bg-white);
}

.apply-history__title {
  margin: 0 0 12px;
  font-size: 18px;
  font-weight: 700;
  color: var(--db-text-title);
}

.apply-history__empty,
.apply-history__error {
  margin: 0;
  font-size: 14px;
  color: var(--db-text-sub);
}

.apply-history__error {
  color: var(--db-status-pink);
}

.apply-history__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.apply-history__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.apply-history__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.apply-history__name {
  min-width: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.apply-history__meta {
  font-size: 14px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.apply-history__note {
  margin: 4px 0 0;
  font-size: 14px;
  color: var(--db-text-body);
  word-break: keep-all;
}

.apply-history__note--bad {
  color: var(--db-status-pink);
}

@media (max-width: 599px) {
  .apply-history {
    padding: 16px;
  }
}
</style>
