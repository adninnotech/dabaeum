<template>
  <!-- 관심 분야(#142) — 강좌 분류 1단계 중 0~3개, 선택 입력. 값을 넣으려면 [선택] 수집·이용 동의가 먼저다(서버 422).
       다대구 가입과 학습자 마이페이지가 같이 쓴다. 동의를 풀면 고른 것도 지운다(= 동의 철회) -->
  <section class="interest-picker" :aria-labelledby="showTitle ? `${idPrefix}-title` : null" :aria-label="showTitle ? null : '관심 분야'">
    <!-- 마이페이지는 카드 제목이 이미 '관심 분야'라 숨긴다 -->
    <h2 v-if="showTitle" :id="`${idPrefix}-title`" class="interest-picker__title">
      관심 분야 <span class="interest-picker__optional">(선택)</span>
    </h2>
    <p class="interest-picker__lead">
      고르면 관심 분야에 맞는 강좌를 먼저 추천해 드려요. 최대 {{ INTEREST_MAX }}개까지 고를 수 있어요.<template v-if="separateFromTerms"> 나중에 마이페이지에서 바꿀 수 있어요.</template>
    </p>

    <div class="interest-picker__consent">
      <!-- 가입 ① 의 [전체 동의]를 누른 사람이 '또 동의?' 했다 — 이 동의는 따로라는 걸 먼저 말한다 -->
      <p v-if="!agreed" class="interest-picker__consent-note">
        관심 분야를 고르려면 아래 동의가 따로 필요해요.<template v-if="separateFromTerms"> 앞 단계의 전체 동의에는 들어 있지 않아요.</template>
      </p>
      <div class="interest-picker__line">
        <label class="interest-picker__check">
          <input
            ref="consentBox"
            type="checkbox"
            class="interest-picker__box"
            :checked="agreed"
            :disabled="disabled"
            @change="setAgreed($event.target.checked)"
          />
          <span class="interest-picker__label">[선택] 관심 분야 수집·이용 동의</span>
        </label>
        <button
          type="button"
          class="interest-picker__toggle"
          :class="{ 'interest-picker__toggle--open': noticeOpen }"
          :aria-expanded="noticeOpen ? 'true' : 'false'"
          :aria-controls="`${idPrefix}-notice`"
          :aria-label="`관심 분야 수집·이용 안내 ${noticeOpen ? '접기' : '자세히 보기'}`"
          @click="noticeOpen = !noticeOpen"
        >
          {{ noticeOpen ? '접기' : '자세히 보기' }}
          <q-icon name="expand_more" size="18px" class="interest-picker__chevron" />
        </button>
      </div>
      <table v-show="noticeOpen" :id="`${idPrefix}-notice`" class="interest-picker__notice">
        <caption class="interest-picker__sr">{{ NOTICE.caption }}</caption>
        <colgroup>
          <col class="interest-picker__notice-col" />
          <col />
        </colgroup>
        <tbody>
          <tr v-for="row in NOTICE.rows" :key="row.key">
            <th scope="row" class="interest-picker__notice-label">{{ row.label }}</th>
            <td class="interest-picker__notice-text">
              <strong v-if="row.emphasis" class="interest-picker__notice-strong">{{ row.text }}</strong>
              <template v-else>{{ row.text }}</template>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <p v-if="loading" class="interest-picker__status" role="status">관심 분야를 불러오는 중…</p>
    <p v-else-if="loadError" class="interest-picker__status interest-picker__status--error" role="alert">
      관심 분야를 불러오지 못했습니다.
      <button type="button" class="interest-picker__retry" @click="load">다시 시도</button>
    </p>
    <template v-else>
      <div class="interest-picker__chips" role="group" :aria-label="`관심 분야 선택, ${count}개 선택됨`">
        <button
          v-for="option in options"
          :key="option.code"
          type="button"
          class="interest-picker__chip"
          :class="{
            'interest-picker__chip--on': isSelected(option.code),
            'interest-picker__chip--locked': chipLocked(option.code),
          }"
          :aria-pressed="isSelected(option.code) ? 'true' : 'false'"
          :disabled="disabled"
          @click="toggle(option.code)"
        >
          <!-- 고른 순서 번호(1·2·3) — 추천에 먼저 쓰는 순서이기도 하다. 화면 읽기엔 '1번째' 로 -->
          <span v-if="isSelected(option.code)" class="interest-picker__order" aria-hidden="true">{{ orderOf(option.code) }}</span>
          <span v-else class="interest-picker__plus" aria-hidden="true">+</span>
          {{ option.name }}<span v-if="isSelected(option.code)" class="interest-picker__sr">, {{ orderOf(option.code) }}번째</span>
        </button>
      </div>
      <!-- 못 고르는 칸을 누르면 막지 않고 이유를 알린다 — 막힌(disabled) 버튼은 키보드로 갈 수도 없었다 -->
      <p v-if="notice" class="interest-picker__notice-msg" role="alert">{{ notice }}</p>
      <p v-else class="interest-picker__hint" aria-live="polite">{{ hint }}</p>
    </template>
  </section>
