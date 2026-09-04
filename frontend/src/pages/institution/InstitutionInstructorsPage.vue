<template>
  <q-page padding>
    <div class="app-page-header row justify-between items-center q-mb-lg">
      <div>
        <div class="app-page-header__title text-h5 text-weight-bold">강사 관리</div>
        <div class="app-page-header__subtitle text-grey-7">강사 신청 승인/거절 · 소속 INSTRUCTOR는 사용자 역할로 조회</div>
      </div>
      <q-btn outline color="primary" icon="refresh" label="새로고침" :loading="loading" @click="reload" />
    </div>

    <AppInstitutionMissingBanner :show="showInstitutionMissingBanner" />
    <AppErrorBanner :message="errorMessage" />

    <q-tabs v-model="tab" align="left" active-color="primary" @update:model-value="onTab">
      <q-tab name="applications" label="강사 신청" />
      <q-tab name="roster" label="소속 강사" />
    </q-tabs>
    <q-separator />
    <q-tab-panels v-model="tab">
      <q-tab-panel name="applications">
        <q-select
          v-model="statusFilter"
          outlined
          dense
          emit-value
          map-options
          class="q-mb-md"
          style="max-width: 220px"
          :options="statusOptions"
          label="상태"
          @update:model-value="loadRows"
        />
        <q-table flat bordered :rows="rows" :columns="columns" row-key="id" :loading="loading">
          <template #body-cell-status="props">
            <q-td :props="props">
              <StatusBadge :status="props.value" />
            </q-td>
          </template>
          <template #body-cell-action="props">
            <q-td :props="props">
              <q-btn flat dense color="primary" label="상세" @click="openDetail(props.row)" />
              <q-btn flat dense color="positive" label="승인" :disable="props.row.status !== 'PENDING'" @click="approve(props.row)" />
              <q-btn flat dense color="negative" label="거절" :disable="props.row.status !== 'PENDING'" @click="openReject(props.row)" />
            </q-td>
          </template>
        </q-table>
      </q-tab-panel>
      <q-tab-panel name="roster">
        <div class="text-caption text-grey-7 q-mb-md">전용 소속 강사 목록 path가 없어 GET /users + GET /users/{id}/roles 로 조합합니다.</div>
        <q-table flat bordered :rows="roster" :columns="rosterColumns" row-key="id" :loading="rosterLoading">
          <template #body-cell-status="props">
            <q-td :props="props">
              <StatusBadge :status="props.value" />
            </q-td>
          </template>
          <template #body-cell-action="props">
            <q-td :props="props">
              <q-btn flat dense color="primary" label="수정" @click="openEdit(props.row)" />
            </q-td>
          </template>
        </q-table>
      </q-tab-panel>
    </q-tab-panels>

    <q-dialog v-model="editOpen">
      <q-card style="min-width: 440px; max-width: 520px">
        <q-card-section class="text-h6">강사 정보 수정</q-card-section>
        <q-card-section class="q-gutter-md">
          <q-inner-loading :showing="editLoading" />
          <q-input v-model="editForm.name" outlined dense label="이름 *" />
          <q-input v-model="editForm.email" outlined dense type="email" label="이메일" />
          <q-input v-model="editForm.phone" outlined dense label="연락처" />
          <q-input v-model="editForm.birthDate" outlined dense type="date" label="생년월일" />
          <q-select
            v-model="editForm.status"
            outlined
            dense
            emit-value
            map-options
            :options="userStatusOptions"
            label="상태"
          />
        </q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="취소" v-close-popup />
          <q-btn color="primary" unelevated :loading="saving" label="저장" @click="saveEdit" />
        </q-card-actions>
      </q-card>
    </q-dialog>

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
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import AppInstitutionMissingBanner from '@/components/AppInstitutionMissingBanner.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import institutionScope from '@/mixins/institution-scope';
import {
  approveInstructorApplication,
  getInstructorApplication,
  listInstitutionInstructorApplications,
  rejectInstructorApplication,
} from '@/services/instructor-api';
import { changeUserStatus, getUser, listUserRoles, listUsers, updateUser } from '@/services/user-api';
import { INSTRUCTOR_APPLICATION_FILTER_OPTIONS, USER_STATUS_OPTIONS, statusLabel } from '@/utils/status';

