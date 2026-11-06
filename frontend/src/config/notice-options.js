/** 공지 대상 (Notice.audience) */
export const AUDIENCE_OPTIONS = [
  { label: '전체', value: 'PUBLIC' },
  { label: '강사', value: 'INSTRUCTOR' },
  { label: '기관', value: 'INSTITUTION' },
];

export const AUDIENCE_LABEL = AUDIENCE_OPTIONS.reduce(
  (map, item) => ({ ...map, [item.value]: item.label }),
  {},
);

/** 공지 상태 */
export const NOTICE_STATUS_OPTIONS = [
  { label: '게시', value: 'PUBLISHED' },
  { label: '임시저장', value: 'DRAFT' },
];