</template>

<script>
import { listCourseClassifications } from '@/services/classification-api';
import { INTEREST_MAX, interestOptions, toggleInterest } from '@/utils/interest';
import { CONSENT_NOTICES } from '@/utils/signup-terms';

let uid = 0;

export default {
  name: 'InterestPicker',
  props: {
    /** 고른 분류 코드 */
    modelValue: { type: Array, default: () => [] },
    /** [선택] 관심 분야 수집·이용 동의 */
    agreed: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false },
    showTitle: { type: Boolean, default: true },
    /** 가입처럼 앞 단계에 [전체 동의]가 있을 때 — 그 동의와 따로라고 덧붙인다 */
    separateFromTerms: { type: Boolean, default: false },
  },
  emits: ['update:modelValue', 'update:agreed'],
  data() {
    uid += 1;
    return {
      INTEREST_MAX,
      NOTICE: CONSENT_NOTICES.INTEREST,
      idPrefix: `interest-picker-${uid}`,
      options: [],
      loading: false,
      loadError: false,
      noticeOpen: false,
      // 못 고르는 칸을 눌렀을 때의 이유 — 동의하거나 고른 것이 바뀌면 지운다
      notice: '',
    };
  },
  computed: {
    count() {
      return this.modelValue.length;
    },
    hint() {
      if (!this.agreed) return '고르지 않아도 가입과 이용에는 제한이 없어요.';
      if (this.count >= INTEREST_MAX) return `${INTEREST_MAX}개를 모두 골랐어요. 바꾸려면 하나를 먼저 빼 주세요.`;
      return `${this.count}/${INTEREST_MAX}개 선택`;
    },
  },
  watch: {
    agreed() {
      this.notice = '';
    },
    modelValue() {
      this.notice = '';
    },
  },
  mounted() {
    this.load();
  },
  methods: {
    async load() {
      this.loading = true;
      this.loadError = false;
      try {
        this.options = interestOptions(await listCourseClassifications());
      } catch {
        this.loadError = true;
      } finally {
        this.loading = false;
      }
    },
    isSelected(code) {
      return this.modelValue.includes(code);
    },
    orderOf(code) {
      return this.modelValue.indexOf(code) + 1;
    },
    // 동의 전이거나 이미 다 찼으면 안 고른 칸은 흐리게 — 누를 수는 있고, 누르면 이유를 알린다(고른 칸은 언제나 뺄 수 있다)
    chipLocked(code) {
      if (!this.agreed) return true;
      return !this.isSelected(code) && this.count >= INTEREST_MAX;
    },
    toggle(code) {
      if (this.disabled) return;
      if (!this.agreed) {
        this.notice = '먼저 [선택] 관심 분야 수집·이용 동의에 체크해 주세요.';
        this.$refs.consentBox?.focus();
        return;
      }
      if (this.chipLocked(code)) {
        this.notice = `${INTEREST_MAX}개까지 고를 수 있어요. 바꾸려면 고른 것 하나를 먼저 빼 주세요.`;
        return;
      }
      this.$emit('update:modelValue', toggleInterest(this.modelValue, code));
    },
    setAgreed(value) {
      this.$emit('update:agreed', value);
      if (!value && this.count) this.$emit('update:modelValue', []);
    },
  },
};
</script>

