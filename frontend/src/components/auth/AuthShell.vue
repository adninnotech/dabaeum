<template>
  <div class="auth-shell">
    <header v-if="showHeader" class="auth-shell__header">
      <router-link to="/" class="auth-shell__brand">
        <BrandLogo />
        <span class="auth-shell__brand-name">다배움</span>
      </router-link>
      <div v-if="showAside" class="auth-shell__aside">
        <span class="auth-shell__aside-text">이미 계정이 있으신가요?</span>
        <router-link :to="loginTo" class="auth-shell__aside-link">로그인</router-link>
      </div>
    </header>

    <main class="auth-shell__main">
      <SignupStepper v-if="showStepper" :current="currentStep" />
      <slot />
    </main>

    <footer class="auth-shell__footer">Copyright © {{ year }} 다배움. All rights reserved.</footer>
  </div>
</template>

<script>
import BrandLogo from '@/components/BrandLogo.vue';
import SignupStepper from '@/components/auth/SignupStepper.vue';
import { safeRedirect } from '@/utils/auth-redirect';

export default {
  name: 'AuthShell',
  components: { BrandLogo, SignupStepper },
  props: {
    currentStep: {
      type: Number,
      default: 1,
    },
    /** 머리 오른쪽 '이미 계정이 있으신가요? 로그인' — 로그인 화면 자신에서는 끈다 */
    showAside: {
      type: Boolean,
      default: true,
    },
    showStepper: {
      type: Boolean,
      default: true,
    },
    /** 로그인 화면은 헤더가 아예 없다 (로고조차 없음) */
    showHeader: {
      type: Boolean,
      default: true,
    },
    /** 머리 [로그인]이 갈 곳 — 강사·기관 관리자 가입은 이메일 로그인(/login/staff)으로(#134) */
    loginPath: {
      type: String,
      default: '/login',
    },
  },
  computed: {
    // 가입 중 '로그인'으로 돌아가도 원래 가려던 화면(redirect)을 잃지 않게
    loginTo() {
      const redirect = safeRedirect(this.$route.query.redirect);
      return redirect ? { path: this.loginPath, query: { redirect } } : this.loginPath;
    },
    year() {
      return new Date().getFullYear();
    },
  },
};
</script>

<style scoped>
/* 80(헤더) + 40 + 74(스테퍼) + 806(본문) + 80(푸터) = 1080 — 프레임 높이와 일치 */
.auth-shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--db-bg-white);
}

.auth-shell__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  height: 80px;
  padding: 0 40px;
  background: var(--db-bg-white);
  border-bottom: 1px solid var(--db-border);
}

.auth-shell__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
}

.auth-shell__brand-name {
  font-size: 22px;
  line-height: 26px;
  font-weight: 700;
  color: var(--db-primary);
}

.auth-shell__aside {
  display: flex;
  align-items: center;
  gap: 8px;
}

.auth-shell__aside-text {
  font-size: 18px;
  line-height: 21px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.auth-shell__aside-link {
  font-size: 18px;
  line-height: 21px;
  font-weight: 600;
  color: var(--db-primary);
  text-decoration: none;
}

.auth-shell__main {
  flex: 1 1 auto;
  width: 100%;
  padding-top: 40px;
}

.auth-shell__footer {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  height: 80px;
  padding: 10px;
  /* 17.78px — 반올림하지 말 것, fd가 computed 값을 잰다 */
  font-size: 17.78px;
  line-height: 25px;
  font-weight: 400;
  color: var(--db-text-placeholder);
}

/* 폰: 헤더 40 여백·18px 문구가 375 폭에서 두 줄로 꺾였다 */
@media (max-width: 1023px) {
  .auth-shell__header {
    gap: 12px;
    height: 64px;
    padding: 0 16px;
  }

  .auth-shell__brand-name {
    font-size: 20px;
    white-space: nowrap;
  }

  .auth-shell__aside {
    gap: 6px;
  }

  .auth-shell__aside-text,
  .auth-shell__aside-link {
    font-size: 14px;
    line-height: 17px;
    white-space: nowrap;
  }

  .auth-shell__main {
    padding-top: 16px;
  }
}
</style>
