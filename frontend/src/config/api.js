/**
 * API Base URL
 * - 기본: 동일 출처 `/api/v1` (개발=Vite proxy, 배포=웹서버 reverse proxy)
 * - 덮어쓰기: 빌드 시 `VITE_API_BASE_URL` (예: http://<API_SERVER>:8080/api/v1)
 *
 * 배포 시 HTTPS 페이지에서 HTTP API를 직접 호출하면 브라우저가 차단하고,
 * 다른 출처 호출은 CORS에 막힐 수 있으므로 동일 출처 프록시를 권장한다.
 */
function resolveApiBaseUrl() {
  const fromEnv = import.meta.env.VITE_API_BASE_URL;
  if (typeof fromEnv === 'string' && fromEnv.trim()) {
    return fromEnv.trim().replace(/\/$/, '');
  }
  return '/api/v1';
}

export const API_BASE_URL = resolveApiBaseUrl();

/** 데모 로그인·관리자 대리 로그인·dev bearer 를 쓰는 빌드인지 (quasar.config.js 의 DABAEUM_DEMO_AUTH) */
export const DEMO_AUTH_ENABLED = __DABAEUM_DEMO_AUTH__ === true;

/** dev bearer (test.http @token). dev 백엔드에서 PLATFORM_ADMIN 으로 통하므로 공개 빌드에는 넣지 않는다 —
 * 조건을 define 상수로 두어야 minify 가 문자열까지 지운다 (런타임 분기로 숨기면 번들에 남는다). */
export const API_BEARER_TOKEN = __DABAEUM_DEMO_AUTH__ ? import.meta.env.VITE_API_TOKEN || null : null;

/** 테스트 기본 userId */
export const DEFAULT_USER_ID = '31b19a98-1982-45a4-96d1-3903856d0c77';

/** 테스트 기본 institutionId (seed: SEOUL-LLC) */
export const DEFAULT_INSTITUTION_ID = '1ca6d1e2-5398-4188-bfe6-e08da954b53d';
