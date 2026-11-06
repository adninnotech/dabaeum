/* 이수 처리 화면의 강의 목록·첫 선택.
 * 가장 최근에 만든 강의가 먼저 열려 수강생 0명인 임시저장 강의가 뜨곤 했다 —
 * 이수와 상관없는 강의는 빼고, 할 일이 있을 법한 강의부터 연다 */
import { formatDateKst } from '@/utils/datetime';

// 이수 처리할 수강생이 없는 강의(작성 중·취소)
const HIDDEN_STATUSES = ['DRAFT', 'CANCELLED'];

/** 강의 등록 때 적은 이수 기준 한 줄 — '이수 기준: 출석률 80% 이상'. 없으면 '' */
export function completionCriteriaText(course) {
  const detail = course?.detail || {};
  if (detail.completionEnabled === false) return '이 강의는 수료 처리를 하지 않는 것으로 등록돼 있습니다.';
  const rate = Number(detail.requiredProgressRate);
  if (detail.completionCriteria === 'PROGRESS' && rate > 0) return `이수 기준: 출석률 ${rate}% 이상 (강의 등록 때 정한 값)`;
  return '';
}

export function filterCompletionCourses(courses) {
  return (Array.isArray(courses) ? courses : []).filter((course) => !HIDDEN_STATUSES.includes(course.status));
}

/**
 * 첫 강의 — 주소의 courseId → 수업 기간이 끝났는데 아직 종료가 아닌 강의(이수 처리할 때) →
 * 진행 중 → 종료 → 그 밖(목록 순)
 */
export function pickCompletionCourseId(courses, wantedId, now = new Date()) {
  const list = Array.isArray(courses) ? courses : [];
  if (wantedId && list.some((course) => course.id === wantedId)) return wantedId;
  const today = formatDateKst(now, '');
  const ended = (course) => course.endDate && formatDateKst(course.endDate, '') < today;
  const rank = (course) => {
    if (course.status !== 'COMPLETED' && ended(course)) return 0;
    if (course.status === 'IN_PROGRESS') return 1;
    if (course.status === 'COMPLETED') return 2;
    return 3;
  };
  const sorted = [...list].sort((a, b) => rank(a) - rank(b));
  return sorted[0]?.id || null;
}
