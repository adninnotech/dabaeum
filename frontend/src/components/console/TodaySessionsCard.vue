<template>
  <!-- 강사 대시보드 '오늘 수업' — 수업 직전에 화면을 옮기지 않고 여기서 바로 출석을 받는다.
       회차마다 지금 할 일 하나만 버튼으로(출결 전 · 시작하고 QR · QR · 마감 뒤 확인). QR 창은 출결 화면과 같은 것 -->
  <section class="today-card" aria-labelledby="today-card-title">
    <header class="today-card__head">
      <h2 id="today-card-title" class="today-card__title">오늘 수업</h2>
      <span v-if="!loading && !failed" class="today-card__count">{{ items.length }}개 회차</span>
    </header>

    <div v-if="loading" class="today-card__state">
      <q-spinner size="20px" color="primary" />
      오늘 회차를 확인하는 중입니다
    </div>

    <div v-else-if="failed" class="today-card__state">
      오늘 회차를 불러오지 못했습니다.
      <q-btn flat dense no-caps color="primary" label="다시 시도" @click="$emit('retry')" />
    </div>

    <template v-else-if="items.length">
      <ul class="today-card__list">
        <li
          v-for="item in rows"
          :key="item.session.id"
          class="today-row"
          :class="`today-row--${item.state.key}`"
        >
          <!-- 시간·강의명·회차를 누르면 그 회차가 골라진 출결 관리로 — 오른쪽 단추(QR·수업 시작)는 각자 동작 -->
          <router-link
            :to="attendanceLink(item)"
            class="today-row__main"
            :aria-label="`${item.courseTitle} ${item.session.sessionNo}회차 출결 관리로`"
          >
            <div class="today-row__time">{{ item.timeText }}</div>
            <div class="today-row__info">
              <div class="today-row__title">{{ item.courseTitle }}</div>
              <div class="today-row__meta">
                {{ item.session.sessionNo }}회차
                <template v-if="item.session.topic"> · {{ item.session.topic }}</template>
                <template v-if="item.session.location"> · {{ item.session.location }}</template>
              </div>
              <div class="today-row__hint">
                <span class="today-row__dot" aria-hidden="true" />
                {{ item.state.hint }}
              </div>
            </div>
            <q-icon name="chevron_right" size="22px" class="today-row__go" aria-hidden="true" />
          </router-link>

          <div class="today-row__actions">
            <q-btn
              v-if="item.state.key === 'qr'"
              unelevated
              no-caps
              color="primary"
              icon="o_qr_code_2"
              label="출석 QR 띄우기"
              class="today-row__primary"
              @click="openQr(item)"
            />
            <q-btn
              v-else-if="item.state.key === 'start'"
              unelevated
              no-caps
              color="primary"
              icon="play_arrow"
              label="수업 시작하고 출석 QR"
              class="today-row__primary"
              :loading="startingId === item.session.id"
              @click="startAndOpenQr(item)"
            />
            <q-btn
              v-else-if="item.state.key === 'before'"
              outline
              no-caps
              color="grey-7"
              icon="schedule"
              label="출결 시간 전"
              class="today-row__primary"
              disable
            />
            <q-btn
              outline
              no-caps
              color="primary"
              :label="item.state.key === 'closed' || item.state.key === 'done' ? '출결 확인·직접 입력' : '명단에서 직접 체크'"
              class="today-row__secondary"
              :to="attendanceLink(item)"
            />
          </div>
        </li>
      </ul>
      <p v-if="capped" class="today-card__note">운영 중인 강의가 많아 최근 강의만 확인했습니다.</p>
    </template>

    <div v-else class="today-card__empty">
      <div class="today-card__empty-title">오늘은 수업이 없습니다</div>
      <div v-if="nextItem" class="today-card__next">
        다음 수업 · <strong>{{ nextText }}</strong> {{ nextItem.courseTitle }} {{ nextItem.session.sessionNo }}회차
      </div>
    </div>

    <AttendanceQrDialog
      :model-value="Boolean(qrItem)"
      :session-id="qrItem?.session.id || null"
      :course-label="qrItem?.courseTitle || ''"
      :session-label="qrItem ? `${qrItem.session.sessionNo}회차 · ${qrItem.timeText}` : ''"
      :attended-count="attendedCount"
      :total-count="qrItem?.totalCount || 0"
      @issued="loadAttendedCount"
      @update:model-value="onQrToggle"
    />
  </section>
