<template>
  <q-page class="stats">
    <h1 class="stats__title">수강·배지 통계</h1>

    <!-- 필터는 차트 위 한 줄 — 기간·분야는 수강 통계에, 기간은 배지 통계에도 적용된다 -->
    <section class="stats__filters" aria-label="통계 조건">
      <div class="stats__period" role="group" aria-label="기간">
        <AppDateInput v-model="from" outlined dense label="시작일" class="stats__date" :max="to || ''" />
        <span class="stats__sep" aria-hidden="true">~</span>
        <AppDateInput v-model="to" outlined dense label="종료일" class="stats__date" :min="from || ''" />
      </div>
      <div class="stats__cls">
        <ClassificationPicker v-model="classificationCode" :leaf-only="false" />
      </div>
      <!-- [조회]가 혼자 한 줄을 차지했다 — 빠른 기간 줄 끝에 둔다 -->
      <div class="stats__foot">
        <!-- 칩은 누르면 바로 조회한다(눌러도 차트가 그대로라 안 눌린 것처럼 보였다). 날짜·분야를 직접 바꿀 때만 [조회] -->
        <div class="stats__presets" role="group" aria-label="빠른 기간">
          <button
            v-for="preset in periodPresets"
            :key="preset.label"
            type="button"
            class="stats__preset"
            :aria-pressed="applied.from === preset.from && applied.to === preset.to"
            @click="setPeriod(preset)"
          >
            {{ preset.label }}
          </button>
        </div>
        <q-btn unelevated no-caps color="primary" label="조회" class="stats__apply" :loading="anyLoading" @click="apply" />
      </div>
    </section>
    <p class="stats__hint">
      기간은 서울 날짜 기준이며 양끝을 포함합니다(최대 5년). 분야는 수강 통계에만 적용되고 하위 분야를 포함합니다.
    </p>

    <AppErrorBanner v-if="totalsError" :message="totalsError" />
    <div v-else class="stats__tiles">
      <div v-for="tile in tiles" :key="tile.label" class="stats__tile">
        <div class="stats__tile-label">{{ tile.label }}</div>
        <div class="stats__tile-value">{{ tile.value }}</div>
      </div>
    </div>

    <!-- 수강 통계 -->
    <section class="stats__card">
      <header class="stats__card-head">
        <h2 class="stats__card-title">수강 통계</h2>
        <div class="stats__seg" role="group" aria-label="묶음 기준">
          <button
            v-for="option in ENROLLMENT_GROUPS"
            :key="option.value"
            type="button"
            class="stats__seg-btn"
            :class="{ 'stats__seg-btn--on': enrollmentGroup === option.value }"
            :aria-pressed="enrollmentGroup === option.value"
            @click="enrollmentGroup = option.value"
          >
            {{ option.label }}
          </button>
        </div>
        <q-btn
          flat
          no-caps
          dense
          icon="o_download"
          label="CSV"
          class="stats__csv"
          :loading="downloading === 'enrollments'"
          @click="downloadEnrollments"
        />
      </header>
      <p v-if="enrollmentGroup === 'CONTENT'" class="stats__card-note">
        신청이 많은 강의 {{ CONTENT_LIMIT }}개까지 보여 줍니다.
      </p>
      <p v-else-if="enrollmentGroup === 'GENDER'" class="stats__card-note">성별은 지금의 회원 정보 기준입니다.</p>
      <AppErrorBanner v-if="enrollmentError" :message="enrollmentError" />
      <div v-else class="stats__chart" :class="{ 'stats__chart--busy': enrollmentLoading }">
        <GroupedBarChart
          :rows="enrollmentRows"
          :series="ENROLLMENT_SERIES"
          :orientation="enrollmentGroup === 'CONTENT' ? 'horizontal' : 'vertical'"
          :category-label="groupLabel(ENROLLMENT_GROUPS, enrollmentGroup)"
          :extra-columns="COMPLETION_RATE_COLUMN"
        />
      </div>
    </section>

    <!-- 배지 발급 -->
    <section class="stats__card">
      <header class="stats__card-head">
        <h2 class="stats__card-title">배지 발급</h2>
        <div class="stats__seg" role="group" aria-label="묶음 기준">
          <button
            v-for="option in BADGE_GROUPS"
            :key="option.value"
            type="button"
            class="stats__seg-btn"
            :class="{ 'stats__seg-btn--on': badgeGroup === option.value }"
            :aria-pressed="badgeGroup === option.value"
            @click="badgeGroup = option.value"
          >
            {{ option.label }}
          </button>
        </div>
        <q-btn
          flat
          no-caps
          dense
          icon="o_download"
          label="CSV"
          class="stats__csv"
          :loading="downloading === 'badges'"
          @click="downloadBadges"
        />
      </header>
      <AppErrorBanner v-if="badgeError" :message="badgeError" />
      <div v-else class="stats__chart" :class="{ 'stats__chart--busy': badgeLoading }">
        <GroupedBarChart
          :rows="badgeRows"
          :series="BADGE_SERIES"
          :orientation="badgeGroup === 'DEFINITION' ? 'horizontal' : 'vertical'"
          :category-label="groupLabel(BADGE_GROUPS, badgeGroup)"
        />
      </div>
    </section>
  </q-page>
