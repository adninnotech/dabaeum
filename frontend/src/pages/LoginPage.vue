<template>
  <!-- 회원가입과 같은 머리(로고 = 홈 링크) — 로그인 화면에만 없어 홈으로 돌아갈 길이 없었다 -->
  <AuthShell :show-aside="false" :show-stepper="false">
    <div class="login">
      <div class="login__wrap">
        <div class="login__main">
          <!-- 수강신청·학습관리 등 로그인이 필요한 곳에서 왔을 때(need=1) — 처음 온 사람도 다대구 로그인으로 바로 가입된다 -->
          <p v-if="loginRequired" class="login__notice" role="status">
            <q-icon name="info" size="18px" aria-hidden="true" />
            <span>
              로그인이 필요한 기능이에요. 처음이시면
              <strong class="login__notice-strong">다대구 로그인</strong>으로 바로 가입하고 이어서 할 수 있어요.
            </span>
          </p>
          <header class="login__header">
            <h1 class="login__title">
              배움이 더 쉬워지는 곳<br /><span class="login__title-point">다배움</span>과 함께하세요
            </h1>
            <p class="login__sub">로그인하여 더 많은 강좌와 학습 관리 기능을 사용해보세요</p>
          </header>

          <!-- 가입·로그인은 다대구로만(#112). 이메일 로그인은 기존 계정(운영자·강사·기관 관리자)이 다대구를 연결하는 동안
               ?local=1 주소(와 개발용 빌드)에서만 연다 -->
          <LocalLoginForm
            v-if="localLogin"
            v-model="loginId"
            :loading="loading"
            :error="loginError"
            @submit="submitLogin"
            @find-password="onFindPassword"
          />

          <div class="login__actions">
            <!-- 디자인: docs/images/ui_design/로그인 버튼/완성형_로그인_버튼.png. 폰에서도 숨기지 않는다 -->
            <button
              type="button"
              class="login__dadaegu"
              :disabled="loading || dadaeguLoading"
              @click="startDadaeguLogin"
            >
              <strong>다대구</strong> 로그인
            </button>
            <!-- 다대구 실패 문구(이용 제한 등) — 이메일 폼을 숨기면 폼 안 안내 자리가 없다 -->
            <p v-if="loginError && !localLogin" class="login__feedback" role="alert">{{ loginError }}</p>
            <!-- 따로 가입 화면이 없다 — 처음 보는 다대구 계정은 로그인하면 추가 가입(/signup/dadaegu)으로 이어진다 -->
            <p class="login__register">처음이신가요? 다대구로 로그인하면 바로 가입돼요.</p>
            <div class="login__app">
              <span class="login__app-text">다대구 앱이 없나요?</span>
              <div class="login__app-links">
                <a :href="iosInstallUrl" class="login__app-link" target="_blank" rel="noopener noreferrer">
                  App Store
                </a>
                <a :href="androidInstallUrl" class="login__app-link" target="_blank" rel="noopener noreferrer">
                  Google Play
                </a>
              </div>
            </div>
            <!-- 강사·기관 관리자는 이메일로 따로 가입·로그인한다(#134). 15px 회색 한 줄 링크라 찾기 어려웠다 —
                 '또는' 아래 폭 꽉 찬 버튼으로, 다대구 버튼 다음 무게(흰 바탕·남색 테두리) -->
            <p class="login__or" aria-hidden="true">또는</p>
            <router-link :to="staffLoginTo" class="login__staff-btn">
              <q-icon name="o_badge" size="22px" aria-hidden="true" />
              <span>강사·기관 관리자 로그인</span>
              <q-icon name="chevron_right" size="22px" class="login__staff-go" aria-hidden="true" />
            </router-link>
            <p class="login__staff-note">이메일로 가입한 강사·기관 관리자 계정으로 로그인해요.</p>
          </div>
        </div>

        <!-- 일반 가입이 빠져(#112) 버튼 하나만 남은 화면 — 처음 온 사람이 누르면 무엇이 이어지는지 미리 읽게 한다.
             DOM 은 로그인 다음(키보드·폰에서 버튼이 먼저), PC 에서만 왼쪽에 놓는다 -->
        <aside class="login__guide" aria-labelledby="login-guide-title">
          <h2 id="login-guide-title" class="login__guide-title">다대구 하나로 시작해요</h2>
          <p class="login__guide-sub">아이디와 비밀번호를 따로 만들지 않아요.</p>
          <ol class="login__steps" role="list">
            <li v-for="item in guideSteps" :key="item.no" class="login__step">
              <span class="login__step-no" aria-hidden="true">{{ item.no }}</span>
              <span class="login__step-text">
                <strong class="login__step-title">{{ item.title }}</strong>
                <span class="login__step-desc">{{ item.desc }}</span>
              </span>
            </li>
          </ol>
        </aside>
      </div>

      <!-- 개발용 로그인 수단. Figma에 없고 프로덕션 빌드에서 통째로 빠진다 -->
      <q-expansion-item
        v-if="isDev"
        dense
        class="login__dev"
        label="개발용 로그인 (역할별 · API 사용자)"
        header-class="text-grey-7"
      >
        <div class="q-pt-sm">
          <q-banner
            v-if="isDemoSession"
            dense
            class="bg-blue-1 text-primary q-mb-md rounded-borders"
          >
            데모 세션으로 로그인되어 있습니다. 역할 버튼을 다시 누르면 전환됩니다.
          </q-banner>

          <div class="text-subtitle2 text-weight-bold q-mb-sm">역할별 임시 로그인</div>
          <div class="row q-col-gutter-sm q-mb-lg">
            <div v-for="item in demoAccounts" :key="item.role" class="col-12 col-sm-6">
              <q-btn
                outline
                class="full-width role-btn"
                :color="item.color"
                :loading="loading && loadingRole === item.role"
                :disable="loading"
                @click="loginDemo(item.role)"
              >
                <div class="text-left full-width">
                  <div class="text-weight-bold">{{ item.label }}</div>
                  <div class="text-caption">{{ item.description }}</div>
                </div>
              </q-btn>
            </div>
          </div>

          <div class="row items-center justify-between q-mb-sm">
            <div class="text-caption">API 사용자 선택</div>
            <q-btn aria-label="사용자 목록 새로고침"
              flat
              dense
              color="primary"
              icon="refresh"
              :loading="loadingUsers"
              @click="loadUsers"
            />
          </div>
          <q-banner v-if="usersError" dense class="bg-red-1 text-negative q-mb-sm">
            {{ usersError }}
          </q-banner>
          <q-list bordered dense class="user-list rounded-borders">
            <q-inner-loading :showing="loadingUsers" />
            <q-item
              v-for="row in filteredUsers"
              :key="row.id"
              clickable
              :active="selectedUserId === row.id"
              active-class="bg-blue-1"
              @click="selectUser(row)"
            >
              <q-item-section>
                <q-item-label>{{ row.name || '-' }}</q-item-label>
                <q-item-label caption>{{ row.email || row.id }}</q-item-label>
                <div class="row q-gutter-xs">
                  <q-badge v-for="role in row.roles || []" :key="role" outline color="primary">
                    {{ role }}
                  </q-badge>
                </div>
              </q-item-section>
            </q-item>
          </q-list>
          <q-btn
            class="full-width q-mt-sm"
            outline
            color="primary"
            label="선택한 API 사용자로 로그인"
            :disable="!selectedUserId || loading"
            :loading="loading"
            @click="loginSelectedUser"
          />
        </div>
      </q-expansion-item>
    </div>

    <PasswordFindDialog v-model="resetOpen" :initial-email="loginId" />
  </AuthShell>
</template>

<script>
import { mapActions, mapState } from 'pinia';
import { Notify } from 'quasar';
import AuthShell from '@/components/auth/AuthShell.vue';
import LocalLoginForm from '@/components/auth/LocalLoginForm.vue';
import PasswordFindDialog from '@/components/auth/PasswordFindDialog.vue';
import { DEMO_AUTH_ENABLED } from '@/config/api';
import { isInterestInputEnabled } from '@/config/features';
import { DEMO_ROLE_ACCOUNTS } from '@/config/demo-users';
import { prepareDadaeguSdk, startDadaeguAuth, takeStashedDadaeguQuery } from '@/services/dadaegu-auth';
import { listUserRoles, listUsers } from '@/services/user-api';
import { normalizeRoles } from '@/access/permissions';
import { useAuthStore } from '@/stores/auth-store';
import { homePathAfterLogin, homePathForRole } from '@/access/roles';
import { toDadaeguLoginBody } from '@/utils/didlogin-loader';
import { DADAEGU_ANDROID_INSTALL_URL, DADAEGU_IOS_INSTALL_URL } from '@/config/dadaegu-app';

// SDK 콜백은 전역 함수 이름으로 넘긴다 (dadaegu-auth.js 참고)
const DADAEGU_CALLBACK_NAME = 'dabaeumDadaeguLoginCallback';

// 화면 왼쪽(폰은 아래) 안내 — 실제 흐름: 다대구 인증 → 처음이면 /signup/dadaegu(약관·관심 분야) → 수강신청·QR 출석
const GUIDE_STEPS = [
  { no: 1, title: '다대구 로그인', desc: '버튼을 누르고 다대구 앱에서 본인 인증을 해요.' },
  {
    no: 2,
    title: '처음 한 번만 가입',
    desc: isInterestInputEnabled()
      ? '약관에 동의하면 끝나요. 관심 분야는 골라도 되고 건너뛰어도 돼요.'
      : '약관에 동의하면 끝나요.',
  },
  { no: 3, title: '바로 수강신청', desc: '강좌를 찾아 신청하고, 출석은 다대구 QR로 해요.' },
];

export default {
  name: 'LoginPage',
  components: { AuthShell, LocalLoginForm, PasswordFindDialog },
  data() {
    return {
      loginId: '',
      loading: false,
      loadingRole: '',
      dadaeguLoading: false,
      loadingUsers: false,
      usersError: '',
      users: [],
      userKeyword: '',
      resetOpen: false,
      selectedUserId: '',
      demoAccounts: DEMO_AUTH_ENABLED ? DEMO_ROLE_ACCOUNTS : [],
      // 데모·대리 로그인 패널. 공개 빌드(DABAEUM_DEMO_AUTH 꺼짐)에서는 통째로 빠진다
      isDev: DEMO_AUTH_ENABLED,
      guideSteps: GUIDE_STEPS,
      iosInstallUrl: DADAEGU_IOS_INSTALL_URL,
      androidInstallUrl: DADAEGU_ANDROID_INSTALL_URL,
    };
  },
  computed: {
    ...mapState(useAuthStore, ['loginError', 'isDemoSession', 'userRoles', 'primaryRole']),
    loginRequired() {
      return this.$route.query.need === '1';
    },
    staffLoginTo() {
      const { redirect } = this.$route.query;
      return redirect ? { path: '/login/staff', query: { redirect: String(redirect) } } : '/login/staff';
    },
    // 이메일 로그인 — 다대구 연결 전 기존 계정용(?local=1). 개발용 빌드는 대리 로그인이 이 폼을 쓴다
    localLogin() {
      return this.isDev || this.$route.query.local === '1';
    },
    filteredUsers() {
      const q = this.userKeyword.trim().toLowerCase();
      if (!q) return this.users;
      return this.users.filter((u) => {
        const hay =
          `${u.name || ''} ${u.email || ''} ${u.id || ''} ${(u.roles || []).join(' ')}`.toLowerCase();
        return hay.includes(q);
      });
    },
  },
  mounted() {
    if (this.isDev) this.loadUsers();
    // 폰에서 앱 인증 뒤 이 페이지로 돌아오면 SDK 가 init 시점에 결과를 조회해 콜백을 부른다.
    // 그래서 버튼을 누르기 전에도 콜백을 걸고 SDK 를 초기화해 둔다.
    window[DADAEGU_CALLBACK_NAME] = (returnData) => this.finishDadaeguLogin(returnData);
    prepareDadaeguSdk().catch(() => {});
  },
  beforeUnmount() {
    if (window[DADAEGU_CALLBACK_NAME]) delete window[DADAEGU_CALLBACK_NAME];
  },
  methods: {
    ...mapActions(useAuthStore, ['login', 'loginAsUser', 'loginAsDemoRole', 'loginWithDadaegu']),
    async loadUsers() {
      this.loadingUsers = true;
      this.usersError = '';
      try {
        const page = await listUsers({ page: 0, size: 50 });
        const rows = Array.isArray(page?.data) ? page.data : Array.isArray(page) ? page : [];
        const withRoles = await Promise.all(
          rows.map(async (row) => {
            try {
              const roleRows = await listUserRoles(row.id);
              const roles = normalizeRoles(roleRows);
              return { ...row, roles };
            } catch {
              return { ...row, roles: [] };
            }
          }),
        );
        this.users = withRoles;
      } catch (error) {
        this.usersError = error.message || '사용자 목록을 불러오지 못했습니다.';
        this.users = [];
      } finally {
        this.loadingUsers = false;
      }
    },
    selectUser(row) {
      this.selectedUserId = row.id;
      this.loginId = row.email || row.name || row.id;
    },
    onFindPassword() {
      this.resetOpen = true;
    },
    async loginDemo(role) {
      this.loading = true;
      this.loadingRole = role;
      try {
        const ok = await this.loginAsDemoRole(role);
        if (ok) this.redirectAfterLogin(role);
      } finally {
        this.loading = false;
        this.loadingRole = '';
      }
    },
    async loginSelectedUser() {
      this.loading = true;
      try {
        const ok = await this.loginAsUser(this.selectedUserId);
        if (ok) this.redirectAfterLogin();
      } finally {
        this.loading = false;
      }
    },
    async submitLogin(password) {
      this.loading = true;
      try {
        const ok = await this.login({
          email: this.loginId,
          password,
          userId: this.selectedUserId || undefined,
        });
        if (ok) this.redirectAfterLogin();
      } finally {
        this.loading = false;
      }
    },
    async startDadaeguLogin() {
      this.dadaeguLoading = true;
      try {
        await startDadaeguAuth(DADAEGU_CALLBACK_NAME);
      } catch (error) {
        Notify.create({ type: 'negative', message: error.message || '다대구 로그인을 시작하지 못했습니다.' });
      } finally {
        this.dadaeguLoading = false;
      }
    },
    // SDK 콜백. 여기서 throw 하면 SDK 가 "콜백 함수에서 오류" alert 를 띄우므로 안에서 끝낸다
    async finishDadaeguLogin(returnData) {
      const body = toDadaeguLoginBody(returnData);
      if (!body) {
        Notify.create({ type: 'negative', message: '다대구 인증 결과를 읽지 못했습니다. 다시 시도해 주세요.' });
        return;
      }
      this.loading = true;
      try {
        const result = await this.loginWithDadaegu(body);
        if (result === 'REGISTRATION_REQUIRED') this.goDadaeguSignup();
        else if (result === 'LOGGED_IN') this.redirectAfterLogin();
      } catch (error) {
        Notify.create({ type: 'negative', message: error.message || '다대구 로그인에 실패했습니다.' });
      } finally {
        this.loading = false;
      }
    },
    // 처음 보는 다대구 DID — 계정이 없어 약관(과 관심 분야)을 받는 추가 가입으로 보낸다. redirect 는 가입 뒤로 넘긴다
    goDadaeguSignup() {
      const redirect = this.$route.query.redirect || takeStashedDadaeguQuery().get('redirect');
      this.$router.push({ path: '/signup/dadaegu', query: redirect ? { redirect: String(redirect) } : {} });
    },
    redirectAfterLogin(forcedRole) {
      // iPhone 다대구 복귀 때는 redirect 쿼리를 떼어 두었다 (dadaegu-auth.js stashQueryForIOSReturn)
      const redirect = this.$route.query.redirect || takeStashedDadaeguQuery().get('redirect');
      if (redirect) {
        this.$router.replace(String(redirect));
        return;
      }
      const path = forcedRole
        ? homePathForRole(forcedRole)
        : homePathAfterLogin(this.userRoles, this.primaryRole);
      this.$router.replace(path);
    },
  },
};
</script>

<style scoped>
/* PC: 셸 머리 80 + main 위 40 + 푸터 80 을 뺀 높이 안에서 세로 가운데 — 위에 몰려 아래가 비던 화면 */
.login {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: calc(100vh - 200px);
  padding: 0 40px 40px;
}

/* PC: 왼쪽 안내 면 + 오른쪽 로그인 450. 버튼 하나만 남아 위 1/3 만 차던 화면을 가로로 채운다 */
.login__wrap {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 450px);
  grid-template-areas: 'guide main';
  align-items: center;
  gap: 80px;
  width: 100%;
  max-width: 1040px;
  margin: 0 auto;
}

.login__main {
  grid-area: main;
  display: flex;
  flex-direction: column;
  gap: 32px;
  min-width: 0;
}

.login__guide {
  grid-area: guide;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 440px;
  padding: 48px 44px;
  border-radius: 16px;
  background: var(--db-accent-soft);
}

.login__guide-title {
  margin: 0;
  font-size: 28px;
  line-height: 36px;
  font-weight: 700;
  color: var(--db-text-title);
  word-break: keep-all;
}

.login__guide-sub {
  margin: 8px 0 0;
  font-size: 16px;
  line-height: 1.5;
  color: var(--db-text-body);
  word-break: keep-all;
}

.login__steps {
  display: flex;
  flex-direction: column;
  gap: 24px;
  margin: 36px 0 0;
  padding: 0;
  list-style: none;
}

.login__step {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.login__step-no {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--db-accent-fill);
  font-size: 15px;
  font-weight: 700;
  color: var(--db-on-color);
}

.login__step-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  padding-top: 4px;
}

.login__step-title {
  font-size: 17px;
  line-height: 24px;
  font-weight: 700;
  color: var(--db-text-title);
}

.login__step-desc {
  font-size: 15px;
  line-height: 1.5;
  color: var(--db-text-body);
  word-break: keep-all;
}

.login__app {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px 12px;
  margin-top: 4px;
}

.login__app-text {
  font-size: 14px;
  line-height: 20px;
  color: var(--db-text-sub);
  white-space: nowrap;
}

.login__app-links {
  display: flex;
  gap: 8px;
}

.login__app-link {
  display: inline-flex;
  align-items: center;
  height: 36px;
  padding: 0 14px;
  border: 1px solid var(--db-border);
  border-radius: 18px;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-title);
  text-decoration: none;
  white-space: nowrap;
}

