<template>
  <!-- 사유를 받아 실행하는 동작 공통(정정·재개·폐기·재발급·거절) — 사유는 서버 이력으로 남는다 -->
  <q-dialog :model-value="modelValue" :persistent="saving" @update:model-value="close" @before-show="reset">
    <q-card class="correction">
      <q-card-section class="correction__head">
        <div class="text-h6">{{ title }}</div>
        <q-btn v-close-popup flat round dense icon="close" aria-label="닫기" :disable="saving" />
      </q-card-section>
      <q-separator />

      <q-card-section class="correction__body">
        <p class="correction__target">{{ target }}</p>
        <ul v-if="notes.length" class="correction__notes">
          <li v-for="note in notes" :key="note">{{ note }}</li>
        </ul>
        <!-- 사유 외 추가 입력(예: 재발급 유효기간) — 값은 부모가 들고 있다 -->
        <slot />
        <q-input
          v-model="reason"
          type="textarea"
          outlined
          autogrow
          counter
          :maxlength="reasonMax"
          :label="`${reasonLabel} *`"
          :hint="reasonHint"
          :error="!!fieldError"
          :error-message="fieldError"
          :disable="saving"
          input-class="correction__input"
        />
        <div v-if="serverError" class="correction__error" role="alert">
          <q-icon name="error_outline" size="18px" aria-hidden="true" />
          <span>{{ serverError }}</span>
        </div>
      </q-card-section>

      <q-card-actions align="right" class="correction__actions">
        <q-btn v-close-popup flat no-caps label="닫기" :disable="saving" />
        <q-btn unelevated no-caps :color="confirmColor" :label="confirmLabel" :loading="saving" @click="submit" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
const REASON_MAX = 1000;

export default {
  name: 'CorrectionReasonDialog',
  props: {
    modelValue: { type: Boolean, default: false },
    title: { type: String, required: true },
    /** 무엇을 정정하는지 한 줄 (예: "홍길동(900115) · 엑셀 실무 활용반") */
    target: { type: String, default: '' },
    /** 실행 전에 알아야 할 결과 */
    notes: { type: Array, default: () => [] },
    confirmLabel: { type: String, default: '정정' },
    reasonLabel: { type: String, default: '정정 사유' },
    reasonHint: { type: String, default: '감사 이력으로 남습니다.' },
    /** 되돌리기(확정 취소)는 negative, 다시 열기(재개)는 primary */
    confirmColor: { type: String, default: 'negative' },
    /** 사유 최대 길이 — API 마다 다르다(수료증 1000, 배지 폐기 200) */
    reasonMax: { type: Number, default: REASON_MAX },
    /** (reason) => Promise — 실패하면 창을 닫지 않고 서버 메시지를 보인다 */
    action: { type: Function, required: true },
    /** (error) => string | null — 코드별 안내로 바꿀 때 */
    errorMessage: { type: Function, default: null },
  },
  emits: ['update:modelValue', 'done'],
  data() {
    return { reason: '', fieldError: '', serverError: '', saving: false };
  },
  methods: {
    reset() {
      this.reason = '';
      this.fieldError = '';
      this.serverError = '';
    },
    close(value) {
      if (!this.saving) this.$emit('update:modelValue', value);
    },
    async submit() {
      const reason = this.reason.trim();
      this.fieldError = reason ? '' : '사유를 입력해 주세요.';
      if (this.fieldError) return;
      this.saving = true;
      this.serverError = '';
      try {
        const result = await this.action(reason);
        this.$emit('update:modelValue', false);
        this.$emit('done', result);
      } catch (error) {
        this.serverError = this.errorMessage?.(error) || error?.message || '처리하지 못했습니다.';
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.correction {
  width: 480px;
  max-width: 94vw;
}

.correction__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.correction__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.correction__target {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.correction__notes {
  margin: 0;
  padding: 10px 12px 10px 28px;
  border-radius: 6px;
  background: var(--db-bg-subtle);
  font-size: 13px;
  line-height: 1.6;
  color: var(--db-text-body);
}

.correction__body :deep(.correction__input) {
  min-height: 88px;
}

.correction__error {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 10px 12px;
  border-radius: 6px;
  background: color-mix(in srgb, var(--db-error) 8%, transparent);
  font-size: 13px;
  color: var(--db-error);
}

.correction__actions {
  padding: 8px 16px 16px;
}
</style>
