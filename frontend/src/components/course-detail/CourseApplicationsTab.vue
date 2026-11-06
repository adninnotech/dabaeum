<template>
  <section class="applications">
    <AppErrorBanner v-if="errorMessage" :message="errorMessage" />

    <div class="applications__head">
      <h2 class="applications__title">수강 신청자</h2>
      <div v-if="canManage" class="applications__actions">
        <span v-if="checked.length" class="applications__selected">선택 {{ checked.length }}건</span>
        <q-btn
          no-caps
          outline
          color="primary"
          label="일괄 승인"
          :disable="!checked.length || bulkSaving"
          :loading="bulkSaving"
          @click="runBulk('APPROVE')"
        />
        <q-btn
          no-caps
          outline
          color="negative"
          label="일괄 거절"
          :disable="!checked.length || bulkSaving"
          @click="openBulkReject"
        />
        <!-- 대리 수강신청은 관리자·기관만 -->
        <q-btn
          v-if="canProxy"
          no-caps
          unelevated
          color="primary"
          label="대리 수강신청"
          @click="openProxy"
        />
      </div>
      <!-- 보조 강사는 버튼이 없어 왜 못 하는지 몰랐다 -->
      <p v-else class="applications__note">승인·거절은 주강사와 기관 관리자가 처리합니다.</p>
    </div>

    <div class="table-wrap">
      <table class="app-table">
        <!-- 고정 레이아웃에서 잔여 폭은 이메일·연락처 컬럼만 흡수한다 -->
        <colgroup>
          <!-- 체크·관리 칸 40px 은 셀 패딩(10×2)을 빼면 20px 이라 체크박스·"관리" 글자가 옆 칸을 덮었다 -->
          <col v-if="canManage" style="width: 44px" />
          <col style="width: 48px" />
          <col style="width: 130px" />
          <col style="width: auto" />
          <col style="width: 112px" />
          <col style="width: 88px" />
          <col style="width: 112px" />
          <col v-if="canManage" style="width: 52px" />
        </colgroup>
        <thead>
          <tr>
            <!-- 강사는 읽기 전용 — 고를 일이 없으니 체크 칸을 두지 않는다 -->
            <th v-if="canManage" class="col-check">
              <!-- 이 페이지에 고를 수 있는 신청이 없으면 전체 선택도 숨긴다 -->
              <q-checkbox v-if="hasSelectableOnPage" v-model="allChecked" dense aria-label="전체 선택" @update:model-value="toggleAll" />
            </th>
            <th class="col-no">번호</th>
            <th class="col-member">회원 정보</th>
            <th class="col-contact">이메일 · 연락처</th>
            <th class="col-date">신청 일시</th>
            <th class="col-badge">신청 상태</th>
            <th class="col-date">처리 일시</th>
            <th v-if="canManage" class="col-kebab">관리</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td :colspan="colspan" class="app-table__state">
              <q-spinner color="primary" size="24px" />
            </td>
          </tr>
          <tr v-else-if="!pagedRows.length">
            <td :colspan="colspan">
              <AppEmptyState compact description="수강 신청 내역이 없습니다." />
            </td>
          </tr>
          <tr v-for="(row, i) in pagedRows" v-else :key="row.id">
            <td v-if="canManage" class="col-check">
              <!-- 이미 처리된 신청은 고를 수 없으니 꺼진 체크박스 대신 빈칸으로 둔다 -->
              <q-checkbox
                v-if="row.selectable"
                v-model="checked"
                :val="row.id"
                dense
                :aria-label="`${row.userName} 선택`"
              />
            </td>
            <td class="col-no">{{ (page - 1) * pageSize + i + 1 }}</td>
            <td class="col-member">
              <div class="cell-strong">{{ row.userName }}</div>
              <div class="cell-sub">{{ row.birthGender }}</div>
            </td>
            <td class="col-contact">
              <div class="cell-sub">{{ row.email }}</div>
              <div class="cell-sub">{{ row.phone }}</div>
            </td>
            <td class="col-date">{{ row.appliedAt }}</td>
            <td class="col-badge">
              <StatusBadge :status="row.status" :label="row.statusLabel" :soft="isWaitlistBadgeSoft(row)" />
            </td>
            <td class="col-date">{{ row.processedAt }}</td>
            <td v-if="canManage" class="col-kebab">
              <!-- 처리가 끝난 신청(거절·취소 등)은 할 일이 없다 — 빈 메뉴를 여는 ⋮ 대신 비운다 -->
              <q-btn
                v-if="SELECTABLE_STATUSES.has(row.status) || CANCELLABLE_STATUSES.has(row.status) || WITHDRAWABLE.has(row.status)"
                aria-label="신청 메뉴"
                flat
                round
                dense
                icon="more_vert"
                size="sm"
              >
                <q-menu>
                  <q-list style="min-width: 120px">
                    <!-- 승인·거절은 신청·대기만, 취소는 끝나지 않은 신청만 -->
                    <!-- 꺼진 항목을 늘어놓지 않고 지금 할 수 있는 것만 — 승인·거절은 신청·대기만, 취소는 끝나지 않은 신청만 -->
                    <q-item v-if="SELECTABLE_STATUSES.has(row.status)" v-close-popup clickable @click="act(row, 'approve')">
                      <q-item-section>승인</q-item-section>
                    </q-item>
                    <q-item v-if="SELECTABLE_STATUSES.has(row.status)" v-close-popup clickable @click="act(row, 'reject')">
                      <q-item-section>거절</q-item-section>
                    </q-item>
                    <!-- 되돌릴 수 없는 취소는 승인·거절과 선으로 띄운다 -->
                    <q-separator
                      v-if="
                        SELECTABLE_STATUSES.has(row.status) && CANCELLABLE_STATUSES.has(row.status)
                      "
                    />
                    <q-item
                      v-if="CANCELLABLE_STATUSES.has(row.status)"
                      v-close-popup
                      clickable
                      class="text-negative"
                      @click="act(row, 'cancel')"
                    >
                      <q-item-section>신청 취소</q-item-section>
                    </q-item>
                    <!-- 승인 건은 신청 취소가 아니라 수강 철회(서버가 /cancel 을 거부한다) -->
                    <q-item
                      v-if="WITHDRAWABLE.has(row.status)"
                      v-close-popup
                      clickable
                      class="text-negative"
                      @click="withdraw(row)"
                    >
                      <q-item-section>수강 철회</q-item-section>
                    </q-item>
                  </q-list>
                </q-menu>
              </q-btn>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <ConsolePagination v-model="page" :total="applicationRows.length" :page-size="pageSize" />

    <CorrectionReasonDialog
      v-model="rowRejectOpen"
      title="수강신청 거절"
      :target="rejectTarget?.userName && rejectTarget.userName !== '-' ? rejectTarget.userName : ''"
      confirm-label="거절"
      reason-label="거절 사유"
      reason-hint="수강신청 기록에 남습니다."
      :action="runRowReject"
      @done="onRowRejected"
    />

    <!-- 일괄 거절 — 직접 만든 창은 결과 전에 닫히며 사유를 지워, 실패하면 쓴 사유가 사라졌다.
         한 건 거절과 같은 창: 실패하면 닫히지 않고 서버 메시지를 보인다 -->
    <CorrectionReasonDialog
      v-model="showRejectReason"
      title="수강신청 일괄 거절"
      :target="`선택한 ${bulkRejectCount}건`"
      confirm-label="거절"
      reason-label="거절 사유"
      reason-hint="선택한 신청 모두에 같은 사유가 남습니다."
      :action="runBulkReject"
      @done="onBulkRejected"
    />

    <q-dialog v-model="showProxy">
      <q-card style="width: 420px; max-width: 94vw">
        <q-card-section class="text-h6">대리 수강신청</q-card-section>
        <q-card-section>
          <!-- 사용자 UUID 를 직접 치던 입력 → 이름·이메일로 찾아 고른다(관리자 콘솔 전용 탭) -->
          <UserSearchSelect v-model="proxyUserId" label="신청할 학습자 *" />
        </q-card-section>
        <q-card-actions align="right">
          <q-btn v-close-popup flat no-caps label="취소" />
          <q-btn
            unelevated
            no-caps
            color="primary"
            :loading="saving"
            label="신청"
            @click="submitProxy"
          />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </section>
