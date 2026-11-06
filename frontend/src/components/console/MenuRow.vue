<template>
  <div
    class="menu-row"
    :class="{ 'menu-row--hint': dropHint, 'menu-row--hidden': !node.visible }"
    :draggable="!locked"
    @dragstart="onDragStart"
    @dragend="$emit('dragend')"
    @dragover="$emit('dragover', $event)"
    @drop.prevent="$emit('drop')"
    @click="onRowClick"
  >
    <q-icon name="drag_indicator" size="20px" class="menu-row__handle" aria-hidden="true" />
    <div class="menu-row__main">
      <span class="menu-row__name">{{ node.name }}</span>
      <span class="menu-row__target">
        <span class="menu-row__type">{{ LINK_TYPE_LABEL[node.linkType] }}</span>
        {{ menuTargetText(node) }}
      </span>
    </div>
    <span v-if="!node.visible" class="menu-row__flag">숨김</span>
    <span v-if="node.linkType === 'CONTENT' && !node.contentPublished" class="menu-row__flag menu-row__flag--warn">
      미게시
    </span>
    <div class="menu-row__actions">
      <q-btn flat dense round icon="arrow_upward" size="sm" :disable="first || locked" :aria-label="`${node.name} 위로`" @click="$emit('move', -1)" />
      <q-btn flat dense round icon="arrow_downward" size="sm" :disable="last || locked" :aria-label="`${node.name} 아래로`" @click="$emit('move', 1)" />
      <q-btn v-if="!child" flat dense no-caps size="sm" label="하위 추가" @click="$emit('add-child')" />
      <q-btn flat dense no-caps size="sm" label="수정" @click="$emit('edit')" />
      <q-btn flat dense no-caps size="sm" label="삭제" class="menu-row__delete" @click="$emit('remove')" />
    </div>
  </div>
</template>

<script>
import { LINK_TYPE_LABEL, menuTargetText } from '@/utils/site-menu';

/* 메뉴 트리 한 줄 — 끌기 대상이자 놓기 대상. 숨김·미게시(초안 페이지를 가리킴)를 표시한다 */
export default {
  name: 'MenuRow',
  props: {
    node: { type: Object, required: true },
    child: { type: Boolean, default: false },
    first: { type: Boolean, default: false },
    last: { type: Boolean, default: false },
    dropHint: { type: Boolean, default: false },
    /** 순서 저장 중 — 끌기·옮기기를 막는다 */
    locked: { type: Boolean, default: false },
  },
  emits: ['dragstart', 'dragend', 'dragover', 'drop', 'move', 'edit', 'add-child', 'remove'],
  data() {
    return { LINK_TYPE_LABEL };
  },
  methods: {
    menuTargetText,
    /** 줄 아무 곳이나 누르면 수정 창 — 위·아래·하위 추가·삭제 단추는 제 일을 한다 */
    onRowClick(event) {
      if (event.target.closest('button, a, .q-btn')) return;
      this.$emit('edit');
    },
    onDragStart(event) {
      // Firefox 는 데이터가 없으면 끌기를 시작하지 않는다
      event.dataTransfer?.setData('text/plain', this.node.id);
      if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move';
      this.$emit('dragstart');
    },
  },
};
</script>

<style scoped>
.menu-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 8px 8px 4px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: var(--db-bg-white);
  cursor: pointer;
}

/* 끌기는 손잡이 모양으로 알린다 — 줄은 누르면 수정이 열린다 */
.menu-row__handle {
  cursor: grab;
}

@media (hover: hover) {
  .menu-row {
    transition: background-color 0.15s ease, border-color 0.15s ease;
  }

  .menu-row:hover {
    border-color: var(--db-border);
    background: var(--db-bg-section);
  }
}

@media (prefers-reduced-motion: reduce) {
  .menu-row {
    transition: none;
  }
}

/* 놓을 자리 — 이 줄 앞에 들어간다 */
.menu-row--hint {
  border-color: var(--db-accent);
  box-shadow: inset 0 2px 0 var(--db-accent);
}

.menu-row__handle {
  flex-shrink: 0;
  color: var(--db-text-sub);
}

.menu-row__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.menu-row__name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 15px;
  font-weight: 600;
  color: var(--db-text-title);
}

.menu-row--hidden .menu-row__name {
  color: var(--db-text-sub);
}

.menu-row__target {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--db-text-sub);
}

.menu-row__type {
  margin-right: 4px;
  font-weight: 600;
  color: var(--db-text-body);
}

.menu-row__flag {
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: 4px;
  background: var(--db-bg-page);
  font-size: 12px;
  font-weight: 600;
  color: var(--db-text-sub);
}

.menu-row__flag--warn {
  background: color-mix(in srgb, var(--db-status-orange) 14%, transparent);
  color: var(--db-status-orange-text);
}

.menu-row__actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 2px;
}

.menu-row__delete {
  color: var(--db-status-pink);
}

@media (max-width: 599px) {
  .menu-row {
    flex-wrap: wrap;
  }

  .menu-row__actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
