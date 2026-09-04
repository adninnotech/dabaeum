<template>
  <q-page padding>
    <q-btn flat dense color="primary" icon="arrow_back" label="강좌 목록" class="q-mb-md" @click="$router.push(coursesListPath)" />
    <div class="app-page-header row items-center justify-between q-mb-lg">
      <div>
        <div class="app-page-header__title text-h5 text-weight-bold">회차 관리</div>
        <div class="app-page-header__subtitle text-grey-7">Course {{ courseId }} · GET/POST sessions · QR 발급</div>
      </div>
      <div class="row q-gutter-sm">
        <q-btn outline color="primary" icon="refresh" label="새로고침" :loading="loading" @click="loadSessions" />
        <q-btn color="primary" unelevated icon="add" label="회차 추가" @click="openCreate" />
      </div>
    </div>

    <AppErrorBanner :message="errorMessage" />

    <q-table
      flat
      bordered
      row-key="id"
      :rows="rows"
      :columns="columns"
      :loading="loading"
      :pagination="{ rowsPerPage: 0 }"
      hide-pagination
    >
      <template #body-cell-status="props">
        <q-td :props="props">
          <q-btn-dropdown flat dense :color="statusColor[props.row.status] || 'grey'" :label="props.row.status">
            <q-list>
              <q-item
                v-for="opt in statusOptions"
                :key="opt.value"
                v-close-popup
                clickable
                @click="updateStatus(props.row, opt.value)"
              >
                <q-item-section>{{ opt.label }}</q-item-section>
              </q-item>
            </q-list>
          </q-btn-dropdown>
        </q-td>
      </template>
      <template #body-cell-actions="props">
        <q-td :props="props">
          <q-btn flat dense color="secondary" label="수정" :loading="detailId === props.row.id" @click="openEdit(props.row)" />
          <q-btn flat dense color="primary" label="QR 발급" :loading="qrLoadingId === props.row.id" @click="issueQr(props.row)" />
        </q-td>
      </template>
    </q-table>

    <q-dialog v-model="showCreate">
      <q-card style="min-width: 440px; max-width: 520px">
        <q-card-section class="text-h6">{{ editingId ? '회차 수정' : '회차 추가' }}</q-card-section>
        <q-card-section class="q-gutter-md">
          <q-input v-model.number="form.sessionNo" outlined dense type="number" label="sessionNo *" />
          <q-input v-model="form.startsAt" outlined dense label="startsAt (ISO) *" hint="예: 2026-09-01T10:00:00+09:00" />
          <q-input v-model="form.endsAt" outlined dense label="endsAt (ISO) *" />
          <q-input v-model="form.location" outlined dense label="location" />
          <q-input v-model="form.attendanceOpensAt" outlined dense label="attendanceOpensAt" />
          <q-input v-model="form.attendanceClosesAt" outlined dense label="attendanceClosesAt" />
          <q-select
            :model-value="form.status"
            outlined
            dense
            emit-value
            map-options
            :options="statusOptions"
            label="status"
            @update:model-value="setFormStatus"
          />
        </q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="취소" v-close-popup />
          <q-btn color="primary" unelevated :loading="saving" :label="editingId ? '수정' : '저장'" @click="saveSession" />
        </q-card-actions>
      </q-card>
    </q-dialog>

    <q-dialog v-model="showQr">
      <q-card style="min-width: 420px; max-width: 560px">
        <q-card-section class="text-h6">출결 QR 토큰</q-card-section>
        <q-card-section>
          <div class="text-caption text-grey-7 q-mb-xs">만료: {{ $formatDateTimeKst(lastQr?.expiresAt) }}</div>
          <code class="break-all">{{ lastQr?.token || '-' }}</code>
        </q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="닫기" v-close-popup />
          <q-btn color="primary" unelevated label="복사" @click="copyToken" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </q-page>
</template>

<script>
import { qFormatDateTimeKst } from '@/utils/datetime';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import { SESSION_STATUS_OPTIONS, statusColor } from '@/utils/status';
import { issueAttendanceQrToken } from '@/services/attendance-api';
import {
  createCourseSession,
  getCourseSession,
  listCourseSessions,
  updateCourseSession,
} from '@/services/session-api';

