/* 강의 등록 위저드 — 임시저장·최종 등록·담당 강사 배정. 페이지의 form·draftCourseId 를 쓴다 */
import {
  assignCourseInstructor,
  attachCourseFile,
  createCourse,
  publishCourse,
  updateCourse,
} from '@/services/course-api';
import { createCourseSessionsBulk } from '@/services/session-api';
import { courseSaveErrorMessage } from '@/utils/classification';
import { buildCourseDetail } from '@/utils/course-detail';
import { integerInputError } from '@/utils/integer-input';

export default {
  methods: {
    /** courseId 생성 직후 호출 — 실패해도 강좌는 이미 저장됐으므로 negative notify 후 계속 진행 */
    async assignSelectedInstructor(courseId) {
      if (!this.selectedInstructorId || this.instructorAssigned) return;
      this.instructorAssigned = true; // 두 번째 저장(PUT)에서 다시 호출하지 않도록 먼저 세운다
      try {
        await assignCourseInstructor(courseId, {
          userId: this.selectedInstructorId,
          role: 'MAIN',
        });
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: `강의는 저장되었지만 담당 강사 배정에 실패했습니다: ${error.message || ''}`.trim(),
          position: 'top',
        });
      }
    },
    /**
     * 생성 본문 → 수정 본문. CourseUpdateRequest 는 additionalProperties:false 이고
     * institutionId 를 아예 받지 않는다(기관은 생성 시점에 고정). 또 startDate·endDate·capacity 는
     * 수정 스키마에서 nullable 이 아니라 null 을 실으면 400 이다 — 값이 없으면 키를 뺀다.
     * (생성 스키마와 달라서 본문을 그대로 재사용하면 400 이 난다.)
     */
    toUpdateBody(body) {
      const rest = { ...body };
      delete rest.institutionId;
      const NOT_NULLABLE_ON_UPDATE = ['startDate', 'endDate', 'capacity'];
      for (const key of NOT_NULLABLE_ON_UPDATE) {
        if (rest[key] === null || rest[key] === undefined) delete rest[key];
      }
      return rest;
    },
    /** 임시저장 멱등 처리 — draftCourseId 가 있으면 PUT(갱신), 없으면 POST(생성) */
    async saveCourse(body) {
      if (this.draftCourseId) return updateCourse(this.draftCourseId, this.toUpdateBody(body));
      const course = await createCourse(body);
      this.draftCourseId = course.id;
      return course;
    },

    /** 담당 강사 배정 여부에 따라 임시저장 완료 안내를 다르게 준다 */
    draftFollowUpHint() {
      if (!this.showInstructorAssignField) return '"내 강의 관리"에서 확인할 수 있습니다.';
      return this.selectedInstructorId
        ? '담당 강사의 "내 강의 관리"에서 확인할 수 있습니다.'
        : '담당 강사를 배정해야 해당 강사의 "내 강의 관리"에 표시됩니다.';
    },
    /* 임시저장 — 제목만 있으면 저장된다. 공개 시점에 서버가 startDate·endDate·capacity 를 요구한다.
     * 페이지에 머물며 이어서 다음 단계를 채울 수 있도록 이동하지 않는다(멱등 — 두 번째부터는 PUT). */
    async saveDraft({ quiet = false } = {}) {
      if (this.saving || (quiet ? this.attaching : this.blockWhileAttaching())) return;
      const f = this.form;
      // 자동 저장은 기관·제목이 갖춰졌을 때만 조용히 — 모자라면 아무 말 없이 건너뛴다
      if (!this.courseInstitutionId) {
        if (!quiet) this.$q.notify({ type: 'warning', message: '강의를 등록할 기관을 골라 주세요.' });
        return;
      }
      if (!f.title.trim()) {
        if (!quiet) this.$q.notify({ type: 'warning', message: '강의명을 입력해 주세요.' });
        return;
      }
      // 정원 0·소수는 빈 값처럼 빠져 서버 값이 그대로 남는데 "임시저장되었습니다"가 떴다(QA CRS-04). 비어 있을 때만 건너뛴다
      const capacityError = integerInputError(f.capacity, { min: 1 });
      if (capacityError) {
        if (!quiet) this.$q.notify({ type: 'warning', message: `모집 인원: ${capacityError}`, position: 'top' });
        return;
      }
      this.saving = true;
      this.autoSaveFailed = false;
      try {
        const course = await this.saveCourse({
          institutionId: this.courseInstitutionId,
          title: f.title.trim(),
          educationType: f.educationType,
          description: f.description || null,
          classificationCode: f.classificationCode || null,
          startDate: f.startDate || null,
          endDate: f.endDate || null,
          recruitStartDate: f.recruitStartDate || null,
          recruitEndDate: f.recruitEndDate || null,
          capacity: f.capacity === '' || f.capacity == null ? null : Number(f.capacity),
          location: this.isOnlineCourse ? null : f.location || null,
          thumbnailFileId: f.thumbnail?.id || null,
          creditBankEligible: false,
          detail: buildCourseDetail(f),
        });
        await this.assignSelectedInstructor(course.id);
        this.draftSavedAt = new Date().toISOString();
        this.draftSavedAuto = quiet;
        this.markSaved();
        if (!quiet) {
          this.$q.notify({
            type: 'positive',
            message: `임시저장되었습니다. ${this.draftFollowUpHint()}`,
            position: 'top',
          });
        }
      } catch (error) {
        if (quiet) {
          // 자동 저장 실패는 하단 바 글자로만 — 입력 중에 알림이 튀지 않게
          this.autoSaveFailed = true;
        } else {
          this.$q.notify({
            type: 'negative',
            message: courseSaveErrorMessage(error, '임시저장에 실패했습니다.'),
            position: 'top',
          });
        }
      } finally {
        this.saving = false;
      }
    },
    /* ---------- 최종 등록 ---------- */
    // 승인 절차가 계약에 없다(승인 대기 상태·요청 API 없음) — 공개로 고르면 바로 공개된다. 버튼도 "등록"
    // 모집예정(UPCOMING)은 recruitStartDate로 서버가 판단 예정
    publishDoneMessage(published) {
      if (published) return '강의가 등록되었습니다.';
      if (!this.canPublishCourse) return '강의가 등록되었습니다. 기관 관리자가 공개하면 모집이 시작됩니다.';
      return '비공개로 저장되었습니다.';
    },
    async submit() {
      if (this.blockWhileAttaching()) return;
      const message = this.firstError();
      if (message) {
        this.$q.notify({ type: 'warning', message });
        return;
      }
      const f = this.form;
      const isOnline = this.isOnlineCourse;
      const startDate = isOnline ? f.startDate : this.sessionDates[0];
      const endDate = isOnline ? f.endDate : this.sessionDates.at(-1);
      this.saving = true;
      try {
        // draftCourseId 가 있으면(먼저 임시저장했으면) 그 강좌를 갱신한다 — 새로 만들지 않는다
        const course = await this.saveCourse({
          institutionId: this.courseInstitutionId,
          // courseCode 는 보내지 않는다 — 생략하면 서버가 C-YYYYMM-XXXXXX 로 만든다
          title: f.title.trim(),
          description: f.description || null,
          classificationCode: f.classificationCode || null,
          educationType: f.educationType,
          startDate,
          endDate,
          recruitStartDate: f.recruitStartDate || null,
          recruitEndDate: f.recruitEndDate || null,
          capacity: Number(f.capacity),
          location: isOnline ? null : f.location,
          onlineUrl: null,
          thumbnailFileId: f.thumbnail?.id || null,
          creditBankEligible: false,
          creditValue: null,
          detail: buildCourseDetail(f),
        });
        // 강좌가 저장된 뒤의 이동(완료·부분 실패 모두)은 의도된 것이라 나가기 확인을 건너뛴다
        this.skipLeaveGuard = true;
        await this.assignSelectedInstructor(course.id);

        // 회차 생성 — 온라인은 시각 없이, 오프라인은 시각과 함께. 한 번에 보낸다
        // 편집 모드: 서버 id 가 이미 있는 회차는 콘솔의 커리큘럼 탭이 담당한다 — 여기서는
        // id 없는(새로 추가한) 회차만 생성해 중복 생성을 막는다.
        const newSessions = f.sessions.filter((s) => !s.id);
        if (newSessions.length) {
          try {
            await createCourseSessionsBulk(
              course.id,
              newSessions.map((s) => ({
                sessionNo: s.sessionNo,
                // topic 은 nullable 이 아니다 — 빈 값이면 키 자체를 뺀다(null 을 보내면 422)
                ...(s.topic?.trim() ? { topic: s.topic.trim() } : {}),
                ...(isOnline
                  ? {}
                  : {
                      startsAt: `${s.date}T${s.startTime}:00+09:00`,
                      endsAt: `${s.date}T${s.endTime}:00+09:00`,
                      location: f.location,
                    }),
              })),
            );
          } catch (error) {
            this.$q.notify({
              type: 'negative',
              message: `강의는 저장되었지만 회차 저장에 실패했습니다: ${error.message || ''}`.trim(),
              position: 'top',
            });
            this.$router.push(`/instructor/courses/${course.id}`);
            return;
          }
        }

        // 첨부 연결 — 업로드는 선택 시점에 끝났고 여기서 강좌에 붙인다.
        // 편집 모드에서 attachmentId 가 있는 것(이미 연결됨)은 건너뛴다 — 중복 첨부 방지.
        for (const a of f.attachments) {
          if (!a.fileId || a.attachmentId) continue;
          try {
            await attachCourseFile(course.id, { fileId: a.fileId });
          } catch (error) {
            this.$q.notify({
              type: 'negative',
              message: `강의는 저장되었지만 첨부 연결에 실패했습니다: ${error.message || ''}`.trim(),
              position: 'top',
            });
            this.$router.push(`/instructor/courses/${course.id}`);
            return;
          }
        }

        const shouldPublish = this.canPublishCourse && f.visibility !== 'DRAFT';
        if (shouldPublish) {
          // 강좌·회차는 이미 저장됐다 — 공개만 실패하면 여기서 멈추고 강좌 화면으로 보낸다.
          // 이 화면에 남아 [등록]을 다시 누르면 회차가 한 번 더 만들어진다
          try {
            await publishCourse(course.id);
          } catch (error) {
            this.$q.notify({
              type: 'warning',
              message: `강의는 저장됐지만 공개하지 못했습니다: ${error.message || ''}`.trim(),
              position: 'top',
              timeout: 0,
              actions: [{ label: '닫기', color: 'white' }],
            });
            this.$router.push(`/instructor/courses/${course.id}`);
            return;
          }
        }
        this.$q.notify({
          type: 'positive',
          message: this.publishDoneMessage(shouldPublish),
          position: 'top',
        });
        this.$router.push('/instructor/courses');
      } catch (error) {
        // 이 화면에 남는다 — 아직 반영 안 된 입력이 있으니 나가기 확인을 다시 켠다
        this.skipLeaveGuard = false;
        this.$q.notify({
          type: 'negative',
          message: courseSaveErrorMessage(error, '등록에 실패했습니다.'),
          position: 'top',
        });
      } finally {
        this.saving = false;
      }
    },
  },
};
