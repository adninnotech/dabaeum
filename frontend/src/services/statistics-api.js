import { apiDownload, apiRequest, unwrapData } from '@/services/api-client';

/* 관리자 통계(설계서 §9, 플랫폼 관리자). 기간은 서울 날짜·양끝 포함, 기본 올해 1월 1일~오늘, 최대 5년(넘으면 422) */

function enrollmentQuery({ groupBy, from, to, classificationCode, institutionId, limit }) {
  return { groupBy, from, to, classificationCode, institutionId, limit };
}

/** 수강 통계 — { groupBy, from, to, rows: [{ key, label, applied, approved, completed }] }. CONTENT 는 신청 많은 순 limit 개 */
export async function getEnrollmentStatistics(params) {
  const payload = await apiRequest('/admin/statistics/enrollments', {
    query: enrollmentQuery(params),
  });
  return unwrapData(payload);
}

/** 배지 발급 통계 — rows: [{ key, label, issued, revoked }]. 정의 없는 옛 배지는 key NONE */
export async function getBadgeStatistics({ groupBy, from, to }) {
  const payload = await apiRequest('/admin/statistics/badges', { query: { groupBy, from, to } });
  return unwrapData(payload);
}

/** CSV(UTF-8 BOM) 내려받기 — 관리자 접속기록에 DOWNLOAD 로 남는다 */
export function downloadEnrollmentStatisticsCsv(params) {
  return apiDownload('/admin/statistics/enrollments', {
    query: { ...enrollmentQuery(params), format: 'csv' },
  });
}

export function downloadBadgeStatisticsCsv({ groupBy, from, to }) {
  return apiDownload('/admin/statistics/badges', { query: { groupBy, from, to, format: 'csv' } });
}
