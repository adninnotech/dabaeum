import { listBadgeDefinitions } from '@/services/badge-api';
import { listCourseClassifications } from '@/services/classification-api';
import { getCourse } from '@/services/course-api';
import { listUserBadges } from '@/services/credential-api';
import { listLearningCourses } from '@/services/learner-api';

// 한 사람이 가진 배지 — 도감 규모(수십 개)라 한 페이지로 받는다
const MY_BADGE_PAGE_SIZE = 100;

/**
 * 배지 도감·학습지갑 공통 로딩. 사용하는 쪽은 badgeUserId(computed, 없으면 비로그인)를 정한다.
 * - 정의(도감) 실패만 화면 오류로 본다. 분류 이름·강좌 제목·내 배지 실패는 문구만 덜 친절해진다
 * - 내 배지 중 정의 없이 받은 옛 배지는 legacyBadges 로 따로 둔다
 */
export default {
  data() {
    return {
      badgeDefinitions: [],
      badgeClassificationTree: [],
      badgeCourseTitles: {},
      myBadges: [],
      myBadgesError: '',
      badgeLoading: false,
      catalogError: '',
      // 수료한 강좌 수 — 단계 배지 '다음 단계까지 n건' 계산용(본인만). null = 모름
      completedCourseCount: null,
    };
  },
  computed: {
    earnedByDefinition() {
      return this.myBadges.reduce((acc, badge) => {
        if (!badge.definitionId || badge.status === 'REVOKED') return acc;
        return { ...acc, [badge.definitionId]: badge };
      }, {});
    },
    // 폐기된 배지 — 수료 확정 취소로 잃은 것은 다시 확정되면 ISSUED 로 돌아오고, 관리자 폐기는 돌아오지 않는다(utils/badge-revoke)
    revokedByDefinition() {
      return this.myBadges.reduce((acc, badge) => {
        if (!badge.definitionId || badge.status !== 'REVOKED') return acc;
        return { ...acc, [badge.definitionId]: badge };
      }, {});
    },
    legacyBadges() {
      return this.myBadges.filter((badge) => !badge.definitionId);
    },
    // 단계 배지는 단계마다 한 장으로 센다(씨앗·새싹… 각각 보유/미보유)
    earnedCount() {
      return this.badgeDefinitions.reduce((sum, item) => {
        const badge = this.earnedByDefinition[item.id];
        if (item.rule?.type === 'TIERED') return sum + Math.min(badge?.tierLevel || 0, (item.rule.levels || []).length);
        return sum + (badge ? 1 : 0);
      }, 0);
    },
    badgeTotal() {
      return this.badgeDefinitions.reduce(
        (sum, item) => sum + (item.rule?.type === 'TIERED' ? (item.rule.levels || []).length || 1 : 1),
        0,
      );
    },
  },
  methods: {
    async loadBadgeCatalog() {
      this.badgeLoading = true;
      this.catalogError = '';
      this.myBadgesError = '';
      const [definitions, tree, badges] = await Promise.allSettled([
        listBadgeDefinitions(),
        listCourseClassifications(),
        this.badgeUserId
          ? listUserBadges(this.badgeUserId, { page: 0, size: MY_BADGE_PAGE_SIZE })
          : Promise.resolve({ data: [] }),
      ]);
      if (definitions.status === 'fulfilled') {
        this.badgeDefinitions = definitions.value;
      } else {
        this.badgeDefinitions = [];
        this.catalogError = definitions.reason?.message || '배지 도감을 불러오지 못했습니다.';
      }
      this.badgeClassificationTree = tree.status === 'fulfilled' ? tree.value : [];
      if (badges.status === 'fulfilled') {
        this.myBadges = Array.isArray(badges.value?.data) ? badges.value.data : [];
      } else {
        this.myBadges = [];
        this.myBadgesError = badges.reason?.message || '내 배지를 불러오지 못했습니다.';
      }
      this.badgeLoading = false;
      this.loadBadgeCourseTitles();
      this.loadCompletedCourseCount();
    },
    async loadCompletedCourseCount() {
      if (!this.badgeUserId) {
        this.completedCourseCount = null;
        return;
      }
      try {
        const page = await listLearningCourses({ page: 0, size: 1, status: 'COMPLETED' });
        this.completedCourseCount = page.page?.totalElements ?? (page.data || []).length;
      } catch {
        this.completedCourseCount = null;
      }
    },
    // 강좌 상세는 로그인이 필요하다 — 비로그인이면 "지정 강좌 수료"로 둔다
    async loadBadgeCourseTitles() {
      if (!this.badgeUserId) return;
      const ids = [
        ...new Set(this.badgeDefinitions.map((item) => item.rule?.courseId).filter(Boolean)),
      ];
      const results = await Promise.allSettled(ids.map((id) => getCourse(id)));
      this.badgeCourseTitles = results.reduce((acc, result, index) => {
        const title = result.status === 'fulfilled' ? result.value?.title : '';
        return title ? { ...acc, [ids[index]]: title } : acc;
      }, {});
    },
  },
};
