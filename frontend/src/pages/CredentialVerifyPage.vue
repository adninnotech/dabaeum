<template>
  <q-page class="cv">
    <div class="learner-container cv__body">
      <header class="cv__head">
        <h1 class="cv__title">수료증 번호로 진짜인지 확인합니다</h1>
        <p class="cv__lead">
          로그인 없이 누구나 확인할 수 있습니다. 정식 발급 여부, 위·변조 여부, 폐기·만료 여부를 함께
          봅니다.
        </p>

        <form class="cv__form" novalidate @submit.prevent="submit">
          <q-input
            v-model="input"
            outlined
            dense
            class="cv__input"
            label="수료증 번호"
            placeholder="예: CERT-27D85BB1…"
            autocomplete="off"
            spellcheck="false"
            :error="!!inputError"
            :error-message="inputError"
            @update:model-value="inputError = ''"
          />
          <q-btn
            type="submit"
            unelevated
            no-caps
            color="primary"
            class="cv__submit"
            label="확인하기"
            :loading="verifying"
          />
        </form>
      </header>

      <section
        v-if="outcome"
        class="cv__result"
        :class="`cv__result--${outcome.tone}`"
        role="status"
        aria-live="polite"
      >
        <q-icon :name="outcome.icon" size="32px" class="cv__result-icon" aria-hidden="true" />
        <div class="cv__result-main">
          <h2 class="cv__result-title">{{ outcome.title }}</h2>
          <p class="cv__result-body">{{ outcome.body }}</p>
          <p v-if="conditional" class="cv__result-note">{{ conditionalNote }}</p>
          <dl class="cv__facts">
            <div class="cv__fact">
              <dt>수료증 번호</dt>
              <dd class="cv__mono">{{ checkedNo }}</dd>
            </div>
            <div v-if="result?.issuerName" class="cv__fact">
              <dt>발급기관</dt>
              <dd>{{ result.issuerName }}</dd>
            </div>
            <div v-if="result?.issuedAt" class="cv__fact">
              <dt>발급일</dt>
              <dd>{{ formatDateKst(result.issuedAt, '-') }}</dd>
            </div>
            <div class="cv__fact">
              <dt>확인 시각</dt>
              <dd>{{ formatDateTimeKst(checkedAt, '-') }}</dd>
            </div>
          </dl>
        </div>
      </section>

      <AppErrorBanner v-else-if="errorMessage" :message="errorMessage">
        <template #action>
          <q-btn flat color="negative" label="다시 시도" @click="verify(checkedNo, 'API')" />
        </template>
      </AppErrorBanner>

      <p class="cv__foot">
        이름·생년월일·연락처 등 개인정보는 표시하지 않습니다. 소지자가 보여 준 수료증 내용과 번호가
        같은지 함께 확인해 주세요.
      </p>
    </div>
  </q-page>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import { verifyCredentialPublic } from '@/services/credential-api';
import {
  CONDITIONAL_NOTE,
  CREDENTIAL_NO_FORMAT_ERROR,
  CREDENTIAL_NO_PATTERN,
  verifyResultOf,
} from '@/utils/credential-verify';
import { formatDateKst, formatDateTimeKst } from '@/utils/datetime';

/* 공개 수료증 검증 — /verify(직접 입력), /verify/:credentialNo(포트폴리오·QR 링크).
 * 링크로 들어오면 바로 확인한다. 검증 이력 유형은 링크면 QR, 직접 입력이면 API */
