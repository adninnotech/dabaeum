<template>
  <!-- 배지 획득 기준 — 단계 배지는 단계 칩(한눈에 비교), 그 밖은 사람 말 한 줄.
       서버 criteria 문장은 분류 코드(TSIX_…)가 그대로 섞여 규칙이 없을 때만 쓴다 -->
  <div v-if="isTiered" class="criteria" :class="{ 'criteria--compact': compact }">
    <span class="criteria__label">{{ tieredLabel }}</span>
    <ol class="criteria__steps" aria-label="단계별 기준">
      <li
        v-for="(level, index) in levels"
        :key="level.level || index"
        class="criteria__step"
        :class="{ 'criteria__step--current': currentLevel && (level.level || index + 1) === currentLevel }"
        :aria-current="currentLevel && (level.level || index + 1) === currentLevel ? 'step' : undefined"
      >
        <span class="criteria__name">{{ level.name || `${index + 1}단계` }}</span>
        <span class="criteria__range">{{ tierRangeText(levels, index) }}</span>
      </li>
    </ol>
  </div>
  <p v-else class="criteria__text">{{ text }}</p>
</template>

<script>
import { findClassificationTrail } from '@/utils/classification';
import { ruleText, tierRangeText } from '@/utils/badge-rule';

export default {
  name: 'BadgeCriteria',
  props: {
    /** 배지 정의 규칙 { type, levels, classificationCode, … } — 없으면 fallback 문장 */
    rule: { type: Object, default: null },
    fallback: { type: String, default: '' },
    classificationTree: { type: Array, default: () => [] },
    courseTitles: { type: Object, default: () => ({}) },
    /** 받은 단계 — 그 칩을 강조한다 */
    currentLevel: { type: Number, default: null },
    /** 표 칸처럼 좁은 곳 */
    compact: { type: Boolean, default: false },
  },
  computed: {
    isTiered() {
      return this.rule?.type === 'TIERED' && (this.rule.levels || []).length > 0;
    },
    levels() {
      return this.rule?.levels || [];
    },
    tieredLabel() {
      const code = this.rule?.classificationCode;
      if (!code) return '강좌 이수 건수';
      const trail = findClassificationTrail(this.classificationTree, code);
      const field = trail.length ? trail[trail.length - 1].name : '지정 분야';
      return `${field} 분야 강좌 이수 건수`;
    },
    text() {
      if (this.rule?.type) {
        return ruleText(this.rule, { tree: this.classificationTree, courseTitles: this.courseTitles });
      }
      return this.fallback || '기준 정보 없음';
    },
  },
  methods: {
    tierRangeText,
  },
};
</script>

<style scoped>
/* Hallmark · component: criteria chips · genre: modern-minimal · theme: system-managed (--db-* 토큰)
 * states: default · current(받은 단계 강조) · compact(표 칸)
 */
.criteria {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.criteria__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--db-text-sub);
}

.criteria__steps {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.criteria__step {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  padding: 2px 8px;
  border: 1px solid var(--db-border);
  border-radius: 999px;
  background: var(--db-bg-white);
  white-space: nowrap;
}

.criteria__step--current {
  border-color: var(--db-primary);
  background: var(--db-accent-soft);
}

.criteria__name {
  font-size: 13px;
  font-weight: 700;
  color: var(--db-text-title);
}

.criteria__step--current .criteria__name {
  color: var(--db-primary);
}

.criteria__range {
  font-size: 13px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.criteria--compact .criteria__step {
  padding: 1px 6px;
}

.criteria__text {
  margin: 0;
  word-break: keep-all;
}
</style>
