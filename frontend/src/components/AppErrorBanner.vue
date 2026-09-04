<template>
  <q-banner v-if="message" class="bg-red-1 text-negative q-mb-md">
    {{ message }}
  </q-banner>
</template>

<script>
export default {
  name: 'AppErrorBanner',
  props: {
    message: {
      type: String,
      default: '',
    },
  },
};
</script>
