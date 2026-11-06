/** 휴대전화 입력 표시용. 숫자만 남기고 11자리까지 3-4-4로 끊는다. */
export function formatPhone(value) {
  const digits = String(value || '')
    .replace(/\D/g, '')
    .slice(0, 11);
  if (digits.length <= 3) return digits;
  if (digits.length <= 7) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
  return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
}

/** 기관 대표번호 입력 표시용 — 지역번호(02·0xx)·대표번호(15xx 등 8자리)를 끊는다. 휴대전화도 받는다.
 * 02-123-4567 · 02-1234-5678 · 053-123-4567 · 010-1234-5678 · 1588-1234 */
export function formatTelephone(value) {
  const digits = String(value || '')
    .replace(/\D/g, '')
    .slice(0, 11);
  if (digits.startsWith('02')) {
    const body = digits.slice(0, 10);
    if (body.length <= 2) return body;
    if (body.length <= 5) return `02-${body.slice(2)}`;
    const mid = body.length === 10 ? 4 : 3;
    return `02-${body.slice(2, 2 + mid)}-${body.slice(2 + mid)}`;
  }
  if (!digits.startsWith('0')) {
    const body = digits.slice(0, 8);
    return body.length <= 4 ? body : `${body.slice(0, 4)}-${body.slice(4)}`;
  }
  if (digits.length <= 3) return digits;
  if (digits.length <= 6) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
  const mid = digits.length === 11 ? 4 : 3;
  return `${digits.slice(0, 3)}-${digits.slice(3, 3 + mid)}-${digits.slice(3 + mid)}`;
}