</template>

<script>
import AppDateInput from '@/components/AppDateInput.vue';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import GroupedBarChart from '@/components/console/GroupedBarChart.vue';
import ClassificationPicker from '@/components/course/ClassificationPicker.vue';
import {
  downloadBadgeStatisticsCsv,
  downloadEnrollmentStatisticsCsv,
  getBadgeStatistics,
  getEnrollmentStatistics,
} from '@/services/statistics-api';
import { todayString } from '@/utils/datetime';

const CONTENT_LIMIT = 20;
const MAX_YEARS = 5;

const ENROLLMENT_GROUPS = [
  { value: 'CONTENT', label: '강의별', column: '강의' },
  { value: 'YEAR', label: '연도' },
  { value: 'MONTH', label: '월' },
  { value: 'AGE_BAND', label: '연령대' },
  { value: 'GENDER', label: '성별' },
];
const BADGE_GROUPS = [
  { value: 'YEAR', label: '연도' },
  { value: 'MONTH', label: '월' },
  { value: 'DEFINITION', label: '배지별', column: '배지' },
];
const ENROLLMENT_SERIES = [
  { key: 'applied', label: '신청' },
  // 기간 안에 승인됐고 지금도 APPROVED — 철회하면 줄어든다(QA DATA-01, 백엔드 2회차)
  { key: 'approved', label: '승인 유지' },
  { key: 'completed', label: '수료' },
];
const BADGE_SERIES = [
  { key: 'issued', label: '발급' },
  { key: 'revoked', label: '폐기' },
];

/** 수료율 = 수료 ÷ 승인 (백엔드 합의). 승인이 0 이면 '-' */
function completionRate(row) {
  const approved = Number(row?.approved) || 0;
  if (!approved) return '-';
  return `${((Number(row.completed) || 0) / approved * 100).toFixed(1)}%`;
}

const COMPLETION_RATE_COLUMN = [{ key: 'rate', label: '수료율', value: completionRate }];

function firstDayOfYear() {
  return `${new Date().getFullYear()}-01-01`;
}

