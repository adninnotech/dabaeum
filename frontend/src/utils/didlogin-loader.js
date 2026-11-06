// 다대구 DID 로그인 SDK(public/vendor/didLogin.js, 대구시 배포본 v3.0.0 원본 그대로)를 한 번만 불러온다.
// npm 패키지가 아니라 전역 window.DIDLogin 을 심는 스크립트라 <script> 로 주입한다.
const SDK_SRC = `${import.meta.env.BASE_URL || '/'}vendor/didLogin.js`.replace(/\/{2,}/g, '/');

let loading = null;

export function loadDidLoginSdk() {
  if (window.DIDLogin) return Promise.resolve(window.DIDLogin);
  if (loading) return loading;

  loading = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = SDK_SRC;
    script.async = true;
    script.onload = () => {
      if (window.DIDLogin) resolve(window.DIDLogin);
      else reject(new Error('다대구 로그인 모듈을 불러오지 못했습니다.'));
    };
    script.onerror = () => {
      loading = null;
      script.remove();
      reject(new Error('다대구 로그인 모듈을 불러오지 못했습니다.'));
    };
    document.head.appendChild(script);
  });
  return loading;
}

// 백엔드 DadaeguLoginRequest 는 additionalProperties: false — SDK 가 늘린 필드를 걸러 보낸다.
// 값은 다대구가 암호화한 Base64 암호문이라 손대지 않는다.
const DADAEGU_LOGIN_FIELDS = ['did', 'name', 'birthdate', 'phoneNumber', 'ci', 'gender', 'isForeigner'];

export function toDadaeguLoginBody(returnData) {
  let data = returnData;
  if (typeof data === 'string') {
    try {
      data = JSON.parse(data);
    } catch {
      return null;
    }
  }
  if (!data || typeof data !== 'object' || typeof data.did !== 'string' || !data.did) return null;

  return DADAEGU_LOGIN_FIELDS.reduce((body, key) => {
    const value = data[key];
    return typeof value === 'string' ? { ...body, [key]: value } : body;
  }, {});
}
