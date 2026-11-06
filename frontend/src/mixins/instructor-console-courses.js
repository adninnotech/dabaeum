import { mapState } from 'pinia';
import institutionScope from '@/mixins/institution-scope';
import { listCourses } from '@/services/course-api';
import { listInstitutionCourses } from '@/services/institution-api';
import { listInstructorCourses } from '@/services/instructor-api';
import { useAuthStore } from '@/stores/auth-store';

/**
 * 콘솔 과정 목록 — 같은 출결·이수 화면을 강사·기관·관리자 콘솔이 같이 쓴다.
 * 라우트 meta.consoleScope 로 어느 콘솔인지 정한다(없으면 예전처럼 역할로).
 * - instructor: GET /instructors/me/courses — 배정된 과정만(남의 강좌를 고를 수 없게)
 * - institution: GET /institutions/{id}/courses — 지금 보고 있는 기관 과정
 * - admin: GET /courses — 전체
 */
export default {
  mixins: [institutionScope],
  computed: {
    ...mapState(useAuthStore, { consoleAsAdmin: 'isAdmin' }),
    consoleScope() {
      return this.$route.meta?.consoleScope || (this.consoleAsAdmin ? 'admin' : 'instructor');
    },
  },
  methods: {
    fetchConsoleCourses({ page = 0, size = 50 } = {}) {
      if (this.consoleScope === 'institution') {
        // 기관을 못 정하면(소속 없음) 빈 목록 — 기관 미설정 배너가 안내한다
        if (!this.institutionId) return Promise.resolve({ data: [] });
        return listInstitutionCourses(this.institutionId, { page, size, sort: 'createdAt,desc' });
      }
      if (this.consoleScope === 'admin') return listCourses({ page, size, sort: 'createdAt,desc' });
      return listInstructorCourses({ page, size });
    },
    /** 처음 고를 과정 — 주소의 ?courseId 가 목록에 있으면 그것, 아니면 첫 과정.
     *  메뉴·강의 상세에서 넘어올 때 과정 선택이 처음 과정으로 초기화되던 것 */
    pickInitialCourseId(courses) {
      const list = Array.isArray(courses) ? courses : [];
      const wanted = this.$route.query.courseId;
      if (wanted && list.some((course) => course.id === wanted)) return wanted;
      return list[0]?.id || null;
    },
    /** 고른 과정을 주소에 남긴다 — 새로고침·뒤로가기·다른 화면 이동 뒤에도 같은 과정이 열리게 */
    syncCourseQuery(courseId) {
      if (!courseId || this.$route.query.courseId === courseId) return;
      this.$router.replace({ query: { ...this.$route.query, courseId } });
    },
  },
};
