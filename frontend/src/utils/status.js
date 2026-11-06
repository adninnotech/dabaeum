import { filterEducationTypeOptions } from '@/config/features';

/** 상태 → Quasar badge color */
export const statusColor = {
  RECRUITING: 'positive',
  DRAFT: 'grey',
  RECRUITMENT_CLOSED: 'warning',
  IN_PROGRESS: 'info',
  COMPLETED: 'primary',
  CANCELLED: 'negative',
  APPLIED: 'info',
  WAITLISTED: 'warning',
  APPROVED: 'positive',
  REJECTED: 'negative',
  WITHDRAWN: 'grey',
  PRESENT: 'positive',
  LATE: 'warning',
  ABSENT: 'negative',
  EXCUSED: 'info',
  ELIGIBLE: 'positive',
  PENDING_EVALUATION: 'warning',
  NOT_COMPLETED: 'negative',
  ISSUED: 'positive',
  PENDING: 'warning',
  ANSWERED: 'positive',
  ISSUING: 'info',
  FAILED: 'negative',
  REVOKED: 'negative',
  SUPERSEDED: 'grey',
  EXPIRED: 'grey',
  ACTIVE: 'positive',
  DORMANT: 'grey',
  SUSPENDED: 'negative',
  INACTIVE: 'grey',
  OPEN: 'positive',
  SCHEDULED: 'info',
  OK: 'positive',
};

/**
 * 상태 → 배지 hex. `statusColor`(Quasar 색 이름)는 q-badge를 직접 쓰는 화면이 있어 그대로 둔다.
 * 값은 src/css/tokens.scss의 --db-status-* 와 같다.
 */
export const STATUS_HEX = {
  // Course
  DRAFT: '#727379',
  RECRUITING: '#2a5fcc',
  RECRUITMENT_CLOSED: '#727379',
  IN_PROGRESS: '#2a5fcc',
  COMPLETED: '#237a54',
  CANCELLED: '#c72656',
  // Enrollment
  PENDING: '#a65300',
  APPLIED: '#a65300',
  WAITLISTED: '#a65300',
  APPROVED: '#237a54',
  REJECTED: '#c72656',
  WITHDRAWN: '#c72656',
  // Attendance — 빠져 있어 출석·지각·결석·공결이 전부 같은 회색이었다
  PRESENT: '#237a54',
  LATE: '#a65300',
  ABSENT: '#c72656',
  EXCUSED: '#2a5fcc',
  // Completion 판정 — 같은 이유로 회색이었다
  ELIGIBLE: '#237a54',
  PENDING_EVALUATION: '#a65300',
  NOT_COMPLETED: '#c72656',
  // Inquiry
  ANSWERED: '#237a54',
  // Credential·Badge — 유효(초록)와 무효(대체 회색·폐기 빨강)가 색으로 갈려야 한다
  ISSUING: '#2a5fcc',
  ISSUED: '#237a54',
  SUPERSEDED: '#727379',
  REVOKED: '#c72656',
  FAILED: '#c72656',
  // EducationType
  ONLINE: '#2a5fcc',
  OFFLINE: '#237a54',
  HYBRID: '#8a38f5',
  // User status (WITHDRAWN은 수강신청의 '신청 취소'와 값이 겹쳐
  // 회원 목록에서만 user-admin.js의 로컬 맵으로 gray를 덮는다)
  ACTIVE: '#237a54',
  DORMANT: '#a65300',
  SUSPENDED: '#c72656',
  INACTIVE: '#727379',
};

export function hexForStatus(status, fallback = '#727379') {
  return STATUS_HEX[status] || fallback;
}

