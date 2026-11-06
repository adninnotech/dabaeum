import { apiRequest, unwrapData } from '@/services/api-client';

export async function loginLocalAccount({ email, password }) {
  const payload = await apiRequest('/auth/login', {
    method: 'POST',
    token: null,
    body: { email, password },
  });
  return unwrapData(payload);
}

/* 약관 동의 — 이용약관·개인정보는 필수 true(아니면 422). 홍보 동의(agreedToMarketing)는 화면에서 뺐다(#144) —
 * 서버는 선택 값이라 보내지 않으면 동의 안 함으로 남긴다 */
function toAgreementBody(agreements = {}) {
  return {
    agreedToUsage: agreements.USAGE === true,
    agreedToPrivacy: agreements.PRIVACY === true,
  };
}

/** 강사·기관 관리자 이메일 가입(#134) */
export async function signupLocalAccount({ email, password, name, phone, birthDate, agreements }) {
  const payload = await apiRequest('/auth/signup', {
    method: 'POST',
    token: null,
    body: {
      email,
      password,
      name,
      phone: phone || null,
      birthDate: birthDate || null,
      ...toAgreementBody(agreements),
    },
  });
  return unwrapData(payload);
}

/** 다대구 첫 로그인 가입 완료. 토큰 만료·재사용 401, 동시 가입 409 (local/dev 전용).
 * interestCodes 는 강좌 분류 1단계 코드 0~3개(선택). 값이 있으면 agreedToInterest 가 true 여야 한다(아니면 400) —
 * 관심 분야 입력을 끈 동안(isInterestInputEnabled false)엔 둘 다 보내지 않는다 */
export async function signupWithDadaeguAccount({ registrationToken, agreements, interestCodes, agreedToInterest }) {
  const interest = Array.isArray(interestCodes) ? { interestCodes, agreedToInterest: agreedToInterest === true } : {};
  const payload = await apiRequest('/auth/dadaegu/signup', {
    method: 'POST',
    token: null,
    body: { registrationToken, ...interest, ...toAgreementBody(agreements) },
  });
  return unwrapData(payload);
}

export async function checkEmailAvailability(email) {
  const payload = await apiRequest('/auth/email/availability', {
    query: { email },
    token: null,
  });
  return unwrapData(payload);
}

export async function requestPasswordReset(email) {
  await apiRequest('/auth/password/reset-request', {
    method: 'POST',
    token: null,
    body: { email },
  });
}

export async function confirmPasswordReset({ token, password }) {
  const payload = await apiRequest('/auth/password/reset', {
    method: 'POST',
    token: null,
    body: { token, password },
  });
  return unwrapData(payload);
}

/** 다대구 QR 로그인 SDK 에 넘길 siteId. 인증 없이 호출한다 (local/dev 전용) */
export async function getDadaeguLoginConfig() {
  const payload = await apiRequest('/auth/dadaegu/config', { token: null });
  return unwrapData(payload);
}

/** SDK 콜백의 암호문 필드를 그대로 보낸다. 복호화 실패·비활성 사용자는 둘 다 401 (local/dev 전용).
 * 항상 200 — 처음 보는 DID 면 토큰 대신 data.registrationRequired=true 와 1회용 registrationToken 이 온다 */
export async function loginWithDadaeguAccount(body) {
  const payload = await apiRequest('/auth/dadaegu/login', {
    method: 'POST',
    token: null,
    body,
  });
  return unwrapData(payload);
}
