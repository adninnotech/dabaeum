<template>
  <div class="gbar" :class="`gbar--${orientation}`">
    <div class="gbar__top">
      <!-- 표에서는 색이 없어 범례가 뜻이 없다 -->
      <ul v-if="!showTable" class="gbar__legend" aria-label="범례">
        <li v-for="(item, index) in series" :key="item.key" class="gbar__legend-item">
          <span class="gbar__swatch" :style="{ background: slotColor(index) }" aria-hidden="true" />
          {{ item.label }}
        </li>
      </ul>
      <button
        v-if="rows.length"
        type="button"
        class="gbar__toggle gbar__toggle--end"
        :aria-pressed="showTable"
        @click="showTable = !showTable"
      >
        {{ showTable ? '차트로 보기' : '표로 보기' }}
      </button>
    </div>

    <p v-if="!rows.length" class="gbar__empty">{{ emptyText }}</p>

    <!-- 표 보기: 모든 값이 툴팁 없이도 닿는다(대비가 낮은 3번째 색의 대안이기도 하다) -->
    <div v-else-if="showTable" class="gbar__table-wrap">
      <table class="gbar__table">
        <thead>
          <tr>
            <th scope="col">{{ categoryLabel }}</th>
            <th v-for="item in series" :key="item.key" scope="col" class="gbar__num">{{ item.label }}</th>
            <th v-for="extra in extraColumns" :key="extra.key" scope="col" class="gbar__num">
              {{ extra.label }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.key">
            <th scope="row">{{ row.label }}</th>
            <td v-for="item in series" :key="item.key" class="gbar__num">{{ format(row[item.key]) }}</td>
            <td v-for="extra in extraColumns" :key="extra.key" class="gbar__num">{{ extra.value(row) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 가로 막대: 이름이 긴 강좌별 -->
    <div v-else-if="orientation === 'horizontal'" ref="plot" class="gbar__plot" @pointerleave="hide">
      <div
        v-for="row in rows"
        :key="row.key"
        class="gbar__hrow"
        tabindex="0"
        :aria-label="ariaLabel(row)"
        @pointerenter="show(row, $event)"
        @focus="show(row, $event)"
        @blur="hide"
      >
        <span class="gbar__hlabel" :title="row.label">{{ row.label }}</span>
        <span class="gbar__hbars">
          <span
            v-for="(item, index) in series"
            :key="item.key"
            class="gbar__hbar"
            :style="{ width: pct(row[item.key]), background: slotColor(index) }"
          />
        </span>
        <!-- 직접 라벨은 첫 계열(신청)만 — 나머지는 툴팁·표에 -->
        <span class="gbar__hvalue">{{ format(row[series[0].key]) }}</span>
      </div>
      <div class="gbar__haxis" aria-hidden="true">
        <span v-for="tick in ticks" :key="tick" :style="{ left: pct(tick) }">{{ format(tick) }}</span>
      </div>
    </div>

    <!-- 세로 막대: 연도·월·구군·연령대 -->
    <div v-else ref="plot" class="gbar__plot gbar__vplot" @pointerleave="hide">
      <div class="gbar__vgrid" aria-hidden="true">
        <span v-for="tick in ticks" :key="tick" class="gbar__vtick" :style="{ bottom: pct(tick) }">
          <span class="gbar__vtick-label">{{ format(tick) }}</span>
        </span>
      </div>
      <div class="gbar__vcols">
        <div
          v-for="row in rows"
          :key="row.key"
          class="gbar__vgroup"
          tabindex="0"
          :aria-label="ariaLabel(row)"
          @pointerenter="show(row, $event)"
          @focus="show(row, $event)"
          @blur="hide"
        >
          <span class="gbar__vbars">
            <span
              v-for="(item, index) in series"
              :key="item.key"
              class="gbar__vbar"
              :style="{ height: pct(row[item.key]), background: slotColor(index) }"
            />
          </span>
          <!-- 폰은 칸이 좁아 '2026-08' 이 잘렸다 — 짧은 라벨(shortLabel)이 있으면 그것 -->
          <span class="gbar__vlabel" :title="row.label">{{ ($q.screen.xs && row.shortLabel) || row.label }}</span>
        </div>
      </div>
    </div>

    <div
      v-if="tip && !showTable"
      class="gbar__tip"
      role="status"
      :style="{ left: `${tip.x}px`, top: `${tip.y}px` }"
    >
      <div class="gbar__tip-title">{{ tip.row.label }}</div>
      <div v-for="(item, index) in series" :key="item.key" class="gbar__tip-row">
        <span class="gbar__tip-key" :style="{ background: slotColor(index) }" />
        <strong class="gbar__tip-value">{{ format(tip.row[item.key]) }}</strong>
        <span class="gbar__tip-name">{{ item.label }}</span>
      </div>
      <div v-for="extra in extraColumns" :key="extra.key" class="gbar__tip-row gbar__tip-row--extra">
        <strong class="gbar__tip-value">{{ extra.value(tip.row) }}</strong>
        <span class="gbar__tip-name">{{ extra.label }}</span>
      </div>
    </div>
  </div>
</template>

<script>
/* 묶음 막대 차트 — 차트 라이브러리 없이 HTML/CSS 로 그린다.
 * 색은 dataviz 기본 범주 팔레트 1~3번(파랑·주황·청록)을 고정 순서로 쓴다(검증: CVD ΔE 9.2, 청록은 대비 3:1 미만 → 표 보기 제공).
 * 값·라벨은 글자색 토큰, 색은 막대만 입는다 */
const SLOT_COLORS = ['var(--gbar-series-1)', 'var(--gbar-series-2)', 'var(--gbar-series-3)'];
const TICK_COUNT = 4;
const TIP_OFFSET = 12;

/** 0 부터 1·2·5 × 10ⁿ 간격의 깔끔한 정수 눈금. 최댓값이 0 이면 [0, 1] */
function niceTicks(max) {
  if (!max || max <= 0) return [0, 1];
  const rough = max / TICK_COUNT;
  const magnitude = 10 ** Math.floor(Math.log10(rough));
  // 건수라 소수 눈금(0.5)은 두지 않는다
  const step = Math.max(1, [1, 2, 5, 10].map((m) => m * magnitude).find((value) => value >= rough));
  const top = Math.ceil(max / step) * step;
  return Array.from({ length: Math.round(top / step) + 1 }, (_, i) => i * step);
}

export default {
  name: 'GroupedBarChart',
  props: {
    /** [{ key, label, [series.key]: number }] */
    rows: { type: Array, default: () => [] },
    /** [{ key, label }] — 최대 3개(고정 색 순서) */
    series: { type: Array, required: true },
    orientation: { type: String, default: 'vertical' },
    categoryLabel: { type: String, default: '구분' },
    /** 표·툴팁에 덧붙일 계산 열 [{ key, label, value(row) }] (예: 수료율) */
    extraColumns: { type: Array, default: () => [] },
    emptyText: { type: String, default: '이 기간에 데이터가 없습니다.' },
  },
  data() {
    return { showTable: false, tip: null };
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
  },
  methods: {
    slotColor(index) {
      return SLOT_COLORS[index] || SLOT_COLORS[SLOT_COLORS.length - 1];
    },
    format(value) {
      return Number(value || 0).toLocaleString('ko-KR');
    },
    pct(value) {
      return `${Math.min(100, ((Number(value) || 0) / this.scaleMax) * 100)}%`;
    },
    ariaLabel(row) {
      const parts = this.series.map((item) => `${item.label} ${this.format(row[item.key])}`);
      const extras = this.extraColumns.map((extra) => `${extra.label} ${extra.value(row)}`);
      return `${row.label}: ${[...parts, ...extras].join(', ')}`;
    },
    // 포인터든 키보드든 같은 툴팁 — 위치는 막대 묶음 기준으로 잡는다
    show(row, event) {
      const host = this.$el.getBoundingClientRect();
      const target = event.currentTarget.getBoundingClientRect();
      const x = Math.min(target.left - host.left + target.width / 2 + TIP_OFFSET, host.width - 200);
      const y = target.top - host.top + TIP_OFFSET;
      this.tip = { row, x: Math.max(0, x), y };
    },
    hide() {
      this.tip = null;
    },
  },
};
</script>

<style scoped>
.gbar {
  /* dataviz 기본 범주 팔레트 1~3 (light) */
  --gbar-series-1: var(--db-chart-1);
  --gbar-series-2: var(--db-chart-2);
  --gbar-series-3: var(--db-chart-3);
  --gbar-grid: var(--db-chart-grid);
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.gbar__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.gbar__legend {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.gbar__legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--db-text-body);
}

.gbar__swatch {
  width: 12px;
  height: 12px;
  border-radius: 3px;
}

.gbar__toggle--end {
  margin-left: auto;
}

.gbar__toggle {
  padding: 4px 10px;
  border: 1px solid var(--db-border);
  border-radius: 6px;
  background: var(--db-bg-white);
  font: inherit;
  font-size: 13px;
  color: var(--db-text-body);
  cursor: pointer;
}

.gbar__toggle:focus-visible,
.gbar__hrow:focus-visible,
.gbar__vgroup:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.gbar__empty {
  margin: 0;
  padding: 40px 0;
  text-align: center;
  font-size: 14px;
  color: var(--db-text-sub);
}

/* ---------- 가로 ---------- */
.gbar__plot {
  position: relative;
}

.gbar__hrow {
  display: grid;
  grid-template-columns: minmax(0, 200px) minmax(0, 1fr) 56px;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
  border-radius: 4px;
  cursor: default;
}

.gbar__hrow:hover {
  background: var(--db-bg-page);
}

.gbar__hlabel {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 13px;
  color: var(--db-text-body);
}

.gbar__hbars {
  display: flex;
  flex-direction: column;
  gap: 2px;
  background-image: linear-gradient(var(--gbar-grid), var(--gbar-grid));
  background-size: 1px 100%;
  background-repeat: no-repeat;
}

.gbar__hbar {
  display: block;
  height: 7px;
  min-width: 0;
  border-radius: 0 4px 4px 0;
}

.gbar__hvalue {
  font-size: 12px;
  font-weight: 600;
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: var(--db-text-body);
}

.gbar__haxis {
  position: relative;
  height: 18px;
  margin: 4px 66px 0 210px;
  border-top: 1px solid var(--gbar-grid);
}

.gbar__haxis span {
  position: absolute;
  top: 2px;
  transform: translateX(-50%);
  font-size: 11px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

/* ---------- 세로 ---------- */
.gbar__vplot {
  height: 260px;
  margin-left: 44px;
}

.gbar__vgrid {
  position: absolute;
  inset: 0 0 28px 0;
}

.gbar__vtick {
  position: absolute;
  left: 0;
  right: 0;
  border-top: 1px solid var(--gbar-grid);
}

.gbar__vtick-label {
  position: absolute;
  right: calc(100% + 6px);
  transform: translateY(-50%);
  font-size: 11px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.gbar__vcols {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: stretch;
  gap: 4px;
  overflow-x: auto;
}

.gbar__vgroup {
  display: flex;
  flex: 1 0 44px;
  flex-direction: column;
  min-width: 44px;
  border-radius: 4px;
  cursor: default;
}

.gbar__vgroup:hover {
  background: color-mix(in srgb, var(--db-bg-page) 70%, transparent);
}

.gbar__vbars {
  display: flex;
  flex: 1;
  align-items: flex-end;
  justify-content: center;
  gap: 2px;
  min-height: 0;
  margin-bottom: 28px;
}

.gbar__vbar {
  display: block;
  width: min(24%, 12px);
  max-width: 24px;
  border-radius: 4px 4px 0 0;
}

.gbar__vlabel {
  position: absolute;
  bottom: 4px;
  width: 44px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  text-align: center;
  font-size: 11px;
  color: var(--db-text-sub);
}

.gbar__vgroup {
  position: relative;
}

.gbar__vgroup .gbar__vlabel {
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
}

/* ---------- 표 ---------- */
.gbar__table-wrap {
  overflow-x: auto;
}

.gbar__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.gbar__table th,
.gbar__table td {
  padding: 8px 10px;
  border-bottom: 1px solid var(--gbar-grid);
  text-align: left;
  color: var(--db-text-body);
  white-space: nowrap;
}

.gbar__table thead th {
  font-weight: 600;
  color: var(--db-text-sub);
}

.gbar__table tbody th {
  font-weight: 500;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.gbar__num {
  text-align: right !important;
  font-variant-numeric: tabular-nums;
}

/* ---------- 툴팁 ---------- */
.gbar__tip {
  position: absolute;
  z-index: 2;
  min-width: 160px;
  max-width: 240px;
  padding: 10px 12px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
  background: var(--db-bg-white);
  box-shadow: 0 4px 16px rgb(0 0 0 / 8%);
  pointer-events: none;
}

.gbar__tip-title {
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.gbar__tip-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  line-height: 20px;
}

.gbar__tip-row--extra {
  margin-top: 4px;
  padding-top: 4px;
  border-top: 1px solid var(--gbar-grid);
}

.gbar__tip-key {
  width: 12px;
  height: 2px;
  border-radius: 1px;
}

.gbar__tip-value {
  font-variant-numeric: tabular-nums;
  color: var(--db-text-title);
}

.gbar__tip-name {
  color: var(--db-text-sub);
}

@media (max-width: 599px) {
  .gbar__hrow {
    grid-template-columns: minmax(0, 110px) minmax(0, 1fr) 44px;
  }

  .gbar__haxis {
    margin: 4px 54px 0 120px;
  }

  /* 칸 최소 44px 이라 10개월이 화면을 넘어 값 있는 달이 오른쪽 밖에 있었다 — 한 화면에 */
  .gbar__vplot {
    margin-left: 32px;
  }

  .gbar__vcols {
    gap: 2px;
  }

  .gbar__vgroup {
    flex: 1 0 18px;
    min-width: 18px;
  }

  .gbar__vbar {
    width: min(28%, 8px);
  }

  .gbar__vgroup .gbar__vlabel {
    overflow: visible;
    text-overflow: clip;
    font-size: 10px;
  }

  /* 표: 수료·수료율이 옆으로 밀려 안 보였다 — 강의명은 줄바꿈, 칸 여백을 줄여 한 화면에 */
  .gbar__table th,
  .gbar__table td {
    padding: 8px 6px;
  }

  .gbar__table tbody th {
    min-width: 96px;
    max-width: none;
    white-space: normal;
    word-break: keep-all;
    overflow-wrap: anywhere;
  }
}
</style>
