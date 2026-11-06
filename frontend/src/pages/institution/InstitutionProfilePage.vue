<template>
  <!-- 다른 콘솔 화면처럼 q-page padding·전체 폭 — 없는 page-shell 클래스와 max-width 880 으로 여백 없이 왼쪽에 쏠렸다 -->
  <q-page padding>
    <AppPageHeader title="기관 정보 수정" subtitle="기관 연락처, 소개, 로고를 관리합니다." />
    <AppInstitutionMissingBanner :show="showInstitutionMissingBanner" />

    <q-banner v-if="isAdmin" class="bg-blue-1 text-blue-10 q-mb-md" rounded>
      기관 관리자의 자기 기관 정보 수정 화면입니다. 기관명과 기관 코드는 플랫폼 관리자에게 요청해 주세요.
    </q-banner>

    <q-card v-if="institutionId && !isAdmin" flat bordered class="profile-card">
      <q-card-section>
        <div class="text-subtitle1 text-weight-bold">기관 공개 정보</div>
        <div class="text-caption text-grey-7 q-mt-xs">
          기관명과 기관 코드는 승인 기록과 수료증에 사용되므로 이 화면에서 변경할 수 없습니다.
        </div>
      </q-card-section>
      <q-separator />
      <q-card-section v-if="loading" class="row justify-center q-pa-xl">
        <q-spinner color="primary" size="32px" />
      </q-card-section>
      <q-card-section v-else class="profile-form">
        <div class="profile-logo">
          <q-avatar rounded size="88px" color="grey-2" text-color="grey-7">
            <img v-if="logoUrl" :src="logoUrl" alt="기관 로고 미리보기" />
            <q-icon v-else name="o_apartment" size="36px" aria-hidden="true" />
          </q-avatar>
          <div class="profile-logo__actions">
            <q-btn outline no-caps color="primary" label="로고 선택" :loading="uploading" @click="$refs.logoInput.click()" />
            <q-btn v-if="form.logoFileId" flat no-caps color="negative" label="로고 삭제" :disable="saving || uploading" @click="clearLogo" />
            <input ref="logoInput" type="file" accept="image/*" class="hidden" @change="onLogoSelected" />
            <div class="text-caption text-grey-7">이미지 파일을 선택하면 업로드됩니다.</div>
          </div>
        </div>

        <q-input v-model="form.contactPhone" outlined label="연락처" maxlength="30" :disable="saving" />
        <q-input v-model="form.contactEmail" outlined type="email" label="이메일" maxlength="320" :disable="saving" />
        <q-input
          v-model="form.introduction"
          outlined
          type="textarea"
          autogrow
          maxlength="2000"
          label="기관 소개"
          :disable="saving"
        />

        <q-banner v-if="errorMessage" class="bg-red-1 text-negative" rounded role="alert">
          {{ errorMessage }}
        </q-banner>
        <q-banner v-if="saved" class="bg-green-1 text-positive" rounded role="status">
          기관 정보를 저장했습니다.
        </q-banner>
      </q-card-section>
      <q-separator v-if="!loading" />
      <q-card-actions v-if="!loading" align="right" class="q-pa-md">
        <q-btn unelevated color="primary" no-caps label="변경사항 저장" :loading="saving" :disable="uploading" @click="save" />
      </q-card-actions>
    </q-card>
  </q-page>
</template>

<script>
import AppInstitutionMissingBanner from '@/components/AppInstitutionMissingBanner.vue';
import AppPageHeader from '@/components/AppPageHeader.vue';
import institutionScope from '@/mixins/institution-scope';
import { getInstitution, updateInstitution } from '@/services/institution-api';
import { storedFileContentUrl, uploadFile } from '@/services/file-api';
import { institutionProfileUpdate } from '@/utils/institution-profile';

export default {
  name: 'InstitutionProfilePage',
  components: { AppInstitutionMissingBanner, AppPageHeader },
  mixins: [institutionScope],
  data() {
    return {
      form: { contactPhone: '', contactEmail: '', introduction: '', logoFileId: null },
      loading: false,
      saving: false,
      uploading: false,
      errorMessage: '',
      saved: false,
    };
  },
  computed: {
    logoUrl() {
      return storedFileContentUrl(this.form.logoFileId);
    },
  },
  watch: {
    institutionId: {
      immediate: true,
      handler() {
        this.load();
      },
    },
  },
  methods: {
    async load() {
      if (!this.institutionId || this.isAdmin) return;
      this.loading = true;
      this.errorMessage = '';
      try {
        const institution = await getInstitution(this.institutionId);
        this.form = {
          contactPhone: institution.contactPhone || '',
          contactEmail: institution.contactEmail || '',
          introduction: institution.introduction || '',
          logoFileId: institution.logoFileId || null,
        };
      } catch (error) {
        this.errorMessage = error?.message || '기관 정보를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    async onLogoSelected(event) {
      const file = event.target.files?.[0];
      event.target.value = '';
      if (!file) return;
      this.uploading = true;
      this.errorMessage = '';
      try {
        const uploaded = await uploadFile(file, 'INSTITUTION_LOGO');
        this.form.logoFileId = uploaded.id;
        this.saved = false;
      } catch (error) {
        this.errorMessage = error?.message || '로고를 업로드하지 못했습니다.';
      } finally {
        this.uploading = false;
      }
    },
    clearLogo() {
      this.form.logoFileId = null;
      this.saved = false;
    },
    async save() {
      if (!this.institutionId || this.isAdmin) return;
      this.saving = true;
      this.errorMessage = '';
      this.saved = false;
      try {
        await updateInstitution(
          this.institutionId,
          institutionProfileUpdate(this.form),
        );
        this.saved = true;
      } catch (error) {
        this.errorMessage = error?.message || '기관 정보를 저장하지 못했습니다.';
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.profile-card {
  border-radius: 16px;
}

.profile-form {
  display: grid;
  gap: 18px;
}

.profile-logo {
  display: flex;
  align-items: center;
  gap: 20px;
}

.profile-logo__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.hidden {
  display: none;
}

@media (max-width: 599px) {
  .profile-logo {
    align-items: flex-start;
  }
}
</style>
