<template>
  <ConsoleListPage
    v-model:page="page"
    v-model:page-size="pageSize"
    class="banner-page"
    title="배너 관리"
    list-title="배너 목록"
    unit="개"
    empty-text="등록된 배너가 없습니다."
    search-placeholder="배너 제목으로 검색"
    :columns="columns"
    :rows="pagedRows"
    :loading="loading"
    :error="errorMessage"
    :total="filteredRows.length"
    :filters="filters"
    row-clickable
    @search="onSearch"
    @row-click="$router.push(`/admin/banners/${$event.id}/edit`)"
  >
    <template #header-actions>
      <q-btn
        unelevated
        no-caps
        color="primary"
        class="btn-md"
        label="배너 등록"
        @click="$router.push('/admin/banners/new')"
      />
    </template>

    <template #cell-no="{ row }">{{ row.sortOrder }}</template>

    <template #cell-title="{ row }">
      <RouterLink :to="`/admin/banners/${row.id}/edit`" class="cell-title cell-link">
        {{ row.title || '-' }}
      </RouterLink>
    </template>

    <template #cell-image="{ row }">
      <img v-if="pcImageUrl(row)" :src="pcImageUrl(row)" alt="" class="banner-thumb" />
      <span v-else class="banner-thumb banner-thumb--empty" />
    </template>

    <template #cell-link="{ row }">
      <span class="banner-link">{{ row.buttonLink || '-' }}</span>
    </template>

    <template #cell-period="{ row }">{{ period(row) }}</template>

    <template #cell-status="{ row }">
      <StatusBadge
        :label="row.visibility === 'PRIVATE' ? '미사용' : '사용'"
        :color="row.visibility === 'PRIVATE' ? 'var(--db-status-gray)' : 'var(--db-status-blue)'"
      />
    </template>

    <template #row-actions="{ row }">
      <q-item v-close-popup clickable @click="$router.push(`/admin/banners/${row.id}/edit`)">
        <q-item-section>수정</q-item-section>
      </q-item>
      <q-item v-close-popup clickable @click="remove(row)">
        <q-item-section>삭제</q-item-section>
      </q-item>
    </template>
  </ConsoleListPage>
</template>

<script>
import ConsoleListPage from '@/components/console/ConsoleListPage.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import consoleList from '@/mixins/console-list';
import { deleteBanner, listAdminBanners } from '@/services/banner-api';
import { storedFileContentUrl } from '@/services/file-api';
import { formatDateKst } from '@/utils/datetime';

const COLUMNS = [
  { key: 'no', label: '순번', width: 60, align: 'center' },
  { key: 'title', label: '제목', minWidth: 200 },
  { key: 'image', label: '이미지', width: 150, align: 'center' },
  { key: 'link', label: '링크', width: 200 },
  { key: 'period', label: '노출기간', width: 170, align: 'center' },
  { key: 'status', label: '상태', width: 90, align: 'center' },
];

export default {
  name: 'AdminBannersPage',
  components: { ConsoleListPage, StatusBadge },
  mixins: [consoleList],
  data() {
    return {
      columns: COLUMNS,
      searchFields: ['title'],
    };
  },
  computed: {
    filters() {
      return [
        {
          key: 'visibility',
          label: '상태',
          options: [
            { label: '공개', value: 'PUBLIC' },
            { label: '기간 공개', value: 'SCHEDULED' },
            { label: '비공개', value: 'PRIVATE' },
          ],
        },
      ];
    },
  },
  created() {
    this.load();
  },
  methods: {
    async load() {
      this.loading = true;
      this.errorMessage = '';
      try {
        this.rows = await listAdminBanners();
      } catch (error) {
        this.handleLoadError(error, '배너 목록 조회 실패');
      } finally {
        this.loading = false;
      }
    },
    pcImageUrl(row) {
      return row.pcImageFileId ? storedFileContentUrl(row.pcImageFileId) : '';
    },
    period(row) {
      if (row.visibility !== 'SCHEDULED') return '상시';
      return `${formatDateKst(row.startAt, '-')} ~ ${formatDateKst(row.endAt, '-')}`;
    },
    remove(row) {
      this.$q
        .dialog({ title: '배너 삭제', message: `"${row.title}"을(를) 삭제할까요?`, cancel: true })
        .onOk(async () => {
          try {
            await deleteBanner(row.id);
            this.$q.notify({ type: 'positive', message: '삭제했습니다.', position: 'top' });
            await this.load();
          } catch (error) {
            this.notifyError(error, '삭제에 실패했습니다.');
          }
        });
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

/* 행 68 = 썸네일 40 + padding 26 (+여유 2) — 인라인이면 베이스라인 여백이 붙는다 */
.banner-page :deep(.console-table td) {
  height: 68px;
}

.banner-thumb {
  display: block;
  width: 80px;
  height: 40px;
  margin: 0 auto;
  border-radius: 4px;
  object-fit: cover;
}

.banner-thumb--empty {
  background: var(--db-border-avatar);
}

.banner-link {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--db-accent);
}
</style>
