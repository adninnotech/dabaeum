import { apiRequest, asPage, unwrapData } from '@/services/api-client';

export async function listFaqs({ page = 0, size = 50 } = {}) {
  const payload = await apiRequest('/support/faqs', {
    query: { page, size },
    token: null,
  });
  return asPage(payload);
}

/** FAQ 관리 목록(플랫폼 관리자) — 표시 순서(sortOrder)대로 온다. 수십 건이라 한 번에 받는다 */
export async function listAdminFaqs({ page = 0, size = 100 } = {}) {
  return asPage(await apiRequest('/admin/faqs', { query: { page, size } }));
}

/** FAQ 등록 — { question, answer, sortOrder? } (sortOrder 생략 시 0) */
export async function createFaq(body) {
  return unwrapData(await apiRequest('/admin/faqs', { method: 'POST', body }));
}

/** FAQ 수정 — PUT 통째 교체라 sortOrder 도 필수 */
export async function updateFaq(faqId, body) {
  return unwrapData(await apiRequest(`/admin/faqs/${encodeURIComponent(faqId)}`, { method: 'PUT', body }));
}

export async function deleteFaq(faqId) {
  await apiRequest(`/admin/faqs/${encodeURIComponent(faqId)}`, { method: 'DELETE' });
}

export async function getTermsContent(type) {
  const payload = await apiRequest('/contents/terms', {
    query: { type },
    token: null,
  });
  return unwrapData(payload);
}

/** 약관·개인정보 새 버전 등록(플랫폼 관리자) — 수정·삭제 없이 쌓인다. 같은 type·version 이면 409 */
export async function createTermsContent(body) {
  return unwrapData(await apiRequest('/admin/terms', { method: 'POST', body }));
}
