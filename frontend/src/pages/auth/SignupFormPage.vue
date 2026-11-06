<template>
  <AuthShell :current-step="2" login-path="/login/staff">
    <div class="signup-form">
      <div class="signup-form__wrap">
        <div class="signup-form__main">
          <header class="signup-form__header">
            <h1 class="signup-form__title">강사 회원가입</h1>
            <p class="signup-form__sub">서비스 이용을 위해 정보를 입력해 주세요</p>
          </header>

          <!-- 이름표를 칸 위에 — 회색 안내 글자는 입력하면 사라져 무슨 칸이었는지 아이콘으로만 짐작해야 했다
               (피그마는 숨김이었지만 기관 관리자 가입처럼 보이게 한다) -->
          <div class="signup-form__fields">
            <div v-for="field in FIELDS" :key="field.key" class="signup-form__row">
              <label :for="`signup-${field.key}`" class="signup-form__label">
                {{ field.label }}<span v-if="field.required" class="signup-form__req" aria-hidden="true"> *</span>
              </label>
              <div class="signup-form__line">
                <div
                  class="signup-form__control"
                  :class="{ 'signup-form__control--short': field.verify }"
                >
                  <q-icon :name="field.icon" size="24px" class="signup-form__icon" />
                  <input
                    :id="`signup-${field.key}`"
                    v-model="form[field.key]"
                    class="signup-form__input"
                    :type="inputType(field)"
                    :placeholder="field.placeholder"
                    :autocomplete="field.autocomplete"
                    @input="onInput(field)"
                    @blur="onBlur(field.key)"
                    @keydown="onFieldKeydown"
                  />
                  <button
                    v-if="field.eye"
                    type="button"
                    class="signup-form__eye"
                    :aria-label="reveal[field.key] ? `${field.label} 숨기기` : `${field.label} 보기`"
                    :aria-pressed="reveal[field.key]"
                    @click="reveal[field.key] = !reveal[field.key]"
                  >
                    <q-icon :name="reveal[field.key] ? 'visibility' : 'visibility_off'" size="24px" aria-hidden="true" />
                  </button>
                </div>
                <button
                  v-if="field.verify"
                  type="button"
                  class="signup-form__verify"
                  @click="notReady"
                >
                  인증하기
                </button>
              </div>
              <p
                v-if="messageOf(field.key)"
                class="signup-form__feedback"
                :class="messageClass(field.key)"
              >
                {{ messageOf(field.key) }}
              </p>
              <!-- 조건은 틀리기 전에 — 예전엔 '10자 이상'이 한 번 틀린 다음에야 나왔다 -->
              <p v-else-if="field.hint" class="signup-form__hint">{{ field.hint }}</p>
            </div>
          </div>
        </div>

        <SignupTermsConsent v-model="agreements" audience="staff" />
      </div>

      <button type="button" class="signup-form__submit" :disabled="submitting" @click="submit">
        등록하기
      </button>
    </div>

  </AuthShell>
</template>

<script>
import { mapActions } from 'pinia';
import AuthShell from '@/components/auth/AuthShell.vue';
import SignupTermsConsent from '@/components/auth/SignupTermsConsent.vue';
import { checkEmailAvailability, signupLocalAccount } from '@/services/auth-api';
import { formatPhone } from '@/utils/phone';
import { emptyAgreements, requiredTermsAgreed } from '@/utils/signup-terms';
import { useAuthStore } from '@/stores/auth-store';

// 학습자는 다대구로만 가입한다(#112) — 이 폼은 강사 가입만 받는다(#134)
const VALID_ROLES = new Set(['INSTRUCTOR']);
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/*
 * Figma 순서. 라벨은 숨김이라 placeholder만 그린다 — 화면낭독기용 이름(label)과 자동완성(autocomplete)은 따로 둔다.
 * placeholder 는 입력하면 사라져 이름 구실을 못 한다
 */
const FIELDS = [
  {
    key: 'loginId',
    icon: 'badge',
    label: '아이디(이메일)',
    autocomplete: 'username',
    placeholder: '아이디를 입력해 주세요',
    required: true,
  },
  {
    key: 'password',
    icon: 'lock_outline',
    label: '비밀번호',
    autocomplete: 'new-password',
    placeholder: '비밀번호를 입력해 주세요',
    eye: true,
    required: true,
    hint: '10자 이상 입력해 주세요.',
  },
  {
    key: 'passwordConfirm',
    icon: 'lock_outline',
    label: '비밀번호 확인',
    autocomplete: 'new-password',
    placeholder: '비밀번호를 다시 입력해 주세요',
    eye: true,
    required: true,
  },
  { key: 'name', icon: 'person_outline', label: '이름', autocomplete: 'name', placeholder: '이름을 입력해 주세요', required: true },
  {
    key: 'email',
    icon: 'mail_outline',
    label: '이메일',
    autocomplete: 'email',
    placeholder: 'example@email.com',
    required: true,
  },
  // 휴대폰 본인인증 API 가 없어 "인증하기"는 "준비 중"만 띄웠다 — 인증이 생기면 verify: true 로 되살린다
  { key: 'phone', icon: 'phone_iphone', label: '휴대폰', autocomplete: 'tel', placeholder: '010-0000-0000' },
];

