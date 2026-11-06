<template>
  <!-- 회차 추가·수정 공통 — 학습 목차 탭·관리자/강사 회차 관리가 함께 쓴다. 날짜는 선택기, 저장할 때 ISO(KST)로 바꾼다 -->
  <q-dialog :model-value="modelValue" :persistent="saving" @update:model-value="close" @before-show="fill">
    <q-card class="session-form">
      <q-card-section class="session-form__head">
        <div class="text-h6">{{ isEdit ? '회차 수정' : '회차 추가' }}</div>
        <q-btn v-close-popup flat round dense icon="close" aria-label="닫기" :disable="saving" />
      </q-card-section>
      <q-separator />

      <q-card-section class="session-form__body">
        <q-input
          v-model.number="sessionNo"
          type="number"
          outlined
          dense
          min="1"
          label="회차 *"
          class="session-form__no"
          :error="!!errors.sessionNo"
          :error-message="errors.sessionNo"
        />

        <fieldset class="session-form__group">
          <legend class="session-form__legend">수업 시간 *</legend>
          <div class="session-form__pair">
            <AppDateTimeInput
              :model-value="startsAt"
              outlined
              dense
              stack-label
              label="시작"
              :error="!!errors.startsAt"
              :error-message="errors.startsAt"
              @update:model-value="moveStart"
            />
            <AppDateTimeInput
              v-model="endsAt"
              outlined
              dense
              stack-label
              label="종료"
              :error="!!errors.endsAt"
              :error-message="errors.endsAt"
            />
          </div>
        </fieldset>

        <q-input v-model="location" outlined dense maxlength="500" label="장소" />

        <fieldset class="session-form__group">
          <legend class="session-form__legend">출결 받는 시간</legend>
          <div class="session-form__pair">
            <AppDateTimeInput
              v-model="attendanceOpensAt"
              outlined
              dense
              stack-label
              label="시작"
              :error="!!errors.attendanceOpensAt"
              :error-message="errors.attendanceOpensAt"
            />
            <AppDateTimeInput
              v-model="attendanceClosesAt"
              outlined
              dense
              stack-label
              label="종료"
              :error="!!errors.attendanceClosesAt"
              :error-message="errors.attendanceClosesAt"
            />
          </div>
          <p class="session-form__help">새 회차는 수업 10분 전부터 끝난 뒤 10분까지로 채워 둡니다.</p>
        </fieldset>

        <q-select
          v-model="status"
          outlined
          dense
          emit-value
          map-options
          :options="SESSION_STATUS_OPTIONS"
          label="상태"
          class="session-form__status"
        />
      </q-card-section>

      <q-card-actions align="right" class="session-form__actions">
        <q-btn v-close-popup flat no-caps label="취소" :disable="saving" />
        <q-btn unelevated no-caps color="primary" :label="isEdit ? '저장' : '추가'" :loading="saving" @click="submit" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import AppDateTimeInput from '@/components/AppDateTimeInput.vue';
import { createCourseSession, updateCourseSession } from '@/services/session-api';
import { fromDateTimeLocalKst, toDateTimeLocalKst } from '@/utils/datetime';
import { SESSION_STATUS_OPTIONS } from '@/utils/status';

const DEFAULT_LEAD_DAYS = 7;
const DEFAULT_LENGTH_HOURS = 2;
const ATTENDANCE_MARGIN_MINUTES = 10;

/** 새 회차 기본값 — 일주일 뒤 정시 2시간, 출결은 앞뒤 10분 */
function defaultTimes() {
  const start = new Date();
  start.setDate(start.getDate() + DEFAULT_LEAD_DAYS);
  start.setMinutes(0, 0, 0);
  const end = new Date(start.getTime() + DEFAULT_LENGTH_HOURS * 3600000);
  const margin = ATTENDANCE_MARGIN_MINUTES * 60000;
  return {
    startsAt: toDateTimeLocalKst(start),
    endsAt: toDateTimeLocalKst(end),
    attendanceOpensAt: toDateTimeLocalKst(new Date(start.getTime() - margin)),
    attendanceClosesAt: toDateTimeLocalKst(new Date(end.getTime() + margin)),
  };
}

/** 'YYYY-MM-DDTHH:mm' 값을 분 단위로 민다. 비었거나 잘못된 값은 그대로 */
function shiftLocal(value, minutes) {
  const iso = fromDateTimeLocalKst(value);
  if (!iso || !minutes) return value;
  return toDateTimeLocalKst(new Date(new Date(iso).getTime() + minutes * 60000));
}

function minutesBetween(from, to) {
  const a = fromDateTimeLocalKst(from);
  const b = fromDateTimeLocalKst(to);
  return a && b ? Math.round((new Date(b) - new Date(a)) / 60000) : 0;
}

// datetime-local 값은 같은 형식(YYYY-MM-DDTHH:mm)이라 문자열 비교로 앞뒤를 가린다
function validate(form) {
  const errors = {};
  if (!Number.isInteger(form.sessionNo) || form.sessionNo < 1) errors.sessionNo = '1 이상의 숫자로 입력해 주세요.';
  if (!form.startsAt) errors.startsAt = '시작 시각을 골라 주세요.';
  if (!form.endsAt) errors.endsAt = '종료 시각을 골라 주세요.';
  else if (form.startsAt && form.endsAt <= form.startsAt) errors.endsAt = '시작보다 뒤여야 합니다.';
  if (!!form.attendanceOpensAt !== !!form.attendanceClosesAt) {
    errors[form.attendanceOpensAt ? 'attendanceClosesAt' : 'attendanceOpensAt'] = '시작과 종료를 함께 정해 주세요.';
  } else if (form.attendanceOpensAt && form.attendanceClosesAt <= form.attendanceOpensAt) {
    errors.attendanceClosesAt = '시작보다 뒤여야 합니다.';
  }
  return { ...errors, ...periodErrors(form) };
}

