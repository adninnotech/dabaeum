<template>
  <!-- 브라우저 기본 날짜 입력(type="date")은 라벨과 '연도-월-일' 자리표시가 겹치고 달력은 작은 아이콘으로만 열렸다.
       칸 어디를 눌러도 달력이 뜨고, 고르면 바로 닫힌다. 키보드로는 숫자만 쳐도 된다(2026-10-06) -->
  <q-input
    ref="field"
    v-bind="$attrs"
    :model-value="text"
    :label="label"
    mask="####-##-##"
    placeholder="YYYY-MM-DD"
    :error="!!errorText"
    :error-message="errorText"
    no-error-icon
    @update:model-value="onType"
    @click="open"
    @keydown="onKeydown"
    @blur="onBlur"
  >
    <template #append>
      <q-icon
        v-if="text && clearable"
        name="cancel"
        class="cursor-pointer"
        aria-label="날짜 지우기"
        @click.stop="pick('')"
      />
      <q-icon name="event" class="cursor-pointer" aria-hidden="true" />
    </template>
    <q-popup-proxy
      ref="popup"
      no-parent-event
      :no-focus="!keyboardOpen"
      :no-refocus="!keyboardOpen"
      :breakpoint="600"
      transition-show="jump-down"
      transition-hide="jump-up"
      @hide="keyboardOpen = false"
    >
      <!-- minimal 은 머리(오늘 버튼 포함)를 숨긴다 — 오늘·닫기를 아래에 둔다 -->
      <div ref="panel" @focusout="onPanelFocusout" @keydown="onPanelKeydown">
      <q-date
        :model-value="modelValue || null"
        mask="YYYY-MM-DD"
        :options="dateOptions"
        :default-view="defaultView"
        :navigation-min-year-month="minYearMonth"
        :navigation-max-year-month="maxYearMonth"
        minimal
        @update:model-value="onCalendarPick"
      >
        <div class="row items-center justify-between">
          <q-btn flat dense no-caps color="primary" label="오늘" :disable="!todayAllowed" @click="pick(today)" />
          <q-btn v-close-popup flat dense no-caps color="grey-8" label="닫기" />
        </div>
      </q-date>
      </div>
    </q-popup-proxy>
  </q-input>
</template>

<script>
import { isValidDateString, todayString } from '@/utils/datetime';

