<template>
  <!-- 학습배지 발급 통계(계획서 plan.admin.blockchain.dashboard 5번) — GET /admin/statistics/badges?groupBy=DEFINITION.
       기간은 API 기본값(올해 1월 1일~오늘). 필터가 없어 색이 순위 따라 다시 칠해지는 일이 없다 -->
  <section class="donut-panel" aria-labelledby="badge-donut-title">
    <header class="donut-panel__head">
      <h2 id="badge-donut-title" class="donut-panel__title">학습배지 발급 통계</h2>
      <span v-if="periodText" class="donut-panel__period">{{ periodText }}</span>
    </header>

    <div v-if="errorMessage" class="donut-panel__error" role="alert">
      <span>{{ errorMessage }}</span>
      <q-btn flat dense no-caps color="primary" label="다시 시도" @click="load" />
    </div>

    <div v-else class="donut" :class="{ 'donut--loading': loading }">
      <div class="donut__figure">
        <svg viewBox="0 0 160 160" class="donut__svg" role="img" :aria-label="ariaSummary">
          <circle class="donut__track" cx="80" cy="80" :r="RADIUS" />
          <circle
            v-for="slice in arcs"
            :key="slice.key"
            class="donut__arc"
            :class="{ 'donut__arc--dim': activeKey && activeKey !== slice.key }"
            cx="80"
            cy="80"
            :r="RADIUS"
            :stroke="slice.color"
            :stroke-dasharray="`${slice.length} ${CIRCUMFERENCE}`"
            :stroke-dashoffset="-slice.offset"
            @mouseenter="activeKey = slice.key"
            @mouseleave="activeKey = null"
          />
        </svg>
        <div class="donut__center" aria-hidden="true">
          <template v-if="activeSlice">
            <span class="donut__center-label">{{ activeSlice.label }}</span>
            <span class="donut__center-value">{{ formatCount(activeSlice.issued) }}<small>건</small></span>
            <span class="donut__center-note">{{ activeSlice.percentText }}</span>
          </template>
          <template v-else>
            <span class="donut__center-label">총 발급</span>
            <span class="donut__center-value">{{ loading ? '—' : formatCount(totalIssued) }}<small>건</small></span>
          </template>
        </div>
      </div>

      <p v-if="!loading && !slices.length" class="donut__empty">이 기간에 발급된 배지가 없습니다.</p>

      <!-- 범례가 곧 표: 이름·건수·비율이 늘 보여 색만으로 구분하지 않는다 -->
      <ul v-else class="legend">
        <li
          v-for="slice in slices"
          :key="slice.key"
          class="legend__row"
          :class="{ 'legend__row--dim': activeKey && activeKey !== slice.key }"
          tabindex="0"
          @mouseenter="activeKey = slice.key"
          @mouseleave="activeKey = null"
          @focus="activeKey = slice.key"
          @blur="activeKey = null"
        >
          <span class="legend__swatch" :style="{ background: slice.color }" aria-hidden="true" />
          <span class="legend__label" :title="slice.label">{{ slice.label }}</span>
          <span class="legend__count">{{ formatCount(slice.issued) }}건</span>
          <span class="legend__pct">{{ slice.percentText }}</span>
        </li>
      </ul>

      <p v-if="totalRevoked" class="donut__note">같은 기간 폐기 {{ formatCount(totalRevoked) }}건</p>
    </div>
  </section>
</template>

<script>
import { getBadgeStatistics } from '@/services/statistics-api';
import { formatDateKst } from '@/utils/datetime';

const RADIUS = 62;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;
const SLICE_GAP = 2; // 조각 사이 바탕색 틈(px)
const MAX_NAMED = 4; // 검증한 색이 4개 — 5개를 넘으면 상위 4개 + 기타
const SERIES_COLORS = ['var(--db-chart-1)', 'var(--db-chart-2)', 'var(--db-chart-3)', 'var(--db-chart-4)'];
const OTHER_COLOR = 'var(--db-chart-other)';
const numberFormat = new Intl.NumberFormat('ko-KR');

function rowLabel(row) {
  if (row.label) return row.label;
  return row.key === 'NONE' ? '정의 없음(옛 배지)' : row.key;
}

function foldRows(rows) {
  const sorted = rows.filter((row) => row.issued > 0).sort((a, b) => b.issued - a.issued);
  if (sorted.length <= MAX_NAMED + 1) return sorted.map((row) => ({ key: row.key, label: rowLabel(row), issued: row.issued }));
  const named = sorted.slice(0, MAX_NAMED).map((row) => ({ key: row.key, label: rowLabel(row), issued: row.issued }));
  const rest = sorted.slice(MAX_NAMED);
  const other = { key: '__other', label: `기타 ${rest.length}종`, issued: rest.reduce((sum, row) => sum + row.issued, 0) };
  return [...named, other];
}

