/**
 * 배지가 왜 폐기됐는지 — 배지 응답 lastEvent 로 가른다(QA 중앙 #54).
 * 수료 확정이 취소돼 조건이 깨지면 서버(BadgeAwarder)가 '수료 확정 취소로 …' 사유로 폐기하고, 조건을 다시 채우면 되살린다.
 * 관리자가 사유를 넣어 폐기한 배지(POST /admin/badges/{id}/revoke)는 조건을 채워도 돌아오지 않는다.
 * lastEvent 에 actorId 가 없어 사유 머리말로 구분한다
 */
const AUTO_REVOKE_PREFIX = '수료 확정 취소로';

/** @returns {{ kind: 'AUTO' | 'MANUAL' | 'UNKNOWN', reason: string }} */
export function revokeCause(badge) {
  const event = badge?.lastEvent;
  if (event?.type !== 'REVOKED') return { kind: 'UNKNOWN', reason: '' };
  const reason = (event.reason || '').trim();
  if (reason.startsWith(AUTO_REVOKE_PREFIX)) return { kind: 'AUTO', reason };
  return { kind: 'MANUAL', reason };
}
