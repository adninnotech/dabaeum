<template>
  <q-page class="detail-page">
    <div class="learner-container">
      <AppErrorBanner v-if="errorMessage" :message="errorMessage" />

      <!-- [목록] 버튼이 [수강신청] 옆에 같은 크기로 있었다 — 돌아가는 길은 글자 링크로 -->
      <button v-if="course" type="button" class="detail-back" @click="goList">
        <q-icon name="arrow_back" size="18px" aria-hidden="true" />강좌 찾기
      </button>

      <article v-if="course" class="detail-card">
        <CourseDetailHead
          :course="course"
          :institution-name="institutionName"
          :sessions="sessions"
          :interested="isInterested"
          :interest-loading="interestLoading"
          :active-enrollment="activeEnrollment"
          :active-enrollment-label="activeEnrollmentLabel"
          :enroll-label="enrollLabel"
          :enroll-disabled="enrollDisabled"
          :enroll-disabled-reason="enrollDisabledReason"
          :applying="applying"
          @share="copyLink"
          @toggle-interest="onInterestClick"
          @apply="applyEnrollment"
        />

        <!-- 탭: 패널이 아니라 앵커 내비 -->
        <div class="detail-tabs">
          <LearnerTabs :tabs="TABS" :model-value="activeTab" @update:model-value="goSection" />
        </div>

        <!-- ① 강좌소개 -->
        <CourseIntroSection :description="course.description || ''" :detail="course.detail" />

        <CourseSessionsSection :sessions="sessions" :is-online="isOnline" />
        <CourseInstructorsSection :instructors="instructors" :is-logged-in="isLoggedIn" />
        <CourseNotesSection :notes="course.detail?.notes || ''" :attachments="attachments" />

        <CourseReviewsSection
          :course-id="course.id"
          :reviews="reviews"
          :summary="reviewSummary"
          :loading="reviewsLoading"
          :can-write="canWriteReview"
          @submitted="onReviewSubmitted"
        />
        <CourseQnaSection
          :course-id="course.id"
          :inquiries="courseInquiries"
          :loading="inquiriesLoading"
          :is-logged-in="isLoggedIn"
          @created="loadCourseInquiries(course.id)"
        />
        <!-- 아래 섹션이 짧으면 끝까지 내려도 그 섹션이 탭 밑까지 못 올라와 '유의사항'을 눌러도 강사소개가 위에 남았다 —
             마지막 섹션이 탭 밑에 닿을 만큼만 빈 칸을 둔다(필요 없으면 0) -->
        <div v-if="tailSpace" class="detail-tail" :style="{ height: `${tailSpace}px` }" aria-hidden="true" />
      </article>

      <AppEmptyState
        v-else-if="!loading"
        title="강좌를 찾을 수 없습니다."
        description="삭제되었거나 공개되지 않은 강좌입니다."
      />
    </div>

    <!-- 폰: 신청 줄이 사진·정보 아래 한참 밑이라 내리다 보면 사라졌다 — 화면 아래(탭바 위)에 고정 -->
    <div v-if="showStickyCta" class="detail-sticky">
      <CourseEnrollCta
        :interested="isInterested"
        :interest-loading="interestLoading"
        :active-enrollment="activeEnrollment"
        :active-enrollment-label="activeEnrollmentLabel"
        :enroll-label="enrollLabel"
        :enroll-disabled="enrollDisabled"
        :applying="applying"
        @toggle-interest="onInterestClick"
        @apply="applyEnrollment"
      />
    </div>

    <q-inner-loading :showing="loading">
      <q-spinner color="primary" size="32px" />
    </q-inner-loading>
  </q-page>
</template>

