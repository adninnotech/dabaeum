<template>
  <q-page>
    <MyAccountPanel :show-withdraw="false" />
  </q-page>
</template>

<script>
import MyAccountPanel from '@/components/MyAccountPanel.vue';

export default {
  name: 'AdminMyPage',
  components: { MyAccountPanel },
};
</script>
