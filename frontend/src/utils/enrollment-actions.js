/**
 * 수강 신청 취소·철회 확인 창 문구 — 목록·상세가 같이 쓴다.
 * 백엔드 규칙: 학습자 본인 철회는 승인(APPROVED)·이수 평가 전·출석 이력 없음·수업 시작 24시간 전까지.
 * 다시 신청하려면 강좌가 모집중(RECRUITING)이어야 해서, 모집이 끝난 강좌를 철회하면 되돌릴 길이 없다 — 그 경우를 따로 강조한다.
 */
import { formatDateMinuteKst } from '@/utils/datetime';

/** 본인 철회 마감 = 수업 시작 24시간 전 (백엔드 dabaeum.enrollment.withdrawal.self-cutoff) */
export const SELF_WITHDRAW_CUTOFF_MS = 24 * 60 * 60 * 1000;

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (ch) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  })[ch]);
}

function reapplyLine(courseStatus) {
  if (courseStatus === 'RECRUITING') return '모집 기간 안에는 다시 신청할 수 있습니다.';
  return '<strong style="color: var(--db-status-pink)">모집이 끝난 강좌라 다시 신청할 수 없습니다.</strong>';
}

/** confirmDialog 옵션(html) — 강좌 이름은 이스케이프한다 */
export function cancelConfirmOptions({ courseTitle, courseStatus }) {
  return {
    title: '신청 취소',
    message: `<b>${escapeHtml(courseTitle || '이 강좌')}</b> 수강 신청을 취소합니다.<br>${reapplyLine(courseStatus)}`,
    html: true,
    ok: '신청 취소',
    color: 'negative',
    cancel: '닫기',
  };
}

export function withdrawConfirmOptions({ courseTitle, courseStatus, deadline = null }) {
  const deadlineLine = deadline ? `<br>직접 철회는 ${escapeHtml(formatDateMinuteKst(deadline.toISOString()))}까지 할 수 있습니다.` : '';
  return {
    title: '수강 철회',
    message:
      `<b>${escapeHtml(courseTitle || '이 강좌')}</b> 수강을 철회합니다. ` +
      `나의 강좌에서 빠지고 출결·이수 기록이 더 쌓이지 않습니다.<br>${reapplyLine(courseStatus)}${deadlineLine}`,
    html: true,
    ok: '수강 철회',
    color: 'negative',
    cancel: '닫기',
  };
}

/**
 * 출석(출석·지각)한 회차가 있나 — 출결 요약(GET /enrollments/{id}/attendance-summary) 기준.
 * 요약은 시작 전 회차라도 출결 기록이 있으면 센다(백엔드 2회차, QA ENR-13·ATT) — 진도로 보충하던 임시 판단은 걷었다
 */
export function hasAttended(summary) {
  return Number(summary?.presentCount || 0) + Number(summary?.lateCount || 0) > 0;
}

/**
 * 본인 철회 마감(Date) — 취소되지 않은 첫 회차 시작, 회차가 없으면 startDate 의 서울 00:00, 에서 24시간 전.
 * 시작 시각을 알 수 없으면 null(화면은 막지 않고 서버 판단에 맡긴다)
 */
export function selfWithdrawDeadline({ sessions = [], startDate = '' } = {}) {
  const starts = sessions
    .filter((session) => session?.status !== 'CANCELLED' && session?.startsAt)
    .map((session) => new Date(session.startsAt).getTime())
    .filter(Number.isFinite);
  const dateStart = /^\d{4}-\d{2}-\d{2}/.test(String(startDate || ''))
    ? new Date(`${String(startDate).slice(0, 10)}T00:00:00+09:00`).getTime()
    : NaN;
  const classStart = starts.length ? Math.min(...starts) : dateStart;
  return Number.isFinite(classStart) ? new Date(classStart - SELF_WITHDRAW_CUTOFF_MS) : null;
}

function deadlinePassed(deadline, now) {
  return deadline instanceof Date && now >= deadline.getTime();
}

/** 이수가 평가(ELIGIBLE)·확정(COMPLETED)됐거나 강좌가 끝났으면 누구도 철회할 수 없다 */
function completionLocked({ completionStatus, courseStatus }) {
  return completionStatus === 'COMPLETED' || completionStatus === 'ELIGIBLE' || courseStatus === 'COMPLETED';
}

/**
 * 철회할 수 있나 — 승인 상태, 이수 평가 전, 출석한 회차 없음, 본인 철회 마감 전.
 * 수료 뒤 철회하면 나의 강좌에선 빠지는데 수료증은 남는 어긋난 상태가 됐다.
 * attendanceKnown=false(출결 요약을 못 받음)면 확인할 수 없으니 막는다
 */
export function canWithdrawEnrollment({ status, courseStatus, completionStatus, summary, attendanceKnown = true, deadline = null, now = Date.now() }) {
  if (status !== 'APPROVED' || completionLocked({ completionStatus, courseStatus })) return false;
  return attendanceKnown && !hasAttended(summary) && !deadlinePassed(deadline, now);
}

const ASK_INSTITUTION = '운영기관에 문의해 주세요';

/**
 * 승인된 수강인데 철회를 막은 이유 — { title, detail } 두 줄(무슨 일인지 먼저, 할 일은 연하게).
 * 한 덩어리 문장이던 때 폰에서 단추 자리를 3~4줄 차지했다. 막지 않았거나 승인 전·수료·강좌 종료면 null
 */
export function withdrawBlockedNotice({ status, courseStatus, completionStatus, summary, attendanceKnown = true, deadline = null, now = Date.now() }) {
  if (status !== 'APPROVED' || completionStatus === 'COMPLETED' || courseStatus === 'COMPLETED') return null;
  if (completionStatus === 'ELIGIBLE') return { title: '이수 평가가 끝난 수강이에요', detail: `철회할 수 없어요 · ${ASK_INSTITUTION}` };
  if (!attendanceKnown) return { title: '출석 기록을 확인하지 못했어요', detail: '잠시 뒤 새로고침해 주세요' };
  if (hasAttended(summary)) return { title: '출석한 수업이 있어요', detail: `직접 철회할 수 없어요 · ${ASK_INSTITUTION}` };
  if (deadlinePassed(deadline, now)) {
    // '2026.10.20 14:00' 에서 연도를 뺀다 — 올해 수업이라 줄만 길어졌다
    return { title: '철회 마감이 지났어요', detail: `${formatDateMinuteKst(deadline.toISOString()).slice(5)}까지 · ${ASK_INSTITUTION}` };
  }
  return null;
}

/** 알림(notify) 한 줄용 — withdrawBlockedNotice 를 이어 붙인다. 막지 않았으면 '' */
export function withdrawBlockedReason(rule) {
  const notice = withdrawBlockedNotice(rule);
  return notice ? `${notice.title}. ${notice.detail}` : '';
}
