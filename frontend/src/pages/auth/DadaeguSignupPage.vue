<template>
  <!-- 두 단계를 한 주소에서 넘긴다 — 가입 토큰은 메모리에만 있어 주소를 나누면 새로고침에 끊긴다 -->
  <AuthShell :current-step="step === 'consent' ? 1 : 2">
    <div class="dadaegu-signup">
      <div class="dadaegu-signup__wrap">
        <header class="dadaegu-signup__header">
          <h1 ref="title" class="dadaegu-signup__title" tabindex="-1">회원가입</h1>
          <!-- 문장 단위로 줄을 바꾼다 — 가운뎃점에서 끊기지 않게 -->
          <p class="dadaegu-signup__sub">
            <span class="dadaegu-signup__sentence">다대구 본인 인증이 끝났습니다.</span>
            <span class="dadaegu-signup__sentence">{{
              step === 'consent'
                ? '약관과 개인정보 수집·이용에 동의해 주세요.'
                : '관심 분야는 골라도 되고 건너뛰어도 됩니다.'
            }}</span>
          </p>
        </header>

        <!-- 가입 토큰 없음: 새로고침·직접 접근. 토큰은 메모리에만 있다 -->
        <section v-if="!identity" class="dadaegu-signup__notice" role="status">
          <q-icon name="info_outline" size="24px" class="dadaegu-signup__notice-icon" />
          <p class="dadaegu-signup__notice-text">
            가입 정보가 없습니다. 다대구 로그인을 다시 하면 이 화면으로 돌아옵니다.
          </p>
        </section>

        <template v-else>
          <section
            class="dadaegu-signup__identity"
            :class="{ 'dadaegu-signup__identity--expired': expired }"
          >
            <q-icon
              :name="expired ? 'timer_off' : 'verified_user'"
              size="24px"
              class="dadaegu-signup__identity-icon"
            />
            <div class="dadaegu-signup__identity-text">
              <span class="dadaegu-signup__identity-label">다대구 인증 이름</span>
              <strong class="dadaegu-signup__identity-name">{{ identity.maskedName || '-' }}</strong>
            </div>
            <span
              class="dadaegu-signup__timer"
              :class="{ 'dadaegu-signup__timer--urgent': urgent }"
              role="timer"
              :aria-label="expired ? '가입 시간 만료' : `남은 시간 ${remainingLabel}`"
            >
              {{ expired ? '시간 만료' : remainingLabel }}
            </span>
          </section>
          <p class="dadaegu-signup__timer-note" :class="{ 'dadaegu-signup__timer-note--expired': expired }">
            {{
              expired
                ? '가입 시간(10분)이 지났습니다. 다대구 로그인부터 다시 해 주세요.'
                : '인증 후 10분 안에 가입을 마쳐 주세요. 시간이 지나면 다대구 로그인부터 다시 합니다.'
            }}
          </p>

          <SignupTermsConsent v-if="step === 'consent'" v-model="agreements" />
          <InterestPicker
            v-else
            v-model="interestCodes"
            v-model:agreed="interestAgreed"
            :disabled="submitting"
            separate-from-terms
          />
        </template>
      </div>

      <template v-if="identity && !expired">
        <!-- 관심 분야 입력을 끈 동안(법무 확인 전)엔 약관 동의가 마지막 단계다 -->
        <button
          v-if="step === 'consent' && interestEnabled"
          type="button"
          class="dadaegu-signup__submit dadaegu-signup__submit--next"
          :disabled="!termsReady"
          @click="goStep('form')"
        >
          다음
        </button>
        <button
          v-else-if="step === 'consent'"
          type="button"
          class="dadaegu-signup__submit dadaegu-signup__submit--next"
          :disabled="!termsReady || submitting"
          @click="submit"
        >
          {{ submitting ? '가입하는 중…' : '가입 완료' }}
        </button>
        <div v-else class="dadaegu-signup__actions">
          <button
            type="button"
            class="dadaegu-signup__submit"
            :disabled="submitting"
            @click="submit"
          >
            {{ submitting ? '가입하는 중…' : interestCodes.length ? '가입 완료' : '건너뛰고 가입 완료' }}
          </button>
          <button
            type="button"
            class="dadaegu-signup__back"
            :disabled="submitting"
            @click="goStep('consent')"
          >
            이전 단계(약관 동의)
          </button>
        </div>
      </template>
      <router-link v-else :to="loginLink" class="dadaegu-signup__submit dadaegu-signup__submit--link">
        다대구 로그인 다시 하기
      </router-link>
    </div>
  </AuthShell>
