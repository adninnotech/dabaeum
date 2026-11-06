/**
 * 관심 분야(#142) — 강좌 분류 1단계(GET /course-classifications) 중 0~3개. 선택 입력이고, 값을 넣으려면
 * [선택] 관심 분야 수집·이용 동의가 있어야 한다(서버 422). 구·군 대신 추천에 쓴다.
 * (@/ import 없이 — node --test 로 단위 테스트한다)
 */

export const INTEREST_MAX = 3;

/* 분류 이름(학력보완교육·시민참여교육 등)은 보고 고르기 어려워 쉬운 이름으로 보인다(사용자 결정 2026-11-03,
 * 다른 서비스의 '관심 주제 고르기'처럼 짧은 낱말 버튼). 서버에 보내는 값은 분류 코드 그대로다.
 * 표준 분류(TSIX) 1단계 코드에 묶는다 — 새 코드가 생기면 서버 이름이 그대로 보인다 */
export const INTEREST_LABELS = Object.freeze({
  TSIX_010000: '한글·스마트폰 배우기',
  TSIX_020000: '검정고시·학력',
  TSIX_030000: '자격증·취업',
  TSIX_040000: '운동·취미·예술',
  TSIX_050000: '인문·건강·요리',
  TSIX_060000: '환경·시민 활동',
  TSIX_070000: '진로·인생 설계',
});

/** 분류 트리 → 사용 중인 1단계 선택지 [{ code, name }] — name 은 쉬운 이름(없으면 서버 이름). enabled 가 없으면 사용 중 */
export function interestOptions(tree) {
  if (!Array.isArray(tree)) return [];
  return tree
    .filter((node) => node?.code && node?.name && node.enabled !== false)
    .map((node) => ({ code: node.code, name: INTEREST_LABELS[node.code] || node.name }));
}

/** 골랐으면 빼고, 안 골랐으면 넣는다. 이미 다 찼으면 같은 배열을 돌려준다 */
export function toggleInterest(codes, code) {
  const list = Array.isArray(codes) ? codes : [];
  if (list.includes(code)) return list.filter((item) => item !== code);
  if (list.length >= INTEREST_MAX) return list;
  return [...list, code];
}

/** 코드 목록 → 이름 목록(고른 순서). 선택지에 없는 코드는 뺀다 */
export function interestNames(codes, options) {
  const byCode = new Map((options || []).map((item) => [item.code, item.name]));
  return (Array.isArray(codes) ? codes : []).map((code) => byCode.get(code)).filter(Boolean);
}
