import { mapActions, mapState } from 'pinia';
import { ROLES, ROLE_CONSOLE_ICONS, homePathForRole, layoutKeyForRole } from '@/access/roles';
import { useAuthStore } from '@/stores/auth-store';

/* 콘솔 바로가기 — 콘솔이 매일 쓰는 주 목적지인데 프로필 메뉴 안쪽에만 있어 찾기 어려웠다.
 * 학습자 머리 버튼, 콘솔 사이드바 전환, 프로필 메뉴가 같은 목록을 쓴다. */

const CONSOLE_ORDER = [ROLES.PLATFORM_ADMIN, ROLES.INSTITUTION_ADMIN, ROLES.INSTRUCTOR];

export const LEARNER_SITE_LABEL = '학습자 사이트';

export const CONSOLE_LABELS = {
  [ROLES.PLATFORM_ADMIN]: '관리자 콘솔',
  [ROLES.INSTITUTION_ADMIN]: '기관 콘솔',
  [ROLES.INSTRUCTOR]: '강사 콘솔',
};

export default {
  computed: {
    ...mapState(useAuthStore, {
      consoleRoles: 'uniqueRoles',
      consoleIsDemo: 'isDemoSession',
      consoleIsInstitutionAdmin: 'isInstitutionAdmin',
      consoleIsInstructor: 'isInstructor',
    }),
    /** 이 계정이 들어갈 수 있는 콘솔 — 역할 목록에 없더라도 소속으로 파생된 강사·기관 관리자 권한도 포함한다 */
    consoleDestinations() {
      const owned = new Set(this.consoleRoles || []);
      if (!this.consoleIsDemo) {
        if (this.consoleIsInstitutionAdmin) owned.add(ROLES.INSTITUTION_ADMIN);
        if (this.consoleIsInstructor) owned.add(ROLES.INSTRUCTOR);
      }
      const own = new Set(this.consoleRoles || []);
      return CONSOLE_ORDER.filter((role) => owned.has(role)).map((role) => ({
        role,
        layoutKey: layoutKeyForRole(role),
        label: CONSOLE_LABELS[role],
        icon: ROLE_CONSOLE_ICONS[role] || 'dashboard',
        path: homePathForRole(role),
        // 내 역할이 아니라 상위 권한(예: 플랫폼 관리자)으로만 열 수 있는 콘솔 — "열람"으로 구분한다
        viewOnly: !own.has(role),
      }));
    },
    /** 주 콘솔 — 내 역할로 가진 첫 콘솔(관리자 > 기관 > 강사). 머리 버튼은 이것으로 바로 들어간다 */
    primaryConsole() {
      return this.consoleDestinations.find((item) => !item.viewOnly) || this.consoleDestinations[0] || null;
    },
    otherConsoles() {
      return this.consoleDestinations.filter((item) => item !== this.primaryConsole);
    },
  },
  methods: {
    ...mapActions(useAuthStore, { consoleSelectRole: 'selectRole' }),
    /** 화면이 바뀌는 것 자체가 결과다 — "이동했습니다" 알림은 띄우지 않는다 */
    goConsole(destination) {
      if (!destination) return;
      this.consoleSelectRole(destination.role);
      if (this.$route.path !== destination.path) this.$router.push(destination.path);
    },
    consoleLabel(item) {
      return item.viewOnly ? `${item.label} (열람)` : item.label;
    },
    goLearnerSite() {
      this.consoleSelectRole(ROLES.LEARNER);
      if (this.$route.path !== '/') this.$router.push('/');
    },
  },
};
