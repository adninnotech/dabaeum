<template>
  <!-- 기관 강의 한 줄의 바로가기 — 상세 탭(학습 목차·수강 신청)·강사 배정, 공개 전이면 [게시], 나머지는 ⋮ -->
  <div class="course-actions">
    <q-btn flat dense no-caps color="primary" label="학습 목차" :to="detail('curriculum')" />
    <q-btn flat dense no-caps color="primary" label="수강 신청" :to="detail('applications')" />
    <q-btn flat dense no-caps color="primary" label="강사 배정" :to="`/institution/courses/${course.id}/instructors`" />
    <!-- 서버가 받는 상태에서만 — 게시는 DRAFT, 마감은 RECRUITING 만(CourseStatusPolicy) -->
    <q-btn
      v-if="canPublish"
      outline
      dense
      no-caps
      color="primary"
      label="게시"
      class="course-actions__publish"
      :loading="busy"
      @click="$emit('publish', course)"
    />
    <q-btn flat round dense size="sm" icon="more_vert" :aria-label="`${course.title} 메뉴`" :disable="busy">
      <q-menu>
        <q-list dense>
          <q-item v-close-popup clickable @click="$emit('edit', course)">
            <q-item-section>강의 정보 수정</q-item-section>
          </q-item>
          <!-- 임시저장(DRAFT)만 — 게시된 강좌는 서버가 409 로 막는다 -->
          <template v-if="canDelete">
            <q-separator />
            <q-item v-close-popup clickable class="text-negative" @click="$emit('delete', course)">
              <q-item-section>임시저장 삭제</q-item-section>
            </q-item>
          </template>
          <template v-if="canClose">
            <q-separator />
            <q-item v-close-popup clickable class="text-negative" @click="$emit('close-recruit', course)">
              <q-item-section>모집 마감</q-item-section>
            </q-item>
          </template>
        </q-list>
      </q-menu>
    </q-btn>
  </div>
</template>

<script>
export default {
  name: 'CourseRowActions',
  props: {
    course: { type: Object, required: true },
    busy: { type: Boolean, default: false },
    canPublish: { type: Boolean, default: false },
    canClose: { type: Boolean, default: false },
    canDelete: { type: Boolean, default: false },
  },
  emits: ['edit', 'publish', 'close-recruit', 'delete'],
  methods: {
    detail(tab) {
      return { name: 'institution-course-detail', params: { courseId: this.course.id }, query: { tab } };
    },
  },
};
</script>

<style scoped>
.course-actions {
  display: inline-flex;
  /* 줄바꿈되면 버튼 넷이 세로로 쌓여 표 한 행이 140px 이었다 */
  flex-wrap: nowrap;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
}

.course-actions__publish {
  margin-left: 4px;
}
</style>
