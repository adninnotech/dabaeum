/* 임시저장(DRAFT) 강좌 지우기 — 내 강의 관리·기관·관리자 강의 목록이 같이 쓴다.
 * 강의 등록 위저드의 자동 저장·임시저장으로 생긴 강좌가 쌓이는데 지울 길이 없었다(백엔드 DELETE /courses/{id} 추가) */
import { deleteDraftCourse } from '@/services/course-api';
import { confirmDialog } from '@/utils/confirm-dialog';

/** 서버 오류 → 사람 말. 이미 지운 강좌(404)는 목록에서 빼면 되므로 '지워짐'으로 본다 */
function deleteErrorMessage(error) {
  if (error?.status === 409) return '이미 게시된 강좌라 지울 수 없습니다. 모집 마감·종료로 정리해 주세요.';
  if (error?.status === 403) return '이 강좌를 지울 권한이 없습니다.';
  if (error?.status === 405) return '아직 서버에 삭제 기능이 반영되지 않았습니다. 잠시 뒤 다시 시도해 주세요.';
  return error?.message || '임시저장 강좌를 지우지 못했습니다.';
}

/**
 * 확인 창 → 삭제. 지워졌으면(또는 이미 없으면) true, 취소·실패면 false. 결과 알림까지 여기서 한다
 * @param {object} $q Quasar
 * @param {{ id: string, title?: string }} course
 */
export async function confirmDeleteDraftCourse($q, course) {
  const ok = await confirmDialog($q, {
    title: '임시저장 강좌 삭제',
    message: `'${course.title || '제목 없는 강좌'}' 임시저장 강좌를 지웁니다. 입력한 내용·회차·첨부가 함께 사라지고 되돌릴 수 없습니다.`,
    ok: '삭제',
    color: 'negative',
    cancel: '닫기',
  });
  if (!ok) return false;
  try {
    await deleteDraftCourse(course.id);
    $q.notify({ type: 'positive', message: '임시저장 강좌를 지웠습니다.', position: 'top' });
    return true;
  } catch (error) {
    if (error?.status === 404) {
      $q.notify({ type: 'info', message: '이미 지워진 강좌입니다.', position: 'top' });
      return true;
    }
    $q.notify({ type: 'negative', message: deleteErrorMessage(error), position: 'top' });
    return false;
  }
}

export { deleteErrorMessage };
