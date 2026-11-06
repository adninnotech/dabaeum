import { findClassificationTrail } from '@/utils/classification';

/* 배지 획득 조건을 사람 말로 (설계서 §11.1). 분류는 하위를 포함하므로 "~ 분야"로 적는다 */

export const BADGE_RULE_KIND = {
  COURSE: 'COURSE',
  CLASSIFICATION: 'CLASSIFICATION',
  CUMULATIVE: 'CUMULATIVE',
  TIERED: 'TIERED',
};

export const BADGE_RULE_KIND_OPTIONS = [
  { value: BADGE_RULE_KIND.CLASSIFICATION, label: '분야 수료', hint: '이 분야 강의를 하나 수료하면' },
  { value: BADGE_RULE_KIND.CUMULATIVE, label: '누적 수료', hint: '수료한 강의가 N개가 되면' },
  { value: BADGE_RULE_KIND.COURSE, label: '특정 강의 수료', hint: '고른 강의를 수료하면' },
  { value: BADGE_RULE_KIND.TIERED, label: '단계 배지', hint: '이수 건수에 따라 단계가 오르내린다' },
];

/** 단계 배지 기본 기준표 — 발주처 발급 체계안(씨앗~나눔 7단계, 강좌 이수 건수) */
export const DEFAULT_TIER_LEVELS = [
  { level: 1, name: '씨앗', minCount: 1 },
  { level: 2, name: '새싹', minCount: 6 },
  { level: 3, name: '자람', minCount: 11 },
  { level: 4, name: '이음', minCount: 16 },
  { level: 5, name: '펼침', minCount: 21 },
  { level: 6, name: '빛남', minCount: 31 },
  { level: 7, name: '나눔', minCount: 41 },
];

export const TIER_LEVEL_MIN = 2;
export const TIER_LEVEL_MAX = 10;

/** 단계별 이수 범위 문장 — "1~5건", 마지막 단계는 "41건 이상" */
export function tierRangeText(levels, index) {
  const current = levels[index];
  const next = levels[index + 1];
  if (!current) return '';
  if (!next) return `${current.minCount}건 이상`;
  const upper = Number(next.minCount) - 1;
  return upper > Number(current.minCount) ? `${current.minCount}~${upper}건` : `${current.minCount}건`;
}

/** 단계 기준표 검증 — 2~10단계, 이름 1~20자, minCount 1 이상·오름차순. 문제가 없으면 '' */
export function validateTierLevels(levels) {
  if (!Array.isArray(levels) || levels.length < TIER_LEVEL_MIN || levels.length > TIER_LEVEL_MAX) {
    return `단계는 ${TIER_LEVEL_MIN}~${TIER_LEVEL_MAX}개여야 합니다.`;
  }
  for (let index = 0; index < levels.length; index += 1) {
    const item = levels[index];
    const name = String(item.name || '').trim();
    if (!name || name.length > 20) return `${index + 1}단계 이름을 1~20자로 입력해 주세요.`;
    const min = Number(item.minCount);
    if (!Number.isInteger(min) || min < 1 || min > 1000) return `${index + 1}단계 최소 이수 건수는 1~1000 사이 정수여야 합니다.`;
    if (index > 0 && min <= Number(levels[index - 1].minCount)) {
      return `${index + 1}단계 최소 이수 건수는 ${index}단계보다 커야 합니다.`;
    }
  }
  return '';
}

/** 서버 규칙 → 화면 선택지 (COURSE_COMPLETION 은 courseId 유무로 둘로 나뉜다) */
export function ruleKind(rule) {
  if (rule?.type === 'TIERED') return BADGE_RULE_KIND.TIERED;
  if (rule?.type === 'CUMULATIVE') return BADGE_RULE_KIND.CUMULATIVE;
  return rule?.courseId ? BADGE_RULE_KIND.COURSE : BADGE_RULE_KIND.CLASSIFICATION;
}

/** 화면 입력 → 서버 규칙. 선택지에 맞지 않는 필드는 null 로 비운다(서버가 조합을 422 로 거른다) */
export function buildRule(kind, { courseId, classificationCode, count, levels }) {
  if (kind === BADGE_RULE_KIND.TIERED) {
    // imageUrl 은 응답 전용 — 요청에는 id 만 보낸다
    return {
      type: 'TIERED',
      courseId: null,
      classificationCode: classificationCode || null,
      count: null,
      levels: (levels || []).map((item, index) => ({
        level: index + 1,
        name: String(item.name || '').trim(),
        minCount: Number(item.minCount),
        imageFileId: item.imageFileId || null,
      })),
    };
  }
  if (kind === BADGE_RULE_KIND.CUMULATIVE) {
    return {
      type: 'CUMULATIVE',
      courseId: null,
      classificationCode: classificationCode || null,
      count: Number(count),
    };
  }
  if (kind === BADGE_RULE_KIND.COURSE) {
    return { type: 'COURSE_COMPLETION', courseId, classificationCode: null, count: null };
  }
  return { type: 'COURSE_COMPLETION', courseId: null, classificationCode, count: null };
}

function classificationName(tree, code) {
  const trail = findClassificationTrail(tree, code);
  return trail.length ? trail[trail.length - 1].name : '';
}

/**
 * 예: "문화예술교육 분야 강좌 1과목 수료", "문화예술교육 분야 3과목 수료", "누적 5과목 수료", "「파이썬 입문」 수료".
 * tree 는 GET /course-classifications, courseTitles 는 { [courseId]: title } (모르면 "지정 강좌")
 */
export function ruleText(rule, { tree = [], courseTitles = {} } = {}) {
  if (!rule?.type) return '-';
  const field = rule.classificationCode ? classificationName(tree, rule.classificationCode) : '';
  const fieldLabel = field || (rule.classificationCode ? '지정 분야' : '');
  if (rule.type === 'TIERED') {
    const levels = rule.levels || [];
    const steps = levels.map((item, index) => `${item.name} ${tierRangeText(levels, index)}`).join(', ');
    return `${fieldLabel ? `${fieldLabel} 분야 ` : ''}강좌 이수 건수 — ${steps}`;
  }
  if (rule.type === 'CUMULATIVE') {
    return fieldLabel ? `${fieldLabel} 분야 ${rule.count}과목 수료` : `누적 ${rule.count}과목 수료`;
  }
  if (rule.courseId) {
    const title = courseTitles[rule.courseId];
    return title ? `「${title}」 수료` : '지정 강좌 수료';
  }
  return `${fieldLabel} 분야 강좌 1과목 수료`;
}
