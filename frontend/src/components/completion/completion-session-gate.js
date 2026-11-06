/* 이수 평가 가능 시점 — 서버(DefaultCompletionApplicationService)는 취소되지 않은 회차 중
 * '종료(COMPLETED)도 아니고 출석 마감 시각(없으면 수업 종료 시각)도 지나지 않은' 회차가 있으면 평가를 막는다.
 * 같은 규칙으로 미리 알려, [출석 평가]를 누른 뒤에야 오류를 보지 않게 한다. */

function closesAtOf(session) {
  const value = session?.attendanceClosesAt || session?.endsAt;
  const time = value ? new Date(value).getTime() : NaN;
  return Number.isNaN(time) ? null : time;
}

/**
 * @returns {{ session: object, closesAt: number } | null} 마지막으로 끝나는 미종료 회차. 없으면 null(평가 가능)
 */
export function pendingSessionGate(sessions, now = Date.now()) {
  const pending = (sessions || [])
    .filter((session) => session.status !== 'CANCELLED' && session.status !== 'COMPLETED')
    .map((session) => ({ session, closesAt: closesAtOf(session) }))
    // 마감 시각을 모르는 회차는 서버도 판단할 수 없어 막는 쪽으로 둔다
    .filter((item) => item.closesAt === null || item.closesAt > now);
  if (!pending.length) return null;
  return pending.reduce((latest, item) =>
    latest.closesAt === null || (item.closesAt !== null && item.closesAt <= latest.closesAt) ? latest : item,
  );
}
