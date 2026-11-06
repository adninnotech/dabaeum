<template>
  <!-- 단계 배지 진행 — '1 / 7개 보유'만으로는 다음 단계까지 얼마나 남았는지 몰랐다.
       수료한 강좌 수(나의 강좌의 수료)와 단계표(minCount)로 센다 -->
  <section v-if="progress" class="badge-progress" :aria-label="`${progress.familyName} 진행`">
    <div class="badge-progress__head">
      <span class="badge-progress__family">{{ progress.familyName }}</span>
      <span class="badge-progress__count">강좌 {{ completed }}건 이수</span>
    </div>
    <p class="badge-progress__text">
      <template v-if="progress.next">
        다음 <strong>'{{ progress.next.name }}'</strong>까지 강좌 <strong>{{ progress.remaining }}건</strong> 남았어요
      </template>
      <template v-else>최고 단계 <strong>'{{ progress.current?.name }}'</strong>에 도달했어요</template>
    </p>
    <div
      class="badge-progress__bar"
      role="progressbar"
      :aria-valuenow="completed"
      aria-valuemin="0"
      :aria-valuemax="progress.goal"
    >
      <div class="badge-progress__fill" :style="{ width: `${progress.percent}%` }" />
    </div>
    <span class="badge-progress__scale">{{ completed }} / {{ progress.goal }}건</span>
  </section>
</template>

<script>
export default {
  name: 'BadgeProgress',
  props: {
    definitions: { type: Array, default: () => [] },
    /** 수료한 강좌 수 — null 이면(비로그인·조회 실패) 그리지 않는다 */
    completedCount: { type: Number, default: null },
  },
  computed: {
    completed() {
      return this.completedCount || 0;
    },
    // 단계 배지(TIERED) 첫 번째 것의 진행 — 단계 배지가 없으면 그리지 않는다
    progress() {
      if (this.completedCount === null) return null;
      const family = this.definitions.find((item) => item.rule?.type === 'TIERED');
      const levels = [...(family?.rule?.levels || [])].sort((a, b) => a.minCount - b.minCount);
      if (!levels.length) return null;
      const current = [...levels].reverse().find((level) => this.completed >= level.minCount) || null;
      const next = levels.find((level) => this.completed < level.minCount) || null;
      const goal = next ? next.minCount : current.minCount;
      return {
        familyName: family.name,
        current,
        next,
        goal,
        remaining: next ? next.minCount - this.completed : 0,
        percent: Math.min(100, Math.round((this.completed / goal) * 100)),
      };
    },
  },
};
</script>

<style scoped>
.badge-progress {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px 18px;
  border: 1px solid var(--db-border);
  border-radius: 10px;
  background: var(--db-bg-white);
}

.badge-progress__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.badge-progress__family {
  font-size: 14px;
  font-weight: 700;
  color: var(--db-text-sub);
}

.badge-progress__count {
  font-size: 14px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.badge-progress__text {
  margin: 0;
  font-size: 16px;
  color: var(--db-text-title);
  word-break: keep-all;
}

.badge-progress__text strong {
  color: var(--db-accent);
}

.badge-progress__bar {
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--db-bg-subtle);
}

.badge-progress__fill {
  height: 100%;
  border-radius: inherit;
  background: var(--db-accent-fill);
  transition: width 0.3s ease;
}

.badge-progress__scale {
  align-self: flex-end;
  font-size: 13px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

@media (prefers-reduced-motion: reduce) {
  .badge-progress__fill {
    transition: none;
  }
}
</style>