/** API status enum → 한글 라벨 (StatusBadge 공통) */
export const STATUS_LABEL = {
  DRAFT: '비공개',
  RECRUITING: '모집중',
  RECRUITMENT_CLOSED: '모집마감',
  IN_PROGRESS: '수강중',
  COMPLETED: '종료',
  CANCELLED: '취소',
  APPLIED: '신청',
  WAITLISTED: '대기',
  APPROVED: '승인',
  REJECTED: '거절',
  WITHDRAWN: '철회',
  PENDING: '대기',
  PRESENT: '출석',
  LATE: '지각',
  ABSENT: '결석',
  EXCUSED: '공결',
  PENDING_EVALUATION: '평가대기',
  ELIGIBLE: '이수가능',
  NOT_COMPLETED: '미이수',
  ISSUING: '발급중',
  ISSUED: '발급완료',
  FAILED: '실패',
  REVOKED: '폐기',
  SUPERSEDED: '대체됨',
  EXPIRED: '만료',
  ACTIVE: '활성',
  DORMANT: '휴면',
  SUSPENDED: '정지',
  INACTIVE: '비활성',
  OPEN: '진행',
  SCHEDULED: '예정',
  OK: '정상',
  ANSWERED: '답변완료',
};

/** @deprecated STATUS_LABEL 사용 권장 */
/**
 * 이수(Completion) 상태 — COMPLETED 가 과정·회차의 COMPLETED(종료)와 이름이 같아
 * STATUS_LABEL 로 보이면 이수 확정이 "종료"로 읽힌다. 이수 화면은 이 표를 쓴다
 */
export const COMPLETION_STATUS_LABEL = {
  PENDING_EVALUATION: '평가대기',
  ELIGIBLE: '이수가능',
  COMPLETED: '이수 완료',
  NOT_COMPLETED: '미이수',
  CANCELLED: STATUS_LABEL.CANCELLED,
};

/** 수강신청 유형(Enrollment.applicationType) */
export const APPLICATION_TYPE_LABEL = {
  SELF: '본인 신청',
  ADMIN_PROXY: '관리자 대리 신청',
  EXTERNAL_SYNC: '외부 연동',
};

/** 출결 방법(Attendance.attendanceMethod) */
export const ATTENDANCE_METHOD_LABEL = {
  QR: 'QR 출석',
  QR_DADAEGU: '다대구 앱 QR',
  ADMIN: '직접 등록',
  EXTERNAL: '외부 연동',
};

/** 출결 기록 경로(Attendance.source) */
export const ATTENDANCE_SOURCE_LABEL = {
  APP: '학습자 앱',
  ADMIN_WEB: '관리 화면',
  EXTERNAL_API: '외부 시스템',
};

export const COURSE_STATUS_LABEL = {
  DRAFT: STATUS_LABEL.DRAFT,
  RECRUITING: STATUS_LABEL.RECRUITING,
  RECRUITMENT_CLOSED: STATUS_LABEL.RECRUITMENT_CLOSED,
  IN_PROGRESS: STATUS_LABEL.IN_PROGRESS,
  COMPLETED: STATUS_LABEL.COMPLETED,
  CANCELLED: STATUS_LABEL.CANCELLED,
};

export const SESSION_STATUS_OPTIONS = [
  { label: STATUS_LABEL.SCHEDULED, value: 'SCHEDULED' },
  { label: STATUS_LABEL.OPEN, value: 'OPEN' },
  { label: STATUS_LABEL.COMPLETED, value: 'COMPLETED' },
  { label: STATUS_LABEL.CANCELLED, value: 'CANCELLED' },
];

export const APPROVAL_FILTER_OPTIONS = [
  { label: '전체', value: '' },
  { label: STATUS_LABEL.PENDING, value: 'PENDING' },
  { label: STATUS_LABEL.APPROVED, value: 'APPROVED' },
  { label: STATUS_LABEL.REJECTED, value: 'REJECTED' },
];

export const INSTRUCTOR_APPLICATION_FILTER_OPTIONS = [
  { label: STATUS_LABEL.PENDING, value: 'PENDING' },
  { label: STATUS_LABEL.APPROVED, value: 'APPROVED' },
  { label: STATUS_LABEL.REJECTED, value: 'REJECTED' },
  { label: '전체', value: null },
];

