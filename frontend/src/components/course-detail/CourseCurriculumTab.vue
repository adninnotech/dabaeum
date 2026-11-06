<template>
  <section class="curriculum">
    <AppErrorBanner v-if="errorMessage" :message="errorMessage" />

    <div v-if="isOnline" class="curriculum__intro">
      <h2 class="curriculum__intro-title">온라인 교육</h2>
      <p class="curriculum__intro-text">온라인으로 학습하는 강의입니다.</p>
    </div>

    <!-- 피그마 외 — 기존 회차 관리 기능 유지 -->
    <div class="curriculum__actions">
      <!-- 일괄 생성은 시간표가 있는 회차만(온라인 회차는 시간이 없다) -->
      <q-btn v-if="!isOnline" no-caps outline color="primary" label="일괄 생성" @click="showBulk = true" />
      <q-btn
        v-if="rows.length > 1"
        no-caps
        outline
        color="primary"
        label="순서 바꾸기"
        @click="showReorder = true"
      />
      <q-btn no-caps unelevated color="primary" label="회차 추가" @click="openCreate" />
    </div>

    <div class="table-wrap">
      <table class="curri-table" :class="{ online: isOnline }">
        <thead>
          <tr>
            <th class="col-no">회차</th>
            <th class="col-topic">주제</th>
            <template v-if="isOnline">
              <th class="col-narrow">영상 시간</th>
              <th class="col-action">액션</th>
              <th class="col-narrow">학습 상태</th>
            </template>
            <th v-else class="col-schedule">일정</th>
            <th v-if="!isOnline" class="col-qr">상태</th>
            <th class="col-kebab" />
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td :colspan="isOnline ? 6 : 5" class="curri-table__state">
              <q-spinner color="primary" size="24px" />
            </td>
          </tr>
          <tr v-else-if="!rows.length">
            <td :colspan="isOnline ? 6 : 5">
              <AppEmptyState compact description="등록된 회차가 없습니다." />
            </td>
          </tr>
          <tr v-for="row in rows" v-else :key="row.id" :class="{ 'is-live': qrState(row) === 'ready' }">
            <td class="col-no">{{ row.sessionNo }}</td>
            <td class="col-topic">{{ row.topic || '-' }}</td>
            <template v-if="isOnline">
              <!-- TODO(backend): 영상 시간·학습 상태 없음 -->
              <td class="col-narrow">-</td>
              <td class="col-action">
                <!-- disabled 버튼은 이벤트를 안 받으므로 툴팁을 감싼 span에 건다 -->
                <span class="tooltip-host">
                  <q-btn no-caps outline dense disable label="학습하기" />
                  <q-tooltip>학습자 전용</q-tooltip>
                </span>
              </td>
              <td class="col-narrow">-</td>
            </template>
            <td v-else class="col-schedule">
              {{ formatSessionScheduleKst(row.startsAt, row.endsAt) }}
            </td>
            <!-- 이 칸이 회차 상태와 QR 실행을 함께 맡아 버튼만 홀로 튀었다(hallmark).
                 상태는 뱃지·글자로, QR 은 지금 출결을 받는 회차에만 글자 버튼으로 낸다.
                 안 되는 이유는 툴팁(마우스 전용) 대신 칸에 적는다 -->
            <td v-if="!isOnline" class="col-qr">
              <q-btn
                v-if="qrState(row) === 'ready'"
                no-caps
                flat
                dense
                color="primary"
                icon="o_qr_code_2"
                label="QR 띄우기"
                class="btn-qr"
                :aria-label="`${row.sessionNo}회차 출석 QR 띄우기`"
                @click="issueQr(row)"
              />
              <div v-else class="session-state">
                <StatusBadge :status="row.status" />
                <!-- 출결 시간이 됐는데 예정 / 지났는데 진행 — ⋮ 안에 묻혀 있던 다음 할 일을 칸에 꺼낸다 -->
                <q-btn
                  v-if="nextAction(row)"
                  no-caps
                  flat
                  dense
                  color="primary"
                  :label="nextAction(row).label"
                  class="session-state__action"
                  @click="changeStatus(row, nextAction(row))"
                />
                <span v-else-if="qrHint(row)" class="session-state__hint">{{ qrHint(row) }}</span>
              </div>
            </td>
            <td class="col-kebab">
              <q-btn aria-label="회차 메뉴" flat round dense icon="more_vert" size="sm">
                <q-menu>
                  <q-list style="min-width: 160px">
                    <q-item v-close-popup clickable @click="openEdit(row)">
                      <q-item-section>회차 수정</q-item-section>
                    </q-item>
                    <!-- 예전엔 '상태: 예정/진행/종료/취소'를 다 늘어놓아 지금 상태·서버가 막는 변경(409)까지 눌렸다.
                         서버 규칙상 가능한 변경만 동사로 보이고, 취소는 구분선 아래 빨간 글자로 -->
                    <q-item v-if="qrState(row) === 'ready'" v-close-popup clickable @click="issueQr(row)">
                      <q-item-section>출석 QR 띄우기</q-item-section>
                    </q-item>
                    <template v-for="action in statusActions(row)" :key="action.status">
                      <q-separator v-if="action.status === 'CANCELLED'" />
                      <q-item
                        v-close-popup
                        clickable
                        :class="{ 'text-negative': action.status === 'CANCELLED' }"
                        @click="changeStatus(row, action)"
                      >
                        <q-item-section>{{ action.label }}</q-item-section>
                      </q-item>
                    </template>
                    <!-- 관리자 정정: 잘못 마감(완료)한 회차를 다시 연다 -->
                    <template v-if="canReopen(row)">
                      <q-separator />
                      <q-item v-close-popup clickable @click="openReopen(row)">
                        <q-item-section>회차 재개 (정정)</q-item-section>
                      </q-item>
                    </template>
                  </q-list>
                </q-menu>
              </q-btn>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <CorrectionReasonDialog
      v-model="reopenOpen"
      title="회차 재개"
      :target="reopenTarget"
      :notes="REOPEN_NOTES"
      confirm-label="회차 재개"
      confirm-color="primary"
      :action="reopen"
      :error-message="reopenErrorMessage"
      @done="onReopened"
    />

    <SessionFormDialog
      v-model="showForm"
      :course-id="courseId"
      :session="editingSession"
      :next-session-no="nextSessionNo"
      :course-start-date="course?.startDate || ''"
      :course-end-date="course?.endDate || ''"
      @saved="load"
    />

    <SessionBulkDialog
      v-model="showBulk"
      :course-id="courseId"
      :next-session-no="nextSessionNo"
      :course-start-date="course?.startDate || ''"
      :course-end-date="course?.endDate || ''"
      @saved="load"
    />

    <SessionReorderDialog v-model="showReorder" :course-id="courseId" :sessions="rows" @saved="load" />

    <!-- 토큰 문자열은 학생이 찍을 수 없다 — 강사 출결 화면과 같은 동적 QR 다이얼로그를 띄운다 -->
    <AttendanceQrDialog
      v-model="showQr"
      :session-id="qrSessionId"
      :course-label="course?.title || ''"
      :session-label="qrSessionLabel"
    />
  </section>
