<template>
  <div>
    <!-- 폰(xs): 표가 가로로만 밀려 처리 메뉴가 화면 밖에 있었다 — 한 행을 카드 한 장으로(첫 칸이 제목, 나머지는 이름·값) -->
    <div v-if="$q.screen.xs" class="console-cards">
      <q-inner-loading :showing="loading">
        <q-spinner color="primary" size="32px" />
      </q-inner-loading>
      <!-- 폰엔 머리글이 없다 — 정렬되는 칸을 버튼 줄로 -->
      <div v-if="sortableColumns.length" class="card-sort" role="group" aria-label="정렬">
        <span class="card-sort__label">정렬</span>
        <button
          v-for="column in sortableColumns"
          :key="column.key"
          type="button"
          class="card-sort__btn"
          :class="{ active: isSorted(column) }"
          :aria-pressed="isSorted(column) ? 'true' : 'false'"
          @click="onSort(column)"
        >
          {{ column.label }}
          <span v-if="isSorted(column)" class="card-sort__dir">{{ currentSortLabel(column) }}</span>
        </button>
      </div>
      <AppEmptyState v-if="!loading && !error && !rows.length" compact :description="emptyText" />
      <div
        v-for="row in rows"
        :key="row[rowKey]"
        class="console-card"
        :class="{ 'is-clickable': rowClickable }"
        @click="onRowClick(row, $event)"
      >
        <div class="console-card__head">
          <div class="console-card__title">
            <slot :name="`cell-${cardTitleColumn.key}`" :row="row">{{ row[cardTitleColumn.key] ?? '-' }}</slot>
          </div>
          <q-btn
            v-if="showActions"
            :aria-label="actionsLabel"
            flat
            round
            dense
            icon="more_vert"
            size="sm"
            class="row-kebab"
          >
            <q-menu>
              <q-list style="min-width: 140px">
                <slot name="row-actions" :row="row" />
              </q-list>
            </q-menu>
          </q-btn>
        </div>
        <dl class="console-card__fields">
          <div v-for="column in cardFieldColumns" :key="column.key" class="console-card__field">
            <dt>{{ column.label }}</dt>
            <dd>
              <slot :name="`cell-${column.key}`" :row="row">{{ row[column.key] ?? '-' }}</slot>
            </dd>
          </div>
        </dl>
      </div>
    </div>

    <div v-else class="console-table-wrap">
      <table
        class="console-table"
        :class="{ 'console-table--tall': tallRows }"
        :style="{ minWidth: `${tableMinWidth}px` }"
      >
        <colgroup>
          <col v-if="selectable" style="width: 40px" />
          <col v-for="column in columns" :key="column.key" :style="colStyle(column)" />
          <!-- 관리 열: 40px 이면 머리글 "관리"가 칸을 꽉 채워 표 오른쪽 끝에 붙는다 -->
          <col v-if="showActions" :style="{ width: ACTIONS_COL_WIDTH }" />
        </colgroup>
        <thead>
          <tr>
            <th v-if="selectable" class="col-center col-fixed">
              <q-checkbox v-model="allChecked" dense @update:model-value="toggleAll" />
            </th>
            <th
              v-for="column in columns"
              :key="column.key"
              :class="`col-${column.align || 'left'}`"
              :aria-sort="column.sortable ? ariaSort(column) : undefined"
            >
              <!-- 정렬되는 칸은 머리글이 버튼 — 늘 화살표를 보여 누를 수 있는 줄 알게, 고른 칸은 '최신순'처럼 글자로 -->
              <button
                v-if="column.sortable"
                type="button"
                class="th-sort"
                :class="{ active: isSorted(column) }"
                :title="`${column.label} ${nextSortLabel(column)}으로 보기`"
                @click="onSort(column)"
              >
                {{ column.label }}
                <q-icon :name="sortIcon(column)" size="16px" class="th-sort__icon" aria-hidden="true" />
                <span v-if="isSorted(column)" class="th-sort__dir">{{ currentSortLabel(column) }}</span>
              </button>
              <template v-else>{{ column.label }}</template>
            </th>
            <th v-if="showActions" class="col-center col-fixed">처리</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && !error && !rows.length">
            <td :colspan="colspan">
              <AppEmptyState compact :description="emptyText" />
            </td>
          </tr>
          <tr
            v-for="row in rows"
            v-else
            :key="row[rowKey]"
            :class="{ 'is-clickable': rowClickable }"
            @click="onRowClick(row, $event)"
          >
            <td v-if="selectable" class="col-center col-fixed">
              <q-checkbox v-model="checked" :val="row[rowKey]" dense />
            </td>
            <td
              v-for="column in columns"
              :key="column.key"
              :class="`col-${column.align || 'left'}`"
            >
              <slot :name="`cell-${column.key}`" :row="row">{{ row[column.key] ?? '-' }}</slot>
            </td>
            <td v-if="showActions" class="col-center col-fixed">
              <q-btn :aria-label="actionsLabel" flat round dense icon="more_vert" size="sm" class="row-kebab">
                <q-menu>
                  <q-list style="min-width: 140px">
                    <slot name="row-actions" :row="row" />
                  </q-list>
                </q-menu>
              </q-btn>
            </td>
          </tr>
        </tbody>
      </table>

      <q-inner-loading :showing="loading">
        <q-spinner color="primary" size="32px" />
      </q-inner-loading>
    </div>

    <ConsolePagination
      v-if="paginated"
      class="console-table__foot"
      :model-value="page"
      :total="total"
      :page-size="pageSize"
      :page-size-options="[10, 20, 50]"
      :unit="unit"
      @update:model-value="$emit('update:page', $event)"
      @update:page-size="$emit('update:pageSize', $event)"
    />
  </div>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import ConsolePagination from '@/components/ConsolePagination.vue';
