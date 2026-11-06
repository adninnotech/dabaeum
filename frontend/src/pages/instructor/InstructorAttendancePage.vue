<template>
  <q-page padding>
    <!-- 다른 콘솔 화면과 같은 머리. 한 명씩 고르던 [출결 등록]은 행의 [등록]·일괄 기록과 겹쳐 뺐다 -->
    <AppPageHeader
      title="출결 관리"
      show-refresh
      :loading="loading"
      @refresh="loadCourses"
    />
    <AppInstitutionMissingBanner :show="consoleScope === 'institution' && showInstitutionMissingBanner" />

    <!-- 실패를 단계별로 나눈다 — 하나의 오류 칸을 서로 지워 강의 목록 오류가 사라지던 것.
         조회 실패를 '수강생이 없습니다'·'회차가 없습니다'로 잘못 알리지 않게 실패면 이 배너를 보인다 -->
    <AppErrorBanner :message="coursesError">
      <template #action>
        <q-btn flat dense no-caps color="negative" label="다시 시도" @click="loadCourses" />
      </template>
    </AppErrorBanner>
    <AppErrorBanner :message="courseDataError">
      <template #action>
        <q-btn flat dense no-caps color="negative" label="다시 시도" @click="retryCourseData" />
      </template>
    </AppErrorBanner>
    <AppErrorBanner :message="attendanceError">
      <template #action>
        <q-btn
          flat
          dense
          no-caps
          color="negative"
          label="다시 시도"
          @click="loadAttendance(selectedSessionId)"
        />
      </template>
    </AppErrorBanner>

    <!-- 머리(새로고침)와 같은 폭 — 1080px 로 묶었더니 머리와 본문 오른쪽 끝이 어긋났다 -->
    <div class="attendance-body">
    <div class="row q-col-gutter-md q-mb-md">
      <div class="col-12 col-md-7">
        <q-select
          :model-value="selectedCourseId"
          outlined
          emit-value
          map-options
          :options="courseOptions"
          label="강의"
          :loading="coursesLoading"
          @update:model-value="onCourseChange"
        />
        <div v-if="!coursesLoading && !coursesError && !courseOptions.length" class="field-hint">
          {{
            consoleAsAdmin
              ? '등록된 강의가 없습니다.'
              : '배정된 강의가 없습니다. 기관 관리자가 강의에 강사로 배정하면 여기에 나타납니다.'
          }}
        </div>
      </div>
      <div class="col-12 col-md-5">
        <q-select
          :model-value="selectedSessionId"
          outlined
          emit-value
          map-options
          :options="sessionOptions"
          label="회차"
          :disable="!selectedCourseId"
          :loading="sessionsLoading"
          @update:model-value="onSessionChange"
        />
      </div>
    </div>

    <!-- 회차 머리 — 일시·장소·지금 상태를 크게, 이전·다음 회차, 지금 할 일(QR·수업 시작/종료) -->
    <AttendanceSessionHeader
      v-if="selectedSession"
      class="q-mb-md"
      :session="selectedSession"
      :sessions="sessions"
      :now="now"
      :status-changing="statusChanging"
      :quiet="Boolean(selectedIds.length)"
      @select="onSessionChange"
      @qr="showQr = true"
      @status-action="changeSessionStatus"
    />

    <!-- 미기록 일괄 등록 — 한 명씩 [출결 등록] 창에서 고르던 일을 명단에서 바로 한다 -->
    <div v-if="selectedSessionId" class="bulk-bar">
      <AttendanceTally
        :model-value="rosterFilter"
        :rows="rosterRows"
        :picked-count="selectedIds.length"
        @update:model-value="onRosterFilterChange"
      />
      <!-- 고른 사람이 있을 때만 일괄 버튼을 보인다. 채운 버튼은 [선택 출석] 하나, 결석은 테두리 -->
      <template v-if="selectedIds.length">
        <q-btn
          unelevated
          color="primary"
          icon="check"
          label="선택 출석"
          :disable="bulkRunning"
          @click="bulkRecord('PRESENT', selectedIds)"
        />
        <q-btn
          outline
          color="negative"
          icon="close"
          label="선택 결석"
          :disable="bulkRunning"
          @click="bulkRecord('ABSENT', selectedIds)"
        />
        <!-- 지각·공결은 드물어 메뉴로 — 고른 사람이 있을 때만 보이므로 항목을 끌 일이 없다 -->
        <q-btn-dropdown outline color="primary" no-caps label="지각·공결" :disable="bulkRunning">
          <q-list dense>
            <q-item v-close-popup clickable @click="bulkRecord('LATE', selectedIds)">
              <q-item-section>선택 지각</q-item-section>
            </q-item>
            <q-item v-close-popup clickable @click="bulkRecord('EXCUSED', selectedIds)">
              <q-item-section>선택 공결</q-item-section>
            </q-item>
          </q-list>
        </q-btn-dropdown>
      </template>
      <!-- 가장 잦은 일(미기록 전원 출석)이 [기타] 메뉴 안에 숨어 있었다 — 아무도 안 골랐을 때 바로 보인다 -->
      <template v-else-if="unrecordedIds.length">

        <q-btn
          outline
          color="primary"
          no-caps
          icon="done_all"
          :label="`미기록 ${unrecordedIds.length}명 전원 출석`"
          :disable="bulkRunning"
          @click="bulkRecord('PRESENT', unrecordedIds)"
        />
      </template>
    </div>
    <!-- 이수 처리 화면과 같은 얇은 진행 막대 — 글자는 막대 위에 -->
    <div v-if="bulkRunning" class="q-mb-sm">
      <div class="field-hint q-mb-xs">
        등록 중 {{ bulkProgress.done }} / {{ bulkProgress.total }}
      </div>
      <q-linear-progress
        rounded
        color="primary"
        :value="bulkProgress.total ? bulkProgress.done / bulkProgress.total : 0"
      />
    </div>

    <!-- 회차가 없거나 아직 안 골랐는데도 명단·[등록]이 보여, 눌러도 아무 일 없이 끝났다(저장할 회차가 없다).
         회차를 고르기 전엔 명단 대신 무엇을 해야 하는지 알린다 -->
    <AppEmptyState
      v-if="!selectedSessionId && !loading && !sessionsLoading && !courseDataError"
      compact
      :title="emptySessionText.title"
      :description="emptySessionText.description"
      :action-label="emptySessionText.actionLabel"
      :action-to="emptySessionText.actionTo"
    />
    <AttendanceRosterTable
      v-else
      :rows="filteredRows"
      :loading="loading || rosterLoading"
      :busy-key="quickBusy.key"
      :busy-status="quickBusy.status"
      :disable="bulkRunning"
      :selected-ids="selectedIds"
      :highlight-key="highlightKey"
      :flash-id="flashAttendanceId"
      :empty-label="
        courseDataError
          ? '수강생 명단을 불러오지 못했습니다.'
          : rosterFilter !== 'ALL'
            ? '이 상태의 수강생이 없습니다.'
            : '이 강의에 승인된 수강생이 없습니다.'
      "
      @update:selected-ids="onSelectedChange"
      @detail="openDetail"
      @adjust="openAdjust"
      @record="openRecord"
      @quick-record="recordOne"
    />
    </div>

    <AttendanceQrDialog
      v-model="showQr"
      :session-id="selectedSessionId"
      :course-label="selectedCourseLabel"
      :session-label="selectedSessionLabel"
      :attended-count="attendedCount"
      :total-count="rosterRows.length"
      @issued="onQrIssued"
      @update:model-value="onQrToggle"
    />
    <AttendanceDetailDialog v-model="showDetail" :attendance-id="detailId" />
    <AttendanceAdjustDialog
      v-model="showAdjust"
      :target="adjustTarget"
      @saved="onAdjusted"
    />
    <AttendanceRecordDialog
      v-model="showRecord"
      :session-id="selectedSessionId"
      :target="recordTarget"
      @saved="loadAttendance(selectedSessionId)"
    />
  </q-page>
