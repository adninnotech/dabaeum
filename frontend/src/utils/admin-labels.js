import { ROLE_LABELS, ROLES } from '@/access/roles';

/* 관리자 관리·접속기록(설계서 §10) 화면 공통 라벨 */

export const ADMIN_ROLE_OPTIONS = [ROLES.PLATFORM_ADMIN, ROLES.INSTITUTION_ADMIN].map((role) => ({
  label: ROLE_LABELS[role],
  value: role,
}));

export const ACCESS_ACTION_LABEL = {
  READ: '조회',
  UPDATE: '변경',
  DELETE: '삭제',
  DOWNLOAD: '다운로드',
};

export const ACCESS_ACTION_OPTIONS = Object.entries(ACCESS_ACTION_LABEL).map(([value, label]) => ({
  label,
  value,
}));

/** 관리자 비밀번호 변경 주기(설계서 §10.3) — 서버 passwordExpired 와 같은 기준 */
export const ADMIN_PASSWORD_MAX_AGE_DAYS = 90;
