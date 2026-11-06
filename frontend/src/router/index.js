import { defineRouter } from '#q-app';
import { Notify } from 'quasar';
import {
  createMemoryHistory,
  createRouter,
  createWebHashHistory,
  createWebHistory,
} from 'vue-router';

import { canAccess } from '@/access/permissions';
import { homePathAfterLogin } from '@/access/roles';
import { useAuthStore } from '@/stores/auth-store';
import { loginRequiredRoute } from '@/utils/auth-redirect';

import routes from './routes';

export default defineRouter((/* { store, ssrContext } */) => {
  const createHistory = import.meta.env.QUASAR_SERVER
    ? createMemoryHistory
    : import.meta.env.QUASAR_VUE_ROUTER_MODE === 'history'
      ? createWebHistory
      : createWebHashHistory;

  const Router = createRouter({
    scrollBehavior: () => ({ left: 0, top: 0 }),
    routes,
    history: createHistory(import.meta.env.QUASAR_VUE_ROUTER_BASE),
  });

  Router.beforeEach(async (to, from) => {
    const auth = useAuthStore();

    // 저장된 세션이 있으면 역할 포함 프로필을 먼저 확보
    if (auth.token && auth.activeUserId && (auth.loadingProfile || !auth.profileReady)) {
      if (auth.loadingProfile) {
        await new Promise((resolve) => {
          const stop = setInterval(() => {
            if (!auth.loadingProfile) {
              clearInterval(stop);
              resolve();
            }
          }, 50);
          setTimeout(() => {
            clearInterval(stop);
            resolve();
          }, 8000);
        });
      } else {
        await auth.bootstrapSession();
      }
    }

    const decision = canAccess(to, {
      isLoggedIn: auth.isLoggedIn,
      roles: auth.userRoles,
    });

    if (decision.ok) {
      return true;
    }

    if (decision.reason === 'unauthenticated') {
      // 로그인 화면이 '로그인이 필요해요 · 처음이면 회원가입' 안내를 띄우게 need 표시를 붙인다
      return loginRequiredRoute(to.fullPath);
    }

    const needRoles = (decision.rule?.roles || []).join(', ') || '권한';
    Notify.create({
      type: 'negative',
      message: `접근 권한이 없습니다. 필요 역할: ${needRoles}`,
      position: 'top',
    });
    const fallback = homePathAfterLogin(auth.userRoles) || '/';
    if (from.name && from.fullPath !== to.fullPath) {
      return false;
    }
    return { path: fallback };
  });

  return Router;
});
