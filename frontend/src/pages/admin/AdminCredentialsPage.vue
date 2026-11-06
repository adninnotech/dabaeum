<template>
  <q-page padding>
    <AppPageHeader title="수료증 관리">
      <template #actions>
        <q-btn outline color="primary" icon="refresh" label="새로고침" :disable="!hasTarget" :loading="loading" @click="reload" />
      </template>
    </AppPageHeader>

    <AppErrorBanner :message="errorMessage" />

    <div class="row q-col-gutter-sm q-mb-sm items-start">
      <div class="col-12 col-md-auto">
        <!-- 강좌 단위로 한꺼번에 보는 것이 기본, 한 학습자의 모든 수료증을 보려면 학습자별 -->
        <q-btn-toggle
          :model-value="mode"
          no-caps
          unelevated
          toggle-color="primary"
          :options="MODE_OPTIONS"
          @update:model-value="onModeChange"
        />
      </div>
      <div class="col-12 col-sm-8 col-md-5">
        <q-select
          v-if="mode === 'course'"
          :model-value="courseId"
          outlined
          dense
          emit-value
          map-options
          use-input
          input-debounce="300"
          label="강의"
          hint="강의명·기관명으로 찾습니다."
          :options="courseOptions"
          :loading="coursesLoading"
          @filter="onCourseFilter"
          @update:model-value="onCoursePicked"
        >
          <template #no-option>
            <q-item><q-item-section class="text-grey-7">맞는 강의가 없습니다.</q-item-section></q-item>
          </template>
        </q-select>
        <UserSearchSelect
          v-else
          :model-value="userId"
          label="학습자 검색"
          @update:model-value="onUserPicked"
          @select="onUserSelected"
        />
      </div>
      <div class="col-12 col-sm-4 col-md-3">
        <q-select
          v-model="statusFilter"
          outlined
          dense
          emit-value
          map-options
          label="상태"
          :options="statusOptions"
        />
      </div>
    </div>

    <div v-if="hasTarget && rows.length" class="row q-gutter-sm q-mb-md text-caption text-grey-8">
      <span v-for="item in statusCounts" :key="item.value">{{ item.label }} {{ item.count }}</span>
    </div>
    <div v-if="partialFailCount" class="text-caption text-warning q-mb-sm">
      수강생 {{ partialFailCount }}명의 수료증을 불러오지 못했습니다. 새로고침해 주세요.
    </div>

    <q-table
      flat
      bordered
      row-key="key"
      :rows="filteredRows"
      :columns="columns"
      :visible-columns="visibleColumns"
      :loading="loading"
      :pagination="{ rowsPerPage: 20 }"
      :grid="$q.screen.xs"
      :no-data-label="noDataLabel"
    >
      <template #body-cell-status="props">
        <q-td :props="props">
          <StatusBadge v-if="props.row.status === NOT_ISSUED" status="PENDING" label="미발급" />
          <StatusBadge v-else :status="props.row.status" />
        </q-td>
      </template>
      <template #body-cell-actions="props">
        <q-td :props="props">
          <CredentialRowActions
            :row="props.row"
            :not-issued="NOT_ISSUED"
            :busy="actionId === props.row.id"
            :issuing="issuingKey === props.row.key"
            :issue-locked="!!issuingKey && issuingKey !== props.row.key"
            :failure-text="failureReason(props.row) || ''"
            @reissue="(row) => openAction('reissue', row)"
            @revoke="(row) => openAction('revoke', row)"
            @badge="openIssue"
            @issue="startIssue"
          />
        </q-td>
      </template>
      <!-- 폰(xs): 표가 옆으로 밀려 상태·처리 칸이 화면 밖에 있었다 — 수료증 한 건을 카드 한 장으로 -->
      <template #item="props">
        <div class="col-12">
          <div class="credential-card">
            <div class="credential-card__head">
              <span class="credential-card__title">
                {{ mode === 'course' ? props.row.learnerLabel : props.row.courseTitle || '-' }}
              </span>
              <StatusBadge v-if="props.row.status === NOT_ISSUED" status="PENDING" label="미발급" />
              <StatusBadge v-else :status="props.row.status" />
            </div>
            <div class="credential-card__meta">{{ props.row.credentialNo || '번호 없음' }}</div>
            <div v-if="props.row.issuedAt" class="credential-card__meta">발급 {{ qFormatDateTimeKst(props.row.issuedAt) }}</div>
            <!-- 할 일이 없는 대체됨·폐기 카드엔 '-'만 홀로 남았다 — 줄째 뺀다 -->
            <div v-if="hasCardAction(props.row)" class="credential-card__actions">
              <CredentialRowActions
                :row="props.row"
                :not-issued="NOT_ISSUED"
                :busy="actionId === props.row.id"
                :issuing="issuingKey === props.row.key"
                :issue-locked="!!issuingKey && issuingKey !== props.row.key"
                :failure-text="failureReason(props.row) || ''"
                @reissue="(row) => openAction('reissue', row)"
                @revoke="(row) => openAction('revoke', row)"
                @badge="openIssue"
                @issue="startIssue"
              />
            </div>
          </div>
        </div>
      </template>
    </q-table>

    <!-- 폐기·재발급: 사유가 필수라 고정 문구 대신 운영자가 적는다. 재발급 유효기간은 선택(비우면 서버 기본) -->
    <CorrectionReasonDialog
      v-model="actionOpen"
      :title="actionKind === 'revoke' ? '수료증 폐기' : '수료증 재발급'"
      :target="actionTargetLabel"
      :notes="actionKind === 'revoke' ? REVOKE_NOTES : REISSUE_NOTES"
      :confirm-label="actionKind === 'revoke' ? '폐기' : '재발급'"
      :confirm-color="actionKind === 'revoke' ? 'negative' : 'primary'"
      :reason-label="actionKind === 'revoke' ? '폐기 사유' : '재발급 사유'"
      reason-hint="수료증 이력에 남습니다."
      :action="runAction"
      @done="onActionDone"
    >
      <AppDateInput
        v-if="actionKind === 'reissue'"
        v-model="reissueValidUntil"
        outlined
        dense
        stack-label
        label="유효기간 (선택)"
        clearable
        :min="minValidUntil"
        :hint="VALID_UNTIL_HINT"
        :error="!!reissueValidUntilError"
        :error-message="reissueValidUntilError"
      />
    </CorrectionReasonDialog>

    <!-- 수동 배지 발급 — 정의를 골라 준다(설계서 §11.2). 이미 가진 정의면 서버가 기존 배지를 돌려준다 -->
    <q-dialog v-model="issueOpen">
      <q-card style="width: 420px; max-width: 92vw">
        <q-card-section class="text-h6">배지 발급</q-card-section>
        <q-separator />
        <q-card-section class="column q-gutter-sm">
          <q-select
            v-model="issueDefinitionId"
            outlined
            dense
            emit-value
            map-options
            :options="definitionOptions"
            :loading="definitionsLoading"
            label="배지 정의 *"
          >
            <template #no-option>
              <q-item><q-item-section class="text-grey-7">사용 중인 배지 정의가 없습니다.</q-item-section></q-item>
            </template>
          </q-select>
          <div class="text-caption text-grey-7">
            사용 중인 정의만 고를 수 있습니다. 이미 받은 배지면 새로 만들지 않고 기존 배지를 그대로 둡니다.
          </div>
        </q-card-section>
        <q-card-actions align="right">
          <q-btn v-close-popup flat no-caps label="취소" />
          <q-btn
            unelevated
            no-caps
            color="primary"
            label="발급"
            :disable="!issueDefinitionId"
            :loading="!!actionId"
            @click="issueBadge"
          />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </q-page>
