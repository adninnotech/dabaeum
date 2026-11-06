<template>
  <span class="app-tag" :class="{ 'app-tag--lg': size === 'lg' }"><slot /></span>
</template>

<script>
export default {
  name: 'AppTag',
  props: {
    /** 'md'(기본, h24) | 'lg'(h28 — 강좌 카드) */
    size: { type: String, default: 'md' },
  },
};
</script>

<style scoped>
.app-tag {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--db-bg-tag);
  font-size: 13px;
  font-weight: 400;
  color: var(--db-text-body);
  white-space: nowrap;
}

.app-tag--lg {
  height: 28px;
  padding: 0 16px;
}
</style>
