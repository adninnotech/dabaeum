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
      {
        path: 'courses/:courseId/instructors',
        name: 'institution-course-instructors',
        meta: { title: '과정 강사 배정' },
        component: () => import('@/pages/CourseInstructorsPage.vue'),
      },
      {
        path: 'courses/:courseId/sessions',
        name: 'institution-sessions',
        meta: { title: '회차 관리' },
        component: () => import('@/pages/admin/AdminSessionsPage.vue'),
      },
      {
        path: 'courses/:courseId/enrollments',
        name: 'institution-course-enrollments',
        meta: { title: '수강 승인' },
        component: () => import('@/pages/admin/AdminEnrollmentsPage.vue'),
      },
      {
        path: 'enrollments',
        name: 'institution-enrollments',
        meta: { title: '수강 관리' },
        component: () => import('@/pages/institution/InstitutionEnrollmentsPage.vue'),
      },
      {
        path: 'inquiries',
        name: 'institution-inquiries',
        meta: { title: '문의 관리' },
        component: () => import('@/pages/institution/InstitutionInquiriesPage.vue'),
      },
    ],
  },
];
