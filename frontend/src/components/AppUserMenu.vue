<template>
  <div class="row items-center no-wrap q-gutter-sm cursor-pointer">
    <q-avatar size="32px" color="primary" text-color="white">
      <q-spinner v-if="loadingProfile" size="18px" color="white" />
      <template v-else>{{ avatarLetter }}</template>
    </q-avatar>
    <div v-if="$q.screen.gt.sm" class="role-shell__user-text">
      <div class="text-body2 text-weight-medium">{{ displayName }}님</div>
      <div class="text-caption text-grey-6">{{ subtitle }}</div>
    </div>
    <q-icon v-if="$q.screen.gt.sm" name="expand_more" color="grey-7" />
    <q-menu>
      <q-list style="min-width: 280px">
        <q-item>
          <q-item-section avatar>
            <q-avatar size="40px" color="primary" text-color="white">{{ avatarLetter }}</q-avatar>
          </q-item-section>
          <q-item-section>
            <q-item-label class="text-weight-bold">{{ displayName }}</q-item-label>
            <q-item-label caption>{{ userEmail || '-' }}</q-item-label>
            <q-item-label caption class="ellipsis">{{ userId }}</q-item-label>
          </q-item-section>
        </q-item>
        <q-item dense>
          <q-item-section>
            <div class="row q-gutter-xs">
              <q-badge v-if="userStatus" color="primary" outline>{{ userStatus }}</q-badge>
            </div>
            <div v-if="userPhone" class="text-caption text-grey-7 q-mt-xs">{{ userPhone }}</div>
            <div v-if="profileError" class="text-caption text-negative q-mt-xs">
              {{ profileError }}
            </div>
          </q-item-section>
        </q-item>

        <q-separator />
        <q-item-label header>{{ canSwitchRole ? '역할 선택' : '역할' }}</q-item-label>
        <q-item v-if="canSwitchRole" dense>
          <q-item-section>
            <div class="text-caption text-grey-7">역할을 누르면 해당 화면으로 전환합니다.</div>
          </q-item-section>
        </q-item>
        <q-item
          v-for="role in uniqueRoles"
          :key="role"
          v-close-popup
          clickable
          :active="role === primaryRole"
          active-class="bg-blue-1 text-primary"
          @click="onRoleClick(role)"
        >
          <q-item-section>
            <q-item-label>{{ roleLabel(role) }}</q-item-label>
            <q-item-label caption>{{ role }}</q-item-label>
          </q-item-section>
          <q-item-section side>
            <q-icon v-if="role === primaryRole" name="check" color="primary" />
          </q-item-section>
        </q-item>

        <template v-if="ownedConsoleEntries.length">
          <q-separator />
          <q-item-label header>운영 콘솔</q-item-label>
          <q-item
            v-for="entry in ownedConsoleEntries"
            :key="entry.role"
            v-close-popup
            clickable
            @click="goToRoleConsole(entry.role)"
          >
            <q-item-section avatar>
              <q-icon :name="entry.icon" color="primary" />
            </q-item-section>
            <q-item-section>{{ entry.label }}</q-item-section>
          </q-item>
        </template>

        <q-separator />
        <q-item v-if="showPublicSiteLink" v-close-popup clickable @click="go('/')">
          <q-item-section>학습자 화면으로</q-item-section>
        </q-item>
        <q-item v-if="currentRole === 'learner'" v-close-popup clickable @click="go('/mypage')">
          <q-item-section>마이페이지</q-item-section>
        </q-item>
        <q-item
          v-if="showDerivedInstitutionConsole"
          v-close-popup
          clickable
          @click="go('/institution')"
        >
          <q-item-section>기관 관리자 (열람)</q-item-section>
        </q-item>
        <q-item
          v-if="showDerivedInstructorConsole"
          v-close-popup
          clickable
          @click="go('/instructor')"
        >
          <q-item-section>강사 (열람)</q-item-section>
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
import { mapActions, mapState } from 'pinia';
import {
  ROLES,
  homePathForRole,
  labelForRole,
  layoutKeyForRole,
  listOwnedConsoleEntries,
} from '@/access/roles';

import { useAuthStore } from '@/stores/auth-store';

export default {
  name: 'AppUserMenu',
  props: {
    currentRole: {
      type: String,
      required: true,
    },
  },
  computed: {
    ...mapState(useAuthStore, [
      'displayName',
      'avatarLetter',
      'userId',
      'userEmail',
      'userPhone',
      'userStatus',
      'uniqueRoles',
      'primaryRole',
      'loadingProfile',
      'profileError',
      'isDemoSession',
      'isAdmin',
      'isInstructor',
      'isInstitutionAdmin',
    ]),
    canSwitchRole() {
      return this.uniqueRoles.length > 1;
    },
    subtitle() {
      if (this.loadingProfile) return '프로필 로딩 중…';
      if (this.primaryRole) return labelForRole(this.primaryRole);
      return this.userEmail || this.userId;
    },
    ownedConsoleEntries() {
      return listOwnedConsoleEntries(this.uniqueRoles, this.currentRole);
    },
    showPublicSiteLink() {
      if (this.isDemoSession) return false;
      return this.currentRole !== 'learner' && !this.uniqueRoles.includes(ROLES.LEARNER);
    },
    showDerivedInstitutionConsole() {
      if (this.isDemoSession) return false;
      return (
        this.isInstitutionAdmin &&
        this.currentRole !== 'institution' &&
        !this.uniqueRoles.includes(ROLES.INSTITUTION_ADMIN)
      );
    },
    showDerivedInstructorConsole() {
      if (this.isDemoSession) return false;
      return (
        this.isInstructor &&
        this.currentRole !== 'instructor' &&
        !this.uniqueRoles.includes(ROLES.INSTRUCTOR)
      );
    },
  },
  methods: {
    ...mapActions(useAuthStore, ['logout', 'bootstrapSession', 'selectRole']),
    roleLabel(role) {
      return labelForRole(role);
    },
    go(path) {
      this.$router.push(path);
    },
    goToRoleConsole(role) {
      this.selectRole(role);
      const path = homePathForRole(role);
      if (path && this.$route.path !== path) {
        this.go(path);
      }
      this.$q.notify({
        type: 'positive',
        message: `${labelForRole(role)} 화면으로 이동했습니다.`,
        position: 'top',
      });
    },
    onRoleClick(role) {
      if (this.canSwitchRole) {
        this.goToRoleConsole(role);
        return;
      }
      if (layoutKeyForRole(role) !== this.currentRole) {
        this.goToRoleConsole(role);
      }
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
