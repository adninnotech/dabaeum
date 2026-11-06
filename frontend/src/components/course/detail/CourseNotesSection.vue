<template>
  <CourseDetailSection tab="유의사항" title="유의사항" wide>
    <!-- eslint-disable-next-line vue/no-v-html -->
    <div v-if="notesHtml" class="detail-rich rich-text" v-html="notesHtml" />
    <AppEmptyState v-else compact description="등록된 유의사항이 없습니다." />

    <div v-if="attachments.length" class="detail-attachments">
      <button
        v-for="file in attachments"
        :key="file.id"
        class="detail-attachment"
        type="button"
        @click="downloadAttachment(file)"
      >
        <q-icon name="attach_file" size="18px" />
        <span class="detail-attachment__name">{{ file.fileName }}</span>
        <span class="detail-attachment__meta">
          {{ formatByteSize(file.byteSize) }} · {{ formatDateKst(file.createdAt) }}
        </span>
      </button>
    </div>
  </CourseDetailSection>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import CourseDetailSection from '@/components/course/detail/CourseDetailSection.vue';
import { downloadStoredFile } from '@/services/file-api';
import { formatDateKst } from '@/utils/datetime';
import { sanitizeRichText } from '@/utils/rich-text';

export default {
  name: 'CourseNotesSection',
  components: { AppEmptyState, CourseDetailSection },
  props: {
    /** course.detail.notes — 편집기 HTML, 여기서 소독한다 */
    notes: { type: String, default: '' },
    attachments: { type: Array, default: () => [] },
  },
  computed: {
    notesHtml() {
      return sanitizeRichText(this.notes);
    },
  },
  methods: {
    formatDateKst,
    async downloadAttachment(file) {
      try {
        await downloadStoredFile(file.fileId, file.fileName || 'attachment');
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      }
    },
    // 단위 앞을 띄운다 — '595B'가 '5958'로 읽혔다
    formatByteSize(bytes) {
      const n = Number(bytes) || 0;
      if (n < 1024) return `${n} B`;
      if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
      return `${(n / 1024 / 1024).toFixed(1)} MB`;
    },
  },
};
</script>

<style scoped>
.detail-rich {
  font-size: 14px;
  line-height: 22px;
  color: var(--db-text-body);
  /* break-word 는 낱말 중간('상세 강의 소 / 개')에서 꺾였다 — 낱말 단위로, 아주 긴 낱말만 쪼갠다 */
  word-break: keep-all;
  overflow-wrap: anywhere;
}

.detail-rich :deep(img) {
  max-width: 100%;
}

.detail-attachments {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 16px;
}

.detail-attachment {
  display: flex;
  width: 100%;
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
  font-size: 13px;
}
</style>
