import { apiRequest, asPage, unwrapData } from '@/services/api-client';

/**
 * 대구 다른 기관 강좌(공공데이터 전국평생학습강좌표준데이터) — 조회 전용, 인증 없음.
 * 다배움에서 신청·출결·수료하지 않고 운영기관 홈페이지(homepageUrl)로 보낸다.
 * targetType·operDay 는 쉼표로 여러 값(OR), 필터끼리는 AND. 기본은 교육이 끝나지 않은 강좌만(includeEnded=false).
 * receiptOpen=true 는 오늘 접수 중(마감일 없는 강좌는 빠짐), timeOfDay 는 startTime 기준 MORNING(<12)·AFTERNOON(<18)·EVENING
 */
export async function listPublicCourses({
  page = 0,
  size = 20,
  q,
  method,
  targetType,
  regionCode,
  free,
  from,
  to,
  includeEnded,
  receiptOpen,
  operDay,
  timeOfDay,
  sort,
} = {}) {
  const payload = await apiRequest('/public-courses', {
    query: {
      page,
      size,
      q,
      method,
      targetType,
      regionCode,
      free,
      from,
      to,
      includeEnded,
      receiptOpen,
      operDay,
      timeOfDay,
      sort,
    },
  });
  return asPage(payload);
}

/**
 * 구·군별 건수 — 목록과 같은 조건(구·군·page·size·sort 제외). { total, regions: [{ regionCode, count }] }
 * regions 는 9개 구·군이 0건까지 늘 들어 있고, total 은 구·군을 모르는 강좌까지 센다
 */
export async function getPublicCourseFacets({ q, method, targetType, free, from, to, includeEnded, receiptOpen, operDay, timeOfDay } = {}) {
  const payload = await apiRequest('/public-courses/facets', {
    query: { q, method, targetType, free, from, to, includeEnded, receiptOpen, operDay, timeOfDay },
  });
  return unwrapData(payload);
}

export async function getPublicCourse(courseId) {
  const payload = await apiRequest(`/public-courses/${courseId}`);
  return unwrapData(payload);
}
