<template>
  <!-- 회차 일괄 생성 — 학습 목차 탭. 마법사에만 있어 게시된 강의는 한 회차씩 추가해야 했다(QA CRS-10).
       기존 회차는 그대로 두고 뒤에 붙인다(replaceExisting 을 보내지 않는다) -->
  <q-dialog :model-value="modelValue" :persistent="saving" @update:model-value="close" @before-show="reset">
    <q-card class="session-form">
      <q-card-section class="session-form__head">
        <div class="text-h6">회차 일괄 생성</div>
        <q-btn v-close-popup flat round dense icon="close" aria-label="닫기" :disable="saving" />
      </q-card-section>
      <q-separator />

      <q-card-section class="session-form__body">
        <fieldset class="session-form__group">
          <legend class="session-form__legend">기간 *</legend>
          <div class="session-form__pair">
            <AppDateInput v-model="startDate" outlined dense label="시작일" :min="courseStartDate" :max="courseEndDate" />
            <AppDateInput v-model="endDate" outlined dense label="종료일" :min="startDate || courseStartDate" :max="courseEndDate" />
          </div>
        </fieldset>

        <fieldset class="session-form__group">
          <legend class="session-form__legend">요일 *</legend>
          <!-- q-btn-toggle 은 하나만 고르는 컴포넌트라 요일이 문자열 하나가 돼 회차 수가 0 이었고,
               고정 색(grey-3·dark)이라 다크 모드에서 안 보였다(QA CRS-10) — 마법사와 같은 칩 버튼으로 -->
          <div class="weekday-row" role="group" aria-label="수업 요일">
            <button
              v-for="day in WEEKDAY_OPTIONS"
              :key="day.value"
              type="button"
              class="weekday-chip"
              :class="{ 'weekday-chip--on': daysOfWeek.includes(day.value) }"
              :aria-pressed="daysOfWeek.includes(day.value) ? 'true' : 'false'"
              @click="toggleDay(day.value)"
            >
              {{ day.label }}
            </button>
          </div>
        </fieldset>

        <fieldset class="session-form__group">
          <legend class="session-form__legend">수업 시간 *</legend>
          <div class="session-form__pair">
            <q-input v-model="startTime" outlined dense type="time" label="시작" />
            <q-input v-model="endTime" outlined dense type="time" label="종료" />
          </div>
        </fieldset>

        <p class="session-form__help" role="status">
          <template v-if="dates.length">
            {{ dates[0] }} 부터 {{ dates.length }}개 회차가 {{ nextSessionNo }}회차부터 만들어집니다.
          </template>
          <template v-else>기간과 요일을 고르면 만들어질 회차 수가 보입니다.</template>
        </p>
        <p v-if="error" class="session-form__error" role="alert">{{ error }}</p>
      </q-card-section>

      <q-card-actions align="right" class="session-form__actions">
        <q-btn v-close-popup flat no-caps label="취소" :disable="saving" />
        <q-btn
          unelevated
          no-caps
          color="primary"
          :label="dates.length ? `${dates.length}개 만들기` : '만들기'"
          :loading="saving"
          @click="submit"
        />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import AppDateInput from '@/components/AppDateInput.vue';
import { createCourseSessionsRepeat } from '@/services/session-api';
import { REPEAT_COUNT_MAX, repeatDates, WEEKDAY_OPTIONS } from '@/utils/session-repeat';

const TIME = /^\d{2}:\d{2}$/;

export default {
  name: 'SessionBulkDialog',
  components: { AppDateInput },
  props: {
    modelValue: { type: Boolean, default: false },
    courseId: { type: String, required: true },
    /** 새 회차가 붙을 첫 번호 — 안내용(번호는 서버가 매긴다) */
    nextSessionNo: { type: Number, default: 1 },
    /** 강좌 운영 기간(YYYY-MM-DD) — 이 밖의 날짜는 고를 수 없다 */
    courseStartDate: { type: String, default: '' },
    courseEndDate: { type: String, default: '' },
  },
  emits: ['update:modelValue', 'saved'],
  data() {
    return {
      WEEKDAY_OPTIONS,
      startDate: '',
      endDate: '',
      daysOfWeek: [],
      startTime: '',
      endTime: '',
      error: '',
      saving: false,
    };
  },
  computed: {
    dates() {
      return repeatDates(this.startDate, this.endDate, this.daysOfWeek);
    },
  },
  methods: {
    toggleDay(value) {
      this.daysOfWeek = this.daysOfWeek.includes(value)
        ? this.daysOfWeek.filter((day) => day !== value)
        : [...this.daysOfWeek, value];
    },
    close(value) {
      if (!this.saving) this.$emit('update:modelValue', value);
    },
    reset() {
      this.startDate = this.courseStartDate || '';
      this.endDate = this.courseEndDate || '';
      this.daysOfWeek = [];
      this.startTime = '';
      this.endTime = '';
      this.error = '';
    },
    validate() {
      if (!this.startDate || !this.endDate) return '시작일과 종료일을 골라 주세요.';
      if (!this.daysOfWeek.length) return '수업 요일을 하나 이상 골라 주세요.';
      if (!TIME.test(this.startTime) || !TIME.test(this.endTime)) return '수업 시작·종료 시간을 입력해 주세요.';
      if (this.endTime <= this.startTime) return '종료 시간은 시작 시간보다 늦어야 합니다.';
      if (!this.dates.length) return '기간 안에 고른 요일이 없습니다.';
      if (this.dates.length > REPEAT_COUNT_MAX) return `한 번에 ${REPEAT_COUNT_MAX}개까지 만들 수 있습니다.`;
      return '';
    },
    async submit() {
      this.error = this.validate();
      if (this.error) return;
      this.saving = true;
      try {
        // { sessions: { repeat } } 로 감싸 보내 서버가 거부했다 — 계약대로 { repeat } 하나만(QA CRS-10 재확인 중 발견)
        await createCourseSessionsRepeat(this.courseId, {
          firstDate: this.dates[0],
          daysOfWeek: this.daysOfWeek,
          startTime: this.startTime,
          endTime: this.endTime,
          count: this.dates.length,
        });
        this.$q.notify({ type: 'positive', message: `회차 ${this.dates.length}개를 만들었습니다.`, position: 'top' });
        this.saving = false;
        this.$emit('update:modelValue', false);
        this.$emit('saved');
      } catch (error) {
        this.error = error.message || '회차를 만들지 못했습니다.';
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.session-form {
  width: 520px;
  max-width: 94vw;
}

.session-form__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.session-form__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.session-form__group {
  margin: 0;
  padding: 0;
  border: 0;
  min-width: 0;
}

.session-form__legend {
  margin-bottom: 6px;
  padding: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--db-text-body);
}

.session-form__pair {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 8px;
}

.weekday-row {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 6px;
}

.weekday-chip {
  height: 36px;
  border: 1px solid var(--db-border);
  border-radius: 4px;
  background: var(--db-bg-white);
  color: var(--db-text-body);
  font-size: 14px;
  cursor: pointer;
}

.weekday-chip:focus-visible {
  outline: 2px solid var(--dabaeum-primary);
  outline-offset: 1px;
}

.weekday-chip--on {
  border-color: var(--dabaeum-primary);
  background: var(--dabaeum-primary);
  color: var(--db-on-color);
}

.session-form__help {
  margin: 0;
  font-size: 13px;
  color: var(--db-text-sub);
}

.session-form__error {
  margin: 0;
  font-size: 13px;
  color: var(--db-status-pink);
}

.session-form__actions {
  padding: 8px 16px 16px;
}

@media (max-width: 479px) {
  .session-form__pair {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
