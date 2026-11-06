/* 가입 약관 2종. hasDoc: 전체 본문이 GET /contents/terms 에 있다.
 * [선택] 홍보 알림 수신 동의는 뺐다(#144) — 홍보를 보내는 기능이 없고 철회할 화면도 없어 고지와 어긋났다 */
export const SIGNUP_TERMS = [
  { kind: 'USAGE', label: '[필수] 서비스 이용약관 동의', required: true, hasDoc: true },
  { kind: 'PRIVACY', label: '[필수] 개인정보 수집·이용 동의', required: true, hasDoc: true },
];

/* 동의 줄을 펼치면 보이는 고지 표 — { 표 이름, 행 }. 행의 emphasis 는 법이 "중요한 내용"으로 정해
 * 굵기·색으로 눈에 띄게 해야 하는 칸이다(개인정보 보호법 제22조 동의 받는 방법).
 * 칸 이름·순서는 법(제15조 ②)을 따랐다. 칸 안 문구는 #142 초안을 사용자가 확정했다(2026-11-03, 법무 확인 없이 진행 — 사용자 결정).
 * 개인정보 전체 본문은 GET /contents/terms(PRIVACY) 가 정본이다 */
export const CONSENT_NOTICES = {
  PRIVACY: {
    caption: '개인정보 수집·이용 안내',
    rows: [
      // 다대구 가입(학습자)이 실제로 받는 값 — VC 의 이름·생년월일·휴대전화 + DID. 이메일·성별은 받지 않는다(#142, 사용자 확정 2026-11-03)
      { key: 'purpose', label: '이용 목적', text: '회원 식별과 가입, 수강 신청·출결·이수 관리' },
      { key: 'items', label: '수집 항목', text: '다대구 본인 인증 정보(이름, 생년월일, 휴대전화번호, 다대구 식별값)' },
      {
        key: 'period',
        label: '보유·이용 기간',
        text: '회원 탈퇴 시까지. 법령에 따로 정한 기간이 있으면 그 기간까지',
        emphasis: true,
      },
      {
        key: 'refuse',
        label: '동의 거부 권리',
        text: '동의를 거부할 수 있습니다. 다만 필수 항목이라 거부하면 회원가입을 할 수 없습니다.',
      },
    ],
  },
  /* 강사·기관 관리자 이메일 가입(SignupFormPage)의 개인정보 표 — 다대구 표가 그대로 보였다(#142) */
  PRIVACY_STAFF: {
    caption: '개인정보 수집·이용 안내',
    rows: [
      { key: 'purpose', label: '이용 목적', text: '회원 식별과 가입, 강사·기관 관리자 신청 처리, 운영 콘솔 이용' },
      { key: 'items', label: '수집 항목', text: '이메일, 비밀번호, 이름, 휴대전화번호(입력한 경우)' },
      {
        key: 'period',
        label: '보유·이용 기간',
        text: '회원 탈퇴 시까지. 법령에 따로 정한 기간이 있으면 그 기간까지',
        emphasis: true,
      },
      {
        key: 'refuse',
        label: '동의 거부 권리',
        text: '동의를 거부할 수 있습니다. 다만 필수 항목이라 거부하면 회원가입을 할 수 없습니다.',
      },
    ],
  },
  /* [선택] 관심 분야 수집·이용(#142) — 관심 분야 입력을 켰을 때만 보인다(isInterestInputEnabled) */
  INTEREST: {
    caption: '관심 분야 수집·이용 안내',
    rows: [
      { key: 'purpose', label: '이용 목적', text: '관심 분야에 맞춘 강좌 추천', emphasis: true },
      { key: 'items', label: '수집 항목', text: '관심 분야(최대 3개)' },
      {
        key: 'period',
        label: '보유·이용 기간',
        text: '동의를 철회하거나 회원 탈퇴할 때까지',
        emphasis: true,
      },
      {
        key: 'refuse',
        label: '동의 거부 권리',
        text: '동의하지 않아도 회원가입과 서비스 이용에는 제한이 없습니다. 고르지 않으면 나이대·인기 강좌 기준으로 추천합니다.',
      },
    ],
  },
};

export function emptyAgreements() {
  return { USAGE: false, PRIVACY: false };
}

export function requiredTermsAgreed(agreements) {
  return SIGNUP_TERMS.filter((item) => item.required).every((item) => agreements?.[item.kind]);
}
