<template>
  <q-page class="notices">
    <div class="learner-container notices__body">
      <header class="notices__head">
        <h1 class="notices__title">공지사항</h1>
        <span v-if="!errorMessage && total" class="notices__total">{{ formatCount(total) }}건</span>
      </header>

      <AppErrorBanner v-if="errorMessage" :message="errorMessage" />

      <section v-else class="notices__board" :class="{ 'notices__board--empty': !items.length }" :aria-busy="loading">
        <AppEmptyState v-if="!loading && !items.length" description="등록된 공지사항이 없습니다." />
        <ol v-else class="notices__list">
          <li v-for="item in items" :key="item.id">
            <router-link :to="detailLink(item)" class="notice-row">
              <span class="notice-row__title">
                <!-- 최근 7일 안에 올라온 공지 -->
                <span v-if="isNew(item)" class="notice-row__new" aria-label="새 공지">N</span>{{ item.title }}
              </span>
              <!-- 작성자는 뺐다 — 공개 공지에 직원 개인 이름('강사1')이 찍혔다. 운영 이름은 상세에 -->
              <span class="notice-row__meta">
                <time :datetime="item.publishedAt || ''">{{ formatDateKst(item.publishedAt, '-') }}</time>
              </span>
              <q-icon name="chevron_right" size="20px" class="notice-row__go" aria-hidden="true" />
            </router-link>
          </li>
        </ol>

        <div v-if="pageCount > 1" class="notices__pager">
          <q-pagination
            :model-value="page"
            :max="pageCount"
            :max-pages="5"
            direction-links
            boundary-numbers
            color="primary"
            active-design="unelevated"
            @update:model-value="goPage"
          />
        </div>
        <q-inner-loading :showing="loading" />
      </section>
    </div>
  </q-page>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import { listNotices } from '@/services/notice-api';
import { daysFromToday, formatDateKst } from '@/utils/datetime';

const PAGE_SIZE = 15;
// 이 날 수 안에 올라온 공지에 'N'
const NEW_DAYS = 7;
const numberFormat = new Intl.NumberFormat('ko-KR');

/* 학습자 공지 목록(공개) — /notices. 게시판형: 제목 한 줄 + 작성자·게시일. 쪽 번호는 ?page= 로 둬서 상세에서 돌아와도 같은 쪽 */
export default {
  name: 'NoticesPage',
  components: { AppEmptyState, AppErrorBanner },
  data() {
    return { items: [], total: 0, loading: false, errorMessage: '' };
  },
  computed: {
    page() {
      const value = Number.parseInt(this.$route.query.page, 10);
      return Number.isInteger(value) && value > 0 ? value : 1;
    },
    pageCount() {
      return Math.max(1, Math.ceil(this.total / PAGE_SIZE));
    },
  },
  watch: {
    page: { immediate: true, handler: 'load' },
  },
  methods: {
    formatDateKst,
    formatCount(value) {
      return numberFormat.format(value || 0);
    },
    isNew(item) {
      const days = daysFromToday(item.publishedAt);
      return days !== null && days <= 0 && days > -NEW_DAYS;
    },
    detailLink(item) {
      return { path: `/notices/${item.id}`, query: this.page > 1 ? { page: this.page } : {} };
    },
    goPage(value) {
      this.$router.push({ query: value > 1 ? { page: value } : {} });
      window.scrollTo({ top: 0 });
    },
    async load() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await listNotices({ page: this.page - 1, size: PAGE_SIZE, audience: 'PUBLIC' });
        this.items = Array.isArray(result.data) ? result.data : [];
        this.total = result.page?.totalElements ?? this.items.length;
      } catch (error) {
        this.items = [];
        this.total = 0;
        this.errorMessage = error?.message || '공지사항을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
.notices {
  padding: 32px 0 48px;
  background: var(--db-bg-page);
}

.notices__body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  /* .learner-container 의 max-width: 100% 를 덮지 않는다 — 폰에서 가로로 넘친다 */
  max-width: min(880px, 100%);
}

.notices__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.notices__title {
  margin: 0;
  font-size: 28px;
  line-height: 36px;
  font-weight: 700;
  letter-spacing: normal;
  color: var(--db-text-title);
}

.notices__total {
  font-size: 15px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.notices__board {
  position: relative;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-top: 2px solid var(--db-text-title);
  border-radius: 0 0 8px 8px;
}

.notices__board--empty {
  min-height: 160px;
}

.notices__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.notices__list li + li {
  border-top: 1px solid var(--db-border);
}

.notice-row {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 18px 24px;
  color: inherit;
  text-decoration: none;
  transition: background-color 0.12s ease-out;
}

.notice-row:hover {
  background: var(--db-bg-subtle);
}

.notice-row:active {
  background: var(--db-bg-section);
}

.notice-row:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: -2px;
}

.notice-row__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 16px;
  font-weight: 500;
  color: var(--db-text-title);
}

.notice-row:hover .notice-row__title {
  color: var(--db-accent);
}

.notice-row__meta {
  display: flex;
  flex-shrink: 0;
  gap: 16px;
  font-size: 14px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.notice-row__new {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  margin-right: 6px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 800;
  vertical-align: 2px;
  color: var(--db-on-color);
  background: var(--db-status-pink);
}

/* 누를 수 있는 줄이라는 표시 */
.notice-row__go {
  flex-shrink: 0;
  color: var(--db-text-sub);
}

.notices__pager {
  display: flex;
  justify-content: center;
  padding: 16px;
  border-top: 1px solid var(--db-border);
}

@media (prefers-reduced-motion: reduce) {
  .notice-row {
    transition: none;
  }
}

@media (max-width: 599px) {
  .notices {
    padding-top: 20px;
  }

  .notices__title {
    font-size: 22px;
    line-height: 30px;
  }

  .notice-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
    padding: 14px 16px;
  }

  .notice-row__title {
    width: 100%;
    white-space: normal;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }

  .notice-row__meta {
    font-size: 14px;
  }

  .notice-row__go {
    display: none;
  }
}
</style>