</template>

<script>
import { formatDateKst } from '@/utils/datetime';
import { STATUS_LABEL, formatCourseOption } from '@/utils/status';
import { formatUserDisplayName } from '@/utils/user-display';
import { confirmDialog } from '@/utils/confirm-dialog';
import { listCourseEnrollments } from '@/services/enrollment-api';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import AppPageHeader from '@/components/AppPageHeader.vue';
import AppInstitutionMissingBanner from '@/components/AppInstitutionMissingBanner.vue';
import { getCourse } from '@/services/course-api';
import AttendanceAdjustDialog from '@/components/attendance/AttendanceAdjustDialog.vue';
import AttendanceDetailDialog from '@/components/attendance/AttendanceDetailDialog.vue';
import AttendanceQrDialog from '@/components/attendance/AttendanceQrDialog.vue';
import AttendanceRecordDialog from '@/components/attendance/AttendanceRecordDialog.vue';
import AttendanceRosterTable from '@/components/attendance/AttendanceRosterTable.vue';
import instructorConsoleCourses from '@/mixins/instructor-console-courses';
import { listCourseSessions, updateCourseSession } from '@/services/session-api';
import AppEmptyState from '@/components/AppEmptyState.vue';
import AttendanceSessionHeader from '@/components/attendance/AttendanceSessionHeader.vue';
import AttendanceTally, { tallyKeyOf } from '@/components/attendance/AttendanceTally.vue';
import { formatSessionWhen } from '@/utils/session-attendance';
import { listSessionAttendance, recordAttendance } from '@/services/attendance-api';