/* 빈 문자열이면 통과. q-form을 걷어냈으므로 이 표가 유일한 검증 지점이다 */
const RULES = {
  loginId: (v) => {
    const text = String(v || '').trim();
    if (!text) return '필수 입력 항목입니다.';
    return EMAIL.test(text) ? '' : '아이디는 이메일 형식으로 입력해 주세요.';
  },
  password: (v) => {
    if (!String(v || '').trim()) return '필수 입력 항목입니다.';
    return String(v).length >= 10 ? '' : '10자 이상 입력해 주세요.';
  },
  passwordConfirm: (v, form) => {
    if (!String(v || '').trim()) return '필수 입력 항목입니다.';
    return v === form.password ? '' : '비밀번호가 일치하지 않습니다.';
  },
  name: (v) => (String(v || '').trim() ? '' : '필수 입력 항목입니다.'),
  email: (v) => {
    const text = String(v || '').trim();
    if (!text) return '필수 입력 항목입니다.';
    return EMAIL.test(text) ? '' : '올바른 이메일을 입력해 주세요.';
  },
};

function validateForm(form) {
  const errors = {};
  Object.keys(RULES).forEach((key) => {
    const message = RULES[key](form[key], form);
    if (message) errors[key] = message;
  });
  return errors;
}

export default {
  name: 'SignupFormPage',
  components: { AuthShell, SignupTermsConsent },
  data() {
    return {
      FIELDS,
      form: {
        loginId: '',
        password: '',
        passwordConfirm: '',
        name: '',
        email: '',
        phone: '',
      },
      reveal: { password: false, passwordConfirm: false },
      errors: {},
      agreements: emptyAgreements(),
      checkMessage: { loginId: '', email: '' },
      checkOk: { loginId: false, email: false },
      emailTouched: false,
      submitting: false,
    };
  },
  computed: {
    role() {
      return String(this.$route.query.role || 'INSTRUCTOR');
    },
  },
  watch: {
    'form.loginId'(value) {
      if (!this.emailTouched) this.form.email = value;
    },
  },
  mounted() {
    if (this.role === 'INSTITUTION_ADMIN') {
      this.$router.replace('/signup/institution');
      return;
    }
    if (!VALID_ROLES.has(this.role)) this.$router.replace('/signup/staff');
  },
  methods: {
    ...mapActions(useAuthStore, ['applyAuthToken']),
    onInput(field) {
      if (field.key === 'phone') this.form.phone = formatPhone(this.form.phone);
    },
    inputType(field) {
      if (!field.eye) return 'text';
      return this.reveal[field.key] ? 'text' : 'password';
    },
    messageOf(key) {
      return this.errors[key] || this.checkMessage[key] || '';
    },
    messageClass(key) {
      return !this.errors[key] && this.checkOk[key] ? 'signup-form__feedback--ok' : '';
    },
    onBlur(key) {
      if (key === 'email') this.emailTouched = true;
      if (this.errors[key]) this.errors = validateForm(this.form);
      if (key === 'loginId' || key === 'email') this.checkAvailability(key);
    },
    async checkAvailability(key) {
      const value = String(this.form[key] || '').trim();
      if (!EMAIL.test(value)) {
        this.checkMessage[key] = '';
        this.checkOk[key] = false;
        return;
      }
      if (key === 'email' && value === this.form.loginId.trim()) {
        this.checkMessage.email = this.checkMessage.loginId;
        this.checkOk.email = this.checkOk.loginId;
        return;
      }
      try {
        const result = await checkEmailAvailability(value);
        this.checkOk[key] = !!result?.available;
        this.checkMessage[key] = this.checkOk[key] ? '사용 가능합니다.' : '이미 사용 중입니다.';
      } catch {
        this.checkMessage[key] = '';
      }
    },
    notReady() {
      this.$q.notify({ type: 'info', message: '본인인증은 준비 중입니다.', position: 'top' });
    },
    /* 서버가 loginId를 안 받는다 — 이메일 우선, 아이디는 버려진다. 대장 #37, 여기서 고치지 말 것 */
    resolveSignupEmail() {
      return this.form.email.trim() || this.form.loginId.trim();
    },
    /** 입력칸에서 Enter 로 제출 — <form> 을 쓰지 않는 화면이라 직접 잇는다 */
    onFieldKeydown(event) {
      if (event.key === 'Enter' && !event.isComposing) {
        event.preventDefault();
        this.submit();
      }
    },
    async submit() {
      this.errors = validateForm(this.form);
      if (Object.keys(this.errors).length) return;
      if (!requiredTermsAgreed(this.agreements)) {
        this.$q.notify({ type: 'warning', message: '필수 약관에 동의해 주세요.' });
        return;
      }

      const signupEmail = this.resolveSignupEmail();
      if (this.checkMessage.loginId && !this.checkOk.loginId) {
        this.$q.notify({ type: 'warning', message: '사용할 수 없는 아이디입니다.' });
        return;
      }
      if (
        signupEmail !== this.form.loginId.trim() &&
        this.checkMessage.email &&
        !this.checkOk.email
      ) {
        this.$q.notify({ type: 'warning', message: '사용할 수 없는 이메일입니다.' });
        return;
      }

      this.submitting = true;
      try {
        const tokenPayload = await signupLocalAccount({
          email: signupEmail,
          password: this.form.password,
          name: this.form.name.trim(),
          phone: this.form.phone.trim() || null,
          birthDate: null,
          agreements: this.agreements,
        });
        await this.applyAuthToken(tokenPayload);
        this.$q.notify({
          type: 'positive',
          message: '가입되었습니다. 소속 기관에 강사 신청을 해 주세요.',
          position: 'top',
        });
        // 가입과 동시에 로그인된다 — 바로 소속 기관을 고르는 강사 신청으로 보낸다
        this.$router.push({ path: '/mypage/instructor-apply', query: { role: this.role } });
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error.message || '회원가입에 실패했습니다.',
          position: 'top',
        });
      } finally {
        this.submitting = false;
      }
    },
  },
};
</script>

