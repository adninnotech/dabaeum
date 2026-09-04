<template>
  <q-page padding>
    <div class="app-page-header row items-center justify-between q-mb-lg">
      <div>
        <div class="app-page-header__title text-h5 text-weight-bold">출결 관리</div>
        <div class="app-page-header__subtitle text-grey-7">과정 → 회차 선택 · 출결 목록 · QR 발급 · 정정 · 관리자 등록</div>
      </div>
      <div class="row q-gutter-sm">
        <q-btn
          outline
          color="primary"
          icon="person_add"
          label="출결 등록"
          :disable="!selectedSessionId"
          @click="openRecord"
        />
        <q-btn outline color="primary" icon="refresh" label="새로고침" :loading="loading" @click="reloadAll" />
      </div>
    </div>

    <AppErrorBanner :message="errorMessage" />

    <div class="row q-col-gutter-md q-mb-md">
      <div class="col-12 col-md-4">
        <q-select
          :model-value="selectedCourseId"
          outlined
          dense
          emit-value
          map-options
          :options="courseOptions"
          label="과정 선택"
          :loading="coursesLoading"
          @update:model-value="onCourseChange"
        />
      </div>
      <div class="col-12 col-md-4">
        <q-select
          :model-value="selectedSessionId"
          outlined
          dense
          emit-value
          map-options
          :options="sessionOptions"
          label="회차 선택"
          :disable="!selectedCourseId"
          :loading="sessionsLoading"
          @update:model-value="onSessionChange"
        />
      </div>
      <div class="col-12 col-md-4 flex items-center">
        <q-btn
          color="primary"
          unelevated
          label="QR 토큰 발급"
          :disable="!selectedSessionId"
          :loading="qrLoading"
          @click="issueQr"
        />
      </div>
    </div>

    <div class="row q-col-gutter-md">
      <div class="col-12 col-md-4" v-if="qr">
        <q-card flat bordered>
          <q-card-section>
            <div class="text-subtitle1 text-weight-bold">QR 토큰</div>
            <div class="text-caption text-grey-7">만료: {{ $formatDateTimeKst(qr.expiresAt) }}</div>
            <code class="break-all q-mt-sm block">{{ qr.token }}</code>
            <q-btn class="q-mt-md" dense outline color="primary" label="복사" @click="copyToken" />
          </q-card-section>
        </q-card>
      </div>
      <div :class="qr ? 'col-12 col-md-8' : 'col-12'">
        <q-table
          flat
          bordered
          row-key="id"
          :rows="rows"
          :columns="columns"
          :loading="loading"
          :pagination="{ rowsPerPage: 10 }"
        >
          <template #body-cell-status="props">
            <q-td :props="props">
              <StatusBadge :status="props.row.status" />
            </q-td>
          </template>
          <template #body-cell-actions="props">
            <q-td :props="props">
              <q-btn flat dense color="primary" label="상세" @click="openDetail(props.row)" />
              <q-btn flat dense color="positive" label="출석" @click="openAdjust(props.row, 'PRESENT')" />
              <q-btn flat dense color="warning" label="지각" @click="openAdjust(props.row, 'LATE')" />
              <q-btn flat dense color="negative" label="결석" @click="openAdjust(props.row, 'ABSENT')" />
              <q-btn flat dense color="info" label="공결" @click="openAdjust(props.row, 'EXCUSED')" />
            </q-td>
          </template>
        </q-table>
      </div>
    </div>

    <q-dialog v-model="showDetail">
      <q-card style="min-width: 420px; max-width: 560px">
        <q-card-section class="text-h6">출결 상세</q-card-section>
        <q-card-section>
          <q-inner-loading :showing="detailLoading" />
          <pre v-if="detailPayload" class="break-all">{{ detailPayload }}</pre>
        </q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="닫기" v-close-popup />
        </q-card-actions>
      </q-card>
    </q-dialog>

    <q-dialog v-model="showAdjust">
      <q-card style="min-width: 400px">
        <q-card-section class="text-h6">출결 정정 · {{ adjustForm.status }}</q-card-section>
        <q-card-section class="q-gutter-md">
          <q-input v-model="adjustForm.reason" outlined dense type="textarea" autogrow label="사유 *" />
        </q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="취소" v-close-popup />
          <q-btn color="primary" unelevated :loading="adjustSaving" label="정정" @click="submitAdjust" />
        </q-card-actions>
      </q-card>
    </q-dialog>

    <q-dialog v-model="showRecord">
      <q-card style="min-width: 420px; max-width: 520px">
        <q-card-section class="text-h6">관리자 출결 등록</q-card-section>
        <q-card-section class="q-gutter-md">
          <q-input v-model="recordForm.enrollmentId" outlined dense label="enrollmentId *" />
          <q-select
            :model-value="recordForm.status"
            outlined
            dense
            emit-value
            map-options
            :options="attendanceStatusOptions"
            label="status *"
            @update:model-value="setRecordStatus"
          />
          <q-input v-model="recordForm.checkedAt" outlined dense label="checkedAt (ISO)" />
          <div class="text-caption text-grey-7">source: ADMIN_WEB · attendanceMethod: ADMIN</div>
        </q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="취소" v-close-popup />
          <q-btn color="primary" unelevated :loading="recordSaving" label="등록" @click="submitRecord" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </q-page>
