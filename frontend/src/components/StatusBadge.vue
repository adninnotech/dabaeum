<template>
  <span
    class="status-badge"
    :class="{ 'status-badge--sm': size === 'sm', 'status-badge--soft': soft }"
    :style="badgeStyle"
  >
    {{ displayLabel }}
  </span>
</template>

<script>
import { EDUCATION_TYPE_LABEL, hexForStatus, STATUS_LABEL } from '@/utils/status';

/** 연한 배경 변형 — 상태색별 틴트 (blue·green·orange는 d9 실측, 나머지는 같은 방식 산출) */
const SOFT_BG = {
  '#2a5fcc': '#e0e5fc',
  '#237a54': '#e6f5eb',
  '#a65300': '#f9eed6',
  '#c72656': '#fde9ef',
  '#8a38f5': '#f1e7fe',
  '#727379': '#eeeeef',
};

export default {
  name: 'StatusBadge',
  props: {
    status: {
      type: String,
      default: '',
    },
    /** 라벨 덮어쓰기 (탭 로컬 문구: "수강 확정" 등) */
    label: {
      type: String,
      default: '',
    },
    /** 색 덮어쓰기 (hex) */
    color: {
      type: String,
      default: '',
    },
    /** 'md'(기본, h28) | 'sm'(h24, 카드 이미지 위) */
    size: {
      type: String,
      default: 'md',
    },
    /** 연한 배경 + 상태색 글자 pill */
    soft: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    background() {
      return this.color || hexForStatus(this.status);
    },
    badgeStyle() {
      if (!this.soft) return { background: this.background };
      // 다크: 밝은 틴트가 어두운 바탕에서 형광처럼 떴다 — 같은 색을 옅게 섞는다
      if (this.$q.dark.isActive) {
        // 상태색은 흰 바탕 대비(4.5:1)에 맞춰 진하다 — 어두운 바탕 위 글자로는 흰색을 섞어 밝힌다
        return {
          background: `color-mix(in srgb, ${this.background} 24%, transparent)`,
          color: `color-mix(in srgb, ${this.background} 55%, white)`,
        };
      }
      return { background: SOFT_BG[this.background] || '#eeeeef', color: this.background };
    },
    displayLabel() {
      if (this.label) return this.label;
      return STATUS_LABEL[this.status] || EDUCATION_TYPE_LABEL[this.status] || this.status || '-';
    },
  },
};
</script>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  /* 인라인 컨텍스트에서 베이스라인 아래 여백이 붙어 행이 2px 커진다 */
  vertical-align: middle;
  height: 28px;
  padding: 0 10px;
  border-radius: 4px;
  color: var(--db-on-color);
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

.status-badge--soft {
  border-radius: 9999px;
}

.status-badge--sm {
  height: 24px;
  padding: 0 8px;
  font-weight: 600;
}
</style>
