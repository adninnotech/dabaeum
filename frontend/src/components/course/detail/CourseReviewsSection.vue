<template>
  <CourseDetailSection tab="리뷰/평점" title="리뷰/평점">
    <template #count>전체 {{ total }}개 · 평균 {{ averageText }}</template>

    <template v-if="canWrite" #before>
      <div class="detail-review-form">
        <!-- 수정·삭제 API 가 없다 — 쓰기 전에 알린다 -->
        <p class="detail-review-form__note">등록한 수강평은 고치거나 지울 수 없어요.</p>
        <div class="detail-review-form__row">
          <span class="detail-review-form__label">평점</span>
          <q-rating v-model="form.rating" size="32px" color="amber" />
        </div>
        <!-- 내용은 필수, 최대 1000자(서버 422 details content) — 공백만 쓴 수강평이 등록됐다(QA VAL-03) -->
        <q-input
          v-model="form.content"
          outlined
          type="textarea"
          :maxlength="CONTENT_MAX"
          counter
          placeholder="수강평을 남겨 주세요."
          :hint="`최대 ${CONTENT_MAX}자`"
          :error="!!contentError"
          :error-message="contentError"
          class="detail-review-form__input"
          @update:model-value="contentError = ''"
        />
        <div class="detail-review-form__actions">
          <q-btn
            unelevated
            no-caps
            color="primary"
            label="등록"
            class="detail-review-form__submit"
            :loading="saving"
            @click="submit"
          />
        </div>
      </div>
    </template>

    <AppEmptyState v-if="!loading && !reviews.length" compact description="아직 등록된 수강평이 없습니다." />
    <div v-for="item in visibleReviews" :key="item.id" class="detail-review">
      <q-avatar size="40px" class="letter-avatar">
        {{ (item.userName || '학').slice(0, 1) }}
      </q-avatar>
      <div class="detail-review__body">
        <div class="detail-review__head">
          <span class="detail-review__author">{{ item.userName || '학습자' }}</span>
          <q-rating :model-value="item.rating || 0" readonly size="16px" color="amber" />
          <span class="detail-review__date">{{ formatDateKst(item.createdAt) }}</span>
        </div>
        <p class="detail-review__text">{{ item.content || '내용 없음' }}</p>
      </div>
    </div>
    <button v-if="hasMore" type="button" class="detail-more" @click="count += PAGE">
      더보기
      <q-icon name="expand_more" size="24px" />
    </button>
  </CourseDetailSection>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import CourseDetailSection from '@/components/course/detail/CourseDetailSection.vue';
import { createCourseReview } from '@/services/review-api';
import { formatDateKst } from '@/utils/datetime';

const PAGE = 5;
// CourseReviewRequest.content maxLength
const CONTENT_MAX = 1000;

export default {
  name: 'CourseReviewsSection',
  components: { AppEmptyState, CourseDetailSection },
  props: {
    courseId: { type: String, required: true },
    reviews: { type: Array, default: () => [] },
    /** 서버 집계(totalCount·averageRating) — 페이지 단위로 계산하면 전체 평균이 아니게 된다 */
    summary: { type: Object, default: null },
    loading: { type: Boolean, default: false },
    /** 수료했고 아직 안 쓴 학습자만 */
    canWrite: { type: Boolean, default: false },
  },
  emits: ['submitted'],
  data() {
    return { PAGE, CONTENT_MAX, count: PAGE, form: { rating: 5, content: '' }, contentError: '', saving: false };
  },
  computed: {
    visibleReviews() {
      return this.reviews.slice(0, this.count);
    },
    hasMore() {
      return this.reviews.length > this.count;
    },
    total() {
      return this.summary?.totalCount ?? 0;
    },
    averageText() {
      const avg = this.summary?.averageRating;
      return avg == null ? '-' : avg.toFixed(1);
    },
  },
  watch: {
    // 다른 강좌로 바뀌면 더보기·쓰던 글을 처음으로
    courseId() {
      this.count = PAGE;
      this.form = { rating: 5, content: '' };
      this.contentError = '';
    },
  },
  methods: {
    formatDateKst,
    async submit() {
      if (!this.form.rating) {
        this.$q.notify({ type: 'warning', message: '별점을 선택해 주세요.' });
        return;
      }
      const content = String(this.form.content || '').trim();
      if (!content) {
        this.contentError = '수강평 내용을 입력해 주세요.';
        return;
      }
      if (content.length > CONTENT_MAX) {
        this.contentError = `수강평은 ${CONTENT_MAX}자까지 쓸 수 있습니다.`;
        return;
      }
      this.saving = true;
      try {
        await createCourseReview(this.courseId, { rating: this.form.rating, content });
        this.form = { rating: 5, content: '' };
        this.$q.notify({ type: 'positive', message: '수강평을 등록했습니다.', position: 'top' });
        // 페이지가 목록을 다시 받고 '이미 씀'으로 표시한다
        this.$emit('submitted');
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error?.message || '수강평 등록에 실패했습니다.', position: 'top' });
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
/* 리뷰 */
.detail-review-form__note {
  margin: 0;
  font-size: 14px;
  color: var(--db-text-sub);
}

.detail-review-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px 34px;
}

.detail-review-form__row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.detail-review-form__label {
  font-size: 14px;
  font-weight: 500;
  color: var(--db-text-body);
}

.detail-review-form__input :deep(.q-field__control) {
  min-height: 160px;
}

.detail-review-form__actions {
  display: flex;
  justify-content: flex-end;
}

.detail-review-form__submit {
  height: 48px;
  min-height: 48px;
  padding: 0 32px;
}

.detail-review {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 13px 24px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.detail-review + .detail-review {
  margin-top: 10px;
}

.detail-review__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.detail-review__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-review__author {
  font-size: 14px;
  font-weight: 700;
  color: var(--db-text-title);
}

.detail-review__date {
  font-size: 13px;
  color: var(--db-text-sub);
}

.detail-review__text {
  margin: 0;
  font-size: 14px;
  line-height: 22px;
  color: var(--db-text-body);
  white-space: pre-wrap;
}

.detail-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 100%;
  padding: 16px 0;
  background: none;
  border: none;
  font-size: 16px;
  font-weight: 400;
  color: var(--db-accent);
  cursor: pointer;
}
</style>
