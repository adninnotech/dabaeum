import { apiRequest, asPage, unwrapData } from '@/services/api-client';

export async function getLearningSummary() {
  const payload = await apiRequest('/users/me/learning-summary');
  return unwrapData(payload);
}

export async function listLearningCourses({ page = 0, size = 20, status, q } = {}) {
  const payload = await apiRequest('/users/me/learning-courses', {
    query: { page, size, status, q },
  });
  return asPage(payload);
}

export async function listMyInterests({ page = 0, size = 20, q } = {}) {
  const payload = await apiRequest('/users/me/interests', {
    query: { page, size, q },
  });
  return asPage(payload);
}

export async function addInterest(courseId) {
  const payload = await apiRequest('/users/me/interests', {
    method: 'POST',
    body: { courseId },
  });
  return unwrapData(payload);
}

export async function removeInterest(interestId) {
  await apiRequest(`/users/me/interests/${interestId}`, { method: 'DELETE' });
}

export { listMyReviews } from '@/services/review-api';
