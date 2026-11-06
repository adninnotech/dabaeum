<template>
  <!-- 홈 추천 강좌 — 넘기는 캐러셀 대신 한눈에 보이는 격자(데스크톱 4칸·태블릿 2칸·폰 가로 행).
       카드 아래에 '왜 추천했는지' 한 줄 -->
  <ul class="recommend-grid" aria-label="추천 강좌">
    <li
      v-for="item in items"
      :key="item.course.id"
      class="recommend-grid__item"
      @click="track(item)"
    >
      <CourseCard
        :course="item.course"
        fluid
        :interested="isInterested(item.course.id)"
        @toggle-interest="$emit('toggle-interest', $event)"
      >
        <template v-if="item.reasonText" #footer>
          <span class="recommend-grid__reason">
            <q-icon name="o_lightbulb" size="16px" aria-hidden="true" />
            <span>{{ item.reasonText }}</span>
          </span>
        </template>
      </CourseCard>
    </li>
  </ul>
</template>

<script>
import CourseCard from '@/components/course/CourseCard.vue';
import { clickRecommendedCourse } from '@/services/course-api';

export default {
  name: 'RecommendedCourseGrid',
  components: { CourseCard },
  props: {
    /** [{ course, impressionId, reasonText }] — 부모가 reason 을 문장으로 바꿔 넘긴다 */
    items: { type: Array, default: () => [] },
    isInterested: { type: Function, default: () => false },
  },
  emits: ['toggle-interest'],
  data() {
    // 같은 노출을 두 번 보내지 않는다(서버도 두 번째는 404)
    return { trackedIds: [] };
  },
  methods: {
    /**
     * 카드(제목 링크)를 누르면 한 번 기록한다. 하트는 @click.stop 이라 여기 오지 않는다.
     * 기다리지 않는다 — 실패해도 상세로 넘어가는 걸 막지 않는다
     */
    track(item) {
      const id = item.impressionId;
      if (!id || this.trackedIds.includes(id)) return;
      this.trackedIds = [...this.trackedIds, id];
      clickRecommendedCourse(id).catch(() => {});
    },
  },
};
</script>

<style scoped>
.recommend-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.recommend-grid__item {
  display: flex;
  min-width: 0;
}

.recommend-grid__item > * {
  flex: 1;
}

.recommend-grid__reason {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--db-accent);
}

/* 이유는 세 줄까지 — 한 줄이면 '정보통신 분야'처럼 핵심이 잘렸고, 두 줄이면 360px 폰에서
   모델 추천 기본 문장('회원님과 비슷한 분들이 많이 신청했어요')이 잘렸다(사용자 결정 10-21) */
.recommend-grid__reason > span {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
  word-break: keep-all;
}

.recommend-grid__reason .q-icon {
  flex-shrink: 0;
  margin-top: 2px;
}

@media (max-width: 1023px) {
  .recommend-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

/* 폰: 카드가 작은 사진 + 글의 가로 행이 된다(fluid) — 한 줄에 하나 */
@media (max-width: 599px) {
  .recommend-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 10px;
  }
}
</style>
