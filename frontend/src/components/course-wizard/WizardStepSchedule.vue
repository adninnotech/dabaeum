<template>
  <!-- 2단계 · 강의 일정 및 구성 — 한 줄기로: 수업 일정 → 모집 설정 → 장소·회차.
       모집 설정이 오른쪽 좁은 칸(320px)에 따로 있어 넓은 화면에서 놓치기 쉬웠다 -->
  <div class="wizard-main">
    <!-- 오프라인/하이브리드 -->
    <template v-if="!wizard.isOnlineCourse">
      <div class="wizard-card">
        <div class="wizard-card__title">수업 일정</div>

        <div class="field">
          <div class="field-label">일정 만드는 방법 <span class="req">*</span></div>
          <div class="field-help">첫 수업일·요일·시간·회차를 다 고르면 회차가 바로 만들어집니다.</div>
          <div class="segment" role="radiogroup" aria-label="수업 일정 만드는 방법">
            <button
              v-for="mode in SCHEDULE_MODES"
              :key="mode.value"
              type="button"
              role="radio"
              class="segment__btn"
              :class="{ active: form.scheduleMode === mode.value }"
              :aria-checked="form.scheduleMode === mode.value ? 'true' : 'false'"
              @click="form.scheduleMode = mode.value"
            >
              {{ mode.label }}
            </button>
          </div>
        </div>

        <template v-if="form.scheduleMode === 'REPEAT'">
          <!-- 생각하는 순서대로 — 언제부터 → 무슨 요일 → 몇 시 → 몇 번. 칸 높이는 모두 같게(hide-bottom-space) -->
          <div class="repeat-grid">
            <div class="field">
              <div class="field-label">첫 수업일 <span class="req">*</span></div>
              <AppDateInput v-model="form.repeat.firstDate" outlined dense hide-bottom-space class="wz-date" />
            </div>

            <div class="field">
              <div class="field-label">반복 요일 <span class="req">*</span></div>
              <div class="weekday-row">
                <button
                  v-for="day in WEEKDAY_OPTIONS"
                  :key="day.value"
                  type="button"
                  class="weekday-chip"
                  :class="{ active: form.repeat.weekdays.includes(day.value) }"
                  :aria-pressed="form.repeat.weekdays.includes(day.value) ? 'true' : 'false'"
                  @click="wizard.toggleWeekday(day.value)"
                >
                  {{ day.label }}
                </button>
              </div>
            </div>

            <div class="field">
              <div class="field-label">강의 시간 <span class="req">*</span></div>
              <div class="time-row">
                <AppTimeInput v-model="form.repeat.startTime" outlined dense hide-bottom-space class="wz-time" />
                <span class="time-row__tilde">~</span>
                <AppTimeInput v-model="form.repeat.endTime" outlined dense hide-bottom-space class="wz-time" />
              </div>
            </div>

            <div class="field">
              <div class="field-label">총 회차 <span class="req">*</span></div>
              <div class="count-row">
                <q-input
                  v-model.number="form.repeat.count"
                  type="number"
                  min="1"
                  outlined
                  dense
                  hide-bottom-space
                  class="wz-count"
                />
                <span class="count-row__unit">회</span>
              </div>
            </div>
          </div>

          <!-- 다 고르면 저절로 만든다([일정 생성]을 깜빡하면 '회차를 추가해 주세요' 오류가 났다).
               회차를 손으로 고친 뒤 일정을 바꿨을 때만 [다시 만들기]가 필요하다 -->
          <div class="schedule-summary-row">
            <div v-if="wizard.scheduleSummary" class="schedule-summary" role="status">
              <q-icon name="event_available" size="20px" aria-hidden="true" />
              {{ wizard.scheduleSummary }}
            </div>
            <q-btn
              no-caps
              outline
              color="primary"
              class="btn-generate"
              :label="form.sessions.length ? '회차 다시 만들기' : '일정 생성'"
              @click="wizard.onGenerate"
            />
          </div>
        </template>
        <div v-else class="field-help">아래 회차 표에서 회차를 직접 추가하세요.</div>
      </div>
    </template>

    <!-- 온라인 — 학습 기간 -->
    <div v-else class="wizard-card">
      <div class="wizard-card__title">학습 기간</div>
      <div class="row-fields">
        <div class="field field--half" data-field="startDate">
          <div class="field-label">학습 시작일 <span class="req">*</span></div>
          <AppDateInput
            v-model="form.startDate"
            outlined
            class="wz-input"
            :error="!!errors.startDate"
            :error-message="errors.startDate"
          />
        </div>
        <div class="field field--half" data-field="endDate">
          <div class="field-label">학습 마감일 <span class="req">*</span></div>
          <AppDateInput
            v-model="form.endDate"
            outlined
            class="wz-input"
            :min="form.startDate || ''"
            :error="!!errors.endDate"
            :error-message="errors.endDate"
          />
        </div>
      </div>
    </div>

    <!-- 모집 설정 — 일정 바로 아래 한 줄 -->
    <div class="wizard-card">
      <div class="wizard-card__title">모집 설정</div>
      <div v-if="!wizard.isOnlineCourse" class="field-help recruit-help">
        회차를 만들면 모집 기간이 오늘부터 첫 수업 전날까지로 채워집니다. 고칠 수 있습니다.
      </div>
      <div class="recruit-grid">
        <div class="field" data-field="recruitStartDate">
          <div class="field-label">모집 시작일 <span class="req">*</span></div>
          <AppDateInput
            v-model="form.recruitStartDate"
            outlined
            dense
            class="wz-input"
            :error="!!errors.recruitStartDate"
            :error-message="errors.recruitStartDate"
          />
        </div>
        <div class="field" data-field="recruitEndDate">
          <div class="field-label">모집 마감일 <span class="req">*</span></div>
          <AppDateInput
            v-model="form.recruitEndDate"
            outlined
            dense
            class="wz-input"
            :min="form.recruitStartDate || ''"
            :error="!!errors.recruitEndDate"
            :error-message="errors.recruitEndDate"
          />
        </div>
        <div class="field" data-field="capacity">
          <div class="field-label">모집 인원 <span class="req">*</span></div>
          <q-input
            v-model.number="form.capacity"
            type="number"
            min="1"
            outlined
            dense
            class="wz-input"
            :error="!!errors.capacity"
            :error-message="errors.capacity"
          >
            <template #append><span class="input-unit">명</span></template>
          </q-input>
        </div>
        <div class="field">
          <div class="field-label">수강생 고르는 방법 <span class="req">*</span></div>
          <q-select
            v-model="form.selectionMethod"
            outlined
            dense
            emit-value
            map-options
            :options="SELECTION_METHOD_OPTIONS"
            class="wz-input"
          />
        </div>
        <div class="field">
          <div class="field-label">대기 인원 <span class="opt">(선택)</span></div>
          <q-input
            v-model.number="form.waitlistCapacity"
            type="number"
            min="0"
            outlined
            dense
            class="wz-input"
            hint="정원이 차면 이만큼 대기자로 받습니다"
          >
            <template #append><span class="input-unit">명</span></template>
          </q-input>
        </div>
      </div>
    </div>

    <!-- 장소·회차 -->
    <div class="wizard-card">
      <div v-if="!wizard.isOnlineCourse" class="field" data-field="location">
        <div class="field-label">강의 장소 <span class="req">*</span></div>
        <!-- 장소 검색(주소 API) 연동 전 — "준비 중"만 뜨던 버튼은 뺐다 -->
        <q-input
          v-model="form.location"
          outlined
          placeholder="장소를 입력해 주세요."
          class="wz-input"
          :error="!!errors.location"
          :error-message="errors.location"
        />
      </div>
      <!-- 온라인 — 영상 업로드는 서버 저장이 없어 꺼진 버튼뿐이라 칸을 뺐다(주제만) -->
      <WizardSessionHead
        :help="
          wizard.isOnlineCourse
            ? '회차별 주제를 입력해 주세요. 강의 영상 등록은 준비 중입니다.'
            : '주제를 적어 주세요. 날짜·시간은 일정대로 들어가 있고, 바꿀 회차만 [고치기]로 엽니다.'
        "
      />
      <WizardSessionList :online="wizard.isOnlineCourse" />
      <div v-if="errors.sessions" class="field-error" role="alert">{{ errors.sessions }}</div>
    </div>
  </div>
</template>

<script>
import AppDateInput from '@/components/AppDateInput.vue';
import AppTimeInput from '@/components/AppTimeInput.vue';
import WizardSessionHead from '@/components/course-wizard/WizardSessionHead.vue';
import WizardSessionList from '@/components/course-wizard/WizardSessionList.vue';
import { SELECTION_METHOD_OPTIONS, WEEKDAY_OPTIONS } from '@/config/course-options';

const SCHEDULE_MODES = [
  { value: 'REPEAT', label: '반복 일정으로 만들기' },
  { value: 'MANUAL', label: '직접 입력하기' },
];

export default {
  name: 'WizardStepSchedule',
  components: { AppDateInput, AppTimeInput, WizardSessionHead, WizardSessionList },
  inject: ['wizard'],
  data() {
    return { SCHEDULE_MODES, SELECTION_METHOD_OPTIONS, WEEKDAY_OPTIONS };
  },
  computed: {
    form() {
      return this.wizard.form;
    },
    errors() {
      return this.wizard.fieldErrors;
    },
  },
};
</script>
