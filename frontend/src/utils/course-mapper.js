import { educationTypeLabel, statusLabel } from '@/utils/status';
import { formatDateKst } from '@/utils/datetime';

const PLACEHOLDER_IMAGES = [
  'https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=640&q=80',
  'https://images.unsplash.com/photo-1503676260728-1c00da094a0b?w=640&q=80',
  'https://images.unsplash.com/photo-1677442136019-21780ecad995?w=640&q=80',
  'https://images.unsplash.com/photo-1456513080080-69d3d2d9c3d5?w=640&q=80',
];

function formatDateDot(value) {
  return formatDateKst(value);
}

function weeksBetween(start, end) {
  if (!start || !end) return '-';
  const a = new Date(start);
  const b = new Date(end);
  if (Number.isNaN(a.getTime()) || Number.isNaN(b.getTime())) return '-';
  const weeks = Math.max(1, Math.round((b - a) / (7 * 24 * 60 * 60 * 1000)));
  return `${weeks}주`;
}

/** API Course → 목록 카드 UI 모델 */
export function toCourseCard(course, institutionName = '') {
  const badges = [];
  if (course.educationType) {
    badges.push(educationTypeLabel(course.educationType));
  }
  if (course.creditBankEligible) badges.push('학점은행제 인정');
  if (course.status === 'RECRUITING') badges.push('모집중');

  const hash = String(course.id || '')
    .split('')
    .reduce((acc, ch) => acc + ch.charCodeAt(0), 0);

  return {
    ...course,
    institution: institutionName || course.institutionName || '교육기관',
    institutionName: institutionName || course.institutionName || '교육기관',
    duration: weeksBetween(course.startDate, course.endDate),
    level: educationTypeLabel(course.educationType),
    recruitStatus: statusLabel(course.status),
    recruitEnd: formatDateDot(course.recruitEndDate),
    priceLabel: '문의',
    location: course.location || (course.educationType === 'ONLINE' ? '온라인' : '-'),
    period: `${formatDateDot(course.startDate)} - ${formatDateDot(course.endDate)}`,
    creditLabel: course.creditBankEligible ? `${course.creditValue || '-'}학점` : '',
    badges,
    image: course.image || PLACEHOLDER_IMAGES[hash % PLACEHOLDER_IMAGES.length],
  };
}
