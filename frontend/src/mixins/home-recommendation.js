/**
 * 홈 추천 강좌 — 인기 섹션을 그린 뒤 그 강좌들을 빼고 한 번만 부른다.
 * 학습자로 로그인했으면 부를 때마다 노출 로그가 쌓이므로 다시 부르지 않는다(recommendLoaded).
 * 비거나 실패해도 섹션은 숨기지 않는다 — 제목과 '찜하면 골라 드려요' 안내를 보인다(통째로 사라지면 기능이 있는 줄 몰랐다).
 */
import { listRecommendedCourses } from '@/services/course-api';
import { decorateCourseRows } from '@/services/course-rows';
import { isLearnerVisibleCourse } from '@/utils/course-fields';
import {
  emptyRecommendationHeading,
  recommendationHeading,
  recommendationReasonText,
} from '@/utils/course-recommendation';

// 데스크톱 한 줄(4칸)
const RECOMMEND_LIMIT = 4;

export default {
  data() {
    return {
      recommendItems: [],
      recommendHeading: null,
      recommendLoaded: false,
    };
  },
  methods: {
    async loadRecommendations(excludeIds) {
      if (this.recommendLoaded) return;
      this.recommendLoaded = true;
      try {
        const { basis, items } = await listRecommendedCourses({ limit: RECOMMEND_LIMIT, excludeIds });
        const visible = items.filter((item) => isLearnerVisibleCourse(item.course));
        const courses = await decorateCourseRows(visible.map((item) => item.course));
        this.recommendItems = visible.map((item, i) => ({
          course: courses[i],
          impressionId: item.impressionId || null,
          reasonText: recommendationReasonText(item.reason),
        }));
        this.recommendHeading = visible.length
          ? recommendationHeading(basis, visible[0].reason)
          : emptyRecommendationHeading(this.isLoggedIn);
      } catch {
        this.recommendItems = [];
        this.recommendHeading = emptyRecommendationHeading(this.isLoggedIn);
      }
    },
  },
};