/** 'YYYY-MM-DD' → 'MM.DD' (안내 문구용) */
function shortDate(date) {
  return date ? date.slice(5).replace('-', '.') : '';
}

/**
 * 강좌 운영 기간 밖 회차는 막는다 — 개강 25일 전 날짜로 회차를 만들고 출석까지 받아,
 * 학습자 화면에선 '시작일까지 25일 남았습니다'인데 출석 1/1회인 강좌가 생겼다(2026-10-08).
 * datetime-local 앞 10자리(YYYY-MM-DD, KST)와 강좌의 startDate·endDate 를 비교한다
 */
function periodErrors(form) {
  const { courseStartDate: from, courseEndDate: to } = form;
  if (!from && !to) return {};
  const message = `강좌 운영 기간(${shortDate(from) || '…'} ~ ${shortDate(to) || '…'}) 안의 날짜로 정해 주세요.`;
  const day = (value) => (value ? value.slice(0, 10) : '');
  const outside = (value) => !!value && ((from && day(value) < from) || (to && day(value) > to));
  return {
    ...(outside(form.startsAt) ? { startsAt: message } : {}),
    ...(outside(form.endsAt) ? { endsAt: message } : {}),
  };
}

export default {
  name: 'SessionFormDialog',
  components: { AppDateTimeInput },
  props: {
    modelValue: { type: Boolean, default: false },
    courseId: { type: String, required: true },
    /** 수정할 회차(상세 조회 결과). null 이면 추가 */
    session: { type: Object, default: null },
    /** 추가할 때 기본 회차 번호 */
    nextSessionNo: { type: Number, default: 1 },
    /** 강좌 운영 기간(YYYY-MM-DD) — 이 밖의 회차는 저장하지 않는다 */
    courseStartDate: { type: String, default: '' },
    courseEndDate: { type: String, default: '' },
  },
  emits: ['update:modelValue', 'saved'],
  data() {
    return {
      SESSION_STATUS_OPTIONS,
      sessionNo: 1,
      startsAt: '',
      endsAt: '',
      location: '',
      attendanceOpensAt: '',
      attendanceClosesAt: '',
      status: 'SCHEDULED',
      errors: {},
      saving: false,
    };
  },
  computed: {
    isEdit() {
      return !!this.session?.id;
    },
  },
  methods: {
    fill() {
      const s = this.session;
      const times = s
        ? {
            startsAt: toDateTimeLocalKst(s.startsAt),
            endsAt: toDateTimeLocalKst(s.endsAt),
            attendanceOpensAt: toDateTimeLocalKst(s.attendanceOpensAt),
            attendanceClosesAt: toDateTimeLocalKst(s.attendanceClosesAt),
          }
        : defaultTimes();
      Object.assign(this, times, {
        sessionNo: s ? s.sessionNo : this.nextSessionNo,
        location: s ? s.location || '' : '',
        status: s?.status || 'SCHEDULED',
        errors: {},
      });
    },
    // 시작을 옮기면 종료·출결 시간도 같이 옮겨 수업 길이와 출결 여유를 지킨다(날짜만 바꿨는데 종료가 앞서던 것)
    moveStart(value) {
      const delta = this.startsAt && value ? minutesBetween(this.startsAt, value) : 0;
      this.startsAt = value;
      if (!delta) return;
      this.endsAt = shiftLocal(this.endsAt, delta);
      this.attendanceOpensAt = shiftLocal(this.attendanceOpensAt, delta);
      this.attendanceClosesAt = shiftLocal(this.attendanceClosesAt, delta);
    },
    close(value) {
      if (!this.saving) this.$emit('update:modelValue', value);
    },
    async submit() {
      this.errors = validate(this);
      if (Object.keys(this.errors).length) return;
      const body = {
        sessionNo: this.sessionNo,
        startsAt: fromDateTimeLocalKst(this.startsAt),
        endsAt: fromDateTimeLocalKst(this.endsAt),
        location: this.location.trim() || null,
        attendanceOpensAt: fromDateTimeLocalKst(this.attendanceOpensAt),
        attendanceClosesAt: fromDateTimeLocalKst(this.attendanceClosesAt),
        status: this.status || 'SCHEDULED',
      };
      this.saving = true;
      try {
        if (this.isEdit) await updateCourseSession(this.session.id, body);
        else await createCourseSession(this.courseId, body);
        this.$q.notify({
          type: 'positive',
          message: this.isEdit ? '회차를 수정했습니다.' : '회차를 추가했습니다.',
          position: 'top',
        });
        this.$emit('update:modelValue', false);
        this.$emit('saved');
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error?.message || '회차를 저장하지 못했습니다.', position: 'top' });
      } finally {
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

.session-form__no,
.session-form__status {
  max-width: 200px;
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

.session-form__help {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--db-text-sub);
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
