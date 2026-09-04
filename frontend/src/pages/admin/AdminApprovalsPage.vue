<template>
  <q-page padding>
    <div class="app-page-header q-mb-lg">
      <div class="app-page-header__title text-h5 text-weight-bold">가입 승인 관리</div>
      <div class="app-page-header__subtitle text-grey-7">
        기관 강사 신청은 OpenAPI로 연동합니다. 기관 자체 가입 신청 path는 없습니다.
      </div>
    </div>
    <q-tabs v-model="tab" align="left" active-color="primary">
      <q-tab name="instructor" label="강사 신청" />
      <q-tab name="institution" label="기관 신청" />
    </q-tabs>
    <q-separator />
    <q-tab-panels v-model="tab">
      <q-tab-panel name="instructor">
        <div class="app-page-header row items-center justify-between q-mb-lg">
          <q-select
            v-model="statusFilter"
            outlined
            dense
            emit-value
            map-options
            style="min-width: 180px"
            :options="statusOptions"
            label="상태"
            @update:model-value="loadApplications"
          />
          <q-btn outline color="primary" icon="refresh" label="새로고침" :loading="loading" @click="loadApplications" />
        </div>
        <AppErrorBanner :message="errorMessage" />
        <q-table flat bordered :rows="rows" :columns="columns" row-key="id" :loading="loading">
          <template #body-cell-status="props">
            <q-td :props="props">
              <StatusBadge :status="props.value" />
            </q-td>
          </template>
          <template #body-cell-action="props">
            <q-td :props="props">
              <q-btn
                flat
                dense
                color="primary"
                label="상세"
                @click="openDetail(props.row)"
              />
              <q-btn
                flat
                dense
                color="positive"
                label="승인"
                :disable="props.row.status !== 'PENDING'"
                @click="approve(props.row)"
              />
              <q-btn
                flat
                dense
                color="negative"
                label="거절"
                :disable="props.row.status !== 'PENDING'"
                @click="openReject(props.row)"
              />
            </q-td>
          </template>
        </q-table>
      </q-tab-panel>
      <q-tab-panel name="institution">
        <q-banner class="bg-amber-1 q-mb-md">기관 등록 신청 API가 OpenAPI에 없습니다. POST /institutions만 있습니다.</q-banner>
      </q-tab-panel>
    </q-tab-panels>

    <q-dialog v-model="rejectOpen">
      <q-card style="min-width: 400px">
        <q-card-section class="text-h6">거절 사유</q-card-section>
        <q-card-section>
          <q-input v-model="rejectReason" outlined type="textarea" autogrow label="rejectionReason *" />
        </q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="취소" v-close-popup />
          <q-btn color="negative" unelevated :loading="saving" label="거절" @click="reject" />
        </q-card-actions>
      </q-card>
    </q-dialog>

    <q-dialog v-model="detailOpen">
      <q-card style="min-width: 440px; max-width: 560px">
        <q-card-section class="text-h6">신청 상세</q-card-section>
        <q-card-section>
          <q-inner-loading :showing="detailLoading" />
          <q-list v-if="detail">
            <q-item><q-item-section><q-item-label caption>기관</q-item-label><q-item-label>{{ detail.institutionName || detail.institutionId }}</q-item-label></q-item-section></q-item>
            <q-item><q-item-section><q-item-label caption>상태</q-item-label><q-item-label>{{ statusLabel(detail.status) }}</q-item-label></q-item-section></q-item>
            <q-item><q-item-section><q-item-label caption>신청자</q-item-label><q-item-label>{{ detail.applicantName }} · {{ detail.applicantEmail || '-' }}</q-item-label></q-item-section></q-item>
            <q-item><q-item-section><q-item-label caption>지원 사유</q-item-label><q-item-label>{{ detail.applicationMessage || '-' }}</q-item-label></q-item-section></q-item>
            <q-item><q-item-section><q-item-label caption>거절 사유</q-item-label><q-item-label>{{ detail.rejectionReason || '-' }}</q-item-label></q-item-section></q-item>
            <q-item><q-item-section><q-item-label caption>신청일</q-item-label><q-item-label>{{ $formatDateTimeKst(detail.appliedAt) }}</q-item-label></q-item-section></q-item>
          </q-list>
        </q-card-section>
        <q-card-actions align="right"><q-btn flat label="닫기" v-close-popup /></q-card-actions>
      </q-card>
    </q-dialog>
  </q-page>
