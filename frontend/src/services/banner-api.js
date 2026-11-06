import { apiRequest, unwrapData } from '@/services/api-client';

/**
 * GET /banners — 공개 배너 목록(E-1 홈 캐러셀용).
 * 계약 확인: 이 경로는 page·size 파라미터를 받지 않고 응답도 { data, meta }뿐(page 없음) —
 * asPage가 아니라 unwrapData를 쓴다. 노출 기간(visibility·startAt·endAt)과 정렬은 서버가 이미
 * 적용해 내려주므로 프론트에서 다시 거르거나 재정렬하지 않는다.
 */
export async function listPublicBanners() {
  const payload = await apiRequest('/banners', { token: null });
  return unwrapData(payload) || [];
}

/**
 * GET /admin/banners — 관리자 배너 목록(B-10). 공개 목록과 같은 BannerListOk 스키마라
 * page 파라미터·page 메타 자체가 없다(경로에 parameters 없음) — unwrapData만 쓴다.
 */
export async function listAdminBanners() {
  const payload = await apiRequest('/admin/banners');
  return unwrapData(payload) || [];
}

/**
 * POST /admin/banners — BannerCreateRequest.
 * visibility 생략 시 PRIVATE. SCHEDULED는 startAt·endAt 둘 다 필수(폼에서 막는다).
 * 이미지 파일은 purpose=BANNER로 먼저 업로드한 fileId만 받는다.
 */
export async function createBanner(body) {
  const payload = await apiRequest('/admin/banners', { method: 'POST', body });
  return unwrapData(payload);
}

/** PUT /admin/banners/{bannerId} — BannerUpdateRequest, 본문 전체 덮어쓰기. */
export async function updateBanner(bannerId, body) {
  const payload = await apiRequest(`/admin/banners/${bannerId}`, { method: 'PUT', body });
  return unwrapData(payload);
}

export async function deleteBanner(bannerId) {
  await apiRequest(`/admin/banners/${bannerId}`, { method: 'DELETE' });
}
