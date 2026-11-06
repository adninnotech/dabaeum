<template>
  <q-page class="home">
    <!-- ⓪ 포털형 통합검색 — 홈의 첫 동작. 제출하면 /search 로 넘긴다 -->
    <section class="home-search" aria-label="통합검색">
      <div class="learner-container home-search__inner">
        <PortalSearchBox v-model="searchText" large @submit="submitSearch" />
      </div>
    </section>

    <!-- ① 배너 — /banners 목록. 0건이면 기존 폴백 1장을 유지한다(빈 화면보다 낫다).
         슬라이드(배경+글)가 옆으로 밀려 바뀌고, 2장 이상이면 자동으로 넘긴다(마우스·포커스가 있으면 멈춤) -->
    <section
      class="home-banner"
      :class="{ 'home-banner--image': hasBannerImage, 'home-banner--multi': hasMultipleBanners }"
      aria-roledescription="carousel"
      aria-label="배너"
      @mouseenter="pauseBannerAutoplay"
      @mouseleave="resumeBannerAutoplay"
      @focusin="pauseBannerAutoplay"
      @focusout="resumeBannerAutoplay"
    >
      <transition :name="`home-banner-slide-${bannerDirection}`">
        <div
          :key="bannerSlideKey"
          class="home-banner__slide"
          :class="{ 'home-banner__slide--image': hasBannerImage }"
          :style="bannerImageStyle"
        >
          <div class="learner-container home-banner__slide-inner">
            <div class="home-banner__contents">
              <template v-if="currentBanner">
                <AppTag v-if="currentBanner.badgeText">{{ currentBanner.badgeText }}</AppTag>
                <h1 class="home-banner__title">{{ currentBanner.title }}</h1>
                <p v-if="currentBanner.subtitle" class="home-banner__desc">
                  {{ currentBanner.subtitle }}
                </p>
                <q-btn
                  v-if="currentBanner.buttonText"
                  unelevated
                  no-caps
                  color="primary"
                  class="btn-md"
                  :label="currentBanner.buttonText"
                  @click="openBanner(currentBanner)"
                />
              </template>
              <template v-else>
                <AppTag>다배움 평생학습</AppTag>
                <h1 class="home-banner__title">배우고 싶은 순간, 가까운 곳에서</h1>
                <p class="home-banner__desc">
                  지역 기관이 여는 평생학습 강좌를 한곳에서 찾고 신청하세요.
                </p>
                <q-btn
                  unelevated
                  no-caps
                  color="primary"
                  class="btn-md"
                  label="자세히 보기"
                  @click="$router.push('/courses')"
                />
              </template>
            </div>
          </div>
        </div>
      </transition>
      <!-- 높이를 잡는 틀이라 늘 그린다. 넘길 배너가 없으면 화살표·"1 / 1"은 쓸모가 없어 숨긴다 -->
      <div class="learner-container home-banner__inner">
        <template v-if="hasMultipleBanners">
          <q-btn aria-label="이전 배너"
            flat
            round
            icon="chevron_left"
            size="lg"
            class="home-banner__arrow"
            @click="prevBanner"
          />
          <q-btn aria-label="다음 배너"
            flat
            round
            icon="chevron_right"
            size="lg"
            class="home-banner__arrow"
            @click="nextBanner"
          />
        </template>
      </div>
      <div v-if="hasMultipleBanners" class="home-banner__indicator">
        <span>{{ bannerIndicatorLabel }}</span>
        <!-- 자동으로 바뀌는 콘텐츠는 사용자가 멈출 수 있어야 한다 (WCAG 2.2.2) -->
        <q-btn
          flat
          round
          dense
          size="sm"
          class="home-banner__toggle"
          :icon="bannerUserPaused ? 'play_arrow' : 'pause'"
          :aria-label="bannerUserPaused ? '배너 자동 넘김 재생' : '배너 자동 넘김 일시정지'"
          :aria-pressed="bannerUserPaused ? 'true' : 'false'"
          @click="toggleBannerAutoplay"
        />
      </div>
    </section>

    <div class="learner-container home-body">
      <AppErrorBanner v-if="errorMessage" :message="errorMessage" />

      <!-- ①-1 바로가기 — 자주 가는 6곳 -->
      <HomeQuickLinks />

      <!-- ② 인기 강좌 -->
      <section class="home-block">
        <header class="home-block__head">
          <div>
            <h2 class="home-block__title">인기 강좌</h2>
          </div>
          <q-btn flat no-caps class="home-block__more" label="더보기 >" to="/courses/popular" />
        </header>
        <AppEmptyState
          v-if="!loading && !popularTop.length"
          inline
          description="등록된 강좌가 없습니다."
        />
        <PopularCourseCarousel
          v-else
          :courses="popularTop"
          :is-interested="isInterested"
          @toggle-interest="toggleInterestFor"
        />
      </section>

      <!-- ②-1 추천 강좌 — 인기 섹션과 겹치지 않는 강좌. 비어도 제목과 안내는 보인다(불러오기 전엔 자리를 비운다) -->
      <section v-if="recommendHeading" class="home-block" aria-labelledby="home-recommend-title">
        <header class="home-block__head">
          <div>
            <h2 id="home-recommend-title" class="home-block__title">
              {{ recommendHeading.title }}
            </h2>
          </div>
        </header>
        <AppEmptyState
          v-if="!recommendItems.length"
          inline
          :description="recommendHeading.empty"
          :action-label="isLoggedIn ? '강좌 둘러보기' : '로그인'"
          :action-to="isLoggedIn ? '/courses' : '/login'"
        />
        <RecommendedCourseGrid
          v-else
          :items="recommendItems"
          :is-interested="isInterested"
          @toggle-interest="toggleInterestFor"
        />
      </section>

      <!-- ③ 모집 중인 강좌 -->
      <section class="home-block">
        <header class="home-block__head">
          <div>
            <h2 class="home-block__title">모집 중인 강좌</h2>
          </div>
          <q-btn flat no-caps class="home-block__more" label="더보기 >" @click="goSearch" />
        </header>
        <AppEmptyState
          v-if="!loading && !recruiting.length"
          inline
          description="지금 모집 중인 강좌가 없어요. 곧 열릴 강좌와 다른 기관 강좌를 둘러보세요."
          action-label="강좌 둘러보기"
          action-to="/courses"
        />
        <div v-else class="home-list">
          <!-- 행 전체를 누르는 건 제목 링크를 행 크기로 늘려서 — 예전 article@click 은 Tab 으로 갈 수 없었다.
               하트는 링크 위에 떠 있어 따로 눌린다(버튼을 링크 안에 넣지 않는다) -->
          <article v-for="course in recruiting" :key="course.id" class="home-row">
            <img
              v-if="course.thumbnailUrl"
              :src="course.thumbnailUrl"
              alt=""
              class="home-row__thumb"
            />
            <div v-else class="home-row__thumb home-row__thumb--empty" />

            <div class="home-row__body">
              <h3 class="home-row__title">
                <router-link :to="`/courses/${course.id}`" class="home-row__link">{{ course.title }}</router-link>
              </h3>
              <div class="home-row__institution">{{ course.institutionName || '-' }}</div>
              <!-- 태그는 개강·인원 줄 끝에 — 따로 한 줄을 쓰면 120px 칸을 넘어 이 줄이 아래로 밀려 잘렸다 -->
              <div class="home-row__meta">
                <span>{{ startLabel(course) }}</span>
                <i class="home-row__sep" />
                <span>{{ course.enrolledCount ?? '-' }} / {{ course.capacity ?? '-' }} 명</span>
                <span v-if="tagsOf(course).length" class="home-row__tags">
                  <AppTag v-for="tag in tagsOf(course)" :key="tag">{{ tag }}</AppTag>
                </span>
              </div>
            </div>

            <div class="home-row__side">
              <StatusBadge v-if="course.educationType" :status="course.educationType" />
              <q-btn :aria-label="isInterested(course.id) ? '관심 강좌 해제' : '관심 강좌 등록'"
                flat
                round
                dense
                :icon="isInterested(course.id) ? 'favorite' : 'favorite_border'"
                :color="isInterested(course.id) ? 'pink-5' : 'grey-7'"
                @click.stop="toggleInterestFor(course.id)"
              />
            </div>
          </article>
        </div>
      </section>

      <!-- ③-2 대구 다른 기관 강좌(공공데이터) — 불러온 게 없으면 블록째 숨긴다 -->
      <section v-show="publicCourseCount !== 0" class="home-block">
        <header class="home-block__head">
          <div>
            <h2 class="home-block__title">대구 다른 기관 강좌</h2>
          </div>
          <q-btn flat no-caps class="home-block__more" label="더보기 >" to="/courses?scope=public" />
        </header>
        <PublicCourseHomeList @update:count="publicCourseCount = $event" />
      </section>

      <!-- ④ 공지 · FAQ -->
      <section class="home-cards">
        <div class="home-card">
          <div class="home-card__inner">
            <header class="home-card__head">
              <h2 class="home-card__title">공지사항</h2>
              <q-btn
                flat
                no-caps
                class="home-block__more"
                label="전체 보기 >"
                to="/notices"
              />
            </header>
            <AppEmptyState
              v-if="!notices.length"
              compact
              description="등록된 공지사항이 없습니다."
            />
            <router-link
              v-for="notice in notices"
              :key="notice.id"
              :to="`/notices/${notice.id}`"
              class="home-notice"
            >
              <span class="home-notice__title">{{ notice.title }}</span>
              <span class="home-notice__date">{{
                formatDateKst(notice.publishedAt || notice.createdAt, '-')
              }}</span>
            </router-link>
          </div>
        </div>

        <div class="home-card">
          <div class="home-card__inner">
            <header class="home-card__head">
              <h2 class="home-card__title">자주 묻는 질문</h2>
              <q-btn flat no-caps class="home-block__more" label="전체 보기 >" to="/support" />
            </header>
            <AppEmptyState v-if="!faqs.length" compact description="등록된 FAQ가 없습니다." />
            <!-- 서버가 관리자 지정 순서(sortOrder)로 내려준다 — 재정렬하지 않는다 -->
            <FaqAccordion v-else :faqs="faqs" />
          </div>
        </div>
      </section>
    </div>

    <q-inner-loading :showing="loading">
      <q-spinner color="primary" size="32px" />
    </q-inner-loading>
  </q-page>
