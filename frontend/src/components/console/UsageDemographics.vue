<template>
  <div class="usage-demo">
    <div class="usage-demo__seg" role="group" aria-label="지표">
      <button
        v-for="option in METRICS"
        :key="option.value"
        type="button"
        class="usage-demo__seg-btn"
        :class="{ 'usage-demo__seg-btn--on': metric === option.value }"
        :aria-pressed="metric === option.value"
        @click="metric = option.value"
      >
        {{ option.label }}
      </button>
    </div>

    <AppErrorBanner v-if="error" :message="error" />
    <template v-else>
      <p class="usage-demo__summary" :class="{ 'usage-demo__summary--busy': loading }">
        {{ totalLabel }} <strong>{{ format(data?.total) }}{{ unit }}</strong>
        <span aria-hidden="true">·</span> 회원
        <strong>{{ format(data?.members) }}{{ unit }}</strong>
        <span aria-hidden="true">·</span> 비회원
        <strong>{{ format(data?.guests) }}{{ unit }}</strong>
      </p>
      <p class="usage-demo__note">
        성별·연령대 막대는 회원만 셉니다. 비회원은 위 비회원 수에만 들어갑니다.
      </p>
      <div class="usage-demo__charts" :class="{ 'usage-demo__charts--busy': loading }">
        <section class="usage-demo__chart" aria-label="성별">
          <h3 class="usage-demo__chart-title">성별</h3>
          <GroupedBarChart :rows="genderRows" :series="series" category-label="성별" />
        </section>
        <section class="usage-demo__chart" aria-label="연령대">
          <h3 class="usage-demo__chart-title">연령대</h3>
          <GroupedBarChart :rows="ageRows" :series="series" category-label="연령대" />
        </section>
      </div>
    </template>
  </div>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import GroupedBarChart from '@/components/console/GroupedBarChart.vue';
import { getUsageDemographics } from '@/services/usage-api';

const METRICS = [
  { value: 'VISIT', label: '방문' },
  { value: 'SEARCH', label: '검색' },
  { value: 'COURSE_VIEW', label: '강좌 조회' },
  { value: 'ENROLLMENT', label: '수강 신청' },
  { value: 'BANNER_CLICK', label: '배너 클릭' },
];

function asRows(items) {
  return (items || []).map((item) => ({
    key: item.key,
    label: item.label || item.key,
    count: item.count,
  }));
}

/* 이용 통계 ④ 성별·연령대 — 지표를 바꾸면 그 지표만 다시 부른다 */
export default {
  name: 'UsageDemographics',
  components: { AppErrorBanner, GroupedBarChart },
  props: {
    from: { type: String, required: true },
    to: { type: String, required: true },
  },
  data() {
    return { METRICS, metric: 'VISIT', data: null, loading: false, error: '' };
  },
  computed: {
    // 수강 신청은 신청 1건 단위, 나머지는 하루 한 사람 1회
    unit() {
      return this.metric === 'ENROLLMENT' ? '건' : '명';
    },
    // 기간 합계는 날마다 센 인원을 더한 값 — 사람 수로 읽히지 않게 연인원
    totalLabel() {
      return this.metric === 'ENROLLMENT' ? '합계' : '연인원';
    },
    series() {
      const label = this.metric === 'ENROLLMENT' ? '회원(건)' : '회원 연인원(명)';
      return [{ key: 'count', label }];
    },
    genderRows() {
      return asRows(this.data?.genders);
    },
    ageRows() {
      return asRows(this.data?.ageBands);
    },
  },
  watch: {
    metric() {
      this.load();
    },
    from() {
      this.load();
    },
    to() {
      this.load();
    },
  },
  created() {
    this.load();
  },
  methods: {
    format(value) {
      return Number(value || 0).toLocaleString('ko-KR');
    },
    async load() {
      const request = { metric: this.metric, from: this.from, to: this.to };
      this.loading = true;
      this.error = '';
      try {
        const data = await getUsageDemographics(request);
        // 지표를 빠르게 바꿨을 때 늦게 온 앞 응답이 덮지 않게
        if (request.metric === this.metric) this.data = data;
      } catch (error) {
        this.data = null;
        this.error = error?.message || '성별·연령대 통계를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
.usage-demo {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.usage-demo__seg {
  display: flex;
  align-self: flex-start;
  max-width: 100%;
  overflow-x: auto;
  padding: 2px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
  background: var(--db-bg-page);
}

.usage-demo__seg-btn {
  padding: 4px 12px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  font: inherit;
  font-size: 13px;
  white-space: nowrap;
  color: var(--db-text-sub);
  cursor: pointer;
}

.usage-demo__seg-btn--on {
  background: var(--db-bg-white);
  font-weight: 600;
  color: var(--db-text-title);
  box-shadow: 0 1px 2px rgb(0 0 0 / 8%);
}

.usage-demo__seg-btn:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 1px;
}

.usage-demo__summary {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0;
  font-size: 14px;
  color: var(--db-text-body);
}

.usage-demo__summary strong {
  font-variant-numeric: tabular-nums;
  color: var(--db-text-title);
}

.usage-demo__note {
  margin: -6px 0 0;
  font-size: 12px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.usage-demo__charts {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 3fr);
  gap: 24px;
  transition: opacity 120ms ease-out;
}

.usage-demo__charts--busy,
.usage-demo__summary--busy {
  opacity: 0.5;
}

.usage-demo__chart {
  min-width: 0;
}

.usage-demo__chart-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-title);
}

@media (prefers-reduced-motion: reduce) {
  .usage-demo__charts {
    transition: none;
  }
}

@media (max-width: 1023px) {
  .usage-demo__charts {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
