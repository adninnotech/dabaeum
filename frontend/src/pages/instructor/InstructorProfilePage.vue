<template>
  <InstructorMyPage />
</template>

<script>
import InstructorMyPage from '@/pages/instructor/InstructorMyPage.vue';

/** 기존 /instructor/profile 경로 호환 */
export default {
  name: 'InstructorProfilePage',
  components: { InstructorMyPage },
};
</script>
