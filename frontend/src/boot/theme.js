import { defineBoot } from '#q-app';
import { Dark, LocalStorage } from 'quasar';

/* 화면 모드 — 'auto'(기기 설정 따름, 기본)·'light'·'dark'. 사용자 메뉴에서 바꾸고 이 브라우저에 남긴다 */
export const THEME_STORAGE_KEY = 'dabaeum.theme';
export const THEME_OPTIONS = [
  { value: 'auto', label: '기기 설정 따르기', icon: 'brightness_auto' },
  { value: 'light', label: '밝게', icon: 'light_mode' },
  { value: 'dark', label: '어둡게', icon: 'dark_mode' },
];

export function readThemePreference() {
  const saved = LocalStorage.getItem(THEME_STORAGE_KEY);
  return THEME_OPTIONS.some((option) => option.value === saved) ? saved : 'auto';
}

export function applyThemePreference(value) {
  Dark.set(value === 'auto' ? 'auto' : value === 'dark');
  if (value === 'auto') LocalStorage.remove(THEME_STORAGE_KEY);
  else LocalStorage.set(THEME_STORAGE_KEY, value);
}

export default defineBoot(() => {
  Dark.set(readThemePreference() === 'auto' ? 'auto' : readThemePreference() === 'dark');
});