</template>

<script>
import { confirmDialog } from '@/utils/confirm-dialog';
import AppEmptyState from '@/components/AppEmptyState.vue';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import AttendanceQrDialog from '@/components/attendance/AttendanceQrDialog.vue';
import CorrectionReasonDialog from '@/components/console/CorrectionReasonDialog.vue';
import SessionBulkDialog from '@/components/console/SessionBulkDialog.vue';
import SessionFormDialog from '@/components/console/SessionFormDialog.vue';
import SessionReorderDialog from '@/components/console/SessionReorderDialog.vue';
import {
  getCourseSession,
  listCourseSessions,
  reopenCourseSession,
  updateCourseSession,
} from '@/services/session-api';
import { formatDateTimeKst, formatSessionScheduleKst } from '@/utils/datetime';
import StatusBadge from '@/components/StatusBadge.vue';
import { sessionNextAction, sessionQrHint, sessionQrState, sessionStatusActions } from '@/utils/session-attendance';
import { STATUS_LABEL } from '@/utils/status';

const REOPEN_NOTES = [
  '회차 상태가 완료에서 다시 열립니다. 출결을 정정할 수 있습니다.',
  '완료된 회차만 재개할 수 있습니다.',
];

export default {
  name: 'CourseCurriculumTab',
  components: {
    AppEmptyState,
    AppErrorBanner,
    AttendanceQrDialog,
    CorrectionReasonDialog,
    SessionBulkDialog,
    SessionFormDialog,
    SessionReorderDialog,
    StatusBadge,
  },
  props: {
    course: { type: Object, default: null },
    console: { type: String, default: 'admin' },
  },
  data() {
    return {
      rows: [],
      loading: false,
      errorMessage: '',
      showForm: false,
      showBulk: false,
      showReorder: false,
      showQr: false,
      qrRow: null,
      editingSession: null,
      REOPEN_NOTES,
      reopenOpen: false,
      reopenRow: null,
      // 출결 시간 판정용 현재 시각 — 탭을 열어 둔 채 수업 시간이 되면 버튼이 켜지도록 30초마다 갱신
      now: Date.now(),
      nowTimer: null,
    };
  },
  computed: {
    nextSessionNo() {
      return this.rows.reduce((max, row) => Math.max(max, Number(row.sessionNo) || 0), 0) + 1;
    },
    reopenTarget() {
      const row = this.reopenRow;
      if (!row) return '';
      return [`${row.sessionNo}회차`, row.topic, this.course?.title].filter(Boolean).join(' · ');
    },
    isOnline() {
      return this.course?.educationType === 'ONLINE';
    },
    courseId() {
      return this.course?.id || this.$route.params.courseId;
    },
    qrSessionId() {
      return this.qrRow?.id || null;
    },
    qrSessionLabel() {
      const row = this.qrRow;
      return row ? `${row.sessionNo}회차 · ${formatDateTimeKst(row.startsAt)}` : '';
    },
  },
  created() {
    this.load();
    this.nowTimer = setInterval(() => {
      this.now = Date.now();
    }, 30000);
  },
  beforeUnmount() {
    clearInterval(this.nowTimer);
  },
  methods: {
    formatSessionScheduleKst,
    // QR 버튼 규칙은 출결 관리 화면과 함께 utils/session-attendance.js 에 둔다
    qrState(row) {
      return sessionQrState(row, this.now);
    },
    nextAction(row) {
      return sessionNextAction(row, this.now);
    },
    statusActions(row) {
      return sessionStatusActions(row);
    },
    qrHint(row) {
      return sessionQrHint(row, this.now);
    },
    async load() {
      if (!this.courseId) return;
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await listCourseSessions(this.courseId, { page: 0, size: 100 });
        this.rows = (Array.isArray(result.data) ? result.data : [])
          .slice()
          .sort((a, b) => (Number(a.sessionNo) || 0) - (Number(b.sessionNo) || 0));
      } catch (error) {
        this.errorMessage = error.message || '회차 목록 조회 실패';
        this.rows = [];
      } finally {
        this.loading = false;
      }
    },
    openCreate() {
      this.editingSession = null;
      this.showForm = true;
    },
    async openEdit(row) {
      try {
        this.editingSession = (await getCourseSession(row.id)) || row;
        this.showForm = true;
      } catch (error) {
        this.notifyError(error, '회차 상세를 불러오지 못했습니다.');
      }
    },
    /** 계약: COMPLETED 회차만, 플랫폼 관리자만 */
    canReopen(row) {
      return this.console === 'admin' && row.status === 'COMPLETED';
    },
    openReopen(row) {
      this.reopenRow = row;
      this.reopenOpen = true;
    },
    reopen(reason) {
      return reopenCourseSession(this.reopenRow.id, reason);
    },
    reopenErrorMessage(error) {
      if (error?.payload?.code === 'CORRECTION_NOT_ALLOWED') return '완료된 회차만 재개할 수 있습니다.';
      return null;
    },
    async onReopened() {
      this.$q.notify({ type: 'positive', message: '회차를 다시 열었습니다.', position: 'top' });
      await this.load();
    },
    // 종료·취소는 출결을 닫는다 — 되돌리려면 관리자 정정이 필요해 바꾸기 전에 묻는다
    async changeStatus(row, action) {
      const confirmText = {
        COMPLETED: { message: `${row.sessionNo}회차 수업을 종료합니다. 종료하면 출결을 더 받을 수 없습니다.`, color: 'primary' },
        CANCELLED: { message: `${row.sessionNo}회차를 취소합니다. 취소한 회차는 출결을 받을 수 없고 되돌릴 수 없습니다.`, color: 'negative' },
      }[action.status];
      if (confirmText) {
        const ok = await confirmDialog(this.$q, {
          title: action.label,
          message: confirmText.message,
          ok: action.label,
          color: confirmText.color,
          cancel: '닫기',
        });
        if (!ok) return;
      }
      try {
        await updateCourseSession(row.id, { status: action.status });
        this.$q.notify({
          type: 'info',
          message: `${row.sessionNo}회차를 '${STATUS_LABEL[action.status] || action.status}' 상태로 바꿨습니다.`,
          position: 'top',
        });
        await this.load();
      } catch (error) {
        this.notifyError(error, '상태 변경에 실패했습니다.');
      }
    },
    // 발급·갱신은 다이얼로그가 직접 한다 — 여기선 대상 회차만 정해 연다
    issueQr(row) {
      this.qrRow = row;
      this.showQr = true;
    },
    notifyError(error, fallback) {
      this.$q.notify({ type: 'negative', message: error?.message || fallback, position: 'top' });
    },
  },
};
</script>

