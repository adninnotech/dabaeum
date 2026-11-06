<template>
  <div class="info-tab">
    <section class="info-card">
      <h2 class="info-card__title">강의 설명</h2>
      <!-- eslint-disable-next-line vue/no-v-html -->
      <div v-if="descriptionHtml" class="info-card__body rich-text" v-html="descriptionHtml" />
      <AppEmptyState v-else compact description="등록된 강의 설명이 없습니다." />
    </section>

    <section class="info-card">
      <div class="info-card__head">
        <h2 class="info-card__title">첨부파일</h2>
        <!-- 게시된 강의는 마법사로 돌아갈 수 없어 첨부를 고칠 곳이 없었다(QA CRS-13). 강사·기관 관리자·관리자가 여기서 고친다 -->
        <template v-if="canManageAttachments">
          <q-btn
            outline
            dense
            no-caps
            color="primary"
            icon="add"
            label="파일 추가"
            :loading="uploading"
            @click="$refs.attachInput.click()"
          />
          <input
            ref="attachInput"
            type="file"
            multiple
            hidden
            :accept="ATTACHMENT_ACCEPT"
            @change="onPickFiles"
          />
        </template>
      </div>
      <p v-if="canManageAttachments" class="info-card__help">
        jpg, jpeg, png, pdf, doc, docx, hwp · 파일당 10MB. 첨부는 로그인한 사용자만 내려받을 수 있습니다.
      </p>
      <!-- 조회 실패를 빈 목록으로 삼켜 '첨부파일이 없습니다'로 보였다 — 불러오는 중·실패·없음을 나눈다 -->
      <div v-if="attachmentsLoading" class="info-card__state">
        <q-spinner color="primary" size="20px" />
      </div>
      <div v-else-if="attachmentsError" class="info-card__error" role="alert">
        첨부파일을 불러오지 못했습니다.
        <q-btn
          flat
          dense
          no-caps
          color="primary"
          label="다시 시도"
          @click="loadAttachments(course?.id)"
        />
      </div>
      <AppEmptyState
        v-else-if="!attachments.length"
        compact
        description="등록된 첨부파일이 없습니다."
      />
      <div v-else class="detail-attachments">
        <div v-for="file in attachments" :key="file.id" class="detail-attachment-row">
          <button class="detail-attachment" type="button" @click="downloadAttachment(file)">
            <q-icon name="attach_file" size="18px" />
            <span class="detail-attachment__name">{{ file.fileName }}</span>
            <span class="detail-attachment__meta">
              {{ formatByteSize(file.byteSize) }} · {{ formatDateKst(file.createdAt) }}
            </span>
          </button>
          <q-btn
            v-if="canManageAttachments"
            flat
            round
            dense
            size="sm"
            icon="close"
            :loading="removingId === file.id"
            :aria-label="`${file.fileName} 첨부 삭제`"
            @click="removeAttachment(file)"
          />
        </div>
      </div>
    </section>
  </div>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import { ATTACHMENT_ACCEPT } from '@/config/course-options';
import { attachCourseFile, detachCourseFile, listCourseAttachments } from '@/services/course-api';
import { downloadStoredFile, uploadFile } from '@/services/file-api';
import { pickAttachments } from '@/utils/attachments';
import { confirmDialog } from '@/utils/confirm-dialog';
import { formatDateKst } from '@/utils/datetime';
import { sanitizeRichText } from '@/utils/rich-text';