import { nextSort, sortLabel } from '@/utils/table-sort';

const ACTIONS_COL_WIDTH = '72px';

export default {
  name: 'ConsoleTable',
  components: { AppEmptyState, ConsolePagination },
  props: {
    /** [{ key, label, width, align }] */
    columns: { type: Array, required: true },
    rows: { type: Array, default: () => [] },
    rowKey: { type: String, default: 'id' },
    loading: { type: Boolean, default: false },
    /** 체크 칸 — 고른 값을 밖으로 넘기는 일괄 처리가 없어 쓰는 화면이 없었다. 기본은 끈다 */
    selectable: { type: Boolean, default: false },
    emptyText: { type: String, default: '데이터가 없습니다.' },
    error: { type: String, default: '' },
    /** 썸네일이 들어가는 목록은 행 86 */
    tallRows: { type: Boolean, default: false },
    paginated: { type: Boolean, default: true },
    page: { type: Number, default: 1 },
    pageSize: { type: Number, default: 10 },
    total: { type: Number, default: 0 },
    unit: { type: String, default: '건' },
    /** 행 아무 곳이나 누르면 row-click — 링크·버튼·처리 메뉴(⋮)·체크 칸을 누른 건 그쪽 동작만 한다.
     *  키보드는 행 안 링크(이름)로 간다 */
    rowClickable: { type: Boolean, default: false },
    /** 머리글 정렬 { key, dir } — sortable 칸의 머리글을 누르면 sort 로 다음 정렬을 알린다 */
    sort: { type: Object, default: null },
  },
  emits: ['update:page', 'update:pageSize', 'row-click', 'sort'],
  data() {
    return { ACTIONS_COL_WIDTH, checked: [], allChecked: false, actionsLabel: '처리 메뉴' };
  },
  computed: {
    showActions() {
      return !!this.$slots['row-actions'];
    },
    /** auto 열에 minWidth 를 주면 그 아래로 줄지 않게 — 좁으면 표가 가로로 스크롤된다.
     *  (강사 그룹 관리: 고정 폭 열들이 자리를 다 차지해 이름 열이 0에 가깝게 눌렸다) */
    tableMinWidth() {
      const columnsWidth = this.columns.reduce(
        (sum, column) => sum + (column.width || column.minWidth || 0),
        0,
      );
      return columnsWidth + (this.selectable ? 40 : 0) + (this.showActions ? parseInt(ACTIONS_COL_WIDTH, 10) : 0);
    },
    /**
     * 카드 제목 칸 — 표의 첫 칸을 그대로 쓰면 공지·배너는 번호('2')가 제목이 됐다.
     * cardTitle 표시가 있는 칸, 없으면 번호(no)가 아닌 첫 칸
     */
    cardTitleColumn() {
      return this.columns.find((column) => column.cardTitle) || this.columns.find((column) => column.key !== 'no') || this.columns[0];
    },
    cardFieldColumns() {
      return this.columns.filter((column) => column.key !== this.cardTitleColumn.key);
    },
    sortableColumns() {
      return this.sort ? this.columns.filter((column) => column.sortable) : [];
    },
    colspan() {
      return this.columns.length + (this.selectable ? 1 : 0) + (this.showActions ? 1 : 0);
    },
  },
  methods: {
    onRowClick(row, event) {
      if (!this.rowClickable) return;
      // 행 안의 링크·버튼·입력·처리 메뉴는 제 일을 한다. 글자를 끌어 고르는 중이면 넘어가지 않는다
      if (event.target.closest('a, button, input, label, .q-btn, .q-checkbox, .q-menu')) return;
      if (window.getSelection?.().toString()) return;
      this.$emit('row-click', row);
    },
    isSorted(column) {
      return this.sort?.key === column.key;
    },
    onSort(column) {
      this.$emit('sort', nextSort(column, this.sort));
    },
    ariaSort(column) {
      if (!this.isSorted(column)) return 'none';
      return this.sort.dir === 'asc' ? 'ascending' : 'descending';
    },
    sortIcon(column) {
      if (!this.isSorted(column)) return 'unfold_more';
      return this.sort.dir === 'asc' ? 'arrow_upward' : 'arrow_downward';
    },
    currentSortLabel(column) {
      return sortLabel(column.sortable, this.sort.dir);
    },
    nextSortLabel(column) {
      return sortLabel(column.sortable, nextSort(column, this.sort).dir);
    },
    colStyle(column) {
      return column.width ? { width: `${column.width}px` } : { width: 'auto' };
    },
    toggleAll(value) {
      this.checked = value ? this.rows.map((row) => row[this.rowKey]) : [];
    },
  },
};
</script>

