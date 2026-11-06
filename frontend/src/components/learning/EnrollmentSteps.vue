<template>
  <div class="enroll-steps-wrap">
  <!-- 신청 상태(승인)와 이수 상태(수료)는 서버가 따로 든다 — 예전엔 신청 상태만 보여 수료한 뒤에도 '승인'이었다.
       단계로 이어 보여 지난 단계(✓)와 지금 단계(●)가 한눈에 보이게 한다 -->
  <ol class="enroll-steps" :aria-label="`진행 단계: ${currentLabel}`">
    <li
      v-for="(step, index) in steps"
      :key="step.key"
      class="enroll-steps__item"
      :class="`enroll-steps__item--${step.state}`"
      :aria-current="step.current ? 'step' : undefined"
    >
      <span v-if="index > 0" class="enroll-steps__line" aria-hidden="true" />
      <span class="enroll-steps__dot" aria-hidden="true">
        <q-icon v-if="step.state === 'done'" name="check" size="14px" />
        <q-icon v-else-if="step.state === 'stopped'" name="close" size="14px" />
      </span>
      <span class="enroll-steps__label">{{ step.label }}</span>
      <span v-if="step.date" class="enroll-steps__date">{{ step.date }}</span>
    </li>
  </ol>
  <p v-if="reason" class="enroll-steps__reason">사유: {{ reason }}</p>
  </div>
</template>

<script>
import { formatDateKst } from '@/utils/datetime';

function step(key, label, state, at, current = false) {
  return { key, label, state, date: at ? formatDateKst(at) : '', current };
}

export default {
  name: 'EnrollmentSteps',
  props: {
    /** EnrollmentResponse — status·appliedAt·approvedAt·rejectedAt·cancelledAt·withdrawnAt·사유 */
    enrollment: { type: Object, required: true },
    /** CompletionResponse 또는 null(평가 전·조회 실패) */
    completion: { type: Object, default: null },
  },
  computed: {
    steps() {
      const e = this.enrollment;
      const applied = step('applied', '신청', 'done', e.appliedAt || e.createdAt);
      switch (e.status) {
        case 'APPLIED':
          return [applied, step('approve', '승인 대기', 'current', null, true), step('complete', '수료', 'todo')];
        case 'WAITLISTED': {
          const label = e.waitlistPosition ? `대기 ${e.waitlistPosition}번` : '대기 중';
          return [applied, step('approve', label, 'current', null, true), step('complete', '수료', 'todo')];
        }
        case 'APPROVED':
          return [applied, step('approve', '승인', 'done', e.approvedAt), this.completionStep];
        case 'REJECTED':
          return [applied, step('approve', '거절', 'stopped', e.rejectedAt, true)];
        case 'CANCELLED':
          return [applied, step('cancel', '신청 취소', 'stopped', e.cancelledAt, true)];
        case 'WITHDRAWN':
          return [
            applied,
            step('approve', '승인', 'done', e.approvedAt),
            step('withdraw', '수강 철회', 'stopped', e.withdrawnAt, true),
          ];
        default:
          return [applied];
      }
    },
    // 승인 뒤 마지막 단계 — 수료면 끝(✓), 미이수면 멈춤, 아직이면 수강 중
    completionStep() {
      const c = this.completion;
      if (c?.status === 'COMPLETED') {
        return step('complete', '수료', 'done', c.completedAt || c.confirmedAt, true);
      }
      if (c?.status === 'NOT_COMPLETED') {
        return step('complete', '미이수', 'stopped', c.evaluatedAt, true);
      }
      return step('complete', '수강 중', 'current', null, true);
    },
    currentLabel() {
      return this.steps.find((item) => item.current)?.label || this.steps[this.steps.length - 1]?.label || '';
    },
    reason() {
      const e = this.enrollment;
      if (e.status === 'REJECTED') return e.rejectionReason || '';
      if (e.status === 'CANCELLED') return e.cancellationReason || '';
      if (this.completion?.status === 'NOT_COMPLETED') return this.completion.failureReason || '';
      return '';
    },
  },
};
</script>

<style scoped>
.enroll-steps {
  display: flex;
  margin: 4px 0 0;
  padding: 0;
  list-style: none;
}

.enroll-steps__item {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 0;
  text-align: center;
}

/* 앞 단계와 잇는 선 — 점의 가운데 높이 */
.enroll-steps__line {
  position: absolute;
  top: 11px;
  right: 50%;
  width: calc(100% - 24px);
  margin-right: 12px;
  height: 2px;
  background: var(--db-border);
}

.enroll-steps__item--done .enroll-steps__line,
.enroll-steps__item--current .enroll-steps__line,
.enroll-steps__item--stopped .enroll-steps__line {
  background: var(--db-accent-fill);
}

.enroll-steps__dot {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: 2px solid var(--db-border);
  border-radius: 50%;
  background: var(--db-bg-white);
  color: var(--db-on-color);
}

.enroll-steps__item--done .enroll-steps__dot {
  border-color: var(--db-accent);
  background: var(--db-accent-fill);
}

/* 지금 단계 — 속이 빈 강조 고리 */
.enroll-steps__item--current .enroll-steps__dot {
  border-color: var(--db-accent);
  box-shadow: 0 0 0 4px var(--db-accent-soft);
}

.enroll-steps__item--stopped .enroll-steps__dot {
  border-color: var(--db-status-pink);
  background: var(--db-status-pink);
}

.enroll-steps__label {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.enroll-steps__item--done .enroll-steps__label {
  color: var(--db-text-body);
}

.enroll-steps__item[aria-current='step'] .enroll-steps__label {
  font-weight: 700;
  color: var(--db-text-title);
}

.enroll-steps__item--stopped .enroll-steps__label {
  color: var(--db-status-pink);
}

.enroll-steps__date {
  font-size: 13px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.enroll-steps__reason {
  margin: 8px 0 0;
  font-size: 14px;
  color: var(--db-text-body);
  overflow-wrap: anywhere;
}
</style>
