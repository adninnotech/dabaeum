<template>
  <q-page class="console-detail">
    <header class="console-detail__head">
      <h1 class="console-detail__title">{{ title }}</h1>
      <div class="console-detail__actions">
        <slot name="actions" />
      </div>
    </header>

    <AppErrorBanner v-if="error" :message="error" />

    <section class="page-info">
      <div class="page-info__profile">
        <div class="page-info__avatar">
          <img v-if="avatarUrl" :src="avatarUrl" alt="" />
          <q-icon v-else-if="avatarIcon" :name="avatarIcon" size="48px" color="grey-7" />
          <template v-else>{{ initial }}</template>
        </div>
        <div class="page-info__name">{{ name || '-' }}</div>
        <div class="page-info__line">
          <span>{{ subtitle || '-' }}</span>
          <template v-if="role">
            <i class="page-info__sep" />
            <span>{{ role }}</span>
          </template>
        </div>
        <div v-for="line in lines" :key="line" class="page-info__line">{{ line }}</div>
      </div>

      <dl v-for="(column, i) in fields" :key="i" class="page-info__fields">
        <div v-for="field in column" :key="field.label" class="page-info__row">
          <dt class="page-info__label">{{ field.label }}</dt>
          <dd class="page-info__value">
            <slot :name="`field-${field.key || field.label}`" :field="field">
              {{ field.value ?? '-' }}
            </slot>
          </dd>
        </div>
      </dl>
    </section>

    <ConsoleTabs v-model="activeTab" :items="tabs" />

    <div class="console-detail__body" :class="{ 'console-detail__body--two': twoColumn }">
      <main class="console-detail__main">
        <slot :tab="activeTab" />
      </main>
      <aside v-if="twoColumn" class="console-detail__aside">
        <slot name="aside" />
      </aside>
    </div>

    <slot name="dialogs" />
  </q-page>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import ConsoleTabs from '@/components/console/ConsoleTabs.vue';

export default {
  name: 'ConsoleDetailPage',
  components: { AppErrorBanner, ConsoleTabs },
  props: {
    title: { type: String, required: true },
    name: { type: String, default: '' },
    /** 프로필 두 번째 줄 앞부분 (생년월일·기관코드 등) */
    subtitle: { type: String, default: '' },
    role: { type: String, default: '' },
    /** 프로필 아래 추가 줄 (이메일·주소·연락처) */
    lines: { type: Array, default: () => [] },
    avatarIcon: { type: String, default: '' },
    avatarUrl: { type: String, default: '' },
    /** [[{label, value, key?}], [{...}]] — 2열 */
    fields: { type: Array, default: () => [] },
    /** [{ label, value }] */
    tabs: { type: Array, required: true },
    modelValue: { type: String, default: '' },
    twoColumn: { type: Boolean, default: false },
    error: { type: String, default: '' },
  },
  emits: ['update:modelValue'],
  watch: {
    // 탭 ↔ ?tab= 동기화 (새로고침·링크 공유)
    activeTab(value) {
      if (this.$route.query.tab === value) return;
      this.$router.replace({ query: { ...this.$route.query, tab: value } });
    },
    '$route.query.tab'(value) {
      if (value && value !== this.activeTab && this.tabs.some((t) => t.value === value)) {
        this.activeTab = value;
      }
    },
  },
  created() {
    const queryTab = this.$route.query.tab;
    if (queryTab && this.tabs.some((tab) => tab.value === queryTab)) {
      this.activeTab = queryTab;
    }
  },
  computed: {
    activeTab: {
      get() {
        return this.modelValue || this.tabs[0]?.value || '';
      },
      set(value) {
        this.$emit('update:modelValue', value);
      },
    },
    initial() {
      return (this.name || '?').trim().charAt(0).toUpperCase();
    },
  },
};
</script>

<style scoped>
.console-detail {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 48px 40px 20px;
  background: var(--db-bg-page);
}

.console-detail__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  min-height: 58px;
  padding: 10px 0;
}

.console-detail__title {
  margin: 0;
  font-size: 32px;
  line-height: 1.2;
  font-weight: 700;
  color: var(--db-text-title);
}

.console-detail__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* q-btn 기본 min-height(2.572em)가 16px 글자에서 41px이 되어 높이를 밀어낸다 */
.console-detail__actions :deep(.q-btn) {
  height: 36px;
  min-height: 36px;
  font-size: 16px;
  font-weight: 700;
}

.page-info {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  min-height: 214px;
  margin-top: 10px;
  padding: 20px 16px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.page-info__profile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: 400px;
  padding: 10px;
}

.page-info__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  border: 6px solid var(--db-border-avatar);
  background: var(--db-border-avatar);
  font-size: 40px;
  font-weight: 700;
  color: var(--db-text-body);
}

.page-info__avatar img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}

.page-info__name {
  font-size: 24px;
  font-weight: 700;
  color: var(--db-text-title);
}

.page-info__line {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 16px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.page-info__sep {
  width: 1px;
  height: 12px;
  background: var(--db-border);
}

.page-info__fields {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 400px;
  margin: 0;
  padding: 10px;
}

/* 이름 칸이 80px 고정이라 '진행 중 강의'·'총 학습시간'이 값에 딱 붙었다 — 넓히고 사이를 띄운다 */
.page-info__row {
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 24px;
}

.page-info__label {
  width: 104px;
  flex-shrink: 0;
  white-space: nowrap;
  font-size: 16px;
  line-height: 24px;
  font-weight: 700;
  color: var(--db-text-title);
}

.page-info__value {
  flex: 1;
  min-width: 0;
  margin: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 16px;
  line-height: 24px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.console-detail__body {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  margin-top: 16px;
}

.console-detail__main {
  flex: 1;
  min-width: 0;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

/* 오른쪽 칸(관리자 메모·강사 정보)이 카드 없이 떠 있어 본문 카드와 좌우 줄이 어긋났다 — 같은 카드로 */
.console-detail__aside {
  width: 320px;
  flex-shrink: 0;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

@media (max-width: 1023px) {
  /* 쌓일 때 align-items:flex-start 가 남으면 본문 카드가 내용 폭으로 줄어 좌우 여백이 달라진다 */
  .console-detail__body {
    flex-direction: column;
    align-items: stretch;
  }

  .console-detail__aside,
  .page-info__profile,
  .page-info__fields {
    width: 100%;
  }
}

/* 폰: 머리 버튼 넷이 한 줄에 끼어 '목록 / 으로 / 돌아 / 가기'처럼 한 글자씩 꺾였고,
   좌우 40px 여백·400px 프로필 칸이 이메일을 잘랐다 */
@media (max-width: 599px) {
  .console-detail {
    padding: 20px 16px 16px;
  }

  .console-detail__title {
    font-size: 24px;
  }

  .console-detail__actions {
    flex-wrap: wrap;
    width: 100%;
  }

  .console-detail__actions :deep(.q-btn) {
    flex: 1 1 calc(50% - 4px);
    width: auto;
    min-width: 0;
    font-size: 14px;
    white-space: nowrap;
  }

  .page-info {
    padding: 16px 12px;
  }

  .page-info__profile {
    padding: 4px 0;
  }

  .page-info__line {
    justify-content: center;
  }
}
</style>
