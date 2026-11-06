/* 공개 검증(POST /credentials/verify · GET /vc/status/{no}) 결과 코드 → 화면 문구.
 * 서버는 없는 번호도 200 + NOT_FOUND 로 준다. 결과에 소지자·과정 정보는 없다(개인정보 최소화). */

// 서버 credentialNo 패턴과 같다 — 보내기 전에 걸러 400 을 피한다
export const CREDENTIAL_NO_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$/;
export const CREDENTIAL_NO_FORMAT_ERROR =
  '수료증 번호 형식이 아닙니다. 영문·숫자로 시작하고 영문·숫자·.-_만 쓸 수 있습니다.';

export const VERIFY_RESULT = {
  VALID: {
    tone: 'ok',
    icon: 'o_verified',
    title: '유효한 수료증입니다',
    body: '다배움에서 발급했고, 서명과 블록체인 원장 기록이 맞으며 폐기되지 않았습니다.',
  },
  REVOKED: {
    tone: 'bad',
    icon: 'o_block',
    title: '폐기된 수료증입니다',
    body: '발급 기관이 이 수료증을 폐기했습니다. 더 이상 효력이 없습니다.',
  },
  SUPERSEDED: {
    tone: 'warn',
    icon: 'o_published_with_changes',
    title: '재발급되어 대체된 수료증입니다',
    body: '같은 이수 건으로 새 수료증이 발급되었습니다. 소지자에게 최신 수료증 번호를 요청해 주세요.',
  },
  EXPIRED: {
    tone: 'warn',
    icon: 'o_event_busy',
    title: '유효기간이 지난 수료증입니다',
    body: '발급은 정상이었지만 유효기간이 끝났습니다.',
  },
  INVALID: {
    tone: 'bad',
    icon: 'o_gpp_bad',
    title: '유효하지 않은 수료증입니다',
    body: '서명이나 원장 기록이 발급 내용과 맞지 않습니다. 위조·변조되었을 수 있습니다.',
  },
  NOT_FOUND: {
    tone: 'bad',
    icon: 'o_search_off',
    title: '수료증을 찾을 수 없습니다',
    body: '이 번호로 발급된 수료증이 없습니다. 번호를 다시 확인해 주세요.',
  },
  ERROR: {
    tone: 'warn',
    icon: 'o_report',
    title: '지금은 확인할 수 없습니다',
    body: '검증 중 오류가 났습니다. 잠시 뒤 다시 확인해 주세요.',
  },
};

// 유출 선언된 키 버전으로 서명됐지만 원장 최초 기록이 유출 이전이라 플랫폼이 조건부로 인정한 경우
export const CONDITIONAL_NOTE =
  '다배움은 이 수료증을 인정하지만, 서명키 사고 이전에 발급된 건이라 외부 검증기에서는 거부될 수 있습니다. 소지자에게 재발급을 안내해 주세요.';

/** 결과 코드 → 문구. 모르는 코드는 ERROR 로 본다 */
export function verifyResultOf(code) {
  return VERIFY_RESULT[String(code || '').toUpperCase()] || VERIFY_RESULT.ERROR;
}
