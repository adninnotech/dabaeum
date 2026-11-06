import { todayString } from '@/utils/datetime';

/* 수료증 발급·재발급 창의 "유효기간 (선택)" 칸 공통 규칙.
 * 서버에는 기본 정책이 없다 — 비우면 validUntil=null 로 저장되어 만료가 없다.
 * 날짜를 넣으면 그날 23:59:59(KST)에 만료된다. 오늘을 넣으면 발급 당일 밤 만료되어 실수가 크다(수료증 QA 2026-10-01). */

export const VALID_UNTIL_HINT = '비우면 만료 없이 발급됩니다. 날짜를 넣으면 그날 자정에 만료됩니다.';

/** 날짜 입력(YYYY-MM-DD) 검사 — 비었으면 통과, 오늘·지난 날짜면 안내 문구 */
export function validUntilError(date) {
  if (!date) return '';
  return date <= todayString() ? '유효기간은 내일 이후 날짜로 넣어 주세요. 비우면 만료 없이 발급됩니다.' : '';
}

/** 날짜 선택기 최솟값(YYYY-MM-DD) — 내일 */
export function minValidUntilDate() {
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const month = String(tomorrow.getMonth() + 1).padStart(2, '0');
  const day = String(tomorrow.getDate()).padStart(2, '0');
  return `${tomorrow.getFullYear()}-${month}-${day}`;
}

/** 날짜 입력 → 그날 끝(KST). 비우면 null — 만료 없음 */
export function endOfDayKst(date) {
  return date ? `${date}T23:59:59+09:00` : null;
}

/**
 * 수료증 유효기간 — 서버 상태는 발급완료(ISSUED)로 남아도 validUntil 이 지나면 공개 검증은 '유효기간 지남'이다.
 * 본인 화면만 초록 '발급완료'로 보여 유효한 것처럼 읽혔다 — 화면에서는 날짜로 판단해 '기간 만료'로 보인다.
 */
export function isCredentialExpired(credential, now = new Date()) {
  if (credential?.status !== 'ISSUED' || !credential.validUntil) return false;
  const until = new Date(credential.validUntil);
  return !Number.isNaN(until.getTime()) && until < now;
}

/** StatusBadge 에 넘길 { status, label } — 만료면 회색 '기간 만료' */
export function credentialBadge(credential) {
  return isCredentialExpired(credential)
    ? { status: 'EXPIRED', label: '기간 만료' }
    : { status: credential?.status, label: undefined };
}
