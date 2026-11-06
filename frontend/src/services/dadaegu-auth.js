import { getDadaeguLoginConfig } from '@/services/auth-api';
import { loadDidLoginSdk } from '@/utils/didlogin-loader';

// dev 계약의 requiredVC (백엔드 복호화 대상과 같아야 한다)
const DADAEGU_REQUIRED_VC = 'Name:PhoneNum:Birthdate';

const STASHED_QUERY_KEY = 'dabaeum.dadaegu.returnQuery';

// SDK 와 같은 판정 — iOS 분기만 현재 주소(location.href)를 앱 복귀 주소로 쓴다
function isIOS() {
  return /iPad|iPhone|iPod/.test(navigator.userAgent);
}

/* iOS 는 앱이 복귀 주소 끝에 "?mwSessionKey=..&siteId=.." 를 붙여 돌아온다. 복귀 주소에 이미 쿼리가 있으면
 * (예: /login?redirect=%2F) "?" 가 두 번 생겨 siteId 가 redirect 값 안으로 먹히고, SDK 가 siteId=null 로 chk 해
 * "siteId 가 유효하지 않습니다" 로 실패한다 (2026-09-22 재현). 그래서 인증을 시작하기 전에 쿼리·해시를 떼어
 * sessionStorage 에 맡기고, 돌아온 뒤 takeStashedDadaeguQuery() 로 되찾는다. */
function stashQueryForIOSReturn() {
  if (!isIOS() || (!window.location.search && !window.location.hash)) return;
  try {
    sessionStorage.setItem(
      STASHED_QUERY_KEY,
      JSON.stringify({ path: window.location.pathname, search: window.location.search }),
    );
  } catch {
    /* 저장이 막혀도 인증은 진행한다 — redirect 만 잃는다 */
  }
  // vue-router 가 쓰는 history.state 는 그대로 두고 주소만 바꾼다
  window.history.replaceState(window.history.state, '', window.location.pathname);
}

/** 인증 전에 맡겨 둔 쿼리. 같은 경로에서 맡긴 것만 돌려주고, 한 번 읽으면 지운다 */
export function takeStashedDadaeguQuery() {
  try {
    const raw = sessionStorage.getItem(STASHED_QUERY_KEY);
    if (!raw) return new URLSearchParams();
    sessionStorage.removeItem(STASHED_QUERY_KEY);
    const saved = JSON.parse(raw);
    return saved?.path === window.location.pathname
      ? new URLSearchParams(saved.search || '')
      : new URLSearchParams();
  } catch {
    return new URLSearchParams();
  }
}

/** SDK 를 불러와 init 한다. 폰에서 앱 인증 뒤 페이지로 돌아오면 SDK 가 init 시점에 결과를 조회해
 * 콜백을 부르므로, 콜백을 쓰는 화면은 버튼을 누르기 전(mounted)에도 이 함수를 불러 둔다. */
export async function prepareDadaeguSdk() {
  const sdk = await loadDidLoginSdk();
  // mbActive 를 켜야 폰에서 다대구 앱 딥링크로 간다. 끄면 SDK 가 "모바일 웹은 지원하지 않습니다" 만 띄운다
  if (!sdk.isReady()) sdk.init({ mbActive: true });
  return sdk;
}

/** 다대구 본인 인증 시작 — 데스크톱은 QR 팝업, 폰은 앱 딥링크. 결과는 window[callbackName] 으로 온다.
 * SDK 는 iOS 복귀·Android 재개 때 콜백을 "전역 함수 이름"으로만 찾는다 (함수 객체는 iOS 에서 거부). */
export async function startDadaeguAuth(callbackName) {
  const [sdk, config] = await Promise.all([prepareDadaeguSdk(), getDadaeguLoginConfig()]);
  if (!config?.siteId) throw new Error('다대구 로그인 설정을 불러오지 못했습니다.');
  stashQueryForIOSReturn();
  // SDK 가 검증·팝업 오류를 직접 alert 한다
  return sdk.loginPersonal({
    siteId: config.siteId,
    requiredVC: DADAEGU_REQUIRED_VC,
    callbackFunc: callbackName,
    iosCallbackFuncName: callbackName,
  });
}
