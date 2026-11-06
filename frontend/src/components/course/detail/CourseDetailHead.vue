<template>
  <header class="detail-head">
    <!-- 강좌 상세 머리 — 제목을 사진 아래가 아니라 오른쪽 위로(노트북 첫 화면 끝에 걸렸다).
         신청에 쓰는 네 가지(모집·교육기간·교육비·남은 자리)를 크게, 나머지는 작은 목록으로 -->
    <div class="detail-head__left">
      <img v-if="thumbnailUrl" :src="thumbnailUrl" :alt="course.title" class="detail-thumb" />
      <div v-else class="detail-thumb detail-thumb--empty">
        <q-icon name="image" size="40px" color="grey-6" />
      </div>
    </div>

    <div class="detail-head__right">
      <div class="detail-headline">
        <div class="detail-badges">
          <StatusBadge v-if="course.educationType" :status="course.educationType" />
          <StatusBadge v-if="course.status" :status="displayStatus" />
          <q-btn flat round dense icon="share" aria-label="링크 복사" class="detail-share" @click="$emit('share')">
            <q-tooltip>링크 복사</q-tooltip>
          </q-btn>
        </div>
        <h1 class="detail-title">{{ course.title }}</h1>
        <div class="detail-org">{{ institutionName || '-' }}</div>
      </div>

      <ul class="detail-facts">
        <li v-for="fact in keyFacts" :key="fact.key" class="detail-fact">
          <span class="detail-fact__label">{{ fact.label }}</span>
          <span class="detail-fact__value" :class="{ 'detail-fact__value--urgent': fact.urgent }">
            {{ fact.value }}
          </span>
          <span v-if="fact.sub" class="detail-fact__sub">{{ fact.sub }}</span>
        </li>
      </ul>

      <dl class="detail-meta">
        <div v-for="row in metaRows" :key="row.key" class="detail-meta__row">
          <dt class="detail-meta__label">{{ row.label }}</dt>
          <dd class="detail-meta__value">{{ row.value }}</dd>
        </div>
      </dl>

      <!-- 폰에서는 화면 아래 고정 줄이 같은 일을 한다 -->
      <CourseEnrollCta
        class="detail-cta"
        :interested="interested"
        :interest-loading="interestLoading"
        :active-enrollment="activeEnrollment"
        :active-enrollment-label="activeEnrollmentLabel"
        :enroll-label="enrollLabel"
        :enroll-disabled="enrollDisabled"
        :applying="applying"
        @toggle-interest="$emit('toggle-interest')"
        @apply="$emit('apply')"
      />
      <!-- 비활성 버튼은 툴팁 이벤트를 받지 못해 이유를 글로 둔다 -->
      <p v-if="enrollDisabledReason" class="detail-cta__reason">
        {{ enrollDisabledReason }}
      </p>
    </div>
  </header>
</template>

<script>
import StatusBadge from '@/components/StatusBadge.vue';
import CourseEnrollCta from '@/components/course/CourseEnrollCta.vue';
import { TARGET_AUDIENCE_OPTIONS } from '@/config/course-options';
import { storedFileContentUrl } from '@/services/file-api';
import { isRecruitOpen, tuitionText } from '@/utils/course-fields';
import { daysFromToday, formatDateKst, formatDatePeriodKst, formatTimeKst } from '@/utils/datetime';
import { STATUS_LABEL } from '@/utils/status';

// 이 날 수·자리 수 이하면 빨갛게
const RECRUIT_URGENT_DAYS = 7;
const SEAT_URGENT = 3;

