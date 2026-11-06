<template>
  <form v-if="!compact" class="header-search" role="search" @submit.prevent="submit">
    <q-icon name="search" size="18px" class="header-search__icon" />
    <input
      v-model="text"
      type="search"
      class="header-search__input"
      :maxlength="SEARCH_MAX_LENGTH"
      placeholder="통합검색"
      aria-label="통합검색"
    />
  </form>
  <q-btn v-else flat dense round icon="search" aria-label="통합검색" @click="$router.push('/search')" />
</template>

<script>
import { SEARCH_MAX_LENGTH, SEARCH_MIN_LENGTH } from '@/services/search-api';

/* 학습자 헤더 통합검색 — 데스크톱은 입력칸, 폰은 검색 화면으로 가는 아이콘 */
export default {
  name: 'HeaderSearch',
  props: {
    compact: { type: Boolean, default: false },
  },
  data() {
    return { SEARCH_MAX_LENGTH, text: '' };
  },
  methods: {
    submit() {
      const q = this.text.trim();
      if (q.length < SEARCH_MIN_LENGTH) {
        this.$q.notify({
          type: 'warning',
          message: `검색어를 ${SEARCH_MIN_LENGTH}자 이상 입력해 주세요.`,
          position: 'top',
        });
        return;
      }
      this.$router.push({ path: '/search', query: { q } });
      this.text = '';
    },
  },
};
</script>

<style scoped>
.header-search {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 200px;
  height: 40px;
  padding: 0 10px;
  border: 1px solid var(--db-border);
  border-radius: 20px;
  background: var(--db-bg-page);
}

.header-search:focus-within {
  border-color: var(--db-accent);
  background: var(--db-bg-white);
}

.header-search__icon {
  flex-shrink: 0;
  color: var(--db-text-sub);
}

.header-search__input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: transparent;
  font: inherit;
  /* 16px 아래면 폰(iOS)이 입력할 때 화면을 확대해 버린다 */
  font-size: 16px;
  color: var(--db-text-title);
}

.header-search__input::placeholder {
  color: var(--db-text-sub);
}
</style>
