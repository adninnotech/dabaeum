<template>
  <q-badge :color="badgeColor" :outline="outline">{{ displayLabel }}</q-badge>
</template>

<script>
import { colorForEducationType, educationTypeLabel } from '@/utils/status';

export default {
  name: 'EducationTypeBadge',
  props: {
    type: {
      type: String,
      default: '',
    },
    outline: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    badgeColor() {
      return colorForEducationType(this.type);
    },
    displayLabel() {
      return educationTypeLabel(this.type);
    },
  },
};
</script>
