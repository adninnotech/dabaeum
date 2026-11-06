<template>
  <q-page class="approvals-page">
    <AppPageHeader title="가입 승인 관리" subtitle="강사 신청과 기관 등록 신청을 승인하거나 거절합니다." show-refresh @refresh="reload" />
    <!-- 다른 탭에 대기 중인 신청이 있는지 눌러 봐야 알았다 — 대기 건수를 탭에 -->
    <q-tabs :model-value="tab" align="left" active-color="primary" no-caps @update:model-value="onTab">
      <q-tab v-for="item in TAB_ITEMS" :key="item.name" :name="item.name">
        <span class="approvals-tab">
          {{ item.label }}
          <span v-if="pendingCounts[item.name]" class="approvals-tab__count">
            <span class="approvals-tab__sr">대기</span>{{ pendingCounts[item.name] }}{{ pendingCapped[item.name] ? '+' : '' }}<span class="approvals-tab__sr">건</span>
          </span>
        </span>
      </q-tab>
    </q-tabs>
    <q-separator class="q-mb-md" />

    <!-- 강사 신청은 기관 콘솔 강사 관리와 같은 패널 — 승인 확인·공용 거절 사유 창·상세에서 바로 처리 -->
    <InstructorApplicationsPanel
      v-if="tab === 'instructor'"
      ref="panel"
      show-institution
      :fetch-applications="fetchInstructorApplications"
      :status="statusFilter"
      @update:status="onStatusFilter"
      @loaded="onInstructorLoaded"
      @changed="loadPendingCounts"
    />
    <InstitutionJoinApplicationsPanel
      v-else
      ref="panel"
      :status="statusFilter"
      @update:status="onStatusFilter"
      @changed="loadPendingCounts"
    />
  </q-page>
</template>

<script>
import AppPageHeader from '@/components/AppPageHeader.vue';
import InstitutionJoinApplicationsPanel from '@/components/admin/InstitutionJoinApplicationsPanel.vue';
import InstructorApplicationsPanel from '@/components/institution/InstructorApplicationsPanel.vue';
import { listInstitutionJoinApplications, listInstitutions } from '@/services/institution-api';
import { listInstitutionInstructorApplications } from '@/services/instructor-api';

const INSTITUTION_PAGE_SIZE = 100;
const APPLICATION_PAGE_SIZE = 50;
// ?tab= 으로 받을 수 있는 탭. 그 밖의 값은 기본(강사 신청) 탭으로 연다
const TAB_ITEMS = [
  { name: 'instructor', label: '강사 신청' },
  { name: 'institution', label: '기관 신청' },
];
const TABS = TAB_ITEMS.map((item) => item.name);
const DEFAULT_TAB = 'instructor';
const APPLICATION_STATUSES = ['PENDING', 'APPROVED', 'REJECTED'];
/* 주소의 ?status=ALL 은 '전체'(필터 없음). 없으면 기본 '대기' — 기관 콘솔 강사 관리와 같은 규칙 */
const ALL_STATUS = 'ALL';

function normalizeTab(value) {
  return TABS.includes(value) ? value : DEFAULT_TAB;
}

function statusFromQuery(value) {
  if (value === ALL_STATUS) return null;
  return APPLICATION_STATUSES.includes(value) ? value : 'PENDING';
}

