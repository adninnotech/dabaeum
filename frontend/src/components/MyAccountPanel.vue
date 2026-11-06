<template>
  <div class="my-account">
    <div class="app-page-header q-mb-lg">
      <!-- 사이드 메뉴 이름(내 정보 관리)과 같게 — 메뉴를 눌렀는데 다른 이름의 화면이 열렸다 -->
      <div class="app-page-header__title text-h5 text-weight-bold">내 정보 관리</div>
    </div>

    <div class="row q-col-gutter-lg">
      <div class="col-12 col-md-3">
        <q-card flat bordered class="my-account__card q-mb-md">
          <q-card-section>
            <div class="text-subtitle2 text-weight-bold q-mb-md">프로필</div>
            <div class="column items-center">
              <div class="my-account__avatar-wrap">
                <!-- 폰: 120px 사진 카드가 첫 화면의 절반을 써 아이디·비밀번호가 밀렸다 -->
                <q-avatar :size="$q.screen.xs ? '88px' : '120px'" class="my-account__avatar">
                  <img v-if="avatarUrl" :src="avatarUrl" alt="프로필" />
                  <span v-else class="text-h4">{{ avatarLetter }}</span>
                </q-avatar>
                <q-btn aria-label="프로필 사진 바꾸기"
                  round
                  dense
                  size="sm"
                  color="grey-8"
                  text-color="white"
                  icon="photo_camera"
                  class="my-account__camera"
                  :loading="avatarUploading"
                  @click="onAvatarClick"
                />
              </div>
              <input
                ref="avatarInput"
                type="file"
                accept="image/*"
                class="hidden"
                @change="onAvatarSelected"
              />
              <AvatarCropDialog v-model="cropOpen" :file="cropFile" :upload="uploadAvatar" />
              <!-- 신분증처럼 사진 아래 이름과 소속 -->
              <div class="my-account__identity">
                <div class="my-account__name">{{ displayName }}</div>
                <MyAffiliationList />
              </div>
            </div>
          </q-card-section>
        </q-card>
        <q-btn
          outline
          color="grey-7"
          class="full-width"
          label="로그아웃"
          :loading="loggingOut"
          @click="doLogout"
        />
        <div v-if="showInstructorApply" class="q-mt-md">
          <q-btn
            flat
            dense
            color="primary"
            icon="badge"
            label="강사 신청"
            class="full-width"
            @click="$router.push('/mypage/instructor-apply')"
          />
        </div>
      </div>

      <div class="col-12 col-md-9">
        <q-card flat bordered class="my-account__card q-mb-md">
          <q-card-section>
            <div class="text-subtitle1 text-weight-bold q-mb-md">로그인 정보</div>
            <div class="my-account__row">
              <div class="my-account__label">아이디</div>
              <div class="my-account__value">{{ loginId }}</div>
            </div>
            <q-separator class="q-my-md" />
            <div class="my-account__row">
              <div class="my-account__label">비밀번호</div>
              <div class="row items-center no-wrap q-gutter-md">
                <span class="my-account__value text-grey-6">••••••••</span>
                <!-- 이름은 '변경'인데 누르면 확인 없이 재설정 메일이 나갔다(메일 발송도 아직 안 됨) —
                     학습자 마이페이지와 같은 창에서 현재·새 비밀번호로 바로 바꾼다 -->
                <!-- dense 라 작은 네모 태그처럼 보였다 — 일반 테두리 버튼 -->
                <q-btn
                  outline
                  no-caps
                  color="primary"
                  class="my-account__btn"
                  label="비밀번호 변경"
                  @click="passwordOpen = true"
                />
              </div>
            </div>
          </q-card-section>
        </q-card>

        <q-card flat bordered class="my-account__card q-mb-md">
          <q-card-section>
            <div class="row items-center justify-between q-mb-md">
              <div class="text-subtitle1 text-weight-bold">사용자 정보</div>
              <q-btn
                v-if="!editing"
                flat
                dense
                color="primary"
                label="회원 정보 수정 >"
                @click="startEdit"
              />
              <div v-else class="row q-gutter-sm">
                <q-btn flat dense label="취소" @click="cancelEdit" />
                <q-btn unelevated dense color="primary" label="저장" :loading="saving" @click="saveProfile" />
              </div>
            </div>

            <div v-if="!editing" class="row q-col-gutter-md">
              <div v-for="item in displayFields" :key="item.label" class="col-12 col-sm-6">
                <div class="my-account__field">
                  <div class="my-account__label">{{ item.label }}</div>
                  <div class="my-account__value">{{ item.value }}</div>
                </div>
              </div>
            </div>

            <div v-else class="row q-col-gutter-md">
              <div class="col-12 col-sm-6">
                <q-input v-model="form.name" outlined dense label="이름 *" />
              </div>
              <div class="col-12 col-sm-6">
                <AppDateInput ref="birthDateInput" v-model="form.birthDate" outlined dense label="생년월일" default-view="Years" no-future />
              </div>
              <div class="col-12 col-sm-6">
                <q-input v-model="form.email" outlined dense type="email" label="이메일" />
              </div>
              <div class="col-12 col-sm-6">
                <q-input v-model="form.phone" outlined dense label="휴대폰번호" />
              </div>
              <div class="col-12 col-sm-6">
                <q-select
                  v-model="form.gender"
                  outlined
                  dense
                  emit-value
                  map-options
                  :options="genderOptions"
                  label="성별"
                />
              </div>
              <div v-if="showCareer" class="col-12">
                <q-input
                  v-model="form.introduction"
                  outlined
                  dense
                  type="textarea"
                  autogrow
                  label="소개"
                  hint="수강생에게 보이는 한두 문장 — 강좌 상세 '강사소개'에 나옵니다"
                />
              </div>
              <!-- 줄마다 [구분]+[내용] 칸 — 큰 글 칸에 규칙을 알아서 지켜 주길 바랄 수 없었다 -->
              <div v-if="showCareer" class="col-12">
                <InstructorCareerEditor v-model="form.career" />
              </div>
            </div>
          </q-card-section>
        </q-card>

        <!-- 강사 프로필 — 소개·경력이 사용자 정보 반 칸에 한 덩어리 평문으로 들어가 읽히지 않았다.
             따로 한 장: 소개는 인용처럼, 경력은 줄마다 항목(강좌 상세 '강사소개'와 같은 모양). 수정은 위 [회원 정보 수정]에서 -->
        <q-card v-if="showCareer && !editing" flat bordered class="my-account__card q-mb-md">
          <q-card-section>
            <div class="text-subtitle1 text-weight-bold q-mb-md">강사 프로필</div>
            <div class="my-account__profile-block">
              <div class="my-account__label">소개</div>
              <p v-if="introductionText" class="my-account__intro">{{ introductionText }}</p>
              <div v-else class="my-account__value">-</div>
            </div>
            <div class="my-account__profile-block">
              <div class="my-account__label">주요 경력</div>
              <InstructorCareerList :value="careerText" />
            </div>
          </q-card-section>
        </q-card>

        <!-- 알림 및 광고 설정(이메일·SMS)은 뺐다(#144) — 메일·문자를 보내는 코드가 없어 동작하지 않는 설정이었다 -->

        <!-- 화면 설정 — 머리 아이콘과 같은 값 -->
        <q-card flat bordered class="my-account__card q-mb-md">
          <q-card-section>
            <div class="text-subtitle1 text-weight-bold q-mb-md">화면 설정</div>
            <ThemeSettingCard />
          </q-card-section>
        </q-card>

        <div v-if="showWithdraw" class="row justify-end">
          <q-btn flat dense color="grey-6" label="회원 탈퇴" @click="confirmWithdraw" />
        </div>
      </div>
    </div>
    <PasswordChangeDialog v-model="passwordOpen" />
  </div>
</template>

<script>
import AppDateInput from '@/components/AppDateInput.vue';
import { BIRTH_DATE_INVALID } from '@/utils/api-error-message';
import AvatarCropDialog from '@/components/mypage/AvatarCropDialog.vue';
import MyAffiliationList from '@/components/mypage/MyAffiliationList.vue';
import { mapActions, mapState } from 'pinia';
import PasswordChangeDialog from '@/components/mypage/PasswordChangeDialog.vue';
import ThemeSettingCard from '@/components/ThemeSettingCard.vue';
import InstructorCareerEditor from '@/components/InstructorCareerEditor.vue';
import InstructorCareerList from '@/components/InstructorCareerList.vue';
import { storedFileContentUrl, uploadFile } from '@/services/file-api';
import { listMyIdentities, withdrawMe } from '@/services/user-api';
import { useAuthStore } from '@/stores/auth-store';
import { formatDateKst } from '@/utils/datetime';

// 탈퇴 확인 입력 문구 (MyPage 와 같다)
const WITHDRAW_CONFIRM_WORD = '탈퇴';

function identityLabel(verified) {
  if (verified === null) return '확인 불가';
  return verified ? '인증 완료' : '미인증';
}

// 서버 UserUpdateRequest.career maxLength
const CAREER_MAX = 2000;

export default {
  name: 'MyAccountPanel',
  components: { AppDateInput, AvatarCropDialog, MyAffiliationList, InstructorCareerEditor, InstructorCareerList, PasswordChangeDialog, ThemeSettingCard },
  props: {
    showInstructorApply: {
      type: Boolean,
      default: false,
    },
    showCareer: {
      type: Boolean,
      default: false,
    },
    /** 플랫폼 관리자는 숨긴다 — 실수로 탈퇴하면 관리자 콘솔에 들어갈 사람이 없어질 수 있다 */
    showWithdraw: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {
      editing: false,
      saving: false,
      loggingOut: false,
      passwordOpen: false,
      avatarUrl: '',
      avatarUploading: false,
      cropOpen: false,
      cropFile: null,
      identityVerified: false,
      form: {
        name: '',
        email: '',
        phone: '',
        birthDate: '',
        gender: '',
        career: '',
        introduction: '',
      },
      genderOptions: [
        { label: '남성', value: 'MALE' },
        { label: '여성', value: 'FEMALE' },
        { label: '밝히지 않음', value: 'UNDISCLOSED' },
      ],
    };
  },
  computed: {
    ...mapState(useAuthStore, ['user', 'userId', 'avatarLetter', 'displayName']),
    loginId() {
      return this.user?.email || this.user?.id || '-';
    },
    genderLabel() {
      const found = this.genderOptions.find((item) => item.value === this.form.gender);
      return found?.label && this.form.gender ? found.label : '-';
    },
    displayFields() {
      const fields = [
        { label: '이름', value: this.user?.name || '-' },
        { label: '생년월일', value: this.user?.birthDate ? formatDateKst(this.user.birthDate) : '-' },
        { label: '이메일', value: this.user?.email || '-' },
        { label: '휴대폰번호', value: this.user?.phone || '-' },
        { label: '성별', value: this.genderLabel },
        { label: '본인인증', value: identityLabel(this.identityVerified) },
      ];
      return fields;
    },
    careerText() {
      return this.user?.career || this.form.career || '';
    },
    introductionText() {
      return this.user?.introduction || this.form.introduction || '';
    },
  },
  watch: {
    user: {
      immediate: true,
      handler() {
        this.syncFromUser();
      },
    },
  },
  mounted() {
    this.loadIdentities();
  },
  methods: {
    ...mapActions(useAuthStore, ['updateMyProfile', 'logout']),
    syncFromUser() {
      if (!this.user) return;
      // 고치는 중엔 칸을 덮지 않는다 — 알림 스위치 저장도 내 정보를 새로 바꿔 이 함수가 도는데, 그때 적던 값이
      // 서버 값으로 되돌아가 [저장]하면 옛 값이 저장됐다(QA 중앙 #51). 알림·사진은 칸과 따로라 늘 맞춘다
      if (!this.editing) {
        this.form = {
          name: this.user.name || '',
          email: this.user.email || '',
          phone: this.user.phone || '',
          birthDate: this.user.birthDate ? String(this.user.birthDate).slice(0, 10) : '',
          gender: this.user.gender || '',
          career: this.user.career || '',
          introduction: this.user.introduction || '',
        };
      }
      this.avatarUrl = this.user.profileImageId
        ? storedFileContentUrl(this.user.profileImageId)
        : '';
    },
    // 조회 실패는 "미인증"과 다르다 — null 로 두어 "확인 불가"로 그린다
    async loadIdentities() {
      if (!this.userId) return;
      try {
        const list = await listMyIdentities();
        this.identityVerified = list.some((item) => item.verifiedAt);
      } catch {
        this.identityVerified = null;
      }
    },
    startEdit() {
      this.syncFromUser();
      this.editing = true;
    },
    cancelEdit() {
      this.editing = false;
      this.syncFromUser();
    },
    async saveProfile() {
      if (!this.form.name.trim()) {
        this.$q.notify({ type: 'warning', message: '이름은 필수입니다.' });
        return;
      }
      if (this.$refs.birthDateInput && !this.$refs.birthDateInput.validate()) {
        this.$q.notify({ type: 'warning', message: BIRTH_DATE_INVALID, position: 'top' });
        return;
      }
      if (this.showCareer && (this.form.career || '').length > CAREER_MAX) {
        this.$q.notify({ type: 'warning', message: `주요 경력은 ${CAREER_MAX}자 안으로 적어 주세요.` });
        return;
      }
      this.saving = true;
      try {
        const body = {
          name: this.form.name.trim(),
          email: this.form.email.trim() || null,
          phone: this.form.phone.trim() || null,
          birthDate: this.form.birthDate || null,
        };
        body.gender = this.form.gender || null;
        if (this.showCareer) {
          body.career = this.form.career.trim() || null;
          body.introduction = this.form.introduction.trim() || null;
        }
        await this.updateMyProfile(body);
        this.editing = false;
        this.syncFromUser();
        this.$q.notify({ type: 'positive', message: '회원 정보를 저장했습니다.' });
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '저장에 실패했습니다.' });
      } finally {
        this.saving = false;
      }
    },
    onAvatarClick() {
      if (this.$refs.avatarInput) this.$refs.avatarInput.click();
    },
    async onAvatarSelected(event) {
      const file = event?.target?.files?.[0];
      if (event?.target) event.target.value = '';
      if (!file) return;
      if (!file.type.startsWith('image/')) {
        this.$q.notify({ type: 'warning', message: '이미지 파일만 업로드할 수 있습니다.' });
        return;
      }
      // 바로 올리지 않고 원형 자르기 창을 먼저 — 길쭉한 사진이 동그라미 안에서 찌그러졌다
      this.cropFile = file;
      this.cropOpen = true;
    },
    async uploadAvatar(file) {
      this.avatarUploading = true;
      try {
        const stored = await uploadFile(file, 'PROFILE');
        await this.updateMyProfile({ profileImageId: stored.id });
        this.avatarUrl = stored.url || this.avatarUrl;
        this.$q.notify({ type: 'positive', message: '프로필 사진을 변경했습니다.' });
      } finally {
        // 실패는 자르기 창이 받아 창 안에 알리고 다시 누르게 한다
        this.avatarUploading = false;
      }
    },
    async doLogout() {
      this.loggingOut = true;
      try {
        this.logout();
        // 다시 로그인하면 보던 화면으로 돌아오게 한다 (다른 계정이면 라우터 가드가 막는다)
        this.$router.push({ path: '/login', query: { redirect: this.$route.fullPath } });
      } finally {
        this.loggingOut = false;
      }
    },
    confirmWithdraw() {
      this.$q
        .dialog({
          title: '회원 탈퇴',
          message: '탈퇴하면 서비스 이용이 제한됩니다. 수강·수강평 이력은 삭제되지 않습니다.',
          prompt: {
            model: '',
            type: 'textarea',
            label: '탈퇴 사유 (선택)',
            outlined: true,
            maxlength: 500,
          },
          ok: { label: '다음', color: 'negative', unelevated: true, noCaps: true },
          cancel: { label: '취소', flat: true, noCaps: true },
          persistent: true,
        })
        .onOk((reason) => this.confirmWithdrawWord(reason));
    },
    /** 버튼 한 번으로 되돌릴 수 없는 탈퇴가 일어나지 않게 '탈퇴'를 직접 입력받는다 */
    confirmWithdrawWord(reason) {
      this.$q
        .dialog({
          title: '회원 탈퇴 확인',
          // 계약(POST /users/me/withdraw)은 토큰 무효화·이력 보존만 말한다 — 공유 링크 처리는 조심스럽게 적는다
          message: `탈퇴하면 학습지갑과 공유 링크를 더 이상 이용할 수 없습니다. 계속하려면 '${WITHDRAW_CONFIRM_WORD}'를 입력하세요.`,
          prompt: {
            model: '',
            type: 'text',
            outlined: true,
            isValid: (value) => (value || '').trim() === WITHDRAW_CONFIRM_WORD,
          },
          ok: { label: '탈퇴하기', color: 'negative', unelevated: true, noCaps: true },
          cancel: { label: '취소', flat: true, noCaps: true },
          persistent: true,
        })
        .onOk(() => this.submitWithdraw(reason));
    },
    async submitWithdraw(reason) {
      try {
        await withdrawMe({ reason: (reason || '').trim() || undefined });
        this.$q.notify({ type: 'positive', message: '탈퇴 처리되었습니다.' });
        this.logout();
        this.$router.push('/');
      } catch (error) {
        const message =
          error?.status === 409
            ? '이미 탈퇴 처리된 계정입니다.'
            : error?.message || '탈퇴에 실패했습니다.';
        this.$q.notify({ type: 'negative', message });
      }
    },
  },
};
</script>

