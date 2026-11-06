import { apiRequest, asPage, unwrapData } from '@/services/api-client';

export async function listInstitutions({ page = 0, size = 20, sort, q, status, region } = {}) {
  const payload = await apiRequest('/institutions', {
    query: { page, size, sort, q, status, region },
  });
  return asPage(payload);
}

export async function getInstitution(institutionId) {
  const payload = await apiRequest(`/institutions/${institutionId}`);
  return unwrapData(payload);
}

export async function createInstitution(body) {
  const payload = await apiRequest('/institutions', { method: 'POST', body });
  return unwrapData(payload);
}

export async function updateInstitution(institutionId, body) {
  const payload = await apiRequest(`/institutions/${institutionId}`, {
    method: 'PUT',
    body,
  });
  return unwrapData(payload);
}

export async function getInstitutionDashboard(institutionId) {
  const payload = await apiRequest(`/institutions/${institutionId}/dashboard`);
  return unwrapData(payload);
}

export async function listInstitutionCourses(
  institutionId,
  { page = 0, size = 50, sort, status } = {},
) {
  const payload = await apiRequest(`/institutions/${institutionId}/courses`, {
    query: { page, size, sort, status },
  });
  return asPage(payload);
}

export async function listInstitutionEnrollments(
  institutionId,
  { page = 0, size = 50, sort, status } = {},
) {
  const payload = await apiRequest(`/institutions/${institutionId}/enrollments`, {
    query: { page, size, sort, status },
  });
  return asPage(payload);
}

export async function listInstitutionInstructors(
  institutionId,
  { page = 0, size = 50, sort, status, q } = {},
) {
  const payload = await apiRequest(`/institutions/${institutionId}/instructors`, {
    query: { page, size, sort, status, q },
  });
  return asPage(payload);
}

export async function updateInstitutionInstructorMemo(institutionId, userId, memo) {
  const payload = await apiRequest(`/institutions/${institutionId}/instructors/${userId}`, {
    method: 'PATCH',
    body: { memo },
  });
  return unwrapData(payload);
}

export async function applyInstitutionJoin(body) {
  const payload = await apiRequest('/institution-applications', {
    method: 'POST',
    body,
  });
  return unwrapData(payload);
}

export async function listInstitutionJoinApplications({ page = 0, size = 20, status } = {}) {
  const payload = await apiRequest('/institution-applications', {
    query: { page, size, status },
  });
  return asPage(payload);
}

/** 본인 기관 등록 신청 목록 — 최신순, sort 없음. status 는 PENDING·APPROVED·REJECTED */
export async function listMyInstitutionJoinApplications({ page = 0, size = 20, status } = {}) {
  const payload = await apiRequest('/institution-applications/me', {
    query: { page, size, status },
  });
  return asPage(payload);
}

export async function getInstitutionJoinApplication(applicationId) {
  const payload = await apiRequest(`/institution-applications/${applicationId}`);
  return unwrapData(payload);
}

export async function approveInstitutionJoinApplication(applicationId) {
  const payload = await apiRequest(`/institution-applications/${applicationId}/approve`, {
    method: 'POST',
  });
  return unwrapData(payload);
}

export async function rejectInstitutionJoinApplication(applicationId, rejectionReason) {
  const payload = await apiRequest(`/institution-applications/${applicationId}/reject`, {
    method: 'POST',
    body: { rejectionReason },
  });
  return unwrapData(payload);
}

/**
 * 기관 관리자용 학습자 정확 일치 조회(중앙 #56) — 이름 + 생년월일(YYYY-MM-DD) + 휴대전화 셋 다 필수.
 * → { userId, name }. 없음·불일치·같은 값의 회원이 둘 이상이면 모두 같은 404. 휴대전화는 서버가 숫자만 비교한다.
 * 조회지만 POST — 개인정보가 쿼리 문자열이면 웹서버 접근 로그에 그대로 남는다. 본문엔 세 필드만(모르는 필드는 400)
 */
export async function lookupInstitutionLearner(institutionId, { name, birthDate, phone } = {}) {
  const payload = await apiRequest(`/institutions/${institutionId}/learners/lookup`, {
    method: 'POST',
    body: { name, birthDate, phone },
  });
  return unwrapData(payload);
}
