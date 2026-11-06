<template>
  <!-- 학습관리 '나의 문의' 탭 — 목록(검색·칩 거른 행)은 페이지가 주고, 펼침·상세 불러오기는 여기서 든다 -->
  <div class="my-inquiries">
    <!-- 이 탭에서 새 문의를 남길 길이 없었다 — 어디서 남기는지 함께 -->
    <p v-if="totalCount" class="inquiry-ask">
      강좌에 대한 질문은 강좌 상세의 Q&amp;A에서, 그 밖의 문의는
      <router-link to="/support" class="inquiry-ask__link">고객센터에서 문의하기</router-link>
    </p>
    <div class="learning-panel">
      <!-- 빈 화면에서 끝나지 않게 — 문의는 강좌 상세 Q&A 에서 남긴다 -->
      <AppEmptyState
        v-if="!rows.length && !failed"
        :description="emptyText"
      >
        <template v-if="!query && !totalCount" #action>
          <q-btn outline no-caps color="primary" label="고객센터에서 문의하기" to="/support" />
          <q-btn unelevated no-caps color="primary" label="강좌 찾기" to="/courses" />
        </template>
      </AppEmptyState>
      <!-- 관리자 C-6과 달리 다이얼로그가 아니라 인라인 아코디언이다 -->
      <div v-for="item in rows" :key="item.id" class="inquiry-row">
        <button
          type="button"
          class="inquiry-row__head"
          :aria-expanded="expandedId === item.id ? 'true' : 'false'"
          @click="toggleInquiry(item)"
        >
          <!-- 고객센터 문의는 강좌가 없어 빈 썸네일·'-' 만 보였다 — '일반 문의'로 -->
          <!-- 92×60 사진 위에 수업 방식 배지를 얹으니 사진이 거의 다 가려졌다 — 배지는 아래 정보 줄 맨 앞으로 -->
          <span class="inquiry-row__thumb" :class="{ 'inquiry-row__thumb--general': !item.courseId }">
            <template v-if="item.courseId">
              <img v-if="item.thumbnailUrl" :src="item.thumbnailUrl" :alt="item.courseTitle" />
            </template>
            <q-icon v-else name="o_support_agent" size="24px" aria-hidden="true" />
          </span>
          <span class="inquiry-row__course">
            <span class="inquiry-row__course-title">{{ item.courseId ? item.courseTitle || '-' : '일반 문의' }}</span>
            <span class="inquiry-row__course-meta">
              <StatusBadge
                v-if="item.courseId && item.educationType"
                size="sm"
                :status="item.educationType"
                class="inquiry-row__type"
              />
              <span>{{ inquiryTypeLabel(item) }}</span>
            </span>
          </span>
          <span class="inquiry-row__title">{{ item.title }}</span>
          <span class="inquiry-row__status">
            <StatusBadge :status="item.status" :label="inquiryStatusLabel(item.status)" />
          </span>
          <span class="inquiry-row__date">{{ formatDateMinuteKst(item.createdAt) }}</span>
          <span class="inquiry-row__toggle">
            <q-icon
              :name="expandedId === item.id ? 'expand_less' : 'expand_more'"
              size="24px"
            />
          </span>
        </button>

        <!-- FAQ 와 같은 Q·A 문법. 본문은 펼칠 때 받는다 — 기다리는 동안·실패를 보인다
             (예전엔 실패하면 질문 '-'·'답변 대기 중'으로 보여 답이 달렸어도 대기로 읽혔다) -->
        <div v-if="expandedId === item.id" class="inquiry-qa">
          <div v-if="inquiryLoadingId === item.id" class="inquiry-qa__state">
            <q-spinner color="primary" size="20px" /> 문의 내용을 불러오는 중…
          </div>
          <div v-else-if="inquiryErrors[item.id]" class="inquiry-qa__state inquiry-qa__state--error" role="alert">
            문의 내용을 불러오지 못했습니다.
            <q-btn flat dense no-caps color="primary" label="다시 시도" @click="loadInquiryDetail(item)" />
          </div>
          <template v-else>
            <div class="inquiry-qa__q">
              <span class="inquiry-qa__mark" aria-hidden="true">Q</span>
              <p class="inquiry-qa__text">{{ inquiryDetail(item).content || '-' }}</p>
            </div>
            <div v-if="inquiryDetail(item).answer" class="inquiry-qa__a">
              <span class="inquiry-qa__mark inquiry-qa__mark--a" aria-hidden="true">A</span>
              <div class="inquiry-qa__a-body">
                <p class="inquiry-qa__text">{{ inquiryDetail(item).answer }}</p>
                <span v-if="answeredAtText(item)" class="inquiry-qa__aside">{{ answeredAtText(item) }} 답변</span>
              </div>
            </div>
            <!-- 답이 없을 때 A 줄이 답처럼 보였다 — 안내 한 줄만 -->
            <p v-else class="inquiry-qa__pending">아직 답변이 없어요. 답변이 달리면 여기에 보여요.</p>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { getInquiry } from '@/services/inquiry-api';
