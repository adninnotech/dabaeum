<template>
  <ConsoleListPage
    v-model:page="page"
    v-model:page-size="pageSize"
    title="관리자 접속기록"
    list-title="개인정보 처리 기록"
    unit="건"
    empty-text="이 기간에 남은 기록이 없습니다."
    row-key="id"
    :searchable="false"
    :selectable="false"
    :columns="columns"
    :rows="rows"
    :loading="loading"
    :error="errorMessage"
    :total="total"
    :filters="filters"
    :initial-filters="initialFilters"
    row-clickable
    @row-click="openActor"
    @search="onSearch"
  >
    <template #filter-extra>
      <div class="logs-period" role="group" aria-label="조회 기간">
        <AppDateInput
          v-model="from"
          outlined
          dense
          label="시작일"
          class="logs-date"
          :max="to || ''"
        />
        <span class="logs-period__sep" aria-hidden="true">~</span>
        <AppDateInput
          v-model="to"
          outlined
          dense
          label="종료일"
          class="logs-date"
          :min="from || ''"
        />
      </div>
    </template>

    <template #cell-createdAt="{ row }">
      <span class="logs-mono">{{ formatDateTimeKst(row.createdAt) }}</span>
    </template>

    <template #cell-actor="{ row }">
      <div class="cell-lines">
        <!-- 이름 → 관리자 계정 관리의 그 사람 상세(역할·상태·보안). 없어진 계정은 갈 곳이 없다 -->
        <RouterLink v-if="row.actorName && row.actorId" :to="actorTo(row)" class="cell-title cell-link logs-actor">
          {{ row.actorName }}
        </RouterLink>
        <span v-else class="cell-title">{{ row.actorName || '(삭제된 계정)' }}</span>
        <span class="cell-note cell-note--sub">{{ labelForRole(row.actorRole) }}</span>
      </div>
    </template>

    <template #cell-action="{ row }">
      <span class="logs-action" :class="`logs-action--${String(row.action).toLowerCase()}`">
        {{ ACCESS_ACTION_LABEL[row.action] || row.action }}
      </span>
    </template>

    <template #cell-endpoint="{ row }">
      <code class="logs-endpoint" :title="row.endpoint">{{ row.endpoint }}</code>
    </template>

    <!-- targetUserIds 가 null 이면 1000명 초과이거나 사용자 id 가 없는 목록 — 건수만 있다 -->
    <template #cell-target="{ row }">
      <span v-if="Array.isArray(row.targetUserIds)" class="logs-count">
        {{ formatCount(row.targetUserIds.length) }}명
      </span>
      <span v-else class="logs-count">{{ formatCount(row.resultCount) }}건</span>
    </template>

    <template #cell-statusCode="{ row }">
      <span class="logs-status" :class="`logs-status--${statusTone(row.statusCode)}`">
        {{ row.statusCode }}
      </span>
    </template>

    <template #cell-ipAddress="{ row }">
      <span class="logs-mono">{{ row.ipAddress || '-' }}</span>
    </template>
  </ConsoleListPage>
</template>

<script>
import AppDateInput from '@/components/AppDateInput.vue';
import ConsoleListPage from '@/components/console/ConsoleListPage.vue';
import consoleList from '@/mixins/console-list';
import { labelForRole } from '@/access/roles';
import { listAdminAccessLogs, listAdministrators } from '@/services/admin-api';
import { ACCESS_ACTION_LABEL, ACCESS_ACTION_OPTIONS } from '@/utils/admin-labels';
import { formatDateTimeKst, todayString } from '@/utils/datetime';

const DEFAULT_PERIOD_DAYS = 7;
// 관리자 선택지 — 관리자 수가 이보다 많아지면 검색형으로 바꾼다
const ACTOR_OPTION_LIMIT = 100;

const COLUMNS = [
  { key: 'createdAt', label: '일시', width: 180, align: 'center' },
  { key: 'actor', label: '관리자', width: 150 },
  { key: 'action', label: '행위', width: 90, align: 'center' },
  { key: 'endpoint', label: '처리한 업무(요청)' },
  { key: 'target', label: '대상', width: 100, align: 'center' },
  { key: 'statusCode', label: '결과', width: 76, align: 'center' },
  // 접속 기록은 법정 보관 항목이라 IP 를 보인다 — '접속지'(장소처럼 읽힌다) 대신 무엇인지 그대로
  { key: 'ipAddress', label: '접속 IP', width: 140, align: 'center' },
];

