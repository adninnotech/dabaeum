<template>
  <!-- 로그인·회원가입과 같은 틀(로고 머리) — 예전엔 로고 없는 Quasar 기본 카드라 다른 사이트처럼 보였다 -->
  <AuthShell :show-stepper="false">
    <div class="reset">
      <header class="reset__header">
        <h1 class="reset__title">비밀번호 재설정</h1>
        <p class="reset__sub">
          {{ fromLink ? '새로 쓸 비밀번호를 입력해 주세요.' : '메일로 받은 재설정 링크를 눌러 들어오면 바로 새 비밀번호를 정할 수 있습니다.' }}
        </p>
      </header>
      <!-- q-gutter-md 는 자식에 왼쪽 여백·폼에 음수 여백을 줘 전체 너비 버튼이 카드 왼쪽에 붙는다 — 세로 간격만 준다 -->
      <q-form ref="form" class="reset-form" @submit="submit">
        <!-- 메일 링크(?token=)로 오면 코드 칸을 숨긴다. 링크 없이 온 경우만 메일의 코드를 붙여 넣게 한다 -->
        <q-input
          v-if="!fromLink"
          v-model="token"
          outlined
          label="메일의 재설정 코드 *"
          hint="링크가 열리지 않으면 메일에 적힌 코드를 붙여 넣어 주세요."
          :rules="[required]"
        />
        <q-input
          v-model="password"
          outlined
          type="password"
          label="새 비밀번호 *"
          hint="10자 이상 입력해 주세요."
          :rules="[required, passwordRule]"
        />
        <q-input
          v-model="passwordConfirm"
          outlined
          type="password"
          label="새 비밀번호 확인 *"
          :rules="[required, confirmRule]"
        />
        <q-btn type="submit" unelevated no-caps color="primary" class="reset-form__submit" label="비밀번호 변경" :loading="submitting" />
        <q-btn flat no-caps color="primary" label="로그인으로" to="/login" />
      </q-form>
      <!-- 메일 발송이 준비 중일 수 있다 — 메일이 안 오면 막히지 않게 -->
      <p class="reset__help">
        메일이 오지 않았나요? <router-link to="/support" class="reset__link">고객센터</router-link>로 문의해 주세요.
      </p>
    </div>
  </AuthShell>
</template>

<script>
import AuthShell from '@/components/auth/AuthShell.vue';
import { confirmPasswordReset } from '@/services/auth-api';

export default {
  name: 'PasswordResetPage',
  components: { AuthShell },
  data() {
    return {
      token: '',
      fromLink: false,
      password: '',
      passwordConfirm: '',
      submitting: false,
    };
  },
  mounted() {
    const fromQuery = this.$route.query.token;
    if (fromQuery) {
      this.token = String(fromQuery);
      this.fromLink = true;
    }
  },
  methods: {
    required(value) {
      return !!value || '필수 입력 항목입니다.';
    },
    passwordRule(value) {
      return (value && value.length >= 10) || '10자 이상 입력해 주세요.';
    },
    confirmRule(value) {
      return value === this.password || '비밀번호가 일치하지 않습니다.';
    },
    async submit() {
      const valid = await this.$refs.form.validate();
      if (!valid) return;
      this.submitting = true;
      try {
        await confirmPasswordReset({
          token: this.token.trim(),
          password: this.password,
        });
        this.$q.notify({ type: 'positive', message: '비밀번호가 변경되었습니다. 로그인해 주세요.' });
        // 메일 링크로 들어와 돌아갈 화면이 없다 — redirect 없이 보내 역할별 홈으로 가게 한다
        this.$router.push({ path: '/login' });
      } catch (error) {
        // 링크가 만료·사용됨이면 서버가 400/401/404 로 거절한다 — 다시 받는 길을 알려 준다
        const expired = [400, 401, 404, 410].includes(error?.status);
        this.$q.notify({
          type: 'negative',
          message: expired
            ? '재설정 링크가 만료됐거나 이미 사용됐습니다. 로그인 화면의 "비밀번호 찾기"로 다시 받아 주세요.'
            : error.message || '비밀번호 재설정에 실패했습니다.',
        });
      } finally {
        this.submitting = false;
      }
    },
  },
};
</script>

<style scoped>
.reset {
  width: 100%;
  max-width: 400px;
  margin: 24px auto 0;
}

.reset__header {
  margin-bottom: 20px;
  text-align: center;
}

.reset__title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  color: var(--db-text-title);
}

.reset__sub {
  margin: 8px 0 0;
  font-size: 14px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.reset__help {
  margin: 16px 0 0;
  font-size: 14px;
  text-align: center;
  color: var(--db-text-sub);
}

.reset__link {
  font-weight: 600;
  color: var(--db-accent);
}

.reset-form {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.reset-form__submit {
  height: 44px;
  margin-top: 8px;
  border-radius: 6px;
}
</style>
