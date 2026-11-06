<template>
  <q-page padding>
    <!-- 강사 수강평 관리와 같은 화면 — 처음엔 전체 강의, 평균 별점, 오류·빈 문구 정리 -->
    <AppPageHeader
      title="수강평 관리"
      show-refresh
      :loading="loading"
      @refresh="$refs.view.reload()"
    />
    <AppInstitutionMissingBanner :show="showInstitutionMissingBanner" />
    <ConsoleReviewsView
      ref="view"
      :fetch-courses="fetchCourses"
      search-label="강의명·강사명 검색"
      search-placeholder="예: 데이터 분석, 홍길동"
      no-courses-text="기관 강의가 없습니다."
      no-reviews-text="기관 강의에 등록된 수강평이 없습니다."
      @loading="loading = $event"
    />
  </q-page>
</template>

<script>
import AppInstitutionMissingBanner from '@/components/AppInstitutionMissingBanner.vue';
import AppPageHeader from '@/components/AppPageHeader.vue';
import ConsoleReviewsView from '@/components/console/ConsoleReviewsView.vue';
import institutionScope from '@/mixins/institution-scope';
import { listInstitutionCourses } from '@/services/institution-api';

export default {
  name: 'InstitutionReviewsPage',
  components: { AppInstitutionMissingBanner, AppPageHeader, ConsoleReviewsView },
  mixins: [institutionScope],
  data() {
    return { loading: false };
  },
  computed: {
    /** 기관이 정해져야 부른다 — 기관을 바꾸면 함수가 새로 만들어져 다시 읽힌다 */
    fetchCourses() {
      const institutionId = this.institutionId;
      if (!institutionId) return null;
      return async () => {
        const page = await listInstitutionCourses(institutionId, { page: 0, size: 100, sort: 'title,asc' });
        return page.data || [];
      };
    },
  },
};
</script>
