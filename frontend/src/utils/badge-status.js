/* 학습배지 상태·변동 표시(설계서 §11.4). status ISSUED=유지, REVOKED=취소, 최근 변동이 RENEWED 면 "갱신" */

export const BADGE_EVENT_LABEL = {
  ISSUED: '발급',
  RENEWED: '갱신',
  REVOKED: '취소',
  RESTORED: '복구',
};

export const BADGE_STATUS_FILTER = [
  { label: '유지', value: 'ISSUED' },
  { label: '취소', value: 'REVOKED' },
];

/** { label, color } — color 는 --db-status-* 토큰 */
export function badgeStatusView(badge) {
  if (!badge) return { label: '-', color: 'var(--db-status-gray)' };
  if (badge.status === 'REVOKED') return { label: '취소', color: 'var(--db-status-pink)' };
  if (badge.status === 'PENDING') return { label: '발급 대기', color: 'var(--db-status-orange)' };
  if (badge.status === 'FAILED') return { label: '발급 실패', color: 'var(--db-status-pink)' };
  if (badge.lastEvent?.type === 'RENEWED') return { label: '갱신', color: 'var(--db-status-blue)' };
  return { label: '유지', color: 'var(--db-status-green)' };
}

/** "3단계 · 자람" — 단계 배지가 아니면 '' */
export function tierLabel(item) {
  if (!item?.tierLevel) return '';
  return item.tierName ? `${item.tierLevel}단계 · ${item.tierName}` : `${item.tierLevel}단계`;
}

/** 변동 한 줄 요약 — "2단계 → 3단계", "발급(1단계)" */
export function badgeEventLevelText(event) {
  const { fromLevel, toLevel } = event || {};
  if (fromLevel && toLevel && fromLevel !== toLevel) return `${fromLevel}단계 → ${toLevel}단계`;
  if (toLevel) return `${toLevel}단계`;
  if (fromLevel) return `${fromLevel}단계`;
  return '';
}
