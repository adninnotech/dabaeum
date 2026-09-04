<template>
  <q-layout :view="layoutView" class="role-shell">
    <q-header elevated class="bg-white text-dark role-shell__header">
      <q-toolbar class="role-shell__toolbar q-px-md">
        <q-btn
          v-if="$q.screen.lt.md"
          flat
          dense
          round
          icon="menu"
          aria-label="메뉴"
          @click="leftDrawerOpen = !leftDrawerOpen"
        />

        <div class="row items-center no-wrap q-mr-md cursor-pointer" @click="goBrand">
          <BrandLogo :size="28" logo-class="q-mr-sm" />
          <div class="role-shell__brand">다배움</div>
          <q-badge v-if="roleLabel" outline color="primary" class="q-ml-sm gt-xs">{{
            roleLabel
          }}</q-badge>
        </div>

        <slot name="header-center" />

        <q-space />

        <div class="row items-center no-wrap q-gutter-sm">
          <q-btn flat round dense color="grey-8" icon="notifications_none" aria-label="알림">
            <q-badge v-if="isLoggedIn" color="negative" floating>2</q-badge>
          </q-btn>
          <AppUserMenu v-if="isLoggedIn" :current-role="currentRole" />
          <q-btn
            v-else
            unelevated
            color="primary"
            label="로그인"
            class="q-px-md"
            @click="goLogin"
          />
        </div>
      </q-toolbar>
    </q-header>

    <q-drawer
      v-model="leftDrawerOpen"
      bordered
      :overlay="!showDesktopDrawer"
      :behavior="showDesktopDrawer ? 'desktop' : 'mobile'"
      :show-if-above="showDesktopDrawer"
      :persistent="showDesktopDrawer"
      :width="250"
      :breakpoint="1023"
      content-class="role-shell-drawer"
    >
      <div class="fit column no-wrap">
        <div class="q-pa-md row items-center no-wrap">
          <BrandLogo :size="28" logo-class="q-mr-sm" />
          <div>
            <div class="text-weight-bold role-shell__drawer-title">{{ drawerTitle }}</div>
            <div class="text-caption text-grey-7">{{ drawerCaption }}</div>
          </div>
        </div>
        <q-scroll-area class="col">
          <q-list padding class="role-shell__menu">
            <q-item
              v-for="item in menus"
              :key="item.to + item.label"
              v-ripple
              clickable
              class="role-shell__item"
              :active="isActive(item)"
              active-class="role-shell__item--active"
              @click="goTo(item.to)"
            >
              <q-item-section avatar>
                <q-icon :name="item.icon" />
              </q-item-section>
              <q-item-section>{{ item.label }}</q-item-section>
            </q-item>
          </q-list>
        </q-scroll-area>
        <DrawerUserFooter v-if="isLoggedIn" />
      </div>
    </q-drawer>

    <q-page-container class="role-shell__page">
      <router-view />
    </q-page-container>

    <slot name="footer" />
  </q-layout>
</template>

<script>
import { mapActions, mapState } from 'pinia';
import AppUserMenu from '@/components/AppUserMenu.vue';
import BrandLogo from '@/components/BrandLogo.vue';
import DrawerUserFooter from '@/components/DrawerUserFooter.vue';
import { useAuthStore } from '@/stores/auth-store';
import { isMenuPathActive } from '@/utils/menu-active.js';

export default {
  name: 'AppRoleLayout',
  components: { AppUserMenu, BrandLogo, DrawerUserFooter },
  props: {
    currentRole: {
      type: String,
      required: true,
    },
    roleLabel: {
      type: String,
      default: '',
    },
    drawerTitle: {
      type: String,
      required: true,
    },
    drawerCaption: {
      type: String,
      default: '',
    },
    menus: {
      type: Array,
      default: () => [],
    },
    desktopPersistent: {
      type: Boolean,
      default: true,
    },
    brandTo: {
      type: String,
      default: '/',
    },
  },
  data() {
    return {
      leftDrawerOpen: false,
    };
  },
  computed: {
    ...mapState(useAuthStore, ['isLoggedIn', 'profileReady']),
    showDesktopDrawer() {
      return this.desktopPersistent && this.$q.screen.gt.sm;
    },
    layoutView() {
      return this.showDesktopDrawer ? 'hHh Lpr fFf' : 'hHh lpR fFf';
    },
  },
  watch: {
    showDesktopDrawer: {
      immediate: true,
      handler(show) {
        if (show) this.leftDrawerOpen = true;
      },
    },
  },
  mounted() {
    if (this.isLoggedIn && !this.profileReady) {
      this.bootstrapSession();
    }
  },
  methods: {
    ...mapActions(useAuthStore, ['bootstrapSession']),
    isActive(item) {
      return isMenuPathActive(this.$route, item);
    },
    goTo(path) {
      if (this.$q.screen.lt.md) this.leftDrawerOpen = false;
      this.$router.push(path);
    },
    goBrand() {
      if (this.$q.screen.lt.md) this.leftDrawerOpen = false;
      this.$router.push(this.brandTo);
    },
    goLogin() {
      if (this.$q.screen.lt.md) this.leftDrawerOpen = false;
      this.$router.push({
        path: '/login',
        query: { redirect: this.$route.fullPath },
      });
    },
  },
};
</script>
