<template>
  <q-page class="learner-page">
    <div class="learner-container learner-page__body wallet-body">
      <!-- 학습관리와 같은 학습자 화면 문법 — 큰 제목 + LearnerTabs (콘솔용 머리·q-tabs 를 걷어냈다) -->
      <header class="wallet-head">
        <div class="wallet-head__text">
          <h1 class="wallet-head__title">학습지갑</h1>
          <p class="wallet-head__sub">받은 수료증과 배지를 모아 봅니다.</p>
        </div>
        <div class="wallet-head__actions">
          <!-- 둘 다 다른 화면으로 가는 길(링크)인데 남색 테두리 버튼이라 제목·수료증보다 먼저 눈에 걸렸다(hallmark).
               본문(수료증 목록)이 주인공이 되게 회색 글자 링크로 낮춘다. 아이콘도 윤곽선 하나로 맞춘다 -->
          <!-- 포트폴리오는 지갑에 모인 수료증·배지를 한 장으로 묶어 공유하는 화면이다 -->
          <q-btn flat no-caps icon="o_folder_shared" label="학습 포트폴리오" to="/wallet/portfolio" class="wallet-head__link" />
          <!-- 번호로 진위를 보는 일은 로그인 없는 공개 화면(/verify)에서 한다 — 지갑 안 "검증" 탭을 옮겼다 -->
          <q-btn flat no-caps icon="o_verified" label="수료증 진위 확인" to="/verify" class="wallet-head__link" />
          <q-btn flat round color="grey-7" icon="refresh" aria-label="새로고침" :loading="loading" @click="loadWallet" />
        </div>
      </header>

      <q-banner v-if="!isLoggedIn" class="learner-notice">
        로그인 후 학습지갑을 확인할 수 있습니다.
        <template #action>
          <q-btn flat color="primary" label="로그인" @click="goLogin" />
        </template>
      </q-banner>

      <AppErrorBanner :message="errorMessage">
        <template #action>
          <q-btn flat color="negative" label="다시 시도" @click="loadWallet" />
        </template>
      </AppErrorBanner>

      <div class="relative-position">
        <q-inner-loading :showing="loading" />

        <!-- 누르기 전에 몇 개인지 — '수료증 1', '배지 1/7' -->
        <LearnerTabs :tabs="tabLabels" :model-value="activeTabLabel" @update:model-value="selectTab" />

        <section v-if="tab === 'credentials'" class="wallet-panel">
          <WalletCredentialList v-if="!loading || credentials.length" :credentials="credentials" />
        </section>

        <section v-else-if="tab === 'badges'" class="wallet-panel">
          <!-- 배지 현황(설계서 §11·§16): 사용 중인 정의 전체를 보여 주고 받은 것만 색으로 — definitionId 로 맞춘다 -->
          <AppErrorBanner :message="catalogError || myBadgesError" />
          <div v-if="!catalogError" class="wallet-badges">
            <div class="wallet-badges__bar">
              <p class="wallet-badges__count">
                <strong>{{ earnedCount }}</strong> / {{ badgeTotal }}개 보유
              </p>
              <router-link to="/badges" class="wallet-badges__link">배지 도감 보기</router-link>
            </div>
            <!-- 다음 단계까지 남은 강좌 수 — '1 / 7개 보유'만으로는 몰랐다 -->
            <BadgeProgress
              v-if="!myBadgesError"
              :definitions="badgeDefinitions"
              :completed-count="completedCourseCount"
            />
            <AppEmptyState
              v-if="!badgeLoading && !badgeDefinitions.length"
              compact
              title="아직 준비된 배지가 없습니다"
            />
            <BadgeGrid
              v-else
              :definitions="sortedDefinitions"
