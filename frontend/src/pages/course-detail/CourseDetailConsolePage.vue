<template>
  <q-page class="detail-page">
    <div v-if="loading" class="detail-page__state">
      <q-spinner color="primary" size="32px" />
    </div>

    <div v-else-if="forbidden" class="detail-page__state detail-page__state--message">
      <div class="detail-page__state-title">{{ forbiddenText.title }}</div>
      <div>{{ forbiddenText.body }}</div>
      <q-btn no-caps flat color="primary" label="목록으로" @click="goList" />
    </div>

    <div v-else-if="error" class="detail-page__state detail-page__state--message">
      <div class="detail-page__state-title">강의를 불러오지 못했습니다.</div>
      <div>{{ error }}</div>
      <q-btn no-caps flat color="primary" label="다시 시도" @click="load" />
    </div>

    <template v-else>
      <!-- 정상 상태에서도 목록으로 돌아갈 길이 있어야 한다 — 오류 상태의 '목록으로'와 같은 경로 -->
      <router-link :to="listPath" class="detail-page__back">
        <q-icon name="chevron_left" size="20px" aria-hidden="true" />강의 목록
      </router-link>
      <div class="detail-page__title-row">
        <h1 class="detail-page__title">강의 상세 정보</h1>
        <!-- 관리자 정정: 잘못 마감한 모집을 다시 연다 (모집 마감·진행 중만) -->
        <q-btn
          v-if="canReopen"
          outline
          no-caps
          color="primary"
          icon="lock_open"
          label="모집 재개"
          class="detail-page__reopen"
          @click="reopenOpen = true"
        />
      </div>

      <CourseDetailHeader
        :course="course"
        :institution-name="institution?.name || ''"
        :instructor-name="mainInstructor?.name || ''"
      />
      <ConsoleTabs v-model="tab" :items="COURSE_DETAIL_TABS" />

      <div class="detail-page__body">
        <main class="detail-page__main">
          <component :is="activeTabComponent" :key="tab" :course="course" :console="consoleRole" v-bind="tabExtraProps" />
        </main>
        <CourseInfoAside
          :instructor="mainInstructor"
          :institution="institution"
          :instructor-limited="instructorLimited"
        />
      </div>

      <CorrectionReasonDialog
        v-model="reopenOpen"
        title="모집 재개"
        :target="course?.title || ''"
        :notes="REOPEN_NOTES"
        confirm-label="모집 재개"
        confirm-color="primary"
        :action="reopen"
        :error-message="reopenErrorMessage"
        @done="onReopened"
      />
    </template>
  </q-page>
</template>

<script>
import CourseApplicationsTab from '@/components/course-detail/CourseApplicationsTab.vue';
import CourseCurriculumTab from '@/components/course-detail/CourseCurriculumTab.vue';
import CourseDetailHeader from '@/components/course-detail/CourseDetailHeader.vue';
import ConsoleTabs from '@/components/console/ConsoleTabs.vue';
import CorrectionReasonDialog from '@/components/console/CorrectionReasonDialog.vue';
import CourseEnrollmentsTab from '@/components/course-detail/CourseEnrollmentsTab.vue';
import CourseInfoAside from '@/components/course-detail/CourseInfoAside.vue';
import CourseInfoTab from '@/components/course-detail/CourseInfoTab.vue';
import CourseInquiriesTab from '@/components/course-detail/CourseInquiriesTab.vue';
import CourseNotesTab from '@/components/course-detail/CourseNotesTab.vue';
import {
  COURSE_DETAIL_TABS,
  DEFAULT_COURSE_DETAIL_TAB,
  isCourseDetailTab,
} from '@/config/course-detail-tabs';
import { getCourse, reopenCourse } from '@/services/course-api';
import { getInstitution } from '@/services/institution-api';
import { listCourseInstructors } from '@/services/instructor-api';
import { getCurrentUser, getUser } from '@/services/user-api';
import { useAuthStore } from '@/stores/auth-store';

// 콘솔별 강의 목록 경로 — '목록으로'가 다른 콘솔(권한 없는 곳)로 보내지 않게
const LIST_PATHS = {
  admin: '/admin/courses',
  institution: '/institution/courses',
  instructor: '/instructor/courses',
};

// 계약: RECRUITMENT_CLOSED·IN_PROGRESS 에서만 재개할 수 있다
const REOPENABLE_STATUSES = ['RECRUITMENT_CLOSED', 'IN_PROGRESS'];
const REOPEN_NOTES = [
  '강의 상태가 모집 중으로 돌아가 학습자가 다시 신청할 수 있습니다.',
  '종료·취소된 강의는 재개할 수 없습니다.',
];

