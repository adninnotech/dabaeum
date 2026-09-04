<template>
  <q-page padding>
    <div class="app-page-header q-mb-lg">
      <div class="app-page-header__title text-h5 text-weight-bold">문의 관리</div>
      <div class="app-page-header__subtitle text-grey-7">기관 강의에 접수된 문의입니다.</div>
    </div>
    <q-list bordered separator class="bg-white">
      <q-item v-for="item in rows" :key="item.id">
        <q-item-section>
          <q-item-label class="text-weight-bold">{{ item.title }}</q-item-label>
          <q-item-label caption>{{ item.courseTitle }} · {{ $formatDateTimeKst(item.createdAt) }}</q-item-label>
        </q-item-section>
        <q-item-section side>
          <q-badge color="orange">{{ item.status }}</q-badge>
          <q-btn flat dense color="primary" label="답변하기" />
        </q-item-section>
      </q-item>
    </q-list>
  </q-page>
</template>

<script>
import { MOCK_INQUIRIES } from '@/data/ui-mock.js';

export default {
  name: 'InstitutionInquiriesPage',
  data() {
    return { rows: MOCK_INQUIRIES };
  },
};
</script>
