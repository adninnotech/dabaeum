import { acceptHMRUpdate, defineStore } from 'pinia';

/* 기관 콘솔에서 "지금 보고 있는 기관" — 플랫폼 관리자(소속 없음)와 여러 기관 소속 관리자가 고른다.
   탭마다 따로(sessionStorage). 저장소를 못 쓰면 메모리로만 — 새로고침하면 다시 고른다 */
const STORAGE_KEY = 'dabaeum.console.institutionId';
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function readStored() {
  try {
    const value = sessionStorage.getItem(STORAGE_KEY);
    return value && UUID_PATTERN.test(value) ? value : null;
  } catch {
    return null;
  }
}

function writeStored(value) {
  try {
    if (value) sessionStorage.setItem(STORAGE_KEY, value);
    else sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // 개인 창·저장소 차단 — 메모리 상태만 쓴다
  }
}

export const useConsoleScopeStore = defineStore('console-scope', {
  state: () => ({
    institutionId: readStored(),
  }),
  actions: {
    selectInstitution(institutionId) {
      const next = institutionId && UUID_PATTERN.test(institutionId) ? institutionId : null;
      this.institutionId = next;
      writeStored(next);
    },
    reset() {
      this.selectInstitution(null);
    },
  },
});

if (import.meta.hot) {
  import.meta.hot.accept(acceptHMRUpdate(useConsoleScopeStore, import.meta.hot));
}
