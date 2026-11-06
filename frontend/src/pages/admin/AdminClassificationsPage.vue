<template>
  <!-- 강좌 분류 사용 관리(설계서 §12) — 표준이 코드·이름을 정하므로 사용 여부만 바꾼다. 피그마 B-9 카테고리 관리를 대체한다.
       평면 표 + 들여쓰기로는 대·중·소가 한눈에 안 들어와, 대분류 묶음 > 중분류 소제목 > 소분류 격자로 보인다 -->
  <q-page class="cls-page">
    <h1 class="cls-page__title">강좌 분류 관리</h1>

    <AppErrorBanner :message="errorMessage">
      <template #action>
        <q-btn flat dense no-caps color="negative" label="다시 시도" @click="load" />
      </template>
    </AppErrorBanner>

    <div class="cls-page__stats">
      <div v-for="stat in stats" :key="stat.label" class="stat-card">
        <div class="stat-card__label">{{ stat.label }}</div>
        <div class="stat-card__value">{{ errorMessage ? '-' : `${stat.value}개` }}</div>
      </div>
    </div>

    <section class="cls-card" aria-labelledby="cls-card-title">
      <header class="cls-card__head">
        <div>
          <h2 id="cls-card-title" class="cls-card__title">표준 강좌 분류</h2>
          <p class="cls-card__hint">강의는 가장 아래 단계(소분류, 또는 아래가 없는 분류)에만 붙습니다.</p>
        </div>
        <div class="cls-card__filters">
          <q-input
            v-model="query"
            outlined
            dense
            clearable
            placeholder="분류명 또는 코드로 찾기"
            aria-label="분류명 또는 코드로 찾기"
            class="cls-card__search"
          >
            <template #prepend><q-icon name="search" size="20px" /></template>
          </q-input>
          <q-select
            v-model="enabledFilter"
            outlined
            dense
            emit-value
            map-options
            clearable
            :options="ENABLED_FILTER"
            label="사용 여부"
            class="cls-card__select"
          />
          <q-btn
            outline
            no-caps
            color="primary"
            :label="allCollapsed ? '모두 펼치기' : '모두 접기'"
            :disable="isFiltering || !tree.length"
            @click="toggleAll"
          />
        </div>
      </header>

      <!-- 대분류 전체를 한 줄에 — 누르면 그 대분류만. 숫자는 강좌를 붙일 수 있는 맨 아래 분류 수 -->
      <nav v-if="tree.length" class="cls-roots" aria-label="대분류">
        <button
          type="button"
          class="cls-root"
          :class="{ 'cls-root--active': !rootCode }"
          :aria-pressed="!rootCode ? 'true' : 'false'"
          @click="rootCode = null"
        >
          전체 <span class="cls-root__count">{{ leafTotal }}</span>
        </button>
        <button
          v-for="root in decoratedTree"
          :key="root.code"
          type="button"
          class="cls-root"
          :class="{ 'cls-root--active': rootCode === root.code, 'cls-root--off': !root.enabled }"
          :aria-pressed="rootCode === root.code ? 'true' : 'false'"
          @click="rootCode = rootCode === root.code ? null : root.code"
        >
          {{ root.name }}
          <span class="cls-root__count">{{ root.leaf ? 1 : root.counts.leaf }}</span>
        </button>
      </nav>

      <div class="cls-card__body" :aria-busy="loading ? 'true' : 'false'">
        <ClassificationGroup
          v-for="root in visibleTree"
          :key="root.code"
          :node="root"
          :expanded="isFiltering || !collapsed[root.code]"
          :saving-code="savingCode"
          @update:expanded="setExpanded(root.code, $event)"
          @toggle="toggle"
        />
        <p v-if="!loading && !errorMessage && !visibleTree.length" class="cls-card__empty">
          조건에 맞는 분류가 없습니다.
        </p>
        <q-inner-loading :showing="loading">
          <q-spinner color="primary" size="32px" />
        </q-inner-loading>
      </div>
    </section>
  </q-page>
</template>

<script>
import { confirmDialog } from '@/utils/confirm-dialog';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import ClassificationGroup from '@/components/console/ClassificationGroup.vue';
import { listAdminCourseClassifications, setCourseClassificationEnabled } from '@/services/classification-api';
import { isLeafClassification } from '@/utils/classification';

