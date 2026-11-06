import { apiRequest, asPage, unwrapData } from '@/services/api-client';

export async function getPlatformDashboard() {
  const payload = await apiRequest('/admin/dashboard');
  return unwrapData(payload);
}

/** 사이트 전체 문의 목록 — 기관·강의별 N회 호출을 이 한 경로로 대체한다 */
export async function listAdminInquiries({
  page = 0,
  size = 20,
  status,
  inquiryType,
  educationType,
  institutionId,
  courseId,
  q,
} = {}) {
  const payload = await apiRequest('/admin/inquiries', {
    query: { page, size, status, inquiryType, educationType, institutionId, courseId, q },
  });
  return asPage(payload);
}

/**
 * 사이트 전체 수강신청 목록. 응답은 InstitutionEnrollment(9필드) — 진도율·최근 학습일 없음
 * (00-common §2-2 표에 없는 스키마다. LearningCourse 와 다르다).
 */
export async function listAdminEnrollments({
  page = 0,
  size = 20,
  status,
  institutionId,
  courseId,
  educationType,
  q,
} = {}) {
  const payload = await apiRequest('/admin/enrollments', {
    query: { page, size, status, institutionId, courseId, educationType, q },
  });
  return asPage(payload);
}

/** 관리자(PLATFORM_ADMIN·INSTITUTION_ADMIN) 계정 목록 — 플랫폼 관리자 전용, 가입 최신순.
 * q 는 이름·이메일 부분일치. 조회도 관리자 접속기록에 남는다 */
export async function listAdministrators({ page = 0, size = 20, role, status, q } = {}) {
  const payload = await apiRequest('/admin/administrators', {
    query: { page, size, role, status, q },
  });
  return asPage(payload);
}

/** 관리자 계정 생성 → { userId, administrator, temporaryPassword }.
 * 임시 비밀번호는 이 응답에서만 보인다. 409 이메일 중복, 404 기관 없음, 422 기관 조합, 503 학번 소진 */
export async function createAdministrator({ name, email, role, institutionId }) {
  const payload = await apiRequest('/admin/administrators', {
    method: 'POST',
    body: {
      name,
      email,
      role,
      institutionId: role === 'INSTITUTION_ADMIN' ? institutionId : null,
    },
  });
  return unwrapData(payload);
}

/** 관리자 접속기록(개인정보 처리 기록) — 플랫폼 관리자 전용, 최신순. from·to 는 서울 날짜(yyyy-MM-dd), 둘 다 포함 */
export async function listAdminAccessLogs({ page = 0, size = 20, actorId, action, from, to } = {}) {
  const payload = await apiRequest('/admin/access-logs', {
    query: { page, size, actorId, action, from, to },
  });
  return asPage(payload);
}
