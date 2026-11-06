import { LEARNER_ROLES } from '@/access/roles';

/** 학습자(메인) + 공개 화면 */
export const learnerRoutes = [
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    children: [
      {
        path: '',
        name: 'home',
        meta: { title: '홈' },
        component: () => import('@/pages/IndexPage.vue'),
      },
      {
        path: 'policies/:type',
        name: 'policy',
        meta: { title: '약관' },
        component: () => import('@/pages/PolicyPage.vue'),
      },
      {
        path: 'notices',
        name: 'notices',
        meta: { title: '공지사항' },
        component: () => import('@/pages/NoticesPage.vue'),
      },
      {
        path: 'notices/:noticeId',
        name: 'notice-detail',
        meta: { title: '공지사항' },
        component: () => import('@/pages/NoticeDetailPage.vue'),
      },
      {
        path: 'courses',
        name: 'courses',
        meta: { title: '강좌 찾기' },
        component: () => import('@/pages/CourseSearchPage.vue'),
      },
      {
        path: 'courses/popular',
        name: 'courses-popular',
        meta: { title: '인기 강좌' },
        component: () => import('@/pages/PopularCoursesPage.vue'),
      },
      {
        path: 'courses/:courseId',
        name: 'course-detail',
        meta: { title: '강좌 상세' },
        component: () => import('@/pages/CourseDetailPage.vue'),
      },
      // 대구 다른 기관 강좌(공공데이터) — 조회 전용, 신청은 운영기관 홈페이지
      {
        path: 'public-courses/:courseId',
        name: 'public-course-detail',
        meta: { title: '강좌 상세' },
        component: () => import('@/pages/PublicCourseDetailPage.vue'),
      },
      // 수강신청 목록은 학습관리 "수강 신청" 탭으로 옮겼다 — 옛 주소·링크는 그쪽으로 보낸다
      {
        path: 'enrollment',
        name: 'enrollment',
        redirect: { path: '/learning', query: { tab: 'applications' } },
      },
      {
        path: 'enrollment/:enrollmentId',
        name: 'enrollment-detail',
        meta: { title: '수강신청 상세', requiresAuth: true, roles: [...LEARNER_ROLES] },
        component: () => import('@/pages/EnrollmentDetailPage.vue'),
      },
      // 코드 붙여넣기 출석은 대리 출석 경로라 없앴다(다대구 QR 또는 강사 기록만) — 옛 주소는 수강 상세로 보낸다
      {
        path: 'attendance/scan',
        name: 'attendance-scan',
        redirect: (to) =>
          to.query.enrollmentId ? `/enrollment/${to.query.enrollmentId}` : '/learning',
      },
      {
        path: 'badges',
        name: 'badges',
        meta: { title: '배지 도감' },
        component: () => import('@/pages/BadgeCatalogPage.vue'),
      },
      {
        path: 'wallet',
        name: 'wallet',
        meta: { title: '학습지갑', requiresAuth: true, roles: [...LEARNER_ROLES] },
        component: () => import('@/pages/WalletPage.vue'),
      },
      {
        path: 'wallet/badges/:badgeId',
        name: 'badge-detail',
        meta: { title: '학습배지 상세', requiresAuth: true, roles: [...LEARNER_ROLES] },
        component: () => import('@/pages/BadgeDetailPage.vue'),
      },
      {
        path: 'wallet/credentials/:credentialId',
        name: 'credential-detail',
        meta: { title: '수료증 상세', requiresAuth: true, roles: [...LEARNER_ROLES] },
        component: () => import('@/pages/CredentialDetailPage.vue'),
      },
      {
        path: 'learning',
        name: 'learning',
        meta: { title: '학습관리', requiresAuth: true, roles: [...LEARNER_ROLES] },
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
        path: 'mypage/institution-apply',
        name: 'institution-apply',
        meta: { title: '기관 등록 신청', requiresAuth: true },
        component: () => import('@/pages/InstitutionApplyPage.vue'),
      },
      {
        path: 'wallet/portfolio',
        name: 'portfolio',
        meta: { title: '학습 포트폴리오', requiresAuth: true },
        component: () => import('@/pages/PortfolioPage.vue'),
      },
      // 포트폴리오는 학습지갑에서 들어간다 — 옛 주소·북마크는 새 주소로 보낸다
      { path: 'mypage/portfolio', redirect: '/wallet/portfolio' },
      // 공개 수료증 진위 확인 — 로그인 없이 연다. 포트폴리오 "진위 확인"·QR 은 번호가 붙은 주소로 온다
      {
        path: 'verify',
        name: 'credential-verify',
        meta: { title: '수료증 진위 확인' },
        component: () => import('@/pages/CredentialVerifyPage.vue'),
      },
      {
        path: 'verify/:credentialNo',
        name: 'credential-verify-no',
        meta: { title: '수료증 진위 확인' },
        component: () => import('@/pages/CredentialVerifyPage.vue'),
      },
      {
        path: 'search',
        name: 'search',
        meta: { title: '통합검색' },
        component: () => import('@/pages/SearchPage.vue'),
      },
      {
        path: 'portfolio/:token',
        name: 'public-portfolio',
        meta: { title: '학습 포트폴리오' },
        component: () => import('@/pages/PublicPortfolioPage.vue'),
      },
      {
        path: 'pages/:slug',
        name: 'content-page',
        meta: { title: '안내' },
        component: () => import('@/pages/ContentPage.vue'),
      },
      {
        path: 'support',
        name: 'support',
        meta: { title: '고객센터' },
        component: () => import('@/pages/SupportPage.vue'),
      },
    ],
  },
  // 학생이 강사 QR 을 폰 기본 카메라로 찍으면 열리는 출석 페이지. 로그인 없이 다대구 DID 로 본인 확인한다
  {
    path: '/attend',
    name: 'attend',
    meta: { title: '출석 체크', requiresAuth: false },
    component: () => import('@/pages/AttendPage.vue'),
  },
];
