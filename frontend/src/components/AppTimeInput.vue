<template>
  <!-- 브라우저 기본 type="time" 은 '--:-- --'(오전/오후) 칸을 하나씩 눌러야 했고 날짜·시간 칸과 모양이 달랐다.
       칸을 누르면 시·분 목록이 뜬다. 숫자만 쳐도 된다(1430). 값 형식은 그대로 'HH:mm' -->
  <q-input
    ref="field"
    v-bind="$attrs"
    :model-value="text"
    :label="label"
    mask="##:##"
    placeholder="HH:mm"
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
      @show="$refs.time?.scrollToSelected()"
      @hide="keyboardOpen = false"
    >
      <div ref="panel" class="time-picker" @focusout="onPanelFocusout" @keydown="onPanelKeydown">
        <TimeColumns ref="time" :hour="hourPart" :minute="minutePart" @pick="onPick" />
        <div class="time-picker__foot">
          <span class="time-picker__value">{{ text || '--:--' }}</span>
          <q-btn v-close-popup unelevated no-caps color="primary" label="확인" />
        </div>
      </div>
    </q-popup-proxy>
  </q-input>
</template>

<script>
import TimeColumns from '@/components/TimeColumns.vue';

const FULL = /^(\d{2}):(\d{2})$/;

function isValidTime(text) {
  const match = FULL.exec(text);
  return !!match && Number(match[1]) < 24 && Number(match[2]) < 60;
}

export default {
  name: 'AppTimeInput',
  components: { TimeColumns },
  inheritAttrs: false,
  props: {
    /** 'HH:mm' 또는 '' */
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
      text: (this.modelValue || '').slice(0, 5),
      typedError: '',
      // 마우스로 열면 커서를 칸에 남겨 바로 타이핑할 수 있게, Alt+↓ 로 열면 목록으로 초점을 옮긴다
      keyboardOpen: false,
    };
  },
  computed: {
    errorText() {
      if (this.$attrs.error) return this.$attrs['error-message'] || this.$attrs.errorMessage || ' ';
      return this.typedError;
    },
    hourPart() {
      return isValidTime(this.text) ? this.text.slice(0, 2) : '';
    },
    minutePart() {
      return isValidTime(this.text) ? this.text.slice(3, 5) : '';
    },
  },
  watch: {
    modelValue(value) {
      const next = (value || '').slice(0, 5);
      if (next !== this.text) this.text = next;
      this.typedError = '';
    },
  },
  methods: {
    open() {
      const { disable, readonly } = this.$attrs;
      if ((disable !== undefined && disable !== false) || (readonly !== undefined && readonly !== false)) return;
      // 폰에서는 목록이 가운데 창으로 뜬다 — 칸에 초점이 남으면 키보드가 올라와 목록을 가린다
      if (this.$q.screen.lt.sm) this.$refs.field?.blur();
      this.$refs.popup?.show();
    },
    // 목록 안에 있던 초점이 칸·목록 밖으로 나가면 닫는다(바깥 클릭은 메뉴가 닫는다)
    onPanelFocusout(event) {
      const next = event.relatedTarget;
      if (!next) return;
      if (this.$refs.panel?.contains(next) || this.$refs.field?.$el?.contains(next)) return;
      this.$refs.popup?.hide();
    },
    // 목록은 body 끝에 붙어 있어 마지막 버튼에서 Tab 하면 초점이 페이지 밖으로 나간다 — 칸으로 돌려놓고 닫는다
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
    commit(text) {
      this.text = text;
      this.typedError = '';
      this.$emit('update:modelValue', text);
    },
    onType(value) {
      const text = value || '';
      this.text = text;
      this.typedError = '';
      if (!text) this.commit('');
      else if (isValidTime(text)) this.commit(text);
    },
    onBlur() {
      if (this.text && !isValidTime(this.text)) this.typedError = '없는 시각입니다';
    },
    onPick({ hour, minute }) {
      this.commit(`${hour}:${minute}`);
    },
  },
};
</script>

<style scoped>
.time-picker {
  background: var(--db-bg-white);
}

.time-picker__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  border-top: 1px solid var(--db-border);
}

.time-picker__value {
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  color: var(--db-text-body);
}
</style>
