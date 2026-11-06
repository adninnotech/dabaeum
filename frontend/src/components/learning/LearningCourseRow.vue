<template>
  <!-- 행 전체 클릭은 제목 버튼을 행 크기로 늘려(stretched) 만든다 — article @click 은 키보드로 닿지 않았다.
       다른 버튼(강좌 정보 등)은 z-index 로 그 위에 둔다 -->
  <article
    class="learning-row"
    :class="{ 'learning-row--closed': isInterest && !row.enrolled && recruit.label && !recruit.open }"
  >
    <div class="learning-row__thumb">
      <img v-if="row.thumbnailUrl" :src="row.thumbnailUrl" :alt="row.title" />
      <q-icon v-else name="image" size="28px" color="grey-6" />
      <!-- TODO(figma): 배지 오프셋 미실측 — 8/8로 둔다 -->
      <span v-if="row.educationType" class="learning-row__type">
        <StatusBadge :status="row.educationType" />
      </span>
    </div>

    <!-- card-details 816×164 = 3행 48 + gap 10 -->
    <div class="learning-row__body">
      <div class="learning-row__line">
        <div class="learning-row__title-group">
          <h3 class="learning-row__title">
            <button type="button" class="learning-row__open" @click="$emit('open')">{{ row.title }}</button>
          </h3>
          <p class="learning-row__org">{{ row.metaLine }}</p>
        </div>
        <!-- 나의 강좌: 위 필터는 '진행 중·수료'인데 카드만 봐선 어느 게 수료인지 몰랐다(100%여도 수료가 아닐 수 있다) -->
        <span
          v-if="!isInterest && courseStateLabel"
          class="learning-row__state"
          :class="row.status === 'COMPLETED' ? 'learning-row__state--done' : 'learning-row__state--open'"
        >
          {{ courseStateLabel }}
        </span>
        <!-- 관심 강좌: 지금 신청할 수 있는지(모집 상태)와 하트 — 예전엔 모집마감도 모집중과 똑같이 보였고,
             관심 해제는 [강좌 정보]와 같은 무게의 흰 버튼이었다(강좌 카드와 같은 하트로) -->
        <template v-if="isInterest">
          <span v-if="stateLabel" class="learning-row__state" :class="{ 'learning-row__state--open': stateOpen }">
            {{ stateLabel }}
          </span>
          <q-btn
            flat
            round
            dense
            icon="favorite"
            color="pink-5"
            class="learning-row__heart"
            aria-label="관심 강좌에서 빼기"
            @click.stop="$emit('remove')"
          >
            <q-tooltip>관심 강좌에서 빼기</q-tooltip>
          </q-btn>
        </template>
      </div>

      <div class="learning-row__line">
        <div class="learning-row__texts">
          <div v-for="(line, index) in metaLines" :key="index" class="learning-row__text-line">
            <span v-for="item in line" :key="item.icon" class="learning-row__text-item">
              <q-icon :name="item.icon" size="20px" />
              {{ item.text }}
            </span>
          </div>
        </div>
        <button
          v-if="secondLineAction"
          type="button"
          :class="secondLineAction.variant"
          @click.stop="secondLineAction.run()"
        >
          {{ secondLineAction.label }}
        </button>
      </div>

      <div class="learning-row__line learning-row__line--split">
        <div v-if="tags.length" class="learning-row__tags">
          <AppTag v-for="tag in tags" :key="tag" size="lg">{{ tag }}</AppTag>
        </div>
        <!-- 시작 전·승인 대기는 진도 대신 안내 박스가 그 자리를 통째로 대체한다 -->
        <p v-else-if="notice" class="learning-row__notice">{{ notice }}</p>
        <div v-else-if="!isInterest" class="learning-row__progress">
          <div class="learning-row__progress-head">
            <span class="learning-row__progress-count">{{ progressText }}</span>
            <span class="learning-row__progress-percent">{{ progressPercent }}%</span>
          </div>
          <div class="learning-row__bar">
            <div class="learning-row__bar-fill" :style="barStyle" />
          </div>
        </div>
        <span v-else />

        <button v-if="lastAction" type="button" class="learning-row__btn-secondary" @click.stop="lastAction.run()">
          {{ lastAction.label }}
        </button>
      </div>
    </div>
  </article>