function isoDate(date) {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** 빠른 기간 — 끝은 모두 오늘(작년만 12월 31일) */
function buildPeriodPresets() {
  const now = new Date();
  const year = now.getFullYear();
  const today = isoDate(now);
  const monthsAgo = (n) => isoDate(new Date(year, now.getMonth() - n, now.getDate() + 1));
  return [
    { label: '이번 달', from: isoDate(new Date(year, now.getMonth(), 1)), to: today },
    { label: '최근 3개월', from: monthsAgo(3), to: today },
    { label: '최근 6개월', from: monthsAgo(6), to: today },
    { label: '올해', from: `${year}-01-01`, to: today },
    { label: '작년', from: `${year - 1}-01-01`, to: `${year - 1}-12-31` },
  ];
}

function yearsBetween(from, to) {
  return (new Date(to) - new Date(from)) / (365.25 * 24 * 3600 * 1000);
}

// label 이 null 이면(삭제된 강좌 등) key 로 대신한다. 정의 없는 옛 배지는 NONE
function withLabels(rows) {
  return (rows || []).map((row) => ({
    ...row,
    label: row.label || (row.key === 'NONE' ? '정의 없는 배지' : row.key),
  }));
}

/** 시간 축(YEAR·MONTH)은 기간 안의 빈 연·월을 0 으로 채운다 — 서버는 값이 있는 칸만 준다 */
function timelineKeys(groupBy, from, to) {
  const [fy, fm] = from.split('-').map(Number);
  const [ty, tm] = to.split('-').map(Number);
  if (groupBy === 'YEAR') {
    return Array.from({ length: ty - fy + 1 }, (_, i) => String(fy + i));
  }
  const months = (ty - fy) * 12 + (tm - fm) + 1;
  return Array.from({ length: Math.max(0, months) }, (_, i) => {
    const total = fm - 1 + i;
    return `${fy + Math.floor(total / 12)}-${String((total % 12) + 1).padStart(2, '0')}`;
  });
}

/** 폰 차트 아래 라벨용 — '2026-08' 이 칸 폭에 잘렸다. 달 숫자만, 첫 칸·1월만 연도('26.1') */
function monthShortLabel(key, index) {
  const [year, month] = key.split('-');
  const m = Number(month);
  return index === 0 || m === 1 ? `${year.slice(2)}.${m}` : String(m);
}

function fillTimeline(rows, groupBy, from, to, zero) {
  if (groupBy !== 'YEAR' && groupBy !== 'MONTH') return rows;
  const byKey = Object.fromEntries(rows.map((row) => [row.key, row]));
  return timelineKeys(groupBy, from, to).map((key, index) => ({
    ...(byKey[key] || { key, label: key, ...zero }),
    ...(groupBy === 'MONTH' ? { shortLabel: monthShortLabel(key, index) } : {}),
  }));
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

/* 관리자 통계(설계서 §9, 플랫폼 관리자) — 피그마에 없다. 차트는 라이브러리 없이 GroupedBarChart(HTML/CSS) */
export default {
  name: 'AdminStatisticsPage',
  components: { AppErrorBanner, ClassificationPicker, GroupedBarChart, AppDateInput },
  data() {
    return {
      CONTENT_LIMIT,
      ENROLLMENT_GROUPS,
      BADGE_GROUPS,
      ENROLLMENT_SERIES,
      BADGE_SERIES,
      COMPLETION_RATE_COLUMN,
      periodPresets: buildPeriodPresets(),
      from: firstDayOfYear(),
      to: todayString(),
      classificationCode: null,
      // 조회 버튼을 눌렀을 때의 조건 — 입력만 바꿔서는 다시 부르지 않는다
      applied: { from: firstDayOfYear(), to: todayString(), classificationCode: null },
      enrollmentGroup: 'MONTH',
      badgeGroup: 'MONTH',
      enrollmentRows: [],
      badgeRows: [],
      totals: null,
      enrollmentLoading: false,
      badgeLoading: false,
      totalsLoading: false,
      enrollmentError: '',
      badgeError: '',
      totalsError: '',
      downloading: '',
    };
  },
  computed: {
    anyLoading() {
      return this.enrollmentLoading || this.badgeLoading || this.totalsLoading;
    },
    enrollmentParams() {
      return {
        groupBy: this.enrollmentGroup,
        from: this.applied.from,
        to: this.applied.to,
        classificationCode: this.applied.classificationCode || undefined,
        limit: this.enrollmentGroup === 'CONTENT' ? CONTENT_LIMIT : undefined,
      };
    },
    badgeParams() {
      return { groupBy: this.badgeGroup, from: this.applied.from, to: this.applied.to };
    },
    tiles() {
      const t = this.totals;
      const value = (n) => (t ? Number(n || 0).toLocaleString('ko-KR') : '-');
      return [
        { label: '신청', value: value(t?.applied) },
        { label: '승인 유지', value: value(t?.approved) },
        { label: '수료', value: value(t?.completed) },
        { label: '수료율 (수료 ÷ 승인)', value: t ? completionRate(t) : '-' },
      ];
    },
  },
  watch: {
    enrollmentGroup() {
      this.loadEnrollments();
    },
    badgeGroup() {
      this.loadBadges();
    },
  },
  created() {
    this.loadAll();
  },
  methods: {
    // 표 첫 칸 머리 — 탭 이름('강의별')이 아니라 칸 이름('강의')
    groupLabel(groups, value) {
      const group = groups.find((item) => item.value === value);
      return group?.column || group?.label || '구분';
    },
    setPeriod(preset) {
      this.from = preset.from;
      this.to = preset.to;
      this.apply();
    },
    apply() {
      if (!this.from || !this.to) {
        this.$q.notify({ type: 'warning', message: '기간을 모두 입력해 주세요.', position: 'top' });
        return;
      }
      if (this.from > this.to) {
        this.$q.notify({ type: 'warning', message: '시작일이 종료일보다 늦습니다.', position: 'top' });
        return;
      }
      if (yearsBetween(this.from, this.to) > MAX_YEARS) {
        this.$q.notify({ type: 'warning', message: `기간은 최대 ${MAX_YEARS}년입니다.`, position: 'top' });
        return;
      }
      this.applied = { from: this.from, to: this.to, classificationCode: this.classificationCode };
      this.loadAll();
    },
    loadAll() {
      this.loadTotals();
      this.loadEnrollments();
      this.loadBadges();
    },
    // 합계는 연도별 행을 더한다 — 강좌별은 상위 N개라 합이 전체가 아니다
    async loadTotals() {
      this.totalsLoading = true;
      this.totalsError = '';
      try {
        const data = await getEnrollmentStatistics({ ...this.enrollmentParams, groupBy: 'YEAR', limit: undefined });
        this.totals = (data?.rows || []).reduce(
          (acc, row) => ({
            applied: acc.applied + (row.applied || 0),
            approved: acc.approved + (row.approved || 0),
            completed: acc.completed + (row.completed || 0),
          }),
          { applied: 0, approved: 0, completed: 0 },
        );
      } catch (error) {
        this.totals = null;
        this.totalsError = error?.message || '합계를 불러오지 못했습니다.';
      } finally {
        this.totalsLoading = false;
      }
    },
    async loadEnrollments() {
      this.enrollmentLoading = true;
      this.enrollmentError = '';
      try {
        const p = this.enrollmentParams;
        const data = await getEnrollmentStatistics(p);
        this.enrollmentRows = fillTimeline(withLabels(data?.rows), p.groupBy, p.from, p.to, {
          applied: 0,
          approved: 0,
          completed: 0,
        });
      } catch (error) {
        this.enrollmentRows = [];
        this.enrollmentError = error?.message || '수강 통계를 불러오지 못했습니다.';
      } finally {
        this.enrollmentLoading = false;
      }
    },
    async loadBadges() {
      this.badgeLoading = true;
      this.badgeError = '';
      try {
        const p = this.badgeParams;
        const data = await getBadgeStatistics(p);
        this.badgeRows = fillTimeline(withLabels(data?.rows), p.groupBy, p.from, p.to, {
          issued: 0,
          revoked: 0,
        });
      } catch (error) {
        this.badgeRows = [];
        this.badgeError = error?.message || '배지 통계를 불러오지 못했습니다.';
      } finally {
        this.badgeLoading = false;
      }
    },
    async download(kind, request, fallbackName) {
      this.downloading = kind;
      try {
        saveBlob(await request(), fallbackName);
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error?.message || 'CSV 를 내려받지 못했습니다.',
          position: 'top',
        });
      } finally {
        this.downloading = '';
      }
    },
    downloadEnrollments() {
      const p = this.enrollmentParams;
      this.download(
        'enrollments',
        () => downloadEnrollmentStatisticsCsv(p),
        `수강통계_${p.groupBy}_${p.from}_${p.to}.csv`,
      );
    },
    downloadBadges() {
      const p = this.badgeParams;
      this.download('badges', () => downloadBadgeStatisticsCsv(p), `배지통계_${p.groupBy}_${p.from}_${p.to}.csv`);
    },
  },
};
</script>

