<template>
  <!-- 1단계 · 기본 정보 — 왼쪽 본문, 오른쪽 공개·유형·강사 프로필·대표 이미지 -->
  <div class="wizard-main">
    <div class="wizard-card">
      <div class="wizard-card__title">기본 정보</div>

      <!-- TODO(figma): 기관 선택 필드 디자인 미확보 — 다중 소속이거나, 소속이 없는 플랫폼 관리자일 때 렌더 -->
      <div v-if="wizard.institutionOptions.length > 1 || wizard.needsInstitutionPick" class="field" data-field="institution">
        <div class="field-label">소속 기관 <span class="req">*</span></div>
        <q-select
          v-model="form.institutionId"
          outlined
          dense
          emit-value
          map-options
          :options="wizard.institutionOptions"
          label="기관 선택"
          class="field--institution"
          :error="!!errors.institution"
          :error-message="errors.institution"
        />
      </div>

      <!-- 피그마의 카테고리 셀렉트(평면 9개)를 표준 강좌 분류(설계서 §12) 대·중·소 선택으로 바꿨다.
           임시저장은 비워 둬도 되고, 공개할 때 필수다 -->
      <div class="field" data-field="classification">
        <div class="field-label">강좌 분류 <span class="req">*</span></div>
        <ClassificationPicker v-model="form.classificationCode" :current-path="form.classificationPath" />
        <div v-if="errors.classification" class="field-error" role="alert">{{ errors.classification }}</div>
      </div>

      <div class="field" data-field="title">
        <div class="field-label">강의 제목 <span class="req">*</span></div>
        <q-input
          v-model="form.title"
          outlined
          maxlength="60"
          placeholder="강의 제목을 입력해주세요 (최대 60자)"
          class="wz-input field--title"
          :error="!!errors.title"
          :error-message="errors.title"
        >
          <template #append>
            <span class="input-counter">{{ form.title.length }}/60</span>
          </template>
        </q-input>
      </div>

      <div class="field" data-field="description">
        <div class="field-label">강의 설명 <span class="req">*</span></div>
        <!-- 폰에서 387px 편집기가 화면 절반을 덮었다 -->
        <q-editor
          v-model="form.description"
          :min-height="$q.screen.xs ? '220px' : '387px'"
          :toolbar="editorToolbar"
          placeholder="강의에 대해 자세히 설명해주세요. 강의 목표, 주요 내용, 이런분들께 추천드려요 등을 포함해 작성해주세요."
          class="wz-editor"
          @paste="pasteMarkdown"
        />
        <div v-if="errors.description" class="field-error" role="alert">{{ errors.description }}</div>
      </div>

      <div class="field" data-field="tags">
        <!-- 선택 — 없다고 등록을 막을 일이 아니다 -->
        <div class="field-label">태그 <span class="opt">(선택)</span></div>
        <!-- 띄어쓰기·쉼표·Enter 로 칩이 된다 — 예전 한 줄 입력엔 긴 문장 하나를 넣곤 했다 -->
        <WizardTagInput
          v-model="form.tags"
          placeholder="예: 엑셀 (입력 후 띄어쓰기)"
          hint="띄어쓰기·쉼표·Enter 로 태그가 만들어집니다. 강좌 카드에는 앞의 3개가 보입니다."
          :error="errors.tags || ''"
        />
      </div>

      <div class="field" data-field="targetAudiences">
        <div class="field-label">수강 대상 <span class="req">*</span></div>
        <q-option-group
          v-model="form.targetAudiences"
          type="checkbox"
          inline
          :options="TARGET_AUDIENCE_OPTIONS"
          class="wz-checkboxes"
        />
        <div v-if="errors.targetAudiences" class="field-error" role="alert">{{ errors.targetAudiences }}</div>
      </div>
    </div>
  </div>

  <div class="wizard-sub">
    <div class="wizard-card">
      <div class="wizard-card__title">강의 공개 설정</div>
      <div class="field">
        <div class="field-label">공개 상태 <span v-if="wizard.canPublishCourse" class="req">*</span></div>
        <!-- 공개(publish)는 기관 관리자·플랫폼 관리자만 한다 — 강사에게 고를 수 없는 선택지를 보이지 않는다 -->
        <p v-if="!wizard.canPublishCourse" class="visibility-note">
          등록하면 비공개로 저장됩니다. 기관 관리자가 공개하면 수강생 모집이 시작됩니다.
        </p>
        <WizardRadioCards v-else v-model="form.visibility" :options="VISIBILITY_OPTIONS" />
      </div>
      <div class="field">
        <div class="field-label">강의 유형 <span class="req">*</span></div>
        <WizardRadioCards v-model="form.educationType" :options="EDUCATION_TYPE_WIZARD_OPTIONS" />
      </div>
    </div>

    <div class="wizard-card">
      <div class="wizard-card__title">강사 소개</div>
      <div class="field">
        <div class="field-label">강좌 상세에 내 소개 보이기 <span class="req">*</span></div>
        <WizardRadioCards v-model="form.instructorProfileVisible" :options="PROFILE_VISIBLE_OPTIONS" tall />
      </div>
    </div>

    <div v-if="wizard.showInstructorAssignField" class="wizard-card">
      <div class="field">
        <div class="field-label">담당 강사</div>
        <!-- q-select 의 placeholder 는 use-input 일 때만 보여 빈 칸으로 보였다 — label 로 -->
        <q-select
          v-model="wizard.selectedInstructorId"
          outlined
          dense
          clearable
          emit-value
          map-options
          :options="wizard.instructorOptions"
          :loading="wizard.loadingInstructors"
          :disable="!wizard.courseInstitutionId"
          label="담당 강사 선택 (선택 사항)"
        />
        <div v-if="!wizard.courseInstitutionId" class="field-help">소속 기관을 먼저 고르면 그 기관의 강사를 고를 수 있습니다.</div>
        <div v-else-if="!wizard.loadingInstructors && !wizard.instructorOptions.length" class="field-help">
          선택할 수 있는 강사가 없습니다. 담당 강사 없이 등록할 수 있습니다.
        </div>
      </div>
    </div>

    <div class="wizard-card" data-field="thumbnail">
      <!-- 선택 — 사진이 없는 강사가 등록 자체를 못 했다. 없으면 강좌 카드에 기본 그림이 나온다 -->
      <div class="field-label">대표 이미지 <span class="opt">(선택)</span></div>
      <div
        class="thumb-box"
        role="button"
        tabindex="0"
        aria-label="대표 이미지 올리기"
        @click="pickThumbnail"
        @keydown="onThumbKeydown"
      >
        <q-spinner v-if="wizard.uploading" color="primary" size="32px" />
        <template v-else-if="form.thumbnail">
          <img :src="form.thumbnail.url" alt="대표 이미지 미리보기" class="thumb-img" />
          <q-btn
            aria-label="대표 이미지 삭제"
            round
            dense
            unelevated
            icon="close"
            size="sm"
            class="thumb-remove"
            @click.stop="form.thumbnail = null"
          />
        </template>
        <template v-else>
          <q-icon name="cloud_upload" size="43px" class="thumb-box__icon" />
          <div class="thumb-box__text">이미지 업로드</div>
        </template>
      </div>
      <div class="thumb-caption">권장 1200×675 (16:9) · 최대 5MB</div>
      <div v-if="errors.thumbnail" class="field-error" role="alert">{{ errors.thumbnail }}</div>
      <input ref="thumbInput" type="file" accept="image/jpeg,image/png" class="hidden" @change="wizard.onThumbnailChange" />
    </div>
  </div>