</template>

<script>
import CorrectionReasonDialog from '@/components/console/CorrectionReasonDialog.vue';
import UserSearchSelect from '@/components/console/UserSearchSelect.vue';
import { confirmDialog } from '@/utils/confirm-dialog';
import AppEmptyState from '@/components/AppEmptyState.vue';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import ConsolePagination from '@/components/ConsolePagination.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import {
  approveEnrollment,
  bulkDecideEnrollments,
  cancelEnrollment,
  createProxyEnrollment,
  listCourseEnrollments,
  rejectEnrollment,
} from '@/services/enrollment-api';
import { formatDateKst, formatDateMinuteKst } from '@/utils/datetime';
import { GENDER_LABEL } from '@/utils/status';
import {
  BULK_DECISION_MAX,
  DECIDABLE_STATUSES,
  enrollmentBadgeLabel,
  isWaitlistBadgeSoft,
  notifyBulkDecision,
  notifyDecision,
} from '@/utils/enrollment-decision';
import { confirmAndWithdraw, WITHDRAWABLE_STATUSES } from '@/utils/enrollment-withdraw';

/** 체크박스로 일괄 승인·거절이 가능한 상태만 */
const SELECTABLE_STATUSES = new Set(DECIDABLE_STATUSES);

// 신청 취소는 승인 전만 — 승인 건은 수강 철회(WITHDRAWABLE)
const CANCELLABLE_STATUSES = new Set(['APPLIED', 'WAITLISTED']);
const WITHDRAWABLE = new Set(WITHDRAWABLE_STATUSES);

