<template>
  <section class="inquiries">
    <AppErrorBanner v-if="errorMessage" :message="errorMessage" />

    <h2 class="inquiries__title">문의 내역</h2>

    <div v-if="loading" class="inquiries__state"><q-spinner color="primary" size="24px" /></div>
    <AppEmptyState v-else-if="!pagedRows.length" compact description="등록된 문의가 없습니다." />

    <div v-for="(row, i) in pagedRows" v-else :key="row.id" class="inquiry-row">
      <div class="col-no">{{ (page - 1) * pageSize + i + 1 }}</div>

      <div class="col-writer">
        <div class="cell-strong">{{ row.userName || '-' }}</div>
        <!-- 마스킹 대상은 email·phone 이 null 로 온다 — '-' 를 찍지 않고 줄 자체를 생략한다 (00-common §2-4) -->
        <div v-if="row.email || row.phone" class="cell-sub">
          {{ [row.email, row.phone].filter(Boolean).join(' · ') }}
        </div>
      </div>

      <div class="col-body">
        <div class="cell-strong">
          <!-- TODO(figma): 문의 유형 배지 색 미확보 — 토큰으로 지정 -->
          <span class="type-badge" :class="`type-badge--${row.inquiryType}`">
            {{ inquiryTypeLabel(row.inquiryType) }}
          </span>
          {{ row.title || '-' }}
        </div>
        <!-- 목록 응답(InquirySummary)엔 본문이 없어 늘 '-' 가 찍혔다 — 본문은 [답변]·[보기] 창에서 본다 -->
        <div v-if="row.content" class="cell-sub cell-ellipsis">{{ row.content }}</div>
      </div>

      <div class="col-date">{{ formatDateTimeFields(row, 'createdAt', 'inquiredAt') }}</div>

      <div class="col-badge">
        <StatusBadge
          :status="answered(row) ? 'ANSWERED' : 'PENDING'"
          :label="answered(row) ? '답변 완료' : '답변 대기'"
        />
      </div>

      <!-- 동작이 '답변' 하나뿐인데 ⋮ 안에 있었다 — 줄에 바로. 답한 문의는 [보기]로 질문·답을 연다 -->
      <div class="col-kebab">
        <q-btn
          flat
          dense
          no-caps
          color="primary"
          :label="answered(row) ? '보기' : '답변'"
          @click="openReply(row)"
        />
      </div>
    </div>

    <ConsolePagination v-model="page" :total="rows.length" :page-size="pageSize" />

    <InquiryReplyDialog v-model="showReply" :inquiry="replyTarget" @replied="load" />
  </section>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import ConsolePagination from '@/components/ConsolePagination.vue';
import InquiryReplyDialog from '@/components/console/InquiryReplyDialog.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { listCourseInquiries } from '@/services/inquiry-api';
import { formatDateTimeFields } from '@/utils/datetime';
import { INQUIRY_TYPE_LABEL } from '@/utils/status';

export default {
  name: 'CourseInquiriesTab',
  components: {
    AppEmptyState,
    AppErrorBanner,
    ConsolePagination,
    InquiryReplyDialog,
    StatusBadge,
  },
  props: {
    course: { type: Object, default: null },
    console: { type: String, default: 'admin' },
  },
  data() {
    return {
      rows: [],
      loading: false,
      errorMessage: '',
      page: 1,
      pageSize: 10,
      showReply: false,
      replyTarget: null,
    };
  },
  computed: {
    courseId() {
      return this.course?.id || this.$route.params.courseId;
    },
    pagedRows() {
      const start = (this.page - 1) * this.pageSize;
      return this.rows.slice(start, start + this.pageSize);
    },
  },
  created() {
    this.load();
  },
  methods: {
    formatDateTimeFields,
    inquiryTypeLabel(type) {
      return INQUIRY_TYPE_LABEL[type] || type;
    },
    answered(row) {
      return !!(row.answeredAt || row.replyContent || row.status === 'ANSWERED');
    },
    async load() {
      if (!this.courseId) return;
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await listCourseInquiries(this.courseId, { page: 0, size: 100 });
        this.rows = Array.isArray(result.data) ? result.data : [];
        this.page = 1;
      } catch (error) {
        this.errorMessage = error.message || '문의 목록 조회 실패';
        this.rows = [];
      } finally {
        this.loading = false;
      }
    },
    openReply(row) {
      this.replyTarget = row;
      this.showReply = true;
    },
  },
};
</script>

<style scoped>
.inquiries {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.inquiries__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--db-text-title);
}

.inquiry-row {
  display: flex;
  align-items: center;
  gap: 24px;
  min-height: 77px;
  padding: 13px 24px;
  border: 1px solid var(--db-border);
  border-radius: 4px;
}

.col-kebab {
  width: 56px;
  flex-shrink: 0;
  text-align: center;
}

.col-no {
  width: 60px;
  flex-shrink: 0;
  text-align: center;
}

.col-writer {
  width: 180px;
  flex-shrink: 0;
  min-width: 0;
}

.col-body {
  flex: 1;
  min-width: 0;
}

.col-date {
  width: 120px;
  flex-shrink: 0;
  font-size: 16px;
  font-weight: 400;
  color: var(--db-text-title);
}

.col-badge {
  width: 90px;
  flex-shrink: 0;
}

.cell-strong {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 16px;
  font-weight: 700;
  color: var(--db-text-title);
}

.type-badge {
  display: inline-block;
  margin-right: 6px;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  vertical-align: middle;
}

.type-badge--COURSE {
  color: var(--db-accent);
  background: var(--db-accent-soft);
}

.type-badge--QNA {
  color: var(--db-text-sub);
  background: var(--db-bg-tag);
}

.cell-sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.cell-ellipsis {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.inquiries__state {
  padding: 40px 0;
  text-align: center;
  color: var(--db-text-sub);
}

/* 좁은 화면: 고정폭 칸을 flex-wrap 으로 접기만 해 번호·체크가 한 줄을 차지하고 본문이 눌렸다.
   카드로 바꾼다 — 1행 유형+제목, 2행 작성자 | 상태, 3행 일시 | 버튼. 번호는 숨긴다 */
@media (max-width: 1023px) {
  .inquiry-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    grid-template-areas:
      'body body'
      'writer badge'
      'date action';
    gap: 6px 12px;
    min-height: 0;
    padding: 12px 16px;
  }

  .col-no {
    display: none;
  }

  .col-body {
    grid-area: body;
  }

  .col-writer {
    grid-area: writer;
    width: auto;
  }

  .col-badge {
    grid-area: badge;
    width: auto;
    justify-self: end;
  }

  .col-date {
    grid-area: date;
    width: auto;
    font-size: 13px;
    color: var(--db-text-sub);
  }

  .col-kebab {
    grid-area: action;
    width: auto;
    justify-self: end;
  }
}
</style>
