<template>
  <!-- 콘솔 문의 목록 — 강사·기관 문의 관리가 같이 쓴다(불러오는 API 만 다르다).
       기본 '미답변', 답할 문의는 채운 [답변], 답변한 문의도 [보기]로 다시 연다 -->
  <div>
    <AppErrorBanner :message="errorMessage">
      <template #action>
        <q-btn flat dense no-caps color="negative" label="다시 시도" @click="load" />
      </template>
    </AppErrorBanner>

    <!-- 미답변이 답변한 문의 사이에 묻혔다 — 기본은 답할 것만 -->
    <div class="inquiry-filter">
      <q-btn-toggle
        v-model="filter"
        no-caps
        unelevated
        dense
        toggle-color="primary"
        class="inquiry-filter__toggle"
        :options="filterOptions"
      />
    </div>

    <!-- 문의가 없거나 불러오는 중이면 그냥 빈 화면이었다 -->
    <div v-if="loading && !rows.length" class="list-state">
      <q-spinner color="primary" size="24px" />
    </div>
    <AppEmptyState
      v-else-if="!visibleRows.length && !errorMessage"
      compact
      :description="emptyText"
    />
    <q-list v-else-if="visibleRows.length" bordered separator class="inquiry-list">
      <!-- 오른쪽 작은 글자만 눌러졌다 — 줄 전체를 누르면 같은 창 -->
      <q-item v-for="item in visibleRows" :key="item.id" clickable @click="openReply(item)">
        <q-item-section avatar>
          <q-avatar size="40px">
            <img v-if="item.profileImageId" :src="storedFileContentUrl(item.profileImageId)" alt="" />
            <template v-else>{{ initial(item.userName) }}</template>
          </q-avatar>
        </q-item-section>
        <q-item-section>
          <q-item-label class="inquiry-title">{{ item.title }}</q-item-label>
          <q-item-label caption class="inquiry-meta">
            <!-- 폰에선 상태 뱃지가 제목 폭을 좁혀 정보 줄로 내린다 -->
            <StatusBadge
              v-if="$q.screen.xs"
              :status="item.status"
              :label="statusText(item)"
              :color="statusColor(item)"
              size="sm"
              soft
            />
            <!-- 초까지 붙은 시각이 폰에서 날짜와 갈라져 다음 줄로 넘어갔다 -->
            <span
              >{{ item.courseTitle || '-' }} · {{ item.userName || '-' }} ·
              <span class="inquiry-time">{{ formatDateMinuteKst(item.createdAt) }}</span></span
            >
          </q-item-label>
        </q-item-section>
        <!-- 답변한 문의도 [보기]로 질문·답을 다시 연다. 답할 문의는 채운 버튼으로 구분한다 -->
        <q-item-section side class="inquiry-side">
          <!-- 상태는 연한 뱃지 — 진한 칠은 지금 누를 [답변] 하나뿐. 진한 회색·초록·남색 덩어리가 한 줄에 셋이었고
               이미 답한 '답변 완료'가 가장 튀었다 -->
          <StatusBadge
            v-if="!$q.screen.xs"
            :status="item.status"
            :label="statusText(item)"
            :color="statusColor(item)"
            soft
          />
          <q-btn
            v-if="isAnswered(item)"
            flat
            dense
            no-caps
            color="primary"
            label="보기"
            class="inquiry-action"
            @click.stop="openReply(item)"
          />
          <q-btn
            v-else
            unelevated
            dense
            no-caps
            color="primary"
            label="답변"
            class="inquiry-action"
            @click.stop="openReply(item)"
          />
        </q-item-section>
      </q-item>
    </q-list>
    <p v-if="capped" class="capped-note">최근 문의 {{ PAGE_SIZE }}건만 보여 줍니다.</p>

    <InquiryReplyDialog v-model="replyOpen" :inquiry="replyTarget" @replied="load" />
  </div>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import InquiryReplyDialog from '@/components/console/InquiryReplyDialog.vue';
import { formatDateMinuteKst } from '@/utils/datetime';
import { hexForStatus } from '@/utils/status';
import { storedFileContentUrl } from '@/services/file-api';

