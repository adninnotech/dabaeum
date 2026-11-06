<template>
  <ConsoleListPage
    v-model:page="page"
    v-model:page-size="pageSize"
    title="FAQ 관리"
    list-title="자주 묻는 질문"
    unit="개"
    empty-text="등록한 FAQ가 없습니다. 고객센터에 보일 질문과 답변을 추가해 주세요."
    search-placeholder="질문 또는 답변으로 검색"
    row-key="id"
    :selectable="false"
    tall-rows
    :columns="columns"
    :rows="pagedRows"
    :loading="loading"
    :error="errorMessage"
    :total="filteredRows.length"
    row-clickable
    @search="onSearch"
    @row-click="openEdit"
  >
    <template #header-actions>
      <q-btn outline no-caps color="primary" icon="open_in_new" label="고객센터에서 보기" to="/support" target="_blank" />
      <q-btn unelevated no-caps color="primary" icon="add" label="FAQ 추가" @click="openCreate" />
    </template>

    <template #cell-sortOrder="{ row }">
      <span class="faq-order">{{ row.sortOrder }}</span>
    </template>

    <template #cell-question="{ row }">
      <div class="cell-lines faq-cell">
        <button type="button" class="cell-title cell-link faq-cell__q" @click="openEdit(row)">{{ row.question }}</button>
        <span class="faq-cell__a">{{ row.answer }}</span>
      </div>
    </template>

    <template #row-actions="{ row }">
      <q-item v-close-popup clickable @click="openEdit(row)">
        <q-item-section>수정</q-item-section>
      </q-item>
      <q-separator />
      <q-item v-close-popup clickable @click="confirmRemove(row)">
        <q-item-section class="faq-danger">삭제</q-item-section>
      </q-item>
    </template>

    <FaqEditDialog v-model="dialogOpen" :faq="editing" :next-sort-order="nextSortOrder" @saved="onSaved" />
  </ConsoleListPage>
</template>

<script>
import ConsoleListPage from '@/components/console/ConsoleListPage.vue';
import FaqEditDialog from '@/components/console/FaqEditDialog.vue';
import consoleList from '@/mixins/console-list';
import { deleteFaq, listAdminFaqs } from '@/services/support-api';

const COLUMNS = [
  { key: 'sortOrder', label: '순서', width: 80, align: 'center' },
  // 폰 카드 제목은 질문 — 첫 칸(순서 숫자)이 제목이 되면 카드가 '1'로 시작했다
  { key: 'question', label: '질문 / 답변', cardTitle: true },
];

// 서버 순서: sortOrder ↑, 등록 순 ↑ — 수정 뒤 로컬 목록도 같은 규칙으로 맞춘다(등록 순은 원래 자리로 대신)
function byDisplayOrder(rows) {
  return rows
    .map((row, index) => ({ row, index }))
    .sort((a, b) => a.row.sortOrder - b.row.sortOrder || a.index - b.index)
    .map(({ row }) => row);
}

/* FAQ 관리(플랫폼 관리자) — 피그마에 없다. 콘솔 목록 + 추가·수정 창. 공개 고객센터(/support)가 이 순서로 보인다 */
export default {
  name: 'AdminFaqsPage',
  components: { ConsoleListPage, FaqEditDialog },
  mixins: [consoleList],
  data() {
    return {
      columns: COLUMNS,
      searchFields: ['question', 'answer'],
      dialogOpen: false,
      editing: null,
    };
  },
  computed: {
    nextSortOrder() {
      const orders = this.rows.map((row) => row.sortOrder ?? 0);
      return orders.length ? Math.max(...orders) + 1 : 0;
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
        const result = await listAdminFaqs();
        this.rows = Array.isArray(result.data) ? result.data : [];
      } catch (error) {
        this.handleLoadError(error, 'FAQ를 불러오지 못했습니다.');
      } finally {
        this.loading = false;
      }
    },
    openCreate() {
      this.editing = null;
      this.dialogOpen = true;
    },
    openEdit(row) {
      this.editing = row;
      this.dialogOpen = true;
    },
    onSaved(saved) {
      if (!saved?.id) {
        this.load();
        return;
      }
      const exists = this.rows.some((row) => row.id === saved.id);
      const next = exists ? this.rows.map((row) => (row.id === saved.id ? saved : row)) : [...this.rows, saved];
      this.rows = byDisplayOrder(next);
      this.$q.notify({ type: 'positive', message: exists ? '저장했습니다.' : 'FAQ를 추가했습니다.', position: 'top' });
    },
    confirmRemove(row) {
      this.$q
        .dialog({
          title: 'FAQ 삭제',
          message: `"${row.question}" 질문을 지웁니다. 되돌릴 수 없습니다.`,
          cancel: { flat: true, noCaps: true, label: '취소' },
          ok: { unelevated: true, noCaps: true, color: 'negative', label: '삭제' },
          persistent: true,
        })
        .onOk(() => this.remove(row));
    },
    async remove(row) {
      try {
        await deleteFaq(row.id);
        this.rows = this.rows.filter((item) => item.id !== row.id);
        this.$q.notify({ type: 'positive', message: 'FAQ를 지웠습니다.', position: 'top' });
      } catch (error) {
        this.notifyError(error, '삭제하지 못했습니다.');
      }
    },
  },
};
</script>

<style scoped>
.faq-order {
  font-variant-numeric: tabular-nums;
  color: var(--db-text-sub);
}

.faq-cell {
  min-width: 0;
}

.faq-cell__q {
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  text-align: left;
  cursor: pointer;
  overflow-wrap: anywhere;
}

.faq-cell__a {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  font-size: 13px;
  line-height: 1.5;
  color: var(--db-text-sub);
  white-space: pre-line;
}

.faq-danger {
  color: var(--db-status-pink);
}
</style>