export default {
  name: 'InstitutionInstructorsPage',
  components: { AppErrorBanner, AppInstitutionMissingBanner, StatusBadge },
  mixins: [institutionScope],
  data() {
    return {
      tab: 'applications',
      loading: false,
      rosterLoading: false,
      saving: false,
      errorMessage: '',
      rows: [],
      roster: [],
      statusFilter: 'PENDING',
      statusOptions: INSTRUCTOR_APPLICATION_FILTER_OPTIONS,
      rejectOpen: false,
      rejectReason: '',
      rejectTarget: null,
      detailOpen: false,
      detailLoading: false,
      detail: null,
      editOpen: false,
      editLoading: false,
      editTargetId: null,
      editOriginalStatus: 'ACTIVE',
      userStatusOptions: USER_STATUS_OPTIONS,
      editForm: {
        name: '',
        email: '',
        phone: '',
        birthDate: '',
        status: 'ACTIVE',
      },
      columns: [
        { name: 'applicantName', label: '이름', field: 'applicantName', align: 'left' },
        { name: 'applicantEmail', label: '이메일', field: 'applicantEmail', align: 'left' },
        { name: 'applicantPhone', label: '연락처', field: 'applicantPhone' },
        { name: 'status', label: '상태', field: 'status' },
        { name: 'appliedAt', label: '신청일', field: 'appliedAt', align: 'left', format: qFormatDateTimeKst },
        { name: 'action', label: '관리', field: 'action' },
      ],
      rosterColumns: [
        { name: 'name', label: '이름', field: 'name', align: 'left' },
        { name: 'email', label: '이메일', field: 'email', align: 'left' },
        { name: 'phone', label: '연락처', field: 'phone' },
        { name: 'status', label: '상태', field: 'status' },
        { name: 'action', label: '관리', field: 'action' },
      ],
    };
  },
  watch: {
    institutionId: {
      immediate: true,
      handler() {
        this.reload();
      },
    },
  },
  methods: {
    statusLabel,
    reload() {
      if (this.tab === 'roster') {
        this.loadRoster();
        return;
      }
      this.loadRows();
    },
    onTab(name) {
      if (name === 'roster') this.loadRoster();
    },
    async loadRows() {
      if (!this.institutionId) {
        this.rows = [];
        return;
      }
      this.loading = true;
      this.errorMessage = '';
      try {
        const page = await listInstitutionInstructorApplications(this.institutionId, {
          page: 0,
          size: 50,
          status: this.statusFilter || undefined,
        });
        this.rows = Array.isArray(page.data) ? page.data : [];
      } catch (error) {
        this.rows = [];
        this.errorMessage = error.message || '신청 목록을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    async loadRoster() {
      if (!this.institutionId) {
        this.roster = [];
        return;
      }
      this.rosterLoading = true;
      this.errorMessage = '';
      try {
        const page = await listUsers({ page: 0, size: 50, sort: 'name,asc' });
        const users = page.data || [];
        const matched = [];
        await Promise.all(
          users.map(async (user) => {
            try {
              const roles = await listUserRoles(user.id);
              const ok = (roles || []).some(
                (item) =>
                  (item?.role || item) === 'INSTRUCTOR' &&
                  (!item?.institutionId || item.institutionId === this.institutionId),
              );
              if (ok) matched.push(user);
            } catch {
              // 역할 조회 실패 사용자는 건너뜀
            }
          }),
        );
        this.roster = matched.sort((a, b) => String(a.name || '').localeCompare(String(b.name || ''), 'ko'));
      } catch (error) {
        this.roster = [];
        this.errorMessage = error.message || '소속 강사를 불러오지 못했습니다.';
      } finally {
        this.rosterLoading = false;
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
    async openEdit(row) {
      if (!row?.id) return;
      this.editOpen = true;
      this.editLoading = true;
      this.editTargetId = row.id;
      this.editForm = {
        name: row.name || '',
        email: row.email || '',
        phone: row.phone || '',
        birthDate: row.birthDate ? String(row.birthDate).slice(0, 10) : '',
        status: row.status || 'ACTIVE',
      };
      this.editOriginalStatus = row.status || 'ACTIVE';
      try {
        const user = await getUser(row.id);
        const status = user?.status || row.status || 'ACTIVE';
        this.editForm = {
          name: user?.name || row.name || '',
          email: user?.email || row.email || '',
          phone: user?.phone || row.phone || '',
          birthDate: user?.birthDate ? String(user.birthDate).slice(0, 10) : '',
          status,
        };
        this.editOriginalStatus = status;
      } catch {
        // 목록 데이터로 폼 유지
      } finally {
        this.editLoading = false;
      }
    },
    async saveEdit() {
      if (!this.editTargetId || !String(this.editForm.name || '').trim()) {
        this.$q.notify({ type: 'warning', message: '이름은 필수입니다.', position: 'top' });
        return;
      }
      this.saving = true;
      try {
        await updateUser(this.editTargetId, {
          name: this.editForm.name.trim(),
          email: this.editForm.email.trim() || null,
          phone: this.editForm.phone.trim() || null,
          birthDate: this.editForm.birthDate || null,
        });
        if (this.editForm.status && this.editForm.status !== this.editOriginalStatus) {
          await changeUserStatus(this.editTargetId, this.editForm.status);
        }
        this.editOpen = false;
        this.$q.notify({ type: 'positive', message: '강사 정보를 저장했습니다.', position: 'top' });
        await this.loadRoster();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '저장에 실패했습니다.', position: 'top' });
      } finally {
        this.saving = false;
      }
    },
    async approve(row) {
      try {
        await approveInstructorApplication(row.id);
        this.$q.notify({ type: 'positive', message: '승인했습니다.', position: 'top' });
        await this.loadRows();
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
      if (!this.rejectTarget || !this.rejectReason.trim()) {
        this.$q.notify({ type: 'warning', message: '거절 사유를 입력해 주세요.', position: 'top' });
        return;
      }
      this.saving = true;
      try {
        await rejectInstructorApplication(this.rejectTarget.id, this.rejectReason.trim());
        this.rejectOpen = false;
        this.$q.notify({ type: 'info', message: '거절했습니다.', position: 'top' });
        await this.loadRows();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>
