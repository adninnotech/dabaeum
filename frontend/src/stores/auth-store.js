import { defineStore, acceptHMRUpdate } from 'pinia';
import { LocalStorage } from 'quasar';
import {
  canAccessAdmin,
  canAccessInstitutionAdmin,
  canAccessInstructor,
  canSelfEnroll,
  hasAnyRole,
  hasRole,
  isLearner,
  normalizeRoles,
} from '@/access/permissions';
import { homePathForRole, ROLES } from '@/access/roles';
import { AUTH_STORAGE_KEY } from '@/config/auth';
import { API_BEARER_TOKEN, DEFAULT_INSTITUTION_ID } from '@/config/api';
import { findDemoAccount, isDemoUserId, DEMO_ROLE_ACCOUNTS } from '@/config/demo-users';
import { loginLocalAccount } from '@/services/auth-api';
import {
  fetchAuthSession,
  getCurrentUser,
  getUser,
  listUserRoles,
  updateCurrentUser,
} from '@/services/user-api';

function findDemoByLoginId(loginId) {
  const key = String(loginId || '').trim().toLowerCase();
  if (!key) return null;
  return (
    DEMO_ROLE_ACCOUNTS.find((item) => {
      const email = String(item.user.email || '').toLowerCase();
      const id = String(item.user.id || '').toLowerCase();
      const label = String(item.label || '');
      return email === key || id === key || label === loginId;
    }) || null
  );
}

function buildDemoSession(user, role) {
  if (role === ROLES.INSTITUTION_ADMIN || role === ROLES.INSTRUCTOR) {
    return {
      userId: user.id,
      roles: [{ role, institutionId: DEFAULT_INSTITUTION_ID }],
    };
  }
  return {
    userId: user.id,
    roles: [{ role }],
  };
}

function resolveDemoAccount(selectedRole, userId, fallbackRole) {
  if (selectedRole) {
    const byRole = findDemoAccount(selectedRole);
    if (byRole) return byRole;
  }
  const byId = DEMO_ROLE_ACCOUNTS.find((item) => item?.user?.id === userId);
  if (byId) return byId;
  if (fallbackRole) {
    return findDemoAccount(fallbackRole);
  }
  return null;
}

function loadPersistedAuth() {
  const saved = LocalStorage.getItem(AUTH_STORAGE_KEY);
  if (!saved || typeof saved !== 'object') {
    return null;
  }
  return {
    token: saved.token || null,
    user: saved.user || null,
    activeUserId: saved.activeUserId || saved.user?.id || null,
    session: saved.session || null,
    selectedRole: saved.selectedRole || null,
  };
}

