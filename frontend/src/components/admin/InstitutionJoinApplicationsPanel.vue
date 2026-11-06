<template>
  <!-- 기관 등록 신청 — 상태 필터·목록(폰 카드)·승인 확인·거절 사유.
       행(카드)을 누르면 상세 — 표에 없는 연락처·주소를 보고 그 자리에서 승인·거절한다 -->
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
      :options="STATUS_OPTIONS"
      label="상태"
      @update:model-value="$emit('update:status', $event)"
    />
    <p v-if="capped" class="capped-note">최근 신청 {{ PAGE_SIZE }}건만 보여 줍니다.</p>
    <q-table
      flat
      bordered
      :rows="rows"
      :columns="columns"
      row-key="id"
      :loading="loading"
      :grid="$q.screen.xs"
      :no-data-label="status === 'PENDING' ? '대기 중인 기관 등록 신청이 없습니다.' : '조건에 맞는 신청이 없습니다.'"
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
      <!-- 표 행은 포커스를 받지 못한다 — 기관명을 단추로 두어 키보드로도 상세를 연다 -->
      <template #body-cell-institutionName="props">
        <q-td :props="props">
          <button type="button" class="row-open" @click="openDetail(props.row)">{{ props.value || '-' }}</button>
        </q-td>
      </template>
      <template #body-cell-status="props">
        <q-td :props="props"><StatusBadge :status="props.value" /></q-td>
      </template>
      <template #body-cell-action="props">
        <q-td :props="props">
          <div v-if="props.row.status === 'PENDING'" class="row-actions">
            <q-btn unelevated dense no-caps color="primary" label="승인" class="decide-btn" :loading="busyId === props.row.id" :disable="!!busyId" @click="approve(props.row)" />
            <q-btn outline dense no-caps color="negative" label="거절" class="decide-btn" :disable="!!busyId" @click="openReject(props.row)" />
          </div>
        </q-td>
      </template>
      <template #item="props">
        <div class="col-12">
          <!-- 카드 전체를 단추로 두면 [승인]·[거절]이 단추 안 단추가 된다 — 이름 줄만 단추, 나머지는 눌러도 열린다 -->
          <div class="item-card clickable-card" @click="onRowClick($event, props.row)">
            <button
              type="button"
              class="item-card__head item-card__open"
              :aria-label="`${props.row.institutionName || '기관'} 신청 상세 보기`"
              @click="openDetail(props.row)"
            >
              <span class="item-card__name">{{ props.row.institutionName || '-' }}</span>
              <StatusBadge :status="props.row.status" />
            </button>
            <div class="item-card__meta">
              {{ [props.row.representativeName, props.row.contactEmail].filter(Boolean).join(' · ') || '-' }}
            </div>
            <div v-if="props.row.status === 'PENDING'" class="item-card__actions">
              <q-btn unelevated dense no-caps color="primary" label="승인" class="decide-btn" :loading="busyId === props.row.id" :disable="!!busyId" @click="approve(props.row)" />
              <q-btn outline dense no-caps color="negative" label="거절" class="decide-btn" :disable="!!busyId" @click="openReject(props.row)" />
            </div>
          </div>
        </div>
      </template>
    </q-table>

    <ApplicationDetailDialog
      v-model="detailOpen"
      :title="detail?.institutionName || ''"
      :status="detail?.status || ''"
      :meta="detailMeta"
      :contacts="detailContacts"
      :facts="detailFacts"
      :pending="detail?.status === 'PENDING'"
      :busy="!!busyId"
      :approving="!!detail && busyId === detail.id"
      pending-note="승인하면 기관이 만들어지고 신청자가 기관 관리자 콘솔을 쓸 수 있습니다."
      :link="detail?.createdInstitutionId ? { to: `/admin/institutions/${detail.createdInstitutionId}`, label: '생성된 기관 보기' } : null"
      @approve="approveFromDetail"
      @reject="rejectFromDetail"
    />

    <CorrectionReasonDialog
      v-model="rejectOpen"
      title="기관 등록 신청 거절"
      :target="rejectTarget?.institutionName || ''"
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
  approveInstitutionJoinApplication,
  listInstitutionJoinApplications,
  rejectInstitutionJoinApplication,
} from '@/services/institution-api';
import { confirmDialog } from '@/utils/confirm-dialog';
import { formatDateTimeKst, qFormatDateTimeKst } from '@/utils/datetime';
import { INSTRUCTOR_APPLICATION_FILTER_OPTIONS } from '@/utils/status';

const PAGE_SIZE = 50;
// 강사 신청과 같은 대기·승인·거절·전체
const STATUS_OPTIONS = INSTRUCTOR_APPLICATION_FILTER_OPTIONS;

