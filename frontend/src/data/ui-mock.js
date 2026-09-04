/** UI 디자인용 목 데이터 (API 미구현 기능) */
export const MOCK_INTEREST_COURSES = [
  {
    id: 'interest-1',
    courseId: 'c-mock-1',
    title: '스타트업을 위한 비즈니스 모델 설계',
    institution: '포천시 평생학습관',
    instructor: '김지수 강사',
    category: 'IT·디지털',
    delivery: '하이브리드',
    period: '2026.09.02(수) ~ 2026.11.21(금)',
    schedule: '매주 수 10:00 ~ 12:00',
    fee: '수강료 40,000원 · 재료비 없음',
    tags: ['#데이터', '#분석', '#엑셀'],
    image: 'https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=640&q=80',
  },
  {
    id: 'interest-2',
    courseId: 'c-mock-2',
    title: '행복에센스, 보드해요!',
    institution: '소흘평생학습관',
    instructor: '이서연 강사',
    category: '문화예술',
    delivery: '오프라인',
    period: '2026.11.26 ~ 2027.01.24',
    schedule: '매주 목 14:00 - 16:00 (총 12회)',
    fee: '수강료 30,000원 · 재료비 5,000원',
    tags: ['#미술', '#취미'],
    image: 'https://images.unsplash.com/photo-1460661419201-fd4cecdf8a8b?w=640&q=80',
  },
];

export const MOCK_MY_COURSES = [
  {
    id: 'enr-1',
    courseId: 'c-mock-2',
    title: '행복에센스, 보드해요!',
    institution: '소흘평생학습관',
    status: '신청중',
    delivery: '온라인',
    period: '2026.11.26 ~ 2027.01.24',
    schedule: '매주 목 14:00 - 16:00 (총 12회)',
    appliedAt: '2026.08.20',
    image: 'https://images.unsplash.com/photo-1460661419201-fd4cecdf8a8b?w=640&q=80',
    action: '상세보기',
  },
  {
    id: 'enr-2',
    courseId: 'c-mock-1',
    title: '스타트업을 위한 비즈니스 모델 설계',
    institution: '포천시 평생학습관',
    status: '수강중',
    delivery: '하이브리드',
    period: '2026.05.01 ~ 2026.06.20',
    schedule: '매주 수 10:00 ~ 12:00 (총 8회)',
    appliedAt: '2026.04.10',
    image: 'https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=640&q=80',
    action: '강의실 입장',
  },
  {
    id: 'enr-3',
    courseId: 'c-mock-3',
    title: '생활 속 데이터 리터러시',
    institution: '다배움 온라인',
    status: '수강종료',
    delivery: '온라인',
    period: '2025.09.01 ~ 2025.10.20',
    schedule: '매주 월 19:00 ~ 21:00 (총 8회)',
    appliedAt: '2025.08.15',
    image: 'https://images.unsplash.com/photo-1503676260728-1c00da094a0b?w=640&q=80',
    action: '수료증 보기',
  },
];

export const MOCK_LEARNING_SUMMARY = {
  applying: 2,
  inProgress: 1,
  finished: 3,
};

export const MOCK_REVIEWS = [
  {
    id: 'rev-1',
    courseTitle: '생활 속 데이터 리터러시',
    rating: 5,
    content: '실무에 바로 쓸 수 있는 내용이었습니다.',
    createdAt: '2025.10.22',
  },
];

export const MOCK_INQUIRIES = [
  {
    id: 'inq-1',
    title: '수강료 환불 문의',
    courseTitle: '행복에센스, 보드해요!',
    status: '답변대기',
    createdAt: '2026.08.21',
  },
];

export const MOCK_NOTICES = [
  { id: 'n1', title: '시스템 점검 안내 (5/10)', date: '2025.05.10' },
  { id: 'n2', title: '5월 신규 강좌 오픈 안내', date: '2025.05.08' },
  { id: 'n3', title: '개인정보 처리방침 개정 안내', date: '2025.05.05' },
  { id: 'n4', title: '강사 정산 일정 안내', date: '2025.05.02' },
  { id: 'n5', title: '기관 등록 절차 변경 공지', date: '2025.04.28' },
];

