<template>
  <!-- 학습자 — 학습지갑 배지 탭에서 배지를 누르면 온다. 본인 배지만(서버가 권한을 거른다) -->
  <q-page class="learner-page">
    <div class="learner-container learner-page__body">
      <!-- 다른 학습자 화면처럼 왼쪽 위 글자 링크 — 가운데 파란 버튼이라 혼자 달랐다 -->
      <router-link :to="{ path: '/wallet', query: { tab: 'badges' } }" class="badge-detail-back">
        <q-icon name="arrow_back" size="18px" aria-hidden="true" />학습지갑 배지
      </router-link>
      <BadgeDetailView :badge-id="$route.params.badgeId" audience="self" />
    </div>
  </q-page>
</template>

<script>
import BadgeDetailView from '@/components/badge/BadgeDetailView.vue';

export default {
  name: 'BadgeDetailPage',
  components: { BadgeDetailView },
};
</script>

<style scoped>
.badge-detail-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 12px;
  font-size: 14px;
  color: var(--db-text-sub);
  text-decoration: none;
}

.badge-detail-back:hover,
.badge-detail-back:focus-visible {
  color: var(--db-accent);
}
</style>
