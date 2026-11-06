import { issueCredential } from '@/services/credential-api';
import { waitForCredentialSettled } from '@/services/credential-status';
import { endOfDayKst, minValidUntilDate, VALID_UNTIL_HINT, validUntilError } from '@/utils/credential-validity';

/* 수료증 발급(POST /completions/{completionId}/credentials) 한 번의 흐름 — 유효기간 확인 창 → 접수 → 끝 상태까지 조회.
 * 이수 처리 화면과 같은 규칙(내일 이후 날짜만, 비우면 만료 없음)을 수료증 관리 화면에서도 쓰려고 뺐다. */

/**
 * 유효기간(선택)을 받는 확인 창. 취소하면 null, [발급]이면 'YYYY-MM-DD' 또는 ''(만료 없음)
 * @param {object} $q Quasar
 * @param {{ title: string, message: string, okLabel?: string }} options
 */
export function askCredentialValidity($q, { title, message, okLabel = '발급' }) {
  return new Promise((resolve) => {
    $q.dialog({
      title,
      message: `${message} ${VALID_UNTIL_HINT}`,
      prompt: {
        model: '',
        type: 'date',
        outlined: true,
        stackLabel: true,
        label: '유효기간 (선택)',
        min: minValidUntilDate(),
        // 오늘·지난 날짜면 [발급]이 꺼진다 — 발급 당일 밤 만료되는 실수를 막는다
        isValid: (value) => !validUntilError(value),
      },
      cancel: { flat: true, noCaps: true, label: '취소' },
      ok: { unelevated: true, noCaps: true, color: 'primary', label: okLabel },
    })
      .onOk((date) => resolve(date || ''))
      .onCancel(() => resolve(null));
  });
}

/** 발급 접수 후 ISSUED·FAILED 가 될 때까지 기다린다 — 202 는 접수일 뿐이라 바로 "완료"라고 알리면 안 된다 */
export async function issueCredentialAndWait(completionId, date) {
  const accepted = await issueCredential(completionId, { validUntil: endOfDayKst(date) });
  return waitForCredentialSettled(accepted, 'issue');
}
