import { shouldShowEducationTypeUI } from '@/config/features';

/** 교육 유형(온/오프/혼합) UI 표시 여부 — Options API computed */
export default {
  computed: {
    showEducationTypeUI() {
      return shouldShowEducationTypeUI();
    },
  },
};
