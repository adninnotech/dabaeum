<template>
  <q-page class="popular-page">
    <div class="learner-container">
      <header class="popular-head">
        <h1 class="popular-head__title">인기 강좌</h1>
        <p class="popular-head__sub">최근 30일 신청과 조회를 합쳐 매시간 다시 계산합니다.</p>
      </header>

      <LearnerTabs :tabs="tabLabels" :model-value="activeLabel" @update:model-value="selectTab" />

      <section class="popular-main">
        <div class="popular-bar">
          <p v-if="hiddenTabNote" class="popular-note">
            {{ hiddenTabNote.text }}
            <!-- 안내만 있고 갈 곳이 없었다 -->
            <router-link :to="hiddenTabNote.to" class="popular-note__link">{{ hiddenTabNote.link }}</router-link>
          </p>
          <!-- 순위는 그대로 두고(번호도 원래 순위), 지금 신청할 수 있는 강좌만 골라 본다 -->
          <q-toggle v-model="openOnly" dense label="신청할 수 있는 강좌만" class="popular-bar__toggle" />
        </div>

        <AppErrorBanner v-if="errorMessage" :message="errorMessage" />
        <AppEmptyState
          v-else-if="!loading && !items.length"
          title="인기 강좌를 집계하고 있습니다"
          description="매시 5분에 새로 계산합니다. 잠시 후 다시 확인해 주세요."
        />
        <AppEmptyState
          v-else-if="!loading && !visibleItems.length"
          compact
          description="지금 신청할 수 있는 인기 강좌가 없습니다."
        >
          <template #action>
            <q-btn outline no-caps color="primary" label="모든 순위 보기" @click="openOnly = false" />
          </template>
        </AppEmptyState>
        <ol v-else class="popular-grid">
          <li v-for="item in visibleItems" :key="item.course.id" class="popular-item">
            <!-- 폰은 작은 썸네일 가로 행(fluid) — 큰 세로 카드는 한 화면에 하나 남짓이었다 -->
            <CourseCard
              :course="item.course"
              :interested="isInterested(item.course.id)"
              :rank="item.rank"
              fluid
              @toggle-interest="toggleInterestFor"
            >
              <template #footer>
                최근 30일 신청 {{ formatCount(item.popularity.enrollmentCount) }}명 · 조회
                {{ formatCount(item.popularity.viewCount) }}
              </template>
            </CourseCard>
          </li>
        </ol>
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
import CourseCard from '@/components/course/CourseCard.vue';
import LearnerTabs from '@/components/LearnerTabs.vue';
import courseInterest from '@/mixins/course-interest';
import { listPopularCourses } from '@/services/course-api';
import { decorateCourseRows } from '@/services/course-rows';
import { isRecruitOpen } from '@/utils/course-fields';
import { useAuthStore } from '@/stores/auth-store';
import { ageBandFromBirthDate } from '@/utils/datetime';

const POPULAR_LIMIT = 12;
const AGE_BAND_LABEL = {
  10: '10대 이하',
  20: '20대',
  30: '30대',
  40: '40대',
  50: '50대',
  60: '60대',
  70: '70대 이상',
};

/* 인기 강좌(설계서 §8) — 전체 / 내 또래. 내 또래는 로그인한 사용자의 생년월일로 고른다(우리 지역 탭은 #142 회원 구·군과 함께 뺐다).
 * 피그마에 없어 강좌 찾기와 같은 탭·카드 그리드를 쓴다 */
