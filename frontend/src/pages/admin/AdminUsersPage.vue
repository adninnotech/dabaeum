<template>
  <q-page padding>
    <div class="app-page-header row items-center justify-between q-mb-lg">
      <div>
        <div class="app-page-header__title text-h5 text-weight-bold">사용자 관리</div>
        <div class="app-page-header__subtitle text-grey-7">회원을 찾아 정보·상태·역할을 관리합니다.</div>
      </div>
      <div class="row q-gutter-sm">
        <q-btn outline color="primary" icon="refresh" label="새로고침" :loading="loading" @click="loadUsers" />
        <q-btn color="primary" unelevated icon="person_add" label="사용자 등록" @click="openCreate" />
      </div>
    </div>

    <AppErrorBanner :message="errorMessage" />

    <div class="row q-col-gutter-sm q-mb-md items-center">
      <div class="col-12 col-sm-4 col-md-3">
        <q-select
          :model-value="statusFilter"
          outlined
          dense
          emit-value
          map-options
          clearable
          :options="statusFilterOptions"
          label="상태 필터"
          @update:model-value="onStatusFilter"
        />
      </div>
      <div class="col-12 col-sm-5 col-md-4">
        <q-input v-model="keyword" outlined dense clearable label="이름/이메일 검색" />
      </div>
    </div>

    <q-table
      flat
      bordered
      row-key="id"
      :rows="filteredRows"
      :columns="columns"
      :loading="loading"
      :pagination="{ rowsPerPage: 10 }"
      :grid="$q.screen.xs"
    >
      <!-- 폰: 표가 옆으로 밀려 상태·처리 칸이 화면 밖에 있었다 — 한 사람을 카드 한 장으로 -->
      <template #item="props">
        <div class="col-12 user-card">
          <div class="user-card__head">
            <div class="user-card__name">{{ props.row.name || '-' }}</div>
            <StatusBadge :status="props.row.status" :label="USER_STATUS_LABEL[props.row.status]" />
          </div>
          <div class="user-card__line">{{ props.row.email || '-' }}</div>
          <div class="user-card__line">{{ maskPhone(props.row.phone) }}</div>
          <div class="row q-gutter-xs">
            <q-badge
              v-for="role in props.row.roles || []"
              :key="`${props.row.id}-${role}`"
              color="primary"
              outline
            >
              {{ labelForRole(role) }}
            </q-badge>
          </div>
          <div class="user-card__actions">
            <q-btn flat dense no-caps color="primary" label="상세" @click="openDetail(props.row)" />
            <q-btn flat dense no-caps color="primary" label="수정" @click="openEdit(props.row)" />
            <q-btn flat dense no-caps color="secondary" label="역할" @click="openRoles(props.row)" />
            <q-btn-dropdown flat dense no-caps color="grey-8" label="상태">
              <q-list>
                <q-item
                  v-for="opt in statusOptions"
                  :key="opt.value"
                  v-close-popup
                  clickable
                  @click="changeStatus(props.row, opt.value)"
                >
                  <q-item-section>{{ opt.label }}</q-item-section>
                </q-item>
              </q-list>
            </q-btn-dropdown>
          </div>
        </div>
      </template>
      <template #body-cell-status="props">
        <q-td :props="props">
          <StatusBadge :status="props.row.status" :label="USER_STATUS_LABEL[props.row.status]" />
        </q-td>
      </template>
      <template #body-cell-roles="props">
        <q-td :props="props">
          <div class="row q-gutter-xs wrap">
            <q-badge
              v-for="role in props.row.roles || []"
              :key="`${props.row.id}-${role}`"
              color="primary"
              outline
            >
              {{ labelForRole(role) }}
            </q-badge>
            <span v-if="!(props.row.roles || []).length" class="text-grey-6 text-caption">-</span>
          </div>
        </q-td>
      </template>
      <template #body-cell-actions="props">
        <q-td :props="props">
          <q-btn flat dense color="primary" label="상세" @click="openDetail(props.row)" />
          <q-btn flat dense color="primary" label="수정" @click="openEdit(props.row)" />
          <q-btn flat dense color="secondary" label="역할" @click="openRoles(props.row)" />
          <q-btn-dropdown flat dense color="grey-8" label="상태">
            <q-list>
              <q-item
                v-for="opt in statusOptions"
                :key="opt.value"
                v-close-popup
                clickable
                @click="changeStatus(props.row, opt.value)"
              >
                <q-item-section>{{ opt.label }}</q-item-section>
              </q-item>
            </q-list>
          </q-btn-dropdown>
        </q-td>
      </template>
    </q-table>

    <q-dialog v-model="formDialog">
      <q-card style="width: 480px; max-width: 94vw">
        <q-card-section class="text-h6">{{ formMode === 'create' ? '사용자 등록' : '사용자 수정' }}</q-card-section>
        <q-card-section class="q-gutter-md">
          <q-input v-model="form.name" outlined dense label="이름 *" />
          <q-input v-model="form.email" outlined dense type="email" label="이메일" />
          <q-input v-model="form.phone" outlined dense label="휴대폰" />
          <q-input v-model="form.birthDate" outlined dense label="생년월일 (YYYY-MM-DD)" mask="####-##-##" />
          <q-select
            v-model="form.status"
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
          <q-btn color="primary" unelevated :loading="saving" :label="formMode === 'create' ? '등록' : '저장'" @click="saveForm" />
        </q-card-actions>
      </q-card>
    </q-dialog>

    <q-dialog v-model="detailDialog">
      <q-card style="min-width: 520px; max-width: 640px">
        <q-card-section class="text-h6">사용자 상세</q-card-section>
        <q-separator />
        <q-card-section v-if="selectedUser">
          <q-inner-loading :showing="detailLoading" />
          <q-list bordered class="rounded-borders">
            <q-item v-for="row in detailRows" :key="row.label">
              <q-item-section>
                <q-item-label caption>{{ row.label }}</q-item-label>
                <q-item-label class="ellipsis">{{ row.value }}</q-item-label>
              </q-item-section>
            </q-item>
          </q-list>

          <div class="text-subtitle2 text-weight-bold q-mt-md q-mb-sm">Identity</div>
          <q-markup-table flat bordered dense>
            <thead>
              <tr>
                <th class="text-left">provider</th>
                <th class="text-left">verifiedAt</th>
                <th class="text-right">액션</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="identity in selectedIdentities" :key="identity.id">
                <td>{{ identity.provider }}</td>
                <td>{{ $formatDateTimeKst(identity.verifiedAt) }}</td>
                <td class="text-right">
                  <q-btn flat dense color="negative" label="해제" @click="unlinkIdentity(identity)" />
                </td>
              </tr>
              <tr v-if="!selectedIdentities.length">
                <td colspan="3" class="text-grey-7">연결된 Identity가 없습니다.</td>
              </tr>
            </tbody>
          </q-markup-table>
          <q-btn class="q-mt-sm" outline dense color="primary" label="Identity 연결" @click="linkIdentity" />
        </q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="닫기" v-close-popup />
        </q-card-actions>
      </q-card>
    </q-dialog>

    <q-dialog v-model="roleDialog">
      <q-card style="min-width: 420px">
        <q-card-section class="text-h6">역할 관리</q-card-section>
        <q-card-section v-if="selectedUser">
          <div class="text-body2 q-mb-md">{{ selectedUser.name }}<template v-if="selectedUser.email"> ({{ selectedUser.email }})</template></div>
          <q-inner-loading :showing="rolesLoading" />
          <div class="row q-gutter-xs q-mb-md">
            <q-chip
              v-for="roleItem in selectedRoles"
              :key="roleItem.id || roleItem.role"
              removable
              color="primary"
              text-color="white"
              @remove="revokeRole(roleItem)"
            >
              {{ labelForRole(roleItem.role || roleItem) }}
            </q-chip>
            <div v-if="!selectedRoles.length" class="text-grey-7">부여된 역할이 없습니다.</div>
          </div>
          <q-select
            :model-value="roleToAssign"
            outlined
            dense
            emit-value
            map-options
            :options="roleOptions"
            label="역할 부여"
            @update:model-value="setRoleToAssign"
          />
          <!-- 기관 관리자·강사는 어느 기관의 역할인지 정해야 한다 — 칸이 없어 institutionId:null 로 422 였다(QA ADM-02) -->
          <q-select
            v-if="roleNeedsInstitution"
            v-model="roleInstitutionId"
            outlined
            dense
            emit-value
            map-options
            use-input
            input-debounce="0"
            :options="filteredInstitutionOptions"
            :loading="institutionsLoading"
            label="기관 *"
            :error="!!institutionsError"
            :error-message="institutionsError"
            @filter="filterInstitutions"
          />
        </q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="닫기" v-close-popup />
          <q-btn color="primary" unelevated :loading="rolesLoading" label="부여" @click="assignRole" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </q-page>
