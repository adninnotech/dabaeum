import { apiRequest, unwrapData } from '@/services/api-client';

/* 학습배지 정의(설계서 §11) — 규칙은 COURSE_COMPLETION(courseId | classificationCode) · CUMULATIVE(count, classificationCode?) 두 가지 */

// 배지 정의 이미지 업로드 purpose (POST /files)
export const BADGE_IMAGE_PURPOSE = 'BADGE_IMAGE';

function asList(payload) {
  const list = unwrapData(payload);
  if (!Array.isArray(list)) throw new Error('배지 정의 응답이 올바르지 않습니다.');
  return list;
}

/** 배지 도감 — 사용 중인 정의만. 인증 없이 호출한다 */
export async function listBadgeDefinitions() {
  return asList(await apiRequest('/badge-definitions', { token: null }));
}

/** 사용 중지 포함 전체 정의 — 플랫폼 관리자 전용 */
export async function listAdminBadgeDefinitions() {
  return asList(await apiRequest('/admin/badge-definitions'));
}

/** 생성 — code 는 대문자·숫자·밑줄 2~50자, 이후 못 바꾼다. 409 BADGE_DEFINITION_CONFLICT(코드 중복) */
export async function createBadgeDefinition(body) {
  const payload = await apiRequest('/admin/badge-definitions', { method: 'POST', body });
  return unwrapData(payload);
}

/** 수정은 PUT(통째 교체, code 무시). 발급된 배지가 있으면 규칙 변경은 409 BADGE_DEFINITION_IN_USE */
export async function updateBadgeDefinition(definitionId, body) {
  const payload = await apiRequest(`/admin/badge-definitions/${definitionId}`, {
    method: 'PUT',
    body,
  });
  return unwrapData(payload);
}

/** 발급된 배지가 있으면 409 BADGE_DEFINITION_IN_USE — 대신 사용 중지한다 */
export async function deleteBadgeDefinition(definitionId) {
  await apiRequest(`/admin/badge-definitions/${definitionId}`, { method: 'DELETE' });
}

export async function setBadgeDefinitionActive(definitionId, active) {
  const action = active ? 'activate' : 'deactivate';
  const payload = await apiRequest(`/admin/badge-definitions/${definitionId}/${action}`, {
    method: 'POST',
  });
  return unwrapData(payload);
}
