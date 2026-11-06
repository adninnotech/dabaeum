<template>
  <!-- 강사 주요 경력 — 줄바꿈이 사라진 한 덩어리 평문이라 읽히지 않았다.
       한 줄 = 한 항목, '현)'·'전)'으로 시작하면 '현재'·'이전' 표시를 붙인다(강사 마이페이지·강좌 상세 공통) -->
  <ul v-if="items.length" class="career-list">
    <li v-for="(item, index) in items" :key="index" class="career-list__item">
      <span v-if="item.tag" class="career-list__tag" :class="`career-list__tag--${item.tag}`">
        {{ TAG_LABEL[item.tag] }}
      </span>
      <span v-else class="career-list__dot" aria-hidden="true" />
      <span class="career-list__text">{{ item.text }}</span>
    </li>
  </ul>
  <span v-else class="career-list__empty">{{ emptyText }}</span>
</template>

<script>
const TAG_LABEL = { current: '현재', past: '이전' };
// '현) …', '현재) …', '전) …', '(현) …' — 괄호는 반각·전각 모두
const TAG_PATTERN = /^\(?\s*(현재|현|전|이전)\s*[)）]\s*/;
// 목록 기호로 시작하면 떼어 낸다('- …', '• …', '· …')
const BULLET_PATTERN = /^[-•·*]\s+/;

/** 경력 글 → [{ tag: 'current'|'past'|'', text }] */
export function parseCareer(value) {
  return String(value || '')
    .split(/\r?\n/)
    .map((line) => line.trim().replace(BULLET_PATTERN, ''))
    .filter(Boolean)
    .map((line) => {
      const match = line.match(TAG_PATTERN);
      if (!match) return { tag: '', text: line };
      const tag = match[1].startsWith('현') ? 'current' : 'past';
      return { tag, text: line.slice(match[0].length) || line };
    });
}

export default {
  name: 'InstructorCareerList',
  props: {
    value: { type: String, default: '' },
    emptyText: { type: String, default: '-' },
  },
  data() {
    return { TAG_LABEL };
  },
  computed: {
    items() {
      return parseCareer(this.value);
    },
  },
};
</script>

<style scoped>
.career-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.career-list__item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 15px;
  line-height: 1.5;
  color: var(--db-text-body);
}

.career-list__tag {
  flex-shrink: 0;
  min-width: 40px;
  margin-top: 1px;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.6;
  text-align: center;
}

.career-list__tag--current {
  background: var(--db-accent-soft);
  color: var(--db-accent);
}

.career-list__tag--past {
  background: var(--db-bg-tag);
  color: var(--db-text-sub);
}

/* 표시가 없는 줄은 점 — 표시 있는 줄과 글 시작선을 맞춘다 */
.career-list__dot {
  flex-shrink: 0;
  width: 40px;
  height: 22px;
  background: radial-gradient(circle, var(--db-text-sub) 0 2.5px, transparent 3px);
}

.career-list__text {
  min-width: 0;
  overflow-wrap: anywhere;
}

.career-list__empty {
  color: var(--db-text-sub);
}
</style>
