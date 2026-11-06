<template>
  <!-- NFT 학습배지 현황(발주처 학습배지 관리 · 설계서 §11.4) — GET /admin/badges, 서버 페이지. 조회는 관리자 접속기록에 남는다 -->
  <ConsoleListPage
    v-model:page="page"
    v-model:page-size="pageSize"
    title="학습배지 현황"
    list-title="발급된 학습배지"
    unit="개"
    empty-text="조건에 맞는 학습배지가 없습니다."
    search-placeholder="이름으로 검색"
    row-key="id"
    :selectable="false"
    :columns="columns"
    :rows="rows"
    :loading="loading"
    :error="errorMessage"
    :total="total"
    :filters="filters"
    :initial-filters="initialFilters"
    row-clickable
    @search="onSearch"
    @row-click="$router.push(`/admin/badges/${$event.id}`)"
  >
    <template #cell-recipient="{ row }">
      <RouterLink :to="`/admin/badges/${row.id}`" class="cell-title cell-link">
        {{ row.recipientName || '이름 없음' }}
      </RouterLink>
    </template>

    <template #cell-badge="{ row }">
      <div class="badges-name">
        <BadgeMedal :image-url="row.imageUrl || ''" :alt="row.badgeName" :size="32" :muted="row.status === 'REVOKED'" />
        <span class="badges-name__text">{{ row.badgeName }}</span>
      </div>
    </template>

    <template #cell-tier="{ row }">
      <span class="badges-nowrap">{{ tierLabel(row) || '단계 없음' }}</span>
    </template>

    <template #cell-status="{ row }">
      <StatusBadge :label="badgeStatusView(row).label" :color="badgeStatusView(row).color" />
    </template>

    <template #cell-issuedAt="{ row }">{{ formatDateMinuteKst(row.issuedAt, '발급 전') }}</template>
  </ConsoleListPage>
</template>

<script>
import BadgeMedal from '@/components/badge/BadgeMedal.vue';
import ConsoleListPage from '@/components/console/ConsoleListPage.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import consoleList from '@/mixins/console-list';
import { listAdminBadgeDefinitions } from '@/services/badge-api';
import { searchAdminBadges } from '@/services/credential-api';
import { BADGE_STATUS_FILTER, badgeStatusView, tierLabel } from '@/utils/badge-status';
import { formatDateMinuteKst } from '@/utils/datetime';

const COLUMNS = [
  // 이름 칸이 남는 폭을 가져가 배지 이름이 '평생학습 이수 …'로 잘렸다 — 이름은 고정, 남는 폭은 배지
  { key: 'recipient', label: '이름', width: 160 },
  { key: 'badge', label: '배지', minWidth: 200 },
  { key: 'tier', label: '단계', width: 110, align: 'center' },
  { key: 'status', label: '상태', width: 90, align: 'center' },
  { key: 'issuedAt', label: '발급일시', width: 160, align: 'center' },
];

export default {
  name: 'AdminBadgesPage',
  components: { BadgeMedal, ConsoleListPage, StatusBadge },
  mixins: [consoleList],
  data() {
    // 학습배지 관리의 '발급 현황 보기'·발급 수에서 오면 그 배지로 거른 채 연다
    const definitionId = typeof this.$route.query.definitionId === 'string' ? this.$route.query.definitionId : '';
    const initialFilters = definitionId ? { definitionId } : {};
    return {
      initialFilters,
      search: { q: '', filters: { ...initialFilters } },
      columns: COLUMNS,
      serverSearch: true,
      total: 0,
      definitions: [],
    };
  },
  computed: {
    // 단계 선택지는 단계 배지 정의의 단계 이름으로 — 정의가 여럿이면 가장 긴 것 기준
    tierOptions() {
      const levels = this.definitions
        .filter((item) => item.rule?.type === 'TIERED')
        .map((item) => item.rule.levels || [])
        .sort((a, b) => b.length - a.length)[0] || [];
      return levels.map((item) => ({ label: `${item.level}단계 · ${item.name}`, value: item.level }));
    },
    filters() {
      return [
        {
          key: 'definitionId',
          label: '배지',
          options: this.definitions.map((item) => ({ label: item.name, value: item.id })),
        },
        { key: 'tierLevel', label: '단계', options: this.tierOptions },
        { key: 'status', label: '상태', options: BADGE_STATUS_FILTER },
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
    this.loadDefinitions();
  },
  methods: {
    formatDateMinuteKst,
    badgeStatusView,
    tierLabel,
    onSearch(payload) {
      this.search = payload;
      if (this.page === 1) this.load();
      else this.page = 1;
    },
    async loadDefinitions() {
      try {
        this.definitions = await listAdminBadgeDefinitions();
      } catch {
        // 선택지만 비고 목록은 그대로 쓸 수 있다
        this.definitions = [];
      }
    },
    async load() {
      this.loading = true;
      this.errorMessage = '';
      const filters = this.search.filters || {};
      try {
        const result = await searchAdminBadges({
          page: this.page - 1,
          size: this.pageSize,
          definitionId: filters.definitionId || undefined,
          tierLevel: filters.tierLevel || undefined,
          status: filters.status || undefined,
          q: this.search.q || undefined,
        });
        this.rows = result.data || [];
        this.total = result.page?.totalElements ?? this.rows.length;
      } catch (error) {
        this.total = 0;
        this.handleLoadError(error, '학습배지 목록을 불러오지 못했습니다.');
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
.badges-nowrap {
  white-space: nowrap;
}

.badges-name {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.badges-name__text {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
</style>