function defaultSessionForm(nextNo) {
  const start = new Date();
  start.setDate(start.getDate() + 7);
  start.setMinutes(0, 0, 0);
  const end = new Date(start);
  end.setHours(end.getHours() + 2);
  const open = new Date(start);
  open.setMinutes(open.getMinutes() - 10);
  const close = new Date(end);
  close.setMinutes(close.getMinutes() + 10);
  const toIso = (d) => {
    const pad = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00+09:00`;
  };
  return {
    sessionNo: nextNo,
    startsAt: toIso(start),
    endsAt: toIso(end),
    location: '온라인',
    attendanceOpensAt: toIso(open),
    attendanceClosesAt: toIso(close),
    status: 'SCHEDULED',
  };
}

export default {
  components: { AppErrorBanner },
  name: 'AdminSessionsPage',
  data() {
    return {
      statusColor,
      rows: [],
      loading: false,
      saving: false,
      qrLoadingId: null,
      errorMessage: '',
      showCreate: false,
      showQr: false,
      lastQr: null,
      editingId: null,
      detailId: null,
      form: defaultSessionForm(1),
      statusOptions: SESSION_STATUS_OPTIONS,
      columns: [
        { name: 'sessionNo', label: '회차', field: 'sessionNo', align: 'center' },
        { name: 'startsAt', label: '시작', field: 'startsAt', align: 'left', format: qFormatDateTimeKst },
        { name: 'endsAt', label: '종료', field: 'endsAt', align: 'left', format: qFormatDateTimeKst },
        { name: 'location', label: '장소', field: 'location', align: 'left' },
        { name: 'status', label: '상태', field: 'status', align: 'center' },
        { name: 'actions', label: '액션', field: 'actions', align: 'right' },
      ],
    };
  },
  computed: {
    courseId() {
      return this.$route.params.courseId;
    },
    coursesListPath() {
      return this.$route.path.startsWith('/institution') ? '/institution/courses' : '/admin/courses';
    },
  },
  watch: {
    courseId: {
      immediate: true,
      handler() {
        this.loadSessions();
      },
    },
  },
  methods: {
    setFormStatus(value) {
      this.form.status = value;
    },
    openCreate() {
      const maxNo = this.rows.reduce((m, r) => Math.max(m, Number(r.sessionNo) || 0), 0);
      this.editingId = null;
      this.form = defaultSessionForm(maxNo + 1);
      this.showCreate = true;
    },
    fillForm(session) {
      this.form = {
        sessionNo: session.sessionNo,
        startsAt: session.startsAt || '',
        endsAt: session.endsAt || '',
        location: session.location || '',
        attendanceOpensAt: session.attendanceOpensAt || '',
        attendanceClosesAt: session.attendanceClosesAt || '',
        status: session.status || 'SCHEDULED',
      };
    },
    async openEdit(row) {
      this.detailId = row.id;
      try {
        const session = await getCourseSession(row.id);
        this.editingId = session?.id || row.id;
        this.fillForm(session || row);
        this.showCreate = true;
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '회차 상세를 불러오지 못했습니다.', position: 'top' });
      } finally {
        this.detailId = null;
      }
    },
    async loadSessions() {
      if (!this.courseId) return;
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await listCourseSessions(this.courseId, { page: 0, size: 100 });
        this.rows = Array.isArray(result.data) ? result.data : [];
      } catch (error) {
        this.errorMessage = error.message || '회차 목록 조회 실패';
        this.rows = [];
      } finally {
        this.loading = false;
      }
    },
    sessionBody() {
      return {
        sessionNo: Number(this.form.sessionNo),
        startsAt: this.form.startsAt,
        endsAt: this.form.endsAt,
        location: this.form.location || null,
        attendanceOpensAt: this.form.attendanceOpensAt || null,
        attendanceClosesAt: this.form.attendanceClosesAt || null,
        status: this.form.status || 'SCHEDULED',
      };
    },
    async saveSession() {
      if (!this.form.sessionNo || !this.form.startsAt || !this.form.endsAt) {
        this.$q.notify({ type: 'warning', message: '회차번호/시작/종료는 필수입니다.', position: 'top' });
        return;
      }
      this.saving = true;
      try {
        if (this.editingId) {
          await updateCourseSession(this.editingId, this.sessionBody());
          this.$q.notify({ type: 'positive', message: '회차를 수정했습니다.', position: 'top' });
        } else {
          await createCourseSession(this.courseId, this.sessionBody());
          this.$q.notify({ type: 'positive', message: '회차가 추가되었습니다.', position: 'top' });
        }
        this.showCreate = false;
        this.editingId = null;
        await this.loadSessions();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.saving = false;
      }
    },
    async updateStatus(row, status) {
      try {
        await updateCourseSession(row.id, { status });
        this.$q.notify({ type: 'info', message: `상태 변경: ${status}`, position: 'top' });
        await this.loadSessions();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      }
    },
    async issueQr(row) {
      this.qrLoadingId = row.id;
      try {
        const data = await issueAttendanceQrToken(row.id);
        this.lastQr = {
          token: data.token || data.qrToken || data.value || JSON.stringify(data),
          expiresAt: data.expiresAt || data.expireAt || '-',
          sessionId: row.id,
        };
        this.showQr = true;
        this.$q.notify({ type: 'positive', message: 'QR 토큰이 발급되었습니다.', position: 'top' });
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.qrLoadingId = null;
      }
    },
    async copyToken() {
      const token = this.lastQr?.token;
      if (!token) return;
      try {
        await navigator.clipboard.writeText(token);
        this.$q.notify({ type: 'positive', message: '토큰이 복사되었습니다.', position: 'top' });
      } catch {
        this.$q.notify({ type: 'warning', message: '클립보드 복사에 실패했습니다.', position: 'top' });
      }
    },
  },
};
</script>

<style scoped>
.break-all {
  word-break: break-all;
}
</style>