<script>
import { mapState } from 'pinia';
import AppEmptyState from '@/components/AppEmptyState.vue';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import LearnerTabs from '@/components/LearnerTabs.vue';
import CourseEnrollCta from '@/components/course/CourseEnrollCta.vue';
import CourseDetailHead from '@/components/course/detail/CourseDetailHead.vue';
import CourseIntroSection from '@/components/course/detail/CourseIntroSection.vue';
import CourseInstructorsSection from '@/components/course/detail/CourseInstructorsSection.vue';
import CourseNotesSection from '@/components/course/detail/CourseNotesSection.vue';
import CourseQnaSection from '@/components/course/detail/CourseQnaSection.vue';
import CourseReviewsSection from '@/components/course/detail/CourseReviewsSection.vue';
import CourseSessionsSection from '@/components/course/detail/CourseSessionsSection.vue';
import { PREVIEW_TABS } from '@/config/course-options';
import { getCourse, listCourseAttachments, recordCourseView } from '@/services/course-api';
import { createEnrollment, listMyEnrollments } from '@/services/enrollment-api';
import { listCourseInquiries } from '@/services/inquiry-api';
import { getInstitution } from '@/services/institution-api';
import { listCourseInstructors } from '@/services/instructor-api';
import {
  addInterest,
  listLearningCourses,
  listMyInterests,
  listMyReviews,
  removeInterest,
} from '@/services/learner-api';
import { getCourseReviewSummary, listCourseReviews } from '@/services/review-api';
import { listCourseSessions } from '@/services/session-api';
import { useAuthStore } from '@/stores/auth-store';
import { animateWindowScroll, scrollMarginOf, scrollTargetOf } from '@/utils/smooth-scroll';
import { loginRequiredRoute } from '@/utils/auth-redirect';
import { isRecruitOpen } from '@/utils/course-fields';

/* ?section= 값 → 앵커 탭 이름 (학습관리에서 바로 리뷰 칸으로 오는 링크) */
const SECTION_QUERY = { review: '리뷰/평점', qna: 'Q&A' };

/** 미리보기(D-4)와 같은 라벨 + 리뷰 탭 보정 — 피그마 E-2는 리뷰 블록만 있고 탭이 없다.
 * '교육장소'는 강좌에 장소 칸이 하나뿐이라 머리 정보 목록과 같은 말만 했다 — 학습자 상세에서는 뺀다 */
// 폰 고정 신청 줄이 떠 있음을 레이아웃에 알리는 body 클래스
const BOTTOM_CTA_CLASS = 'has-bottom-cta';

// 스크롤이 끝난 뒤 탭 고정을 푸기까지(마지막 교차 알림을 기다린다), 섹션 강조 시간
const TAB_SETTLE_MS = 150;
const FLASH_MS = 1400;
const FLASH_CLASS = 'detail-section--flash';
// 사용자가 직접 움직이기 시작하면 고정을 푼다
const USER_SCROLL_EVENTS = ['wheel', 'touchstart', 'keydown'];
const TABS = [...PREVIEW_TABS.slice(0, -1).filter((tab) => tab !== '교육장소'), '리뷰/평점', PREVIEW_TABS.at(-1)];

/** 진행 중인 신청 — 이 상태면 다시 신청할 수 없다. 취소·반려·철회는 재신청을 연다 */
const ACTIVE_ENROLLMENT_LABEL = {
  APPLIED: '신청 완료 · 승인 대기',
  WAITLISTED: '대기 중',
  APPROVED: '수강 중',
};
/** GET /users/me/enrollments 에 courseId 필터가 없어 한 페이지를 받아 거른다 */
const MY_ENROLLMENT_PAGE_SIZE = 100;
// "목록" 버튼이 뒤로가기로 돌아가도 되는 직전 화면 — 강좌 찾기·인기 강좌·통합검색 (쿼리 포함)
const LIST_PATH_PATTERN = /^\/(courses(\/popular)?|search)(\?|$)/;

// 맨 아래 빈칸 다시 계산 때 이만큼 이하 차이는 무시 — 반올림으로 1px 씩 오가며 떨렸다
const TAIL_TOLERANCE_PX = 1;

