<template>
  <!-- 과정 수강생 명단 — 한 명씩 고르던 흐름 대신 한 표에서 평가·확정·발급을 행별·일괄로 처리한다 -->
  <q-card flat bordered class="completion-roster">
    <!-- 강사·관리자: 단계 띠가 거르기와 '지금 할 일' 단추를 맡는다(기관 발급 화면은 아래 칩) -->
    <CompletionFlowStrip
      v-if="!issueOnly && courseId && rows.length"
      :counts="stageCounts"
      :filter="statusFilter"
      :busy="busy"
      :blocked-reason="evaluateBlockedReason"
      :criteria="criteriaText"
      @filter="onFilterChange"
      @evaluate-all="askStage('evaluate')"
      @confirm-all="askStage('confirm')"
    />
    <q-card-section class="completion-roster__toolbar">
      <q-btn-toggle
        v-if="issueOnly"
        :model-value="statusFilter"
        :options="filterOptions"
        no-caps
        unelevated
        dense
        color="grey-2"
        text-color="grey-9"
        toggle-color="primary"
        class="completion-roster__filter"
        @update:model-value="onFilterChange"
      />
      <!-- 폰 카드 보기에는 표 머리 체크박스가 없어 한 명씩만 고를 수 있었다 — 같은 선택을 여기서 전체로 -->
      <q-checkbox
        v-if="$q.screen.lt.md && filteredRows.length"
        :model-value="allSelectedState"
        label="전체 선택"
        dense
        :disable="busy"
        @update:model-value="toggleSelectAll"
      />
      <!-- 아무도 고르지 않았을 때 (0)짜리 꺼진 버튼 셋이 늘 서 있으면 눈만 끈다 — 고른 뒤에만 보인다.
           채운 버튼은 다음 단계인 [일괄 확정] 하나, 나머지는 테두리 버튼 -->
      <div v-if="selectedRows.length" class="completion-roster__bulk">
        <span class="completion-roster__selected">{{ selectedRows.length }}명 선택</span>
        <q-btn
          v-if="!issueOnly"
          color="primary"
          outline
          no-caps
          :label="`일괄 평가 (${bulkTargets.evaluate.length})`"
          :disable="busy || !bulkTargets.evaluate.length || !!evaluateBlockedReason"
          @click="askBulk('evaluate')"
        />
        <q-btn
          v-if="!issueOnly"
          color="positive"
          unelevated
          no-caps
          icon="check"
          :label="`일괄 확정 (${bulkTargets.confirm.length})`"
          :disable="busy || !bulkTargets.confirm.length"
          @click="askBulk('confirm')"
        />
        <q-btn
          v-if="canIssue"
          color="primary"
          :outline="!issueOnly"
          :unelevated="issueOnly"
          no-caps
          :label="`일괄 발급 (${bulkTargets.issue.length})`"
          :disable="busy || !bulkTargets.issue.length"
          @click="askBulk('issue')"
        />
      </div>
      <div v-else class="completion-roster__hint">{{ hintText }}</div>
    </q-card-section>

    <div v-if="progress" class="q-px-md q-pb-sm">
      <div class="completion-roster__progress-text q-mb-xs" role="status">
        {{ progress.label }} {{ progress.done }}/{{ progress.total }}
      </div>
      <q-linear-progress
        :value="progress.total ? progress.done / progress.total : 0"
        color="primary"
        rounded
      />
    </div>

    <!-- 끝나지 않은 회차가 있으면 서버가 평가를 막는다 — 누른 뒤 오류로 알던 것을 미리, 할 수 있는 일과 함께 -->
    <div v-if="evaluateBlockedReason" class="completion-roster__gate q-mx-md q-mb-sm" role="status">
      <q-icon name="schedule" size="18px" aria-hidden="true" />
      <div class="completion-roster__gate-text">
        <strong>{{ evaluateBlockedReason }}</strong>
        <span>수업을 일찍 마쳤다면 학습 목차에서 그 회차를 [수업 종료]하면 바로 평가할 수 있어요.</span>
      </div>
      <router-link :to="curriculumLink" class="completion-roster__gate-link">학습 목차에서 회차 보기</router-link>
    </div>

    <q-banner v-if="failures.length" dense class="completion-roster__failures q-mx-md q-mb-sm">
      <div class="text-weight-bold q-mb-xs">{{ failureTitle }} — 실패 {{ failures.length }}건</div>
      <ul class="q-my-none q-pl-md">
        <li v-for="failure in failures" :key="failure.enrollmentId">
          <span class="text-weight-medium">{{ failure.applicantLabel }}</span
          >: {{ failure.message }}
        </li>
      </ul>
      <template #action>
        <q-btn flat dense no-caps label="닫기" @click="failures = []" />
      </template>
    </q-banner>

    <q-table
      flat
      :rows="filteredRows"
      :columns="columns"
      row-key="enrollmentId"
      selection="multiple"
      :selected="selectedRows"
      :pagination="{ rowsPerPage: 0 }"
      hide-pagination
      :loading="loading"
      :grid="$q.screen.lt.md"
      :no-data-label="emptyLabel"
      @update:selected="onSelected"
    >
      <!-- 평가 전에도 출결 요약(조회만)으로 'n/m회 · %'를 보인다 — 근거 없이 [판정]부터 눌러야 했다 -->
      <template #body-cell-rate="props">
        <q-td :props="props">
          <div class="roster-rate">
            <span class="roster-rate__main">{{ rateText(props.row) }}</span>
            <span v-if="minutesText(props.row) !== '-'" class="roster-rate__sub">이수 {{ minutesText(props.row) }}</span>
          </div>
        </q-td>
      </template>
      <template #body-cell-status="props">
        <q-td :props="props">
          <CompletionRosterStatus :row="props.row" />
          <div v-if="props.row.completion?.evaluatedAt" class="roster-rate__sub">
            판정 {{ formatDateMinuteKst(props.row.completion.evaluatedAt) }}
          </div>
        </q-td>
      </template>
      <template #body-cell-credential="props">
        <q-td :props="props">
          <StatusBadge v-if="props.row.credential" :status="props.row.credential.status" />
          <span v-else-if="props.row.credentialError" class="text-negative" :title="props.row.credentialError">확인 못 함</span>
          <span v-else-if="isCheckingCredential(props.row)" class="text-grey-6">확인 중</span>
          <!-- '-'는 무슨 뜻인지 모호했다 -->
          <span v-else class="text-grey-6">미발급</span>
        </q-td>
      </template>
      <template #body-cell-actions="props">
        <q-td :props="props" class="completion-roster__actions">
          <CompletionRosterActions
            :row="props.row"
            :can-issue="canIssue"
            :can-process="!issueOnly"
            :can-revert="canRevert"
            :disable="!!progress"
            :evaluate-blocked-reason="evaluateBlockedReason"
            @evaluate="evaluateOne"
            @fail="openFail"
            @confirm="askConfirmOne"
            @issue="askIssueOne"
            @revert="openRevert"
          />
        </q-td>
      </template>
      <template #item="props">
        <!-- 명단 카드 안에 또 테두리 카드라 이중 테두리·들여쓰기가 겹쳤다 — 구분선으로 나눈다 -->
        <div class="col-12">
          <q-card flat class="completion-roster__item">
            <q-card-section class="row items-center no-wrap q-gutter-sm">
              <q-checkbox
                :model-value="isSelected(props.row)"
                :aria-label="`${props.row.applicantLabel} 선택`"
                dense
                @update:model-value="toggleSelect(props.row)"
              />
              <div class="col text-weight-medium ellipsis">{{ props.row.applicantLabel }}</div>
              <CompletionRosterStatus :row="props.row" />
            </q-card-section>
            <q-card-section class="q-pt-none text-caption text-grey-8">
              출석률 {{ rateText(props.row) }} · 이수 시간 {{ minutesText(props.row) }}
              <!-- 폰은 수료증이 있을 때만 보여 이수 완료인데 발급됐는지 알 수 없었다 -->
              <template v-if="canIssue && props.row.credential">
                · 수료증 <StatusBadge size="sm" :status="props.row.credential.status" />
              </template>
              <template v-else-if="canIssue && props.row.credentialError">· 수료증 확인 못 함</template>
              <template v-else-if="canIssue && isCheckingCredential(props.row)">· 수료증 확인 중</template>
              <template v-else-if="canIssue">· 수료증 미발급</template>
            </q-card-section>
            <!-- 할 수 있는 버튼이 없으면(강사가 본 이수 완료 등) 빈 칸이 카드마다 70px 남았다 -->
            <q-card-section v-if="hasRowActions(props.row)" class="q-pt-none">
              <CompletionRosterActions
                :row="props.row"
                :can-issue="canIssue"
                :can-process="!issueOnly"
                :can-revert="canRevert"
                :disable="!!progress"
                :evaluate-blocked-reason="evaluateBlockedReason"
                @evaluate="evaluateOne"
                @fail="openFail"
                @confirm="askConfirmOne"
                @issue="askIssueOne"
                @revert="openRevert"
              />
            </q-card-section>
          </q-card>
        </div>
      </template>
    </q-table>

    <!-- 미이수 사유 — POST …/completion/evaluate { failureReason }. 사유는 학습자 신청 상세에도 보인다 -->
    <CorrectionReasonDialog
      v-model="failOpen"
      title="미이수 처리"
      :target="failTarget"
      :notes="FAIL_NOTES"
      confirm-label="미이수로 판정"
      reason-label="미이수 사유"
      reason-hint="학습자 신청 상세에 그대로 보입니다."
      :action="fail"
      @done="onFailed"
    />

    <CorrectionReasonDialog
      v-if="canRevert"
      v-model="revertOpen"
      title="이수 확정 취소"
      :target="revertTarget"
      :notes="REVERT_NOTES"
      confirm-label="확정 취소"
      :action="revert"
      :error-message="revertErrorMessage"
      @done="onReverted"
    />
  </q-card>
