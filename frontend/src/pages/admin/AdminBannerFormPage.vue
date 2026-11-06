<template>
  <q-page class="banner-form">
    <h1 class="banner-form__title">{{ isEdit ? '배너 수정' : '배너 등록' }}</h1>
    <!-- 불러오기에 실패한 채 빈 폼으로 [수정]을 누르면 배너를 빈 값으로 덮어쓸 수 있었다 — 오류를 보이고 저장을 막는다 -->
    <AppErrorBanner :message="loadError">
      <template #action>
        <q-btn flat dense no-caps color="negative" label="다시 시도" @click="loadBanner" />
      </template>
    </AppErrorBanner>

    <div class="banner-form__body">
      <section class="form-card">
        <h2 class="form-card__title">기본 정보</h2>
        <div v-for="field in textFields" :key="field.key" class="field">
          <div class="field-label">
            {{ field.label }}<span v-if="field.required" class="req"> *</span>
          </div>
          <q-input v-model="form[field.key]" outlined maxlength="60" class="wz-input">
            <template #append>
              <span class="input-counter">{{ form[field.key].length }}/60</span>
            </template>
          </q-input>
        </div>
      </section>

      <aside class="form-aside">
        <section class="form-card">
          <h2 class="form-card__title">배너 설정</h2>
          <div class="field">
            <div class="field-label">공개 상태 <span class="req">*</span></div>
            <div
              v-for="opt in BANNER_VISIBILITY_OPTIONS"
              :key="opt.value"
              class="radio-row"
              @click="form.visibility = opt.value"
            >
              <q-radio v-model="form.visibility" :val="opt.value" dense />
              <div>
                <div class="radio-label">{{ opt.label }}</div>
                <div class="radio-caption">{{ opt.caption }}</div>
              </div>
            </div>
          </div>

          <div class="field">
            <div class="field-label">공개 시작</div>
            <AppDateInput
              v-model="form.startDate"
              outlined
              clearable
              :disable="form.visibility !== 'SCHEDULED'"
              class="wz-date"
            />
          </div>
          <div class="field">
            <div class="field-label">공개 종료</div>
            <AppDateInput
              v-model="form.endDate"
              outlined
              clearable
              :min="form.startDate || ''"
              :disable="form.visibility !== 'SCHEDULED'"
              class="wz-date"
            />
          </div>
          <div class="field">
            <div class="field-label">정렬 순서</div>
            <q-input
              v-model.number="form.sortOrder"
              type="number"
              min="1"
              outlined
              class="wz-date"
              :error="!!sortOrderError"
              :error-message="sortOrderError"
            />
          </div>
        </section>

        <section class="form-card">
          <h2 class="form-card__title">이미지</h2>
          <div v-for="slot in imageSlots" :key="slot.key" class="field">
            <div class="field-label">
              {{ slot.label }}<span v-if="slot.required" class="req"> *</span>
            </div>
            <div
              class="image-box"
              role="button"
              tabindex="0"
              :aria-label="`${slot.label || '이미지'} 올리기`"
              @click="pickImage(slot.key)"
              @keydown="onPickKeydown($event, () => pickImage(slot.key))"
            >
              <template v-if="form[slot.key]">
                <img :src="form[slot.key].url" alt="" class="image-box__preview" />
                <q-btn aria-label="이미지 삭제"
                  round
                  dense
                  unelevated
                  icon="close"
                  size="sm"
                  class="image-box__clear"
                  @click.stop="form[slot.key] = null"
                />
              </template>
              <template v-else-if="uploading === slot.key">
                <q-spinner size="24px" color="primary" />
              </template>
              <template v-else>
                <q-icon name="cloud_upload" size="32px" color="grey-6" />
                <div class="image-box__text">이미지 업로드</div>
              </template>
            </div>
            <div class="field-help">{{ slot.hint }}</div>
            <div v-if="form[slot.key]" class="field-help">{{ form[slot.key].name }}</div>
          </div>
          <input
            ref="imageInput"
            type="file"
            accept="image/jpeg,image/png"
            class="hidden"
            @change="onImageChange"
          />
        </section>
      </aside>
    </div>

    <footer class="banner-form__foot">
      <!-- 임시저장: 배너 계약에 초안 상태가 없어 버튼을 두지 않는다 -->
      <q-space />
      <q-btn outline no-caps class="btn-cancel" label="취소" @click="goList" />
      <q-btn
        unelevated
        no-caps
        class="btn-submit"
        :label="isEdit ? '수정' : '등록'"
        :loading="saving"
        :disable="isEdit && !loaded"
        @click="submit"
      />
    </footer>
  </q-page>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import unsavedGuard from '@/mixins/unsaved-guard';