export default {
  name: 'AppDateInput',
  inheritAttrs: false,
  props: {
    /** 'YYYY-MM-DD' 또는 '' */
    modelValue: {
      type: String,
      default: '',
    },
    label: {
      type: String,
      default: '',
    },
    /** 고를 수 있는 첫날 'YYYY-MM-DD' */
    min: {
      type: String,
      default: '',
    },
    /** 고를 수 있는 마지막 날 'YYYY-MM-DD' */
    max: {
      type: String,
      default: '',
    },
    /** 생년월일처럼 먼 날을 고를 때는 'Years' 로 시작한다 */
    defaultView: {
      type: String,
      default: 'Calendar',
    },
    clearable: {
      type: Boolean,
      default: false,
    },
    /** 생년월일처럼 오늘 뒤를 막는다 */
    noFuture: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['update:modelValue'],
  data() {
    return {
      text: this.modelValue || '',
      typedError: '',
      // 마우스로 열면 커서를 칸에 남겨 바로 타이핑할 수 있게, Alt+↓ 로 열면 달력으로 초점을 옮긴다
      keyboardOpen: false,
    };
  },
  computed: {
    isLocked() {
      const { disable, readonly } = this.$attrs;
      return (disable !== undefined && disable !== false) || (readonly !== undefined && readonly !== false);
    },
    errorText() {
      // 부모가 준 오류(:error-message)가 먼저다
      if (this.$attrs.error) return this.$attrs['error-message'] || this.$attrs.errorMessage || ' ';
      return this.typedError;
    },
    today() {
      return todayString();
    },
    todayAllowed() {
      return !this.outOfRange(this.today);
    },
    upper() {
      if (!this.noFuture) return this.max;
      const today = todayString();
      return this.max && this.max < today ? this.max : today;
    },
    minYearMonth() {
      return this.min ? this.min.slice(0, 7).replace('-', '/') : undefined;
    },
    maxYearMonth() {
      return this.upper ? this.upper.slice(0, 7).replace('-', '/') : undefined;
    },
  },
  watch: {
    modelValue(value) {
      if ((value || '') !== this.text) this.text = value || '';
      this.typedError = '';
    },
  },
  methods: {
    // q-date options 는 'YYYY/MM/DD' 로 묻는다
    dateOptions(day) {
      const value = day.replaceAll('/', '-');
      if (this.min && value < this.min) return false;
      if (this.upper && value > this.upper) return false;
      return true;
    },
    outOfRange(value) {
      return (this.min && value < this.min) || (this.upper && value > this.upper);
    },
    open() {
      // 사용 안 함·읽기 전용 칸은 달력도 열지 않는다
      if (this.isLocked) return;
      // 폰에서는 달력이 가운데 창으로 뜬다 — 칸에 초점이 남으면 키보드가 올라와 달력을 가린다
      if (this.$q.screen.lt.sm) this.$refs.field?.blur();
      this.$refs.popup?.show();
    },
    // 달력 안(날짜·시각 버튼)에 있던 초점이 Tab 으로 칸·달력 밖으로 나가면 닫는다.
    // 바깥을 누른 경우는 메뉴가 알아서 닫으므로 relatedTarget 이 없으면 그대로 둔다
    onPanelFocusout(event) {
      const next = event.relatedTarget;
      if (!next) return;
      if (this.$refs.panel?.contains(next) || this.$refs.field?.$el?.contains(next)) return;
      this.$refs.popup?.hide();
    },
    // 달력은 body 끝에 붙어 있어 마지막 버튼에서 Tab 하면 초점이 페이지 밖으로 나가 버린다.
    // 경계에서 Tab 하면 칸으로 초점을 돌려놓고 닫는다 — 앞으로 가는 Tab 은 그대로 다음 칸으로 이어진다
    onPanelKeydown(event) {
      if (event.key !== 'Tab') return;
      const focusables = Array.from(
        this.$refs.panel?.querySelectorAll('button:not([disabled]), [tabindex]:not([tabindex="-1"])') || [],
      );
      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      const leaving = event.shiftKey ? document.activeElement === first : document.activeElement === last;
      if (!leaving) return;
      this.$refs.field?.focus();
      if (event.shiftKey) event.preventDefault();
      this.$refs.popup?.hide();
    },
    onKeydown(event) {
      // 마우스로 연 달력은 초점이 칸에 남아 있다 — Tab 으로 칸을 떠나면 함께 닫는다
      if (event.key === 'Tab' && !this.keyboardOpen) {
        this.$refs.popup?.hide();
        return;
      }
      // 키보드 사용자는 Alt+↓ 로 달력을 연다(브라우저 select 와 같은 규칙)
      if (event.key === 'ArrowDown' && event.altKey) {
        event.preventDefault();
        this.keyboardOpen = true;
        this.$nextTick(() => this.open());
      }
    },
    onType(value) {
      this.text = value || '';
      this.typedError = '';
      if (!this.text) {
        this.$emit('update:modelValue', '');
        return;
      }
      if (isValidDateString(this.text) && !this.outOfRange(this.text)) {
        this.$emit('update:modelValue', this.text);
      }
    },
    // 칸에 친 글자가 날짜로 쓸 수 없으면 그 이유 — 쓸 수 있거나 비었으면 ''
    textError() {
      if (!this.text) return '';
      if (!isValidDateString(this.text)) return '없는 날짜입니다';
      if (this.outOfRange(this.text)) {
        return this.min && this.text < this.min ? `${this.min} 이후 날짜` : `${this.upper} 이전 날짜`;
      }
      return '';
    },
    onBlur() {
      this.typedError = this.textError();
    },
    /**
     * 저장 전에 부모가 ref 로 부른다. 잘못 친 날짜는 v-model 로 올라가지 않아 부모에는 직전 값이 남는다 —
     * 그대로 저장하면 입력이 조용히 버려졌다(생년월일 미래 날짜가 null 로 저장).
     * @returns {boolean} 칸이 비었거나 올바른 날짜면 true. 아니면 칸에 오류를 띄우고 false
     */
    validate() {
      this.typedError = this.textError();
      return !this.typedError;
    },
    onCalendarPick(value) {
      this.pick(value || '');
    },
    pick(value) {
      this.text = value;
      this.typedError = '';
      this.$emit('update:modelValue', value);
      this.$refs.popup?.hide();
    },
  },
};
</script>