<style scoped>
/* 10 + wrap(자동 690) + 32 + 53(버튼) + 10. 높이는 박지 않는다 */
.signup-form {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 32px;
  width: 880px;
  margin: 0 auto;
  padding: 10px;
}

.signup-form__wrap {
  display: flex;
  flex-direction: column;
  gap: 24px;
  width: 516px;
  margin: 0 auto;
}

.signup-form__main {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 32px;
}

.signup-form__header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 9px;
}

.signup-form__title {
  margin: 0;
  font-size: 40px;
  line-height: 48px;
  font-weight: 700;
  color: var(--db-text-body);
}

.signup-form__sub {
  margin: 0;
  text-align: center;
  font-size: 16px;
  line-height: 19px;
  font-weight: 400;
  color: var(--db-text-sub);
}

/* 404 = 54 × 6 + 16 × 5. 폭을 꽉 채우므로 컨테이너의 CENTER가 무효 = 좌측 */
.signup-form__fields {
  display: flex;
  flex-direction: column;
  width: 100%;
  gap: 16px;
}

.signup-form__line {
  display: flex;
  gap: 8px;
}

.signup-form__control {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  height: 54px;
  padding: 4px 14px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 6px;
}

/* 입력칸이 outline: none 이라 포커스 위치가 안 보였다 — 칸 테두리로 */
.signup-form__control:focus-within {
  border-color: var(--db-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--db-primary) 15%, transparent);
}

/* 인증 행만 516 − 8 − 81 = 427 */
.signup-form__control--short {
  flex: 0 0 427px;
}

.signup-form__icon,
.signup-form__eye {
  flex-shrink: 0;
  color: var(--db-text-sub);
}

.signup-form__eye {
  display: grid;
  place-items: center;
  padding: 2px;
  border: 0;
  border-radius: 4px;
  background: none;
  cursor: pointer;
}

.signup-form__eye:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 1px;
}

.signup-form__input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: transparent;
  font-family: inherit;
  font-size: 16px;
  line-height: 19px;
  font-weight: 400;
  color: var(--db-text-title);
}

.signup-form__input::placeholder {
  color: var(--db-text-sub);
}

/* API가 없어도 흐리게 만들지 않는다 — 클릭하면 안내만 뜬다 */
.signup-form__verify {
  flex: 0 0 81px;
  height: 54px;
  border: 0;
  border-radius: 6px;
  background: var(--db-fill-strong);
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-on-color);
  cursor: pointer;
}

.signup-form__label {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-title);
}

.signup-form__req {
  color: var(--db-status-pink);
}

.signup-form__hint {
  margin: 6px 0 0;
  font-size: 14px;
  color: var(--db-text-sub);
}

.signup-form__feedback {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 14px;
  color: var(--db-status-pink);
}

.signup-form__feedback--ok {
  color: var(--db-status-green);
}

/* 입력 516 vs 버튼 496 — container padding 10 때문이다. 맞추지 말 것 */
.signup-form__submit {
  width: 496px;
  height: 53px;
  align-self: center;
  border: 0;
  border-radius: 8px;
  background: var(--db-primary); /* 사이트 주 버튼과 같은 남색 — 밝은 파랑이라 다른 화면과 어긋났다 */
  font-family: inherit;
  font-size: 18px;
  line-height: 21px;
  font-weight: 600;
  color: var(--db-on-color);
  cursor: pointer;
}

/* 폰: 880 컨테이너·516 폼·427 인증 입력·421 약관 라벨이 375 폭을 넘었다 */
@media (max-width: 1023px) {
  .signup-form {
    gap: 24px;
    width: 100%;
    max-width: 880px;
    padding: 16px;
  }

  .signup-form__wrap {
    width: 100%;
    max-width: 516px;
  }

  .signup-form__title {
    font-size: 28px;
    line-height: 36px;
  }

  .signup-form__control--short {
    flex: 1 1 auto;
    min-width: 0;
  }

  .signup-form__submit {
    width: 100%;
    max-width: 496px;
  }
}
</style>
