<template>
  <q-page class="console-list">
    <h1 class="console-list__title">{{ title }}</h1>

    <AppErrorBanner v-if="error" :message="error">
      <template #action>
        <q-btn flat dense no-caps color="negative" label="다시 시도" @click="emitSearch" />
      </template>
    </AppErrorBanner>

    <div
      v-if="stats.length"
      class="console-list__stats"
      :style="{ gridTemplateColumns: `repeat(${statColumns}, minmax(0, 1fr))`, '--stat-cols-xs': statColumns === 3 ? 3 : 2 }"
    >
      <div v-for="stat in stats" :key="stat.label" class="stat-card">
        <div class="stat-card__label">{{ stat.label }}</div>
        <div class="stat-card__value">
          <template v-if="error">-</template>
          <template v-else>{{ stat.value }}{{ stat.unit || '' }}</template>
        </div>
      </div>
    </div>

    <section class="console-list__card">
      <header class="console-list__head">
        <h2 class="console-list__list-title">{{ listTitle }}</h2>

        <div class="console-list__filters">
          <q-input
            v-if="searchable"
            v-model="query"
            outlined
            dense
            clearable
            :placeholder="searchPlaceholder"
            class="filter-search"
            @keyup="onSearchKeyup"
          >
            <template #prepend><q-icon name="search" size="20px" /></template>
          </q-input>

          <q-select
            v-for="filter in filters"
            :key="filter.key"
            v-model="selected[filter.key]"
            outlined
            dense
            emit-value
            map-options
            clearable
            :options="filter.options"
            :label="filter.label"
            :disable="!filter.options.length"
            class="filter-select"
          />

          <!-- 선택 목록으로 안 되는 필터(기간 등)는 페이지가 채운다 -->
          <slot name="filter-extra" />

          <q-btn unelevated no-caps class="filter-submit" label="검색" @click="emitSearch" />
          <slot name="header-actions" />
        </div>
      </header>

      <ConsoleTable
        :columns="columns"
        :rows="rows"
        :row-key="rowKey"
        :loading="loading"
        :selectable="selectable"
        :empty-text="emptyText"
        :error="error"
        :tall-rows="tallRows"
        :page="page"
        :page-size="pageSize"
        :total="total"
        :unit="unit"
        :row-clickable="rowClickable"
        :sort="sort"
        @sort="$emit('sort', $event)"
        @row-click="$emit('row-click', $event)"
        @update:page="$emit('update:page', $event)"
        @update:page-size="$emit('update:pageSize', $event)"
      >
        <template v-for="name in tableSlots" :key="name" #[name]="slotProps">
          <slot :name="name" v-bind="slotProps || {}" />
        </template>
      </ConsoleTable>
    </section>

    <!-- 페이지별 다이얼로그 등 -->
    <slot />
  </q-page>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import ConsoleTable from '@/components/console/ConsoleTable.vue';