<style scoped>
.stats {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 48px 40px 32px;
  background: var(--db-bg-page);
}

.stats__title {
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

.stats__filters {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
  padding: 16px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.stats__period {
  display: flex;
  align-items: center;
  gap: 6px;
}

.stats__date {
  width: 160px;
}

.stats__foot {
  display: flex;
  flex-basis: 100%;
  align-items: center;
  gap: 12px;
}

.stats__presets {
  display: flex;
  flex: 1 1 auto;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.stats__preset {
  height: 30px;
  padding: 0 12px;
  font: inherit;
  font-size: 13px;
  color: var(--db-text-body);
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 15px;
  cursor: pointer;
}

.stats__preset:hover {
  background: var(--db-bg-subtle);
}

.stats__preset:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 1px;
}

.stats__preset[aria-pressed='true'] {
  color: var(--db-accent);
  background: var(--db-accent-soft);
  border-color: var(--db-accent);
}

.stats__sep {
  color: var(--db-text-sub);
}

.stats__cls {
  flex: 1 1 420px;
  min-width: 0;
}

.stats__apply {
  flex-shrink: 0;
  height: 40px;
  padding: 0 20px;
  border-radius: 6px;
  white-space: nowrap;
}

/* 폰: 기간 두 칸이 160px 고정이라 화면 폭을 넘었다 — 한 줄을 나눠 쓴다 */
@media (max-width: 599px) {
  .stats__period {
    flex: 1 1 100%;
    min-width: 0;
  }

  .stats__date {
    flex: 1 1 0;
    width: auto;
    min-width: 0;
  }
}

.stats__hint {
  margin: -8px 0 0;
  font-size: 12px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.stats__tiles {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.stats__tile {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.stats__tile-label {
  font-size: 14px;
  color: var(--db-text-sub);
}

.stats__tile-value {
  font-size: 26px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--db-text-title);
}

.stats__card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 20px 20px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.stats__card-head {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.stats__card-title {
  margin: 0 8px 0 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--db-text-title);
}

.stats__card-note {
  margin: -4px 0 0;
  font-size: 12px;
  color: var(--db-text-sub);
}

.stats__seg {
  display: flex;
  padding: 2px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
  background: var(--db-bg-page);
}

.stats__seg-btn {
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

.stats__seg-btn--on {
  background: var(--db-bg-white);
  font-weight: 600;
  color: var(--db-text-title);
  box-shadow: 0 1px 2px rgb(0 0 0 / 8%);
}

.stats__seg-btn:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 1px;
}

/* [표로 보기]와 같은 보조 버튼 모양 — 검은 테두리라 혼자 튀었다 */
.stats__csv {
  margin-left: auto;
  padding: 0 10px;
  border: 1px solid var(--db-border);
  border-radius: 6px;
  color: var(--db-text-body);
}

.stats__chart {
  transition: opacity 120ms ease-out;
}

.stats__chart--busy {
  opacity: 0.5;
}

@media (prefers-reduced-motion: reduce) {
  .stats__chart {
    transition: none;
  }
}

@media (max-width: 1023px) {
  .stats {
    padding: 24px 16px;
  }

  .stats__tiles {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stats__seg {
    overflow-x: auto;
    max-width: 100%;
  }
}

/* 폰: 묶음 탭 때문에 CSV 가 한 줄을 따로 차지했다 — 제목 줄 오른쪽에 두고 탭은 그 아래 */
@media (max-width: 599px) {
  .stats__seg {
    order: 3;
  }

  .stats__foot {
    align-items: flex-end;
  }
}
</style>
