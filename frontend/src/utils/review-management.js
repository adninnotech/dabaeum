/** 수강평 관리 — 강의명·강사명 검색 */
export function normalizeReviewSearchKeyword(keyword) {
  return String(keyword || '').trim().toLowerCase();
}

/**
 * @param {object} course
 * @param {string} keyword
 * @param {string[]} instructorNames
 */
export function courseMatchesReviewKeyword(course, keyword, instructorNames = []) {
  const q = normalizeReviewSearchKeyword(keyword);
  if (!q) return true;
  const title = String(course?.title || '').toLowerCase();
  const code = String(course?.courseCode || '').toLowerCase();
  if (title.includes(q) || code.includes(q)) return true;
  return (instructorNames || []).some((name) => String(name || '').toLowerCase().includes(q));
}

/**
 * @param {string[]} courseIds
 * @param {(courseId: string) => Promise<Array<{ instructorName?: string }>>} fetchInstructors
 */
export async function loadCourseInstructorNameMap(courseIds, fetchInstructors) {
  const map = {};
  const ids = Array.isArray(courseIds) ? courseIds.filter(Boolean) : [];
  await Promise.all(
    ids.map(async (id) => {
      try {
        const list = await fetchInstructors(id);
        map[id] = (Array.isArray(list) ? list : [])
          .map((item) => item.instructorName)
          .filter(Boolean);
      } catch {
        map[id] = [];
      }
    }),
  );
  return map;
}

/** 여러 과정 수강평 병합 (최신순) */
export function mergeCourseReviews(pages, courseTitleById = {}) {
  const merged = [];
  (pages || []).forEach(({ courseId, page }) => {
    const rows = Array.isArray(page?.data) ? page.data : [];
    rows.forEach((row) => {
      merged.push({
        ...row,
        courseTitle: row.courseTitle || courseTitleById[courseId] || '-',
      });
    });
  });
  merged.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));
  return merged;
}
