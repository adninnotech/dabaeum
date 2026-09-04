<template>
  <q-page padding>
    <div class="page-inner">
      <div class="app-page-header q-mb-lg">
        <div class="app-page-header__title text-h5 text-weight-bold">출석 QR 스캔</div>
        <div class="app-page-header__subtitle text-grey-7">POST /sessions/{sessionId}/attendance</div>
      </div>

      <q-banner v-if="errorMessage" class="bg-red-1 text-negative q-mb-md rounded-borders">
        {{ errorMessage }}
      </q-banner>

      <q-banner v-if="successMessage" class="bg-green-1 text-positive q-mb-md rounded-borders">
        {{ successMessage }}
      </q-banner>

      <q-card flat bordered>
        <q-card-section class="q-gutter-md">
          <q-input v-model="sessionId" outlined dense label="sessionId" />
          <q-input v-model="enrollmentId" outlined dense label="enrollmentId" />
          <q-input
            v-model="qrToken"
            outlined
            dense
            type="textarea"
            autogrow
            label="qrToken (붙여넣기, ≥32자)"
          />
          <q-btn
            color="primary"
            unelevated
            label="출석 제출"
            :loading="submitting"
            :disable="!sessionId || !enrollmentId || !qrToken"
            @click="submitAttendance"
          />
        </q-card-section>
      </q-card>
    </div>
  </q-page>
</template>

<script>
import { recordAttendance } from '@/services/attendance-api';

export default {
  name: 'AttendanceScanPage',
  data() {
    return {
      sessionId: '',
      enrollmentId: '',
      qrToken: '',
      submitting: false,
      errorMessage: '',
      successMessage: '',
    };
  },
  mounted() {
    const query = this.$route.query || {};
    if (query.sessionId) this.sessionId = String(query.sessionId);
    if (query.enrollmentId) this.enrollmentId = String(query.enrollmentId);
    if (query.qrToken) this.qrToken = String(query.qrToken);
  },
  methods: {
    async submitAttendance() {
      this.errorMessage = '';
      this.successMessage = '';

      if (!this.sessionId.trim()) {
        this.errorMessage = 'sessionId를 입력해 주세요.';
        return;
      }
      if (!this.enrollmentId.trim()) {
        this.errorMessage = 'enrollmentId를 입력해 주세요.';
        return;
      }
      if (!this.qrToken || this.qrToken.trim().length < 32) {
        this.errorMessage = 'qrToken은 32자 이상이어야 합니다.';
        return;
      }

      this.submitting = true;
      try {
        const result = await recordAttendance(this.sessionId.trim(), {
          enrollmentId: this.enrollmentId.trim(),
          attendanceMethod: 'QR',
          status: 'PRESENT',
          checkedAt: new Date().toISOString(),
          source: 'APP',
          qrToken: this.qrToken.trim(),
        });
        this.successMessage = `출석 기록 완료 · ${result?.status || 'PRESENT'}${
          result?.id ? ` (${result.id})` : ''
        }`;
        this.$q.notify({
          type: 'positive',
          message: '출석이 등록되었습니다.',
          position: 'top',
        });
      } catch (error) {
        this.errorMessage = error.message || '출석 등록에 실패했습니다.';
        this.$q.notify({
          type: 'negative',
          message: this.errorMessage,
          position: 'top',
        });
      } finally {
        this.submitting = false;
      }
    },
  },
};
</script>

<style scoped>
.page-inner {
  max-width: 560px;
  margin: 0 auto;
}
.rounded-borders {
  border-radius: 12px;
}
</style>
