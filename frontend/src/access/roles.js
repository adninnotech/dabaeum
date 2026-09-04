/** API Role enum 과 동기화 */
export const ROLES = {
  LEARNER: 'LEARNER',
  INSTRUCTOR: 'INSTRUCTOR',
  INSTITUTION_ADMIN: 'INSTITUTION_ADMIN',
  PLATFORM_ADMIN: 'PLATFORM_ADMIN',
};

export const ALL_ROLES = Object.values(ROLES);

/** 사이트(플랫폼) 관리자 콘솔 진입 역할 */
export const ADMIN_ROLES = [ROLES.PLATFORM_ADMIN];

/** 기관 관리자 콘솔 진입 역할 */
export const INSTITUTION_ADMIN_ROLES = [ROLES.INSTITUTION_ADMIN, ROLES.PLATFORM_ADMIN];

/** 강사 콘솔 진입 역할 (관리자도 강사 UI 접근 가능) */
export const INSTRUCTOR_ROLES = [ROLES.INSTRUCTOR, ROLES.PLATFORM_ADMIN];

/** 본인 수강신청(SELF) 가능 역할 */
export const LEARNER_ROLES = [ROLES.LEARNER];

/** 레이아웃 currentRole → API Role */
export const LAYOUT_TO_API_ROLE = {
  learner: ROLES.LEARNER,
  instructor: ROLES.INSTRUCTOR,
  institution: ROLES.INSTITUTION_ADMIN,
  admin: ROLES.PLATFORM_ADMIN,
};

export const ROLE_HOME_PATHS = {
  [ROLES.LEARNER]: '/',
  [ROLES.INSTRUCTOR]: '/instructor',
  [ROLES.INSTITUTION_ADMIN]: '/institution',
  [ROLES.PLATFORM_ADMIN]: '/admin',
};

export const ROLE_LABELS = {
  [ROLES.LEARNER]: '학습자',
  [ROLES.INSTRUCTOR]: '강사',
  [ROLES.INSTITUTION_ADMIN]: '기관 관리자',
  [ROLES.PLATFORM_ADMIN]: '사이트 관리자',
};

export function homePathForRole(role) {
  return ROLE_HOME_PATHS[role] || '/';
}

/** API Role → AppRoleLayout currentRole prop */
export const ROLE_LAYOUT_KEYS = {
  [ROLES.LEARNER]: 'learner',
  [ROLES.INSTRUCTOR]: 'instructor',
  [ROLES.INSTITUTION_ADMIN]: 'institution',
  [ROLES.PLATFORM_ADMIN]: 'admin',
};

export function layoutKeyForRole(role) {
  return ROLE_LAYOUT_KEYS[role] || 'learner';
}

export function labelForRole(role) {
  return ROLE_LABELS[role] || role;
}

/** 계정 메뉴 운영 콘솔 아이콘 */
export const ROLE_CONSOLE_ICONS = {
  [ROLES.PLATFORM_ADMIN]: 'admin_panel_settings',
  [ROLES.INSTITUTION_ADMIN]: 'apartment',
  [ROLES.INSTRUCTOR]: 'school',
};

const CONSOLE_ROLE_ORDER = [ROLES.PLATFORM_ADMIN, ROLES.INSTITUTION_ADMIN, ROLES.INSTRUCTOR];

/** 보유 역할 중 현재 레이아웃과 다른 운영 콘솔 목록 */
export function listOwnedConsoleEntries(userRoles, currentLayoutKey) {
  const owned = new Set(Array.isArray(userRoles) ? userRoles.filter(Boolean) : []);
  return CONSOLE_ROLE_ORDER.filter(
    (role) => owned.has(role) && layoutKeyForRole(role) !== currentLayoutKey,
  ).map((role) => ({
    role,
    label: labelForRole(role),
    icon: ROLE_CONSOLE_ICONS[role] || 'dashboard',
  }));
}

/** 로그인 직후 이동 경로 (보유 역할 기준, 플랫폼 관리자 우선) */
export function homePathAfterLogin(userRoles, preferredRole) {
  const roles = Array.isArray(userRoles) ? userRoles.filter(Boolean) : [];
  if (preferredRole && roles.includes(preferredRole)) {
    return homePathForRole(preferredRole);
  }
  if (roles.includes(ROLES.PLATFORM_ADMIN)) return homePathForRole(ROLES.PLATFORM_ADMIN);
  if (roles.includes(ROLES.INSTITUTION_ADMIN)) return homePathForRole(ROLES.INSTITUTION_ADMIN);
  if (roles.includes(ROLES.INSTRUCTOR)) return homePathForRole(ROLES.INSTRUCTOR);
  return homePathForRole(ROLES.LEARNER);
}