export const useAuthStore = defineStore('auth', {
  state: () => {
    const persisted = loadPersistedAuth();
    const hasSession = !!(persisted?.token && persisted?.user?.id && persisted?.activeUserId);

    return {
      token: hasSession ? persisted.token || API_BEARER_TOKEN : null,
      activeUserId: hasSession ? persisted.activeUserId : null,
      user: hasSession
        ? {
            id: persisted.activeUserId || persisted.user.id,
            name: persisted.user.name || '로딩중...',
            email: persisted.user.email || '',
            phone: persisted.user.phone || '',
            birthDate: persisted.user.birthDate || null,
            status: persisted.user.status || 'ACTIVE',
            roles: Array.isArray(persisted.user.roles) ? [...persisted.user.roles] : [],
            withdrawnAt: persisted.user.withdrawnAt || null,
            createdAt: persisted.user.createdAt || null,
            updatedAt: persisted.user.updatedAt || null,
          }
        : null,
      loginError: '',
      session: hasSession ? persisted.session || null : null,
      selectedRole: hasSession ? persisted.selectedRole || null : null,
      loadingProfile: false,
      profileError: '',
    };
  },

  getters: {
    isLoggedIn: (state) => !!state.token && !!state.user?.id && !!state.activeUserId,
    displayName: (state) => {
      const name = state.user?.name || '';
      if (!name || name === '로딩중...' || name === 'API 연결 실패') {
        return state.loadingProfile ? '불러오는 중' : name || '사용자';
      }
      return name;
    },
    avatarLetter: (state) => {
      const name = state.user?.name || '';
      if (!name || name === '로딩중...' || name === 'API 연결 실패') return '?';
      return name.charAt(0);
    },
    userId: (state) => {
      if (!state.token || !state.activeUserId) return '';
      return state.activeUserId;
    },
    userEmail: (state) => state.user?.email || '',
    userPhone: (state) => state.user?.phone || '',
    userStatus: (state) => state.user?.status || '',
    userRoles: (state) => state.user?.roles || [],
    uniqueRoles: (state) => {
      const roles = state.user?.roles || [];
      return [...new Set(roles.filter(Boolean))];
    },
    primaryRole: (state) => {
      const roles = state.user?.roles || [];
      return state.selectedRole && roles.includes(state.selectedRole) ? state.selectedRole : roles[0] || '';
    },
    /** 관리자 콘솔 진입 가능 여부 (PLATFORM_ADMIN) */
    isAdmin: (state) => canAccessAdmin(state.user?.roles || []),
    /** 기관 관리자 콘솔 진입 가능 여부 */
    isInstitutionAdmin: (state) => canAccessInstitutionAdmin(state.user?.roles || []),
    /** 강사 콘솔 진입 가능 여부 (INSTRUCTOR / PLATFORM_ADMIN) */
    isInstructor: (state) => canAccessInstructor(state.user?.roles || []),
    /** 학습자 역할 보유 */
    isLearner: (state) => isLearner(state.user?.roles || []),
    isDemoSession: (state) => isDemoUserId(state.activeUserId),
    /** 본인 수강신청 가능 (LEARNER만) */
    canEnroll: (state) => canSelfEnroll(state.user?.roles || []),
    /** 세션 역할에 붙은 기관 ID (강사·기관 관리자) */
    primaryInstitutionId: (state) => {
      const roles = state.session?.roles;
      if (!Array.isArray(roles)) return null;
      const found = roles.find((item) => item && item.institutionId);
      return found?.institutionId || null;
    },
    profileReady: (state) =>
      !!state.token &&
      !!state.activeUserId &&
      !!state.user?.id &&
      !!state.user?.name &&
      state.user.name !== '로딩중...' &&
      !state.loadingProfile,
  },

  actions: {
    hasRole(role) {
      return hasRole(this.userRoles, role);
    },
    hasAnyRole(roles) {
      return hasAnyRole(this.userRoles, roles);
    },

    syncSelectedRole(preferred) {
      const roles = this.uniqueRoles;
      if (preferred && roles.includes(preferred)) {
        this.selectedRole = preferred;
        return;
      }
      if (this.selectedRole && roles.includes(this.selectedRole)) {
        return;
      }
      this.selectedRole = roles[0] || null;
    },

    selectRole(role) {
      if (!this.uniqueRoles.includes(role)) return null;
      this.selectedRole = role;
      this.persist();
      return homePathForRole(role);
    },

    persist() {
      if (this.token && this.user && this.activeUserId) {
        LocalStorage.set(AUTH_STORAGE_KEY, {
          token: this.token,
          user: this.user,
          activeUserId: this.activeUserId,
          session: this.session,
          selectedRole: this.selectedRole,
        });
      } else {
        LocalStorage.remove(AUTH_STORAGE_KEY);
      }
    },

    applyLocalUser(user, preferredRole) {
      this.token = API_BEARER_TOKEN;
      this.activeUserId = user.id;
      this.user = {
        id: user.id,
        name: user.name || '사용자',
        email: user.email || '',
        phone: user.phone || '',
        birthDate: user.birthDate || null,
        status: user.status || 'ACTIVE',
        withdrawnAt: user.withdrawnAt || null,
        createdAt: user.createdAt || null,
        updatedAt: user.updatedAt || null,
        roles: Array.isArray(user.roles) ? [...user.roles] : [],
      };
      const role = preferredRole || user.roles?.[0] || null;
      this.session = role && isDemoUserId(user.id) ? buildDemoSession(user, role) : null;
      this.syncSelectedRole(preferredRole || role);
      this.profileError = '';
      this.persist();
    },

    async bootstrapSession() {
      this.loadingProfile = true;
      this.profileError = '';
      try {
        const targetUserId = this.activeUserId;
        if (!targetUserId) {
          this.profileError = '로그인된 userId가 없습니다.';
          this.token = null;
          this.user = null;
          this.persist();
          return false;
        }

        if (isDemoUserId(targetUserId)) {
          const demo = resolveDemoAccount(
            this.selectedRole,
            targetUserId,
            this.user?.roles?.[0],
          );
          if (demo?.user) {
            this.applyLocalUser(demo.user, demo.role);
            return true;
          }
          if (this.user?.id === targetUserId) {
            this.token = API_BEARER_TOKEN;
            this.persist();
            return true;
          }
        }

        if (!this.token) {
          this.token = API_BEARER_TOKEN;
        }

        let session = null;
        try {
          session = await fetchAuthSession();
          this.session = session;
          if (session?.userId) {
            this.activeUserId = session.userId;
          }
        } catch {
          this.session = this.session || null;
        }

        const resolvedId = this.activeUserId || targetUserId;
        let user = null;
        try {
          user = await getCurrentUser();
        } catch (meError) {
          try {
            user = await getUser(resolvedId);
          } catch {
            throw meError;
          }
        }

        const userId = user?.id || resolvedId;
        let roles = [];
        try {
          roles = normalizeRoles(await listUserRoles(userId));
        } catch {
          if (session?.userId === userId) {
            roles = normalizeRoles(session?.roles);
          }
        }

        this.activeUserId = userId;
        this.user = {
          id: userId,
          name: user?.name || '사용자',
          email: user?.email || '',
          phone: user?.phone || '',
          birthDate: user?.birthDate || null,
          status: user?.status || 'ACTIVE',
          withdrawnAt: user?.withdrawnAt || null,
          createdAt: user?.createdAt || null,
          updatedAt: user?.updatedAt || null,
          roles,
        };
        this.syncSelectedRole();
        this.persist();
        return true;
      } catch (error) {
        this.profileError = error.message || '사용자 정보를 불러오지 못했습니다.';
        this.token = null;
        this.user = null;
        this.activeUserId = null;
        this.session = null;
        this.persist();
        return false;
      } finally {
        this.loadingProfile = false;
      }
    },

    async loginAsUser(userId) {
      this.loginError = '';
      const id = String(userId || '').trim();
      if (!id) {
        this.loginError = '사용자를 선택해 주세요.';
        return false;
      }

      this.activeUserId = id;
      this.token = API_BEARER_TOKEN;
      const ok = await this.bootstrapSession();
      if (!ok) {
        this.loginError = this.profileError || '로그인에 실패했습니다.';
        return false;
      }
      return true;
    },

    /** 역할별 임시 로그인 (디자인 검증용 — API 역할 혼합 없이 단일 역할 세션) */
    async loginAsDemoRole(role) {
      this.loginError = '';
      const demo = findDemoAccount(role);
      if (!demo) {
        this.loginError = '지원하지 않는 역할입니다.';
        return false;
      }

      this.applyLocalUser(demo.user, role);
      return true;
    },

    async applyAuthToken(auth) {
      const data = auth?.accessToken ? auth : auth?.data || auth;
      const accessToken = data?.accessToken;
      const session = data?.session;
      if (!accessToken || !session?.userId) {
        this.loginError = '로그인 응답에 Access Token이 없습니다.';
        return false;
      }
      this.token = accessToken;
      this.session = session;
      this.activeUserId = session.userId;
      this.loginError = '';
      const ok = await this.bootstrapSession();
      if (!ok) {
        this.loginError = this.profileError || '세션을 불러오지 못했습니다.';
      }
      return ok;
    },

    async updateMyProfile(body) {
      const updated = await updateCurrentUser(body);
      if (updated && this.user) {
        this.user = {
          ...this.user,
          name: updated.name ?? this.user.name,
          email: updated.email ?? this.user.email,
          phone: updated.phone ?? this.user.phone,
          birthDate: updated.birthDate ?? this.user.birthDate,
        };
        this.persist();
      }
      return updated;
    },

    async login({ email, password, userId } = {}) {
      this.loginError = '';

      if (userId) {
        return this.loginAsUser(userId);
      }

      const normalizedEmail = String(email || '').trim();
      const normalizedPassword = String(password || '');

      if (!normalizedEmail) {
        this.loginError = '이메일을 입력해 주세요.';
        return false;
      }
      if (!normalizedPassword) {
        this.loginError = '비밀번호를 입력해 주세요.';
        return false;
      }

      if (normalizedEmail.includes('@')) {
        try {
          const tokenPayload = await loginLocalAccount({
            email: normalizedEmail,
            password: normalizedPassword,
          });
          return this.applyAuthToken(tokenPayload);
        } catch (error) {
          if (error.status && error.status !== 401 && error.status !== 404) {
            this.loginError = error.message || '로그인에 실패했습니다.';
            return false;
          }
        }
      }

      const byDemo = findDemoByLoginId(normalizedEmail);
      if (byDemo) {
        return this.loginAsDemoRole(byDemo.role);
      }

      this.loginError = '이메일/비밀번호가 올바르지 않습니다. 역할별 임시 로그인을 이용할 수 있습니다.';
      return false;
    },

    logout() {
      this.token = null;
      this.user = null;
      this.session = null;
      this.activeUserId = null;
      this.selectedRole = null;
      this.loginError = '';
      this.profileError = '';
      this.persist();
    },
  },
});

if (import.meta.hot) {
  import.meta.hot.accept(acceptHMRUpdate(useAuthStore, import.meta.hot));
}
