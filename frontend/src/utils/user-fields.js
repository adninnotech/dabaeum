import { storedFileContentUrl } from '@/services/file-api';
import { ROLE_LABELS } from '@/access/roles';

/** 역할 배지 라벨 — 기관 소속 역할은 기관명을 괄호로 붙인다 */
export function roleLabels(roles) {
  if (!Array.isArray(roles) || !roles.length) return [];
  return roles.map((r) => ({
    role: r.role,
    label: r.institutionName
      ? `${ROLE_LABELS[r.role] || r.role} (${r.institutionName})`
      : ROLE_LABELS[r.role] || r.role,
  }));
}

/** `User` 응답 → 회원·강사 목록/상세 헤더용 행. `lastLoginLabel`은 포맷하지 않은 원본이다 —
 * 화면마다 `formatDateKst`/`formatDateMinuteKst` 중 맞는 것을 고른다. */
export function userRow(row) {
  return {
    id: row.id,
    name: row.name || '-',
    email: row.email || '-',
    phone: row.phone || '-',
    status: row.status,
    roleLabels: roleLabels(row.roles),
    regionLabel: row.region || '-',
    lastLoginLabel: row.lastLoginAt || null,
    avatarUrl: row.profileImageId
      ? storedFileContentUrl(row.profileImageId)
      : '',
  };
}
