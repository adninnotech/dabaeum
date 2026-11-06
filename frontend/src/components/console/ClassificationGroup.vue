<template>
  <!-- 대분류 한 묶음 — 대분류(머리 띠) > 중분류(소제목 줄) > 소분류(격자 항목)로 무게를 달리해 단계가 한눈에 보이게 한다 -->
  <article class="cls-group" :class="{ 'cls-group--off': !node.enabled, 'cls-group--closed': node.leaf || !expanded }">
    <header class="cls-group__head">
      <button
        v-if="!node.leaf"
        type="button"
        class="cls-group__fold"
        :aria-expanded="expanded ? 'true' : 'false'"
        :aria-controls="`cls-body-${node.code}`"
        :aria-label="`${node.name} ${expanded ? '접기' : '펼치기'}`"
        @click="$emit('update:expanded', !expanded)"
      >
        <q-icon :name="expanded ? 'expand_more' : 'chevron_right'" size="22px" />
      </button>
      <span v-else class="cls-group__fold cls-group__fold--blank" aria-hidden="true" />

      <div class="cls-group__title">
        <span class="cls-level cls-level--1" aria-label="대분류">대</span>
        <h3 class="cls-group__name">{{ node.name }}</h3>
        <code class="cls-code">{{ node.code }}</code>
      </div>

      <p v-if="!node.leaf" class="cls-group__meta">
        중분류 {{ node.counts.mid }} · 소분류 {{ node.counts.leaf }} ·
        <strong>등록 가능 {{ node.counts.usable }}</strong>
      </p>
      <StatusBadge v-else-if="!node.usable" class="cls-group__meta" :label="usableLabel(node)" :color="usableColor(node)" />
      <p v-else class="cls-group__meta">하위 없음 · 바로 등록</p>

      <q-toggle
        :model-value="node.enabled"
        :disable="savingCode === node.code"
        :aria-label="`${node.name} 사용`"
        color="primary"
        @update:model-value="$emit('toggle', node, $event)"
      />
    </header>

    <div v-if="!node.leaf && expanded" :id="`cls-body-${node.code}`" class="cls-group__body">
      <section
        v-for="mid in node.children"
        :key="mid.code"
        class="cls-mid"
        :class="{ 'cls-mid--off': !mid.enabled || !mid.ancestorsEnabled }"
      >
        <div class="cls-mid__head">
          <span class="cls-level cls-level--2" aria-label="중분류">중</span>
          <h4 class="cls-mid__name">{{ mid.name }}</h4>
          <code class="cls-code">{{ mid.code }}</code>
          <span v-if="!mid.leaf" class="cls-mid__meta">소분류 {{ mid.children.length }}</span>
          <StatusBadge v-else-if="!mid.usable" :label="usableLabel(mid)" :color="usableColor(mid)" />
          <span v-else class="cls-mid__meta">바로 등록</span>
          <q-toggle
            class="cls-mid__toggle"
            :model-value="mid.enabled"
            :disable="savingCode === mid.code"
            :aria-label="`${mid.name} 사용`"
            color="primary"
            dense
            @update:model-value="$emit('toggle', mid, $event)"
          />
        </div>

        <ul v-if="!mid.leaf" class="cls-leaves">
          <li
            v-for="leaf in mid.children"
            :key="leaf.code"
            class="cls-leaf"
            :class="{ 'cls-leaf--off': !leaf.usable }"
          >
            <span class="cls-level cls-level--3" aria-label="소분류">소</span>
            <span class="cls-leaf__text">
              <span class="cls-leaf__name">{{ leaf.name }}</span>
              <code class="cls-code">{{ leaf.code }}</code>
            </span>
            <!-- 쓸 수 있는 게 기본 — 예외(사용 중지·상위 꺼짐)만 배지로 -->
            <StatusBadge v-if="!leaf.usable" :label="usableLabel(leaf)" :color="usableColor(leaf)" />
            <q-toggle
              :model-value="leaf.enabled"
              :disable="savingCode === leaf.code"
              :aria-label="`${leaf.name} 사용`"
              color="primary"
              dense
              @update:model-value="$emit('toggle', leaf, $event)"
            />
          </li>
        </ul>
      </section>
    </div>
  </article>
</template>

<script>
import StatusBadge from '@/components/StatusBadge.vue';