</template>

<script>
import StatusBadge from '@/components/StatusBadge.vue';
import CorrectionReasonDialog from '@/components/console/CorrectionReasonDialog.vue';
import CompletionFlowStrip from '@/components/completion/CompletionFlowStrip.vue';
import CompletionRosterActions from '@/components/completion/CompletionRosterActions.vue';
import CompletionRosterStatus from '@/components/completion/CompletionRosterStatus.vue';
import completionRosterBulk from '@/components/completion/completion-roster-bulk';
import { runPool } from '@/components/completion/run-pool';
import {
  canConfirmRow,
  canEvaluateRow,
  canIssueRow,
  fetchApprovedEnrollments,
  fetchCompletionOrNull,
  fetchCourseCredential,
  matchesFilter,
  patchRow,
  ISSUE_READY_FILTER,
  ROSTER_FILTERS,
  rowStatusKey,
  toRosterRow,
} from '@/components/completion/completion-roster-rows';
import { pendingSessionGate } from '@/components/completion/completion-session-gate';
import { evaluateCompletion, revertCompletion } from '@/services/completion-api';
import { listCourseSessions } from '@/services/session-api';
import { formatAttendanceRate } from '@/utils/attendance-rate';
import { getAttendanceSummary } from '@/services/attendance-api';
import { formatDateMinuteKst } from '@/utils/datetime';

