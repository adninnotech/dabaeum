import { apiRequest, unwrapData } from '@/services/api-client';

// 사용 중 트리는 관리자가 켜고 끌 때만 바뀐다 — 성공한 응답만 모듈에 잡아 둔다 (관리 화면이 바꾸면 비운다)
let enabledTreeCache = null;

/** 표준 강좌 분류 트리(사용 중만) — 1→2→3단계 [{code, level, name, enabled, children[]}]. 인증 없이 호출한다 */
export async function listCourseClassifications() {
  if (enabledTreeCache) return enabledTreeCache;
  const payload = await apiRequest('/course-classifications', { token: null });
  const tree = unwrapData(payload);
  if (!Array.isArray(tree)) throw new Error('강좌 분류 응답이 올바르지 않습니다.');
  enabledTreeCache = tree;
  return tree;
}

/** 비활성 포함 전체 트리 — 플랫폼 관리자 전용 */
export async function listAdminCourseClassifications() {
  const payload = await apiRequest('/admin/course-classifications');
  const tree = unwrapData(payload);
  if (!Array.isArray(tree)) throw new Error('강좌 분류 응답이 올바르지 않습니다.');
  return tree;
}

/** 사용 여부만 바꾼다(코드·이름은 표준이 정한다). 사용 중인 강좌가 있어도 끌 수 있다 */
export async function setCourseClassificationEnabled(code, enabled) {
  const action = enabled ? 'enable' : 'disable';
  const payload = await apiRequest(
    `/admin/course-classifications/${encodeURIComponent(code)}/${action}`,
    { method: 'POST' },
  );
  enabledTreeCache = null;
  return unwrapData(payload);
}
