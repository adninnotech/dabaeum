<template>
  <!-- 이수 처리 흐름 — ① 출석으로 판정 → ② 이수 확정 → ③ 기관이 수료증 발급.
       [출석 평가] 단추만 있어 무엇을 어떤 순서로 하는지 몰랐다. 단계마다 인원과, 지금 할 일을 단추 하나로 -->
  <section class="flow" aria-label="이수 처리 순서">
    <p v-if="criteria" class="flow__criteria">
      <q-icon name="rule" size="18px" aria-hidden="true" />
      {{ criteria }}
    </p>
    <ol class="flow__steps">
      <li
        v-for="step in steps"
        :key="step.key"
        class="flow__step"
        :class="{ 'flow__step--todo': step.count > 0 && step.todo, 'flow__step--on': filter === step.filter }"
      >
        <button
          type="button"
          class="flow__step-btn"
          :aria-pressed="filter === step.filter ? 'true' : 'false'"
          @click="$emit('filter', filter === step.filter ? 'ALL' : step.filter)"
        >
          <span class="flow__no" aria-hidden="true">{{ step.no }}</span>
          <span class="flow__text">
            <span class="flow__label">{{ step.label }}</span>
            <span class="flow__count"><strong>{{ step.count }}</strong>명</span>
          </span>
        </button>
      </li>
      <li class="flow__step flow__step--last">
        <span class="flow__no" aria-hidden="true">3</span>
        <span class="flow__text">
          <span class="flow__label">기관이 수료증 발급</span>
          <span class="flow__count">확정하면 기관 화면으로 넘어갑니다</span>
        </span>
      </li>
    </ol>

    <div class="flow__actions">
      <button v-if="notYetCount" type="button" class="flow__note" @click="$emit('filter', 'NOT_COMPLETED')">
        미이수 {{ notYetCount }}명 — 출석이 더 쌓이면 다시 판정할 수 있어요
      </button>
      <q-btn
        v-if="counts.none"
        :unelevated="!counts.eligible"
        :outline="!!counts.eligible"
        no-caps
        color="primary"
        icon="fact_check"
        :label="`판정 전 ${counts.none}명 출석으로 판정하기`"
        class="flow__btn"
        :disable="busy || !!blockedReason"
        @click="$emit('evaluate-all')"
      >
        <q-tooltip v-if="blockedReason">{{ blockedReason }}</q-tooltip>
      </q-btn>
      <q-btn
        v-if="counts.eligible"
        unelevated
        no-caps
        color="positive"
        icon="task_alt"
        :label="`이수 가능 ${counts.eligible}명 확정하기`"
        class="flow__btn"
        :disable="busy"
        @click="$emit('confirm-all')"
      />
    </div>
  </section>
</template>

<script>
export default {
  name: 'CompletionFlowStrip',
  props: {
    /** { none, eligible, completed, notCompleted } */
    counts: { type: Object, required: true },
    /** 지금 거른 상태 — 'ALL' | 'NONE' | 'ELIGIBLE' | 'COMPLETED' */
    filter: { type: String, default: 'ALL' },
    busy: { type: Boolean, default: false },
    /** 끝나지 않은 회차 때문에 판정할 수 없는 이유 */
    blockedReason: { type: String, default: '' },
    /** 이수 기준 한 줄 */
    criteria: { type: String, default: '' },
  },
  emits: ['filter', 'evaluate-all', 'confirm-all'],
  computed: {
    steps() {
      return [
        { key: 'none', no: 1, label: '출석 판정 전', count: this.counts.none, filter: 'NONE', todo: true },
        { key: 'eligible', no: 2, label: '이수 가능 · 확정 대기', count: this.counts.eligible, filter: 'ELIGIBLE', todo: true },
        { key: 'completed', no: '✓', label: '이수 완료', count: this.counts.completed, filter: 'COMPLETED', todo: false },
      ];
    },
    notYetCount() {
      return this.counts.notCompleted || 0;
    },
  },
};
</script>

<style scoped>
.flow {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  /* 양끝 정렬이면 미이수 안내가 단계 카드와 떨어져 맨 오른쪽에 붙었다(QA) */
  justify-content: flex-start;
  gap: 12px 24px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--db-border-light);
}

.flow__criteria {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-basis: 100%;
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-body);
}

.flow__steps {
  display: flex;
  flex-wrap: wrap;
  align-items: stretch;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.flow__step {
  display: flex;
  align-items: center;
}

/* 단계 사이 화살표 */
.flow__step + .flow__step::before {
  content: '›';
  margin-right: 8px;
  font-size: 20px;
  color: var(--db-text-sub);
}

.flow__step-btn,
.flow__step--last {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 52px;
  padding: 6px 14px 6px 8px;
  border: 1px solid var(--db-border);
  border-radius: 10px;
  background: var(--db-bg-white);
  font: inherit;
  text-align: left;
  color: var(--db-text-body);
}

.flow__step-btn {
  cursor: pointer;
}

.flow__step-btn:hover {
  border-color: var(--db-accent);
}

.flow__step-btn:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.flow__step--last {
  border-style: dashed;
}

.flow__no {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--db-bg-tag);
  font-size: 14px;
  font-weight: 700;
  color: var(--db-text-sub);
}

.flow__text {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.flow__label {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-title);
}

.flow__count {
  font-size: 13px;
  color: var(--db-text-sub);
}

.flow__count strong {
  font-size: 17px;
  font-variant-numeric: tabular-nums;
}

/* 할 일이 남은 단계 — 번호·숫자를 파랗게 */
.flow__step--todo .flow__no {
  background: var(--db-accent);
  color: var(--db-on-color);
}

.flow__step--todo .flow__count strong {
  color: var(--db-accent);
}

.flow__step--on .flow__step-btn {
  border-color: var(--db-accent);
  background: var(--db-accent-soft);
}

.flow__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.flow__btn {
  min-height: 44px;
  font-size: 15px;
}

.flow__note {
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  font-size: 13px;
  color: var(--db-text-sub);
  text-decoration: underline;
  cursor: pointer;
}

@media (max-width: 599px) {
  .flow__step + .flow__step::before {
    display: none;
  }

  .flow__steps,
  .flow__actions,
  .flow__btn {
    width: 100%;
  }
}
</style>
