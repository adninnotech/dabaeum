<template>
  <q-page class="menus">
    <div class="menus__head">
      <h1 class="menus__title">메뉴 관리</h1>
      <q-btn unelevated no-caps color="primary" icon="add" label="메뉴 추가" @click="openCreate(null)" />
    </div>
    <p class="menus__lead">
      학습자 화면 상단에 더하는 메뉴입니다. 기본 메뉴(강좌찾기·학습관리·학습지갑·마이페이지·고객센터)는 늘 보이고,
      여기서 만든 메뉴는 그 뒤에 붙습니다. 끌어서 순서를 바꾸거나 ↑↓ 로 옮기면 바로 저장됩니다. 메뉴는 두 단계까지입니다.
    </p>

    <AppErrorBanner v-if="errorMessage" :message="errorMessage" />

    <section class="menus__card" :class="{ 'menus__card--busy': saving }">
      <p v-if="!loading && !tree.length" class="menus__empty">아직 만든 메뉴가 없습니다.</p>

      <ol v-else class="menus__list">
        <li v-for="(top, topIndex) in tree" :key="top.id" class="menus__group">
          <MenuRow
            :node="top"
            :first="topIndex === 0"
            :last="topIndex === tree.length - 1"
            :drop-hint="dropTarget === top.id"
            :locked="saving"
            @dragstart="startDrag(top)"
            @dragend="endDrag"
            @dragover="overRow(top, $event)"
            @drop="dropBefore(top)"
            @move="move(top, $event)"
            @edit="openEdit(top)"
            @add-child="openCreate(top.id)"
            @remove="confirmRemove(top)"
          />
          <ol v-if="top.children.length || canNestInto(top)" class="menus__children">
            <li v-for="(child, childIndex) in top.children" :key="child.id">
              <MenuRow
                :node="child"
                child
                :first="childIndex === 0"
                :last="childIndex === top.children.length - 1"
                :drop-hint="dropTarget === child.id"
                :locked="saving"
                @dragstart="startDrag(child)"
                @dragend="endDrag"
                @dragover="overRow(child, $event)"
                @drop="dropBefore(child)"
                @move="move(child, $event)"
                @edit="openEdit(child)"
                @remove="confirmRemove(child)"
              />
            </li>
            <!-- 끌고 있을 때만: 이 메뉴의 하위 맨 끝으로 -->
            <li
              v-if="canNestInto(top)"
              class="menus__drop"
              :class="{ 'menus__drop--on': dropTarget === `into:${top.id}` }"
              @dragover.prevent="dropTarget = `into:${top.id}`"
              @drop.prevent="dropInto(top)"
            >
              "{{ top.name }}" 하위 메뉴로 넣기
            </li>
          </ol>
        </li>
        <li
          v-if="dragging && dragging.parentId"
          class="menus__drop menus__drop--root"
          :class="{ 'menus__drop--on': dropTarget === 'root' }"
          @dragover.prevent="dropTarget = 'root'"
          @drop.prevent="dropToRoot"
        >
          최상위 맨 끝으로 꺼내기
        </li>
      </ol>
    </section>

    <MenuEditDialog
      v-model="dialogOpen"
      :menu="editing"
      :default-parent-id="createParentId"
      :top-level="tree"
      :next-sort-order="nextSortOrder"
      @saved="onSaved"
    />

    <q-inner-loading :showing="loading" />
  </q-page>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import MenuEditDialog from '@/components/console/MenuEditDialog.vue';
import MenuRow from '@/components/console/MenuRow.vue';
import { deleteMenu, listAdminMenus, reorderMenus } from '@/services/cms-api';

const SORT_STEP = 10;

function sortNodes(nodes) {
  return [...(nodes || [])]
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((node) => ({ ...node, children: sortNodes(node.children) }));
}

/** 트리 → PUT /admin/menus/order 전체 배치. 순서는 10 간격으로 다시 매긴다 */
function toOrderItems(tree) {
  return tree.flatMap((top, i) => [
    { id: top.id, parentId: null, sortOrder: (i + 1) * SORT_STEP },
    ...top.children.map((child, j) => ({ id: child.id, parentId: top.id, sortOrder: (j + 1) * SORT_STEP })),
  ]);
}