export default {
  name: 'ConsoleListPage',
  components: { AppErrorBanner, ConsoleTable },
  props: {
    title: { type: String, required: true },
    listTitle: { type: String, default: '목록' },
    /** [{ label, value, unit }] */
    stats: { type: Array, default: () => [] },
    statColumns: { type: Number, default: 4 },
    /** [{ key, label, width, align }] */
    columns: { type: Array, required: true },
    rows: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    total: { type: Number, default: 0 },
    page: { type: Number, default: 1 },
    pageSize: { type: Number, default: 10 },
    unit: { type: String, default: '건' },
    searchPlaceholder: { type: String, default: '검색어를 입력해 주세요' },
    /** [{ key, label, options }] */
    filters: { type: Array, default: () => [] },
    rowKey: { type: String, default: 'id' },
    selectable: { type: Boolean, default: false },
    emptyText: { type: String, default: '데이터가 없습니다.' },
    /** 조회 실패 메시지 — 있으면 배너를 띄우고 빈 상태를 감춘다 */
    error: { type: String, default: '' },
    tallRows: { type: Boolean, default: false },
    /** 목록 API 에 q 가 없으면 검색창을 숨긴다 */
    searchable: { type: Boolean, default: true },
    /** 처음 고른 채로 열 필터 — 예: 문의 관리는 '답변 대기'부터 */
    initialFilters: { type: Object, default: () => ({}) },
    /** 행을 누르면 row-click (ConsoleTable rowClickable) */
    rowClickable: { type: Boolean, default: false },
    /** 머리글 정렬 { key, dir } — 칸 정의에 sortable 이 있어야 머리글을 누를 수 있다 */
    sort: { type: Object, default: null },
  },
  emits: ['search', 'update:page', 'update:pageSize', 'row-click', 'sort'],
  data() {
    return {
      query: '',
      selected: { ...this.initialFilters },
    };
  },
  computed: {
    /** cell-* 와 row-actions 만 테이블로 넘긴다 (header-actions·default는 이 컴포넌트 것) */
    tableSlots() {
      return Object.keys(this.$slots).filter(
        (name) => name.startsWith('cell-') || name === 'row-actions',
      );
    },
  },
  methods: {
    // 저장소 규칙 — @keyup.enter 대신 메서드에서 키를 본다
    onSearchKeyup(event) {
      if (event.key === 'Enter') this.emitSearch();
    },
    emitSearch() {
      this.$emit('search', { q: (this.query || '').trim(), filters: { ...this.selected } });
    },
  },
};
</script>

<style scoped>
.console-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 48px 40px 20px;
  background: var(--db-bg-page);
}

.console-list__title {
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

.console-list__stats {
  display: grid;
  gap: 16px;
}

@media (max-width: 1023px) {
  /* 좁은 화면에서 좌우 40px 은 표 자리를 너무 먹는다 — 다른 콘솔 화면 여백(16px)에 맞춘다 */
  .console-list {
    padding-right: 16px;
    padding-left: 16px;
  }

  .console-list__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
  }
}

.stat-card {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  height: 120px;
  padding: 16px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.stat-card__label {
  font-size: 16px;
  font-weight: 400;
  color: var(--db-text-title);
}

.stat-card__value {
  font-size: 24px;
  font-weight: 700;
  color: var(--db-text-title);
}

/* 폰: 120px 카드가 2+1 로 놓여 목록 전에 화면 400px 을 썼다 — 셋이면 한 줄, 작게 */
@media (max-width: 599px) {
  .console-list__stats {
    grid-template-columns: repeat(var(--stat-cols-xs), minmax(0, 1fr)) !important;
    gap: 8px;
  }

  .stat-card {
    gap: 4px;
    height: auto;
    padding: 12px;
  }

  .stat-card__label {
    font-size: 13px;
    color: var(--db-text-sub);
    word-break: keep-all;
  }

  .stat-card__value {
    font-size: 20px;
  }
}

.console-list__card {
  display: flex;
  flex-direction: column;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.console-list__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  min-height: 52px;
  padding: 10px 20px;
}

.console-list__list-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--db-text-title);
}

.console-list__filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
  max-width: 100%;
}

.filter-search {
  width: 360px;
  max-width: 100%;
}

.filter-search :deep(.q-field__control) {
  height: 36px;
  border-radius: 8px;
}

.filter-select {
  width: 120px;
}

.filter-select :deep(.q-field__control) {
  height: 36px;
  min-height: 36px;
  border-radius: 6px;
}

.filter-submit {
  width: 60px;
  height: 36px;
  border-radius: 8px;
  background: var(--db-accent-fill);
  color: var(--db-on-color);
  font-size: 12px;
  font-weight: 600;
}

/* 폰: 검색칸(360px)과 선택 칸이 한 줄에 놓여 페이지가 397px 로 밀렸다 — 검색칸은 한 줄, 선택 칸은 나눠 쓴다 */
@media (max-width: 599px) {
  .console-list__title {
    height: auto;
    font-size: 24px;
  }

  .console-list__head {
    padding: 10px 16px;
  }

  .console-list__filters {
    width: 100%;
  }

  .filter-search {
    flex: 1 1 100%;
    width: auto;
  }

  .filter-select {
    flex: 1 1 120px;
    width: auto;
    min-width: 0;
  }
}
</style>
