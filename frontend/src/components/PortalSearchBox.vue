<template>
  <!-- 포털형 큰 검색창 — 홈 가운데·통합검색 화면 위쪽에서 쓴다. 제출 처리는 부모가 한다 -->
  <form class="portal-search" :class="{ 'portal-search--large': large }" role="search" @submit.prevent="$emit('submit')">
    <q-input
      :model-value="modelValue"
      borderless
      clearable
      :autofocus="autofocus"
      :maxlength="SEARCH_MAX_LENGTH"
      :placeholder="placeholder"
      class="portal-search__input"
      aria-label="검색어"
      @update:model-value="$emit('update:modelValue', $event || '')"
    />
    <button type="submit" class="portal-search__submit" aria-label="검색">
      <q-icon name="search" class="portal-search__submit-icon" />
    </button>
  </form>
</template>

<script>
import { SEARCH_MAX_LENGTH } from '@/services/search-api';

export default {
  name: 'PortalSearchBox',
  props: {
    modelValue: { type: String, default: '' },
    autofocus: { type: Boolean, default: false },
    placeholder: { type: String, default: '강좌, 기관, 공지, 자주 묻는 질문 검색' },
    /** 홈처럼 화면의 주인공일 때 한 단계 크게 */
    large: { type: Boolean, default: false },
  },
  emits: ['update:modelValue', 'submit'],
  data() {
    return { SEARCH_MAX_LENGTH };
  },
};
</script>

<style scoped>
.portal-search {
  --portal-search-height: 60px;

  display: flex;
  align-items: stretch;
  width: 100%;
  max-width: 720px;
  height: var(--portal-search-height);
  background: var(--db-bg-white);
  border: 2px solid var(--db-primary);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 16px color-mix(in srgb, var(--db-primary) 10%, transparent);
  transition: box-shadow 0.15s ease;
}

.portal-search--large {
  --portal-search-height: 64px;

  max-width: 760px;
}

.portal-search:focus-within {
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--db-primary) 16%, transparent);
}

.portal-search__input {
  flex: 1;
  min-width: 0;
  padding: 0 8px 0 22px;
}

.portal-search__input :deep(.q-field__control),
.portal-search__input :deep(.q-field__marginal) {
  height: calc(var(--portal-search-height) - 4px);
}

.portal-search__input :deep(.q-field__native) {
  font-size: 19px;
  font-weight: 500;
  color: var(--db-text-title);
}

.portal-search__input :deep(.q-field__native::placeholder) {
  font-weight: 400;
  /* 연회색(#cacfd5)은 중장년 이용자에게 안 보였다 — 무엇을 칠지 알려 주는 글이라 보조 글자색으로 */
  color: var(--db-text-sub);
  opacity: 1;
}

.portal-search__submit {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: var(--portal-search-height);
  border: 0;
  background: var(--db-primary);
  color: var(--db-on-color);
  cursor: pointer;
  transition: background 0.15s ease;
}

.portal-search__submit:hover {
  background: color-mix(in srgb, var(--db-primary) 88%, black);
}

.portal-search__submit:active {
  background: color-mix(in srgb, var(--db-primary) 78%, black);
}

.portal-search__submit:focus-visible {
  outline: 3px solid var(--db-accent);
  outline-offset: -5px;
}

.portal-search__submit-icon {
  font-size: 30px;
}

@media (max-width: 599px) {
  .portal-search,
  .portal-search--large {
    --portal-search-height: 52px;

    border-radius: 10px;
  }

  .portal-search__input {
    padding-left: 14px;
  }

  .portal-search__input :deep(.q-field__native) {
    font-size: 17px;
  }

  .portal-search__submit-icon {
    font-size: 26px;
  }
}
</style>
