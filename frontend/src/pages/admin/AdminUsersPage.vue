<template>
  <q-page padding>
    <div class="app-page-header row items-center justify-between q-mb-lg">
      <div>
        <div class="app-page-header__title text-h5 text-weight-bold">사용자 관리</div>
        <div class="app-page-header__subtitle text-grey-7">실제 API 연동 · Bearer adninnotech</div>
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
    >
      <template #body-cell-status="props">
        <q-td :props="props">
          <StatusBadge :status="props.row.status" />
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
              {{ role }}
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
      <q-card style="min-width: 440px; max-width: 520px">
        <q-card-section class="text-h6">{{ formMode === 'create' ? '사용자 등록' : '사용자 수정' }}</q-card-section>
        <q-card-section class="q-gutter-md">
          <q-input v-model="form.name" outlined dense label="name *" />
          <q-input v-model="form.email" outlined dense type="email" label="email" />
          <q-input v-model="form.phone" outlined dense label="phone" />
          <q-input v-model="form.birthDate" outlined dense label="birthDate (YYYY-MM-DD)" mask="####-##-##" />
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
          <div class="text-body2 q-mb-md">{{ selectedUser.name }} ({{ selectedUser.email || selectedUser.id }})</div>
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
              {{ roleItem.role || roleItem }}
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
import { formatDateKst, formatDateTimeKst, qFormatDateKst } from '@/utils/datetime';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import StatusBadge from '@/components/StatusBadge.vue';
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
import { USER_STATUS_OPTIONS } from '@/utils/status';

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
      statusOptions: USER_STATUS_OPTIONS,
      statusFilterOptions: USER_STATUS_OPTIONS,
      roleOptions: [
        { label: 'LEARNER', value: 'LEARNER' },
        { label: 'INSTRUCTOR', value: 'INSTRUCTOR' },
        { label: 'INSTITUTION_ADMIN', value: 'INSTITUTION_ADMIN' },
        { label: 'PLATFORM_ADMIN', value: 'PLATFORM_ADMIN' },
      ],
      columns: [
        { name: 'name', label: '이름', field: 'name', align: 'left', sortable: true },
        { name: 'email', label: '이메일', field: 'email', align: 'left' },
        { name: 'phone', label: '휴대폰', field: 'phone', align: 'left' },
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
        { label: 'name', value: u.name },
        { label: 'email', value: u.email || '-' },
        { label: 'phone', value: u.phone || '-' },
        { label: 'birthDate', value: formatDateKst(u.birthDate) },
        { label: 'status', value: u.status },
        { label: 'roles', value: (u.roles || []).join(', ') || '-' },
        { label: 'createdAt', value: formatDateTimeKst(u.createdAt) },
        { label: 'updatedAt', value: formatDateTimeKst(u.updatedAt) },
      ];
    },
  },
  mounted() {
    this.loadUsers();
  },
  methods: {
    roleNames(roleItems) {
      if (!Array.isArray(roleItems)) return [];
      return roleItems
        .map((item) => (typeof item === 'string' ? item : item?.role))
        .filter(Boolean);
    },
    async attachRoles(users) {
      const list = Array.isArray(users) ? users : [];
      return Promise.all(
        list.map(async (user) => {
          try {
            const roles = await listUserRoles(user.id);
            return {
              ...user,
              roles: this.roleNames(roles),
              roleItems: Array.isArray(roles) ? roles : [],
            };
          } catch {
            return { ...user, roles: [], roleItems: [] };
          }
        }),
      );
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
          sort: 'createdAt,desc',
        });
        const users = Array.isArray(result.data) ? result.data : [];
        this.rows = await this.attachRoles(users);
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
          if (this.form.status && this.form.status !== this.formOriginalStatus) {
            await changeUserStatus(this.editingId, this.form.status);
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
      try {
        await changeUserStatus(row.id, status);
        this.$q.notify({ type: 'info', message: `상태 변경: ${status}`, position: 'top' });
        await this.loadUsers();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      }
    },
    async assignRole() {
      if (!this.selectedUser || !this.roleToAssign) return;
      this.rolesLoading = true;
      try {
        await assignUserRole(this.selectedUser.id, {
          role: this.roleToAssign,
          institutionId: null,
        });
        this.selectedRoles = await listUserRoles(this.selectedUser.id);
        await this.syncRowRoles(this.selectedUser.id);
        this.$q.notify({ type: 'positive', message: `역할 부여: ${this.roleToAssign}`, position: 'top' });
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
      this.rolesLoading = true;
      try {
        await revokeUserRole(this.selectedUser.id, roleItem.id);
        this.selectedRoles = await listUserRoles(this.selectedUser.id);
        await this.syncRowRoles(this.selectedUser.id);
        this.$q.notify({ type: 'info', message: `역할 해제: ${roleItem.role}`, position: 'top' });
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
.rounded-borders {
  border-radius: 12px;
}
</style>