const TAB_COMPONENTS = {
  info: CourseInfoTab,
  curriculum: CourseCurriculumTab,
  applications: CourseApplicationsTab,
  enrollments: CourseEnrollmentsTab,
  notes: CourseNotesTab,
  inquiries: CourseInquiriesTab,
};

export default {
  name: 'CourseDetailConsolePage',
  components: { ConsoleTabs, CorrectionReasonDialog, CourseDetailHeader, CourseInfoAside },
  data() {
    return {
      loading: true,
      error: '',
      forbidden: false,
      course: null,
      instructors: [],
      mainInstructor: null,
      // true 면 강사 정보가 배정 행(이름·이메일)뿐 — 나머지 칸은 '미등록'이 아니라 '볼 수 없음'
      instructorLimited: false,
      institution: null,
      tab: this.resolveTab(this.$route.query.tab),
      COURSE_DETAIL_TABS,
      REOPEN_NOTES,
      reopenOpen: false,
    };
  },
  computed: {
    /** 'admin' | 'institution' | 'instructor' — 라우트 meta로 판단한다 */
    consoleRole() {
      return this.$route.meta.console || 'admin';
    },
    courseId() {
      return this.$route.params.courseId;
    },
    listPath() {
      return LIST_PATHS[this.consoleRole] || LIST_PATHS.admin;
    },
    forbiddenText() {
      if (this.consoleRole === 'institution') {
        return { title: '다른 기관의 강의입니다.', body: '소속 기관의 강의만 볼 수 있습니다.' };
      }
      return { title: '배정되지 않은 강의입니다.', body: '이 강의에 배정된 강사만 볼 수 있습니다.' };
    },
    /** 정정은 플랫폼 관리자 콘솔에서만 — 강사 콘솔은 같은 화면을 쓰지만 권한이 없다 */
    canReopen() {
      return this.consoleRole === 'admin' && REOPENABLE_STATUSES.includes(this.course?.status);
    },
    /**
     * 수강신청 승인·거절·취소는 주강사(MAIN)도 할 수 있다(서버 EnrollmentDecisionAuthority).
     * 강사 콘솔이면 '내가 이 강의 주강사인지'로 연다 — 보조 강사는 읽기 전용
     */
    canDecideEnrollments() {
      if (this.consoleRole !== 'instructor') return true;
      const { userId } = useAuthStore();
      return this.instructors.some((item) => (item.userId || item.id) === userId && item.role === 'MAIN');
    },
    // 수강 신청 현황 탭에만 넘긴다(다른 탭에 붙으면 루트 속성으로 새어 나간다)
    tabExtraProps() {
      return this.tab === 'applications' ? { canDecide: this.canDecideEnrollments } : {};
    },
    activeTabComponent() {
      return TAB_COMPONENTS[this.tab] || TAB_COMPONENTS[DEFAULT_COURSE_DETAIL_TAB];
    },
  },
  watch: {
    // 탭 ↔ ?tab= 양방향 동기화 (뒤로가기·새로고침·링크 공유)
    tab(value) {
      if (this.$route.query.tab === value) return;
      this.$router.replace({ query: { ...this.$route.query, tab: value } });
    },
    '$route.query.tab'(value) {
      this.tab = this.resolveTab(value);
    },
    courseId() {
      this.load();
    },
  },
  created() {
    this.load();
  },
  methods: {
    resolveTab(value) {
      return isCourseDetailTab(value) ? value : DEFAULT_COURSE_DETAIL_TAB;
    },
    reopen(reason) {
      return reopenCourse(this.courseId, reason);
    },
    reopenErrorMessage(error) {
      if (error?.payload?.code === 'CORRECTION_NOT_ALLOWED') return '종료·취소된 강의는 재개할 수 없습니다.';
      return null;
    },
    onReopened(course) {
      if (course?.id) this.course = { ...this.course, ...course };
      this.$q.notify({ type: 'positive', message: '모집을 다시 열었습니다.', position: 'top' });
    },
    goList() {
      this.$router.push(this.listPath);
    },
    async load() {
      this.loading = true;
      this.error = '';
      this.forbidden = false;
      try {
        const [course, instructors] = await Promise.all([
          getCourse(this.courseId),
          // 강사 콘솔은 이 목록으로 '내 강의인지'를 가린다 — 실패를 빈 목록으로 삼키면 확인 없이 열렸다
          listCourseInstructors(this.courseId).catch((error) => {
            if (this.consoleRole === 'instructor') throw error;
            return [];
          }),
        ]);
        this.course = course;
        this.instructors = Array.isArray(instructors) ? instructors : instructors?.content || [];

        if (this.consoleRole === 'instructor' && !this.isAssignedToMe()) {
          this.forbidden = true;
          return;
        }
        if (this.consoleRole === 'institution' && !this.isMyInstitutionCourse(course)) {
          this.forbidden = true;
          return;
        }

        // 기관·강사 상세는 부가 정보 — 실패해도 본문을 막지 않는다
        await Promise.all([this.loadInstitution(course), this.loadMainInstructor()]);
      } catch (error) {
        if (error?.status === 403) this.forbidden = true;
        else this.error = error?.message || '알 수 없는 오류입니다.';
      } finally {
        this.loading = false;
      }
    },
    async loadInstitution(course) {
      if (!course?.institutionId) return;
      this.institution = await getInstitution(course.institutionId).catch(() => null);
    },
    /** MAIN 역할 강사 우선, 없으면 첫 번째 */
    async loadMainInstructor() {
      const assigned =
        this.instructors.find((item) => item.role === 'MAIN') || this.instructors[0] || null;
      const userId = assigned?.userId || assigned?.id;
      if (!userId) return;
      // GET /users/{id} 는 플랫폼 관리자 전용이라 강사·기관 관리자가 부르면 403 이다.
      // 배정 행(CourseInstructor)은 필드명이 instructorName·instructorEmail 이라 그대로 쓰면
      // 이름이 비어 "강사 미배정" 으로 보인다 — 키를 맞춰서 넘긴다. 나머지 칸은 aside 가 숨긴다.
      const fromAssignment = {
        name: assigned.instructorName || '',
        email: assigned.instructorEmail || '',
      };
      // 본인이면 /users/me, 플랫폼 관리자면 /users/{id}, 그 밖엔 403 이 뻔하니 부르지 않는다
      const { userId: myId, isAdmin } = useAuthStore();
      let load = null;
      if (userId === myId) load = getCurrentUser();
      else if (isAdmin) load = getUser(userId);
      const user = load ? await load.catch(() => null) : null;
      this.instructorLimited = !user;
      this.mainInstructor = user || fromAssignment;
    },
    /** 기관 콘솔: 주소만 바꿔 다른 기관 강의를 여는 것을 막는다(플랫폼 관리자는 모든 기관) */
    isMyInstitutionCourse(course) {
      const { isAdmin, myInstitutions, primaryInstitutionId } = useAuthStore();
      if (isAdmin || !course?.institutionId) return true;
      // 소속 목록을 못 받은 세션(데모 로그인 등)은 세션 역할의 기관으로 대신 본다
      const fromServer = (myInstitutions || []).map((item) => item.institutionId);
      const owned = fromServer.length ? fromServer : [primaryInstitutionId].filter(Boolean);
      if (!owned.length) return true; // 판정 근거가 없으면 막지 않는다 — 서버 403 이 최종 판단
      return owned.includes(course.institutionId);
    },
    isAssignedToMe() {
      const { userId } = useAuthStore();
      // TODO: a5 배정 데이터 확인 후 빈 배열도 차단
      if (!userId || !this.instructors.length) return true; // 판정 근거가 없으면 막지 않는다
      return this.instructors.some((item) => (item.userId || item.id) === userId);
    },
  },
};
</script>

