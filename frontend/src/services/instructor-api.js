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

export async function listInstructorCourses({ page = 0, size = 50, sort, status } = {}) {
  const payload = await apiRequest('/instructors/me/courses', {
    query: { page, size, sort, status },
  });
  return asPage(payload);
}

export async function getInstructorCourseStats() {
  const payload = await apiRequest('/instructors/me/courses/stats');
  return unwrapData(payload);
}

/** 강사 상세용 — 특정 강사에게 배정된 과정 목록 (관리자 전용).
 * ⚠ 기존 `listInstructorCourses`(내 강좌, `/instructors/me/courses`)와 다른 함수다 — 오버로드 아님. */
export async function listInstructorCoursesOf(userId, { page = 0, size = 20, status } = {}) {
  const payload = await apiRequest(`/instructors/${userId}/courses`, {
    query: { page, size, status },
  });
  return asPage(payload);
}

/** 강사 상세용 — 배정 과정 상태별 통계 (관리자 전용) */
export async function getInstructorCourseStatsOf(userId) {
  const payload = await apiRequest(`/instructors/${userId}/courses/stats`);
  return unwrapData(payload);
}

/** 강사 상세용 — 특정 강사 담당 과정 수강 현황 (관리자 전용) */
export async function listInstructorEnrollmentStatusOf(
  userId,
  { page = 0, size = 50, courseId } = {},
) {
  const payload = await apiRequest(`/instructors/${userId}/enrollment-status`, {
    query: { page, size, courseId },
  });
  return asPage(payload);
}

export async function listInstructorEnrollmentStatus({ page = 0, size = 50, courseId } = {}) {
  const payload = await apiRequest('/instructors/me/enrollment-status', {
    query: { page, size, courseId },
  });
  return asPage(payload);
}
