import { apiRequest, asPage, unwrapData } from '@/services/api-client';

export async function applyInstructor(institutionId, body = {}) {
  const payload = await apiRequest(`/institutions/${institutionId}/instructor-applications`, {
    method: 'POST',
    body,
  });
  return unwrapData(payload);
}

export async function listInstitutionInstructorApplications(
  institutionId,
  { page = 0, size = 20, sort, status } = {},
) {
  const payload = await apiRequest(`/institutions/${institutionId}/instructor-applications`, {
    query: { page, size, sort, status },
  });
  return asPage(payload);
}

export async function listMyInstructorApplications({ page = 0, size = 20, sort, status } = {}) {
  const payload = await apiRequest('/instructor-applications/me', {
    query: { page, size, sort, status },
  });
  return asPage(payload);
}

export async function getInstructorApplication(applicationId) {
  const payload = await apiRequest(`/instructor-applications/${applicationId}`);
  return unwrapData(payload);
}

export async function approveInstructorApplication(applicationId) {
  const payload = await apiRequest(`/instructor-applications/${applicationId}/approve`, {
    method: 'POST',
  });
  return unwrapData(payload);
}

export async function rejectInstructorApplication(applicationId, rejectionReason) {
  const payload = await apiRequest(`/instructor-applications/${applicationId}/reject`, {
    method: 'POST',
    body: { rejectionReason },
  });
  return unwrapData(payload);
}

export async function listCourseInstructors(courseId) {
  const payload = await apiRequest(`/courses/${courseId}/instructors`);
  const data = unwrapData(payload);
  return Array.isArray(data) ? data : [];
}

export async function assignCourseInstructor(courseId, body) {
  const payload = await apiRequest(`/courses/${courseId}/instructors`, {
    method: 'POST',
    body,
  });
  return unwrapData(payload);
}

export async function updateCourseInstructor(courseId, userId, body) {
  const payload = await apiRequest(`/courses/${courseId}/instructors/${userId}`, {
    method: 'PUT',
    body,
  });
  return unwrapData(payload);
}

export async function removeCourseInstructor(courseId, userId) {
  const payload = await apiRequest(`/courses/${courseId}/instructors/${userId}`, {
    method: 'DELETE',
  });
  return unwrapData(payload);
}