<style scoped>
.interest-picker {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.interest-picker__title {
  margin: 0;
  font-size: 18px;
  line-height: 21px;
  font-weight: 600;
  color: var(--db-text-title);
}

.interest-picker__optional {
  font-size: 15px;
  font-weight: 500;
  color: var(--db-text-sub);
}

.interest-picker__lead,
.interest-picker__hint,
.interest-picker__status {
  margin: 0;
  font-size: 14px;
  line-height: 20px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.interest-picker__status--error {
  color: var(--db-status-pink);
}

.interest-picker__consent {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.interest-picker__consent-note {
  margin: 0;
  font-size: 14px;
  line-height: 20px;
  color: var(--db-text-body);
  word-break: keep-all;
}

/* 못 고르는 칸을 눌렀을 때 — 안내(hint) 자리에 대신 뜬다 */
.interest-picker__notice-msg {
  margin: 0;
  font-size: 14px;
  line-height: 20px;
  font-weight: 600;
  color: var(--db-status-pink);
  word-break: keep-all;
}

.interest-picker__line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.interest-picker__check {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 8px;
  min-width: 0;
  min-height: 20px;
  cursor: pointer;
}

/* 약관 동의 체크박스(SignupTermsConsent)와 같은 모양 */
.interest-picker__box {
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

.interest-picker__box:checked {
  background: var(--db-accent-fill);
  border-color: var(--db-accent);
}

.interest-picker__box:checked::after {
  display: block;
  width: 5px;
  height: 9px;
  margin: 1px auto 0;
  border: solid var(--db-bg-white);
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
  content: '';
}

.interest-picker__box:disabled {
  cursor: default;
  opacity: 0.6;
}

.interest-picker__box:focus-visible,
.interest-picker__toggle:focus-visible,
.interest-picker__retry:focus-visible,
.interest-picker__chip:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.interest-picker__label {
  font-size: 16px;
  line-height: 19px;
  font-weight: 500;
  color: var(--db-text-sub);
}

.interest-picker__toggle {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 2px;
  height: 32px;
  padding: 0 4px 0 8px;
  border: 0;
  border-radius: 6px;
  background: none;
  font-family: inherit;
  font-size: 14px;
  line-height: 17px;
  color: var(--db-text-sub);
  white-space: nowrap;
  cursor: pointer;
}

.interest-picker__toggle:hover {
  background: var(--db-bg-subtle);
  color: var(--db-text-title);
}

.interest-picker__toggle:active {
  background: var(--db-border-rt);
}

.interest-picker__chevron {
  transition: transform 0.15s ease;
}

.interest-picker__toggle--open .interest-picker__chevron {
  transform: rotate(180deg);
}

.interest-picker__notice {
  width: 100%;
  border-collapse: collapse;
  border: 1px solid var(--db-border-rt);
  border-radius: 6px;
}

.interest-picker__notice tr + tr {
  border-top: 1px solid var(--db-border-rt);
}

.interest-picker__notice-col {
  width: 112px;
}

.interest-picker__notice-label,
.interest-picker__notice-text {
  padding: 10px 12px;
  vertical-align: top;
  text-align: left;
  font-size: 14px;
  line-height: 20px;
  word-break: keep-all;
  overflow-wrap: anywhere;
}

.interest-picker__notice-label {
  font-weight: 600;
  color: var(--db-text-title);
}

.interest-picker__notice-text {
  color: var(--db-text-body);
}

.interest-picker__notice-strong {
  font-weight: 700;
  color: var(--db-accent);
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: 3px;
}

.interest-picker__sr {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

/* 짧은 낱말 알약 버튼을 흘려 놓는다(다른 서비스의 '관심 주제 고르기'처럼) — 한눈에 보이고 빨리 고른다 */
.interest-picker__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.interest-picker__chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  min-height: 44px;
  padding: 0 16px 0 12px;
  border: 1px solid var(--db-border);
  border-radius: 999px;
  background: var(--db-bg-white);
  font-family: inherit;
  font-size: 15px;
  line-height: 20px;
  font-weight: 600;
  color: var(--db-text-title);
  word-break: keep-all;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}

.interest-picker__plus {
  font-size: 17px;
  font-weight: 500;
  color: var(--db-text-sub);
}

/* 안 고른 칸의 hover — 고른 칸(--on)은 뺀다. 같이 걸리면 밝은 바탕 위 흰 글자가 사라졌다 */
.interest-picker__chip:hover:not(:disabled):not(.interest-picker__chip--locked):not(.interest-picker__chip--on) {
  border-color: var(--db-accent);
  background: var(--db-bg-subtle);
}

.interest-picker__chip:active:not(:disabled):not(.interest-picker__chip--locked) {
  transform: translateY(1px);
}

/* 지금 못 고르는 칸(동의 전·3개 다 참) — 흐리게만, 누르면 이유가 나온다. 글자는 읽을 수 있는 대비로 둔다 */
.interest-picker__chip--locked {
  border-color: var(--db-border-rt);
  background: var(--db-bg-subtle);
  color: var(--db-text-sub);
}

/* 고른 칸 — 채운 바탕 + 순서 번호로 색만으로 구분하지 않는다 */
.interest-picker__chip--on {
  border-color: var(--db-primary);
  background: var(--db-primary);
  color: var(--db-bg-white);
}

/* 고른 칸의 hover — 바탕만 한 단계 짙게(누르면 빠진다는 신호), 흰 글자 대비는 그대로 */
.interest-picker__chip--on:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--db-primary) 80%, black);
  background: color-mix(in srgb, var(--db-primary) 80%, black);
}

.interest-picker__order {
  display: inline-grid;
  place-items: center;
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--db-bg-white);
  font-size: 13px;
  font-weight: 700;
  color: var(--db-primary);
  font-variant-numeric: tabular-nums;
}

.interest-picker__chip:disabled {
  cursor: default;
  opacity: 0.5;
}

.interest-picker__retry {
  padding: 0;
  margin-left: 4px;
  border: 0;
  background: none;
  font: inherit;
  font-weight: 700;
  color: var(--db-accent);
  text-decoration: underline;
  cursor: pointer;
}

@media (prefers-reduced-motion: reduce) {
  .interest-picker__chip,
  .interest-picker__chevron {
    transition: none;
  }

  .interest-picker__chip:active:not(:disabled) {
    transform: none;
  }
}
</style>
