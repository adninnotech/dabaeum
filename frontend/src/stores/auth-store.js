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
import { useConsoleScopeStore } from '@/stores/console-scope-store';
import { API_BEARER_TOKEN, DEFAULT_INSTITUTION_ID, DEMO_AUTH_ENABLED } from '@/config/api';
import { findDemoAccount, isDemoUserId, DEMO_ROLE_ACCOUNTS } from '@/config/demo-users';
import {
  loginLocalAccount,
  loginWithDadaeguAccount,
  signupWithDadaeguAccount,
} from '@/services/auth-api';
import {
  fetchAuthSession,
  getCurrentUser,
  getUser,
  listMyInstitutions,
  listUserRoles,
  updateCurrentUser,
} from '@/services/user-api';

const ACCOUNT_LOCKED_MESSAGE =
  '로그인에 5번 실패해 계정이 30분 동안 잠겼습니다. 30분 뒤 다시 시도하거나 [비밀번호 찾기]로 재설정해 주세요.';

function findDemoByLoginId(loginId) {
  const key = String(loginId || '')
    .trim()
    .toLowerCase();
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
  // 데모를 뺀 빌드에서는 예전에 저장된 데모·대리 로그인 세션을 살리지 않는다 (토큰이 dev bearer 다)
  if (
    !DEMO_AUTH_ENABLED &&
    (saved.impersonating || isDemoUserId(saved.activeUserId || saved.user?.id))
  ) {
    LocalStorage.remove(AUTH_STORAGE_KEY);
    return null;
  }
  return {
    token: saved.token || null,
    user: saved.user || null,
    activeUserId: saved.activeUserId || saved.user?.id || null,
    session: saved.session || null,
    selectedRole: saved.selectedRole || null,
    impersonating: !!saved.impersonating,
  };
}

