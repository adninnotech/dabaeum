<template>
  <!-- 수강 신청 한 건의 승인·거절 — 표 칸과 폰 카드가 같이 쓴다 -->
  <div v-if="pending" class="decision">
    <template v-if="row.canDecide">
      <q-btn
        flat
        dense
        no-caps
        color="primary"
        label="승인"
        :loading="busyId === row.id"
        :disable="!!busyId"
        @click="$emit('approve', row)"
      />
      <q-btn
        flat
        dense
        no-caps
        color="negative"
        label="거절"
        :disable="!!busyId"
        @click="$emit('reject', row)"
      />
    </template>
    <span v-else class="decision__note">주강사만 처리할 수 있어요</span>
  </div>
</template>

<script>
import { DECIDABLE_STATUSES } from '@/utils/enrollment-decision';

export default {
  name: 'EnrollmentDecisionButtons',
  props: {
    /** { id, status, canDecide } */
    row: { type: Object, required: true },
    busyId: { type: String, default: null },
  },
  emits: ['approve', 'reject'],
  computed: {
    pending() {
      return DECIDABLE_STATUSES.includes(this.row.status);
    },
  },
};
</script>

<style scoped>
.decision {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.decision__note {
  font-size: 12px;
  color: var(--db-text-sub);
  word-break: keep-all;
}
</style>
