<template>
  <!-- 출결 화면 회차 머리 — 회차 일시·장소·지금 상태가 선택 칸 글자 안에만 있어 잘리고 묻혔다.
       크게 보이고, 이전·다음 회차로 바로 넘기고, 지금 할 일(QR·수업 시작/종료)을 여기 둔다 -->
  <section class="session-head" :class="`session-head--${state.key}`" aria-label="회차 정보">
    <div class="session-head__nav">
      <q-btn
        flat
        round
        icon="chevron_left"
        class="session-head__arrow"
        :disable="!prevSession"
        :aria-label="prevSession ? `이전 회차 ${prevSession.sessionNo}회차` : '이전 회차 없음'"
        @click="$emit('select', prevSession.id)"
      />
      <div class="session-head__body">
        <div class="session-head__title">
          <span class="session-head__no">{{ session.sessionNo }}회차</span>
          <span class="session-head__when">{{ when }}</span>
          <span v-if="isToday" class="session-head__today">오늘</span>
        </div>
        <div v-if="session.topic || session.location" class="session-head__meta">
          <span v-if="session.topic">{{ session.topic }}</span>
          <span v-if="session.topic && session.location" aria-hidden="true"> · </span>
          <span v-if="session.location">{{ session.location }}</span>
        </div>
        <div class="session-head__state">
          <span class="session-head__dot" aria-hidden="true" />
          {{ state.hint }}
        </div>
      </div>
      <q-btn
        flat
        round
        icon="chevron_right"
        class="session-head__arrow"
        :disable="!nextSession"
        :aria-label="nextSession ? `다음 회차 ${nextSession.sessionNo}회차` : '다음 회차 없음'"
        @click="$emit('select', nextSession.id)"
      />
    </div>

    <div class="session-head__actions">
      <q-btn
        v-if="nextAction"
        :unelevated="nextAction.status === 'OPEN'"
        :outline="nextAction.status !== 'OPEN'"
        no-caps
        color="primary"
        :icon="nextAction.status === 'OPEN' ? 'play_arrow' : 'stop'"
        :label="nextAction.label"
        :loading="statusChanging"
        class="session-head__btn"
        @click="$emit('status-action', nextAction)"
      />
      <q-btn
        v-if="qrReady"
        :unelevated="!quiet"
        :outline="quiet"
        no-caps
        color="primary"
        icon="o_qr_code_2"
        label="출석 QR 띄우기"
        class="session-head__btn"
        @click="$emit('qr')"
      />
    </div>
  </section>
</template>

<script>
import { formatDateKst } from '@/utils/datetime';
import {
  formatSessionWhen,
  sessionHeaderState,
  sessionNextAction,
  sessionQrState,
} from '@/utils/session-attendance';

export default {
  name: 'AttendanceSessionHeader',
  props: {
    session: { type: Object, required: true },
    /** 같은 강의의 회차 — 이전·다음 단추용(취소 회차는 건너뛴다) */
    sessions: { type: Array, default: () => [] },
    now: { type: Number, default: () => Date.now() },
    statusChanging: { type: Boolean, default: false },
    /** 명단에서 사람을 고른 동안엔 [선택 출석]이 주 버튼 — QR 은 테두리로 물러난다 */
    quiet: { type: Boolean, default: false },
  },
  /** select(sessionId) · qr · status-action({ status, label }) */
  emits: ['select', 'qr', 'status-action'],
  computed: {
    ordered() {
      return this.sessions
        .filter((item) => item.status !== 'CANCELLED' || item.id === this.session.id)
        .slice()
        .sort((a, b) => (a.sessionNo || 0) - (b.sessionNo || 0));
    },
    position() {
      return this.ordered.findIndex((item) => item.id === this.session.id);
    },
    prevSession() {
      return this.position > 0 ? this.ordered[this.position - 1] : null;
    },
    nextSession() {
      return this.position >= 0 && this.position < this.ordered.length - 1 ? this.ordered[this.position + 1] : null;
    },
    when() {
      return formatSessionWhen(this.session);
    },
    isToday() {
      return Boolean(this.session.startsAt) && formatDateKst(this.session.startsAt) === formatDateKst(new Date(this.now));
    },
    state() {
      return sessionHeaderState(this.session, this.now);
    },
    qrReady() {
      return sessionQrState(this.session, this.now) === 'ready';
    },
    nextAction() {
      return sessionNextAction(this.session, this.now);
    },
  },
};
</script>

<style scoped>
.session-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px 24px;
  padding: 16px 20px;
  border: 1px solid var(--db-border);
  border-radius: 12px;
  background: var(--db-bg-white);
}

.session-head__nav {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1 1 420px;
}

.session-head__arrow {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  color: var(--db-text-body);
}

/* 제목 폭만큼만 — 오른쪽 화살표가 제목 바로 옆에 붙게(‹ 1회차 ›) */
.session-head__body {
  min-width: 0;
  flex: 0 1 auto;
}

.session-head__title {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 10px;
}

.session-head__no {
  font-size: 20px;
  font-weight: 700;
  color: var(--db-text-title);
}

.session-head__when {
  font-size: 17px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--db-text-title);
}

.session-head__today {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--db-accent-soft);
  font-size: 13px;
  font-weight: 700;
  color: var(--db-accent);
}

.session-head__meta {
  margin-top: 2px;
  font-size: 14px;
  color: var(--db-text-sub);
  overflow-wrap: anywhere;
}

.session-head__state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--db-text-body);
}

.session-head__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--db-status-gray);
}

.session-head--qr .session-head__state {
  color: var(--db-status-green-text);
}

.session-head--qr .session-head__dot {
  background: var(--db-status-green);
}

.session-head--start .session-head__state {
  color: var(--db-accent);
}

.session-head--start .session-head__dot {
  background: var(--db-accent);
}

.session-head__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.session-head__btn {
  min-height: 44px;
  font-size: 15px;
}

@media (max-width: 599px) {
  .session-head {
    padding: 12px;
  }

  .session-head__nav {
    flex-basis: 100%;
  }

  .session-head__actions {
    flex-direction: column;
    width: 100%;
  }

  .session-head__btn {
    width: 100%;
    min-height: 48px;
  }
}
</style>
