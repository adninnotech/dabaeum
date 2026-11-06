<template>
  <!-- 로그인 화면 [비밀번호 찾기] — 가입 이메일로 재설정 링크를 받는다. 보낸 뒤엔 창을 닫지 않고 '어디로 보냈는지·다음에 할 일'을 같은 창에 보인다
       (예전엔 토스트 한 줄 뜨고 닫혀, 메일을 기다리는 사람이 무엇을 해야 할지 몰랐다) -->
  <q-dialog :model-value="modelValue" :persistent="sending" @update:model-value="close" @before-show="reset" @show="focusEmail">
    <q-card class="pw-find">
      <button type="button" class="pw-find__close" aria-label="닫기" :disabled="sending" @click="close(false)">
        <q-icon name="close" size="24px" aria-hidden="true" />
      </button>

      <!-- 1. 이메일 입력 -->
      <form v-if="!sentTo" class="pw-find__body" novalidate @submit.prevent="submit">
        <span class="pw-find__mark" aria-hidden="true"><q-icon name="o_lock_reset" size="28px" /></span>
        <h2 class="pw-find__title">비밀번호 찾기</h2>
        <p class="pw-find__lead">가입한 이메일을 적어 주시면 비밀번호를 새로 정하는 링크를 메일로 보내 드립니다.</p>

        <div class="pw-find__field">
          <label class="pw-find__label" for="pw-find-email">가입 이메일</label>
          <div class="pw-find__control" :class="{ 'pw-find__control--error': !!error }">
            <q-icon name="o_mail" size="22px" class="pw-find__control-icon" aria-hidden="true" />
            <input
              id="pw-find-email"
              ref="email"
              v-model="email"
              class="pw-find__input"
              type="email"
              inputmode="email"
              autocomplete="email"
              :maxlength="EMAIL_MAX"
              placeholder="example@email.com"
              :disabled="sending"
              :aria-invalid="!!error"
              :aria-describedby="error ? 'pw-find-error' : null"
              @input="error = ''"
            />
          </div>
          <p v-if="error" id="pw-find-error" class="pw-find__error" role="alert">
            <q-icon name="error_outline" size="18px" aria-hidden="true" />
            <span>{{ error }}</span>
          </p>
        </div>

        <button type="submit" class="pw-find__submit" :disabled="sending">
          <q-spinner v-if="sending" size="20px" aria-hidden="true" />
          <span>{{ sending ? '보내는 중…' : '재설정 링크 받기' }}</span>
        </button>

        <!-- 다대구로만 가입한 계정은 비밀번호가 없다 — 여기서 메일을 기다려도 오지 않는다 -->
        <p class="pw-find__aside">
          <q-icon name="o_info" size="18px" aria-hidden="true" />
          <span>다대구로 가입하셨다면 비밀번호가 없습니다. 로그인 화면의 <strong>다대구 로그인</strong>을 이용해 주세요.</span>
        </p>
      </form>

      <!-- 2. 보낸 뒤 -->
      <div v-else class="pw-find__body" role="status">
        <span class="pw-find__mark pw-find__mark--done" aria-hidden="true"><q-icon name="o_mark_email_read" size="28px" /></span>
        <h2 class="pw-find__title">메일을 확인해 주세요</h2>
        <p class="pw-find__lead">
          <strong class="pw-find__email">{{ sentTo }}</strong>(으)로 가입된 계정이 있으면 재설정 링크가 도착합니다.
        </p>

        <ol class="pw-find__steps">
          <li><span>받은 메일의 재설정 링크를 누릅니다.</span></li>
          <li><span>새 비밀번호를 두 번 적고 저장합니다.</span></li>
          <li><span>새 비밀번호로 다시 로그인합니다.</span></li>
        </ol>

        <button type="button" class="pw-find__submit" @click="close(false)">로그인 화면으로</button>

        <div class="pw-find__more">
          <p class="pw-find__more-title">메일이 오지 않나요?</p>
          <ul class="pw-find__more-list">
            <li>스팸함·프로모션함을 확인해 주세요. 몇 분 걸릴 수 있습니다.</li>
            <li>
              이메일을 잘못 적었다면
              <button type="button" class="pw-find__text-btn" @click="retry">다른 이메일로 다시 받기</button>
            </li>
            <li>
              메일에 코드만 있다면
              <router-link to="/auth/password/reset" class="pw-find__link" @click="close(false)">코드로 재설정하기</router-link>
            </li>
            <li>
              그래도 안 되면
              <router-link to="/support" class="pw-find__link" @click="close(false)">고객센터</router-link>로 문의해 주세요.
            </li>
          </ul>
        </div>
      </div>
    </q-card>
  </q-dialog>
