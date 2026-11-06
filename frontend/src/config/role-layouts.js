import { ADMIN_MENUS, INSTITUTION_MENUS, INSTRUCTOR_MENUS } from '@/config/role-menus.js';

/** AppConsoleLayout에 전달하는 역할별 콘솔 설정.
 *  brandTo — 로고 '다배움'은 사이트 홈(/)으로 간다(콘솔 첫 화면은 메뉴의 '대시보드') */
export const ROLE_LAYOUTS = {
  admin: {
    currentRole: 'admin',
    brandTo: '/',
    menus: ADMIN_MENUS,
  },
  institution: {
    currentRole: 'institution',
    brandTo: '/',
    menus: INSTITUTION_MENUS,
    institutionScope: true,
  },
  instructor: {
    currentRole: 'instructor',
    brandTo: '/',
    menus: INSTRUCTOR_MENUS,
  },
};