</template>

<script>
import AppTag from '@/components/AppTag.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { isOnlineCoursesEnabled } from '@/config/features';
import { courseRecruitState } from '@/utils/course-fields';
import { daysFromToday, formatDateWeekdayKst } from '@/utils/datetime';

const COURSE_STATE_LABEL = { IN_PROGRESS: '진행 중', COMPLETED: '수료' };

export default {
  name: 'LearningCourseRow',
  components: { AppTag, StatusBadge },
  props: {
    /**
     * row = {
     *   courseId, enrollmentId, title, metaLine, thumbnailUrl, educationType,
     *   status, startDate, endDate, timeText?, reviewed, tags: string[], tuitionText,
     *   progress: { attendedCount, sessionCount, progressPercent }
     * }
     */
    row: { type: Object, required: true },
    /** 'course'(기본) | 'interest' — 3행만 다르다 */
    variant: { type: String, default: 'course' },
  },
  /* open = 행 본문 클릭(나의 강의는 수강 상세, 관심 강의는 강좌 페이지), open-course = [강의 정보]·[수강평 쓰기] */
  emits: ['open', 'open-course', 'open-enrollment', 'remove'],
  computed: {
    isInterest() {
      return this.variant === 'interest';
    },
    // 관심 강좌 응답엔 courseStatus 만 있다(모집 마감일 없음) — 상태 라벨만 쓴다
    recruit() {
      return this.isInterest ? courseRecruitState({ status: this.row.courseStatus }) : { open: false, label: '' };
    },
    stateOpen() {
      return !this.row.enrolled && this.recruit.open;
    },
    courseStateLabel() {
      return COURSE_STATE_LABEL[this.row.status] || '';
    },
    stateLabel() {
      if (this.row.enrolled) return this.row.enrolledStatus === 'COMPLETED' ? '수료' : '수강 중';
      return this.recruit.label;
    },
    metaLines() {
      // 관심 강좌 응답엔 개강·종강일이 없어 늘 '- ~ -' 였다 — 회차 수와 교육비만
      if (this.isInterest) {
        const sessions = this.row.sessionCount ? [{ icon: 'format_list_numbered', text: `총 ${this.row.sessionCount}회` }] : [];
        return [[...sessions, { icon: 'payment', text: `교육비: ${this.row.tuitionText || '-'}` }]];
      }
      const from = formatDateWeekdayKst(this.row.startDate);
      const to = formatDateWeekdayKst(this.row.endDate);
      // 요일·시간은 스키마에 컬럼이 없다 — 값이 없으면 늘 '-'인 시간 칸을 그리지 않는다
      const timeItems = this.row.timeText ? [{ icon: 'schedule', text: this.row.timeText }] : [];
      return [
        [{ icon: 'calendar_month', text: `${from} ~ ${to}` }, ...timeItems],
        [{ icon: 'payment', text: `교육비: ${this.row.tuitionText || '-'}` }],
      ];
    },
    tags() {
      return this.isInterest && Array.isArray(this.row.tags) ? this.row.tags : [];
    },
    daysLeft() {
      const days = daysFromToday(this.row.startDate);
      return days === null ? 0 : days;
    },
    notice() {
      if (this.isInterest) return '';
      if (this.row.status !== 'APPLIED' && this.row.status !== 'IN_PROGRESS') return '';
      // 출석 기록이 있으면 안내 대신 출석 막대 — 개강 전 회차에 출석했는데 '시작일까지 25일'로 가려졌다
      if (this.row.progress?.attendedCount > 0) return '';
      if (this.daysLeft > 0) return `학습 시작일까지 ${this.daysLeft}일 남았습니다`;
      // 52 결정: 시작일이 지난 신청중 문구는 Figma에 없다
      if (this.row.status === 'APPLIED') return '수강 승인 대기 중입니다';
      return '';
    },
    progressText() {
      const progress = this.row.progress;
      if (!progress) return '- / -회';
      // 무엇의 진도인지 없어 출석인지 학습 진도인지 몰랐다
      return `출석 ${progress.attendedCount ?? '-'} / ${progress.sessionCount ?? '-'}회`;
    },
    progressPercent() {
      return Number(this.row.progress?.progressPercent) || 0;
    },
    barStyle() {
      return { width: `${this.progressPercent}%` };
    },
    secondLineAction() {
      if (this.isInterest) {
        // 이미 듣는 강좌 → 나의 강좌, 모집중 → 신청(주 동작), 그 밖 → 강좌 정보
        if (this.row.enrolled) {
          return { label: '나의 강좌에서 보기', variant: 'learning-row__btn-secondary', run: () => this.$emit('open-enrollment') };
        }
        if (this.recruit.open) {
          return { label: '신청하러 가기', variant: 'learning-row__btn-action', run: () => this.$emit('open-course') };
        }
        return { label: '강좌 정보', variant: 'learning-row__btn-secondary', run: () => this.$emit('open-course') };
      }
      if (this.notice) return null;
      // 1인 1강좌 1수강평이라 이미 쓴 강의엔 버튼을 안 그린다 (서버는 409)
      if (this.row.status === 'COMPLETED' && !this.row.reviewed) {
        // 수강평 작성 폼은 강좌 상세에 있다 — 입력 화면을 두 벌 두지 않는다
        return {
          label: '수강평 쓰기',
          variant: 'learning-row__btn-action',
          run: () => this.$emit('open-course'),
        };
      }
      // 온라인 학습실이 없다 — 온라인 강의 기능이 꺼져 있으면 "준비 중"만 뜨던 버튼을 두지 않는다
      if (this.row.status === 'IN_PROGRESS' && isOnlineCoursesEnabled()) {
        return {
          label: '이어 학습하기',
          variant: 'learning-row__btn-action',
          run: () => this.notifyPreparing('온라인 학습실은 준비 중입니다.'),
        };
      }
      return null;
    },
    lastAction() {
      // 관심 해제는 첫 줄 하트로 옮겼다
      if (this.isInterest) return null;
      return { label: '강좌 정보', run: () => this.$emit('open-course') };
    },
  },
  methods: {
    /* 온라인 학습실은 화면·API가 없다 — [인증하기]와 같은 안내 패턴 */
    notifyPreparing(message) {
      this.$q.notify({ type: 'info', message });
    },
  },
};
</script>

