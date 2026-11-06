<template>
  <!-- 라디오 줄 카드 — 줄 어디를 눌러도 고른다(공개 상태·강의 유형·프로필·수료 설정) -->
  <div class="radio-cards" role="radiogroup">
    <div
      v-for="opt in options"
      :key="String(opt.value)"
      class="radio-row"
      :class="{ 'radio-row--tall': tall }"
      @click="$emit('update:modelValue', opt.value)"
    >
      <q-radio
        :model-value="modelValue"
        :val="opt.value"
        dense
        :aria-label="opt.label"
        @update:model-value="$emit('update:modelValue', $event)"
      />
      <div class="radio-text">
        <div class="radio-label" :style="opt.color ? { color: opt.color } : null">{{ opt.label }}</div>
        <div v-if="opt.caption" class="radio-caption">{{ opt.caption }}</div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'WizardRadioCards',
  props: {
    modelValue: { type: [String, Boolean, Number], default: null },
    /** [{ label, value, caption?, color? }] — color 는 토큰(var(--db-*))만 */
    options: { type: Array, required: true },
    tall: { type: Boolean, default: false },
  },
  emits: ['update:modelValue'],
};
</script>
