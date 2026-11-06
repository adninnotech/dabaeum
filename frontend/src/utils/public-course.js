import { daysFromToday, formatDateKst } from '@/utils/datetime';

/* 대구 다른 기관 강좌(공공데이터) 표시용. 값은 외부 기관 원문이라 모든 칸이 비어 올 수 있다 */

export const PUBLIC_METHOD_LABEL = {
  OFFLINE: '오프라인',
  ONLINE: '온라인',
  HYBRID: '하이브리드',
  VISIT: '방문',
  OTHER: '기타',
};

// 서버 regionCode — 대구 구·군
export const PUBLIC_REGION_LABEL = {
  11: '중구',
  14: '동구',
  17: '서구',
  20: '남구',
  23: '북구',
  26: '수성구',
  29: '달서구',
  71: '달성군',
  72: '군위군',
};

// 서버 operDay 가 받는 값(이 밖의 값은 400)
export const PUBLIC_OPER_DAYS = ['월', '화', '수', '목', '금', '토', '일'];

// 서버 timeOfDay — startTime 기준. 시작 시각이 없는 강좌는 시간대를 고르면 빠진다
export const PUBLIC_TIME_OPTIONS = [
  { label: '오전', value: 'MORNING', hint: '12시 전에 시작' },
  { label: '오후', value: 'AFTERNOON', hint: '12시~18시 사이에 시작' },
  { label: '저녁', value: 'EVENING', hint: '18시부터 시작' },
];

/** 주소의 days=토,일 → ['토','일'] (모르는 값은 버린다 — 서버가 400 을 낸다) */
export function parseOperDays(value) {
  const parts = typeof value === 'string' ? value.split(',') : [];
  return PUBLIC_OPER_DAYS.filter((day) => parts.includes(day));
}

const DEADLINE_SOON_DAYS = 3;

export function publicMethodLabel(method) {
  return PUBLIC_METHOD_LABEL[method] || '';
}

export function publicRegionLabel(regionCode) {
  return PUBLIC_REGION_LABEL[regionCode] || '';
}

/** 운영기관 링크 — http(s) 만 연다(javascript: 같은 값이 섞여 와도 링크로 만들지 않는다) */
export function safeHomepageUrl(url) {
  const text = typeof url === 'string' ? url.trim() : '';
  return /^https?:\/\/\S+$/i.test(text) ? text : '';
}

export function publicCostLabel(course) {
  if (course?.free === true || course?.costAmount === 0) return '무료';
  if (Number.isFinite(course?.costAmount)) return `${course.costAmount.toLocaleString('ko-KR')}원`;
  return course?.free === false ? '유료' : '';
}

function rangeLabel(start, end) {
  if (!start && !end) return '';
  return `${formatDateKst(start, '')} ~ ${formatDateKst(end, '')}`.trim();
}

export function publicPeriodLabel(course) {
  return rangeLabel(course?.edcStartDate, course?.edcEndDate);
}

export function publicReceiptLabel(course) {
  return rangeLabel(course?.receiptStartDate, course?.receiptEndDate);
}

const ALL_DAY = { start: '00:00', end: '23:59' };

/**
 * 요일 묶음 — 이어진 3일 이상은 '월~토', 월~금은 '평일', 7일은 '매일'. 그 밖엔 '월·수'.
 * '월·화·수·목·금·토·일' 이 카드 한 줄을 다 차지했다
 */
function daysLabel(days) {
  const index = days.map((day) => PUBLIC_OPER_DAYS.indexOf(day)).filter((i) => i >= 0);
  const sorted = [...new Set(index)].sort((a, b) => a - b);
  if (sorted.length === 7) return '매일';
  const consecutive = sorted.length >= 3 && sorted.every((value, i) => i === 0 || value === sorted[i - 1] + 1);
  if (!consecutive) return days.join('·');
  if (sorted[0] === 0 && sorted[sorted.length - 1] === 4) return '평일';
  return `${PUBLIC_OPER_DAYS[sorted[0]]}~${PUBLIC_OPER_DAYS[sorted[sorted.length - 1]]}`;
}

/** '월·수 10:00~12:00' · '평일 09:00~18:00' · '매일 언제나' — 요일·시간 중 있는 것만 */
export function publicScheduleLabel(course) {
  const days = Array.isArray(course?.operDays) ? daysLabel(course.operDays.filter(Boolean)) : '';
  const allDay = course?.startTime === ALL_DAY.start && course?.endTime === ALL_DAY.end;
  const time = allDay
    ? '언제나'
    : course?.startTime || course?.endTime ? `${course?.startTime || ''}~${course?.endTime || ''}` : '';
  return [days, time].filter(Boolean).join(' ');
}

/**
 * 접수 상태 — 접수 마감일이 있을 때만 판단한다(없으면 운영기관에서 확인).
 * { text, tone: 'open' | 'soon' | 'closed' | 'unknown' }
 */
export function publicReceiptState(course) {
  const startLeft = daysFromToday(course?.receiptStartDate);
  const endLeft = daysFromToday(course?.receiptEndDate);
  if (endLeft === null) return { text: '접수 기간은 운영기관에서 확인', tone: 'unknown' };
  if (endLeft < 0) return { text: '접수 마감', tone: 'closed' };
  if (startLeft !== null && startLeft > 0) return { text: `접수 예정 · ${formatDateKst(course.receiptStartDate)}부터`, tone: 'unknown' };
  if (endLeft === 0) return { text: '접수 중 · 오늘 마감', tone: 'soon' };
  // 마감이 멀면 '46일 뒤' 보다 날짜가 읽기 쉽다 — 임박하면 'n일 남음'(강좌 카드 '모집중 · 3일 남음' 과 같은 말, D-day 줄임말은 피한다)
  if (endLeft <= DEADLINE_SOON_DAYS) return { text: `접수 중 · ${endLeft}일 남음`, tone: 'soon' };
  const end = String(course.receiptEndDate).slice(0, 10);
  return { text: `접수 중 · ${end.slice(5, 7)}.${end.slice(8, 10)} 마감`, tone: 'open' };
}
