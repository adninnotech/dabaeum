import { getCompletion } from '@/services/completion-api';
import { listAllUserCredentials } from '@/services/credential-api';
import { listCourseEnrollments, listEnrollmentsOfUser } from '@/services/enrollment-api';
import { formatUserDisplayName } from '@/utils/user-display';

/* 수료증 관리 화면의 표 행 만들기.
 * 계약(dabaeum-api-v1.yaml)에는 과정별·상태별 수료증 목록 API 가 없다 — 수료증 목록은 사람 단위
 * (GET /users/{userId}/credentials)뿐이고, 수료증에는 completionId 도 없다.
 * 그래서 과정의 승인된 수강생마다 수료증 목록과 이수 기록을 읽어 화면에서 맞춘다. */

export const NOT_ISSUED = 'NOT_ISSUED';

// 이 상태의 수료증이 있으면 같은 이수로 새로 발급할 필요가 없다
const LIVE_STATUSES = ['PENDING', 'ISSUING', 'ISSUED'];
const ENROLLMENT_PAGE_SIZE = 100;
const ENROLLMENT_MAX_PAGES = 10;
// 수강생 수만큼 요청이 나가므로 서버에 한꺼번에 몰리지 않게 나눠 보낸다
const FETCH_CONCURRENCY = 4;

async function listApprovedEnrollments(courseId) {
  const pages = [];
  for (let page = 0; page < ENROLLMENT_MAX_PAGES; page += 1) {
    const result = await listCourseEnrollments(courseId, { page, size: ENROLLMENT_PAGE_SIZE });
    const rows = Array.isArray(result.data) ? result.data : [];
    pages.push(rows);
    const totalPages = result.page?.totalPages;
    if (rows.length < ENROLLMENT_PAGE_SIZE || (totalPages != null && page + 1 >= totalPages)) break;
  }
  // 이수·수료증은 승인된 수강에서만 생긴다
  return pages.flat().filter((item) => item.status === 'APPROVED');
}

/** 묶음 단위로 나눠 순서대로 처리 — 결과 순서는 입력 순서와 같다 */
async function mapInChunks(items, size, fn) {
  const chunks = Array.from({ length: Math.ceil(items.length / size) }, (_, i) => items.slice(i * size, (i + 1) * size));
  return chunks.reduce(async (prev, chunk) => {
    const done = await prev;
    const next = await Promise.all(chunk.map(fn));
    return [...done, ...next];
  }, Promise.resolve([]));
}

/** 이수 기록 — 404 는 아직 평가 전이라 기록이 없는 것(오류 아님) */
async function findCompletion(enrollmentId) {
  try {
    return await getCompletion(enrollmentId);
  } catch (error) {
    if (error?.status === 404) return null;
    throw error;
  }
}

function toCredentialRow(credential, extra) {
  return {
    ...credential,
    ...extra,
    key: credential.id,
    courseTitle: credential.courseTitle || extra.courseTitle || '',
  };
}

/**
 * FAILED 행에 [다시 발급]을 달지 정한다 — 같은 학습자·과정에서 가장 최근 수료증이 FAILED 일 때만.
 * 실패 뒤 이미 새로 발급됐으면 다시 발급하지 않는다(서버도 막는다).
 */
export function markRetryable(rows) {
  const latestByGroup = rows
    .filter((row) => row.status !== NOT_ISSUED)
    .reduce((acc, row) => {
      const group = `${row.userId}|${row.courseId}`;
      const prev = acc[group];
      const newer = !prev || String(row.createdAt || '') > String(prev.createdAt || '');
      return newer ? { ...acc, [group]: row } : acc;
    }, {});
  return rows.map((row) => {
    const latest = latestByGroup[`${row.userId}|${row.courseId}`];
    const hasLive = rows.some(
      (other) => other.userId === row.userId && other.courseId === row.courseId && LIVE_STATUSES.includes(other.status),
    );
    return { ...row, retryable: row.status === 'FAILED' && latest?.key === row.key && !hasLive };
  });
}

function sortRows(rows) {
  return [...rows].sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')));
}

async function loadEnrollmentRows(enrollment, course) {
  const learnerLabel = formatUserDisplayName(enrollment);
  const [credentials, completion] = await Promise.all([
    listAllUserCredentials(enrollment.userId),
    findCompletion(enrollment.id),
  ]);
  const extra = {
    userId: enrollment.userId,
    learnerLabel,
    enrollmentId: enrollment.id,
    completionId: completion?.id || null,
    courseTitle: course.title,
  };
  const forCourse = credentials.filter((item) => item.courseId === course.id).map((item) => toCredentialRow(item, extra));
  // 이수는 확정됐는데 수료증이 하나도 없다 — [발급] 대상
  if (completion?.status === 'COMPLETED' && forCourse.length === 0) {
    return [{ ...extra, key: `not-issued-${enrollment.id}`, id: null, courseId: course.id, status: NOT_ISSUED, createdAt: completion.confirmedAt || '' }];
  }
  return forCourse;
}

/**
 * 과정의 수료증 행 + 이수 확정·미발급 행
 * @param {{ id: string, title?: string }} course
 * @returns {{ rows: object[], failedCount: number }} failedCount = 읽지 못한 수강생 수
 */
export async function loadCourseCredentialRows(course) {
  const enrollments = await listApprovedEnrollments(course.id);
  const results = await mapInChunks(enrollments, FETCH_CONCURRENCY, (enrollment) =>
    loadEnrollmentRows(enrollment, course).then(
      (rows) => ({ rows, failed: false }),
      () => ({ rows: [], failed: true }),
    ),
  );
  return {
    rows: markRetryable(sortRows(results.flatMap((item) => item.rows))),
    failedCount: results.filter((item) => item.failed).length,
  };
}

/** 학습자 한 명의 수료증 행 */
export async function loadLearnerCredentialRows(userId, learnerLabel) {
  const credentials = await listAllUserCredentials(userId);
  const rows = credentials.map((item) => toCredentialRow(item, { userId, learnerLabel, enrollmentId: null, completionId: null }));
  return markRetryable(sortRows(rows));
}

/**
 * 다시 발급할 이수 id — 수료증에는 completionId 가 없어 학습자의 수강(같은 과정·승인)에서 이수 기록을 찾는다.
 * 과정별 보기에서 이미 읽었으면 그것을 쓴다.
 */
export async function resolveCompletionId(row) {
  if (row.completionId) return row.completionId;
  const result = await listEnrollmentsOfUser(row.userId, { page: 0, size: ENROLLMENT_PAGE_SIZE });
  const enrollment = (result.data || []).find((item) => item.courseId === row.courseId && item.status === 'APPROVED');
  const completion = enrollment ? await findCompletion(enrollment.id) : null;
  if (!completion?.id) throw new Error('이 강의의 이수 기록을 찾지 못했습니다. 이수 처리 화면에서 확인해 주세요.');
  return completion.id;
}