</template>

<script>
import AttendanceQrDialog from '@/components/attendance/AttendanceQrDialog.vue';
import { listSessionAttendance } from '@/services/attendance-api';
import { updateCourseSession } from '@/services/session-api';
import { formatTimeKst } from '@/utils/datetime';
import { todaySessionState } from '@/utils/session-attendance';

// 출결 시간 전 → 시작 → 마감이 저절로 바뀌게 시간을 다시 본다
const NOW_TICK_MS = 30 * 1000;
// QR 을 띄운 동안 출석 인원을 다시 센다(출결 화면과 같은 간격)
const ATTENDED_POLL_MS = 10 * 1000;
const ATTENDED_STATUSES = ['PRESENT', 'LATE'];
const ATTENDANCE_PAGE_SIZE = 200;
const NEXT_DAY_FORMAT = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  month: 'numeric',
  day: 'numeric',
  weekday: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

export default {
  name: 'TodaySessionsCard',
  components: { AttendanceQrDialog },
  props: {
    /** [{ courseId, courseTitle, session, totalCount }] — 오늘(KST) 시작하는 취소 안 된 회차, 시각순 */
    items: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    failed: { type: Boolean, default: false },
    capped: { type: Boolean, default: false },
    /** 오늘 수업이 없을 때 보일 다음 회차 { courseTitle, session } */
    nextItem: { type: Object, default: null },
    attendancePath: { type: String, default: '/instructor/attendance' },
  },
  /** session-updated(session): 수업 시작으로 회차 상태가 바뀌었다 — 부모 목록을 바꿔 끼운다 */
  emits: ['retry', 'session-updated'],
  data() {
    return {
      now: Date.now(),
      nowTimer: null,
      startingId: '',
      qrItem: null,
      attendedCount: 0,
      attendedTimer: null,
    };
  },
  computed: {
    rows() {
      return this.items.map((item) => ({
        ...item,
        timeText: this.timeRange(item.session),
        state: todaySessionState(item.session, this.now),
      }));
    },
    nextText() {
      const startsAt = this.nextItem?.session?.startsAt;
      const date = startsAt ? new Date(startsAt) : null;
      return date && !Number.isNaN(date.getTime()) ? NEXT_DAY_FORMAT.format(date) : '';
    },
  },
  mounted() {
    this.nowTimer = window.setInterval(() => {
      this.now = Date.now();
    }, NOW_TICK_MS);
  },
  beforeUnmount() {
    window.clearInterval(this.nowTimer);
    this.stopAttendedPoll();
  },
  methods: {
    timeRange(session) {
      const start = formatTimeKst(session?.startsAt);
      const end = session?.endsAt ? formatTimeKst(session.endsAt) : '';
      return end ? `${start}~${end}` : start;
    },
    attendanceLink(item) {
      return { path: this.attendancePath, query: { courseId: item.courseId, sessionId: item.session.id } };
    },
    openQr(item) {
      this.attendedCount = 0;
      this.qrItem = item;
    },
    /** 출결 시간인데 '예정'이면 서버 규칙상 QR 이 안 나온다 — 진행 중으로 바꾸고 바로 띄운다 */
    async startAndOpenQr(item) {
      if (this.startingId) return;
      this.startingId = item.session.id;
      try {
        const updated = await updateCourseSession(item.session.id, { status: 'OPEN' });
        const session = { ...item.session, ...(updated || {}), status: 'OPEN' };
        this.$emit('session-updated', session);
        this.openQr({ ...item, session });
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error?.message || '수업을 시작하지 못했습니다. 출결 화면에서 다시 시도해 주세요.',
          position: 'top',
        });
      } finally {
        this.startingId = '';
      }
    },
    onQrToggle(open) {
      if (open) return;
      this.qrItem = null;
      this.stopAttendedPoll();
    },
    async loadAttendedCount() {
      const sessionId = this.qrItem?.session.id;
      if (!sessionId) return;
      if (!this.attendedTimer) {
        this.attendedTimer = window.setInterval(() => this.loadAttendedCount(), ATTENDED_POLL_MS);
      }
      try {
        const page = await listSessionAttendance(sessionId, { page: 0, size: ATTENDANCE_PAGE_SIZE });
        if (this.qrItem?.session.id !== sessionId) return;
        const rows = Array.isArray(page.data) ? page.data : [];
        this.attendedCount = rows.filter((row) => ATTENDED_STATUSES.includes(row.status)).length;
      } catch (error) {
        // 숫자 줄만 못 바꾼다 — QR 은 그대로 쓸 수 있다
        console.warn('[TodaySessionsCard] 출석 인원을 다시 세지 못했습니다', error);
      }
    },
    stopAttendedPoll() {
      if (this.attendedTimer) window.clearInterval(this.attendedTimer);
      this.attendedTimer = null;
    },
  },
};
</script>

