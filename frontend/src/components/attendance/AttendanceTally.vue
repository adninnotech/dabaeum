<template>
  <!-- 출결 집계 — 누르면 그 상태만 걸러 본다(누가 빠졌는지 바로). 다시 누르면 전체 -->
  <div class="tally" role="group" aria-label="출결 집계로 거르기">
    <button
      v-for="chip in chips"
      :key="chip.value"
      type="button"
      class="tally__chip"
      :class="[`tally__chip--${chip.tone}`, { 'tally__chip--on': modelValue === chip.value }]"
      :aria-pressed="modelValue === chip.value ? 'true' : 'false'"
      @click="$emit('update:modelValue', modelValue === chip.value ? 'ALL' : chip.value)"
    >
      {{ chip.label }} <strong>{{ chip.count }}</strong>
    </button>
    <span v-if="pickedCount" class="tally__picked">{{ pickedCount }}명 선택</span>
  </div>
</template>

<script>
import { STATUS_LABEL } from '@/utils/status';

/** 미기록을 맨 앞에(가장 먼저 챙길 사람). value 'NONE' = 기록 없음 */
const TALLY = [
  { value: 'NONE', label: '미기록', tone: 'none' },
  { value: 'PRESENT', label: STATUS_LABEL.PRESENT, tone: 'present' },
  { value: 'LATE', label: STATUS_LABEL.LATE, tone: 'late' },
  { value: 'ABSENT', label: STATUS_LABEL.ABSENT, tone: 'absent' },
  { value: 'EXCUSED', label: STATUS_LABEL.EXCUSED, tone: 'excused' },
];

/** 행의 집계 키 — 기록이 없으면 'NONE' */
export function tallyKeyOf(row) {
  return row?.status || 'NONE';
}

export default {
  name: 'AttendanceTally',
  props: {
    /** 'ALL' | 'NONE' | PRESENT·LATE·ABSENT·EXCUSED */
    modelValue: { type: String, default: 'ALL' },
    /** 명단 행 { status } */
    rows: { type: Array, default: () => [] },
    pickedCount: { type: Number, default: 0 },
  },
  emits: ['update:modelValue'],
  computed: {
    chips() {
      return TALLY.map((item) => ({
        ...item,
        count: this.rows.filter((row) => tallyKeyOf(row) === item.value).length,
      }));
    },
  },
};
</script>

<style scoped>
.tally {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  flex: 1 1 auto;
}

.tally__chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid var(--db-border);
  border-radius: 999px;
  background: var(--db-bg-white);
  font: inherit;
  font-size: 14px;
  color: var(--db-text-body);
  cursor: pointer;
  font-variant-numeric: tabular-nums;
}

.tally__chip strong {
  font-size: 15px;
}

.tally__chip:hover {
  border-color: var(--db-accent);
}

.tally__chip:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.tally__chip--none strong {
  color: var(--db-text-title);
}

.tally__chip--present strong {
  color: var(--db-status-green-text);
}

.tally__chip--late strong {
  color: var(--db-status-orange-text);
}

.tally__chip--absent strong {
  color: var(--db-status-pink);
}

.tally__chip--excused strong {
  color: var(--db-accent);
}

.tally__chip--on {
  border-color: var(--db-accent);
  background: var(--db-accent-soft);
  font-weight: 700;
}

.tally__picked {
  margin-left: 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-accent);
}
</style>
