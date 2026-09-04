import { adminRoutes } from './admin.routes';
import { authRoutes } from './auth.routes';
import { institutionRoutes } from './institution.routes';
import { instructorRoutes } from './instructor.routes';
import { learnerRoutes } from './learner.routes';

/**
 * 역할별 라우트 묶음
 * - learner: 학습자/공개 (MainLayout)
 * - auth: 로그인·회원가입
 * - instructor: 강사
 * - institution: 기관 관리자
 * - admin: 사이트(플랫폼) 관리자
 */
const routes = [
  ...learnerRoutes,
  ...authRoutes,
  ...instructorRoutes,
  ...institutionRoutes,
  ...adminRoutes,
  {
    path: '/:catchAll(.*)*',
    name: 'not-found',
    meta: { title: '페이지를 찾을 수 없음' },
    component: () => import('@/pages/ErrorNotFound.vue'),
  },
];

export default routes;
