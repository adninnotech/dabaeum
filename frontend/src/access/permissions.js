import {
  ADMIN_ROLES,
  INSTITUTION_ADMIN_ROLES,
  INSTRUCTOR_ROLES,
  LEARNER_ROLES,
  ROLES,
} from '@/access/roles';

/**
 * 페이지(라우트 name)별 접근 규칙
 * - requiresAuth: 로그인 필요
 * - roles: 하나라도 보유하면 허용 (OR). 비어 있으면 역할 검사 없음
 */
export const PAGE_ACCESS = {
  // 학습자 (공개/선택적 인증)
  courses: { requiresAuth: false, roles: [] },
  'course-detail': { requiresAuth: false, roles: [] },
  support: { requiresAuth: false, roles: [] },
  login: { requiresAuth: false, roles: [] },

  // 학습자 전용 수강 기능
  enrollment: { requiresAuth: true, roles: [...LEARNER_ROLES] },
  'enrollment-detail': { requiresAuth: true, roles: [...LEARNER_ROLES] },
  'attendance-scan': { requiresAuth: true, roles: [...LEARNER_ROLES] },
  wallet: { requiresAuth: true, roles: [] },
  'credential-detail': { requiresAuth: true, roles: [] },
  mypage: { requiresAuth: true, roles: [] },
  learning: { requiresAuth: true, roles: [] },
  'learning-interests': { requiresAuth: true, roles: [] },
  'learning-inquiries': { requiresAuth: true, roles: [] },
  'learning-reviews': { requiresAuth: true, roles: [] },
  signup: { requiresAuth: false, roles: [] },
  'signup-form': { requiresAuth: false, roles: [] },
  'signup-complete': { requiresAuth: false, roles: [] },

  // 강사 — INSTRUCTOR (PLATFORM_ADMIN 포함)
  'instructor-dashboard': { requiresAuth: true, roles: [...INSTRUCTOR_ROLES] },
  'instructor-sessions': { requiresAuth: true, roles: [...INSTRUCTOR_ROLES] },
  'instructor-enrollments': { requiresAuth: true, roles: [...INSTRUCTOR_ROLES] },
  'instructor-attendance': { requiresAuth: true, roles: [...INSTRUCTOR_ROLES] },
  'instructor-completion': { requiresAuth: true, roles: [...INSTRUCTOR_ROLES] },
  'instructor-courses': { requiresAuth: true, roles: [...INSTRUCTOR_ROLES] },
  'instructor-course-create': { requiresAuth: true, roles: [...INSTRUCTOR_ROLES] },
  'instructor-enrollment-apps': { requiresAuth: true, roles: [...INSTRUCTOR_ROLES] },
  'instructor-enrollment-status': { requiresAuth: true, roles: [...INSTRUCTOR_ROLES] },
  'instructor-inquiries': { requiresAuth: true, roles: [...INSTRUCTOR_ROLES] },
  'instructor-notices': { requiresAuth: true, roles: [...INSTRUCTOR_ROLES] },
  'instructor-profile': { requiresAuth: true, roles: [...INSTRUCTOR_ROLES] },

  // 기관 관리자
  'institution-dashboard': { requiresAuth: true, roles: [...INSTITUTION_ADMIN_ROLES] },
  'institution-instructors': { requiresAuth: true, roles: [...INSTITUTION_ADMIN_ROLES] },
  'institution-courses': { requiresAuth: true, roles: [...INSTITUTION_ADMIN_ROLES] },
  'institution-enrollments': { requiresAuth: true, roles: [...INSTITUTION_ADMIN_ROLES] },
  'institution-inquiries': { requiresAuth: true, roles: [...INSTITUTION_ADMIN_ROLES] },

  // 관리자 — PLATFORM_ADMIN 전용
  'admin-dashboard': { requiresAuth: true, roles: [...ADMIN_ROLES] },
  'admin-blockchain': { requiresAuth: true, roles: [...ADMIN_ROLES] },
  'admin-courses': { requiresAuth: true, roles: [...ADMIN_ROLES] },
  'admin-sessions': { requiresAuth: true, roles: [...ADMIN_ROLES] },
  'admin-enrollments': { requiresAuth: true, roles: [...ADMIN_ROLES] },
  'admin-users': { requiresAuth: true, roles: [...ADMIN_ROLES] },
  'admin-students': { requiresAuth: true, roles: [...ADMIN_ROLES] },
  'admin-attendance': { requiresAuth: true, roles: [...ADMIN_ROLES] },
  'admin-completion': { requiresAuth: true, roles: [...ADMIN_ROLES] },
  'admin-credentials': { requiresAuth: true, roles: [...ADMIN_ROLES] },
  'admin-institution': { requiresAuth: true, roles: [...ADMIN_ROLES] },
  'admin-system': { requiresAuth: true, roles: [...ADMIN_ROLES] },
  'admin-notices': { requiresAuth: true, roles: [...ADMIN_ROLES] },
  'admin-approvals': { requiresAuth: true, roles: [...ADMIN_ROLES] },
};

