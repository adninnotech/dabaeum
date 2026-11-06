<template>
  <div class="trend">
    <div class="trend__top">
      <ul v-if="!showTable" class="trend__legend" aria-label="범례">
        <li v-for="(item, index) in series" :key="item.key" class="trend__legend-item">
          <span
            class="trend__swatch"
            :style="{ background: slotColor(index) }"
            aria-hidden="true"
          />
          {{ item.label }}
        </li>
      </ul>
      <button
        v-if="rows.length"
        type="button"
        class="trend__toggle"
        :aria-pressed="showTable"
        @click="showTable = !showTable"
      >
        {{ showTable ? '차트로 보기' : '표로 보기' }}
      </button>
    </div>

    <p v-if="!rows.length" class="trend__empty">{{ emptyText }}</p>

    <div v-else-if="showTable" class="trend__table-wrap">
      <table class="trend__table">
        <thead>
          <tr>
            <th scope="col">{{ categoryLabel }}</th>
            <th v-for="item in series" :key="item.key" scope="col" class="trend__num">
              {{ item.label }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.period">
            <th scope="row">{{ row.period }}</th>
            <td v-for="item in series" :key="item.key" class="trend__num">
              {{ format(row[item.key]) }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 선은 SVG(가로세로 비율 무시, 선 두께 고정), 눈금·날짜 글자는 HTML 로 얹는다 — 글자가 늘어나지 않게 -->
    <div v-else class="trend__frame">
      <ol class="trend__ticks" aria-hidden="true">
        <li v-for="tick in ticks" :key="tick" class="trend__tick" :style="{ bottom: pct(tick) }">
          {{ format(tick) }}
        </li>
      </ol>
      <div
        ref="plot"
        class="trend__plot"
        tabindex="0"
        role="img"
        :aria-label="summaryLabel"
        @pointermove="onPointer"
        @pointerleave="active = -1"
        @keydown="onKeydown"
        @blur="active = -1"
      >
        <span
          v-for="tick in ticks"
          :key="tick"
          class="trend__grid"
          :style="{ bottom: pct(tick) }"
          aria-hidden="true"
        />
        <svg class="trend__svg" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
          <polyline
            v-for="(item, index) in series"
            :key="item.key"
            :points="points(item.key)"
            fill="none"
            :stroke="slotColor(index)"
            stroke-width="2"
            stroke-linejoin="round"
            vector-effect="non-scaling-stroke"
          />
        </svg>
        <template v-if="active >= 0">
          <span class="trend__cursor" :style="{ left: xPct(active) }" aria-hidden="true" />
          <span
            v-for="(item, index) in series"
            :key="item.key"
            class="trend__dot"
            :style="{
              left: xPct(active),
              bottom: pct(rows[active][item.key]),
              background: slotColor(index),
            }"
            aria-hidden="true"
          />
        </template>
        <div
          v-if="activeRow"
          class="trend__tip"
          role="status"
          :class="tipSide"
          :style="{ left: xPct(active) }"
        >
          <div class="trend__tip-title">{{ activeRow.period }}</div>
          <div v-for="(item, index) in series" :key="item.key" class="trend__tip-row">
            <span class="trend__tip-key" :style="{ background: slotColor(index) }" />
            <strong class="trend__tip-value">{{ format(activeRow[item.key]) }}</strong>
            <span class="trend__tip-name">{{ item.label }}</span>
          </div>
        </div>
      </div>
      <ol class="trend__xlabels" aria-hidden="true">
        <li
          v-for="label in xLabels"
          :key="label.index"
          class="trend__xlabel"
          :style="{ left: xPct(label.index) }"
        >
          {{ label.text }}
        </li>
      </ol>
    </div>
  </div>
</template>

<script>
/* 이용 추이 선 그래프 — 차트 라이브러리 없이 SVG.
 * 색은 dataviz 범주 팔레트 1~4번 고정 순서(tokens.scss --db-chart-1~4, 4개 함께 검증). 3·4번은 대비 3:1 미만 → 표 보기 제공.
 * 키보드: 그래프에 초점을 두고 ←→ 로 날짜를 옮기면 툴팁이 따라간다 */
const SLOT_COLORS = [
  'var(--db-chart-1)',
  'var(--db-chart-2)',
  'var(--db-chart-3)',
  'var(--db-chart-4)',
];
const TICK_COUNT = 4;
const MAX_X_LABELS = 7;

/** 0 부터 1·2·5 × 10ⁿ 간격의 정수 눈금 (GroupedBarChart 와 같은 규칙) */
function niceTicks(max) {
  if (!max || max <= 0) return [0, 1];
  const rough = max / TICK_COUNT;
  const magnitude = 10 ** Math.floor(Math.log10(rough));
  const step = Math.max(
    1,
    [1, 2, 5, 10].map((m) => m * magnitude).find((value) => value >= rough),
  );
  const top = Math.ceil(max / step) * step;
  return Array.from({ length: Math.round(top / step) + 1 }, (_, i) => i * step);
}

/** 날짜 축 글자 — 일별 '10.3', 월별 '26.10' */
function shortPeriod(period) {
  const [year, month, day] = period.split('-');
  return day ? `${Number(month)}.${Number(day)}` : `${year.slice(2)}.${Number(month)}`;
}

export default {
  name: 'UsageTrendChart',
  props: {
    /** [{ period: 'YYYY-MM-DD' | 'YYYY-MM', [series.key]: number }] — 빈 날도 0 으로 온다 */
    rows: { type: Array, default: () => [] },
    /** [{ key, label }] — 최대 4개(고정 색 순서) */
    series: { type: Array, required: true },
    categoryLabel: { type: String, default: '날짜' },
    emptyText: { type: String, default: '이 기간에 데이터가 없습니다.' },
  },
  data() {
    return { showTable: false, active: -1 };
  },
  computed: {
    ticks() {
      const max = Math.max(
        0,
        ...this.rows.flatMap((row) => this.series.map((item) => Number(row[item.key]) || 0)),
      );
      return niceTicks(max);
    },
    scaleMax() {
      return this.ticks[this.ticks.length - 1] || 1;
    },
    xLabels() {
      const count = this.rows.length;
      const step = Math.max(1, Math.ceil(count / MAX_X_LABELS));
      return this.rows
        .map((row, index) => ({ index, text: shortPeriod(row.period) }))
        .filter(({ index }) => index % step === 0);
    },
    activeRow() {
      return this.rows[this.active] || null;
    },
    // 오른쪽 끝에서는 툴팁을 왼쪽으로 띄운다
    tipSide() {
      return this.active > (this.rows.length - 1) / 2 ? 'trend__tip--left' : 'trend__tip--right';
    },
    summaryLabel() {
      const first = this.rows[0]?.period;
      const last = this.rows[this.rows.length - 1]?.period;
      return `${first} ~ ${last} 추이 선 그래프. 좌우 화살표로 날짜별 값을 읽습니다.`;
    },
  },
  watch: {
    rows() {
      this.active = -1;
    },
  },
  methods: {
    slotColor(index) {
      return SLOT_COLORS[index] || SLOT_COLORS[SLOT_COLORS.length - 1];
    },
    format(value) {
      return Number(value || 0).toLocaleString('ko-KR');
    },
    ratio(value) {
      return Math.min(1, (Number(value) || 0) / this.scaleMax);
    },
    pct(value) {
      return `${this.ratio(value) * 100}%`;
    },
    xRatio(index) {
      return this.rows.length > 1 ? index / (this.rows.length - 1) : 0.5;
    },
    xPct(index) {
      return `${this.xRatio(index) * 100}%`;
    },
    points(key) {
      return this.rows
        .map((row, index) => `${this.xRatio(index) * 100},${100 - this.ratio(row[key]) * 100}`)
        .join(' ');
    },
    onPointer(event) {
      const rect = this.$refs.plot.getBoundingClientRect();
      if (!rect.width || !this.rows.length) return;
      const ratio = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width));
      this.active = Math.round(ratio * (this.rows.length - 1));
    },
    onKeydown(event) {
      const last = this.rows.length - 1;
      const moves = { ArrowRight: 1, ArrowLeft: -1 };
      if (event.key === 'Home' || event.key === 'End') {
        event.preventDefault();
        this.active = event.key === 'Home' ? 0 : last;
        return;
      }
      if (!(event.key in moves)) return;
      event.preventDefault();
      const start = this.active < 0 ? (moves[event.key] > 0 ? -1 : last + 1) : this.active;
      this.active = Math.min(last, Math.max(0, start + moves[event.key]));
    },
  },
};
</script>

