import { apiRequest, asPage, unwrapData } from '@/services/api-client';
import { listCourses } from '@/services/course-api';

export async function listCourseEnrollments(courseId, { page = 0, size = 50, sort } = {}) {
  const payload = await apiRequest(`/courses/${courseId}/enrollments`, {
    query: { page, size, sort },
  });
  return asPage(payload);
}

export async function createEnrollment(courseId, body) {
  const payload = await apiRequest(`/courses/${courseId}/enrollments`, {
    method: 'POST',
    body,
  });
  return unwrapData(payload);
}

export async function createProxyEnrollment(courseId, body) {
  const payload = await apiRequest(`/courses/${courseId}/proxy-enrollments`, {
    method: 'POST',
    body,
  });
  return unwrapData(payload);
}

/**
 * 수강 진도 상세 — { attendedCount, sessionCount(취소 회차 제외), progressPercent, lastStudiedAt, ... }.
 * 출석 기준 진도라 나의 강의 목록 카드의 진도율과 같은 값이다
 */
export async function getEnrollmentProgress(enrollmentId) {
  const payload = await apiRequest(`/enrollments/${enrollmentId}/progress`);
  return unwrapData(payload);
}

export async function getEnrollment(enrollmentId) {
  const payload = await apiRequest(`/enrollments/${enrollmentId}`);
  return unwrapData(payload);
}

export async function approveEnrollment(enrollmentId) {
  const payload = await apiRequest(`/enrollments/${enrollmentId}/approve`, {
    method: 'POST',
  });
  return unwrapData(payload);
}

export async function rejectEnrollment(enrollmentId, body) {
  const payload = await apiRequest(`/enrollments/${enrollmentId}/reject`, {
    method: 'POST',
    body,
  });
  return unwrapData(payload);
}

export async function cancelEnrollment(enrollmentId) {
  const payload = await apiRequest(`/enrollments/${enrollmentId}/cancel`, {
    method: 'POST',
  });
  return unwrapData(payload);
}

export async function withdrawEnrollment(enrollmentId) {
  const payload = await apiRequest(`/enrollments/${enrollmentId}/withdraw`, {
    method: 'POST',
  });
  return unwrapData(payload);
}

/** 최대 100건 일괄 승인·반려. reason은 REJECT일 때만 전송(APPROVE면 무시되므로 보내지 않는다) */
export async function bulkDecideEnrollments({ enrollmentIds, decision, reason }) {
  const payload = await apiRequest('/enrollments/bulk-decision', {
    method: 'POST',
    body: { enrollmentIds, decision, ...(decision === 'REJECT' ? { reason } : {}) },
  });
  return unwrapData(payload);
}

export async function listMyEnrollments({ page = 0, size = 50, sort, status } = {}) {
  const payload = await apiRequest('/users/me/enrollments', {
    query: { page, size, sort, status },
  });
  return asPage(payload);
}

/** 특정 사용자의 수강 내역(관리자 전용 GET /users/{userId}/enrollments) — 과정·기관 정보 포함 */
export async function listEnrollmentsOfUser(userId, { page = 0, size = 100, sort, status } = {}) {
  const payload = await apiRequest(`/users/${userId}/enrollments`, {
    query: { page, size, sort, status },
  });
  return asPage(payload);
}

/** 전용 API 실패 시 과정×수강신청 집계로 폴백 */
export async function listUserEnrollments(userId, { coursePageSize = 50 } = {}) {
  try {
    const page = await listMyEnrollments({ page: 0, size: coursePageSize, sort: 'createdAt,desc' });
    if (Array.isArray(page.data)) return page.data;
  } catch {
    // 폴백
  }
  const courses = await listCourses({ page: 0, size: coursePageSize, sort: 'createdAt,desc' });
  const rows = [];

  await Promise.all(
    (courses.data || []).map(async (course) => {
      try {
        const result = await listCourseEnrollments(course.id, { page: 0, size: 100 });
        (result.data || [])
          .filter((item) => item.userId === userId)
          .forEach((item) => {
            rows.push({
              ...item,
              courseTitle: course.title,
              courseCode: course.courseCode,
              courseStatus: course.status,
            });
          });
      } catch {
        // 권한/빈 과정은 건너뜀
      }
    }),
  );

  return rows.sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')));
}

