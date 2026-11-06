const KST = 'Asia/Seoul';

export function parseDateValue(value) {
  if (value == null || value === '') return null;
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value;
  const str = String(value).trim();
  if (!str || str === '-') return null;
  if (/^\d{4}-\d{2}-\d{2}$/.test(str)) {
    return new Date(`${str}T12:00:00+09:00`);
  }
  const date = new Date(str);
  return Number.isNaN(date.getTime()) ? null : date;
}

function formatParts(date, includeTime) {
  const options = {
    timeZone: KST,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    ...(includeTime
      ? { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }
      : {}),
  };
  const parts = new Intl.DateTimeFormat('en-US', options).formatToParts(date);
  const get = (type) => parts.find((part) => part.type === type)?.value || '';
  const dateText = `${get('year')}.${get('month')}.${get('day')}`;
  if (!includeTime) return dateText;
  return `${dateText} ${get('hour')}:${get('minute')}:${get('second')}`;
}

/** 날짜: 2026.08.14 */
export function formatDateKst(value, fallback = '-') {
  const date = parseDateValue(value);
  if (!date) return fallback;
  return formatParts(date, false);
}

/** 날짜·시간: 2026.08.14 03:14:16 (KST) */
export function formatDateTimeKst(value, fallback = '-') {
  const date = parseDateValue(value);
  if (!date) return fallback;
  const str = String(value).trim();
  if (/^\d{4}-\d{2}-\d{2}$/.test(str)) {
    return formatParts(date, false);
  }
  return formatParts(date, true);
}

/** 날짜·분: 2026.08.14 03:14 — formatParts가 전부 2자리 고정폭이라 초 3글자만 떼면 된다 */
export function formatDateMinuteKst(value, fallback = '-') {
  return formatDateTimeKst(value, fallback).slice(0, 16);
}

/** 테이블 row에서 첫 번째 유효 datetime 필드 포맷 */
export function formatDateTimeFields(row, ...keys) {
  for (const key of keys) {
    const value = row?.[key];
    if (value) return formatDateTimeKst(value);
  }
  return '-';
}

/** 기간(날짜): 2026.08.01 ~ 2026.08.31 */
export function formatDatePeriodKst(start, end, fallback = '-') {
  const startText = formatDateKst(start, fallback);
  const endText = formatDateKst(end, fallback);
  if (startText === fallback && endText === fallback) return fallback;
  if (endText === fallback) return startText;
  return `${startText} ~ ${endText}`;
}

/** 회차 일정: 2026.08.14 10:00:00 ~ 12:00:00 */
export function formatDateRangeKst(start, end, fallback = '-') {
  if (!start) return fallback;
  const startText = formatDateTimeKst(start, fallback);
  if (!end) return startText;
  const endDate = parseDateValue(end);
  if (!endDate) return startText;
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: KST,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).formatToParts(endDate);
  const get = (type) => parts.find((part) => part.type === type)?.value || '';
  return `${startText} ~ ${get('hour')}:${get('minute')}:${get('second')}`;
}

/** Quasar q-table column format */
export const qFormatDateKst = (value) => formatDateKst(value);
export const qFormatDateTimeKst = (value) => formatDateTimeKst(value);

const weekdayFormatter = new Intl.DateTimeFormat('ko-KR', { timeZone: KST, weekday: 'short' });
const timeFormatter = new Intl.DateTimeFormat('en-US', {
  timeZone: KST,
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

/** 날짜+요일: 2026.09.19(토) */
export function formatDateWeekdayKst(value, fallback = '-') {
  const date = parseDateValue(value);
  if (!date) return fallback;
  return `${formatParts(date, false)}(${weekdayFormatter.format(date)})`;
}

/** 시각만: 14:00 (KST) */
export function formatTimeKst(value, fallback = '-') {
  const date = parseDateValue(value);
  return date ? timeFormatter.format(date) : fallback;
}

/** 회차 일정: 2026.09.19(토) 14:00~16:00 */
export function formatSessionScheduleKst(startsAt, endsAt, fallback = '-') {
  const start = parseDateValue(startsAt);
  if (!start) return fallback;
  const head = `${formatDateWeekdayKst(startsAt, fallback)} ${timeFormatter.format(start)}`;
  const end = parseDateValue(endsAt);
  return end ? `${head}~${timeFormatter.format(end)}` : head;
}

const DAY_MS = 24 * 60 * 60 * 1000;

function midnight(dateString) {
  const str = String(dateString || '').slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(str)) return null;
  const date = new Date(`${str}T00:00:00`);
  return Number.isNaN(date.getTime()) ? null : date;
}

/** 자정 기준 일수 차이 (to - from). 값이 잘못되면 null */
export function daysBetween(from, to) {
  const start = midnight(from);
  const end = midnight(to);
  if (!start || !end) return null;
  return Math.round((end - start) / DAY_MS);
}

/** 오늘 자정 기준 남은 일수 — 오늘이면 0, 지났으면 음수 */
export function daysFromToday(value) {
  const target = midnight(value);
  if (!target) return null;
  const now = new Date();
  return Math.round((target - new Date(now.getFullYear(), now.getMonth(), now.getDate())) / DAY_MS);
}

/** 오늘 날짜 (로컬 기준 YYYY-MM-DD) */
/** 'YYYY-MM-DD' 가 실제 달력에 있는 날인지(2026-02-30 같은 값 거름) */
export function isValidDateString(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(value || ''))) return false;
  const [y, m, d] = value.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  return date.getUTCFullYear() === y && date.getUTCMonth() === m - 1 && date.getUTCDate() === d;
}

export function todayString() {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

/** 만 나이 → 인기 강좌 연령대 10·20·…·70 (10대 이하는 10, 70대 이상은 70). 생년월일이 없거나 이상하면 null */
export function ageBandFromBirthDate(birthDate, today = new Date()) {
  const born = parseDateValue(birthDate);
  if (!born) return null;
  let age = today.getFullYear() - born.getFullYear();
  const beforeBirthday =
    today.getMonth() < born.getMonth() ||
    (today.getMonth() === born.getMonth() && today.getDate() < born.getDate());
  if (beforeBirthday) age -= 1;
  if (age < 0) return null;
  return String(Math.min(70, Math.max(10, Math.floor(age / 10) * 10)));
}

/** ISO date-time → `<input type="datetime-local">` 값(YYYY-MM-DDTHH:mm, KST). 잘못된 값이면 '' */
export function toDateTimeLocalKst(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    // hour12:false 는 자정을 "24"로 내는 브라우저가 있다 — h23 으로 고정
    hourCycle: 'h23',
  }).formatToParts(date);
  const get = (type) => parts.find((part) => part.type === type)?.value || '';
  return `${get('year')}-${get('month')}-${get('day')}T${get('hour')}:${get('minute')}`;
}

/** `<input type="datetime-local">` 값(YYYY-MM-DDTHH:mm) → ISO date-time(KST, +09:00). 비면 null */
export function fromDateTimeLocalKst(value) {
  return value ? `${value}:00+09:00` : null;
}
