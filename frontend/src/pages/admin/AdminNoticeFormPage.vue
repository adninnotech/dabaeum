<template>
  <q-page class="notice-form">
    <h1 class="notice-form__title">{{ isEdit ? '공지사항 수정' : '공지사항 작성' }}</h1>

    <AppErrorBanner v-if="errorMessage" :message="errorMessage" />

    <section class="form-card">
      <div class="field">
        <div class="field-label">제목 <span class="req">*</span></div>
        <q-input
          v-model="form.title"
          outlined
          :maxlength="TITLE_MAX"
          :placeholder="`공지 제목을 입력해 주세요 (최대 ${TITLE_MAX}자)`"
          class="wz-input"
        >
          <template #append>
            <span class="input-counter">{{ form.title.length }}/{{ TITLE_MAX }}</span>
          </template>
        </q-input>
      </div>

      <div class="field">
        <div class="field-label">공지 유형 <span class="req">*</span></div>
        <q-select
          v-model="form.audience"
          outlined
          emit-value
          map-options
          :options="AUDIENCE_OPTIONS"
          class="wz-input field--audience"
        />
      </div>

      <div class="field">
        <div class="field-label">내용 <span class="req">*</span></div>
        <q-editor
          @paste="pasteMarkdown"
          v-model="form.body"
          min-height="280px"
          :toolbar="editorToolbar"
          placeholder="공지 내용을 입력해 주세요."
          class="wz-editor"
        />
      </div>

      <div class="field">
        <div class="field-label">첨부파일</div>
        <div class="attach-pick">
          <q-btn
            unelevated
            no-caps
            class="btn-attach"
            label="파일 선택"
            @click="$refs.attachInput.click()"
          />
          <div class="attach-pick__status">
            {{ attachmentCountLabel }}
          </div>
        </div>
        <div class="field-help">
          첨부 가능한 파일 형식: jpg, jpeg, png, pdf, doc, docx, hwp (최대 10MB)
        </div>
        <div v-for="file in existingAttachments" :key="file.id" class="attach-row">
          <q-icon name="insert_drive_file" size="20px" class="attach-row__icon" />
          <span class="attach-row__name">{{ file.fileName }}</span>
          <span class="attach-row__size">{{ (file.byteSize / 1024 / 1024).toFixed(1) }}MB</span>
          <q-btn aria-label="첨부 삭제" flat round dense icon="close" size="sm" @click="removeExistingAttachment(file)" />
        </div>
        <div v-for="(file, i) in form.newAttachments" :key="`new-${i}`" class="attach-row">
          <q-icon name="insert_drive_file" size="20px" class="attach-row__icon" />
          <span class="attach-row__name">{{ file.name }}</span>
          <span class="attach-row__size">{{ (file.size / 1024 / 1024).toFixed(1) }}MB</span>
          <q-btn aria-label="첨부 삭제" flat round dense icon="close" size="sm" @click="form.newAttachments.splice(i, 1)" />
        </div>
        <input
          ref="attachInput"
          type="file"
          multiple
          :accept="ATTACHMENT_ACCEPT"
          class="hidden"
          @change="onAttachmentsChange"
        />
      </div>

      <div class="field">
        <div class="field-label">상태 <span class="req">*</span></div>
        <q-option-group
          v-model="form.status"
          type="radio"
          inline
          :options="NOTICE_STATUS_OPTIONS"
        />
      </div>

      <div class="field">
        <div class="field-label">게시 시각</div>
        <AppDateTimeInput
          v-model="form.publishedAt"
          outlined
          :disable="form.status !== 'PUBLISHED'"
          class="wz-input field--audience"
        />
        <div class="field-help">
          비워 두면 등록하는 즉시 공개됩니다. 미래 시각을 넣으면 그 시각부터 학습자에게 보입니다.
        </div>
      </div>
    </section>

    <footer class="notice-form__foot">
      <q-btn outline no-caps class="btn-cancel" label="취소" @click="goList" />
      <q-btn
        unelevated
        no-caps
        class="btn-submit"
        :loading="saving"
        :label="isEdit ? '수정' : '등록'"
        @click="submit"
      />
    </footer>
  </q-page>
</template>

<script>
import AppDateTimeInput from '@/components/AppDateTimeInput.vue';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import { ATTACHMENT_ACCEPT } from '@/config/course-options';
import { AUDIENCE_OPTIONS, NOTICE_STATUS_OPTIONS } from '@/config/notice-options';
import { uploadFile } from '@/services/file-api';
import {
  attachNoticeFile,
  createNotice,
  detachNoticeFile,
  getAdminNotice,
  listNoticeAttachments,
  updateNotice,
} from '@/services/notice-api';
import { pickAttachments } from '@/utils/attachments';
import { fromDateTimeLocalKst, toDateTimeLocalKst } from '@/utils/datetime';
import { EDITOR_TOOLBAR, plainTextLength } from '@/utils/rich-text';
import { pasteMarkdownAsRichText } from '@/utils/markdown-paste';
import { confirmDialog } from '@/utils/confirm-dialog';
import unsavedGuard from '@/mixins/unsaved-guard';

