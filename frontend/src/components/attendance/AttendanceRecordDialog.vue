<template>
  <!-- 한 사람 출결 등록 — 명단 행의 [등록]에서만 열려 수강생은 늘 정해져 있다.
       쓰이지 않던 수강생 선택칸을 빼고 이름만 보인다 -->
  <q-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    @show="reset"
  >
    <q-card class="attendance-dialog">
      <q-card-section class="attendance-dialog__title"
        >출결 등록 · {{ target?.name }}</q-card-section
      >
      <q-card-section class="q-gutter-md">
        <q-select
          v-model="status"
          outlined
          dense
          emit-value
          map-options
          :options="statusOptions"
          label="출결 상태"
        />
        <AppDateTimeInput v-model="checkedAt" outlined dense stack-label label="출석 시각" />
      </q-card-section>
      <q-card-actions align="right">
        <q-btn v-close-popup flat no-caps label="취소" />
        <q-btn color="primary" unelevated no-caps label="등록" :loading="saving" @click="submit" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import AppDateTimeInput from '@/components/AppDateTimeInput.vue';
import { recordAttendance } from '@/services/attendance-api';
import { fromDateTimeLocalKst, toDateTimeLocalKst } from '@/utils/datetime';
import { STATUS_LABEL } from '@/utils/status';

const STATUS_OPTIONS = ['PRESENT', 'LATE', 'ABSENT', 'EXCUSED'].map((value) => ({
  label: STATUS_LABEL[value],
  value,
}));

export default {
  name: 'AttendanceRecordDialog',
  components: { AppDateTimeInput },
  props: {
    modelValue: { type: Boolean, default: false },
    sessionId: { type: String, default: null },
    /** { enrollmentId, name } */
    target: { type: Object, default: null },
  },
  emits: ['update:modelValue', 'saved'],
  data() {
    return { status: 'PRESENT', checkedAt: '', saving: false, statusOptions: STATUS_OPTIONS };
  },
  methods: {
    reset() {
      this.status = 'PRESENT';
      this.checkedAt = toDateTimeLocalKst(new Date());
    },
    async submit() {
      if (!this.sessionId || !this.target?.enrollmentId) return;
      this.saving = true;
      try {
        await recordAttendance(this.sessionId, {
          enrollmentId: this.target.enrollmentId,
          status: this.status || 'PRESENT',
          source: 'ADMIN_WEB',
          attendanceMethod: 'ADMIN',
          checkedAt: fromDateTimeLocalKst(this.checkedAt) || new Date().toISOString(),
        });
        this.$emit('update:modelValue', false);
        this.$q.notify({
          type: 'positive',
          message: `${this.target.name} 님을 '${STATUS_LABEL[this.status]}'(으)로 등록했습니다.`,
          position: 'top',
        });
        this.$emit('saved');
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error.message || '출결을 등록하지 못했습니다.',
          position: 'top',
        });
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped src="./attendance-dialog.css"></style>
