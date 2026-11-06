/** 생년월일 → YYMMDD (예: 1990-01-15 → 900115) */
export function formatBirthDateCompact(birthDate) {
  if (!birthDate) return '';
  const match = String(birthDate).trim().match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (!match) return '';
  return `${match[1].slice(-2)}${match[2]}${match[3]}`;
}

/**
 * 목록용 휴대폰 — 가운데를 가린다(010-****-1234). 전체 번호는 상세 화면에서만 보인다.
 * 목록 한 장에 수십 명의 번호가 그대로 깔리던 것
 */
export function maskPhone(phone) {
  const digits = String(phone || '').replace(/\D/g, '');
  // 번호가 없으면(빈 값·목록이 채운 '-') '-' — 숫자가 아닌 '-'를 가린 번호('***')로 보였다
  if (!digits) return '-';
  if (digits.length < 9) return '***';
  return `${digits.slice(0, 3)}-****-${digits.slice(-4)}`;
}

/** 사용자 표시명: 홍길동(900115) */
export function formatUserDisplayName(user) {
  const name = user?.name || user?.userName || '';
  const birth = formatBirthDateCompact(user?.birthDate);
  if (name && birth) return `${name}(${birth})`;
  if (name) return name;
  const fallback = user?.id || user?.userId || '';
  return fallback || '-';
}