export default {
  name: 'CourseDetailHead',
  components: { CourseEnrollCta, StatusBadge },
  props: {
    course: { type: Object, required: true },
    institutionName: { type: String, default: '' },
    /** 교육시간(1회차 시각)을 고른다 */
    sessions: { type: Array, default: () => [] },
    // 신청 줄 — 페이지가 신청 상태·권한을 판단해 넘긴다(폰 고정 줄과 같은 값)
    interested: { type: Boolean, default: false },
    interestLoading: { type: Boolean, default: false },
    activeEnrollment: { type: Object, default: null },
    activeEnrollmentLabel: { type: String, default: '' },
    enrollLabel: { type: String, default: '수강신청' },
    enrollDisabled: { type: Boolean, default: false },
    enrollDisabledReason: { type: String, default: '' },
    applying: { type: Boolean, default: false },
  },
  emits: ['share', 'toggle-interest', 'apply'],
  computed: {
    isOnline() {
      return this.course?.educationType === 'ONLINE';
    },
    thumbnailUrl() {
      const fileId = this.course?.thumbnailFileId;
      return fileId ? storedFileContentUrl(fileId) : '';
    },
    audienceLabels() {
      const values = this.course?.detail?.targetAudiences || [];
      const labels = values.map(
        (value) => TARGET_AUDIENCE_OPTIONS.find((o) => o.value === value)?.label || value,
      );
      return labels.join(', ') || '-';
    },
    placeText() {
      if (this.isOnline) return '온라인';
      return this.course?.location || '-';
    },
    /** 1회차 시각으로 대신한다 — TODO(backend): 강의 시간 텍스트 필드가 없음 */
    classTimeText() {
      const first = this.sessions[0];
      if (!first?.startsAt) return '-';
      const start = formatTimeKst(first.startsAt);
      const end = first.endsAt ? formatTimeKst(first.endsAt) : '';
      return end ? `${start}~${end}` : start;
    },
    // 크게 보이는 네 가지(keyFacts) 밖의 정보 — 작은 목록
    metaRows() {
      const course = this.course || {};
      const d = course.detail || {};
      return [
        {
          key: 'recruit',
          label: '모집기간',
          value: formatDatePeriodKst(course.recruitStartDate, course.recruitEndDate),
        },
        { key: 'time', label: '교육시간', value: this.classTimeText },
        { key: 'material', label: '재료비', value: tuitionText(d.materialFeeType, d.materialFeeAmount) },
        { key: 'credit-bank', label: '학점은행', value: course.creditBankEligible ? '인정' : '해당 없음' },
        {
          key: 'credit',
          label: '학점',
          value: course.creditBankEligible && course.creditValue != null ? `${course.creditValue}학점` : '없음',
        },
        { key: 'audience', label: '수강대상', value: this.audienceLabels },
        // 준비물이 없으면 '-' 대신 '없음' — 빠뜨린 건지 필요 없는 건지 헷갈렸다
        { key: 'materials', label: '준비물', value: d.materials?.trim() || '없음' },
        { key: 'place', label: '교육장소', value: this.placeText },
      ];
    },
    /** 신청을 정하는 네 가지 — 모집 상태·마감, 교육기간, 교육비, 남은 자리 */
    keyFacts() {
      const course = this.course || {};
      const d = course.detail || {};
      return [
        this.recruitFact,
        this.periodFact,
        { key: 'fee', label: '교육비', value: tuitionText(d.tuitionType, d.tuitionAmount) },
        this.seatFact,
      ];
    },
    // 모집중이면 마감까지 남은 날 — 상태는 모집중인데 마감일이 지났으면(데이터 어긋남) 그렇게 알린다
    /** 상태가 RECRUITING 인 채 모집 마감일이 지났으면 '모집마감'으로 — 배지는 모집중, 모집 칸은 마감으로 엇갈렸다(QA CRS-17) */
    displayStatus() {
      const status = this.course?.status;
      return status === 'RECRUITING' && !isRecruitOpen(this.course) ? 'RECRUITMENT_CLOSED' : status;
    },
    recruitFact() {
      const course = this.course || {};
      const base = { key: 'recruit', label: '모집' };
      if (course.status !== 'RECRUITING') {
        return { ...base, value: STATUS_LABEL[course.status] || '-' };
      }
      const days = daysFromToday(course.recruitEndDate);
      if (days === null) return { ...base, value: '모집중' };
      if (days < 0) return { ...base, value: '모집 마감', sub: formatDateKst(course.recruitEndDate) };
      if (days === 0) return { ...base, value: '오늘 마감', urgent: true };
      return {
        ...base,
        // 'D-3' 은 중장년 이용자에게 낯설다 — 풀어 쓴다
        value: `${days}일 남음`,
        sub: `${formatDateKst(course.recruitEndDate)} 마감`,
        urgent: days <= RECRUIT_URGENT_DAYS,
      };
    },
    // 큰 칸에 연도까지 넣으면 두 줄로 꺾였다 — 월.일만 크게, 연도는 아래 작게
    periodFact() {
      const { startDate, endDate } = this.course || {};
      const base = { key: 'period', label: '교육기간' };
      const start = String(startDate || '').slice(0, 10);
      const end = String(endDate || '').slice(0, 10);
      if (!/^\d{4}-\d{2}-\d{2}$/.test(start)) return { ...base, value: '-' };
      const md = (date) => `${date.slice(5, 7)}.${date.slice(8, 10)}`;
      const hasEnd = /^\d{4}-\d{2}-\d{2}$/.test(end);
      const years = hasEnd && end.slice(0, 4) !== start.slice(0, 4) ? `${start.slice(0, 4)}~${end.slice(0, 4)}년` : `${start.slice(0, 4)}년`;
      return { ...base, value: hasEnd ? `${md(start)} ~ ${md(end)}` : md(start), sub: years };
    },
    seatFact() {
      const { capacity, enrolledCount } = this.course || {};
      const base = { key: 'seats', label: '남은 자리' };
      if (capacity == null) return { ...base, value: '-' };
      const left = Math.max(capacity - (enrolledCount || 0), 0);
      const sub = `${enrolledCount ?? 0} / ${capacity}명`;
      if (left === 0) return { ...base, value: '정원 마감', sub: `${sub} · 대기 신청`, urgent: true };
      return { ...base, value: `${left}자리`, sub, urgent: left <= SEAT_URGENT };
    },
  },
};
</script>

