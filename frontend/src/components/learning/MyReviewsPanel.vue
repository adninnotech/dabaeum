<template>
  <!-- 학습관리 '나의 수강평' 탭 — 행·쓸 수 있는 강좌는 페이지가 고른다(나의 강좌 목록과 맞춰 봐야 해서) -->
  <div class="my-reviews">
    <!-- 쓸 수 있는 수강평을 이 탭에서 바로 — 예전엔 배너 → 나의 강좌(수료) → [수강평 쓰기] → 강좌 상세 → 스크롤로 세 번을 거쳤다.
         버튼은 강좌 상세의 리뷰 칸으로 바로 간다(?section=review) -->
    <section v-if="writableCourses.length && !query" class="review-todo" aria-labelledby="review-todo-title">
      <h2 id="review-todo-title" class="review-todo__title">쓸 수 있는 수강평 {{ writableCourses.length }}</h2>
      <p class="review-todo__sub">수료한 강좌에 수강평을 남겨 주시면 다른 학습자에게 큰 도움이 됩니다.</p>
      <ul class="review-todo__list">
        <li v-for="course in writableCourses" :key="course.courseId" class="review-todo__item">
          <span class="review-todo__course">{{ course.title }}</span>
          <q-btn
            unelevated
            no-caps
            dense
            color="primary"
            label="수강평 쓰기"
            class="review-todo__btn"
            :to="{ path: `/courses/${course.courseId}`, query: { section: 'review' } }"
          />
        </li>
      </ul>
    </section>

    <!-- 수정·삭제 API 가 없다 — 알리지 않아 고칠 수 있는 줄 알았다 -->
    <p v-if="rows.length" class="review-note">등록한 수강평은 고치거나 지울 수 없어요.</p>

    <div class="learning-panel">
      <AppEmptyState
        v-if="!rows.length && !failed"
        :description="query ? '검색 결과가 없습니다.' : '작성한 수강평이 없습니다.'"
      />
      <div v-for="item in rows" :key="item.id" class="review-row">
        <!-- 응답에 강좌 카드 필드(썸네일)가 온다 — 예전 주석('응답에 없다')과 달리 늘 회색 빈 칸이었다 -->
        <div class="review-row__thumb">
          <img v-if="item.thumbnailUrl" :src="item.thumbnailUrl" alt="" class="review-row__img" />
          <StatusBadge
            v-if="item.educationType"
            :status="item.educationType"
            class="review-row__type"
          />
        </div>

        <div class="review-row__course">
          <router-link :to="`/courses/${item.courseId}`" class="review-row__course-title">{{ item.title }}</router-link>
          <span class="review-row__course-meta">{{ item.metaLine }}</span>
        </div>

        <div class="review-row__review">
          <!-- 24px 별 5개가 강좌명보다 크게 보였다 — 16px, 점수는 옆에 작게 -->
          <div class="review-row__rating" :aria-label="`별점 ${item.rating}점`">
            <span class="review-row__stars" aria-hidden="true">
              <q-icon
                v-for="star in 5"
                :key="star"
                name="star"
                size="16px"
                :style="{ color: star <= item.rating ? 'var(--db-rating)' : 'var(--db-border)' }"
              />
            </span>
            <span class="review-row__score">{{ item.score }}</span>
          </div>
          <p class="review-row__text">{{ item.content }}</p>
        </div>

        <span class="review-row__date">{{ formatDateKst(item.createdAt) }}</span>
      </div>
    </div>
  </div>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { formatDateKst } from '@/utils/datetime';

export default {
  name: 'MyReviewsPanel',
  components: { AppEmptyState, StatusBadge },
  props: {
    /** 검색·칩으로 거른 수강평 행 */
    rows: { type: Array, default: () => [] },
    /** 수료했는데 아직 안 쓴 강좌 [{ courseId, title }] */
    writableCourses: { type: Array, default: () => [] },
    query: { type: String, default: '' },
    failed: { type: Boolean, default: false },
  },
  methods: {
    formatDateKst,
  },
};
</script>