// 이수 조회는 수강생마다 1건이라(N건) 몇 개씩만 동시에 — 한꺼번에 보내면 서버가 몰린다
const LOAD_CONCURRENCY = 4;

const REVERT_NOTES = [
  '이수 상태가 확정 전(이수가능)으로 돌아갑니다. 다시 확정할 수 있습니다.',
  '이 이수로 받은 배지는 폐기로 표시됩니다.',
  '수료증(VC)이 이미 발급된 이수는 취소할 수 없습니다.',
];

export default {
  name: 'CompletionRoster',
  components: {
    CompletionFlowStrip,
    CompletionRosterActions,
    CompletionRosterStatus,
    CorrectionReasonDialog,
    StatusBadge,
  },
  mixins: [completionRosterBulk],
  props: {
    courseId: { type: String, default: null },
    /** 확정 취소 대상 문구에 붙인다 */
    courseTitle: { type: String, default: '' },
    /** 수료증 발급 — 플랫폼·기관 관리자만(강사는 발급 권한이 없다) */
    canIssue: { type: Boolean, default: false },
    /** 확정 취소 — 플랫폼 관리자만 */
    canRevert: { type: Boolean, default: false },
    /** 이수 기준 한 줄(강의 등록 때 정한 값) — 단계 띠 위에 */
    criteriaText: { type: String, default: '' },
    /** 발급 전용(기관 콘솔) — 출석 평가·이수 확정은 강사 몫이라 버튼을 그리지 않고 '발급 대기'부터 보인다 */
    issueOnly: { type: Boolean, default: false },
  },
  emits: ['busy-change', 'error'],
  data() {
    return {
      REVERT_NOTES,
      rows: [],
      loading: false,
      statusFilter: this.issueOnly ? ISSUE_READY_FILTER.value : 'ALL',
      selectedIds: [],
      revertOpen: false,
      failOpen: false,
      failRowId: null,
      failTarget: '',
      FAIL_NOTES: ['미이수로 판정해도 [다시 판정]으로 다시 판정할 수 있습니다.'],
      revertRowId: null,
      loadToken: 0,
      // 마지막으로 끝나는 미종료 회차({ session, closesAt }) — 있으면 평가를 미리 막는다
      sessionGate: null,
    };
  },
  computed: {
    columns() {
      // 칸 6개가 평가 전엔 '-'뿐이었다 — 넷으로(이수 시간·평가 일시는 작은 글자로). 이름·출석·상태 폭을 정해 처리 단추가 가깝게
      const fixed = (width) => ({ style: `width: ${width}px`, headerStyle: `width: ${width}px` });
      const columns = [
        { name: 'learner', label: '수강생', field: 'applicantLabel', align: 'left', sortable: true, ...fixed(220) },
        { name: 'rate', label: '출석', field: (row) => this.rateText(row), align: 'left', ...fixed(200) },
        { name: 'status', label: '이수 상태', field: rowStatusKey, align: 'left', ...fixed(220) },
      ];
      // 강사는 수료증을 다루지 않는다 — 비어 있는 열을 그리지 않는다
      if (this.canIssue)
        columns.push({
          name: 'credential',
          label: '수료증',
          field: (row) => row.credential?.status,
          align: 'center',
        });
      return [
        ...columns,
        { name: 'actions', label: '처리', field: 'enrollmentId', align: 'left' },
      ];
    },
    filterOptions() {
      // 발급 전용은 '발급 대기'를 맨 앞에, 평가 단계(평가 전·이수 가능·미이수)는 강사 몫이라 뺀다
      const filters = this.issueOnly
        ? [ISSUE_READY_FILTER, ...ROSTER_FILTERS.filter((f) => ['ALL', 'COMPLETED'].includes(f.value))]
        : ROSTER_FILTERS;
      return filters.map((filter) => {
        const count =
          filter.value === 'ALL'
            ? this.rows.length
            : this.rows.filter((row) => matchesFilter(row, filter.value)).length;
        return { value: filter.value, label: `${filter.label} ${count}` };
      });
    },
    filteredRows() {
      return this.rows.filter((row) => matchesFilter(row, this.statusFilter));
    },
    selectedRows() {
      return this.rows.filter((row) => this.selectedIds.includes(row.enrollmentId));
    },
    bulkTargets() {
      return {
        evaluate: this.issueOnly ? [] : this.selectedRows.filter(canEvaluateRow),
        confirm: this.issueOnly ? [] : this.selectedRows.filter(canConfirmRow),
        issue: this.canIssue ? this.selectedRows.filter(canIssueRow) : [],
      };
    },
    busy() {
      return this.loading || !!this.progress || this.rows.some((row) => row.busy);
    },
    emptyLabel() {
      if (!this.courseId) return '강의를 고르면 승인된 수강생 명단이 보입니다.';
      if (this.rows.length) return '이 상태의 수강생이 없습니다.';
      return '승인된 수강생이 없습니다.';
    },
    // 강사는 발급 권한이 없다 — 할 수 없는 일을 안내하지 않고 누가 하는지 알린다
    hintText() {
      if (this.issueOnly) return '강사가 이수를 확정한 수강생에게 수료증(VC)을 발급합니다. 체크하면 한 번에 발급할 수 있습니다.';
      return this.canIssue
        ? '수강생을 체크하면 한 번에 평가·확정·발급할 수 있습니다.'
        : '위 단추로 한 번에 판정·확정하거나, 체크한 수강생만 처리할 수 있습니다. 확정하면 기관에서 수료증(VC)을 발급합니다.';
    },
    /** 보이는 행 기준 — 전부 true, 하나도 없으면 false, 일부면 null(가운데 표시) */
    allSelectedState() {
      const visible = this.filteredRows.filter((row) => this.isSelected(row)).length;
      if (!visible) return false;
      return visible === this.filteredRows.length ? true : null;
    },
    /** 단계 띠 인원 — 판정 전·확정 대기·완료·미이수 */
    stageCounts() {
      const count = (key) => this.rows.filter((row) => rowStatusKey(row) === key).length;
      return {
        none: count('NONE'),
        eligible: count('ELIGIBLE'),
        completed: count('COMPLETED'),
        notCompleted: count('NOT_COMPLETED'),
      };
    },
    evaluateBlockedReason() {
      const gate = this.sessionGate;
      if (!gate) return '';
      const no = gate.session.sessionNo ? `${gate.session.sessionNo}회차` : '회차';
      return gate.closesAt
        ? `${no} 출석이 ${formatDateMinuteKst(new Date(gate.closesAt).toISOString())}에 마감되면 평가할 수 있어요.`
        : `${no}의 출석 마감 시각이 없어 아직 평가할 수 없어요.`;
    },
    // 콘솔마다 강좌 상세 라우트 이름이 다르다(admin·institution·instructor)
    curriculumLink() {
      const scope = (this.$route.path.split('/')[1] || 'instructor').replace(/[^a-z]/g, '');
      return { name: `${scope}-course-detail`, params: { courseId: this.courseId }, query: { tab: 'curriculum' } };
    },
    revertTarget() {
      const row = this.rows.find((item) => item.enrollmentId === this.revertRowId);
      return [row?.applicantLabel, this.courseTitle].filter(Boolean).join(' · ');
    },
  },
  watch: {
    courseId: {
      immediate: true,
      handler() {
        this.reload();
      },
    },
    busy(value) {
      this.$emit('busy-change', value);
    },
  },
  methods: {
    // CompletionRosterActions 가 그리는 버튼과 같은 조건
    hasRowActions(row) {
      const completed = row.completion?.status === 'COMPLETED';
      const canShowIssue = this.canIssue && !!row.completion && row.completion.status !== 'CANCELLED';
      const canProcess = !this.issueOnly && (canEvaluateRow(row) || canConfirmRow(row));
      return canProcess || canShowIssue || (this.canRevert && completed);
    },
    formatDateMinuteKst,
    /** 이수 확정 행의 수료증을 아직 읽는 중 */
    isCheckingCredential(row) {
      return row.completion?.status === 'COMPLETED' && !row.credentialLoaded;
    },
    /** 이미 발급한 수료증 — 발급 화면(canIssue)에서 이수 확정 행만 읽는다 */
    async loadCredential(row, token) {
      try {
        const credential = await fetchCourseCredential(row.userId, this.courseId);
        if (token === this.loadToken)
          this.updateRow(row.enrollmentId, { credential, credentialLoaded: true, credentialError: '' });
      } catch (error) {
        if (token === this.loadToken)
          this.updateRow(row.enrollmentId, {
            credentialLoaded: true,
            credentialError: error.message || '수료증 조회 실패',
          });
      }
    },
    /** '6/8회 · 75%' — 판정 뒤엔 판정 값, 판정 전엔 출결 요약(조회만) */
    rateText(row) {
      const source = row.completion || row.summary;
      if (!source) return '-';
      const rate = formatAttendanceRate(source.attendanceRate);
      const summary = row.summary;
      if (!summary?.totalSessions) return rate;
      const attended = (summary.presentCount || 0) + (summary.lateCount || 0);
      return `${attended}/${summary.totalSessions}회 · ${rate}`;
    },
    minutesText(row) {
      return row.completion?.completedMinutes != null
        ? `${row.completion.completedMinutes}분`
        : '-';
    },
    updateRow(enrollmentId, patch) {
      this.rows = patchRow(this.rows, enrollmentId, patch);
    },
    /** 과정 명단을 다시 읽는다 — 부모의 [새로고침]도 이것을 부른다 */
    async reload() {
      // 과정을 빠르게 바꾸면 앞 과정 응답이 늦게 와서 덮어쓰지 않게 — 마지막 요청만 반영한다
      this.loadToken += 1;
      const token = this.loadToken;
      this.rows = [];
      this.selectedIds = [];
      this.failures = [];
      this.progress = null;
      this.sessionGate = null;
      if (!this.courseId) return;
      this.loadSessionGate(token);
      this.loading = true;
      try {
        const enrollments = await fetchApprovedEnrollments(this.courseId);
        if (token !== this.loadToken) return;
        this.rows = enrollments.map(toRosterRow);
        this.loading = false;
        await this.loadCompletions(token);
      } catch (error) {
        if (token === this.loadToken)
          this.$emit('error', error.message || '수강생 명단을 불러오지 못했습니다.');
      } finally {
        if (token === this.loadToken) this.loading = false;
      }
    },
    // 회차를 못 불러와도 명단은 그대로 — 그땐 미리 막지 않고 서버 응답(오류 문구)에 맡긴다
    async loadSessionGate(token) {
      try {
        // Size 상한 100 — 200 이면 400(details page)이라 회차 조회가 매번 실패했다(QA)
        const page = await listCourseSessions(this.courseId, { page: 0, size: 100 });
        if (token === this.loadToken) this.sessionGate = pendingSessionGate(page.data);
      } catch {
        if (token === this.loadToken) this.sessionGate = null;
      }
    },
    async loadCompletions(token) {
      const targets = this.rows.map((row) => row.enrollmentId);
      await runPool(
        targets,
        async (enrollmentId) => {
          try {
            const completion = await fetchCompletionOrNull(enrollmentId);
            if (token === this.loadToken)
              this.updateRow(enrollmentId, { completion, completionLoaded: true });
            if (this.canIssue && completion?.status === 'COMPLETED') {
              const row = this.rows.find((item) => item.enrollmentId === enrollmentId);
              if (row) await this.loadCredential(row, token);
            }
            // 출결 요약(조회만) — 판정 전 출석을 미리 보이고, 판정 뒤엔 'n/m회'를 붙인다. 실패해도 명단은 그대로
            const summary = await getAttendanceSummary(enrollmentId).catch(() => null);
            if (token === this.loadToken && summary) this.updateRow(enrollmentId, { summary });
          } catch (error) {
            if (token === this.loadToken) {
              this.updateRow(enrollmentId, {
                completionLoaded: true,
                completionError: error.message || '이수 조회 실패',
              });
            }
          }
        },
        {
          concurrency: LOAD_CONCURRENCY,
          onProgress: (done, total) => {
            if (token !== this.loadToken) return;
            this.progress = done < total ? { label: '이수 정보 불러오는 중', done, total } : null;
          },
        },
      );
    },
    onFilterChange(value) {
      this.statusFilter = value;
      // 보이지 않는 행이 일괄 처리에 섞이지 않게 — 필터를 바꾸면 선택을 비운다
      this.selectedIds = [];
    },
    onSelected(list) {
      this.selectedIds = list.map((row) => row.enrollmentId);
    },
    isSelected(row) {
      return this.selectedIds.includes(row.enrollmentId);
    },
    toggleSelectAll() {
      // 표 머리 체크박스처럼 보이는 행만 — 일부만 골랐을 때 누르면 전부 고른다
      this.selectedIds = this.allSelectedState === true
        ? []
        : this.filteredRows.map((row) => row.enrollmentId);
    },
    toggleSelect(row) {
      this.selectedIds = this.isSelected(row)
        ? this.selectedIds.filter((id) => id !== row.enrollmentId)
        : [...this.selectedIds, row.enrollmentId];
    },
    openFail(row) {
      this.failRowId = row.enrollmentId;
      this.failTarget = row.applicantLabel || '';
      this.failOpen = true;
    },
    fail(reason) {
      return evaluateCompletion(this.failRowId, { failureReason: reason });
    },
    onFailed(completion) {
      if (completion) this.updateRow(this.failRowId, { completion, completionError: '' });
      this.$q.notify({ type: 'positive', message: `${this.failTarget || '수강생'} 님을 미이수로 판정했습니다.`, position: 'top' });
    },
    openRevert(row) {
      this.revertRowId = row.enrollmentId;
      this.revertOpen = true;
    },
    revert(reason) {
      return revertCompletion(this.revertRowId, reason);
    },
    revertErrorMessage(error) {
      if (error?.payload?.code === 'CORRECTION_NOT_ALLOWED') {
        return '수료증(VC)이 이미 발급된 이수라 취소할 수 없습니다.';
      }
      return null;
    },
    onReverted(completion) {
      if (completion) this.updateRow(this.revertRowId, { completion });
      this.$q.notify({ type: 'positive', message: '이수 확정을 취소했습니다.', position: 'top' });
    },
  },
};
</script>

