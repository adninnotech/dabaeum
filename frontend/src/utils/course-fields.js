import { storedFileContentUrl } from '@/services/file-api';
import { classificationLeafName } from '@/utils/classification';
import { daysFromToday } from '@/utils/datetime';

/** 교육비 문구 — tuitionType 이 PAID 인데 금액이 없으면 '-' */
export function tuitionText(type, amount) {
  if (type === 'FREE') return '무료';
  if (type === 'PAID') {
    return amount == null ? '-' : `${Number(amount).toLocaleString('ko-KR')}원`;
  }
  return '-';
}

/**
 * LearningCourse · Interest · Review · InquirySummary 가 공유하는 CourseCardFields 를
 * 화면용으로 바꾼다. 백엔드가 @JsonUnwrapped 로 내려 평탄한 필드다.
 * tags 는 문자열(최대 60자)이라 칩으로 쓰려면 쪼갠다.
 */
export function toCourseCard(row) {
  return {
    institutionName: row.institutionName || '-',
    instructorName: row.instructorName || '',
    // 표준 분류(설계서 §12) — 좁은 자리라 가장 아래 단계 이름만. 전체 경로는 classificationPath
    classificationName: classificationLeafName(row.classificationPath),
    classificationPath: Array.isArray(row.classificationPath) ? row.classificationPath : [],
    educationType: row.educationType || '',
    sessionCount: row.sessionCount ?? null,
    tags: String(row.tags || '')
      .split(/[\s,]+/)
      .filter(Boolean),
    tuitionText: tuitionText(row.tuitionType, row.tuitionAmount),
    thumbnailUrl: row.thumbnailFileId
      ? storedFileContentUrl(row.thumbnailFileId)
      : '',
  };
}

/** 기관 | 강사 | 분류 — 값이 있는 것만 이어붙인다 (빈 자리에 '-'를 끼우지 않는다) */
export function metaLine(row) {
  const classification = row.classificationName || classificationLeafName(row.classificationPath);
  return (
    [row.institutionName, row.instructorName, classification].filter(Boolean).join(' | ') || '-'
  );
}

/**
 * 학습자 목록에 보여줄 강좌인가 — 비공개(DRAFT)·취소(CANCELLED)는 뺀다.
 *
 * ⚠ 서버가 `GET /courses` 에서 **DRAFT 를 학습자 토큰에도 내려준다**(2026-09-18 실측, 백엔드 제보함).
 * 즉 다른 기관의 작성 중인 강좌가 학습자 목록에 섞인다. 서버가 고쳐져도 프론트에서 한 번 더 막는다 —
 * 목록 화면이 강좌 상태를 판단 없이 그대로 그리면 안 되는 자리다.
 */
export function isLearnerVisibleCourse(course) {
  const s = course?.status;
  return s !== 'DRAFT' && s !== 'CANCELLED';
}

/* 학습자 카드의 모집 상태 라벨 — 콘솔 라벨(STATUS_LABEL)의 '수강중'은 내 수강 얘기로 읽혀 '진행중'으로 쓴다 */
const RECRUIT_STATE_LABEL = {
  RECRUITING: '모집중',
  RECRUITMENT_CLOSED: '모집마감',
  IN_PROGRESS: '진행중',
  COMPLETED: '종료',
};

/* 이 날 수 이하로 남으면 'D-n 마감' 으로 눈에 띄게 */
const DEADLINE_URGENT_DAYS = 7;

/** 지금 신청할 수 있나 — 서버 상태가 RECRUITING 이어도 모집 마감일이 지났으면 false (courseRecruitState 와 같은 판단) */
export function isRecruitOpen(course) {
  return courseRecruitState(course).open;
}

/**
 * 강좌 찾기 카드에서 '지금 신청할 수 있나' 를 한눈에 — 상태 라벨과 모집 마감 문구.
 * open 은 모집중일 때만 true. deadline 은 모집중이고 마감일이 있을 때만(지난 날짜면 없음).
 */
export function courseRecruitState(course) {
  const status = course?.status || '';
  const days = status === 'RECRUITING' ? daysFromToday(course.recruitEndDate) : null;
  // 서버 상태가 RECRUITING 인 채로 모집 마감일이 지난 강좌가 있다(상태 전환이 늦다) — 날짜를 믿고 마감으로 보인다
  if (days !== null && days < 0) return { open: false, label: RECRUIT_STATE_LABEL.RECRUITMENT_CLOSED, deadline: null };
  const open = status === 'RECRUITING';
  let deadline = null;
  if (days === 0) deadline = { text: '오늘 마감', days, urgent: true };
  else if (days > 0 && days <= DEADLINE_URGENT_DAYS) deadline = { text: `${days}일 뒤 마감`, days, urgent: true };
  else if (days > DEADLINE_URGENT_DAYS) {
    const date = String(course.recruitEndDate).slice(0, 10);
    deadline = { text: `${date.slice(5, 7)}.${date.slice(8, 10)} 마감`, days, urgent: false };
  }
  return { open, label: RECRUIT_STATE_LABEL[status] || '', deadline };
}