import AppDateInput from '@/components/AppDateInput.vue';
import { createBanner, listAdminBanners, updateBanner } from '@/services/banner-api';
import { storedFileContentUrl, uploadFile } from '@/services/file-api';
import { integerInputError } from '@/utils/integer-input';

const MAX_IMAGE_BYTES = 10 * 1024 * 1024;

/** Banner.visibility(BannerCreateRequest) — 생략하면 PRIVATE, SCHEDULED는 startAt·endAt 둘 다 필수 */
const BANNER_VISIBILITY_OPTIONS = [
  { label: '공개', caption: '지금 바로 노출합니다', value: 'PUBLIC' },
  {
    label: '정해진 기간만 공개',
    caption: '공개 시작·종료일 사이에만 노출합니다',
    value: 'SCHEDULED',
  },
  { label: '비공개', caption: '노출하지 않습니다', value: 'PRIVATE' },
];

const TEXT_FIELDS = [
  { key: 'badgeText', label: '안내 배지' },
  { key: 'title', label: '배너 제목', required: true },
  { key: 'subtitle', label: '보조 설명' },
  { key: 'buttonText', label: '버튼 문구' },
  { key: 'buttonLink', label: '버튼 링크' },
];

const IMAGE_SLOTS = [
  {
    key: 'pcImage',
    label: 'PC 배너',
    required: true,
    hint: '권장 2560×380 · jpg, jpeg, png (최대 10MB)',
  },
  {
    key: 'mobileImage',
    label: '모바일 배너',
    hint: '권장 1440×1202 · jpg, jpeg, png (최대 10MB) · 없으면 폰·태블릿에서는 이미지 없이 배경색 위에 글만 보입니다',
  },
];

/** YYYY-MM-DD(date input) → ISO date-time. 시작은 00시, 종료는 23시59분59초(KST) */
function toStartAt(dateStr) {
  return dateStr ? `${dateStr}T00:00:00+09:00` : null;
}
function toEndAt(dateStr) {
  return dateStr ? `${dateStr}T23:59:59+09:00` : null;
}
/** ISO date-time → YYYY-MM-DD(date input, KST 기준) */
function toDateInput(isoStr) {
  if (!isoStr) return '';
  const d = new Date(isoStr);
  if (Number.isNaN(d.getTime())) return '';
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(d);
  return parts;
}

function emptyForm() {
  return {
    id: null,
    badgeText: '',
    title: '',
    subtitle: '',
    buttonText: '',
    buttonLink: '',
    visibility: 'PUBLIC',
    startDate: '',
    endDate: '',
    sortOrder: 1,
    pcImage: null,
    mobileImage: null,
  };
}

