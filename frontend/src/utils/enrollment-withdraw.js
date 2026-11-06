/* 운영 콘솔의 수강 철회 — 기관 관리자·이 강의 주강사·플랫폼 관리자가 승인된 수강을 철회한다.
 * '신청 취소'(POST /cancel)는 승인 전 신청만 받는다 — 승인 건에 [신청 취소]를 보여 서버가 거부했다(QA ENR-16·ENR-14).
 * 결정 권한자는 본인 철회 마감·출석과 상관없이 철회할 수 있고, 이수 평가·확정 뒤엔 서버가 409 로 막는다 */
import { withdrawEnrollment } from '@/services/enrollment-api';
import { confirmDialog } from '@/utils/confirm-dialog';

/** 철회할 수 있는 상태 */
export const WITHDRAWABLE_STATUSES = ['APPROVED'];

/** 묻고 철회한다. 철회했으면 true — 실패는 알림을 띄우고 false */
export async function confirmAndWithdraw($q, { enrollmentId, name }) {
  const who = name && name !== '-' ? `${name} 님` : '이 수강생';
  const ok = await confirmDialog($q, {
    title: '수강 철회',
    message: `${who}의 수강을 철회합니다. 정원이 한 자리 비지만 대기자가 자동으로 올라가지는 않습니다. 출결 기록은 남습니다.`,
    ok: '수강 철회',
    color: 'negative',
    cancel: '닫기',
  });
  if (!ok) return false;
  try {
    await withdrawEnrollment(enrollmentId);
    $q.notify({ type: 'positive', message: `${who}의 수강을 철회했습니다.`, position: 'top' });
    return true;
  } catch (error) {
    $q.notify({ type: 'negative', message: error.message || '수강을 철회하지 못했습니다.', position: 'top' });
    return false;
  }
}
