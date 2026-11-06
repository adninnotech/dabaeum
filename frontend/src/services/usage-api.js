import { apiDownload, apiRequest, apiUrl, unwrapData } from '@/services/api-client';
import { useAuthStore } from '@/stores/auth-store';

/* 이용 통계(백엔드 docs/specs/usage-statistics.md). 기록 2개는 무인증·항상 200 이고, 관리자 조회 4개는 플랫폼 관리자 전용.
 * 기간은 서울 날짜·양끝 포함. 빼면 서버가 마지막 집계일(보통 어제)까지 최근 30일을 쓴다. to 가 마지막 집계일 뒤면 422 */

const VISIT_SENT_KEY = 'dabaeum.visitRecorded';

function alreadySent() {
  try {
    return window.sessionStorage.getItem(VISIT_SENT_KEY) === '1';
  } catch {
    return false;
  }
}

function markSent() {
  try {
    window.sessionStorage.setItem(VISIT_SENT_KEY, '1');
  } catch {
    /* 사생활 보호 모드 등 — 탭마다 다시 보내도 서버가 하루 1건으로 센다 */
  }
}

function postRecord(path, token) {
  return fetch(apiUrl(path), {
    method: 'POST',
    keepalive: true,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
}

/* 기록 요청 — 빈 본문 keepalive fetch 라 링크로 떠나도 이어지고, 화면은 응답을 기다리지 않는다.
 * 로그인했으면 토큰을 실어 회원 성별·연령대가 남게 한다(sendBeacon 은 토큰을 못 실었다 — 백엔드 합의).
 * 만료·잘못된 토큰은 서버 보안 필터가 401 로 거절한다 — 토큰 없이 한 번만 다시 보내 비회원으로 남긴다(백엔드 합의).
 * apiRequest 를 쓰지 않는다 — 401 이 세션 만료 처리로 이어져 앱을 열자마자 로그인 화면으로 보냈다 */
async function postQuietly(path) {
  const token = useAuthStore().token;
  try {
    const response = await postRecord(path, token);
    if (response.status === 401 && token) await postRecord(path, null);
  } catch {
    /* 기록 실패는 화면에 영향이 없다 */
  }
}

/** 방문 기록 — 탭당 한 번. 실패해도 화면에는 영향이 없다(같은 날 다시 보내도 서버가 1건으로 남긴다) */
export function recordVisitOncePerTab() {
  if (typeof window === 'undefined' || alreadySent()) return;
  markSent();
  postQuietly('/visits');
}

/** 배너 클릭 기록 — 링크 이동은 막지 않는다 */
export function recordBannerClick(bannerId) {
  if (!bannerId || typeof window === 'undefined') return;
  postQuietly(`/banners/${encodeURIComponent(bannerId)}/clicks`);
}

/** 요약·추이 — { from, to, interval, totals, previousFrom, previousTo, previousTotals, rows: [{ period, visitors, … }] } */
export async function getUsageStatistics({ from, to, interval }) {
  const payload = await apiRequest('/admin/statistics/usage', { query: { from, to, interval } });
  return unwrapData(payload);
}

/** CSV(UTF-8 BOM) — rows 를 내려받는다. 관리자 접속기록에 DOWNLOAD 로 남는다 */
export function downloadUsageStatisticsCsv({ from, to, interval }) {
  return apiDownload('/admin/statistics/usage', {
    query: { from, to, interval, format: 'csv' },
  });
}

/** 성별·연령대 — { metric, total, members, guests, genders: [{ key, label, count }], ageBands } (genders·ageBands 는 회원만) */
export async function getUsageDemographics({ metric, from, to }) {
  const payload = await apiRequest('/admin/statistics/usage/demographics', {
    query: { metric, from, to },
  });
  return unwrapData(payload);
}

/** 검색어 순위 — type POPULAR | ZERO_RESULT, rows: [{ keyword, searchers }]. 하루 3명 이상인 검색어만 있다 */
export async function getUsageSearchKeywords({ type, from, to, limit }) {
  const payload = await apiRequest('/admin/statistics/usage/search-keywords', {
    query: { type, from, to, limit },
  });
  return unwrapData(payload);
}

/** 배너별 클릭한 사람 수 — rows: [{ bannerId, title, deleted, clickers }] 내림차순 */
export async function getUsageBannerClicks({ from, to }) {
  const payload = await apiRequest('/admin/statistics/usage/banners', { query: { from, to } });
  return unwrapData(payload);
}