export default {
  name: 'InstitutionJoinApplicationsPanel',
  components: { AppErrorBanner, ApplicationDetailDialog, CorrectionReasonDialog, StatusBadge },
  props: {
    /** 상태 필터(null = 전체) — 주소(?status=)와 맞추려고 페이지가 든다 */
    status: { type: String, default: 'PENDING' },
  },
  emits: ['update:status', 'changed'],
  data() {
    return {
      PAGE_SIZE,
      STATUS_OPTIONS,
      rows: [],
      capped: false,
      loading: false,
      errorMessage: '',
      busyId: null,
      rejectOpen: false,
      rejectTarget: null,
      detailOpen: false,
      detail: null,
      columns: [
        { name: 'institutionName', label: '기관명', field: 'institutionName', align: 'left' },
        { name: 'representativeName', label: '대표자', field: 'representativeName', align: 'left' },
        { name: 'contactEmail', label: '이메일', field: 'contactEmail', align: 'left' },
        { name: 'status', label: '상태', field: 'status', align: 'center' },
        { name: 'createdAt', label: '신청일', field: 'createdAt', align: 'left', format: qFormatDateTimeKst },
        { name: 'action', label: '처리', field: 'id', align: 'right' },
      ],
    };
  },
  computed: {
    // 머리 한 줄 — 무슨 신청인지·대표자·언제
    detailMeta() {
      const d = this.detail;
      if (!d) return [];
      return ['기관 등록 신청', `대표 ${d.representativeName || '-'}`, `${formatDateTimeKst(d.createdAt)} 신청`];
    },
    detailContacts() {
      const d = this.detail;
      if (!d) return [];
      return [
        d.contactEmail ? { label: '이메일', value: d.contactEmail, href: `mailto:${d.contactEmail}`, icon: 'o_mail' } : { label: '이메일', value: '-' },
        d.contactPhone
          ? { label: '연락처', value: d.contactPhone, href: `tel:${d.contactPhone.replace(/[^\d+]/g, '')}`, icon: 'o_call' }
          : { label: '연락처', value: '-' },
      ];
    },
    /** 표에 없는 칸 — 목록 응답에 다 들어 있다. 처리일·거절 사유는 처리된 건에만 */
    detailFacts() {
      const d = this.detail;
      if (!d) return [];
      const rows = [
        { label: '기관 코드', value: d.institutionCode || '-' },
        { label: '주소', value: d.address || '-' },
      ];
      if (d.status !== 'PENDING') rows.push({ label: '처리일', value: formatDateTimeKst(d.decidedAt) });
      if (d.status === 'REJECTED') rows.push({ label: '거절 사유', value: d.rejectionReason || '-' });
      return rows;
    },
  },
  watch: {
    status: 'load',
  },
  mounted() {
    this.load();
  },
  methods: {
    paginationLabel(first, last, total) {
      return `총 ${total}건 중 ${first}-${last}`;
    },
    /** 승인·거절 단추·링크를 누른 건 행 클릭이 아니다 */
    onRowClick(event, row) {
      if (event?.target?.closest?.('a, button')) return;
      this.openDetail(row);
    },
    openDetail(row) {
      this.detail = row;
      this.detailOpen = true;
    },
    async approveFromDetail() {
      if (await this.approve(this.detail)) this.detailOpen = false;
    },
    rejectFromDetail() {
      this.detailOpen = false;
      this.openReject(this.detail);
    },
    async load() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const page = await listInstitutionJoinApplications({ page: 0, size: PAGE_SIZE, status: this.status || undefined });
        this.rows = page.data || [];
        this.capped = (page.page?.totalElements || 0) > PAGE_SIZE;
      } catch (error) {
        this.rows = [];
        this.errorMessage = error.message || '기관 등록 신청을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    /** 승인하면 기관과 기관 관리자 권한이 생긴다 — 누르는 즉시 처리되던 것을 묻는다 */
    async approve(row) {
      if (this.busyId) return false;
      const name = row.institutionName || '이 기관';
      const ok = await confirmDialog(this.$q, {
        title: '기관 등록 승인',
        message: `'${name}' 기관 등록을 승인합니다. 승인하면 신청자가 기관 관리자 콘솔을 쓸 수 있습니다.`,
        ok: '승인',
        cancel: '닫기',
      });
      if (!ok) return false;
      this.busyId = row.id;
      try {
        await approveInstitutionJoinApplication(row.id);
        this.$q.notify({ type: 'positive', message: `'${name}' 기관 등록을 승인했습니다.`, position: 'top' });
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
    openReject(row) {
      this.rejectTarget = row;
      this.rejectOpen = true;
    },
    runReject(reason) {
      return rejectInstitutionJoinApplication(this.rejectTarget.id, reason);
    },
    async onRejected() {
      this.$q.notify({
        type: 'positive',
        message: `'${this.rejectTarget?.institutionName || '기관'}' 등록 신청을 거절했습니다.`,
        position: 'top',
      });
      await this.load();
      this.$emit('changed');
    },
  },
};
</script>

<style scoped src="../institution/institution-panel.css"></style>
