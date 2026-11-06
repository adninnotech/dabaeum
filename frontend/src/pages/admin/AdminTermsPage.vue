<template>
  <q-page class="terms-admin">
    <h1 class="terms-admin__title">약관 관리</h1>

    <div class="terms-admin__panel">
      <ConsoleTabs :model-value="type" :items="TYPE_TABS" @update:model-value="selectType" />

      <div class="terms-admin__body">
        <!-- 현재 게시본 -->
        <section class="terms-card" aria-labelledby="terms-current-title">
          <header class="terms-card__head">
            <h2 id="terms-current-title" class="terms-card__title">현재 게시본</h2>
            <span v-if="current" class="terms-version">v{{ current.version }}</span>
          </header>

          <div v-if="loading" class="terms-card__state"><q-spinner color="primary" size="28px" /></div>
          <AppErrorBanner v-else-if="errorMessage" :message="errorMessage" />
          <p v-else-if="!current" class="terms-card__state terms-card__state--empty">
            등록된 {{ typeLabel }}이 없습니다. 오른쪽에서 첫 버전을 등록해 주세요.
          </p>
          <template v-else>
            <div class="terms-current__meta">
              <span class="terms-current__name">{{ current.title }}</span>
              <span>등록 {{ formatDateMinuteKst(current.updatedAt, '-') }}</span>
            </div>
            <div class="terms-current__text" tabindex="0" :aria-label="`${typeLabel} 본문`">{{ current.body }}</div>
            <p class="terms-card__note">회원가입 동의 화면은 항상 이 최신본을 보여 줍니다.</p>
          </template>
        </section>

        <!-- 새 버전 등록 -->
        <section class="terms-card" aria-labelledby="terms-new-title">
          <header class="terms-card__head">
            <h2 id="terms-new-title" class="terms-card__title">새 버전 등록</h2>
          </header>
          <p class="terms-card__note terms-card__note--top">
            약관은 고치거나 지울 수 없고 새 버전을 쌓습니다. 현재 내용을 채워 두었으니 바꿀 부분만 고쳐 등록하세요.
          </p>

          <q-input
            v-model="version"
            outlined
            dense
            :maxlength="VERSION_MAX"
            label="버전 *"
            :hint="current ? `현재 v${current.version} — 같은 버전은 다시 쓸 수 없습니다.` : '예: 1.0'"
            class="terms-form__version"
            :error="!!errors.version"
            :error-message="errors.version"
            :disable="saving"
          />
          <q-input
            v-model="title"
            outlined
            dense
            counter
            :maxlength="TITLE_MAX"
            label="제목 *"
            :error="!!errors.title"
            :error-message="errors.title"
            :disable="saving"
          />
          <q-input
            v-model="body"
            type="textarea"
            outlined
            label="본문 *"
            hint="줄바꿈은 그대로 보입니다."
            input-class="terms-form__body"
            :error="!!errors.body"
            :error-message="errors.body"
            :disable="saving"
          />

          <div class="terms-form__foot">
            <q-btn flat no-caps label="현재 내용으로 되돌리기" :disable="saving || !current" @click="resetToCurrent" />
            <q-space />
            <q-btn unelevated no-caps color="primary" label="새 버전 등록" :loading="saving" @click="confirmSubmit" />
          </div>
        </section>
      </div>
    </div>
  </q-page>
</template>

<script>
import { confirmDialog } from '@/utils/confirm-dialog';
import unsavedGuard from '@/mixins/unsaved-guard';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import ConsoleTabs from '@/components/console/ConsoleTabs.vue';
import { createTermsContent, getTermsContent } from '@/services/support-api';
import { formatDateMinuteKst } from '@/utils/datetime';

const TYPE_TABS = [
  { label: '이용약관', value: 'USAGE' },
  { label: '개인정보처리방침', value: 'PRIVACY' },
];
const VERSION_MAX = 30;
const TITLE_MAX = 200;

function validate({ version, title, body }) {
  const errors = {};
  if (!version.trim()) errors.version = '버전을 입력해 주세요.';
  if (!title.trim()) errors.title = '제목을 입력해 주세요.';
  if (!body.trim()) errors.body = '본문을 입력해 주세요.';
  return errors;
}

