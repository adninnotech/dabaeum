import { defineRouter } from '#q-app';
import { Notify } from 'quasar';
import {
  createMemoryHistory,
  createRouter,
  createWebHashHistory,
  createWebHistory,
} from 'vue-router';

import { canAccess } from '@/access/permissions';
import { useAuthStore } from '@/stores/auth-store';

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

  Router.beforeEach(async (to, from, next) => {
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
      next();
      return;
    }

    if (decision.reason === 'unauthenticated') {
      next({
        path: '/login',
        query: { redirect: to.fullPath },
      });
      return;
    }

    const needRoles = (decision.rule?.roles || []).join(', ') || '권한';
    Notify.create({
      type: 'negative',
      message: `접근 권한이 없습니다. 필요 역할: ${needRoles}`,
      position: 'top',
    });
    next(from.name ? false : { path: '/' });
  });

  return Router;
});
