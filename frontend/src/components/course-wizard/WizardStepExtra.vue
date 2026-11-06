<template>
  <!-- 3단계 · 추가 정보 — 왼쪽 비용·유의사항·첨부, 오른쪽 수료 기준 -->
  <div class="wizard-main">
    <div class="wizard-card">
      <div class="fee-split">
        <div v-for="fee in FEES" :key="fee.key" class="fee-col">
          <div class="wizard-card__title">{{ fee.title }}</div>
          <div class="fee-row">
            <div class="field fee-row__type">
              <div class="field-label">{{ fee.label }} <span class="req">*</span></div>
              <q-option-group v-model="form[fee.typeKey]" type="radio" inline :options="fee.options" />
            </div>
            <div class="field fee-row__amount" :data-field="fee.amountKey">
              <!-- 있음·유료일 때만 금액이 필수다(검증도 그렇다) — 별표도 그때만 -->
              <div class="field-label">금액 <span v-if="form[fee.typeKey] === 'PAID'" class="req">*</span></div>
              <q-input
                v-model.number="form[fee.amountKey]"
                type="number"
                min="0"
                outlined
                dense
                :placeholder="fee.placeholder"
                :disable="form[fee.typeKey] !== 'PAID'"
                :error="!!errors[fee.amountKey]"
                :error-message="errors[fee.amountKey]"
              >
                <template #append><span class="input-unit">원</span></template>
              </q-input>
            </div>
          </div>
          <div class="field-note">• {{ fee.note }}</div>
        </div>
      </div>
    </div>

    <div class="wizard-card">
      <div class="field">
        <div class="field-label">학습 전 유의사항 <span class="opt">(선택)</span></div>
        <q-editor
          v-model="form.notes"
          :min-height="$q.screen.xs ? '200px' : '357px'"
          :toolbar="editorToolbar"
          class="wz-editor"
          @paste="pasteMarkdown"
        />
      </div>

      <div class="field">
        <!-- 첨부는 검증하지 않는 선택 항목이다 -->
        <div class="field-label">첨부파일 <span class="opt">(선택)</span></div>
        <div class="attach-pick">
          <q-btn
            unelevated
            no-caps
            class="btn-attach"
            label="파일 선택"
            :disable="wizard.attaching"
            @click="$refs.attachInput.click()"
          />
          <!-- 한 개씩 올라가 시간이 걸린다 — 진행 중임을 보여 주지 않으면 도중에 등록해 파일이 빠졌다 -->
          <div v-if="wizard.attaching" class="attach-pick__status" role="status">
            <q-spinner size="14px" color="primary" />
            업로드 중 {{ wizard.attachDone }}/{{ wizard.attachTotal }}
          </div>
          <div v-else class="attach-pick__status">
            {{ form.attachments.length ? `${form.attachments.length}개 선택됨` : '선택된 파일 없음' }}
          </div>
        </div>
        <div class="field-help">첨부 가능한 파일 형식: jpg, jpeg, png, pdf, doc, docx, hwp (최대 10MB)</div>
        <div v-for="(f, i) in form.attachments" :key="f.fileId || i" class="attach-row">
          <q-icon name="insert_drive_file" size="20px" class="attach-row__icon" />
          <span class="attach-row__name">{{ f.name }}</span>
          <span class="attach-row__size">{{ (f.size / 1024 / 1024).toFixed(1) }}MB</span>
          <q-btn
            flat
            round
            dense
            icon="close"
            size="sm"
            :aria-label="`${f.name} 첨부 삭제`"
            @click="wizard.removeAttachment(i)"
          />
        </div>
        <input
          ref="attachInput"
          type="file"
          multiple
          :accept="ATTACHMENT_ACCEPT"
          class="hidden"
          @change="wizard.onAttachmentsChange"
        />
      </div>
    </div>
  </div>

  <div class="wizard-sub">
    <div class="wizard-card">
      <div class="wizard-card__title">수료 기준 설정</div>
      <div class="field">
        <div class="field-label">수료증 <span class="req">*</span></div>
        <WizardRadioCards v-model="form.completionEnabled" :options="COMPLETION_ENABLED_OPTIONS" tall />
      </div>

      <div v-if="form.completionEnabled" class="field">
        <div class="field-label">수료 기준 <span class="req">*</span></div>
        <WizardRadioCards v-model="form.completionCriteria" :options="COMPLETION_CRITERIA_OPTIONS" tall />
      </div>

      <div
        v-if="form.completionEnabled && form.completionCriteria === 'PROGRESS'"
        class="field"
        data-field="requiredProgressRate"
      >
        <div class="field-label">필요 출석률 <span class="req">*</span></div>
        <q-input
          v-model.number="form.requiredProgressRate"
          type="number"
          min="0"
          max="100"
          outlined
          class="wz-input"
          :error="!!errors.requiredProgressRate"
          :error-message="errors.requiredProgressRate"
        >
          <template #append><span class="input-unit">% 이상</span></template>
        </q-input>
      </div>

      <div v-if="form.completionEnabled" class="field">
        <div class="field-label">수료 시 제공</div>
        <div class="radio-row radio-row--tall">
          <q-checkbox v-model="form.issueCertificate" dense aria-label="수료증 발급" />
          <div class="radio-text">
            <div class="radio-label">수료증 발급</div>
            <div class="radio-caption">수료 기준을 충족한 수강생에게 수료증을 발급합니다.</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import WizardRadioCards from '@/components/course-wizard/WizardRadioCards.vue';
import {
  ATTACHMENT_ACCEPT,
  COMPLETION_CRITERIA_OPTIONS,
  COMPLETION_ENABLED_OPTIONS,
} from '@/config/course-options';
import { pasteMarkdownAsRichText } from '@/utils/markdown-paste';
import { EDITOR_TOOLBAR } from '@/utils/rich-text';

// 교육비·재료비 — 같은 모양 두 칸을 한 틀로
const FEES = [
  {
    key: 'tuition',
    title: '교육비 정보',
    label: '교육비',
    typeKey: 'tuitionType',
    amountKey: 'tuitionAmount',
    placeholder: '30,000',
    options: [
      { label: '무료', value: 'FREE' },
      { label: '유료', value: 'PAID' },
    ],
    note: '유료 강의는 수강생에게 안내되는 금액이며, 강사와 수강생 간의 별도 협의로 진행됩니다.',
  },
  {
    key: 'material',
    title: '재료비 정보',
    label: '재료비',
    typeKey: 'materialFeeType',
    amountKey: 'materialFeeAmount',
    placeholder: '10,000',
    options: [
      { label: '없음', value: 'NONE' },
      { label: '있음', value: 'PAID' },
    ],
    note: '재료비는 강의에 필요한 재료 구매 비용을 안내하는 금액입니다.',
  },
];

export default {
  name: 'WizardStepExtra',
  components: { WizardRadioCards },
  inject: ['wizard'],
  data() {
    return {
      FEES,
      ATTACHMENT_ACCEPT,
      COMPLETION_CRITERIA_OPTIONS,
      COMPLETION_ENABLED_OPTIONS,
      editorToolbar: EDITOR_TOOLBAR,
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
    pasteMarkdown(event) {
      pasteMarkdownAsRichText(event);
    },
  },
};
</script>
