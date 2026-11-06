<template>
  <!-- 피그마 avatar 블록은 숨김 상태라 렌더하지 않음 -->
  <CourseDetailSection tab="강사소개" title="강사소개">
    <AppEmptyState v-if="!instructors.length" compact description="배정된 강사가 없습니다." />
    <!-- 소개·경력은 detail.instructorProfileVisible 이 false 면 null 로 온다 -->
    <div v-for="item in instructors" :key="item.userId" class="detail-instructor">
      <div class="detail-instructor__name">{{ item.instructorName || '강사' }}</div>
      <!-- 강사 연락처를 비로그인 방문자에게 그대로 내보내지 않는다 -->
      <div v-if="isLoggedIn && item.instructorEmail" class="detail-instructor__sub">
        {{ item.instructorEmail }}
      </div>
      <p v-if="item.introduction" class="detail-instructor__text">{{ item.introduction }}</p>
      <div v-if="item.career" class="detail-instructor__career">
        <div class="detail-instructor__label">경력</div>
        <!-- 줄마다 항목 + 현재·이전 표시(강사 마이페이지와 같은 모양) -->
        <InstructorCareerList :value="item.career" class="detail-instructor__career-list" />
      </div>
    </div>
  </CourseDetailSection>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import InstructorCareerList from '@/components/InstructorCareerList.vue';
import CourseDetailSection from '@/components/course/detail/CourseDetailSection.vue';

export default {
  name: 'CourseInstructorsSection',
  components: { AppEmptyState, CourseDetailSection, InstructorCareerList },
  props: {
    instructors: { type: Array, default: () => [] },
    isLoggedIn: { type: Boolean, default: false },
  },
};
</script>

<style scoped>
/* 강사 */
.detail-instructor {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 0;
}

.detail-instructor__name {
  font-size: 16px;
  font-weight: 700;
  color: var(--db-text-title);
}

.detail-instructor__sub {
  font-size: 14px;
  color: var(--db-text-sub);
}

.detail-instructor__text {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--db-text-body);
  white-space: pre-line;
  overflow-wrap: anywhere;
}

.detail-instructor__career {
  margin-top: 8px;
}

.detail-instructor__label {
  font-size: 14px;
  font-weight: 700;
  color: var(--db-text-title);
}

.detail-instructor__career-list {
  margin-top: 6px;
  /* 위 .detail-instructor 의 pre-line 을 받으면 항목 사이 줄바꿈이 빈 줄로 보인다 */
  white-space: normal;
}
</style>