// NoticeRequest.title maxLength — 화면은 100 이라 61~100자는 저장 때 422 였다
const TITLE_MAX = 60;

export default {
  name: 'AdminNoticeFormPage',
  components: { AppErrorBanner, AppDateTimeInput },
  mixins: [unsavedGuard],
  // 사이드바·뒤로가기로 나가면 쓰던 공지가 통째로 사라졌다
  beforeRouteLeave(to, from, next) {
    this.confirmLeave(next);
  },
  data() {
    return {
      saving: false,
      errorMessage: '',
      AUDIENCE_OPTIONS,
      NOTICE_STATUS_OPTIONS,
      ATTACHMENT_ACCEPT,
      TITLE_MAX,
      editorToolbar: EDITOR_TOOLBAR,
      existingAttachments: [],
      form: {
        title: '',
        audience: 'PUBLIC',
        body: '',
        // 새 공지는 임시저장으로 시작한다 — '게시'가 기본이라 [등록]을 누르는 순간 바로 공개됐다
        status: 'DRAFT',
        publishedAt: '',
        newAttachments: [],
      },
    };
  },
  computed: {
    noticeId() {
      return this.$route.params.noticeId;
    },
    isEdit() {
      return !!this.noticeId;
    },
    attachmentCountLabel() {
      const count = this.existingAttachments.length + this.form.newAttachments.length;
      return count ? `${count}개 선택됨` : '선택된 파일 없음';
    },
  },
  created() {
    if (this.isEdit) this.load();
  },
  methods: {
    // 마크다운 글을 붙여 넣으면 제목·목록·표로 바꿔 넣는다
    pasteMarkdown(event) {
      pasteMarkdownAsRichText(event);
    },
    goList() {
      this.$router.push('/admin/notices');
    },
    guardSnapshot() {
      const { newAttachments, ...rest } = this.form;
      return JSON.stringify({ ...rest, newAttachments: newAttachments.map((file) => file.fileId) });
    },
    async load() {
      try {
        const [notice, attachments] = await Promise.all([
          // 공개 GET /notices/{id} 는 초안·예약 공지를 404 로 줘 임시저장한 공지를 고칠 수 없었다(QA OPS-01)
          getAdminNotice(this.noticeId),
          listNoticeAttachments(this.noticeId).catch(() => []),
        ]);
        this.form = {
          title: notice?.title || '',
          audience: notice?.audience || 'PUBLIC',
          body: notice?.body || '',
          status: notice?.status || 'PUBLISHED',
          publishedAt: toDateTimeLocalKst(notice?.publishedAt),
          newAttachments: [],
        };
        this.existingAttachments = attachments;
        this.$nextTick(this.markSaved);
      } catch (error) {
        this.errorMessage = error?.message || '공지를 불러오지 못했습니다.';
      }
    },
    // 선택 시점에 업로드까지 끝낸다(course-api의 첨부와 같은 2단계). 연결(attachNoticeFile)은
    // 신규 등록일 때 noticeId가 아직 없어 submit()에서 한다.
    async onAttachmentsChange(event) {
      const { accepted, rejected } = pickAttachments(event.target.files);
      event.target.value = '';
      if (rejected.length) {
        this.$q.notify({ type: 'warning', message: `첨부 제외 — ${rejected.join(', ')}` });
      }
      for (const file of accepted) {
        try {
          const { id } = await uploadFile(file, 'NOTICE_ATTACHMENT');
          this.form.newAttachments.push({ fileId: id, name: file.name, size: file.size });
        } catch (error) {
          this.$q.notify({
            type: 'negative',
            message: `${file.name} 업로드 실패: ${error.message || ''}`.trim(),
          });
        }
      }
    },
    /** 이미 저장된 공지에 붙은 첨부라 즉시 서버에서 제거한다(신규 선택분과 다름) */
    async removeExistingAttachment(file) {
      // 저장 전 [취소]로 되돌릴 수 없다 — 누르는 즉시 서버에서 지워지니 묻는다
      const ok = await confirmDialog(this.$q, {
        title: '첨부 삭제',
        message: `'${file.fileName}'을(를) 이 공지에서 지웁니다. 바로 반영되며 되돌릴 수 없습니다.`,
        ok: '삭제',
        color: 'negative',
        cancel: '닫기',
      });
      if (!ok) return;
      try {
        await detachNoticeFile(this.noticeId, file.id);
        this.existingAttachments = this.existingAttachments.filter((f) => f.id !== file.id);
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '첨부 제거 실패' });
      }
    },
    async submit() {
      if (!this.form.title.trim()) {
        this.$q.notify({ type: 'warning', message: '제목을 입력해 주세요.', position: 'top' });
        return;
      }
      if (!plainTextLength(this.form.body)) {
        this.$q.notify({ type: 'warning', message: '내용을 입력해 주세요.', position: 'top' });
        return;
      }
      // 게시로 등록하면 바로(또는 예약 시각에) 학습자에게 보인다 — 한 번 묻는다
      if (!this.isEdit && this.form.status === 'PUBLISHED') {
        const ok = await confirmDialog(this.$q, {
          title: '공지 게시',
          message: this.form.publishedAt
            ? '정한 게시 시각부터 학습자에게 공개됩니다.'
            : '등록하면 바로 학습자에게 공개됩니다.',
          ok: '게시',
          cancel: '닫기',
        });
        if (!ok) return;
      }
      this.saving = true;
      try {
        const body = {
          title: this.form.title.trim(),
          body: this.form.body,
          audience: this.form.audience,
          status: this.form.status,
          publishedAt: this.form.status === 'PUBLISHED' ? fromDateTimeLocalKst(this.form.publishedAt) : null,
        };
        const notice = this.isEdit
          ? await updateNotice(this.noticeId, body)
          : await createNotice(body);
        for (const file of this.form.newAttachments) {
          await attachNoticeFile(notice.id, { fileId: file.fileId });
        }
        this.$q.notify({
          type: 'positive',
          message: `'${body.title}' 공지를 ${this.isEdit ? '수정' : '등록'}했습니다.`,
          position: 'top',
        });
        this.skipLeaveGuard = true;
        this.goList();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.notice-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 48px 40px 20px;
  background: var(--db-bg-page);
}

.notice-form__title {
  display: flex;
  align-items: center;
  height: 58px;
  margin: 0;
  padding: 10px 0;
  font-size: 32px;
  line-height: 1.2;
  font-weight: 700;
  color: var(--db-text-title);
}

.form-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px 16px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 14px;
  font-weight: 700;
  color: var(--db-text-body);
}

