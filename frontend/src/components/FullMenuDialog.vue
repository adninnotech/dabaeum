<template>
  <!-- ☰ 전체 메뉴 — 마우스를 올려야 펼쳐지는 패널이 어려운 이용자(중장년·폰)도 누르기만 하면 모든 분야와 메뉴를 본다.
       벤치마킹: 대구평생학습플랫폼 수강신청 ☰ 전체 메뉴(화면 전체를 덮는 6칸) -->
  <q-dialog
    :model-value="modelValue"
    maximized
    transition-show="fade"
    transition-hide="fade"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="full-menu" aria-labelledby="full-menu-title">
      <div class="learner-container full-menu__head">
        <h2 id="full-menu-title" class="full-menu__title">전체 메뉴</h2>
        <q-btn v-close-popup flat no-caps icon="close" label="닫기" class="full-menu__close" />
      </div>

      <div class="learner-container full-menu__body">
        <section aria-labelledby="full-menu-fields">
          <h3 id="full-menu-fields" class="full-menu__section">강좌 분야</h3>
          <div v-if="loading" class="full-menu__state">
            <q-spinner size="24px" color="primary" />
            분야를 불러오는 중입니다
          </div>
          <p v-else-if="!columns.length" class="full-menu__state">
            분야를 불러오지 못했습니다. 아래 '전체 강좌 보기'에서 찾아 주세요.
          </p>
          <div v-else class="full-menu__grid">
            <div v-for="root in columns" :key="root.code" class="full-menu__col">
              <router-link :to="tabRoute(root)" class="full-menu__root" @click="close">{{ root.name }}</router-link>
              <ul class="full-menu__list">
                <li v-for="middle in root.children" :key="middle.code">
                  <router-link :to="tabRoute(root, middle)" class="full-menu__link" @click="close">
                    {{ middle.name }}
                  </router-link>
                </li>
              </ul>
            </div>
          </div>
          <div class="full-menu__extra">
            <router-link
              v-for="root in singles"
              :key="root.code"
              :to="tabRoute(root)"
              class="full-menu__pill"
              @click="close"
            >
              {{ root.name }}
            </router-link>
            <router-link to="/courses?scope=public" class="full-menu__pill" @click="close">대구 다른 기관 강좌</router-link>
            <router-link to="/courses" class="full-menu__pill full-menu__pill--primary" @click="close">
              전체 강좌 보기
            </router-link>
          </div>
        </section>

        <section aria-labelledby="full-menu-site">
          <h3 id="full-menu-site" class="full-menu__section">다배움</h3>
          <div class="full-menu__menus">
            <button
              v-for="item in menus"
              :key="item.name"
              type="button"
              class="full-menu__menu"
              @click="onMenu(item.name)"
            >
              <q-icon :name="item.icon" size="22px" />
              {{ item.label }}
            </button>
          </div>
        </section>

        <!-- 화면 모드 — 머리에서 옮겨 왔다. 로그인 없이도 바꾼다 -->
        <section aria-labelledby="full-menu-theme">
          <h3 id="full-menu-theme" class="full-menu__section">화면 모드</h3>
          <div class="full-menu__theme" role="radiogroup" aria-labelledby="full-menu-theme">
            <button
              v-for="option in THEME_OPTIONS"
              :key="option.value"
              type="button"
              role="radio"
              class="full-menu__theme-option"
              :class="{ 'full-menu__theme-option--on': preference === option.value }"
              :aria-checked="preference === option.value ? 'true' : 'false'"
              @click="setPreference(option.value)"
            >
              <q-icon :name="option.icon" size="20px" />
              {{ option.label }}
            </button>
          </div>
        </section>
      </div>
    </div>
  </q-dialog>
</template>

<script>
import { mapActions, mapState } from 'pinia';
import { THEME_OPTIONS } from '@/boot/theme';
import courseClassificationMenu from '@/mixins/course-classification-menu';
import { useThemeStore } from '@/stores/theme-store';

export default {
  name: 'FullMenuDialog',
  mixins: [courseClassificationMenu],
  props: {
    modelValue: { type: Boolean, default: false },
    /** 다배움 메뉴 [{ name, label, icon }] — 누르면 menu(name)을 올려 레이아웃이 로그인·권한을 따져 보낸다 */
    menus: { type: Array, default: () => [] },
  },
  emits: ['update:modelValue', 'menu'],
  data() {
    return { loading: false, THEME_OPTIONS };
  },
  computed: {
    ...mapState(useThemeStore, ['preference']),
  },
  watch: {
    async modelValue(value) {
      if (!value || this.tree.length) return;
      this.loading = true;
      await this.loadTree();
      this.loading = false;
    },
  },
  methods: {
    ...mapActions(useThemeStore, ['setPreference']),
    close() {
      this.$emit('update:modelValue', false);
    },
    onMenu(name) {
      this.close();
      this.$emit('menu', name);
    },
  },
};
</script>

