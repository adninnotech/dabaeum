<template>
  <!-- 관리자 한 명 — 역할(부여·회수) · 계정 상태 · 로그인 보안 · 접속기록을 한 창에.
       예전엔 ⋮ 메뉴에 상태 넷과 초기화만 있어, 관리자 권한만 빼려면 계정 전체를 정지·탈퇴해야 했다 -->
  <q-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" @before-show="load">
    <q-card v-if="admin" class="admin-detail">
      <header class="admin-detail__head">
        <div class="admin-detail__who">
          <h2 class="admin-detail__name">
            {{ admin.name || '-' }}
            <span v-if="isSelf" class="admin-detail__me">나</span>
          </h2>
          <p class="admin-detail__sub">
            <span>{{ admin.email || '이메일 없음' }}</span>
          </p>
        </div>
        <button type="button" class="admin-detail__close" aria-label="닫기" @click="$emit('update:modelValue', false)">
          <q-icon name="close" size="22px" aria-hidden="true" />
        </button>
      </header>

      <div class="admin-detail__body">
        <!-- 확인이 필요한 것 — 잠김·임시 비밀번호·변경 주기 -->
        <div v-if="alerts.length" class="admin-detail__alerts">
          <p v-for="alert in alerts" :key="alert.key" class="admin-detail__alert" :class="`admin-detail__alert--${alert.tone}`">
            <q-icon :name="alert.icon" size="18px" aria-hidden="true" />
            <span>{{ alert.text }}</span>
          </p>
        </div>

        <!-- 역할 -->
        <section class="admin-detail__section" aria-labelledby="admin-detail-roles">
          <h3 id="admin-detail-roles" class="admin-detail__h">역할</h3>
          <p v-if="rolesError" class="admin-detail__error" role="alert">{{ rolesError }}</p>
          <div v-else-if="rolesLoading" class="admin-detail__loading">
            <q-spinner size="20px" aria-hidden="true" /> 역할을 불러오는 중…
          </div>
          <ul v-else class="admin-detail__roles">
            <li v-for="item in roleRows" :key="item.id" class="admin-detail__role">
              <span class="admin-detail__role-label">
                {{ item.label }}
                <span v-if="item.institutionName" class="admin-detail__role-org">{{ item.institutionName }}</span>
              </span>
              <q-btn
                v-if="item.revocable"
                flat
                no-caps
                color="negative"
                label="회수"
                class="admin-detail__role-btn"
                :loading="busyRoleId === item.id"
                :disable="Boolean(busyRoleId) || adding"
                :aria-label="`${item.label} 역할 회수`"
                @click="revoke(item)"
              />
              <span v-else-if="isSelf && item.admin" class="admin-detail__role-note">내 역할은 회수할 수 없습니다</span>
            </li>
            <li v-if="!roleRows.length" class="admin-detail__role admin-detail__role--empty">역할이 없습니다.</li>
          </ul>

          <!-- 역할 추가 -->
          <div v-if="!isSelf && !rolesLoading && !rolesError" class="admin-detail__add">
            <template v-if="addOpen">
              <q-select
                v-model="newRole"
                outlined
                dense
                emit-value
                map-options
                :options="ADMIN_ROLE_OPTIONS"
                label="역할"
                class="admin-detail__add-field"
              />
              <q-select
                v-if="newRole === 'INSTITUTION_ADMIN'"
                v-model="newInstitutionId"
                outlined
                dense
                emit-value
                map-options
                use-input
                input-debounce="0"
                :options="filteredInstitutions"
                :loading="institutionsLoading"
                label="소속 기관"
                class="admin-detail__add-field"
                @filter="filterInstitutions"
              >
                <template #no-option>
                  <q-item><q-item-section class="text-grey-7">일치하는 기관이 없습니다.</q-item-section></q-item>
                </template>
              </q-select>
              <div class="admin-detail__add-actions">
                <q-btn flat no-caps label="취소" :disable="adding" @click="addOpen = false" />
                <q-btn unelevated no-caps color="primary" label="역할 추가" :loading="adding" :disable="!canAdd" @click="addRole" />
              </div>
            </template>
            <q-btn v-else outline no-caps color="primary" icon="add" label="역할 추가" class="admin-detail__add-open" @click="openAdd" />
          </div>
        </section>

        <!-- 계정 상태 — 자주 쓰는 활성·정지만 크게, 비활성·탈퇴는 접어 둔다 -->
        <section v-if="!isSelf" class="admin-detail__section" aria-labelledby="admin-detail-status">
          <h3 id="admin-detail-status" class="admin-detail__h">계정 상태</h3>
          <div class="admin-detail__status" role="group" aria-label="계정 상태 바꾸기">
            <button
              v-for="option in MAIN_STATUS"
              :key="option.value"
              type="button"
              class="admin-detail__status-btn"
              :class="[`admin-detail__status-btn--${option.tone}`, { 'admin-detail__status-btn--on': admin.status === option.value }]"
              :aria-pressed="admin.status === option.value"
              @click="pickStatus(option.value)"
            >
              <q-icon v-if="admin.status === option.value" name="check" size="16px" aria-hidden="true" />
              {{ option.label }}
            </button>
          </div>
          <p class="admin-detail__hint">정지하면 로그인할 수 없습니다. 관리 권한만 빼려면 위 역할을 회수하세요.</p>
          <p v-if="otherStatusLabel" class="admin-detail__hint">지금 상태: <strong>{{ otherStatusLabel }}</strong></p>
          <button type="button" class="admin-detail__more" :aria-expanded="moreOpen" @click="moreOpen = !moreOpen">
            {{ moreOpen ? '접기' : '비활성·탈퇴 처리' }}
            <q-icon :name="moreOpen ? 'expand_less' : 'expand_more'" size="18px" aria-hidden="true" />
          </button>
          <div v-if="moreOpen" class="admin-detail__more-body">
            <q-btn
              v-for="option in MORE_STATUS"
              :key="option.value"
              outline
              no-caps
              :color="option.value === 'WITHDRAWN' ? 'negative' : 'grey-8'"
              :label="`${option.label}(으)로`"
              :disable="admin.status === option.value"
              class="admin-detail__more-btn"
              @click="pickStatus(option.value)"
            />
          </div>
        </section>

        <!-- 로그인·비밀번호 -->
        <section class="admin-detail__section" aria-labelledby="admin-detail-security">
          <h3 id="admin-detail-security" class="admin-detail__h">로그인·비밀번호</h3>
          <dl class="admin-detail__facts">
            <div><dt>최근 로그인</dt><dd>{{ formatDateMinuteKst(admin.lastLoginAt, '기록 없음') }}</dd></div>
            <div><dt>비밀번호 변경</dt><dd>{{ admin.passwordChangedAt ? formatDateKst(admin.passwordChangedAt) : '임시 비밀번호' }}</dd></div>
          </dl>
          <router-link v-if="isSelf" to="/admin/mypage" class="admin-detail__link">내 정보에서 비밀번호 바꾸기</router-link>
          <q-btn
            v-else
            :outline="!admin.lockedUntil"
            :unelevated="!!admin.lockedUntil"
            no-caps
            :color="admin.lockedUntil ? 'primary' : 'grey-8'"
            :label="admin.lockedUntil ? '잠금 풀기 (비밀번호 초기화)' : '비밀번호 초기화'"
            class="admin-detail__reset"
            @click="$emit('reset-password', admin)"
          />
        </section>
      </div>

      <footer class="admin-detail__foot">
        <router-link :to="accessLogsTo" class="admin-detail__link">
          <q-icon name="o_manage_search" size="18px" aria-hidden="true" />
          이 관리자의 접속기록 보기
        </router-link>
      </footer>
    </q-card>
  </q-dialog>
