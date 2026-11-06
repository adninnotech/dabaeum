import { apiRequest, createIdempotencyKey, unwrapData } from '@/services/api-client';

export async function getCompletion(enrollmentId) {
  const payload = await apiRequest(`/enrollments/${enrollmentId}/completion`);
  return unwrapData(payload);
}

export async function evaluateCompletion(enrollmentId, body = {}, idempotencyKey) {
  const payload = await apiRequest(`/enrollments/${enrollmentId}/completion/evaluate`, {
    method: 'POST',
    body,
    idempotencyKey: idempotencyKey || createIdempotencyKey('eval'),
  });
  return unwrapData(payload);
}

export async function confirmCompletion(enrollmentId, idempotencyKey) {
  const payload = await apiRequest(`/enrollments/${enrollmentId}/completion/confirm`, {
    method: 'POST',
    idempotencyKey: idempotencyKey || createIdempotencyKey('confirm'),
  });
  return unwrapData(payload);
}

/** 이수 확정 취소(관리자 정정) — 확정된 이수를 ELIGIBLE 로. 사유 필수, 수료증이 발급됐으면 409 CORRECTION_NOT_ALLOWED */
export async function revertCompletion(enrollmentId, reason) {
  const payload = await apiRequest(`/enrollments/${enrollmentId}/completion/revert`, {
    method: 'POST',
    body: { reason },
  });
  return unwrapData(payload);
}
