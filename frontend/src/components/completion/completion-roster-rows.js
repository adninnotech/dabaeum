import { getCompletion } from '@/services/completion-api';
import { listAllUserCredentials } from '@/services/credential-api';
import { listCourseEnrollments } from '@/services/enrollment-api';
import { formatUserDisplayName } from '@/utils/user-display';

/* 이수 명단 행 모델 — 수강신청(승인) 1건 = 1행. 화면 상태는 행을 통째로 바꿔 끼운다(불변 갱신). */

const ENROLLMENT_PAGE_SIZE = 100;
// 과정 수강생이 많아도 끝없이 돌지 않게 — 100명 × 20쪽이면 한 과정으로 충분하다
const ENROLLMENT_MAX_PAGES = 20;

/** 필터 키 — 평가 전(NONE)은 이수 기록이 아직 없는(404) 행 */
export const ROSTER_FILTERS = [
  { value: 'ALL', label: '전체' },
  { value: 'NONE', label: '판정 전' },
  { value: 'ELIGIBLE', label: '이수 가능' },
  { value: 'NOT_COMPLETED', label: '미이수' },
  { value: 'COMPLETED', label: '이수 완료' }, // 행 배지(COMPLETION_STATUS_LABEL)와 같은 말로
];

// 확정·취소된 이수는 다시 평가할 수 없다(서버 409)
const NOT_EVALUABLE = ['COMPLETED', 'CANCELLED'];
// 발급 접수·진행·완료된 수료증은 다시 발급하지 않는다 — 실패(FAILED)만 다시 시도할 수 있다
const CREDENTIAL_IN_FLIGHT = ['PENDING', 'ISSUING', 'ISSUED'];

export function toRosterRow(enrollment) {
  return {
    enrollmentId: enrollment.id,
    userId: enrollment.userId,
    applicantLabel: formatUserDisplayName(enrollment),
    email: enrollment.email || '',
    completion: null,
    completionLoaded: false,
    completionError: '',
    credential: null,
    // 이미 발급한 수료증을 읽었는지 — 처음 열 때 읽지 않아 발급해도 새로고침하면 '미발급'이었다
    credentialLoaded: false,
    credentialError: '',
    busy: '',
  };
}

export function rowStatusKey(row) {
  return row.completion?.status || (row.completionLoaded && !row.completionError ? 'NONE' : '');
}

/** 발급 대기 — 이수 확정됐는데 수료증이 없거나 실패한 행(기관 발급 화면의 기본 보기) */
export const ISSUE_READY_FILTER = { value: 'ISSUE_READY', label: '발급 대기' };

export function matchesFilter(row, filter) {
  if (filter === ISSUE_READY_FILTER.value) return canIssueRow(row);
  return filter === 'ALL' || rowStatusKey(row) === filter;
}

export function canEvaluateRow(row) {
  return row.completionLoaded && !NOT_EVALUABLE.includes(row.completion?.status);
}

export function canConfirmRow(row) {
  return row.completion?.status === 'ELIGIBLE';
}

/** 수료증은 이수 확정(COMPLETED) 뒤에만 — 이수가능(ELIGIBLE)에서 누르면 서버가 거절한다 */
export function canIssueRow(row) {
  return (
    row.completion?.status === 'COMPLETED' &&
    // 발급 여부를 모르면(읽는 중·실패) 막는다 — 같은 이수로 두 번 발급하지 않게
    row.credentialLoaded &&
    !row.credentialError &&
    !CREDENTIAL_IN_FLIGHT.includes(row.credential?.status)
  );
}

/**
 * 이 과정의 수료증 — 살아 있는 것(접수·발급 중·발급)이 먼저, 없으면 가장 최근 것(실패·폐기 등).
 * 과정별 수료증 조회 API 가 없어 사람 단위 목록(GET /users/{userId}/credentials)에서 courseId 로 고른다
 */
export function pickCourseCredential(credentials, courseId) {
  const forCourse = credentials
    .filter((item) => item.courseId === courseId)
    .sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')));
  return forCourse.find((item) => CREDENTIAL_IN_FLIGHT.includes(item.status)) || forCourse[0] || null;
}

export async function fetchCourseCredential(userId, courseId) {
  if (!userId) throw new Error('수강생 정보가 없어 수료증을 확인하지 못했습니다.');
  return pickCourseCredential(await listAllUserCredentials(userId), courseId);
}

export function patchRow(rows, enrollmentId, patch) {
  return rows.map((row) => (row.enrollmentId === enrollmentId ? { ...row, ...patch } : row));
}

/** 과정의 승인된 수강신청 전부 — 쪽을 넘겨 끝까지 읽는다 */
export async function fetchApprovedEnrollments(courseId) {
  const collected = [];
  for (let page = 0; page < ENROLLMENT_MAX_PAGES; page += 1) {
    const result = await listCourseEnrollments(courseId, { page, size: ENROLLMENT_PAGE_SIZE });
    const list = Array.isArray(result.data) ? result.data : [];
    collected.push(...list);
    const totalPages = result.page?.totalPages ?? 1;
    if (page + 1 >= totalPages || list.length < ENROLLMENT_PAGE_SIZE) break;
  }
  return collected.filter((enrollment) => enrollment.status === 'APPROVED');
}

/** 이수 조회 — 404 는 아직 출석 평가 전이라 기록이 없는 것(오류 아님) */
export async function fetchCompletionOrNull(enrollmentId) {
  try {
    return await getCompletion(enrollmentId);
  } catch (error) {
    if (error?.status === 404) return null;
    throw error;
  }
}