</template>

<script>
import { mapState } from 'pinia';
import AppEmptyState from '@/components/AppEmptyState.vue';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import AppTag from '@/components/AppTag.vue';
import FaqAccordion from '@/components/FaqAccordion.vue';
import HomeQuickLinks from '@/components/home/HomeQuickLinks.vue';
import PopularCourseCarousel from '@/components/home/PopularCourseCarousel.vue';
import RecommendedCourseGrid from '@/components/home/RecommendedCourseGrid.vue';
import PortalSearchBox from '@/components/PortalSearchBox.vue';
import PublicCourseHomeList from '@/components/public-course/PublicCourseHomeList.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { listPublicBanners } from '@/services/banner-api';
import { listCourses, listPopularCourses } from '@/services/course-api';
import { decorateCourseRows } from '@/services/course-rows';
import { storedFileContentUrl } from '@/services/file-api';
import { isLearnerVisibleCourse, isRecruitOpen } from '@/utils/course-fields';
import { listMyInterests } from '@/services/learner-api';
import courseInterest from '@/mixins/course-interest';
import homeRecommendation from '@/mixins/home-recommendation';
import { listNotices } from '@/services/notice-api';
import { SEARCH_MIN_LENGTH } from '@/services/search-api';
import { listFaqs } from '@/services/support-api';
import { recordBannerClick } from '@/services/usage-api';
import { useAuthStore } from '@/stores/auth-store';
import { formatDateKst } from '@/utils/datetime';

