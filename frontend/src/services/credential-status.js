import { getCredential } from '@/services/credential-api';
import { credentialFailureReason } from '@/utils/credential-failure';

/* 수료증 발급·폐기·재발급은 202(접수) 뒤 서버가 비동기로 처리한다.
 * 접수 응답만 보고 "완료" 알림을 띄우면 FAILED 를 성공처럼 알린다(수료증 QA G5) —
 * 끝 상태가 될 때까지 GET /credentials/{id} 를 반복 조회하고, 그 결과로 알린다. */

const POLL_INTERVAL_MS = 1000;
const POLL_TIMEOUT_MS = 30000;

const SETTLED = {
  issue: ['ISSUED', 'FAILED'],
  revoke: ['REVOKED', 'FAILED'],
};

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * @param {object} accepted 202 응답 본문(CredentialResponse) — id·status
 * @param {'issue'|'revoke'} kind 발급·재발급은 issue, 폐기는 revoke
 * @returns {{ credential: object, timedOut: boolean }}
 */
export async function waitForCredentialSettled(accepted, kind) {
  const settled = SETTLED[kind];
  let credential = accepted;
  const deadline = Date.now() + POLL_TIMEOUT_MS;
  while (!settled.includes(credential?.status)) {
    if (!credential?.id || Date.now() >= deadline) return { credential, timedOut: true };
    await sleep(POLL_INTERVAL_MS);
    credential = await getCredential(credential.id);
  }
  return { credential, timedOut: false };
}

const DONE_MESSAGES = {
  issue: '수료증을 발급했습니다.',
  reissue: '수료증을 재발급했습니다.',
  revoke: '수료증을 폐기했습니다.',
};

const FAILED_MESSAGES = {
  issue: '수료증 발급에 실패했습니다.',
  reissue: '수료증 재발급에 실패했습니다.',
  revoke: '수료증 폐기에 실패했습니다.',
};
// failureCode 가 없을 때(배포 전 서버 등)의 원인 안내
const FAILED_FALLBACK_HINT = '블록체인 모니터링의 이상 징후에서 원인을 확인해 주세요.';

/** 끝 상태 → q-notify 옵션. action 은 issue·reissue·revoke */
export function settledNotice(action, { credential, timedOut }) {
  if (timedOut) {
    return {
      type: 'info',
      message: '요청은 접수됐고 아직 처리 중입니다. 잠시 뒤 목록을 새로고침해 확인해 주세요.',
    };
  }
  if (credential?.status === 'FAILED') {
    const reason = credentialFailureReason(credential.failureCode) || FAILED_FALLBACK_HINT;
    return {
      type: 'negative',
      message: `${FAILED_MESSAGES[action]} ${reason}`,
      timeout: 0,
      actions: [{ label: '닫기', color: 'white' }],
    };
  }
  return { type: 'positive', message: DONE_MESSAGES[action] };
}
