<template>
  <q-page padding class="flex flex-center">
    <div class="text-center" style="max-width: 520px">
      <q-icon :name="icon" size="56px" color="primary" />
      <div class="text-h5 text-weight-bold q-mt-md">{{ title }}</div>
      <p class="text-grey-7 q-mt-sm">
        {{ description }}
      </p>
      <q-badge color="grey-3" text-color="grey-8" class="q-mt-sm q-px-sm q-py-xs">
        API 연동 전 · UI 준비 중
      </q-badge>
    </div>
  </q-page>
</template>

<script>
export default {
  name: 'PlaceholderPage',
  props: {
    title: {
      type: String,
      default: '준비 중',
    },
    description: {
      type: String,
      default: '이 화면은 계획서 기준으로 순차 구현됩니다. 현재는 레이아웃만 연결되어 있습니다.',
    },
    icon: {
      type: String,
      default: 'construction',
    },
  },
};
</script>