// 큰 카드 4개를 보이고 5초마다 한 칸씩 넘긴다 — 넘길 거리가 있게 8개
const POPULAR_TOP_COUNT = 8;
// 숨김 강좌를 걸러도 8개가 남도록 조금 넉넉히 받는다
const POPULAR_FETCH_LIMIT = 16;
// 배너 자동 넘김 간격 — 글을 읽을 시간은 주고, 한 화면에 오래 머물지 않게
const BANNER_AUTOPLAY_MS = 5000;
// 홈에 보이는 FAQ 수
const HOME_FAQ_COUNT = 5;

export default {
  name: 'IndexPage',
  components: {
    AppEmptyState,
    AppErrorBanner,
    AppTag,
    FaqAccordion,
    HomeQuickLinks,
    PopularCourseCarousel,
    PortalSearchBox,
    RecommendedCourseGrid,
    PublicCourseHomeList,
    StatusBadge,
  },
  mixins: [courseInterest, homeRecommendation],
  data() {
    return {
      loading: false,
      errorMessage: '',
      courses: [],
      searchText: '',
      popularCourses: [],
      notices: [],
      banners: [],
      bannerIndex: 0,
      // 슬라이드가 밀려나는 방향 — 다음은 왼쪽으로, 이전은 오른쪽으로
      bannerDirection: 'next',
      bannerTimer: null,
      bannerPaused: false,
      // 사용자가 일시정지 버튼으로 멈춘 상태 — 마우스·포커스로 잠깐 멈춘 bannerPaused 와 달리 다시 누를 때까지 유지
      bannerUserPaused: false,
      faqs: [],
      // 대구 다른 기관 강좌 수 — null 은 아직 불러오는 중(자리를 보여 둔다), 0이면 블록을 숨긴다
      publicCourseCount: null,
    };
  },
  computed: {
    ...mapState(useAuthStore, ['isLoggedIn']),
    popularTop() {
      // 인기 집계(최근 30일)가 있으면 그 순서, 첫 집계 전이거나 실패하면 목록 앞쪽으로 채운다.
      // 비공개·취소 강좌는 올리지 않는다 (서버가 DRAFT 를 내려주는 문제 방어)
      const popular = this.popularCourses.filter(isLearnerVisibleCourse);
      const source = popular.length ? popular : this.courses.filter(isLearnerVisibleCourse);
      return source.slice(0, POPULAR_TOP_COUNT);
    },
    recruiting() {
      // 상태만 보면 마감일이 지난 강좌(서버 상태 전환이 늦다)가 '모집 중'에 섞였다
      return this.courses.filter(isRecruitOpen).slice(0, 5);
    },
    currentBanner() {
      return this.banners[this.bannerIndex] || null;
    },
    bannerSlideKey() {
      return this.currentBanner?.id ?? `banner-${this.bannerIndex}`;
    },
    hasMultipleBanners() {
      return this.banners.length > 1;
    },
    bannerIndicatorLabel() {
      return `${this.bannerIndex + 1} / ${this.banners.length}`;
    },
    bannerImageStyle() {
      const banner = this.currentBanner;
      const pcId = banner?.pcImageFileId;
      if (!pcId) return {};
      const pc = storedFileContentUrl(pcId);
      // 모바일 이미지가 없으면 폰·태블릿은 배경 없이 글만 — PC 이미지(약 6.7:1)를 세로로 긴 폰 배너에 cover 로 깔면
      // 가운데 일부만 크게 잘려 깨져 보였다(QA 제보)
      const mobile = banner.mobileImageFileId ? `url(${storedFileContentUrl(banner.mobileImageFileId)})` : 'none';
      return { '--home-banner-bg-pc': `url(${pc})`, '--home-banner-bg-mobile': mobile };
    },
    hasBannerImage() {
      if (!this.bannerImageStyle['--home-banner-bg-pc']) return false;
      // 폰·태블릿(1023px 이하)에 모바일 이미지가 없으면 그림 배너가 아니다 — 그림 배너용 판(여백 24·32px)이 붙으면
      // 360px 에서 글 폭이 96px 로 줄어 판이 배너보다 길어지고 버튼이 잘렸다(QA #49 재확인)
      return !(this.$q.screen.lt.md && this.bannerImageStyle['--home-banner-bg-mobile'] === 'none');
    },
  },
  created() {
    this.load();
  },
  beforeUnmount() {
    this.stopBannerAutoplay();
  },
  methods: {
    formatDateKst,
    submitSearch() {
      const q = this.searchText.trim();
      if (q.length < SEARCH_MIN_LENGTH) {
        this.$q.notify({ type: 'warning', message: `검색어를 ${SEARCH_MIN_LENGTH}자 이상 입력해 주세요.`, position: 'top' });
        return;
      }
      this.$router.push({ path: '/search', query: { q } });
    },
    goSearch() {
      this.$router.push('/courses');
    },
    tagsOf(course) {
      return String(course.detail?.tags || '')
        .split(/[\s,]+/)
        .filter(Boolean)
        .map((tag) => (tag.startsWith('#') ? tag : `#${tag}`))
        .slice(0, 3);
    },
    startLabel(course) {
      const date = String(course.startDate || '').slice(0, 10);
      if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return '개강일 미정';
      return `${date.slice(5, 7)}.${date.slice(8, 10)} 개강`;
    },
    showBanner(step) {
      if (this.banners.length < 2) return;
      this.bannerDirection = step > 0 ? 'next' : 'prev';
      this.bannerIndex = (this.bannerIndex + step + this.banners.length) % this.banners.length;
    },
    // 손으로 넘기면 타이머를 처음부터 — 넘기자마자 또 넘어가지 않게
    prevBanner() {
      this.showBanner(-1);
      this.startBannerAutoplay();
    },
    nextBanner() {
      this.showBanner(1);
      this.startBannerAutoplay();
    },
    startBannerAutoplay() {
      this.stopBannerAutoplay();
      if (this.banners.length < 2 || this.bannerPaused || this.bannerUserPaused) return;
      this.bannerTimer = window.setInterval(() => this.showBanner(1), BANNER_AUTOPLAY_MS);
    },
    stopBannerAutoplay() {
      if (this.bannerTimer) window.clearInterval(this.bannerTimer);
      this.bannerTimer = null;
    },
    toggleBannerAutoplay() {
      this.bannerUserPaused = !this.bannerUserPaused;
      if (this.bannerUserPaused) {
        this.stopBannerAutoplay();
        return;
      }
      // 재생을 누른 순간은 버튼에 포커스·마우스가 있어 잠깐 멈춤 상태다 — 명시적 재생이 이긴다
      this.bannerPaused = false;
      this.startBannerAutoplay();
    },
    pauseBannerAutoplay() {
      this.bannerPaused = true;
      this.stopBannerAutoplay();
    },
    resumeBannerAutoplay(event) {
      // 포커스가 배너 안에서 옮겨 다닐 때는 계속 멈춰 둔다
      if (event?.relatedTarget && event.currentTarget?.contains(event.relatedTarget)) return;
      this.bannerPaused = false;
      this.startBannerAutoplay();
    },
    // 이용 통계 배너 클릭 — 기록은 keepalive fetch 로 보내고 이동은 막지 않는다
    openBanner(banner) {
      recordBannerClick(banner.id);
      this.goBannerLink(banner.buttonLink);
    },
    goBannerLink(link) {
      if (!link) return;
      if (/^https?:\/\//i.test(link)) {
        window.location.href = link;
        return;
      }
      this.$router.push(link);
    },
    async load() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const [coursePage, popularList, noticePage, bannerList, faqPage, interestPage] = await Promise.all([
          listCourses({ page: 0, size: 100 }),
          listPopularCourses({ limit: POPULAR_FETCH_LIMIT }).catch(() => []),
          // audience 를 빼면 강사·기관 공지까지 온다 — 학습자 홈은 공개 공지만
          listNotices({ page: 0, size: 5, audience: 'PUBLIC' }).catch(() => null),
          listPublicBanners().catch(() => []),
          // 홈은 맛보기만 — 나머지는 [전체 보기](고객센터). 예전엔 50개를 받아 320px 칸 안에서 스크롤시켰다
          listFaqs({ page: 0, size: HOME_FAQ_COUNT }).catch(() => null),
          // 비로그인 상태에서 부르면 401이 난다
          this.isLoggedIn
            ? listMyInterests({ page: 0, size: 100 }).catch(() => null)
            : Promise.resolve(null),
        ]);
        const [courses, popularCourses] = await Promise.all([
          decorateCourseRows(Array.isArray(coursePage?.data) ? coursePage.data : []),
          decorateCourseRows(popularList.map((item) => item.course).filter(Boolean)),
        ]);
        this.courses = courses;
        this.popularCourses = popularCourses;
        this.notices = Array.isArray(noticePage?.data) ? noticePage.data : [];
        this.banners = Array.isArray(bannerList) ? bannerList : [];
        this.bannerIndex = 0;
        this.startBannerAutoplay();
        this.faqs = Array.isArray(faqPage?.data) ? faqPage.data : [];
        this.applyInterestRows(interestPage?.data);
        // 인기 섹션이 정해진 뒤 — 같은 강좌가 두 섹션에 나오지 않게 그 id 를 뺀다. 기다리지 않는다
        this.loadRecommendations(this.popularTop.map((course) => course.id));
      } catch (error) {
        this.errorMessage = error?.message || '강좌를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
.home {
  position: relative;
  background: var(--db-bg-white);
}

/* 통합검색 */
.home-search {
  background: var(--db-bg-white);
}

.home-search__inner {
  display: flex;
  justify-content: center;
  padding-top: 40px;
  padding-bottom: 40px;
}

@media (max-width: 599px) {
  .home-search__inner {
    padding-top: 20px;
    padding-bottom: 20px;
  }
}

/* 배너 — 슬라이드(배경+글)는 뒤에 겹쳐 깔리고, 화살표 줄이 그 위에서 높이를 잡는다 */
.home-banner {
  position: relative;
  overflow: hidden;
  background: var(--db-banner-bg);
  --home-banner-arrow: 60px;
  --home-banner-gap: 24px;
  --home-banner-edge: 0px;
}

.home-banner__slide {
  position: absolute;
  inset: 0;
}

.home-banner__slide--image {
  background-image: var(--home-banner-bg-pc);
  background-size: cover;
  background-position: center;
}

@media (max-width: 1023px) {
  .home-banner {
    /* .learner-container 좌우 16 — 슬라이드 글 여백 계산에 더한다 */
    --home-banner-edge: 16px;
  }

  .home-banner__slide--image {
    background-image: var(--home-banner-bg-mobile);
  }
}

/* 글은 화살표 사이에만 — 화살표 아래로 깔리지 않게 좌우를 비운다 */
.home-banner__slide-inner {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding-left: calc(var(--home-banner-edge) + var(--home-banner-arrow) + var(--home-banner-gap));
  padding-right: calc(var(--home-banner-edge) + var(--home-banner-arrow) + var(--home-banner-gap));
}

.home-banner-slide-next-enter-active,
.home-banner-slide-next-leave-active,
.home-banner-slide-prev-enter-active,
.home-banner-slide-prev-leave-active {
  transition: transform 0.5s ease;
}

.home-banner-slide-next-enter-from,
.home-banner-slide-prev-leave-to {
  transform: translateX(100%);
}

.home-banner-slide-next-leave-to,
.home-banner-slide-prev-enter-from {
  transform: translateX(-100%);
}

@media (prefers-reduced-motion: reduce) {
  .home-banner-slide-next-enter-active,
  .home-banner-slide-next-leave-active,
  .home-banner-slide-prev-enter-active,
  .home-banner-slide-prev-leave-active {
    transition: none;
  }
}

/* 화살표 줄은 클릭을 슬라이드(버튼)로 흘려보내고, 화살표만 받는다 */
.home-banner__inner {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 380px;
  pointer-events: none;
}

.home-banner__arrow {
  color: var(--db-text-body);
  pointer-events: auto;
}

.home-banner__toggle {
  color: var(--db-on-color);
}

.home-banner__contents {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  width: 501px;
  max-width: 100%;
  text-align: center;
}

.home-banner__title {
  margin: 0;
  font-size: 32px;
  line-height: 1.3;
  font-weight: 700;
  color: var(--db-text-title);
}

.home-banner__desc {
  margin: 0;
  font-size: 16px;
  font-weight: 400;
  color: var(--db-text-body);
}

/* 그림 배너 — 글 뒤에 옅은 판을 깐다. 그림이 가운데까지 차 있으면 제목이 메달·일러스트 위에 겹쳐 읽기 어려웠다 */
.home-banner--image .home-banner__contents {
  width: auto;
  max-width: min(560px, 100%);
  padding: 24px 32px;
  border-radius: 16px;
  background: var(--db-banner-panel);
  backdrop-filter: blur(6px);
}

/* 관리자가 올린 배너 그림은 화면 모드와 상관없이 같은 색이다 — 글자를 테마 색(다크에선 흰 글자)으로 두면
 * 밝은 그림 위에서 안 보였다. 그림 배너에선 늘 진한 글자(밝은 그림 기준, 라이트 모드에서 보던 그대로) */
.home-banner--image .home-banner__title {
  color: var(--db-banner-ink);
}

.home-banner--image .home-banner__desc,
.home-banner--image .home-banner__arrow {
  color: var(--db-banner-ink-sub);
}

.home-banner__indicator {
  position: absolute;
  z-index: 1;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-width: 68px;
  height: 27px;
  padding: 4px 6px 4px 16px;
  border-radius: 999px;
  background: var(--db-fill-strong);
  color: var(--db-on-color);
  font-size: 13px;
}

/* 본문 */
.home-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  /* 좌우는 .learner-container 몫 — shorthand 로 덮지 않는다 */
  padding-top: 32px;
  padding-bottom: 32px;
}

.home-block__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  /* 제목 한 줄 — 소제목(설명 문장)은 뺐다 */
  min-height: 64px;
  padding: 10px 0;
}

