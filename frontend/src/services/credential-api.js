import { apiRequest, asPage, createIdempotencyKey, unwrapData } from '@/services/api-client';

export async function issueCredential(completionId, body = {}, idempotencyKey) {
  const payload = await apiRequest(`/completions/${completionId}/credentials`, {
    method: 'POST',
    body,
    idempotencyKey: idempotencyKey || createIdempotencyKey('cred'),
  });
  return unwrapData(payload);
}

export async function getCredential(credentialId) {
  const payload = await apiRequest(`/credentials/${credentialId}`);
  return unwrapData(payload);
}

export async function listMyCredentials({ page = 0, size = 20 } = {}) {
  const payload = await apiRequest('/users/me/credentials', {
    query: { page, size },
  });
  return asPage(payload);
}

export async function listUserCredentials(userId, { page = 0, size = 20 } = {}) {
  const payload = await apiRequest(`/users/${userId}/credentials`, {
    query: { page, size },
  });
  return asPage(payload);
}

// 계약상 size 최대 100. 한 사람 수료증이 이보다 많을 일은 드물지만 무한 반복은 막는다
const USER_CREDENTIAL_PAGE_SIZE = 100;
const USER_CREDENTIAL_MAX_PAGES = 5;

/** 사용자 수료증 전부(GET /users/{userId}/credentials 를 끝까지). 과정·상태로 거르는 조회 API 가 없어 화면에서 거른다 */
export async function listAllUserCredentials(userId) {
  const pages = [];
  for (let page = 0; page < USER_CREDENTIAL_MAX_PAGES; page += 1) {
    const payload = await apiRequest(`/users/${userId}/credentials`, {
      query: { page, size: USER_CREDENTIAL_PAGE_SIZE },
    });
    const result = asPage(payload);
    const rows = Array.isArray(result.data) ? result.data : [];
    pages.push(rows);
    const totalPages = result.page?.totalPages;
    if (rows.length < USER_CREDENTIAL_PAGE_SIZE || (totalPages != null && page + 1 >= totalPages)) break;
  }
  return pages.flat();
}

export async function downloadCredentialDocument(credentialId) {
  const payload = await apiRequest(`/credentials/${credentialId}/document`, {
    headers: { Accept: 'application/vc+jwt, application/jose, text/plain, application/json' },
  });
  if (typeof payload === 'string') return payload;
  if (payload && typeof payload === 'object' && payload.data) {
    return typeof payload.data === 'string' ? payload.data : JSON.stringify(payload.data);
  }
  return JSON.stringify(payload);
}

export async function getVcContext() {
  return apiRequest('/vc/contexts/lifelong-education/v1', { token: null });
}

export async function getVcVocabulary() {
  return apiRequest('/vc/vocabulary/lifelong-education/v1', { token: null });
}

export async function getVcIssuer(institutionId) {
  return apiRequest(`/vc/issuers/${institutionId}`, { token: null });
}

export async function getPublicCredentialStatus(credentialNo) {
  return apiRequest(`/vc/status/${encodeURIComponent(credentialNo)}`, { token: null });
}

export async function revokeCredential(credentialId, body, idempotencyKey) {
  const payload = await apiRequest(`/credentials/${credentialId}/revoke`, {
    method: 'POST',
    body,
    idempotencyKey: idempotencyKey || createIdempotencyKey('revoke'),
  });
  return unwrapData(payload);
}

export async function reissueCredential(credentialId, body, idempotencyKey) {
  const payload = await apiRequest(`/credentials/${credentialId}/reissue`, {
    method: 'POST',
    body,
    idempotencyKey: idempotencyKey || createIdempotencyKey('reissue'),
  });
  return unwrapData(payload);
}

/** 로그인 없이 수료증 번호로 공개 검증 — 검증 이력에는 요청자 '개인'으로 남는다. type 은 QR(링크로 들어옴)·API(직접 입력) */
export async function verifyCredentialPublic(credentialNo, verificationType = 'API') {
  const payload = await apiRequest('/credentials/verify', {
    method: 'POST',
    body: { credentialNo, verificationType, requesterType: 'INDIVIDUAL' },
    token: null,
  });
  return unwrapData(payload);
}

export async function listCredentialVerifications(credentialId, { page = 0, size = 20 } = {}) {
  const payload = await apiRequest(`/credentials/${credentialId}/verifications`, {
    query: { page, size },
  });
  return asPage(payload);
}

export async function issueLearningBadge(credentialId, body, idempotencyKey) {
  const payload = await apiRequest(`/credentials/${credentialId}/badges`, {
    method: 'POST',
    body,
    idempotencyKey: idempotencyKey || createIdempotencyKey('badge'),
  });
  return unwrapData(payload);
}

export async function listUserBadges(userId, { page = 0, size = 20 } = {}) {
  const payload = await apiRequest(`/users/${userId}/badges`, {
    query: { page, size },
  });
  return asPage(payload);
}

export async function getBadge(badgeId) {
  const payload = await apiRequest(`/badges/${badgeId}`);
  return unwrapData(payload);
}

/** 변동 이력(발급·갱신·취소·복구 + 사유), 최근 것부터. 권한은 배지 상세와 같다(본인 또는 관리자) */
export async function listBadgeHistory(badgeId) {
  const payload = await apiRequest(`/badges/${badgeId}/history`);
  const list = unwrapData(payload);
  return Array.isArray(list) ? list : [];
}

/** 관리자 배지 폐기 — 사유 1~200자. 이미 폐기면 409 BADGE_CONFLICT. 관리자가 폐기한 배지는 자동 복구되지 않는다 */
export async function revokeLearningBadge(badgeId, reason) {
  const payload = await apiRequest(`/admin/badges/${badgeId}/revoke`, { method: 'POST', body: { reason } });
  return unwrapData(payload);
}

/** NFT 학습배지 현황(관리자) — q 는 이름 부분일치. 접속기록에 남는다 */
export async function searchAdminBadges({ page = 0, size = 20, definitionId, status, tierLevel, q } = {}) {
  const payload = await apiRequest('/admin/badges', {
    query: { page, size, definitionId, status, tierLevel, q },
  });
  return asPage(payload);
}