<style scoped>
/* 나의 강의·관심 강의 공통 규격 (1200×228) — 안쪽 1160 = 썸네일 320 + 24 + 816 */
.learning-row {
  display: flex;
  gap: 24px;
  height: 228px;
  padding: 24px 20px;
  background: var(--db-bg-white);
  /* border 대신 outline — Figma stroke처럼 레이아웃을 안 먹어야 안쪽이 1160으로 남는다 */
  outline: 1px solid var(--db-border);
  outline-offset: -1px;
  border-radius: 8px;
  cursor: pointer;
  position: relative;
}

/* 제목 버튼 — 겉모양은 제목 글자 그대로, ::after 가 행 전체를 덮어 클릭을 받는다 */
.learning-row__open {
  display: block;
  max-width: 100%;
  padding: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  border: 0;
  background: none;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

/* h3 의 overflow 는 positioned 가 아니라 이 ::after 를 자르지 않는다 (기준은 .learning-row) */
.learning-row__open::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 1;
  border-radius: 8px;
}

.learning-row__open:focus-visible {
  outline: none;
}

.learning-row__open:focus-visible::after {
  outline: 2px solid var(--db-accent);
  outline-offset: -2px;
}

/* 행 안의 다른 버튼은 늘린 클릭 영역보다 위 */
.learning-row__btn-action,
.learning-row__btn-secondary {
  position: relative;
  z-index: 2;
}