<style scoped>
/* 넓은 화면 표: 처리 버튼 둘이 두 줄로 꺾여 행이 높아졌다 — 한 줄 */
.completion-roster__actions :deep(.completion-row-actions) {
  flex-wrap: nowrap;
}

.completion-roster__gate {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 8px 10px;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--db-accent-soft);
  color: var(--db-text-body);
  font-size: 13px;
}

.completion-roster__gate-text {
  display: flex;
  flex: 1 1 260px;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  word-break: keep-all;
}

.completion-roster__gate-text strong {
  font-size: 14px;
  color: var(--db-text-title);
}

.completion-roster__gate-link {
  flex-shrink: 0;
  align-self: center;
  color: var(--db-accent);
  font-weight: 600;
  white-space: nowrap;
}

.completion-roster__item {
  border-bottom: 1px solid var(--db-border-light);
  border-radius: 0;
}

/* 출석률·시간·일시는 행마다 자릿수가 달라 세로줄이 흔들렸다 — 숫자 폭을 고정한다 */
.completion-roster :deep(td.num) {
  font-variant-numeric: tabular-nums;
}

.completion-roster__selected {
  font-size: 14px;
  color: var(--db-text-sub);
}

.completion-roster__hint {
  font-size: 13px;
  color: var(--db-text-sub);
}

