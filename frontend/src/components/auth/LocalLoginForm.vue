<template>
  <form class="local-login__form" novalidate @submit.prevent="onSubmit">
    <div class="local-login__fields">
      <div class="local-login__inner">
        <div class="local-login__field">
          <!-- 아이디는 이메일이다(서버에 email 로 보낸다) — 라벨·키보드·자동완성을 맞춘다 -->
          <label class="local-login__label" for="login-id">아이디(이메일)</label>
          <div class="local-login__control">
            <input
              id="login-id"
              :value="modelValue"
              class="local-login__input"
              type="email"
              autocomplete="username"
              placeholder="이메일을 입력해 주세요"
              :disabled="loading"
              @input="$emit('update:modelValue', $event.target.value)"
            />
          </div>
        </div>

        <div class="local-login__field">
          <label class="local-login__label" for="login-password">비밀번호</label>
          <div class="local-login__control">
            <input
              id="login-password"
              v-model="password"
              class="local-login__input"
              :type="showPassword ? 'text' : 'password'"
              autocomplete="current-password"
              placeholder="비밀번호를 입력해 주세요"
              :disabled="loading"
            />
            <!-- 클릭만 되던 아이콘 → 버튼: 키보드로 켜고 끌 수 있고 화면낭독기가 읽는다 -->
            <button
              type="button"
              class="local-login__eye"
              :aria-label="showPassword ? '비밀번호 숨기기' : '비밀번호 보기'"
              :aria-pressed="showPassword"
              @click="showPassword = !showPassword"
            >
              <q-icon :name="showPassword ? 'visibility' : 'visibility_off'" size="24px" aria-hidden="true" />
            </button>
          </div>
          <p v-if="error" class="local-login__feedback" role="alert">{{ error }}</p>
        </div>
      </div>

      <button type="button" class="local-login__find" @click="$emit('find-password')">
        비밀번호 찾기
      </button>
    </div>

    <button type="submit" class="local-login__submit" :disabled="loading">로그인</button>
  </form>
</template>

<script>
/* 이메일·비밀번호 로그인 폼 — 강사·기관 관리자 로그인(/login/staff)과 QA 용 /login?local=1 이 함께 쓴다(#134).
 * 아이디(이메일)는 부모가 v-model 로 쥔다(개발용 대리 로그인이 목록에서 골라 채운다). 비밀번호는 여기서만 들고 있다 */
export default {
  name: 'LocalLoginForm',
  props: {
    modelValue: { type: String, default: '' },
    loading: { type: Boolean, default: false },
    error: { type: String, default: '' },
  },
  emits: ['update:modelValue', 'submit', 'find-password'],
  data() {
    return { password: '', showPassword: false };
  },
  methods: {
    onSubmit() {
      this.$emit('submit', this.password);
    },
  },
};
</script>

<style scoped>
.local-login__form {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

/* 221 = inner 186 + gap 16 + 비밀번호 찾기 19 */
.local-login__fields {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.local-login__inner {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

/* 77 = 라벨 19 + gap 4 + 입력 54 */
.local-login__field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.local-login__label {
  font-size: 16px;
  line-height: 19px;
  font-weight: 600;
  color: var(--db-text-title);
}

.local-login__control {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 54px;
  padding: 4px 14px;
  border: 1px solid var(--db-border);
  border-radius: 6px;
  background: var(--db-bg-white);
}

/* 입력칸 자체는 outline: none 이라 어디에 포커스가 있는지 안 보였다 — 칸 테두리로 보인다 */
.local-login__control:focus-within {
  border-color: var(--db-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--db-primary) 15%, transparent);
}

.local-login__input {
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

.local-login__input::placeholder {
  color: var(--db-text-sub);
}

.local-login__eye {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  padding: 2px;
  border: 0;
  border-radius: 4px;
  background: none;
  color: var(--db-text-title);
  cursor: pointer;
}

.local-login__eye:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 1px;
}

.local-login__feedback {
  margin: 0;
  font-size: 13px;
  line-height: 14px;
  font-weight: 400;
  color: var(--db-status-pink);
}

.local-login__find {
  padding: 0;
  border: 0;
  background: none;
  text-align: right;
  font-family: inherit;
  font-size: 16px;
  line-height: 19px;
  font-weight: 500;
  color: var(--db-primary);
  cursor: pointer;
}

/* 버튼 폭이 입력과 같은 450 — 회원가입 폼(516 vs 496)과 다르다 */
.local-login__submit {
  height: 53px;
  padding: 16px;
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

.local-login__submit:disabled {
  cursor: default;
}
</style>