</template>

<script>
import { ROLE_LABELS, ROLES } from '@/access/roles';
import { listInstitutions } from '@/services/institution-api';
import { assignUserRole, listUserRoles, revokeUserRole } from '@/services/user-api';
import { ADMIN_PASSWORD_MAX_AGE_DAYS, ADMIN_ROLE_OPTIONS } from '@/utils/admin-labels';
import { confirmDialog } from '@/utils/confirm-dialog';
import { daysFromToday, formatDateKst, formatDateMinuteKst, formatTimeKst } from '@/utils/datetime';
import { toInstitutionOptions } from '@/utils/institution-option';

const ADMIN_ROLE_SET = new Set([ROLES.PLATFORM_ADMIN, ROLES.INSTITUTION_ADMIN]);
const MAIN_STATUS = [
  { value: 'ACTIVE', label: '활성', tone: 'ok' },
  { value: 'SUSPENDED', label: '정지', tone: 'stop' },
];
const MORE_STATUS = [
  { value: 'DORMANT', label: '비활성' },
  { value: 'WITHDRAWN', label: '탈퇴' },
];
const INSTITUTION_PAGE_SIZE = 100;

export default {
  name: 'AdministratorDetailDialog',
  props: {
    modelValue: { type: Boolean, default: false },
    /** Administrator 행(+ id) */
    admin: { type: Object, default: null },
    currentUserId: { type: String, default: '' },
  },
  /** changed: 역할이 바뀜(목록 다시 읽기) · change-status(admin, status) · reset-password(admin) — 확인 창은 화면 믹스인이 띄운다 */
  emits: ['update:modelValue', 'changed', 'change-status', 'reset-password'],
  data() {
    return {
      ADMIN_ROLE_OPTIONS,
      MAIN_STATUS,
      MORE_STATUS,
      roles: [],
      rolesLoading: false,
      rolesError: '',
      busyRoleId: '',
      addOpen: false,
      adding: false,
      newRole: ROLES.INSTITUTION_ADMIN,
      newInstitutionId: null,
      institutions: [],
      filteredInstitutions: [],
      institutionsLoading: false,
      moreOpen: false,
    };
  },
  computed: {
    isSelf() {
      return Boolean(this.admin?.id) && this.admin.id === this.currentUserId;
    },
    /** listUserRoles 응답(id 있음)에 목록의 기관명을 붙인다 */
    roleRows() {
      const names = new Map((this.admin?.roles || []).map((r) => [`${r.role}:${r.institutionId || ''}`, r.institutionName]));
      return this.roles.map((r) => ({
        id: r.id,
        role: r.role,
        label: ROLE_LABELS[r.role] || r.role,
        institutionName: names.get(`${r.role}:${r.institutionId || ''}`) || '',
        admin: ADMIN_ROLE_SET.has(r.role),
        // 학습자·강사 역할은 이 화면 몫이 아니다. 내 관리자 역할은 스스로 잠기지 않게 막는다
        revocable: ADMIN_ROLE_SET.has(r.role) && !this.isSelf,
      }));
    },
    canAdd() {
      if (!this.newRole) return false;
      return this.newRole !== ROLES.INSTITUTION_ADMIN || Boolean(this.newInstitutionId);
    },
    otherStatusLabel() {
      const found = MORE_STATUS.find((item) => item.value === this.admin?.status);
      return found ? found.label : '';
    },
    alerts() {
      const a = this.admin;
      if (!a) return [];
      const list = [];
      if (a.lockedUntil) {
        list.push({ key: 'lock', tone: 'danger', icon: 'o_lock', text: `비밀번호를 여러 번 틀려 ${formatTimeKst(a.lockedUntil)}까지 로그인이 잠겼습니다.` });
      }
      if (!a.passwordChangedAt) {
        list.push({ key: 'temp', tone: 'warn', icon: 'o_key', text: '임시 비밀번호 상태입니다. 첫 로그인 때 바꾸도록 안내해 주세요.' });
      } else {
        const days = daysFromToday(a.passwordChangedAt);
        if (days !== null && -days >= ADMIN_PASSWORD_MAX_AGE_DAYS) {
          list.push({ key: 'stale', tone: 'warn', icon: 'o_schedule', text: `비밀번호를 바꾼 지 ${-days}일 지났습니다(기준 ${ADMIN_PASSWORD_MAX_AGE_DAYS}일).` });
        }
      }
      return list;
    },
    accessLogsTo() {
      return { path: '/admin/access-logs', query: { actorId: this.admin?.id } };
    },
  },
  methods: {
    formatDateKst,
    formatDateMinuteKst,
    async load() {
      this.addOpen = false;
      this.moreOpen = false;
      this.roles = [];
      this.rolesError = '';
      if (!this.admin?.id) return;
      this.rolesLoading = true;
      try {
        this.roles = await listUserRoles(this.admin.id);
      } catch (error) {
        this.rolesError = error?.message || '역할을 불러오지 못했습니다.';
      } finally {
        this.rolesLoading = false;
      }
    },
    pickStatus(status) {
      if (status === this.admin?.status) return;
      this.$emit('change-status', this.admin, status);
    },
    openAdd() {
      this.addOpen = true;
      this.newRole = ROLES.INSTITUTION_ADMIN;
      this.newInstitutionId = null;
      if (!this.institutions.length) this.loadInstitutions();
    },
    async loadInstitutions() {
      this.institutionsLoading = true;
      try {
        const result = await listInstitutions({ page: 0, size: INSTITUTION_PAGE_SIZE });
        this.institutions = toInstitutionOptions(result.data);
        this.filteredInstitutions = this.institutions;
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error?.message || '기관 목록을 불러오지 못했습니다.', position: 'top' });
      } finally {
        this.institutionsLoading = false;
      }
    },
    filterInstitutions(value, update) {
      update(() => {
        const needle = String(value || '').trim().toLowerCase();
        this.filteredInstitutions = needle
          ? this.institutions.filter((item) => String(item.label).toLowerCase().includes(needle))
          : this.institutions;
      });
    },
    async addRole() {
      if (!this.canAdd || !this.admin?.id) return;
      this.adding = true;
      try {
        await assignUserRole(this.admin.id, {
          role: this.newRole,
          institutionId: this.newRole === ROLES.INSTITUTION_ADMIN ? this.newInstitutionId : null,
        });
        this.$q.notify({ type: 'positive', message: `${this.admin.name} 님에게 '${ROLE_LABELS[this.newRole]}' 역할을 줬습니다.`, position: 'top' });
        this.addOpen = false;
        this.$emit('changed');
        await this.load();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error?.message || '역할을 추가하지 못했습니다.', position: 'top' });
      } finally {
        this.adding = false;
      }
    },
    async revoke(item) {
      if (!item.revocable || !this.admin?.id) return;
      const where = item.institutionName ? ` (${item.institutionName})` : '';
      const lastAdmin = this.roleRows.filter((r) => r.admin).length === 1;
      const ok = await confirmDialog(this.$q, {
        title: '역할 회수',
        message: `${this.admin.name} 님의 '${item.label}${where}' 역할을 회수합니다.${
          lastAdmin ? ' 마지막 관리자 역할이라 회수하면 이 목록에서 빠지고 관리 화면에 들어올 수 없습니다.' : ''
        } 계정과 수강 기록은 그대로입니다.`,
        ok: '회수',
        color: 'negative',
        cancel: '닫기',
      });
      if (!ok) return;
      this.busyRoleId = item.id;
      try {
        await revokeUserRole(this.admin.id, item.id);
        this.$q.notify({ type: 'positive', message: `'${item.label}${where}' 역할을 회수했습니다.`, position: 'top' });
        this.$emit('changed');
        if (lastAdmin) this.$emit('update:modelValue', false);
        else await this.load();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error?.message || '역할을 회수하지 못했습니다.', position: 'top' });
      } finally {
        this.busyRoleId = '';
      }
    },
  },
};
</script>

