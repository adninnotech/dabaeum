import { mapState } from 'pinia';
import { DEFAULT_INSTITUTION_ID } from '@/config/api';
import { useAuthStore } from '@/stores/auth-store';

/** 기관 관리자 페이지 공통: institutionId + 기관 미연결 배너 */
export default {
  computed: {
    ...mapState(useAuthStore, ['primaryInstitutionId', 'isAdmin']),
    institutionId() {
      return this.primaryInstitutionId || (this.isAdmin ? DEFAULT_INSTITUTION_ID : null);
    },
    showInstitutionMissingBanner() {
      return !this.institutionId && !this.isAdmin;
    },
  },
};
