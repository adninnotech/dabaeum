/**
 * q-table / 템플릿 날짜·시간 표시를 KST 포맷 import·헬퍼로 일괄 정리 (수동 실행용)
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'src');

const importLine = "import { formatDateKst, formatDatePeriodKst, formatDateRangeKst, formatDateTimeFields, formatDateTimeKst, qFormatDateKst, qFormatDateTimeKst } from '@/utils/datetime';";

const files = [
  'pages/instructor/InstructorEnrollmentAppsPage.vue',
  'pages/instructor/InstructorEnrollmentsPage.vue',
  'pages/instructor/InstructorCompletionPage.vue',
  'pages/instructor/InstructorSessionsPage.vue',
  'pages/instructor/InstructorAttendancePage.vue',
  'pages/instructor/InstructorCoursesPage.vue',
  'pages/instructor/InstructorDashboardPage.vue',
  'pages/instructor/InstructorInquiriesPage.vue',
  'pages/institution/InstitutionEnrollmentsPage.vue',
  'pages/institution/InstitutionInstructorsPage.vue',
  'pages/institution/InstitutionCoursesPage.vue',
  'pages/institution/InstitutionInquiriesPage.vue',
  'pages/admin/AdminEnrollmentsPage.vue',
  'pages/admin/AdminCompletionPage.vue',
  'pages/admin/AdminSessionsPage.vue',
  'pages/admin/AdminAttendancePage.vue',
  'pages/admin/AdminApprovalsPage.vue',
  'pages/admin/AdminCredentialsPage.vue',
  'pages/admin/AdminUsersPage.vue',
  'pages/EnrollmentPage.vue',
  'pages/EnrollmentDetailPage.vue',
  'pages/InstructorApplyPage.vue',
  'pages/CourseDetailPage.vue',
  'pages/CourseInstructorsPage.vue',
  'pages/CredentialDetailPage.vue',
  'pages/WalletPage.vue',
  'pages/learning/LearningPage.vue',
];

const replacements = [
  ["field: (r) => r.appliedAt || r.createdAt || '-'", "field: (r) => formatDateTimeFields(r, 'appliedAt', 'createdAt')"],
  ["field: 'appliedAt', align: 'left' }", "field: 'appliedAt', align: 'left', format: qFormatDateTimeKst }"],
  ["field: 'startsAt', align: 'left' }", "field: 'startsAt', align: 'left', format: qFormatDateTimeKst }"],
  ["field: 'endsAt', align: 'left' }", "field: 'endsAt', align: 'left', format: qFormatDateTimeKst }"],
  ["field: 'checkedAt', align: 'left' }", "field: 'checkedAt', align: 'left', format: qFormatDateTimeKst }"],
  ["field: 'issuedAt', align: 'left' }", "field: 'issuedAt', align: 'left', format: qFormatDateTimeKst }"],
  ["field: 'assignedAt', align: 'left' }", "field: 'assignedAt', align: 'left', format: qFormatDateTimeKst }"],
  ["field: 'startDate' }", "field: 'startDate', format: qFormatDateKst }"],
  ["field: 'endDate' }", "field: 'endDate', format: qFormatDateKst }"],
  ['{{ detail.appliedAt || \'-\' }}', '{{ $formatDateTimeKst(detail.appliedAt) }}'],
  ['{{ detail.reviewedAt || \'-\' }}', '{{ $formatDateTimeKst(detail.reviewedAt) }}'],
  ['{{ lastQr?.expiresAt || \'-\' }}', '{{ $formatDateTimeKst(lastQr?.expiresAt) }}'],
  ['{{ qr.expiresAt }}', '{{ $formatDateTimeKst(qr.expiresAt) }}'],
  ['{{ item.createdAt }}', '{{ $formatDateTimeKst(item.createdAt) }}'],
  ['{{ enrollment.appliedAt || enrollment.createdAt || \'-\' }}', '{{ $formatDateTimeKst(enrollment.appliedAt || enrollment.createdAt) }}'],
  ['{{ item.verifiedAt || item.createdAt || \'-\' }}', '{{ $formatDateTimeKst(item.verifiedAt || item.createdAt) }}'],
  ['{{ identity.verifiedAt || \'-\' }}', '{{ $formatDateTimeKst(identity.verifiedAt) }}'],
  ['{{ course.recruitStartDate }} ~ {{ course.recruitEndDate }}', '{{ $formatDateKst(course.recruitStartDate) }} ~ {{ $formatDateKst(course.recruitEndDate) }}'],
  ['{{ course.startDate }} ~ {{ course.endDate }}', '{{ $formatDateKst(course.startDate) }} ~ {{ $formatDateKst(course.endDate) }}'],
  ['{{ item.validFrom || \'-\' }} ~ {{ item.validUntil || \'-\' }}', '{{ $formatDateKst(item.validFrom) }} ~ {{ $formatDateKst(item.validUntil) }}'],
  ['field: (r) => `${r.startDate || \'-\'} ~ ${r.endDate || \'-\'}`,', 'field: (r) => formatDatePeriodKst(r.startDate, r.endDate),'],
  ['value: c.evaluatedAt || \'-\'', 'value: formatDateTimeKst(c.evaluatedAt)'],
  ['value: c.completedAt || \'-\'', 'value: formatDateTimeKst(c.completedAt)'],
  ['value: c.confirmedAt || \'-\'', 'value: formatDateTimeKst(c.confirmedAt)'],
  ['value: c.issuedAt || c.createdAt || \'-\'', 'value: formatDateTimeKst(c.issuedAt || c.createdAt)'],
  ['value: u.createdAt || \'-\'', 'value: formatDateTimeKst(u.createdAt)'],
  ['value: u.updatedAt || \'-\'', 'value: formatDateTimeKst(u.updatedAt)'],
  ['value: `${c.validFrom || \'-\'} ~ ${c.validUntil || \'-\'}`', 'value: formatDatePeriodKst(c.validFrom, c.validUntil)'],
  ['label: `${s.sessionNo}회차 · ${s.startsAt || \'\'} · ${s.status || \'\'}`.trim(),', "label: `${s.sessionNo}회차 · ${formatDateTimeKst(s.startsAt)} · ${s.status || ''}`.trim(),"],
  ['format: (v) => (v ? String(v).slice(0, 10) : \'-\'),', 'format: qFormatDateTimeKst,'],
];

for (const rel of files) {
  const filePath = path.join(root, rel);
  if (!fs.existsSync(filePath)) continue;
  let content = fs.readFileSync(filePath, 'utf8');
  if (!content.includes('@/utils/datetime')) {
    content = content.replace(/(<script>\n)/, `$1${importLine}\n`);
  }
  for (const [from, to] of replacements) {
    content = content.split(from).join(to);
  }
  fs.writeFileSync(filePath, content);
  console.log('updated', rel);
}
