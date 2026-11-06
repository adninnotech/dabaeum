/* 서버 오류 응답 → 사용자에게 보일 한국어 문장.
 * 서버 message 는 영어("Completion cannot be evaluated in its current state")라 그대로 띄우면 안 된다.
 * 우선순위: 서버 문구가 이미 한국어면 그대로 → 오류 코드(ApiErrorCode) 문구 → HTTP 상태 문구.
 * 코드 목록: 백엔드 common/api/ApiErrorCode.java (2026-10-02 기준 101개) */

const CODE_MESSAGE = {
  // 공통
  BAD_REQUEST: '요청 형식이 올바르지 않습니다.',
  VALIDATION_FAILED: '입력한 내용을 다시 확인해 주세요. 빠졌거나 형식이 맞지 않는 항목이 있습니다.',
  UNAUTHORIZED: '로그인이 필요합니다. 다시 로그인해 주세요.',
  FORBIDDEN: '이 작업을 할 권한이 없습니다.',
  ENDPOINT_NOT_FOUND: '요청한 기능을 찾을 수 없습니다.',
  INTERNAL_SERVER_ERROR: '서버에서 오류가 났습니다. 잠시 뒤 다시 시도해 주세요.',
  INSTITUTION_SCOPE_FORBIDDEN: '다른 기관의 데이터에는 접근할 수 없습니다.',

  // 계정·본인 확인
  AUTHENTICATION_FAILED: '이메일 또는 비밀번호가 올바르지 않습니다.',
  ACCOUNT_LOCKED: '로그인 실패가 반복되어 계정이 잠겼습니다. 비밀번호 찾기로 잠금을 풀어 주세요.',
  USER_NOT_FOUND: '회원 정보를 찾을 수 없습니다.',
  USER_STATUS_CONFLICT: '지금 회원 상태에서는 이 작업을 할 수 없습니다.',
  USER_STATUS_FORBIDDEN: '이용이 제한된 계정입니다. 관리자에게 문의해 주세요.',
  PASSWORD_RESET_TOKEN_INVALID: '비밀번호 재설정 링크가 만료되었거나 올바르지 않습니다. 다시 요청해 주세요.',
  IDENTITY_NOT_FOUND: '본인 확인 정보를 찾을 수 없습니다.',
  IDENTITY_CONFLICT: '이미 다른 계정에 연결된 본인 확인 정보입니다.',
  IDENTITY_REQUIRED: '본인 확인이 필요합니다.',
  IDENTITY_NOT_VERIFIED: '본인 확인이 끝나지 않았습니다.',
  ROLE_NOT_FOUND: '역할 정보를 찾을 수 없습니다.',
  ROLE_ASSIGNMENT_CONFLICT: '이미 부여된 역할입니다.',
  ROLE_REQUIRED: '이 작업에 필요한 역할이 없습니다.',

  // 기관
  INSTITUTION_NOT_FOUND: '기관을 찾을 수 없습니다.',
  INSTITUTION_CODE_CONFLICT: '이미 사용 중인 기관 코드입니다.',
  INSTITUTION_APPLICATION_NOT_FOUND: '기관 등록 신청을 찾을 수 없습니다.',
  INSTITUTION_APPLICATION_CONFLICT: '이미 심사 중인 기관 등록 신청이 있습니다.',
  INSTITUTION_APPLICATION_STATUS_CONFLICT: '이미 처리된 기관 등록 신청입니다.',

  // 강사
  INSTRUCTOR_APPLICATION_NOT_FOUND: '강사 신청을 찾을 수 없습니다.',
  INSTRUCTOR_APPLICATION_CONFLICT: '이미 이 기관에 강사 신청을 했습니다.',
  INSTRUCTOR_APPLICATION_STATUS_CONFLICT: '이미 처리된 강사 신청입니다.',

  // 강좌
  COURSE_NOT_FOUND: '강좌를 찾을 수 없습니다.',
  COURSE_CONFLICT: '같은 정보의 강의가 이미 있습니다. 강의 코드를 확인해 주세요.',
  COURSE_STATUS_CONFLICT: '지금 강의 상태에서는 이 작업을 할 수 없습니다.',
  COURSE_INSTRUCTOR_NOT_FOUND: '강의 강사 배정을 찾을 수 없습니다.',
  COURSE_INSTRUCTOR_CONFLICT: '이미 배정된 강사이거나 배정을 바꿀 수 없는 상태입니다.',
  COURSE_SESSION_NOT_FOUND: '회차를 찾을 수 없습니다.',
  COURSE_SESSION_CONFLICT: '회차 일정이 겹치거나 지금 바꿀 수 없는 회차입니다.',
  COURSE_ATTACHMENT_NOT_FOUND: '강의 첨부파일을 찾을 수 없습니다.',
  COURSE_ATTACHMENT_CONFLICT: '이미 등록된 강의 첨부파일입니다.',
  COURSE_CAPACITY_EXCEEDED: '정원이 가득 찼습니다.',
  COURSE_RECRUITMENT_ENDED: '모집이 마감된 강좌입니다.',
  COURSE_CLASSIFICATION_NOT_FOUND: '강좌 분류를 찾을 수 없습니다. 분류를 다시 골라 주세요.',
  CORRECTION_NOT_ALLOWED: '지금은 고칠 수 없는 상태입니다.',

  // 수강
  ENROLLMENT_NOT_FOUND: '수강 신청을 찾을 수 없습니다.',
  ENROLLMENT_CONFLICT: '이미 신청한 강좌입니다.',
  ENROLLMENT_STATUS_CONFLICT: '지금 수강 상태에서는 이 작업을 할 수 없습니다.',
  ENROLLMENT_WITHDRAW_AFTER_COMPLETION: '이수 평가가 끝난 수강은 철회할 수 없습니다. 운영기관에 문의해 주세요.',
  ENROLLMENT_WITHDRAW_REQUIRES_MANAGER: '수업 시작 24시간 전이 지났거나 출석한 수업이 있어 직접 철회할 수 없습니다. 운영기관에 문의해 주세요.',

  // 출결
  ATTENDANCE_NOT_FOUND: '출결 기록을 찾을 수 없습니다.',
  ATTENDANCE_CONFLICT: '이미 출결이 기록되어 있습니다.',
  ATTENDANCE_WINDOW_CLOSED: '출석할 수 있는 시간이 아닙니다.',
  ATTENDANCE_QR_INVALID: '올바르지 않은 출석 QR입니다. 강사에게 새 QR을 요청해 주세요.',
  ATTENDANCE_QR_EXPIRED: '출석 QR이 만료되었습니다. 강사 화면의 새 QR을 다시 찍어 주세요.',
  ATTEND_SESSION_NOT_FOUND: '출석할 회차를 찾을 수 없습니다.',
  ATTENDANCE_METHOD_NOT_SUPPORTED: '이 회차에서 쓸 수 없는 출석 방식입니다.',
  ATTENDANCE_DADAEGU_REQUIRED: '다대구 본인 확인 후 출석할 수 있습니다.',

  // 이수
  COMPLETION_NOT_FOUND: '이수 정보를 찾을 수 없습니다. 출석 평가를 먼저 해 주세요.',
  // 평가는 평가 대기·이수 가능·미이수일 때만 — 확정(COMPLETED)·취소(CANCELLED)된 건은 막힌다
  COMPLETION_STATUS_CONFLICT: '이미 이수가 확정되었거나 취소된 건이라 이 작업을 할 수 없습니다. 새로고침해 상태를 확인해 주세요.',
  COMPLETION_METRICS_CONFLICT: '이수 평가 값(출석률·이수 시간)이 기준과 맞지 않습니다.',
  COMPLETION_NOT_CONFIRMED: '이수가 확정되지 않았습니다. 이수 확정 후 다시 시도해 주세요.',

  // 수료증·배지
  CREDENTIAL_NOT_FOUND: '수료증을 찾을 수 없습니다.',
  CREDENTIAL_ALREADY_EXISTS: '이미 발급된 수료증이 있습니다.',
  CREDENTIAL_STATE_CONFLICT: '지금 수료증 상태에서는 이 작업을 할 수 없습니다.',
  CREDENTIAL_IDEMPOTENCY_CONFLICT: '같은 요청이 이미 처리 중입니다. 잠시 뒤 목록을 새로고침해 주세요.',
  CREDENTIAL_PROOF_GENERATION_FAILED: '수료증 서명을 만들지 못했습니다. 잠시 뒤 다시 시도해 주세요.',
  CREDENTIAL_PROOF_INVALID: '수료증 서명이 올바르지 않습니다.',
  CREDENTIAL_HASH_MISMATCH: '수료증 문서가 발급 내용과 맞지 않습니다.',
  CREDENTIAL_VERIFICATION_FAILED: '수료증을 검증하지 못했습니다.',
  BADGE_NOT_FOUND: '학습배지를 찾을 수 없습니다.',
  BADGE_CONFLICT: '이미 발급된 학습배지입니다.',
  BADGE_DEFINITION_NOT_FOUND: '배지 정의를 찾을 수 없습니다.',
  BADGE_DEFINITION_CONFLICT: '같은 이름이나 조건의 배지 정의가 이미 있습니다.',
  BADGE_DEFINITION_IN_USE: '이미 발급된 배지가 있어 이 배지 정의를 바꾸거나 지울 수 없습니다.',
  PORTFOLIO_SHARE_NOT_FOUND: '공유 링크를 찾을 수 없습니다.',

  // 블록체인·서명키
  FABRIC_SUBMIT_FAILED: '블록체인 원장에 기록하지 못했습니다. 잠시 뒤 다시 시도해 주세요.',
  FABRIC_COMMIT_TIMEOUT: '블록체인 원장 기록이 늦어지고 있습니다. 잠시 뒤 상태를 다시 확인해 주세요.',
  FABRIC_COMMIT_INVALID: '블록체인 원장 기록이 올바르지 않습니다. 블록체인 모니터링을 확인해 주세요.',
  FABRIC_LEDGER_CONFLICT: '블록체인 원장 값이 기대와 다릅니다. 블록체인 모니터링을 확인해 주세요.',
  SIGNING_KEY_NOT_FOUND: '발급 기관의 서명키가 없습니다. 관리자에게 서명키 생성을 요청해 주세요.',
  SIGNING_KEY_ALREADY_EXISTS: '이미 서명키가 있습니다.',
  SIGNING_KEY_STATE_CONFLICT: '지금 서명키 상태에서는 이 작업을 할 수 없습니다.',
  SIGNING_KEY_APPROVAL_REJECTED: '서명키 작업 승인이 거부되었습니다.',
  SIGNING_KEY_IDEMPOTENCY_CONFLICT: '같은 서명키 요청이 이미 처리 중입니다.',
  KMS_UNAVAILABLE: '서명 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해 주세요.',

  // 운영 콘텐츠
  NOTICE_NOT_FOUND: '공지사항을 찾을 수 없습니다.',
  NOTICE_ATTACHMENT_NOT_FOUND: '공지 첨부파일을 찾을 수 없습니다.',
  NOTICE_ATTACHMENT_CONFLICT: '이미 등록된 공지 첨부파일입니다.',
  FAQ_NOT_FOUND: 'FAQ를 찾을 수 없습니다.',
  TERMS_CONTENT_CONFLICT: '같은 버전의 약관이 이미 있습니다.',
  BANNER_NOT_FOUND: '배너를 찾을 수 없습니다.',
  CONTENT_PAGE_NOT_FOUND: '콘텐츠 페이지를 찾을 수 없습니다.',
  CONTENT_PAGE_CONFLICT: '같은 주소의 콘텐츠 페이지가 이미 있습니다.',
  CONTENT_PAGE_IN_USE: '메뉴에 연결된 콘텐츠 페이지라 지울 수 없습니다. 메뉴에서 먼저 빼 주세요.',
  SITE_MENU_NOT_FOUND: '메뉴를 찾을 수 없습니다.',
  SITE_MENU_HAS_CHILDREN: '하위 메뉴가 있어 지울 수 없습니다. 하위 메뉴를 먼저 정리해 주세요.',
  INQUIRY_NOT_FOUND: '문의를 찾을 수 없습니다.',
  INQUIRY_STATUS_CONFLICT: '이미 답변했거나 닫힌 문의입니다.',
  REVIEW_NOT_FOUND: '수강평을 찾을 수 없습니다.',
  REVIEW_CONFLICT: '이미 이 강좌에 수강평을 남겼습니다.',
  REVIEW_NOT_ELIGIBLE: '수강을 마친 강좌에만 수강평을 남길 수 있습니다.',
  INTEREST_NOT_FOUND: '관심 강좌를 찾을 수 없습니다.',
  INTEREST_CONFLICT: '이미 관심 강좌에 담았습니다.',
  NOTIFICATION_NOT_FOUND: '알림을 찾을 수 없습니다.',

  // 파일
  FILE_NOT_FOUND: '파일을 찾을 수 없습니다.',
  FILE_TYPE_NOT_SUPPORTED: '올릴 수 없는 파일 형식입니다.',
  FILE_SIZE_EXCEEDED: '파일이 너무 큽니다. 용량 제한을 확인해 주세요.',
};

