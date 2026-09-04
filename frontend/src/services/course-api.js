import { apiRequest, asPage, unwrapData } from '@/services/api-client';

export async function listCourses({ page = 0, size = 20, sort } = {}) {
  const payload = await apiRequest('/courses', {
    query: { page, size, sort },
  });
  return asPage(payload);
}

export async function getCourse(courseId) {
  const payload = await apiRequest(`/courses/${courseId}`);
  return unwrapData(payload);
}

export async function createCourse(body) {
  const payload = await apiRequest('/courses', { method: 'POST', body });
  return unwrapData(payload);
}

export async function updateCourse(courseId, body) {
  const payload = await apiRequest(`/courses/${courseId}`, {
    method: 'PUT',
    body,
  });
  return unwrapData(payload);
}

export async function publishCourse(courseId) {
  const payload = await apiRequest(`/courses/${courseId}/publish`, {
    method: 'POST',
  });
  return unwrapData(payload);
}

export async function closeCourse(courseId) {
  const payload = await apiRequest(`/courses/${courseId}/close`, {
    method: 'POST',
  });
  return unwrapData(payload);
}
