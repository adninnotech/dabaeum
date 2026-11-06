import { API_BASE_URL, API_BEARER_TOKEN } from '@/config/api';
import { useAuthStore } from '@/stores/auth-store';
import { apiErrorMessage } from '@/utils/api-error-message';

/* 토큰 정본은 스토어 하나. 스토어가 부팅 때 localStorage를 읽어 채우므로 스토어 ⊇ localStorage다.
 * localStorage를 직접 읽으면 로그인 직후 persist() 전(=user 미확정) 구간에 dev bearer로 샌다.
 * 순환 import 회피: useAuthStore()는 모듈 최상위가 아니라 호출 시점에만 쓴다. */
function getToken() {
  return useAuthStore().token || API_BEARER_TOKEN;
}

/* 로그인한 채 받은 401 = 토큰 만료. 화면엔 "이메일 또는 비밀번호가 올바르지 않습니다."가 뜨고 로그인 상태로 남았다(QA PERM-04).
 * 자격 증명을 확인하는 요청(로그인·가입·비밀번호 변경)의 401 은 "비밀번호가 틀렸다"라 만료로 보지 않는다 */
export const SESSION_EXPIRED_MESSAGE = '로그인이 만료되었습니다. 다시 로그인해 주세요.';
const CREDENTIAL_PATHS = ['/auth/', '/users/me/password'];
let sessionExpiredHandler = null;

/** 세션 만료 때 할 일(로그아웃·로그인 화면 이동) — boot/api.js 가 등록한다. handler({ method }) */
export function onSessionExpired(handler) {
  sessionExpiredHandler = handler;
}

/** 스토어 토큰으로 보냈나 — 명시 token·token:null(인증 없이)·dev bearer 는 세션이 아니다 */
function usesSession(token) {
  return token === undefined && !!useAuthStore().token;
}

function isSessionExpiry(path, status, sessionUsed) {
  if (status !== 401 || !sessionUsed) return false;
  const normalized = path.startsWith('/') ? path : `/${path}`;
  return !CREDENTIAL_PATHS.some((prefix) => normalized.startsWith(prefix));
}

/** sendBeacon 처럼 fetch 밖에서 API 주소가 필요할 때 */
export function apiUrl(path, query) {
  return buildUrl(path, query);
}

function buildUrl(path, query) {
  const normalized = path.startsWith('/') ? path : `/${path}`;
  const base = API_BASE_URL.startsWith('http')
    ? API_BASE_URL
    : `${typeof window !== 'undefined' ? window.location.origin : 'http://localhost'}${API_BASE_URL}`;
  const url = new URL(`${base.replace(/\/$/, '')}${normalized}`);
  if (query && typeof query === 'object') {
    Object.entries(query).forEach(([key, value]) => {
      if (value === undefined || value === null || value === '') return;
      url.searchParams.set(key, String(value));
    });
  }
  return url.toString();
}

async function parseBody(response) {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function responseError(payload, response) {
  const retry = Number(response.headers.get('Retry-After'));
  const retryAfter =
    response.status === 429 && Number.isFinite(retry) && retry > 0 ? Math.ceil(retry) : null;
  const message = apiErrorMessage(payload, response.status);
  const error = new Error(
    retryAfter ? `${message} ${retryAfter}초 뒤 다시 시도해 주세요.` : message,
  );
  error.serverMessage = payload?.message || '';
  error.status = response.status;
  error.payload = payload;
  error.retryAfter = retryAfter;
  return error;
}

function failure(payload, response, { path, method = 'GET', sessionUsed }) {
  const error = responseError(payload, response);
  if (isSessionExpiry(path, response.status, sessionUsed)) {
    error.message = SESSION_EXPIRED_MESSAGE;
    error.sessionExpired = true;
    if (sessionExpiredHandler) sessionExpiredHandler({ method });
  }
  return error;
}

export function createIdempotencyKey(prefix = 'fe') {
  const rand = Math.random().toString(36).slice(2, 10);
  return `${prefix}-${Date.now()}-${rand}`;
}

export async function apiRequest(path, options = {}) {
  const { method = 'GET', query, body, headers = {}, token, idempotencyKey } = options;
  const skipAuth = token === null;
  const authToken = skipAuth ? null : token || getToken();
  const sessionUsed = usesSession(token);

  const response = await fetch(buildUrl(path, query), {
    method,
    headers: {
      Accept: 'application/json',
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
      ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  const payload = await parseBody(response);

  if (!response.ok) {
    // 서버 문구는 영어라 한국어로 바꿔 보인다 — 원문은 serverMessage 에 남긴다
    throw failure(payload, response, { path, method, sessionUsed });
  }

  return payload;
}

/** multipart/form-data 업로드 (Content-Type 자동 설정) */
export async function apiFormDataRequest(path, options = {}) {
  const { method = 'POST', formData, query, token } = options;
  const skipAuth = token === null;
  const authToken = skipAuth ? null : token || getToken();
  const sessionUsed = usesSession(token);

  const response = await fetch(buildUrl(path, query), {
    method,
    headers: {
      Accept: 'application/json',
      ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
    },
    body: formData,
  });

  const payload = await parseBody(response);

  if (!response.ok) {
    // 서버 문구는 영어라 한국어로 바꿔 보인다 — 원문은 serverMessage 에 남긴다
    throw failure(payload, response, { path, method, sessionUsed });
  }

  return payload;
}

// Content-Disposition 의 filename*=UTF-8''… 또는 filename="…" — 한글 파일명을 살린다
function filenameFromDisposition(header) {
  if (!header) return '';
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (encoded) {
    try {
      return decodeURIComponent(encoded[1].trim());
    } catch {
      /* 아래 평문 이름으로 */
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(header);
  return plain ? plain[1].trim() : '';
}

/** 파일 내려받기(CSV 등) — { blob, filename }. 실패 응답은 JSON 오류 본문을 읽어 apiRequest 와 같은 Error 로 던진다 */
export async function apiDownload(path, options = {}) {
  const { query, token } = options;
  const authToken = token === null ? null : token || getToken();
  const sessionUsed = usesSession(token);
  const response = await fetch(buildUrl(path, query), {
    headers: authToken ? { Authorization: `Bearer ${authToken}` } : {},
  });
  if (!response.ok) {
    const payload = await parseBody(response);
    throw failure(payload, response, { path, sessionUsed });
  }
  return {
    blob: await response.blob(),
    filename: filenameFromDisposition(response.headers.get('Content-Disposition')),
  };
}

export function unwrapData(payload) {
  if (payload && typeof payload === 'object' && 'data' in payload) {
    return payload.data;
  }
  return payload;
}

export function asPage(payload) {
  return {
    data: unwrapData(payload) || [],
    page: payload?.page || null,
    meta: payload?.meta || null,
  };
}
