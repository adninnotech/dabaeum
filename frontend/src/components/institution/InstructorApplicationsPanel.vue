<template>
  <!-- 강사 신청 — 상태 필터·목록(폰 카드)·상세(그 자리에서 승인·거절)·거절 사유.
       행(카드)을 누르면 상세 — 따로 있던 [상세] 단추는 뺐다 -->
  <div>
    <AppErrorBanner :message="errorMessage">
      <template #action>
        <q-btn flat dense no-caps color="negative" label="다시 시도" @click="load" />
      </template>
    </AppErrorBanner>
    <q-select
      :model-value="status"
      outlined
      dense
      emit-value
      map-options
      class="status-select"
      :options="statusOptions"
      label="상태"
      @update:model-value="$emit('update:status', $event)"
    />
    <p v-if="cappedText" class="capped-note">{{ cappedText }}</p>
    <q-table
      flat
      bordered
      :rows="rows"
      :columns="columns"
      row-key="id"
      :loading="loading"
      :grid="$q.screen.xs"
      :no-data-label="emptyText"
      :pagination="{ rowsPerPage: 10 }"
      :rows-per-page-options="[10, 20, 50]"
      rows-per-page-label="한 번에 보기"
      :pagination-label="paginationLabel"
      class="clickable-table"
      @row-click="onRowClick"
    >
      <!-- 표 기본 '데이터 없음'은 경고(⚠) 아이콘이라 처리할 일이 없는 것도 오류처럼 보였다 -->
      <template #no-data="{ message }">
        <div class="table-empty">{{ message }}</div>
      </template>
      <!-- 표 행은 포커스를 받지 못한다 — 이름을 단추로 두어 키보드로도 상세를 연다 -->
      <template #body-cell-applicantName="props">
        <q-td :props="props">
          <button type="button" class="row-open" @click="openDetail(props.row)">{{ props.value || '-' }}</button>
        </q-td>
      </template>
      <template #body-cell-status="props">
        <q-td :props="props"><StatusBadge :status="props.value" /></q-td>
      </template>
      <template #body-cell-action="props">
        <q-td :props="props">
          <!-- 처리 끝난 신청엔 꺼진 버튼을 늘어놓지 않는다 -->
          <div v-if="isPending(props.row)" class="row-actions">
            <q-btn unelevated dense no-caps color="primary" label="승인" class="decide-btn" :loading="busyId === props.row.id" :disable="!!busyId" @click="approve(props.row)" />
            <q-btn outline dense no-caps color="negative" label="거절" class="decide-btn" :disable="!!busyId" @click="openReject(props.row)" />
          </div>
        </q-td>
      </template>
      <!-- 폰(xs): 6칸 표에 버튼 셋이 옆으로 밀렸다 — 한 신청을 카드 한 장으로 -->
      <template #item="props">
        <div class="col-12">
          <!-- 카드 전체를 단추로 두면 [승인]·[거절]이 단추 안 단추가 된다 — 이름 줄만 단추, 나머지는 눌러도 열린다 -->
          <div class="item-card clickable-card" @click="onRowClick($event, props.row)">
            <button
              type="button"
              class="item-card__head item-card__open"
              :aria-label="`${props.row.applicantName || '신청자'} 강사 신청 상세 보기`"
              @click="openDetail(props.row)"
            >
              <span class="item-card__name">{{ props.row.applicantName || '-' }}</span>
              <StatusBadge :status="props.row.status" />
            </button>
            <div v-if="showInstitution" class="item-card__meta">{{ props.row.institutionName || '기관 정보 없음' }}</div>
            <div class="item-card__meta">{{ props.row.applicantEmail || '-' }} · 신청 {{ formatDate(props.row.appliedAt) }}</div>
            <div v-if="isPending(props.row)" class="item-card__actions">
              <q-btn unelevated dense no-caps color="primary" label="승인" class="decide-btn" :loading="busyId === props.row.id" :disable="!!busyId" @click="approve(props.row)" />
              <q-btn outline dense no-caps color="negative" label="거절" class="decide-btn" :disable="!!busyId" @click="openReject(props.row)" />
            </div>
          </div>
        </div>
      </template>
    </q-table>

    <ApplicationDetailDialog
      v-model="detailOpen"
      :loading="detailLoading"
      :title="detail?.applicantName || ''"
      :status="detail?.status || ''"
      :meta="detailMeta"
      :message="detail ? { label: '지원 사유', text: detail.applicationMessage } : null"
      :contacts="detailContacts"
      :facts="detailFacts"
      :pending="isPending(detail)"
      :busy="!!busyId"
      :approving="!!detail && busyId === detail.id"
      pending-note="승인하면 이 기관의 강사 콘솔을 쓸 수 있습니다."
      @approve="approveFromDetail"
      @reject="rejectFromDetail"
    />

    <!-- 거절 — 직접 만든 창은 폰에서 넘치고 누구를 거절하는지 몰랐다. 실패하면 닫히지 않는 공용 창 -->
    <CorrectionReasonDialog
      v-model="rejectOpen"
      title="강사 신청 거절"
      :target="rejectTargetLabel"
      :notes="['거절 사유는 신청자에게 보입니다.']"
      confirm-label="거절"
      reason-label="거절 사유"
      reason-hint="신청 이력에 남습니다."
      :action="runReject"
      @done="onRejected"
    />
  </div>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import ApplicationDetailDialog from '@/components/console/ApplicationDetailDialog.vue';
