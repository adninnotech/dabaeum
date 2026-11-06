<template>
  <div>
    <div class="row q-col-gutter-md q-mb-lg">
      <div class="col-12 col-md-6">
        <q-input
          v-model="searchKeyword"
          outlined
          dense
          clearable
          :label="searchLabel"
          :placeholder="searchPlaceholder"
          @keyup="onSearchKeyup"
        >
          <template #append>
            <q-btn flat dense round icon="search" aria-label="검색" @click="applySearch" />
          </template>
        </q-input>
      </div>
      <div class="col-12 col-md-6">
        <q-select
          v-model="courseId"
          outlined
          dense
          emit-value
          map-options
          clearable
          stack-label
          :options="courseOptions"
          label="강의 선택"
          :display-value="courseId ? undefined : '전체 강의'"
          :loading="coursesLoading"
          @update:model-value="onCourseChange"
        />
      </div>
    </div>

    <div v-if="searchKeyword && filteredCourses.length === 0" class="reviews-page__note">
      검색 결과에 맞는 강의가 없습니다.
    </div>
    <div v-else-if="searchKeyword && filteredCourses.length > 1" class="reviews-page__note">
      검색된 {{ filteredCourses.length }}개 강의의 수강평을 표시합니다.
    </div>

    <AppErrorBanner :message="errorMessage">
      <template #action>
        <q-btn flat dense no-caps color="negative" label="다시 시도" @click="reload" />
      </template>
    </AppErrorBanner>
    <!-- 별점 요약 — 지금 보고 있는 범위(강의·검색)의 평균과 건수 -->
    <p v-if="reviews.length && !loading" class="reviews-page__summary">
      평균 <strong>{{ averageRating }}</strong
      >점 · {{ reviews.length }}건
    </p>
    <!-- 목록이 비어 있을 때 q-inner-loading 이 높이 0 으로 안 보였다 — 자리를 잡아 둔다 -->
    <div
      class="relative-position reviews-page__list"
      :class="{ 'reviews-page__list--loading': loading }"
    >
      <!-- 오류일 땐 빈 목록 문구('담당 강의가 없습니다')를 함께 보이지 않는다 — 서로 어긋난 말이었다 -->
      <CourseReviewList
        v-if="!errorMessage || reviews.length"
        :reviews="reviews"
        :loading="loading"
        show-author
        :show-course="showCourseInList"
        :empty-label="emptyLabel"
        clickable
        @select="openReview"
      />
    </div>

    <!-- 줄을 눌러도 아무 일이 없었다 — 수강평 전체와 그 강의 별점 요약을 창으로 -->
    <ConsoleReviewDialog v-model="reviewOpen" :review="selectedReview" />
  </div>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import CourseReviewList from '@/components/CourseReviewList.vue';
import ConsoleReviewDialog from '@/components/console/ConsoleReviewDialog.vue';
import { listCourseInstructors } from '@/services/instructor-api';
import { listCourseReviews } from '@/services/review-api';
import {
  courseMatchesReviewKeyword,
  loadCourseInstructorNameMap,
  mergeCourseReviews,
  normalizeReviewSearchKeyword,
} from '@/utils/review-management';
import { formatCourseOption } from '@/utils/status';

/* 콘솔 수강평 — 강사·기관 수강평 관리가 같이 쓴다(강의 목록 API 만 다르다).
 * 처음엔 전체 강의(첫 강의만 열려 다른 강의엔 수강평이 없어 보였다), 평균 별점, 오류와 빈 문구를 같이 띄우지 않음 */
