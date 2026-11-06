<template>
  <q-page>
    <MyAccountPanel show-career />
  </q-page>
</template>

<script>
import MyAccountPanel from '@/components/MyAccountPanel.vue';

export default {
  name: 'InstructorMyPage',
  components: { MyAccountPanel },
};
</script>
