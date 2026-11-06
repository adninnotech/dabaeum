<template>
  <!-- lHh: 왼쪽 메뉴는 화면 높이 전체, 위 바는 본문 위에만 — 로고는 메뉴 맨 위, 프로필은 오른쪽 위 -->
  <q-layout view="lHh Lpr lff" class="console-shell">
    <!-- 위 바 — 프로필·화면 모드를 오른쪽 위에(예전엔 메뉴 맨 아래라 찾기 어렵다는 말이 나왔다). 폰은 메뉴 단추·로고도 -->
    <q-header bordered class="console-shell__header">
      <q-toolbar class="console-shell__toolbar">
        <template v-if="$q.screen.lt.md">
          <q-btn flat dense round icon="menu" aria-label="메뉴" @click="drawerOpen = !drawerOpen" />
          <router-link :to="brandTo" class="row items-center no-wrap q-ml-sm console-shell__home" :aria-label="brandLabel">
            <BrandLogo :size="24" logo-class="q-mr-sm" />
            <span class="console-shell__brand">다배움</span>
          </router-link>
        </template>
        <!-- 지금 어디인지 — 넓은 화면에서 로고와 프로필이 멀어 '무슨 콘솔의 어느 화면'인지 위 바 왼쪽에 -->
        <div v-else class="console-shell__where">
          <q-icon v-if="currentConsole" :name="currentConsole.icon" size="20px" aria-hidden="true" />
          <span class="console-shell__where-console">{{ currentConsoleLabel }}</span>
          <template v-if="$route.meta.title">
            <span class="console-shell__where-sep" aria-hidden="true">·</span>
            <span class="console-shell__where-page">{{ $route.meta.title }}</span>
          </template>
        </div>
        <q-space />
        <ThemeToggle class="console-shell__theme" />
        <!-- 프로필 — 글자만 떠 있어 눌리는 곳으로 안 보였다: 테두리 알약 + 진한 아바타 + 역할(메일은 펼친 메뉴에) -->
        <AppUserMenu v-if="isLoggedIn" :current-role="currentRole" class="console-profile">
          <template #trigger>
            <span class="console-profile__inner">
              <AppUserAvatar size="36px" class="console-profile__avatar" />
              <span v-if="$q.screen.gt.sm" class="console-profile__text">
                <span class="console-profile__name">{{ displayName }}</span>
                <span class="console-profile__role">{{ currentConsoleLabel }}</span>
              </span>
              <q-icon v-if="$q.screen.gt.sm" name="expand_more" size="20px" class="console-profile__chevron" />
            </span>
          </template>
        </AppUserMenu>
        <q-btn v-else unelevated no-caps color="primary" label="로그인" @click="goLogin" />
      </q-toolbar>
    </q-header>

    <q-drawer
      v-model="drawerOpen"
      bordered
      :width="320"
      :breakpoint="1023"
      :overlay="!showDesktopDrawer"
      :behavior="showDesktopDrawer ? 'desktop' : 'mobile'"
      :show-if-above="showDesktopDrawer"
      :persistent="showDesktopDrawer"
    >
      <div class="aside">
        <div class="aside__content">
          <!-- 클릭만 되던 div → 링크: Tab 이동·Enter·새 탭 열기가 된다 -->
          <router-link :to="brandTo" class="aside__logo" :aria-label="brandLabel" @click="closeOnMobile">
            <BrandLogo :size="28" />
            <span class="aside__brand">다배움</span>
          </router-link>

          <!-- 콘솔이 둘 이상인 계정은 프로필 메뉴를 거치지 않고 여기서 오간다.
               이름은 머리·프로필 메뉴와 같은 "○○ 콘솔". 지금 콘솔은 누를 일이 없으니 버튼이 아닌 표시로 둔다.
               칩 셋은 긴 이름에서 폭(320px)을 넘어 세로 목록으로 -->
          <nav v-if="consoleDestinations.length > 1" class="aside__switch" aria-label="콘솔 전환">
            <template v-for="item in consoleDestinations" :key="item.role">
              <div
                v-if="item.layoutKey === currentRole"
                class="aside__switch-item aside__switch-item--current"
                aria-current="page"
              >
                <q-icon :name="item.icon" size="16px" aria-hidden="true" />
                <span>{{ consoleLabel(item) }}</span>
                <q-icon name="check" size="16px" class="aside__switch-check" aria-hidden="true" />
              </div>
              <button
                v-else
                type="button"
                class="aside__switch-item"
                @click="closeOnMobile(); goConsole(item)"
              >
                <q-icon :name="item.icon" size="16px" aria-hidden="true" />
                <span>{{ consoleLabel(item) }}</span>
              </button>
            </template>
          </nav>

          <q-scroll-area visible :thumb-style="scrollThumbStyle" class="aside__scroll">
            <div class="aside__menu">
              <template v-for="(group, gi) in menus" :key="group.title || gi">
                <div v-if="gi > 0" class="aside__divider" />
                <div v-if="group.title" class="aside__group-title">{{ group.title }}</div>
                <router-link
                  v-for="item in group.items"
                  :key="item.to + item.label"
                  :to="item.to"
                  class="aside__item"
                  :class="{ active: isActive(item) }"
                  :aria-current="isActive(item) ? 'page' : undefined"
                  @click="closeOnMobile"
                >
                  <q-icon :name="item.icon" size="16px" aria-hidden="true" />
                  <span class="aside__label">{{ item.label }}</span>
                  <q-icon v-if="item.action === 'add'" name="add" size="16px" aria-hidden="true" />
                  <q-icon
                    v-if="item.action === 'leave'"
                    name="north_east"
                    size="16px"
                    aria-hidden="true"
                  />
                </router-link>
              </template>
            </div>
          </q-scroll-area>
        </div>

      </div>
    </q-drawer>

    <q-page-container>
      <PasswordExpiredBanner />
      <ConsoleInstitutionPicker v-if="institutionScope" />
      <!-- 기관을 바꾸면 본문을 새로 그려 페이지마다 새 기관으로 다시 읽게 한다 -->
      <router-view v-slot="{ Component }">
        <component :is="Component" :key="scopeKey" />
      </router-view>
    </q-page-container>
  </q-layout>
