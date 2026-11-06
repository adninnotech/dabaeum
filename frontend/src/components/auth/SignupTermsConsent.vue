<template>
  <section class="signup-terms">
    <h2 class="signup-terms__title">서비스 이용약관 및 개인정보 수집·이용 동의</h2>

    <label class="signup-terms__check">
      <input
        type="checkbox"
        class="signup-terms__box"
        :checked="allAgreed"
        @change="setAll($event.target.checked)"
      />
      <span class="signup-terms__all">전체 동의</span>
    </label>

    <div class="signup-terms__divider" />

    <div class="signup-terms__items">
      <div v-for="item in SIGNUP_TERMS" :key="item.kind" class="signup-terms__row">
        <div class="signup-terms__line">
          <label class="signup-terms__check">
            <input
              type="checkbox"
              class="signup-terms__box"
              :checked="!!local[item.kind]"
              @change="setOne(item.kind, $event.target.checked)"
            />
            <span class="signup-terms__label">{{ item.label }}</span>
          </label>
          <!-- 팝업 대신 그 자리에서 펼친다(#116) — 체크 줄과 내용이 떨어지지 않게 -->
          <button
            type="button"
            class="signup-terms__toggle"
            :class="{ 'signup-terms__toggle--open': !!expanded[item.kind] }"
            :aria-expanded="expanded[item.kind] ? 'true' : 'false'"
            :aria-controls="panelId(item.kind)"
            :aria-label="`${TERMS_TITLE[item.kind]} ${expanded[item.kind] ? '접기' : '자세히 보기'}`"
            @click="toggle(item.kind)"
          >
            {{ expanded[item.kind] ? '접기' : '자세히 보기' }}
            <q-icon name="expand_more" size="18px" class="signup-terms__chevron" />
          </button>
        </div>

        <!-- v-show: 접혀 있어도 aria-controls 가 가리킬 요소가 있게 -->
        <div
          v-show="expanded[item.kind]"
          :id="panelId(item.kind)"
          class="signup-terms__panel"
        >
          <!-- 법정 고지 표를 먼저, 전체 본문은 그 아래 -->
          <table v-if="notices[item.kind]" class="signup-terms__notice">
            <caption class="signup-terms__notice-caption">{{ notices[item.kind].caption }}</caption>
            <colgroup>
              <col class="signup-terms__notice-col" />
              <col />
            </colgroup>
            <tbody>
              <tr v-for="row in notices[item.kind].rows" :key="row.key">
                <th scope="row" class="signup-terms__notice-label">{{ row.label }}</th>
                <td class="signup-terms__notice-text">
                  <strong v-if="row.emphasis" class="signup-terms__notice-strong">{{ row.text }}</strong>
                  <template v-else>{{ row.text }}</template>
                </td>
              </tr>
            </tbody>
          </table>

          <p v-if="docs[item.kind]?.status === 'loading'" class="signup-terms__status" role="status">
            {{ TERMS_TITLE[item.kind] }} 본문을 불러오는 중…
          </p>
          <p v-else-if="docs[item.kind]?.status === 'error'" class="signup-terms__status" role="alert">
            본문을 불러오지 못했습니다.
            <button type="button" class="signup-terms__retry" @click="loadDoc(item.kind)">다시 시도</button>
          </p>
          <div
            v-else-if="docs[item.kind]?.body"
            class="signup-terms__body"
            role="region"
            tabindex="0"
            :aria-label="`${TERMS_TITLE[item.kind]} 전체 본문`"
          >{{ docs[item.kind].body }}</div>
          <p v-else-if="docs[item.kind]?.status === 'done'" class="signup-terms__status">
            아직 게시된 본문이 없습니다.
          </p>
        </div>
      </div>
    </div>
  </section>
</template>

<script>
import { getTermsContent } from '@/services/support-api';
import { CONSENT_NOTICES, SIGNUP_TERMS } from '@/utils/signup-terms';

const TERMS_TITLE = { USAGE: '이용약관', PRIVACY: '개인정보 수집·이용' };

const LOAD_ERROR_STATUS = { status: 'error', body: '' };

/* 다대구 추가 가입 첫 단계의 약관·개인정보 동의. 모양은 예전 자체 가입 화면(Figma 약관 블록) 그대로 */
export default {
  name: 'SignupTermsConsent',
  props: {
    /** { USAGE, PRIVACY } — 새 객체로만 갱신한다 */
    modelValue: {
      type: Object,
      required: true,
    },
    /** 'learner'(다대구 가입) | 'staff'(강사·기관 이메일 가입) — 개인정보 표가 받는 항목이 다르다 */
    audience: {
      type: String,
      default: 'learner',
    },
  },
  emits: ['update:modelValue'],
  data() {
    return {
      SIGNUP_TERMS,
      CONSENT_NOTICES,
      TERMS_TITLE,
      // { USAGE: true } — 펼친 줄. 처음엔 모두 접힘
      expanded: {},
      // { USAGE: { status: 'loading'|'done'|'error', body } } — 처음 펼칠 때 한 번 불러온다
      docs: {},
      /* 클릭은 이 사본을 바로 고치고 복사본을 올린다. prop 은 부모 재렌더 뒤에야 바뀌므로 prop 에서
       * 다음 값을 만들면 연속 클릭이 앞 클릭을 덮어쓰고, 화면 체크 상태와 값이 어긋난다 (E2E 에서 재현) */
      local: { ...this.modelValue },
    };
  },
  computed: {
    notices() {
      return this.audience === 'staff' ? { ...CONSENT_NOTICES, PRIVACY: CONSENT_NOTICES.PRIVACY_STAFF } : CONSENT_NOTICES;
    },
    allAgreed() {
      return SIGNUP_TERMS.every((item) => this.local[item.kind]);
    },
  },
  watch: {
    modelValue(value) {
      this.local = { ...value };
    },
  },
  methods: {
    setAll(value) {
      this.local = SIGNUP_TERMS.reduce((acc, item) => ({ ...acc, [item.kind]: value }), {});
      this.$emit('update:modelValue', { ...this.local });
    },
    setOne(kind, value) {
      this.local = { ...this.local, [kind]: value };
      this.$emit('update:modelValue', { ...this.local });
    },
    panelId(kind) {
      return `signup-terms-panel-${kind.toLowerCase()}`;
    },
    toggle(kind) {
      const open = !this.expanded[kind];
      this.expanded = { ...this.expanded, [kind]: open };
      const status = this.docs[kind]?.status;
      const hasDoc = SIGNUP_TERMS.some((item) => item.kind === kind && item.hasDoc);
      if (open && hasDoc && status !== 'done' && status !== 'loading') this.loadDoc(kind);
    },
    async loadDoc(kind) {
      this.docs = { ...this.docs, [kind]: { status: 'loading', body: '' } };
      try {
        const content = await getTermsContent(kind);
        this.docs = { ...this.docs, [kind]: { status: 'done', body: content?.body || '' } };
      } catch {
        // 하드코딩 사본을 두지 않는다 — 약관은 UNIQUE(type, version) 이력 구조라 서버 정본이
        // 유일해야 한다. 프론트 사본을 남기면 "화면에 보인 문안"과 "동의 이력이 가리키는 버전"이
        // 어긋날 수 있고, 그게 빈 화면보다 나쁘다.
        this.docs = { ...this.docs, [kind]: LOAD_ERROR_STATUS };
      }
    },
  },
};
</script>

<style scoped>
.signup-terms {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.signup-terms__title {
  margin: 0;
  font-size: 18px;
  line-height: 21px;
  font-weight: 600;
  color: var(--db-text-title);
}

.signup-terms__check {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 20px;
  cursor: pointer;
}

.signup-terms__items {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.signup-terms__row {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.signup-terms__line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

/* '자세히 보기 ⌄' 글자 버튼 — 높이 32px 누름 영역. 펼치면 '접기'로 바뀌고 꺾쇠가 뒤집힌다 */
.signup-terms__toggle {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 2px;
  height: 32px;
  margin: -6px -8px -6px 0;
  padding: 0 4px 0 8px;
  border: 0;
  border-radius: 6px;
  background: none;
  font-family: inherit;
  font-size: 14px;
  line-height: 17px;
  font-weight: 600;
  white-space: nowrap;
  color: var(--db-text-sub);
  cursor: pointer;
}

.signup-terms__toggle:hover {
  background: var(--db-bg-page);
  color: var(--db-text-title);
}

.signup-terms__toggle:active {
  background: var(--db-border-rt);
}

.signup-terms__toggle:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.signup-terms__chevron {
  transition: transform 150ms cubic-bezier(0.2, 0, 0, 1);
}

.signup-terms__toggle--open .signup-terms__chevron {
  transform: rotate(180deg);
}

@media (prefers-reduced-motion: reduce) {
  .signup-terms__chevron {
    transition: none;
  }
}

/* 펼친 내용은 체크박스 글자 줄에 맞춰 들여 쓴다 (상자 20 + 간격 8) */
.signup-terms__panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-left: 28px;
}

.signup-terms__status {
  margin: 0;
  font-size: 14px;
  line-height: 20px;
  color: var(--db-text-sub);
}

.signup-terms__retry {
  margin-left: 6px;
  padding: 0;
  border: 0;
  background: none;
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  color: var(--db-accent);
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.signup-terms__retry:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.signup-terms__notice {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  background: var(--db-bg-page);
  border: 1px solid var(--db-border-rt);
  border-radius: 6px;
}

.signup-terms__notice-caption {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

.signup-terms__notice tr + tr {
  border-top: 1px solid var(--db-border-rt);
}

.signup-terms__notice-label,
.signup-terms__notice-text {
  padding: 10px 12px;
  vertical-align: top;
  text-align: left;
  font-size: 14px;
  line-height: 20px;
  word-break: keep-all;
  overflow-wrap: anywhere;
}

.signup-terms__notice-col {
  width: 112px;
}

.signup-terms__notice-label {
  font-weight: 600;
  color: var(--db-text-title);
}

.signup-terms__notice-text {
  color: var(--db-text-body);
}

/* 법이 정한 "중요한 내용"(보유 기간·홍보 연락) — 굵게 + 강조색 + 밑줄로 색만으로 구분하지 않는다 */
.signup-terms__notice-strong {
  font-weight: 700;
  color: var(--db-accent);
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: 3px;
}

.signup-terms__line .signup-terms__check {
  flex: 1;
  min-width: 0;
}

.signup-terms__box {
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

.signup-terms__box:checked {
  background: var(--db-accent-fill);
  border-color: var(--db-accent);
}

.signup-terms__box:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

/* 체크 표시는 회전한 ㄴ자 — 아이콘 폰트를 끌어오지 않는다 */
.signup-terms__box:checked::after {
  display: block;
  width: 5px;
  height: 9px;
  margin: 1px auto 0;
  border: solid var(--db-bg-white);
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
  content: '';
}

.signup-terms__all,
.signup-terms__label {
  font-size: 16px;
  line-height: 19px;
  font-weight: 500;
  color: var(--db-text-sub);
}

.signup-terms__divider {
  height: 1px;
  background: var(--db-border-rt);
}

/* 전체 본문은 짧은 스크롤 상자 — 펼쳐도 [다음] 버튼이 멀리 밀려나지 않게 */
.signup-terms__body {
  max-height: 220px;
  overflow: auto;
  padding: 12px 14px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border-rt);
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: keep-all;
  overflow-wrap: anywhere;
  font-size: 14px;
  line-height: 22px;
  color: var(--db-text-body);
}

.signup-terms__body:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

/* 폰 폭: 라벨을 내용 위로 올려 내용이 좁은 칸에 갇히지 않게 */
@media (max-width: 599px) {
  .signup-terms__notice,
  .signup-terms__notice tbody,
  .signup-terms__notice tr,
  .signup-terms__notice-label,
  .signup-terms__notice-text {
    display: block;
    width: 100%;
  }

  .signup-terms__notice-label {
    padding-bottom: 2px;
  }

  .signup-terms__notice-text {
    padding-top: 0;
  }

  /* 폰은 폭이 좁아 들여쓰기 없이 줄 전체를 쓴다 */
  .signup-terms__panel {
    margin-left: 0;
  }
}
</style>