</template>

<script>
import CredentialRowActions from '@/components/admin/CredentialRowActions.vue';
import AppDateInput from '@/components/AppDateInput.vue';
import { qFormatDateTimeKst } from '@/utils/datetime';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import AppPageHeader from '@/components/AppPageHeader.vue';
import CorrectionReasonDialog from '@/components/console/CorrectionReasonDialog.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import UserSearchSelect from '@/components/console/UserSearchSelect.vue';
import { issueLearningBadge, reissueCredential, revokeCredential } from '@/services/credential-api';
import { listAdminBadgeDefinitions } from '@/services/badge-api';
import { listCourses } from '@/services/course-api';
import {
  loadCourseCredentialRows,
  loadLearnerCredentialRows,
  NOT_ISSUED,
  resolveCompletionId,
} from '@/services/credential-course-rows';
import { askCredentialValidity, issueCredentialAndWait } from '@/services/credential-issue';
import { settledNotice, waitForCredentialSettled } from '@/services/credential-status';
import { credentialFailureReason } from '@/utils/credential-failure';
import { endOfDayKst, minValidUntilDate, VALID_UNTIL_HINT, validUntilError } from '@/utils/credential-validity';
import { formatUserDisplayName } from '@/utils/user-display';

/* 폐기된 수료증은 서버가 재발급을 받지 않는다(재발급은 발급완료일 때만) — '다시 필요하면 재발급'이라던 안내가 막다른 길이었다 */
const REVOKE_NOTES = [
  '폐기하면 이 수료증은 학습자 지갑과 공유 링크에서 검증할 때 무효로 나옵니다.',
  '폐기는 되돌릴 수 없고, 폐기한 수료증은 다시 발급할 수 없습니다. 내용만 고칠 거라면 폐기 대신 [재발급]을 쓰세요.',
];
const REISSUE_NOTES = ['새 수료증이 발급되고, 기존 수료증은 대체됨으로 바뀝니다.'];
const MODE_OPTIONS = [
  { label: '강의별', value: 'course' },
  { label: '학습자별', value: 'learner' },
];
// 발급중·대기는 잠깐 거치는 상태라 '전체'에서만 보인다
const BASE_STATUS_OPTIONS = [
  { label: '전체', value: '' },
  { label: '발급', value: 'ISSUED' },
  { label: '발급 실패', value: 'FAILED' },
  { label: '폐기', value: 'REVOKED' },
  { label: '대체됨', value: 'SUPERSEDED' },
];
// 이수 확정·미발급은 과정의 이수 기록을 함께 읽는 강좌별 보기에서만 알 수 있다
const NOT_ISSUED_OPTION = { label: '미발급(이수 확정)', value: NOT_ISSUED };
const COURSE_PAGE_SIZE = 50;

