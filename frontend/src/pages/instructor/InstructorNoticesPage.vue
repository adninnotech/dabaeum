<template>
  <q-page padding>
    <AppPageHeader
      title="공지사항"
      show-refresh
      :loading="loading"
      @refresh="loadNotices"
    />
    <AppErrorBanner :message="errorMessage" />
    <!-- 불러오는 중엔 아무 표시가 없었고, 행을 눌러도 상세가 올 때까지 반응이 없었다 -->
    <div v-if="loading && !notices.length" class="list-state"><q-spinner color="primary" size="24px" /></div>
    <q-list v-else-if="notices.length" bordered separator class="bg-white">
      <q-item v-for="item in notices" :key="item.id" clickable :disable="!!openingId" @click="openDetail(item)">
        <q-item-section>
          <q-item-label class="notice-title">
            <span class="notice-audience" :class="`notice-audience--${item.audienceKey}`">{{ item.audienceLabel }}</span>
            {{ item.title }}
          </q-item-label>
        </q-item-section>
        <q-item-section side>
          <q-spinner v-if="openingId === item.id" color="primary" size="18px" />
          <span v-else>{{ item.date }}</span>
        </q-item-section>
      </q-item>
    </q-list>
    <AppEmptyState v-else-if="!errorMessage" compact description="등록된 공지가 없습니다." />

    <q-dialog v-model="detailOpen">
      <q-card style="width: 560px; max-width: 94vw">
        <q-card-section class="text-h6">{{ detail?.title }}</q-card-section>
        <q-card-section class="text-grey-7">{{ detail?.date }}</q-card-section>
        <q-separator />
        <!-- 공지 본문은 관리자 편집기(q-editor)로 쓴 HTML 이다 — 글자로 찍으면 태그가 그대로 보였다 -->
        <q-card-section><RichTextBody :html="detail?.body || ''" /></q-card-section>
        <q-card-actions align="right"><q-btn flat label="닫기" v-close-popup /></q-card-actions>
      </q-card>
    </q-dialog>
  </q-page>
</template>

<script>
import { formatDateMinuteKst } from '@/utils/datetime';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import AppEmptyState from '@/components/AppEmptyState.vue';
import AppPageHeader from '@/components/AppPageHeader.vue';
import RichTextBody from '@/components/RichTextBody.vue';
import { getNotice, listNotices } from '@/services/notice-api';

const NOTICE_AUDIENCES = ['INSTRUCTOR', 'PUBLIC'];
const NOTICE_PAGE_SIZE = 50;

export default {
  name: 'InstructorNoticesPage',
  components: { AppEmptyState, AppErrorBanner, AppPageHeader, RichTextBody },
  data() {
    return {
      notices: [],
      loading: false,
      errorMessage: '',
      detailOpen: false,
      detail: null,
      openingId: null,
    };
  },
  mounted() {
    this.loadNotices();
  },
  methods: {
    async loadNotices() {
      this.loading = true;
      this.errorMessage = '';
      try {
        // audience 는 한 값으로만 거른다 — 강사 대상만 불러 관리자가 '전체'(PUBLIC)로 낸 공지가 빠졌다. 둘을 합친다
        const [instructorPage, publicPage] = await Promise.all(
          NOTICE_AUDIENCES.map((audience) => listNotices({ page: 0, size: NOTICE_PAGE_SIZE, audience })),
        );
        const tagged = [
          ...(instructorPage.data || []).map((item) => ({ ...item, audienceKey: 'INSTRUCTOR', audienceLabel: '강사' })),
          ...(publicPage.data || []).map((item) => ({ ...item, audienceKey: 'PUBLIC', audienceLabel: '전체' })),
        ];
        this.notices = tagged
          .map((item) => ({ ...item, sortAt: item.publishedAt || item.createdAt || '', date: formatDateMinuteKst(item.publishedAt || item.createdAt) }))
          .sort((a, b) => String(b.sortAt).localeCompare(String(a.sortAt)));
      } catch (error) {
        this.notices = [];
        this.errorMessage = error.message || '공지를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    async openDetail(item) {
      if (this.openingId) return;
      this.openingId = item.id;
      try {
        const detail = await getNotice(item.id);
        this.detail = {
          title: detail.title,
          body: detail.body,
          date: formatDateMinuteKst(detail.publishedAt || detail.createdAt),
        };
        this.detailOpen = true;
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '상세를 불러오지 못했습니다.' });
      } finally {
        this.openingId = null;
      }
    },
  },
};
</script>

<style scoped>
.notice-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.notice-audience {
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.notice-audience--INSTRUCTOR {
  color: var(--db-primary);
  background: var(--db-accent-soft);
}

.notice-audience--PUBLIC {
  color: var(--db-text-sub);
  background: var(--db-bg-subtle);
}

.list-state {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}
</style>
