<template>
  <AuthShell :current-step="2" login-path="/login/staff">
    <div class="inst">
      <div class="inst__wrap">
        <div class="inst__main">
          <header class="inst__header">
            <h1 class="inst__title">기관 관리자 회원가입</h1>
            <p class="inst__sub">계정과 기관 정보를 입력해주세요.</p>
          </header>

          <div class="inst__sections">
            <template v-for="(section, order) in SECTIONS" :key="section.title">
              <div v-if="order > 0" class="inst__divider" />

              <section class="inst__section" :class="{ 'inst__section--loose': section.loose }">
                <h2 class="inst__section-title">{{ section.title }}</h2>

                <div v-for="(row, index) in section.rows" :key="index" class="inst__row">
                  <div v-for="key in row" :key="key" class="inst__field">
                    <label class="inst__label" :for="`inst-${key}`">
                      {{ FIELDS[key].label }}
                      <span v-if="FIELDS[key].required" class="inst__required">*</span>
                    </label>

                    <div class="inst__line">
                      <div class="inst__control">
                        <input
                          :id="`inst-${key}`"
                          v-model="form[key]"
                          class="inst__input"
                          :type="FIELDS[key].type || 'text'"
                          :placeholder="FIELDS[key].placeholder"
                          @input="onInput(key)"
                          @blur="onBlur(key)"
                        />
                      </div>
                      <button
                        v-if="FIELDS[key].check"
                        type="button"
                        class="inst__check"
                        @click="notReady"
                      >
                        유효성 확인
                      </button>
                    </div>

                    <p v-if="errors[key]" class="inst__feedback">{{ errors[key] }}</p>
                  </div>
                </div>
              </section>
            </template>
          </div>
        </div>

        <section class="inst-terms">
          <h2 class="inst-terms__title">서비스 이용약관 및 개인정보 수집·이용 동의</h2>

          <label class="inst-terms__check">
            <input v-model="agreeAll" type="checkbox" class="inst-terms__box" />
            <span class="inst-terms__all">전체 동의</span>
          </label>

          <div class="inst-terms__divider" />

          <div class="inst-terms__items">
            <div v-for="item in TERMS" :key="item.kind" class="inst-terms__row">
              <label class="inst-terms__check">
                <input v-model="agree[item.kind]" type="checkbox" class="inst-terms__box" />
                <span class="inst-terms__label">{{ item.label }}</span>
              </label>
              <button
                v-if="item.viewable"
                type="button"
                class="inst-terms__view"
                @click="openTerms(item.kind)"
              >
                내용 보기
              </button>
            </div>
          </div>
        </section>
      </div>

      <button type="button" class="inst__submit" :disabled="submitting" @click="submit">
        가입 신청
      </button>
    </div>

    <q-dialog v-model="termsOpen">
      <q-card style="width: 620px; max-width: 90vw">
        <q-card-section class="row items-center justify-between">
          <div class="text-h6">{{ termsTitle }}</div>
          <q-btn v-close-popup flat round icon="close" />
        </q-card-section>
        <q-separator />
        <q-card-section class="terms-body">{{ terms }}</q-card-section>
      </q-card>
    </q-dialog>
  </AuthShell>
</template>