export default {
  name: 'CourseDetailPage',
  components: {
    AppEmptyState,
    AppErrorBanner,
    CourseDetailHead,
    CourseIntroSection,
    CourseEnrollCta,
    CourseInstructorsSection,
    CourseNotesSection,
    CourseQnaSection,
    CourseReviewsSection,
    CourseSessionsSection,
    LearnerTabs,
  },
  data() {
    return {
      TABS,
      activeTab: TABS[0],
      loading: false,
      errorMessage: '',
      course: null,
      institutionName: '',
      sessions: [],
      instructors: [],
      attachments: [],
      reviews: [],
      reviewSummary: null,
      reviewsLoading: false,
      myReviewedCourseIds: [],
      completedCourseIds: [],
      courseInquiries: [],
      inquiriesLoading: false,
      interestId: null,
      interestLoading: false,
      applying: false,
      myEnrollments: [],
      observer: null,
      visibleSections: null,
      // 탭을 눌러 움직이는 동안 그 탭을 고정한다 — 아래 섹션이 짧으면 끝까지 내려도 섹션이 위로 못 올라와
      // 관찰자가 다른 탭(바닥이면 마지막 Q&A)을 골라 '눌러도 안 간다'로 보였다
      tabLock: null,
      tailSpace: 0,
      tailObserver: null,
    };
  },
  computed: {
    ...mapState(useAuthStore, ['isLoggedIn', 'canEnroll', 'userId']),
    // 신청할 수 있거나 내 신청이 있을 때만 — 모집 마감이면 꺼진 버튼 줄이 탭바 위에 겹쳐 본문을 117px 가렸다
    showStickyCta() {
      return !!this.course && this.$q.screen.lt.md && (!this.enrollDisabled || !!this.activeEnrollment);
    },
    isOnline() {
      return this.course?.educationType === 'ONLINE';
    },
    /** 모집중이 아니면 신청 불가 — 비로그인보다 모집 마감이 우선.
     *  상태가 RECRUITING 이어도 모집 마감일이 지났으면 마감이다(카드와 같은 기준, QA CRS-17) */
    recruitOpen() {
      return isRecruitOpen(this.course);
    },
    enrollDisabled() {
      if (!this.recruitOpen) return true;
      return this.isLoggedIn && !this.canEnroll;
    },
    /** 같은 강좌에 이력이 여러 건이어도(취소 후 재신청) 진행 중인 것만 본다 */
    activeEnrollment() {
      const courseId = this.course?.id;
      if (!courseId) return null;
      return (
        this.myEnrollments.find(
          (row) => row.courseId === courseId && ACTIVE_ENROLLMENT_LABEL[row.status],
        ) || null
      );
    },
    activeEnrollmentLabel() {
      return ACTIVE_ENROLLMENT_LABEL[this.activeEnrollment?.status] || '';
    },
    /** 모집 중인데 로그인 계정이 학습자가 아니라 막힌 경우만 이유를 알린다 (모집 마감은 버튼 문구로 충분) */
    enrollDisabledReason() {
      if (!this.recruitOpen) return '';
      if (!this.isLoggedIn || this.canEnroll) return '';
      return '수강신청은 학습자 계정으로 로그인해야 할 수 있습니다.';
    },
    enrollLabel() {
      const status = this.course?.status;
      if (this.recruitOpen) return '수강신청';
      if (status === 'RECRUITING') return '모집 마감';
      // 취소·종료된 강의에 "모집 마감"은 틀린 문구다
      return status === 'RECRUITMENT_CLOSED' ? '모집 마감' : '신청 불가';
    },
    isInterested() {
      return !!this.interestId;
    },
    canWriteReview() {
      const courseId = this.course?.id;
      if (!courseId || !this.isLoggedIn || !this.canEnroll) return false;
      if (this.myReviewedCourseIds.includes(courseId)) return false;
      return this.completedCourseIds.includes(courseId);
    },
  },
  watch: {
    // 폰 고정 신청 줄이 있는 동안 — 레이아웃 하단(사이트 푸터)이 그 줄에 가리지 않게 body 표시를 단다(app.scss)
    showStickyCta: {
      immediate: true,
      handler(on) {
        document.body.classList.toggle(BOTTOM_CTA_CLASS, on);
      },
    },
    '$route.params.courseId'() {
      this.loadDetail();
      this.recordView();
    },
  },
  mounted() {
    this.loadDetail();
    this.recordView();
  },
  beforeUnmount() {
    this.releaseTabLock();
    this.disconnectObserver();
    document.body.classList.remove(BOTTOM_CTA_CLASS);
  },
  methods: {
    // 인기 강좌 집계용 조회 기록(설계서 §8) — 화면에 들어올 때 한 번. 새로 불러오기·다시 시도에서는 부르지 않는다.
    // 실패해도 화면에는 영향이 없다 — 조회 수가 하나 덜 셀 뿐이다
    recordView() {
      const courseId = this.$route.params.courseId;
      if (courseId) recordCourseView(String(courseId)).catch(() => {});
    },
    /**
     * 강좌 목록(강좌 찾기·인기 강좌·통합검색)에서 왔으면 되돌아가 탭·검색·더보기 상태(URL 쿼리)를 살린다.
     * 외부·새 탭 진입이거나 홈 등 다른 화면에서 왔으면 "목록"이라는 이름대로 강좌 찾기 첫 화면으로 간다
     */
    goList() {
      const back = window.history.state?.back;
      if (typeof back === 'string' && LIST_PATH_PATTERN.test(back)) this.$router.back();
      else this.$router.push('/courses');
    },
    // 수강신청·관심 강좌는 로그인해야 한다 — 로그인 화면이 '처음이면 회원가입' 안내를 띄운다
    goLogin() {
      this.$router.push(loginRequiredRoute(this.$route.fullPath));
    },
    /* ---------- 앵커 탭 ---------- */
    goSection(tab) {
      const node = this.$el.querySelector(`[data-tab="${tab}"]`);
      this.activeTab = tab;
      if (!node) return;
      const margin = scrollMarginOf(node);
      this.lockTab(tab, animateWindowScroll(scrollTargetOf(node, margin)));
      this.flashSection(node);
    },
    /**
     * 움직이는 동안 누른 탭을 고정한다 — 끝나면(마지막 교차 알림이 늦게 오기도 해 조금 기다렸다) 풀고,
     * 사용자가 휠·터치·키로 직접 움직이면 애니메이션을 멈추고 바로 푼다
     */
    lockTab(tab, scroll) {
      this.releaseTabLock();
      const release = () => this.releaseTabLock();
      // 화살표 키로 탭을 옮기면 그 keydown 이 window 까지 올라와 바로 풀렸다 — 지금 이벤트가 끝난 뒤에 붙인다
      const listen = window.setTimeout(() => {
        USER_SCROLL_EVENTS.forEach((type) => window.addEventListener(type, release, { once: true, passive: true }));
      });
      this.tabLock = { tab, listen, release, scroll, timer: 0 };
      scroll.done.then((completed) => {
        if (!completed || this.tabLock?.scroll !== scroll) return;
        this.tabLock = { ...this.tabLock, timer: window.setTimeout(release, TAB_SETTLE_MS) };
      });
    },
    releaseTabLock() {
      if (!this.tabLock) return;
      const { timer, listen, release, scroll } = this.tabLock;
      window.clearTimeout(timer);
      window.clearTimeout(listen);
      USER_SCROLL_EVENTS.forEach((type) => window.removeEventListener(type, release));
      this.tabLock = null;
      scroll.cancel();
    },
    /** 섹션이 화면 위까지 못 올라오는 경우(페이지 끝) 어디로 갔는지 잠깐 테두리로 보인다 */
    flashSection(node) {
      node.classList.remove(FLASH_CLASS);
      // 같은 탭을 다시 눌러도 애니메이션이 처음부터 돌게 한 프레임 뒤에 붙인다
      window.requestAnimationFrame(() => node.classList.add(FLASH_CLASS));
      window.setTimeout(() => node.classList.remove(FLASH_CLASS), FLASH_MS);
    },
    observeSections() {
      this.disconnectObserver();
      const nodes = this.$el?.querySelectorAll('[data-tab]');
      if (!nodes?.length) return;
      // entries에는 이번에 교차 상태가 "바뀐" 섹션만 담긴다.
      // 그 부분집합에서 최상단을 고르면 이미 교차 중인 위쪽 섹션이 빠져 아래쪽이 이긴다.
      // 그래서 상태를 누적해두고 매번 관측 대상 전체에서 다시 고른다.
      this.visibleSections = new Map();
      this.observer = new IntersectionObserver(
        (entries) => {
          entries.forEach((entry) => this.visibleSections.set(entry.target, entry.isIntersecting));
          if (this.tabLock) return;
          // 페이지가 짧으면 하위 섹션이 밴드까지 못 올라온다 — 바닥에 닿으면 마지막 탭으로 확정.
          // activeTab을 정하는 지점을 여기 하나로 두어야 스크롤 리스너와 경합하지 않는다.
          if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 2) {
            this.activeTab = TABS[TABS.length - 1];
            return;
          }
          const top = Array.from(this.visibleSections.entries())
            .filter(([, visible]) => visible)
            .map(([node]) => node)
            .sort((a, b) => a.getBoundingClientRect().top - b.getBoundingClientRect().top)[0];
          if (top) this.activeTab = top.dataset.tab;
        },
        { rootMargin: '-120px 0px -70% 0px' },
      );
      nodes.forEach((node) => this.observer.observe(node));
      this.watchTailSpace();
    },
    /** 리뷰·문의가 늦게 와 높이가 바뀌거나 창 크기가 바뀌면 다시 잰다 */
    watchTailSpace() {
      const card = this.$el?.querySelector('.detail-card');
      if (!card) return;
      this.tailObserver = new ResizeObserver(() => this.updateTailSpace());
      this.tailObserver.observe(card);
      window.addEventListener('resize', this.updateTailSpace);
      this.updateTailSpace();
    },
    /** 마지막 섹션을 탭 밑(scroll-margin-top)에 맞췄을 때 아래로 화면 한 장이 남도록 모자란 만큼 */
    /*
     * 본문은 zoom 1.125 라 칸 높이(CSS px)와 화면에 그려진 높이가 다르다. 예전엔 둘을 섞어 계산해
     * 칸이 바뀔 때마다 다시 계산하는 관찰자와 맞물려 1px 씩 오가며 맨 아래가 계속 떨렸다 —
     * 그려진 높이(화면 px)로 재고, 칸에 넣을 땐 zoom 으로 나누고, 1px 이하 차이는 무시한다
     */
    updateTailSpace() {
      const nodes = this.$el?.querySelectorAll('[data-tab]');
      const last = nodes?.[nodes.length - 1];
      if (!last) return;
      const zoom = last.currentCSSZoom || 1;
      const margin = scrollMarginOf(last);
      const alignAt = last.getBoundingClientRect().top + window.scrollY - margin;
      const tail = this.$el.querySelector('.detail-tail');
      const tailScreen = tail ? tail.getBoundingClientRect().height : 0;
      const room = document.documentElement.scrollHeight - tailScreen - alignAt;
      const next = Math.max(0, Math.ceil((window.innerHeight - room) / zoom));
      if (Math.abs(next - this.tailSpace) > TAIL_TOLERANCE_PX) this.tailSpace = next;
    },
    disconnectObserver() {
      this.observer?.disconnect();
      this.observer = null;
      this.tailObserver?.disconnect();
      this.tailObserver = null;
      window.removeEventListener('resize', this.updateTailSpace);
      this.visibleSections = null;
    },
    /* ---------- 로드 ---------- */
    async loadDetail() {
      const courseId = this.$route.params.courseId;
      this.disconnectObserver();
      this.course = null;
      this.institutionName = '';
      this.sessions = [];
      this.instructors = [];
      this.attachments = [];
      this.reviews = [];
      this.reviewSummary = null;
      this.courseInquiries = [];
      this.myEnrollments = [];
      this.activeTab = TABS[0];
      this.errorMessage = '';
      if (!courseId) {
        this.errorMessage = '강좌 ID가 없습니다.';
        return;
      }

      this.loading = true;
      try {
        this.course = await getCourse(courseId);
        await Promise.all([
          this.loadInstitution(this.course),
          this.loadSessions(courseId),
          this.loadInstructors(courseId),
          this.loadAttachments(courseId),
          this.loadReviews(courseId),
          this.loadReviewSummary(courseId),
          this.loadReviewEligibility(),
          this.loadInterest(courseId),
          this.loadCourseInquiries(courseId),
          this.loadMyEnrollments(),
        ]);
        this.$nextTick(this.observeSections);
      } catch (error) {
        this.course = null;
        this.errorMessage = error?.message || '강좌 정보를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
      this.jumpToRequestedSection();
    },
    /* 학습관리 > 나의 수강평 [수강평 쓰기]가 ?section=review 로 온다 — 예전엔 상세 맨 위에서 다시 찾아 내려가야 했다 */
    jumpToRequestedSection() {
      const section = SECTION_QUERY[this.$route.query.section];
      if (!section || !this.course) return;
      this.$nextTick(() => {
        this.goSection(section);
        if (section === '리뷰/평점') this.$el.querySelector('.detail-review-form textarea')?.focus({ preventScroll: true });
      });
    },
    // 강좌 응답의 institutionName 이 정본. 기관 조회는 로그인해야 열려 있어 이름이 빠졌을 때만 부른다
    async loadInstitution(course) {
      this.institutionName = course?.institutionName || '';
      if (this.institutionName || !course?.institutionId || !this.isLoggedIn) return;
      const institution = await getInstitution(course.institutionId).catch(() => null);
      this.institutionName = institution?.name || '';
    },
    async loadSessions(courseId) {
      const page = await listCourseSessions(courseId, {
        page: 0,
        size: 100,
        sort: 'sessionNo,asc',
      }).catch(() => null);
      this.sessions = Array.isArray(page?.data) ? page.data : [];
    },
    async loadInstructors(courseId) {
      const list = await listCourseInstructors(courseId).catch(() => []);
      this.instructors = Array.isArray(list) ? list : [];
    },
    // 첨부·문의 목록은 로그인해야 열려 있다 (비로그인 공개는 강좌·회차·강사·수강평 조회뿐)
    async loadAttachments(courseId) {
      if (!this.isLoggedIn) return;
      const list = await listCourseAttachments(courseId).catch(() => []);
      this.attachments = Array.isArray(list) ? list : [];
    },
    async loadReviews(courseId) {
      this.reviewsLoading = true;
      const page = await listCourseReviews(courseId, { page: 0, size: 100 }).catch(() => null);
      this.reviews = Array.isArray(page?.data) ? page.data : [];
      this.reviewsLoading = false;
    },
    /** 전체 개수·평균은 목록 페이지가 아니라 서버 집계에서 받는다(00-common §2-4 옆, writableCount는 강좌 상세에서 쓰지 않는다) */
    async loadReviewSummary(courseId) {
      this.reviewSummary = await getCourseReviewSummary(courseId).catch(() => null);
    },
    /* 리뷰 칸이 등록을 마치면 — '이미 씀'으로 표시하고 목록·집계를 다시 받는다 */
    async onReviewSubmitted() {
      this.myReviewedCourseIds = [...this.myReviewedCourseIds, this.course.id];
      await Promise.all([this.loadReviews(this.course.id), this.loadReviewSummary(this.course.id)]);
    },
    async loadCourseInquiries(courseId) {
      if (!this.isLoggedIn) return;
      this.inquiriesLoading = true;
      const page = await listCourseInquiries(courseId, { page: 0, size: 50 }).catch(() => null);
      this.courseInquiries = Array.isArray(page?.data) ? page.data : [];
      this.inquiriesLoading = false;
    },
    async loadReviewEligibility() {
      this.myReviewedCourseIds = [];
      this.completedCourseIds = [];
      if (!this.isLoggedIn) return;
      const [coursePage, reviewPage] = await Promise.all([
        listLearningCourses({ page: 0, size: 100, status: 'COMPLETED' }).catch(() => null),
        listMyReviews({ page: 0, size: 100 }).catch(() => null),
      ]);
      this.completedCourseIds = (coursePage?.data || []).map((row) => row.courseId).filter(Boolean);
      this.myReviewedCourseIds = (reviewPage?.data || [])
        .map((row) => row.courseId)
        .filter(Boolean);
    },
    /** 학습자만 본인 신청을 조회한다. 실패하면 기존처럼 [수강신청]을 보이고 서버가 중복을 막는다 */
    async loadMyEnrollments() {
      this.myEnrollments = [];
      if (!this.isLoggedIn || !this.canEnroll) return;
      const page = await listMyEnrollments({
        page: 0,
        size: MY_ENROLLMENT_PAGE_SIZE,
        sort: 'createdAt,desc',
      }).catch(() => null);
      this.myEnrollments = Array.isArray(page?.data) ? page.data : [];
    },
    /** 해제하려면 interestId가 필요해서 목록에서 이 강의의 레코드를 찾아 둔다 */
    async loadInterest(courseId) {
      this.interestId = null;
      if (!this.isLoggedIn || !this.canEnroll) return;
      const page = await listMyInterests({ page: 0, size: 100 }).catch(() => null);
      const rows = Array.isArray(page?.data) ? page.data : [];
      this.interestId = rows.find((row) => row.courseId === courseId)?.id || null;
    },
    /* ---------- 액션 ---------- */
    async copyLink() {
      try {
        await navigator.clipboard.writeText(window.location.href);
        this.$q.notify({ type: 'positive', message: '링크를 복사했습니다.', position: 'top' });
      } catch {
        this.$q.notify({ type: 'warning', message: '링크 복사를 지원하지 않는 브라우저입니다.' });
      }
    },
    onInterestClick() {
      if (!this.isLoggedIn) {
        this.goLogin();
        return;
      }
      this.toggleInterest();
    },
    async toggleInterest() {
      if (!this.course?.id || this.interestLoading) return;
      this.interestLoading = true;
      try {
        if (this.interestId) {
          await removeInterest(this.interestId);
          this.interestId = null;
          this.$q.notify({ type: 'info', message: '관심 강좌에서 뺐습니다.' });
        } else {
          const created = await addInterest(this.course.id);
          this.interestId = created?.id || null;
          // 응답에 id가 없으면 해제할 때 쓸 수 없어 다시 조회한다
          if (!this.interestId) await this.loadInterest(this.course.id);
          this.$q.notify({ type: 'positive', message: '관심 강좌에 담았습니다.' });
        }
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error?.message || '관심 강좌 처리에 실패했습니다.',
        });
      } finally {
        this.interestLoading = false;
      }
    },
    async applyEnrollment() {
      if (!this.isLoggedIn) {
        this.goLogin();
        return;
      }
      if (!this.course?.id) return;
      this.applying = true;
      try {
        await createEnrollment(this.course.id, {
          userId: this.userId,
          applicationType: 'SELF',
        });
        this.$q.notify({
          type: 'positive',
          message: '수강신청이 접수되었습니다.',
          position: 'top',
        });
        this.$router.push('/enrollment');
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error?.message || '수강신청에 실패했습니다.',
          position: 'top',
        });
        // 다른 탭에서 이미 신청했을 수 있다 — 다시 읽어 상태 표시로 바꾼다
        await this.loadMyEnrollments();
      } finally {
        this.applying = false;
      }
    },
  },
};
</script>

