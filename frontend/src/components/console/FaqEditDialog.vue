<template>
  <q-dialog :model-value="modelValue" :persistent="saving" @update:model-value="close" @before-show="fill">
    <q-card class="faq-form">
      <q-card-section class="faq-form__head">
        <div class="text-h6">{{ faq ? 'FAQ 수정' : 'FAQ 추가' }}</div>
        <q-btn v-close-popup flat round dense icon="close" aria-label="닫기" :disable="saving" />
      </q-card-section>
      <q-separator />

      <q-card-section class="faq-form__body">
        <q-input
          v-model="question"
          outlined
          autogrow
          counter
          :maxlength="QUESTION_MAX"
          label="질문 *"
          :error="!!errors.question"
          :error-message="errors.question"
          :disable="saving"
        />
        <q-input
          v-model="answer"
          type="textarea"
          outlined
          autogrow
          label="답변 *"
          hint="줄바꿈은 그대로 보입니다."
          input-class="faq-form__answer"
          :error="!!errors.answer"
          :error-message="errors.answer"
          :disable="saving"
        />
        <q-input
          v-model.number="sortOrder"
          type="number"
          outlined
          dense
          label="표시 순서"
          hide-bottom-space
          class="faq-form__order"
          :error="!!errors.sortOrder"
          :error-message="errors.sortOrder"
          :disable="saving"
        />
        <!-- 칸이 좁아 q-input hint 로 두면 두 줄로 꺾인다 — 칸 밖 전체 폭으로 -->
        <p class="faq-form__help">작을수록 위에 보입니다. 같으면 먼저 등록한 것이 위입니다.</p>
      </q-card-section>

      <q-card-actions align="right" class="faq-form__actions">
        <q-btn v-close-popup flat no-caps label="취소" :disable="saving" />
        <q-btn unelevated no-caps color="primary" :label="faq ? '저장' : '추가'" :loading="saving" @click="submit" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import { createFaq, updateFaq } from '@/services/support-api';

const QUESTION_MAX = 500;
const INT32_MAX = 2147483647;

function validate({ question, answer, sortOrder }) {
  const errors = {};
  if (!question.trim()) errors.question = '질문을 입력해 주세요.';
  if (!answer.trim()) errors.answer = '답변을 입력해 주세요.';
  if (!Number.isInteger(sortOrder) || Math.abs(sortOrder) > INT32_MAX) {
    errors.sortOrder = '정수로 입력해 주세요.';
  }
  return errors;
}

/* FAQ 추가·수정(플랫폼 관리자). 공개 고객센터 FAQ 는 이 순서대로 보인다 */
export default {
  name: 'FaqEditDialog',
  props: {
    modelValue: { type: Boolean, default: false },
    /** 수정할 FAQ. null 이면 추가 */
    faq: { type: Object, default: null },
    /** 추가할 때 기본 순서(맨 뒤) */
    nextSortOrder: { type: Number, default: 0 },
  },
  emits: ['update:modelValue', 'saved'],
  data() {
    return { QUESTION_MAX, question: '', answer: '', sortOrder: 0, errors: {}, saving: false };
  },
  methods: {
    fill() {
      this.question = this.faq?.question || '';
      this.answer = this.faq?.answer || '';
      this.sortOrder = this.faq ? this.faq.sortOrder ?? 0 : this.nextSortOrder;
      this.errors = {};
    },
    close(value) {
      if (!this.saving) this.$emit('update:modelValue', value);
    },
    async submit() {
      this.errors = validate(this);
      if (Object.keys(this.errors).length) return;
      const body = { question: this.question.trim(), answer: this.answer.trim(), sortOrder: this.sortOrder };
      this.saving = true;
      try {
        const saved = this.faq ? await updateFaq(this.faq.id, body) : await createFaq(body);
        this.$emit('update:modelValue', false);
        this.$emit('saved', saved);
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error?.message || '저장하지 못했습니다.', position: 'top' });
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.faq-form {
  width: 560px;
  max-width: 94vw;
}

.faq-form__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.faq-form__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.faq-form__body :deep(.faq-form__answer) {
  min-height: 160px;
  line-height: 1.6;
}

.faq-form__order {
  max-width: 200px;
}

.faq-form__help {
  margin: -6px 0 0 12px;
  font-size: 12px;
  color: var(--db-text-sub);
}

.faq-form__actions {
  padding: 8px 16px 16px;
}
</style>
