<template>
  <!-- 이름·이메일·연락처로 사용자를 찾아 고른다(GET /users?q= — 플랫폼 관리자 전용). v-model 은 userId -->
  <q-select
    :model-value="modelValue"
    outlined
    dense
    use-input
    fill-input
    hide-selected
    emit-value
    map-options
    input-debounce="300"
    :options="options"
    :loading="loading"
    :label="label"
    :hint="hint"
    :error="!!errorText"
    :error-message="errorText"
    @filter="onFilter"
    @update:model-value="onSelect"
  >
    <template #option="{ itemProps, opt }">
      <q-item v-bind="itemProps">
        <q-item-section>
          <q-item-label>{{ opt.label }}</q-item-label>
          <q-item-label caption>{{ opt.caption }}</q-item-label>
        </q-item-section>
      </q-item>
    </template>
    <template #no-option>
      <q-item>
        <q-item-section class="text-grey-7">{{ noOptionText }}</q-item-section>
      </q-item>
    </template>
  </q-select>
</template>

<script>
import { listUsers } from '@/services/user-api';
import { formatUserDisplayName } from '@/utils/user-display';

const MIN_QUERY = 2;
const PAGE_SIZE = 20;

function toOption(user) {
  return {
    value: user.id,
    // 표시명이 없으면 이메일로 — UUID 는 보이지 않는다
    label: user.name || user.userName ? formatUserDisplayName(user) : user.email || '이름 없음',
    caption: [user.email, user.phone].filter(Boolean).join(' · '),
    user,
  };
}

export default {
  name: 'UserSearchSelect',
  props: {
    modelValue: { type: String, default: null },
    label: { type: String, default: '사용자 검색' },
    hint: { type: String, default: '이름·이메일·연락처로 찾습니다.' },
    /** 목록 조건(예: { role: 'LEARNER', status: 'ACTIVE' }) */
    filters: { type: Object, default: () => ({}) },
  },
  emits: ['update:modelValue', 'select'],
  data() {
    return { options: [], loading: false, errorText: '', lastQuery: '' };
  },
  computed: {
    noOptionText() {
      if (this.lastQuery.length < MIN_QUERY) return `${MIN_QUERY}글자 이상 입력해 주세요.`;
      return '맞는 사용자가 없습니다.';
    },
  },
  methods: {
    onFilter(value, update, abort) {
      const q = String(value || '').trim();
      this.lastQuery = q;
      if (q.length < MIN_QUERY) {
        update(() => {
          this.options = [];
        });
        return;
      }
      this.loading = true;
      this.errorText = '';
      listUsers({ page: 0, size: PAGE_SIZE, q, ...this.filters })
        .then((result) => {
          update(() => {
            this.options = (result.data || []).map(toOption);
          });
        })
        .catch((error) => {
          this.errorText = error?.message || '사용자를 찾지 못했습니다.';
          abort();
        })
        .finally(() => {
          this.loading = false;
        });
    },
    onSelect(userId) {
      this.$emit('update:modelValue', userId);
      this.$emit('select', this.options.find((opt) => opt.value === userId)?.user || null);
    },
  },
};
</script>
