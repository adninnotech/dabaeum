<template>
  <q-dialog
    :model-value="modelValue"
    :persistent="saving || uploading"
    :maximized="$q.screen.xs"
    @update:model-value="$emit('update:modelValue', $event)"
    @before-show="fill"
  >
    <q-card class="badge-form" :class="{ 'badge-form--full': $q.screen.xs }">
      <q-card-section class="badge-form__head">
        <div class="text-h6">{{ definition ? '배지 정의 수정' : '배지 추가' }}</div>
        <q-btn v-close-popup flat round dense icon="close" aria-label="닫기" :disable="saving" />
      </q-card-section>
      <q-separator />

      <q-card-section class="badge-form__body">
        <!-- 이미지 -->
        <div class="badge-form__image">
          <BadgeMedal :image-url="imagePreview" :image-file-id="imageFileId || ''" :size="72" />
          <div class="badge-form__image-actions">
            <!-- 작은 네모 태그처럼 보였다 — 다른 폼 [파일 선택]과 같은 버튼 -->
            <q-btn
              outline
              no-caps
              color="primary"
              class="badge-form__image-btn"
              :label="imageFileId ? '이미지 바꾸기' : '이미지 올리기'"
              :loading="uploading"
              @click="$refs.imageInput.click()"
            />
            <q-btn
              v-if="imageFileId"
              flat
              no-caps
              dense
              label="이미지 빼기"
              class="badge-form__muted-btn"
              @click="clearImage"
            />
            <input
              ref="imageInput"
              type="file"
              accept="image/*"
              class="badge-form__file"
              @change="onImageSelected"
            />
          </div>
        </div>
        <p class="badge-form__hint">{{ imageHint }}</p>

        <q-input
          v-model="name"
          outlined
          dense
          maxlength="100"
          label="배지 이름 *"
          :error="!!errors.name"
          :error-message="errors.name"
        />
        <q-input
          v-model="description"
          outlined
          dense
          type="textarea"
          autogrow
          maxlength="500"
          label="설명"
        />

        <!-- 코드는 시스템이 쓰는 이름 — 사람이 먼저 정하는 이름·설명 뒤로 -->
        <q-input
          v-model="code"
          outlined
          dense
          label="코드 *"
          hint="다른 배지와 겹치지 않는 영문 이름(예: CITIZEN_UNIV). 대문자·숫자·밑줄 2~50자, 만든 뒤에는 바꿀 수 없습니다."
          :disable="!!definition"
          :error="!!errors.code"
          :error-message="errors.code"
          @update:model-value="code = String($event || '').toUpperCase()"
        />

        <!-- 획득 조건 -->
        <fieldset class="badge-form__rule" :disabled="ruleLocked && kind !== BADGE_RULE_KIND.TIERED">
          <legend class="badge-form__legend">획득 조건 *</legend>
          <p v-if="ruleLocked" class="badge-form__locked" role="note">
            <q-icon name="o_lock" size="18px" aria-hidden="true" />
            <span>{{
              kind === BADGE_RULE_KIND.TIERED
                ? '이미 발급된 배지가 있어 단계 수·최소 이수 건수·분야는 바꿀 수 없습니다. 단계 이름·이미지는 바꿀 수 있어요.'
                : '이미 발급된 배지가 있어 조건은 바꿀 수 없습니다. 이름·설명·이미지는 바꿀 수 있어요.'
            }}</span>
          </p>
          <q-option-group
            v-model="kind"
            :options="BADGE_RULE_KIND_OPTIONS"
            type="radio"
            inline
            :disable="ruleLocked"
            @update:model-value="onKindChange"
          />

          <div v-if="kind === BADGE_RULE_KIND.COURSE" class="badge-form__rule-field">
            <q-select
              v-model="courseId"
              outlined
              dense
              emit-value
              map-options
              use-input
              input-debounce="300"
              :options="courseOptions"
              :loading="coursesLoading"
              :disable="ruleLocked"
              label="강의 검색"
              :error="!!errors.courseId"
              :error-message="errors.courseId"
              @filter="searchCourses"
            >
              <template #no-option>
                <q-item>
                  <q-item-section class="text-grey-7">강의명을 입력해 검색해 주세요.</q-item-section>
                </q-item>
              </template>
            </q-select>
          </div>

          <div v-else class="badge-form__rule-field">
            <div class="badge-form__rule-label">
              {{ kind === BADGE_RULE_KIND.CLASSIFICATION ? '분야 *' : '분야 (선택 — 비우면 모든 강의)' }}
            </div>
            <ClassificationPicker
              v-model="classificationCode"
              :leaf-only="false"
              :disable="ruleLocked"
            />
            <p v-if="errors.classificationCode" class="badge-form__error">
              {{ errors.classificationCode }}
            </p>
            <template v-if="kind === BADGE_RULE_KIND.TIERED">
              <div class="badge-form__rule-label">단계 기준표 * (강의 이수 건수)</div>
              <BadgeTierLevelsEditor
                :levels="levels"
                :locked="ruleLocked"
                @update:levels="levels = $event"
                @uploading="uploading = $event"
              />
              <p v-if="errors.levels" class="badge-form__error" role="alert">{{ errors.levels }}</p>
            </template>
            <q-input
              v-if="kind === BADGE_RULE_KIND.CUMULATIVE"
              v-model.number="count"
              outlined
              dense
              type="number"
              min="1"
              max="1000"
              label="수료 개수 *"
              suffix="과목"
              class="badge-form__count"
              :disable="ruleLocked"
              :error="!!errors.count"
              :error-message="errors.count"
            />
          </div>

          <p class="badge-form__preview">
            <span class="badge-form__preview-label">미리 보기</span>
            {{ previewText }}
          </p>
        </fieldset>
      </q-card-section>

      <q-card-actions align="right" class="badge-form__actions">
        <q-btn v-close-popup flat no-caps label="취소" :disable="saving" />
        <q-btn
          unelevated
          no-caps
          color="primary"
          :label="definition ? '저장' : '만들기'"
          :loading="saving"
          :disable="uploading"
          @click="submit"
        />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import BadgeMedal from '@/components/badge/BadgeMedal.vue';
