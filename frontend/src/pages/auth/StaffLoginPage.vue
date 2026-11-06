<template>
  <AuthShell :show-aside="false" :show-stepper="false">
    <div class="staff-login">
      <div class="staff-login__main">
        <header class="staff-login__header">
          <h1 class="staff-login__title">강사·기관 관리자 로그인</h1>
          <p class="staff-login__sub">이메일로 가입한 강사·기관 관리자 계정으로 로그인해요.</p>
        </header>

        <!-- 강사·기관 역할이 아직 없는 계정 — 가입 직후 승인 전 강사이거나, 학습자가 잘못 들어온 경우 -->
        <section v-if="noStaffRole" class="staff-login__pending" aria-labelledby="staff-pending-title">
          <h2 id="staff-pending-title" class="staff-login__pending-title">
            아직 강사·기관 관리자 역할이 없어요
          </h2>
          <p class="staff-login__pending-text" role="status">
            신청이 승인되면 콘솔을 쓸 수 있어요. 신청 현황은 아래에서 확인해 주세요.
          </p>
          <div class="staff-login__pending-links">
            <router-link to="/mypage/instructor-apply" class="staff-login__pending-link">
              강사 신청 현황
            </router-link>
            <router-link to="/mypage/institution-apply" class="staff-login__pending-link">
              기관 등록 신청 현황
            </router-link>
          </div>
        </section>

        <LocalLoginForm
          v-else
          v-model="email"
          :loading="loading"
          :error="loginError"
          @submit="submitLogin"
          @find-password="resetOpen = true"
        />

        <!-- 이미 로그인한 상태 — 가입·다대구 안내 대신 계정 바꾸기만 -->
        <p v-if="noStaffRole" class="staff-login__line">
          다른 계정이신가요?
          <button type="button" class="staff-login__link staff-login__link--button" @click="switchAccount">
            다른 계정으로 로그인
          </button>
        </p>
        <div v-else class="staff-login__links">
          <p class="staff-login__line">
            아직 계정이 없나요?
            <router-link :to="signupTo" class="staff-login__link">회원가입</router-link>
          </p>
          <!-- 학습자는 다대구로만 가입·로그인한다(#112) — 잘못 온 학습자가 돌아갈 길 -->
          <p class="staff-login__line staff-login__line--learner">
            강좌를 들으려는 학습자이신가요?
            <router-link :to="learnerTo" class="staff-login__link">다대구로 로그인</router-link>
          </p>
        </div>
      </div>
    </div>

    <PasswordFindDialog v-model="resetOpen" :initial-email="email" />
  </AuthShell>
</template>

<script>
import { mapActions, mapState } from 'pinia';
import AuthShell from '@/components/auth/AuthShell.vue';
import LocalLoginForm from '@/components/auth/LocalLoginForm.vue';
import PasswordFindDialog from '@/components/auth/PasswordFindDialog.vue';
import { homePathAfterLogin, ROLES } from '@/access/roles';
import { useAuthStore } from '@/stores/auth-store';
import { safeRedirect } from '@/utils/auth-redirect';

// 콘솔에 들어갈 수 있는 역할 — 이것 없이 학습자 역할만 있으면 콘솔 대신 신청 현황을 안내한다
const STAFF_ROLES = [ROLES.INSTRUCTOR, ROLES.INSTITUTION_ADMIN, ROLES.PLATFORM_ADMIN];

/* 강사·기관 관리자 로그인(#134). 학습자는 /login 의 다대구 로그인만 쓴다.
 * API 는 학습자와 같은 POST /auth/login — 역할로만 갈라 콘솔 또는 신청 현황으로 보낸다 */
export default {
  name: 'StaffLoginPage',
  components: { AuthShell, LocalLoginForm, PasswordFindDialog },
  data() {
    return { email: '', loading: false, resetOpen: false, noStaffRole: false };
  },
  computed: {
    ...mapState(useAuthStore, ['loginError', 'userRoles', 'primaryRole']),
    redirect() {
      return safeRedirect(this.$route.query.redirect);
    },
    signupTo() {
      return this.redirect ? { path: '/signup/staff', query: { redirect: this.redirect } } : '/signup/staff';
    },
    learnerTo() {
      return this.redirect ? { path: '/login', query: { redirect: this.redirect } } : '/login';
    },
  },
  methods: {
    ...mapActions(useAuthStore, ['login', 'logout']),
    async submitLogin(password) {
      this.loading = true;
      try {
        const ok = await this.login({ email: this.email, password });
        if (ok) this.afterLogin();
      } finally {
        this.loading = false;
      }
    },
    switchAccount() {
      this.logout();
      this.noStaffRole = false;
    },
    afterLogin() {
      const roles = this.userRoles || [];
      if (!roles.some((role) => STAFF_ROLES.includes(role))) {
        this.noStaffRole = true;
        return;
      }
      this.$router.replace(this.redirect || homePathAfterLogin(roles, this.primaryRole));
    },
  },
};
</script>

<style scoped>
/* 로그인(/login) 오른쪽 열과 같은 450 폭·글자 크기 — 안내 면 없이 한 열로 가운데 */
.staff-login {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 200px);
  padding: 0 40px 40px;
}

.staff-login__main {
  display: flex;
  flex-direction: column;
  gap: 32px;
  width: 100%;
  max-width: 450px;
  min-width: 0;
}

.staff-login__header {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.staff-login__title {
  margin: 0;
  font-size: 32px;
  line-height: 40px;
  font-weight: 700;
  color: var(--db-text-body);
  word-break: keep-all;
}

.staff-login__sub {
  margin: 0;
  font-size: 16px;
  line-height: 1.5;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.staff-login__pending {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 20px;
  border-radius: 12px;
  background: var(--db-accent-soft);
}

.staff-login__pending-title {
  margin: 0;
  font-size: 18px;
  line-height: 1.4;
  font-weight: 700;
  color: var(--db-text-title);
  word-break: keep-all;
}

.staff-login__pending-text {
  margin: 0;
  font-size: 15px;
  line-height: 1.5;
  color: var(--db-text-body);
  word-break: keep-all;
}

.staff-login__pending-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

/* 로그인 화면 스토어 링크와 같은 알약형 */
.staff-login__pending-link {
  display: inline-flex;
  align-items: center;
  height: 40px;
  padding: 0 16px;
  border: 1px solid var(--db-border);
  border-radius: 20px;
  background: var(--db-bg-white);
  font-size: 15px;
  font-weight: 600;
  color: var(--db-text-title);
  text-decoration: none;
  white-space: nowrap;
}

.staff-login__pending-link:hover {
  background: var(--db-bg-subtle);
}

.staff-login__pending-link:focus-visible,
.staff-login__link:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.staff-login__links {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.staff-login__line {
  margin: 0;
  font-size: 16px;
  line-height: 1.5;
  text-align: center;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.staff-login__line--learner {
  padding-top: 20px;
  border-top: 1px solid var(--db-border);
  font-size: 15px;
}

.staff-login__link--button {
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}

.staff-login__link {
  margin-left: 6px;
  font-weight: 600;
  color: var(--db-primary);
  text-decoration: underline;
  text-underline-offset: 3px;
  white-space: nowrap;
}

@media (max-width: 1023px) {
  .staff-login {
    align-items: flex-start;
    min-height: 0;
    margin-top: 24px;
    padding: 0 16px 32px;
  }

  .staff-login__title {
    font-size: 28px;
    line-height: 36px;
  }
}
</style>