export default {
  name: 'AdminApprovalsPage',
  components: { AppPageHeader, InstitutionJoinApplicationsPanel, InstructorApplicationsPanel },
  data() {
    return {
      // 대시보드 '처리할 일' 링크가 ?tab= 으로 해당 신청 탭을 바로 연다
      tab: normalizeTab(this.$route.query.tab),
      // 다른 화면으로 떠나는 순간에도 $route 감시가 돌므로, 이 화면 주소일 때만 쿼리를 읽고 쓴다
      ownPath: this.$route.path,
      // 탭마다 따로 두지 않는다 — 탭을 바꾸면 '대기'로 돌아간다
      statusFilter: statusFromQuery(this.$route.query.status),
      TAB_ITEMS,
      pendingCounts: { instructor: null, institution: null },
      // 강사 신청은 기관마다 최근 50건까지만 모은다 — 넘치면 '+'
      pendingCapped: { instructor: false, institution: false },
    };
  },
  watch: {
    // 같은 화면에 머문 채 다른 링크로 들어오면(컴포넌트 재사용) 탭·필터만 바꾼다
    '$route.query': {
      handler(query) {
        if (this.$route.path !== this.ownPath) return;
        const nextTab = normalizeTab(query.tab);
        if (nextTab !== this.tab) this.tab = nextTab;
        const nextStatus = statusFromQuery(query.status);
        if (nextStatus !== this.statusFilter) this.statusFilter = nextStatus;
      },
    },
  },
  created() {
    this.loadPendingCounts();
  },
  methods: {
    reload() {
      this.$refs.panel?.load();
      this.loadPendingCounts();
    },
    // 탭·필터를 주소에 남겨 새로고침·뒤로 가기에도 같은 목록을 본다(기록은 쌓지 않는다)
    syncQuery() {
      if (this.$route.path !== this.ownPath) return;
      const status = this.statusFilter === 'PENDING' ? undefined : this.statusFilter || ALL_STATUS;
      this.$router.replace({ query: { ...this.$route.query, tab: this.tab, status } });
    },
    onTab(name) {
      this.tab = normalizeTab(name);
      this.statusFilter = 'PENDING';
      this.syncQuery();
    },
    onStatusFilter(value) {
      this.statusFilter = value;
      this.syncQuery();
    },
    onInstructorLoaded({ status, count, capped }) {
      if (status !== 'PENDING') return;
      this.pendingCounts = { ...this.pendingCounts, instructor: count };
      this.pendingCapped = { ...this.pendingCapped, instructor: !!capped };
    },
    /**
     * 탭 대기 건수 — 기관 신청은 1건만 받아 전체 수를 읽는다. 강사 신청은 기관을 도는 무거운 호출이라
     * 강사 탭이 '대기'로 열려 있으면 그 목록 수를 받고(onInstructorLoaded), 아니면 여기서 한 번 모은다.
     * 실패하면 숫자만 안 보인다 — 목록 자체의 오류는 각 패널이 알린다
     */
    async loadPendingCounts() {
      const tasks = [this.loadInstitutionPendingCount()];
      if (!(this.tab === 'instructor' && this.statusFilter === 'PENDING')) tasks.push(this.loadInstructorPendingCount());
      await Promise.all(tasks);
    },
    async loadInstitutionPendingCount() {
      try {
        const page = await listInstitutionJoinApplications({ page: 0, size: 1, status: 'PENDING' });
        this.pendingCounts = { ...this.pendingCounts, institution: page.page?.totalElements ?? (page.data || []).length };
      } catch {
        this.pendingCounts = { ...this.pendingCounts, institution: null };
      }
    },
    async loadInstructorPendingCount() {
      try {
        const { rows, cappedText } = await this.fetchInstructorApplications({ status: 'PENDING' });
        this.pendingCounts = { ...this.pendingCounts, instructor: rows.length };
        this.pendingCapped = { ...this.pendingCapped, instructor: !!cappedText };
      } catch {
        this.pendingCounts = { ...this.pendingCounts, instructor: null };
      }
    },
    /**
     * 강사 신청 — 기관별 API 뿐이라 기관을 돌며 모은다.
     * 예전엔 한 기관이 실패하면 조용히 빈 목록이 돼 '신청 없음'처럼 보였다 — 몇 곳이 실패했는지 알린다
     */
    async fetchInstructorApplications({ status }) {
      const instPage = await listInstitutions({ page: 0, size: INSTITUTION_PAGE_SIZE });
      const institutions = instPage.data || [];
      const failed = [];
      const capped = [];
      const chunks = await Promise.all(
        institutions.map(async (institution) => {
          try {
            const page = await listInstitutionInstructorApplications(institution.id, {
              page: 0,
              size: APPLICATION_PAGE_SIZE,
              status,
            });
            if ((page.page?.totalElements || 0) > APPLICATION_PAGE_SIZE) capped.push(institution.name);
            return (page.data || []).map((row) => ({ ...row, institutionName: institution.name }));
          } catch {
            failed.push(institution.name);
            return [];
          }
        }),
      );
      if (failed.length === institutions.length && institutions.length) {
        throw new Error('강사 신청을 불러오지 못했습니다.');
      }
      const notes = [];
      if (failed.length) notes.push(`${failed.length}개 기관(${failed.slice(0, 3).join(', ')}${failed.length > 3 ? ' 등' : ''})의 신청을 불러오지 못했습니다.`);
      if ((instPage.page?.totalElements || 0) > INSTITUTION_PAGE_SIZE) notes.push(`기관 ${INSTITUTION_PAGE_SIZE}곳까지만 확인했습니다.`);
      if (capped.length) notes.push(`${capped.join(', ')}은 최근 ${APPLICATION_PAGE_SIZE}건만 보입니다.`);
      return { rows: chunks.flat(), cappedText: notes.join(' ') };
    },
  },
};
</script>

<style scoped>
/* 다른 관리자 화면과 같은 여백 — 위가 좁고 폰 좌우가 8px 이었다 */
.approvals-page {
  padding: 48px 40px 32px;
}

.approvals-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
}

.approvals-tab__count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: 11px;
  background: var(--db-status-pink);
  color: var(--db-bg-white);
  font-size: 13px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

/* 숫자만 보이고, 화면 읽기에는 '대기 3건'으로 */
.approvals-tab__sr {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

@media (max-width: 1023px) {
  .approvals-page {
    padding: 24px 16px;
  }
}
</style>