</template>

<script>
import AppUserAvatar from '@/components/AppUserAvatar.vue';
import { mapActions, mapState } from 'pinia';
import AppUserMenu from '@/components/AppUserMenu.vue';
import ThemeToggle from '@/components/ThemeToggle.vue';
import BrandLogo from '@/components/BrandLogo.vue';
import ConsoleInstitutionPicker from '@/components/console/ConsoleInstitutionPicker.vue';
import PasswordExpiredBanner from '@/components/console/PasswordExpiredBanner.vue';
import { useAuthStore } from '@/stores/auth-store';
import { useConsoleScopeStore } from '@/stores/console-scope-store';
import { isMenuPathActive } from '@/utils/menu-active.js';
import consoleSwitch from '@/mixins/console-switch';

// 역할 목록을 아직 못 받았을 때(프로필 불러오는 중) 위 바에 쓸 이름
const CONSOLE_FALLBACK_LABELS = { admin: '관리자 콘솔', institution: '기관 콘솔', instructor: '강사 콘솔' };

export default {
  name: 'AppConsoleLayout',
  components: { AppUserAvatar, AppUserMenu, BrandLogo, ConsoleInstitutionPicker, PasswordExpiredBanner, ThemeToggle },
  mixins: [consoleSwitch],
  props: {
    currentRole: {
      type: String,
      required: true,
    },
    /** `{ title?, items[] }` 그룹 배열 */
    menus: {
      type: Array,
      default: () => [],
    },
    /** 로고 '다배움'을 누르면 갈 곳 — 콘솔 첫 화면이 아니라 사이트 홈(학습자 메인) */
    brandTo: {
      type: String,
      default: '/',
    },
    /** 기관 콘솔: 머리에 "보고 있는 기관" 선택기 */
    institutionScope: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      drawerOpen: false,
      // opacity를 주지 않으면 Quasar 기본 .q-scrollarea__thumb의 0.2에 묻힌다
      scrollThumbStyle: {
        width: '4px',
        right: '2px',
        borderRadius: '2px',
        background: '#c4cbd4',
        opacity: 1,
      },
    };
  },
  computed: {
    ...mapState(useAuthStore, ['isLoggedIn', 'profileReady', 'displayName']),
    // 지금 보고 있는 콘솔(역할 목록에서) — 위 바 왼쪽·프로필 두 번째 줄
    currentConsole() {
      return this.consoleDestinations.find((item) => item.layoutKey === this.currentRole) || null;
    },
    currentConsoleLabel() {
      return this.currentConsole ? this.consoleLabel(this.currentConsole) : CONSOLE_FALLBACK_LABELS[this.currentRole] || '운영 콘솔';
    },
    ...mapState(useConsoleScopeStore, { pickedInstitutionId: 'institutionId' }),
    scopeKey() {
      // 기관만 key 로 — 경로·쿼리까지 넣으면 쿼리로 필터를 남기는 페이지가 매번 새로 그려진다
      return this.institutionScope ? `scope:${this.pickedInstitutionId || ''}` : undefined;
    },
    brandLabel() {
      return this.brandTo === '/' ? '다배움 홈페이지로' : '다배움';
    },
    showDesktopDrawer() {
      return this.$q.screen.gt.sm;
    },
  },
  watch: {
    showDesktopDrawer: {
      immediate: true,
      handler(show) {
        if (show) this.drawerOpen = true;
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
    closeOnMobile() {
      if (this.$q.screen.lt.md) this.drawerOpen = false;
    },
    goLogin() {
      this.closeOnMobile();
      this.$router.push({ path: '/login', query: { redirect: this.$route.fullPath } });
    },
  },
};
</script>

<style scoped>
/* 콘솔 화면의 기본 여백(<q-page padding>)을 관리자 목록 화면과 같게 — 강사·기관 콘솔은 위 16px·폰 좌우 8px 이라
   화면마다 따로 고치고 있었다. 여기 한 번이면 세 콘솔이 같다 */
.console-shell :deep(.q-page.q-layout-padding) {
  padding: 48px 40px 32px;
}

@media (max-width: 1023px) {
  .console-shell :deep(.q-page.q-layout-padding) {
    padding: 24px 16px;
  }
}

.console-shell {
  --as-text: var(--db-text-title);
  --as-label: var(--db-text-body);
  --as-border: var(--db-border);
  --as-divider: var(--db-border-light);
  --as-hover: var(--db-bg-subtle);
  --as-active: var(--db-bg-active);
  --as-brand: var(--db-primary);
}

.console-shell__brand {
  font-size: 18px;
  font-weight: 700;
  color: var(--as-brand);
}

.aside {
  display: flex;
  flex-direction: column;
  gap: 40px;
  height: 100%;
  padding: 24px 20px;
}

.aside__content {
  display: flex;
  flex-direction: column;
  gap: 24px;
  flex: 1;
  min-height: 0;
}

.aside__logo {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 48px;
  padding: 10px;
  cursor: pointer;
}

/* 콘솔 전환 — 세로 목록. 지금 콘솔은 옅은 바탕 + 체크, 나머지는 메뉴 항목처럼 */
.aside__switch {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 4px 0 8px;
  padding: 6px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.aside__switch-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 34px;
  padding: 0 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  font-size: 14px;
  color: var(--db-text-body);
  text-align: left;
  cursor: pointer;
}

.aside__switch-item:hover,
.aside__switch-item:focus-visible {
  background: var(--db-bg-subtle);
}

.aside__switch-item:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: -2px;
}

