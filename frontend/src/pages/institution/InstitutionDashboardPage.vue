<template>
  <q-page padding>
    <div class="app-page-header q-mb-lg">
      <div class="app-page-header__title text-h5 text-weight-bold">기관 대시보드</div>
      <div class="app-page-header__subtitle text-grey-7">기관의 강사 및 교육 운영 현황입니다.</div>
    </div>
    <div class="row q-col-gutter-md">
      <div v-for="card in cards" :key="card.label" class="col-12 col-sm-6 col-md-3">
        <q-card flat bordered>
          <q-card-section>
            <q-icon :name="card.icon" color="primary" size="30px" />
            <div class="text-caption text-grey-7 q-mt-md">{{ card.label }}</div>
            <div class="text-h5 text-weight-bold">{{ card.value }}</div>
          </q-card-section>
        </q-card>
      </div>
    </div>
  </q-page>
</template>

<script>
import { MOCK_INSTITUTION_STATS } from '@/data/ui-mock.js';

export default {
  name: 'InstitutionDashboardPage',
  data() {
    return {
      cards: [
        { label: '전체 강사', value: MOCK_INSTITUTION_STATS.total, icon: 'groups' },
        { label: '활성 강사', value: MOCK_INSTITUTION_STATS.active, icon: 'verified_user' },
        { label: '비활성 강사', value: MOCK_INSTITUTION_STATS.inactive, icon: 'person_off' },
        { label: '탈퇴 강사', value: MOCK_INSTITUTION_STATS.withdrawn, icon: 'no_accounts' },
      ],
    };
  },
};
</script>
