<template>
  <q-page class="notice-detail">
    <div class="learner-container notice-detail__body">
      <router-link :to="listLink" class="notice-detail__back">
        <q-icon name="chevron_left" size="20px" aria-hidden="true" />공지사항
      </router-link>

      <section v-if="notFound" class="notice-detail__gone" role="status">
        <h1 class="notice-detail__gone-title">공지를 찾을 수 없습니다</h1>
        <p class="notice-detail__gone-text">삭제됐거나 아직 게시되지 않은 공지입니다.</p>
        <router-link :to="listLink" class="notice-detail__link">목록으로</router-link>
      </section>

      <AppErrorBanner v-else-if="errorMessage" :message="errorMessage" />

      <article v-else-if="notice" class="notice-detail__article">
        <header class="notice-detail__head">
          <h1 class="notice-detail__title">{{ notice.title }}</h1>
          <p class="notice-detail__meta">
            <!-- 공개 공지에 작성 계정의 개인 이름('강사1')이 찍혔다 — 운영 이름으로 -->
            <span>{{ PUBLISHER_NAME }}</span>
            <time :datetime="notice.publishedAt || notice.createdAt">
              {{ formatDateMinuteKst(notice.publishedAt || notice.createdAt, '-') }}
            </time>
          </p>
        </header>
        <RichTextBody :html="notice.body || ''" class="notice-detail__content" />

        <section v-if="attachments.length || attachmentsError" class="notice-files" aria-label="첨부파일">
          <h2 class="notice-files__title">첨부파일 <span v-if="attachments.length">{{ attachments.length }}</span></h2>
          <p v-if="attachmentsError" class="notice-files__error" role="alert">
            첨부파일 목록을 불러오지 못했습니다.
            <q-btn flat dense no-caps color="primary" label="다시 시도" @click="loadAttachments(String(notice.id))" />
          </p>
          <ul v-else class="notice-files__list">
            <li v-for="file in attachments" :key="file.id">
              <a :href="attachmentUrl(file)" class="notice-files__item" target="_blank" rel="noopener" download>
                <q-icon name="o_file_download" size="20px" aria-hidden="true" />
                <span class="notice-files__name">{{ file.fileName || '이름 없는 파일' }}</span>
                <span class="notice-files__size">{{ formatByteSize(file.byteSize) }}</span>
              </a>
            </li>
          </ul>
        </section>
        <!-- 다른 공지로 넘어가려면 목록으로 돌아가야 했다 -->
        <nav v-if="older || newer" class="notice-nav" aria-label="이전·다음 공지">
          <router-link v-if="newer" :to="detailLink(newer)" class="notice-nav__item">
            <span class="notice-nav__label">다음 글</span>
            <span class="notice-nav__title">{{ newer.title }}</span>
          </router-link>
          <router-link v-if="older" :to="detailLink(older)" class="notice-nav__item">
            <span class="notice-nav__label">이전 글</span>
            <span class="notice-nav__title">{{ older.title }}</span>
          </router-link>
        </nav>
        <footer class="notice-detail__foot">
          <q-btn outline no-caps color="primary" label="목록으로" :to="listLink" class="notice-detail__list-btn" />
        </footer>
      </article>
    </div>

    <q-inner-loading :showing="loading">
      <q-spinner color="primary" size="32px" />
    </q-inner-loading>
  </q-page>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import RichTextBody from '@/components/RichTextBody.vue';
import { storedFileContentUrl } from '@/services/file-api';
import { getNotice, listNotices, listPublicNoticeAttachments } from '@/services/notice-api';

const PUBLISHER_NAME = '다배움 운영팀';
// 앞뒤 공지를 찾으려고 받는 최근 공지 수
const NEIGHBOR_WINDOW = 50;
import { formatDateMinuteKst } from '@/utils/datetime';

