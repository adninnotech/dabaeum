/* 출석률 표시 — API 의 attendanceRate 는 0~100 퍼센트 값이다(AttendanceSummary·Completion 공통).
 * 예전 화면들이 0~1 비율로 보고 ×100 해 10000% 를 띄우거나, 1 이하를 비율로 짐작해 1% 를 100% 로 보였다 */
export function formatAttendanceRate(rate) {
  const value = Number(rate);
  if (rate == null || rate === '' || !Number.isFinite(value)) return '-';
  return `${Math.round(value)}%`;
}