.learning-row__heart,
.learning-row__state {
  position: relative;
  z-index: 2;
  flex-shrink: 0;
}

.learning-row__state {
  align-self: center;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  color: var(--db-text-sub);
  background: var(--db-bg-subtle);
}

.learning-row__state--open {
  color: var(--db-accent);
  background: var(--db-accent-soft);
}

.learning-row__state--done {
  color: var(--db-status-green-text);
  background: color-mix(in srgb, var(--db-status-green) 12%, transparent);
}

.learning-row__heart {
  align-self: center;
}

/* 모집이 끝난 관심 강좌는 사진만 흐리게 */
.learning-row--closed .learning-row__thumb img,
.learning-row--closed .learning-row__thumb > .q-icon {
  opacity: 0.55;
}

.learning-row__thumb {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 320px;
  height: 180px;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 4px;
  background: var(--db-border-avatar);
}

.learning-row__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.learning-row__type {
  position: absolute;
  top: 8px;
  left: 8px;
}

/* 이 자리 배지만 좌우 8 (공용 md는 10) */
.learning-row__type :deep(.status-badge) {
  padding: 0 8px;
}

.learning-row__body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
  /* stretch면 썸네일 높이 180까지 늘어난다 — 콘텐츠와 같은 164로 둔다(시각 차이는 없음) */
  align-self: flex-start;
  min-width: 0;
}

.learning-row__line {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  height: 48px;
}

/* 3행은 520 + 여백 146 + 150이라 gap이 아니라 space-between */
.learning-row__line--split {
  justify-content: space-between;
}

.learning-row__title-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.learning-row__title {
  margin: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 24px;
  line-height: 29px;
  font-weight: 700;
  color: var(--db-text-title);
}

.learning-row__org {
  margin: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 14px;
  line-height: 17px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.learning-row__texts {
  display: flex;
  flex-direction: column;
  gap: 4px;
  /* 데스크톱은 650(816 − 버튼 150 − 16)과 같고, 좁으면 줄어든다 */
  flex: 1;
  min-width: 0;
}

.learning-row__text-line {
  display: flex;
  align-items: center;
  gap: 24px;
  height: 20px;
}

.learning-row__text-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  line-height: 17px;
  font-weight: 400;
  color: var(--db-text-body);
}

.learning-row__text-item :deep(.q-icon) {
  color: var(--db-text-sub);
}

.learning-row__progress {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 0 1 520px;
  min-width: 0;
  padding-top: 8px;
}

.learning-row__progress-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 28px;
}

.learning-row__progress-count {
  font-size: 13px;
  line-height: 17px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.learning-row__progress-percent {
  font-size: 18px;
  line-height: 28px;
  font-weight: 700;
  color: var(--db-text-title);
}

/* Figma 채움 132/520은 라벨 88%와 안 맞는 더미라 라벨값을 정본으로 쓴다 */
.learning-row__bar {
  height: 8px;
  overflow: hidden;
  border-radius: 64px;
  background: var(--db-border-light);
}

.learning-row__bar-fill {
  height: 100%;
  border-radius: 64px;
  background: var(--db-status-green);
}

.learning-row__notice {
  display: flex;
  align-items: center;
  flex: 0 1 520px;
  min-width: 0;
  height: 48px;
  margin: 0;
  padding: 10px 16px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--db-status-orange) 10%, var(--db-bg-white));
  font-size: 16px;
  line-height: 19px;
  font-weight: 400;
  color: var(--db-text-title);
}

.learning-row__tags {
  display: flex;
  align-items: center;
  gap: 11px;
  height: 48px;
}

