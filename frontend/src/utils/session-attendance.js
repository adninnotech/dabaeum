/* 출석 QR 을 띄울 수 있는지 — 서버(issueAttendanceQrToken.requireAttendanceOpen)와 같은 규칙.
 * 진행 중(OPEN)이고 지금이 출결 시간 안이어야 한다. 출결 시간 = attendanceOpensAt~ClosesAt, 없으면 수업 시작~종료.
 * 학습 목차 탭·출결 관리 화면이 함께 쓴다(상태만 보고 켜서 누르면 "출결 가능 시간이 아닙니다"가 나던 것) */

export function attendanceWindow(session) {
  return {
    opens: session?.attendanceOpensAt || session?.startsAt || null,
    closes: session?.attendanceClosesAt || session?.endsAt || null,
  };
}

/** 'ready' | 'scheduled' | 'outside' | 'none'(종료·취소 등 QR 대상 아님) */
export function sessionQrState(session, now = Date.now()) {
  if (!session) return 'none';
  if (session.status === 'SCHEDULED') return 'scheduled';
  if (session.status !== 'OPEN') return 'none';
  const { opens, closes } = attendanceWindow(session);
  const from = opens ? new Date(opens).getTime() : NaN;
  const to = closes ? new Date(closes).getTime() : NaN;
  if (!Number.isFinite(from) || !Number.isFinite(to)) return 'outside';
  return now >= from && now < to ? 'ready' : 'outside';
}

const KST_TIME = new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', hour: '2-digit', minute: '2-digit', hour12: false });
const KST_DAY = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' });
const KST_MONTH_DAY = new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', month: 'numeric', day: 'numeric' });

/* '예정'인데 출결 시간이 됐거나 수업이 이미 끝났으면 상태를 바꿔야 한다고 알린다 */
function scheduledHint(session, now) {
  const { opens } = attendanceWindow(session);
  const end = session?.endsAt ? new Date(session.endsAt).getTime() : NaN;
  if (Number.isFinite(end) && now >= end) return '수업 시간 지남';
  const from = opens ? new Date(opens).getTime() : NaN;
  if (Number.isFinite(from) && now >= from) return '진행 중으로 바꾸면 QR';
  return '';
}

/**
 * 표 칸에 바로 적는 짧은 출결 안내 — 툴팁(마우스 전용) 대신 글자로 보인다.
 * 출결 시간 밖이거나 '예정'인데 손봐야 할 때만 글자가 있고, 나머지는 '' (상태 뱃지가 말한다)
 */
export function sessionQrHint(session, now = Date.now()) {
  const state = sessionQrState(session, now);
  if (state === 'scheduled') return scheduledHint(session, now);
  if (state !== 'outside') return '';
  const { opens, closes } = attendanceWindow(session);
  const from = opens ? new Date(opens) : null;
  const to = closes ? new Date(closes) : null;
  if (!from || !to || Number.isNaN(from.getTime()) || Number.isNaN(to.getTime())) return '출결 시간 미정';
  if (now >= to.getTime()) return '출결 마감';
  const sameDay = KST_DAY.format(from) === KST_DAY.format(new Date(now));
  const time = KST_TIME.format(from);
  return sameDay ? `출결 ${time}부터` : `출결 ${KST_MONTH_DAY.format(from)} ${time}부터`;
}

/* 서버 회차 상태 규칙(CourseSessionStatusPolicy.updateTarget)과 같다 — 여기 없는 변경은 409 */
export const SESSION_STATUS_ACTIONS = {
  START: { status: 'OPEN', label: '수업 시작' },
  FINISH: { status: 'COMPLETED', label: '수업 종료' },
  CANCEL: { status: 'CANCELLED', label: '회차 취소' },
};

/** 지금 상태에서 할 수 있는 상태 변경(⋮ 메뉴) */
export function sessionStatusActions(session) {
  if (session?.status === 'SCHEDULED') return [SESSION_STATUS_ACTIONS.START, SESSION_STATUS_ACTIONS.CANCEL];
  if (session?.status === 'OPEN') return [SESSION_STATUS_ACTIONS.FINISH, SESSION_STATUS_ACTIONS.CANCEL];
  return [];
}

