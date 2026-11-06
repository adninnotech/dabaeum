<template>
  <div class="signup-stepper">
    <template v-for="(step, index) in stepItems" :key="step.key">
      <div class="signup-stepper__block">
        <span class="signup-stepper__circle" :class="`signup-stepper__circle--${step.state}`">
          <q-icon v-if="step.state === 'done'" name="check" size="24px" />
          <template v-else>{{ step.no }}</template>
        </span>
        <span class="signup-stepper__label" :class="`signup-stepper__label--${step.state}`">
          {{ step.label }}
        </span>
      </div>
      <span v-if="index < stepItems.length - 1" class="signup-stepper__sep" />
    </template>
  </div>
</template>

<script>
const STEPS = [
  { key: 'terms', no: 1, label: '약관 동의' },
  { key: 'form', no: 2, label: '정보 입력' },
  { key: 'complete', no: 3, label: '가입 완료' },
];

export default {
  name: 'SignupStepper',
  props: {
    current: {
      type: Number,
      default: 1,
    },
  },
  computed: {
    stepItems() {
      return STEPS.map((step, index) => {
        const no = index + 1;
        const state = no < this.current ? 'done' : no === this.current ? 'active' : 'todo';
        return { ...step, no, state };
      });
    },
  },
};
</script>

<style scoped lang="scss">
/* 880×74 = padding 22 + 원 30 + padding 22. 가로: 30+[원30+12+라벨]+12+선234+... = 880 */
.signup-stepper {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 880px;
  margin: 0 auto;
  padding: 22px 30px;
  background: var(--db-bg-white);
  border-radius: 8px;
}

.signup-stepper__block {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.signup-stepper__circle {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  border-radius: 9999px;
  font-size: 14px;
  line-height: 18px;
  font-weight: 700;
}

/* 완료는 흰 배경 + 회색 체크다. 파란 채움은 활성 하나뿐 */
.signup-stepper__circle--done,
.signup-stepper__circle--todo {
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  color: var(--db-text-sub);
}

.signup-stepper__circle--active {
  background: var(--db-accent-fill);
  border: 1px solid var(--db-accent);
  color: var(--db-on-color);
}

.signup-stepper__label {
  white-space: nowrap;
  font-size: 16px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.signup-stepper__label--active {
  font-weight: 700;
  color: var(--db-accent);
}

.signup-stepper__sep {
  flex-shrink: 0;
  width: 234px;
  height: 1px;
  background: var(--db-text-sub);
}

/* 폰: 880 고정 + 234 구분선이 375 폭을 넘었다 — 선은 남는 폭만, 라벨은 현재 단계만 */
@media (max-width: 1023px) {
  .signup-stepper {
    gap: 8px;
    width: 100%;
    max-width: 880px;
    padding: 16px;
  }

  .signup-stepper__block {
    gap: 8px;
  }

  .signup-stepper__label {
    font-size: 14px;
  }

  .signup-stepper__label--done,
  .signup-stepper__label--todo {
    display: none;
  }

  .signup-stepper__sep {
    flex: 1 1 auto;
    width: auto;
    min-width: 12px;
  }
}
</style>