<style scoped>
.today-card {
  padding: 20px 24px;
  border: 1px solid var(--db-border);
  border-radius: 12px;
  background: var(--db-bg-white);
}

.today-card__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;
}

.today-card__title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--db-text-title);
}

.today-card__count {
  font-size: 14px;
  color: var(--db-text-sub);
}

.today-card__state,
.today-card__empty {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-height: 56px;
  font-size: 15px;
  color: var(--db-text-body);
}

.today-card__empty {
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 4px;
}

.today-card__empty-title {
  font-weight: 700;
  color: var(--db-text-title);
}

.today-card__next {
  font-size: 14px;
  color: var(--db-text-sub);
}

.today-card__next strong {
  color: var(--db-text-title);
}

.today-card__note {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--db-text-sub);
}

.today-card__list {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}

.today-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px 24px;
  padding: 14px 0;
  border-top: 1px solid var(--db-border-light);
}

.today-row:first-child {
  border-top: 0;
}

.today-row__main {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  min-width: 0;
  flex: 1 1 360px;
  margin: -8px -12px;
  padding: 8px 12px;
  border-radius: 10px;
  color: inherit;
  text-decoration: none;
  transition: background-color 0.15s ease;
}

.today-row__main:hover {
  background: var(--db-bg-subtle);
}

.today-row__main:hover .today-row__title {
  color: var(--db-accent);
  text-decoration: underline;
}

.today-row__main:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.today-row__go {
  flex-shrink: 0;
  align-self: center;
  margin-left: auto;
  color: var(--db-text-sub);
}

.today-row__time {
  flex-shrink: 0;
  min-width: 112px;
  font-size: 17px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--db-text-title);
}

.today-row__info {
  min-width: 0;
}

.today-row__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 16px;
  font-weight: 700;
  color: var(--db-text-title);
}

.today-row__meta {
  margin-top: 2px;
  font-size: 14px;
  color: var(--db-text-sub);
  overflow-wrap: anywhere;
}

.today-row__hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-body);
}

.today-row__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--db-status-gray);
}

/* 지금 할 일이 있는 줄만 색으로 — 출석 받는 중(초록)·시작할 때(파랑) */
.today-row--qr .today-row__hint {
  color: var(--db-status-green-text);
}

.today-row--qr .today-row__dot {
  background: var(--db-status-green);
}

.today-row--start .today-row__hint {
  color: var(--db-accent);
}

.today-row--start .today-row__dot {
  background: var(--db-accent);
}

.today-row__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.today-row__primary,
.today-row__secondary {
  min-height: 44px;
  font-size: 15px;
}

/* 폰: 교실에서 한 손으로 — 시간·제목 위, 버튼은 한 줄 전체 폭 */
@media (max-width: 599px) {
  .today-card {
    padding: 16px;
  }

  .today-row__main {
    flex-direction: column;
    gap: 4px;
    flex-basis: 100%;
  }

  .today-row__title {
    white-space: normal;
  }

  .today-row__actions {
    flex-direction: column;
    width: 100%;
  }

  .today-row__primary,
  .today-row__secondary {
    width: 100%;
    min-height: 48px;
  }
}
</style>
