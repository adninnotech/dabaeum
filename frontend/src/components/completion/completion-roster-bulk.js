import { runPool } from '@/components/completion/run-pool';
import { confirmCompletion, evaluateCompletion } from '@/services/completion-api';
import { issueCredential } from '@/services/credential-api';
import { settledNotice, waitForCredentialSettled } from '@/services/credential-status';
import {
  endOfDayKst,
  minValidUntilDate,
  VALID_UNTIL_HINT,
  validUntilError,
} from '@/utils/credential-validity';
import { formatAttendanceRate } from '@/utils/attendance-rate';
import { formatDateTimeKst } from '@/utils/datetime';

/* 이수 명단의 처리(평가·확정·발급) — 행 하나와 일괄이 같은 요청 함수를 쓴다.
 * CompletionRoster 전용 mixin: this.rows·updateRow·bulkTargets·canRevert 에 기댄다. */

const BULK_LABEL = {
  evaluate: '출석 판정',
  confirm: '이수 확정',
  issue: '수료증 발급',
};

// 발급은 요청마다 최대 30초 상태 조회가 붙어 무겁다 — 평가·확정보다 적게 동시에
const BULK_CONCURRENCY = { evaluate: 3, confirm: 3, issue: 2 };

const DIALOG_BUTTONS = {
  cancel: { flat: true, noCaps: true, label: '취소' },
};

// 확정은 되돌리기 어렵다 — 되돌릴 권한이 없는 사람에게는 누가 되돌리는지 미리 알린다
const REVERT_LIMIT_NOTE = '확정 취소는 사이트 관리자만 할 수 있습니다.';

function okButton(label, color = 'primary') {
  return { unelevated: true, noCaps: true, color, label };
}

// 폐기(REVOKED)된 수료증은 같은 발급(POST issue)으로 다시 발급한다 — /reissue 는 ISSUED 만 받는다(사용자 결정 10-21)
const REISSUE_NOTE = '폐기된 수료증을 대신할 새 수료증을 발급합니다. 대구체인에 영구 기록됩니다.';

function isRevoked(row) {
  return row.credential?.status === 'REVOKED';
}

/** 폐기 행 확인 문구 — 이전 수료증의 폐기 일시·사유 */
function reissueMessage(row) {
  const when = formatDateTimeKst(row.credential?.revokedAt, '일시 미상');
  const why = row.credential?.revocationReason || '사유 없음';
  return `${row.applicantLabel}의 이전 수료증은 ${when}에 폐기됐습니다(사유: ${why}). ${REISSUE_NOTE}`;
}

/** 발급 확인 창 — 발급하면 확정 취소가 막히므로 확인과 함께 유효기간(선택)을 받는다 */
function issueDialog(message, { title = '수료증(VC) 발급', okLabel = '발급' } = {}) {
  return {
    title,
    message: `${message} 발급한 뒤에는 이수 확정을 취소할 수 없습니다. ${VALID_UNTIL_HINT}`,
    prompt: {
      model: '',
      type: 'date',
      outlined: true,
      stackLabel: true,
      label: '유효기간 (선택)',
      min: minValidUntilDate(),
      // 오늘·지난 날짜면 [발급]이 꺼진다 — 발급 당일 밤 만료되는 실수를 막는다
      isValid: (value) => !validUntilError(value),
    },
    ...DIALOG_BUTTONS,
    ok: okButton(okLabel),
  };
}

