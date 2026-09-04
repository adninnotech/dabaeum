<template>
  <AppRoleLayout
    current-role="learner"
    :role-label="isLoggedIn ? '학습자' : ''"
    drawer-title="학습자"
    drawer-caption="LEARNER"
    :menus="drawerMenus"
    :desktop-persistent="showDesktopSidebar"
    brand-to="/"
  >
    <template #header-center>
      <q-tabs
        v-if="$q.screen.gt.sm"
        :model-value="activeTab"
        dense
        no-caps
        active-color="primary"
        indicator-color="primary"
        class="role-shell__tabs col"
        @update:model-value="onTabChange"
      >
        <q-tab v-for="item in desktopMenus" :key="item.name" :name="item.name" :label="item.label" />
      </q-tabs>
    </template>

    <template #footer>
      <q-footer v-if="$q.screen.lt.md" elevated class="bg-white text-grey-8">
        <q-tabs
          :model-value="mobileTab"
          dense
          no-caps
          active-color="primary"
          indicator-color="transparent"
          class="role-shell__mobile-tabs"
          @update:model-value="onMobileTabChange"
        >
          <q-tab name="home" icon="home" label="홈" />
          <q-tab name="courses" icon="search" label="강좌찾기" />
          <q-tab name="mypage" icon="person" label="마이페이지" />
        </q-tabs>
      </q-footer>
    </template>
  </AppRoleLayout>
</template>

<script>
import { mapState } from 'pinia';
import AppRoleLayout from '@/components/AppRoleLayout.vue';
import { LEARNER_ACCOUNT_MENUS, LEARNER_MAIN_MENUS } from '@/config/role-menus.js';
import { useAuthStore } from '@/stores/auth-store';

export default {
  name: 'MainLayout',
  components: { AppRoleLayout },
  data() {
    return {
      activeTab: 'courses',
      mobileTab: 'courses',
    };
  },
  computed: {
    ...mapState(useAuthStore, ['isLoggedIn']),
    desktopMenus() {
      return LEARNER_MAIN_MENUS.filter((item) => {
        if (item.requiresAuth && !this.isLoggedIn) return false;
        return true;
      });
    },
    showLearnerSidebar() {
      if (!this.isLoggedIn) return false;
      const path = this.$route.path || '';
      return (
        path === '/mypage' ||
        path.startsWith('/mypage/') ||
        path === '/learning' ||
        path.startsWith('/learning/') ||
        path.startsWith('/wallet') ||
        path.startsWith('/enrollment') ||
        path.startsWith('/attendance') ||
        path.startsWith('/mypage/instructor-apply')
      );
    },
    showDesktopSidebar() {
      return this.showLearnerSidebar && this.$q.screen.gt.sm;
    },
    drawerMenus() {
      return this.showLearnerSidebar ? LEARNER_ACCOUNT_MENUS : this.desktopMenus;
    },
  },
  watch: {
    '$route.path': {
      immediate: true,
      handler(path) {
        let found = this.desktopMenus.find((m) => m.to === path);
        if (!found && (path.startsWith('/learning') || path.startsWith('/enrollment') || path.startsWith('/wallet'))) {
          found = this.desktopMenus.find((m) => m.name === 'learning');
        }
        if (!found && path.startsWith('/mypage')) {
          found = this.desktopMenus.find((m) => m.name === 'mypage');
        }
        if (!found && (path === '/' || path.startsWith('/courses'))) {
          found = this.desktopMenus.find((m) => m.name === 'courses');
        }
        if (!found && path.startsWith('/support')) {
          found = this.desktopMenus.find((m) => m.name === 'support');
        }
        const name = found?.name ?? 'courses';
        this.activeTab = name;
        if (name === 'mypage' || name === 'learning') this.mobileTab = 'mypage';
        else if (name === 'courses' || path === '/') this.mobileTab = 'courses';
      },
    },
  },
  methods: {
    onTabChange(name) {
      this.goMenu(String(name));
    },
    goMenu(name) {
      const item = LEARNER_MAIN_MENUS.find((m) => m.name === name) || this.desktopMenus.find((m) => m.name === name);
      if (!item) return;
      if (item.requiresAuth && !this.isLoggedIn) {
        this.$router.push({ path: '/login', query: { redirect: item.to } });
        return;
      }
      this.$router.push(item.to);
    },
    onMobileTabChange(name) {
      const key = String(name);
      if (key === 'home' || key === 'courses') this.$router.push('/');
      else if (key === 'mypage') {
        if (!this.isLoggedIn) {
          this.$router.push({ path: '/login', query: { redirect: '/mypage' } });
          return;
        }
        this.$router.push('/mypage');
      }
    },
  },
};
</script>

<style scoped lang="scss">
.role-shell__tabs :deep(.q-tab) {
  min-height: 64px;
  padding: 0 14px;
  font-weight: 600;
}

.role-shell__mobile-tabs :deep(.q-tab) {
  min-height: 58px;
}
</style>
