/**
 * 홈 추천 강좌 — 섹션 제목과 카드 이유 문구(docs/specs/course-recommendation.md, 백엔드 저장소).
 * 제목은 다른 홈 섹션과 같은 한 색 — 이 제목만 두 색이면 실수처럼 보였고 파랑은 '누를 수 있음' 신호다.
 */
/** 조사 — 받침이 있으면 '을', 없으면 '를' (한글로 끝나지 않으면 '을(를)') */
function objectParticle(word) {
  const last = String(word || '').trim().slice(-1);
  const code = last.charCodeAt(0) - 0xac00;
  if (code < 0 || code > 11171) return '을(를)';
  return code % 28 ? '을' : '를';
}

/** 따옴표 안 강좌명이 길면 줄인다 — 카드 한 줄에 들어가게 */
const SOURCE_TITLE_MAX = 16;

function shortTitle(title) {
  const value = String(title || '').trim();
  return value.length > SOURCE_TITLE_MAX ? `${value.slice(0, SOURCE_TITLE_MAX)}…` : value;
}

/**
 * basis + 첫 항목 reason → { title }
 * AGE_BAND 는 첫 항목의 ageBand 로 이름을 만든다(없으면 일반 인기 제목). INTEREST 는 회원이 고른 관심 분야 기준
 */
export function recommendationHeading(basis, firstReason = {}) {
  // 모델 점수 추천 — 명세(GET /courses/recommended) "회원님을 위한 추천". 분기가 없어 기본 제목으로 떨어졌다(QA HOME-17)
  if (basis === 'MODEL') {
    return { title: '회원님을 위한 추천' };
  }
  if (basis === 'PERSONAL') {
    return { title: `회원님 맞춤 강좌` };
  }
  if (basis === 'INTEREST') {
    return { title: '관심 분야 강좌' };
  }
  if (basis === 'AGE_BAND' && firstReason?.ageBand) {
    return { title: `${firstReason.ageBand}대가 많이 듣는 강좌` };
  }
  // 일반 추천 — '이런 강좌는 어때요'는 말투가 튀어 다른 홈 섹션 제목처럼 명사로(사용자 요청 10-21)
  return { title: '추천 강좌' };
}

/** 추천할 강좌가 없을 때 — 섹션을 숨기지 않고 제목과 다음 행동을 보인다 */
export function emptyRecommendationHeading(loggedIn) {
  return {
    title: '나의 추천 강좌',
    empty: loggedIn
      ? '지금은 추천할 강좌가 없어요. 마음에 드는 강좌를 찜해 두면 비슷한 강좌를 골라 드려요.'
      : '로그인하고 마음에 드는 강좌를 찜하면 비슷한 강좌를 골라 드려요.',
  };
}

/**
 * 모델 추천 이유 — reason.topFactor(그 강좌에 가장 크게 기여한 feature 묶음)마다 한 문장.
 * 문구는 사용자 확정 2026-10-21(INTEREST 는 2026-11-03 구·군 대신 추가). topFactor 가 없거나 모르는 값이면 기본 문장
 */
const MODEL_REASON_DEFAULT = '회원님과 비슷한 분들이 많이 신청했어요';
export const MODEL_REASON_TEXT = {
  CLASSIFICATION: '같은 분야를 들으셨어요',
  INTEREST: '관심 분야에 맞는 강좌예요',
  AGE_BAND: '비슷한 연령대가 많이 들어요',
  PRICE: '선호하시는 수강료 조건이에요',
  SCHEDULE: '선호하시는 요일·시간대예요',
  POPULARITY: '요즘 많이 신청하는 강좌예요',
};

/** 카드 아래 이유 한 줄 — 만들 수 없으면 '' */
export function recommendationReasonText(reason) {
  if (!reason?.type) return '';
  if (reason.type === 'SIMILAR_CLASSIFICATION') {
    const fullTitle = String(reason.sourceCourseTitle || '').trim();
    const field = reason.classificationName ? `${reason.classificationName} 분야` : '비슷한 분야';
    if (!fullTitle) return field;
    const verb = reason.sourceSignal === 'ENROLLMENT' ? '신청하셔서' : '찜하셔서';
    // 조사는 줄이기 전 제목의 끝 글자로 — '…' 로 끝나면 을(를)이 됐다
    return `'${shortTitle(fullTitle)}'${objectParticle(fullTitle)} ${verb} · ${field}`;
  }
  if (reason.type === 'MODEL_SCORE') {
    // '지금 인기'로 떨어져 모델 점수로 고른 강좌를 인기 강좌로 잘못 안내했다(QA HOME-17)
    return MODEL_REASON_TEXT[reason.topFactor] || MODEL_REASON_DEFAULT;
  }
  if (reason.type === 'INTEREST') {
    return reason.classificationName ? `관심 분야: ${reason.classificationName}` : '관심 분야 추천';
  }
  if (reason.type === 'POPULAR_AGE_BAND' && reason.ageBand) return `${reason.ageBand}대 인기`;
  return '지금 인기';
}