/** node 를 트리에서 빼고 [새 트리, 뺀 노드] (불변) */
function detach(tree, id) {
  let found = null;
  const next = tree
    .filter((top) => {
      if (top.id === id) found = top;
      return top.id !== id;
    })
    .map((top) => ({
      ...top,
      children: top.children.filter((child) => {
        if (child.id === id) found = child;
        return child.id !== id;
      }),
    }));
  return [next, found];
}

/* 메뉴 트리 편집(설계서 §7, 플랫폼 관리자) — 피그마에 없다. 끌어서 놓기 + ↑↓ 버튼(키보드). 옮길 때마다 전체 배치를 저장하고,
 * 서버가 거절하면(3단계 등 422) 원래 트리로 되돌린다 */
export default {
  name: 'AdminMenusPage',
  components: { AppErrorBanner, MenuEditDialog, MenuRow },
  data() {
    return {
      tree: [],
      loading: false,
      saving: false,
      errorMessage: '',
      dragging: null,
      dropTarget: '',
      dialogOpen: false,
      editing: null,
      createParentId: null,
    };
  },
  computed: {
    nextSortOrder() {
      const siblings = this.createParentId
        ? this.tree.find((top) => top.id === this.createParentId)?.children || []
        : this.tree;
      return (siblings.length + 1) * SORT_STEP;
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
        this.tree = sortNodes(await listAdminMenus());
      } catch (error) {
        this.tree = [];
        this.errorMessage = error?.message || '메뉴를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    // 하위가 있는 메뉴는 다른 메뉴 아래로 못 간다 — 끌기 중 안내 칸도 그 규칙대로만 연다
    canNestInto(top) {
      const d = this.dragging;
      return !!d && d.id !== top.id && !d.children?.length && d.parentId !== top.id;
    },
    // 저장 중에는 옮기지 않는다 — 앞 저장의 응답이 뒤 옮김을 덮어쓴다(연속 조작에서 재현)
    startDrag(node) {
      if (this.saving) return;
      this.dragging = node;
    },
    endDrag() {
      this.dragging = null;
      this.dropTarget = '';
    },
    overRow(node, event) {
      if (!this.dragging || this.dragging.id === node.id) return;
      // 하위가 있는 메뉴를 하위 줄 앞에 놓으면 3단계가 된다 — 놓기 표시를 하지 않는다
      if (node.parentId && this.dragging.children?.length) return;
      event.preventDefault();
      this.dropTarget = node.id;
    },
    dropBefore(target) {
      const moving = this.dragging;
      this.endDrag();
      if (!moving || moving.id === target.id) return;
      const [rest, node] = detach(this.tree, moving.id);
      const placed = { ...node, parentId: target.parentId || null };
      const next = target.parentId
        ? rest.map((top) => {
            if (top.id !== target.parentId) return top;
            const i = top.children.findIndex((c) => c.id === target.id);
            return { ...top, children: [...top.children.slice(0, i), placed, ...top.children.slice(i)] };
          })
        : (() => {
            const i = rest.findIndex((t) => t.id === target.id);
            return [...rest.slice(0, i), placed, ...rest.slice(i)];
          })();
      this.persist(next);
    },
    dropInto(top) {
      const moving = this.dragging;
      this.endDrag();
      if (!moving) return;
      const [rest, node] = detach(this.tree, moving.id);
      const next = rest.map((item) =>
        item.id === top.id ? { ...item, children: [...item.children, { ...node, parentId: top.id }] } : item,
      );
      this.persist(next);
    },
    dropToRoot() {
      const moving = this.dragging;
      this.endDrag();
      if (!moving) return;
      const [rest, node] = detach(this.tree, moving.id);
      this.persist([...rest, { ...node, parentId: null, children: node.children || [] }]);
    },
    // ↑(-1) ↓(+1) — 같은 단계 안에서 한 칸
    move(node, delta) {
      if (this.saving) return;
      const swap = (list) => {
        const i = list.findIndex((item) => item.id === node.id);
        const j = i + delta;
        if (i < 0 || j < 0 || j >= list.length) return list;
        const copy = [...list];
        [copy[i], copy[j]] = [copy[j], copy[i]];
        return copy;
      };
      const next = node.parentId
        ? this.tree.map((top) => (top.id === node.parentId ? { ...top, children: swap(top.children) } : top))
        : swap(this.tree);
      this.persist(next);
    },
    async persist(next) {
      if (this.saving) return;
      const previous = this.tree;
      this.tree = next;
      this.saving = true;
      try {
        this.tree = sortNodes(await reorderMenus(toOrderItems(next)));
      } catch (error) {
        this.tree = previous;
        this.$q.notify({
          type: 'negative',
          message:
            error?.status === 422
              ? '그렇게 옮길 수 없습니다. 메뉴는 두 단계까지이고, 하위가 있는 메뉴는 최상위에만 둘 수 있습니다.'
              : error?.message || '순서를 저장하지 못했습니다.',
          position: 'top',
        });
      } finally {
        this.saving = false;
      }
    },
    openCreate(parentId) {
      this.editing = null;
      this.createParentId = parentId;
      this.dialogOpen = true;
    },
    openEdit(node) {
      this.editing = node;
      this.createParentId = null;
      this.dialogOpen = true;
    },
    onSaved() {
      this.$q.notify({ type: 'positive', message: '메뉴를 저장했습니다.', position: 'top' });
      this.load();
    },
    confirmRemove(node) {
      if (node.children?.length) {
        this.$q.notify({
          type: 'warning',
          message: '하위 메뉴가 있는 메뉴는 지울 수 없습니다. 하위 메뉴를 먼저 옮기거나 지워 주세요.',
          position: 'top',
        });
        return;
      }
      this.$q
        .dialog({
          title: '메뉴 삭제',
          message: `"${node.name}" 메뉴를 지웁니다. 연결된 콘텐츠 페이지는 지워지지 않습니다.`,
          cancel: { flat: true, noCaps: true, label: '취소' },
          ok: { unelevated: true, noCaps: true, color: 'negative', label: '삭제' },
          persistent: true,
        })
        .onOk(() => this.remove(node));
    },
    async remove(node) {
      try {
        await deleteMenu(node.id);
        await this.load();
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message:
            error?.payload?.code === 'SITE_MENU_HAS_CHILDREN'
              ? '하위 메뉴가 있어 지울 수 없습니다.'
              : error?.message || '삭제하지 못했습니다.',
          position: 'top',
        });
      }
    },
  },
};
</script>

