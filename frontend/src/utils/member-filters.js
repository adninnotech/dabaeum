/**
 * 관리자 회원 목록 필터 — 모두 서버 GET /users 가 거른다(QA 중앙 #52).
 * 예전엔 최신 100명만 받아 가입일·최근 로그인·수강·성별을 그 안에서 걸러 숫자가 크게 모자랐다.
 */
import { formatDateKst } from '@/utils/datetime';

export const GENDER_FILTER = [
  { label: '남성', value: 'MALE' },
  { label: '여성', value: 'FEMALE' },
  { label: '밝히지 않음', value: 'UNDISCLOSED' },
];

export const ENROLLMENT_FILTER = [
  { label: '수강 있음', value: 'HAS' },
  { label: '수강 없음', value: 'NONE' },
];

export const LOGIN_FILTER = [
  { label: '7일 안', value: 'WITHIN_7' },
  { label: '30일 안', value: 'WITHIN_30' },
  { label: '90일 넘게 없음', value: 'OVER_90' },
  { label: '기록 없음', value: 'NEVER' },
];

/** 가입일 빠른 선택 — 날짜 두 칸을 채운다 */
export const SIGNUP_PRESETS = [
  { label: '오늘', days: 1 },
  { label: '7일', days: 7 },
  { label: '30일', days: 30 },
  { label: '90일', days: 90 },
];

/** 서울 날짜 'YYYY-MM-DD' — 날짜 칸 값과 문자열로 비교한다. 없으면 '' */
export function kstDateKey(value) {
  return formatDateKst(value, '').replace(/\./g, '-');
}

/** 수강 필터 값 → hasEnrollment */
const ENROLLMENT_PARAM = { HAS: true, NONE: false };

/** 머리글 정렬 열 → 서버 sort 필드 (칸 key 와 같다) */
const SORT_FIELDS = ['createdAt', 'name', 'lastLoginAt', 'enrollmentCount'];

/**
 * 화면 조건 → GET /users query. 빈 값은 빼고(api-client 가 undefined 를 버린다),
 * 가입일은 'YYYY-MM-DD' 그대로 — 서버가 서울 날짜 양 끝 포함으로 본다
 */
export function memberListQuery({ q, filters = {}, signupFrom, signupTo, sort } = {}) {
  const sortParam = SORT_FIELDS.includes(sort?.key) ? `${sort.key},${sort.dir === 'asc' ? 'asc' : 'desc'}` : undefined;
  return {
    q: q || undefined,
    status: filters.status || undefined,
    gender: filters.gender || undefined,
    hasEnrollment: ENROLLMENT_PARAM[filters.enrollment],
    lastLogin: filters.login || undefined,
    createdFrom: signupFrom || undefined,
    createdTo: signupTo || undefined,
    sort: sortParam,
  };
}
