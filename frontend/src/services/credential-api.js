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

export async function listMyCredentials({ page = 0, size = 20, sort } = {}) {
  const payload = await apiRequest('/users/me/credentials', {
    query: { page, size, sort },
  });
  return asPage(payload);
}

export async function listUserCredentials(userId, { page = 0, size = 20, sort } = {}) {
  const payload = await apiRequest(`/users/${userId}/credentials`, {
    query: { page, size, sort },
  });
  return asPage(payload);
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

export async function verifyCredential(body) {
  const payload = await apiRequest('/credentials/verify', {
    method: 'POST',
    body,
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

export async function listUserBadges(userId, { page = 0, size = 20, sort } = {}) {
  const payload = await apiRequest(`/users/${userId}/badges`, {
    query: { page, size, sort },
  });
  return asPage(payload);
}

export async function getBadge(badgeId) {
  const payload = await apiRequest(`/badges/${badgeId}`);
  return unwrapData(payload);
}
