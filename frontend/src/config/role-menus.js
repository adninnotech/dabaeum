/** 역할별 왼쪽 사이드바 메뉴. to가 `/`인 항목은 공개 사이트로 나가므로 활성 표시하지 않는다. */
export const INSTRUCTOR_MENUS = [
  { label: '대시보드', icon: 'dashboard', to: '/instructor', match: 'exact' },
  // { label: '사이트보기', icon: 'public', to: '/', match: 'none' },
  { label: '강의 등록', icon: 'add_circle_outline', to: '/instructor/courses/create', match: 'prefix' },
  {
    label: '내 강의 관리',
    icon: 'menu_book',
    to: '/instructor/courses',
    match: 'prefix',
    exclude: ['/instructor/courses/create'],
  },
  { label: '수강 신청 관리', icon: 'how_to_reg', to: '/instructor/enrollment-apps' },
  { label: '수강 현황 관리', icon: 'insights', to: '/instructor/enrollment-status' },
  { label: '출결 관리', icon: 'fact_check', to: '/instructor/attendance' },
  { label: '이수 처리', icon: 'task_alt', to: '/instructor/completion' },
  { label: '문의 관리', icon: 'forum', to: '/instructor/inquiries' },
  { label: '공지사항', icon: 'campaign', to: '/instructor/notices' },
  { label: '프로필 설정', icon: 'manage_accounts', to: '/instructor/profile' },
];

export const INSTITUTION_MENUS = [
  { label: '대시보드', icon: 'dashboard', to: '/institution', match: 'exact' },
  { label: '강사 관리', icon: 'groups', to: '/institution/instructors' },
  { label: '강의 관리', icon: 'menu_book', to: '/institution/courses' },
  { label: '수강 관리', icon: 'how_to_reg', to: '/institution/enrollments' },
  { label: '문의 관리', icon: 'support_agent', to: '/institution/inquiries' },
  // { label: '사이트보기', icon: 'public', to: '/', match: 'none' },
];

export const ADMIN_MENUS = [
  { label: '대시보드', icon: 'dashboard', to: '/admin', match: 'exact' },
  { label: '가입 승인', icon: 'pending_actions', to: '/admin/approvals' },
  { label: '사용자 관리', icon: 'manage_accounts', to: '/admin/users' },
  { label: '강좌 관리', icon: 'menu_book', to: '/admin/courses' },
  { label: '수강생 관리', icon: 'groups', to: '/admin/students' },
  { label: '출결 승인', icon: 'fact_check', to: '/admin/attendance' },
  { label: '이수 처리', icon: 'task_alt', to: '/admin/completion' },
  { label: 'VC 수료증 발급', icon: 'workspace_premium', to: '/admin/credentials' },
  { label: '공지사항', icon: 'campaign', to: '/admin/notices' },
  { label: '블록체인 모니터링', icon: 'hub', to: '/admin/blockchain' },
  { label: '기관 정보 관리', icon: 'apartment', to: '/admin/institution' },
  { label: '시스템 관리', icon: 'settings', to: '/admin/system' },
];

export const LEARNER_ACCOUNT_MENUS = [
  { label: '학습 현황', icon: 'insights', to: '/mypage', match: 'exact' },
  { label: '나의 강의', icon: 'school', to: '/learning', match: 'exact' },
  { label: '수강 신청', icon: 'assignment', to: '/enrollment' },
  { label: '수료증/배지', icon: 'workspace_premium', to: '/wallet' },
  { label: '관심 강좌', icon: 'favorite_border', to: '/learning?tab=interests' },
  { label: '나의 수강평', icon: 'rate_review', to: '/learning?tab=reviews' },
  { label: '나의 문의', icon: 'help_outline', to: '/learning?tab=inquiries' },
  { label: '강사 신청', icon: 'badge', to: '/mypage/instructor-apply' },
];

export const LEARNER_MAIN_MENUS = [
  { name: 'courses', label: '강좌찾기', icon: 'search', to: '/', match: 'home' },
  { name: 'learning', label: '학습관리', icon: 'school', to: '/learning', requiresAuth: true },
  { name: 'mypage', label: '마이페이지', icon: 'person', to: '/mypage', requiresAuth: true },
  { name: 'support', label: '고객센터', icon: 'support_agent', to: '/support' },
];