/** 서버 page size 상한(PageMeta.size ≤ 100) */
const PAGE_SIZE = 100;
/** 목록을 끝까지 넘겨 받을 때의 안전 상한 — 서버 이상으로 무한 반복하지 않게 */
const MAX_PAGES = 20;
/** 일괄 등록 동시 요청 수 — 서버에 한꺼번에 몰리지 않게 조금씩 */
const BULK_CONCURRENCY = 3;
/** 회차 상태 — 공용 STATUS_LABEL 의 OPEN('진행')보다 선택 목록에서 읽기 쉬운 말로 */
const SESSION_STATUS_TEXT = { SCHEDULED: '예정', OPEN: '진행 중', COMPLETED: '종료', CANCELLED: '취소' };
const MISSING_NAME = '이름 없음';
/** 출결 시간 판정 주기 — 30초였을 땐 출결 시간이 돼도 QR 버튼이 최대 30초 늦게 켜졌다 */
const NOW_TICK_MS = 5000;
/** QR 을 띄운 동안 출석 현황을 다시 받는 주기 — 창 안의 '출석 N / M명'이 따라 늘게 */
const QR_ROSTER_POLL_MS = 10000;
/** QR 창의 출석 수에 넣는 상태 — 출석·지각은 왔고, 결석·공결은 오지 않았다 */
const ATTENDED_STATUSES = ['PRESENT', 'LATE'];
// 정정한 줄을 깜빡이는 시간 — 명단 CSS 애니메이션(1.6s)과 맞춘다
const ROW_FLASH_MS = 1600;

function nowIso() {
  return new Date().toISOString();
}

/** 쪽이 여러 개인 목록을 끝까지 받는다 — 수강생이 100명을 넘어도 명단이 잘리지 않게 */
async function fetchAllPages(fetchPage) {
  const first = await fetchPage(0);
  const totalPages = Math.min(first.page?.totalPages || 1, MAX_PAGES);
  const rest = [];
  for (let page = 1; page < totalPages; page += 1) {
    rest.push(await fetchPage(page));
  }
  return [first, ...rest].flatMap((result) => (Array.isArray(result.data) ? result.data : []));
}

/**
 * 처음 보일 회차 — 오늘(KST) 회차 → 다음 예정 회차 → 가장 최근 지난 회차.
 * 첫 회차로 고정돼 있어 수업 당일마다 회차를 다시 골라야 했다. 취소 회차는 기본값에서 뺀다
 */
