<template>
  <!-- 콘솔 수강평 상세 — 수강평 한 건과 그 강의의 별점 요약(GET /courses/{id}/reviews/summary).
       답글·숨김 API 는 아직 없어 읽기 전용이다 -->
  <q-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    @before-show="loadSummary"
  >
    <q-card class="review-dialog">
      <q-card-section class="review-dialog__head">
        <h2 class="review-dialog__title">수강평</h2>
        <q-btn v-close-popup flat round dense icon="close" aria-label="닫기" />
      </q-card-section>

      <q-card-section v-if="review" class="review-dialog__body">
        <div class="review-dialog__review">
          <div class="review-dialog__course">{{ review.courseTitle || '-' }}</div>
          <div class="review-dialog__meta">
            <!-- 별 그림만으론 화면낭독기가 점수를 읽지 못한다 — 숫자를 함께 둔다 -->
            <q-rating :model-value="review.rating" readonly color="amber" size="18px" aria-hidden="true" />
            <span class="review-dialog__score">{{ review.rating }}점</span>
            <!-- 작성자·시각은 한 덩어리 — 폰에서 줄이 바뀌면 '· 시각'이 점으로 시작했다 -->
            <span class="review-dialog__by">{{ review.userName || '학습자' }} · {{ formatDateMinuteKst(review.createdAt) }}</span>
          </div>
          <p v-if="review.content" class="review-dialog__text">{{ review.content }}</p>
          <p v-else class="review-dialog__text review-dialog__text--empty">내용 없음</p>
        </div>

        <!-- 이 수강평이 그 강의에서 어느 쪽인지 보이게 — 강의 전체 평균과 별점 분포 -->
        <section class="review-dialog__summary" aria-label="이 강의 수강평 요약">
          <div class="review-dialog__summary-head">
            <span class="review-dialog__summary-title">이 강의 수강평</span>
            <span v-if="summary" class="review-dialog__summary-total">
              평균 <strong>{{ averageText }}</strong> · {{ summary.totalCount }}건
            </span>
          </div>
          <div v-if="summaryLoading" class="review-dialog__state"><q-spinner color="primary" size="20px" /></div>
          <div v-else-if="summaryError" class="review-dialog__error" role="alert">
            {{ summaryError }}
            <q-btn flat dense no-caps color="primary" label="다시 시도" @click="loadSummary(true)" />
          </div>
          <ul v-else-if="summary" class="review-dialog__dist">
            <li
              v-for="row in distribution"
              :key="row.star"
              class="review-dialog__dist-row"
              :class="{ 'review-dialog__dist-row--mine': row.star === review.rating }"
            >
              <span class="review-dialog__dist-star">{{ row.star }}점</span>
              <span
                class="review-dialog__bar"
                role="meter"
                :aria-label="`${row.star}점 ${row.count}건`"
                aria-valuemin="0"
                :aria-valuemax="summary.totalCount"
                :aria-valuenow="row.count"
              >
                <span class="review-dialog__bar-fill" :style="{ width: `${row.percent}%` }" />
              </span>
              <span class="review-dialog__dist-count">{{ row.count }}</span>
            </li>
          </ul>
        </section>
      </q-card-section>

      <q-card-actions align="right" class="review-dialog__actions">
        <q-btn
          v-if="courseLink"
          v-close-popup
          flat
          no-caps
          color="primary"
          label="강의 보기"
          :to="courseLink"
        />
        <q-btn v-close-popup unelevated no-caps color="primary" label="닫기" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import { getCourseReviewSummary } from '@/services/review-api';
import { formatDateMinuteKst } from '@/utils/datetime';

const STARS = [5, 4, 3, 2, 1];
// 콘솔마다 강의 상세 경로가 다르다 — 지금 콘솔 prefix 로 고른다
const COURSE_DETAIL_PREFIX = ['/instructor', '/institution'];

export default {
  name: 'ConsoleReviewDialog',
  props: {
    modelValue: { type: Boolean, default: false },
    /** 목록의 수강평 한 건 { id, courseId, courseTitle, rating, content, userName, createdAt } */
    review: { type: Object, default: null },
  },
  emits: ['update:modelValue'],
  data() {
    return {
      // 강의별 요약 — 같은 강의 수강평을 연달아 열 때 다시 받지 않는다
      summaries: {},
      summaryLoading: false,
      summaryError: '',
    };
  },
  computed: {
    summary() {
      return this.review ? this.summaries[this.review.courseId] || null : null;
    },
    averageText() {
      const value = this.summary?.averageRating;
      return Number.isFinite(value) ? value.toFixed(1) : '-';
    },
    distribution() {
      const dist = this.summary?.distribution || {};
      const total = this.summary?.totalCount || 0;
      return STARS.map((star) => {
        const count = Number(dist[`star${star}`]) || 0;
        return { star, count, percent: total ? Math.round((count / total) * 100) : 0 };
      });
    },
    courseLink() {
      const courseId = this.review?.courseId;
      const prefix = COURSE_DETAIL_PREFIX.find((item) => this.$route.path.startsWith(item));
      return courseId && prefix ? `${prefix}/courses/${courseId}` : '';
    },
  },
  methods: {
    formatDateMinuteKst,
    async loadSummary(force = false) {
      const courseId = this.review?.courseId;
      if (!courseId || (!force && this.summaries[courseId])) return;
      this.summaryLoading = true;
      this.summaryError = '';
      try {
        const summary = await getCourseReviewSummary(courseId);
        this.summaries = { ...this.summaries, [courseId]: summary };
      } catch (error) {
        this.summaryError = error?.message || '강의 수강평 요약을 불러오지 못했습니다.';
      } finally {
        this.summaryLoading = false;
      }
    },
  },
};
</script>

<style scoped>
.review-dialog {
  width: 520px;
  max-width: 94vw;
  border-radius: 12px;
}

.review-dialog__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.review-dialog__title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--db-text-title);
}

.review-dialog__body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 0;
}

.review-dialog__review {
  padding: 14px 16px;
  border-radius: 8px;
  background: var(--db-bg-subtle);
}

.review-dialog__course {
  font-size: 16px;
  font-weight: 700;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.review-dialog__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  font-size: 13px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.review-dialog__score {
  font-weight: 700;
  color: var(--db-text-title);
}

.review-dialog__by {
  white-space: nowrap;
}

.review-dialog__text {
  margin: 10px 0 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--db-text-body);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.review-dialog__text--empty {
  color: var(--db-text-sub);
}

.review-dialog__summary-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.review-dialog__summary-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--db-text-title);
}

.review-dialog__summary-total {
  font-size: 13px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.review-dialog__summary-total strong {
  color: var(--db-text-title);
}

.review-dialog__state {
  display: flex;
  justify-content: center;
  padding: 12px 0;
}

.review-dialog__error {
  font-size: 13px;
  color: var(--db-status-pink);
}

.review-dialog__dist {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.review-dialog__dist-row {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) 32px;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

/* 이 수강평의 별점 줄 — 글자만 진하게, 색은 더하지 않는다 */
.review-dialog__dist-row--mine {
  font-weight: 700;
  color: var(--db-text-title);
}

.review-dialog__bar {
  height: 8px;
  overflow: hidden;
  border-radius: 4px;
  background: var(--db-bg-section);
}

.review-dialog__bar-fill {
  display: block;
  height: 100%;
  border-radius: 4px;
  background: var(--db-text-sub);
}

.review-dialog__dist-row--mine .review-dialog__bar-fill {
  background: var(--db-text-title);
}

.review-dialog__dist-count {
  text-align: right;
}

.review-dialog__actions {
  padding: 8px 16px 16px;
}
</style>