import { formatDateMinuteKst } from '@/utils/datetime';

export default {
  name: 'MyInquiriesPanel',
  components: { AppEmptyState, StatusBadge },
  props: {
    /** 검색·칩으로 거른 문의 행(toCourseCard 를 편 InquirySummary) */
    rows: { type: Array, default: () => [] },
    /** 거르기 전 전체 문의 수 — 안내 줄·빈 화면 문구를 고른다 */
    totalCount: { type: Number, default: 0 },
    query: { type: String, default: '' },
    /** 목록을 못 받았으면 빈 안내 대신 페이지 오류 배너만 */
    failed: { type: Boolean, default: false },
  },
  data() {
    return {
      expandedId: '',
      inquiryDetails: {},
      inquiryLoadingId: '',
      inquiryErrors: {},
    };
  },
  computed: {
    emptyText() {
      if (this.query) return '검색 결과가 없습니다.';
      if (this.totalCount) return '이 조건에 맞는 문의가 없습니다.';
      return '등록한 문의가 없습니다. 강좌 질문은 강좌 상세의 Q&A에서, 그 밖의 문의는 고객센터에서 남길 수 있어요.';
    },
  },
  methods: {
    formatDateMinuteKst,
    /* 목록 응답에는 본문·답변이 없어서 펼칠 때 한 번만 상세를 받아 둔다 */
    toggleInquiry(item) {
      if (this.expandedId === item.id) {
        this.expandedId = '';
        return;
      }
      this.expandedId = item.id;
      if (!this.inquiryDetails[item.id]) this.loadInquiryDetail(item);
    },
    async loadInquiryDetail(item) {
      this.inquiryLoadingId = item.id;
      this.inquiryErrors = { ...this.inquiryErrors, [item.id]: false };
      try {
        const detail = await getInquiry(item.id);
        this.inquiryDetails = { ...this.inquiryDetails, [item.id]: detail };
      } catch {
        this.inquiryErrors = { ...this.inquiryErrors, [item.id]: true };
      } finally {
        if (this.inquiryLoadingId === item.id) this.inquiryLoadingId = '';
      }
    },
    /* 강좌 없는 문의는 고객센터에서 남긴 일반 문의 */
    inquiryTypeLabel(item) {
      if (!item.courseId) return '고객센터 문의';
      return item.inquiryType === 'QNA' ? '수강생 Q&A' : '강좌 문의';
    },
    inquiryDetail(item) {
      return this.inquiryDetails[item.id] || item;
    },
    /* 공용 라벨은 '답변완료'/'대기' — 이 화면 문구만 따로 쓴다 */
    inquiryStatusLabel(status) {
      return status === 'ANSWERED' ? '답변 완료' : '답변 대기';
    },
    answeredAtText(item) {
      const detail = this.inquiryDetail(item);
      return detail.answeredAt ? formatDateMinuteKst(detail.answeredAt) : '';
    },
  },
};
</script>

<style scoped>
/* 문의·수강평 공용 패널 — 행과 같은 이유로 outline (border면 안쪽 폭이 1.6px 모자란다) */
.learning-panel {
  background: var(--db-bg-white);
  outline: 1px solid var(--db-border);
  outline-offset: -1px;
  border-radius: 8px;
}

/* 문의 아코디언 */
/* 접힘 86 = 13 + 헤드 60 + 13. 펼침은 같은 padding 안에 Q&A가 더 들어간다 */
.inquiry-row {
  padding: 13px 24px;
  border-top: 1px solid var(--db-border);
}