.home-block__title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--db-text-title);
}

/* 폰에서 '더보기 / >' 로 두 줄이 됐다 */
.home-block__more {
  flex-shrink: 0;
  color: var(--db-accent);
  white-space: nowrap;
}

/* 가로형 행 */
.home-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.home-row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 120px;
  padding: 13px 16px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

/* 누를 수 있다는 걸 보이게 — 예전엔 올려도 변화가 없었다 */
.home-row:hover {
  border-color: var(--db-accent);
  box-shadow: 0 2px 8px rgb(0 0 0 / 6%);
}

.home-row:has(.home-row__link:focus-visible) {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.home-row__link {
  color: inherit;
  text-decoration: none;
  outline: none;
}

/* 제목 링크를 행 전체로 늘린다 */
.home-row__link::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 8px;
}

@media (prefers-reduced-motion: reduce) {
  .home-row {
    transition: none;
  }
}

.home-row__thumb {
  width: 160px;
  height: 94px;
  flex-shrink: 0;
  border-radius: 6px;
  object-fit: cover;
}

.home-row__thumb--empty {
  background: var(--db-border-avatar);
}

.home-row__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.home-row__title {
  margin: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 20px;
  font-weight: 700;
  color: var(--db-text-title);
}

.home-row__institution {
  font-size: 14px;
  font-weight: 400;
  color: var(--db-text-body);
}

