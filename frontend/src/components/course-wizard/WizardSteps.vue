<template>
  <!-- 단계 표시 — 키보드로도 고를 수 있게 button. 앞 단계로만 돌아갈 수 있어 나머지는 막는다 -->
  <nav class="wizard-steps" aria-label="등록 단계">
    <template v-for="(label, i) in steps" :key="label">
      <div v-if="i > 0" class="wizard-steps__line" />
      <button
        type="button"
        class="wizard-steps__item"
        :class="{ active: step === i + 1, done: step > i + 1 }"
        :aria-current="step === i + 1 ? 'step' : undefined"
        :disabled="i + 1 >= step"
        @click="$emit('go', i + 1)"
      >
        <span class="wizard-steps__num">
          <q-icon v-if="step > i + 1" name="check" size="24px" />
          <template v-else>{{ i + 1 }}</template>
        </span>
        <!-- 폰에선 단계 이름을 지금 단계만 남긴다 — 넷 다 쓰면 줄이 화면을 넘었다 -->
        <span class="wizard-steps__label">{{ label }}</span>
      </button>
    </template>
  </nav>
</template>

<script>
export default {
  name: 'WizardSteps',
  props: {
    steps: { type: Array, required: true },
    step: { type: Number, required: true },
  },
  emits: ['go'],
};
</script>
