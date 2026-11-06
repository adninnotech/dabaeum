<template>
  <!-- 브라우저 기본 datetime-local 은 연·월·일·시·분 칸을 하나씩 눌러야 했다.
       칸을 누르면 달력과 시·분 목록이 함께 뜬다. 값 형식은 그대로 'YYYY-MM-DDTHH:mm' -->
  <q-input
    ref="field"
    v-bind="$attrs"
    :model-value="text"
    :label="label"
    mask="####-##-## ##:##"
    placeholder="YYYY-MM-DD HH:mm"
    :error="!!errorText"
    :error-message="errorText"
    no-error-icon
    @update:model-value="onType"
    @click="open"
    @keydown="onKeydown"
    @blur="onBlur"
  >
    <template #append>
      <q-icon name="schedule" class="cursor-pointer" aria-hidden="true" />
    </template>
    <q-popup-proxy
      ref="popup"
      no-parent-event
      :no-focus="!keyboardOpen"
      :no-refocus="!keyboardOpen"
      :breakpoint="600"
      transition-show="jump-down"
      transition-hide="jump-up"
      @show="scrollToSelected"
      @hide="keyboardOpen = false"
    >
      <div ref="panel" class="dt-picker" @focusout="onPanelFocusout" @keydown="onPanelKeydown">
        <div class="dt-picker__panes">
          <q-date
            :model-value="datePart || null"
            mask="YYYY-MM-DD"
            minimal
            flat
            class="dt-picker__date"
            @update:model-value="onDatePick"
          />
          <div class="dt-picker__time">
            <TimeColumns ref="time" :hour="hourPart" :minute="minutePart" @pick="setTime" />
          </div>
        </div>
        <div class="dt-picker__foot">
          <span class="dt-picker__value">{{ text || '날짜와 시간을 골라 주세요' }}</span>
          <q-btn v-close-popup unelevated no-caps color="primary" label="확인" />
        </div>
      </div>
    </q-popup-proxy>
  </q-input>
</template>

<script>
import TimeColumns from '@/components/TimeColumns.vue';
import { isValidDateString, todayString } from '@/utils/datetime';

const FULL = /^(\d{4}-\d{2}-\d{2}) (\d{2}):(\d{2})$/;
/* 날짜만 고르고 시간을 아직 안 골랐을 때 채우는 시각 */
const DEFAULT_HOUR = '09';

function toText(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '';
}

function isValidText(text) {
  const match = FULL.exec(text);
  return !!match && isValidDateString(match[1]) && Number(match[2]) < 24 && Number(match[3]) < 60;
}

export default {
  name: 'AppDateTimeInput',
  components: { TimeColumns },
  inheritAttrs: false,
  props: {
    /** 'YYYY-MM-DDTHH:mm' 또는 '' */
    modelValue: {
      type: String,
      default: '',
    },
    label: {
      type: String,
      default: '',
    },
  },
  emits: ['update:modelValue'],
  data() {
    return {
      text: toText(this.modelValue),
      typedError: '',
      // 마우스로 열면 커서를 칸에 남겨 바로 타이핑할 수 있게, Alt+↓ 로 열면 달력으로 초점을 옮긴다
      keyboardOpen: false,
    };
  },
  computed: {
    errorText() {
      if (this.$attrs.error) return this.$attrs['error-message'] || this.$attrs.errorMessage || ' ';
      return this.typedError;
    },
    datePart() {
      return isValidText(this.text) ? this.text.slice(0, 10) : '';
    },
    hourPart() {
      return isValidText(this.text) ? this.text.slice(11, 13) : '';
    },
    minutePart() {
      return isValidText(this.text) ? this.text.slice(14, 16) : '';
    },
  },
  watch: {
    modelValue(value) {
      const next = toText(value);
      if (next !== this.text) this.text = next;
      this.typedError = '';
    },
  },
  methods: {
    open() {
      const { disable, readonly } = this.$attrs;
      if ((disable !== undefined && disable !== false) || (readonly !== undefined && readonly !== false)) return;
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
      if (event.key === 'ArrowDown' && event.altKey) {
        event.preventDefault();
        this.keyboardOpen = true;
        this.$nextTick(() => this.open());
      }
    },
    scrollToSelected() {
      this.$refs.time?.scrollToSelected();
    },
    commit(text) {
      this.text = text;
      this.typedError = '';
      this.$emit('update:modelValue', text ? text.replace(' ', 'T') : '');
    },
    onType(value) {
      const text = value || '';
      this.text = text;
      this.typedError = '';
      if (!text) this.commit('');
      else if (isValidText(text)) this.commit(text);
    },
    onBlur() {
      if (this.text && !isValidText(this.text)) this.typedError = '없는 날짜·시간입니다';
    },
    onDatePick(date) {
      if (!date) return;
      const time = this.hourPart ? `${this.hourPart}:${this.minutePart}` : `${DEFAULT_HOUR}:00`;
      this.commit(`${date} ${time}`);
    },
    setTime({ hour, minute }) {
      this.commit(`${this.datePart || todayString()} ${hour}:${minute}`);
    },
  },
};
</script>

<style scoped>
.dt-picker {
  background: var(--db-bg-white);
}

.dt-picker__panes {
  display: flex;
  flex-wrap: wrap;
}

.dt-picker__date {
  flex: 1 1 290px;
}

.dt-picker__time {
  display: flex;
  flex: 0 0 auto;
  border-left: 1px solid var(--db-border);
}

.dt-picker__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  border-top: 1px solid var(--db-border);
}

.dt-picker__value {
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  color: var(--db-text-body);
}

/* 폰(대화상자)에서는 달력 아래로 내려 가로를 꽉 채운다 */
@media (max-width: 599px) {
  .dt-picker__time {
    flex: 1 1 100%;
    border-left: 0;
    border-top: 1px solid var(--db-border);
  }

  .dt-picker__time > * {
    width: 100%;
  }
}
</style>