export default {
  name: 'CredentialVerifyPage',
  components: { AppErrorBanner },
  data() {
    return {
      input: '',
      inputError: '',
      verifying: false,
      result: null,
      checkedNo: '',
      errorMessage: '',
      // 폼으로 주소를 바꾼 경우 — 이어지는 라우트 변경을 직접 입력(API)으로 기록한다
      submittedFromForm: false,
      conditionalNote: CONDITIONAL_NOTE,
    };
  },
  computed: {
    outcome() {
      return this.result ? verifyResultOf(this.result.result) : null;
    },
    conditional() {
      return this.result?.verificationScope === 'PLATFORM_CONDITIONAL';
    },
    checkedAt() {
      return this.result?.verifiedAt || '';
    },
  },
  watch: {
    '$route.params.credentialNo': {
      immediate: true,
      handler(value) {
        const credentialNo = String(value || '').trim();
        const type = this.submittedFromForm ? 'API' : 'QR';
        this.submittedFromForm = false;
        this.input = credentialNo;
        this.result = null;
        this.errorMessage = '';
        if (!credentialNo) return;
        if (!CREDENTIAL_NO_PATTERN.test(credentialNo)) {
          this.inputError = CREDENTIAL_NO_FORMAT_ERROR;
          return;
        }
        this.verify(credentialNo, type);
      },
    },
  },
  methods: {
    formatDateKst,
    formatDateTimeKst,
    submit() {
      const credentialNo = this.input.trim();
      if (!credentialNo) {
        this.inputError = '수료증 번호를 입력해 주세요.';
        return;
      }
      if (!CREDENTIAL_NO_PATTERN.test(credentialNo)) {
        this.inputError = CREDENTIAL_NO_FORMAT_ERROR;
        return;
      }
      // 같은 번호를 다시 확인하면 주소가 그대로라 watch 가 돌지 않는다 — 바로 확인한다
      if (credentialNo === this.$route.params.credentialNo) {
        this.verify(credentialNo, 'API');
        return;
      }
      this.submittedFromForm = true;
      // 이동이 막히면 표시가 남아 다음 링크 진입을 API 로 기록한다 — 끈다
      this.$router
        .push({ name: 'credential-verify-no', params: { credentialNo } })
        .then((failure) => {
          if (failure) this.submittedFromForm = false;
        })
        .catch(() => {
          this.submittedFromForm = false;
        });
    },
    async verify(credentialNo, verificationType) {
      this.verifying = true;
      this.errorMessage = '';
      this.checkedNo = credentialNo;
      try {
        const result = await verifyCredentialPublic(credentialNo, verificationType);
        // 응답 사이 다른 번호로 바뀌었으면 버린다
        if (this.checkedNo === credentialNo) this.result = result;
      } catch (error) {
        if (this.checkedNo !== credentialNo) return;
        this.result = null;
        this.errorMessage =
          error?.status === 400
            ? '수료증 번호 형식이 올바르지 않습니다.'
            : error?.message || '수료증을 확인하지 못했습니다. 잠시 뒤 다시 시도해 주세요.';
      } finally {
        // 겹친 요청 중 먼저 끝난 쪽이 로딩을 끄지 않게 — 마지막 번호의 요청만 끈다
        if (this.checkedNo === credentialNo) this.verifying = false;
      }
    },
  },
};
</script>

<style scoped>
.cv {
  padding: 32px 0;
  background: var(--db-bg-page);
}

.cv__body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  /* .learner-container 의 max-width: 100% 를 덮지 않는다 — 폰에서 가로로 넘친다 */
  max-width: min(720px, 100%);
}

.cv__head {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 24px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.cv__title {
  margin: 0;
  font-size: 24px;
  line-height: 32px;
  font-weight: 700;
  color: var(--db-text-title);
  word-break: keep-all;
  overflow-wrap: anywhere;
}

.cv__lead {
  margin: 0;
  font-size: 14px;
  line-height: 21px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.cv__form {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 12px;
}

.cv__input {
  flex: 1;
  min-width: 0;
}

.cv__submit {
  height: 40px;
  padding: 0 20px;
  border-radius: 6px;
  white-space: nowrap;
}

.cv__result {
  display: flex;
  gap: 16px;
  padding: 24px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-left-width: 4px;
  border-radius: 8px;
}

.cv__result--ok {
  border-left-color: var(--db-status-green);
}

.cv__result--ok .cv__result-icon {
  color: var(--db-status-green);
}

.cv__result--warn {
  border-left-color: var(--db-status-orange);
}

.cv__result--warn .cv__result-icon {
  color: var(--db-status-orange);
}

.cv__result--bad {
  border-left-color: var(--db-status-pink);
}

.cv__result--bad .cv__result-icon {
  color: var(--db-status-pink);
}

.cv__result-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.cv__result-title {
  margin: 0;
  font-size: 20px;
  line-height: 28px;
  font-weight: 700;
  color: var(--db-text-title);
}

.cv__result-body,
.cv__result-note {
  margin: 0;
  font-size: 14px;
  line-height: 21px;
  color: var(--db-text-body);
  word-break: keep-all;
}

.cv__result-note {
  padding: 8px 12px;
  background: var(--db-bg-subtle);
  border-radius: 6px;
}

.cv__facts {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 8px 0 0;
}

.cv__fact {
  display: flex;
  gap: 12px;
  font-size: 14px;
}

.cv__fact dt {
  width: 72px;
  flex-shrink: 0;
  color: var(--db-text-sub);
}

.cv__fact dd {
  margin: 0;
  min-width: 0;
  color: var(--db-text-body);
  overflow-wrap: anywhere;
}

.cv__mono {
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
}

.cv__foot {
  margin: 0;
  padding: 0 4px;
  font-size: 13px;
  line-height: 18px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

@media (max-width: 599px) {
  .cv {
    padding: 16px 0;
  }

  .cv__head,
  .cv__result {
    padding: 20px 16px;
  }

  .cv__title {
    font-size: 20px;
    line-height: 28px;
  }

  .cv__form {
    flex-direction: column;
    align-items: stretch;
  }

  .cv__result {
    flex-direction: column;
    gap: 8px;
  }
}
</style>
