/** 인증 · 회원가입 (역할 공통) */
export const authRoutes = [
  {
    path: '/login',
    name: 'login',
    meta: { title: '로그인' },
    component: () => import('@/pages/LoginPage.vue'),
  },
  {
    path: '/signup',
    name: 'signup',
    meta: { title: '회원가입 - 역할 선택' },
    component: () => import('@/pages/auth/SignupPage.vue'),
  },
  {
    path: '/signup/form',
    name: 'signup-form',
    meta: { title: '회원가입 - 정보 입력' },
    component: () => import('@/pages/auth/SignupFormPage.vue'),
  },
  {
    path: '/signup/complete',
    name: 'signup-complete',
    meta: { title: '회원가입 - 가입 완료' },
    component: () => import('@/pages/auth/SignupCompletePage.vue'),
  },
];
