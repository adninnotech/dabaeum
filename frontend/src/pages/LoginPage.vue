<template>
  <q-layout view="hHh lpR fFf">
    <q-page-container>
      <q-page class="login-page flex flex-center">
        <div class="login-wrap">
          <div class="text-h5 text-weight-bold text-grey-9">배움이 더 쉬워지는 곳</div>
          <div class="text-h4 text-weight-bolder q-mt-xs">
            <span class="text-primary">다배움</span><span class="text-grey-9">과 함께하세요</span>
          </div>
          <div class="text-body2 text-grey-7 q-mt-sm q-mb-lg">
            로그인하여 더 많은 강좌와 학습 관리 기능을 사용해보세요
          </div>

          <q-banner v-if="isDemoSession" dense class="bg-blue-1 text-primary q-mb-md rounded-borders">
            데모 세션으로 로그인되어 있습니다. 역할 버튼을 다시 누르면 전환됩니다.
          </q-banner>

          <div class="text-subtitle2 text-weight-bold q-mb-sm">역할별 임시 로그인</div>
          <div class="row q-col-gutter-sm q-mb-lg">
            <div v-for="item in demoAccounts" :key="item.role" class="col-12 col-sm-6">
              <q-btn
                outline
                class="full-width role-btn"
                :color="item.color"
                :loading="loading && loadingRole === item.role"
                :disable="loading"
                @click="loginDemo(item.role)"
              >
                <div class="text-left full-width">
                  <div class="text-weight-bold">{{ item.label }}</div>
                  <div class="text-caption">{{ item.description }}</div>
                </div>
              </q-btn>
            </div>
          </div>

          <q-separator class="q-mb-lg" />

          <q-form @submit.prevent="submitLogin">
            <div class="text-caption text-grey-8 q-mb-xs">이메일</div>
            <q-input
              v-model="loginId"
              outlined
              dense
              placeholder="이메일"
              :disable="loading"
              class="q-mb-md"
            />

            <div class="text-caption text-grey-8 q-mb-xs">비밀번호</div>
            <q-input
              v-model="password"
              outlined
              dense
              :type="showPassword ? 'text' : 'password'"
              placeholder="비밀번호를 입력해주세요"
              :disable="loading"
            >
              <template #append>
                <q-icon
                  :name="showPassword ? 'visibility' : 'visibility_off'"
                  class="cursor-pointer"
                  @click="showPassword = !showPassword"
                />
              </template>
            </q-input>
            <div class="row justify-end q-mt-xs">
              <q-btn flat dense color="primary" label="비밀번호 찾기" @click="onFindPassword" />
            </div>

            <div v-if="loginError" class="text-negative text-caption q-mt-sm">{{ loginError }}</div>

            <q-btn
              type="submit"
              class="full-width q-mt-md"
              color="primary"
              unelevated
              label="로그인"
              :loading="loading"
            />
          </q-form>

          <div class="text-center q-mt-md text-body2">
            아직 계정이 없으신가요?
            <router-link class="text-primary text-weight-bold" to="/signup">회원가입 하기</router-link>
          </div>

          <q-expansion-item
            class="q-mt-lg"
            dense
            label="API 사용자로 로그인 (테스트)"
            header-class="text-grey-7"
          >
            <div class="q-pt-sm">
              <div class="row items-center justify-between q-mb-sm">
                <div class="text-caption">사용자 선택</div>
                <q-btn flat dense color="primary" icon="refresh" :loading="loadingUsers" @click="loadUsers" />
              </div>
              <q-banner v-if="usersError" dense class="bg-red-1 text-negative q-mb-sm">{{ usersError }}</q-banner>
              <q-list bordered dense class="user-list rounded-borders">
                <q-inner-loading :showing="loadingUsers" />
                <q-item
                  v-for="row in filteredUsers"
                  :key="row.id"
                  clickable
                  :active="selectedUserId === row.id"
                  active-class="bg-blue-1"
                  @click="selectUser(row)"
                >
                  <q-item-section>
                    <q-item-label>{{ row.name || '-' }}</q-item-label>
                    <q-item-label caption>{{ row.email || row.id }}</q-item-label>
                    <div class="row q-gutter-xs">
                      <q-badge v-for="role in row.roles || []" :key="role" outline color="primary">{{ role }}</q-badge>
                    </div>
                  </q-item-section>
                </q-item>
              </q-list>
              <q-btn
                class="full-width q-mt-sm"
                outline
                color="primary"
                label="선택한 API 사용자로 로그인"
                :disable="!selectedUserId || loading"
                :loading="loading"
                @click="loginSelectedUser"
              />
            </div>
          </q-expansion-item>

          <div class="text-center text-caption text-grey-6 q-mt-xl">© 2025 Dabaeum. All rights reserved.</div>
        </div>
      </q-page>
    </q-page-container>
  </q-layout>