:earned-by-definition="earnedByDefinition"
              :revoked-by-definition="revokedByDefinition"
              :show-earned="!myBadgesError"
              :classification-tree="badgeClassificationTree"
              :course-titles="badgeCourseTitles"
              detail-base="/wallet/badges"
            />

            <!-- 정의 없이 수동으로 받은 옛 배지 -->
            <section v-if="legacyBadges.length" class="wallet-badges__legacy">
              <h3 class="wallet-badges__legacy-title">그 밖에 받은 배지</h3>
              <ul class="wallet-badges__legacy-list">
                <li
                  v-for="badge in legacyBadges"
                  :key="badge.id"
                  class="wallet-badges__legacy-item"
                  role="link"
                  tabindex="0"
                  @click="$router.push(`/wallet/badges/${badge.id}`)"
                  @keydown="openLegacyBadge($event, badge)"
                >
                  <BadgeMedal :size="40" :image-url="badge.imageUrl || ''" :alt="badge.badgeName" />
                  <span class="wallet-badges__legacy-name">{{ badge.badgeName || '배지' }}</span>
                  <span v-if="badge.status !== 'ISSUED'" class="wallet-badges__legacy-status">
                    {{ STATUS_LABEL[badge.status] || badge.status }}
                  </span>
                  <span class="wallet-badges__legacy-date">
                    {{ $formatDateKst(badge.issuedAt || badge.createdAt) }}
                  </span>
                </li>
              </ul>
            </section>
          </div>
        </section>

      </div>
    </div>
  </q-page>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import LearnerTabs from '@/components/LearnerTabs.vue';
import WalletCredentialList from '@/components/wallet/WalletCredentialList.vue';
import BadgeGrid from '@/components/badge/BadgeGrid.vue';
import BadgeProgress from '@/components/badge/BadgeProgress.vue';
import BadgeMedal from '@/components/badge/BadgeMedal.vue';
import badgeCatalog from '@/mixins/badge-catalog';
import { STATUS_LABEL } from '@/utils/status';
import { mapState } from 'pinia';
import { listMyCredentials, listUserCredentials } from '@/services/credential-api';
import { useAuthStore } from '@/stores/auth-store';
import { loginRequiredRoute } from '@/utils/auth-redirect';

const TABS = [
  { key: 'credentials', label: '수료증' },
  { key: 'badges', label: '배지' },
];