/** path prefix 기반 기본 규칙 (name 매칭 실패 시) */
export const PATH_ACCESS = [
  {
    prefix: '/instructor',
    rule: { requiresAuth: true, roles: [...INSTRUCTOR_ROLES] },
  },
  {
    prefix: '/institution',
    rule: { requiresAuth: true, roles: [...INSTITUTION_ADMIN_ROLES] },
  },
  {
    prefix: '/admin',
    rule: { requiresAuth: true, roles: [...ADMIN_ROLES] },
  },
];

export function normalizeRoles(roles) {
  if (!Array.isArray(roles)) return [];
  return roles.map((item) => (typeof item === 'string' ? item : item?.role)).filter(Boolean);
}

export function hasAnyRole(userRoles, requiredRoles = []) {
  if (!requiredRoles.length) return true;
  const owned = new Set(normalizeRoles(userRoles));
  return requiredRoles.some((role) => owned.has(role));
}

export function hasRole(userRoles, role) {
  return hasAnyRole(userRoles, [role]);
}

export function isPlatformAdmin(userRoles) {
  return hasRole(userRoles, ROLES.PLATFORM_ADMIN);
}

export function isInstructor(userRoles) {
  return hasRole(userRoles, ROLES.INSTRUCTOR);
}

export function isLearner(userRoles) {
  return hasAnyRole(userRoles, LEARNER_ROLES);
}

/** 본인 수강신청(SELF) 가능 여부 */
export function canSelfEnroll(userRoles) {
  return isLearner(userRoles);
}

export function canAccessAdmin(userRoles) {
  return hasAnyRole(userRoles, ADMIN_ROLES);
}

export function canAccessInstitutionAdmin(userRoles) {
  return hasAnyRole(userRoles, INSTITUTION_ADMIN_ROLES);
}

export function canAccessInstructor(userRoles) {
  return hasAnyRole(userRoles, INSTRUCTOR_ROLES);
}

/**
 * 라우트에서 접근 규칙 해석
 * 우선순위: route.meta > PAGE_ACCESS[name] > PATH_ACCESS > 공개
 */
export function resolveAccessRule(route) {
  const matchedMeta = [...(route.matched || [])]
    .reverse()
    .find((record) => record.meta && (record.meta.requiresAuth != null || record.meta.roles));

  if (matchedMeta?.meta) {
    const meta = matchedMeta.meta;
    return {
      requiresAuth: !!meta.requiresAuth,
      roles: Array.isArray(meta.roles) ? meta.roles : [],
      source: 'meta',
    };
  }

  const byName = route.name ? PAGE_ACCESS[String(route.name)] : null;
  if (byName) {
    return {
      requiresAuth: !!byName.requiresAuth,
      roles: Array.isArray(byName.roles) ? byName.roles : [],
      source: 'page',
    };
  }

  const path = route.path || '';
  const byPath = PATH_ACCESS.find(
    (item) => path === item.prefix || path.startsWith(`${item.prefix}/`),
  );
  if (byPath) {
    return {
      requiresAuth: !!byPath.rule.requiresAuth,
      roles: Array.isArray(byPath.rule.roles) ? byPath.rule.roles : [],
      source: 'path',
    };
  }

  return { requiresAuth: false, roles: [], source: 'public' };
}

/**
 * @returns {{ ok: boolean, reason?: 'unauthenticated'|'forbidden', rule: object }}
 */
export function canAccess(route, { isLoggedIn = false, roles = [] } = {}) {
  const rule = resolveAccessRule(route);

  if (rule.requiresAuth && !isLoggedIn) {
    return { ok: false, reason: 'unauthenticated', rule };
  }

  if (rule.roles.length && !hasAnyRole(roles, rule.roles)) {
    return { ok: false, reason: 'forbidden', rule };
  }

  return { ok: true, rule };
}
