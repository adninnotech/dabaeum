<template>
  <!-- 하단 바 — [취소]가 [다음 단계] 옆에 같은 무게로 있어 잘못 누르기 쉬웠다. 나가기는 머리 '← 내 강의 관리'로.
       폰에선 [임시저장]이 한 줄을 따로 차지해 바가 109px 이었다 — 한 줄에 셋 -->
  <footer class="wizard-footer">
    <!-- 저장 상태 — 왼쪽은 글자만. 단추는 오른쪽에 모은다([임시저장]이 [다음]과 2,000px 떨어져 있었다) -->
    <div class="wizard-footer__left" role="status">
      <span v-if="autoSaveFailed" class="wizard-footer__hint wizard-footer__hint--error">
        자동 저장 실패 — [임시저장]을 눌러 주세요
      </span>
      <span v-else-if="draftSavedAt" class="wizard-footer__hint">
        <q-icon name="cloud_done" size="16px" aria-hidden="true" />
        {{ draftSavedAuto ? '자동 저장됨' : '임시저장됨' }} · {{ formatDateMinuteKst(draftSavedAt) }}
      </span>
      <span v-else-if="autoSaveEnabled" class="wizard-footer__hint gt-xs">기본 정보를 다 채우면 1분마다·단계를 넘길 때 자동 저장됩니다</span>
    </div>
    <div class="wizard-footer__right">
      <!-- 연타하면 첫 응답(draftCourseId) 전에 POST 가 두 번 나가 같은 강의가 둘 생겼다 -->
      <q-btn flat no-caps class="btn-draft" label="임시저장" :loading="saving" :disable="saving" @click="$emit('save-draft')" />
      <q-btn v-if="step > 1" no-caps outline class="btn-nav" label="이전" @click="$emit('prev')" />
      <q-btn
        no-caps
        unelevated
        color="primary"
        class="btn-nav btn-nav--main"
        :loading="saving"
        :label="isLast ? submitLabel : '다음 단계'"
        @click="$emit('next')"
      />
    </div>
  </footer>
</template>

<script>
import { formatDateMinuteKst } from '@/utils/datetime';

export default {
  name: 'WizardFooter',
  props: {
    step: { type: Number, required: true },
    isLast: { type: Boolean, default: false },
    saving: { type: Boolean, default: false },
    submitLabel: { type: String, default: '등록' },
    draftSavedAt: { type: String, default: null },
    /** 마지막 저장이 자동 저장이었나 */
    draftSavedAuto: { type: Boolean, default: false },
    autoSaveFailed: { type: Boolean, default: false },
    autoSaveEnabled: { type: Boolean, default: false },
  },
  emits: ['save-draft', 'prev', 'next'],
  methods: { formatDateMinuteKst },
};
</script>
