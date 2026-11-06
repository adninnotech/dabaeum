<template>
  <q-page class="cp-form">
    <div class="cp-form__head">
      <h1 class="cp-form__title">{{ isEdit ? '콘텐츠 페이지 수정' : '콘텐츠 페이지 만들기' }}</h1>
      <StatusBadge
        v-if="isEdit"
        :label="published ? '게시' : '초안'"
        :color="published ? 'var(--db-status-green)' : 'var(--db-status-gray)'"
      />
    </div>

    <AppErrorBanner v-if="errorMessage" :message="errorMessage" />

    <section class="form-card">
      <div class="field">
        <label class="field-label" for="cp-title">제목 <span class="req">*</span></label>
        <q-input
          id="cp-title"
          v-model="title"
          outlined
          maxlength="200"
          placeholder="예: 다배움 소개"
          class="wz-input"
          :error="!!errors.title"
          :error-message="errors.title"
        />
      </div>

      <div class="field">
        <label class="field-label" for="cp-slug">주소 <span class="req">*</span></label>
        <q-input
          id="cp-slug"
          v-model="slug"
          outlined
          maxlength="100"
          placeholder="about"
          class="wz-input field--slug"
          :error="!!errors.slug"
          :error-message="errors.slug"
          @update:model-value="slug = String($event || '').toLowerCase()"
        >
          <template #prepend><span class="cp-form__prefix">/pages/</span></template>
        </q-input>
        <div class="field-help">영문 소문자·숫자·하이픈 2~100자. 게시한 뒤 바꾸면 이전 주소는 열리지 않습니다.</div>
      </div>

      <div class="field">
        <div class="field-label">본문</div>
        <q-editor
          @paste="pasteMarkdown"
          v-model="bodyHtml"
          min-height="320px"
          :toolbar="editorToolbar"
          placeholder="페이지 내용을 입력해 주세요."
          class="wz-editor"
        />
        <div class="field-help">저장할 때 서버가 허용된 태그만 남깁니다.</div>
      </div>
    </section>

    <div class="cp-form__foot">
      <q-btn flat no-caps label="목록으로" to="/admin/content-pages" />
      <q-space />
      <q-btn
        v-if="isEdit && published"
        outline
        no-caps
        color="primary"
        label="공개 화면 보기"
        :href="publicPath"
        target="_blank"
      />
      <q-btn
        v-if="isEdit"
        outline
        no-caps
        color="primary"
        :label="published ? '게시 취소' : '게시'"
        :loading="publishing"
        :disable="saving"
        @click="togglePublish"
      />
      <q-btn unelevated no-caps color="primary" :label="isEdit ? '저장' : '초안으로 만들기'" :loading="saving" @click="save" />
    </div>

    <q-inner-loading :showing="loading" />
  </q-page>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import {
  createContentPage,
  getAdminContentPage,
  setContentPagePublished,
  updateContentPage,
} from '@/services/cms-api';
import { EDITOR_TOOLBAR } from '@/utils/rich-text';
import { pasteMarkdownAsRichText } from '@/utils/markdown-paste';
import { contentPagePath } from '@/utils/site-menu';
import { confirmDialog } from '@/utils/confirm-dialog';
import unsavedGuard from '@/mixins/unsaved-guard';

const SLUG = /^[a-z0-9-]{2,100}$/;

function validate({ title, slug }) {
  const errors = {};
  if (!title.trim()) errors.title = '제목을 입력해 주세요.';
  if (!SLUG.test(slug)) errors.slug = '영문 소문자·숫자·하이픈 2~100자로 입력해 주세요.';
  return errors;
}