const ENABLED_FILTER = [
  { label: '사용 중', value: true },
  { label: '사용 중지', value: false },
];

/** 트리에 화면용 값을 붙인다 — usable = 강좌에 붙일 수 있음(자신·조상 모두 켜짐 + 하위 없음) */
function decorate(nodes, ancestorsEnabled = true) {
  return (nodes || []).map((node) => {
    const leaf = isLeafClassification(node);
    const children = decorate(node.children, ancestorsEnabled && node.enabled);
    const descendants = children.flatMap((child) => [child, ...(child.descendants || [])]);
    const leaves = descendants.filter((item) => item.leaf);
    return {
      code: node.code,
      name: node.name,
      level: node.level,
      enabled: node.enabled,
      leaf,
      ancestorsEnabled,
      usable: leaf && node.enabled && ancestorsEnabled,
      children,
      descendants,
      counts: {
        mid: children.length,
        leaf: leaves.length,
        usable: leaves.filter((item) => item.usable).length,
      },
    };
  });
}

/** 조건에 맞는 노드와 그 조상만 남긴다. 노드 자신이 맞으면 하위는 통째로 둔다(경로가 끊기지 않게) */
function filterTree(nodes, matches) {
  return nodes.flatMap((node) => {
    if (matches(node)) return [node];
    const children = filterTree(node.children, matches);
    return children.length ? [{ ...node, children }] : [];
  });
}

export default {
  name: 'AdminClassificationsPage',
  components: { AppErrorBanner, ClassificationGroup },
  data() {
    return {
      ENABLED_FILTER,
      tree: [],
      loading: false,
      errorMessage: '',
      savingCode: '',
      query: '',
      enabledFilter: null,
      rootCode: null,
      collapsed: {},
    };
  },
  computed: {
    decoratedTree() {
      return decorate(this.tree);
    },
    leafTotal() {
      return this.decoratedTree.reduce((sum, root) => sum + (root.leaf ? 1 : root.counts.leaf), 0);
    },
    allNodes() {
      return this.decoratedTree.flatMap((root) => [root, ...root.descendants]);
    },
    stats() {
      const leaves = this.allNodes.filter((node) => node.leaf);
      return [
        { label: '전체 분류', value: this.allNodes.length },
        { label: '강의에 붙일 수 있는 분류', value: leaves.filter((node) => node.usable).length },
        { label: '사용 중지', value: this.allNodes.filter((node) => !node.enabled).length },
      ];
    },
    isFiltering() {
      return Boolean((this.query || '').trim()) || this.enabledFilter !== null;
    },
    visibleTree() {
      const roots = this.rootCode
        ? this.decoratedTree.filter((root) => root.code === this.rootCode)
        : this.decoratedTree;
      const needle = (this.query || '').trim().toLowerCase();
      const enabled = this.enabledFilter;
      if (!needle && enabled === null) return roots;
      return filterTree(roots, (node) => {
        const textOk = !needle || `${node.name} ${node.code}`.toLowerCase().includes(needle);
        const enabledOk = enabled === null || node.enabled === enabled;
        return textOk && enabledOk;
      });
    },
    allCollapsed() {
      return this.decoratedTree.filter((root) => !root.leaf).every((root) => this.collapsed[root.code]);
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
        this.tree = await listAdminCourseClassifications();
      } catch (error) {
        this.tree = [];
        this.errorMessage = error?.message || '강좌 분류를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    setExpanded(code, expanded) {
      this.collapsed = { ...this.collapsed, [code]: !expanded };
    },
    toggleAll() {
      const collapse = !this.allCollapsed;
      this.collapsed = Object.fromEntries(this.decoratedTree.map((root) => [root.code, collapse]));
    },
    replaceEnabled(nodes, code, enabled) {
      return (nodes || []).map((node) => ({
        ...node,
        enabled: node.code === code ? enabled : node.enabled,
        children: this.replaceEnabled(node.children, code, enabled),
      }));
    },
    /** 분류 아래 몇 개가 함께 꺼지는지 */
    countDescendants(node) {
      return (node.children || []).reduce((sum, child) => sum + 1 + this.countDescendants(child), 0);
    },
    async toggle(node, enabled) {
      // 상위 분류를 끄면 아래 분류까지 한꺼번에 꺼진다 — 끈 뒤에 안내하던 것을 먼저 묻는다
      if (!enabled && !node.leaf) {
        const count = this.countDescendants(node);
        const ok = await confirmDialog(this.$q, {
          title: '분류 사용 중지',
          message: `'${node.name}'${count ? `과 아래 분류 ${count}개` : ''}를 새 강의에 쓸 수 없게 됩니다. 이미 붙은 강의는 그대로입니다.`,
          ok: '사용 중지',
          color: 'negative',
          cancel: '닫기',
        });
        if (!ok) return;
      }
      this.savingCode = node.code;
      try {
        const updated = await setCourseClassificationEnabled(node.code, enabled);
        this.tree = this.replaceEnabled(this.tree, node.code, updated?.enabled ?? enabled);
        this.$q.notify({
          type: 'positive',
          message: `'${node.name}' 분류를 ${enabled ? '다시 사용합니다' : '사용 중지했습니다'}.`,
          position: 'top',
        });
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error?.message || '사용 여부를 바꾸지 못했습니다.', position: 'top' });
      } finally {
        this.savingCode = '';
      }
    },
  },
};
</script>

