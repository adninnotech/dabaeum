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

/** API status enum → 한글 라벨 (StatusBadge 공통) */
export const STATUS_LABEL = {
  DRAFT: '작성중',
  RECRUITING: '모집중',
  RECRUITMENT_CLOSED: '모집마감',
  IN_PROGRESS: '진행중',
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
};

/** @deprecated STATUS_LABEL 사용 권장 */
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

/** 사용자 계정 상태 (OpenAPI User.status) */
export const USER_STATUS_OPTIONS = [
  { label: STATUS_LABEL.ACTIVE, value: 'ACTIVE' },
  { label: STATUS_LABEL.DORMANT, value: 'DORMANT' },
  { label: STATUS_LABEL.SUSPENDED, value: 'SUSPENDED' },
  { label: STATUS_LABEL.WITHDRAWN, value: 'WITHDRAWN' },
];

/** 강의 교육 유형 (OpenAPI educationType) */
export const EDUCATION_TYPE_LABEL = {
  ONLINE: '온라인',
  OFFLINE: '오프라인',
  HYBRID: '혼합',
};

export const educationTypeColor = {
  ONLINE: 'info',
  OFFLINE: 'teal',
  HYBRID: 'deep-purple',
};

export const EDUCATION_TYPE_OPTIONS = [
  { label: '온라인', value: 'ONLINE' },
  { label: '오프라인', value: 'OFFLINE' },
  { label: '혼합', value: 'HYBRID' },
];

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

export function formatCourseOption(course) {
  if (!course) return '';
  return `${course.courseCode || ''} ${course.title || course.id}`.trim();
}
