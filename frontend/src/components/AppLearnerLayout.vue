<template>
  <q-layout view="hHh lpR fff" class="learner-shell">
    <q-header class="learner-header" @mouseleave="scheduleMegaClose">
      <div class="learner-container learner-header__inner">
        <router-link to="/" class="learner-brand" aria-label="다배움 홈">
          <BrandLogo :size="28" />
          <span class="learner-brand__text">다배움</span>
        </router-link>

        <!-- 데스크톱 GNB -->
        <nav v-if="$q.screen.gt.sm" class="learner-nav">
          <!-- 기본 메뉴는 늘 — 메뉴 관리(GET /menus)에서 만든 메뉴는 그 뒤에 붙는다.
               예전엔 메뉴를 하나만 만들어도 강좌찾기·학습관리 등이 통째로 사라졌다 -->
          <button
            v-for="item in menus"
            :key="item.name"
            type="button"
            class="learner-nav__item"
            :class="{ active: !activeCmsId && item.name === activeMenu }"
            v-bind="item.name === 'courses' ? megaTriggerAttrs : {}"
            @mouseenter="item.name === 'courses' ? queueMegaOpen() : closeMega()"
            @keydown="item.name === 'courses' && onMegaTriggerKeydown($event)"
            @click="goMenu(item.name)"
          >
            {{ item.label }}
          </button>
          <button
            v-for="item in cmsMenus"
            :key="item.id"
            type="button"
            class="learner-nav__item"
            :class="{ active: item.id === activeCmsId }"
            v-bind="isCoursesCms(item) ? megaTriggerAttrs : {}"
            @mouseenter="isCoursesCms(item) ? queueMegaOpen() : closeMega()"
            @keydown="isCoursesCms(item) && onMegaTriggerKeydown($event)"
            @click="item.children.length ? null : goCms(item)"
          >
            {{ item.name }}
            <q-icon v-if="item.children.length" name="expand_more" size="18px" />
            <q-menu v-if="item.children.length" anchor="bottom left" self="top left">
              <q-list dense class="learner-nav__sub">
                <q-item v-if="item.target" v-close-popup clickable @click.stop="goCms(item)">
                  <q-item-section>{{ item.name }} 바로가기</q-item-section>
                </q-item>
                <q-item v-for="sub in item.children" :key="sub.id" v-close-popup clickable @click.stop="goCms(sub)">
                  <q-item-section>{{ sub.name }}</q-item-section>
                  <q-item-section v-if="sub.linkType === 'EXTERNAL'" side>
                    <q-icon name="open_in_new" size="14px" />
                  </q-item-section>
                </q-item>
              </q-list>
            </q-menu>
          </button>
        </nav>
        <q-space v-else />

        <div class="learner-user">
          <!-- 홈·통합검색 화면엔 큰 검색창이 있다 — 겹치지 않게 헤더 검색창을 뺀다 -->
          <!-- 1024~1279px 는 메뉴 5개 + 검색창 + 콘솔 버튼 + 이름이 한 줄에 안 들어가 검색창이 메뉴를 덮었다 — 아이콘으로 접는다 -->
          <HeaderSearch v-if="!hasPortalSearch" :compact="$q.screen.lt.md || headerTight" />
          <!-- 화면 모드는 머리에서 뺐다 — 한 번 고르면 다시 안 건드리는데 알림·프로필과 같은 무게로 늘 보였다.
               데스크톱은 [전체메뉴] 안, 폰은 메뉴(드로어)에 -->
          <template v-if="isLoggedIn">
            <!-- 콘솔이 주 목적지인 강사·기관·관리자가 프로필 메뉴를 열지 않고 바로 들어가게 -->
            <template v-if="$q.screen.gt.sm && primaryConsole">
              <!-- 주 콘솔은 한 번에 들어가고(관리자도 두 번 누르지 않게), 다른 콘솔은 옆 ▾ 에.
                   학습자 사이트의 주인공은 학습 메뉴라 버튼은 회색 테두리로 조용하게 -->
              <q-btn
                v-if="!otherConsoles.length"
                outline
                no-caps
                color="grey-8"
                class="learner-console-btn"
                :icon="primaryConsole.icon"
                :label="headerTight ? undefined : consoleLabel(primaryConsole)"
                :aria-label="consoleLabel(primaryConsole)"
                @click="goConsole(primaryConsole)"
              >
                <q-tooltip v-if="headerTight">{{ consoleLabel(primaryConsole) }}</q-tooltip>
              </q-btn>
              <q-btn-dropdown
                v-else
                split
                outline
                no-caps
                color="grey-8"
                class="learner-console-btn"
                :icon="primaryConsole.icon"
                :label="headerTight ? undefined : consoleLabel(primaryConsole)"
                dropdown-icon="expand_more"
                :aria-label="`${primaryConsole.label}, 다른 콘솔 보기`"
                @click="goConsole(primaryConsole)"
              >
                <q-list dense>
                  <q-item
                    v-for="item in otherConsoles"
                    :key="item.role"
                    v-close-popup
                    clickable
                    @click="goConsole(item)"
                  >
                    <q-item-section avatar><q-icon :name="item.icon" color="grey-8" /></q-item-section>
                    <q-item-section>{{ consoleLabel(item) }}</q-item-section>
                  </q-item>
                </q-list>
              </q-btn-dropdown>
            </template>
            <!-- 알림 벨은 뺐다(#144) — 알림을 만드는 곳이 없어 늘 비어 있었다 -->
            <AppUserMenu current-role="learner">
              <template #trigger>
                <div class="learner-user__trigger" :title="displayName">
                  <AppUserAvatar size="28px" class="letter-avatar" />
                  <span v-if="!headerTight" class="learner-user__name">{{ displayName }}</span>
                </div>
              </template>
            </AppUserMenu>
          </template>
          <template v-else>
            <!-- 따로 가입 화면이 없다 — 다대구 로그인이 처음 온 사람의 가입까지 맡는다(#112) -->
            <q-btn unelevated no-caps color="primary" class="btn-sm" label="로그인" @click="goLogin" />
          </template>
          <!-- 전체 메뉴(☰) — 마우스를 올리지 않고 눌러서 모든 분야·메뉴를 본다.
               앞의 '내 것'(알림·프로필)과 가는 세로선으로 나눠 묶음이 둘로 읽히게 -->
          <span v-if="$q.screen.gt.sm" class="learner-user__divider" aria-hidden="true" />
          <q-btn
            v-if="$q.screen.gt.sm"
            flat
            no-caps
            icon="menu"
            class="learner-full-menu-btn"
            :label="headerTight ? undefined : '전체메뉴'"
            aria-label="전체 메뉴"
            aria-haspopup="dialog"
            @click="openFullMenu"
          />
          <q-btn
            v-if="$q.screen.lt.md"
            flat
            dense
            round
            icon="menu"
            aria-label="메뉴"
            @click="drawerOpen = true"
          />
        </div>
      </div>
      <!-- 전체 메뉴 패널이 떠 있는 동안 본문을 어둡게 — 패널에 눈이 가고, 본문으로 마우스를 내리면 닫힌다 -->
      <div v-if="$q.screen.gt.sm && megaOpen" class="learner-mega-backdrop" aria-hidden="true" @mouseenter="closeMega" />
      <CourseMegaMenu
        v-if="$q.screen.gt.sm"
        ref="megaMenu"
        :open="megaOpen"
        @hold="holdMega"
        @leave="scheduleMegaClose"
        @close="onMegaClose"
      />
    </q-header>

    <!-- 모바일 메뉴 (mobile-* 프레임은 5단계) -->
    <q-drawer v-model="drawerOpen" side="right" overlay behavior="mobile" :width="260">
      <!-- 메뉴 맨 위에 로그인 — 가입은 다대구 로그인에서 이어진다(#112) -->
      <div v-if="!isLoggedIn" class="learner-drawer__auth">
        <q-btn unelevated no-caps color="primary" label="로그인" @click="drawerOpen = false; goLogin()" />
      </div>
      <!-- 폰에선 머리에 자리가 없다 — 메뉴 맨 위에 콘솔 바로가기 -->
      <q-list v-if="isLoggedIn && consoleDestinations.length" padding class="learner-drawer__consoles">
        <q-item-label header>운영 콘솔</q-item-label>
        <q-item
          v-for="item in consoleDestinations"
          :key="item.role"
          v-ripple
          clickable
          @click="drawerOpen = false; goConsole(item)"
        >
          <q-item-section avatar><q-icon :name="item.icon" color="primary" /></q-item-section>
          <q-item-section>{{ consoleLabel(item) }}</q-item-section>
        </q-item>
        <q-separator spaced />
      </q-list>
      <q-list padding class="learner-drawer__full">
        <q-item v-ripple clickable @click="openFullMenu">
          <q-item-section avatar><q-icon name="apps" color="primary" /></q-item-section>
          <q-item-section>강좌 분야·전체 메뉴</q-item-section>
          <q-item-section side><q-icon name="chevron_right" /></q-item-section>
        </q-item>
        <q-separator spaced />
      </q-list>
      <q-list padding>
        <q-item
          v-for="item in menus"
          :key="item.name"
          v-ripple
          clickable
          @click="goMenu(item.name)"
        >
          <q-item-section avatar><q-icon :name="item.icon" /></q-item-section>
          <q-item-section>{{ item.label }}</q-item-section>
        </q-item>
      </q-list>
      <!-- 메뉴 관리에서 만든 메뉴 — 기본 메뉴 아래에 더한다 -->
      <q-list v-if="cmsMenus.length" padding class="learner-drawer__cms">
        <q-separator spaced />
        <template v-for="item in cmsMenus" :key="item.id">
          <q-item v-ripple clickable :disable="!item.target && !item.children.length" @click="goCms(item)">
            <q-item-section>{{ item.name }}</q-item-section>
          </q-item>
          <q-item
            v-for="sub in item.children"
            :key="sub.id"
            v-ripple
            clickable
            class="learner-drawer__sub"
            @click="goCms(sub)"
          >
            <q-item-section>{{ sub.name }}</q-item-section>
          </q-item>
        </template>
      </q-list>
      <q-list padding class="learner-drawer__theme">
        <q-separator spaced />
        <q-item-label header>화면 모드</q-item-label>
        <q-item
          v-for="option in THEME_OPTIONS"
          :key="option.value"
          v-ripple
          clickable
          :active="themePreference === option.value"
          @click="setThemePreference(option.value)"
        >
          <q-item-section avatar><q-icon :name="option.icon" /></q-item-section>
          <q-item-section>{{ option.label }}</q-item-section>
          <q-item-section v-if="themePreference === option.value" side>
            <q-icon name="check" size="18px" />
          </q-item-section>
        </q-item>
      </q-list>
    </q-drawer>

    <FullMenuDialog v-model="fullMenuOpen" :menus="fullMenus" @menu="goMenu" />

    <q-page-container>
      <!-- 모든 화면 머리 아래 통합검색 띠 — 중장년 이용자가 '검색은 늘 여기'로 기억하게(벤치마킹: 대구평생학습플랫폼 수강신청).
           한 화면에 검색칸은 하나만: 띠가 있으면 머리 검색칸은 뺀다 -->
      <section v-if="showSearchBand" class="learner-search-band" aria-label="통합검색">
        <div class="learner-container learner-search-band__inner">
          <!-- 폰 폭에서도 잘리지 않게 짧은 안내 -->
          <PortalSearchBox
            v-model="bandQuery"
            class="learner-search-band__box"
            placeholder="강좌·공지·질문 검색"
            @submit="submitBandSearch"
          />
        </div>
      </section>
      <router-view />
    </q-page-container>

    <!-- 사이트 푸터는 본문 흐름에 둔다. 탭바 높이만큼 아래를 비워(learner-footer--tabs) 마지막 줄이 탭바에 가리지 않게 한다.
         Quasar 가 푸터 높이를 재서 page-container 의 padding-bottom 으로 넣으므로 여기 여백이 본문 여백이 된다 -->
    <q-footer class="learner-footer" :class="{ 'learner-footer--tabs': $q.screen.lt.md }">
      <AppSiteFooter />
    </q-footer>

    <!-- 모바일 하단 탭바 (기존 MainLayout에서 이관) — view 가 소문자 f 라 q-footer 안에 두면 페이지 끝에서만 보였다.
         푸터와 떼어 화면 하단에 고정한다 -->
    <div v-if="$q.screen.lt.md" class="learner-mobile-tabs">
      <q-tabs
        :model-value="mobileTab"
        dense
        no-caps
        active-color="primary"
        indicator-color="transparent"
        @update:model-value="onMobileTabChange"
      >
        <!-- 마이페이지는 헤더 사용자 메뉴·햄버거 메뉴에 있다 — 탭은 자주 쓰는 학습 입구만 -->
        <q-tab name="home" icon="home" label="홈" />
        <q-tab name="courses" icon="search" label="강좌찾기" />
        <q-tab name="learning" icon="school" label="학습관리" />
        <q-tab name="wallet" icon="workspace_premium" label="학습지갑" />
      </q-tabs>
    </div>
  </q-layout>
</template>

<script>
import AppUserAvatar from '@/components/AppUserAvatar.vue';
import { mapActions, mapState } from 'pinia';
import AppSiteFooter from '@/components/AppSiteFooter.vue';
import AppUserMenu from '@/components/AppUserMenu.vue';
import { THEME_OPTIONS } from '@/boot/theme';
import { useThemeStore } from '@/stores/theme-store';
import consoleSwitch from '@/mixins/console-switch';
import BrandLogo from '@/components/BrandLogo.vue';
import CourseMegaMenu from '@/components/CourseMegaMenu.vue';
import FullMenuDialog from '@/components/FullMenuDialog.vue';
import HeaderSearch from '@/components/HeaderSearch.vue';
import PortalSearchBox from '@/components/PortalSearchBox.vue';
import { SEARCH_MIN_LENGTH } from '@/services/search-api';
import { LEARNER_MAIN_MENUS } from '@/config/role-menus.js';
import { listPublicMenus } from '@/services/cms-api';
import { menuTarget } from '@/utils/site-menu';
import { useAuthStore } from '@/stores/auth-store';
import { loginRequiredRoute } from '@/utils/auth-redirect';

// 본문에 큰 검색창(PortalSearchBox)이 있는 화면
const PORTAL_SEARCH_PATHS = ['/', '/search'];
// 통합검색 띠를 두지 않는 화면 — 자기 큰 검색창이 있거나(홈·통합검색·강좌찾기 목록) 카메라가 화면의 전부(QR 출석)
const NO_SEARCH_BAND_PATHS = [...PORTAL_SEARCH_PATHS, '/courses', '/attendance/scan'];

// 메뉴 5개·검색창·콘솔 버튼·이름이 한 줄에 다 들어가는 최소 폭
const HEADER_FULL_WIDTH = 1280;

// 강좌찾기 전체 메뉴 — 지나가던 마우스에 펼쳐지지 않게 조금 머문 뒤 열고, 패널로 내려가는 사이 닫히지 않게 늦게 닫는다
const MEGA_OPEN_DELAY_MS = 120;
const MEGA_CLOSE_DELAY_MS = 400;

export default {
  name: 'AppLearnerLayout',
  mixins: [consoleSwitch],
  components: {
    AppUserAvatar,
    AppSiteFooter,
    AppUserMenu,
    BrandLogo,
    CourseMegaMenu,
    FullMenuDialog,
    HeaderSearch,
    PortalSearchBox,
  },
  data() {
    return { drawerOpen: false, cmsTree: [], THEME_OPTIONS, megaOpen: false, megaTimer: 0, fullMenuOpen: false, bandQuery: '' };
  },
  computed: {
    megaTriggerAttrs() {
      return { 'aria-haspopup': 'true', 'aria-expanded': String(this.megaOpen), 'aria-controls': 'course-mega-menu' };
    },
    // 데스크톱 메뉴는 보이지만 머리 줄이 좁은 폭(1024~1279px) — 검색·콘솔 버튼을 아이콘으로, 이름 칩은 뺀다
    headerTight() {
      return this.$q.screen.gt.sm && this.$q.screen.width < HEADER_FULL_WIDTH;
    },
    showSearchBand() {
      return !NO_SEARCH_BAND_PATHS.includes(this.$route.path);
    },
    // 본문에 큰 검색창이나 통합검색 띠가 있으면 머리 검색칸을 뺀다(검색칸은 한 화면에 하나)
    hasPortalSearch() {
      return PORTAL_SEARCH_PATHS.includes(this.$route.path) || this.showSearchBand;
    },
    ...mapState(useThemeStore, { themePreference: 'preference' }),
    ...mapState(useAuthStore, [
      'isLoggedIn',
      'isLearner',
      'profileReady',
      'displayName',
    ]),
    menus() {
      return LEARNER_MAIN_MENUS.filter((item) => {
        if (item.requiresAuth && !this.isLoggedIn) return false;
        if (item.requiresLearner && !this.isLearner) return false;
        return true;
      });
    },
    // 전체 메뉴는 로그인 전에도 학습관리·학습지갑·마이페이지를 보인다 — 누르면 로그인(가입 안내)으로 간다(goMenu)
    fullMenus() {
      return LEARNER_MAIN_MENUS.filter((item) => !(this.isLoggedIn && item.requiresLearner && !this.isLearner));
    },
    activeMenu() {
      const path = this.$route.path || '';
      if (path.startsWith('/learning') || path.startsWith('/enrollment')) return 'learning';
      if (path.startsWith('/wallet') || path.startsWith('/badges')) return 'wallet';
      if (path.startsWith('/mypage')) return 'mypage';
      // 공지사항은 고객센터 소관이다
      if (path.startsWith('/support') || path.startsWith('/notices') || path.startsWith('/policies')) return 'support';
      // 강좌 화면에서만 "강좌찾기"를 켠다 — 홈·통합검색 등에서 켜져 있던 것
      if (path.startsWith('/courses')) return 'courses';
      return '';
    },
    // CMS 메뉴 — 갈 곳을 미리 풀어 둔다. 갈 곳도 하위도 없는 메뉴는 뺀다
    cmsMenus() {
      const withTarget = (node) => ({
        ...node,
        target: menuTarget(node),
        children: (node.children || []).map(withTarget).filter((child) => child.target),
      });
      // 기본 메뉴를 그대로 다시 만든 것(이름·갈 곳이 같은 '강좌찾기')만 두 번 보이지 않게 뺀다.
      // 갈 곳만 같고 이름이 다르면(예: '내 정보' → 마이페이지) 관리자가 일부러 만든 것이라 남긴다
      const isDefaultCopy = (item) =>
        LEARNER_MAIN_MENUS.some((menu) => menu.label === item.name && menu.to === item.target?.path);
      return this.cmsTree
        .map(withTarget)
        .filter((item) => item.target || item.children.length)
        .filter((item) => item.children.length || !isDefaultCopy(item));
    },
    // 지금 경로와 가장 길게 겹치는 메뉴(자신 또는 하위)를 켠다 — "/" 는 홈에서만
    activeCmsId() {
      const path = this.$route.path || '/';
      let best = { id: '', length: -1 };
      const consider = (id, target) => {
        const to = target?.path;
        if (!to) return;
        const hit = to === '/' ? path === '/' : path === to || path.startsWith(`${to}/`);
        if (hit && to.length > best.length) best = { id, length: to.length };
      };
      this.cmsMenus.forEach((item) => {
        consider(item.id, item.target);
        item.children.forEach((sub) => consider(item.id, sub.target));
      });
      return best.id;
    },
    mobileTab() {
      if (this.$route.path === '/') return 'home';
      return ['courses', 'learning', 'wallet'].includes(this.activeMenu) ? this.activeMenu : '';
    },
  },
  watch: {
    // 다른 화면으로 가면 펼친 메뉴를 접는다(패널 안 링크·검색·뒤로가기 모두)
    '$route.fullPath'() {
      this.closeMega();
      this.bandQuery = '';
    },
  },
  mounted() {
    if (this.isLoggedIn && !this.profileReady) this.bootstrapSession();
    this.loadCmsMenus();
  },
  beforeUnmount() {
    window.clearTimeout(this.megaTimer);
  },
  methods: {
    ...mapActions(useAuthStore, ['bootstrapSession']),
    ...mapActions(useThemeStore, { setThemePreference: 'setPreference' }),
    // 메뉴를 못 받으면 기본 메뉴로 둔다 — 내비게이션이 비는 것보다 낫다
    async loadCmsMenus() {
      try {
        this.cmsTree = await listPublicMenus();
      } catch {
        this.cmsTree = [];
      }
    },
    goCms(item) {
      // 하위만 있는 상위 메뉴는 드롭다운만 연다(데스크톱) / 폰에서는 하위 줄이 따로 있다
      if (!item.target) return;
      this.drawerOpen = false;
      if (item.target.external) {
        window.open(item.target.href, '_blank', 'noopener');
        return;
      }
      this.$router.push(item.target.path);
    },
    // CMS 메뉴 중 하위 없이 강좌찾기로 가는 항목에만 전체 메뉴를 붙인다(하위가 있으면 그 드롭다운이 우선)
    isCoursesCms(item) {
      return !item.children.length && item.target?.path === '/courses';
    },
    submitBandSearch() {
      const q = this.bandQuery.trim();
      if (q.length < SEARCH_MIN_LENGTH) {
        this.$q.notify({ type: 'warning', message: `검색어를 ${SEARCH_MIN_LENGTH}자 이상 입력해 주세요.`, position: 'top' });
        return;
      }
      this.$router.push({ path: '/search', query: { q } });
    },
    openFullMenu() {
      this.drawerOpen = false;
      this.closeMega();
      this.fullMenuOpen = true;
    },
    queueMegaOpen() {
      window.clearTimeout(this.megaTimer);
      if (this.megaOpen) return;
      this.megaTimer = window.setTimeout(() => {
        this.megaOpen = true;
      }, MEGA_OPEN_DELAY_MS);
    },
    holdMega() {
      window.clearTimeout(this.megaTimer);
    },
    scheduleMegaClose() {
      window.clearTimeout(this.megaTimer);
      if (!this.megaOpen) return;
      this.megaTimer = window.setTimeout(() => {
        this.megaOpen = false;
      }, MEGA_CLOSE_DELAY_MS);
    },
    closeMega() {
      window.clearTimeout(this.megaTimer);
      this.megaOpen = false;
    },
    // Esc 로 닫으면 [강좌찾기]로 초점을 돌려 키보드 사용자가 길을 잃지 않게
    onMegaClose(options) {
      this.closeMega();
      if (options?.restoreFocus) this.$el.querySelector('[aria-controls="course-mega-menu"]')?.focus();
    },
    // 키보드: ↓ 로 펼치고 첫 분야로, Esc 로 접는다(Enter 는 강좌찾기로 바로 간다)
    onMegaTriggerKeydown(event) {
      if (event.key === 'Escape') {
        this.closeMega();
        return;
      }
      if (event.key !== 'ArrowDown') return;
      event.preventDefault();
      window.clearTimeout(this.megaTimer);
      this.megaOpen = true;
      this.$refs.megaMenu?.focusFirst();
    },
    goLogin() {
      this.$router.push({ path: '/login', query: { redirect: this.$route.fullPath } });
    },
    goMenu(name) {
      this.drawerOpen = false;
      const item = LEARNER_MAIN_MENUS.find((menu) => menu.name === name);
      if (!item) return;
      if (item.requiresAuth && !this.isLoggedIn) {
        this.$router.push(loginRequiredRoute(item.to));
        return;
      }
      if (item.requiresLearner && !this.isLearner) {
        this.$q.notify({ type: 'warning', message: '학습자 권한이 필요합니다.', position: 'top' });
        return;
      }
      this.$router.push(item.to);
    },
    onMobileTabChange(name) {
      const key = String(name);
      if (key === 'home') this.$router.push('/');
      else this.goMenu(key);
    },
  },
};
</script>

<style scoped>
.learner-header {
  height: 68px;
  background: var(--db-bg-white);
  border-bottom: 1px solid var(--db-border);
  color: var(--db-text-body);
  box-shadow: none;
}

.learner-header__inner {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 68px;
}

/* 로고(.learner-brand padding 10)가 본문 16 선과 맞도록 6만 준다 */
@media (max-width: 1023px) {
  .learner-header__inner {
    padding-left: 6px;
    padding-right: 6px;
  }
}

.learner-brand:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: -2px;
  border-radius: 6px;
}

.learner-brand {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 8px;
  width: 160px;
  padding: 10px;
  text-decoration: none;
  cursor: pointer;
}

.learner-brand__text {
  font-size: 22px;
  font-weight: 700;
  color: var(--db-primary);
}

.learner-nav {
  display: flex;
  flex: 1;
  gap: 8px;
}

.learner-nav__item {
  height: 67px;
  padding: 0 12px;
  background: none;
  border: none;
  font-size: 16px;
  font-weight: 500;
  color: var(--db-text-body);
  white-space: nowrap;
  cursor: pointer;
}

.learner-nav__item {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.learner-nav__sub {
  min-width: 180px;
  font-size: 15px;
  padding: 4px 0;
}

.learner-drawer__sub {
  padding-left: 32px;
  font-size: 14px;
  color: var(--db-text-sub);
}

.learner-nav__item {
  position: relative;
  transition: color 0.18s ease;
}

/* 밑줄 하나로 hover·현재 메뉴를 함께 — 올리면 글자 폭만큼 가운데서 펴지고, 현재 메뉴는 펴진 채 남는다 */
.learner-nav__item::after {
  content: '';
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 0;
  height: 3px;
  border-radius: 3px 3px 0 0;
  background: var(--db-accent);
  transform: scaleX(0);
  transition: transform 0.22s cubic-bezier(0.2, 0.7, 0.2, 1);
}

.learner-nav__item:hover,
.learner-nav__item:focus-visible {
  color: var(--db-accent);
}

.learner-nav__item:hover::after,
.learner-nav__item:focus-visible::after,
.learner-nav__item.active::after {
  transform: scaleX(1);
}

.learner-nav__item:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: -6px;
  border-radius: 6px;
}

.learner-nav__item.active {
  font-weight: 700;
  color: var(--db-accent);
}

@media (prefers-reduced-motion: reduce) {
  .learner-nav__item,
  .learner-nav__item::after {
    transition: none;
  }
}

.learner-user :deep(.btn-sm) {
  min-height: 40px;
  min-width: 40px;
}

.learner-search-band {
  background: var(--db-bg-white);
  border-bottom: 1px solid var(--db-border);
}

/* 내용을 너무 밀지 않게 낮게 — 검색칸 56px + 위아래 16 */
.learner-search-band__inner {
  display: flex;
  justify-content: center;
  padding-top: 16px;
  padding-bottom: 16px;
}

.learner-search-band__inner .learner-search-band__box {
  --portal-search-height: 56px;
}

@media (max-width: 599px) {
  .learner-search-band__inner {
    padding-top: 12px;
    padding-bottom: 12px;
  }

  .learner-search-band__inner .learner-search-band__box {
    --portal-search-height: 52px;
  }
}

.learner-user__divider {
  width: 1px;
  height: 20px;
  margin: 0 4px;
  background: var(--db-border);
}

.learner-full-menu-btn {
  min-height: 40px;
  padding: 0 8px;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
}

/* 머리 아래 본문을 덮는다(머리·패널은 q-header 안이라 위에 남는다) */
.learner-mega-backdrop {
  position: fixed;
  top: 68px;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: -1;
  background: rgba(0, 0, 0, 0.32);
}

@media (prefers-reduced-motion: no-preference) {
  .learner-mega-backdrop {
    animation: learner-fade-in 160ms ease-out;
  }
}

@keyframes learner-fade-in {
  from {
    opacity: 0;
  }
}

.learner-console-btn {
  height: 40px;
  border-radius: 6px;
  white-space: nowrap;
}

.learner-user {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-width: 160px;
  padding: 6px 10px;
}

/* 중장년 이용자 — 머리의 글자 링크·버튼도 누르는 자리 40px */
.learner-drawer__auth {
  display: grid;
  padding: 16px;
  border-bottom: 1px solid var(--db-border);
}

.learner-user__trigger {
  display: flex;
  align-items: center;
  gap: 8px;
}

.learner-user__name {
  /* flex 면 말줄임(text-overflow)이 걸리지 않는다 */
  display: block;
  height: 27px;
  line-height: 19px;
  padding: 4px 12px;
  border-radius: 999px;
  background: var(--db-bg-tag);
  font-size: 13px;
  font-weight: 500;
  color: var(--db-text-body);
  /* 머리 줄이 좁으면(콘솔 전환 버튼이 있는 계정) 이름이 두 줄로 꺾였다 — 한 줄로 두고 길면 말줄임 */
  flex-shrink: 0;
  max-width: 120px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.learner-footer {
  background: var(--db-primary);
  color: var(--db-on-color);
}

/* 탭 58px + 테두리 1px + 홈 인디케이터 영역 — .learner-mobile-tabs 높이와 맞춘다 */
.learner-footer--tabs {
  padding-bottom: calc(59px + env(safe-area-inset-bottom));
}

/* 헤더(z 2000)와 같은 층 — 본문 위, 드로어·다이얼로그 아래 */
.learner-mobile-tabs {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 2000;
  padding-bottom: env(safe-area-inset-bottom);
  background: var(--db-bg-white);
  color: var(--db-text-body);
  border-top: 1px solid var(--db-border);
}

.learner-mobile-tabs :deep(.q-tab) {
  min-height: 58px;
}

/* 폰(375): 로고 160 고정 + 아이콘들 + 이름 칩이 헤더 폭을 넘었다 — 이름은 아바타로 갈음하고 로고 폭을 풀어 준다 */
@media (max-width: 599px) {
  .learner-brand {
    width: auto;
  }

  .learner-user {
    min-width: 0;
  }

  .learner-user__name {
    display: none;
  }
}
</style>