<style scoped>
.console-cards {
  position: relative;
  min-height: 80px;
}

/* 빈 목록 문구가 카드 테두리 끝까지 붙었다 */
.console-cards :deep(.empty-state) {
  padding-inline: 16px;
}

.console-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--db-border);
}

.console-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.console-card__title {
  min-width: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

/* 표에서 쓰던 좌우 여백(.cell-course·.cell-user 의 padding 0 10px)이 카드에도 남아
   썸네일·프로필이 글자보다 오른쪽으로 밀려 보였다 — 카드에선 걷어 왼쪽 줄을 맞춘다 */
.console-card :deep(.cell-course),
.console-card :deep(.cell-user),
.console-card :deep(.cell-inquiry) {
  padding-right: 0;
  padding-left: 0;
}

/* 이름·값의 위아래가 어긋났다 — 뱃지·버튼이 든 줄은 높고 글자 줄은 낮아 이름은 위로 붙고 값은 아래로 처졌다.
   모든 줄을 같은 최소 높이로 두고 이름·값을 세로 가운데에, 이름 칸 폭은 카드마다 같게 고정한다 */
.console-card__fields {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 6px 12px;
  align-items: center;
  margin: 0;
}

.console-card__field {
  display: contents;
}

/* 긴 이름('처리한 업무(요청)')이 76px 칸을 넘어 값에 붙었다 — 넘치면 낱말 단위로 줄을 바꾼다 */
.console-card__field dt {
  display: flex;
  align-items: center;
  min-height: 28px;
  font-size: 12px;
  line-height: 16px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

/* 아무 데서나 줄을 바꾸면 이메일이 'dabaeum.k / r'처럼 단어 중간에서 끊겼다 — 한 줄로 두고 넘치면 '…' */
.console-card__field dd {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  min-width: 0;
  min-height: 28px;
  margin: 0;
  overflow: hidden;
  font-size: 13px;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--db-text-body);
}

.console-card__field dd :deep(*:not(.status-badge):not(.q-btn):not(.q-btn *)) {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 표용 글자 크기(.cell-lines·.cell-title 16px)를 카드에서도 그대로 써서 어떤 값은 크고 어떤 값은 작았다 — 카드 값은 한 크기로 */
.console-card__field dd :deep(.cell-lines) {
  font-size: 13px;
  line-height: 18px;
}

.console-card__field dd :deep(.cell-title) {
  font-size: 14px;
  line-height: 20px;
}

/* 표에서 가운데 정렬하던 감싸개(.status-cell 등)가 카드에서도 뱃지를 가운데에 띄웠다 — 카드에선 왼쪽.
   뱃지·버튼 자체엔 걸지 않는다: 모든 자식에 걸었더니 뱃지 글자가 위에 붙고 버튼 글자가 왼쪽으로 쏠렸다 */
.console-card__field dd > :deep(*:not(.status-badge):not(.q-btn)) {
  justify-content: flex-start;
  margin-right: auto;
  margin-left: 0;
  text-align: left;
}

/* 세로로 쌓는 감싸개(.status-cell)는 가로 위치가 align-items 다 — 여기에만 왼쪽을 준다.
   모든 감싸개에 걸면 그림+글자처럼 가로로 놓인 칸(배지 메달·이름)의 글자가 위로 붙었다 */
.console-card__field dd > :deep(.status-cell) {
  align-items: flex-start;
}

/* 진행 막대처럼 폭을 채워야 하는 값 — 왼쪽 붙이기 규칙에 줄어 막대가 30px 남짓이 됐다 */
.console-card__field dd > :deep(.progress-cell) {
  align-self: stretch;
  width: 100%;
  height: auto;
}

.console-table-wrap {
  position: relative;
  overflow-x: auto;
}

.console-table {
  width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
}

.console-table th {
  height: 52px;
  padding: 0 10px;
  background: var(--db-bg-white);
  border-bottom: 1px solid var(--db-border);
  font-size: 16px;
  font-weight: 600;
  color: var(--db-text-title);
  white-space: nowrap;
}

/* 좌우 24px 이면 좁은 날짜 칸(가입일 등)에서 '2026.10.0'처럼 끝 글자가 잘렸다 — 머리(10px)에 가깝게 */
.console-table td {
  height: 64px;
  padding: 13px 12px;
  border-bottom: 1px solid var(--db-border);
  font-size: 16px;
  font-weight: 400;
  color: var(--db-text-title);
}

/* 칸 폭보다 긴 값(이메일 등)이 옆 칸까지 넘쳐 '가입일'과 겹쳤다 — 칸 안에서 '…'로 끝낸다 */
.console-table td {
  overflow: hidden;
}

.console-table td :deep(.cell-lines > *) {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.console-table--tall td {
  height: 86px;
}

.console-table__foot {
  height: 64px;
  padding: 16px 24px;
}

/* 40px 컬럼은 td 좌우 padding 24(=48)만으로도 넘친다 — 패딩을 빼고 내용도 가둔다 */
.col-fixed {
  padding-left: 0 !important;
  padding-right: 0 !important;
  overflow: hidden;
}

.row-kebab {
  min-width: 0;
  min-height: 0;
  padding: 4px;
}

.col-left {
  text-align: left;
}

.col-center {
  text-align: center;
}

.col-right {
  text-align: right;
}

/* 정렬 머리글 — 글자는 머리글 그대로, 안 고른 칸은 옅은 화살표 */
.th-sort {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  max-width: 100%;
  margin: 0 -4px;
  padding: 4px;
  border: 0;
  border-radius: 6px;
  background: none;
  font: inherit;
  color: inherit;
  white-space: nowrap;
  cursor: pointer;
}

.th-sort:hover {
  background: var(--db-bg-section);
}

.th-sort:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 1px;
}

.th-sort__icon {
  color: var(--db-text-sub);
  opacity: 0.6;
}

.th-sort.active .th-sort__icon {
  color: var(--db-accent);
  opacity: 1;
}

.th-sort__dir {
  font-size: 13px;
  font-weight: 700;
  color: var(--db-accent);
}

.card-sort {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--db-border);
}

.card-sort__label {
  font-size: 13px;
  color: var(--db-text-sub);
}

.card-sort__btn {
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid var(--db-border);
  border-radius: 999px;
  background: var(--db-bg-white);
  font: inherit;
  font-size: 13px;
  color: var(--db-text-body);
  cursor: pointer;
}

.card-sort__btn:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.card-sort__btn.active {
  border-color: var(--db-accent);
  background: var(--db-accent-soft);
  color: var(--db-accent);
  font-weight: 700;
}

.card-sort__dir {
  margin-left: 2px;
}

/* 행 전체를 눌러 상세로 가는 목록(rowClickable) */
tr.is-clickable,
.console-card.is-clickable {
  cursor: pointer;
}

/* 누를 수 있는 행 — 마우스가 있는 기기에서만 바탕이 옅게 바뀐다(터치는 눌린 채 남아 뺀다) */
@media (hover: hover) {
  tr.is-clickable td {
    transition: background-color 0.15s ease;
  }

  tr.is-clickable:hover td {
    background: var(--db-bg-section);
  }
}

@media (prefers-reduced-motion: reduce) {
  tr.is-clickable td {
    transition: none;
  }
}
</style>
