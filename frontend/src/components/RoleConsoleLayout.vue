<template>
  <AppConsoleLayout v-bind="layoutConfig" />
</template>

<script>
import AppConsoleLayout from '@/components/AppConsoleLayout.vue';
import { ROLE_LAYOUTS } from '@/config/role-layouts.js';

export default {
  name: 'RoleConsoleLayout',
  components: { AppConsoleLayout },
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
