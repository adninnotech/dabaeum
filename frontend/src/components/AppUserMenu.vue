<template>
  <div
    class="cursor-pointer user-menu-trigger"
    :class="triggerClass"
    tabindex="0"
    role="button"
    aria-haspopup="menu"
    @keydown.enter.prevent="openMenu"
    @keydown.space.prevent="openMenu"
  >
    <!-- 트리거를 통째로 갈아끼울 수 있다 (학습자 GNB의 이름 칩 등) -->
    <slot name="trigger">
      <AppUserAvatar
        size="32px"
        :loading="loadingProfile"
        :color="compact ? undefined : 'primary'"
        :text-color="compact ? undefined : 'white'"
        :class="{ 'user-trigger__avatar letter-avatar': compact }"
      />
      <div v-if="compact || $q.screen.gt.sm" class="role-shell__user-text user-trigger__text">
        <div :class="compact ? 'user-trigger__name' : 'text-body2 text-weight-medium'">
          {{ compact ? displayName : `${displayName}님` }}
        </div>
        <div :class="compact ? 'user-trigger__sub' : 'text-caption text-grey-6'">
          {{ showEmail ? userEmail || '-' : subtitle }}
        </div>
      </div>
      <q-icon
        v-if="compact || $q.screen.gt.sm"
        name="expand_more"
        :size="compact ? '24px' : undefined"
        :color="compact ? undefined : 'grey-7'"
        :class="{ 'user-trigger__chevron': compact }"
      />
    </slot>
    <q-menu ref="menu">
      <q-list style="min-width: 280px">
        <q-item>
          <q-item-section avatar>
            <AppUserAvatar size="40px" color="primary" text-color="white" />
          </q-item-section>
          <q-item-section>
            <q-item-label class="text-weight-bold">{{ displayName }}</q-item-label>
            <q-item-label caption>{{ userEmail || '-' }}</q-item-label>
          </q-item-section>
        </q-item>
        <q-item dense>
          <q-item-section>
            <div class="row q-gutter-xs">
              <!-- 정상(ACTIVE)은 알릴 게 없다 — 비활성·정지일 때만 한글로 -->
              <q-badge v-if="accountStatusLabel" :color="accountStatusColor">{{ accountStatusLabel }}</q-badge>
            </div>
            <div v-if="userPhone" class="text-caption text-grey-7 q-mt-xs">{{ userPhone }}</div>
            <div v-if="profileError" class="text-caption text-negative q-mt-xs">
              {{ profileError }}
            </div>
          </q-item-section>
        </q-item>

        <!-- "역할 선택"·"운영 콘솔"·"(열람)"이 같은 일을 세 번 하던 것 — 갈 수 있는 화면 목록 하나로 -->
        <template v-if="moveTargets.length">
          <q-separator />
          <q-item-label header>화면 이동</q-item-label>
          <q-item
            v-for="target in moveTargets"
            :key="target.key"
            v-close-popup
            clickable
            @click="target.go()"
          >
            <q-item-section avatar>
              <q-icon :name="target.icon" color="primary" />
            </q-item-section>
            <q-item-section>{{ target.label }}</q-item-section>
          </q-item>
        </template>

        <q-separator />
        <q-item v-close-popup clickable @click="go(mypagePath)">
          <q-item-section>마이페이지</q-item-section>
        </q-item>
        <!-- 학습자 사이트: 학습 입구는 GNB·하단 탭이 맡고, 여기는 학습지갑 바로가기만 -->
        <q-item v-if="showLearnerWallet" v-close-popup clickable @click="go('/wallet')">
          <q-item-section>학습지갑</q-item-section>
        </q-item>
        <q-item v-close-popup clickable :disable="loadingProfile" @click="reloadProfile">
          <q-item-section>프로필 새로고침</q-item-section>
        </q-item>
        <q-separator />
        <q-item v-close-popup clickable @click="handleLogout">
          <q-item-section class="text-negative">로그아웃</q-item-section>
        </q-item>
      </q-list>
    </q-menu>
  </div>
</template>

<script>
import AppUserAvatar from '@/components/AppUserAvatar.vue';
import { mapActions, mapState } from 'pinia';
import { labelForRole } from '@/access/roles';
import consoleSwitch, { LEARNER_SITE_LABEL } from '@/mixins/console-switch';

