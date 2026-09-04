<template>
  <q-badge :color="badgeColor">{{ displayLabel }}</q-badge>
</template>

<script>
import { colorForStatus, statusLabel } from '@/utils/status';

export default {
  name: 'StatusBadge',
  props: {
    status: {
      type: String,
      default: '',
    },
    label: {
      type: String,
      default: '',
    },
  },
  computed: {
    badgeColor() {
      return colorForStatus(this.status);
    },
    displayLabel() {
      return this.label || statusLabel(this.status);
    },
  },
};
</script>
