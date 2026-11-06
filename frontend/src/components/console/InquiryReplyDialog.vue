<template>
  <!-- 목록(InquirySummary)에는 본문·답변이 없다 — 열 때 상세(GET /inquiries/{id})를 받아 질문을 읽고 답하게 한다.
       예전엔 제목만 보고 답했고, 답변한 문의는 다시 열어 볼 수 없었다 -->
  <!-- 답변을 쓰는 중엔 바깥 누름·Esc 로 닫히지 않는다 — 쓴 답이 확인 없이 사라졌다 -->
  <q-dialog
    :model-value="modelValue"
    :persistent="saving || Boolean(content.trim())"
    @update:model-value="$emit('update:modelValue', $event)"
    @before-show="load"
  >
    <q-card class="reply-dialog">
      <q-card-section class="reply-dialog__head">
        <h2 class="reply-dialog__title">{{ answered ? '문의 답변 보기' : '답변 작성' }}</h2>
        <q-btn v-close-popup flat round dense icon="close" aria-label="닫기" />
      </q-card-section>

      <q-card-section class="reply-dialog__body">
        <div v-if="loading" class="reply-dialog__state"><q-spinner color="primary" size="24px" /></div>
        <div v-else-if="loadError" class="reply-dialog__error" role="alert">
          {{ loadError }}
          <q-btn flat dense no-caps color="primary" label="다시 시도" @click="load" />
        </div>
        <template v-else>
          <div class="reply-dialog__question">
            <div class="reply-dialog__q-title">{{ detail?.title || inquiry?.title }}</div>
            <div v-if="metaLine" class="reply-dialog__meta">{{ metaLine }}</div>
            <p class="reply-dialog__text">{{ detail?.content || '(본문 없음)' }}</p>
          </div>

          <div v-if="answered" class="reply-dialog__answer">
            <div class="reply-dialog__answer-label">
              답변 <span v-if="detail?.answeredAt">· {{ formatDateMinuteKst(detail.answeredAt) }}</span>
            </div>
            <p class="reply-dialog__text">{{ detail?.answer || '-' }}</p>
          </div>
          <q-input
            v-else
            v-model="content"
            type="textarea"
            outlined
            autogrow
            label="답변 내용 *"
            :input-style="{ minHeight: '120px' }"
          />
        </template>
      </q-card-section>

      <q-card-actions align="right" class="reply-dialog__actions">
        <q-btn v-close-popup flat no-caps :label="answered ? '닫기' : '취소'" />
        <q-btn
          v-if="!answered"
          unelevated
          no-caps
          color="primary"
          :loading="saving"
          :disable="loading || !!loadError"
          label="답변 등록"
          @click="submit"
        />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import { getInquiry, replyInquiry } from '@/services/inquiry-api';
import { formatDateMinuteKst } from '@/utils/datetime';

export default {
  name: 'InquiryReplyDialog',
  props: {
    modelValue: { type: Boolean, default: false },
    /** 목록 행(id·title·status·userName 등) */
    inquiry: { type: Object, default: null },
  },
  emits: ['update:modelValue', 'replied'],
  data() {
    return { content: '', saving: false, loading: false, loadError: '', detail: null };
  },
  computed: {
    answered() {
      return (this.detail?.status || this.inquiry?.status) === 'ANSWERED';
    },
    metaLine() {
      const row = this.inquiry || {};
      // 초까지 붙어 폰에서 시각이 다음 줄로 넘어갔다 — 목록과 같이 분까지
      const when = row.createdAt ? formatDateMinuteKst(row.createdAt) : '';
      return [row.courseTitle, row.userName, when].filter(Boolean).join(' · ');
    },
  },
  methods: {
    formatDateMinuteKst,
    async load() {
      this.content = '';
      this.detail = null;
      this.loadError = '';
      if (!this.inquiry?.id) return;
      this.loading = true;
      const id = this.inquiry.id;
      try {
        const detail = await getInquiry(id);
        // 기다리는 사이 다른 문의를 열었으면 늦게 온 응답은 버린다
        if (this.inquiry?.id === id) this.detail = detail;
      } catch (error) {
        this.loadError = error?.message || '문의 내용을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    async submit() {
      if (this.saving) return;
      if (!this.content.trim()) {
        this.$q.notify({ type: 'warning', message: '답변 내용을 입력해 주세요.', position: 'top' });
        return;
      }
      this.saving = true;
      try {
        await replyInquiry(this.inquiry.id, this.content.trim());
        this.$q.notify({ type: 'positive', message: '답변을 등록했습니다.', position: 'top' });
        this.$emit('update:modelValue', false);
        this.$emit('replied');
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error?.message || '답변 등록에 실패했습니다.', position: 'top' });
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
/* min-width 480px 이 폰 화면을 넘었다 */
.reply-dialog {
  width: 560px;
  max-width: 94vw;
  border-radius: 12px;
}

.reply-dialog__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.reply-dialog__title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--db-text-title);
}

.reply-dialog__body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 0;
}

.reply-dialog__state {
  display: flex;
  justify-content: center;
  padding: 24px 0;
}

.reply-dialog__error {
  font-size: 14px;
  color: var(--db-status-pink);
}

.reply-dialog__question {
  padding: 14px 16px;
  background: var(--db-bg-subtle);
  border-radius: 8px;
}

.reply-dialog__q-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.reply-dialog__meta {
  margin-top: 2px;
  font-size: 13px;
  color: var(--db-text-sub);
}

.reply-dialog__text {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--db-text-body);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.reply-dialog__answer {
  padding: 0 4px;
}

.reply-dialog__answer-label {
  font-size: 13px;
  font-weight: 700;
  color: var(--db-accent);
}

.reply-dialog__actions {
  padding: 0 16px 16px;
}
</style>
