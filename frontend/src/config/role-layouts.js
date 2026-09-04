import { labelForRole, ROLES } from '@/access/roles';
import { ADMIN_MENUS, INSTITUTION_MENUS, INSTRUCTOR_MENUS } from '@/config/role-menus.js';

/** AppRoleLayout에 전달하는 역할별 콘솔 설정 */
export const ROLE_LAYOUTS = {
  admin: {
    currentRole: 'admin',
    roleLabel: labelForRole(ROLES.PLATFORM_ADMIN),
    drawerTitle: labelForRole(ROLES.PLATFORM_ADMIN),
    drawerCaption: 'PLATFORM',
    brandTo: '/admin',
    menus: ADMIN_MENUS,
  },
  institution: {
    currentRole: 'institution',
    roleLabel: labelForRole(ROLES.INSTITUTION_ADMIN),
    drawerTitle: labelForRole(ROLES.INSTITUTION_ADMIN),
    drawerCaption: 'INSTITUTION',
    brandTo: '/institution',
    menus: INSTITUTION_MENUS,
  },
  instructor: {
    currentRole: 'instructor',
    roleLabel: labelForRole(ROLES.INSTRUCTOR),
    drawerTitle: labelForRole(ROLES.INSTRUCTOR),
    drawerCaption: 'INSTRUCTOR',
    brandTo: '/instructor',
    menus: INSTRUCTOR_MENUS,
  },
};
