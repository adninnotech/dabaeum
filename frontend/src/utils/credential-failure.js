/* 수료증 발급 실패 코드(CredentialDetail.failureCode) → 관리자용 한글 사유.
 * 서버는 모든 역할에 같은 코드를 준다 — 학습자 화면은 코드 대신 일반 문구(LEARNER_FAILURE_TEXT)를 쓴다. */

const SIGNING_KEY = '발급 기관의 서명키가 없거나 사용할 수 없습니다. 서명키를 만든 뒤 다시 발급해 주세요.';
const SIGNING_SERVICE = '서명 서비스에 연결하지 못했습니다. 잠시 뒤 다시 발급해 주세요.';
const LEDGER = '블록체인 원장에 기록하지 못했습니다. 블록체인 모니터링을 확인한 뒤 다시 발급해 주세요.';
const LEDGER_STATE = '블록체인 원장 값이 기대와 다릅니다. 블록체인 모니터링에서 확인해 주세요.';
const COMPROMISED = '발급 기관의 서명키가 유출 의심으로 막혀 있습니다. 키를 정리한 뒤 다시 발급해 주세요.';
const KEY_VERSION = '서명 중에 서명키 버전이 바뀌었습니다. 다시 발급해 주세요.';

// 서버가 tb_credentials.failure_code 에 남기는 값만 둔다(백엔드 확인, 2026-10-01).
// 에뮬레이터 원본 LEDGER_* 는 서버가 BLOCKCHAIN_* 로 바꿔 저장하고, CREDENTIAL_SIGNING_STALE 은 FAILED 로 끝나지 않는다
const FAILURE_REASON = {
  // 서명
  CREDENTIAL_SIGNING_KEY_INACTIVE: SIGNING_KEY,
  SIGNING_KEY_INACTIVE_AT_FINALIZE: SIGNING_KEY,
  CREDENTIAL_SIGNING_KEY_COMPROMISED: COMPROMISED,
  COMPROMISED_AT_FINALIZE: COMPROMISED,
  CREDENTIAL_SIGNING_INSTITUTION_INACTIVE: '발급 기관이 비활성 상태라 서명할 수 없습니다.',
  CREDENTIAL_SIGNING_UNAVAILABLE: SIGNING_SERVICE,
  CREDENTIAL_SIGNING_REJECTED: '서명 요청이 거부되었습니다. 서명키 상태를 확인해 주세요.',
  CREDENTIAL_SIGNING_VERSION_REJECTED: KEY_VERSION,
  CREDENTIAL_SIGNATURE_VERSION_MISMATCH: KEY_VERSION,
  CREDENTIAL_PROOF_GENERATION_FAILED: '수료증 서명을 만들지 못했습니다. 잠시 뒤 다시 발급해 주세요.',
  // 발급 문서
  CREDENTIAL_HASH_MISMATCH: '수료증 문서 해시가 맞지 않아 발급을 멈췄습니다. 다시 발급해 주세요.',
  // 원장
  BLOCKCHAIN_SUBMIT_FAILED: LEDGER,
  BLOCKCHAIN_CONNECTION_FAILED: LEDGER,
  BLOCKCHAIN_COMMIT_TIMEOUT: LEDGER,
  BLOCKCHAIN_READ_FAILED: LEDGER,
  BLOCKCHAIN_RESPONSE_INVALID: '블록체인 원장 응답이 올바르지 않습니다(인증·형식). 원장 연결 설정을 확인해 주세요.',
  BLOCKCHAIN_COMMIT_INVALID: LEDGER_STATE,
  BLOCKCHAIN_LEDGER_CONFLICT: LEDGER_STATE,
  BLOCKCHAIN_NOT_FOUND: '블록체인 원장에 기록이나 프로젝트가 없습니다. 원장 설정을 확인해 주세요.',
  BLOCKCHAIN_ALREADY_EXISTS: '블록체인 원장에 같은 기록이 이미 있습니다. 블록체인 모니터링에서 확인해 주세요.',
  BLOCKCHAIN_WRITE_NOT_APPROVED: '지금 설정에서는 블록체인 원장에 쓸 수 없습니다. 운영 설정을 확인해 주세요.',
  BLOCKCHAIN_UNSUPPORTED_REFERENCE: '옛 원장 기록이라 지금 원장으로 처리할 수 없습니다. 관리자에게 문의해 주세요.',
  BLOCKCHAIN_COMPENSATION_PENDING: '원장 되돌리기 처리가 아직 끝나지 않았습니다. 잠시 뒤 상태를 다시 확인해 주세요.',
};

export const LEARNER_FAILURE_TEXT = '발급하지 못했습니다. 발급 기관에 문의해 주세요.';

/** 관리자 화면용 사유 — 모르는 코드는 코드를 그대로 붙여 원인 추적이 되게 한다. 코드가 없으면 '' */
export function credentialFailureReason(code) {
  if (!code) return '';
  return FAILURE_REASON[code] || `발급 중 오류가 났습니다 (${code}).`;
}