/**
 * 표 칸에 꺼내 둘 '지금 할 일' — 출결 시간이 됐는데 아직 예정이면 [수업 시작],
 * 출결 시간이 지났는데 아직 진행이면 [수업 종료]. 그 밖엔 null
 */
export function sessionNextAction(session, now = Date.now()) {
  const { opens, closes } = attendanceWindow(session);
  const from = opens ? new Date(opens).getTime() : NaN;
  const to = closes ? new Date(closes).getTime() : NaN;
  if (session?.status === 'SCHEDULED' && Number.isFinite(from) && now >= from) return SESSION_STATUS_ACTIONS.START;
  if (session?.status === 'OPEN' && Number.isFinite(to) && now >= to) return SESSION_STATUS_ACTIONS.FINISH;
  return null;
}

const MINUTE_MS = 60 * 1000;
// 이 안이면 'n분 남음'까지 알린다(그보다 멀면 시각만)
const COUNTDOWN_WINDOW_MIN = 60;

/**
 * 대시보드 '오늘 수업' 한 줄의 상태 — 지금 강사가 할 일 하나만 고른다.
 * before: 출결 시간 전 · start: 출결 시간인데 예정(시작하고 QR) · qr: 출석 받는 중 · closed: 출결 마감 ·
 * done: 수업 종료 · unknown: 출결 시간 미정. hint 는 화면에 그대로 쓰는 한 줄
 * @returns {{ key: string, hint: string }}
 */
export function todaySessionState(session, now = Date.now()) {
  if (session?.status === 'COMPLETED') return { key: 'done', hint: '수업 종료' };
  const { opens, closes } = attendanceWindow(session);
  const from = opens ? new Date(opens).getTime() : NaN;
  const to = closes ? new Date(closes).getTime() : NaN;
  if (!Number.isFinite(from) || !Number.isFinite(to)) return { key: 'unknown', hint: '출결 시간 미정' };
  if (now < from) {
    const minutes = Math.ceil((from - now) / MINUTE_MS);
    const time = KST_TIME.format(new Date(from));
    const hint = minutes <= COUNTDOWN_WINDOW_MIN ? `출결 ${time}부터 · ${minutes}분 남음` : `출결 ${time}부터`;
    return { key: 'before', hint };
  }
  if (now >= to) return { key: 'closed', hint: '출결 마감' };
  const until = `${KST_TIME.format(new Date(to))}까지`;
  if (session?.status === 'SCHEDULED') return { key: 'start', hint: `출결 시간 · ${until}` };
  return { key: 'qr', hint: `출석 받는 중 · ${until}` };
}

const KST_SESSION_DAY = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  month: 'numeric',
  day: 'numeric',
  weekday: 'short',
});

/** 회차 날짜·시간 짧게 — '10. 13. (월) 10:00~12:00'. 연도·초는 뺀다(선택 칸에서 잘렸다) */
export function formatSessionWhen(session) {
  const start = session?.startsAt ? new Date(session.startsAt) : null;
  // 잘못된 날짜를 Intl 에 넘기면 RangeError 로 화면이 깨진다
  if (!start || Number.isNaN(start.getTime())) return '일시 미정';
  const endRaw = session.endsAt ? new Date(session.endsAt) : null;
  const end = endRaw && !Number.isNaN(endRaw.getTime()) ? endRaw : null;
  const day = KST_SESSION_DAY.format(start);
  const time = KST_TIME.format(start);
  return end ? `${day} ${time}~${KST_TIME.format(end)}` : `${day} ${time}`;
}

/** 출결 화면 머리의 상태 한 줄 — 오늘 회차는 todaySessionState, 다른 날은 날짜가 붙은 sessionQrHint */
export function sessionHeaderState(session, now = Date.now()) {
  if (session?.status === 'CANCELLED') return { key: 'cancelled', hint: '취소된 회차' };
  const state = todaySessionState(session, now);
  if (state.key !== 'before') return state;
  const { opens } = attendanceWindow(session);
  const sameDay = KST_DAY.format(new Date(opens)) === KST_DAY.format(new Date(now));
  // 오늘이면 'n분 남음'까지, 다른 날이면 날짜를 붙인다
  return sameDay ? state : { key: 'before', hint: sessionQrHint({ ...session, status: 'OPEN' }, now) || state.hint };
}