.aside__switch-item--current {
  background: var(--db-accent-soft);
  font-weight: 700;
  color: var(--db-text-title);
  cursor: default;
}

.aside__switch-check {
  margin-left: auto;
  color: var(--db-primary);
}

.aside__brand {
  font-size: 22px;
  font-weight: 700;
  color: var(--as-brand);
}

.aside__scroll {
  flex: 1;
  min-height: 0;
}

.aside__menu {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.aside__divider {
  padding: 8px 0;
}

.aside__divider::after {
  content: '';
  display: block;
  height: 1px;
  background: var(--as-divider);
}

.aside__group-title {
  display: flex;
  align-items: center;
  height: 32px;
  padding: 6px 12px;
  font-size: 14px;
  font-weight: 500;
  color: var(--as-label);
  cursor: default;
}

.aside__item {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 6px 12px;
  border-radius: 4px;
  color: var(--as-label);
  cursor: pointer;
}

.aside__item:hover {
  background: var(--as-hover);
}

.console-shell__home,
.aside__item,
.aside__logo {
  text-decoration: none;
}

.aside__item:focus-visible,
.aside__logo:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: -2px;
}

.aside__item.active {
  background: var(--as-active);
}

.aside__label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 14px;
  font-weight: 500;
}

.console-shell__header {
  background: var(--db-bg-white);
  color: var(--db-text-title);
  border-color: var(--as-border);
}

.console-shell__toolbar {
  gap: 8px;
  min-height: 60px;
  padding: 0 24px;
}

@media (max-width: 1023px) {
  .console-shell__toolbar {
    padding: 0 8px;
  }
}

.console-shell__where {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  font-size: 16px;
  color: var(--db-text-body);
  white-space: nowrap;
}

.console-shell__where-console {
  font-weight: 700;
  color: var(--db-text-title);
}

.console-shell__where-sep {
  color: var(--db-text-sub);
}

.console-shell__where-page {
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 화면 모드는 보조 — 프로필보다 튀지 않게 회색 */
.console-shell__toolbar :deep(.console-shell__theme) {
  color: var(--db-text-sub);
}

/* 프로필 — 테두리 알약 단추. 마우스를 올리면 바탕, 펼치면(aria-expanded) 테두리 강조 */
.console-profile {
  border-radius: 999px;
}

.console-profile__inner {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  max-width: 300px;
  min-height: 44px;
  padding: 3px 10px 3px 3px;
  border: 1px solid var(--db-border);
  border-radius: 999px;
  background: var(--db-bg-white);
  transition:
    background-color 0.15s ease,
    border-color 0.15s ease;
}

.console-profile:hover .console-profile__inner {
  border-color: var(--db-accent);
  background: var(--db-bg-subtle);
}

.console-profile:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.console-profile__avatar {
  flex-shrink: 0;
  background: var(--db-primary);
  color: var(--db-on-color);
  font-size: 16px;
  font-weight: 700;
}

.console-profile__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.25;
}

.console-profile__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
  font-weight: 700;
  color: var(--db-text-title);
}

.console-profile__role {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
  color: var(--db-accent);
}

.console-profile__chevron {
  flex-shrink: 0;
  color: var(--db-text-body);
}

/* 폰: 아바타만 — 테두리 원 */
@media (max-width: 1023px) {
  .console-profile__inner {
    padding: 3px;
  }
}

.console-shell :deep(.q-drawer) {
  border-color: var(--as-border);
}
</style>
