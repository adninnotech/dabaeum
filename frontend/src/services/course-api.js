import { apiRequest, asPage, unwrapData } from '@/services/api-client';

export async function listCourses({
  page = 0,
  size = 20,
  sort,
  q,
  educationType,
  selectionMethod,
  status,
  classificationCode,
} = {}) {
  // classificationCode 는 어느 단계든 받고 하위를 포함해 거른다 (TSIX_040000 = 문화예술교육 전체)
  const payload = await apiRequest('/courses', {
    query: { page, size, sort, q, educationType, selectionMethod, status, classificationCode },
  });
  return asPage(payload);
}

export async function getCourse(courseId) {
  const payload = await apiRequest(`/courses/${courseId}`);
  return unwrapData(payload);
}

export async function createCourse(body) {
  const payload = await apiRequest('/courses', { method: 'POST', body });
  return unwrapData(payload);
}

export async function updateCourse(courseId, body) {
  const payload = await apiRequest(`/courses/${courseId}`, {
    method: 'PUT',
    body,
  });
  return unwrapData(payload);
}

// body: { userId, role: 'MAIN' | 'ASSISTANT' } — additionalProperties:false, 둘 다 required
export async function assignCourseInstructor(courseId, body) {
  const payload = await apiRequest(`/courses/${courseId}/instructors`, {
    method: 'POST',
    body,
  });
  return unwrapData(payload);
}

/** 모집 재개(관리자 정정) — RECRUITMENT_CLOSED·IN_PROGRESS 만. 종료·취소는 409 CORRECTION_NOT_ALLOWED. 사유 필수 */
export async function reopenCourse(courseId, reason) {
  const payload = await apiRequest(`/courses/${courseId}/reopen`, { method: 'POST', body: { reason } });
  return unwrapData(payload);
}

export async function publishCourse(courseId) {
  const payload = await apiRequest(`/courses/${courseId}/publish`, {
    method: 'POST',
  });
  return unwrapData(payload);
}

export async function closeCourse(courseId) {
  const payload = await apiRequest(`/courses/${courseId}/close`, {
    method: 'POST',
  });
  return unwrapData(payload);
}

export async function listCourseAttachments(courseId) {
  const payload = await apiRequest(`/courses/${courseId}/attachments`);
  return unwrapData(payload);
}

// ⚠ 요청 스키마(CourseAttachmentAttachRequest)는 additionalProperties: false 로 fileId 단독만
// 받는다. fileName·sortOrder 는 서버가 StoredFile·첨부 순서로 채운다 — 같이 보내면 422.
export async function attachCourseFile(courseId, { fileId }) {
  const payload = await apiRequest(`/courses/${courseId}/attachments`, {
    method: 'POST',
    body: { fileId },
  });
  return unwrapData(payload);
}

export async function detachCourseFile(courseId, attachmentId) {
  await apiRequest(`/courses/${courseId}/attachments/${attachmentId}`, { method: 'DELETE' });
}

/**
 * 임시저장(DRAFT) 강좌 삭제 — DELETE /courses/{courseId} (soft delete, 회차·첨부도 함께 사라진다).
 * 권한은 과정 수정과 같다. DRAFT 가 아니면 409 COURSE_STATUS_CONFLICT, 없으면 404 COURSE_NOT_FOUND
 */
export async function deleteDraftCourse(courseId) {
  const payload = await apiRequest(`/courses/${encodeURIComponent(courseId)}`, { method: 'DELETE' });
  return unwrapData(payload);
}

/** 강좌 상세 조회 기록 — 상세 화면에 들어올 때 한 번. 같은 사람은 하루 한 번만 센다. 항상 200 (없는 강좌도 같은 응답) */
export async function recordCourseView(courseId) {
  await apiRequest(`/courses/${encodeURIComponent(courseId)}/views`, { method: 'POST' });
}

/**
 * 인기 강좌 — [{ course, popularity: { enrollmentCount, viewCount, score } }].
 * ageBand 가 있으면 그 나이대, 없으면 전체. 매시 5분에 최근 30일로 다시 계산하며 첫 계산 전에는 빈 목록이다
 */
export async function listPopularCourses({ ageBand, limit = 12 } = {}) {
  const payload = await apiRequest('/courses/popular', {
    query: { ageBand, limit },
  });
  const list = unwrapData(payload);
  return Array.isArray(list) ? list : [];
}

/* 추천 강좌 exclude 는 최대 20개(서버 400) */
const RECOMMEND_EXCLUDE_MAX = 20;

/**
 * 홈 추천 강좌 — { basis: MODEL|PERSONAL|INTEREST|AGE_BAND|POPULAR, items: [{ impressionId, course, matchLevel, reason }] }.
 * 학습자 토큰이면 개인화하고 노출 로그가 남는다 — 홈에 들어올 때 한 번만 부른다.
 * excludeIds: 인기 섹션에 이미 그린 강좌(쉼표 문자열 하나로 보낸다, 반복 파라미터 아님)
 */
export async function listRecommendedCourses({ limit = 6, excludeIds = [] } = {}) {
  const exclude = excludeIds.filter(Boolean).slice(0, RECOMMEND_EXCLUDE_MAX).join(',');
  const payload = await apiRequest('/courses/recommended', { query: { limit, exclude } });
  const data = unwrapData(payload) || {};
  return {
    basis: data.basis || 'POPULAR',
    items: Array.isArray(data.items) ? data.items.filter((item) => item?.course) : [],
  };
}

/** 추천 카드 클릭 기록 — 학습자 노출(impressionId)에만. 404(이미 클릭 등)도 화면엔 상관없다 */
export async function clickRecommendedCourse(impressionId) {
  await apiRequest(`/courses/recommended/${encodeURIComponent(impressionId)}/click`, { method: 'POST' });
}