</template>

<script>
import { requestPasswordReset } from '@/services/auth-api';

// 회원가입·이메일 로그인 연결과 같은 규칙
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const EMAIL_MAX = 320;

export default {
  name: 'PasswordFindDialog',
  props: {
    modelValue: { type: Boolean, default: false },
    /** 로그인 칸에 적어 둔 아이디(이메일) — 다시 치지 않게 채워 둔다 */
    initialEmail: { type: String, default: '' },
  },
  emits: ['update:modelValue'],
  data() {
    return {
      EMAIL_MAX,
      email: '',
      error: '',
      sending: false,
      sentTo: '',
    };
  },
  methods: {
    reset() {
      this.email = this.initialEmail.trim();
      this.error = '';
      this.sending = false;
      this.sentTo = '';
    },
    focusEmail() {
      this.$refs.email?.focus();
    },
    close(value = false) {
      if (this.sending) return;
      this.$emit('update:modelValue', value);
    },
    retry() {
      this.sentTo = '';
      this.error = '';
      this.$nextTick(() => {
        this.$refs.email?.focus();
        this.$refs.email?.select();
      });
    },
    async submit() {
      const email = this.email.trim();
      if (!email) {
        this.error = '가입할 때 쓴 이메일을 적어 주세요.';
        return;
      }
      if (!EMAIL.test(email)) {
        this.error = '이메일 형식이 아닙니다. 예) example@email.com';
        return;
      }
      this.sending = true;
      try {
        // 계정이 있는지는 서버가 알려 주지 않는다(가입 여부 노출 방지) — 결과 문구도 '있으면 도착'으로 쓴다
        await requestPasswordReset(email);
        this.sentTo = email;
      } catch (error) {
        this.error = error?.message || '메일을 보내지 못했습니다. 잠시 뒤 다시 시도해 주세요.';
      } finally {
        this.sending = false;
      }
    },
  },
};
</script>

<style scoped>
/* Hallmark · component: dialog · genre: modern-minimal · theme: project tokens (--db-*)
 * states: default · hover · focus · active · disabled · loading · error · success
 */
.pw-find {
  position: relative;
  width: 440px;
  max-width: calc(100vw - 32px);
  border-radius: 12px;
  background: var(--db-bg-white);
  color: var(--db-text-body);
}

.pw-find__close {
  position: absolute;
  top: 8px;
  right: 8px;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border: 0;
  border-radius: 8px;
  background: none;
  color: var(--db-text-sub);
  cursor: pointer;
}

.pw-find__close:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: -2px;
}

.pw-find__close:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.pw-find__body {
  display: flex;
  flex-direction: column;
  padding: 32px 28px 28px;
}

.pw-find__mark {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  margin-bottom: 16px;
  border-radius: 14px;
  background: var(--db-accent-soft);
  color: var(--db-accent);
}

.pw-find__mark--done {
  background: color-mix(in srgb, var(--db-status-green) 14%, transparent);
  color: var(--db-status-green-text);
}

.pw-find__title {
  margin: 0;
  font-size: 22px;
  line-height: 1.35;
  font-weight: 700;
  color: var(--db-text-title);
}

.pw-find__lead {
  margin: 8px 0 0;
  font-size: 16px;
  line-height: 1.6;
  color: var(--db-text-body);
  word-break: keep-all;
}

.pw-find__email {
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

/* 입력칸 — 로그인 화면 칸과 같은 모양(54px · 라벨 위) */
.pw-find__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 24px;
}