</template>

<script>
import { qFormatDateTimeKst } from '@/utils/datetime';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { listInstitutions } from '@/services/institution-api';
import {
  approveInstructorApplication,
  getInstructorApplication,
  listInstitutionInstructorApplications,
  rejectInstructorApplication,
} from '@/services/instructor-api';

import { statusLabel } from '@/utils/status';

export default {
  components: { AppErrorBanner, StatusBadge },
  name: 'AdminApprovalsPage',
  data() {
    return {
      tab: 'instructor',
      loading: false,
      saving: false,
      errorMessage: '',
      rows: [],
      statusFilter: 'PENDING',
      statusOptions: [
        { label: '대기', value: 'PENDING' },
        { label: '승인', value: 'APPROVED' },
        { label: '거절', value: 'REJECTED' },
        { label: '전체', value: null },
      ],
      rejectOpen: false,
      rejectReason: '',
      rejectTarget: null,
      detailOpen: false,
      detailLoading: false,
      detail: null,
      columns: [
        { name: 'applicantName', label: '신청자', field: 'applicantName', align: 'left' },
        { name: 'applicantEmail', label: '이메일', field: 'applicantEmail', align: 'left' },
        { name: 'institutionName', label: '기관', field: 'institutionName', align: 'left' },
        { name: 'status', label: '상태', field: 'status', align: 'center' },
        { name: 'appliedAt', label: '신청일', field: 'appliedAt', align: 'left', format: qFormatDateTimeKst },
        { name: 'action', label: '관리', field: 'action', align: 'right' },
      ],
    };
  },
  mounted() {
    this.loadApplications();
  },
  methods: {
    statusLabel,
    async loadApplications() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const instPage = await listInstitutions({ page: 0, size: 50 });
        const institutions = instPage.data || [];
        const chunks = await Promise.all(
          institutions.map(async (institution) => {
            try {
              const page = await listInstitutionInstructorApplications(institution.id, {
                page: 0,
                size: 50,
                status: this.statusFilter || undefined,
              });
              return (page.data || []).map((row) => ({
                ...row,
                institutionName: institution.name,
              }));
            } catch {
              return [];
            }
          }),
        );
        this.rows = chunks.flat();
      } catch (error) {
        this.rows = [];
        this.errorMessage = error.message || '신청 목록을 불러오지 못했습니다.';
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
        const data = await getInstructorApplication(row.id);
        this.detail = { ...data, institutionName: row.institutionName };
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '상세를 불러오지 못했습니다.', position: 'top' });
        this.detailOpen = false;
      } finally {
        this.detailLoading = false;
      }
    },
    async approve(row) {
      try {
        await approveInstructorApplication(row.id);
        this.$q.notify({ type: 'positive', message: '승인했습니다.', position: 'top' });
        await this.loadApplications();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      }
    },
    openReject(row) {
      this.rejectTarget = row;
      this.rejectReason = '';
      this.rejectOpen = true;
    },
    async reject() {
      if (!this.rejectTarget) return;
      if (!this.rejectReason.trim()) {
        this.$q.notify({ type: 'warning', message: '거절 사유를 입력해 주세요.', position: 'top' });
        return;
      }
      this.saving = true;
      try {
        await rejectInstructorApplication(this.rejectTarget.id, this.rejectReason.trim());
        this.rejectOpen = false;
        this.$q.notify({ type: 'info', message: '거절했습니다.', position: 'top' });
        await this.loadApplications();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>