<style scoped>
.full-menu {
  min-height: 100%;
  overflow-y: auto;
  background: var(--db-bg-white);
  color: var(--db-text-body);
}

.full-menu__head {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 68px;
  background: var(--db-bg-white);
  border-bottom: 1px solid var(--db-border);
}

.full-menu__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--db-text-title);
}

.full-menu__close {
  min-height: 44px;
  font-size: 16px;
}

.full-menu__body {
  display: flex;
  flex-direction: column;
  gap: 40px;
  padding-top: 32px;
  padding-bottom: 48px;
}

.full-menu__section {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--db-text-title);
}

.full-menu__state {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 16px;
  color: var(--db-text-sub);
}

.full-menu__grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
}

.full-menu__col {
  min-width: 0;
  padding: 0 16px;
  border-left: 1px solid var(--db-border);
}

.full-menu__col:first-child {
  padding-left: 0;
  border-left: 0;
}

.full-menu__root {
  display: flex;
  align-items: center;
  min-height: 48px;
  margin-bottom: 4px;
  border-bottom: 2px solid var(--db-accent);
  font-size: 17px;
  font-weight: 700;
  color: var(--db-text-title);
  text-decoration: none;
  overflow-wrap: anywhere;
}

.full-menu__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.full-menu__link {
  display: flex;
  align-items: center;
  min-height: 44px;
  font-size: 16px;
  color: var(--db-text-body);
  text-decoration: none;
}

.full-menu__root:hover,
.full-menu__link:hover {
  color: var(--db-accent);
  text-decoration: underline;
}

.full-menu__extra {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 24px;
}

.full-menu__pill {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  padding: 0 18px;
  border: 1px solid var(--db-border);
  border-radius: 22px;
  font-size: 16px;
  color: var(--db-text-body);
  text-decoration: none;
  white-space: nowrap;
}

.full-menu__pill:hover {
  border-color: var(--db-accent);
  color: var(--db-accent);
}

.full-menu__pill--primary {
  border-color: var(--db-primary);
  background: var(--db-primary);
  color: var(--db-on-color);
  font-weight: 700;
}

.full-menu__pill--primary:hover {
  color: var(--db-on-color);
  opacity: 0.9;
}

.full-menu__menus {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 8px;
}

.full-menu__menu {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 56px;
  padding: 0 16px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
  background: var(--db-bg-white);
  font: inherit;
  font-size: 16px;
  font-weight: 600;
  color: var(--db-text-body);
  cursor: pointer;
}

.full-menu__menu:hover {
  border-color: var(--db-accent);
  color: var(--db-accent);
}

.full-menu__theme {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
}

.full-menu__theme-option {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 0 18px;
  border: 1px solid var(--db-border);
  border-radius: 22px;
  background: var(--db-bg-white);
  font: inherit;
  font-size: 16px;
  color: var(--db-text-body);
  cursor: pointer;
}

.full-menu__theme-option:hover {
  border-color: var(--db-accent);
  color: var(--db-accent);
}

.full-menu__theme-option--on,
.full-menu__theme-option--on:hover {
  border-color: var(--db-accent);
  background: var(--db-accent-soft);
  color: var(--db-accent);
  font-weight: 700;
}

.full-menu__theme-option:focus-visible,
.full-menu__root:focus-visible,
.full-menu__link:focus-visible,
.full-menu__pill:focus-visible,
.full-menu__menu:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

/* 태블릿 3칸, 폰 2칸 — 세로선 대신 칸마다 아래 여백 */
@media (max-width: 1023px) {
  .full-menu__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 24px 0;
  }

  .full-menu__col:nth-child(3n + 1) {
    padding-left: 0;
    border-left: 0;
  }
}

@media (max-width: 599px) {
  .full-menu__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .full-menu__col,
  .full-menu__col:nth-child(3n + 1) {
    padding: 0 12px;
    border-left: 1px solid var(--db-border);
  }

  .full-menu__col:nth-child(2n + 1) {
    padding-left: 0;
    border-left: 0;
  }

  .full-menu__menus {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
