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
