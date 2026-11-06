/**
 * 역할별 사이드바 메뉴.
 * 콘솔(강사·기관·관리자)은 `{ title?, items[] }` 그룹 배열 — title이 없으면 구분선 없이 최상단 묶음.
 * 학습자(LEARNER_*)는 평면 배열 그대로 사용한다.
 * to가 `/`인 항목은 공개 사이트로 나가므로 활성 표시하지 않는다.
 */
export const INSTRUCTOR_MENUS = [
  {
    items: [{ label: '대시보드', icon: 'o_dashboard', to: '/instructor', match: 'exact' }],
  },
  {
    title: '강의 관리',
    items: [
      {
        label: '강의 등록',
        icon: 'o_edit_note',
        to: '/instructor/courses/create',
        match: 'prefix',
        action: 'add',
      },
      {
        label: '내 강의 관리',
        icon: 'o_menu_book',
        to: '/instructor/courses',
        match: 'prefix',
        exclude: ['/instructor/courses/create'],
      },
    ],
  },
  {
    title: '수강 관리',
    items: [
      { label: '수강 신청 관리', icon: 'o_how_to_reg', to: '/instructor/enrollment-apps' },
      { label: '수강 현황 관리', icon: 'o_insights', to: '/instructor/enrollment-status' },
      { label: '출결 관리', icon: 'o_fact_check', to: '/instructor/attendance' }, // 피그마 외 — 유지
      { label: '이수 처리', icon: 'o_task_alt', to: '/instructor/completion' }, // 피그마 외 — 유지
    ],
  },
  {
    title: '운영 관리',
    items: [
      { label: '문의 관리', icon: 'o_forum', to: '/instructor/inquiries' },
      { label: '공지사항', icon: 'o_campaign', to: '/instructor/notices' },
      { label: '수강평 관리', icon: 'o_rate_review', to: '/instructor/reviews' }, // 피그마 외 — 유지
    ],
  },
  {
    title: '내 정보',
    items: [{ label: '내 정보 관리', icon: 'o_person', to: '/instructor/mypage' }],
  },
  // 콘솔 밖(공개 사이트)으로 나가는 길 — 대시보드 옆에 콘솔 메뉴처럼 섞여 있었다. 맨 아래에 나가는 표시와 함께
  {
    items: [
      { label: '학습자 사이트', icon: 'o_language', to: '/', match: 'none', action: 'leave' },
    ], // 프로필 메뉴와 같은 이름
  },
];

export const INSTITUTION_MENUS = [
  {
    items: [{ label: '대시보드', icon: 'o_dashboard', to: '/institution', match: 'exact' }],
  },
  {
    title: '강사 관리',
    items: [{ label: '강사 관리', icon: 'o_groups', to: '/institution/instructors' }],
  },
  {
    title: '강의 관리',
    items: [{ label: '강의 관리', icon: 'o_menu_book', to: '/institution/courses' }],
  },
  {
    title: '수강 관리',
    items: [
      { label: '수강 관리', icon: 'o_how_to_reg', to: '/institution/enrollments' },
      { label: '출결 관리', icon: 'o_fact_check', to: '/institution/attendance' },
      // 강사가 이수를 확정하면 기관이 수료증(VC)을 발급한다 — 기관 화면은 발급 전용
      { label: '수료증 발급', icon: 'o_workspace_premium', to: '/institution/completion' },
    ],
  },
  {
    title: '운영 관리',
    items: [
      { label: '문의 관리', icon: 'o_support_agent', to: '/institution/inquiries' },
      { label: '수강평 관리', icon: 'o_rate_review', to: '/institution/reviews' },
    ],
  },
  {
    title: '내 정보',
    items: [
      { label: '기관 정보 수정', icon: 'o_apartment', to: '/institution/profile' },
      { label: '내 정보 관리', icon: 'o_person', to: '/institution/mypage' },
    ],
  },
  // 콘솔 밖(공개 사이트)으로 나가는 길 — 대시보드 옆에 콘솔 메뉴처럼 섞여 있었다. 맨 아래에 나가는 표시와 함께
  {
    items: [
      { label: '학습자 사이트', icon: 'o_language', to: '/', match: 'none', action: 'leave' },
    ], // 프로필 메뉴와 같은 이름
  },
];