/* Quasar q-table 기본 글자(머리 12px·칸 13px)가 옆 버튼·필터(14px)와 다른 콘솔 표보다 작아 섞여 보였다 —
   운영 콘솔 목록 크기로 맞춘다 */
.completion-roster :deep(thead th) {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-title);
}

.completion-roster :deep(tbody td) {
  font-size: 14px;
  color: var(--db-text-body);
}

/* 칸 일곱에 처리 버튼 둘이 한 줄로 들어가게 — 기본 좌우 16px 이라 표가 옆으로 넘쳤다 */
.completion-roster :deep(thead th),
.completion-roster :deep(tbody td) {
  padding-left: 10px;
  padding-right: 10px;
}

.completion-roster :deep(.q-table__bottom) {
  font-size: 13px;
  color: var(--db-text-sub);
}

.completion-roster__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
/* 붙은 회색 덩어리 하나로 보여 칸이 나뉘는지 몰랐다 — 칩처럼 띄우고 테두리를 둔다 */
.completion-roster__filter {
  flex-wrap: wrap;
  gap: 6px;
  box-shadow: none;
  background: transparent;
}

.completion-roster__filter :deep(.q-btn) {
  border: 1px solid var(--db-border);
  border-radius: 999px !important;
  padding: 0 12px;
}

.completion-roster__filter :deep(.q-btn.bg-primary) {
  border-color: var(--db-primary);
}
.completion-roster__bulk {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.completion-roster__failures {
  background: var(--db-status-pink-soft);
  border-radius: 8px;
}
.completion-roster__actions {
  white-space: nowrap;
}

.completion-roster__progress-text {
  font-size: 12px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.roster-rate {
  display: flex;
  flex-direction: column;
}

.roster-rate__main {
  font-size: 15px;
  font-variant-numeric: tabular-nums;
  color: var(--db-text-title);
}

.roster-rate__sub {
  margin-top: 2px;
  font-size: 13px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}
</style>
