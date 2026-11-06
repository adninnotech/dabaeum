<template>
  <q-badge v-if="visible" :color="badgeColor" :outline="outline">{{ displayLabel }}</q-badge>
</template>

<script>
import { shouldShowEducationTypeUI } from '@/config/features';
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
    visible() {
      return shouldShowEducationTypeUI() && !!this.type;
    },
    badgeColor() {
      return colorForEducationType(this.type);
    },
    displayLabel() {
      return educationTypeLabel(this.type);
    },
  },
};
</script>
