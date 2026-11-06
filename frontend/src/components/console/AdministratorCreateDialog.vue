<template>
  <q-dialog
    :model-value="modelValue"
    :persistent="!!created || saving"
    @update:model-value="onDialogToggle"
    @hide="reset"
  >
    <q-card class="admin-create">
      <q-card-section class="admin-create__head">
        <div class="text-h6">{{ created ? '관리자 계정을 만들었습니다' : '관리자 추가' }}</div>
        <q-btn v-if="!created" v-close-popup flat round dense icon="close" aria-label="닫기" />
      </q-card-section>
      <q-separator />

      <!-- 1단계: 입력 -->
      <q-card-section v-if="!created" class="admin-create__body">
        <q-input
          v-model="name"
          outlined
          dense
          label="이름 *"
          maxlength="100"
          :error="!!errors.name"
          :error-message="errors.name"
        />
        <q-input
          v-model="email"
          outlined
          dense
          type="email"
          label="이메일 (로그인 아이디) *"
          maxlength="320"
          :error="!!errors.email"
          :error-message="errors.email"
        />
        <q-select
          v-model="role"
          outlined
          dense
          emit-value
          map-options
          :options="ADMIN_ROLE_OPTIONS"
          label="역할 *"
        />
        <q-select
          v-if="role === 'INSTITUTION_ADMIN'"
          v-model="institutionId"
          outlined
          dense
          emit-value
          map-options
          use-input
          input-debounce="0"
          :options="filteredInstitutions"
          :loading="institutionsLoading"
          label="소속 기관 *"
          :error="!!errors.institutionId"
          :error-message="errors.institutionId"
          @filter="filterInstitutions"
        >
          <template #no-option>
            <q-item><q-item-section class="text-grey-7">일치하는 기관이 없습니다.</q-item-section></q-item>
          </template>
        </q-select>
        <p class="admin-create__note">
          임시 비밀번호가 함께 발급됩니다. 첫 로그인 후 비밀번호를 바꾸도록 안내됩니다.
        </p>
      </q-card-section>

      <!-- 2단계: 임시 비밀번호 1회 표시. 닫으면 지우고 다시 보여 주지 않는다 -->
      <q-card-section v-else class="admin-create__body">
        <div class="admin-create__who">
          <span class="admin-create__who-name">{{ created.administrator?.name || name }}</span>
          <span class="admin-create__who-mail">{{ created.administrator?.email || email }}</span>
        </div>

        <div class="admin-create__secret-label">임시 비밀번호</div>
        <div class="admin-create__secret">
          <code class="admin-create__secret-value">{{ created.temporaryPassword }}</code>
          <q-btn
            unelevated
            no-caps
            dense
            color="primary"
            class="admin-create__copy"
            :icon="copied ? 'check' : 'content_copy'"
            :label="copied ? '복사됨' : '복사'"
            @click="copyPassword"
          />
        </div>
        <p class="admin-create__warn" role="alert">
          이 창을 닫으면 임시 비밀번호를 다시 볼 수 없습니다. 복사해서 본인에게 전달해 주세요.
        </p>
      </q-card-section>

      <q-card-actions align="right" class="admin-create__actions">
        <template v-if="!created">
          <q-btn v-close-popup flat no-caps label="취소" :disable="saving" />
          <q-btn unelevated no-caps color="primary" label="만들기" :loading="saving" @click="submit" />
        </template>
        <q-btn
          v-else
          unelevated
          no-caps
          color="primary"
          :label="copied ? '닫기' : '복사하지 않고 닫기'"
          @click="close"
        />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import { copyToClipboard } from 'quasar';
import { createAdministrator } from '@/services/admin-api';
import { listInstitutions } from '@/services/institution-api';
import { ADMIN_ROLE_OPTIONS } from '@/utils/admin-labels';
import { toInstitutionOptions } from '@/utils/institution-option';

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const INSTITUTION_PAGE_SIZE = 100;

const CREATE_ERROR_MESSAGE = {
  409: '이미 가입된 이메일입니다. 기존 계정이면 사용자 관리에서 역할을 부여해 주세요.',
  404: '선택한 기관을 찾을 수 없습니다. 기관 목록을 새로 불러와 주세요.',
  422: '입력값을 확인해 주세요. 기관 관리자는 소속 기관이 필요합니다.',
};

function validate({ name, email, role, institutionId }) {
  const errors = {};
  if (!name.trim()) errors.name = '이름을 입력해 주세요.';
  if (!EMAIL.test(email.trim())) errors.email = '올바른 이메일을 입력해 주세요.';
  if (role === 'INSTITUTION_ADMIN' && !institutionId) errors.institutionId = '소속 기관을 선택해 주세요.';
  return errors;
}