export default {
  name: 'AdminBannerFormPage',
  components: { AppDateInput, AppErrorBanner },
  mixins: [unsavedGuard],
  beforeRouteLeave(to, from, next) {
    this.confirmLeave(next);
  },
  data() {
    return {
      form: emptyForm(),
      loaded: false,
      loadError: '',
      imageTarget: null,
      uploading: null,
      saving: false,
      BANNER_VISIBILITY_OPTIONS,
      textFields: TEXT_FIELDS,
      imageSlots: IMAGE_SLOTS,
    };
  },
  computed: {
    sortOrderError() {
      return integerInputError(this.form.sortOrder, { min: 1 });
    },
    bannerId() {
      return this.$route.params.bannerId;
    },
    isEdit() {
      return !!this.bannerId;
    },
  },
  created() {
    if (this.isEdit) this.loadBanner();
  },
  methods: {
    async loadBanner() {
      this.loadError = '';
      try {
        // GET /admin/banners/{id} 단건 경로가 없다 — 목록에서 찾는다.
        const banners = await listAdminBanners();
        const banner = banners.find((b) => String(b.id) === String(this.bannerId));
        if (!banner) {
          this.loadError = '배너를 찾을 수 없습니다. 목록에서 다시 골라 주세요.';
          return;
        }
        this.form = this.fromBanner(banner);
        this.loaded = true;
        this.$nextTick(this.markSaved);
      } catch (error) {
        this.loadError = error.message || '배너를 불러오지 못했습니다.';
      }
    },
    fromBanner(b) {
      return {
        id: b.id,
        badgeText: b.badgeText || '',
        title: b.title || '',
        subtitle: b.subtitle || '',
        buttonText: b.buttonText || '',
        buttonLink: b.buttonLink || '',
        visibility: b.visibility || 'PRIVATE',
        startDate: toDateInput(b.startAt),
        endDate: toDateInput(b.endAt),
        sortOrder: b.sortOrder ?? 1,
        pcImage: b.pcImageFileId
          ? { id: b.pcImageFileId, url: storedFileContentUrl(b.pcImageFileId) }
          : null,
        mobileImage: b.mobileImageFileId
          ? {
              id: b.mobileImageFileId,
              url: storedFileContentUrl(b.mobileImageFileId),
            }
          : null,
      };
    },
    goList() {
      this.$router.push('/admin/banners');
    },
    /** 이미지 칸은 div 라 Enter·Space 로도 열리게 한다 */
    onPickKeydown(event, pick) {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        pick();
      }
    },
    pickImage(key) {
      this.imageTarget = key;
      this.$refs.imageInput.click();
    },
    async onImageChange(event) {
      const file = event.target.files?.[0];
      event.target.value = '';
      if (!file) return;
      if (!['image/jpeg', 'image/png'].includes(file.type)) {
        this.$q.notify({ type: 'warning', message: 'JPG 또는 PNG만 올릴 수 있습니다.' });
        return;
      }
      if (file.size > MAX_IMAGE_BYTES) {
        this.$q.notify({ type: 'warning', message: '이미지 용량은 10MB 이하여야 합니다.' });
        return;
      }
      const target = this.imageTarget;
      this.uploading = target;
      try {
        // 배너 이미지는 purpose=BANNER로 업로드해야 한다 — 다른 purpose 파일 id는 거부된다.
        const uploaded = await uploadFile(file, 'BANNER');
        this.form[target] = { id: uploaded.id, url: uploaded.url, name: file.name };
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '이미지 업로드 실패' });
      } finally {
        this.uploading = null;
      }
    },
    /** 첫 실패 항목 하나만 알린다. SCHEDULED는 시작·종료일 둘 다 있어야 422를 안 낸다 */
    validate() {
      if (!this.form.title.trim()) return '배너 제목을 입력해 주세요.';
      // 화면엔 PC 이미지가 필수로 표시되는데 검사하지 않아 이미지 없는 배너가 등록됐다
      if (!this.form.pcImage?.id) return 'PC 배너 이미지를 올려 주세요.';
      if (this.form.visibility === 'SCHEDULED' && (!this.form.startDate || !this.form.endDate)) {
        return '공개 시작일과 종료일을 모두 선택해 주세요.';
      }
      if (this.sortOrderError) return `정렬 순서: ${this.sortOrderError}`;
      return null;
    },
    toRequestBody() {
      const scheduled = this.form.visibility === 'SCHEDULED';
      return {
        badgeText: this.form.badgeText.trim() || null,
        title: this.form.title.trim(),
        subtitle: this.form.subtitle.trim() || null,
        buttonText: this.form.buttonText.trim() || null,
        buttonLink: this.form.buttonLink.trim() || null,
        visibility: this.form.visibility,
        startAt: scheduled ? toStartAt(this.form.startDate) : null,
        endAt: scheduled ? toEndAt(this.form.endDate) : null,
        sortOrder: this.form.sortOrder || null,
        pcImageFileId: this.form.pcImage?.id || null,
        mobileImageFileId: this.form.mobileImage?.id || null,
      };
    },
    async submit() {
      const message = this.validate();
      if (message) {
        this.$q.notify({ type: 'warning', message, position: 'top' });
        return;
      }
      this.saving = true;
      try {
        const body = this.toRequestBody();
        if (this.isEdit) await updateBanner(this.bannerId, body);
        else await createBanner(body);
        this.$q.notify({
          type: 'positive',
          message: `'${body.title}' 배너를 ${this.isEdit ? '수정' : '등록'}했습니다.`,
          position: 'top',
        });
        this.skipLeaveGuard = true;
        this.goList();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.banner-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 48px 40px 20px;
  background: var(--db-bg-page);
}

.banner-form__title {
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

.banner-form__body {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.form-aside {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 320px;
  flex-shrink: 0;
}

.form-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex: 1;
  min-width: 0;
  padding: 20px 16px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.form-card__title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--db-text-body);
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
  font-size: 12px;
  color: var(--db-text-sub);
}

.req {
  color: var(--db-status-pink);
}

.input-counter {
  font-size: 12px;
  color: var(--db-text-sub);
}

.form-card :deep(.wz-input .q-field__control) {
  height: 48px;
  min-height: 48px;
  border-radius: 8px;
}

.form-card :deep(.wz-date .q-field__control) {
  height: 48px;
  min-height: 48px;
  border-radius: 4px;
}

.radio-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  padding: 4px 10px;
  border: 1px solid var(--db-border);
  border-radius: 4px;
  cursor: pointer;
}

