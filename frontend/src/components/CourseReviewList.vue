<template>
  <div>
    <q-inner-loading :showing="loading" />
    <q-list v-if="reviews.length" bordered separator class="bg-white rounded-borders">
      <!-- clickable 이면 줄을 눌러(Enter 포함) 상세를 연다 — 마우스 기기에선 바탕이 옅게 바뀌어 누를 수 있음을 알린다 -->
      <q-item
        v-for="item in reviews"
        :key="item.id"
        :clickable="clickable"
        :role="clickable ? 'button' : undefined"
        :class="{ 'review-item--clickable': clickable }"
        @click="clickable && $emit('select', item)"
      >
        <q-item-section>
          <q-item-label v-if="showCourse" class="text-weight-bold">{{ item.courseTitle || '-' }}</q-item-label>
          <q-item-label v-if="showAuthor" caption>{{ item.userName || '학습자' }}</q-item-label>
          <div class="row items-center q-gutter-xs q-mt-xs">
            <!-- 별 그림만으론 화면낭독기가 점수를 읽지 못했다 — 숫자를 함께 둔다 -->
            <q-rating
              :model-value="item.rating"
              readonly
              color="amber"
              size="20px"
              aria-hidden="true"
            />
            <span class="text-caption text-weight-bold">{{ item.rating }}점</span>
            <!-- 문의 목록처럼 분까지 — 초까지 붙어 다른 콘솔 목록과 달랐다 -->
            <span class="text-caption text-grey-7">{{ formatDateMinuteKst(item.createdAt) }}</span>
          </div>
          <q-item-label v-if="item.content" class="q-mt-sm text-body2">{{ item.content }}</q-item-label>
          <q-item-label v-else class="q-mt-sm text-grey-6 text-caption">내용 없음</q-item-label>
        </q-item-section>
      </q-item>
    </q-list>
    <div v-else-if="!loading" class="text-center text-grey-6 q-pa-xl">
      {{ emptyLabel }}
    </div>
  </div>
</template>

<script>
import { formatDateMinuteKst } from '@/utils/datetime';

export default {
  name: 'CourseReviewList',
  methods: {
    formatDateMinuteKst,
  },
  props: {
    reviews: {
      type: Array,
      default: () => [],
    },
    loading: {
      type: Boolean,
      default: false,
    },
    showAuthor: {
      type: Boolean,
      default: false,
    },
    showCourse: {
      type: Boolean,
      default: false,
    },
    emptyLabel: {
      type: String,
      default: '등록된 수강평이 없습니다.',
    },
    /** 줄을 눌러 select(review) 를 받는다 */
    clickable: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['select'],
};
</script>

<style scoped>
/* 콘솔 표(ConsoleTable)의 누를 수 있는 행과 같은 hover — 터치는 눌린 채 남아 마우스 기기만 */
@media (hover: hover) {
  .review-item--clickable {
    transition: background-color 0.15s ease;
  }

  .review-item--clickable:hover {
    background: var(--db-bg-section);
  }

  /* Quasar 기본 hover 덧칠(q-focus-helper)까지 겹쳐 너무 진했다 — 키보드 포커스 표시는 그대로 둔다 */
  .review-item--clickable:hover:not(:focus-visible) :deep(.q-focus-helper) {
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .review-item--clickable {
    transition: none;
  }
}
</style>
