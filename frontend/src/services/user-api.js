import { apiRequest, asPage, unwrapData } from '@/services/api-client';

export async function fetchAuthSession() {
  const payload = await apiRequest('/auth/session');
  return unwrapData(payload);
}

// 소속 기관 목록 — INSTRUCTOR/INSTITUTION_ADMIN 만 항목이 있다 (PLATFORM_ADMIN 은 빈 배열)
export async function listMyInstitutions() {
  const payload = await apiRequest('/users/me/institutions');
  return unwrapData(payload);
}

/**
 * 사용자 목록. gender·hasEnrollment·lastLogin(WITHIN_7·WITHIN_30·OVER_90·NEVER)·
 * createdFrom/createdTo('YYYY-MM-DD' 서울 날짜, 양 끝 포함) 는 서버가 거르고 totalElements 에도 반영한다.
 * sort 는 'createdAt|name|lastLoginAt|enrollmentCount|updatedAt|email|status,asc|desc'
 */
export async function listUsers({
  page = 0,
  size = 20,
  sort,
  status,
  role,
  q,
  gender,
  hasEnrollment,
  lastLogin,
  createdFrom,
  createdTo,
} = {}) {
  const payload = await apiRequest('/users', {
    query: {
      page,
      size,
      sort,
      status,
      role,
      q,
      gender,
      hasEnrollment,
      lastLogin,
      createdFrom,
      createdTo,
    },
  });
  return {
    data: unwrapData(payload) || [],
    page: payload?.page || null,
    meta: payload?.meta || null,
  };
}

export async function getUser(userId) {
  const payload = await apiRequest(`/users/${userId}`);
  return unwrapData(payload);
}

export async function getCurrentUser() {
  const payload = await apiRequest('/users/me');
  return unwrapData(payload);
}

export async function updateCurrentUser(body) {
  const payload = await apiRequest('/users/me', { method: 'PUT', body });
  return unwrapData(payload);
}

export async function createUser(body) {
  const payload = await apiRequest('/users', { method: 'POST', body });
  return unwrapData(payload);
}

export async function updateUser(userId, body) {
  const payload = await apiRequest(`/users/${userId}`, { method: 'PUT', body });
  return unwrapData(payload);
}

export async function changeUserStatus(userId, status) {
  // OpenAPI: PATCH, test.http: PUT — PATCH 우선, 실패 시 PUT 재시도
  try {
    const payload = await apiRequest(`/users/${userId}/status`, {
      method: 'PATCH',
      body: { status },
    });
    return unwrapData(payload);
  } catch (error) {
    if (error.status !== 405 && error.status !== 404) throw error;
    const payload = await apiRequest(`/users/${userId}/status`, {
      method: 'PUT',
      body: { status },
    });
    return unwrapData(payload);
  }
}

export async function listUserIdentities(userId) {
  const payload = await apiRequest(`/users/${userId}/identities`);
  return unwrapData(payload) || [];
}

/** 본인 identity 목록. /users/{userId}/identities 는 관리자 전용이라 본인 화면은 이 경로를 쓴다 */
export async function listMyIdentities() {
  const payload = await apiRequest('/users/me/identities');
  return unwrapData(payload) || [];
}

export async function linkUserIdentity(userId, body) {
  const payload = await apiRequest(`/users/${userId}/identities`, {
    method: 'POST',
    body,
  });
  return unwrapData(payload);
}

export async function unlinkUserIdentity(userId, identityId) {
  await apiRequest(`/users/${userId}/identities/${identityId}`, {
    method: 'DELETE',
  });
}

/** 회원 상세용 — 특정 회원의 나의 강의 목록 조회 (관리자 전용) */
export async function listUserLearningCourses(userId, { page = 0, size = 20, status, q } = {}) {
  const payload = await apiRequest(`/users/${userId}/learning-courses`, {
    query: { page, size, status, q },
  });
  return asPage(payload);
}

/** 회원 상세용 — 특정 회원이 등록한 문의 목록 조회 (관리자 전용) */
export async function listUserInquiries(userId, { page = 0, size = 20, status, q } = {}) {
  const payload = await apiRequest(`/users/${userId}/inquiries`, {
    query: { page, size, status, q },
  });
  return asPage(payload);
}

/** 회원 상세용 — 학습 통계 (관리자 전용) */
export async function getUserLearningStats(userId) {
  const payload = await apiRequest(`/users/${userId}/learning-stats`);
  return unwrapData(payload);
}

/** 회원 관리자 메모 변경. yaml 확인: PATCH (계획서 초안의 PUT 은 오기) */
export async function updateUserMemo(userId, memo) {
  const payload = await apiRequest(`/users/${userId}/memo`, { method: 'PATCH', body: { memo } });
  return unwrapData(payload);
}

export async function listUserRoles(userId) {
  const payload = await apiRequest(`/users/${userId}/roles`);
  return unwrapData(payload) || [];
}

export async function assignUserRole(userId, body) {
  const payload = await apiRequest(`/users/${userId}/roles`, {
    method: 'POST',
    body,
  });
  return unwrapData(payload);
}

export async function revokeUserRole(userId, roleId) {
  await apiRequest(`/users/${userId}/roles/${roleId}`, {
    method: 'DELETE',
  });
}

/** 관리자 비밀번호 초기화 */
export async function adminResetUserPassword(userId, temporaryPassword) {
  const payload = await apiRequest(`/users/${userId}/password/reset`, {
    method: 'POST',
    body: temporaryPassword ? { temporaryPassword } : {},
  });
  return unwrapData(payload);
}

/** 본인 비밀번호 변경. 성공 시 기존 토큰이 즉시 무효화된다. */
export async function changeMyPassword({ currentPassword, newPassword }) {
  const payload = await apiRequest('/users/me/password', {
    method: 'PUT',
    body: { currentPassword, newPassword },
  });
  return unwrapData(payload);
}

/** 본인 회원 탈퇴. 성공 시 토큰이 즉시 무효화된다. 이력(수강·수강평)은 삭제되지 않는다. */
export async function withdrawMe({ reason } = {}) {
  const payload = await apiRequest('/users/me/withdraw', {
    method: 'POST',
    body: reason ? { reason } : {},
  });
  return unwrapData(payload);
}

/** 내 계정에 다대구 DID 연결. body 는 SDK 콜백 암호문 (DadaeguLoginRequest 와 같다).
 * 이름·전화·생년월일은 다대구 값으로 덮어쓰고 이메일은 유지. 409 IDENTITY_CONFLICT, 401 복호화 실패 (local/dev 전용) */
export async function linkMyDadaeguIdentity(body) {
  const payload = await apiRequest('/users/me/identities/dadaegu', { method: 'POST', body });
  return unwrapData(payload);
}