function daysAgo(days) {
  const date = new Date();
  date.setDate(date.getDate() - days);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/* 관리자 접속기록 조회(설계서 §10.2, 개인정보 안전성 확보조치 기준 제8조) — 읽기 전용. 피그마에 없다 */
export default {
  name: 'AdminAccessLogsPage',
  components: { ConsoleListPage, AppDateInput },
  mixins: [consoleList],
  data() {
    // 관리자 계정 관리에서 '접속기록 보기'로 오면(?actorId=) 그 관리자로 거른 채 연다
    const actorId = typeof this.$route.query.actorId === 'string' ? this.$route.query.actorId : '';
    const initialFilters = actorId ? { actorId } : {};
    return {
      initialFilters,
      search: { q: '', filters: { ...initialFilters } },
      ACCESS_ACTION_LABEL,
      columns: COLUMNS,
      serverSearch: true,
      total: 0,
      // 기본은 최근 7일(오늘 포함). 서버가 from·to 를 서울 날짜로 받고 둘 다 포함한다
      from: daysAgo(DEFAULT_PERIOD_DAYS - 1),
      to: todayString(),
      actorOptions: [],
    };
  },
  computed: {
    filters() {
      return [
        { key: 'actorId', label: '관리자', options: this.actorOptions },
        { key: 'action', label: '행위', options: ACCESS_ACTION_OPTIONS },
      ];
    },
  },
  watch: {
    page() {
      this.load();
    },
    pageSize() {
      if (this.page === 1) this.load();
      else this.page = 1;
    },
  },
  created() {
    this.load();
    this.loadActors();
  },
  methods: {
    formatDateTimeKst,
    labelForRole,
    formatCount(value) {
      return Number(value || 0).toLocaleString('ko-KR');
    },
    actorTo(row) {
      return { path: '/admin/administrators', query: { userId: row.actorId } };
    },
    /** 행을 눌러도 그 관리자 상세 — 기록 한 줄엔 따로 볼 상세가 없다 */
    openActor(row) {
      if (!row?.actorId || !row.actorName) return;
      this.$router.push(this.actorTo(row));
    },
    statusTone(code) {
      if (code >= 500) return 'error';
      if (code >= 400) return 'warn';
      return 'ok';
    },
    onSearch(payload) {
      if (this.from && this.to && this.from > this.to) {
        this.$q.notify({ type: 'warning', message: '시작일이 종료일보다 늦습니다.', position: 'top' });
        return;
      }
      this.search = payload;
      if (this.page === 1) this.load();
      else this.page = 1;
    },
    // 관리자 선택지가 없어도 목록은 볼 수 있다 — 실패는 조용히 비워 둔다(필터만 비활성)
    async loadActors() {
      try {
        const result = await listAdministrators({ page: 0, size: ACTOR_OPTION_LIMIT });
        this.actorOptions = (result.data || []).map((item) => ({
          label: item.email ? `${item.name} (${item.email})` : item.name,
          value: item.userId,
        }));
      } catch {
        this.actorOptions = [];
      }
    },
    async load() {
      this.loading = true;
      this.errorMessage = '';
      const filters = this.search.filters || {};
      try {
        const result = await listAdminAccessLogs({
          page: this.page - 1,
          size: this.pageSize,
          actorId: filters.actorId || undefined,
          action: filters.action || undefined,
          from: this.from || undefined,
          to: this.to || undefined,
        });
        this.rows = result.data || [];
        this.total = result.page?.totalElements ?? this.rows.length;
      } catch (error) {
        this.total = 0;
        this.handleLoadError(error, '접속기록을 불러오지 못했습니다.');
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
/* 관리자 이름 — 누르면 관리자 계정 관리의 상세. 평소엔 제목색, 가리키면 파랑 밑줄 */
.logs-actor {
  color: var(--db-text-title);
  text-decoration: none;
}

.logs-actor:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
  border-radius: 2px;
}

@media (hover: hover) {
  .logs-actor:hover {
    color: var(--db-accent);
    text-decoration: underline;
  }
}
.logs-period {
  display: flex;
  align-items: center;
  gap: 6px;
}

.logs-period__sep {
  color: var(--db-text-sub);
}

.logs-date {
  width: 150px;
}

.logs-mono,
.logs-count {
  white-space: nowrap;
}

.logs-count {
  font-variant-numeric: tabular-nums;
}

.logs-mono,
.logs-endpoint {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.logs-endpoint {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--db-text-body);
}

.logs-action {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 18px;
  font-weight: 600;
  white-space: nowrap;
  background: var(--db-bg-page);
  color: var(--db-text-body);
}

/* 변경·삭제·다운로드는 조회보다 눈에 띄게 — 색은 기존 상태 토큰만 쓴다 */
.logs-action--update {
  background: color-mix(in srgb, var(--db-status-blue) 12%, transparent);
  color: var(--db-status-blue);
}

.logs-action--delete {
  background: color-mix(in srgb, var(--db-status-pink) 12%, transparent);
  color: var(--db-status-pink);
}

.logs-action--download {
  background: color-mix(in srgb, var(--db-status-purple) 12%, transparent);
  color: var(--db-status-purple);
}

.logs-status {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
  font-weight: 600;
}

.logs-status--ok {
  color: var(--db-status-green);
}

.logs-status--warn {
  color: var(--db-status-orange-text);
}

.logs-status--error {
  color: var(--db-status-pink);
}

@media (max-width: 599px) {
  .logs-period {
    width: 100%;
  }

  .logs-date {
    flex: 1;
    width: auto;
    min-width: 0;
  }
}
</style>