</template>

<script>
import { mapActions, mapState } from 'pinia';
import AuthShell from '@/components/auth/AuthShell.vue';
import InterestPicker from '@/components/auth/InterestPicker.vue';
import SignupTermsConsent from '@/components/auth/SignupTermsConsent.vue';
import { isInterestInputEnabled } from '@/config/features';
import { useAuthStore } from '@/stores/auth-store';
import { emptyAgreements, requiredTermsAgreed } from '@/utils/signup-terms';

const TOKEN_LIFETIME_MS = 10 * 60 * 1000;
const URGENT_MS = 60 * 1000;
const TICK_MS = 1000;

const SIGNUP_ERROR_MESSAGE = {
  409: '이미 가입이 끝난 다대구 계정입니다. 다대구로 다시 로그인해 주세요.',
};

function formatRemaining(ms) {
  const totalSeconds = Math.max(0, Math.ceil(ms / 1000));
  const minutes = String(Math.floor(totalSeconds / 60)).padStart(2, '0');
  const seconds = String(totalSeconds % 60).padStart(2, '0');
  return `${minutes}:${seconds}`;
}

/* 다대구 첫 로그인 뒤 추가 가입 단계 — POST /auth/dadaegu/login 이 registrationRequired=true 를 준 경우.
 * ① 약관·개인정보 동의 → ② 관심 분야(선택, #142 — 구·군 대신). 동의 결과는 따로 보내지 않고 가입 요청에 싣는다.
 * 관심 분야 입력을 끈 동안(isInterestInputEnabled false)엔 ①에서 바로 가입한다.
 * 피그마에 없는 화면이라 예전 자체 가입 화면의 폭·입력 행·약관 블록을 그대로 따른다 */
