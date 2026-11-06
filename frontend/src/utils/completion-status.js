/* 이수 상태 칸 공통 — 강사 '수강 현황 관리'와 강의 상세 '수강 현황' 탭이 같이 쓴다 */
import { getCompletion } from '@/services/completion-api';
import { COMPLETION_STATUS_LABEL } from '@/utils/status';

/** 이수 기록 — null = 아직 출석 평가 전(404, 오류 아님), undefined = 조회 실패 */
export async function completionOrNull(enrollmentId) {
  try {
    return await getCompletion(enrollmentId);
  } catch (error) {
    return error?.status === 404 ? null : undefined;
  }
}

export function completionKey(row) {
  if (row.completion === undefined) return '';
  return row.completion?.status || 'NONE';
}

/** 실패를 '-' 로 두면 '평가 전'과 헷갈렸다 — '확인 불가'로 구분한다 */
export function completionLabel(row) {
  if (row.completion === undefined) return '확인 불가';
  if (!row.completion) return '평가 전';
  return COMPLETION_STATUS_LABEL[row.completion.status] || row.completion.status;
}

/** 조회 실패 건수 → 배너 문구('' = 없음) */
export function completionFailureText(rows) {
  const failed = rows.filter((row) => row.completion === undefined).length;
  return failed ? `${failed}명의 이수 정보를 불러오지 못했습니다. 새로고침해 주세요.` : '';
}
