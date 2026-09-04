<template>
  <q-page padding>
    <div class="app-page-header q-mb-lg">
      <div class="app-page-header__title text-h5 text-weight-bold">사이트 관리자 대시보드</div>
      <div class="app-page-header__subtitle text-grey-7">다배움 플랫폼의 주요 운영 현황입니다.</div>
    </div>
    <div class="row q-col-gutter-md q-mb-lg"><div v-for="item in dashboard.kpis" :key="item.label" class="col-6 col-md-2"><q-card flat bordered class="full-height"><q-card-section><div class="text-caption text-grey-7">{{ item.label }}</div><div class="text-h5 text-weight-bold q-mt-sm">{{ item.value }}</div><q-icon v-if="item.trend" name="trending_up" color="positive" /></q-card-section></q-card></div></div>
    <div class="row q-col-gutter-lg q-mb-lg">
      <div v-for="section in summaries" :key="section.title" class="col-12 col-md-4"><q-card flat bordered class="full-height"><q-card-section class="text-h6 text-weight-bold">{{ section.title }}</q-card-section><q-separator /><q-list separator><q-item v-for="item in section.items" :key="item.label"><q-item-section>{{ item.label }}</q-item-section><q-item-section side class="text-weight-bold">{{ item.value }}</q-item-section></q-item></q-list></q-card></div>
    </div>
    <div class="row q-col-gutter-lg">
      <div class="col-12 col-md-7"><q-card flat bordered><q-card-section class="text-h6 text-weight-bold">최근 가입 회원</q-card-section><q-separator /><q-table flat :rows="dashboard.recentMembers" :columns="memberColumns" row-key="at" hide-pagination /></q-card></div>
      <div class="col-12 col-md-5"><q-card flat bordered><q-card-section class="row justify-between"><div class="text-h6 text-weight-bold">공지사항</div><q-btn flat dense color="primary" label="더보기" @click="$router.push('/admin/notices')" /></q-card-section><q-separator /><q-list separator><q-item v-for="item in notices" :key="item.id"><q-item-section>{{ item.title }}</q-item-section><q-item-section side>{{ item.date }}</q-item-section></q-item></q-list></q-card></div>
    </div>
  </q-page>
</template>
<script>
import { MOCK_NOTICES, MOCK_PLATFORM_DASHBOARD } from '@/data/ui-mock.js';
export default {
  name: 'AdminDashboardPage',
  data() { return { dashboard: MOCK_PLATFORM_DASHBOARD, notices: MOCK_NOTICES, memberColumns: [{ name: 'name', label: '이름', field: 'name', align: 'left' }, { name: 'role', label: '회원유형', field: 'role', align: 'left' }, { name: 'at', label: '가입일시', field: 'at', align: 'left' }] }; },
  computed: { summaries() { return [{ title: '회원 현황', items: this.dashboard.memberSummary }, { title: '승인 대기', items: this.dashboard.approvalSummary }, { title: '강의 현황', items: this.dashboard.courseSummary }]; } },
};
</script>
