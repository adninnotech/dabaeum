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

export async function updateCourseSession(sessionId, body) {
  const payload = await apiRequest(`/sessions/${sessionId}`, {
    method: 'PUT',
    body,
  });
  return unwrapData(payload);
}
