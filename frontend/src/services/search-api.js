import { apiRequest, unwrapData } from '@/services/api-client';

export const SEARCH_MIN_LENGTH = 2;
export const SEARCH_MAX_LENGTH = 100;
export const SEARCH_MAX_LIMIT = 20;

const EMPTY_TAB = { total: 0, items: [] };

/**
 * 통합검색(설계서 §5, 공개) — { courses, institutions, notices, faqs } 각 { total, items }.
 * q 는 앞뒤 공백을 뺀 2~100자(아니면 400), limit 은 탭당 1~20
 */
export async function searchAll(q, { limit = 5 } = {}) {
  const payload = await apiRequest('/search', { query: { q, limit }, token: null });
  const data = unwrapData(payload) || {};
  return {
    courses: data.courses || EMPTY_TAB,
    institutions: data.institutions || EMPTY_TAB,
    notices: data.notices || EMPTY_TAB,
    faqs: data.faqs || EMPTY_TAB,
  };
}