<style scoped>
/* 문의·수강평 공용 패널 — 행과 같은 이유로 outline (border면 안쪽 폭이 1.6px 모자란다) */
.learning-panel {
  background: var(--db-bg-white);
  outline: 1px solid var(--db-border);
  outline-offset: -1px;
  border-radius: 8px;
}

.review-row:first-of-type {
  border-top: 0;
}

/* 수강평 행 — 1152 = 152 + 320 + 456 + 120 + 40 + gap 16×4 */
.review-row {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 13px 24px;
  border-top: 1px solid var(--db-border);
}

.review-todo {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 16px;
  padding: 16px 20px;
  background: var(--db-accent-soft);
  border-radius: 8px;
}

.review-todo__title {
  margin: 0;
  font-size: 16px;
  line-height: 1.4;
  font-weight: 700;
  color: var(--db-text-title);
}

.review-todo__sub {
  margin: 0;
  font-size: 14px;
  color: var(--db-text-body);
  word-break: keep-all;
}

.review-todo__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 6px 0 0;
  padding: 0;
  list-style: none;
}

.review-todo__item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: var(--db-bg-white);
  border-radius: 6px;
}

.review-todo__course {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--db-text-title);
}

.review-todo__btn {
  flex-shrink: 0;
  padding: 0 12px;
  border-radius: 6px;
}

.review-note {
  margin: 0 0 10px;
  font-size: 14px;
  color: var(--db-text-sub);
}

.review-row__img {
  width: 100%;
  height: 100%;
  border-radius: 4px;
  object-fit: cover;
}

.review-row__thumb {
  position: relative;
  overflow: hidden;
  flex: 0 0 152px;
  height: 80px;
  border-radius: 4px;
  background: var(--db-border-avatar);
}

.review-row__type {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 0 8px;
}

.review-row__course {
  display: flex;
  flex-direction: column;
  flex: 0 0 320px;
  min-width: 0;
}

.review-row__course-title {
  display: block;
  margin-bottom: 8px;
  text-decoration: none;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 16px;
  line-height: 19px;
  font-weight: 700;
  color: var(--db-text-title);
}

/* 강사·카테고리는 어느 응답에도 없다 — 기관명 한 조각만 */
.review-row__course-meta {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 16px;
  line-height: 19px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.review-row__review {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 0 0 456px;
  min-width: 0;
  padding: 0 16px;
}

.review-row__rating {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 18px;
}

.review-row__course-title:hover,
.review-row__course-title:focus-visible {
  color: var(--db-accent);
  text-decoration: underline;
}

.review-row__stars {
  display: flex;
}

.review-row__score {
  font-size: 14px;
  line-height: 16px;
  font-weight: 600;
  color: var(--db-text-body);
}

.review-row__text {
  margin: 0;
  font-size: 16px;
  line-height: 19.09px;
  font-weight: 400;
  color: var(--db-text-body);
  white-space: pre-wrap;
}

.review-row__date {
  flex: 0 0 120px;
  white-space: nowrap;
  font-size: 16px;
  line-height: 19px;
  font-weight: 400;
  color: var(--db-text-sub);
}




/* 문의·수강평 줄은 데스크톱 1152px 칸 너비(477·456px 등)로 고정돼 있었고 폰 설정이 없었다 —
 * 그 폭보다 좁은 화면(태블릿·폰)에선 강좌·상태 / 제목 / 날짜를 쌓는다 */

@media (max-width: 1199px) {
  .review-row {
    padding: 12px 16px;
  }

  .review-row {
    flex-wrap: wrap;
    gap: 8px 12px;
  }

  .review-row__thumb {
    display: none;
  }

  .review-row__course {
    flex: 1 1 0;
  }

  .review-row__course-title {
    margin-bottom: 2px;
  }

  .review-row__course-meta {
    font-size: 14px;
  }

  .review-row__date {
    flex: 0 0 auto;
    font-size: 14px;
  }

  .review-row__review {
    flex: 1 1 100%;
    padding: 0;
  }

  .review-row__text {
    font-size: 14px;
    line-height: 1.5;
    overflow-wrap: anywhere;
  }
}
</style>