<style scoped lang="scss">
.my-account {
  /* 다른 콘솔 화면과 같은 여백·폭 — 위가 좁고 폰 좌우가 8px 이었다.
     1180px 로 묶었더니 위 바·다른 화면보다 오른쪽이 짧아 어긋났다 */
  padding: 48px 40px 32px;
}

@media (max-width: 1023px) {
  .my-account {
    padding: 24px 16px;
  }
}

.my-account__profile-block + .my-account__profile-block {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--db-border-light);
}

.my-account__profile-block .my-account__label {
  margin-bottom: 8px;
}

/* 소개 — 한두 문장 인용처럼(왼쪽 선) */
.my-account__intro {
  margin: 0;
  padding: 4px 0 4px 14px;
  border-left: 3px solid var(--db-accent);
  font-size: 16px;
  line-height: 1.6;
  color: var(--db-text-title);
  white-space: pre-line;
  overflow-wrap: anywhere;
}

.my-account__btn {
  height: 36px;
  padding: 0 14px;
  white-space: nowrap;
}

.my-account__card {
  border-radius: 12px;
  background: var(--db-bg-white);
}

/* 사진 — 사이트 주 색(예전 분홍은 다른 화면과 따로 놀았다) */
.my-account__avatar {
  background: var(--db-primary);
  color: var(--db-on-color);
}

/* 자르기 전에 올린 길쭉한 사진도 찌그러지지 않게 — 가운데를 채워 자른다 */
.my-account__avatar img {
  object-fit: cover;
}

.my-account__identity {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  width: 100%;
  margin-top: 16px;
  text-align: center;
}

.my-account__name {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.my-account__avatar-wrap {
  position: relative;
  display: inline-block;
}

.my-account__camera {
  position: absolute;
  right: 0;
  bottom: 4px;
}

.hidden {
  display: none;
}

.my-account__row {
  display: flex;
  align-items: center;
  gap: 24px;
  min-height: 36px;
}

.my-account__field {
  padding: 10px 0;
  border-bottom: 1px solid var(--db-border-light);
}

.my-account__label {
  min-width: 96px;
  color: var(--db-text-sub);
  font-size: 0.875rem;
  font-weight: 600;
}

.my-account__value {
  color: var(--db-text-title);
  font-size: 0.95rem;
}

/* 폰: 이름 칸(96px)+간격(24px)이 넓어 아이디가 'seed-/admin…'으로 꺾였다 */
@media (max-width: 599px) {
  .my-account__row {
    gap: 12px;
  }

  .my-account__label {
    min-width: 64px;
  }
}
</style>
