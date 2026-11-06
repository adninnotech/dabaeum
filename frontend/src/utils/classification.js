/* 표준 강좌 분류(설계서 §12, TSIX_AABBCC) 트리 도우미 */

export const CLASSIFICATION_PATH_SEPARATOR = ' > ';

/** 하위가 없는 코드만 강좌에 붙일 수 있다 — 3단계, 또는 하위 없는 1단계(TSIX_070000) */
export function isLeafClassification(node) {
  return !Array.isArray(node?.children) || node.children.length === 0;
}

/** 코드 → 루트부터 그 노드까지의 경로 [node, …]. 없으면 [] */
export function findClassificationTrail(tree, code) {
  if (!code || !Array.isArray(tree)) return [];
  for (const node of tree) {
    if (node.code === code) return [node];
    const rest = findClassificationTrail(node.children, code);
    if (rest.length) return [node, ...rest];
  }
  return [];
}

/** 응답의 classificationPath(이름 배열) → 표시 문자열. 비어 있으면 fallback */
export function classificationPathText(path, fallback = '') {
  return Array.isArray(path) && path.length ? path.join(CLASSIFICATION_PATH_SEPARATOR) : fallback;
}

/** 목록 한 줄처럼 좁은 자리는 가장 아래 단계 이름만 쓴다 */
export function classificationLeafName(path, fallback = '') {
  return Array.isArray(path) && path.length ? path[path.length - 1] : fallback;
}

/** q-select 선택지 — 노드 배열 → [{label, value}] */
export function classificationOptions(nodes) {
  return (nodes || []).map((node) => ({ label: node.name, value: node.code }));
}

/** 강좌 저장·게시 422 중 분류 문제(부모·사용 중지 코드, 게시 시 미지정)를 사람 말로 바꾼다 */
export function courseSaveErrorMessage(error, fallback) {
  const details = Array.isArray(error?.payload?.details) ? error.payload.details : [];
  if (error?.status === 422 && details.includes('classificationCode')) {
    return '강좌 분류를 확인해 주세요. 공개하려면 사용 중인 분류를 소분류까지 골라야 합니다.';
  }
  return error?.message || fallback;
}
