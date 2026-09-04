import { apiRequest, asPage, unwrapData } from '@/services/api-client';
import { listCourses } from '@/services/course-api';
import { getUser } from '@/services/user-api';
import { formatUserDisplayName } from '@/utils/user-display';

const userCache = new Map();

export async function fetchUserCached(userId) {
  const id = String(userId || '').trim();
  if (!id) return null;
  if (userCache.has(id)) return userCache.get(id);
  try {
    const user = await getUser(id);
    userCache.set(id, user);
    return user;
  } catch {
    userCache.set(id, null);
    return null;
  }
}

/** 수강신청 목록에 신청자 이름(생년월일) 보조 필드 추가 */
export async function enrichEnrollmentsWithUsers(rows) {
  const list = Array.isArray(rows) ? rows : [];
  const ids = [...new Set(list.map((row) => row.userId).filter(Boolean))];
  await Promise.all(ids.map((id) => fetchUserCached(id)));
  return list.map((row) => {
    const user = row.userId ? userCache.get(row.userId) : null;
    return {
      ...row,
      userName: user?.name || row.userName,
      birthDate: user?.birthDate ?? row.birthDate,
      applicantLabel: formatUserDisplayName(user || { userId: row.userId, id: row.userId }),
    };
  });
}

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

/** BE에 사용자별 목록 API가 없어 과정×수강신청을 집계 */
export async function listUserEnrollments(userId, { coursePageSize = 50 } = {}) {
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