</template>

<script>
import { mapActions, mapState } from 'pinia';
import { Notify } from 'quasar';
import { DEMO_ROLE_ACCOUNTS } from '@/config/demo-users';
import { listUserRoles, listUsers } from '@/services/user-api';
import { normalizeRoles } from '@/access/permissions';
import { useAuthStore } from '@/stores/auth-store';
import { homePathAfterLogin, homePathForRole } from '@/access/roles';

export default {
  name: 'LoginPage',
  data() {
    return {
      loginId: '',
      password: '',
      showPassword: false,
      loading: false,
      loadingRole: '',
      loadingUsers: false,
      usersError: '',
      users: [],
      userKeyword: '',
      selectedUserId: '',
      demoAccounts: DEMO_ROLE_ACCOUNTS,
    };
  },
  computed: {
    ...mapState(useAuthStore, ['loginError', 'isDemoSession', 'userRoles', 'primaryRole']),
    filteredUsers() {
      const q = this.userKeyword.trim().toLowerCase();
      if (!q) return this.users;
      return this.users.filter((u) => {
        const hay = `${u.name || ''} ${u.email || ''} ${u.id || ''} ${(u.roles || []).join(' ')}`.toLowerCase();
        return hay.includes(q);
      });
    },
  },
  mounted() {
    this.loadUsers();
  },
  methods: {
    ...mapActions(useAuthStore, ['login', 'loginAsUser', 'loginAsDemoRole']),
    async loadUsers() {
      this.loadingUsers = true;
      this.usersError = '';
      try {
        const page = await listUsers({ page: 0, size: 50 });
        const rows = Array.isArray(page?.data) ? page.data : Array.isArray(page) ? page : [];
        const withRoles = await Promise.all(
          rows.map(async (row) => {
            try {
              const roleRows = await listUserRoles(row.id);
              const roles = normalizeRoles(roleRows);
              return { ...row, roles };
            } catch {
              return { ...row, roles: [] };
            }
          }),
        );
        this.users = withRoles;
      } catch (error) {
        this.usersError = error.message || '사용자 목록을 불러오지 못했습니다.';
        this.users = [];
      } finally {
        this.loadingUsers = false;
      }
    },
    selectUser(row) {
      this.selectedUserId = row.id;
      this.loginId = row.email || row.name || row.id;
    },
    onFindPassword() {
      Notify.create({ type: 'info', message: '비밀번호 찾기 API가 아직 없습니다. (docs/unimplemented.md)' });
    },
    async loginDemo(role) {
      this.loading = true;
      this.loadingRole = role;
      try {
        const ok = await this.loginAsDemoRole(role);
        if (ok) this.redirectAfterLogin(role);
      } finally {
        this.loading = false;
        this.loadingRole = '';
      }
    },
    async loginSelectedUser() {
      this.loading = true;
      try {
        const ok = await this.loginAsUser(this.selectedUserId);
        if (ok) this.redirectAfterLogin();
      } finally {
        this.loading = false;
      }
    },
    async submitLogin() {
      this.loading = true;
      try {
        const ok = await this.login({
          email: this.loginId,
          password: this.password,
          userId: this.selectedUserId || undefined,
        });
        if (ok) this.redirectAfterLogin();
      } finally {
        this.loading = false;
      }
    },
    redirectAfterLogin(forcedRole) {
      const redirect = this.$route.query.redirect;
      if (redirect) {
        this.$router.replace(String(redirect));
        return;
      }
      const path = forcedRole
        ? homePathForRole(forcedRole)
        : homePathAfterLogin(this.userRoles, this.primaryRole);
      this.$router.replace(path);
    },
  },
};
</script>

<style scoped>
.login-page {
  background: #fff;
  min-height: 100vh;
  padding: 24px;
}
.login-wrap {
  width: 100%;
  max-width: 420px;
}
.role-btn {
  min-height: 64px;
  justify-content: flex-start;
  padding: 10px 14px;
}
.user-list {
  max-height: 240px;
  overflow: auto;
}
</style>
