/* 수강 신청 승인·거절 화면 공통 — 강사 '수강 신청 관리'와 강의 상세 '수강 신청 현황' 탭이 같은 말을 쓴다.
 * 한쪽은 '승인', 다른 쪽은 '수강 확정'으로 같은 상태를 다르게 불렀다 */
import { statusLabel } from '@/utils/status';

/**
 * 신청 화면 전용 라벨 — 전역 STATUS_LABEL 은 그대로 둔다.
 * ⚠ APPLIED 는 넣지 않는다: "신청"(중립) 그대로 보인다. 정원이 찬 뒤에도 재평가되지 않고 남는 상태라
 * "정원 안에 들었다"는 뉘앙스를 붙이지 않는다.
 */
const BADGE_LABELS = {
  APPROVED: '수강 확정',
  // 거절은 운영자가 내린 결정이라 신청자 본인의 취소와 구분해 보여 준다
  REJECTED: '거절',
  CANCELLED: '신청 취소',
  // 승인 뒤 철회 — 승인 전 취소(CANCELLED)와 같은 말을 써 기관 목록에서 헷갈렸다(QA ENR-16)
  WITHDRAWN: '수강 철회',
};

/** 승인·거절을 할 수 있는 상태 */
export const DECIDABLE_STATUSES = ['APPLIED', 'WAITLISTED'];

/** 한 번에 처리할 수 있는 최대 건수(서버 bulk 상한) */
export const BULK_DECISION_MAX = 100;

/** 대기는 연한 뱃지 — '신청'(바로 처리)과 같은 주황 꽉 찬 뱃지라 글자를 읽기 전엔 구분이 안 됐다 */
export function isWaitlistBadgeSoft(row) {
  return row?.status === 'WAITLISTED';
}

/** 대기 순번이 있으면 `대기 N번`, 없으면 신청 화면 라벨 → 전역 라벨 */
export function enrollmentBadgeLabel(row) {
  if (row?.status === 'WAITLISTED' && row.waitlistPosition != null)
    return `대기 ${row.waitlistPosition}번`;
  return BADGE_LABELS[row?.status] || statusLabel(row?.status);
}

/** 과정 취소 사유는 승인 전 취소와 과정 취소로 철회된 수강에 표시한다. */
export function enrollmentCancellationReason(row) {
  return ['CANCELLED', 'WITHDRAWN'].includes(row?.status) ? row.cancellationReason || '' : '';
}

/** 한 건 처리 뒤 알림 — 기본 필터(처리 대기)에선 처리한 행이 사라져 결과를 알 길이 없었다 */
export function notifyDecision($q, name, decision) {
  const who = name && name !== '-' ? `${name} 님` : '신청';
  const verb = decision === 'REJECT' ? '거절했습니다' : '승인했습니다';
  $q.notify({ type: 'positive', message: `${who} 신청을 ${verb}.`, position: 'top' });
}

/** bulkDecideEnrollments 결과 알림 — 실패 사유는 건마다 달라(정원 초과·이미 처리됨) 그대로 보인다 */
export function notifyBulkDecision($q, result, decision) {
  const ok = result?.succeeded?.length || 0;
  const failed = result?.failed || [];
  const verb = decision === 'REJECT' ? '거절' : '승인';
  if (!failed.length) {
    $q.notify({ type: 'positive', message: `${ok}건을 ${verb}했습니다.`, position: 'top' });
    return;
  }
  $q.notify({
    type: 'warning',
    message: `${ok}건 ${verb}, ${failed.length}건 실패했습니다.`,
    caption: failed.map((f) => f.reason).join('\n'),
    multiLine: true,
    timeout: 0,
    actions: [{ label: '닫기', color: 'white' }],
    position: 'top',
  });
}