export const useAuthStore = defineStore('auth', {
  state: () => {
    const persisted = loadPersistedAuth();
    const hasSession = !!(persisted?.token && persisted?.user?.id && persisted?.activeUserId);

    return {
      token: hasSession ? persisted.token || API_BEARER_TOKEN : null,
      activeUserId: hasSession ? persisted.activeUserId : null,
      /* 저장본을 펼쳐서 쓴다. 필드를 다시 나열하면 persist() 가 저장한 신규 필드가 여기서 잘려
       * 나가고, bootstrapSession() 이 끝나기 전까지 화면이 "값 없음"으로 본다.
       * 세션 부트스트랩이 실패하면 그 상태로 남는다. */
      user: hasSession
        ? {
            ...persisted.user,
            id: persisted.activeUserId || persisted.user.id,
            name: persisted.user.name || '로딩중...',
            email: persisted.user.email || '',
            phone: persisted.user.phone || '',
            status: persisted.user.status || 'ACTIVE',
            roles: Array.isArray(persisted.user.roles) ? [...persisted.user.roles] : [],
          }
        : null,
      loginError: '',
      session: hasSession ? persisted.session || null : null,
      selectedRole: hasSession ? persisted.selectedRole || null : null,
      impersonating: hasSession ? !!persisted.impersonating : false,
      loadingProfile: false,
      profileError: '',
      // 소속 기관 — 강좌 생성 시 institutionId 출처 (plan-2 Task 6). 정상 로그인 경로에서만 채운다
      myInstitutions: [],
      /* 다대구 첫 로그인의 1회용 가입 토큰 {registrationToken, expiresAt, maskedName}.
       * 10분짜리 자격이라 persist() 하지 않는다 — 새로고침하면 다대구 로그인부터 다시 한다 */
      pendingDadaeguRegistration: null,
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
      return state.selectedRole && roles.includes(state.selectedRole)
        ? state.selectedRole
        : roles[0] || '';
    },
    /** 관리자 콘솔 진입 가능 여부 (PLATFORM_ADMIN) */
    isAdmin: (state) => canAccessAdmin(state.user?.roles || []),
    /** 기관 관리자 콘솔 진입 가능 여부 */
    isInstitutionAdmin: (state) => canAccessInstitutionAdmin(state.user?.roles || []),
    /** 강사 콘솔 진입 가능 여부 (INSTRUCTOR / PLATFORM_ADMIN) */
    isInstructor: (state) => canAccessInstructor(state.user?.roles || []),
    /** 학습자 역할 보유 */
    isLearner: (state) => isLearner(state.user?.roles || []),
    isDemoSession: (state) => DEMO_AUTH_ENABLED && isDemoUserId(state.activeUserId),
    /** 본인 수강신청 가능 (LEARNER만) */
    canEnroll: (state) => canSelfEnroll(state.user?.roles || []),
    /** 소속 기관 ID (강사·기관 관리자) — 서버가 준 소속 기관이 정본, 없으면 세션 역할 기반 폴백 */
    primaryInstitutionId: (state) => {
      const fromServer = state.myInstitutions?.[0]?.institutionId;
      if (fromServer) return fromServer;
      // (기존 로직 — demo 로그인·세션 실패 경로가 쓴다. 지우지 않는다)
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
          impersonating: this.impersonating,
        });
      } else {
        LocalStorage.remove(AUTH_STORAGE_KEY);
      }
    },

    applyLocalUser(user, preferredRole) {
      if (!DEMO_AUTH_ENABLED) return;
      this.impersonating = false;
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
        career: user.career || null,
        introduction: user.introduction || null,
        profileImageId: user.profileImageId || null,
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

        if (DEMO_AUTH_ENABLED && isDemoUserId(targetUserId)) {
          const demo = resolveDemoAccount(this.selectedRole, targetUserId, this.user?.roles?.[0]);
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

        if (DEMO_AUTH_ENABLED && this.impersonating) {
          const userId = this.activeUserId || targetUserId;
          const user = await getUser(userId);
          const roles = normalizeRoles(await listUserRoles(userId));
          this.activeUserId = user?.id || userId;
          // 응답을 펼쳐 쓴다 — 필드를 나열하면 신규 응답 필드가 잘린다(state()·정상 경로와 같은 규칙)
          this.user = {
            ...(user || {}),
            id: this.activeUserId,
            name: user?.name || '사용자',
            email: user?.email || '',
            phone: user?.phone || '',
            status: user?.status || 'ACTIVE',
            roles,
          };
          this.session = null;
          this.syncSelectedRole();
          this.persist();
          return true;
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
        // 토큰이 만료돼 401 이면 api-client 가 이미 로그아웃했다 — 비운 토큰(dev 는 dev bearer)으로 이어서 사용자를 채우지 않는다
        if (!this.token) return false;

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
        // 자기 roles는 /auth/session이 준다. /users/{id}/roles는 관리자 전용이라
        // impersonate·세션 부재 때만 fallback (학습자가 먼저 부르면 매번 403)
        let roles = session?.userId === userId ? normalizeRoles(session?.roles) : [];
        if (!roles.length) {
          try {
            roles = normalizeRoles(await listUserRoles(userId));
          } catch {
            /* 관리자 전용 엔드포인트 — 학습자는 403이 정상 */
          }
        }

        this.activeUserId = userId;
        /* 필드를 명시적으로 나열한다. 새 응답 필드를 여기 안 넣으면 화면이 "값 없음"으로 본다 —
         * 저장은 되는데 새로고침하면 사라지는 증상이 된다(성별·지역·알림 설정에서 실제로 났다). */
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
          gender: user?.gender ?? null,
          // 관심 분야(강좌 분류 1단계 코드 0~3개)와 그 선택 동의 — 학습자만 쓴다(#142)
          interestCodes: Array.isArray(user?.interestCodes) ? user.interestCodes : [],
          interestAgreed: user?.interestAgreed === true,
          career: user?.career ?? null,
          introduction: user?.introduction ?? null,
          profileImageId: user?.profileImageId ?? null,
          lastLoginAt: user?.lastLoginAt ?? null,
          roles,
        };

        // 소속 기관 — 강좌 생성 시 institutionId 출처. 실패해도 세션은 유지한다
        try {
          this.myInstitutions = (await listMyInstitutions()) || [];
        } catch {
          this.myInstitutions = [];
        }

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
      if (!DEMO_AUTH_ENABLED) {
        this.loginError = '지원하지 않는 로그인 방식입니다.';
        return false;
      }
      const id = String(userId || '').trim();
      if (!id) {
        this.loginError = '사용자를 선택해 주세요.';
        return false;
      }

      this.impersonating = true;
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
      if (!DEMO_AUTH_ENABLED) {
        this.loginError = '지원하지 않는 로그인 방식입니다.';
        return false;
      }
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
      this.impersonating = false;
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
          career: updated.career ?? this.user.career,
          introduction: updated.introduction ?? this.user.introduction,
          profileImageId: updated.profileImageId ?? this.user.profileImageId,
          gender: updated.gender ?? this.user.gender,
          interestCodes: Array.isArray(updated.interestCodes) ? updated.interestCodes : this.user.interestCodes,
          interestAgreed: typeof updated.interestAgreed === 'boolean' ? updated.interestAgreed : this.user.interestAgreed,
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
          // 5회 연속 실패 → 30분 잠금. 잠긴 동안은 비밀번호가 맞아도 같은 401 이다 — 데모 폴백으로 넘기지 않는다
          if (error.status === 401 && error.payload?.code === 'ACCOUNT_LOCKED') {
            this.loginError = ACCOUNT_LOCKED_MESSAGE;
            return false;
          }
          if (error.status && error.status !== 401 && error.status !== 404) {
            this.loginError = error.message || '로그인에 실패했습니다.';
            return false;
          }
        }
      }

      if (!DEMO_AUTH_ENABLED) {
        this.loginError = '이메일/비밀번호가 올바르지 않습니다.';
        return false;
      }

      const byDemo = findDemoByLoginId(normalizedEmail);
      if (byDemo) {
        return this.loginAsDemoRole(byDemo.role);
      }

      this.loginError =
        '이메일/비밀번호가 올바르지 않습니다. 역할별 임시 로그인을 이용할 수 있습니다.';
      return false;
    },

    /** 다대구 DID 로그인 — body 는 SDK 콜백의 암호문 필드 (toDadaeguLoginBody).
     * 'LOGGED_IN' | 'REGISTRATION_REQUIRED' | 'FAILED'. 가입이 필요하면 가입 토큰을 잡아 두고
     * 화면이 /signup/dadaegu 로 보낸다 (계정·액세스 토큰은 아직 없다) */
    async loginWithDadaegu(body) {
      this.loginError = '';
      this.pendingDadaeguRegistration = null;
      try {
        const data = await loginWithDadaeguAccount(body);
        if (data?.registrationRequired === true) {
          if (!data.registrationToken) {
            this.loginError = '다대구 가입 정보를 받지 못했습니다. 다시 시도해 주세요.';
            return 'FAILED';
          }
          this.pendingDadaeguRegistration = {
            registrationToken: data.registrationToken,
            expiresAt: data.expiresAt || null,
            maskedName: data.maskedName || '',
          };
          return 'REGISTRATION_REQUIRED';
        }
        return (await this.applyAuthToken(data)) ? 'LOGGED_IN' : 'FAILED';
      } catch (error) {
        // 서버는 복호화 실패와 비활성 사용자를 구분하지 않고 401 하나로 준다
        this.loginError =
          error.status === 401
            ? '다대구 인증에 실패했습니다. 이용이 제한된 계정일 수 있습니다.'
            : error.message || '다대구 로그인에 실패했습니다.';
        return 'FAILED';
      }
    },

    /** 다대구 첫 로그인 가입 완료. 성공·401(만료·재사용)·409(이미 가입)면 토큰을 버린다.
     * 그 밖의 실패(422·503·네트워크)는 토큰을 남겨 다시 누를 수 있게 한다. 서버는 소비·가입을 한 트랜잭션으로
     * 처리해 실패하면 used_at 까지 롤백한다 — 토큰은 10분 만료까지 유효하다 (백엔드 확인, 2026-09-28) */
    async signupWithDadaegu({ agreements, interestCodes, agreedToInterest }) {
      const pending = this.pendingDadaeguRegistration;
      if (!pending?.registrationToken) {
        const error = new Error('가입 시간이 지났습니다. 다대구 로그인부터 다시 해 주세요.');
        error.status = 401;
        throw error;
      }
      try {
        const tokenPayload = await signupWithDadaeguAccount({
          registrationToken: pending.registrationToken,
          agreements,
          interestCodes,
          agreedToInterest,
        });
        this.pendingDadaeguRegistration = null;
        return this.applyAuthToken(tokenPayload);
      } catch (error) {
        if (error.status === 401 || error.status === 409) this.pendingDadaeguRegistration = null;
        throw error;
      }
    },

    logout() {
      this.pendingDadaeguRegistration = null;
      this.token = null;
      this.user = null;
      this.session = null;
      this.activeUserId = null;
      this.selectedRole = null;
      this.impersonating = false;
      this.loginError = '';
      this.profileError = '';
      this.myInstitutions = [];
      // 다음 계정이 앞 계정이 고른 기관을 이어받지 않게
      useConsoleScopeStore().reset();
      this.persist();
    },
  },
});

if (import.meta.hot) {
  import.meta.hot.accept(acceptHMRUpdate(useAuthStore, import.meta.hot));
}