/* 공지 상세(공개) — /notices/:noticeId. 주소로 공유할 수 있게 창이 아닌 화면으로 둔다. 없거나 예약 게시 전이면 404 */
export default {
  name: 'NoticeDetailPage',
  components: { AppErrorBanner, RichTextBody },
  data() {
    return {
      PUBLISHER_NAME,
      notice: null,
      loading: false,
      notFound: false,
      errorMessage: '',
      older: null,
      newer: null,
      attachments: [],
      attachmentsError: false,
    };
  },
  computed: {
    // 목록에서 온 쪽 번호로 돌아간다
    listLink() {
      const page = this.$route.query.page;
      return { path: '/notices', query: page ? { page } : {} };
    },
  },
  watch: {
    '$route.params.noticeId': { immediate: true, handler: 'load' },
  },
  methods: {
    formatDateMinuteKst,
    detailLink(item) {
      return { path: `/notices/${item.id}`, query: this.$route.query.page ? { page: this.$route.query.page } : {} };
    },
    attachmentUrl(file) {
      return file?.fileId ? storedFileContentUrl(file.fileId) : '';
    },
    // 단위 앞을 띄운다 — 강좌 유의사항 첨부와 같은 표기
    formatByteSize(bytes) {
      const n = Number(bytes) || 0;
      if (n < 1024) return `${n} B`;
      if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
      return `${(n / 1024 / 1024).toFixed(1)} MB`;
    },
    /** 첨부 목록 — 404(첨부 API 배포 전·게시 전)는 첨부 없음과 같다. 그 밖의 실패만 알린다 */
    async loadAttachments(noticeId) {
      this.attachmentsError = false;
      try {
        this.attachments = await listPublicNoticeAttachments(noticeId);
      } catch (error) {
        this.attachments = [];
        this.attachmentsError = error?.status !== 404;
      }
    },
    /** 최근 공지 목록(게시일 내림차순)에서 앞뒤 — 못 찾으면 줄을 그리지 않는다 */
    async loadNeighbors(noticeId) {
      this.older = null;
      this.newer = null;
      try {
        const result = await listNotices({ page: 0, size: NEIGHBOR_WINDOW, audience: 'PUBLIC' });
        const rows = Array.isArray(result.data) ? result.data : [];
        const at = rows.findIndex((row) => String(row.id) === noticeId);
        if (at === -1) return;
        this.newer = rows[at - 1] || null;
        this.older = rows[at + 1] || null;
      } catch {
        // 앞뒤 줄이 없을 뿐 — 본문은 그대로
      }
    },
    async load() {
      const noticeId = String(this.$route.params.noticeId || '');
      if (!noticeId) return;
      this.loading = true;
      this.notFound = false;
      this.errorMessage = '';
      this.attachments = [];
      this.attachmentsError = false;
      try {
        this.notice = await getNotice(noticeId);
        this.loadNeighbors(noticeId);
        this.loadAttachments(noticeId);
      } catch (error) {
        this.notice = null;
        // 형식이 틀린 id 는 400 으로 온다 — 사용자에겐 같은 "없음"이다
        if (error?.status === 404 || error?.status === 400) this.notFound = true;
        else this.errorMessage = error?.message || '공지를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
.notice-detail {
  position: relative;
  padding: 24px 0 48px;
  background: var(--db-bg-page);
}

.notice-detail__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  /* .learner-container 의 max-width: 100% 를 덮지 않는다 — 폰에서 가로로 넘친다 */
  max-width: min(880px, 100%);
}

.notice-detail__back {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  margin-left: -6px;
  padding: 4px 6px;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-sub);
  text-decoration: none;
}

.notice-detail__back:hover {
  color: var(--db-text-title);
}

.notice-detail__back:focus-visible,
.notice-detail__link:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.notice-detail__article,
.notice-detail__gone {
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.notice-detail__head {
  padding: 28px 32px 20px;
  border-bottom: 1px solid var(--db-border);
}

.notice-detail__title {
  margin: 0;
  font-size: 26px;
  line-height: 34px;
  font-weight: 700;
  letter-spacing: normal;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.notice-detail__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  margin: 10px 0 0;
  font-size: 14px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.notice-detail__content {
  padding: 28px 32px 8px;
}

/* 본문이 글자 단위로 꺾였다('제안 / 해주세요') — 낱말 단위로 */
.notice-detail__content {
  word-break: keep-all;
  overflow-wrap: anywhere;
}

.notice-files {
  margin: 24px 32px 0;
  padding: 16px 20px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
  background: var(--db-bg-subtle);
}

.notice-files__title {
  margin: 0 0 8px;
  font-size: 15px;
  line-height: 22px;
  font-weight: 700;
  letter-spacing: normal;
  color: var(--db-text-title);
}

.notice-files__title span {
  color: var(--db-accent);
}

.notice-files__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.notice-files__item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 0 8px;
  margin: 0 -8px;
  border-radius: 6px;
  color: var(--db-text-body);
  text-decoration: none;
}

.notice-files__item:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: -2px;
}

.notice-files__name {
  min-width: 0;
  overflow-wrap: anywhere;
  font-size: 15px;
  font-weight: 500;
}

.notice-files__size {
  flex-shrink: 0;
  margin-left: auto;
  font-size: 14px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.notice-files__error {
  margin: 0;
  font-size: 14px;
  color: var(--db-status-pink);
}

@media (hover: hover) {
  .notice-files__item:hover {
    background: var(--db-bg-white);
  }

  .notice-files__item:hover .notice-files__name {
    color: var(--db-accent);
    text-decoration: underline;
  }
}

.notice-nav {
  display: flex;
  flex-direction: column;
  margin: 24px 32px 0;
  border-top: 1px solid var(--db-border);
}

.notice-nav__item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 4px;
  border-bottom: 1px solid var(--db-border);
  color: inherit;
  text-decoration: none;
}

.notice-nav__item:hover .notice-nav__title {
  color: var(--db-accent);
}

.notice-nav__item:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: -2px;
}

.notice-nav__label {
  flex-shrink: 0;
  width: 56px;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-sub);
}

.notice-nav__title {
  min-width: 0;
  overflow: hidden;
  font-size: 15px;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--db-text-title);
}

.notice-detail__foot {
  display: flex;
  justify-content: center;
  padding: 24px 32px 32px;
}

.notice-detail__list-btn {
  min-width: 140px;
  height: 40px;
  border-radius: 6px;
}

.notice-detail__gone {
  padding: 40px 24px;
  text-align: center;
}

.notice-detail__gone-title {
  margin: 0;
  font-size: 20px;
  line-height: 28px;
  font-weight: 700;
  letter-spacing: normal;
  color: var(--db-text-title);
}

.notice-detail__gone-text {
  margin: 8px 0 16px;
  font-size: 14px;
  color: var(--db-text-sub);
}

.notice-detail__link {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-accent);
}

@media (max-width: 599px) {
  .notice-detail__head {
    padding: 20px 16px 16px;
  }

  .notice-detail__title {
    font-size: 21px;
    line-height: 29px;
  }

  .notice-detail__content {
    padding: 20px 16px 4px;
  }

  .notice-detail__foot {
    padding: 20px 16px 24px;
  }

  .notice-nav,
  .notice-files {
    margin: 20px 16px 0;
  }

  .notice-files {
    padding: 12px 14px;
  }
}
</style>