<script>
import { mapActions } from 'pinia';
import AuthShell from '@/components/auth/AuthShell.vue';
import { signupLocalAccount } from '@/services/auth-api';
import { applyInstitutionJoin } from '@/services/institution-api';
import { getTermsContent } from '@/services/support-api';
import { useAuthStore } from '@/stores/auth-store';
import { formatPhone } from '@/utils/phone';
import { emptyAgreements, requiredTermsAgreed, SIGNUP_TERMS } from '@/utils/signup-terms';

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/* Figma 1050:17667. 정보입력과 달리 라벨이 표시된다 */
const FIELDS = {
  loginId: { label: '아이디', required: true, placeholder: '아이디를 입력해 주세요.' },
  password: {
    label: '비밀번호',
    required: true,
    type: 'password',
    placeholder: '비밀번호를 입력해주세요.',
  },
  passwordConfirm: {
    label: '비밀번호 확인',
    required: true,
    type: 'password',
    placeholder: '비밀번호를 다시 입력해 주세요.',
  },
  institutionName: { label: '기관명', required: true, placeholder: '기관 이름을 입력해주세요.' },
  businessNumber: {
    label: '사업자 등록번호',
    // 국세청 확인 API 연동 전 — "유효성 확인"은 "준비 중"만 띄웠다. 연동되면 check: true 로 되살린다
    placeholder: '-없이 숫자만 입력해주세요.',
  },
  representativeName: {
    label: '대표자명',
    required: true,
    placeholder: '대표자 이름을 입력해주세요.',
  },
  contactPhone: {
    label: '기관 전화번호',
    required: true,
    phone: true,
    placeholder: '010-0000-0000',
  },
  /* Figma엔 *가 없지만 서버 스키마가 required다. 의도된 이탈 — 52 결정 */
  contactEmail: { label: '기관 이메일', required: true, placeholder: 'example@email.com' },
  address: { label: '주소', placeholder: '주소를 검색해주세요.' },
  addressDetail: { label: '상세주소', placeholder: '상세주소를 입력해주세요.' },
  homepage: { label: '홈페이지', placeholder: '홈페이지 주소를 입력해주세요.' },
  adminName: { label: '관리자명', required: true, placeholder: '관리자 이름을 입력해주세요.' },
  adminPhone: { label: '휴대전화', required: true, phone: true, placeholder: '010-0000-0000' },
  adminEmail: { label: '이메일', placeholder: 'example@email.com' },
};

/* 행은 전부 3열. loose는 3섹션만 제목↔행 간격이 16인 실측 불일치 — 대장행 */
const SECTIONS = [
  { title: '1. 계정 정보', rows: [['loginId', 'password', 'passwordConfirm']] },
  {
    title: '2. 기관 정보',
    rows: [
      // 기관 유형은 선택지 목록도, 서버가 받는 자리도 없어 칸을 두지 않는다
      ['institutionName', 'businessNumber'],
      ['representativeName', 'contactPhone', 'contactEmail'],
      ['address', 'addressDetail', 'homepage'],
    ],
  },
  {
    title: '3. 관리자 정보',
    loose: true,
    rows: [['adminName', 'adminPhone', 'adminEmail']],
  },
];