<style scoped>
.trend {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.trend__top {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.trend__legend {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.trend__legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--db-text-body);
}

.trend__swatch {
  width: 12px;
  height: 3px;
  border-radius: 2px;
}

.trend__toggle {
  margin-left: auto;
  padding: 4px 10px;
  border: 1px solid var(--db-border);
  border-radius: 6px;
  background: var(--db-bg-white);
  font: inherit;
  font-size: 13px;
  color: var(--db-text-body);
  cursor: pointer;
}

.trend__toggle:focus-visible,
.trend__plot:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.trend__empty {
  margin: 0;
  padding: 40px 0;
  text-align: center;
  font-size: 14px;
  color: var(--db-text-sub);
}

.trend__frame {
  position: relative;
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  grid-template-rows: 220px 24px;
}

.trend__ticks,
.trend__xlabels {
  position: relative;
  margin: 0;
  padding: 0;
  list-style: none;
}

.trend__tick {
  position: absolute;
  right: 8px;
  transform: translateY(50%);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  color: var(--db-text-sub);
}

.trend__plot {
  position: relative;
  border-left: 1px solid var(--db-chart-grid);
  border-bottom: 1px solid var(--db-chart-grid);
  cursor: crosshair;
}

.trend__grid {
  position: absolute;
  left: 0;
  right: 0;
  height: 1px;
  background: var(--db-chart-grid);
}

.trend__svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: visible;
}

