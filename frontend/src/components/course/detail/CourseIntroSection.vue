<template>
  <!-- 강좌소개 — 학습자 강좌 상세와 강의 등록 미리보기가 같이 쓴다(미리보기가 옛 화면으로 어긋났었다) -->
  <CourseDetailSection tab="강좌소개" title="강좌소개" wide>
    <div v-if="tagList.length" class="detail-tags">
      <AppTag v-for="tag in tagList" :key="tag">{{ tag }}</AppTag>
    </div>
    <!-- eslint-disable-next-line vue/no-v-html -->
    <div v-if="learningObjectiveHtml" class="detail-rich rich-text" v-html="learningObjectiveHtml" />
    <!-- eslint-disable-next-line vue/no-v-html -->
    <div v-if="descriptionHtml" class="detail-rich rich-text" v-html="descriptionHtml" />
    <AppEmptyState
      v-if="!descriptionHtml && !learningObjectiveHtml"
      compact
      description="등록된 강좌 소개가 없습니다."
    />
  </CourseDetailSection>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import AppTag from '@/components/AppTag.vue';
import CourseDetailSection from '@/components/course/detail/CourseDetailSection.vue';
import { sanitizeRichText } from '@/utils/rich-text';

export default {
  name: 'CourseIntroSection',
  components: { AppEmptyState, AppTag, CourseDetailSection },
  props: {
    /** course.description — 편집기 HTML, 여기서 소독한다 */
    description: { type: String, default: '' },
    /** course.detail — tags·learningObjective 를 읽는다 */
    detail: { type: Object, default: null },
  },
  computed: {
    descriptionHtml() {
      return sanitizeRichText(this.description);
    },
    learningObjectiveHtml() {
      return sanitizeRichText(this.detail?.learningObjective);
    },
    tagList() {
      return String(this.detail?.tags || '')
        .split(/[\s,]+/)
        .filter(Boolean);
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

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}
</style>