export default {
  name: 'DadaeguSignupPage',
  components: { AuthShell, InterestPicker, SignupTermsConsent },
  data() {
    return {
      interestEnabled: isInterestInputEnabled(),
      interestCodes: [],
      interestAgreed: false,
      // 'consent'(약관·개인정보 동의) → 'form'(관심 분야, 선택)
      step: 'consent',
      agreements: emptyAgreements(),
      submitting: false,
      deadline: 0,
      now: Date.now(),
      timerId: null,
      // 401 은 "토큰 만료·재사용" — 남은 시간과 관계없이 만료로 본다
      forcedExpired: false,
      /* 화면에 들어올 때의 가입 토큰 정보(이름·만료 시각). 스토어는 401 때 토큰을 비우므로
       * 화면은 이 사본으로 그린다 — 그래야 "가입 정보 없음"이 아니라 "시간 만료"로 보인다 */
      identity: null,
    };
  },
  computed: {
    ...mapState(useAuthStore, ['pendingDadaeguRegistration']),
    termsReady() {
      return requiredTermsAgreed(this.agreements);
    },
    remainingMs() {
      return this.deadline - this.now;
    },
    expired() {
      return this.forcedExpired || (!!this.identity && this.remainingMs <= 0);
    },
    urgent() {
      return !this.expired && this.remainingMs <= URGENT_MS;
    },
    remainingLabel() {
      return formatRemaining(this.remainingMs);
    },
    redirect() {
      return this.$route.query.redirect ? String(this.$route.query.redirect) : '';
    },
    loginLink() {
      return this.redirect ? { path: '/login', query: { redirect: this.redirect } } : '/login';
    },
  },
  mounted() {
    const pending = this.pendingDadaeguRegistration;
    if (!pending) return;
    this.identity = { maskedName: pending.maskedName, expiresAt: pending.expiresAt };
    const parsed = Date.parse(pending.expiresAt || '');
    this.deadline = Number.isNaN(parsed) ? Date.now() + TOKEN_LIFETIME_MS : parsed;
    this.timerId = window.setInterval(this.tick, TICK_MS);
  },
  beforeUnmount() {
    this.stopTimer();
  },
  methods: {
    ...mapActions(useAuthStore, ['signupWithDadaegu']),
    tick() {
      this.now = Date.now();
      if (this.expired) this.stopTimer();
    },
    stopTimer() {
      if (this.timerId) window.clearInterval(this.timerId);
      this.timerId = null;
    },
    // 단계를 바꾸면 화면 위 제목으로 초점을 옮긴다 — 긴 약관을 내려 읽은 위치에 남지 않게
    goStep(step) {
      if (step === 'form' && !this.termsReady) return;
      this.step = step;
      this.$nextTick(() => {
        window.scrollTo(0, 0);
        this.$refs.title?.focus();
      });
    },
    async submit() {
      if (this.expired || this.submitting) return;
      if (!this.termsReady) {
        this.$q.notify({ type: 'warning', message: '필수 약관에 동의해 주세요.' });
        this.goStep('consent');
        return;
      }

      this.submitting = true;
      try {
        const ok = await this.signupWithDadaegu({
          agreements: this.agreements,
          // 입력을 끈 동안엔 보내지 않는다 — 서버가 모르는 동의를 기록하지 않게
          ...(this.interestEnabled ? { interestCodes: this.interestCodes, agreedToInterest: this.interestAgreed } : {}),
        });
        if (!ok) {
          this.$q.notify({
            type: 'negative',
            message: '가입은 끝났지만 로그인하지 못했습니다. 다대구로 다시 로그인해 주세요.',
            position: 'top',
          });
          this.$router.replace(this.loginLink);
          return;
        }
        this.stopTimer();
        this.$q.notify({ type: 'positive', message: '회원가입이 완료되었습니다.', position: 'top' });
        this.$router.replace(
          this.redirect || { path: '/signup/complete', query: { role: 'LEARNER' } },
        );
      } catch (error) {
        this.handleSignupError(error);
      } finally {
        this.submitting = false;
      }
    },
    handleSignupError(error) {
      if (error?.status === 401) {
        this.forcedExpired = true;
        this.stopTimer();
        return;
      }
      this.$q.notify({
        type: 'negative',
        message: SIGNUP_ERROR_MESSAGE[error?.status] || error?.message || '가입에 실패했습니다.',
        position: 'top',
      });
      if (error?.status === 409) this.$router.replace(this.loginLink);
    },
  },
};
</script>

<style scoped>
/* 예전 자체 가입 화면과 같은 뼈대: 컨테이너 880 · 폼 516 · 버튼 496 */
.dadaegu-signup {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 32px;
  width: 880px;
  margin: 0 auto;
  padding: 10px;
}

.dadaegu-signup__wrap {
  display: flex;
  flex-direction: column;
  gap: 24px;
  width: 516px;
  margin: 0 auto;
}

.dadaegu-signup__header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 9px;
  margin-bottom: 8px;
}

.dadaegu-signup__title {
  margin: 0;
  outline: none;
  font-size: 40px;
  line-height: 48px;
  font-weight: 700;
  color: var(--db-text-body);
}

.dadaegu-signup__sentence {
  display: inline-block;
}

/* 템플릿 줄바꿈 공백은 컴파일 때 빠진다 — 한 줄에 놓일 때 두 문장이 붙지 않게 */
.dadaegu-signup__sentence:not(:last-child) {
  margin-right: 0.3em;
}