const STATUS_MESSAGE = {
  400: '요청 형식이 올바르지 않습니다.',
  401: '로그인이 필요합니다. 다시 로그인해 주세요.',
  403: '이 작업을 할 권한이 없습니다.',
  404: '요청한 정보를 찾을 수 없습니다.',
  409: '지금 상태에서는 이 작업을 할 수 없습니다. 새로고침해 최신 상태를 확인해 주세요.',
  422: '입력한 내용을 다시 확인해 주세요.',
  429: '요청이 너무 많습니다. 잠시 뒤 다시 시도해 주세요.',
  503: '서비스를 잠시 쓸 수 없습니다. 잠시 뒤 다시 시도해 주세요.',
};

/* 같은 코드라도 서버 message 로 이유가 갈리는 것 — 'code|message' 로 먼저 찾는다.
 * COMPLETION_STATUS_CONFLICT 는 '이미 확정·취소'만이 아니라 '끝나지 않은 회차가 있음'도 같은 코드라,
 * 출석 평가를 눌렀는데 '이미 이수가 확정되었거나 취소된 건'으로 잘못 알렸다(백엔드 DefaultCompletionApplicationService) */
const DETAIL_MESSAGE = {
  'COMPLETION_STATUS_CONFLICT|Attendance sessions are not closed':
    '아직 출석이 끝나지 않은 회차가 있어 평가할 수 없습니다. 모든 회차의 출석이 마감된 뒤 평가해 주세요.',
  'COMPLETION_STATUS_CONFLICT|Completion state changed concurrently':
    '다른 곳에서 먼저 처리했습니다. 새로고침해 상태를 확인해 주세요.',
};

