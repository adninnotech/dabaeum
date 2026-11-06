import { apiRequest, asPage, unwrapData } from '@/services/api-client';

export async function listMyReviews({ page = 0, size = 20, q } = {}) {
  const payload = await apiRequest('/users/me/reviews', {
    query: { page, size, q },
  });
  return asPage(payload);
}

export async function getMyReviewSummary() {
  const payload = await apiRequest('/users/me/reviews/summary');
  return unwrapData(payload);
}

export async function listCourseReviews(courseId, { page = 0, size = 50 } = {}) {
  const payload = await apiRequest(`/courses/${courseId}/reviews`, {
    query: { page, size },
  });
  return asPage(payload);
}

export async function getCourseReviewSummary(courseId) {
  const payload = await apiRequest(`/courses/${courseId}/reviews/summary`);
  return unwrapData(payload);
}

export async function createCourseReview(courseId, { rating, content }) {
  const payload = await apiRequest(`/courses/${courseId}/reviews`, {
    method: 'POST',
    body: {
      rating,
      content: content?.trim() || null,
    },
  });
  return unwrapData(payload);
}