export default {
  name: 'ConsoleReviewsView',
  components: { AppErrorBanner, ConsoleReviewDialog, CourseReviewList },
  props: {
    /** () => Promise<course[]> — null 이면(기관 미정) 비운다. 바뀌면 다시 읽는다 */
    fetchCourses: { type: Function, default: null },
    searchLabel: { type: String, default: '강의명 검색' },
    searchPlaceholder: { type: String, default: '예: 데이터 분석' },
    /** 강의가 하나도 없을 때 문구 */
    noCoursesText: { type: String, default: '담당 강의가 없습니다.' },
    /** 전체 강의 수강평이 없을 때 문구 */
    noReviewsText: { type: String, default: '담당 강의에 등록된 수강평이 없습니다.' },
  },
  emits: ['loading'],
  data() {
    return {
      courses: [],
      courseId: null,
      reviews: [],
      coursesLoading: false,
      loading: false,
      errorMessage: '',
      searchKeyword: '',
      courseInstructorNames: {},
      reviewOpen: false,
      selectedReview: null,
    };
  },
  computed: {
    filteredCourses() {
      const q = normalizeReviewSearchKeyword(this.searchKeyword);
      if (!q) return this.courses;
      return this.courses.filter((course) =>
        courseMatchesReviewKeyword(course, q, this.courseInstructorNames[course.id] || []),
      );
    },
    courseOptions() {
      return this.filteredCourses.map((item) => ({ label: formatCourseOption(item), value: item.id }));
    },
    showCourseInList() {
      return !!normalizeReviewSearchKeyword(this.searchKeyword) || (!this.courseId && this.filteredCourses.length > 1);
    },
    // 빈 목록 문구는 지금 보고 있는 범위를 말해야 한다 — "등록된 수강평이 없습니다"만으로는 오해한다
    emptyLabel() {
      if (normalizeReviewSearchKeyword(this.searchKeyword)) return '검색 결과가 없습니다.';
      if (this.courseId) return '이 강의에 등록된 수강평이 없습니다.';
      if (!this.courses.length) return this.noCoursesText;
      return this.noReviewsText;
    },
    averageRating() {
      const rated = this.reviews.filter((item) => Number.isFinite(item.rating));
      if (!rated.length) return '-';
      return (rated.reduce((sum, item) => sum + item.rating, 0) / rated.length).toFixed(1);
    },
    courseTitleById() {
      return Object.fromEntries(this.courses.map((c) => [c.id, c.title]));
    },
  },
  watch: {
    searchKeyword(val, oldVal) {
      if (!normalizeReviewSearchKeyword(val) && normalizeReviewSearchKeyword(oldVal)) this.applySearch();
    },
    // 기관을 바꾸면 고른 강의·검색을 비우고 그 기관 것으로 다시 읽는다
    fetchCourses: {
      immediate: true,
      handler() {
        this.courseId = null;
        this.searchKeyword = '';
        this.loadCourses();
      },
    },
    loading(value) {
      this.$emit('loading', value || this.coursesLoading);
    },
    coursesLoading(value) {
      this.$emit('loading', value || this.loading);
    },
  },
  methods: {
    openReview(review) {
      this.selectedReview = review;
      this.reviewOpen = true;
    },
    reload() {
      if (!this.courses.length) {
        this.loadCourses();
        return;
      }
      this.loadReviews();
    },
    onSearchKeyup(event) {
      if (event.key === 'Enter') this.applySearch();
    },
    onCourseChange() {
      // 검색어가 남아 있으면 강의를 골라도 반응이 없었다 — 검색어를 지우면 watcher 가 다시 불러온다
      if (normalizeReviewSearchKeyword(this.searchKeyword)) {
        this.searchKeyword = '';
        return;
      }
      this.loadReviews();
    },
    applySearch() {
      const q = normalizeReviewSearchKeyword(this.searchKeyword);
      if (!q) {
        this.loadReviews();
        return;
      }
      const matched = this.filteredCourses;
      if (!matched.length) {
        this.courseId = null;
        this.reviews = [];
        return;
      }
      if (matched.length === 1) {
        this.courseId = matched[0].id;
      } else {
        this.courseId = null;
      }
      this.loadReviews();
    },
    async loadCourses() {
      if (!this.fetchCourses) {
        this.courses = [];
        this.reviews = [];
        return;
      }
      this.coursesLoading = true;
      this.errorMessage = '';
      try {
        this.courses = (await this.fetchCourses()) || [];
        this.courseInstructorNames = await loadCourseInstructorNameMap(
          this.courses.map((c) => c.id),
          listCourseInstructors,
        );
        if (this.courses.length) await this.loadReviews();
        else this.reviews = [];
      } catch (error) {
        this.courses = [];
        this.errorMessage = error.message || '강의 목록을 불러오지 못했습니다.';
      } finally {
        this.coursesLoading = false;
      }
    },
    resolveTargetCourseIds() {
      const q = normalizeReviewSearchKeyword(this.searchKeyword);
      if (q) {
        if (this.courseId && this.filteredCourses.some((c) => c.id === this.courseId)) {
          return [this.courseId];
        }
        return this.filteredCourses.map((c) => c.id);
      }
      // 강의 선택을 비우면 담당 강의 전체 — 강사 단위 목록 API 가 없어 강의별로 모아 합친다
      return this.courseId ? [this.courseId] : this.courses.map((c) => c.id);
    },
    async loadReviews() {
      const targetIds = this.resolveTargetCourseIds();
      if (!targetIds.length) {
        this.reviews = [];
        return;
      }
      this.loading = true;
      this.errorMessage = '';
      try {
        const pages = await Promise.all(
          targetIds.map(async (id) => ({
            courseId: id,
            page: await listCourseReviews(id, { page: 0, size: 100 }),
          })),
        );
        this.reviews = mergeCourseReviews(pages, this.courseTitleById);
      } catch (error) {
        this.reviews = [];
        this.errorMessage = error.message || '수강평을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
.reviews-page__note {
  margin-bottom: 16px;
  font-size: 12px;
  color: var(--db-text-sub);
}

.reviews-page__summary {
  margin: 0 0 8px;
  font-size: 14px;
  color: var(--db-text-body);
  font-variant-numeric: tabular-nums;
}

.reviews-page__summary strong {
  color: var(--db-text-title);
}

.reviews-page__list--loading {
  min-height: 160px;
}
</style>
