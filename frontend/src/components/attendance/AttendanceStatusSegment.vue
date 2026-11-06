<template>
  <!-- 출결 상태 네 칸 — 지금 상태는 색이 채워진 칸(✓), 다른 칸을 누르면 그 상태로 바꾼다.
       명단의 기록된 행(정정)과 정정 창(바꿀 상태 고르기)이 같이 쓴다 -->
  <div class="att-seg" :class="{ 'att-seg--compact': compact }" role="group" :aria-label="label">
    <button
      v-for="option in OPTIONS"
      :key="option.value"
      type="button"
      class="att-seg__btn"
      :class="[`att-seg__btn--${option.tone}`, { 'att-seg__btn--on': option.value === modelValue }]"
      :aria-pressed="option.value === modelValue"
      :disabled="disabled || option.value === locked"
      @click="pick(option.value)"
    >
      <q-icon v-if="option.value === modelValue" name="check" size="16px" aria-hidden="true" />
      <span>{{ option.label }}</span>
    </button>
  </div>
</template>

<script>
import { STATUS_LABEL } from '@/utils/status';

// 기록 단추와 같은 순서·색 계열(출석 초록·지각 주황·결석 분홍·공결 파랑)
const OPTIONS = [
  { value: 'PRESENT', tone: 'present' },
  { value: 'LATE', tone: 'late' },
  { value: 'ABSENT', tone: 'absent' },
  { value: 'EXCUSED', tone: 'excused' },
].map((option) => ({ ...option, label: STATUS_LABEL[option.value] }));

export default {
  name: 'AttendanceStatusSegment',
  props: {
    /** 켜진 칸 — 명단에선 지금 상태, 정정 창에선 바꿀 상태 */
    modelValue: { type: String, default: '' },
    /** 누를 수 없는 칸 — 정정 창에서 '지금 상태'로 다시 바꾸는 건 뜻이 없다 */
    locked: { type: String, default: '' },
    label: { type: String, default: '출결 상태' },
    disabled: { type: Boolean, default: false },
    /** 명단 행 — 조금 좁게 */
    compact: { type: Boolean, default: false },
  },
  emits: ['update:modelValue'],
  data() {
    return { OPTIONS };
  },
  methods: {
    pick(value) {
      if (value === this.modelValue) return;
      this.$emit('update:modelValue', value);
    },
  },
};
</script>

<style scoped>
/* Hallmark · component: segmented control · genre: modern-minimal · theme: project tokens (--db-*)
 * states: default · hover · focus · active · disabled · on(selected)
 */
.att-seg {
  display: inline-grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid var(--db-border);
  border-radius: 8px;
  overflow: hidden;
  background: var(--db-bg-white);
}

.att-seg__btn {
  --tone: var(--db-text-title);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  min-width: 64px;
  min-height: 44px;
  padding: 0 10px;
  border: 0;
  border-left: 1px solid var(--db-border);
  background: transparent;
  font-family: inherit;
  font-size: 15px;
  font-weight: 500;
  color: var(--db-text-body);
  white-space: nowrap;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.att-seg__btn:first-child {
  border-left: 0;
}

.att-seg__btn--present {
  --tone: var(--db-status-green-text);
}

.att-seg__btn--late {
  --tone: var(--db-status-orange-text);
}

.att-seg__btn--absent {
  --tone: var(--db-status-pink);
}

.att-seg__btn--excused {
  --tone: var(--db-status-blue);
}

/* 켜진 칸 — 상태 색 글자 + 옅은 바탕 */
.att-seg__btn--on {
  background: color-mix(in srgb, var(--tone) 14%, transparent);
  color: var(--tone);
  font-weight: 700;
}

.att-seg__btn:focus-visible {
  position: relative;
  outline: 2px solid var(--db-accent);
  outline-offset: -2px;
}

.att-seg__btn:active:not(:disabled) {
  background: color-mix(in srgb, var(--tone) 20%, transparent);
}

.att-seg__btn:disabled {
  cursor: not-allowed;
}

.att-seg__btn:disabled:not(.att-seg__btn--on) {
  color: var(--db-text-placeholder);
}

.att-seg__btn--on:disabled {
  cursor: default;
}

.att-seg--compact .att-seg__btn {
  min-width: 56px;
  padding: 0 8px;
  font-size: 14px;
}

@media (hover: hover) {
  .att-seg__btn:not(:disabled):not(.att-seg__btn--on):hover {
    background: var(--db-bg-subtle);
    color: var(--tone);
  }
}

@media (prefers-reduced-motion: reduce) {
  .att-seg__btn {
    transition: none;
  }
}
</style>
