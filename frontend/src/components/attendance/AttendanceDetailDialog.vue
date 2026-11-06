<template>
  <!-- 출결 한 건 상세 — 응답 JSON 대신 한글 항목, 내부 ID 는 보이지 않는다 -->
  <q-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    @show="load"
  >
    <q-card class="attendance-dialog attendance-dialog--wide">
      <q-card-section class="attendance-dialog__title">출결 상세</q-card-section>
      <q-card-section>
        <q-inner-loading :showing="loading" />
        <p v-if="error" class="attendance-dialog__error">{{ error }}</p>
        <q-list v-else-if="data" dense>
          <q-item v-for="row in rows" :key="row.label">
            <q-item-section>
              <q-item-label caption>{{ row.label }}</q-item-label>
              <q-item-label>{{ row.value }}</q-item-label>
            </q-item-section>
          </q-item>
        </q-list>
        <AttendanceAdjustmentHistory v-if="data && !error" :adjustments="data.adjustments" />
      </q-card-section>
      <q-card-actions align="right">
        <q-btn v-close-popup flat no-caps label="닫기" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import AttendanceAdjustmentHistory from '@/components/attendance/AttendanceAdjustmentHistory.vue';
import { getAttendance } from '@/services/attendance-api';
import { formatDateTimeKst } from '@/utils/datetime';
import { ATTENDANCE_METHOD_LABEL, ATTENDANCE_SOURCE_LABEL, STATUS_LABEL } from '@/utils/status';

export default {
  name: 'AttendanceDetailDialog',
  components: { AttendanceAdjustmentHistory },
  props: {
    modelValue: { type: Boolean, default: false },
    attendanceId: { type: String, default: null },
  },
  emits: ['update:modelValue'],
  data() {
    return { loading: false, data: null, error: '' };
  },
  computed: {
    rows() {
      const d = this.data;
      if (!d) return [];
      return [
        { label: '출결 상태', value: STATUS_LABEL[d.status] || d.status || '-' },
        {
          label: '출석 방법',
          value: ATTENDANCE_METHOD_LABEL[d.attendanceMethod] || d.attendanceMethod || '-',
        },
        { label: '기록 경로', value: ATTENDANCE_SOURCE_LABEL[d.source] || d.source || '-' },
        { label: '출석 시각', value: formatDateTimeKst(d.checkedAt) },
        { label: '처음 기록', value: formatDateTimeKst(d.createdAt) },
        { label: '마지막 수정', value: formatDateTimeKst(d.updatedAt) },
      ];
    },
  },
  methods: {
    async load() {
      if (!this.attendanceId) return;
      this.loading = true;
      this.data = null;
      this.error = '';
      try {
        this.data = await getAttendance(this.attendanceId);
      } catch (error) {
        this.error = error.message || '출결 상세를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped src="./attendance-dialog.css"></style>
