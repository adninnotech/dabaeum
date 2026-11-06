import { INSTRUCTOR_ROLES } from '@/access/roles';

/** 강사 */
export const instructorRoutes = [
  {
    path: '/instructor',
    component: () => import('@/layouts/InstructorLayout.vue'),
    meta: { requiresAuth: true, roles: [...INSTRUCTOR_ROLES], title: '강사' },
    children: [
      {
        path: '',
        name: 'instructor-dashboard',
        meta: { title: '강사 대시보드' },
        component: () => import('@/pages/instructor/InstructorDashboardPage.vue'),
      },
      {
        path: 'courses',
        name: 'instructor-courses',
        meta: { title: '내 강의 관리' },
        component: () => import('@/pages/instructor/InstructorCoursesPage.vue'),
      },
      {
        path: 'courses/create',
        name: 'instructor-course-create',
        meta: { title: '강의 등록' },
        component: () => import('@/pages/instructor/InstructorCourseCreatePage.vue'),
      },
      {
        path: 'courses/:courseId/edit',
        name: 'instructor-course-edit',
        meta: { title: '강의 수정' },
        component: () => import('@/pages/instructor/InstructorCourseCreatePage.vue'),
      },
      {
        path: 'courses/:courseId',
        name: 'instructor-course-detail',
        meta: { title: '강의 상세', console: 'instructor' },
        component: () => import('@/pages/course-detail/CourseDetailConsolePage.vue'),
      },
      // 기존 하위 페이지는 상세 탭으로 흡수 — 파일은 남겨 두고 라우트만 리다이렉트
      {
        path: 'courses/:courseId/sessions',
        redirect: (to) => ({
          name: 'instructor-course-detail',
          params: to.params,
          query: { tab: 'curriculum' },
        }),
      },
      {
        path: 'courses/:courseId/enrollments',
        redirect: (to) => ({
          name: 'instructor-course-detail',
          params: to.params,
          query: { tab: 'enrollments' },
        }),
      },
      {
        path: 'enrollment-apps',
        name: 'instructor-enrollment-apps',
        meta: { title: '수강 신청 관리' },
        component: () => import('@/pages/instructor/InstructorEnrollmentAppsPage.vue'),
      },
      {
        path: 'enrollment-status',
        name: 'instructor-enrollment-status',
        meta: { title: '수강 현황 관리' },
        component: () => import('@/pages/instructor/InstructorEnrollmentStatusPage.vue'),
      },
      {
        path: 'attendance',
        name: 'instructor-attendance',
        meta: { title: '출결 관리' },
        component: () => import('@/pages/instructor/InstructorAttendancePage.vue'),
      },
      {
        path: 'completion',
        name: 'instructor-completion',
        meta: { title: '이수 처리' },
        component: () => import('@/pages/instructor/InstructorCompletionPage.vue'),
      },
      {
        path: 'inquiries',
        name: 'instructor-inquiries',
        meta: { title: '문의 관리' },
        component: () => import('@/pages/instructor/InstructorInquiriesPage.vue'),
      },
      {
        path: 'reviews',
        name: 'instructor-reviews',
        meta: { title: '수강평 관리' },
        component: () => import('@/pages/instructor/InstructorReviewsPage.vue'),
      },
      {
        path: 'notices',
        name: 'instructor-notices',
        meta: { title: '공지사항' },
        component: () => import('@/pages/instructor/InstructorNoticesPage.vue'),
      },
      {
        path: 'mypage',
        name: 'instructor-mypage',
        meta: { title: '마이페이지' },
        component: () => import('@/pages/instructor/InstructorMyPage.vue'),
      },
      {
        path: 'profile',
        name: 'instructor-profile',
        meta: { title: '마이페이지' },
        component: () => import('@/pages/instructor/InstructorProfilePage.vue'),
      },
    ],
  },
];
