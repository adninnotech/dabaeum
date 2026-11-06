/**
 * 기능 플래그
 *
 * 온라인/혼합 강의는 코드에 유지하되, 기본은 오프라인만 사용한다.
 * 활성화 방법 (우선순위 높은 순):
 * 1) 환경변수 `VITE_ENABLE_ONLINE_COURSES` / `QCLI_ENABLE_ONLINE_COURSES` = true
 * 2) 아래 `FEATURES.enableOnlineCourses`를 `true`로 변경 후 재빌드
 */

function envFlag(...names) {
  for (const name of names) {
    const raw = import.meta.env?.[name];
    if (typeof raw !== 'string') continue;
    const value = raw.trim().toLowerCase();
    if (value === 'true' || value === '1' || value === 'yes') return true;
    if (value === 'false' || value === '0' || value === 'no') return false;
  }
  return null;
}

const onlineFromEnv = envFlag('VITE_ENABLE_ONLINE_COURSES', 'QCLI_ENABLE_ONLINE_COURSES');
const interestFromEnv = envFlag('VITE_ENABLE_INTEREST_INPUT');

export const FEATURES = {
  /**
   * true: ONLINE / HYBRID 선택·온라인 URL·유형 컬럼/뱃지 UI 사용
   * false: 오프라인만 (기본) — env가 명시적으로 true일 때만 활성화
   */
  enableOnlineCourses: onlineFromEnv === true,
  /**
   * 관심 분야 입력(#142) — 다대구 가입·학습자 마이페이지의 관심 분야 선택과 [선택] 수집·이용 동의.
   * 기본 켬(사용자 결정 2026-11-03). 끄려면 env `VITE_ENABLE_INTEREST_INPUT=false` 로 빌드한다 —
   * 꺼지면 가입은 약관 동의에서 끝나고 서버엔 관심 분야 필드를 보내지 않는다
   */
  enableInterestInput: interestFromEnv !== false,
};

export function isInterestInputEnabled() {
  return FEATURES.enableInterestInput === true;
}

/** 신규 강의 기본 교육 유형 */
export const DEFAULT_EDUCATION_TYPE = 'OFFLINE';

export function isOnlineCoursesEnabled() {
  return FEATURES.enableOnlineCourses === true;
}

/** UI에 교육 유형(온라인/오프라인/혼합) 뱃지·컬럼 표시 여부 */
export function shouldShowEducationTypeUI() {
  return isOnlineCoursesEnabled();
}

/** ONLINE 또는 HYBRID */
export function isOnlineEducationType(type) {
  return type === 'ONLINE' || type === 'HYBRID';
}

/**
 * 교육 유형 선택 옵션
 * @param {Array<{label:string,value:string}>} allOptions
 */
export function filterEducationTypeOptions(allOptions) {
  const list = Array.isArray(allOptions) ? allOptions : [];
  if (isOnlineCoursesEnabled()) return list;
  return list.filter((item) => item.value === 'OFFLINE');
}

/** 저장 시 교육 유형 정규화 (오프라인 전용 모드면 OFFLINE 고정) */
export function resolveEducationType(type) {
  if (!isOnlineCoursesEnabled()) return DEFAULT_EDUCATION_TYPE;
  return type || DEFAULT_EDUCATION_TYPE;
}

/** 장소: 온라인 전용이면 null, 그 외는 location 또는 fallback */
export function resolveCourseLocation(educationType, location, fallback = '미정') {
  if (isOnlineCoursesEnabled() && educationType === 'ONLINE') {
    return null;
  }
  const text = String(location || '').trim();
  return text || fallback;
}

/** 온라인 URL: 기능 비활성 또는 OFFLINE이면 null */
export function resolveOnlineUrl(educationType, onlineUrl) {
  if (!isOnlineCoursesEnabled()) return null;
  if (educationType === 'OFFLINE') return null;
  return onlineUrl || null;
}