.login__app-link:hover {
  background: var(--db-bg-subtle);
}

.login__app-link:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

/* 다대구 묶음과 강사·기관 입구 사이 — 양옆 선 가운데 '또는' */
.login__or {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 16px 0 4px;
  font-size: 15px;
  color: var(--db-text-sub);
}

.login__or::before,
.login__or::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--db-border);
}

/* 다대구(꽉 찬 빨강) 다음 무게 — 같은 높이·폭, 흰 바탕에 남색 테두리 */
.login__staff-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 53px;
  padding: 0 20px;
  border: 1.5px solid var(--db-primary);
  border-radius: 8px;
  background: var(--db-bg-white);
  font-size: 17px;
  font-weight: 700;
  color: var(--db-primary);
  text-decoration: none;
  white-space: nowrap;
  transition: background-color 0.15s ease-out;
}

.login__staff-btn:hover {
  background: var(--db-bg-subtle);
}

.login__staff-btn:active {
  transform: translateY(1px);
}

.login__staff-btn:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

@media (prefers-reduced-motion: reduce) {
  .login__staff-btn {
    transition: none;
  }

  .login__staff-btn:active {
    transform: none;
  }
}

.login__staff-go {
  margin-left: 2px;
}

.login__staff-note {
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
  text-align: center;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.login__header {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.login__title {
  margin: 0;
  font-size: 40px;
  line-height: 48px;
  font-weight: 700;
  color: var(--db-text-body);
}

/* "다배움"만 accent — primary(var(--db-primary))가 아니다 */
.login__title-point {
  color: var(--db-accent);
}

.login__sub {
  margin: 0;
  font-size: 16px;
  line-height: 1.5;
  font-weight: 400;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.login__actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 완성형_로그인_버튼.png 색·굵기는 따르되, 로그인 버튼과 맞춰 글씨는 가운데 정렬(2026-09-23 요청) */
.login__dadaegu {
  height: 53px;
  padding: 16px 24px;
  border: 0;
  border-radius: 8px;
  background: var(--db-dadaegu);
  text-align: center;
  font-family: inherit;
  font-size: 18px;
  line-height: 21px;
  font-weight: 400;
  color: var(--db-on-color);
  cursor: pointer;
}

.login__dadaegu strong {
  font-weight: 800;
}

.login__dadaegu:disabled {
  cursor: default;
  opacity: 0.7;
}

.login__notice {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0;
  padding: 12px 14px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.5;
  color: var(--db-text-body);
  background: var(--db-accent-soft);
  word-break: keep-all;
}

.login__notice .q-icon {
  flex-shrink: 0;
  margin-top: 1px;
  color: var(--db-accent);
}

.login__notice-strong {
  font-weight: 700;
  color: var(--db-accent);
}

.login__register {
  margin: 8px 0 0;
  font-size: 16px;
  line-height: 1.5;
  text-align: center;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.login__dev {
  width: 100%;
  max-width: 450px;
  margin: 48px auto 0;
}

.role-btn {
  min-height: 64px;
  justify-content: flex-start;
  padding: 10px 14px;
}

.user-list {
  max-height: 240px;
  overflow: auto;
}

/* 폰·태블릿: 한 줄로 — 로그인이 먼저, 안내 면은 그 아래. 좌우 16 */
@media (max-width: 1023px) {
  .login {
    min-height: 0;
    margin-top: 24px;
    padding: 0 16px 32px;
  }

  .login__wrap {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas:
      'main'
      'guide';
    gap: 40px;
    max-width: 450px;
  }

  .login__guide {
    min-height: 0;
    padding: 28px 24px;
  }

  .login__guide-title {
    font-size: 22px;
    line-height: 30px;
  }

  .login__steps {
    gap: 20px;
    margin-top: 24px;
  }

  .login__title {
    font-size: 28px;
    line-height: 36px;
  }

  .login__main {
    gap: 32px;
  }
}
</style>
