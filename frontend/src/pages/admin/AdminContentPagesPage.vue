<template>
  <ConsoleListPage
    v-model:page="page"
    v-model:page-size="pageSize"
    title="콘텐츠 관리"
    list-title="콘텐츠 페이지"
    unit="개"
    empty-text="만든 페이지가 없습니다. 기관 소개·이용 안내 같은 페이지를 만들어 메뉴에 연결할 수 있습니다."
    search-placeholder="제목 또는 주소로 검색"
    row-key="id"
    :selectable="false"
    :columns="columns"
    :rows="pagedRows"
    :loading="loading"
    :error="errorMessage"
    :total="filteredRows.length"
    :filters="filters"
    row-clickable
    @search="onSearch"
    @row-click="$router.push(`/admin/content-pages/${$event.id}/edit`)"
  >
    <template #header-actions>
      <q-btn unelevated no-caps color="primary" icon="add" label="페이지 추가" to="/admin/content-pages/new" />
    </template>

    <template #cell-title="{ row }">
      <div class="cell-lines">
        <RouterLink :to="`/admin/content-pages/${row.id}/edit`" class="cell-title cell-link">{{ row.title }}</RouterLink>
        <code class="cp-slug">{{ contentPagePath(row.slug) }}</code>
      </div>
    </template>

    <template #cell-status="{ row }">
      <StatusBadge
        :label="row.status === 'PUBLISHED' ? '게시' : '초안'"
        :color="row.status === 'PUBLISHED' ? 'var(--db-status-green)' : 'var(--db-status-gray)'"
      />
    </template>

    <template #cell-publishedAt="{ row }">{{ formatDateKst(row.publishedAt, '-') }}</template>
    <template #cell-updatedAt="{ row }">{{ formatDateMinuteKst(row.updatedAt, '-') }}</template>

    <template #row-actions="{ row }">
      <q-item v-close-popup clickable :to="`/admin/content-pages/${row.id}/edit`">
        <q-item-section>수정</q-item-section>
      </q-item>
      <q-item v-if="row.status === 'PUBLISHED'" v-close-popup clickable :href="contentPagePath(row.slug)" target="_blank">
        <q-item-section>공개 화면 보기</q-item-section>
      </q-item>
      <q-item v-close-popup clickable @click="togglePublish(row)">
        <q-item-section>{{ row.status === 'PUBLISHED' ? '게시 취소' : '게시' }}</q-item-section>
      </q-item>
      <q-separator />
      <q-item v-close-popup clickable @click="confirmRemove(row)">
        <q-item-section class="cp-danger">삭제</q-item-section>
      </q-item>
    </template>
  </ConsoleListPage>
</template>

<script>
import ConsoleListPage from '@/components/console/ConsoleListPage.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import consoleList from '@/mixins/console-list';
import {
  deleteContentPage,
  listAdminContentPages,
  setContentPagePublished,
} from '@/services/cms-api';
import { formatDateKst, formatDateMinuteKst } from '@/utils/datetime';
import { contentPagePath } from '@/utils/site-menu';

const COLUMNS = [
  { key: 'title', label: '제목 / 주소' },
  { key: 'status', label: '상태', width: 90, align: 'center' },
  { key: 'publishedAt', label: '게시일', width: 130, align: 'center' },
  { key: 'updatedAt', label: '수정일', width: 170, align: 'center' },
];

const STATUS_FILTER = [
  { label: '게시', value: 'PUBLISHED' },
  { label: '초안', value: 'DRAFT' },
];

/* 콘텐츠 페이지 관리(설계서 §7, 플랫폼 관리자) — 피그마에 없다. 콘솔 목록을 쓰고 편집은 공지 작성과 같은 리치텍스트 */
export default {
  name: 'AdminContentPagesPage',
  components: { ConsoleListPage, StatusBadge },
  mixins: [consoleList],
  data() {
    return {
      columns: COLUMNS,
      searchFields: ['title', 'slug'],
      filters: [{ key: 'status', label: '상태', options: STATUS_FILTER }],
    };
  },
  created() {
    this.load();
  },
  methods: {
    formatDateKst,
    formatDateMinuteKst,
    contentPagePath,
    async load() {
      this.loading = true;
      this.errorMessage = '';
      try {
        this.rows = await listAdminContentPages();
      } catch (error) {
        this.handleLoadError(error, '콘텐츠 페이지를 불러오지 못했습니다.');
      } finally {
        this.loading = false;
      }
    },
    async togglePublish(row) {
      try {
        const updated = await setContentPagePublished(row.id, row.status !== 'PUBLISHED');
        this.rows = this.rows.map((item) => (item.id === row.id ? { ...item, ...updated } : item));
      } catch (error) {
        this.notifyError(error, '게시 상태를 바꾸지 못했습니다.');
      }
    },
    confirmRemove(row) {
      this.$q
        .dialog({
          title: '페이지 삭제',
          message: `"${row.title}" 페이지를 지웁니다. 메뉴가 이 페이지를 가리키면 지울 수 없습니다.`,
          cancel: { flat: true, noCaps: true, label: '취소' },
          ok: { unelevated: true, noCaps: true, color: 'negative', label: '삭제' },
          persistent: true,
        })
        .onOk(() => this.remove(row));
    },
    async remove(row) {
      try {
        await deleteContentPage(row.id);
        this.rows = this.rows.filter((item) => item.id !== row.id);
        this.$q.notify({ type: 'positive', message: '페이지를 지웠습니다.', position: 'top' });
      } catch (error) {
        if (error?.payload?.code === 'CONTENT_PAGE_IN_USE') {
          this.$q.notify({
            type: 'warning',
            message: '메뉴가 이 페이지를 가리키고 있어 지울 수 없습니다. 메뉴 관리에서 먼저 연결을 바꿔 주세요.',
            position: 'top',
            timeout: 6000,
            actions: [{ label: '메뉴 관리', color: 'white', handler: () => this.$router.push('/admin/menus') }],
          });
          return;
        }
        this.notifyError(error, '삭제하지 못했습니다.');
      }
    },
  },
};
</script>

<style scoped>
.cp-slug {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  color: var(--db-text-sub);
}

.cp-danger {
  color: var(--db-status-pink);
}
</style>
