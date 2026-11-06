import { addInterest, listMyInterests, removeInterest } from '@/services/learner-api';
import { useAuthStore } from '@/stores/auth-store';

// 하트 상태를 맞추려고 한 번에 받는 관심 강좌 수 — 홈과 같은 값
const INTEREST_FETCH_SIZE = 100;

/**
 * 강좌 카드의 관심(하트) 토글 — 홈·강좌 찾기·인기 강좌가 같은 규칙을 쓴다.
 * interestByCourseId: courseId → interestId. 키가 있으면 관심 등록 상태다
 * (등록 요청 중에는 값이 null 이다).
 */
export default {
  data() {
    return {
      interestByCourseId: {},
      // 같은 강좌를 연타하면 등록 응답 전에 해제가 나가 interestId 없이 DELETE 를 부르게 된다 — 막는다
      interestPendingIds: {},
    };
  },
  methods: {
    isInterested(courseId) {
      return Object.prototype.hasOwnProperty.call(this.interestByCourseId, courseId);
    },
    /** 서버가 준 관심 목록으로 하트 상태를 통째로 갈아끼운다 */
    applyInterestRows(rows) {
      const list = Array.isArray(rows) ? rows : [];
      this.interestByCourseId = Object.fromEntries(
        list.filter((row) => row?.courseId).map((row) => [row.courseId, row.id]),
      );
    },
    /** 로그인 상태일 때만 부른다 — 비로그인으로 부르면 401 이 난다.
     *  실패해도 강좌 목록은 그대로 보여야 하니 하트만 빈 상태로 두고 알린다 */
    async loadMyInterests() {
      if (!useAuthStore().isLoggedIn) {
        this.applyInterestRows([]);
        return;
      }
      try {
        const page = await listMyInterests({ page: 0, size: INTEREST_FETCH_SIZE });
        this.applyInterestRows(page?.data);
      } catch (error) {
        this.applyInterestRows([]);
        this.$q.notify({
          type: 'warning',
          message: error?.message || '관심 강좌 목록을 불러오지 못했습니다.',
          position: 'top',
        });
      }
    },
    withoutInterest(courseId) {
      return Object.fromEntries(
        Object.entries(this.interestByCourseId).filter(([key]) => key !== String(courseId)),
      );
    },
    setInterestPending(courseId, pending) {
      const next = Object.fromEntries(
        Object.entries(this.interestPendingIds).filter(([key]) => key !== String(courseId)),
      );
      this.interestPendingIds = pending ? { ...next, [courseId]: true } : next;
    },
    /* 낙관적 토글 + 실패 시 되돌리기. 비로그인 상태에서는 하트가 보이되 누르면 로그인으로 보내고,
     * 로그인 후 보던 화면으로 돌아오게 redirect 를 싣는다 */
    async toggleInterestFor(courseId) {
      if (!courseId) return;
      if (!useAuthStore().isLoggedIn) {
        this.$router.push({ path: '/login', query: { redirect: this.$route.fullPath } });
        return;
      }
      if (this.interestPendingIds[courseId]) return;
      this.setInterestPending(courseId, true);
      try {
        if (this.isInterested(courseId)) await this.dropCourseInterest(courseId);
        else await this.addCourseInterest(courseId);
      } finally {
        this.setInterestPending(courseId, false);
      }
    },
    async dropCourseInterest(courseId) {
      // 등록 응답에 id 가 없었으면 해제할 대상을 모른다 — 서버 목록으로 다시 맞춘 뒤 찾는다
      if (!this.interestByCourseId[courseId]) await this.loadMyInterests();
      const interestId = this.interestByCourseId[courseId];
      if (!interestId) return;
      this.interestByCourseId = this.withoutInterest(courseId);
      try {
        await removeInterest(interestId);
      } catch (error) {
        this.interestByCourseId = { ...this.interestByCourseId, [courseId]: interestId };
        this.$q.notify({
          type: 'negative',
          message: error?.message || '관심 강좌 해제에 실패했습니다.',
        });
      }
    },
    async addCourseInterest(courseId) {
      this.interestByCourseId = { ...this.interestByCourseId, [courseId]: null };
      try {
        const created = await addInterest(courseId);
        this.interestByCourseId = { ...this.interestByCourseId, [courseId]: created?.id || null };
      } catch (error) {
        this.interestByCourseId = this.withoutInterest(courseId);
        this.$q.notify({
          type: 'negative',
          message: error?.message || '관심 강좌 등록에 실패했습니다.',
        });
      }
    },
  },
};
