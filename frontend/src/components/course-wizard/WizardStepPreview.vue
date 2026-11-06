<template>
  <!-- 4단계 · 미리 보기 — 학습자 강좌 상세와 같은 컴포넌트에 입력값을 넣는다.
       따로 그리던 미리보기(CoursePreview)가 옛 화면(교육장소 탭 등)으로 어긋나 실제 모습과 달랐다.
       화면 폭만 줄이던 '모바일 미리보기'는 실제 폰 배치를 보여 주지 못해 뺐다 -->
  <div class="wizard-main">
    <div class="preview-toolbar">
      <div>
        <div class="wizard-card__title">강의 미리보기</div>
        <div class="field-help">수강생이 강좌 상세 페이지에서 보게 될 화면입니다. 신청 버튼은 눌리지 않습니다.</div>
      </div>
    </div>
    <div class="wizard-card wizard-preview">
      <CourseDetailHead
        :course="course"
        :institution-name="wizard.institutionName"
        :sessions="sessions"
        enroll-disabled
        enroll-disabled-reason="미리보기에서는 신청할 수 없습니다."
      />
      <CourseIntroSection :description="course.description" :detail="course.detail" />
      <CourseSessionsSection :sessions="sessions" :is-online="wizard.isOnlineCourse" />
      <CourseInstructorsSection :instructors="instructors" />
      <CourseNotesSection :notes="course.detail.notes || ''" :attachments="attachments" />
    </div>
  </div>

  <div class="wizard-sub">
    <div class="wizard-card">
      <div class="wizard-card__title">등록 전 체크사항</div>
      <!-- 필수 — 빠진 것을 하나씩, 그 단계로 가는 길과 함께 -->
      <div class="checklist" role="status">
        <div v-if="!checklist.missing.length" class="checklist__row checklist__row--ok">
          <q-icon name="check_circle" size="20px" class="checklist__ok" aria-hidden="true" />
          <span>필수 정보를 모두 입력했습니다.</span>
        </div>
        <template v-else>
          <div class="checklist__title">필수 {{ checklist.missing.length }}개가 비어 있습니다</div>
          <button
            v-for="item in checklist.missing"
            :key="item.message"
            type="button"
            class="checklist__row checklist__row--missing"
            @click="wizard.fixMissing(item.step)"
          >
            <q-icon name="error_outline" size="20px" class="checklist__todo" aria-hidden="true" />
            <span class="checklist__text">{{ item.message }}</span>
            <span class="checklist__step">{{ item.step }}단계</span>
          </button>
        </template>
      </div>
      <!-- 선택 — 비어도 등록된다. 아이콘·색만으로 구분하지 않게 글자로 '있음·없음' -->
      <div class="checklist checklist--optional">
        <div class="checklist__title">선택 항목</div>
        <div v-for="item in checklist.optional" :key="item.label" class="checklist__row">
          <q-icon
            :name="item.ok ? 'check_circle' : 'radio_button_unchecked'"
            size="18px"
            :class="item.ok ? 'checklist__ok' : 'checklist__none'"
            aria-hidden="true"
          />
          <span class="checklist__text">{{ item.label }}</span>
          <span class="checklist__state">{{ item.ok ? '있음' : '없음' }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import CourseDetailHead from '@/components/course/detail/CourseDetailHead.vue';
import CourseInstructorsSection from '@/components/course/detail/CourseInstructorsSection.vue';
import CourseIntroSection from '@/components/course/detail/CourseIntroSection.vue';
import CourseNotesSection from '@/components/course/detail/CourseNotesSection.vue';
import CourseSessionsSection from '@/components/course/detail/CourseSessionsSection.vue';
import { buildCourseDetail } from '@/utils/course-detail';

/** 'YYYY-MM-DD' + 'HH:mm' → KST ISO. 둘 중 하나라도 비면 null */
function kstIso(date, time) {
  return date && time ? `${date}T${time}:00+09:00` : null;
}

export default {
  name: 'WizardStepPreview',
  components: {
    CourseDetailHead,
    CourseInstructorsSection,
    CourseIntroSection,
    CourseNotesSection,
    CourseSessionsSection,
  },
  inject: ['wizard'],
  computed: {
    checklist() {
      return this.wizard.checklist;
    },
    /** 학습자 화면이 받는 강좌 응답 모양으로 — 상태는 공개된 뒤 학습자가 보는 '모집중' */
    course() {
      const f = this.wizard.form;
      return {
        title: f.title.trim() || '강의명 미입력',
        description: f.description || '',
        educationType: f.educationType,
        status: 'RECRUITING',
        recruitStartDate: f.recruitStartDate || null,
        recruitEndDate: f.recruitEndDate || null,
        startDate: this.wizard.previewStartDate || null,
        endDate: this.wizard.previewEndDate || null,
        capacity: f.capacity || null,
        enrolledCount: 0,
        location: this.wizard.isOnlineCourse ? null : f.location,
        thumbnailFileId: f.thumbnail?.id || null,
        detail: buildCourseDetail(f),
      };
    },
    sessions() {
      return this.wizard.form.sessions.map((s, i) => ({
        id: s.id || `preview-${i}`,
        sessionNo: s.sessionNo,
        topic: s.topic,
        startsAt: kstIso(s.date, s.startTime),
        endsAt: kstIso(s.date, s.endTime),
      }));
    },
    instructors() {
      const user = this.wizard.previewInstructor;
      if (!user) return [];
      return [{ userId: user.id || 'me', instructorName: user.name, introduction: user.introduction, career: user.career }];
    },
    attachments() {
      return this.wizard.form.attachments.map((a) => ({
        id: a.attachmentId || a.fileId,
        fileId: a.fileId,
        fileName: a.name,
        byteSize: a.size,
        // 첨부는 이번에 올린 파일이다 — 날짜 칸이 '-'로 비지 않게 오늘로
        createdAt: new Date().toISOString(),
      }));
    },
  },
};
</script>