const BULK_MAX = BULK_DECISION_MAX;

const ACTIONS = {
  approve: approveEnrollment,
  cancel: cancelEnrollment,
};
// 거절은 사유가 필수라(EnrollmentRejectionRequest.reason) 사유 창에서 따로 부른다

export default {
  name: 'CourseApplicationsTab',
  components: { AppEmptyState, AppErrorBanner, ConsolePagination, CorrectionReasonDialog, StatusBadge, UserSearchSelect },
  props: {
    course: { type: Object, default: null },
    console: { type: String, default: 'admin' },
    /** 강사 콘솔에서 이 강의 주강사인지 — 페이지가 배정 정보로 판단해 넘긴다 */
    canDecide: { type: Boolean, default: false },
  },
  data() {
    return {
      rows: [],
      loading: false,
      saving: false,
      bulkSaving: false,
      errorMessage: '',
      showProxy: false,
      proxyUserId: null,
      showRejectReason: false,
      rowRejectOpen: false,
      SELECTABLE_STATUSES,
      CANCELLABLE_STATUSES,
      WITHDRAWABLE,
      rejectTarget: null,
      bulkRejectCount: 0,
      bulkRejectResult: null,
      checked: [],
      allChecked: false,
      page: 1,
      pageSize: 10,
    };
  },
  computed: {
    /** 승인·거절·취소 — 관리자·기관, 그리고 이 강의의 주강사(서버 규칙). 보조 강사는 읽기 전용 */
    canManage() {
      return this.console !== 'instructor' || this.canDecide;
    },
    /** 대리 수강신청은 관리자·기관만 */
    canProxy() {
      return this.console !== 'instructor';
    },
    courseId() {
      return this.course?.id || this.$route.params.courseId;
    },
    colspan() {
      return this.canManage ? 8 : 6;
    },
    /**
     * Enrollment(강좌별 경로) 응답을 화면 행으로 매핑한다.
     * ⚠ waitlistPosition은 조회 시 서버가 appliedAt 순으로 계산하는 값이라 캐시하지 않는다 —
     * 매번 load()로 새로 받은 row를 그대로 읽는다.
     */
    applicationRows() {
      return this.rows.map((row) => ({
        id: row.id,
        userId: row.userId,
        userName: row.userName || '-',
        birthGender:
          [
            row.birthDate ? formatDateKst(row.birthDate) : '',
            row.gender ? GENDER_LABEL[row.gender] : '',
          ]
            .filter(Boolean)
            .join(' ') || '-',
        email: row.email || '-',
        phone: row.phone || '-',
        status: row.status,
        statusLabel: enrollmentBadgeLabel(row),
        appliedAt: formatDateMinuteKst(row.appliedAt),
        // 처리일시 — 승인·거절·취소·탈퇴를 서버가 processedAt 하나로 합쳐 준다
        processedAt: row.processedAt ? formatDateMinuteKst(row.processedAt) : '-',
        selectable: SELECTABLE_STATUSES.has(row.status),
      }));
    },
    pagedRows() {
      const start = (this.page - 1) * this.pageSize;
      return this.applicationRows.slice(start, start + this.pageSize);
    },
    hasSelectableOnPage() {
      return this.pagedRows.some((row) => row.selectable);
    },
  },
  created() {
    this.load();
  },
  methods: {
    isWaitlistBadgeSoft,
    formatDateKst,
    toggleAll(value) {
      const selectableIds = this.pagedRows.filter((row) => row.selectable).map((row) => row.id);
      this.checked = value ? selectableIds : [];
    },
    async load() {
      if (!this.courseId) return;
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await listCourseEnrollments(this.courseId, { page: 0, size: 100 });
        this.rows = Array.isArray(result.data) ? result.data : [];
        this.checked = [];
        this.page = 1;
      } catch (error) {
        this.errorMessage = error.message || '수강 신청을 불러오지 못했습니다.';
        this.rows = [];
      } finally {
        this.loading = false;
      }
    },
    async act(row, action) {
      if (action === 'reject') {
        this.rejectTarget = row;
        this.rowRejectOpen = true;
        return;
      }
      if (action === 'cancel') {
        const ok = await confirmDialog(this.$q, {
          title: '수강신청 취소',
          message: `${row.userName && row.userName !== '-' ? row.userName : '이 신청자'}의 수강신청을 취소합니다. 되돌릴 수 없습니다.`,
          ok: '신청 취소',
          color: 'negative',
          cancel: '닫기',
        });
        if (!ok) return;
      }
      try {
        await ACTIONS[action](row.id);
        if (action === 'approve') notifyDecision(this.$q, row.userName, 'APPROVE');
        else
          this.$q.notify({
            type: 'positive',
            message: '수강신청을 취소했습니다.',
            position: 'top',
          });
        await this.load();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      }
    },
    async withdraw(row) {
      if (await confirmAndWithdraw(this.$q, { enrollmentId: row.id, name: row.userName })) await this.load();
    },
    runRowReject(reason) {
      return rejectEnrollment(this.rejectTarget.id, { reason });
    },
    async onRowRejected() {
      notifyDecision(this.$q, this.rejectTarget?.userName, 'REJECT');
      await this.load();
    },
    openBulkReject() {
      if (!this.checked.length) return;
      if (this.checked.length > BULK_MAX) {
        this.$q.notify({
          type: 'warning',
          message: `한 번에 ${BULK_MAX}건까지 처리할 수 있습니다. 선택을 줄여 주세요.`,
          position: 'top',
        });
        return;
      }
      this.bulkRejectCount = this.checked.length;
      this.showRejectReason = true;
    },
    /** 사유 창의 action — 실패하면 던져서 창이 사유를 들고 남는다 */
    async runBulkReject(reason) {
      this.bulkRejectResult = await bulkDecideEnrollments({
        enrollmentIds: [...this.checked],
        decision: 'REJECT',
        reason,
      });
    },
    async onBulkRejected() {
      notifyBulkDecision(this.$q, this.bulkRejectResult, 'REJECT');
      this.checked = [];
      await this.load();
    },
    async runBulk(decision, reason) {
      if (!this.checked.length) return;
      if (this.checked.length > BULK_MAX) {
        this.$q.notify({
          type: 'warning',
          message: `한 번에 ${BULK_MAX}건까지 처리할 수 있습니다. 선택을 줄여 주세요.`,
          position: 'top',
        });
        return;
      }
      this.bulkSaving = true;
      try {
        const result = await bulkDecideEnrollments({
          enrollmentIds: [...this.checked],
          decision,
          reason,
        });
        notifyBulkDecision(this.$q, result, decision);
        this.checked = [];
        // 상태·대기 순번이 서버에서 바뀌므로 반드시 다시 불러온다
        await this.load();
      } catch (error) {
        // 403 = 다른 기관 건이 섞임. 부분 성공과 달리 아무것도 처리되지 않았다
        this.$q.notify({
          type: 'negative',
          message: error.message || '일괄 처리에 실패했습니다.',
          position: 'top',
        });
      } finally {
        this.bulkSaving = false;
      }
    },
    openProxy() {
      this.proxyUserId = null;
      this.showProxy = true;
    },
    async submitProxy() {
      if (!this.proxyUserId) {
        this.$q.notify({ type: 'warning', message: '신청할 학습자를 골라 주세요.', position: 'top' });
        return;
      }
      this.saving = true;
      try {
        await createProxyEnrollment(this.courseId, { userId: this.proxyUserId });
        this.$q.notify({ type: 'positive', message: '대리 신청했습니다.', position: 'top' });
        this.showProxy = false;
        await this.load();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.applications {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.applications__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.applications__note {
  margin: 0;
  font-size: 13px;
  color: var(--db-text-sub);
}

.applications__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--db-text-title);
}

.applications__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.applications__selected {
  font-size: 14px;
  color: var(--db-text-sub);
}

.table-wrap {
  border: 1px solid var(--db-border);
  border-radius: 4px;
  overflow-x: auto;
}

/* 고정 레이아웃 — 이메일·연락처 칸이 0 가까이 눌려 글자가 옆 칸을 덮지 않게 최소 폭을 둔다.
 * 강의 상세는 오른쪽 강사 정보 패널이 있어 노트북(창 1500px)에서 표 자리가 약 750px 이다 — 그 안에
 * 스크롤 없이 들어가고, 더 좁을 때만 표가 가로 스크롤된다. 고정 칸 합 586 + 이메일 최소 약 114 */
.app-table {
  width: 100%;
  min-width: 700px;
  table-layout: fixed;
  border-collapse: collapse;
}

.app-table th {
  height: 52px;
  padding: 0 10px;
  background: var(--db-bg-table-head);
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-title);
  white-space: nowrap;
}

.app-table td {
  padding: 13px 10px;
  border-top: 1px solid var(--db-border);
  font-size: 16px;
  font-weight: 400;
  color: var(--db-text-title);
  vertical-align: middle;
}

/* 폭은 <colgroup>이 잡는다 — 여기는 정렬만 */
.app-table .col-date {
  font-size: 14px;
}

.app-table .col-check,
.app-table .col-kebab {
  padding-right: 6px;
  padding-left: 6px;
}

.col-check,
.col-kebab,
.col-no,
.col-badge {
  text-align: center;
}

.cell-strong {
  font-size: 16px;
  font-weight: 700;
}

.cell-sub {
  overflow-wrap: anywhere;
  font-size: 12px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.app-table__state {
  height: 80px;
  text-align: center;
  color: var(--db-text-sub);
}
</style>