export default {
  data() {
    return {
      // { label, done, total } — 불러오기·일괄 처리 진행 표시
      progress: null,
      failures: [],
      failureTitle: '',
    };
  },
  methods: {
    async requestEvaluate(row) {
      // 판정은 서버가 출결 기록으로 계산한다 — 빈 본문으로 요청한다
      const completion = await evaluateCompletion(row.enrollmentId);
      this.updateRow(row.enrollmentId, { completion, completionError: '' });
      return completion;
    },
    async requestConfirm(row) {
      const completion = await confirmCompletion(row.enrollmentId);
      // 방금 확정한 이수엔 수료증이 아직 없다 — 다시 읽지 않고 '미발급'으로 둬 바로 발급할 수 있게
      this.updateRow(row.enrollmentId, { completion, credentialLoaded: true, credentialError: '' });
      return completion;
    },
    /** 202 는 접수일 뿐 — 발급 완료·실패가 정해질 때까지 기다리고, 실패면 사유를 담아 던진다 */
    async requestIssue(row, validUntilDate) {
      const accepted = await issueCredential(row.completion.id, {
        validUntil: endOfDayKst(validUntilDate),
      });
      this.updateRow(row.enrollmentId, { credential: accepted });
      const result = await waitForCredentialSettled(accepted, 'issue');
      this.updateRow(row.enrollmentId, { credential: result.credential || accepted });
      if (!result.timedOut && result.credential?.status === 'FAILED') {
        throw new Error(settledNotice('issue', result).message);
      }
      return result;
    },

    /** 확정 확인 문구 — 확정 취소 권한(this.canRevert)이 없으면 한 줄 덧붙인다 */
    confirmMessage(lead) {
      const base = `${lead} 확정하면 조건에 맞는 배지가 발급됩니다.`;
      return this.canRevert ? base : `${base} ${REVERT_LIMIT_NOTE}`;
    },

    /* ---------- 행 하나 ---------- */
    async runOne(row, kind, request) {
      this.updateRow(row.enrollmentId, { busy: kind });
      try {
        return await request();
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: `${row.applicantLabel}: ${error.message}`,
          position: 'top',
        });
        return null;
      } finally {
        this.updateRow(row.enrollmentId, { busy: '' });
      }
    },
    async evaluateOne(row) {
      const completion = await this.runOne(row, 'evaluate', () => this.requestEvaluate(row));
      if (!completion) return;
      this.$q.notify({
        type: 'positive',
        message: `${row.applicantLabel} 출석 판정 완료 (출석률 ${formatAttendanceRate(completion.attendanceRate)})`,
        position: 'top',
      });
    },
    askConfirmOne(row) {
      this.$q
        .dialog({
          title: '이수 확정',
          message: this.confirmMessage(`${row.applicantLabel}의 이수를 확정합니다.`),
          ...DIALOG_BUTTONS,
          ok: okButton('확정', 'positive'),
        })
        .onOk(async () => {
          const completion = await this.runOne(row, 'confirm', () => this.requestConfirm(row));
          if (completion)
            this.$q.notify({
              type: 'positive',
              message: '이수가 확정되었습니다.',
              position: 'top',
            });
        });
    },
    askIssueOne(row) {
      const dialog = isRevoked(row)
        ? issueDialog(reissueMessage(row), { title: '수료증(VC) 재발급', okLabel: '재발급' })
        : issueDialog(`${row.applicantLabel}의 수료증을 발급합니다.`);
      this.$q
        .dialog(dialog)
        .onOk(async (date) => {
          const result = await this.runOne(row, 'issue', () => this.requestIssue(row, date));
          if (result) this.$q.notify({ ...settledNotice('issue', result), position: 'top' });
        });
    },

    /* ---------- 단계 단추(체크 없이 전원) ---------- */
    /** 판정 전 전원 판정 · 이수 가능 전원 확정 — 가장 흔한 일을 체크 없이 한 번에(확인 창은 거친다) */
    askStage(kind) {
      const targets =
        kind === 'evaluate'
          ? this.rows.filter((row) => row.completionLoaded && !row.completion && !row.completionError)
          : this.rows.filter((row) => row.completion?.status === 'ELIGIBLE');
      if (!targets.length) return;
      const message =
        kind === 'evaluate'
          ? `판정 전 ${targets.length}명을 지금까지의 출결로 판정합니다. 기준을 넘으면 '이수 가능', 아니면 '미이수'가 됩니다.`
          : this.confirmMessage(`이수 가능 ${targets.length}명의 이수를 확정합니다.`);
      this.$q
        .dialog({
          title: kind === 'evaluate' ? '출석으로 판정' : '이수 확정',
          message,
          ...DIALOG_BUTTONS,
          ok: okButton(`${targets.length}명 ${kind === 'evaluate' ? '판정' : '확정'}`, kind === 'confirm' ? 'positive' : 'primary'),
        })
        .onOk(() => this.runBulk(kind, targets));
    },

    /* ---------- 일괄 ---------- */
    askBulk(kind) {
      const targets = this.bulkTargets[kind];
      if (!targets.length) return;
      const count = `선택한 수강생 중 ${targets.length}명`;
      if (kind === 'issue') {
        const revoked = targets.filter(isRevoked).length;
        const reissue = revoked ? ` 그중 ${revoked}명은 폐기된 수료증의 재발급입니다 — ${REISSUE_NOTE}` : '';
        this.$q
          .dialog(issueDialog(`${count}의 수료증을 발급합니다.${reissue}`))
          .onOk((date) => this.runBulk(kind, targets, date));
        return;
      }
      const message =
        kind === 'evaluate'
          ? `${count}의 출석을 평가합니다. 이미 평가한 수강생은 지금 출결로 다시 평가됩니다.`
          : this.confirmMessage(`${count}의 이수를 확정합니다.`);
      this.$q
        .dialog({
          title: `일괄 ${BULK_LABEL[kind]}`,
          message,
          ...DIALOG_BUTTONS,
          ok: okButton(`${targets.length}명 ${BULK_LABEL[kind]}`, kind === 'confirm' ? 'positive' : 'primary'),
        })
        .onOk(() => this.runBulk(kind, targets));
    },
    bulkRequest(kind, row, extra) {
      if (kind === 'evaluate') return this.requestEvaluate(row);
      if (kind === 'confirm') return this.requestConfirm(row);
      return this.requestIssue(row, extra);
    },
    async runBulk(kind, targets, extra) {
      const label = BULK_LABEL[kind];
      this.failures = [];
      this.progress = { label, done: 0, total: targets.length };
      const results = await runPool(targets, (row) => this.bulkRequest(kind, row, extra), {
        concurrency: BULK_CONCURRENCY[kind],
        onProgress: (done, total) => {
          this.progress = { label, done, total };
        },
      });
      this.progress = null;
      this.reportBulk(kind, results);
    },
    reportBulk(kind, results) {
      const failed = results.filter((result) => !result.ok);
      const okCount = results.length - failed.length;
      // 발급은 30초 안에 끝나지 않으면 '처리 중'으로 남는다 — 성공에 넣되 따로 알린다
      const pending =
        kind === 'issue'
          ? results.filter((result) => result.ok && result.value?.timedOut).length
          : 0;
      this.failureTitle = `일괄 ${BULK_LABEL[kind]}`;
      this.failures = failed.map((result) => ({
        enrollmentId: result.item.enrollmentId,
        applicantLabel: result.item.applicantLabel,
        message: result.error?.message || '처리하지 못했습니다.',
      }));
      const pendingText = pending ? ` (이 중 ${pending}건은 아직 처리 중 — 잠시 뒤 새로고침)` : '';
      this.$q.notify({
        type: failed.length ? 'warning' : 'positive',
        message: `일괄 ${BULK_LABEL[kind]} 완료 — 성공 ${okCount}건${pendingText}, 실패 ${failed.length}건`,
        position: 'top',
      });
      // 성공한 행은 선택에서 빼고 실패한 행만 남긴다 — 사유를 보고 바로 다시 시도할 수 있게
      this.selectedIds = this.failures.map((failure) => failure.enrollmentId);
    },
  },
};
