<template>
  <q-page padding>
    <AppPageHeader
      title="문의 관리"
      show-refresh
      :loading="loading"
      @refresh="$refs.list.load()"
    />
    <ConsoleInquiryList ref="list" :fetch-page="fetchPage" @loading="loading = $event" />
  </q-page>
</template>

<script>
import AppPageHeader from '@/components/AppPageHeader.vue';
import ConsoleInquiryList from '@/components/console/ConsoleInquiryList.vue';
import { listInstructorInquiries } from '@/services/inquiry-api';

export default {
  name: 'InstructorInquiriesPage',
  components: { AppPageHeader, ConsoleInquiryList },
  data() {
    return { loading: false };
  },
  methods: {
    fetchPage({ page, size }) {
      return listInstructorInquiries({ page, size, sort: 'createdAt,desc' });
    },
  },
};
</script>
