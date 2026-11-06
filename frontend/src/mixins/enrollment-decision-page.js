/* 수강 신청 처리 화면 공통 — 강사 '수강 신청 관리'·기관 '수강 관리'.
 * 한 건·일괄 승인/거절, 승인 건 철회와 결과 알림. 페이지는 loadRows() 만 갖춘다 */
import { approveEnrollment, bulkDecideEnrollments, rejectEnrollment } from '@/services/enrollment-api';
import { BULK_DECISION_MAX, notifyBulkDecision, notifyDecision } from '@/utils/enrollment-decision';
import { confirmAndWithdraw } from '@/utils/enrollment-withdraw';

export default {
  data() {
    return {
      checked: [],
      busyId: null,
      bulkSaving: false,
      rejectOpen: false,
      // 'one' = 한 건(rejectTarget), 'bulk' = 고른 신청 전부
      rejectMode: 'one',
      rejectTarget: null,
      bulkResult: null,
      REJECT_NOTES: ['거절하면 신청자는 이 강의를 다시 신청해야 합니다.'],
    };
  },
  computed: {
    rejectTargetLabel() {
      if (this.rejectMode === 'bulk') return `선택한 ${this.checked.length}건`;
      const row = this.rejectTarget;
      return row ? row.applicantLabel || row.userName || '' : '';
    },
  },
  methods: {
    tooManySelected() {
      if (this.checked.length <= BULK_DECISION_MAX) return false;
      this.$q.notify({ type: 'warning', message: `한 번에 ${BULK_DECISION_MAX}건까지 처리할 수 있습니다.`, position: 'top' });
      return true;
    },
    async approve(row) {
      if (this.busyId) return;
      this.busyId = row.id;
      try {
        await approveEnrollment(row.id);
        // 기본 필터(처리 대기)에선 승인한 행이 목록에서 사라진다 — 무엇이 됐는지 알린다
        notifyDecision(this.$q, row.applicantLabel, 'APPROVE');
        await this.loadRows();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.busyId = null;
      }
    },
    /** 승인된 수강 철회(⋮ 메뉴) — 기관 관리자·주강사 */
    async withdraw(row) {
      if (this.busyId) return;
      this.busyId = row.id;
      try {
        if (await confirmAndWithdraw(this.$q, { enrollmentId: row.id, name: row.applicantLabel })) await this.loadRows();
      } finally {
        this.busyId = null;
      }
    },
    async bulkApprove() {
      if (!this.checked.length || this.bulkSaving || this.tooManySelected()) return;
      this.bulkSaving = true;
      try {
        const result = await bulkDecideEnrollments({ enrollmentIds: [...this.checked], decision: 'APPROVE' });
        notifyBulkDecision(this.$q, result, 'APPROVE');
        this.checked = [];
        await this.loadRows();
      } catch (error) {
        // 403 = 다른 기관 건이 섞임. 부분 성공과 달리 아무것도 처리되지 않았다
        this.$q.notify({ type: 'negative', message: error.message || '일괄 승인에 실패했습니다.', position: 'top' });
      } finally {
        this.bulkSaving = false;
      }
    },
    openReject(row) {
      this.rejectMode = 'one';
      this.rejectTarget = row;
      this.rejectOpen = true;
    },
    openBulkReject() {
      if (!this.checked.length || this.tooManySelected()) return;
      this.rejectMode = 'bulk';
      this.rejectOpen = true;
    },
    /** 사유 창의 action — 실패하면 던져서 창이 사유를 들고 남는다 */
    async runReject(reason) {
      if (this.rejectMode === 'bulk') {
        this.bulkResult = await bulkDecideEnrollments({ enrollmentIds: [...this.checked], decision: 'REJECT', reason });
        return;
      }
      await rejectEnrollment(this.rejectTarget.id, { reason });
    },
    async onRejected() {
      if (this.rejectMode === 'bulk') {
        notifyBulkDecision(this.$q, this.bulkResult, 'REJECT');
        this.checked = [];
      } else {
        notifyDecision(this.$q, this.rejectTarget?.applicantLabel, 'REJECT');
      }
      await this.loadRows();
    },
  },
};
