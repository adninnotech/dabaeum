<template>
  <!-- NFT 학습배지 현황 상세(관리자) — 수강 내역은 GET /users/{userId}/enrollments -->
  <q-page class="admin-badge-detail">
    <AppPageHeader title="학습배지 상세" :subtitle="subtitle">
      <template #actions>
        <q-btn outline no-caps color="primary" icon="arrow_back" label="목록으로" @click="$router.push('/admin/badges')" />
      </template>
    </AppPageHeader>
    <BadgeDetailView :badge-id="$route.params.badgeId" audience="admin" @loaded="onLoaded" />
  </q-page>
</template>

<script>
import AppPageHeader from '@/components/AppPageHeader.vue';
import BadgeDetailView from '@/components/badge/BadgeDetailView.vue';

export default {
  name: 'AdminBadgeDetailPage',
  components: { AppPageHeader, BadgeDetailView },
  data() {
    return { subtitle: '' };
  },
  methods: {
    onLoaded(badge) {
      const who = badge.recipientName || '';
      this.subtitle = who ? `${who}의 배지` : '';
    },
  },
};
</script>

<style scoped>
/* 다른 관리자 화면(통계·회원 상세)과 같은 여백 — 위가 좁고 폰 좌우가 8px 이었다.
 * 폭은 다른 관리자 화면처럼 콘솔 전체 — 1200px 로 막아 넓은 화면에서 오른쪽이 비었다 */
.admin-badge-detail {
  padding: 48px 40px 32px;
}

@media (max-width: 1023px) {
  .admin-badge-detail {
    padding: 24px 16px;
  }
}
</style>
