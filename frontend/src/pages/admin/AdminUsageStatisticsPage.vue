<template>
  <q-page class="usage">
    <h1 class="usage__title">이용 통계</h1>

    <section class="usage__filters" aria-label="통계 조건">
      <div class="usage__period" role="group" aria-label="기간">
        <AppDateInput
          v-model="from"
          outlined
          dense
          hide-bottom-space
          label="시작일"
          class="usage__date"
          :max="to || lastDay"
        />
        <span class="usage__sep" aria-hidden="true">~</span>
        <!-- 오늘은 집계 전이라 고를 수 없다(마지막 집계일 뒤는 422) -->
        <AppDateInput
          v-model="to"
          outlined
          dense
          hide-bottom-space
          label="종료일"
          class="usage__date"
          :min="from || ''"
          :max="lastDay"
        />
      </div>
      <div class="usage__seg" role="group" aria-label="묶음 단위">
        <button
          v-for="option in INTERVALS"
          :key="option.value"
          type="button"
          class="usage__seg-btn"
          :class="{ 'usage__seg-btn--on': interval === option.value }"
          :aria-pressed="interval === option.value"
          @click="setInterval(option.value)"
        >
          {{ option.label }}
        </button>
      </div>
      <q-btn
        flat
        no-caps
        dense
        icon="o_download"
        label="CSV 내려받기"
        class="usage__csv"
        :loading="downloading"
        :disable="!applied"
        @click="downloadCsv"
      />
      <div class="usage__foot">
        <div class="usage__presets" role="group" aria-label="빠른 기간">
          <button
            v-for="preset in presets"
            :key="preset.label"
            type="button"
            class="usage__preset"
            :aria-pressed="applied?.from === preset.from && applied?.to === preset.to"
            @click="setPeriod(preset)"
          >
            {{ preset.label }}
          </button>
        </div>
        <q-btn
          unelevated
          no-caps
          color="primary"
          label="조회"
          class="usage__apply"
          :loading="loading"
          @click="apply"
        />
      </div>
    </section>
    <p class="usage__hint">
      기간은 서울 날짜 기준이며 양끝을 포함합니다. 마지막 집계일은 {{ lastDay }}입니다. 일별은 최대
      {{ MAX_DAILY_DAYS }}일까지 볼 수 있습니다.
    </p>

    <AppErrorBanner v-if="error" :message="error" />
    <template v-else>
      <!-- ① 요약 -->
      <div class="usage__tiles" :class="{ 'usage__tiles--busy': loading }">
        <div v-for="tile in tiles" :key="tile.key" class="usage__tile">
          <div class="usage__tile-label">{{ tile.label }}</div>
          <div class="usage__tile-value">
            {{ tile.value }}<span class="usage__tile-unit">{{ tile.unit }}</span>
          </div>
          <div class="usage__tile-delta" :class="`usage__tile-delta--${tile.delta.tone}`">
            {{ tile.delta.text }}
          </div>
        </div>
      </div>
      <p v-if="stats?.previousFrom" class="usage__hint">
        앞 기간({{ stats.previousFrom }} ~ {{ stats.previousTo }})과 비교합니다.
      </p>

      <!-- ② 추이 -->
      <section class="usage__card">
        <h2 class="usage__card-title">추이</h2>
        <div :class="{ usage__busy: loading }">
          <UsageTrendChart
            :rows="trendRows"
            :series="trendSeries"
            :category-label="interval === 'MONTH' ? '월' : '날짜'"
          />
        </div>
      </section>

      <!-- ③ 단계별 규모 — 같은 사람을 따라간 값이 아니다(전환율 아님) -->
      <section class="usage__card">
        <h2 class="usage__card-title">단계별 규모</h2>
        <p class="usage__card-note">
          단계마다 따로 센 규모입니다. 같은 사람이 다음 단계로 넘어간 비율이 아닙니다.
        </p>
        <div :class="{ usage__busy: loading }">
          <GroupedBarChart
            :rows="stepRows"
            :series="STEP_SERIES"
            orientation="horizontal"
            category-label="단계"
          />
        </div>
      </section>
    </template>

    <template v-if="applied">
      <!-- ④ 성별·연령대 -->
      <section class="usage__card">
        <h2 class="usage__card-title">성별·연령대</h2>
        <UsageDemographics :from="applied.from" :to="applied.to" />
      </section>

      <!-- ⑤ 검색어 -->
      <section class="usage__card">
        <h2 class="usage__card-title">검색어</h2>
        <p class="usage__card-note">
          하루에 3명 이상이 검색한 검색어만 보입니다. 이용이 적은 기간에는 목록이 비어 있을 수
          있습니다.
        </p>
        <div class="usage__pair">
          <UsageRankTable
            title="인기 검색어 Top 20"
            name-label="검색어"
            value-label="검색 연인원"
            empty-text="3명 이상이 검색한 검색어가 없습니다."
            :rows="keywordRows.POPULAR"
            :loading="keywordLoading"
            :error="keywordError.POPULAR"
          />
          <UsageRankTable
            title="결과 0건 검색어 Top 20"
            name-label="검색어"
            value-label="결과 0건 연인원"
            empty-text="결과가 0건이었던 검색어가 없습니다."
            :rows="keywordRows.ZERO_RESULT"
            :loading="keywordLoading"
            :error="keywordError.ZERO_RESULT"
          />
        </div>
      </section>

      <!-- ⑥ 배너 -->
      <section class="usage__card">
        <h2 class="usage__card-title">배너별 클릭</h2>
        <UsageRankTable
          title="클릭 연인원이 많은 순"
          name-label="배너"
          value-label="클릭 연인원"
          empty-text="이 기간에 배너를 클릭한 사람이 없습니다."
          :rows="bannerRows"
          :loading="bannerLoading"
          :error="bannerError"
        />
      </section>
    </template>

    <p class="usage__footnote">
      어제까지 집계합니다. 같은 사람의 같은 날 행동은 한 번으로 셉니다. 연인원은 날마다 센 인원을
      더한 값으로, 실제 회원 수와 다릅니다.
    </p>
  </q-page>