/* 콘텐츠 페이지 작성·수정(설계서 §7) — 공지 작성 화면(AdminNoticeFormPage)과 같은 폼 카드·리치텍스트 */
export default {
  name: 'AdminContentPageFormPage',
  components: { AppErrorBanner, StatusBadge },
  mixins: [unsavedGuard],
  beforeRouteLeave(to, from, next) {
    this.confirmLeave(next);
  },
  data() {
    return {
      editorToolbar: EDITOR_TOOLBAR,
      title: '',
      slug: '',
      bodyHtml: '',
      published: false,
      errors: {},
      loading: false,
      saving: false,
      publishing: false,
      errorMessage: '',
    };
  },
  computed: {
    pageId() {
      return this.$route.params.pageId || '';
    },
    isEdit() {
      return !!this.pageId;
    },
    publicPath() {
      return contentPagePath(this.slug);
    },
  },
  created() {
    if (this.isEdit) this.load();
  },
  methods: {
    // 마크다운 글을 붙여 넣으면 제목·목록·표로 바꿔 넣는다
    pasteMarkdown(event) {
      pasteMarkdownAsRichText(event);
    },
    guardSnapshot() {
      return JSON.stringify({ title: this.title, slug: this.slug, bodyHtml: this.bodyHtml });
    },
    async load() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const page = await getAdminContentPage(this.pageId);
        this.title = page.title || '';
        this.slug = page.slug || '';
        this.bodyHtml = page.bodyHtml || '';
        this.published = page.status === 'PUBLISHED';
        this.$nextTick(this.markSaved);
      } catch (error) {
        this.errorMessage = error?.message || '페이지를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    /** 저장 성공이면 true — 게시 전에 먼저 저장할 때 쓴다 */
    async save() {
      this.errors = validate(this);
      if (Object.keys(this.errors).length) return false;
      const body = { title: this.title.trim(), slug: this.slug, bodyHtml: this.bodyHtml || null };
      this.saving = true;
      try {
        if (this.isEdit) {
          const saved = await updateContentPage(this.pageId, body);
          // 서버가 정제한 본문으로 바꿔 둔다 — 화면과 저장본이 어긋나지 않게
          this.bodyHtml = saved?.bodyHtml ?? this.bodyHtml;
          this.markSaved();
          this.$q.notify({ type: 'positive', message: `'${body.title}' 페이지를 저장했습니다.`, position: 'top' });
        } else {
          const created = await createContentPage(body);
          this.markSaved();
          this.$q.notify({ type: 'positive', message: '초안으로 만들었습니다. 게시하면 공개됩니다.', position: 'top' });
          this.$router.replace(`/admin/content-pages/${created.id}/edit`);
        }
      } catch (error) {
        if (error?.payload?.code === 'CONTENT_PAGE_CONFLICT') {
          this.errors = { slug: '이미 쓰는 주소입니다.' };
          return false;
        }
        this.$q.notify({ type: 'negative', message: error?.message || '저장하지 못했습니다.', position: 'top' });
        return false;
      } finally {
        this.saving = false;
      }
      return true;
    },
    /**
     * 게시·게시 취소 — 저장 안 한 편집이 있으면 예전 본문이 공개됐다. 먼저 저장하고 게시한다.
     * 게시 취소는 공개 중인 페이지를 내리므로 묻는다
     */
    async togglePublish() {
      const publishing = !this.published;
      if (!publishing) {
        const ok = await confirmDialog(this.$q, {
          title: '게시 취소',
          message: `'${this.title}' 페이지를 내립니다. 학습자 사이트에서 이 주소가 보이지 않게 됩니다.`,
          ok: '게시 취소',
          color: 'negative',
          cancel: '닫기',
        });
        if (!ok) return;
      } else if (this.isDirty() && !(await this.save())) {
        return;
      }
      this.publishing = true;
      try {
        const updated = await setContentPagePublished(this.pageId, publishing);
        this.published = updated?.status === 'PUBLISHED';
        this.$q.notify({
          type: 'positive',
          message: `'${this.title}' 페이지를 ${this.published ? '게시했습니다' : '내렸습니다'}.`,
          position: 'top',
        });
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error?.message || '게시 상태를 바꾸지 못했습니다.',
          position: 'top',
        });
      } finally {
        this.publishing = false;
      }
    },
  },
  watch: {
    // 생성 뒤 replace 로 편집 주소가 되면 같은 화면이 재사용된다 — 새 id 로 다시 읽는다
    pageId(value) {
      if (value) this.load();
    },
  },
};
</script>

<style scoped>
.cp-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 48px 40px 20px;
  background: var(--db-bg-page);
}

.cp-form__head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cp-form__title {
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

.form-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px 16px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 14px;
  font-weight: 700;
  color: var(--db-text-body);
}

.field-help {
  font-size: 13px;
  color: var(--db-text-sub);
}

.req {
  color: var(--db-status-pink);
}

.field--slug {
  max-width: 420px;
}

.cp-form__prefix {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
  color: var(--db-text-sub);
}

.form-card :deep(.wz-input .q-field__control) {
  min-height: 48px;
  border-radius: 8px;
}

.form-card :deep(.wz-editor) {
  border: 1px solid var(--db-border-rt);
  border-radius: 8px;
}

.cp-form__foot {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.cp-form__foot .q-btn {
  border-radius: 6px;
}

@media (max-width: 1023px) {
  .cp-form {
    padding: 24px 16px;
  }

  .cp-form__title {
    font-size: 24px;
  }
}
</style>
