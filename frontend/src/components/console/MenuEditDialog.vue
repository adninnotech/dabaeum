<template>
  <q-dialog :model-value="modelValue" :persistent="saving" @update:model-value="$emit('update:modelValue', $event)" @before-show="fill">
    <q-card class="menu-form">
      <q-card-section class="menu-form__head">
        <div class="text-h6">{{ menu ? '메뉴 수정' : '메뉴 추가' }}</div>
        <q-btn v-close-popup flat round dense icon="close" aria-label="닫기" :disable="saving" />
      </q-card-section>
      <q-separator />

      <q-card-section class="menu-form__body">
        <q-input
          v-model="name"
          outlined
          dense
          maxlength="60"
          label="메뉴 이름 *"
          :error="!!errors.name"
          :error-message="errors.name"
        />
        <q-select
          v-model="parentId"
          outlined
          dense
          emit-value
          map-options
          :options="parentOptions"
          :disable="hasChildren"
          label="위치"
          :hint="hasChildren ? '하위 메뉴가 있는 메뉴는 최상위에만 둘 수 있습니다.' : '메뉴는 두 단계까지입니다.'"
        />

        <div class="menu-form__label">연결할 곳 *</div>
        <q-option-group v-model="linkType" :options="LINK_TYPE_OPTIONS" type="radio" inline />

        <q-select
          v-if="linkType === 'ROUTE'"
          v-model="routeKey"
          outlined
          dense
          emit-value
          map-options
          :options="routeOptions"
          :loading="optionsLoading"
          label="사이트 화면 *"
          :error="!!errors.routeKey"
          :error-message="errors.routeKey"
        />
        <template v-else-if="linkType === 'CONTENT'">
          <q-select
            v-model="contentPageId"
            outlined
            dense
            emit-value
            map-options
            :options="pageOptions"
            :loading="optionsLoading"
            label="콘텐츠 페이지 *"
            :error="!!errors.contentPageId"
            :error-message="errors.contentPageId"
          >
            <template #no-option>
              <q-item><q-item-section class="text-grey-7">만든 페이지가 없습니다. 콘텐츠 관리에서 먼저 만들어 주세요.</q-item-section></q-item>
            </template>
          </q-select>
          <p v-if="selectedPageDraft" class="menu-form__warn">
            초안 페이지입니다. 게시하기 전까지 공개 메뉴에 나오지 않습니다.
          </p>
        </template>
        <q-input
          v-else
          v-model="externalUrl"
          outlined
          dense
          maxlength="500"
          label="주소 *"
          placeholder="https://"
          hint="새 탭에서 열립니다."
          :error="!!errors.externalUrl"
          :error-message="errors.externalUrl"
        />

        <q-toggle v-model="visible" label="공개 메뉴에 보이기" />
      </q-card-section>

      <q-card-actions align="right" class="menu-form__actions">
        <q-btn v-close-popup flat no-caps label="취소" :disable="saving" />
        <q-btn unelevated no-caps color="primary" :label="menu ? '저장' : '추가'" :loading="saving" @click="submit" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import { createMenu, listAdminContentPages, listMenuRoutes, updateMenu } from '@/services/cms-api';
import { LINK_TYPE_LABEL, MENU_ROUTES } from '@/utils/site-menu';

const LINK_TYPE_OPTIONS = Object.entries(LINK_TYPE_LABEL).map(([value, label]) => ({ value, label }));
const URL_PATTERN = /^https?:\/\/\S+$/;

function validate(form) {
  const errors = {};
  if (!form.name.trim()) errors.name = '메뉴 이름을 입력해 주세요.';
  if (form.linkType === 'ROUTE' && !form.routeKey) errors.routeKey = '화면을 골라 주세요.';
  if (form.linkType === 'CONTENT' && !form.contentPageId) errors.contentPageId = '페이지를 골라 주세요.';
  if (form.linkType === 'EXTERNAL' && !URL_PATTERN.test(form.externalUrl.trim())) {
    errors.externalUrl = 'http:// 또는 https:// 로 시작하는 주소를 입력해 주세요.';
  }
  return errors;
}