</template>

<script>
import AppDateInput from '@/components/AppDateInput.vue';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import GroupedBarChart from '@/components/console/GroupedBarChart.vue';
import UsageDemographics from '@/components/console/UsageDemographics.vue';
import UsageRankTable from '@/components/console/UsageRankTable.vue';
import UsageTrendChart from '@/components/console/UsageTrendChart.vue';
import {
  downloadUsageStatisticsCsv,
  getUsageBannerClicks,
  getUsageSearchKeywords,
  getUsageStatistics,
} from '@/services/usage-api';
import { todayString } from '@/utils/datetime';

const MAX_DAILY_DAYS = 366;
const MAX_YEARS = 5;
const KEYWORD_LIMIT = 20;
const KEYWORD_TYPES = ['POPULAR', 'ZERO_RESULT'];

const INTERVALS = [
  { value: 'DAY', label: '일별' },
  { value: 'MONTH', label: '월별' },
];
// 수강 신청만 신청 1건 단위, 나머지는 하루 한 사람 1회(명).
// 여러 날을 더한 값(요약·단계·월별)은 사람 수가 아니라 연인원이다 — sumLabel
const METRICS = [
  { key: 'visitors', label: '방문자', sumLabel: '방문 연인원', step: '방문 연인원', unit: '명' },
  { key: 'searches', label: '검색', sumLabel: '검색 연인원', step: '검색 연인원', unit: '명' },
  {
    key: 'courseViews',
    label: '강좌 조회',
    sumLabel: '강좌 조회 연인원',
    step: '조회 연인원',
    unit: '명',
  },
  { key: 'enrollments', label: '수강 신청', sumLabel: '수강 신청', step: '신청', unit: '건' },
];
const STEP_SERIES = [{ key: 'count', label: '규모' }];

