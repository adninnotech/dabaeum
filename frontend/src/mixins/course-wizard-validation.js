/* 강의 등록 위저드 — 단계별 검증·[다음 단계]·등록 전 체크사항 */
import { WIZARD_STEPS } from '@/config/course-options';
import { plainTextLength } from '@/utils/rich-text';

export default {
  computed: {
    /**
     * 등록 전 체크사항 — 필수는 빠진 것을 하나씩(어느 단계인지와 함께), 선택은 따로.
     * '모든 필수 정보를 입력했습니다' 한 줄로는 무엇이 빠졌는지 몰랐고, 선택 항목도 미완료처럼 보였다
     */
    checklist() {
      const f = this.form;
      const missing = [1, 2, 3].flatMap((step) =>
        Object.values(this.stepErrors(step)).map((message) => ({ step, stepLabel: WIZARD_STEPS[step - 1], message })),
      );
      return {
        missing,
        optional: [
          { label: '강사 소개', ok: !!this.previewInstructor?.introduction },
          { label: '학습 전 유의사항', ok: plainTextLength(f.notes) > 0 },
          { label: '첨부파일', ok: f.attachments.length > 0 },
        ],
      };
    },
  },
  methods: {
    // 단계별 검증 — 항목 키별로 모든 오류를 모은다(키 순서 = 화면 순서). 칸 아래에 함께 보여 주려고
    stepErrors1() {
      const f = this.form;
      const errors = {};
      if (!this.courseInstitutionId) errors.institution = '강의를 등록할 기관을 골라 주세요.';
      if (!f.classificationCode) errors.classification = '강좌 분류를 소분류까지 선택해 주세요.';
      if (!f.title.trim()) errors.title = '강의 제목을 입력해 주세요.';
      if (!plainTextLength(f.description)) errors.description = '강의 설명을 입력해 주세요.';
      if (!f.targetAudiences.length) errors.targetAudiences = '수강 대상을 1개 이상 선택해 주세요.';
      return errors;
    },
    stepErrors2() {
      const f = this.form;
      const errors = {};
      if (!f.recruitStartDate) errors.recruitStartDate = '모집 시작일을 선택해 주세요.';
      if (!f.recruitEndDate) errors.recruitEndDate = '모집 마감일을 선택해 주세요.';
      else if (f.recruitStartDate && f.recruitEndDate < f.recruitStartDate)
        errors.recruitEndDate = '모집 마감일은 모집 시작일 이후여야 합니다.';
      // 모집 마감일이 강의 시작일(첫 회차·온라인은 학습 시작일)보다 늦으면 서버가 422(details recruitEndDate). 같은 날은 된다(QA VAL-02)
      const courseStart = this.previewStartDate;
      if (!errors.recruitEndDate && f.recruitEndDate && courseStart && f.recruitEndDate > courseStart) {
        errors.recruitEndDate = `모집 마감일은 강의 시작일(${courseStart}) 이전이어야 합니다. 같은 날은 됩니다.`;
      }
      if (!(Number(f.capacity) >= 1)) errors.capacity = '모집 인원을 1명 이상 입력해 주세요.';
      else if (!Number.isInteger(Number(f.capacity))) errors.capacity = '모집 인원은 정수로 입력해 주세요.';
      if (!f.sessions.length) errors.sessions = '회차를 1개 이상 추가해 주세요.';
      if (this.isOnlineCourse) {
        if (!f.startDate) errors.startDate = '학습 시작일을 선택해 주세요.';
        if (!f.endDate) errors.endDate = '학습 마감일을 선택해 주세요.';
        else if (f.startDate && f.endDate < f.startDate) errors.endDate = '학습 마감일은 학습 시작일 이후여야 합니다.';
        return errors;
      }
      if (!f.location.trim()) errors.location = '강의 장소를 입력해 주세요.';
      const incomplete = f.sessions.find((s) => !s.date || !s.startTime || !s.endTime);
      if (incomplete) errors.sessions = `${incomplete.sessionNo}회차의 날짜와 시간을 입력해 주세요.`;
      return errors;
    },
    stepErrors3() {
      const f = this.form;
      const errors = {};
      if (f.tuitionType === 'PAID' && !(Number(f.tuitionAmount) > 0)) errors.tuitionAmount = '교육비 금액을 입력해 주세요.';
      if (f.materialFeeType === 'PAID' && !(Number(f.materialFeeAmount) > 0)) {
        errors.materialFeeAmount = '재료비 금액을 입력해 주세요.';
      }
      if (f.completionEnabled && f.completionCriteria === 'PROGRESS') {
        const rate = Number(f.requiredProgressRate);
        if (!(rate > 0 && rate <= 100)) errors.requiredProgressRate = '필요 출석률은 1~100 사이로 입력해 주세요.';
      }
      return errors;
    },
    stepErrors(step) {
      if (step === 1) return this.stepErrors1();
      if (step === 2) return this.stepErrors2();
      if (step === 3) return this.stepErrors3();
      return {};
    },
    /** 1~3단계 첫 오류 문구 하나 — 최종 등록 직전 확인용 */
    firstError() {
      return [1, 2, 3].map((step) => Object.values(this.stepErrors(step))[0]).find(Boolean) || null;
    },
    /** 오류 난 첫 칸으로 스크롤 — 폰에선 대표 이미지가 맨 아래라 찾기 어려웠다 */
    scrollToFirstError() {
      const firstKey = Object.keys(this.fieldErrors)[0];
      if (!firstKey) return;
      this.$nextTick(() => {
        const el = this.$el?.querySelector?.(`[data-field="${firstKey}"]`);
        if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      });
    },
    goToStep(target) {
      if (target < this.step) this.step = target;
    },
    /** 체크사항에서 빠진 항목의 단계로 — 그 칸 오류를 띄운 채 연다 */
    fixMissing(step) {
      this.step = step;
      this.$nextTick(() => {
        this.fieldErrors = this.stepErrors(step);
        this.scrollToFirstError();
      });
    },
    goNext() {
      if (this.blockWhileAttaching()) return;
      const errors = this.stepErrors(this.step);
      const count = Object.keys(errors).length;
      if (count) {
        this.fieldErrors = errors;
        this.$q.notify({ type: 'warning', message: `입력이 필요한 항목 ${count}개를 확인해 주세요.` });
        this.scrollToFirstError();
        return;
      }
      this.fieldErrors = {};
      if (this.step === WIZARD_STEPS.length) {
        if (!this.saving) this.submit();
        return;
      }
      this.step += 1;
    },
  },
};