export default {
  name: 'PopularCoursesPage',
  components: { AppEmptyState, AppErrorBanner, CourseCard, LearnerTabs },
  mixins: [courseInterest],
  data() {
    return {
      activeKey: 'all',
      items: [],
      loading: false,
      errorMessage: '',
      openOnly: false,
    };
  },
  computed: {
    ...mapState(useAuthStore, ['isLoggedIn', 'user', 'isDemoSession']),
    realUser() {
      return this.isLoggedIn && !this.isDemoSession ? this.user : null;
    },
    ageBand() {
      return this.realUser ? ageBandFromBirthDate(this.realUser.birthDate) : null;
    },
    tabs() {
      const tabs = [{ key: 'all', label: '전체', query: {} }];
      if (this.ageBand) {
        tabs.push({
          key: 'age',
          label: `내 또래 · ${AGE_BAND_LABEL[this.ageBand]}`,
          query: { ageBand: this.ageBand },
        });
      }
      return tabs;
    },
    tabLabels() {
      return this.tabs.map((tab) => tab.label);
    },
    activeTab() {
      return this.tabs.find((tab) => tab.key === this.activeKey) || this.tabs[0];
    },
    activeLabel() {
      return this.activeTab.label;
    },
    // 로그인했는데 탭이 빠진 이유와 갈 곳 — 비로그인은 전체만 보는 게 정상이다
    hiddenTabNote() {
      if (!this.realUser) {
        return {
          text: '로그인하면 내 또래 인기 강좌도 볼 수 있습니다.',
          link: '로그인',
          to: { path: '/login', query: { redirect: '/courses/popular' } },
        };
      }
      if (!this.ageBand) {
        return { text: '생년월일을 등록하면 내 또래 인기 강좌도 볼 수 있습니다.', link: '내 정보에서 등록', to: '/mypage' };
      }
      return null;
    },
    // 순위는 받은 순서 그대로 매기고, 거를 때도 원래 순위를 보인다
    rankedItems() {
      return this.items.map((item, index) => ({ ...item, rank: index + 1 }));
    },
    visibleItems() {
      // 마감일이 지났는데 서버 상태가 RECRUITING 인 강좌는 신청할 수 없다 — 날짜까지 본다
      return this.openOnly ? this.rankedItems.filter((item) => item.course && isRecruitOpen(item.course)) : this.rankedItems;
    },
  },
  watch: {
    activeKey() {
      this.load();
    },
  },
  created() {
    this.load();
    // 하트가 서버의 실제 관심 상태를 보여 주게 한다 (비로그인이면 빈 상태)
    this.loadMyInterests();
  },
  methods: {
    formatCount(value) {
      return Number(value || 0).toLocaleString('ko-KR');
    },
    selectTab(label) {
      const tab = this.tabs.find((item) => item.label === label);
      if (tab) this.activeKey = tab.key;
    },
    async load() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const list = await listPopularCourses({ ...this.activeTab.query, limit: POPULAR_LIMIT });
        const courses = await decorateCourseRows(list.map((item) => item.course));
        this.items = list.map((item, index) => ({ ...item, course: courses[index] }));
      } catch (error) {
        this.items = [];
        this.errorMessage = error?.message || '인기 강좌를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
.popular-page {
  position: relative;
  padding: 24px 0;
  background: var(--db-bg-page);
}

.popular-head {
  display: flex;
  flex-direction: column;
  gap: 6px;
  /* 왼쪽 선을 아래 탭·흰 박스 테두리에 맞춘다 */
  padding: 12px 0;
}

.popular-head__title {
  margin: 0;
  font-size: 24px;
  line-height: 32px;
  font-weight: 700;
  color: var(--db-text-body);
}

.popular-head__sub {
  margin: 0;
  font-size: 14px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.popular-main {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-top: 0;
  border-radius: 0 0 8px 8px;
}

.popular-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px 16px;
}

.popular-note {
  margin: 0;
  font-size: 14px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.popular-note__link {
  margin-left: 4px;
  white-space: nowrap;
  font-weight: 600;
  color: var(--db-accent);
  text-decoration: none;
}

.popular-note__link:hover,
.popular-note__link:focus-visible {
  text-decoration: underline;
}

.popular-bar__toggle {
  margin-left: auto;
  font-size: 14px;
  color: var(--db-text-body);
}

.popular-grid {
  display: grid;
  /* 260 고정 칸을 가운데 두면 남는 폭이 양옆 여백이 되어 제목·탭 왼쪽 선과 어긋난다 —
   * 칸이 남는 폭을 나눠 가져 카드 줄이 본문 폭을 꽉 채운다 */
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.popular-item {
  display: flex;
  min-width: 0;
}

/* 같은 줄의 카드 높이를 맞춘다 — 인기 근거 줄이 카드 맨 아래에 붙는다 */
.popular-item > :deep(.course-card) {
  flex: 1 1 auto;
}

@media (max-width: 599px) {
  .popular-main {
    padding: 12px 6px;
  }

  .popular-grid {
    grid-template-columns: minmax(0, 1fr);
    padding: 0 6px;
  }
}
</style>
