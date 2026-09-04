<template>
  <div class="app-page-header row items-center justify-between" :class="marginClass">
    <div>
      <div class="app-page-header__title text-h5 text-weight-bold">{{ title }}</div>
      <div v-if="subtitle" class="app-page-header__subtitle text-grey-7">{{ subtitle }}</div>
    </div>
    <div v-if="$slots.actions || showRefresh" class="row q-gutter-sm">
      <slot name="actions">
        <q-btn
          v-if="showRefresh"
          outline
          color="primary"
          icon="refresh"
          label="새로고침"
          :loading="loading"
          @click="$emit('refresh')"
        />
      </slot>
    </div>
  </div>
</template>

<script>
export default {
  name: 'AppPageHeader',
  props: {
    title: {
      type: String,
      required: true,
    },
    subtitle: {
      type: String,
      default: '',
    },
    loading: {
      type: Boolean,
      default: false,
    },
    showRefresh: {
      type: Boolean,
      default: false,
    },
    dense: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['refresh'],
  computed: {
    marginClass() {
      return this.dense ? 'q-mb-md' : 'q-mb-lg';
    },
  },
};
</script>
