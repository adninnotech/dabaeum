<template>
  <q-page class="badges-page">
    <div class="learner-container badges-body">
      <!-- 도감에서 학습지갑으로 돌아갈 길이 없었다 -->
      <router-link v-if="badgeUserId" to="/wallet?tab=badges" class="badges-back">
        <q-icon name="arrow_back" size="18px" aria-hidden="true" />학습지갑
      </router-link>
      <header class="badges-head">
        <h1 class="badges-head__title">배지 도감</h1>
        <p class="badges-head__sub">강좌를 수료하면 조건에 맞는 배지가 자동으로 발급됩니다.</p>
      </header>

      <AppErrorBanner v-if="catalogError" :message="catalogError" />

      <template v-else>
        <div v-if="badgeUserId && badgeDefinitions.length" class="badges-bar">
          <p class="badges-bar__count">
            <strong>{{ earnedCount }}</strong> / {{ badgeTotal }}개 보유
          </p>
          <div class="badges-bar__filter" role="group" aria-label="획득 여부">
            <button
              v-for="option in FILTERS"
              :key="option.value"
              type="button"
              class="badges-chip"
              :class="{ 'badges-chip--on': filter === option.value }"
              :aria-pressed="filter === option.value"
              @click="filter = option.value"
            >
              {{ option.label }}
            </button>
          </div>
        </div>
        <BadgeProgress
          v-if="badgeUserId && !myBadgesError"
          :definitions="badgeDefinitions"
          :completed-count="completedCourseCount"
          class="badges-progress"
        />
        <p v-if="myBadgesError" class="badges-note">
          내 배지를 불러오지 못해 획득 여부를 표시하지 않았습니다.
        </p>
        <p v-else-if="!badgeUserId" class="badges-note">
          <router-link to="/login?redirect=%2Fbadges" class="badges-note__link">로그인</router-link>
          하면 내가 딴 배지를 함께 볼 수 있습니다.
        </p>

        <AppEmptyState
          v-if="!badgeLoading && !visibleDefinitions.length"
          :description="filter === 'all' ? '아직 준비된 배지가 없습니다.' : '해당하는 배지가 없습니다.'"
        />
        <BadgeGrid
          v-else
          :definitions="visibleDefinitions"
:earned-by-definition="earnedByDefinition"
          :revoked-by-definition="revokedByDefinition"
          :show-earned="!!badgeUserId && !myBadgesError"
          :classification-tree="badgeClassificationTree"
          :course-titles="badgeCourseTitles"
          :detail-base="badgeUserId ? '/wallet/badges' : ''"
        />
      </template>
    </div>

    <q-inner-loading :showing="badgeLoading">
      <q-spinner color="primary" size="32px" />
    </q-inner-loading>
  </q-page>
</template>

<script>
import { mapState } from 'pinia';
import AppEmptyState from '@/components/AppEmptyState.vue';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import BadgeProgress from '@/components/badge/BadgeProgress.vue';
import BadgeGrid from '@/components/badge/BadgeGrid.vue';
import badgeCatalog from '@/mixins/badge-catalog';
import { useAuthStore } from '@/stores/auth-store';

const FILTERS = [
  { value: 'all', label: '전체' },
  { value: 'earned', label: '획득' },
  { value: 'locked', label: '미획득' },
];

/* 배지 도감(설계서 §11·§16) — 공개 화면. 피그마에 없어 학습자 화면 여백·카드 톤을 따른다 */
export default {
  name: 'BadgeCatalogPage',
  components: { AppEmptyState, AppErrorBanner, BadgeGrid, BadgeProgress },
  mixins: [badgeCatalog],
  data() {
    return { FILTERS, filter: 'all' };
  },
  computed: {
    ...mapState(useAuthStore, ['isLoggedIn', 'userId', 'isDemoSession']),
    // 데모 세션은 서버 계정이 아니다 — 비로그인처럼 도감만 보여 준다
    badgeUserId() {
      return this.isLoggedIn && !this.isDemoSession ? this.userId : null;
    },
    visibleDefinitions() {
      if (this.filter === 'earned') {
        return this.badgeDefinitions.filter((item) => this.earnedByDefinition[item.id]);
      }
      if (this.filter === 'locked') {
        return this.badgeDefinitions.filter((item) => !this.earnedByDefinition[item.id]);
      }
      return this.badgeDefinitions;
    },
  },
  created() {
    this.loadBadgeCatalog();
  },
};
</script>

<style scoped>
.badges-back {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  gap: 4px;
  font-size: 14px;
  color: var(--db-text-sub);
  text-decoration: none;
}

.badges-back:hover,
.badges-back:focus-visible {
  color: var(--db-accent);
}

.badges-progress {
  margin-bottom: 4px;
}

.badges-page {
  position: relative;
  padding: 24px 0;
  background: var(--db-bg-page);
}

.badges-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.badges-head {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 10px 0;
}

.badges-head__title {
  margin: 0;
  font-size: 26px;
  line-height: 31px;
  font-weight: 700;
  color: var(--db-text-body);
}

.badges-head__sub {
  margin: 0;
  font-size: 15px;
  line-height: 22px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.badges-bar {
  display: flex;
  padding: 0 10px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.badges-bar__count {
  margin: 0;
  font-size: 15px;
  color: var(--db-text-body);
  font-variant-numeric: tabular-nums;
}

.badges-bar__count strong {
  font-size: 20px;
  color: var(--db-accent);
}

.badges-bar__filter {
  display: flex;
  gap: 6px;
}

.badges-chip {
  padding: 6px 14px;
  border: 1px solid var(--db-border);
  border-radius: 999px;
  background: var(--db-bg-white);
  font: inherit;
  font-size: 14px;
  line-height: 20px;
  white-space: nowrap;
  color: var(--db-text-body);
  cursor: pointer;
}

.badges-chip--on {
  border-color: var(--db-accent);
  background: var(--db-accent-soft);
  font-weight: 600;
  color: var(--db-accent);
}

.badges-chip:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.badges-note {
  margin: 0;
  padding: 0 10px;
  font-size: 14px;
  color: var(--db-text-sub);
}

.badges-note__link {
  font-weight: 600;
  color: var(--db-accent);
}
</style>
