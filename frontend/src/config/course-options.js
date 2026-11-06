/** 백엔드 예정 enum CourseTargetAudience — 화면 순서 그대로 */
export const TARGET_AUDIENCE_OPTIONS = [
  { label: '누구나', value: 'ANYONE' },
  { label: '어린이', value: 'CHILDREN' },
  { label: '보호자·가족', value: 'GUARDIANS_FAMILY' },
  { label: '아동·청소년', value: 'YOUTH' },
  { label: '성인', value: 'ADULTS' },
  { label: '중장년·시니어', value: 'MIDDLE_AGED_SENIORS' },
  { label: '남성', value: 'MEN' },
  { label: '여성', value: 'WOMEN' },
];

/** 화면 3값. 저장 시 매핑: 비공개→DRAFT, 모집중/모집예정→RECRUITING */
export const VISIBILITY_OPTIONS = [
  { label: '모집중', caption: '수강생 모집을 시작합니다', value: 'RECRUITING', color: 'var(--db-status-blue)' },
  {
    label: '모집예정',
    caption: '모집 시작 전에 미리 노출합니다',
    value: 'UPCOMING',
    color: 'var(--db-status-orange)',
  },
  { label: '비공개', caption: '등록 후 공개하지 않습니다', value: 'DRAFT', color: 'var(--db-status-gray)' },
];

export const EDUCATION_TYPE_WIZARD_OPTIONS = [
  { label: '온라인', caption: '실시간 또는 영상 강의', value: 'ONLINE', color: 'var(--db-status-blue)' },
  { label: '오프라인', caption: '대면 강의', value: 'OFFLINE', color: 'var(--db-status-green)' },
  { label: '하이브리드', caption: '온라인 + 오프라인 병행', value: 'HYBRID', color: 'var(--db-status-purple)' },
];

export const PROFILE_VISIBLE_OPTIONS = [
  { label: '보이기', caption: '강좌 상세 \'강사소개\'에 내 소개·경력이 나옵니다.', value: true },
  { label: '숨기기', caption: '강좌 상세에 내 소개를 보이지 않습니다.', value: false },
];

export const WIZARD_STEPS = ['기본 정보', '강의 일정 및 구성', '추가 정보', '미리 보기'];

/** D-2 반복 일정 요일. value = JS Date#getDay() */
export const WEEKDAY_OPTIONS = [
  { label: '월', value: 1 },
  { label: '화', value: 2 },
  { label: '수', value: 3 },
  { label: '목', value: 4 },
  { label: '금', value: 5 },
  { label: '토', value: 6 },
  { label: '일', value: 0 },
];

/** 백엔드 없음 — form 전용 */
export const SELECTION_METHOD_OPTIONS = [
  { label: '선착순', value: 'FCFS' },
  { label: '추첨', value: 'LOTTERY' },
];

/** D-3 수료 기준 — 백엔드 없음, form 전용 */
export const COMPLETION_ENABLED_OPTIONS = [
  { label: '수료증 줌', caption: '기준을 채운 수강생을 수료 처리하고 수료증을 줍니다.', value: true },
  { label: '수료증 없음', caption: '수료 처리를 하지 않습니다.', value: false },
];

export const COMPLETION_CRITERIA_OPTIONS = [
  {
    label: '출석률 기준',
    caption: '전체 회차 중 출석한 비율로 수료를 정합니다.',
    value: 'PROGRESS',
  },
  {
    label: '과제·평가 기준',
    caption: '과제 제출 및 평가 점수를 기준으로 수료 처리',
    value: 'ASSESSMENT',
  },
];

export const ATTACHMENT_ACCEPT = '.jpg,.jpeg,.png,.pdf,.doc,.docx,.hwp';
export const ATTACHMENT_MAX_BYTES = 10 * 1024 * 1024;

/** D-4 미리보기 탭 */
export const PREVIEW_TABS = ['강좌소개', '학습목차', '강사소개', '유의사항', '교육장소', 'Q&A'];