</template>

<script>
import { confirmDialog } from '@/utils/confirm-dialog';
import { labelForRole } from '@/access/roles';
import { formatDateKst, formatDateTimeKst, qFormatDateKst } from '@/utils/datetime';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { maskPhone } from '@/utils/user-display';
import {
  assignUserRole,
  changeUserStatus,
  createUser,
  getUser,
  linkUserIdentity,
  listUserIdentities,
  listUserRoles,
  listUsers,
  revokeUserRole,
  unlinkUserIdentity,
  updateUser,
} from '@/services/user-api';
import { listInstitutions } from '@/services/institution-api';
import { toInstitutionOptions } from '@/utils/institution-option';
import { USER_STATUS_LABEL, USER_STATUS_OPTIONS } from '@/utils/status';

// 기관 범위 역할 — 부여할 때 institutionId 가 필수다
const INSTITUTION_SCOPED_ROLES = ['INSTITUTION_ADMIN', 'INSTRUCTOR'];
// 기관 목록 API 의 size 상한
const INSTITUTION_OPTION_LIMIT = 100;

function emptyForm() {
  return {
    name: '',
    email: '',
    phone: '',
    birthDate: '',
    status: 'ACTIVE',
  };
}

export default {
  components: { AppErrorBanner,  StatusBadge },
  name: 'AdminUsersPage',
  data() {
    return {
      USER_STATUS_LABEL,
      keyword: '',
      statusFilter: null,
      rows: [],
      loading: false,
      saving: false,
      detailLoading: false,
      rolesLoading: false,
      errorMessage: '',
      formDialog: false,
      detailDialog: false,
      roleDialog: false,
      formMode: 'create',
      form: emptyForm(),
      editingId: null,
      formOriginalStatus: 'ACTIVE',
      userStatusOptions: USER_STATUS_OPTIONS,
      selectedUser: null,
      selectedIdentities: [],
      selectedRoles: [],
      roleToAssign: 'LEARNER',
      roleInstitutionId: null,
      institutionOptions: [],
      institutionFilter: '',
      institutionsLoading: false,
      institutionsError: '',
      statusOptions: USER_STATUS_OPTIONS,
      statusFilterOptions: USER_STATUS_OPTIONS,
      roleOptions: ['LEARNER', 'INSTRUCTOR', 'INSTITUTION_ADMIN', 'PLATFORM_ADMIN'].map((value) => ({
        label: labelForRole(value),
        value,
      })),
      columns: [
        { name: 'name', label: '이름', field: 'name', align: 'left', sortable: true },
        { name: 'email', label: '이메일', field: 'email', align: 'left' },
        // 다른 회원 목록과 같이 가린다 — 전체 번호는 상세에서 본다
        { name: 'phone', label: '휴대폰', field: 'phone', align: 'left', format: maskPhone },
        { name: 'birthDate', label: '생년월일', field: 'birthDate', align: 'left', format: qFormatDateKst },
        { name: 'status', label: '상태', field: 'status', align: 'center' },
        {
          name: 'roles',
          label: '역할',
          field: (row) => (row.roles || []).join(', '),
          align: 'left',
        },
        { name: 'actions', label: '액션', field: 'actions', align: 'right' },
      ],
    };
  },
  computed: {
    roleNeedsInstitution() {
      return INSTITUTION_SCOPED_ROLES.includes(this.roleToAssign);
    },
    filteredInstitutionOptions() {
      const needle = this.institutionFilter.toLowerCase();
      if (!needle) return this.institutionOptions;
      return this.institutionOptions.filter((option) => option.label.toLowerCase().includes(needle));
    },
    filteredRows() {
      const kw = String(this.keyword || '')
        .trim()
        .toLowerCase();
      return this.rows.filter((row) => {
        if (!kw) return true;
        const roleText = (row.roles || []).join(' ').toLowerCase();
        return (
          String(row.name || '')
            .toLowerCase()
            .includes(kw) ||
          String(row.email || '')
            .toLowerCase()
            .includes(kw) ||
          String(row.id || '')
            .toLowerCase()
            .includes(kw) ||
          roleText.includes(kw)
        );
      });
    },
    detailRows() {
      const u = this.selectedUser;
      if (!u) return [];
      return [
        { label: '이름', value: u.name },
        { label: '이메일', value: u.email || '이메일 미등록' },
        { label: '휴대폰', value: u.phone || '휴대폰 미등록' },
        { label: '생년월일', value: u.birthDate ? formatDateKst(u.birthDate) : '생년월일 미등록' },
        { label: '상태', value: USER_STATUS_LABEL[u.status] || u.status },
        { label: '역할', value: (u.roles || []).map(labelForRole).join(', ') || '-' },
        { label: '가입일시', value: formatDateTimeKst(u.createdAt) },
        { label: '수정일시', value: formatDateTimeKst(u.updatedAt) },
      ];
    },
  },
  mounted() {
    this.loadUsers();
  },
  methods: {
    labelForRole,
    maskPhone,
    roleNames(roleItems) {
      if (!Array.isArray(roleItems)) return [];
      return roleItems
        .map((item) => (typeof item === 'string' ? item : item?.role))
        .filter(Boolean);
    },
    async syncRowRoles(userId) {
      const index = this.rows.findIndex((row) => row.id === userId);
      if (index < 0) return;
      try {
        const roles = await listUserRoles(userId);
        const next = {
          ...this.rows[index],
          roles: this.roleNames(roles),
          roleItems: Array.isArray(roles) ? roles : [],
        };
        this.rows.splice(index, 1, next);
        if (this.selectedUser?.id === userId) {
          this.selectedUser = { ...this.selectedUser, roles: next.roles };
        }
      } catch {
        // 목록 역할 갱신 실패는 무시
      }
    },
    async loadUsers() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await listUsers({
          page: 0,
          size: 50,
          status: this.statusFilter || undefined,
        });
        const users = Array.isArray(result.data) ? result.data : [];
        this.rows = users.map((user) => ({
          ...user,
          roles: this.roleNames(user.roles),
          roleItems: Array.isArray(user.roles) ? user.roles : [],
        }));
      } catch (error) {
        this.errorMessage = error.message || '사용자 목록 조회 실패';
        this.rows = [];
      } finally {
        this.loading = false;
      }
    },
    onStatusFilter(value) {
      this.statusFilter = value;
      this.loadUsers();
    },
    setRoleToAssign(value) {
      this.roleToAssign = value;
      if (INSTITUTION_SCOPED_ROLES.includes(value)) this.loadInstitutionOptions();
    },
    async loadInstitutionOptions() {
      if (this.institutionOptions.length || this.institutionsLoading) return;
      this.institutionsLoading = true;
      this.institutionsError = '';
      try {
        const page = await listInstitutions({ page: 0, size: INSTITUTION_OPTION_LIMIT, sort: 'name,asc' });
        this.institutionOptions = toInstitutionOptions(page.data || []);
      } catch (error) {
        this.institutionsError = `기관 목록을 불러오지 못했습니다. ${error.message || ''}`.trim();
      } finally {
        this.institutionsLoading = false;
      }
    },
    filterInstitutions(text, update) {
      update(() => {
        this.institutionFilter = String(text || '').trim();
      });
    },
    openCreate() {
      this.formMode = 'create';
      this.editingId = null;
      this.form = emptyForm();
      this.formOriginalStatus = 'ACTIVE';
      this.formDialog = true;
    },
    openEdit(row) {
      this.formMode = 'edit';
      this.editingId = row.id;
      const status = row.status || 'ACTIVE';
      this.formOriginalStatus = status;
      this.form = {
        name: row.name || '',
        email: row.email || '',
        phone: row.phone || '',
        birthDate: row.birthDate || '',
        status,
      };
      this.formDialog = true;
    },
    async openDetail(row) {
      this.detailDialog = true;
      this.detailLoading = true;
      this.selectedUser = row;
      this.selectedIdentities = [];
      try {
        const [user, identities, roles] = await Promise.all([
          getUser(row.id),
          listUserIdentities(row.id),
          listUserRoles(row.id),
        ]);
        this.selectedUser = {
          ...(user || row),
          roles: this.roleNames(roles),
        };
        this.selectedIdentities = Array.isArray(identities) ? identities : [];
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.detailLoading = false;
      }
    },
    async openRoles(row) {
      this.selectedUser = row;
      this.roleInstitutionId = null;
      this.roleDialog = true;
      this.rolesLoading = true;
      this.selectedRoles = [];
      try {
        const roles = await listUserRoles(row.id);
        this.selectedRoles = Array.isArray(roles) ? roles : [];
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.rolesLoading = false;
      }
    },
    async saveForm() {
      if (!this.form.name || !String(this.form.name).trim()) {
        this.$q.notify({ type: 'warning', message: '이름은 필수입니다.', position: 'top' });
        return;
      }
      this.saving = true;
      try {
        if (this.formMode === 'create') {
          await createUser({
            name: this.form.name.trim(),
            email: this.form.email || null,
            phone: this.form.phone || null,
            birthDate: this.form.birthDate || null,
            status: this.form.status || 'ACTIVE',
          });
          this.$q.notify({ type: 'positive', message: '사용자가 등록되었습니다.', position: 'top' });
        } else {
          await updateUser(this.editingId, {
            name: this.form.name.trim(),
            email: this.form.email || null,
            phone: this.form.phone || null,
            birthDate: this.form.birthDate || null,
          });
          // 수정 창에서 상태를 낮추면 확인 없이 정지·탈퇴됐다 — 목록 [상태]와 같이 묻는다
          if (this.form.status && this.form.status !== this.formOriginalStatus) {
            const label = USER_STATUS_LABEL[this.form.status] || this.form.status;
            const ok =
              this.form.status === 'ACTIVE' ||
              (await confirmDialog(this.$q, {
                title: `계정 ${label}`,
                message: `${this.form.name.trim()}의 계정을 '${label}' 상태로 바꿉니다. 바꾸면 로그인할 수 없게 됩니다.`,
                ok: '변경',
                color: 'negative',
              }));
            if (ok) await changeUserStatus(this.editingId, this.form.status);
          }
          this.$q.notify({ type: 'positive', message: '사용자 정보가 수정되었습니다.', position: 'top' });
        }
        this.formDialog = false;
        await this.loadUsers();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.saving = false;
      }
    },
    async changeStatus(row, status) {
      // 정지·비활성은 그 사용자의 로그인을 막는다
      if (status !== 'ACTIVE') {
        const ok = await confirmDialog(this.$q, {
          title: `계정 ${USER_STATUS_LABEL[status] || status}`,
          message: `${row.name || '이 사용자'}의 계정을 '${USER_STATUS_LABEL[status] || status}' 상태로 바꿉니다. 바꾸면 로그인할 수 없게 됩니다.`,
          ok: '변경',
          color: 'negative',
        });
        if (!ok) return;
      }
      try {
        await changeUserStatus(row.id, status);
        this.$q.notify({ type: 'info', message: `계정을 '${USER_STATUS_LABEL[status] || status}' 상태로 바꿨습니다.`, position: 'top' });
        await this.loadUsers();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      }
    },
    async assignRole() {
      if (!this.selectedUser || !this.roleToAssign) return;
      if (this.roleNeedsInstitution && !this.roleInstitutionId) {
        this.$q.notify({ type: 'warning', message: `${labelForRole(this.roleToAssign)} 역할을 줄 기관을 골라 주세요.`, position: 'top' });
        return;
      }
      this.rolesLoading = true;
      try {
        await assignUserRole(this.selectedUser.id, {
          role: this.roleToAssign,
          institutionId: this.roleNeedsInstitution ? this.roleInstitutionId : null,
        });
        this.selectedRoles = await listUserRoles(this.selectedUser.id);
        await this.syncRowRoles(this.selectedUser.id);
        this.$q.notify({ type: 'positive', message: `${labelForRole(this.roleToAssign)} 역할을 부여했습니다.`, position: 'top' });
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.rolesLoading = false;
      }
    },
    async revokeRole(roleItem) {
      if (!this.selectedUser || !roleItem?.id) {
        this.$q.notify({ type: 'warning', message: '역할 ID가 없어 해제할 수 없습니다.', position: 'top' });
        return;
      }
      const ok = await confirmDialog(this.$q, {
        title: '역할 해제',
        message: `${this.selectedUser.name || '이 사용자'}의 ${labelForRole(roleItem.role)} 역할을 해제합니다. 해당 콘솔에 들어갈 수 없게 됩니다.`,
        ok: '해제',
        color: 'negative',
      });
      if (!ok) return;
      this.rolesLoading = true;
      try {
        await revokeUserRole(this.selectedUser.id, roleItem.id);
        this.selectedRoles = await listUserRoles(this.selectedUser.id);
        await this.syncRowRoles(this.selectedUser.id);
        this.$q.notify({ type: 'info', message: `${labelForRole(roleItem.role)} 역할을 해제했습니다.`, position: 'top' });
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.rolesLoading = false;
      }
    },
    async linkIdentity() {
      if (!this.selectedUser) return;
      try {
        await linkUserIdentity(this.selectedUser.id, {
          provider: 'LOCAL',
          providerSubject: this.selectedUser.email || this.selectedUser.id,
          externalDid: null,
          verified: true,
        });
        this.selectedIdentities = await listUserIdentities(this.selectedUser.id);
        this.$q.notify({ type: 'positive', message: 'Identity 연결 완료', position: 'top' });
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      }
    },
    async unlinkIdentity(identity) {
      if (!this.selectedUser) return;
      try {
        await unlinkUserIdentity(this.selectedUser.id, identity.id);
        this.selectedIdentities = await listUserIdentities(this.selectedUser.id);
        this.$q.notify({ type: 'info', message: 'Identity 해제 완료', position: 'top' });
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      }
    },
  },
};
</script>

<style scoped>
.user-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px;
  border-bottom: 1px solid var(--db-border);
}

.user-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.user-card__name {
  min-width: 0;
  overflow: hidden;
  font-size: 15px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-card__line {
  overflow: hidden;
  font-size: 13px;
  color: var(--db-text-sub);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-left: -8px;
}

.rounded-borders {
  border-radius: 12px;
}
</style>
