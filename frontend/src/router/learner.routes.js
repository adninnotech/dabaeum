import { LEARNER_ROLES } from '@/access/roles';

/** 학습자(메인) + 공개 화면 */
export const learnerRoutes = [
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    children: [
      {
        path: '',
        name: 'courses',
        meta: { title: '강좌찾기 / 홈' },
        component: () => import('@/pages/IndexPage.vue'),
      },
      {
        path: 'courses/:courseId',
        name: 'course-detail',
        meta: { title: '강좌 상세' },
        component: () => import('@/pages/CourseDetailPage.vue'),
      },
      {
        path: 'enrollment',
        name: 'enrollment',
        meta: { title: '수강신청 목록', requiresAuth: true, roles: [...LEARNER_ROLES] },
        component: () => import('@/pages/EnrollmentPage.vue'),
      },
      {
        path: 'enrollment/:enrollmentId',
        name: 'enrollment-detail',
        meta: { title: '수강신청 상세', requiresAuth: true, roles: [...LEARNER_ROLES] },
        component: () => import('@/pages/EnrollmentDetailPage.vue'),
      },
      {
        path: 'attendance/scan',
        name: 'attendance-scan',
        meta: { title: '출석 스캔', requiresAuth: true, roles: [...LEARNER_ROLES] },
        component: () => import('@/pages/AttendanceScanPage.vue'),
      },
      {
        path: 'wallet',
        name: 'wallet',
        meta: { title: '학습지갑', requiresAuth: true },
        component: () => import('@/pages/WalletPage.vue'),
      },
      {
        path: 'wallet/credentials/:credentialId',
        name: 'credential-detail',
        meta: { title: '수료증 상세', requiresAuth: true },
        component: () => import('@/pages/CredentialDetailPage.vue'),
      },
      {
        path: 'learning',
        name: 'learning',
        meta: { title: '학습관리', requiresAuth: true },
        component: () => import('@/pages/learning/LearningPage.vue'),
      },
      {
        path: 'mypage',
        name: 'mypage',
        meta: { title: '마이페이지', requiresAuth: true },
        component: () => import('@/pages/MyPage.vue'),
      },
      {
        path: 'mypage/instructor-apply',
        name: 'instructor-apply',
        meta: { title: '강사 신청', requiresAuth: true },
        component: () => import('@/pages/InstructorApplyPage.vue'),
      },
      {
        path: 'support',
        name: 'support',
        meta: { title: '고객센터' },
        component: () => import('@/pages/SupportPage.vue'),
      },
    ],
  },
];
