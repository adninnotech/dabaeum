import { apiRequest, unwrapData } from '@/services/api-client';

/* 학습포트폴리오(설계서 §6) — 본인 조회·공유 링크, 공개 포트폴리오 */

export const SHARE_MIN_DAYS = 1;
export const SHARE_MAX_DAYS = 365;
export const SHARE_DEFAULT_DAYS = 30;

export async function getMyPortfolio() {
  const payload = await apiRequest('/users/me/portfolio');
  return unwrapData(payload);
}

/** 공유 링크 생성 → { shareId, token, expiresAt }. token 은 이 응답에서만 보인다.
 * credentialIds·badgeIds 를 빼면 전부. 내 것이 아닌 id 는 422 */
export async function createPortfolioShare({ credentialIds, badgeIds, expiresInDays }) {
  const payload = await apiRequest('/users/me/portfolio/shares', {
    method: 'POST',
    body: { credentialIds, badgeIds, expiresInDays },
  });
  return unwrapData(payload);
}

/** 공유 링크 목록(토큰 없음) — status ACTIVE | EXPIRED | REVOKED */
export async function listPortfolioShares() {
  const payload = await apiRequest('/users/me/portfolio/shares');
  const list = unwrapData(payload);
  return Array.isArray(list) ? list : [];
}

/** 즉시 폐기 */
export async function revokePortfolioShare(shareId) {
  await apiRequest(`/users/me/portfolio/shares/${shareId}`, { method: 'DELETE' });
}

/** 공개 포트폴리오 — { name, credentials, badges }. 만료·폐기·없는 토큰은 404. 열 때마다 조회 수가 오른다 */
export async function getPublicPortfolio(token) {
  const payload = await apiRequest(`/public/portfolios/${encodeURIComponent(token)}`, {
    token: null,
  });
  return unwrapData(payload);
}

/** 공유 페이지 주소 — 프론트가 {origin}/portfolio/{token} 으로 만든다 */
export function portfolioShareUrl(token) {
  return `${window.location.origin}/portfolio/${encodeURIComponent(token)}`;
}
