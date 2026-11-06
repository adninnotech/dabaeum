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
        path: 'administrators',
        name: 'admin-administrators',
        meta: { title: '관리자 계정 관리' },
        component: () => import('@/pages/admin/AdminAdministratorsPage.vue'),
      },
      {
        path: 'access-logs',
        name: 'admin-access-logs',
        meta: { title: '관리자 접속기록' },
        component: () => import('@/pages/admin/AdminAccessLogsPage.vue'),
      },
      {
        path: 'instructors',
        name: 'admin-instructors',
        meta: { title: '전체 강사 관리' },
        component: () => import('@/pages/admin/AdminInstructorsPage.vue'),
      },
      {
        path: 'institutions',
        name: 'admin-institutions',
        meta: { title: '기관 관리' },
        component: () => import('@/pages/admin/AdminInstitutionsPage.vue'),
      },
      {
        path: 'menus',
        name: 'admin-menus',
        meta: { title: '메뉴 관리' },
        component: () => import('@/pages/admin/AdminMenusPage.vue'),
      },
      {
        path: 'content-pages',
        name: 'admin-content-pages',
        meta: { title: '콘텐츠 관리' },
        component: () => import('@/pages/admin/AdminContentPagesPage.vue'),
      },
      {
        path: 'content-pages/new',
        name: 'admin-content-page-create',
        meta: { title: '콘텐츠 페이지 만들기' },
        component: () => import('@/pages/admin/AdminContentPageFormPage.vue'),
      },
      {
        path: 'content-pages/:pageId/edit',
        name: 'admin-content-page-edit',
        meta: { title: '콘텐츠 페이지 수정' },
        component: () => import('@/pages/admin/AdminContentPageFormPage.vue'),
      },
      {
        path: 'statistics',
        name: 'admin-statistics',
        meta: { title: '수강·배지 통계' },
        component: () => import('@/pages/admin/AdminStatisticsPage.vue'),
      },
      {
        path: 'statistics/usage',
        name: 'admin-usage-statistics',
        meta: { title: '이용 통계' },
        component: () => import('@/pages/admin/AdminUsageStatisticsPage.vue'),
      },
      {
        path: 'badges',
        name: 'admin-badges',
        meta: { title: '학습배지 현황' },
        component: () => import('@/pages/admin/AdminBadgesPage.vue'),
      },
      {
        path: 'badges/:badgeId',
        name: 'admin-badge-detail',
        meta: { title: '학습배지 상세' },
        component: () => import('@/pages/admin/AdminBadgeDetailPage.vue'),
      },
      {
        path: 'badge-definitions',
        name: 'admin-badge-definitions',
        meta: { title: '학습배지 관리' },
        component: () => import('@/pages/admin/AdminBadgeDefinitionsPage.vue'),
      },
      {
        path: 'classifications',
        name: 'admin-classifications',
        meta: { title: '강좌 분류 관리' },
        component: () => import('@/pages/admin/AdminClassificationsPage.vue'),
      },
      {
        path: 'banners',
        name: 'admin-banners',
        meta: { title: '배너 관리' },
        component: () => import('@/pages/admin/AdminBannersPage.vue'),
      },
      {
        path: 'banners/new',
        name: 'admin-banner-create',
        meta: { title: '배너 등록' },
        component: () => import('@/pages/admin/AdminBannerFormPage.vue'),
      },
      {
        path: 'banners/:bannerId/edit',
        name: 'admin-banner-edit',
        meta: { title: '배너 수정' },
        component: () => import('@/pages/admin/AdminBannerFormPage.vue'),
      },
      {
        path: 'enrollment-status',
        name: 'admin-enrollment-status',
        meta: { title: '수강 현황 관리' },
        component: () => import('@/pages/admin/AdminEnrollmentStatusPage.vue'),
      },
      {
        path: 'inquiries',
        name: 'admin-inquiries',
        meta: { title: '문의 관리' },
        component: () => import('@/pages/admin/AdminInquiriesPage.vue'),
      },
      {
        path: 'notices/new',
        name: 'admin-notice-create',
        meta: { title: '공지사항 작성' },
        component: () => import('@/pages/admin/AdminNoticeFormPage.vue'),
      },
      {
        path: 'notices/:noticeId/edit',
        name: 'admin-notice-edit',
        meta: { title: '공지사항 수정' },
        component: () => import('@/pages/admin/AdminNoticeFormPage.vue'),
      },
      {
        path: 'instructors/:userId',
        name: 'admin-instructor-detail',
        meta: { title: '강사 상세 정보' },
        component: () => import('@/pages/admin/AdminInstructorDetailPage.vue'),
      },
      {
        path: 'students/:userId',
        name: 'admin-student-detail',
        meta: { title: '수강생 상세 정보' },
        component: () => import('@/pages/admin/AdminStudentDetailPage.vue'),
      },
      {
        path: 'institutions/:institutionId',
        name: 'admin-institution-detail',
        meta: { title: '기관 상세 정보' },
        component: () => import('@/pages/admin/AdminInstitutionDetailPage.vue'),
      },
      {
        path: 'enrollments',
        name: 'admin-enrollment-list',
        meta: { title: '수강 신청 관리' },
        component: () => import('@/pages/admin/AdminEnrollmentsListPage.vue'),
      },
      {
        path: 'courses',
        name: 'admin-courses',
        meta: { title: '전체 강의 관리' },
        component: () => import('@/pages/admin/AdminCoursesPage.vue'),
      },
      {
        path: 'courses/:courseId',
        name: 'admin-course-detail',
        meta: { title: '강의 상세', console: 'admin' },
        component: () => import('@/pages/course-detail/CourseDetailConsolePage.vue'),
      },
      // 기존 하위 페이지는 상세 탭으로 흡수 — 옛 주소는 리다이렉트만 남긴다(회차·수강 승인 페이지는 삭제)
      {
        path: 'courses/:courseId/sessions',
        redirect: (to) => ({
          name: 'admin-course-detail',
          params: to.params,
          query: { tab: 'curriculum' },
        }),
      },
      {
        path: 'courses/:courseId/enrollments',
        redirect: (to) => ({
          name: 'admin-course-detail',
          params: to.params,
          query: { tab: 'applications' },
        }),
      },
      {
        path: 'courses/:courseId/instructors',
        redirect: (to) => ({
          name: 'admin-course-detail',
          params: to.params,
          query: { tab: 'info' },
        }),
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
        // 강사·기관 콘솔과 같은 화면 — QR 그림·직접 등록·정정·보정 이력을 같이 쓴다(옛 관리자 전용 화면은 직접 등록이 없었다)
        meta: { title: '출결 관리', consoleScope: 'admin' },
        component: () => import('@/pages/instructor/InstructorAttendancePage.vue'),
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
        meta: { title: '수료증 관리' },
        component: () => import('@/pages/admin/AdminCredentialsPage.vue'),
      },
      {
        path: 'terms',
        name: 'admin-terms',
        meta: { title: '약관 관리' },
        component: () => import('@/pages/admin/AdminTermsPage.vue'),
      },
      {
        path: 'faqs',
        name: 'admin-faqs',
        meta: { title: 'FAQ 관리' },
        component: () => import('@/pages/admin/AdminFaqsPage.vue'),
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
      {
        path: 'mypage',
        name: 'admin-mypage',
        meta: { title: '마이페이지' },
        component: () => import('@/pages/admin/AdminMyPage.vue'),
      },
    ],
  },
];
