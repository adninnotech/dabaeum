<template>
  <ConsoleListPage
    v-model:page="page"
    v-model:page-size="pageSize"
    title="기관 관리"
    list-title="기관 목록"
    unit="개"
    empty-text="등록된 기관이 없습니다."
    search-placeholder="기관명, 연락처로 검색"
    :stats="stats"
    :stat-columns="3"
    :columns="columns"
    :rows="pagedRows"
    :loading="loading"
    :error="errorMessage"
    :total="filteredRows.length"
    :filters="filters"
    row-clickable
    @search="onSearch"
    @row-click="openDetail"
  >
    <template #cell-group="{ row }">
      <div class="cell-user">
        <div class="cell-user__avatar">
          <img v-if="logoUrl(row)" :src="logoUrl(row)" alt="" class="cell-user__logo" />
          <q-icon v-else name="apartment" size="18px" />
        </div>
        <div class="cell-lines">
          <RouterLink :to="`/admin/institutions/${row.id}`" class="cell-title cell-link">
            {{ row.name || '-' }}
          </RouterLink>
          <div class="cell-note cell-note--sub">{{ row.contactPhone || '-' }}</div>
        </div>
      </div>
    </template>

    <template #cell-region="{ row }">{{ row.region || '-' }}</template>

    <template #cell-manager="{ row }">{{ managerLabel(row) }}</template>
    <template #cell-instructorCount="{ row }">{{ row.instructorCount ?? '-' }}</template>
    <template #cell-courseCount="{ row }">{{ row.courseCount ?? '-' }}</template>
    <template #cell-learnerCount="{ row }">{{ row.learnerCount ?? '-' }}</template>

    <template #cell-status="{ row }">
      <StatusBadge :status="row.status || 'ACTIVE'" />
    </template>

    <template #cell-createdAt="{ row }">{{ formatDateKst(row.createdAt, '-') }}</template>

    <!-- 상세는 행 아무 곳(이름 링크 포함)이나 누르면 — 처리(⋮ 상태 변경)는 그대로 둔다.
         따로 있던 버튼 열이 좁은 화면에서 두 줄로 꺾이고 이름 열을 밀어냈다 -->
    <template #row-actions="{ row }">
      <q-item v-close-popup clickable @click="openDetail(row)">
        <q-item-section>상세 정보 보기</q-item-section>
      </q-item>
      <q-separator />
      <q-item
        v-for="option in statusOptions"
        :key="option.value"
        v-close-popup
        clickable
        @click="changeInstitutionStatus(row, option.value)"
      >
        <q-item-section>상태: {{ option.label }}</q-item-section>
      </q-item>
    </template>
  </ConsoleListPage>
</template>

<script>
import ConsoleListPage from '@/components/console/ConsoleListPage.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import consoleList from '@/mixins/console-list';
import { listInstitutions, updateInstitution } from '@/services/institution-api';
import { storedFileContentUrl } from '@/services/file-api';
import { formatDateKst } from '@/utils/datetime';

const COLUMNS = [
  { key: 'group', label: '기관 정보', minWidth: 220 },
  { key: 'region', label: '지역', width: 110, align: 'center' },
  { key: 'manager', label: '관리자', width: 103, align: 'center' },
  { key: 'instructorCount', label: '소속 강사', width: 92, align: 'center' },
  { key: 'courseCount', label: '운영 강의', width: 92, align: 'center' },
  { key: 'learnerCount', label: '수강생 수', width: 95, align: 'center' },
  { key: 'status', label: '상태', width: 90, align: 'center' },
  { key: 'createdAt', label: '등록일', width: 99, align: 'center' },
];

const STATUS_OPTIONS = [
  { label: '활성', value: 'ACTIVE' },
  { label: '정지', value: 'SUSPENDED' },
];

export default {
  name: 'AdminInstitutionsPage',
  components: { ConsoleListPage, StatusBadge },
  mixins: [consoleList],
  data() {
    return {
      columns: COLUMNS,
      statusOptions: STATUS_OPTIONS,
      searchFields: ['name', 'contactPhone'],
      serverSearch: true,
    };
  },
  computed: {
    filters() {
      return [
        // region 값 자체는 채워지지만 선택지 목록을 만들 별도 코드 값 API가 없다 — 검색으로 대신한다.
        { key: 'region', label: '지역', options: [] },
        { key: 'status', label: '상태', options: STATUS_OPTIONS },
      ];
    },
    stats() {
      return [
        // 전체는 서버 총계, 나머지는 받은 행으로 센다
        { label: '전체 기관', value: this.serverTotal ?? this.rows.length, unit: '개' },
        { label: this.statLabel('활성 기관'), value: this.countBy('status', 'ACTIVE'), unit: '개' },
        { label: this.statLabel('정지 기관'), value: this.countBy('status', 'SUSPENDED'), unit: '개' },
      ];
    },
  },
  created() {
    this.load();
  },
  methods: {
    formatDateKst,
    async load() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await listInstitutions({
          page: 0,
          size: 100,
          q: this.search.q || undefined,
          status: this.search.filters.status || undefined,
          region: this.search.filters.region || undefined,
        });
        this.rows = Array.isArray(result.data) ? result.data : [];
        this.rememberTotal(result);
      } catch (error) {
        this.handleLoadError(error, '기관 목록 조회 실패');
      } finally {
        this.loading = false;
      }
    },
    onSearch(payload) {
      this.search = payload;
      this.applyServerSearch();
    },
    // "김다혜 외 3명" — primaryAdminName 은 목록 응답에만 있고 관리자가 없으면 빠진다
    managerLabel(row) {
      const count = row.adminCount || 0;
      if (!count || !row.primaryAdminName) return '-';
      return count > 1 ? `${row.primaryAdminName} 외 ${count - 1}명` : row.primaryAdminName;
    },
    logoUrl(row) {
      return row.logoFileId ? storedFileContentUrl(row.logoFileId) : '';
    },
    openDetail(row) {
      this.$router.push(`/admin/institutions/${row.id}`);
    },
    async changeInstitutionStatus(row, status) {
      try {
        await updateInstitution(row.id, { status });
        this.$q.notify({ type: 'positive', message: '상태를 변경했습니다.', position: 'top' });
        await this.load();
      } catch (error) {
        this.notifyError(error, '상태 변경에 실패했습니다.');
      }
    },
  },
};
</script>

<style scoped>

.cell-link {
  text-decoration: none;
}

.cell-link:hover {
  text-decoration: underline;
}

.cell-user__logo {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
}
</style>