const PAGE_SIZE = 50;
// 연한 뱃지 바탕(SOFT_BG)을 찾으려면 상태색 hex 그대로여야 한다
const ANSWERED_COLOR = hexForStatus('ANSWERED');

export default {
  name: 'ConsoleInquiryList',
  components: { AppEmptyState, AppErrorBanner, InquiryReplyDialog, StatusBadge },
  props: {
    /** ({ page, size }) => Promise<{ data, page }> — 콘솔마다 목록 API 가 다르다. null 이면(기관 미정) 비운다 */
    fetchPage: { type: Function, default: null },
  },
  emits: ['loading'],
  data() {
    return {
      PAGE_SIZE,
      rows: [],
      capped: false,
      filter: 'OPEN',
      loading: false,
      errorMessage: '',
      replyOpen: false,
      replyTarget: null,
    };
  },
  computed: {
    openCount() {
      return this.rows.filter((item) => !this.isAnswered(item)).length;
    },
    filterOptions() {
      return [
        { label: `미답변 ${this.openCount}`, value: 'OPEN' },
        // 전체도 건수 — 눌러 보기 전엔 몇 건인지 몰랐다
        { label: `전체 ${this.rows.length}`, value: 'ALL' },
      ];
    },
    visibleRows() {
      return this.filter === 'OPEN' ? this.rows.filter((item) => !this.isAnswered(item)) : this.rows;
    },
    emptyText() {
      return this.filter === 'OPEN' && this.rows.length ? '답변할 문의가 없습니다.' : '접수된 문의가 없습니다.';
    },
  },
  watch: {
    // 기관 콘솔에서 기관을 바꾸면 불러오는 함수가 바뀐다
    fetchPage: { immediate: true, handler: 'load' },
    loading(value) {
      this.$emit('loading', value);
    },
  },
  methods: {
    formatDateMinuteKst,
    storedFileContentUrl,
    initial(name) {
      return (name || '?').trim().charAt(0).toUpperCase();
    },
    async load() {
      if (!this.fetchPage) {
        this.rows = [];
        return;
      }
      this.loading = true;
      this.errorMessage = '';
      try {
        const page = await this.fetchPage({ page: 0, size: PAGE_SIZE });
        this.rows = page.data || [];
        this.capped = (page.page?.totalElements || 0) > PAGE_SIZE;
      } catch (error) {
        this.rows = [];
        this.errorMessage = error.message || '문의 목록을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    // 공용 라벨 '대기'는 무엇을 기다리는지 몰라 문의 화면에선 '답변 대기'
    statusText(item) {
      return this.isAnswered(item) ? '답변 완료' : '답변 대기';
    },
    // 대기는 보조 글자색(회색 4.5:1 넘김), 완료는 완료 초록 — 서버 상태값(OPEN·PENDING…)마다 색이 갈리지 않게 둘로 고정
    statusColor(item) {
      return this.isAnswered(item) ? ANSWERED_COLOR : 'var(--db-text-sub)';
    },
    isAnswered(item) {
      return item.status === 'ANSWERED';
    },
    openReply(item) {
      this.replyTarget = item;
      this.replyOpen = true;
    },
  },
};
</script>

<style scoped>
.list-state {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}

.inquiry-filter {
  margin-bottom: 12px;
}

.inquiry-filter__toggle {
  border: 1px solid var(--db-border);
}

/* dense 토글은 글자가 테두리에 붙었다 — 누르기 좋은 폭으로 */
.inquiry-filter__toggle :deep(.q-btn) {
  min-height: 32px;
  padding: 0 14px;
}

.inquiry-list {
  background: var(--db-bg-white);
}

.inquiry-title {
  font-weight: 700;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.inquiry-time {
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.inquiry-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.capped-note {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--db-text-sub);
}

.inquiry-side {
  flex-direction: row;
  align-items: center;
  gap: 8px;
}

/* dense 버튼(32px)이 옆 상태 뱃지(28px)보다 커 줄 오른쪽 높이가 들쭉날쭉했다 — 뱃지 높이에 맞춘다 */
.inquiry-action {
  min-height: 28px;
  height: 28px;
  padding: 0 12px;
}
</style>