.pw-find__label {
  font-size: 16px;
  font-weight: 600;
  color: var(--db-text-title);
}

.pw-find__control {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 54px;
  padding: 0 14px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
  background: var(--db-bg-white);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.pw-find__control:focus-within {
  border-color: var(--db-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--db-primary) 15%, transparent);
}

.pw-find__control--error,
.pw-find__control--error:focus-within {
  border-color: var(--db-status-pink);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--db-status-pink) 15%, transparent);
}

.pw-find__control-icon {
  flex-shrink: 0;
  color: var(--db-text-sub);
}

.pw-find__input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: transparent;
  font-family: inherit;
  font-size: 16px;
  color: var(--db-text-title);
}

.pw-find__input::placeholder {
  color: var(--db-text-placeholder);
}

.pw-find__input:disabled {
  cursor: not-allowed;
}

.pw-find__error {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 0;
  font-size: 15px;
  line-height: 1.45;
  color: var(--db-status-pink);
  word-break: keep-all;
}

.pw-find__error .q-icon {
  flex-shrink: 0;
  margin-top: 2px;
}

/* 주 버튼 — 로그인 버튼과 같은 남색 */
.pw-find__submit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 52px;
  margin-top: 20px;
  border: 0;
  border-radius: 8px;
  background: var(--db-primary);
  color: var(--db-on-color);
  font-family: inherit;
  font-size: 17px;
  font-weight: 700;
  cursor: pointer;
  transition: filter 0.15s ease, transform 0.1s ease;
}

.pw-find__submit:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.pw-find__submit:active:not(:disabled) {
  transform: translateY(1px);
}

.pw-find__submit:disabled {
  cursor: progress;
  opacity: 0.7;
}

.pw-find__aside {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 20px 0 0;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--db-bg-subtle);
  font-size: 15px;
  line-height: 1.55;
  color: var(--db-text-body);
  word-break: keep-all;
}

.pw-find__aside .q-icon {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--db-text-sub);
}

.pw-find__aside strong {
  color: var(--db-text-title);
}

/* 보낸 뒤 — 다음에 할 일 세 단계 */
.pw-find__steps {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 20px 0 0;
  padding: 0;
  list-style: none;
  counter-reset: pw-step;
}

.pw-find__steps li {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 16px;
  line-height: 1.5;
  color: var(--db-text-body);
  word-break: keep-all;
  counter-increment: pw-step;
}

.pw-find__steps li::before {
  content: counter(pw-step);
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--db-bg-tag);
  font-size: 14px;
  font-weight: 700;
  color: var(--db-text-title);
}

.pw-find__more {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--db-border);
}

.pw-find__more-title {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 700;
  color: var(--db-text-title);
}

.pw-find__more-list {
  margin: 0;
  padding-left: 18px;
  font-size: 15px;
  line-height: 1.5;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.pw-find__more-list li + li {
  margin-top: 2px;
}

/* 줄 안 링크·버튼 — 글줄 높이 그대로 두되 누르는 자리는 위아래로 넓힌다 */
.pw-find__link,
.pw-find__text-btn {
  display: inline-block;
  padding: 10px 0;
  margin: -10px 0;
  border: 0;
  background: none;
  font-family: inherit;
  font-size: inherit;
  font-weight: 600;
  color: var(--db-accent);
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.pw-find__link:focus-visible,
.pw-find__text-btn:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
  border-radius: 2px;
}

@media (hover: hover) {
  .pw-find__close:hover:not(:disabled) {
    background: var(--db-bg-subtle);
    color: var(--db-text-title);
  }

  .pw-find__submit:hover:not(:disabled) {
    filter: brightness(1.12);
  }

  .pw-find__link:hover,
  .pw-find__text-btn:hover {
    text-decoration-thickness: 2px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .pw-find__control,
  .pw-find__submit {
    transition: none;
  }

  .pw-find__submit:active:not(:disabled) {
    transform: none;
  }
}

@media (max-width: 599px) {
  .pw-find__body {
    padding: 28px 20px 20px;
  }

  .pw-find__title {
    font-size: 20px;
  }
}
</style>