/* VALIDATION_FAILED 의 details 에 항목 이름이 오면 어느 칸인지 알린다 — 백엔드는 details ['birthDate'] 처럼 보낸다 */
export const BIRTH_DATE_INVALID = '생년월일을 확인해 주세요. 오늘이나 그 이전 날짜만 넣을 수 있습니다.';
const FIELD_MESSAGE = {
  birthDate: BIRTH_DATE_INVALID,
  recruitEndDate: '모집 마감일은 강의 시작일 이전이어야 합니다. 같은 날은 됩니다.',
};

function fieldMessage(payload) {
  if (payload?.code !== 'VALIDATION_FAILED' || !Array.isArray(payload.details)) return '';
  const fields = payload.details.map((item) => (typeof item === 'string' ? item : item?.field));
  const known = fields.find((field) => FIELD_MESSAGE[field]);
  return known ? FIELD_MESSAGE[known] : '';
}

const HANGUL = /[가-힣]/;

/**
 * @param {object|null} payload 오류 응답 본문({ code, message, details })
 * @param {number} status HTTP 상태
 * @returns {string} 사용자에게 보일 한국어 문장
 */
export function apiErrorMessage(payload, status) {
  const serverMessage = payload && (payload.message || payload.error || payload.title);
  if (typeof serverMessage === 'string' && HANGUL.test(serverMessage)) return serverMessage;
  const byField = fieldMessage(payload);
  if (byField) return byField;
  const byDetail = payload?.code && DETAIL_MESSAGE[`${payload.code}|${serverMessage}`];
  if (byDetail) return byDetail;
  const byCode = payload?.code && CODE_MESSAGE[payload.code];
  if (byCode) return byCode;
  if (STATUS_MESSAGE[status]) return STATUS_MESSAGE[status];
  if (status >= 500) return CODE_MESSAGE.INTERNAL_SERVER_ERROR;
  return `요청을 처리하지 못했습니다. (${status})`;
}