/** 'YYYY-MM-DD' 에 n 일을 더한다(달력 날짜 계산이라 UTC 로) */
function addDays(iso, n) {
  const date = new Date(`${iso}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + n);
  return date.toISOString().slice(0, 10);
}

function daysBetween(from, to) {
  return Math.round((new Date(`${to}T00:00:00Z`) - new Date(`${from}T00:00:00Z`)) / 86400000) + 1;
}

/** 빠른 기간 — 끝은 모두 마지막 집계일 */
function buildPresets(lastDay) {
  return [7, 30, 90, 365].map((days) => ({
    label: days === 365 ? '최근 1년' : `최근 ${days}일`,
    from: addDays(lastDay, 1 - days),
    to: lastDay,
  }));
}

function formatCount(value) {
  return Number(value || 0).toLocaleString('ko-KR');
}

/** 앞 기간 대비 — 앞 기간이 0 이면 비율 없이 차이만 */
function deltaOf(current, previous, unit) {
  if (previous === undefined || previous === null) return { text: '', tone: 'flat' };
  const diff = (Number(current) || 0) - (Number(previous) || 0);
  if (!diff) return { text: '앞 기간과 같음', tone: 'flat' };
  const arrow = diff > 0 ? '↑' : '↓';
  const rate =
    previous > 0 ? ` (${diff > 0 ? '+' : ''}${((diff / previous) * 100).toFixed(1)}%)` : '';
  return {
    text: `앞 기간보다 ${arrow} ${formatCount(Math.abs(diff))}${unit}${rate}`,
    tone: diff > 0 ? 'up' : 'down',
  };
}

function saveBlob({ blob, filename }, fallbackName) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename || fallbackName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

/* 관리자 이용 통계(백엔드 usage-statistics.md §6·§8, 플랫폼 관리자) — 피그마에 없다.
 * 첫 조회는 기간 없이 불러 서버 기본값(마지막 집계일까지 최근 30일)과 마지막 집계일을 받는다 */
export default {
  name: 'AdminUsageStatisticsPage',
  components: {
    AppDateInput,
    AppErrorBanner,
    GroupedBarChart,
    UsageDemographics,
    UsageRankTable,
    UsageTrendChart,
  },
  data() {
    return {
      MAX_DAILY_DAYS,
      INTERVALS,
      STEP_SERIES,
      // 서버가 알려 주기 전에는 어제로 막는다
      lastDay: addDays(todayString(), -1),
      from: '',
      to: '',
      interval: 'DAY',
      // [조회]를 눌렀을 때의 기간 — 입력만 바꿔서는 다시 부르지 않는다
      applied: null,
      stats: null,
      loading: false,
      error: '',
      keywordRows: { POPULAR: [], ZERO_RESULT: [] },
      keywordError: { POPULAR: '', ZERO_RESULT: '' },
      keywordLoading: false,
      bannerRows: [],
      bannerError: '',
      bannerLoading: false,
      downloading: false,
    };
  },
  computed: {
    presets() {
      return buildPresets(this.lastDay);
    },
    tiles() {
      const totals = this.stats?.totals;
      const previous = this.stats?.previousTotals;
      return METRICS.map(({ key, sumLabel, unit }) => ({
        key,
        label: sumLabel,
        unit: totals ? unit : '',
        value: totals ? formatCount(totals[key]) : '-',
        delta: totals ? deltaOf(totals[key], previous?.[key], unit) : { text: '', tone: 'flat' },
      }));
    },
    // 일별은 하루 값이라 사람 수, 월별은 날마다 센 값을 더해 연인원
    trendSeries() {
      const monthly = this.interval === 'MONTH';
      return METRICS.map(({ key, label, sumLabel }) => ({ key, label: monthly ? sumLabel : label }));
    },
    trendRows() {
      return this.stats?.rows || [];
    },
    stepRows() {
      const totals = this.stats?.totals;
      if (!totals) return [];
      return METRICS.map(({ key, step, unit }) => ({
        key,
        label: `${step}(${unit})`,
        count: totals[key] || 0,
      }));
    },
  },
  created() {
    this.loadInitial();
  },
  methods: {
    validate(from, to, interval) {
      if (!from || !to) return '기간을 모두 입력해 주세요.';
      if (from > to) return '시작일이 종료일보다 늦습니다.';
      if (to > this.lastDay) return `종료일은 마지막 집계일(${this.lastDay})까지 고를 수 있습니다.`;
      if (daysBetween(from, to) > MAX_YEARS * 366) return `기간은 최대 ${MAX_YEARS}년입니다.`;
      if (interval === 'DAY' && daysBetween(from, to) > MAX_DAILY_DAYS) {
        return `일별은 최대 ${MAX_DAILY_DAYS}일입니다. 더 긴 기간은 월별로 봐 주세요.`;
      }
      return '';
    },
    warn(message) {
      this.$q.notify({ type: 'warning', message, position: 'top' });
    },
    setPeriod(preset) {
      this.from = preset.from;
      this.to = preset.to;
      this.apply();
    },
    setInterval(value) {
      if (value === this.interval) return;
      const period = this.applied || { from: this.from, to: this.to };
      const message = this.validate(period.from, period.to, value);
      if (message) {
        this.warn(message);
        return;
      }
      this.interval = value;
      if (this.applied) this.loadUsage();
    },
    apply() {
      const message = this.validate(this.from, this.to, this.interval);
      if (message) {
        this.warn(message);
        return;
      }
      this.applied = { from: this.from, to: this.to };
      this.loadUsage();
      this.loadRankings();
    },
    // 기간 없이 불러 서버 기본 기간·마지막 집계일을 받는다
    async loadInitial() {
      this.loading = true;
      this.error = '';
      try {
        const data = await getUsageStatistics({ interval: this.interval });
        this.stats = data;
        this.lastDay = data?.to || this.lastDay;
        this.from = data?.from || addDays(this.lastDay, -29);
        this.to = this.lastDay;
        this.applied = { from: this.from, to: this.to };
        this.loadRankings();
      } catch (error) {
        this.stats = null;
        this.error = error?.message || '이용 통계를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    async loadUsage() {
      const request = { ...this.applied, interval: this.interval };
      this.loading = true;
      this.error = '';
      try {
        this.stats = await getUsageStatistics(request);
      } catch (error) {
        this.stats = null;
        this.error = error?.message || '이용 통계를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    loadRankings() {
      this.loadKeywords();
      this.loadBanners();
    },
    async loadKeywords() {
      const period = this.applied;
      this.keywordLoading = true;
      const results = await Promise.allSettled(
        KEYWORD_TYPES.map((type) =>
          getUsageSearchKeywords({ type, ...period, limit: KEYWORD_LIMIT }),
        ),
      );
      const rows = {};
      const errors = {};
      results.forEach((result, index) => {
        const type = KEYWORD_TYPES[index];
        rows[type] =
          result.status === 'fulfilled'
            ? (result.value?.rows || []).map((row) => ({
                key: row.keyword,
                name: row.keyword,
                value: row.searchers,
              }))
            : [];
        errors[type] =
          result.status === 'rejected'
            ? result.reason?.message || '검색어 순위를 불러오지 못했습니다.'
            : '';
      });
      this.keywordRows = rows;
      this.keywordError = errors;
      this.keywordLoading = false;
    },
    async loadBanners() {
      this.bannerLoading = true;
      this.bannerError = '';
      try {
        const data = await getUsageBannerClicks(this.applied);
        this.bannerRows = (data?.rows || []).map((row) => ({
          key: row.bannerId,
          name: row.title || '(제목 없음)',
          note: row.deleted ? '(삭제됨)' : '',
          value: row.clickers,
        }));
      } catch (error) {
        this.bannerRows = [];
        this.bannerError = error?.message || '배너 클릭 통계를 불러오지 못했습니다.';
      } finally {
        this.bannerLoading = false;
      }
    },
    async downloadCsv() {
      const request = { ...this.applied, interval: this.interval };
      this.downloading = true;
      try {
        saveBlob(
          await downloadUsageStatisticsCsv(request),
          `이용통계_${request.interval}_${request.from}_${request.to}.csv`,
        );
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error?.message || 'CSV 를 내려받지 못했습니다.',
          position: 'top',
        });
      } finally {
        this.downloading = false;
      }
    },
  },
};
</script>

<style scoped>
.usage {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 48px 40px 32px;
  background: var(--db-bg-page);
}

.usage__title {
  display: flex;
  align-items: center;
  height: 58px;
  margin: 0;
  padding: 10px 0;
  font-size: 32px;
  line-height: 1.2;
  font-weight: 700;
  color: var(--db-text-title);
}

.usage__filters {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 16px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.usage__period {
  display: flex;
  align-items: center;
  gap: 6px;
}

.usage__date {
  width: 160px;
}

.usage__sep {
  color: var(--db-text-sub);
}

/* 조건 줄 높이를 날짜 칸(40px)에 맞춘다 */
.usage__seg {
  display: flex;
  box-sizing: border-box;
  height: 40px;
  padding: 2px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
  background: var(--db-bg-page);
}

.usage__seg-btn {
  padding: 0 14px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  font: inherit;
  font-size: 13px;
  white-space: nowrap;
  color: var(--db-text-sub);
  cursor: pointer;
}

.usage__seg-btn--on {
  background: var(--db-bg-white);
  font-weight: 600;
  color: var(--db-text-title);
  box-shadow: 0 1px 2px rgb(0 0 0 / 8%);
}

.usage__seg-btn:focus-visible,
.usage__preset:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 1px;
}

.usage__csv {
  min-height: 40px;
  margin-left: auto;
  padding: 0 10px;
  border: 1px solid var(--db-border);
  border-radius: 6px;
  color: var(--db-text-body);
  white-space: nowrap;
}

.usage__foot {
  display: flex;
  flex-basis: 100%;
  align-items: center;
  gap: 12px;
}

.usage__presets {
  display: flex;
  flex: 1 1 auto;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.usage__preset {
  height: 30px;
  padding: 0 12px;
  font: inherit;
  font-size: 13px;
  white-space: nowrap;
  color: var(--db-text-body);
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 15px;
  cursor: pointer;
}

.usage__preset:hover {
  background: var(--db-bg-subtle);
}

.usage__preset[aria-pressed='true'] {
  color: var(--db-accent);
  background: var(--db-accent-soft);
  border-color: var(--db-accent);
}

.usage__apply {
  flex-shrink: 0;
  height: 40px;
  padding: 0 20px;
  border-radius: 6px;
  white-space: nowrap;
}

.usage__hint {
  margin: -8px 0 0;
  font-size: 12px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.usage__tiles {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  transition: opacity 120ms ease-out;
}

.usage__tiles--busy,
.usage__busy {
  opacity: 0.5;
}

.usage__tile {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.usage__tile-label {
  font-size: 14px;
  color: var(--db-text-sub);
}

.usage__tile-value {
  font-size: 26px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--db-text-title);
}

.usage__tile-unit {
  margin-left: 2px;
  font-size: 14px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.usage__tile-delta {
  min-height: 18px;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--db-text-sub);
}

.usage__tile-delta--up {
  color: var(--db-status-green-text);
}

.usage__tile-delta--down {
  color: var(--db-status-pink);
}

.usage__card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  padding: 16px 20px 20px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.usage__card-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--db-text-title);
}

.usage__card-note {
  margin: -4px 0 0;
  font-size: 12px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.usage__pair {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}

.usage__footnote {
  margin: 0;
  font-size: 13px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

@media (prefers-reduced-motion: reduce) {
  .usage__tiles {
    transition: none;
  }
}

@media (max-width: 1023px) {
  .usage {
    padding: 24px 16px;
  }

  .usage__tiles {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .usage__pair {
    grid-template-columns: minmax(0, 1fr);
  }
}

/* 폰: 기간 두 칸이 160px 고정이라 화면 폭을 넘었다 — 한 줄을 나눠 쓴다 */
@media (max-width: 599px) {
  .usage__period {
    flex: 1 1 100%;
    min-width: 0;
  }

  .usage__date {
    flex: 1 1 0;
    width: auto;
    min-width: 0;
  }

  .usage__tile-value {
    font-size: 22px;
  }
}
</style>
