<template>
  <q-page padding>
    <!-- 강사 문의 관리와 같은 목록 — 직접 만든 답변 창은 문의 본문이 안 보였고 답변한 문의는 다시 열 수 없었다 -->
    <AppPageHeader
      title="문의 관리"
      show-refresh
      :loading="loading"
      @refresh="$refs.list.load()"
    />
    <AppInstitutionMissingBanner :show="showInstitutionMissingBanner" />
    <ConsoleInquiryList ref="list" :fetch-page="fetchPage" @loading="loading = $event" />
  </q-page>
</template>

<script>
import AppInstitutionMissingBanner from '@/components/AppInstitutionMissingBanner.vue';
import AppPageHeader from '@/components/AppPageHeader.vue';
import ConsoleInquiryList from '@/components/console/ConsoleInquiryList.vue';
import institutionScope from '@/mixins/institution-scope';
import { listInstitutionInquiries } from '@/services/inquiry-api';

export default {
  name: 'InstitutionInquiriesPage',
  components: { AppInstitutionMissingBanner, AppPageHeader, ConsoleInquiryList },
  mixins: [institutionScope],
  data() {
    return { loading: false };
  },
  computed: {
    /** 기관이 정해져야 부른다 — 기관을 바꾸면 함수가 새로 만들어져 목록이 다시 읽힌다 */
    fetchPage() {
      const institutionId = this.institutionId;
      if (!institutionId) return null;
      // sort: 서버 기본 정렬이 이미 createdAt desc — 보내지 않는다(00-common §3)
      return ({ page, size }) => listInstitutionInquiries(institutionId, { page, size });
    },
  },
};
</script>
