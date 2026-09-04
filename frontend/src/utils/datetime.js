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