.radio-label {
  font-size: 14px;
  font-weight: 700;
  color: var(--db-text-body);
}

.radio-caption {
  font-size: 12px;
  color: var(--db-text-sub);
}

.image-box {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 160px;
  border: 1px dashed var(--db-border);
  border-radius: 8px;
  cursor: pointer;
  overflow: hidden;
}

.image-box__preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-box__clear {
  position: absolute;
  top: 6px;
  right: 6px;
  background: rgba(0, 0, 0, 0.5);
  color: var(--db-on-color);
}

.image-box__text {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-body);
}

.banner-form__foot {
  display: flex;
  align-items: center;
  gap: 10px;
}

.btn-cancel {
  width: 120px;
  height: 40px;
  border-radius: 6px;
  color: var(--db-text-body);
}

.btn-submit {
  width: 180px;
  height: 40px;
  border-radius: 6px;
  background: var(--db-accent-fill);
  color: var(--db-on-color);
}

/* 세로로 쌓일 때 align-items:flex-start 가 남아 '기본 정보' 카드만 내용 폭(251px)으로 줄어
   오른쪽 여백이 100px 로 벌어졌다 — 쌓이면 폭을 채운다 */
@media (max-width: 1023px) {
  .banner-form__body {
    flex-direction: column;
    align-items: stretch;
  }

  .form-aside {
    width: 100%;
  }
}

.image-box:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

/* 폰: 좌우 40px 여백·큰 제목이 칸을 좁혔다 */
@media (max-width: 599px) {
  .banner-form {
    padding: 20px 16px 16px;
  }

  .banner-form__title {
    height: auto;
    font-size: 24px;
  }
}
</style>
