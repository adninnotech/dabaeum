<template>
  <q-page padding>
    <AppPageHeader
      title="수강평 관리"
      show-refresh
      :loading="loading"
      @refresh="$refs.view.reload()"
    />
    <ConsoleReviewsView ref="view" :fetch-courses="fetchCourses" @loading="loading = $event" />
  </q-page>
</template>

<script>
import AppPageHeader from '@/components/AppPageHeader.vue';
import ConsoleReviewsView from '@/components/console/ConsoleReviewsView.vue';
import { listInstructorCourses } from '@/services/instructor-api';

export default {
  name: 'InstructorReviewsPage',
  components: { AppPageHeader, ConsoleReviewsView },
  data() {
    return { loading: false };
  },
  methods: {
    async fetchCourses() {
      const page = await listInstructorCourses({ page: 0, size: 100, sort: 'title,asc' });
      return page.data || [];
    },
  },
};
</script>
