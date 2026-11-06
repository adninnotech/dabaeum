import { INSTITUTION_ADMIN_ROLES } from '@/access/roles';

/** 기관 관리자 */
export const institutionRoutes = [
  {
    path: '/institution',
    component: () => import('@/layouts/InstitutionLayout.vue'),
    meta: { requiresAuth: true, roles: [...INSTITUTION_ADMIN_ROLES], title: '기관 관리자' },
    children: [
      {
        path: '',
        name: 'institution-dashboard',
        meta: { title: '기관 대시보드' },
        component: () => import('@/pages/institution/InstitutionDashboardPage.vue'),
      },
      {
        path: 'instructors',
        name: 'institution-instructors',
        meta: { title: '강사 관리' },
        component: () => import('@/pages/institution/InstitutionInstructorsPage.vue'),
      },
      {
        path: 'courses',
        name: 'institution-courses',
        meta: { title: '강의 관리' },
        component: () => import('@/pages/institution/InstitutionCoursesPage.vue'),
      },
      // 강사·관리자 콘솔과 같은 강의 상세 화면 — meta.console 로 탭 권한을 정한다
      // (기관 관리자는 신청 승인·회차·QR 은 되고, 플랫폼 관리자 전용 정정·모집 재개는 안 보인다)
      {
        path: 'courses/:courseId',
        name: 'institution-course-detail',
        meta: { title: '강의 상세', console: 'institution' },
        component: () => import('@/pages/course-detail/CourseDetailConsolePage.vue'),
      },
      {
        path: 'courses/:courseId/instructors',
        name: 'institution-course-instructors',
        meta: { title: '강의 강사 배정' },
        component: () => import('@/pages/CourseInstructorsPage.vue'),
      },
      {
        // 옛 회차 화면 — 강의 상세 '학습 목차' 탭이 대신한다. 남은 북마크를 위해 넘겨만 준다
        path: 'courses/:courseId/sessions',
        name: 'institution-sessions',
        redirect: (to) => ({ name: 'institution-course-detail', params: to.params, query: { tab: 'curriculum' } }),
      },
      {
        // 옛 수강 승인 화면 — 강의 상세 '수강 신청 현황' 탭이 대신한다
        path: 'courses/:courseId/enrollments',
        name: 'institution-course-enrollments',
        redirect: (to) => ({ name: 'institution-course-detail', params: to.params, query: { tab: 'applications' } }),
      },
      {
        path: 'enrollments',
        name: 'institution-enrollments',
        meta: { title: '수강 관리' },
        component: () => import('@/pages/institution/InstitutionEnrollmentsPage.vue'),
      },
      // 출결·이수는 강사·관리자 콘솔과 같은 화면을 쓴다 — 과정 목록만 이 기관 것(meta.consoleScope)
      {
        path: 'attendance',
        name: 'institution-attendance',
        meta: { title: '출결 관리', consoleScope: 'institution' },
        component: () => import('@/pages/instructor/InstructorAttendancePage.vue'),
      },
      {
        path: 'completion',
        name: 'institution-completion',
        // 관리자 '이수 처리'와 같은 화면 — 기관 관리자도 평가·확정·발급(서버 권한과 같게, QA CMP-04·사용자 결정 10-21). 확정 취소만 관리자 전용
        meta: { title: '수료증 발급', consoleScope: 'institution' },
        component: () => import('@/pages/admin/AdminCompletionPage.vue'),
      },
      {
        path: 'inquiries',
        name: 'institution-inquiries',
        meta: { title: '문의 관리' },
        component: () => import('@/pages/institution/InstitutionInquiriesPage.vue'),
      },
      {
        path: 'reviews',
        name: 'institution-reviews',
        meta: { title: '수강평 관리' },
        component: () => import('@/pages/institution/InstitutionReviewsPage.vue'),
      },
      {
        path: 'mypage',
        name: 'institution-mypage',
        meta: { title: '마이페이지' },
        component: () => import('@/pages/institution/InstitutionMyPage.vue'),
      },
      {
        path: 'profile',
        name: 'institution-profile',
        meta: { title: '기관 정보 수정' },
        component: () => import('@/pages/institution/InstitutionProfilePage.vue'),
      },
    ],
  },
];
