<template>
  <!-- 회원 정보 수정 — 강사·수강생 상세가 같이 쓴다(두 페이지에 같은 창·저장 코드가 있었다).
       상태는 여기서 바꾸지 않는다 — 확인을 거치는 [회원 탈퇴 처리]·목록 ⋮ 메뉴에서만 -->
  <q-dialog :model-value="modelValue" :persistent="saving" @update:model-value="$emit('update:modelValue', $event)" @before-show="reset">
    <q-card class="member-edit">
      <q-card-section class="member-edit__title">회원 정보 수정</q-card-section>
      <q-card-section class="column q-gutter-sm">
        <q-input v-model="name" outlined dense label="이름 *" :error="!!nameError" :error-message="nameError" />
        <q-input v-model="phone" outlined dense label="연락처" />
        <p class="member-edit__note">계정 상태는 목록의 ⋮ 메뉴나 [회원 탈퇴 처리]에서 바꿉니다.</p>
      </q-card-section>
      <q-card-actions align="right">
        <q-btn v-close-popup flat no-caps label="닫기" :disable="saving" />
        <q-btn unelevated no-caps color="primary" :loading="saving" label="저장" @click="save" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import { updateUser } from '@/services/user-api';

export default {
  name: 'MemberEditDialog',
  props: {
    modelValue: { type: Boolean, default: false },
    /** { id, name, phone } */
    user: { type: Object, default: null },
  },
  emits: ['update:modelValue', 'saved'],
  data() {
    return { name: '', phone: '', nameError: '', saving: false };
  },
  methods: {
    reset() {
      this.name = this.user?.name || '';
      this.phone = this.user?.phone || '';
      this.nameError = '';
    },
    async save() {
      const name = this.name.trim();
      if (!name) {
        this.nameError = '이름을 입력해 주세요.';
        return;
      }
      this.saving = true;
      try {
        await updateUser(this.user.id, { name, phone: this.phone.trim() || null });
        this.$emit('update:modelValue', false);
        this.$q.notify({ type: 'positive', message: `${name} 님의 정보를 수정했습니다.`, position: 'top' });
        this.$emit('saved');
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error?.message || '회원 정보 수정에 실패했습니다.', position: 'top' });
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.member-edit {
  width: 420px;
  max-width: 94vw;
}

.member-edit__title {
  font-size: 18px;
  font-weight: 700;
  color: var(--db-text-title);
}

/* q-gutter 가 주는 왼쪽 여백은 살린다 */
.member-edit__note {
  margin-top: 0;
  margin-bottom: 0;
  font-size: 12px;
  color: var(--db-text-sub);
}
</style>