.home-row__tags {
  display: flex;
  gap: 6px;
  min-width: 0;
  margin-left: 4px;
  overflow: hidden;
}

.home-row__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  height: 22px; /* 태그 칩이 있어도 줄 높이는 같게 — 카드 높이가 들쭉날쭉하지 않도록 */
  font-size: 14px;
  color: var(--db-text-sub);
  white-space: nowrap;
}

.home-row__sep {
  width: 1px;
  height: 10px;
  background: var(--db-border);
}

/* 하트·뱃지는 늘린 링크 위에 — 하트를 눌러도 상세로 넘어가지 않는다 */
.home-row__side {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  flex-shrink: 0;
}

/* 공지·FAQ */
.home-cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  padding: 20px 0;
}

/* 예전엔 320px 고정 + 칸 안 스크롤이라 공지 1건·FAQ 3건이면 절반 넘게 비었다 — 내용 높이에 맞춘다
   (보여 줄 수는 받아 올 때 제한). 두 칸은 그리드라 높은 쪽에 맞춰 같은 높이 */
.home-card {
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.home-card__inner {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 20px 20px;
}

.home-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.home-card__title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--db-text-title);
}

.home-notice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: inherit;
  text-decoration: none;
}

.home-notice:hover .home-notice__title {
  color: var(--db-accent);
}