/* 관리자 계정 생성 — POST /admin/administrators. 피그마에 없는 화면이라 기존 콘솔 다이얼로그(q-card)를 따른다 */
export default {
  name: 'AdministratorCreateDialog',
  props: {
    modelValue: { type: Boolean, default: false },
  },
  emits: ['update:modelValue', 'created'],
  data() {
    return {
      ADMIN_ROLE_OPTIONS,
      name: '',
      email: '',
      role: 'PLATFORM_ADMIN',
      institutionId: null,
      errors: {},
      saving: false,
      created: null,
      copied: false,
      institutions: [],
      filteredInstitutions: [],
      institutionsLoading: false,
    };
  },
  watch: {
    role(value) {
      if (value === 'INSTITUTION_ADMIN' && !this.institutions.length) this.loadInstitutions();
    },
  },
  methods: {
    onDialogToggle(open) {
      // 임시 비밀번호가 떠 있는 동안은 바깥 클릭·ESC 로 닫히지 않는다 (persistent) — 버튼으로만 닫는다
      if (!open && this.created) return;
      this.$emit('update:modelValue', open);
    },
    reset() {
      this.name = '';
      this.email = '';
      this.role = 'PLATFORM_ADMIN';
      this.institutionId = null;
      this.errors = {};
      this.created = null;
      this.copied = false;
    },
    close() {
      this.$emit('update:modelValue', false);
    },
    async loadInstitutions() {
      this.institutionsLoading = true;
      try {
        const result = await listInstitutions({ page: 0, size: INSTITUTION_PAGE_SIZE });
        this.institutions = toInstitutionOptions(result.data);
        this.filteredInstitutions = this.institutions;
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error?.message || '기관 목록을 불러오지 못했습니다.',
          position: 'top',
        });
      } finally {
        this.institutionsLoading = false;
      }
    },
    filterInstitutions(text, update) {
      update(() => {
        const keyword = String(text || '').trim().toLowerCase();
        this.filteredInstitutions = keyword
          ? this.institutions.filter((item) => item.label.toLowerCase().includes(keyword))
          : this.institutions;
      });
    },
    async submit() {
      const form = {
        name: this.name,
        email: this.email,
        role: this.role,
        institutionId: this.institutionId,
      };
      this.errors = validate(form);
      if (Object.keys(this.errors).length) return;

      this.saving = true;
      try {
        this.created = await createAdministrator({
          ...form,
          name: form.name.trim(),
          email: form.email.trim(),
        });
        this.$emit('created', this.created.administrator || null);
      } catch (error) {
        if (error?.status === 409) this.errors = { email: '이미 가입된 이메일입니다.' };
        this.$q.notify({
          type: 'negative',
          message: CREATE_ERROR_MESSAGE[error?.status] || error?.message || '계정을 만들지 못했습니다.',
          position: 'top',
        });
      } finally {
        this.saving = false;
      }
    },
    async copyPassword() {
      try {
        await copyToClipboard(this.created?.temporaryPassword || '');
        this.copied = true;
      } catch {
        this.$q.notify({
          type: 'warning',
          message: '복사하지 못했습니다. 직접 선택해서 복사해 주세요.',
          position: 'top',
        });
      }
    },
  },
};
</script>

<style scoped>
.admin-create {
  width: 460px;
  max-width: 92vw;
}

.admin-create__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.admin-create__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.admin-create__note {
  margin: 0;
  font-size: 13px;
  line-height: 18px;
  color: var(--db-text-sub);
}

.admin-create__who {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.admin-create__who-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--db-text-title);
}

.admin-create__who-mail {
  font-size: 14px;
  color: var(--db-text-sub);
  overflow-wrap: anywhere;
}

.admin-create__secret-label {
  margin-top: 4px;
  font-size: 13px;
  font-weight: 600;
  color: var(--db-text-body);
}

.admin-create__secret {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 8px 8px 14px;
  background: var(--db-bg-page);
  border: 1px solid var(--db-border);
  border-radius: 6px;
}

.admin-create__secret-value {
  flex: 1;
  min-width: 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
  user-select: all;
}

.admin-create__copy {
  flex-shrink: 0;
  padding: 0 12px;
}

.admin-create__warn {
  margin: 0;
  font-size: 13px;
  line-height: 18px;
  color: var(--db-status-pink);
  word-break: keep-all;
}

.admin-create__actions {
  padding: 8px 16px 16px;
}
</style>
