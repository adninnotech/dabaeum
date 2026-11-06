<template>
  <!-- 마이페이지 '화면 설정' — 머리 아이콘과 같은 값(useThemeStore). 이 브라우저에만 남는다 -->
  <div class="theme-setting">
    <q-option-group
      :model-value="preference"
      :options="options"
      type="radio"
      inline
      @update:model-value="setPreference"
    />
    <p class="theme-setting__note">이 브라우저에만 저장됩니다. 다른 기기에서는 따로 정해 주세요.</p>
  </div>
</template>

<script>
import { mapActions, mapState } from 'pinia';
import { THEME_OPTIONS } from '@/boot/theme';
import { useThemeStore } from '@/stores/theme-store';

export default {
  name: 'ThemeSettingCard',
  computed: {
    ...mapState(useThemeStore, ['preference']),
    options() {
      return THEME_OPTIONS.map((option) => ({ label: option.label, value: option.value }));
    },
  },
  methods: {
    ...mapActions(useThemeStore, ['setPreference']),
  },
};
</script>

<style scoped>
.theme-setting__note {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--db-text-sub);
}
</style>
