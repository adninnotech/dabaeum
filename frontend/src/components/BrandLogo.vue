<template>
  <img
    class="brand-logo"
    :class="logoClass"
    :src="src"
    alt="다배움"
    :width="size"
    :height="size"
    decoding="async"
  />
</template>

<script>
import brandLogoUrl from '@/assets/brand-logo.svg';

export default {
  name: 'BrandLogo',
  props: {
    size: {
      type: [Number, String],
      default: 28,
    },
    /** 여백용 클래스 예: q-mr-sm */
    logoClass: {
      type: String,
      default: '',
    },
  },
  data() {
    return {
      // public 상대경로가 아니라 번들 자산 — 배포 경로/라우트와 무관하게 표시
      src: brandLogoUrl,
    };
  },
};
</script>

<style scoped>
.brand-logo {
  display: block;
  flex-shrink: 0;
  object-fit: contain;
  border-radius: 0;
  background: transparent;
}
</style>
