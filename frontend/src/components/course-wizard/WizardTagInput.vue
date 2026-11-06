<template>
  <!-- velog 처럼 — 글자를 치고 띄어쓰기·쉼표·Enter 를 누르면 칩이 된다. 저장값은 예전처럼 쉼표로 이은 문자열 하나(최대 60자) -->
  <div class="tag-input">
    <div class="tag-input__box" :class="{ 'tag-input__box--error': !!shownError }" @click="focusInput">
      <button
        v-for="tag in tags"
        :key="tag"
        type="button"
        class="tag-input__chip"
        :aria-label="`${tag} 태그 지우기`"
        @click.stop="removeTag(tag)"
      >
        <span>{{ tag }}</span>
        <q-icon name="close" size="16px" aria-hidden="true" />
      </button>
      <input
        ref="input"
        :value="draft"
        class="tag-input__field"
        :placeholder="tags.length ? '' : placeholder"
        aria-label="태그 입력"
        :aria-describedby="hintId"
        @input="onInput"
        @keydown="onKeydown"
        @compositionend="onCompositionEnd"
        @blur="commitDraft"
      />
    </div>
    <div :id="hintId" class="tag-input__hint" :class="{ 'tag-input__hint--error': !!shownError }" :role="shownError ? 'alert' : null">
      <span>{{ shownError || hint }}</span>
      <span class="tag-input__count">{{ modelValue.length }}/{{ maxLength }}</span>
    </div>
  </div>
</template>

<script>
const SEPARATOR = /[\s,]+/;
const JOINER = ',';

let uid = 0;

/** '#엑셀' 처럼 붙여 넣어도 글자만 */
function cleanTag(text) {
  return String(text || '').replace(/^#+/, '').trim();
}

export default {
  name: 'WizardTagInput',
  props: {
    /** 쉼표로 이은 태그 문자열 — 백엔드 tags 필드 그대로 */
    modelValue: { type: String, default: '' },
    maxLength: { type: Number, default: 60 },
    placeholder: { type: String, default: '태그를 입력하고 띄어쓰기' },
    hint: { type: String, default: '' },
    error: { type: String, default: '' },
  },
  emits: ['update:modelValue'],
  data() {
    uid += 1;
    return {
      draft: '',
      localError: '',
      commitAfterCompose: false,
      hintId: `wizard-tag-hint-${uid}`,
    };
  },
  computed: {
    tags() {
      return String(this.modelValue || '')
        .split(SEPARATOR)
        .map(cleanTag)
        .filter(Boolean);
    },
    shownError() {
      return this.localError || this.error;
    },
  },
  methods: {
    focusInput() {
      this.$refs.input?.focus();
    },
    emitTags(tags) {
      this.$emit('update:modelValue', tags.join(JOINER));
    },
    /** 새 태그들을 붙인다 — 겹치면 건너뛰고, 합쳐 maxLength 를 넘으면 거기서 멈춘다 */
    addTags(words) {
      const result = words.map(cleanTag).filter(Boolean).reduce(
        (acc, word) => {
          if (acc.tags.includes(word)) return acc;
          const next = [...acc.tags, word];
          if (next.join(JOINER).length > this.maxLength) return { ...acc, overflow: true };
          return { ...acc, tags: next };
        },
        { tags: this.tags, overflow: false },
      );
      this.localError = result.overflow ? `태그는 모두 합쳐 ${this.maxLength}자까지 넣을 수 있습니다.` : '';
      if (result.tags.length !== this.tags.length) this.emitTags(result.tags);
    },
    commitDraft() {
      if (!cleanTag(this.draft)) {
        this.draft = '';
        return;
      }
      this.addTags([this.draft]);
      this.draft = '';
    },
    removeTag(tag) {
      this.localError = '';
      this.emitTags(this.tags.filter((item) => item !== tag));
      this.focusInput();
    },
    /** 띄어쓰기·쉼표가 들어오면 그 앞까지를 칩으로 — 한글 조합 중 띄어쓰기도 여기서 잡힌다 */
    onInput(event) {
      const value = event.target.value;
      if (!SEPARATOR.test(value)) {
        this.draft = value;
        return;
      }
      const parts = value.split(SEPARATOR);
      const rest = parts[parts.length - 1];
      this.addTags(parts.slice(0, -1));
      this.draft = rest;
      // 같은 값이면 Vue 가 input 을 다시 그리지 않아 구분자가 남는다
      event.target.value = rest;
    },
    onKeydown(event) {
      if (event.key === 'Enter') {
        event.preventDefault();
        // 한글 조합 중 Enter 는 조합만 끝낸다 — 끝난 뒤 칩으로
        if (event.isComposing) {
          this.commitAfterCompose = true;
          return;
        }
        this.commitDraft();
        return;
      }
      if (event.key === 'Backspace' && !this.draft && !event.isComposing && this.tags.length) {
        event.preventDefault();
        this.removeTag(this.tags[this.tags.length - 1]);
      }
    },
    onCompositionEnd(event) {
      if (!this.commitAfterCompose) return;
      this.commitAfterCompose = false;
      this.draft = event.target.value;
      this.$nextTick(() => this.commitDraft());
    },
  },
};
</script>

<style scoped>
.tag-input__box {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-height: 56px;
  padding: 8px 12px;
  border: 1px solid var(--db-border-rt);
  border-radius: 8px;
  cursor: text;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.tag-input__box:focus-within {
  border-color: var(--db-accent);
  box-shadow: 0 0 0 1px var(--db-accent);
}

.tag-input__box--error,
.tag-input__box--error:focus-within {
  border-color: var(--db-status-pink);
  box-shadow: 0 0 0 1px var(--db-status-pink);
}

.tag-input__chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 36px;
  max-width: 100%;
  padding: 0 10px 0 14px;
  border: 0;
  border-radius: 999px;
  background: var(--db-accent-soft);
  color: var(--db-accent);
  font: inherit;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.tag-input__chip span {
  overflow: hidden;
  text-overflow: ellipsis;
}

.tag-input__chip:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

@media (hover: hover) {
  /* 누르면 지워진다는 신호 — 바탕은 회색으로, × 는 분홍으로 */
  .tag-input__chip:hover {
    background: var(--db-bg-tag);
    color: var(--db-text-title);
  }

  .tag-input__chip:hover .q-icon {
    color: var(--db-status-pink);
  }
}

.tag-input__field {
  flex: 1 1 120px;
  min-width: 120px;
  height: 36px;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--db-text-title);
  font: inherit;
  font-size: 15px;
}

.tag-input__field::placeholder {
  color: var(--db-text-placeholder);
}

.tag-input__hint {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 6px;
  font-size: 13px;
  color: var(--db-text-sub);
}

.tag-input__hint--error {
  color: var(--db-status-pink);
}

.tag-input__count {
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

@media (prefers-reduced-motion: reduce) {
  .tag-input__box,
  .tag-input__chip {
    transition: none;
  }
}
</style>
