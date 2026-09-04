<template>
  <q-page padding>
    <AppPageHeader title="공지사항" subtitle="강사 서비스의 주요 소식을 확인하세요." />
    <q-list bordered separator class="bg-white">
      <q-item v-for="(item, index) in notices" :key="item.id" clickable>
        <q-item-section avatar>
          <q-badge v-if="index < 2" color="primary">NEW</q-badge>
          <span v-else class="text-grey-6">{{ notices.length - index }}</span>
        </q-item-section>
        <q-item-section>
          <q-item-label>{{ item.title }}</q-item-label>
        </q-item-section>
        <q-item-section side>{{ item.date }}</q-item-section>
      </q-item>
    </q-list>
  </q-page>
</template>

<script>
import AppPageHeader from '@/components/AppPageHeader.vue';
import { MOCK_NOTICES } from '@/data/ui-mock.js';

export default {
  name: 'InstructorNoticesPage',
  components: { AppPageHeader },
  data() {
    return { notices: MOCK_NOTICES };
  },
};
</script>
