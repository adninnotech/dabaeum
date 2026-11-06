import { defineStore } from 'pinia';
import { applyThemePreference, readThemePreference, THEME_OPTIONS } from '@/boot/theme';

/* 화면 모드 — 머리 아이콘·마이페이지가 같은 값을 본다(한쪽에서 바꾸면 다른 쪽도 바로 바뀐다) */
export const useThemeStore = defineStore('theme', {
  state: () => ({
    preference: readThemePreference(),
  }),
  getters: {
    current: (state) => THEME_OPTIONS.find((option) => option.value === state.preference) || THEME_OPTIONS[0],
  },
  actions: {
    setPreference(value) {
      applyThemePreference(value);
      this.preference = value;
    },
  },
});