.field-help {
  font-size: 14px;
  color: var(--db-text-sub);
}

.req {
  color: var(--db-status-pink);
}

.field--audience {
  width: 240px;
  max-width: 100%;
}

/* q-select 기본 min-height가 56을 잡아 48로 안 내려간다 */
.form-card :deep(.wz-input .q-field__control) {
  height: 48px;
  min-height: 48px;
  border-radius: 8px;
}

.form-card :deep(.wz-editor) {
  border: 1px solid var(--db-border-rt);
  border-radius: 8px;
}

.input-counter {
  font-size: 12px;
  color: var(--db-text-sub);
}

.attach-pick {
  display: flex;
  align-items: center;
  gap: 10px;
}

.btn-attach {
  /* 84px 고정이라 폰에서 '파일 / 선택'이 두 줄로 꺾여 잘렸다 */
  flex-shrink: 0;
  min-width: 84px;
  white-space: nowrap;
  height: 32px;
  border-radius: 4px;
  background: var(--db-accent-fill);
  color: var(--db-on-color);
}

.attach-pick__status {
  display: flex;
  align-items: center;
  flex: 0 1 240px;
  min-width: 0;
  height: 36px;
  padding: 6px 8px;
  border: 1px solid var(--db-border);
  border-radius: 6px;
  font-size: 14px;
  color: var(--db-text-sub);
}

.attach-row {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  border-bottom: 1px solid var(--db-border);
}

.attach-row__icon {
  color: var(--db-text-sub);
}

.attach-row__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 14px;
  color: var(--db-text-body);
}

.attach-row__size {
  font-size: 12px;
  color: var(--db-text-sub);
}

.notice-form__foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.btn-cancel {
  width: 120px;
  height: 40px;
  border-radius: 6px;
  color: var(--db-text-body);
}

.btn-submit {
  width: 180px;
  height: 40px;
  border-radius: 6px;
  background: var(--db-accent-fill);
  color: var(--db-on-color);
}

/* 폰: 좌우 40px 여백·32px 제목이 입력 칸을 좁혔다 */
@media (max-width: 599px) {
  .notice-form {
    padding: 20px 16px 16px;
  }

  .notice-form__title {
    height: auto;
    font-size: 24px;
  }

  .btn-cancel,
  .btn-submit {
    flex: 1 1 0;
    width: auto;
  }
}
</style>
