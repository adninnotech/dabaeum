<template>
  <!-- 기관 콘솔 머리 — 플랫폼 관리자·여러 기관 소속 관리자가 "지금 보고 있는 기관"을 고른다.
       고르면 AppConsoleLayout 이 본문을 다시 그려 모든 페이지가 새 기관으로 다시 읽는다 -->
  <div v-if="visible" class="scope-bar" :class="{ 'scope-bar--empty': !currentId }">
    <div class="scope-bar__inner">
      <span id="scope-bar-label" class="scope-bar__label">보고 있는 기관</span>
      <q-select
        :model-value="currentId"
        outlined
        dense
        emit-value
        map-options
        use-input
        fill-input
        hide-selected
        input-debounce="0"
        :options="filteredOptions"
        :loading="loading"
        :placeholder="currentId ? '' : '기관 이름으로 찾기'"
        aria-labelledby="scope-bar-label"
        class="scope-bar__select"
        @filter="filterOptions"
        @update:model-value="pick"
      >
        <template #option="{ itemProps, opt }">
          <q-item v-bind="itemProps">
            <q-item-section>
              <q-item-label>{{ opt.label }}</q-item-label>
              <q-item-label v-if="opt.caption" caption>{{ opt.caption }}</q-item-label>
            </q-item-section>
          </q-item>
        </template>
        <template #no-option>
          <q-item>
            <q-item-section class="text-grey-7">맞는 기관이 없습니다.</q-item-section>
          </q-item>
        </template>
      </q-select>
      <p v-if="errorMessage" class="scope-bar__hint scope-bar__hint--error" role="alert">
        {{ errorMessage }}
        <q-btn flat dense no-caps color="primary" label="다시 시도" @click="loadOptions" />
      </p>
      <p v-else-if="!currentId" class="scope-bar__hint">기관을 고르면 그 기관의 강의·수강·강사 데이터를 봅니다.</p>
    </div>
  </div>
</template>

<script>
import { mapState } from 'pinia';
import { listInstitutions } from '@/services/institution-api';
import { useAuthStore } from '@/stores/auth-store';
import { useConsoleScopeStore } from '@/stores/console-scope-store';
import { toInstitutionOptions } from '@/utils/institution-option';
import { STATUS_LABEL } from '@/utils/status';

const INSTITUTION_PAGE_SIZE = 100; // 계약 최대 size

function toOption(item) {
  const extra = [item.region, item.institutionCode];
  if (item.status && item.status !== 'ACTIVE') extra.push(STATUS_LABEL[item.status] || item.status);
  return { value: item.id, label: item.name, caption: extra.filter(Boolean).join(' · ') };
}

export default {
  name: 'ConsoleInstitutionPicker',
  data() {
    return {
      options: [],
      filteredOptions: [],
      loading: false,
      errorMessage: '',
    };
  },
  computed: {
    ...mapState(useAuthStore, ['isAdmin', 'myInstitutions', 'primaryInstitutionId']),
    ...mapState(useConsoleScopeStore, { pickedId: 'institutionId' }),
    visible() {
      return this.isAdmin || (this.myInstitutions || []).length > 1;
    },
    currentId() {
      // institution-scope mixin 과 같은 규칙으로 "지금 보고 있는" 기관을 보인다
      if (this.isAdmin) return this.pickedId || this.primaryInstitutionId || null;
      const owns = (this.myInstitutions || []).some((item) => item.institutionId === this.pickedId);
      return owns ? this.pickedId : this.primaryInstitutionId;
    },
  },
  watch: {
    visible: { immediate: true, handler: 'loadOptions' },
  },
  methods: {
    async loadOptions() {
      if (!this.visible) return;
      this.errorMessage = '';
      if (!this.isAdmin) {
        this.options = toInstitutionOptions(this.myInstitutions).map((item) => ({ ...item, caption: '' }));
        this.filteredOptions = this.options;
        return;
      }
      this.loading = true;
      try {
        const result = await listInstitutions({ page: 0, size: INSTITUTION_PAGE_SIZE, sort: 'name,asc' });
        this.options = (result?.data || []).map(toOption);
        this.filteredOptions = this.options;
      } catch (error) {
        this.options = [];
        this.errorMessage = error?.message || '기관 목록을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    filterOptions(text, update) {
      update(() => {
        const needle = text.trim().toLowerCase();
        this.filteredOptions = needle
          ? this.options.filter((item) => `${item.label} ${item.caption}`.toLowerCase().includes(needle))
          : this.options;
      });
    },
    pick(institutionId) {
      useConsoleScopeStore().selectInstitution(institutionId);
    },
  },
};
</script>

<style scoped>
/* Hallmark · component: select bar · genre: modern-minimal · theme: system-managed (--db-* 토큰)
 * states: default · hover/focus(Quasar q-select) · loading · error · empty(미선택 강조)
 * pre-emit critique: P4 H4 E4 S4 R5 V4
 */
.scope-bar {
  padding: 12px 16px;
  background: var(--db-bg-white);
  border-bottom: 1px solid var(--db-border);
}

.scope-bar--empty {
  background: var(--db-accent-soft);
}

.scope-bar__inner {
  display: flex;
  align-items: center;
  gap: 8px 12px;
  flex-wrap: wrap;
}

.scope-bar__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--db-text-sub);
  white-space: nowrap;
}

.scope-bar__select {
  width: min(360px, 100%);
  background: var(--db-bg-white);
}

.scope-bar__hint {
  margin: 0;
  font-size: 13px;
  color: var(--db-text-body);
}

.scope-bar__hint--error {
  color: var(--db-status-pink);
}
</style>
