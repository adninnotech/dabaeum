import { safeRedirect } from '@/utils/auth-redirect';

/* 학습자는 다대구로만 가입·로그인한다(#112). 강사·기관 관리자는 이메일로 따로 가입·로그인한다(#134) —
 * /login/staff → /signup/staff(역할 선택) → /signup/form(강사) · /signup/institution(기관 관리자).
 * 학습자 이메일 가입(/signup)은 없다 — 북마크·옛 링크로 오면 로그인으로 보내고 돌아갈 주소는 넘긴다 */
function toLogin(to) {
  const redirect = safeRedirect(to.query.redirect);
  return { path: '/login', query: redirect ? { redirect } : {} };
}

/** 인증 · 다대구 가입 (역할 공통) */
export const authRoutes = [
  {
    path: '/login',
    name: 'login',
    meta: { title: '로그인' },
    component: () => import('@/pages/LoginPage.vue'),
  },
  {
    path: '/login/staff',
    name: 'login-staff',
    meta: { title: '강사·기관 관리자 로그인' },
    component: () => import('@/pages/auth/StaffLoginPage.vue'),
  },
  { path: '/signup', redirect: toLogin },
  {
    path: '/signup/staff',
    name: 'signup',
    meta: { title: '회원가입 - 역할 선택' },
    component: () => import('@/pages/auth/SignupPage.vue'),
  },
  {
    path: '/signup/form',
    name: 'signup-form',
    meta: { title: '회원가입 - 강사 정보 입력' },
    component: () => import('@/pages/auth/SignupFormPage.vue'),
  },
  {
    path: '/signup/institution',
    name: 'signup-institution',
    meta: { title: '회원가입 - 기관 관리자 정보 입력' },
    component: () => import('@/pages/auth/InstitutionSignupPage.vue'),
  },
  {
    path: '/signup/dadaegu',
    name: 'signup-dadaegu',
    meta: { title: '회원가입 - 다대구 추가 정보' },
    component: () => import('@/pages/auth/DadaeguSignupPage.vue'),
  },
  {
    path: '/signup/complete',
    name: 'signup-complete',
    meta: { title: '회원가입 - 가입 완료' },
    component: () => import('@/pages/auth/SignupCompletePage.vue'),
  },
  {
    path: '/auth/password/reset',
    name: 'password-reset',
    meta: { title: '비밀번호 재설정' },
    component: () => import('@/pages/auth/PasswordResetPage.vue'),
  },
];