/* 칩이 패널 밖으로 나가 첫 줄 위 선이 패널 테두리와 겹친다 */
.inquiry-row:first-of-type,
.review-row:first-of-type {
  border-top: 0;
}

/* 1152 = 92 + 223 + 477 + 80 + 160 + 40 + gap 16×5 */
.inquiry-row__head {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  width: 100%;
  height: 60px;
  padding: 0;
  background: none;
  border: none;
  text-align: left;
  cursor: pointer;
}

.inquiry-row__thumb {
  position: relative;
  display: flex;
  flex: 0 0 92px;
  height: 60px;
  overflow: hidden;
  border-radius: 4px;
  background: var(--db-border-avatar);
}

.inquiry-row__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.inquiry-row__type {
  flex-shrink: 0;
}

.inquiry-row__course {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 0 0 223px;
  min-width: 0;
}

.inquiry-row__course-title {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 16px;
  line-height: 19px;
  font-weight: 700;
  color: var(--db-text-title);
}

/* 기관·강사·카테고리가 문의 응답에 없다 */
.inquiry-row__course-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: 13px;
  line-height: 15px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.inquiry-row__title {
  flex: 0 0 477px;
  min-width: 0;
  overflow: hidden;
  padding: 12px 10px;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 16px;
  line-height: 19px;
  font-weight: 700;
  color: var(--db-text-body);
}

.inquiry-row__status {
  flex: 0 0 80px;
}

.inquiry-row__date {
  flex: 0 0 160px;
  white-space: nowrap;
  font-size: 16px;
  line-height: 19px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.inquiry-row__toggle {
  flex: 0 0 40px;
  color: var(--db-text-sub);
}

/* 펼친 Q·A — FAQ(FaqAccordion)와 같은 문법: 파란 Q, 연한 칸 안의 A */
.inquiry-qa {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 0 4px 108px;
}

.inquiry-qa__q,
.inquiry-qa__a {
  display: flex;
  gap: 10px;
}

.inquiry-qa__a {
  padding: 12px;
  background: var(--db-bg-subtle);
  border-radius: 6px;
}

.inquiry-qa__a-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.inquiry-qa__mark {
  flex-shrink: 0;
  width: 20px;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.5;
  color: var(--db-accent);
}

.inquiry-qa__mark--a {
  color: var(--db-text-sub);
}

.inquiry-qa__text {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-size: 15px;
  line-height: 1.5;
  color: var(--db-text-body);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.inquiry-qa__aside {
  font-size: 14px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.inquiry-qa__pending {
  margin: 0 0 0 30px;
  font-size: 14px;
  color: var(--db-text-sub);
}

.inquiry-qa__state {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--db-text-sub);
}

.inquiry-qa__state--error {
  color: var(--db-status-pink);
}

/* 고객센터 문의 — 강좌 썸네일 대신 */
.inquiry-row__thumb--general {
  align-items: center;
  justify-content: center;
  color: var(--db-text-sub);
  background: var(--db-bg-subtle);
}

.inquiry-ask {
  margin: 0 0 10px;
  font-size: 14px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.inquiry-ask__link {
  font-weight: 600;
  color: var(--db-accent);
  text-decoration: none;
}

.inquiry-ask__link:hover,
.inquiry-ask__link:focus-visible {
  text-decoration: underline;
}

@media (max-width: 1199px) {
  .inquiry-row {
    padding: 12px 16px;
  }

  .inquiry-row__head {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto auto;
    grid-template-areas:
      'course status toggle'
      'title title title'
      'date date date';
    gap: 4px 8px;
    height: auto;
  }

  .inquiry-row__thumb {
    display: none;
  }

  .inquiry-row__course {
    grid-area: course;
  }

  .inquiry-row__status {
    grid-area: status;
  }

  .inquiry-row__toggle {
    grid-area: toggle;
  }

  .inquiry-row__title {
    grid-area: title;
    padding: 0;
    white-space: normal;
    overflow-wrap: anywhere;
  }

  .inquiry-row__date {
    grid-area: date;
    font-size: 14px;
  }

  .inquiry-qa {
    padding-left: 0;
  }

  .inquiry-qa__text {
    font-size: 14px;
  }
}
</style>