<style scoped>
/* Hallmark · pre-emit critique: P4 H5 E4 S4 R4 V4
 * system-managed(--db-* 토큰) · 셸(제목·요약 카드·목록 카드)은 ConsoleListPage 와 같은 치수, 본문만 계층형 */
.cls-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 48px 40px 20px;
  background: var(--db-bg-page);
}

.cls-page__title {
  display: flex;
  align-items: center;
  height: 58px;
  margin: 0;
  padding: 10px 0;
  font-size: 32px;
  line-height: 1.2;
  font-weight: 700;
  letter-spacing: normal;
  color: var(--db-text-title);
}

.cls-page__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
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
  color: var(--db-text-title);
}

.stat-card__value {
  font-size: 24px;
  font-weight: 700;
  color: var(--db-text-title);
  font-variant-numeric: tabular-nums;
}

.cls-card {
  display: flex;
  flex-direction: column;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.cls-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 14px 20px;
}

.cls-card__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: normal;
  color: var(--db-text-title);
}

.cls-card__hint {
  margin: 2px 0 0;
  font-size: 13px;
  color: var(--db-text-sub);
}

.cls-card__filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.cls-card__search {
  width: 280px;
  max-width: 100%;
}

.cls-card__select {
  width: 130px;
}

/* 대분류 칩 */
.cls-roots {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 0 20px 14px;
  border-bottom: 1px solid var(--db-border);
}

.cls-root {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--db-border);
  border-radius: 16px;
  background: var(--db-bg-white);
  font: inherit;
  font-size: 13px;
  font-weight: 500;
  color: var(--db-text-body);
  white-space: nowrap;
  cursor: pointer;
}

.cls-root:hover {
  background: var(--db-bg-subtle);
}

.cls-root:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.cls-root--active {
  border-color: var(--db-primary);
  background: var(--db-accent-soft);
  color: var(--db-primary);
  font-weight: 700;
}

.cls-root--off {
  color: var(--db-text-sub);
}

.cls-root__count {
  font-size: 12px;
  font-weight: 600;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.cls-card__body {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
  padding: 16px 20px 20px;
}

.cls-card__empty {
  margin: 24px 0;
  font-size: 14px;
  text-align: center;
  color: var(--db-text-sub);
}

@media (max-width: 1023px) {
  .cls-page {
    padding: 24px 16px;
  }

  .cls-page__title {
    font-size: 24px;
  }

  .cls-page__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .cls-card__head,
  .cls-card__body {
    padding-left: 16px;
    padding-right: 16px;
  }

  .cls-roots {
    padding: 0 16px 12px;
  }
}

@media (max-width: 599px) {
  .cls-page__stats {
    grid-template-columns: minmax(0, 1fr);
  }

  .stat-card {
    height: auto;
  }

  .cls-card__search {
    width: 100%;
  }
}
</style>