export default {
  components: {
    AppEmptyState,
    AppErrorBanner,
    BadgeGrid,
    BadgeMedal,
    BadgeProgress,
    LearnerTabs,
    WalletCredentialList,
  },
  name: 'WalletPage',
  mixins: [badgeCatalog],
  data() {
    return {
      // 배지 상세에서 돌아오면 ?tab=badges 로 배지 탭을 연다
      tab: TABS.some((item) => item.key === this.$route.query.tab) ? this.$route.query.tab : 'credentials',
      STATUS_LABEL,
      credentials: [],
      loading: false,
      errorMessage: '',
    };
  },
  computed: {
    ...mapState(useAuthStore, ['isLoggedIn', 'userId']),
    // 재발급으로 대체된 옛 버전은 세지 않는다 — 목록과 같은 기준(한 수료증 = 한 줄)
    credentialCount() {
      return new Set(this.credentials.map((item) => item.credentialGroupId || item.id)).size;
    },
    tabLabels() {
      return TABS.map((item) => {
        if (item.key === 'credentials' && this.credentials.length) return `${item.label} ${this.credentialCount}`;
        if (item.key === 'badges' && this.badgeTotal) return `${item.label} ${this.earnedCount}/${this.badgeTotal}`;
        return item.label;
      });
    },
    activeTabLabel() {
      return this.tabLabels[TABS.findIndex((item) => item.key === this.tab)] || this.tabLabels[0];
    },
    badgeUserId() {
      return this.isLoggedIn ? this.userId : null;
    },
    // 받은 배지를 앞에 — 도감 순서는 그 안에서 유지한다
    sortedDefinitions() {
      const earned = this.badgeDefinitions.filter((item) => this.earnedByDefinition[item.id]);
      const locked = this.badgeDefinitions.filter((item) => !this.earnedByDefinition[item.id]);
      return [...earned, ...locked];
    },
  },
  created() {
    // 옛 "검증" 탭 주소·북마크는 공개 진위 확인 화면으로 보낸다
    if (this.$route.query.tab === 'verify') this.$router.replace({ name: 'credential-verify' });
  },
  mounted() {
    // 옛 검증 탭 주소면 /verify 로 넘어가는 중이다 — 지갑을 읽지 않는다
    if (this.$route.query.tab === 'verify') return;
    this.loadWallet();
  },
  methods: {
    selectTab(label) {
      const found = TABS[this.tabLabels.indexOf(label)];
      if (!found || found.key === this.tab) return;
      this.tab = found.key;
      // 상세에서 뒤로 왔을 때 같은 탭이 열리게 주소에 남긴다
      this.$router.replace({ query: { ...this.$route.query, tab: found.key } });
    },
    openLegacyBadge(event, badge) {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        this.$router.push(`/wallet/badges/${badge.id}`);
      }
    },
    goLogin() {
      this.$router.push(loginRequiredRoute('/wallet'));
    },
    async loadWallet() {
      if (!this.isLoggedIn) {
        this.credentials = [];
        return;
      }
      this.loadBadgeCatalog();

      this.loading = true;
      this.errorMessage = '';
      try {
        let credPage;
        try {
          credPage = await listMyCredentials({ page: 0, size: 50 });
        } catch {
          credPage = await listUserCredentials(this.userId, { page: 0, size: 50 });
        }
        this.credentials = Array.isArray(credPage.data) ? credPage.data : [];

      } catch (error) {
        this.credentials = [];
        this.errorMessage = error.message || '학습지갑을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
/* 카드 격자 화면 — 읽기용 960px 대신 헤더와 같은 폭(1200)으로. 좁으면 양옆이 헤더보다 안으로 들어가 잘려 보였다 */
.learner-page__body.wallet-body {
  max-width: 100%;
}

/* 머리 — 학습관리 제목과 같은 크기, 왼쪽 선은 탭과 맞춘다 */
.wallet-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.wallet-head__text {
  min-width: 0;
}

.wallet-head__title {
  margin: 0;
  font-size: 26px;
  line-height: 31px;
  font-weight: 700;
  color: var(--db-text-body);
}

.wallet-head__sub {
  margin: 6px 0 0;
  font-size: 14px;
  color: var(--db-text-sub);
}

.wallet-head__actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 4px;
}

.wallet-head__link {
  min-height: 36px;
  padding: 0 10px;
  font-size: 14px;
  font-weight: 500;
  color: var(--db-text-sub);
  border-radius: 6px;
}

.wallet-head__link :deep(.q-icon) {
  margin-right: 6px;
  font-size: 18px;
}

.wallet-head__link:hover,
.wallet-head__link:focus-visible {
  color: var(--db-accent);
}

.wallet-panel {
  padding-top: 20px;
}

@media (max-width: 599px) {
  .wallet-head {
    flex-wrap: wrap;
    align-items: flex-start;
  }

  /* 버튼 두 개 + 새로고침이 폰 폭을 넘는다 — 줄을 넘긴다 */
  .wallet-head__actions {
    flex-wrap: wrap;
    flex-shrink: 1;
    min-width: 0;
  }
}

.wallet-badges {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.wallet-badges__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.wallet-badges__count {
  margin: 0;
  font-size: 15px;
  color: var(--db-text-body);
  font-variant-numeric: tabular-nums;
}

.wallet-badges__count strong {
  font-size: 20px;
  color: var(--db-accent);
}

.wallet-badges__link {
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  color: var(--db-accent);
}

.wallet-badges__legacy {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.wallet-badges__legacy-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--db-text-title);
}

.wallet-badges__legacy-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.wallet-badges__legacy-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
  cursor: pointer;
}

.wallet-badges__legacy-item:hover {
  background: var(--db-bg-subtle);
}

.wallet-badges__legacy-item:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.wallet-badges__legacy-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-body);
}

.wallet-badges__legacy-status {
  flex-shrink: 0;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--db-bg-page);
  font-size: 13px;
  line-height: 20px;
  color: var(--db-text-sub);
}

.wallet-badges__legacy-date {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

</style>