<style scoped>
/* 헤더 — 좌 사진(줄였다), 우 제목·핵심 네 가지·작은 목록·신청 */
.detail-head {
  display: grid;
  grid-template-columns: minmax(0, 440px) minmax(0, 1fr);
  gap: 28px;
  padding: 10px;
}

.detail-thumb {
  width: 100%;
  aspect-ratio: 4 / 3;
  border-radius: 8px;
  object-fit: cover;
  background: var(--db-border-avatar);
}

.detail-thumb--empty {
  display: flex;
  align-items: center;
  justify-content: center;
}

.detail-head__right {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
}

.detail-headline {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-badges {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-share {
  margin-left: auto;
  color: var(--db-text-sub);
}

.detail-title {
  margin: 0;
  font-size: 28px;
  line-height: 1.3;
  font-weight: 700;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.detail-org {
  font-size: 15px;
  color: var(--db-text-body);
}

/* 신청을 정하는 네 가지 — 큰 값 */
.detail-facts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1px;
  margin: 0;
  padding: 0;
  list-style: none;
  background: var(--db-border);
  border: 1px solid var(--db-border);
  border-radius: 8px;
  overflow: hidden;
}

.detail-fact {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  padding: 12px 14px;
  background: var(--db-bg-white);
}

.detail-fact__label {
  font-size: 13px;
  color: var(--db-text-sub);
}

.detail-fact__value {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--db-text-title);
  font-variant-numeric: tabular-nums;
  word-break: keep-all;
}

.detail-fact__value--urgent {
  color: var(--db-status-pink);
}

.detail-fact__sub {
  font-size: 13px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

/* 그 밖의 정보 — 작은 2열 목록 */
.detail-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 20px;
  margin: 0;
}

.detail-meta__row {
  display: flex;
  gap: 10px;
  min-width: 0;
  font-size: 14px;
  line-height: 20px;
}

.detail-meta__label {
  flex: 0 0 64px;
  color: var(--db-text-sub);
}

.detail-meta__value {
  min-width: 0;
  margin: 0;
  font-weight: 500;
  color: var(--db-text-body);
  overflow-wrap: anywhere;
}

.detail-cta {
  margin-top: auto;
}

.detail-cta__reason {
  margin: -10px 0 0;
  font-size: 14px;
  color: var(--db-text-sub);
}

/* 강의 등록 미리보기처럼 좁은 칸(container) 안에 들어가면 화면이 넓어도 세로로 쌓는다 —
   두 칸 그대로 두면 핵심 네 가지가 한 글자씩 꺾였다. 학습자 상세에는 container 가 없어 닿지 않는다 */
@container (max-width: 879px) {
  .detail-head {
    grid-template-columns: minmax(0, 1fr);
    gap: 16px;
  }

  .detail-thumb {
    aspect-ratio: 16 / 9;
  }
}

@media (max-width: 1023px) {
  .detail-head {
    grid-template-columns: minmax(0, 1fr);
    gap: 16px;
  }

  .detail-thumb {
    aspect-ratio: 16 / 9;
  }

  /* 폰·태블릿은 화면 아래 고정 줄이 신청을 맡는다 — 머리 안 신청 줄은 숨긴다 */
  .detail-cta {
    display: none;
  }
}

@media (max-width: 599px) {
  .detail-head {
    padding: 0;
  }

  .detail-title {
    font-size: 22px;
  }

  .detail-facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-fact {
    padding: 10px 12px;
  }

  .detail-fact__value {
    font-size: 16px;
  }

  .detail-meta {
    grid-template-columns: minmax(0, 1fr);
  }

  .detail-cta__reason {
    margin-top: 0;
  }
}
</style>
