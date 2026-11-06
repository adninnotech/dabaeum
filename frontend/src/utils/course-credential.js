/**
 * 이 강좌로 발급된 수료증 id — 없으면 ''.
 * 재발급되면 옛 버전(SUPERSEDED)이 남으므로 ISSUED 를 먼저, 그다음 최신 버전을 고른다.
 * credentials: GET /users/me/credentials 의 data
 */
export function courseCredentialId(credentials, courseId) {
  if (!courseId || !Array.isArray(credentials)) return '';
  const matches = credentials.filter((c) => c.courseId === courseId);
  const sorted = [...matches].sort((a, b) => {
    const issuedDiff = Number(b.status === 'ISSUED') - Number(a.status === 'ISSUED');
    return issuedDiff || (b.versionNo || 0) - (a.versionNo || 0);
  });
  return sorted[0]?.id || '';
}