/* 메뉴 추가·수정 — PUT 은 통째 교체라 순서(sortOrder)도 원래 값을 함께 보낸다 */
export default {
  name: 'MenuEditDialog',
  props: {
    modelValue: { type: Boolean, default: false },
    /** 수정할 메뉴 노드. null 이면 추가 */
    menu: { type: Object, default: null },
    /** 추가할 때 미리 고른 상위(하위 메뉴 추가) */
    defaultParentId: { type: String, default: null },
    /** 최상위 메뉴 목록 — 위치 선택지 */
    topLevel: { type: Array, default: () => [] },
    /** 새 메뉴의 순서 값(맨 뒤) */
    nextSortOrder: { type: Number, default: 0 },
  },
  emits: ['update:modelValue', 'saved'],
  data() {
    return {
      LINK_TYPE_OPTIONS,
      name: '',
      parentId: null,
      linkType: 'ROUTE',
      routeKey: null,
      contentPageId: null,
      externalUrl: '',
      visible: true,
      errors: {},
      saving: false,
      optionsLoading: false,
      routeOptions: [],
      pages: [],
    };
  },
  computed: {
    hasChildren() {
      return !!this.menu?.children?.length;
    },
    parentOptions() {
      const parents = this.topLevel
        .filter((item) => item.id !== this.menu?.id)
        .map((item) => ({ label: `${item.name} 아래`, value: item.id }));
      return [{ label: '최상위', value: null }, ...parents];
    },
    pageOptions() {
      return this.pages.map((page) => ({
        label: page.status === 'PUBLISHED' ? page.title : `${page.title} (초안)`,
        value: page.id,
      }));
    },
    selectedPageDraft() {
      const page = this.pages.find((item) => item.id === this.contentPageId);
      return page ? page.status !== 'PUBLISHED' : false;
    },
  },
  methods: {
    fill() {
      const m = this.menu;
      this.name = m?.name || '';
      this.parentId = m ? m.parentId || null : this.defaultParentId;
      this.linkType = m?.linkType || 'ROUTE';
      this.routeKey = m?.routeKey || null;
      this.contentPageId = m?.contentPageId || null;
      this.externalUrl = m?.externalUrl || '';
      this.visible = m ? m.visible !== false : true;
      this.errors = {};
      this.loadOptions();
    },
    async loadOptions() {
      this.optionsLoading = true;
      const [routes, pages] = await Promise.allSettled([listMenuRoutes(), listAdminContentPages()]);
      this.routeOptions =
        routes.status === 'fulfilled'
          ? routes.value.map((item) => ({ label: MENU_ROUTES[item.key]?.label || item.key, value: item.key }))
          : Object.entries(MENU_ROUTES).map(([value, item]) => ({ label: item.label, value }));
      this.pages = pages.status === 'fulfilled' ? pages.value : [];
      this.optionsLoading = false;
    },
    async submit() {
      const form = {
        name: this.name,
        linkType: this.linkType,
        routeKey: this.routeKey,
        contentPageId: this.contentPageId,
        externalUrl: this.externalUrl,
      };
      this.errors = validate(form);
      if (Object.keys(this.errors).length) return;
      const body = {
        parentId: this.parentId,
        name: this.name.trim(),
        linkType: this.linkType,
        routeKey: this.linkType === 'ROUTE' ? this.routeKey : null,
        contentPageId: this.linkType === 'CONTENT' ? this.contentPageId : null,
        externalUrl: this.linkType === 'EXTERNAL' ? this.externalUrl.trim() : null,
        sortOrder: this.menu ? this.menu.sortOrder : this.nextSortOrder,
        visible: this.visible,
      };
      this.saving = true;
      try {
        if (this.menu) await updateMenu(this.menu.id, body);
        else await createMenu(body);
        this.$emit('saved');
        this.$emit('update:modelValue', false);
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message:
            error?.status === 422
              ? '메뉴는 두 단계까지입니다. 하위 메뉴가 있는 메뉴는 다른 메뉴 아래로 옮길 수 없습니다.'
              : error?.message || '저장하지 못했습니다.',
          position: 'top',
        });
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.menu-form {
  width: 480px;
  max-width: 94vw;
}

.menu-form__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.menu-form__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.menu-form__label {
  margin-bottom: -6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--db-text-body);
}

.menu-form__warn {
  margin: -6px 0 0;
  font-size: 12px;
  color: var(--db-status-orange-text);
}

.menu-form__actions {
  padding: 8px 16px 16px;
}
</style>
