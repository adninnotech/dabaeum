import { apiRequest, asPage, unwrapData } from '@/services/api-client';

export async function listCourseSessions(courseId, { page = 0, size = 50, sort } = {}) {
  const payload = await apiRequest(`/courses/${courseId}/sessions`, {
    query: { page, size, sort },
  });
  return asPage(payload);
}

export async function createCourseSession(courseId, body) {
  const payload = await apiRequest(`/courses/${courseId}/sessions`, {
    method: 'POST',
    body,
  });
  return unwrapData(payload);
}

export async function getCourseSession(sessionId) {
  const payload = await apiRequest(`/sessions/${sessionId}`);
  return unwrapData(payload);
}

/** 회차 재개(관리자 정정) — COMPLETED 회차만. 그 밖엔 409 CORRECTION_NOT_ALLOWED. 사유 필수 */
export async function reopenCourseSession(sessionId, reason) {
  const payload = await apiRequest(`/sessions/${sessionId}/reopen`, { method: 'POST', body: { reason } });
  return unwrapData(payload);
}

export async function updateCourseSession(sessionId, body) {
  const payload = await apiRequest(`/sessions/${sessionId}`, {
    method: 'PUT',
    body,
  });
  return unwrapData(payload);
}

export async function createCourseSessionsBulk(courseId, sessions) {
  const payload = await apiRequest(`/courses/${courseId}/sessions/bulk`, {
    method: 'POST',
    body: { sessions },
  });
  return unwrapData(payload);
}

/** 반복 규칙으로 여러 회차 — body { repeat }. createCourseSessionsBulk 는 회차 배열을 { sessions } 로 감싸 이 용도에 쓸 수 없다 */
export async function createCourseSessionsRepeat(courseId, repeat) {
  const payload = await apiRequest(`/courses/${courseId}/sessions/bulk`, {
    method: 'POST',
    body: { repeat },
  });
  return unwrapData(payload);
}

export async function reorderCourseSessions(courseId, sessionIds) {
  const payload = await apiRequest(`/courses/${courseId}/sessions/order`, {
    method: 'PUT',
    body: { sessionIds },
  });
  return unwrapData(payload);
}
