<template>
  <q-page padding class="bg-grey-1">
    <div class="page-wrap">
      <div class="app-page-header q-mb-lg">
        <div class="app-page-header__title text-h5 text-weight-bold">강사 신청</div>
        <div class="app-page-header__subtitle text-grey-7">
        전역 LEARNER가 기관 강사로 신청합니다. 승인되면 해당 기관 INSTRUCTOR 역할이 부여됩니다.
        </div>
      </div>

      <q-banner v-if="!isLoggedIn" class="bg-blue-1 q-mb-md">
        로그인 후 신청할 수 있습니다.
      </q-banner>
      <q-banner v-else-if="isInstructor" class="bg-green-1 q-mb-md">
        이미 강사 역할이 있습니다. 추가 기관에 신청할 수 있습니다.
      </q-banner>

      <q-card flat bordered class="q-mb-lg">
        <q-card-section>
          <div class="text-subtitle1 text-weight-bold q-mb-md">새 신청</div>
          <q-select
            v-model="institutionId"
            outlined
            emit-value
            map-options
            :options="institutionOptions"
            :loading="loadingInstitutions"
            label="신청 기관 *"
          />
          <q-input
            v-model="applicationMessage"
            outlined
            type="textarea"
            autogrow
            class="q-mt-md"
            label="지원 사유 (선택)"
            maxlength="1000"
          />
          <q-btn
            class="q-mt-md"
            color="primary"
            unelevated
            label="강사 신청하기"
            :loading="submitting"
            :disable="!isLoggedIn || !institutionId"
            @click="submitApply"
          />
        </q-card-section>
      </q-card>

      <q-card flat bordered>
        <q-card-section class="row items-center justify-between">
          <div class="text-subtitle1 text-weight-bold">내 신청 이력</div>
          <q-btn flat dense color="primary" icon="refresh" :loading="loading" @click="loadMine" />
        </q-card-section>
        <q-separator />
        <q-banner v-if="errorMessage" dense class="bg-red-1 text-negative">{{ errorMessage }}</q-banner>
        <q-table
          flat
          :rows="rows"
          :columns="columns"
          row-key="id"
          :loading="loading"
          hide-pagination
          :pagination="{ rowsPerPage: 0 }"
        >
          <template #body-cell-status="props">
            <q-td :props="props">
              <StatusBadge :status="props.value" />
            </q-td>
          </template>
          <template #body-cell-actions="props">
            <q-td :props="props">
              <q-btn flat dense color="primary" label="상세" @click="openDetail(props.row)" />
            </q-td>
          </template>
        </q-table>
      </q-card>
    </div>
    <q-dialog v-model="detailOpen">
      <q-card style="min-width: 440px; max-width: 560px">
        <q-card-section class="text-h6">신청 상세</q-card-section>
        <q-card-section>
          <q-inner-loading :showing="detailLoading" />
          <q-list v-if="detail">
            <q-item><q-item-section><q-item-label caption>상태</q-item-label><q-item-label>{{ statusLabel(detail.status) }}</q-item-label></q-item-section></q-item>
            <q-item><q-item-section><q-item-label caption>신청자</q-item-label><q-item-label>{{ detail.applicantName }} · {{ detail.applicantEmail || '-' }}</q-item-label></q-item-section></q-item>
            <q-item><q-item-section><q-item-label caption>지원 사유</q-item-label><q-item-label>{{ detail.applicationMessage || '-' }}</q-item-label></q-item-section></q-item>
            <q-item><q-item-section><q-item-label caption>거절 사유</q-item-label><q-item-label>{{ detail.rejectionReason || '-' }}</q-item-label></q-item-section></q-item>
            <q-item><q-item-section><q-item-label caption>신청일</q-item-label><q-item-label>{{ $formatDateTimeKst(detail.appliedAt) }}</q-item-label></q-item-section></q-item>
            <q-item><q-item-section><q-item-label caption>검토일</q-item-label><q-item-label>{{ $formatDateTimeKst(detail.reviewedAt) }}</q-item-label></q-item-section></q-item>
          </q-list>
        </q-card-section>
        <q-card-actions align="right"><q-btn flat label="닫기" v-close-popup /></q-card-actions>
      </q-card>
    </q-dialog>
  </q-page>
</template>

<script>
import { qFormatDateTimeKst } from '@/utils/datetime';
import StatusBadge from '@/components/StatusBadge.vue';
import { mapState } from 'pinia';
import { listInstitutions } from '@/services/institution-api';
import { applyInstructor, getInstructorApplication, listMyInstructorApplications } from '@/services/instructor-api';
import { useAuthStore } from '@/stores/auth-store';
import { statusLabel } from '@/utils/status';

export default {
  components: { StatusBadge },
  name: 'InstructorApplyPage',
  data() {
    return {
      institutionId: null,
      applicationMessage: '',
      institutionOptions: [],
      loadingInstitutions: false,
      loading: false,
      submitting: false,
      errorMessage: '',
      rows: [],
      detailOpen: false,
      detailLoading: false,
      detail: null,
      columns: [
        { name: 'institutionId', label: '기관 ID', field: 'institutionId', align: 'left' },
        { name: 'status', label: '상태', field: 'status', align: 'center' },
        { name: 'applicationMessage', label: '사유', field: 'applicationMessage', align: 'left' },
        { name: 'appliedAt', label: '신청일', field: 'appliedAt', align: 'left', format: qFormatDateTimeKst },
        { name: 'rejectionReason', label: '거절 사유', field: 'rejectionReason', align: 'left' },
        { name: 'actions', label: '관리', field: 'actions', align: 'right' },
      ],
    };
  },
  computed: {
    ...mapState(useAuthStore, ['isLoggedIn', 'isInstructor']),
  },
  mounted() {
    this.loadInstitutions();
    this.loadMine();
  },
  methods: {
    statusLabel,
    async loadInstitutions() {
      this.loadingInstitutions = true;
      try {
        const page = await listInstitutions({ page: 0, size: 50, sort: 'name,asc' });
        this.institutionOptions = (page.data || []).map((item) => ({
          label: `${item.name} (${item.institutionCode || item.id})`,
          value: item.id,
        }));
      } catch {
        this.institutionOptions = [];
      } finally {
        this.loadingInstitutions = false;
      }
    },
    async loadMine() {
      if (!this.isLoggedIn) return;
      this.loading = true;
      this.errorMessage = '';
      try {
        const page = await listMyInstructorApplications({ page: 0, size: 50, sort: 'appliedAt,desc' });
        this.rows = Array.isArray(page.data) ? page.data : [];
      } catch (error) {
        this.rows = [];
        this.errorMessage = error.message || '신청 이력을 불러오지 못했습니다.';
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
        this.detail = await getInstructorApplication(row.id);
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '상세를 불러오지 못했습니다.', position: 'top' });
        this.detailOpen = false;
      } finally {
        this.detailLoading = false;
      }
    },
    async submitApply() {
      if (!this.institutionId) return;
      this.submitting = true;
      try {
        await applyInstructor(this.institutionId, {
          applicationMessage: this.applicationMessage.trim() || null,
        });
        this.applicationMessage = '';
        this.$q.notify({ type: 'positive', message: '강사 신청이 접수되었습니다.', position: 'top' });
        await this.loadMine();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '신청에 실패했습니다.', position: 'top' });
      } finally {
        this.submitting = false;
      }
    },
  },
};
</script>

<style scoped>
.page-wrap {
  max-width: 960px;
  margin: 0 auto;
}
</style>
