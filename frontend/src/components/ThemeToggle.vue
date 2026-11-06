<template>
  <!-- 화면 모드 — 로그인 없이도 머리에서 바로 바꾼다. 아이콘은 지금 고른 모드(자동·해·달) -->
  <q-btn flat round dense :icon="current.icon" :aria-label="`화면 모드: ${current.label}`" class="theme-toggle">
    <q-tooltip>화면 모드: {{ current.label }}</q-tooltip>
    <q-menu anchor="bottom right" self="top right">
      <q-list dense style="min-width: 180px">
        <q-item-label header>화면 모드</q-item-label>
        <q-item
          v-for="option in THEME_OPTIONS"
          :key="option.value"
          v-close-popup
          clickable
          :active="preference === option.value"
          active-class="theme-toggle__active"
          @click="setPreference(option.value)"
        >
          <q-item-section avatar>
            <q-icon :name="option.icon" size="20px" />
          </q-item-section>
          <q-item-section>{{ option.label }}</q-item-section>
          <q-item-section v-if="preference === option.value" side>
            <q-icon name="check" size="18px" />
          </q-item-section>
        </q-item>
      </q-list>
    </q-menu>
  </q-btn>
</template>

<script>
import { mapActions, mapState } from 'pinia';
import { THEME_OPTIONS } from '@/boot/theme';
import { useThemeStore } from '@/stores/theme-store';

export default {
  name: 'ThemeToggle',
  data() {
    return { THEME_OPTIONS };
  },
  computed: {
    ...mapState(useThemeStore, ['preference', 'current']),
  },
  methods: {
    ...mapActions(useThemeStore, ['setPreference']),
  },
};
</script>

<style scoped>
.theme-toggle {
  color: var(--db-text-body);
}

.theme-toggle__active {
  color: var(--db-accent);
  background: var(--db-accent-soft);
}
</style>