/* 약관 관리(플랫폼 관리자) — 피그마에 없다(role-menus "약관 및 정책" 자리). 조회는 최신본만 된다(이력 API 없음) */
export default {
  name: 'AdminTermsPage',
  components: { AppErrorBanner, ConsoleTabs },
  mixins: [unsavedGuard],
  // 탭 전환에만 묻던 '작성 중' 확인을 다른 화면으로 나갈 때도
  beforeRouteLeave(to, from, next) {
    this.confirmLeave(next);
  },
  data() {
    return {
      TYPE_TABS,
      VERSION_MAX,
      TITLE_MAX,
      type: 'USAGE',
      current: null,
      loading: false,
      errorMessage: '',
      version: '',
      title: '',
      body: '',
      errors: {},
      saving: false,
    };
  },
  computed: {
    typeLabel() {
      return TYPE_TABS.find((tab) => tab.value === this.type)?.label || '';
    },
    dirty() {
      const c = this.current;
      return this.version.trim() !== '' || this.title !== (c?.title || '') || this.body !== (c?.body || '');
    },
  },
  created() {
    this.load();
  },
  methods: {
    formatDateMinuteKst,
    selectType(value) {
      if (value === this.type) return;
      if (!this.dirty) {
        this.switchType(value);
        return;
      }
      this.$q
        .dialog({
          title: '작성 중인 내용',
          message: '다른 약관으로 이동하면 작성 중인 새 버전 내용이 사라집니다.',
          cancel: { flat: true, noCaps: true, label: '계속 작성' },
          ok: { unelevated: true, noCaps: true, color: 'primary', label: '이동' },
        })
        .onOk(() => this.switchType(value));
    },
    switchType(value) {
      this.type = value;
      this.load();
    },
    async load() {
      this.loading = true;
      this.errorMessage = '';
      try {
        this.current = await getTermsContent(this.type);
      } catch (error) {
        this.current = null;
        // 아직 한 번도 등록하지 않은 종류는 404 — 빈 상태로 보인다
        if (error?.status !== 404) this.errorMessage = error?.message || '약관을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
        this.fillFromCurrent();
      }
    },
    // 이 화면은 '현재 게시본과 다른가'(dirty)가 곧 저장 안 한 내용이다
    isDirty() {
      return this.dirty;
    },
    /** [현재 내용으로 되돌리기] — 쓰던 새 버전을 확인 없이 지웠다 */
    async resetToCurrent() {
      if (this.dirty) {
        const ok = await confirmDialog(this.$q, {
          title: '현재 내용으로 되돌리기',
          message: '작성 중인 새 버전 내용이 지워지고 현재 게시본으로 바뀝니다.',
          ok: '되돌리기',
          color: 'negative',
          cancel: '계속 작성',
        });
        if (!ok) return;
      }
      this.fillFromCurrent();
    },
    fillFromCurrent() {
      this.version = '';
      this.title = this.current?.title || '';
      this.body = this.current?.body || '';
      this.errors = {};
    },
    confirmSubmit() {
      this.errors = validate(this);
      if (Object.keys(this.errors).length) return;
      this.$q
        .dialog({
          title: `${this.typeLabel} v${this.version.trim()} 등록`,
          message: '등록하면 바로 최신본이 되어 회원가입 동의 화면에 나옵니다. 등록한 버전은 고치거나 지울 수 없습니다.',
          cancel: { flat: true, noCaps: true, label: '취소' },
          ok: { unelevated: true, noCaps: true, color: 'primary', label: '등록' },
          persistent: true,
        })
        .onOk(() => this.submit());
    },
    async submit() {
      this.saving = true;
      try {
        const saved = await createTermsContent({
          type: this.type,
          version: this.version.trim(),
          title: this.title.trim(),
          body: this.body,
        });
        this.current = saved || this.current;
        this.fillFromCurrent();
        this.$q.notify({ type: 'positive', message: '새 버전을 등록했습니다.', position: 'top' });
      } catch (error) {
        if (error?.status === 409) {
          this.errors = { version: '이미 등록된 버전입니다. 다른 버전을 입력해 주세요.' };
          return;
        }
        this.$q.notify({ type: 'negative', message: error?.message || '등록하지 못했습니다.', position: 'top' });
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.terms-admin {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 48px 40px 32px;
  background: var(--db-bg-page);
}

.terms-admin__title {
  display: flex;
  align-items: center;
  height: 58px;
  margin: 0;
  padding: 10px 0;
  font-size: 32px;
  line-height: 1.2;
  font-weight: 700;
  color: var(--db-text-title);
}

.terms-admin__panel {
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.terms-admin__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 24px;
  padding: 24px;
}

.terms-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.terms-card + .terms-card {
  padding-left: 24px;
  border-left: 1px solid var(--db-border);
}

.terms-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.terms-card__title {
  margin: 0;
  font-size: 18px;
  line-height: 26px;
  font-weight: 700;
  letter-spacing: normal;
  color: var(--db-text-title);
}

.terms-version {
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--db-accent-soft);
  font-size: 13px;
  font-weight: 700;
  color: var(--db-primary);
  font-variant-numeric: tabular-nums;
}

.terms-card__state {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}

.terms-card__state--empty {
  margin: 0;
  font-size: 14px;
  color: var(--db-text-sub);
}

.terms-card__note {
  margin: 0;
  font-size: 13px;
  color: var(--db-text-sub);
}

.terms-card__note--top {
  margin-top: -4px;
}

.terms-current__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  font-size: 14px;
  color: var(--db-text-sub);
}

.terms-current__name {
  font-weight: 600;
  color: var(--db-text-title);
}

.terms-current__text {
  height: 440px;
  overflow-y: auto;
  padding: 16px;
  border: 1px solid var(--db-border);
  border-radius: 6px;
  background: var(--db-bg-subtle);
  font-size: 14px;
  line-height: 1.7;
  color: var(--db-text-body);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.terms-current__text:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.terms-form__version {
  max-width: 240px;
}

.terms-card :deep(.terms-form__body) {
  height: 300px;
  line-height: 1.7;
}

.terms-form__foot {
  display: flex;
  align-items: center;
  gap: 8px;
}

@media (max-width: 1023px) {
  .terms-admin {
    padding: 24px 16px;
  }

  .terms-admin__title {
    font-size: 24px;
  }

  .terms-admin__body {
    grid-template-columns: minmax(0, 1fr);
    padding: 16px;
  }

  .terms-card + .terms-card {
    padding: 24px 0 0;
    border-top: 1px solid var(--db-border);
    border-left: 0;
  }

  .terms-current__text {
    height: 280px;
  }
}
</style>