export default {
  name: 'BadgeIssueDonut',
  data() {
    return {
      RADIUS,
      CIRCUMFERENCE,
      loading: false,
      errorMessage: '',
      stats: null,
      activeKey: null,
    };
  },
  computed: {
    totalIssued() {
      return (this.stats?.rows || []).reduce((sum, row) => sum + (row.issued || 0), 0);
    },
    totalRevoked() {
      return (this.stats?.rows || []).reduce((sum, row) => sum + (row.revoked || 0), 0);
    },
    slices() {
      const total = this.totalIssued;
      return foldRows(this.stats?.rows || []).map((slice, index) => ({
        ...slice,
        color: slice.key === '__other' ? OTHER_COLOR : SERIES_COLORS[index],
        percentText: total ? `${((slice.issued / total) * 100).toFixed(1)}%` : '0%',
      }));
    },
    arcs() {
      const total = this.totalIssued;
      if (!total) return [];
      const gap = this.slices.length > 1 ? SLICE_GAP : 0;
      let offset = 0;
      return this.slices.map((slice) => {
        const full = (slice.issued / total) * CIRCUMFERENCE;
        const arc = { key: slice.key, color: slice.color, offset, length: Math.max(full - gap, 0.5) };
        offset += full;
        return arc;
      });
    },
    activeSlice() {
      return this.slices.find((slice) => slice.key === this.activeKey) || null;
    },
    periodText() {
      if (!this.stats?.from) return '';
      return `${formatDateKst(this.stats.from)} ~ ${formatDateKst(this.stats.to)}`;
    },
    ariaSummary() {
      if (!this.slices.length) return '학습배지 발급 통계 — 발급 없음';
      const parts = this.slices.map((slice) => `${slice.label} ${slice.issued}건`);
      return `학습배지 발급 통계, 총 ${this.totalIssued}건: ${parts.join(', ')}`;
    },
  },
  created() {
    this.load();
  },
  methods: {
    formatCount(value) {
      return numberFormat.format(value || 0);
    },
    async load() {
      this.loading = true;
      this.errorMessage = '';
      try {
        this.stats = await getBadgeStatistics({ groupBy: 'DEFINITION' });
      } catch (error) {
        this.stats = null;
        this.errorMessage = error?.message || '배지 발급 통계를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
/* Hallmark · component: donut chart · genre: modern-minimal · theme: system-managed (--db-* 토큰)
 * states: default · hover(조각↔범례 연동) · focus(범례 행) · loading · error · empty
 * contrast: 3·4번 색 3:1 미만 — 범례에 이름·건수·비율을 늘 보여 보완
 * pre-emit critique: P4 H4 E4 S5 R5 V4
 */
.donut-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  padding: 18px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.donut-panel__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}

.donut-panel__title {
  margin: 0;
  font-size: 17px;
  line-height: 24px;
  font-weight: 700;
  letter-spacing: normal;
  color: var(--db-text-title);
}

.donut-panel__period {
  font-size: 12px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.donut-panel__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 14px;
  color: var(--db-status-pink);
}

.donut {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.donut__figure {
  position: relative;
  width: 168px;
  aspect-ratio: 1;
  align-self: center;
}

.donut__svg {
  display: block;
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.donut__track,
.donut__arc {
  fill: none;
  stroke-width: 20;
}

.donut__track {
  stroke: var(--db-chart-grid);
}

.donut__arc {
  cursor: default;
  transition: opacity 150ms cubic-bezier(0.2, 0, 0, 1);
}

.donut__arc--dim,
.legend__row--dim {
  opacity: 0.35;
}

.donut--loading .donut__arc {
  opacity: 0;
}

.donut__center {
  position: absolute;
  inset: 34px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  pointer-events: none;
}

.donut__center-label {
  max-width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--db-text-sub);
}

.donut__center-value {
  font-size: 24px;
  line-height: 1.2;
  font-weight: 700;
  color: var(--db-text-title);
  font-variant-numeric: tabular-nums;
}

.donut__center-value small {
  margin-left: 2px;
  font-size: 13px;
  font-weight: 600;
}

.donut__center-note {
  font-size: 12px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.donut__empty,
.donut__note {
  margin: 0;
  font-size: 13px;
  color: var(--db-text-sub);
  text-align: center;
}

.legend {
  margin: 0;
  padding: 0;
  list-style: none;
}

.legend__row {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto 52px;
  align-items: center;
  gap: 8px;
  padding: 7px 4px;
  border-top: 1px solid var(--db-border-light);
  border-radius: 4px;
  font-size: 13px;
  color: var(--db-text-body);
  transition: opacity 150ms cubic-bezier(0.2, 0, 0, 1);
}

.legend__row:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: -2px;
}

.legend__swatch {
  width: 10px;
  height: 10px;
  border-radius: 2px;
}

.legend__label {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--db-text-title);
}

.legend__count,
.legend__pct {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.legend__pct {
  color: var(--db-text-sub);
}

@media (prefers-reduced-motion: reduce) {
  .donut__arc,
  .legend__row {
    transition: none;
  }
}
</style>