export const MOCK_PLATFORM_DASHBOARD = {
  kpis: [
    { label: '전체 회원', value: '2,186명' },
    { label: '강사 수', value: '528명', trend: 'up' },
    { label: '운영 기관 수', value: '54개' },
    { label: '전체 강의 수', value: '3,246개' },
    { label: '진행 중 강의', value: '2,123개' },
    { label: '누적 수강 수', value: '46,591건' },
  ],
  memberSummary: [
    { label: '일반 회원', value: '12,317 명' },
    { label: '강사', value: '438 명' },
    { label: '기관 관리자', value: '79 명' },
    { label: '탈퇴 회원 (누적)', value: '1,023 명' },
  ],
  approvalSummary: [
    { label: '강사 신청 대기', value: '23 건' },
    { label: '기관 등록 대기', value: '2 건' },
  ],
  courseSummary: [
    { label: '전체 강의', value: '3,234 개' },
    { label: '진행 중 강의', value: '2,123 개' },
    { label: '모집 중 강의', value: '825 개' },
    { label: '종료 강의', value: '251 개' },
  ],
  recentMembers: [
    { name: '학생1', at: '2025.05.12 10:24', role: '일반 회원' },
    { name: '학생1', at: '2025.05.12 09:11', role: '강사' },
    { name: '학생1', at: '2025.05.11 18:02', role: '기관 관리자' },
    { name: '학생1', at: '2025.05.11 14:40', role: '일반 회원' },
    { name: '학생1', at: '2025.05.10 11:05', role: '일반 회원' },
  ],
};

export const MOCK_INSTRUCTOR_COURSES = [
  {
    id: 'ic-1',
    title: '스타트업을 위한 비즈니스 모델 설계',
    institution: '포천시 평생학습관',
    instructor: '김지수 강사',
    category: 'IT·디지털',
    delivery: '온라인',
    recruitPeriod: '2025.05.01 ~ 2025.05.20',
    students: 42,
    coursePeriod: '2025.05.01 ~ 2025.06.20 (총 8회차)',
    status: '수강중',
    registeredAt: '2025.05.12 09:41',
    image: 'https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=640&q=80',
  },
  {
    id: 'ic-2',
    title: '생활 속 데이터 리터러시',
    institution: '다배움 온라인',
    instructor: '김지수 강사',
    category: 'IT·디지털',
    delivery: '하이브리드',
    recruitPeriod: '2025.03.01 ~ 2025.03.20',
    students: 28,
    coursePeriod: '2025.04.01 ~ 2025.05.20 (총 8회차)',
    status: '수강종료',
    registeredAt: '2025.03.10 11:20',
    image: 'https://images.unsplash.com/photo-1503676260728-1c00da094a0b?w=640&q=80',
  },
];

export const MOCK_INSTRUCTOR_STATS = {
  total: 12,
  recruiting: 2,
  ongoing: 3,
  ended: 7,
};

export const MOCK_INSTITUTION_INSTRUCTORS = [
  {
    id: 'inst-lec-1',
    name: '최민준',
    birth: '1990.03.15 (남)',
    email: 'leejunho@naver.com',
    phone: '010-1234-5678',
    joinedAt: '2025.04.01 10:24',
    status: '활성',
    lastLogin: '2025.05.12 09:41',
    courseCount: 3,
    institution: '포천시 평생학습관',
  },
  {
    id: 'inst-lec-2',
    name: '김지수',
    birth: '1988.07.02 (여)',
    email: 'jisoo@dabaeum.kr',
    phone: '010-2222-3333',
    joinedAt: '2025.03.12 09:10',
    status: '비활성',
    lastLogin: '2025.04.01 18:22',
    courseCount: 1,
    institution: '포천시 평생학습관',
  },
];

export const MOCK_INSTITUTION_STATS = {
  total: 512,
  active: 476,
  inactive: 50,
  withdrawn: 0,
};

export const MOCK_TERMS = `제1조 (목적)
본 약관은 다배움 서비스 이용과 관련하여 회사와 회원 간의 권리·의무를 규정합니다.

제2조 (개인정보 수집)
회원 가입 시 아이디, 이름, 이메일, 휴대전화 등을 수집하며 서비스 제공 목적 외로 이용하지 않습니다.`;
