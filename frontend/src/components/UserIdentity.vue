<template>
  <div class="row items-center no-wrap q-gutter-sm">
    <q-avatar :size="avatarSize" color="primary" text-color="white">{{ avatarLetter }}</q-avatar>
    <div class="col ellipsis">
      <div class="text-body2 text-weight-medium ellipsis">{{ displayName }}</div>
      <div class="text-caption text-grey-7 ellipsis">{{ userEmail || '-' }}</div>
    </div>
  </div>
</template>

<script>
import { mapState } from 'pinia';
import { useAuthStore } from '@/stores/auth-store';

export default {
  name: 'UserIdentity',
  props: {
    avatarSize: {
      type: String,
      default: '36px',
    },
  },
  computed: {
    ...mapState(useAuthStore, ['displayName', 'avatarLetter', 'userEmail']),
  },
};
</script>
