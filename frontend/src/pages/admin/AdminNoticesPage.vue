<template>
  <ConsoleListPage
    v-model:page="page"
    v-model:page-size="pageSize"
    title="공지사항 관리"
    list-title="공지 목록"
    unit="건"
    empty-text="등록된 공지사항이 없습니다."
    search-placeholder="제목, 작성자로 검색"
    :columns="columns"
    :rows="pagedRows"
    :loading="loading"
    :error="errorMessage"
    :total="filteredRows.length"
    :filters="filters"
    row-clickable
    @search="onSearch"
    @row-click="$router.push(`/admin/notices/${$event.id}/edit`)"
  >
    <template #header-actions>
      <q-btn
        unelevated
        no-caps
        color="primary"
        class="btn-md"
        label="공지 작성"
        @click="$router.push('/admin/notices/new')"
      />
    </template>

    <template #cell-no="{ row }">{{ rowNumber(row) }}</template>

    <template #cell-title="{ row }">
      <RouterLink :to="`/admin/notices/${row.id}/edit`" class="cell-title cell-link">
        {{ row.title || '-' }}
      </RouterLink>
    </template>

    <template #cell-audience="{ row }">{{ AUDIENCE_LABEL[row.audience] || '전체' }}</template>

    <!-- authorName은 nullable — 작성자 계정을 못 찾으면 null -->
    <template #cell-author="{ row }">{{ row.authorName || '-' }}</template>

    <template #cell-createdAt="{ row }">{{ formatDateKst(row.createdAt, '-') }}</template>

    <template #cell-status="{ row }">
      <div class="status-cell">
        <StatusBadge
          :status="row.status"
          :label="row.status === 'PUBLISHED' ? '게시' : '임시저장'"
          :color="row.status === 'PUBLISHED' ? 'var(--db-status-green)' : 'var(--db-status-gray)'"
        />
        <!-- TODO(figma): 예약 게시 배지 디자인 미확보 — 토큰 색으로 임시 표시 -->
        <StatusBadge v-if="isScheduled(row)" label="예약" color="var(--db-status-orange)" />
      </div>
    </template>

    <template #row-actions="{ row }">
      <q-item v-close-popup clickable @click="$router.push(`/admin/notices/${row.id}/edit`)">
        <q-item-section>수정</q-item-section>
      </q-item>
      <q-item v-close-popup clickable @click="confirmDelete(row)">
        <q-item-section>삭제</q-item-section>
      </q-item>
    </template>
  </ConsoleListPage>
</template>

<script>
import ConsoleListPage from '@/components/console/ConsoleListPage.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import consoleList from '@/mixins/console-list';
import { AUDIENCE_LABEL, AUDIENCE_OPTIONS } from '@/config/notice-options';
import { deleteNotice, listAdminNotices } from '@/services/notice-api';
import { formatDateKst } from '@/utils/datetime';

const COLUMNS = [
  { key: 'no', label: '번호', width: 64, align: 'center' },
  { key: 'title', label: '제목' },
  { key: 'audience', label: '공지 유형', width: 100, align: 'center' },
  { key: 'author', label: '작성자', width: 120, align: 'center' },
  { key: 'createdAt', label: '작성일', width: 156, align: 'center' },
  { key: 'status', label: '상태', width: 80, align: 'center' },
];

export default {
  name: 'AdminNoticesPage',
  components: { ConsoleListPage, StatusBadge },
  mixins: [consoleList],
  data() {
    return {
      columns: COLUMNS,
      searchFields: ['title'],
      AUDIENCE_LABEL,
      serverSearch: true,
    };
  },
  computed: {
    filters() {
      return [
        { key: 'audience', label: '공지 유형', options: AUDIENCE_OPTIONS },
        {
          key: 'status',
          label: '상태',
          options: [
            { label: '게시', value: 'PUBLISHED' },
            { label: '임시저장', value: 'DRAFT' },
          ],
        },
      ];
    },
  },
  created() {
    this.load();
  },
  methods: {
    formatDateKst,
    rowNumber(row) {
      return this.filteredRows.length - this.filteredRows.indexOf(row);
    },
    onSearch(payload) {
      this.search = payload;
      this.applyServerSearch();
    },
    async load() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await listAdminNotices({ page: 0, size: 100, q: this.search.q || undefined });
        this.rows = Array.isArray(result.data) ? result.data : [];
      } catch (error) {
        this.handleLoadError(error, '공지사항 조회 실패');
      } finally {
        this.loading = false;
      }
    },
    /** publishedAt이 미래인 PUBLISHED 공지 = 예약 게시 */
    isScheduled(row) {
      return row.status === 'PUBLISHED' && row.publishedAt && new Date(row.publishedAt) > new Date();
    },
    confirmDelete(row) {
      this.$q
        .dialog({ title: '공지 삭제', message: '이 공지를 삭제할까요?', cancel: true })
        .onOk(async () => {
          try {
            await deleteNotice(row.id);
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

.status-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
</style>
