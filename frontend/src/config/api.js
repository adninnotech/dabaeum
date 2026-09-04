/**
 * API Base URL
 * - 기본: 동일 출처 `/api/v1` (개발=Vite proxy, 배포=웹서버 reverse proxy)
 * - 덮어쓰기: 빌드 시 `VITE_API_BASE_URL` (예: http://<api-host>:8080/api/v1)
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

/**
 * 개발·데모용 고정 Bearer 토큰. 소스에 두면 빌드 산출물에 그대로 실려 브라우저에서
 * 읽히므로 넣지 않는다. 필요할 때만 빌드 시 `VITE_API_BEARER_TOKEN` 으로 준다.
 * 비어 있으면 로그인으로 받은 토큰만 쓴다.
 */
export const API_BEARER_TOKEN = import.meta.env.VITE_API_BEARER_TOKEN ?? '';

/** 테스트 기본 userId */
export const DEFAULT_USER_ID = '31b19a98-1982-45a4-96d1-3903856d0c77';

/** 테스트 기본 institutionId (seed: SEOUL-LLC) */
export const DEFAULT_INSTITUTION_ID = '1ca6d1e2-5398-4188-bfe6-e08da954b53d';