export default {
  name: 'ClassificationGroup',
  components: { StatusBadge },
  props: {
    /** 꾸민 대분류 노드 — { code, name, enabled, leaf, usable, ancestorsEnabled, counts, children[] } */
    node: { type: Object, required: true },
    expanded: { type: Boolean, default: true },
    savingCode: { type: String, default: '' },
  },
  emits: ['toggle', 'update:expanded'],
  methods: {
    usableLabel(item) {
      if (item.usable) return '등록 가능';
      return item.enabled ? '상위가 꺼짐' : '사용 중지';
    },
    usableColor(item) {
      return item.usable ? 'var(--db-status-green)' : 'var(--db-status-gray)';
    },
  },
};
</script>

<style scoped>
/* Hallmark · component: tree group · genre: modern-minimal · theme: system-managed (--db-* 토큰)
 * states: default · hover(행) · focus(접기 버튼·토글) · disabled(저장 중 토글) · off(사용 중지·상위 꺼짐)
 * pre-emit critique: P4 H5 E4 S4 R4 V4
 */
.cls-group {
  border: 1px solid var(--db-border);
  border-radius: 8px;
  background: var(--db-bg-white);
  overflow: hidden;
}

.cls-group__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px 12px 8px;
  background: var(--db-bg-subtle);
  border-bottom: 1px solid var(--db-border);
}

.cls-group--closed .cls-group__head {
  border-bottom: 0;
}

.cls-group__fold {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--db-text-body);
  cursor: pointer;
}

.cls-group__fold:hover {
  background: var(--db-bg-active);
}

.cls-group__fold:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 1px;
}

.cls-group__fold--blank {
  cursor: default;
}

.cls-group__title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.cls-group__name {
  margin: 0;
  font-size: 17px;
  line-height: 24px;
  font-weight: 700;
  letter-spacing: normal;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.cls-group__meta {
  margin: 0;
  font-size: 13px;
  color: var(--db-text-sub);
  white-space: nowrap;
}

.cls-group__meta strong {
  font-weight: 600;
  color: var(--db-text-body);
}

.cls-group__body {
  display: flex;
  flex-direction: column;
}

/* 중분류 */
.cls-mid {
  padding: 12px 16px 14px 48px;
  border-top: 1px solid var(--db-border-light);
}

.cls-mid:first-child {
  border-top: 0;
}

.cls-mid__head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-left: 10px;
  border-left: 3px solid var(--db-primary);
}

.cls-mid__name {
  margin: 0;
  font-size: 15px;
  line-height: 22px;
  font-weight: 600;
  letter-spacing: normal;
  color: var(--db-text-title);
}

.cls-mid__meta {
  font-size: 12px;
  color: var(--db-text-sub);
}

.cls-mid__toggle {
  margin-left: auto;
}

/* 소분류 — 중분류 아래 격자 */
.cls-leaves {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 12px;
  margin: 10px 0 0;
  padding: 0 0 0 13px;
  list-style: none;
}

.cls-leaf {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 6px 10px;
  border: 1px solid var(--db-border-light);
  border-radius: 6px;
}

.cls-leaf:hover {
  background: var(--db-bg-subtle);
}

.cls-leaf__text {
  display: flex;
  align-items: baseline;
  gap: 6px;
  flex: 1;
  min-width: 0;
}

.cls-leaf__name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 14px;
  color: var(--db-text-title);
}

/* 꺼진 항목은 흐리게 — 토글은 그대로 눌린다 */
.cls-group--off .cls-group__name,
.cls-mid--off .cls-mid__name,
.cls-leaf--off .cls-leaf__name {
  color: var(--db-text-sub);
}

/* 단계 표시 */
.cls-level {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  font-weight: 700;
}

.cls-level--1 {
  width: 24px;
  height: 24px;
  background: var(--db-primary);
  color: var(--db-on-color);
  font-size: 12px;
}

.cls-level--2 {
  width: 20px;
  height: 20px;
  background: var(--db-accent-soft);
  color: var(--db-primary);
  font-size: 11px;
}

.cls-level--3 {
  width: 18px;
  height: 18px;
  border: 1px solid var(--db-border);
  color: var(--db-text-sub);
  font-size: 10px;
}

.cls-code {
  flex-shrink: 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

@media (max-width: 1023px) {
  .cls-leaves {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 599px) {
  .cls-group__head {
    flex-wrap: wrap;
  }

  .cls-group__meta {
    order: 3;
    width: 100%;
    padding-left: 42px;
    white-space: normal;
  }

  .cls-mid {
    padding-left: 16px;
  }

  .cls-mid__head {
    flex-wrap: wrap;
  }

  .cls-group .cls-code {
    display: none;
  }
}
</style>
