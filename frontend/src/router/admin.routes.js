import { ADMIN_ROLES } from '@/access/roles';

/** 사이트(플랫폼) 관리자 */
export const adminRoutes = [
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true, roles: [...ADMIN_ROLES], title: '사이트 관리자' },
    children: [
      {
        path: '',
        name: 'admin-dashboard',
        meta: { title: '관리자 대시보드' },
        component: () => import('@/pages/admin/AdminDashboardPage.vue'),
      },
      {
        path: 'approvals',
        name: 'admin-approvals',
        meta: { title: '가입 승인' },
        component: () => import('@/pages/admin/AdminApprovalsPage.vue'),
      },
      {
        path: 'users',
        name: 'admin-users',
        meta: { title: '사용자 관리' },
        component: () => import('@/pages/admin/AdminUsersPage.vue'),
      },
      {
        path: 'courses',
        name: 'admin-courses',
        meta: { title: '강좌 관리' },
        component: () => import('@/pages/admin/AdminCoursesPage.vue'),
      },
      {
        path: 'courses/:courseId/sessions',
        name: 'admin-sessions',
        meta: { title: '회차 관리' },
        component: () => import('@/pages/admin/AdminSessionsPage.vue'),
      },
      {
        path: 'courses/:courseId/enrollments',
        name: 'admin-enrollments',
        meta: { title: '수강 승인' },
        component: () => import('@/pages/admin/AdminEnrollmentsPage.vue'),
      },
      {
        path: 'courses/:courseId/instructors',
        name: 'admin-course-instructors',
        meta: { title: '과정 강사 배정' },
        component: () => import('@/pages/CourseInstructorsPage.vue'),
      },
      {
        path: 'students',
        name: 'admin-students',
        meta: { title: '수강생 관리' },
        component: () => import('@/pages/admin/AdminStudentsPage.vue'),
      },
      {
        path: 'attendance',
        name: 'admin-attendance',
        meta: { title: '출결 승인' },
        component: () => import('@/pages/admin/AdminAttendancePage.vue'),
      },
      {
        path: 'completion',
        name: 'admin-completion',
        meta: { title: '이수 처리' },
        component: () => import('@/pages/admin/AdminCompletionPage.vue'),
      },
      {
        path: 'credentials',
        name: 'admin-credentials',
        meta: { title: 'VC 수료증 발급' },
        component: () => import('@/pages/admin/AdminCredentialsPage.vue'),
      },
      {
        path: 'notices',
        name: 'admin-notices',
        meta: { title: '공지사항' },
        component: () => import('@/pages/admin/AdminNoticesPage.vue'),
      },
      {
        path: 'blockchain',
        name: 'admin-blockchain',
        meta: { title: '블록체인 모니터링' },
        component: () => import('@/pages/admin/BlockchainDashboardPage.vue'),
      },
      {
        path: 'institution',
        name: 'admin-institution',
        meta: { title: '기관 정보 관리' },
        component: () => import('@/pages/admin/AdminInstitutionPage.vue'),
      },
      {
        path: 'system',
        name: 'admin-system',
        meta: { title: '시스템 관리' },
        component: () => import('@/pages/admin/AdminSystemPage.vue'),
      },
    ],
  },
];
