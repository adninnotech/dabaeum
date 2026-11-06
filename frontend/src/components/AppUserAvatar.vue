<template>
  <!-- 로그인한 사람의 아바타 — 프로필 사진이 있으면 사진, 없거나 못 읽으면 이름 첫 글자.
       예전엔 머리·프로필 메뉴가 늘 글자만 그려 마이페이지에서 사진을 바꿔도 그대로였다 -->
  <q-avatar :size="size">
    <q-spinner v-if="loading" :size="spinnerSize" />
    <img v-else-if="photoUrl && failedUrl !== photoUrl" :src="photoUrl" alt="" @error="failedUrl = photoUrl" />
    <template v-else>{{ avatarLetter }}</template>
  </q-avatar>
</template>

<script>
import { mapState } from 'pinia';
import { storedFileContentUrl } from '@/services/file-api';
import { useAuthStore } from '@/stores/auth-store';

export default {
  name: 'AppUserAvatar',
  props: {
    size: { type: String, default: '32px' },
    loading: { type: Boolean, default: false },
  },
  data() {
    // 404 등으로 못 그린 사진 주소 — 같은 주소면 글자로 돌아간다(사진을 새로 바꾸면 주소가 달라져 다시 시도)
    return { failedUrl: '' };
  },
  computed: {
    ...mapState(useAuthStore, ['user', 'avatarLetter']),
    photoUrl() {
      return storedFileContentUrl(this.user?.profileImageId);
    },
    spinnerSize() {
      return `${Math.round(parseInt(this.size, 10) * 0.55)}px`;
    },
  },
};
</script>

<style scoped>
img {
  object-fit: cover;
}
</style>