function toCourseOption(course) {
  return { label: [course.courseCode, course.title].filter(Boolean).join(' ') || '이름 없는 강의', value: course.id, title: course.title };
}

export default {
  components: { AppErrorBanner, AppPageHeader, CorrectionReasonDialog, StatusBadge, UserSearchSelect, AppDateInput, CredentialRowActions },
  name: 'AdminCredentialsPage',
  data() {
    return {
      MODE_OPTIONS,
      NOT_ISSUED,
      mode: 'course',
      courseId: null,
      courseOptions: [],
      coursesLoading: false,
      userId: null,
      learnerLabel: '',
      statusFilter: '',
      rows: [],
      partialFailCount: 0,
      loading: false,
      // 늦게 끝난 이전 조회가 새 선택의 결과를 덮지 않게 조회마다 번호를 붙인다
      loadSeq: 0,
      issuingKey: null,
      actionId: null,
      errorMessage: '',
      issueOpen: false,
      actionOpen: false,
      actionKind: 'revoke',
      actionTarget: null,
      reissueValidUntil: '',
      VALID_UNTIL_HINT,
      REVOKE_NOTES,
      REISSUE_NOTES,
      issueTarget: null,
      issueDefinitionId: null,
      definitionOptions: [],
      definitionsLoading: false,
      columns: [
        { name: 'learner', label: '학습자', field: 'learnerLabel', align: 'left' },
        { name: 'credentialNo', label: '수료증 번호', field: (r) => r.credentialNo || '-', align: 'left' },
        { name: 'courseTitle', label: '강의', field: (r) => r.courseTitle || '-', align: 'left' },
        { name: 'status', label: '상태', field: 'status', align: 'center' },
        { name: 'issuedAt', label: '발급일', field: 'issuedAt', align: 'left', format: qFormatDateTimeKst },
        { name: 'actions', label: '처리', field: 'actions', align: 'right' },
      ],
    };
  },
  computed: {
    hasTarget() {
      return this.mode === 'course' ? !!this.courseId : !!this.userId;
    },
    statusOptions() {
      return this.mode === 'course' ? [...BASE_STATUS_OPTIONS, NOT_ISSUED_OPTION] : BASE_STATUS_OPTIONS;
    },
    /** 강좌별은 강좌가 하나라 강좌 칸 대신 학습자 칸, 학습자별은 그 반대 */
    visibleColumns() {
      const hidden = this.mode === 'course' ? 'courseTitle' : 'learner';
      return this.columns.map((col) => col.name).filter((name) => name !== hidden);
    },
    filteredRows() {
      if (!this.statusFilter) return this.rows;
      return this.rows.filter((row) => row.status === this.statusFilter);
    },
    statusCounts() {
      return this.statusOptions
        .filter((opt) => opt.value)
        .map((opt) => ({ ...opt, count: this.rows.filter((row) => row.status === opt.value).length }))
        .filter((item) => item.count > 0);
    },
    noDataLabel() {
      if (!this.hasTarget) {
        return this.mode === 'course' ? '강의를 고르면 수료증 발급 현황이 보입니다.' : '학습자를 검색해 고르면 수료증이 보입니다.';
      }
      return this.statusFilter ? '이 상태의 수료증이 없습니다.' : '발급된 수료증이 없습니다.';
    },
    /** 날짜 선택기는 내일부터 — 오늘을 고르면 발급 당일 밤 만료된다 */
    minValidUntil() {
      return minValidUntilDate();
    },
    reissueValidUntilError() {
      return validUntilError(this.reissueValidUntil);
    },
    actionTargetLabel() {
      const row = this.actionTarget;
      if (!row) return '';
      return [row.learnerLabel, row.credentialNo, row.courseTitle].filter(Boolean).join(' · ');
    },
  },
  mounted() {
    this.searchCourses('');
  },
  methods: {
    hasCardAction(row) {
      return ['ISSUED', 'FAILED', NOT_ISSUED].includes(row.status);
    },
    qFormatDateTimeKst,
    failureReason(row) {
      return credentialFailureReason(row.failureCode);
    },
    async searchCourses(q) {
      this.coursesLoading = true;
      try {
        const result = await listCourses({ page: 0, size: COURSE_PAGE_SIZE, q: q || undefined });
        const found = (Array.isArray(result.data) ? result.data : []).map(toCourseOption);
        // 검색어를 바꿔도 고른 강좌 이름이 사라지지 않게 남긴다
        const selected = this.courseOptions.find((opt) => opt.value === this.courseId);
        this.courseOptions = selected && !found.some((opt) => opt.value === selected.value) ? [selected, ...found] : found;
      } catch (error) {
        this.errorMessage = error.message || '강의 목록을 불러오지 못했습니다.';
      } finally {
        this.coursesLoading = false;
      }
    },
    onCourseFilter(value, update) {
      this.searchCourses(String(value || '').trim()).finally(() => update());
    },
    onModeChange(mode) {
      this.mode = mode;
      this.statusFilter = '';
      this.rows = [];
      this.partialFailCount = 0;
      this.errorMessage = '';
      // 진행 중이던 조회 결과는 버린다 — 새 조회가 없으면 로딩도 여기서 끈다
      this.loadSeq += 1;
      this.loading = false;
      if (this.hasTarget) this.reload();
    },
    onCoursePicked(courseId) {
      this.courseId = courseId;
      this.rows = [];
      if (courseId) this.reload();
    },
    onUserPicked(userId) {
      this.userId = userId;
      this.rows = [];
      if (!userId) this.learnerLabel = '';
    },
    /** UserSearchSelect 는 update 다음에 select 를 보낸다 — 이름을 받은 뒤 조회해야 학습자 칸이 채워진다 */
    onUserSelected(user) {
      this.learnerLabel = user ? formatUserDisplayName(user) : '';
      if (this.userId) this.reload();
    },
    async reload() {
      if (!this.hasTarget) return;
      this.loadSeq += 1;
      const seq = this.loadSeq;
      this.loading = true;
      this.errorMessage = '';
      try {
        const next = await this.fetchRows();
        if (seq !== this.loadSeq) return;
        this.rows = next.rows;
        this.partialFailCount = next.failedCount;
      } catch (error) {
        if (seq !== this.loadSeq) return;
        this.errorMessage = error.message || '수료증 목록을 불러오지 못했습니다.';
        this.rows = [];
        this.partialFailCount = 0;
      } finally {
        if (seq === this.loadSeq) this.loading = false;
      }
    },
    async fetchRows() {
      if (this.mode === 'course') {
        const course = this.courseOptions.find((opt) => opt.value === this.courseId);
        return loadCourseCredentialRows({ id: this.courseId, title: course?.title || '' });
      }
      const rows = await loadLearnerCredentialRows(this.userId, this.learnerLabel);
      return { rows, failedCount: 0 };
    },
    /** 미발급 [발급]·발급 실패 [다시 발급] — 이수 처리 화면과 같은 발급 API·유효기간 창·결과 확인을 쓴다 */
    async startIssue(row, kind) {
      this.issuingKey = row.key;
      try {
        const completionId = await resolveCompletionId(row);
        const date = await askCredentialValidity(this.$q, {
          title: kind === 'retry' ? '수료증 다시 발급' : '수료증 발급',
          message: `${[row.learnerLabel, row.courseTitle].filter(Boolean).join(' · ')} 수료증을 발급합니다. 발급한 뒤에는 이수 확정을 취소할 수 없습니다.`,
        });
        if (date === null) return;
        const settled = await issueCredentialAndWait(completionId, date);
        this.$q.notify({ ...settledNotice('issue', settled), position: 'top' });
        await this.reload();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '수료증을 발급하지 못했습니다.', position: 'top' });
      } finally {
        this.issuingKey = null;
      }
    },
    openAction(kind, row) {
      this.actionKind = kind;
      this.actionTarget = row;
      this.reissueValidUntil = '';
      this.actionOpen = true;
    },
    runAction(reason) {
      const id = this.actionTarget.id;
      if (this.actionKind === 'revoke') return revokeCredential(id, { reason });
      // 오늘·지난 날짜는 보내지 않는다 — 창이 오류 문구를 띄우고 닫히지 않는다
      if (this.reissueValidUntilError) return Promise.reject(new Error(this.reissueValidUntilError));
      return reissueCredential(id, { reason, validUntil: endOfDayKst(this.reissueValidUntil) });
    },
    /** result = 202 응답(폐기: 그 수료증, 재발급: 새 수료증) — 끝 상태를 확인한 뒤 알리고 목록을 다시 읽는다 */
    async onActionDone(result) {
      const action = this.actionKind;
      this.loading = true;
      try {
        const settled = await waitForCredentialSettled(result, action === 'revoke' ? 'revoke' : 'issue');
        const notice = settledNotice(action, settled);
        // 누구 수료증인지 — '수료증을 폐기했습니다'만으로는 대상을 몰랐다
        const who = this.actionTargetLabel;
        this.$q.notify({ ...notice, message: who ? `${who} · ${notice.message}` : notice.message, position: 'top' });
      } catch (error) {
        this.$q.notify({ type: 'warning', message: `요청은 접수됐지만 결과를 확인하지 못했습니다: ${error.message || ''}`.trim(), position: 'top' });
      } finally {
        this.loading = false;
      }
      // 폐기·대체된 행이 [폐기]·[재발급] 버튼을 그대로 달고 남지 않게 다시 읽는다
      await this.reload();
    },
    async openIssue(row) {
      this.issueTarget = row;
      this.issueDefinitionId = null;
      this.issueOpen = true;
      this.definitionsLoading = true;
      try {
        const definitions = await listAdminBadgeDefinitions();
        this.definitionOptions = definitions
          // 단계 배지(TIERED)는 수료 확정 때 자동으로만 오르내린다 — 수동 발급은 서버가 422
          .filter((item) => item.active && item.rule?.type !== 'TIERED')
          // 내부 정의 코드는 운영자에게 뜻이 없다 — 이름만 보인다
          .map((item) => ({ label: item.name || '이름 없는 배지', value: item.id }));
      } catch (error) {
        this.definitionOptions = [];
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.definitionsLoading = false;
      }
    },
    async issueBadge() {
      const row = this.issueTarget;
      if (!row || !this.issueDefinitionId) return;
      this.actionId = row.id;
      try {
        await issueLearningBadge(row.id, {
          definitionId: this.issueDefinitionId,
          courseId: row.courseId || null,
        });
        this.issueOpen = false;
        const badgeName = this.definitionOptions.find((item) => item.value === this.issueDefinitionId)?.label || '배지';
        this.$q.notify({
          type: 'positive',
          message: `${row.learnerLabel || '학습자'}에게 '${badgeName}'를 발급했습니다.`,
          position: 'top',
        });
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message:
            error?.status === 422
              ? '사용 중지된 배지 정의입니다. 다른 정의를 골라 주세요.'
              : error.message,
          position: 'top',
        });
      } finally {
        this.actionId = null;
      }
    },
  },
};
</script>

<style scoped>


.credential-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 4px;
  border-bottom: 1px solid var(--db-border);
}

.credential-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.credential-card__title {
  min-width: 0;
  overflow: hidden;
  font-size: 15px;
  font-weight: 700;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--db-text-title);
}

.credential-card__meta {
  overflow: hidden;
  font-size: 12px;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--db-text-sub);
}

.credential-card__actions {
  display: flex;
  justify-content: flex-end;
}

.muted-note {
  font-size: 12px;
  color: var(--db-text-sub);
}
</style>
