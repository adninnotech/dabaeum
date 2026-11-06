<template>
  <!-- 폰(xs) 강의 카드 — 강의 표가 옆으로 밀려 버튼이 화면 밖에 있던 것. 표의 칸을 세로로 쌓는다 -->
  <div class="course-card">
    <div class="course-card__head">
      <router-link :to="to" class="course-card__title">{{ course.title || '-' }}</router-link>
      <StatusBadge :status="course.status" />
    </div>
    <div class="course-card__meta">
      <span v-for="item in meta" :key="item" class="num">{{ item }}</span>
    </div>
    <div v-if="$slots.default" class="course-card__actions">
      <slot />
    </div>
  </div>
</template>

<script>
import StatusBadge from '@/components/StatusBadge.vue';

export default {
  name: 'ConsoleCourseCard',
  components: { StatusBadge },
  props: {
    course: { type: Object, required: true },
    /** 강의 상세 링크 */
    to: { type: [String, Object], required: true },
    /** 제목 아래 한 줄로 이어 보일 글자들(기간·역할·신청자 등) — 빈 값은 뺀다 */
    metaItems: { type: Array, default: () => [] },
  },
  computed: {
    meta() {
      return this.metaItems.filter(Boolean);
    },
  },
};
</script>

<style scoped>
.course-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 4px;
  border-bottom: 1px solid var(--db-border);
}

.course-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.course-card__title {
  min-width: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--db-text-title);
  text-decoration: none;
  overflow-wrap: anywhere;
}

.course-card__title:hover,
.course-card__title:focus-visible {
  color: var(--db-accent);
  text-decoration: underline;
}

.course-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  font-size: 12px;
  color: var(--db-text-sub);
}

.course-card__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 4px;
}
</style>