<style scoped>
.detail-page {
  position: relative;
  padding: 24px 0;
  background: var(--db-bg-page);
}

.detail-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px 16px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.detail-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 40px;
  margin: 0 0 8px;
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  font-size: 14px;
  color: var(--db-text-sub);
  cursor: pointer;
}

.detail-back:hover,
.detail-back:focus-visible {
  color: var(--db-accent);
}

.detail-back:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
  border-radius: 4px;
}

/* 폰 아래 고정 신청 줄 — 하단 탭바(58 + 1 + 홈 인디케이터) 바로 위 */
.detail-sticky {
  position: fixed;
  right: 0;
  bottom: calc(59px + env(safe-area-inset-bottom));
  left: 0;
  /* 사이트 푸터(q-footer, 2000)가 페이지 끝에서 이 줄을 덮었다 — 한 칸 위. 드로어·대화상자(6000~)보다는 아래 */
  z-index: 2001;
  padding: 10px 16px;
  background: var(--db-bg-white);
  border-top: 1px solid var(--db-border);
  box-shadow: 0 -4px 12px rgb(0 0 0 / 6%);
}

/* 탭으로 옮겨 온 섹션 — 잠깐 테두리(섹션 컴포넌트 루트에 붙는다).
   섹션 윗변이 붙박이 탭 바로 밑에 맞춰져 바깥 테두리(box-shadow)는 윗변이 탭에 가려졌다 — 안쪽에 그린다 */
.detail-card :deep(.detail-section--flash) {
  outline: 2px solid transparent;
  outline-offset: -2px;
  animation: detail-section-flash 1.4s ease-out;
}

@keyframes detail-section-flash {
  0%,
  40% {
    outline-color: var(--db-accent);
  }

  100% {
    outline-color: transparent;
  }
}

@media (prefers-reduced-motion: reduce) {
  .detail-card :deep(.detail-section--flash) {
    animation: none;
    outline-color: var(--db-accent);
  }
}

/* 탭 */
.detail-tabs {
  position: sticky;
  /* 머리(68px, 확대 안 됨) 바로 밑 — 본문은 zoom 이라 나눠야 화면에서 68px 이다 */
  top: calc(68px / var(--db-content-zoom, 1));
  z-index: 2;
  background: var(--db-bg-white);
}

/* 폰: 바깥 카드 테두리·여백과 섹션 카드 여백이 겹쳐 글 폭이 좁았다 — 바깥 카드를 걷는다 */
@media (max-width: 599px) {
  .detail-card {
    padding: 0;
    border: 0;
    background: transparent;
  }
}
</style>
