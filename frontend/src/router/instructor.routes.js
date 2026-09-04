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
        path: 'courses/:courseId/sessions',
        name: 'instructor-sessions',
        meta: { title: '회차 관리' },
        component: () => import('@/pages/instructor/InstructorSessionsPage.vue'),
      },
      {
        path: 'courses/:courseId/enrollments',
        name: 'instructor-enrollments',
        meta: { title: '강좌 수강생' },
        component: () => import('@/pages/instructor/InstructorEnrollmentsPage.vue'),
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
        path: 'notices',
        name: 'instructor-notices',
        meta: { title: '공지사항' },
        component: () => import('@/pages/instructor/InstructorNoticesPage.vue'),
      },
      {
        path: 'profile',
        name: 'instructor-profile',
        meta: { title: '프로필 설정' },
        component: () => import('@/pages/instructor/InstructorProfilePage.vue'),
      },
    ],
  },
];