function pickDefaultSessionId(sessions, now = new Date()) {
  const dated = sessions.filter((s) => s.status !== 'CANCELLED' && s.startsAt);
  const today = formatDateKst(now);
  const todays = dated.find((s) => formatDateKst(s.startsAt) === today);
  if (todays) return todays.id;
  const time = (s) => new Date(s.startsAt).getTime();
  const upcoming = dated.filter((s) => time(s) > now.getTime()).sort((a, b) => time(a) - time(b));
  if (upcoming.length) return upcoming[0].id;
  const past = dated.filter((s) => time(s) <= now.getTime()).sort((a, b) => time(b) - time(a));
  return past[0]?.id || sessions[0]?.id || null;
}

/** 이름이 없으면 UUID 대신 '이름 없음' — 내부 ID 는 강사에게 의미가 없다 */
function displayNameOf(enrollment, record) {
  const userName = enrollment?.userName || record?.userName;
  if (userName) return formatUserDisplayName({ userName, birthDate: enrollment?.birthDate });
  return enrollment?.email || MISSING_NAME;
}

export default {
  components: {
    AppEmptyState,
    AppPageHeader,
    AttendanceSessionHeader,
    AttendanceTally,
    AppErrorBanner,
    AppInstitutionMissingBanner,
    AttendanceAdjustDialog,
    AttendanceDetailDialog,
    AttendanceQrDialog,
    AttendanceRecordDialog,
    AttendanceRosterTable,
  },
  name: 'InstructorAttendancePage',
  mixins: [instructorConsoleCourses],
  data() {
    return {
      courses: [],
      sessions: [],
      enrollments: [],
      selectedCourseId: null,
      selectedSessionId: null,
      records: [],
      selectedIds: [],
      // 주소의 ?sessionId·?userId 는 첫 진입에서 한 번만 쓴다 (과정을 바꾼 뒤엔 의미가 없다)
      pendingSessionId: this.$route.query.sessionId || null,
      highlightUserId: this.$route.query.userId || '',
      showQr: false,
      // 출결 시간 판정용 — 화면을 열어 둔 채 수업 시간이 되면 QR 버튼이 켜지도록 몇 초마다 갱신
      now: Date.now(),
      nowTimer: null,
      qrPollTimer: null,
      statusChanging: false,
      coursesLoading: false,
      sessionsLoading: false,
      rosterLoading: false,
      loading: false,
      coursesError: '',
      enrollmentsError: '',
      sessionsError: '',
      attendanceError: '',
      showDetail: false,
      detailId: null,
      showAdjust: false,
      adjustTarget: null,
      flashAttendanceId: '',
      showRecord: false,
      recordTarget: null,
      bulkRunning: false,
      bulkProgress: { done: 0, total: 0 },
      // 집계 칩으로 거른 상태 — 'ALL' | 'NONE'(미기록) | PRESENT·LATE·ABSENT·EXCUSED
      rosterFilter: 'ALL',
      // 행에서 한 번에 기록 중인 { key, status }
      quickBusy: { key: '', status: '' },
    };
  },
  computed: {
    /**
     * 명단 = 승인된 수강생 + 이 회차 출결 기록. 기록이 없는 수강생은 '미기록' 행으로,
     * 승인 목록에 없는 기록(그 뒤 철회 등)도 버리지 않고 함께 보인다
     */
    rosterRows() {
      const recordByEnrollment = new Map(this.records.map((r) => [r.enrollmentId, r]));
      const fromRoster = this.enrollments.map((e) => this.toRow(e, recordByEnrollment.get(e.id)));
      const rosterIds = new Set(this.enrollments.map((e) => e.id));
      const orphans = this.records.filter((r) => !rosterIds.has(r.enrollmentId)).map((r) => this.toRow(null, r));
      return [...fromRoster, ...orphans].sort((a, b) => a.displayName.localeCompare(b.displayName, 'ko'));
    },
    unrecordedIds() {
      return this.rosterRows.filter((row) => !row.attendanceId).map((row) => row.enrollmentId);
    },
    /** 수강생·회차 중 하나라도 못 받았으면 — 빈 상태 대신 오류로 보인다 */
    courseDataError() {
      return this.enrollmentsError || this.sessionsError;
    },
    filteredRows() {
      if (this.rosterFilter === 'ALL') return this.rosterRows;
      return this.rosterRows.filter((row) => tallyKeyOf(row) === this.rosterFilter);
    },
    attendedCount() {
      return this.rosterRows.filter((row) => ATTENDED_STATUSES.includes(row.status)).length;
    },
    highlightKey() {
      if (!this.highlightUserId) return '';
      return this.rosterRows.find((row) => row.userId === this.highlightUserId)?.key || '';
    },
    courseOptions() {
      return this.courses.map((c) => ({ label: formatCourseOption(c), value: c.id }));
    },
    selectedCourseLabel() {
      return this.courseOptions.find((o) => o.value === this.selectedCourseId)?.label || '';
    },
    selectedSession() {
      return this.sessions.find((s) => s.id === this.selectedSessionId) || null;
    },
    emptySessionText() {
      if (!this.selectedCourseId) {
        return { title: '강의를 골라 주세요', description: '강의와 회차를 고르면 수강생 출결 명단이 보입니다.' };
      }
      if (!this.sessions.length) {
        const detail = { admin: 'admin', institution: 'institution' }[this.consoleScope] || 'instructor';
        return {
          title: '이 강의에 회차가 없습니다',
          description: '학습 목차에서 회차를 추가하면 출결을 기록할 수 있습니다.',
          actionLabel: '학습 목차로 가기',
          actionTo: { path: `/${detail}/courses/${this.selectedCourseId}`, query: { tab: 'curriculum' } },
        };
      }
      return { title: '회차를 골라 주세요', description: '회차를 고르면 그 회차의 출결 명단이 보입니다.' };
    },
    selectedSessionLabel() {
      const session = this.selectedSession;
      return session ? `${session.sessionNo}회차 · ${formatSessionWhen(session)}` : '';
    },
    sessionOptions() {
      const today = formatDateKst(new Date());
      return this.sessions.map((s) => ({
        label: [
          `${s.sessionNo}회차`,
          // 연도·초까지 붙어 칸에서 잘렸다 — '10. 13. (월) 10:00~12:00'
          formatSessionWhen(s),
          SESSION_STATUS_TEXT[s.status] || '',
          s.startsAt && formatDateKst(s.startsAt) === today ? '오늘' : '',
        ]
          .filter(Boolean)
          .join(' · '),
        value: s.id,
      }));
    },
  },
  watch: {
    // 기관 콘솔: 머리의 기관 선택기로 기관을 바꾸면 그 기관 과정으로 다시 읽는다
    institutionId(next, prev) {
      if (this.consoleScope !== 'institution' || next === prev) return;
      this.selectedCourseId = null;
      this.resetCourseData();
      this.loadCourses();
    },
  },
  mounted() {
    this.loadCourses();
    this.nowTimer = setInterval(() => {
      this.now = Date.now();
    }, NOW_TICK_MS);
  },
  beforeUnmount() {
    clearInterval(this.nowTimer);
    this.stopQrPoll();
  },
  methods: {
    toRow(enrollment, record) {
      const enrollmentId = enrollment?.id || record?.enrollmentId;
      return {
        key: enrollmentId || record?.id,
        enrollmentId,
        userId: enrollment?.userId || record?.userId || '',
        displayName: displayNameOf(enrollment, record),
        attendanceId: record?.id || null,
        status: record?.status || null,
        attendanceMethod: record?.attendanceMethod || null,
        checkedAt: record?.checkedAt || null,
      };
    },
    resetCourseData() {
      this.selectedSessionId = null;
      this.sessions = [];
      this.enrollments = [];
      this.records = [];
      this.selectedIds = [];
    },
    openDetail(row) {
      if (!row?.attendanceId) return;
      this.detailId = row.attendanceId;
      this.showDetail = true;
    },
    async loadCourses() {
      this.coursesLoading = true;
      this.coursesError = '';
      try {
        const result = await this.fetchConsoleCourses({ page: 0, size: 50 });
        const fetched = Array.isArray(result.data) ? result.data : [];
        const queryCourseId = this.$route.query.courseId;
        // 관리자: 강좌 상세 "출결 상세"가 ?courseId= 로 넘긴 강좌가 최근 50개 밖이면 따로 받아 앞에 붙인다
        // (강사·기관은 자기 범위 밖 강좌를 붙이지 않는다)
        const needsExtra = queryCourseId && this.consoleScope === 'admin' && !fetched.some((c) => c.id === queryCourseId);
        const extra = needsExtra ? await getCourse(queryCourseId).catch(() => null) : null;
        this.courses = extra ? [extra, ...fetched] : fetched;
        const preferred = this.pickInitialCourseId(this.courses);
        if (preferred) await this.onCourseChange(preferred);
      } catch (error) {
        this.coursesError = error.message || '강의 목록을 불러오지 못했습니다.';
        this.courses = [];
      } finally {
        this.coursesLoading = false;
      }
    },
    async onCourseChange(courseId) {
      const sameCourse = courseId === this.selectedCourseId;
      const keepSessionId = sameCourse ? this.selectedSessionId : null;
      this.selectedCourseId = courseId;
      if (!sameCourse) this.resetCourseData();
      this.syncCourseQuery(courseId);
      await Promise.all([this.loadEnrollments(courseId), this.loadSessions(courseId, keepSessionId)]);
    },
    retryCourseData() {
      if (this.selectedCourseId) this.onCourseChange(this.selectedCourseId);
    },
    /** 이 과정의 승인된 수강생 — 회차 명단의 기준 */
    async loadEnrollments(courseId) {
      if (!courseId) return;
      this.rosterLoading = true;
      this.enrollmentsError = '';
      try {
        const all = await fetchAllPages((page) => listCourseEnrollments(courseId, { page, size: PAGE_SIZE }));
        // 응답이 늦게 와 그새 과정을 바꿨으면 버린다
        if (courseId !== this.selectedCourseId) return;
        this.enrollments = all.filter((row) => row.status === 'APPROVED');
      } catch (error) {
        if (courseId !== this.selectedCourseId) return;
        this.enrollments = [];
        this.enrollmentsError = error.message || '수강생 명단을 불러오지 못했습니다.';
      } finally {
        this.rosterLoading = false;
      }
    },
    async loadSessions(courseId, keepSessionId = null) {
      if (!courseId) return;
      this.sessionsLoading = true;
      this.sessionsError = '';
      try {
        const result = await listCourseSessions(courseId, { page: 0, size: PAGE_SIZE });
        if (courseId !== this.selectedCourseId) return;
        this.sessions = Array.isArray(result.data) ? result.data : [];
        const has = (id) => id && this.sessions.some((s) => s.id === id);
        const wanted = this.pendingSessionId;
        this.pendingSessionId = null;
        const next = has(wanted) ? wanted : has(keepSessionId) ? keepSessionId : pickDefaultSessionId(this.sessions);
        if (next) await this.onSessionChange(next);
      } catch (error) {
        if (courseId !== this.selectedCourseId) return;
        this.sessions = [];
        this.sessionsError = error.message || '회차 목록을 불러오지 못했습니다.';
      } finally {
        this.sessionsLoading = false;
      }
    },
    // 학습 목차 탭과 같은 규칙 — 종료는 출결을 닫아 되돌리려면 관리자 정정이 필요해 바꾸기 전에 묻는다
    async changeSessionStatus(action) {
      const session = this.selectedSession;
      if (!session || this.statusChanging) return;
      if (action.status === 'COMPLETED') {
        const ok = await confirmDialog(this.$q, {
          title: action.label,
          message: `${session.sessionNo}회차 수업을 종료합니다. 종료하면 출결을 더 받을 수 없습니다.`,
          ok: action.label,
          color: 'primary',
          cancel: '닫기',
        });
        if (!ok) return;
      }
      this.statusChanging = true;
      try {
        await updateCourseSession(session.id, { status: action.status });
        this.$q.notify({
          type: 'info',
          message: `${session.sessionNo}회차를 '${STATUS_LABEL[action.status] || action.status}' 상태로 바꿨습니다.`,
          position: 'top',
        });
        await this.loadSessions(this.selectedCourseId, session.id);
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error?.message || '상태 변경에 실패했습니다.', position: 'top' });
      } finally {
        this.statusChanging = false;
      }
    },
    async onSessionChange(sessionId) {
      if (sessionId !== this.selectedSessionId) {
        this.selectedIds = [];
        this.rosterFilter = 'ALL';
      }
      this.selectedSessionId = sessionId;
      await this.loadAttendance(sessionId);
    },
    /** quiet: QR 창 뒤에서 몇 초마다 다시 받을 때 — 표 로딩 막대를 깜빡이지 않는다 */
    async loadAttendance(sessionId, { quiet = false } = {}) {
      if (!sessionId) return;
      if (!quiet) this.loading = true;
      this.attendanceError = '';
      try {
        const all = await fetchAllPages((page) => listSessionAttendance(sessionId, { page, size: PAGE_SIZE }));
        if (sessionId !== this.selectedSessionId) return;
        this.records = all;
        // 그새 기록이 생긴 수강생은 선택에서 뺀다 — 일괄 등록이 409 로 실패하지 않게
        const recorded = new Set(all.map((r) => r.enrollmentId));
        this.selectedIds = this.selectedIds.filter((id) => !recorded.has(id));
      } catch (error) {
        if (sessionId !== this.selectedSessionId) return;
        this.attendanceError = error.message || '출결 목록을 불러오지 못했습니다.';
        if (!quiet) this.records = [];
      } finally {
        this.loading = false;
      }
    },
    // 거르면 안 보이는 사람이 [선택 출석]에 섞이지 않게 선택을 비운다(이수 처리 화면과 같은 규칙)
    onRosterFilterChange(value) {
      this.rosterFilter = value;
      this.selectedIds = [];
    },
    onSelectedChange(ids) {
      this.selectedIds = ids;
    },
    // QR 이 갱신될 때마다 출석 현황도 다시 받아 방금 찍은 학생이 보이게 한다
    onQrIssued() {
      if (this.selectedSessionId) this.loadAttendance(this.selectedSessionId, { quiet: true });
    },
    onQrToggle(open) {
      if (open) {
        this.startQrPoll();
        return;
      }
      this.stopQrPoll();
      if (this.selectedSessionId) this.loadAttendance(this.selectedSessionId);
    },
    startQrPoll() {
      this.stopQrPoll();
      this.qrPollTimer = setInterval(() => {
        if (this.selectedSessionId) this.loadAttendance(this.selectedSessionId, { quiet: true });
      }, QR_ROSTER_POLL_MS);
    },
    stopQrPoll() {
      if (this.qrPollTimer) clearInterval(this.qrPollTimer);
      this.qrPollTimer = null;
    },
    /**
     * 미기록 수강생 일괄 등록 — 서버는 수동(ADMIN) 기록만 받으므로 한 명씩 POST 하되
     * 몇 개씩 동시에 보내 기다림을 줄인다. 한 명 실패가 나머지를 막지 않는다
     */
    async bulkRecord(status, enrollmentIds) {
      const sessionId = this.selectedSessionId;
      const unrecorded = new Set(this.unrecordedIds);
      const targets = enrollmentIds.filter((id) => unrecorded.has(id));
      if (!sessionId || !targets.length || this.bulkRunning) return;
      const statusText = STATUS_LABEL[status] || status;
      const ok = await confirmDialog(this.$q, {
        title: `${statusText} 일괄 등록`,
        message: `[${this.selectedSessionLabel}] 미기록 수강생 ${targets.length}명을 '${statusText}'(으)로 등록할까요?`,
        ok: `${targets.length}명 등록`,
      });
      if (!ok) return;
      this.bulkRunning = true;
      this.bulkProgress = { done: 0, total: targets.length };
      const failures = await this.runBulk(sessionId, status, targets);
      this.bulkRunning = false;
      this.notifyBulkResult(statusText, targets.length, failures);
      await this.loadAttendance(sessionId);
    },
    async runBulk(sessionId, status, targets) {
      const checkedAt = nowIso();
      const queue = [...targets];
      const failures = [];
      const worker = async () => {
        while (queue.length) {
          const enrollmentId = queue.shift();
          try {
            await recordAttendance(sessionId, {
              enrollmentId,
              status,
              source: 'ADMIN_WEB',
              attendanceMethod: 'ADMIN',
              checkedAt,
            });
          } catch (error) {
            failures.push({ enrollmentId, message: error.message || '알 수 없는 오류' });
          }
          this.bulkProgress = { ...this.bulkProgress, done: this.bulkProgress.done + 1 };
        }
      };
      await Promise.all(Array.from({ length: Math.min(BULK_CONCURRENCY, targets.length) }, worker));
      return failures;
    },
    notifyBulkResult(statusText, total, failures) {
      const succeeded = total - failures.length;
      if (!failures.length) {
        this.$q.notify({ type: 'positive', message: `${succeeded}명을 '${statusText}'(으)로 등록했습니다.`, position: 'top' });
        return;
      }
      const nameOf = (id) => this.rosterRows.find((row) => row.enrollmentId === id)?.displayName || MISSING_NAME;
      const detail = failures
        .slice(0, 3)
        .map((f) => `${nameOf(f.enrollmentId)}: ${f.message}`)
        .join(' / ');
      const more = failures.length > 3 ? ` 외 ${failures.length - 3}명` : '';
      this.$q.notify({
        type: succeeded ? 'warning' : 'negative',
        message: `성공 ${succeeded}명 · 실패 ${failures.length}명`,
        caption: `${detail}${more}`,
        position: 'top',
        timeout: 8000,
        multiLine: true,
      });
    },
    openAdjust(row, status) {
      this.adjustTarget = {
        attendanceId: row.attendanceId,
        name: row.displayName,
        fromStatus: row.status,
        status,
        attendanceMethod: row.attendanceMethod,
        checkedAt: row.checkedAt,
      };
      this.showAdjust = true;
    },
    /** 정정 뒤 명단을 다시 읽고, 바뀐 줄을 잠깐 깜빡인다 */
    async onAdjusted(attendanceId) {
      this.flashAttendanceId = '';
      await this.loadAttendance(this.selectedSessionId);
      this.flashAttendanceId = attendanceId;
      window.setTimeout(() => {
        if (this.flashAttendanceId === attendanceId) this.flashAttendanceId = '';
      }, ROW_FLASH_MS);
    },
    /** 미기록 행 한 번에 기록 — 창 없이 지금 시각으로. 잘못 눌렀으면 [정정](사유)으로 바꾼다 */
    async recordOne(row, status) {
      const sessionId = this.selectedSessionId;
      if (!sessionId || !row?.enrollmentId || this.quickBusy.key || this.bulkRunning) return;
      this.quickBusy = { key: row.key, status };
      try {
        await recordAttendance(sessionId, {
          enrollmentId: row.enrollmentId,
          status,
          source: 'ADMIN_WEB',
          attendanceMethod: 'ADMIN',
          checkedAt: nowIso(),
        });
        this.$q.notify({
          type: 'positive',
          message: `${row.displayName} — ${STATUS_LABEL[status] || status}`,
          caption: '잘못 눌렀으면 [정정]으로 바꿀 수 있습니다.',
          position: 'top',
          timeout: 2500,
        });
        await this.loadAttendance(sessionId, { quiet: true });
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error?.message || '출결을 기록하지 못했습니다.', position: 'top' });
      } finally {
        this.quickBusy = { key: '', status: '' };
      }
    },
    /** 명단 행의 [등록] — 그 수강생으로 정해진 채 연다 */
    openRecord(row) {
      if (!row?.enrollmentId) return;
      this.recordTarget = { enrollmentId: row.enrollmentId, name: row.displayName };
      this.showRecord = true;
    },
  },
};
</script>

<style scoped>
.field-hint {
  margin-top: 4px;
  font-size: 13px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

/* 요약 · 일괄 버튼 줄 — 폰에선 안내 글자를 숨기고 버튼이 다음 줄로 내려간다 */
.bulk-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

/* 이름과 기록 단추가 1,000px 떨어져 있었다 — 본문 폭을 묶는다 */
</style>
