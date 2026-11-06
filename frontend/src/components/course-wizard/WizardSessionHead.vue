<template>
  <!-- 회차 커리큘럼 머리 — 제목·안내·[회차 추가] -->
  <div class="curriculum-head" data-field="sessions">
    <div class="field-label">회차 커리큘럼 <span class="req">*</span></div>
    <div class="table-head">
      <div class="field-help">{{ help }}</div>
      <q-btn no-caps outline icon="add" class="btn-add-session" label="회차 추가" @click="wizard.addSession" />
    </div>
  </div>
</template>

<script>
export default {
  name: 'WizardSessionHead',
  inject: ['wizard'],
  props: {
    help: { type: String, default: '' },
  },
};
</script>
