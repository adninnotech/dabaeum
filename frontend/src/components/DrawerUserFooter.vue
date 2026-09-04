<template>
  <div class="drawer-user q-pa-md">
    <q-separator class="q-mb-md" />
    <UserIdentity />
  </div>
</template>

<script>
import UserIdentity from '@/components/UserIdentity.vue';

export default {
  name: 'DrawerUserFooter',
  components: { UserIdentity },
};
</script>