</template>

<script>
import ClassificationPicker from '@/components/course/ClassificationPicker.vue';
import WizardRadioCards from '@/components/course-wizard/WizardRadioCards.vue';
import WizardTagInput from '@/components/course-wizard/WizardTagInput.vue';
import {
  EDUCATION_TYPE_WIZARD_OPTIONS,
  PROFILE_VISIBLE_OPTIONS,
  TARGET_AUDIENCE_OPTIONS,
  VISIBILITY_OPTIONS,
} from '@/config/course-options';
import { pasteMarkdownAsRichText } from '@/utils/markdown-paste';
import { EDITOR_TOOLBAR } from '@/utils/rich-text';

/* 위저드 상태(form·오류·기관)는 페이지가 provide 한 wizard 하나로 받는다 — 단계마다 props 수십 개를 오가지 않게 */
export default {
  name: 'WizardStepBasic',
  components: { ClassificationPicker, WizardRadioCards, WizardTagInput },
  inject: ['wizard'],
  data() {
    return {
      editorToolbar: EDITOR_TOOLBAR,
      EDUCATION_TYPE_WIZARD_OPTIONS,
      PROFILE_VISIBLE_OPTIONS,
      TARGET_AUDIENCE_OPTIONS,
      VISIBILITY_OPTIONS,
    };
  },
  computed: {
    form() {
      return this.wizard.form;
    },
    errors() {
      return this.wizard.fieldErrors;
    },
  },
  methods: {
    // 마크다운 글을 붙여 넣으면 제목·목록·표로 바꿔 넣는다
    pasteMarkdown(event) {
      pasteMarkdownAsRichText(event);
    },
    pickThumbnail() {
      if (this.wizard.uploading) return;
      this.$refs.thumbInput.click();
    },
    /** 대표 이미지 칸은 div 라 Enter·Space 로도 열리게 한다 */
    onThumbKeydown(event) {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        this.pickThumbnail();
      }
    },
  },
};
</script>
