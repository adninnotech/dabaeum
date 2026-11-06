<template>
  <section class="notes">
    <h2 class="notes__title">학습 전 유의사항</h2>
    <!-- eslint-disable-next-line vue/no-v-html -->
    <div v-if="notesHtml" class="notes__body rich-text" v-html="notesHtml" />
    <AppEmptyState v-else compact description="등록된 유의사항이 없습니다." />
  </section>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import { sanitizeRichText } from '@/utils/rich-text';

export default {
  name: 'CourseNotesTab',
  components: { AppEmptyState },
  props: {
    course: { type: Object, default: null },
    console: { type: String, default: 'admin' },
  },
  computed: {
    notesHtml() {
      return sanitizeRichText(this.course?.detail?.notes);
    },
  },
};
</script>

<style scoped>
.notes {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 0;
}

.notes__title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--db-text-body);
}

.notes__body {
  padding: 4px 20px;
  font-size: 16px;
  font-weight: 400;
  color: var(--db-text-body);
}
</style>