<style scoped>
.detail-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 48px 40px 20px;
  background: var(--db-bg-page);
}

.detail-page__back {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  margin: 0 0 -8px -6px;
  padding: 4px 6px;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-sub);
  text-decoration: none;
}

.detail-page__back:hover {
  color: var(--db-text-title);
}

.detail-page__back:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.detail-page__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.detail-page__reopen {
  height: 40px;
  border-radius: 6px;
}

.detail-page__title {
  display: flex;
  align-items: center;
  height: 58px;
  margin: 0;
  padding: 10px 0;
  font-size: 32px;
  line-height: 1.2;
  font-weight: 700;
  color: var(--db-text-title);
}

.detail-page__body {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.detail-page__main {
  flex: 1;
  min-width: 0;
}

@media (max-width: 1023px) {
  /* 세로로 쌓을 때 flex-start 면 본문이 표 너비만큼 늘어나 페이지가 가로로 밀렸다 */
  .detail-page__body {
    flex-direction: column;
    align-items: stretch;
  }
}

@media (max-width: 599px) {
  .detail-page {
    padding: 20px 16px;
  }

  .detail-page__title {
    height: auto;
    padding: 0;
    font-size: 24px;
  }
}

.detail-page__state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
}

.detail-page__state--message {
  flex-direction: column;
  gap: 8px;
  font-size: 14px;
  color: var(--db-text-help);
}

.detail-page__state-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--db-text-body);
}
</style>