export default {
  name: 'CourseInfoTab',
  components: { AppEmptyState },
  props: {
    course: { type: Object, default: null },
    console: { type: String, default: 'admin' },
  },
  data() {
    return {
      ATTACHMENT_ACCEPT,
      attachments: [],
      attachmentsLoading: false,
      attachmentsError: false,
      uploading: false,
      removingId: null,
    };
  },
  computed: {
    descriptionHtml() {
      return sanitizeRichText(this.course?.description);
    },
    /** 콘솔(관리자·기관·배정 강사)에서만 — 서버가 그 강의의 강사·기관 관리자인지 다시 본다(아니면 403) */
    canManageAttachments() {
      return ['admin', 'institution', 'instructor'].includes(this.console) && !!this.course?.id;
    },
  },
  watch: {
    'course.id': {
      immediate: true,
      handler(id) {
        if (id) this.loadAttachments(id);
      },
    },
  },
  methods: {
    formatDateKst,
    async loadAttachments(courseId) {
      if (!courseId) return;
      this.attachmentsLoading = true;
      this.attachmentsError = false;
      try {
        const list = await listCourseAttachments(courseId);
        if (courseId === this.course?.id) this.attachments = Array.isArray(list) ? list : [];
      } catch {
        this.attachments = [];
        this.attachmentsError = true;
      } finally {
        this.attachmentsLoading = false;
      }
    },
    async downloadAttachment(file) {
      try {
        await downloadStoredFile(file.fileId, file.fileName || 'attachment');
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      }
    },
    /** 고른 파일을 올리고(POST /files) 강의에 붙인다(POST /courses/{id}/attachments). 한 건씩 — 실패한 것만 알린다 */
    async onPickFiles(event) {
      const courseId = this.course?.id;
      const { accepted, rejected } = pickAttachments(event.target.files);
      event.target.value = '';
      if (rejected.length) {
        this.$q.notify({ type: 'warning', message: `올릴 수 없는 파일: ${rejected.join(', ')}`, position: 'top' });
      }
      if (!courseId || !accepted.length) return;
      this.uploading = true;
      const failed = [];
      for (const file of accepted) {
        try {
          const { id } = await uploadFile(file, 'COURSE_ATTACHMENT');
          await attachCourseFile(courseId, { fileId: id });
        } catch (error) {
          failed.push(`${file.name}: ${error.message}`);
        }
      }
      this.uploading = false;
      const done = accepted.length - failed.length;
      if (done) this.$q.notify({ type: 'positive', message: `첨부파일 ${done}개를 추가했습니다.`, position: 'top' });
      if (failed.length) this.$q.notify({ type: 'negative', message: failed.join(' / '), position: 'top' });
      await this.loadAttachments(courseId);
    },
    async removeAttachment(file) {
      const courseId = this.course?.id;
      const ok = await confirmDialog(this.$q, {
        title: '첨부 삭제',
        message: `'${file.fileName}' 첨부를 삭제합니다. 학습자 화면에서도 사라지고 링크로도 내려받을 수 없습니다.`,
        ok: '삭제',
        color: 'negative',
        cancel: '닫기',
      });
      if (!ok || !courseId) return;
      this.removingId = file.id;
      try {
        await detachCourseFile(courseId, file.id);
        this.$q.notify({ type: 'positive', message: '첨부파일을 삭제했습니다.', position: 'top' });
        await this.loadAttachments(courseId);
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.removingId = null;
      }
    },
    formatByteSize(bytes) {
      const n = Number(bytes) || 0;
      if (n < 1024) return `${n}B`;
      if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)}KB`;
      return `${(n / 1024 / 1024).toFixed(1)}MB`;
    },
  },
};
</script>

<style scoped>
.info-card__state {
  display: flex;
  justify-content: center;
  padding: 16px 0;
}

.info-card__error {
  font-size: 14px;
  color: var(--db-status-pink);
}

.info-tab {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 0;
}

.info-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.info-card__title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--db-text-body);
}

.info-card__more {
  font-size: 12px;
  font-weight: 400;
  color: var(--db-accent);
}

.info-card__body {
  padding: 4px 20px;
  font-size: 16px;
  font-weight: 400;
  color: var(--db-text-body);
}

.detail-attachments {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-card__help {
  margin: 0;
  font-size: 12px;
  color: var(--db-text-sub);
}

.detail-attachment-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-attachment {
  display: flex;
  flex: 1;
  min-width: 0;
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  text-align: left;
  cursor: pointer;
  align-items: center;
  gap: 8px;
  color: var(--db-text-body);
  text-decoration: none;
}

.detail-attachment__name {
  font-weight: 500;
}

.detail-attachment__meta {
  color: var(--db-text-sub);
  font-size: 12px;
}
</style>
