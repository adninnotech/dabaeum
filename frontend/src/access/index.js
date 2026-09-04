export { ADMIN_ROLES, ALL_ROLES, INSTRUCTOR_ROLES, LEARNER_ROLES, ROLES } from '@/access/roles';
export {
  PAGE_ACCESS,
  PATH_ACCESS,
  canAccess,
  canAccessAdmin,
  canAccessInstructor,
  canSelfEnroll,
  hasAnyRole,
  hasRole,
  isInstructor,
  isLearner,
  isPlatformAdmin,
  normalizeRoles,
  resolveAccessRule,
} from '@/access/permissions';