<style scoped>
/* Hallmark · component: dialog · genre: modern-minimal · theme: project tokens (--db-*)
 * states: default · hover · focus · active · disabled · loading · error · success
 */
.admin-detail {
  width: 560px;
  max-width: calc(100vw - 32px);
  border-radius: 12px;
  background: var(--db-bg-white);
}

.admin-detail__head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 20px 12px 12px 24px;
  border-bottom: 1px solid var(--db-border);
}

.admin-detail__who {
  flex: 1;
  min-width: 0;
}

.admin-detail__name {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--db-text-title);
}

.admin-detail__me {
  padding: 0 8px;
  border-radius: 4px;
  background: var(--db-accent-soft);
  font-size: 14px;
  line-height: 24px;
  font-weight: 600;
  color: var(--db-accent);
}

.admin-detail__sub {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin: 4px 0 0;
  font-size: 15px;
  color: var(--db-text-sub);
  overflow-wrap: anywhere;
}

.admin-detail__close {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border: 0;
  border-radius: 8px;
  background: none;
  color: var(--db-text-sub);
  cursor: pointer;
}

.admin-detail__close:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: -2px;
}

.admin-detail__body {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 20px 24px;
}

.admin-detail__alerts {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.admin-detail__alert {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 15px;
  line-height: 1.5;
  word-break: keep-all;
}

.admin-detail__alert .q-icon {
  flex-shrink: 0;
  margin-top: 2px;
}

.admin-detail__alert--danger {
  background: var(--db-status-pink-soft);
  color: var(--db-status-pink);
}

.admin-detail__alert--warn {
  background: var(--db-status-orange-soft);
  color: var(--db-status-orange-text);
}

.admin-detail__section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.admin-detail__h {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--db-text-title);
}

