<template>
  <!-- 출결 [상세] 창 아래 — 정정 이력(GET /attendance/{id} 의 adjustments, 최신순).
       배포 전 서버처럼 키 자체가 없으면 구역을 그리지 않는다 -->
  <section v-if="Array.isArray(adjustments)" class="att-adjust" aria-label="정정 이력">
    <h3 class="att-adjust__title">정정 이력 <span class="att-adjust__count">{{ adjustments.length }}건</span></h3>
    <p v-if="!adjustments.length" class="att-adjust__empty">정정한 적이 없습니다.</p>
    <ol v-else class="att-adjust__list">
      <li v-for="item in adjustments" :key="item.id" class="att-adjust__item">
        <div class="att-adjust__change">
          {{ statusLabel(item.fromStatus) }} → <strong>{{ statusLabel(item.toStatus) }}</strong>
        </div>
        <div class="att-adjust__reason">{{ item.reason || '사유 없음' }}</div>
        <div class="att-adjust__meta">
          {{ item.adjustedByName || '정정자 미상' }} · {{ formatDateTimeKst(item.adjustedAt) }}
        </div>
      </li>
    </ol>
  </section>
</template>

<script>
import { formatDateTimeKst } from '@/utils/datetime';
import { STATUS_LABEL } from '@/utils/status';

export default {
  name: 'AttendanceAdjustmentHistory',
  props: {
    /** [{ id, fromStatus, toStatus, reason, adjustedBy, adjustedByName, adjustedAt }] 또는 undefined(서버에 키 없음) */
    adjustments: {
      type: Array,
      default: undefined,
    },
  },
  methods: {
    formatDateTimeKst,
    statusLabel(status) {
      return STATUS_LABEL[status] || status || '-';
    },
  },
};
</script>

<style scoped>
.att-adjust {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--db-border);
}

.att-adjust__title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 700;
  color: var(--db-text-title);
}

.att-adjust__count {
  font-weight: 400;
  color: var(--db-text-sub);
}

.att-adjust__empty {
  margin: 0;
  font-size: 13px;
  color: var(--db-text-sub);
}

.att-adjust__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.att-adjust__change {
  font-size: 14px;
  color: var(--db-text-body);
}

.att-adjust__reason {
  margin-top: 2px;
  overflow-wrap: anywhere;
  font-size: 13px;
  color: var(--db-text-body);
}

.att-adjust__meta {
  margin-top: 2px;
  font-size: 12px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}
</style>
