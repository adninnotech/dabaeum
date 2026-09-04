<template>
  <AppRoleLayout v-bind="layoutConfig" />
</template>

<script>
import AppRoleLayout from '@/components/AppRoleLayout.vue';
import { ROLE_LAYOUTS } from '@/config/role-layouts.js';

export default {
  name: 'RoleConsoleLayout',
  components: { AppRoleLayout },
  props: {
    layoutKey: {
      type: String,
      required: true,
      validator: (value) => Object.prototype.hasOwnProperty.call(ROLE_LAYOUTS, value),
    },
  },
  computed: {
    layoutConfig() {
      return ROLE_LAYOUTS[this.layoutKey];
    },
  },
};
</script>