import BadgeTierLevelsEditor from '@/components/console/BadgeTierLevelsEditor.vue';
import ClassificationPicker from '@/components/course/ClassificationPicker.vue';
import {
  BADGE_IMAGE_PURPOSE,
  createBadgeDefinition,
  updateBadgeDefinition,
} from '@/services/badge-api';
import { listCourseClassifications } from '@/services/classification-api';
import { searchAdminBadges } from '@/services/credential-api';
import { getCourse, listCourses } from '@/services/course-api';
import { uploadFile } from '@/services/file-api';
import {
  BADGE_RULE_KIND,
  BADGE_RULE_KIND_OPTIONS,
  DEFAULT_TIER_LEVELS,
  buildRule,
  ruleKind,
  ruleText,
  validateTierLevels,
} from '@/utils/badge-rule';

const CODE = /^[A-Z0-9_]{2,50}$/;

/** 서버 단계표 → 편집용 복사본(없으면 7단계 기본표) */
function toEditableLevels(levels) {
  const source = Array.isArray(levels) && levels.length ? levels : DEFAULT_TIER_LEVELS;
  return source.map((item) => ({
    name: item.name,
    minCount: item.minCount,
    imageFileId: item.imageFileId || null,
    imageUrl: item.imageUrl || '',
  }));
}
const COURSE_SEARCH_SIZE = 20;
const MAX_COUNT = 1000;

