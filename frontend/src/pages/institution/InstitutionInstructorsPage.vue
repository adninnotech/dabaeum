<template>
  <q-page padding>
    <AppPageHeader title="강사 관리" subtitle="강사 신청을 승인·거절하고 소속 강사를 관리합니다." show-refresh @refresh="reload" />
    <AppInstitutionMissingBanner :show="showInstitutionMissingBanner" />

    <q-tabs :model-value="tab" align="left" active-color="primary" no-caps @update:model-value="onTab">
      <q-tab name="applications" label="강사 신청" />
      <q-tab name="roster" label="소속 강사" />
    </q-tabs>
    <q-separator class="q-mb-md" />

    <!-- 탭마다 오류·목록을 따로 든다 — 한 오류 칸을 같이 써서 탭을 바꿔도 앞 탭 오류가 남았다 -->
    <InstructorApplicationsPanel
      v-if="tab === 'applications'"
      ref="panel"
      :fetch-applications="fetchApplications"
      :status="statusFilter"
      @update:status="onStatusFilter"
    />
    <InstructorRosterPanel v-else ref="panel" :institution-id="institutionId" />
  </q-page>
</template>

<script>
import AppInstitutionMissingBanner from '@/components/AppInstitutionMissingBanner.vue';
import AppPageHeader from '@/components/AppPageHeader.vue';
import InstructorApplicationsPanel from '@/components/institution/InstructorApplicationsPanel.vue';
import InstructorRosterPanel from '@/components/institution/InstructorRosterPanel.vue';
import institutionScope from '@/mixins/institution-scope';
import { listInstitutionInstructorApplications } from '@/services/instructor-api';

const APPLICATION_PAGE_SIZE = 50;

const TABS = ['applications', 'roster'];
const APPLICATION_STATUSES = ['PENDING', 'APPROVED', 'REJECTED'];
/* 주소의 ?status=ALL 은 '전체'(필터 없음). 없으면 기본 '대기' */
const ALL_STATUS = 'ALL';

function statusFromQuery(value) {
  if (value === ALL_STATUS) return null;
  return APPLICATION_STATUSES.includes(value) ? value : 'PENDING';
}

export default {
  name: 'InstitutionInstructorsPage',
  components: { AppInstitutionMissingBanner, AppPageHeader, InstructorApplicationsPanel, InstructorRosterPanel },
  mixins: [institutionScope],
  data() {
    return {
      // 대시보드 '강사 신청 대기'가 ?tab=applications&status=PENDING 으로 연다
      tab: TABS.includes(this.$route.query.tab) ? this.$route.query.tab : 'applications',
      statusFilter: statusFromQuery(this.$route.query.status),
    };
  },
  computed: {
    /** 기관이 정해져야 부른다 — 기관을 바꾸면 함수가 새로 만들어져 다시 읽힌다 */
    fetchApplications() {
      const institutionId = this.institutionId;
      if (!institutionId) return null;
      return async ({ status }) => {
        const page = await listInstitutionInstructorApplications(institutionId, { page: 0, size: APPLICATION_PAGE_SIZE, status });
        const capped = (page.page?.totalElements || 0) > APPLICATION_PAGE_SIZE;
        return { rows: page.data || [], cappedText: capped ? `최근 신청 ${APPLICATION_PAGE_SIZE}건만 보여 줍니다.` : '' };
      };
    },
  },
  methods: {
    reload() {
      this.$refs.panel?.load();
    },
    // 탭·필터를 주소에 남겨 새로고침·뒤로가기에도 같은 목록을 본다
    syncQuery() {
      const status = this.tab === 'applications' ? this.statusFilter || ALL_STATUS : undefined;
      this.$router.replace({ query: { ...this.$route.query, tab: this.tab, status } });
    },
    onTab(name) {
      this.tab = name;
      this.syncQuery();
    },
    onStatusFilter(value) {
      this.statusFilter = value;
      this.syncQuery();
    },
  },
};
</script>
