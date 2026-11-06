/* 회차 일괄 생성(POST /courses/{id}/sessions/bulk 의 repeat) — 화면은 '시작일~종료일·요일'로 받고,
 * 계약은 firstDate·daysOfWeek·count 라 그 사이 수업일 수를 세어 count 로 보낸다 */

/** 월요일부터. value 는 계약 enum(CourseSessionRepeatPayload.daysOfWeek) */
export const WEEKDAY_OPTIONS = [
  { label: '월', value: 'MONDAY' },
  { label: '화', value: 'TUESDAY' },
  { label: '수', value: 'WEDNESDAY' },
  { label: '목', value: 'THURSDAY' },
  { label: '금', value: 'FRIDAY' },
  { label: '토', value: 'SATURDAY' },
  { label: '일', value: 'SUNDAY' },
];

// Date#getDay() → enum
const DAY_BY_INDEX = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

/** 계약 상한 */
export const REPEAT_COUNT_MAX = 200;

function toLocalNoon(dateText) {
  const [y, m, d] = String(dateText || '').split('-').map(Number);
  if (!y || !m || !d) return null;
  return new Date(y, m - 1, d, 12);
}

function toDateText(date) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** 시작일~종료일(둘 다 포함) 중 고른 요일인 날짜들 'YYYY-MM-DD'[]. 입력이 모자라면 [] */
export function repeatDates(startDate, endDate, daysOfWeek) {
  const start = toLocalNoon(startDate);
  const end = toLocalNoon(endDate);
  if (!start || !end || start > end || !daysOfWeek?.length) return [];
  const picked = new Set(daysOfWeek);
  const dates = [];
  for (let day = new Date(start); day <= end && dates.length <= REPEAT_COUNT_MAX; day.setDate(day.getDate() + 1)) {
    if (picked.has(DAY_BY_INDEX[day.getDay()])) dates.push(toDateText(day));
  }
  return dates;
}
