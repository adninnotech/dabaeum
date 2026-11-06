import { storedFileContentUrl } from '@/services/file-api';

/**
 * 목록 응답에 이미 담긴 기관명·강사명을 소비처가 쓰는 키로 매핑하고 썸네일 URL을 붙인다.
 */
export async function decorateCourseRows(rows) {
  return Promise.all(
    rows.map(async (row) => ({
      ...row,
      institutionName: row.institutionName || '',
      instructorName: row.mainInstructorName || '',
      thumbnailUrl: row.thumbnailFileId ? storedFileContentUrl(row.thumbnailFileId) : '',
    })),
  );
}