import CorrectionReasonDialog from '@/components/console/CorrectionReasonDialog.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import {
  approveInstructorApplication,
  getInstructorApplication,
  rejectInstructorApplication,
} from '@/services/instructor-api';
import { confirmDialog } from '@/utils/confirm-dialog';
import { formatDateKst, formatDateTimeKst, qFormatDateTimeKst } from '@/utils/datetime';
import { INSTRUCTOR_APPLICATION_FILTER_OPTIONS } from '@/utils/status';
import { maskPhone } from '@/utils/user-display';

/** 이메일·연락처 줄 — 값이 있으면 눌러서 바로 메일·전화 */
function contactRow(label, value, scheme, icon) {
  if (!value) return { label, value: '-' };
  const target = scheme === 'tel:' ? value.replace(/[^\d+]/g, '') : value;
  return { label, value, href: `${scheme}${target}`, icon };
}

export default {
  name: 'InstructorApplicationsPanel',
  components: { AppErrorBanner, ApplicationDetailDialog, CorrectionReasonDialog, StatusBadge },
  props: {
    /**
     * ({ status }) => Promise<{ rows, cappedText? }> — 기관 콘솔은 그 기관 신청, 관리자는 기관을 가로질러 모은다.
     * null 이면(기관 미정) 비운다. 바뀌면 다시 읽는다
     */
    fetchApplications: { type: Function, default: null },
    /** 관리자 화면처럼 여러 기관 신청이 섞이면 기관 칸을 보인다 */
    showInstitution: { type: Boolean, default: false },
    /** 상태 필터(null = 전체) — 주소와 맞추려고 페이지가 든다 */
    status: { type: String, default: 'PENDING' },
  },
  emits: ['update:status', 'changed', 'loaded'],
  data() {
    return {
      rows: [],
      cappedText: '',
      loading: false,
      errorMessage: '',
      busyId: null,
      statusOptions: INSTRUCTOR_APPLICATION_FILTER_OPTIONS,
      detailOpen: false,
      detailLoading: false,
      detail: null,
      rejectOpen: false,
      rejectTarget: null,
      baseColumns: [
        { name: 'applicantName', label: '이름', field: 'applicantName', align: 'left' },
        { name: 'institutionName', label: '기관', field: 'institutionName', align: 'left' },
        { name: 'applicantEmail', label: '이메일', field: 'applicantEmail', align: 'left' },
        // 목록은 가운데를 가린다(다른 회원 목록과 같게) — 전체 번호는 [상세]에서
        { name: 'applicantPhone', label: '연락처', field: 'applicantPhone', align: 'left', format: maskPhone },
        { name: 'status', label: '상태', field: 'status', align: 'center' },
        { name: 'appliedAt', label: '신청일', field: 'appliedAt', align: 'left', format: qFormatDateTimeKst },
        { name: 'action', label: '처리', field: 'id', align: 'right' },
      ],
    };
  },
  computed: {
    columns() {
      return this.showInstitution ? this.baseColumns : this.baseColumns.filter((col) => col.name !== 'institutionName');
    },
    emptyText() {
      return this.status === 'PENDING' ? '대기 중인 강사 신청이 없습니다.' : '조건에 맞는 강사 신청이 없습니다.';
    },
    rejectTargetLabel() {
      const row = this.rejectTarget;
      return row ? [row.applicantName, row.applicantEmail].filter(Boolean).join(' · ') : '';
    },
    // 머리 한 줄 — 무슨 신청인지·어느 기관·언제
    detailMeta() {
      const d = this.detail;
      if (!d) return [];
      return [
        '강사 신청',
        ...(this.showInstitution ? [d.institutionName || '기관 정보 없음'] : []),
        `${formatDateTimeKst(d.appliedAt)} 신청`,
      ];
    },
    detailContacts() {
      const d = this.detail;
      if (!d) return [];
      return [
        contactRow('이메일', d.applicantEmail, 'mailto:', 'o_mail'),
        contactRow('연락처', d.applicantPhone, 'tel:', 'o_call'),
      ];
    },
    // 대기 중엔 비어 있을 수밖에 없는 검토일·거절 사유를 늘어놓지 않는다
    detailFacts() {
      const d = this.detail;
      if (!d || d.status === 'PENDING') return [];
      return [
        { label: '검토일', value: formatDateTimeKst(d.reviewedAt) },
        ...(d.status === 'REJECTED' ? [{ label: '거절 사유', value: d.rejectionReason || '-' }] : []),
      ];
    },
  },
  watch: {
    fetchApplications: { immediate: true, handler: 'load' },
    status: 'load',
  },
  methods: {
    paginationLabel(first, last, total) {
      return `총 ${total}건 중 ${first}-${last}`;
    },
    formatDate(value) {
      return formatDateKst(value);
    },
    isPending(row) {
      return row?.status === 'PENDING';
    },
    async load() {
      if (!this.fetchApplications) {
        this.rows = [];
        return;
      }
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await this.fetchApplications({ status: this.status || undefined });
        this.rows = result.rows || [];
        this.cappedText = result.cappedText || '';
        this.$emit('loaded', { status: this.status, count: this.rows.length, capped: !!this.cappedText });
      } catch (error) {
        this.rows = [];
        this.errorMessage = error.message || '강사 신청을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    async openDetail(row) {
      if (!row?.id) return;
      this.detailOpen = true;
      this.detailLoading = true;
      this.detail = null;
      try {
        // 상세 응답엔 기관 이름이 없다 — 목록 행에서 가져온다(UUID 대신)
        this.detail = { ...(await getInstructorApplication(row.id)), institutionName: row.institutionName };
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '상세를 불러오지 못했습니다.', position: 'top' });
        this.detailOpen = false;
      } finally {
        this.detailLoading = false;
      }
    },
    /** 승인하면 강사 권한이 생긴다 — 누른 즉시 바뀌던 것을 한 번 묻는다 */
    async approve(row) {
      if (this.busyId) return false;
      const ok = await confirmDialog(this.$q, {
        title: '강사 신청 승인',
        message: `${row.applicantName || '신청자'} 님을 소속 강사로 승인합니다. 승인하면 강사 콘솔을 쓸 수 있습니다.`,
        ok: '승인',
        cancel: '닫기',
      });
      if (!ok) return false;
      this.busyId = row.id;
      try {
        await approveInstructorApplication(row.id);
        this.$q.notify({ type: 'positive', message: `${row.applicantName || '신청자'} 님을 강사로 승인했습니다.`, position: 'top' });
        await this.load();
        this.$emit('changed');
        return true;
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '승인하지 못했습니다.', position: 'top' });
        return false;
      } finally {
        this.busyId = null;
      }
    },
    /** 승인·거절 단추·링크를 누른 건 행 클릭이 아니다 */
    onRowClick(event, row) {
      if (event?.target?.closest?.('a, button')) return;
      this.openDetail(row);
    },
    async approveFromDetail() {
      if (await this.approve(this.detail)) this.detailOpen = false;
    },
    openReject(row) {
      this.rejectTarget = row;
      this.rejectOpen = true;
    },
    rejectFromDetail() {
      this.detailOpen = false;
      this.openReject(this.detail);
    },
    runReject(reason) {
      return rejectInstructorApplication(this.rejectTarget.id, reason);
    },
    async onRejected() {
      this.$q.notify({
        type: 'positive',
        message: `${this.rejectTarget?.applicantName || '신청자'} 님의 강사 신청을 거절했습니다.`,
        position: 'top',
      });
      await this.load();
      this.$emit('changed');
    },
  },
};
</script>

<style scoped src="./institution-panel.css"></style>