import { useAuthStore } from '@/stores/auth-store';
import { USER_STATUS_LABEL, statusColor } from '@/utils/status';

export default {
  name: 'AppUserMenu',
  components: { AppUserAvatar },
  mixins: [consoleSwitch],
  props: {
    currentRole: {
      type: String,
      required: true,
    },
    /** 두 번째 줄에 역할 대신 이메일을 보여준다 */
    showEmail: {
      type: Boolean,
      default: false,
    },
    /** 콘솔 aside 하단용 트리거 외형 (전체 폭, 화면 크기와 무관하게 텍스트 표시) */
    compact: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    /** trigger 슬롯을 쓰면 기본 트리거용 정렬 클래스를 붙이지 않는다 */
    triggerClass() {
      if (this.$slots.trigger) return '';
      return this.compact
        ? 'row items-center no-wrap user-trigger user-trigger--compact'
        : 'row items-center no-wrap q-gutter-sm';
    },
    ...mapState(useAuthStore, [
      'displayName',
      'user',
      'userEmail',
      'userPhone',
      'userStatus',
      'uniqueRoles',
      'primaryRole',
      'loadingProfile',
      'profileError',
      'isLearner',
    ]),
    subtitle() {
      if (this.loadingProfile) return '프로필 로딩 중…';
      if (this.primaryRole) return labelForRole(this.primaryRole);
      return this.userEmail || '';
    },
    accountStatusLabel() {
      if (!this.userStatus || this.userStatus === 'ACTIVE') return '';
      return `계정 ${USER_STATUS_LABEL[this.userStatus] || this.userStatus}`;
    },
    accountStatusColor() {
      return statusColor[this.userStatus] || 'grey';
    },
    showLearnerWallet() {
      return this.currentRole === 'learner' && this.isLearner;
    },
    /** 지금 화면을 뺀 이동 목록 — 콘솔에서는 학습자 사이트도 넣는다 */
    moveTargets() {
      const consoles = this.consoleDestinations
        .filter((item) => item.layoutKey !== this.currentRole)
        .map((item) => ({ key: item.role, label: this.consoleLabel(item), icon: item.icon, go: () => this.goConsole(item) }));
      if (this.currentRole === 'learner') return consoles;
      return [
        { key: 'learner', label: LEARNER_SITE_LABEL, icon: 'o_language', go: () => this.goLearnerSite() },
        ...consoles,
      ];
    },
    mypagePath() {
      if (this.currentRole === 'admin') return '/admin/mypage';
      if (this.currentRole === 'institution') return '/institution/mypage';
      if (this.currentRole === 'instructor') return '/instructor/mypage';
      return '/mypage';
    },
  },
  methods: {
    /** Enter·Space로도 열리게 — q-menu 기본 동작은 클릭뿐이다 */
    openMenu() {
      this.$refs.menu?.show();
    },
    ...mapActions(useAuthStore, ['logout', 'bootstrapSession']),
    roleLabel(role) {
      return labelForRole(role);
    },
    go(path) {
      this.$router.push(path);
    },
    reloadProfile() {
      this.bootstrapSession();
    },
    handleLogout() {
      this.logout();
      this.$q.notify({
        type: 'info',
        message: '로그아웃되었습니다.',
        position: 'top',
      });
      this.$router.push('/');
    },
  },
};
</script>

<style scoped>

.user-menu-trigger:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
  border-radius: 6px;
}

.user-trigger--compact {
  width: 100%;
  gap: 6px;
  padding: 6px;
}

.user-trigger__avatar {
  border: 1px solid var(--db-border);
}

/* compact일 때만 — 학습자 헤더 트리거 레이아웃은 건드리지 않는다 */
.user-trigger--compact .user-trigger__text {
  flex: 1;
  min-width: 0;
}

/* 두 줄 합 36px 고정 — 기본 line-height 1.5면 24+18=42가 되어 블록이 74px로 커진다 */
.user-trigger__name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 16px;
  line-height: 20px;
  font-weight: 700;
  color: var(--db-text-title);
}

.user-trigger__sub {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 13px;
  line-height: 16px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.user-trigger__chevron {
  color: var(--db-text-body);
}
</style>
