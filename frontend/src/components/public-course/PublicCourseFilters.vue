<template>
  <!-- 요일(여럿 고르면 그중 하루라도) · 시간대(시작 시각 기준) · 접수 중 · 무료 — 서버에서 모두 AND.
       웹은 검색줄 아래 한 줄, 폰은 [필터] 창 안에 세로로(stacked) -->
  <div class="pc-filters" :class="{ 'pc-filters--stacked': stacked }">
    <div class="pc-filters__group" role="group" aria-labelledby="pc-filters-day">
      <span id="pc-filters-day" class="pc-filters__label">요일</span>
      <div class="pc-filters__buttons">
        <button
          v-for="day in PUBLIC_OPER_DAYS"
          :key="day"
          type="button"
          class="pc-filters__btn pc-filters__btn--day"
          :class="{ 'pc-filters__btn--on': days.includes(day) }"
          :aria-pressed="days.includes(day) ? 'true' : 'false'"
          @click="$emit('toggle-day', day)"
        >
          {{ day }}
        </button>
      </div>
    </div>
    <div class="pc-filters__group" role="group" aria-labelledby="pc-filters-time">
      <span id="pc-filters-time" class="pc-filters__label">시간대</span>
      <div class="pc-filters__buttons">
        <button
          v-for="item in PUBLIC_TIME_OPTIONS"
          :key="item.value"
          type="button"
          class="pc-filters__btn"
          :class="{ 'pc-filters__btn--on': timeOfDay === item.value }"
          :aria-pressed="timeOfDay === item.value ? 'true' : 'false'"
          :title="item.hint"
          @click="$emit('set', 'time', timeOfDay === item.value ? '' : item.value)"
        >
          {{ item.label }}
          <span v-if="stacked" class="pc-filters__hint">{{ item.hint }}</span>
        </button>
      </div>
    </div>
    <div class="pc-filters__toggles">
      <q-toggle
        :model-value="receiptOpen"
        dense
        label="접수 중만"
        @update:model-value="$emit('set', 'open', $event ? '1' : '')"
      />
      <q-toggle
        :model-value="freeOnly"
        dense
        label="무료만"
        @update:model-value="$emit('set', 'free', $event ? '1' : '')"
      />
    </div>
  </div>
</template>

<script>
import { PUBLIC_OPER_DAYS, PUBLIC_TIME_OPTIONS } from '@/utils/public-course';

export default {
  name: 'PublicCourseFilters',
  props: {
    days: { type: Array, default: () => [] },
    timeOfDay: { type: String, default: '' },
    receiptOpen: { type: Boolean, default: false },
    freeOnly: { type: Boolean, default: false },
    /** 폰 [필터] 창 — 묶음을 세로로 쌓고 시간대 설명을 보인다 */
    stacked: { type: Boolean, default: false },
  },
  /** set(key, value): 주소 조건 하나를 바꾼다(빈 값이면 지움) */
  emits: ['toggle-day', 'set'],
  data() {
    return { PUBLIC_OPER_DAYS, PUBLIC_TIME_OPTIONS };
  },
};
</script>

<style scoped>
.pc-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 24px;
}

.pc-filters__group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pc-filters__label {
  font-size: 14px;
  font-weight: 500;
  color: var(--db-text-sub);
  white-space: nowrap;
}

.pc-filters__buttons {
  display: flex;
  gap: 4px;
}

.pc-filters__btn {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid var(--db-border);
  border-radius: 6px;
  background: var(--db-bg-white);
  font: inherit;
  font-size: 14px;
  color: var(--db-text-body);
  cursor: pointer;
  white-space: nowrap;
}

.pc-filters__btn--day {
  width: 40px;
  padding: 0;
}

.pc-filters__btn:hover {
  border-color: var(--db-accent);
}

.pc-filters__btn:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.pc-filters__btn--on {
  border-color: var(--db-accent);
  background: var(--db-accent-soft);
  color: var(--db-accent);
  font-weight: 700;
}

.pc-filters__hint {
  font-size: 13px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.pc-filters__toggles {
  display: flex;
  gap: 16px;
  margin-left: auto;
  white-space: nowrap;
}

/* 폰 [필터] 창 — 이름표를 위에, 버튼은 폭을 나눠 쓴다 */
.pc-filters--stacked {
  flex-direction: column;
  align-items: stretch;
  gap: 20px;
}

.pc-filters--stacked .pc-filters__group {
  flex-direction: column;
  align-items: stretch;
}

.pc-filters--stacked .pc-filters__btn {
  flex: 1 1 0;
  width: auto;
  min-height: 40px;
  padding: 4px;
}

.pc-filters--stacked .pc-filters__toggles {
  flex-direction: column;
  gap: 16px;
  margin-left: 0;
}
</style>
