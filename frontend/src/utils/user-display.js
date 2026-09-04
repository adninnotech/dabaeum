/** 생년월일 → YYMMDD (예: 1990-01-15 → 900115) */
export function formatBirthDateCompact(birthDate) {
  if (!birthDate) return '';
  const match = String(birthDate).trim().match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (!match) return '';
  return `${match[1].slice(-2)}${match[2]}${match[3]}`;
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
