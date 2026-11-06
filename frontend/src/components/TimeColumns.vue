<template>
  <!-- 시(00~23)·분(5분 단위) 목록 — 날짜·시간 칸과 시간 칸이 같이 쓴다.
       24시간 시계판은 안쪽·바깥 고리를 겨눠야 했다 — 시·분을 목록에서 한 번씩 누른다 -->
  <div ref="root" class="time-cols">
    <div class="time-cols__col" role="group" aria-label="시">
      <div class="time-cols__head" aria-hidden="true">시</div>
      <button
        v-for="item in HOURS"
        :key="item"
        type="button"
        class="time-cols__cell"
        :aria-pressed="item === hour"
        :aria-label="`${item}시`"
        :data-anchor="item === DEFAULT_HOUR || undefined"
        @click="$emit('pick', { hour: item, minute: minute || '00' })"
      >
        {{ item }}
      </button>
    </div>
    <div class="time-cols__col" role="group" aria-label="분">
      <div class="time-cols__head" aria-hidden="true">분</div>
      <button
        v-for="item in MINUTES"
        :key="item"
        type="button"
        class="time-cols__cell"
        :aria-pressed="item === minute"
        :aria-label="`${item}분`"
        @click="$emit('pick', { hour: hour || DEFAULT_HOUR, minute: item })"
      >
        {{ item }}
      </button>
    </div>
  </div>
</template>

<script>
/* 분만 먼저 골랐을 때 채우는 시 */
const DEFAULT_HOUR = '09';
const HOURS = Array.from({ length: 24 }, (_, i) => String(i).padStart(2, '0'));
/* 수업·출결 시각은 5분 단위로 고른다. 칸에 직접 치면 아무 분이나 된다 */
const MINUTES = Array.from({ length: 12 }, (_, i) => String(i * 5).padStart(2, '0'));

export default {
  name: 'TimeColumns',
  props: {
    /** 'HH' 또는 '' */
    hour: {
      type: String,
      default: '',
    },
    /** 'mm' 또는 '' */
    minute: {
      type: String,
      default: '',
    },
  },
  emits: ['pick'],
  data() {
    return { HOURS, MINUTES, DEFAULT_HOUR };
  },
  methods: {
    // 고른 시·분이 목록 가운데 보이게 — 팝업이 열린 뒤 부모가 부른다.
    // 폰(대화상자)은 여는 애니메이션 중에 크기가 잡히므로 한 프레임 뒤에 재고,
    // offsetTop 은 기준 요소가 열이 아니라 어긋나서 화면 좌표 차이로 옮긴다
    scrollToSelected() {
      requestAnimationFrame(() => {
        this.$refs.root?.querySelectorAll('.time-cols__col').forEach((col) => {
          // 아직 안 골랐으면 수업이 많은 오전 9시 근처를 보여 준다(00시부터 내려가지 않게)
          const cell = col.querySelector('[aria-pressed="true"]') || col.querySelector('[data-anchor]');
          if (!cell) return;
          const offset = cell.getBoundingClientRect().top - col.getBoundingClientRect().top;
          col.scrollTop += offset - (col.clientHeight - cell.clientHeight) / 2;
        });
      });
    },
  },
};
</script>

<style scoped>
.time-cols {
  display: flex;
}

.time-cols__col {
  display: flex;
  flex-direction: column;
  width: 64px;
  height: 300px;
  padding: 0 6px 6px;
  overflow-y: auto;
  scrollbar-width: thin;
}

.time-cols__col + .time-cols__col {
  border-left: 1px solid var(--db-border);
}

.time-cols__head {
  position: sticky;
  top: 0;
  padding: 8px 0 6px;
  font-size: 12px;
  text-align: center;
  color: var(--db-text-sub);
  background: var(--db-bg-white);
}

.time-cols__cell {
  flex: 0 0 auto;
  height: 32px;
  font: inherit;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  color: var(--db-text-body);
  background: none;
  border: 0;
  border-radius: 6px;
  cursor: pointer;
}

.time-cols__cell:hover {
  background: var(--db-bg-subtle);
}

.time-cols__cell:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: -2px;
}

.time-cols__cell[aria-pressed='true'] {
  font-weight: 700;
  color: var(--db-on-color);
  background: var(--q-primary);
}

/* 폰(대화상자)에서는 가로를 반씩 나눠 쓴다 */
@media (max-width: 599px) {
  .time-cols__col {
    flex: 1 1 50%;
    width: auto;
    height: 180px;
  }
}
</style>