.trend__cursor {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 1px;
  background: var(--db-border);
}

.trend__dot {
  position: absolute;
  width: 8px;
  height: 8px;
  border: 2px solid var(--db-bg-white);
  border-radius: 50%;
  transform: translate(-50%, 50%);
}

.trend__xlabels {
  grid-column: 2;
}

.trend__xlabel {
  position: absolute;
  top: 6px;
  transform: translateX(-50%);
  font-size: 11px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
  color: var(--db-text-sub);
}

.trend__tip {
  position: absolute;
  top: 8px;
  z-index: 2;
  min-width: 140px;
  padding: 10px 12px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
  background: var(--db-bg-white);
  box-shadow: 0 4px 16px rgb(0 0 0 / 8%);
  pointer-events: none;
}

.trend__tip--right {
  transform: translateX(12px);
}

.trend__tip--left {
  transform: translateX(calc(-100% - 12px));
}

.trend__tip-title {
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--db-text-title);
}

.trend__tip-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  line-height: 20px;
}

.trend__tip-key {
  width: 12px;
  height: 3px;
  border-radius: 2px;
}

.trend__tip-value {
  font-variant-numeric: tabular-nums;
  color: var(--db-text-title);
}

.trend__tip-name {
  color: var(--db-text-sub);
}

.trend__table-wrap {
  max-height: 360px;
  overflow: auto;
}

.trend__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.trend__table th,
.trend__table td {
  padding: 6px 10px;
  border-bottom: 1px solid var(--db-chart-grid);
  text-align: left;
  font-weight: 400;
  color: var(--db-text-body);
}

.trend__table thead th {
  position: sticky;
  top: 0;
  background: var(--db-bg-white);
  font-weight: 600;
  color: var(--db-text-title);
}

.trend__num {
  text-align: right !important;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 599px) {
  .trend__frame {
    grid-template-columns: 36px minmax(0, 1fr);
    grid-template-rows: 180px 24px;
  }
}
</style>