</template>

<script>
import { formatDateTimeKst, qFormatDateTimeKst } from '@/utils/datetime';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { listCourses } from '@/services/course-api';
import { listCourseSessions } from '@/services/session-api';
import {
  adjustAttendance,
  getAttendance,
  issueAttendanceQrToken,
  listSessionAttendance,
  recordAttendance,
} from '@/services/attendance-api';

function nowIso() {
  return new Date().toISOString();
}

export default {
  components: { AppErrorBanner,  StatusBadge },
  name: 'InstructorAttendancePage',
  data() {
    return {
      courses: [],
      sessions: [],
      selectedCourseId: null,
      selectedSessionId: null,
      rows: [],
      qr: null,
      coursesLoading: false,
      sessionsLoading: false,
      loading: false,
      qrLoading: false,
      errorMessage: '',
      showDetail: false,
      detailLoading: false,
      detailPayload: '',
      showAdjust: false,
      adjustSaving: false,
      adjustForm: { attendanceId: null, status: 'PRESENT', reason: '' },
      showRecord: false,
      recordSaving: false,
      recordForm: {
        enrollmentId: '',
        status: 'PRESENT',
        checkedAt: nowIso(),
      },
      attendanceStatusOptions: [
        { label: 'PRESENT', value: 'PRESENT' },
        { label: 'LATE', value: 'LATE' },
        { label: 'ABSENT', value: 'ABSENT' },
        { label: 'EXCUSED', value: 'EXCUSED' },
      ],
      columns: [
        {
          name: 'userName',
          label: '수강생',
          field: (r) => r.userName || r.user?.name || r.enrollmentId || '-',
          align: 'left',
        },
        {
          name: 'sessionNo',
          label: '회차',
          field: (r) => r.sessionNo || r.session?.sessionNo || '-',
          align: 'center',
        },
        { name: 'attendanceMethod', label: '방법', field: 'attendanceMethod', align: 'center' },
        { name: 'status', label: '상태', field: 'status', align: 'center' },
        { name: 'checkedAt', label: '체크시각', field: 'checkedAt', align: 'left', format: qFormatDateTimeKst },
        { name: 'actions', label: '정정', field: 'actions', align: 'right' },
      ],
    };
  },
  computed: {
    courseOptions() {
      return this.courses.map((c) => ({
        label: `${c.courseCode || ''} ${c.title || c.id}`.trim(),
        value: c.id,
      }));
    },
    sessionOptions() {
      return this.sessions.map((s) => ({
        label: `${s.sessionNo}회차 · ${formatDateTimeKst(s.startsAt)} · ${s.status || ''}`.trim(),
        value: s.id,
      }));
    },
  },
  mounted() {
    this.loadCourses();
  },
  methods: {
    async openDetail(row) {
      if (!row?.id) return;
      this.showDetail = true;
      this.detailLoading = true;
      this.detailPayload = '';
      try {
        const data = await getAttendance(row.id);
        this.detailPayload = JSON.stringify(data, null, 2);
      } catch (error) {
        this.detailPayload = error.message || '출결 상세를 불러오지 못했습니다.';
      } finally {
        this.detailLoading = false;
      }
    },
    async reloadAll() {
      await this.loadCourses();
      if (this.selectedCourseId) await this.loadSessions(this.selectedCourseId);
      if (this.selectedSessionId) await this.loadAttendance(this.selectedSessionId);
    },
    async loadCourses() {
      this.coursesLoading = true;
      this.errorMessage = '';
      try {
        const result = await listCourses({ page: 0, size: 50, sort: 'createdAt,desc' });
        this.courses = Array.isArray(result.data) ? result.data : [];
        const queryCourseId = this.$route.query.courseId;
        const preferred =
          queryCourseId && this.courses.some((c) => c.id === queryCourseId)
            ? queryCourseId
            : this.selectedCourseId && this.courses.some((c) => c.id === this.selectedCourseId)
              ? this.selectedCourseId
              : this.courses[0]?.id || null;
        if (preferred) {
          await this.onCourseChange(preferred);
        }
      } catch (error) {
        this.errorMessage = error.message || '과정 목록 조회 실패';
        this.courses = [];
      } finally {
        this.coursesLoading = false;
      }
    },
    async onCourseChange(courseId) {
      this.selectedCourseId = courseId;
      this.selectedSessionId = null;
      this.sessions = [];
      this.rows = [];
      this.qr = null;
      if (this.$route.query.courseId !== courseId) {
        this.$router.replace({ query: { ...this.$route.query, courseId } }).catch(() => {});
      }
      await this.loadSessions(courseId);
    },
    async loadSessions(courseId) {
      if (!courseId) return;
      this.sessionsLoading = true;
      try {
        const result = await listCourseSessions(courseId, { page: 0, size: 100 });
        this.sessions = Array.isArray(result.data) ? result.data : [];
        if (this.sessions.length) {
          await this.onSessionChange(this.sessions[0].id);
        }
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '회차 목록 조회 실패', position: 'top' });
        this.sessions = [];
      } finally {
        this.sessionsLoading = false;
      }
    },
    async onSessionChange(sessionId) {
      this.selectedSessionId = sessionId;
      this.qr = null;
      await this.loadAttendance(sessionId);
    },
    async loadAttendance(sessionId) {
      if (!sessionId) return;
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await listSessionAttendance(sessionId, { page: 0, size: 100 });
        this.rows = Array.isArray(result.data) ? result.data : [];
      } catch (error) {
        this.errorMessage = error.message || '출결 목록 조회 실패';
        this.rows = [];
      } finally {
        this.loading = false;
      }
    },
    async issueQr() {
      if (!this.selectedSessionId) return;
      this.qrLoading = true;
      try {
        const data = await issueAttendanceQrToken(this.selectedSessionId);
        this.qr = {
          token: data.token || data.qrToken || data.value || JSON.stringify(data),
          expiresAt: data.expiresAt || data.expireAt || '-',
        };
        this.$q.notify({ type: 'positive', message: 'QR 토큰이 발급되었습니다.', position: 'top' });
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.qrLoading = false;
      }
    },
    async copyToken() {
      if (!this.qr?.token) return;
      try {
        await navigator.clipboard.writeText(this.qr.token);
        this.$q.notify({ type: 'positive', message: '토큰이 복사되었습니다.', position: 'top' });
      } catch {
        this.$q.notify({ type: 'warning', message: '클립보드 복사에 실패했습니다.', position: 'top' });
      }
    },
    openAdjust(row, status) {
      this.adjustForm = {
        attendanceId: row.id,
        status,
        reason: `강사 정정: ${status}`,
      };
      this.showAdjust = true;
    },
    async submitAdjust() {
      if (!this.adjustForm.attendanceId) return;
      if (!this.adjustForm.reason || !String(this.adjustForm.reason).trim()) {
        this.$q.notify({ type: 'warning', message: '정정 사유를 입력해 주세요.', position: 'top' });
        return;
      }
      this.adjustSaving = true;
      try {
        await adjustAttendance(this.adjustForm.attendanceId, {
          status: this.adjustForm.status,
          reason: this.adjustForm.reason.trim(),
        });
        this.showAdjust = false;
        this.$q.notify({ type: 'info', message: `출결 정정: ${this.adjustForm.status}`, position: 'top' });
        await this.loadAttendance(this.selectedSessionId);
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.adjustSaving = false;
      }
    },
    openRecord() {
      this.recordForm = {
        enrollmentId: '',
        status: 'PRESENT',
        checkedAt: nowIso(),
      };
      this.showRecord = true;
    },
    setRecordStatus(value) {
      this.recordForm.status = value;
    },
    async submitRecord() {
      if (!this.selectedSessionId) return;
      if (!this.recordForm.enrollmentId || !String(this.recordForm.enrollmentId).trim()) {
        this.$q.notify({ type: 'warning', message: 'enrollmentId를 입력해 주세요.', position: 'top' });
        return;
      }
      this.recordSaving = true;
      try {
        await recordAttendance(this.selectedSessionId, {
          enrollmentId: this.recordForm.enrollmentId.trim(),
          status: this.recordForm.status || 'PRESENT',
          source: 'ADMIN_WEB',
          attendanceMethod: 'ADMIN',
          checkedAt: this.recordForm.checkedAt || nowIso(),
        });
        this.showRecord = false;
        this.$q.notify({ type: 'positive', message: '출결이 등록되었습니다.', position: 'top' });
        await this.loadAttendance(this.selectedSessionId);
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.recordSaving = false;
      }
    },
  },
};
</script>

<style scoped>
.break-all {
  word-break: break-all;
}
.block {
  display: block;
}
</style>