/** 기관 수강 관리 상태 필터 (GET /institutions/{id}/enrollments?status=) */
export const ENROLLMENT_FILTER_OPTIONS = [
  { label: '전체', value: null },
  { label: STATUS_LABEL.APPLIED, value: 'APPLIED' },
  { label: STATUS_LABEL.WAITLISTED, value: 'WAITLISTED' },
  { label: STATUS_LABEL.APPROVED, value: 'APPROVED' },
  { label: STATUS_LABEL.REJECTED, value: 'REJECTED' },
  { label: STATUS_LABEL.CANCELLED, value: 'CANCELLED' },
  { label: '신청 철회', value: 'WITHDRAWN' },
];

/**
 * 사용자 계정 상태 라벨 (OpenAPI User.status) — WITHDRAWN 은 수강 신청에선 '철회'지만 회원에겐 '탈퇴'다.
 * 전역 STATUS_LABEL 을 그대로 쓰니 /admin/users 에서 탈퇴 회원이 '철회'로 보였다(QA 중앙 #53)
 */
export const USER_STATUS_LABEL = {
  ACTIVE: STATUS_LABEL.ACTIVE,
  DORMANT: STATUS_LABEL.DORMANT,
  SUSPENDED: STATUS_LABEL.SUSPENDED,
  INACTIVE: STATUS_LABEL.INACTIVE,
  WITHDRAWN: '탈퇴',
};

export const USER_STATUS_OPTIONS = ['ACTIVE', 'DORMANT', 'SUSPENDED', 'WITHDRAWN'].map((value) => ({
  label: USER_STATUS_LABEL[value],
  value,
}));

/** 강의 교육 유형 (OpenAPI educationType) — 전체 정의 유지 */
export const EDUCATION_TYPE_LABEL = {
  ONLINE: '온라인',
  OFFLINE: '오프라인',
  HYBRID: '하이브리드',
};

export const educationTypeColor = {
  ONLINE: 'info',
  OFFLINE: 'teal',
  HYBRID: 'deep-purple',
};

/** 전체 옵션 (ONLINE 포함). UI 선택용은 getEducationTypeOptions() 사용 */
export const EDUCATION_TYPE_OPTIONS_ALL = [
  { label: EDUCATION_TYPE_LABEL.ONLINE, value: 'ONLINE' },
  { label: EDUCATION_TYPE_LABEL.OFFLINE, value: 'OFFLINE' },
  { label: EDUCATION_TYPE_LABEL.HYBRID, value: 'HYBRID' },
];

/**
 * UI 선택용 — 기능 플래그에 따라 ONLINE/HYBRID 제외
 * (하위 호환 alias. 새 코드는 getEducationTypeOptions() 사용)
 */
export const EDUCATION_TYPE_OPTIONS = filterEducationTypeOptions(EDUCATION_TYPE_OPTIONS_ALL);

export function getEducationTypeOptions() {
  return filterEducationTypeOptions(EDUCATION_TYPE_OPTIONS_ALL);
}

export function colorForStatus(status) {
  return statusColor[status] || 'grey';
}

export function statusLabel(status) {
  if (!status) return '-';
  return STATUS_LABEL[status] || status;
}

export function educationTypeLabel(type) {
  if (!type) return '-';
  return EDUCATION_TYPE_LABEL[type] || type;
}

export function colorForEducationType(type) {
  return educationTypeColor[type] || 'grey';
}

/** 성별 → 표기 (생년월일 옆 괄호). UNDISCLOSED는 빈 문자열 — 괄호를 찍지 않는다 */
export const GENDER_LABEL = { MALE: '(남)', FEMALE: '(여)', UNDISCLOSED: '' };

/**
 * 문의 유형(C-6) 배지 라벨.
 * TODO(figma): 문의 유형 배지 색 미확보 — 원시 hex 대신 기존 토큰(--db-accent/--db-text-sub)으로 지정
 */
export const INQUIRY_TYPE_LABEL = { COURSE: '강의 문의', QNA: 'Q&A' };

export function formatCourseOption(course) {
  if (!course) return '';
  // 강의명이 앞 — 코드가 앞이라 좁은 선택 칸에서 강의명이 잘렸다
  const title = course.title || course.id;
  return course.courseCode ? `${title} · ${course.courseCode}` : title;
}