// 약관 및 정책(figma B-1, 프레임 없음)은 사이트 설정 > 약관 관리(/admin/terms)로 붙였다.
// '사용자 관리'(/admin/users)는 피그마에 없어 메뉴에서 뺐고 라우트만 유지한다.
export const ADMIN_MENUS = [
  {
    items: [
      { label: '대시보드', icon: 'o_dashboard', to: '/admin', match: 'exact' },
      // 대시보드·통계와 같은 '보는' 화면 — 운영 관리(문의·공지·배너) 끝에 묻혀 있었다
      { label: '블록체인 모니터링', icon: 'o_hub', to: '/admin/blockchain' }, // 피그마 외 — 유지
    ],
  },
  // 피그마 외 — 강좌·기관별 수강·배지(설계서 §9)와 사이트 전체 이용(백엔드 usage-statistics.md)
  {
    title: '통계',
    items: [
      { label: '수강·배지 통계', icon: 'o_bar_chart', to: '/admin/statistics', match: 'exact' },
      { label: '이용 통계', icon: 'o_insights', to: '/admin/statistics/usage' },
    ],
  },
  {
    title: '회원 관리',
    items: [
      { label: '학생 회원 관리', icon: 'o_groups', to: '/admin/students' },
      { label: '전체 강사 관리', icon: 'o_school', to: '/admin/instructors' },
      { label: '기관 관리', icon: 'o_apartment', to: '/admin/institutions' },
      { label: '가입 승인', icon: 'o_pending_actions', to: '/admin/approvals' }, // 피그마 외 — 유지
    ],
  },
  {
    title: '강의 관리',
    items: [
      { label: '전체 강의 관리', icon: 'o_menu_book', to: '/admin/courses' },
      // 피그마 B-9 카테고리 관리를 표준 강좌 분류(설계서 §12)로 대체
      { label: '강좌 분류 관리', icon: 'o_category', to: '/admin/classifications' },
    ],
  },
  {
    title: '수강 관리',
    items: [
      { label: '수강 신청 관리', icon: 'o_how_to_reg', to: '/admin/enrollments' },
      { label: '수강 현황 관리', icon: 'o_insights', to: '/admin/enrollment-status' },
      { label: '출결 관리', icon: 'o_fact_check', to: '/admin/attendance' }, // 피그마 외 — 유지(화면 제목과 같은 이름)
      { label: '이수 처리', icon: 'o_task_alt', to: '/admin/completion' }, // 피그마 외 — 유지
      { label: '수료증 관리', icon: 'o_workspace_premium', to: '/admin/credentials' }, // 피그마 외 — 발급 현황·다시 발급·폐기·재발급을 하는 화면이라 이름을 맞춤
      { label: '학습배지 현황', icon: 'o_verified', to: '/admin/badges' }, // 발주처 학습배지 관리 — 설계서 §11.4
      { label: '학습배지 관리', icon: 'o_military_tech', to: '/admin/badge-definitions' }, // 피그마 외 — 설계서 §11
    ],
  },
  {
    title: '운영 관리',
    items: [
      { label: '문의 관리', icon: 'o_forum', to: '/admin/inquiries' },
      { label: '공지사항 관리', icon: 'o_campaign', to: '/admin/notices' },
      { label: 'FAQ 관리', icon: 'o_quiz', to: '/admin/faqs' }, // 피그마 외 — 고객센터 FAQ
      { label: '배너 관리', icon: 'o_view_carousel', to: '/admin/banners' },
    ],
  },
  {
    // 피그마 외 — 설계서 §7 CMS
    title: '홈페이지 관리',
    items: [
      { label: '메뉴 관리', icon: 'o_menu', to: '/admin/menus' },
      { label: '콘텐츠 관리', icon: 'o_article', to: '/admin/content-pages' },
    ],
  },
  {
    title: '사이트 설정',
    items: [
      { label: '기관 정보 관리', icon: 'o_apartment', to: '/admin/institution' }, // 피그마 외 — 유지
      { label: '약관 관리', icon: 'o_gavel', to: '/admin/terms' }, // 피그마 외 — 약관 및 정책
      { label: '시스템 관리', icon: 'o_settings', to: '/admin/system' }, // 피그마 외 — 유지
      { label: '관리자 계정 관리', icon: 'o_admin_panel_settings', to: '/admin/administrators' }, // 피그마 외 — 설계서 §10
      { label: '관리자 접속기록', icon: 'o_manage_search', to: '/admin/access-logs' }, // 피그마 외 — 설계서 §10
    ],
  },
  {
    title: '내 정보',
    items: [{ label: '내 정보 관리', icon: 'o_person', to: '/admin/mypage' }],
  },
  // 콘솔 밖(공개 사이트)으로 나가는 길 — 대시보드 옆에 콘솔 메뉴처럼 섞여 있었다. 맨 아래에 나가는 표시와 함께
  {
    items: [
      { label: '학습자 사이트', icon: 'o_language', to: '/', match: 'none', action: 'leave' },
    ], // 프로필 메뉴와 같은 이름
  },
];

export const LEARNER_ACCOUNT_MENUS = [
  { label: '마이페이지', icon: 'person', to: '/mypage', match: 'exact' },
  { label: '나의 강좌', icon: 'school', to: '/learning', match: 'exact', requiresLearner: true },
  { label: '수강 신청', icon: 'assignment', to: '/enrollment', requiresLearner: true },
  { label: '수료증/배지', icon: 'workspace_premium', to: '/wallet', requiresLearner: true },
  { label: '배지 도감', icon: 'military_tech', to: '/badges' },
  { label: '학습 포트폴리오', icon: 'o_folder_shared', to: '/wallet/portfolio' },
  {
    label: '관심 강좌',
    icon: 'favorite_border',
    to: '/learning?tab=interests',
    requiresLearner: true,
  },
  { label: '나의 수강평', icon: 'rate_review', to: '/learning?tab=reviews', requiresLearner: true },
  {
    label: '나의 문의',
    icon: 'help_outline',
    to: '/learning?tab=inquiries',
    requiresLearner: true,
  },
  { label: '강사 신청', icon: 'badge', to: '/mypage/instructor-apply' },
  { label: '기관 등록 신청', icon: 'o_apartment', to: '/mypage/institution-apply' },
];

export const LEARNER_MAIN_MENUS = [
  { name: 'courses', label: '강좌찾기', icon: 'search', to: '/courses', match: 'prefix' },
  {
    name: 'learning',
    label: '학습관리',
    icon: 'school',
    to: '/learning',
    requiresAuth: true,
    requiresLearner: true,
  },
  {
    name: 'wallet',
    label: '학습지갑',
    icon: 'workspace_premium',
    to: '/wallet',
    requiresAuth: true,
    requiresLearner: true,
  },
  { name: 'mypage', label: '마이페이지', icon: 'person', to: '/mypage', requiresAuth: true },
  { name: 'support', label: '고객센터', icon: 'support_agent', to: '/support' },
];
