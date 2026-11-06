import { apiRequest, asPage, unwrapData } from '@/services/api-client';

export async function createInquiry(body) {
  const payload = await apiRequest('/inquiries', { method: 'POST', body });
  return unwrapData(payload);
}

export async function getInquiry(inquiryId) {
  const payload = await apiRequest(`/inquiries/${inquiryId}`);
  return unwrapData(payload);
}

export async function replyInquiry(inquiryId, content) {
  const payload = await apiRequest(`/inquiries/${inquiryId}/reply`, {
    method: 'POST',
    body: { content },
  });
  return unwrapData(payload);
}

export async function listCourseInquiries(courseId, { page = 0, size = 20, type } = {}) {
  const payload = await apiRequest(`/courses/${courseId}/inquiries`, {
    query: { page, size, type },
  });
  return asPage(payload);
}

export async function listMyInquiries({ page = 0, size = 20, status, q } = {}) {
  const payload = await apiRequest('/users/me/inquiries', {
    query: { page, size, status, q },
  });
  return asPage(payload);
}

export async function listInstructorInquiries({ page = 0, size = 20, status, courseId } = {}) {
  const payload = await apiRequest('/instructors/me/inquiries', {
    query: { page, size, status, courseId },
  });
  return asPage(payload);
}

/** 강사 상세용 — 특정 강사 담당 과정 문의 목록 (관리자 전용) */
export async function listInstructorInquiriesOf(
  userId,
  { page = 0, size = 20, status, courseId } = {},
) {
  const payload = await apiRequest(`/instructors/${userId}/inquiries`, {
    query: { page, size, status, courseId },
  });
  return asPage(payload);
}

export async function listInstitutionInquiries(
  institutionId,
  { page = 0, size = 20, status, courseId } = {},
) {
  const payload = await apiRequest(`/institutions/${institutionId}/inquiries`, {
    query: { page, size, status, courseId },
  });
  return asPage(payload);
}
