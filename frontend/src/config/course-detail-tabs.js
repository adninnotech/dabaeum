/** 강의 상세 콘솔 탭. value가 `?tab=` 쿼리 값이다. */
export const COURSE_DETAIL_TABS = [
  { label: '강의 정보', value: 'info' },
  { label: '학습 목차', value: 'curriculum' },
  { label: '수강 신청 현황', value: 'applications' },
  { label: '수강 현황', value: 'enrollments' },
  { label: '유의사항', value: 'notes' },
  { label: '문의 내역', value: 'inquiries' },
];

export const DEFAULT_COURSE_DETAIL_TAB = 'info';

export function isCourseDetailTab(value) {
  return COURSE_DETAIL_TABS.some((tab) => tab.value === value);
}
