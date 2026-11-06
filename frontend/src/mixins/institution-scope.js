import { mapState } from 'pinia';
import { useAuthStore } from '@/stores/auth-store';
import { useConsoleScopeStore } from '@/stores/console-scope-store';

/**
 * 기관 콘솔 페이지 공통: 지금 보고 있는 institutionId.
 * - 플랫폼 관리자: 콘솔 머리의 기관 선택기로 고른 기관 → 없으면 본인 소속 → 없으면 null(고르라고 안내).
 *   예전처럼 개발용 기본 기관으로 조용히 대체하지 않는다
 * - 여러 기관 소속 기관 관리자: 고른 기관이 본인 소속일 때만, 아니면 첫 소속
 * - 그 외: 첫 소속
 */
export default {
  computed: {
    ...mapState(useAuthStore, ['primaryInstitutionId', 'isAdmin', 'myInstitutions']),
    ...mapState(useConsoleScopeStore, { pickedInstitutionId: 'institutionId' }),
    institutionId() {
      const picked = this.pickedInstitutionId;
      if (this.isAdmin) return picked || this.primaryInstitutionId || null;
      const owns = (this.myInstitutions || []).some((item) => item.institutionId === picked);
      return owns ? picked : this.primaryInstitutionId;
    },
    showInstitutionMissingBanner() {
      return !this.institutionId && !this.isAdmin;
    },
  },
};