.home-notice:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
  border-radius: 4px;
}

.home-notice__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 16px;
  font-weight: 400;
  color: var(--db-text-title);
}

.home-notice__date {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--db-text-sub);
}


@media (max-width: 1023px) {
  .home-cards {
    grid-template-columns: minmax(0, 1fr);
  }

  .home-row {
    min-height: 0;
    flex-wrap: wrap;
  }
}

/* 폰: 160 썸네일 + wrap 때문에 제목·기관·날짜가 좁은 칸에서 한두 글자씩 꺾였다.
 * plan.main.mobile.png 처럼 작은 썸네일 왼쪽, 글은 한 줄씩, 뱃지·하트는 오른쪽 */
@media (max-width: 599px) {
  /* 폰: 수업 방식 뱃지(오프라인·온라인·하이브리드)가 작은 썸네일·좁은 줄에 비해 너무 컸다 */
  .home-row__side :deep(.status-badge) {
    height: 20px;
    padding: 0 6px;
    font-size: 13px;
    border-radius: 4px;
  }

  .home-row {
    flex-wrap: nowrap;
    align-items: flex-start;
    gap: 12px;
    padding: 12px;
  }

  /* 제목이 9글자쯤에서 '…'로 잘리고 인원이 '2 / 40 !'로 잘렸다 — 썸네일을 줄여 글 칸을 넓히고 제목은 두 줄까지 */
  .home-row__thumb {
    width: 72px;
    height: 54px;
  }

  .home-row__title {
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    white-space: normal;
    word-break: keep-all;
    overflow-wrap: anywhere;
    font-size: 16px;
    line-height: 22px;
  }

  .home-row__institution {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 14px;
  }

  .home-row__meta {
    flex-wrap: wrap;
    gap: 2px 6px;
    height: auto;
    font-size: 13px;
  }

  /* [더보기 >]가 두 줄로 꺾인 설명 사이에 떠 있었다 — 제목과 한 줄, 설명은 아래 전체 폭 */
  .home-block__head {
    flex-wrap: wrap;
    gap: 0 12px;
    min-height: 0;
  }

  .home-block__head > div {
    display: contents;
  }

  .home-block__title {
    flex: 1 1 auto;
    font-size: 22px;
  }

  .home-block__more {
    order: 2;
  }

  /* 폰 폭에선 개강·인원만 — 태그는 칸 끝에서 반쯤 잘려 보였다 */
  .home-row__tags {
    display: none;
  }
}

/* 폰: 380 고정이라 화면 절반을 차지했고, 1440×1202 모바일 이미지 좌우가 cover 로 잘렸다.
 * 권장 규격 비율로 높이를 맞춰(375 폭 → 약 313) 이미지를 자르지 않는다 */
@media (max-width: 599px) {
  .home-banner {
    --home-banner-gap: 4px;
  }

  .home-banner__inner {
    height: auto;
    aspect-ratio: 1440 / 1202;
  }

  .home-banner__title {
    font-size: 24px;
  }

  .home-banner__desc {
    font-size: 14px;
  }

  .home-banner__indicator {
    bottom: 12px;
  }

  /* 그림 없는 배너는 이미지 비율을 따를 까닭이 없다 — 360 폭에서 300 높이에 글 묶음(267)을 가운데 두니
   * [버튼]이 1/2 표시에 가려졌다(QA #49 재확인 2차). 높이를 늘리고 표시 자리(12 + 27 + 틈 9)를 비운다 */
  .home-banner:not(.home-banner--image) .home-banner__inner {
    height: 360px;
    aspect-ratio: auto;
  }

  .home-banner--multi:not(.home-banner--image) .home-banner__slide-inner {
    padding-bottom: 48px;
  }
}
</style>