function validate(form) {
  const errors = {};
  if (!form.isEdit && !CODE.test(form.code)) errors.code = '대문자·숫자·밑줄 2~50자로 입력해 주세요.';
  if (!form.name.trim()) errors.name = '배지 이름을 입력해 주세요.';
  if (form.kind === BADGE_RULE_KIND.COURSE && !form.courseId) errors.courseId = '강의를 골라 주세요.';
  if (form.kind === BADGE_RULE_KIND.CLASSIFICATION && !form.classificationCode) {
    errors.classificationCode = '분야를 골라 주세요.';
  }
  if (form.kind === BADGE_RULE_KIND.TIERED) {
    const message = validateTierLevels(form.levels);
    if (message) errors.levels = message;
  }
  if (form.kind === BADGE_RULE_KIND.CUMULATIVE) {
    const count = Number(form.count);
    if (!Number.isInteger(count) || count < 1 || count > MAX_COUNT) {
      errors.count = `1~${MAX_COUNT} 사이 정수로 입력해 주세요.`;
    }
  }
  return errors;
}

/* 배지 정의 생성·수정 — 수정은 PUT(통째 교체). 피그마에 없어 콘솔 다이얼로그(q-card) 톤을 따른다 */
export default {
  name: 'BadgeDefinitionDialog',
  components: { BadgeMedal, BadgeTierLevelsEditor, ClassificationPicker },
  props: {
    modelValue: { type: Boolean, default: false },
    /** 수정할 정의. null 이면 생성 */
    definition: { type: Object, default: null },
  },
  emits: ['update:modelValue', 'saved'],
  data() {
    return {
      BADGE_RULE_KIND,
      BADGE_RULE_KIND_OPTIONS,
      code: '',
      name: '',
      description: '',
      imageFileId: null,
      imagePreview: '',
      // 저장 전 미리보기로 만든 blob URL — 바꾸거나 창이 사라지면 놓아 준다
      localPreviewUrl: '',
      kind: BADGE_RULE_KIND.CLASSIFICATION,
      courseId: null,
      classificationCode: null,
      count: 3,
      levels: [],
      courseOptions: [],
      coursesLoading: false,
      classificationTree: [],
      errors: {},
      saving: false,
      uploading: false,
      ruleLocked: false,
    };
  },
  computed: {
    // 단계 배지는 이미지가 두 군데(대표·단계별) — 둘의 관계를 적는다. 목록은 대표가 없으면 1단계 그림을 쓴다
    imageHint() {
      return this.kind === BADGE_RULE_KIND.TIERED
        ? '대표 이미지는 목록에 보이는 그림입니다. 비우면 1단계 그림을 씁니다. 단계별 그림은 아래 단계표에서 올려요.'
        : '비우면 기본 훈장 그림이 보입니다.';
    },
    previewText() {
      const rule = buildRule(this.kind, this);
      const selectedCourse = this.courseOptions.find((item) => item.value === this.courseId);
      return ruleText(rule, {
        tree: this.classificationTree,
        courseTitles: selectedCourse ? { [this.courseId]: selectedCourse.label } : {},
      });
    },
  },
  beforeUnmount() {
    this.releaseLocalPreview();
  },
  methods: {
    async fill() {
      const d = this.definition;
      this.code = d?.code || '';
      this.name = d?.name || '';
      this.description = d?.description || '';
      this.imageFileId = d?.imageFileId || null;
      this.releaseLocalPreview();
      this.imagePreview = d?.imageUrl || '';
      this.kind = d ? ruleKind(d.rule) : BADGE_RULE_KIND.CLASSIFICATION;
      this.courseId = d?.rule?.courseId || null;
      this.classificationCode = d?.rule?.classificationCode || null;
      this.count = d?.rule?.count || 3;
      this.levels = toEditableLevels(d?.rule?.levels);
      this.courseOptions = [];
      this.errors = {};
      this.ruleLocked = false;
      // 발급된 배지가 있으면 조건을 처음부터 잠근다 — 저장해서 409 를 받기 전에 알 수 있게
      if (d) this.checkInUse(d.id);
      listCourseClassifications()
        .then((tree) => {
          this.classificationTree = tree;
        })
        .catch(() => {});
      if (this.courseId) this.loadSelectedCourse(this.courseId);
    },
    async checkInUse(definitionId) {
      try {
        const result = await searchAdminBadges({ definitionId, page: 0, size: 1 });
        if ((result.page?.totalElements ?? result.data?.length ?? 0) > 0) this.ruleLocked = true;
      } catch {
        // 확인 못 하면 저장 때 서버가 409 로 알려 준다
      }
    },
    onKindChange(kind) {
      if (kind === BADGE_RULE_KIND.TIERED && !this.levels.length) this.levels = toEditableLevels(null);
    },
    async loadSelectedCourse(courseId) {
      try {
        const course = await getCourse(courseId);
        this.courseOptions = [{ label: course.title, value: course.id }];
      } catch {
        this.courseOptions = [{ label: '(찾을 수 없는 강의)', value: courseId }];
      }
    },
    searchCourses(text, update, abort) {
      const keyword = String(text || '').trim();
      if (!keyword) {
        update(() => {});
        return;
      }
      this.coursesLoading = true;
      listCourses({ page: 0, size: COURSE_SEARCH_SIZE, q: keyword })
        .then((result) => {
          update(() => {
            this.courseOptions = (result.data || []).map((course) => ({
              label: course.institutionName ? `${course.title} · ${course.institutionName}` : course.title,
              value: course.id,
            }));
          });
        })
        .catch(() => abort())
        .finally(() => {
          this.coursesLoading = false;
        });
    },
    async onImageSelected(event) {
      const file = event?.target?.files?.[0];
      if (event?.target) event.target.value = '';
      if (!file) return;
      if (!file.type.startsWith('image/')) {
        this.$q.notify({ type: 'warning', message: '이미지 파일만 올릴 수 있습니다.', position: 'top' });
        return;
      }
      this.uploading = true;
      try {
        const stored = await uploadFile(file, BADGE_IMAGE_PURPOSE);
        this.imageFileId = stored.id;
        // 저장 전 파일은 올린 사람 인증이 있어야 열려 <img> 로는 403 — 고른 파일을 바로 보인다
        this.releaseLocalPreview();
        this.localPreviewUrl = URL.createObjectURL(file);
        this.imagePreview = this.localPreviewUrl;
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error?.message || '이미지를 올리지 못했습니다.',
          position: 'top',
        });
      } finally {
        this.uploading = false;
      }
    },
    clearImage() {
      this.releaseLocalPreview();
      this.imageFileId = null;
      this.imagePreview = '';
    },
    releaseLocalPreview() {
      if (this.localPreviewUrl) URL.revokeObjectURL(this.localPreviewUrl);
      this.localPreviewUrl = '';
    },
    handleSaveError(error) {
      const code = error?.payload?.code;
      if (code === 'BADGE_DEFINITION_CONFLICT') {
        this.errors = { ...this.errors, code: '이미 쓰는 코드입니다.' };
        return '이미 같은 코드의 배지가 있습니다.';
      }
      if (code === 'BADGE_DEFINITION_IN_USE') {
        this.lockRuleToOriginal();
        return '이미 발급된 배지가 있어 조건은 원래대로 되돌렸습니다. 이름·설명·이미지만 바꿀 수 있어요.';
      }
      if (error?.status === 404) return '선택한 강의를 찾을 수 없습니다.';
      if (error?.status === 422) return '획득 조건을 확인해 주세요. 없는 분야이거나 조건 조합이 맞지 않습니다.';
      return error?.message || '저장하지 못했습니다.';
    },
    // 잠긴 조건은 화면에도 원래 값으로 보여 준다 — 저장 때는 원래 규칙을 그대로 보낸다
    lockRuleToOriginal() {
      const rule = this.definition?.rule;
      this.ruleLocked = true;
      if (!rule) return;
      this.kind = ruleKind(rule);
      this.courseId = rule.courseId || null;
      this.classificationCode = rule.classificationCode || null;
      this.count = rule.count || 3;
      this.levels = toEditableLevels(rule.levels);
    },
    // 잠긴 단계 배지는 원래 기준(단계 수·minCount·분야)에 이름·이미지만 바꿔 넣는다
    lockedRule(form) {
      if (!this.ruleLocked || !this.definition) return buildRule(form.kind, form);
      const original = this.definition.rule;
      if (original?.type !== 'TIERED') return original;
      return buildRule(BADGE_RULE_KIND.TIERED, {
        classificationCode: original.classificationCode,
        levels: (original.levels || []).map((item, index) => ({
          ...item,
          name: this.levels[index]?.name ?? item.name,
          imageFileId: this.levels[index] ? this.levels[index].imageFileId : item.imageFileId,
        })),
      });
    },
    async submit() {
      const form = {
        isEdit: !!this.definition,
        code: this.code.trim(),
        name: this.name,
        kind: this.kind,
        courseId: this.courseId,
        classificationCode: this.classificationCode,
        count: this.count,
        levels: this.levels,
      };
      this.errors = validate(form);
      if (Object.keys(this.errors).length) return;

      const body = {
        name: form.name.trim(),
        description: this.description.trim() || null,
        imageFileId: this.imageFileId,
        // 조건이 잠겼으면 원래 규칙을 그대로 보낸다 — PUT 은 통째 교체다
        rule: this.lockedRule(form),
      };
      this.saving = true;
      try {
        const saved = this.definition
          ? await updateBadgeDefinition(this.definition.id, body)
          : await createBadgeDefinition({ ...body, code: form.code });
        this.$emit('saved', saved);
        this.$emit('update:modelValue', false);
      } catch (error) {
        this.$q.notify({ type: 'negative', message: this.handleSaveError(error), position: 'top' });
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.badge-form {
  width: 640px;
  max-width: 94vw;
}

.badge-form--full {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 100%;
}

.badge-form--full .badge-form__body {
  flex: 1;
  max-height: none;
}

.badge-form__image-btn {
  height: 36px;
  padding: 0 14px;
  white-space: nowrap;
}

.badge-form__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.badge-form__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 70vh;
  overflow-y: auto;
}

.badge-form__image {
  display: flex;
  align-items: center;
  gap: 16px;
}

.badge-form__image-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.badge-form__muted-btn {
  color: var(--db-text-sub);
}

.badge-form__file {
  display: none;
}

.badge-form__rule {
  /* fieldset 기본 min-width(min-content)가 단계표 폭을 붙들어 폰에서 창 본문이 522px 로 밀렸다 */
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 4px 0 0;
  padding: 12px 14px 14px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.badge-form__legend {
  padding: 0 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-title);
}

/* 조건 잠김 — 주황 글자 한 줄이라 입력칸을 만지다 뒤늦게 알았다. 아이콘·옅은 바탕 띠로 */
.badge-form__locked {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--db-status-orange-soft);
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
  color: var(--db-status-orange-text);
  word-break: keep-all;
}

.badge-form__locked .q-icon {
  flex-shrink: 0;
  margin-top: 1px;
}

.badge-form__hint {
  margin: -4px 0 0;
  font-size: 14px;
  line-height: 20px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.badge-form__rule-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.badge-form__rule-label {
  font-size: 13px;
  color: var(--db-text-sub);
}

.badge-form__count {
  max-width: 200px;
}

.badge-form__error {
  margin: 0;
  font-size: 12px;
  color: var(--db-status-pink);
}

.badge-form__preview {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 0;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--db-bg-page);
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-body);
  word-break: keep-all;
}

.badge-form__preview-label {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.badge-form__actions {
  padding: 8px 16px 16px;
}
</style>