<style scoped>
.menus {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 48px 40px 20px;
  background: var(--db-bg-page);
}

.menus__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.menus__head .q-btn {
  border-radius: 6px;
}

.menus__title {
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

.menus__lead {
  margin: -8px 0 0;
  font-size: 14px;
  line-height: 21px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.menus__card {
  padding: 12px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
  transition: opacity 120ms ease-out;
}

.menus__card--busy {
  opacity: 0.6;
}

@media (prefers-reduced-motion: reduce) {
  .menus__card {
    transition: none;
  }
}

.menus__empty {
  margin: 0;
  padding: 40px 0;
  text-align: center;
  font-size: 14px;
  color: var(--db-text-sub);
}

.menus__list,
.menus__children {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.menus__group + .menus__group {
  margin-top: 4px;
  padding-top: 4px;
  border-top: 1px solid var(--db-border);
}

.menus__children {
  margin: 4px 0 0 32px;
}

.menus__drop {
  padding: 8px 12px;
  border: 1px dashed var(--db-border);
  border-radius: 6px;
  font-size: 13px;
  color: var(--db-text-sub);
}

.menus__drop--root {
  margin-top: 8px;
}

.menus__drop--on {
  border-color: var(--db-accent);
  background: var(--db-accent-soft);
  color: var(--db-accent);
}

@media (max-width: 1023px) {
  .menus {
    padding: 24px 16px;
  }

  .menus__children {
    margin-left: 16px;
  }
}
</style>