.admin-detail__loading,
.admin-detail__error {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 15px;
  color: var(--db-text-sub);
}

.admin-detail__error {
  color: var(--db-status-pink);
}

.admin-detail__roles {
  margin: 0;
  padding: 0;
  list-style: none;
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.admin-detail__role {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 52px;
  padding: 4px 8px 4px 14px;
  font-size: 15px;
  color: var(--db-text-title);
}

.admin-detail__role + .admin-detail__role {
  border-top: 1px solid var(--db-border);
}

.admin-detail__role--empty {
  color: var(--db-text-sub);
}

.admin-detail__role-label {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 8px;
  flex: 1;
  min-width: 0;
  font-weight: 600;
}

.admin-detail__role-org {
  font-weight: 400;
  color: var(--db-text-sub);
  overflow-wrap: anywhere;
}

.admin-detail__role-btn {
  min-height: 44px;
  font-weight: 600;
}

.admin-detail__role-note {
  font-size: 14px;
  color: var(--db-text-sub);
}

.admin-detail__add {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.admin-detail__add-open {
  align-self: flex-start;
  min-height: 44px;
}

.admin-detail__add-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.admin-detail__add-actions .q-btn {
  min-height: 44px;
}

/* 계정 상태 두 칸 */
.admin-detail__status {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border: 1px solid var(--db-border);
  border-radius: 8px;
  overflow: hidden;
}

.admin-detail__status-btn {
  --tone: var(--db-status-green-text);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 48px;
  border: 0;
  background: transparent;
  font-family: inherit;
  font-size: 16px;
  font-weight: 500;
  color: var(--db-text-body);
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.admin-detail__status-btn + .admin-detail__status-btn {
  border-left: 1px solid var(--db-border);
}

.admin-detail__status-btn--stop {
  --tone: var(--db-status-pink);
}

.admin-detail__status-btn--on {
  background: color-mix(in srgb, var(--tone) 14%, transparent);
  color: var(--tone);
  font-weight: 700;
  cursor: default;
}

.admin-detail__status-btn:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: -2px;
}

.admin-detail__status-btn:active:not(.admin-detail__status-btn--on) {
  background: var(--db-bg-subtle);
}

.admin-detail__hint {
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.admin-detail__more {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  gap: 2px;
  min-height: 44px;
  padding: 0;
  border: 0;
  background: none;
  font-family: inherit;
  font-size: 15px;
  font-weight: 600;
  color: var(--db-text-sub);
  cursor: pointer;
}

.admin-detail__more:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
  border-radius: 4px;
}

.admin-detail__more-body {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.admin-detail__more-btn {
  min-height: 44px;
}

.admin-detail__facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
}

.admin-detail__facts dt {
  font-size: 14px;
  color: var(--db-text-sub);
}

.admin-detail__facts dd {
  margin: 2px 0 0;
  font-size: 15px;
  color: var(--db-text-title);
  font-variant-numeric: tabular-nums;
}

.admin-detail__reset {
  align-self: flex-start;
  min-height: 44px;
  font-weight: 600;
}

.admin-detail__foot {
  padding: 12px 24px 20px;
  border-top: 1px solid var(--db-border);
}

.admin-detail__link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 44px;
  font-size: 15px;
  font-weight: 600;
  color: var(--db-accent);
  text-decoration: none;
}

.admin-detail__link:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
  border-radius: 4px;
}

@media (hover: hover) {
  .admin-detail__close:hover {
    background: var(--db-bg-subtle);
    color: var(--db-text-title);
  }

  .admin-detail__status-btn:not(.admin-detail__status-btn--on):hover {
    background: var(--db-bg-subtle);
    color: var(--tone);
  }

  .admin-detail__more:hover,
  .admin-detail__link:hover {
    text-decoration: underline;
  }
}

@media (prefers-reduced-motion: reduce) {
  .admin-detail__status-btn {
    transition: none;
  }
}

@media (max-width: 599px) {
  .admin-detail__head {
    padding-left: 20px;
  }

  .admin-detail__body {
    padding: 16px 20px;
  }

  .admin-detail__foot {
    padding: 8px 20px 16px;
  }

  .admin-detail__facts {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