.dadaegu-signup__sub {
  margin: 0;
  text-align: center;
  font-size: 16px;
  line-height: 24px;
  color: var(--db-text-sub);
  word-break: keep-all;
  text-wrap: balance;
}

/* 인증 확인 행 — 입력 행과 같은 54px 높이·r6, 배경만 accent-soft 로 "이미 채워진 값"임을 보인다 */
.dadaegu-signup__identity {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 54px;
  padding: 8px 14px;
  background: var(--db-accent-soft);
  border: 1px solid var(--db-accent);
  border-radius: 6px;
}

.dadaegu-signup__identity--expired {
  background: var(--db-bg-page);
  border-color: var(--db-border);
}

.dadaegu-signup__identity-icon {
  flex-shrink: 0;
  color: var(--db-accent);
}

.dadaegu-signup__identity--expired .dadaegu-signup__identity-icon {
  color: var(--db-text-sub);
}

.dadaegu-signup__identity-text {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: baseline;
  gap: 10px;
}

.dadaegu-signup__identity-label {
  flex-shrink: 0;
  font-size: 14px;
  line-height: 17px;
  color: var(--db-text-sub);
}

.dadaegu-signup__identity-name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 18px;
  line-height: 22px;
  font-weight: 700;
  color: var(--db-text-title);
}

.dadaegu-signup__timer {
  flex-shrink: 0;
  font-size: 16px;
  line-height: 19px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--db-text-body);
}

.dadaegu-signup__timer--urgent,
.dadaegu-signup__identity--expired .dadaegu-signup__timer {
  color: var(--db-status-pink);
}

.dadaegu-signup__timer-note {
  margin: -16px 0 0;
  font-size: 13px;
  line-height: 16px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.dadaegu-signup__timer-note--expired {
  color: var(--db-status-pink);
}

.dadaegu-signup__notice {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 16px;
  background: var(--db-bg-page);
  border: 1px solid var(--db-border);
  border-radius: 6px;
}

.dadaegu-signup__notice-icon {
  flex-shrink: 0;
  color: var(--db-text-sub);
}

.dadaegu-signup__notice-text {
  margin: 0;
  word-break: keep-all;
  font-size: 15px;
  line-height: 22px;
  color: var(--db-text-body);
}

.dadaegu-signup__submit {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 496px;
  height: 53px;
  align-self: center;
  border: 0;
  border-radius: 8px;
  background: var(--db-primary); /* 사이트 주 버튼과 같은 남색 */
  font-family: inherit;
  font-size: 18px;
  line-height: 21px;
  font-weight: 600;
  white-space: nowrap;
  color: var(--db-on-color);
  text-decoration: none;
  cursor: pointer;
}

.dadaegu-signup__submit:focus-visible {
  outline: 2px solid var(--db-text-title);
  outline-offset: 2px;
}

/* opacity 는 Quasar 전역 `[disabled]{opacity:.6!important}` 가 가져간다 */
.dadaegu-signup__submit:disabled {
  cursor: progress;
}

/* [다음]은 기다림이 아니라 필수 동의 전이라 막힌 것 */
.dadaegu-signup__submit--next:disabled {
  cursor: not-allowed;
}

.dadaegu-signup__actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.dadaegu-signup__back {
  padding: 4px 8px;
  border: 0;
  background: none;
  font-family: inherit;
  font-size: 15px;
  line-height: 20px;
  font-weight: 600;
  white-space: nowrap;
  color: var(--db-text-sub);
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.dadaegu-signup__back:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

@media (max-width: 1023px) {
  .dadaegu-signup {
    gap: 24px;
    width: 100%;
    max-width: 880px;
    padding: 16px;
  }

  .dadaegu-signup__wrap {
    width: 100%;
    max-width: 516px;
  }

  .dadaegu-signup__title {
    font-size: 28px;
    line-height: 36px;
  }

  .dadaegu-signup__identity-text {
    flex-direction: column;
    gap: 2px;
  }

  .dadaegu-signup__submit {
    width: 100%;
    max-width: 496px;
  }
}
</style>
