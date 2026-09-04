import { ROLES } from '@/access/roles';

/**
 * 역할별 임시(데모) 로그인 계정
 * - 역할 버튼 클릭 시 API와 매칭하지 않고 아래 단일 역할 세션으로 로그인 (화면·권한 분리 검증용)
 * - 실 API 연동 테스트는 로그인 화면의 「API 사용자로 로그인」 사용
 */
export const DEMO_ROLE_ACCOUNTS = [
  {
    role: ROLES.LEARNER,
    label: '수강생',
    description: '강좌 검색·수강·학습관리',
    color: 'primary',
    user: {
      id: 'demo-learner',
      name: '가나다',
      email: 'learner@dabaeum.kr',
      phone: '010-1111-2222',
      status: 'ACTIVE',
      roles: [ROLES.LEARNER],
    },
  },
  {
    role: ROLES.INSTRUCTOR,
    label: '강사',
    description: '강의 등록·수강/출결 운영',
    color: 'teal',
    user: {
      id: 'demo-instructor',
      name: '강사입니다',
      email: 'instructor@dabaeum.kr',
      phone: '010-3333-4444',
      status: 'ACTIVE',
      roles: [ROLES.INSTRUCTOR],
    },
  },
  {
    role: ROLES.INSTITUTION_ADMIN,
    label: '기관 관리자',
    description: '기관 강사·강의 관리',
    color: 'deep-purple',
    user: {
      id: 'demo-institution-admin',
      name: '기관관리자',
      email: 'institution@dabaeum.kr',
      phone: '010-5555-6666',
      status: 'ACTIVE',
      roles: [ROLES.INSTITUTION_ADMIN],
    },
  },
  {
    role: ROLES.PLATFORM_ADMIN,
    label: '사이트 관리자',
    description: '플랫폼 전체 운영',
    color: 'negative',
    user: {
      id: 'demo-platform-admin',
      name: '사이트관리자',
      email: 'admin@dabaeum.kr',
      phone: '010-7777-8888',
      status: 'ACTIVE',
      roles: [ROLES.PLATFORM_ADMIN],
    },
  },
];

export function isDemoUserId(userId) {
  return String(userId || '').startsWith('demo-');
}

export function findDemoAccount(role) {
  return DEMO_ROLE_ACCOUNTS.find((item) => item.role === role) || null;
}