<style scoped>
.curriculum {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.curriculum__intro-title {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 700;
  color: var(--db-text-body);
}

.curriculum__intro-text {
  margin: 0;
  font-size: 16px;
  color: var(--db-text-title);
}

.curriculum__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.table-wrap {
  border: 1px solid var(--db-border);
  border-radius: 4px;
  overflow-x: auto;
}

.curri-table {
  width: 100%;
  /* 고정 폭 칸(회차·일정·QR·메뉴)을 뺀 나머지가 주제 칸 — 좁은 화면에서 주제가 0에 가까워져
     한 글자씩 세로로 쌓였다. 주제 몫(160px)을 남기고 넘치면 표만 가로로 민다 */
  min-width: 620px;
  table-layout: fixed;
  border-collapse: collapse;
}

.curri-table th {
  height: 39px;
  padding: 0 10px;
  background: var(--db-bg-table-head);
  font-size: 16px;
  font-weight: 500;
  color: var(--db-text-sub);
  text-align: center;
}

.curri-table.online th {
  height: 52px;
}

.curri-table td {
  height: 52px;
  padding: 10px;
  border-top: 1px solid var(--db-border);
  font-size: 16px;
  font-weight: 500;
  color: var(--db-text-title);
  text-align: center;
}

.curri-table th.col-topic,
.curri-table td.col-topic {
  text-align: left;
  font-weight: 700;
}

.col-no {
  width: 80px;
}

.col-narrow {
  width: 80px;
}

.col-action {
  width: 120px;
}

.col-schedule {
  width: 220px;
}

.col-kebab {
  width: 40px;
}

.col-qr {
  width: 140px;
  text-align: center;
}

.btn-qr {
  white-space: nowrap;
  font-weight: 700;
}

.session-state {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.session-state__action {
  font-size: 13px;
  font-weight: 700;
}

.session-state__hint {
  font-size: 12px;
  font-weight: 400;
  line-height: 16px;
  color: var(--db-text-sub);
  white-space: nowrap;
}

/* 지금 출결을 받는 회차 — 줄 전체로 알린다(버튼 하나만 튀지 않게) */
.curri-table tr.is-live td {
  background: var(--db-accent-soft);
}

.curri-table tr.is-live td:first-child {
  box-shadow: inset 3px 0 0 var(--q-primary);
}

.curri-table__state {
  height: 80px;
  color: var(--db-text-sub);
  font-weight: 400;
}

.tooltip-host {
  display: inline-block;
}

@media (max-width: 599px) {
  .curri-table {
    min-width: 520px;
  }

  .curri-table th,
  .curri-table td {
    font-size: 14px;
  }

  .col-no {
    width: 56px;
  }

  .col-schedule {
    width: 160px;
  }
}
</style>
