<template>
  <q-banner v-if="show" class="bg-amber-1 q-mb-md">
    세션에 기관 ID가 없습니다. 기관 관리자 계정으로 로그인해 주세요.
  </q-banner>
</template>

<script>
export default {
  name: 'AppInstitutionMissingBanner',
  props: {
    show: {
      type: Boolean,
      default: false,
    },
  },
};
</script>