.learning-row__tags :deep(.app-tag) {
  height: 38px;
}

.learning-row__btn-action,
.learning-row__btn-secondary {
  width: 150px;
  height: 48px;
  flex-shrink: 0;
  margin-left: auto;
  border: 0;
  border-radius: 8px;
  font-family: inherit;
  font-size: 16px;
  line-height: 19px;
  font-weight: 700;
  cursor: pointer;
}

.learning-row__btn-action {
  /* 사이트 주 버튼(검색·로그인·확인하기)과 같은 남색 — 나의 수강평 탭의 같은 [수강평 쓰기]와 색이 달랐다 */
  background: var(--db-primary);
  color: var(--db-on-color);
}

.learning-row__btn-secondary {
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  color: var(--db-text-body);
}

/* 태블릿: 썸네일을 줄여 본문 자리를 만든다 */
@media (max-width: 1023px) {
  .learning-row__thumb {
    width: 240px;
    height: 135px;
  }
}

/* 폰: 썸네일 위, 본문 아래로 쌓고 고정 높이를 푼다 — 버튼이 화면 밖으로 밀리던 것 */
@media (max-width: 599px) {
  /* 폰: 수업 방식 뱃지(오프라인·온라인·하이브리드)가 작은 썸네일·좁은 줄에 비해 너무 컸다 */
  .learning-row__type :deep(.status-badge) {
    height: 20px;
    padding: 0 6px;
    font-size: 13px;
    border-radius: 4px;
  }

  /* 16:9 큰 사진이 첫 화면을 다 써 진도·버튼은 내려야 보였다 — 작은 썸네일 왼쪽, 글·진도·버튼 오른쪽 */
  .learning-row {
    flex-direction: row;
    align-items: flex-start;
    gap: 12px;
    height: auto;
    padding: 12px;
  }

  .learning-row__thumb {
    flex: 0 0 88px;
    width: 88px;
    height: 66px;
  }

  .learning-row__body {
    flex: 1;
    min-width: 0;
  }

  .learning-row__line {
    flex-wrap: wrap;
    height: auto;
    gap: 12px;
  }

  .learning-row__title {
    font-size: 16px;
    line-height: 22px;
  }

  /* 관심 강좌: 상태 칩·하트가 제목 줄을 먹어 제목이 몇 글자만 남았다 —
   * 하트는 행 오른쪽 위 모서리로, 칩은 제목 아래 줄로 */
  .learning-row__heart {
    position: absolute;
    top: 6px;
    right: 6px;
  }

  .learning-row__state {
    align-self: flex-start;
  }

  .learning-row__line:has(.learning-row__heart) .learning-row__title-group {
    flex-basis: 100%;
    padding-right: 28px;
  }

  .learning-row__text-line {
    flex-wrap: wrap;
    gap: 4px 16px;
    height: auto;
  }

  .learning-row__texts,
  .learning-row__progress,
  .learning-row__notice,
  .learning-row__tags {
    flex: 1 1 100%;
  }

  .learning-row__notice {
    height: auto;
  }

  .learning-row__btn-action,
  .learning-row__btn-secondary {
    flex: 1 1 0;
    width: auto;
    margin-left: 0;
  }

  /* [수강평 쓰기]가 날짜와 진도 사이에 끼어 버튼 둘이 떨어져 있었다 — 줄 상자를 풀어 날짜 → 진도 → 버튼 둘 나란히 */
  .learning-row__body {
    flex-direction: row;
    flex-wrap: wrap;
    gap: 10px 8px;
  }

  .learning-row__line {
    display: contents;
  }

  .learning-row__title-group,
  .learning-row__texts,
  .learning-row__progress,
  .learning-row__notice,
  .learning-row__tags {
    flex: 1 1 100%;
  }

  .learning-row__btn-action,
  .learning-row__btn-secondary {
    order: 10;
    height: 44px;
  }
}
</style>
