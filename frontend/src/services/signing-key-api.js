import { apiRequest, createIdempotencyKey, unwrapData } from '@/services/api-client';

/** 기관 서명키 현황 — { institutionId, keys[], operations[], openApprovals[] } (PLATFORM_ADMIN) */
export async function getInstitutionSigningKeys(institutionId) {
  const payload = await apiRequest(`/institutions/${institutionId}/signing-keys`);
  return unwrapData(payload);
}

/** 서명키 수동 생성 — 자동 생성이 멈췄을 때(키 없음·ERROR)만 쓴다. 멱등키는 본문에 실어 클릭마다 새로 만든다 */
export async function createInstitutionSigningKeys(institutionId, reason) {
  const body = { idempotencyKey: createIdempotencyKey('sigkey') };
  if (reason) body.reason = reason;
  const payload = await apiRequest(`/institutions/${institutionId}/signing-keys`, {
    method: 'POST',
    body,
  });
  return unwrapData(payload);
}