const TERMS = SIGNUP_TERMS;

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
  institutionName: (v) => (String(v || '').trim() ? '' : '필수 입력 항목입니다.'),
  representativeName: (v) => (String(v || '').trim() ? '' : '필수 입력 항목입니다.'),
  contactPhone: (v) => (String(v || '').trim() ? '' : '필수 입력 항목입니다.'),
  adminName: (v) => (String(v || '').trim() ? '' : '필수 입력 항목입니다.'),
  adminPhone: (v) => (String(v || '').trim() ? '' : '필수 입력 항목입니다.'),
  contactEmail: (v) => {
    const text = String(v || '').trim();
    if (!text) return '필수 입력 항목입니다.';
    return EMAIL.test(text) ? '' : '올바른 이메일을 입력해 주세요.';
  },
  adminEmail: (v) => {
    const text = String(v || '').trim();
    if (!text) return '';
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

/* 서버 바디(InstitutionJoinApplicationCreateRequest)에 있는 필드만 추린다.
 * 사업자 등록번호·홈페이지는 받는 자리가 없어 보내지 않는다 — 대장행.
 */
function buildInstitutionBody(form) {
  const address = [form.address, form.addressDetail]
    .map((part) => String(part || '').trim())
    .filter(Boolean)
    .join(' ');
  return {
    institutionName: form.institutionName.trim(),
    representativeName: form.representativeName.trim(),
    contactEmail: form.contactEmail.trim(),
    contactPhone: form.contactPhone.trim(),
    address: address || null,
  };
}

export default {
  name: 'InstitutionSignupPage',
  components: { AuthShell },
  data() {
    return {
      FIELDS,
      SECTIONS,
      TERMS,
      form: {
        loginId: '',
        password: '',
        passwordConfirm: '',
        institutionName: '',
        businessNumber: '',
        representativeName: '',
        contactPhone: '',
        contactEmail: '',
        address: '',
        addressDetail: '',
        homepage: '',
        adminName: '',
        adminPhone: '',
        adminEmail: '',
      },
      errors: {},
      agree: emptyAgreements(),
      termsOpen: false,
      terms: '',
      termsTitle: '이용약관',
      submitting: false,
    };
  },
  computed: {
    agreeAll: {
      get() {
        return TERMS.every((item) => this.agree[item.kind]);
      },
      set(value) {
        TERMS.forEach((item) => {
          this.agree[item.kind] = value;
        });
      },
    },
  },
  methods: {
    ...mapActions(useAuthStore, ['applyAuthToken']),
    onInput(key) {
      if (FIELDS[key].phone) this.form[key] = formatPhone(this.form[key]);
    },
    onBlur(key) {
      if (this.errors[key]) this.errors = validateForm(this.form);
    },
    notReady() {
      this.$q.notify({
        type: 'info',
        message: '사업자 등록번호 확인은 준비 중입니다.',
        position: 'top',
      });
    },
    async openTerms(kind) {
      this.termsTitle = kind === 'USAGE' ? '이용약관' : '개인정보 수집·이용';
      this.terms = '';
      this.termsOpen = true;
      try {
        const content = await getTermsContent(kind);
        this.termsTitle = content?.title || this.termsTitle;
        this.terms = content?.body || '';
      } catch {
        // 하드코딩 사본을 두지 않는다 — 약관은 UNIQUE(type, version) 이력 구조라 서버 정본이
        // 유일해야 한다. 프론트 사본을 남기면 "화면에 보인 문안"과 "동의 이력이 가리키는 버전"이
        // 어긋날 수 있고, 그게 빈 화면보다 나쁘다.
        this.terms = '약관을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
      }
    },
    async submit() {
      if (!requiredTermsAgreed(this.agree)) {
        this.$q.notify({ type: 'warning', message: '필수 약관에 동의해 주세요.' });
        return;
      }
      this.errors = validateForm(this.form);
      if (Object.keys(this.errors).length) return;

      this.submitting = true;
      try {
        const tokenPayload = await signupLocalAccount({
          email: this.form.adminEmail.trim() || this.form.loginId.trim(),
          password: this.form.password,
          name: this.form.adminName.trim(),
          phone: this.form.adminPhone.trim() || null,
          birthDate: null,
          agreements: this.agree,
        });
        await this.applyAuthToken(tokenPayload);
        await applyInstitutionJoin(buildInstitutionBody(this.form));
        this.$q.notify({
          type: 'positive',
          message: '가입 및 기관 등록 신청이 접수되었습니다.',
          position: 'top',
        });
        this.$router.push({ path: '/signup/complete', query: { role: 'INSTITUTION_ADMIN' } });
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error.message || '가입 신청에 실패했습니다.',
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
/* 본문만 1200이고 스테퍼는 셸에서 880 고정 — 여기서 늘리지 말 것.
 * Figma가 가로 padding 10을 선언해놓고 렌더엔 반영이 없다(컨테이너·inner wrap x좌표 동일) */
.inst {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 32px;
  width: 1200px;
  margin: 0 auto;
  padding: 10px 0;
}

.inst__wrap {
  display: flex;
  flex-direction: column;
  width: 100%;
  gap: 24px;
}

.inst__main {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 32px;
}

/* 섹션 사이 = 16 + 구분선 1px + 16. 폭을 꽉 채우므로 CENTER가 무효 = 좌측 */
.inst__sections {
  display: flex;
  flex-direction: column;
  width: 100%;
  gap: 16px;
}

.inst__divider {
  height: 1px;
  background: var(--db-border-rt);
}

.inst__header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 9px;
}

.inst__title {
  margin: 0;
  font-size: 40px;
  line-height: 48px;
  font-weight: 700;
  color: var(--db-text-body);
}

.inst__sub {
  margin: 0;
  text-align: center;
  font-size: 16px;
  line-height: 19px;
  font-weight: 400;
  color: var(--db-text-sub);
}

/* 106 = 21 + 8 + 77 / 276 = 21 + 8 + 77 + 8 + 77 + 8 + 77 / 114 = 21 + 16 + 77 */
.inst__section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.inst__section--loose {
  gap: 16px;
}

.inst__section-title {
  margin: 0;
  font-size: 18px;
  line-height: 21px;
  font-weight: 700;
  color: var(--db-accent);
}

/* 389 × 3 + 16 × 2 = 1199. 285는 4열이 아니라 사업자 등록번호 칸을 쪼갠 값이다 */
.inst__row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.inst__field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.inst__label {
  font-size: 16px;
  line-height: 19px;
  font-weight: 600;
  color: var(--db-text-title);
}

/* line-height를 라벨과 같은 19로 둬도 12px 글꼴은 baseline 아래로 1.2px 더 내려가
 * 라인박스 합집합이 20.2 → 21이 된다. 인라인 박스 기여를 0으로 만들어 스트럿(19)만 남긴다.
 * 글리프는 baseline 위치 그대로 그려진다 — height로 눌러 덮는 게 아니다. */
.inst__required {
  font-size: 13px;
  line-height: 0;
  font-weight: 400;
  color: var(--db-status-pink);
}

.inst__line {
  display: flex;
  gap: 8px;
  min-width: 0;
}

.inst__control {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
  height: 54px;
  padding: 4px 14px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 6px;
}

.inst__input {
  flex: 1;
  min-width: 0;
  padding: 0;
  border: 0;
  outline: none;
  background: transparent;
  font-family: inherit;
  font-size: 16px;
  line-height: 19px;
  font-weight: 400;
  color: var(--db-text-title);
}

.inst__input::placeholder {
  color: var(--db-text-sub);
}

/* API가 없어도 흐리게 만들지 않는다 — 클릭하면 안내만 뜬다 */
.inst__check {
  flex: 0 0 96px;
  height: 54px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: var(--db-fill-strong);
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-on-color);
  white-space: nowrap;
  cursor: pointer;
}

.inst__feedback {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 14px;
  color: var(--db-status-pink);
}

/* 정보입력(516)과 구성·문구 동일, 폭만 1200 */
.inst-terms {
  display: flex;
  flex-direction: column;
  width: 100%;
  gap: 20px;
}

.inst-terms__title {
  margin: 0;
  font-size: 18px;
  line-height: 21px;
  font-weight: 600;
  color: var(--db-text-title);
}

.inst-terms__check {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 20px;
  cursor: pointer;
}

.inst-terms__items {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.inst-terms__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 20px;
}

.inst-terms__box {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  margin: 0;
  appearance: none;
  background: var(--db-bg-white);
  border: 1.67px solid var(--db-border);
  border-radius: 3.33px;
  cursor: pointer;
}

.inst-terms__box:checked {
  background: var(--db-accent-fill);
  border-color: var(--db-accent);
}

.inst-terms__box:checked::after {
  display: block;
  width: 5px;
  height: 9px;
  margin: 1px auto 0;
  border: solid var(--db-bg-white);
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
  content: '';
}

.inst-terms__all,
.inst-terms__label {
  font-size: 16px;
  line-height: 19px;
  font-weight: 500;
  color: var(--db-text-sub);
}

.inst-terms__divider {
  height: 1px;
  background: var(--db-border-rt);
}

.inst-terms__view {
  padding: 0;
  border: 0;
  background: none;
  font-family: inherit;
  font-size: 16px;
  line-height: 19px;
  font-weight: 700;
  color: var(--db-accent);
  cursor: pointer;
}

.inst__submit {
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

.terms-body {
  white-space: pre-wrap;
  max-height: 60vh;
  overflow: auto;
}

/* 폰: 1200 컨테이너·3열 입력·496 버튼이 375 폭을 넘었다 — 입력은 1열 */
@media (max-width: 1023px) {
  .inst {
    gap: 24px;
    width: 100%;
    max-width: 1200px;
    padding: 16px;
  }

  .inst__title {
    font-size: 28px;
    line-height: 36px;
  }

  .inst__row {
    grid-template-columns: minmax(0, 1fr);
  }

  .inst-terms__row {
    gap: 12px;
    height: auto;
  }

  .inst-terms__row .inst-terms__check {
    flex: 1;
    min-width: 0;
  }

  .inst-terms__view {
    flex-shrink: 0;
    white-space: nowrap;
  }

  .inst__submit {
    width: 100%;
    max-width: 496px;
  }
}
</style>
