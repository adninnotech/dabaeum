/**
 * 위저드 form 을 Course.detail(CourseDetail 스키마)로 바꾼다.
 *
 * ⚠ 재료비 유형만 값이 다르다 — form 은 '없음'을 'NONE' 으로 쓰고 계약은 'FREE' 다.
 * 화면 라벨·disable 조건이 'NONE' 에 걸려 있어 form 쪽을 바꾸지 않고 여기서 변환한다.
 *
 * ⚠ `PUT /courses/{id}` 는 detail 이 요청에 있으면 payload 만으로 CourseDetail 을 새로 만든다
 * (부분 필드는 도메인 기본값으로 리셋된다). 이 함수는 항상 17개 필드 전체를 채워 반환한다 —
 * 호출부는 절대 이 결과의 일부만 잘라 보내면 안 된다.
 */
function feeType(value) {
  return value === 'PAID' ? 'PAID' : 'FREE';
}

/** 금액은 유형이 PAID 일 때만 보낸다. FREE 인데 금액이 남아 있으면 무시한다 */
function feeAmount(type, amount) {
  return type === 'PAID' && amount != null ? Number(amount) : null;
}

/** buildCourseDetail 의 기본값 — course.detail 이 null(임시저장 1단계에서 나감)일 때 그대로 쓴다 */
const DEFAULT_DETAIL_FORM = {
  tags: '',
  targetAudiences: ['ANYONE'],
  instructorProfileVisible: true,
  tuitionType: 'FREE',
  tuitionAmount: null,
  materialFeeType: 'NONE',
  materialFeeAmount: null,
  completionEnabled: true,
  completionCriteria: 'PROGRESS',
  requiredProgressRate: 80,
  issueCertificate: true,
  notes: '',
  selectionMethod: 'FCFS',
  waitlistCapacity: null,
  materials: '',
  learningObjective: '',
};

/**
 * Course.detail(CourseDetail 응답) → 위저드 form 필드로 되돌린다. buildCourseDetail 의 역함수.
 *
 * ⚠ 재료비만 값이 다르다 — 계약은 'FREE'/'PAID' 뿐이지만 form 은 '없음'을 'NONE' 으로 쓴다.
 * (교육비는 form 도 'FREE' 그대로 — 두 필드의 form 값 집합이 다르다.)
 *
 * detail 이 null 이면(1단계만 저장하고 나간 임시저장) 위저드 기본값을 그대로 유지한다.
 * form 에 없는 detail 필드(materials, learningObjective)는 되돌리지 않는다 — 위저드에 입력칸이 없다.
 */
export function parseCourseDetail(detail) {
  if (!detail) return { ...DEFAULT_DETAIL_FORM, targetAudiences: [...DEFAULT_DETAIL_FORM.targetAudiences] };
  return {
    tags: detail.tags ?? '',
    targetAudiences:
      Array.isArray(detail.targetAudiences) && detail.targetAudiences.length
        ? [...detail.targetAudiences]
        : [...DEFAULT_DETAIL_FORM.targetAudiences],
    instructorProfileVisible: !!detail.instructorProfileVisible,
    tuitionType: detail.tuitionType === 'PAID' ? 'PAID' : 'FREE',
    tuitionAmount: detail.tuitionAmount ?? null,
    // FREE → 'NONE' (form 표기), PAID → 'PAID'
    materialFeeType: detail.materialFeeType === 'PAID' ? 'PAID' : 'NONE',
    materialFeeAmount: detail.materialFeeAmount ?? null,
    completionEnabled: !!detail.completionEnabled,
    completionCriteria: detail.completionCriteria || DEFAULT_DETAIL_FORM.completionCriteria,
    requiredProgressRate: detail.requiredProgressRate ?? DEFAULT_DETAIL_FORM.requiredProgressRate,
    issueCertificate: !!detail.issueCertificate,
    notes: detail.notes ?? '',
    // 위저드에 입력칸이 없지만 학습자 강좌 상세에 표시되는 값이다(학습목표·준비물).
    // buildCourseDetail 이 다시 실어 보내도록 복원한다 — 비워 두면 저장 시 조용히 지워진다.
    materials: detail.materials ?? '',
    learningObjective: detail.learningObjective ?? '',
    selectionMethod: detail.selectionMethod || 'FCFS',
    waitlistCapacity: detail.waitlistCapacity ?? null,
  };
}

export function buildCourseDetail(form) {
  const completionEnabled = !!form.completionEnabled;
  const tuitionType = feeType(form.tuitionType);
  const materialFeeType = feeType(form.materialFeeType);
  return {
    tags: form.tags?.trim() || null,
    targetAudiences: Array.isArray(form.targetAudiences) ? [...form.targetAudiences] : [],
    instructorProfileVisible: !!form.instructorProfileVisible,

    tuitionType,
    tuitionAmount: feeAmount(tuitionType, form.tuitionAmount),
    materialFeeType,
    materialFeeAmount: feeAmount(materialFeeType, form.materialFeeAmount),

    completionEnabled,
    // 수료 기준을 끄면 하위 값은 보내지 않는다 — 서버가 기본값으로 정규화한다
    completionCriteria: completionEnabled ? form.completionCriteria || null : null,
    requiredProgressRate:
      completionEnabled && form.completionCriteria === 'PROGRESS'
        ? Number(form.requiredProgressRate ?? 0)
        : null,
    issueCertificate: completionEnabled ? !!form.issueCertificate : false,

    notes: form.notes?.trim() || null,
    materials: form.materials?.trim() || null,
    learningObjective: form.learningObjective?.trim() || null,

    selectionMethod: form.selectionMethod || 'FCFS',
    // 선착순이면 대기 정원은 의미가 없다
    waitlistCapacity:
      form.selectionMethod === 'FCFS' && form.waitlistCapacity != null
        ? Number(form.waitlistCapacity)
        : null,
  };
}
