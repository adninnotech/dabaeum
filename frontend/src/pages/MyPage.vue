<template>
  <q-page class="mypage">
    <div class="learner-container mypage-body">
      <header class="mypage-header">
        <h1 class="mypage-header__title">마이페이지</h1>
      </header>

      <div class="mypage-grid">
        <!-- 좌: 프로필 -->
        <aside class="mypage-card mypage-profile">
          <div class="mypage-profile__avatar-wrap">
            <q-avatar :size="$q.screen.lt.md ? '64px' : '120px'" class="letter-avatar">
              <img v-if="avatarUrl" :src="avatarUrl" alt="프로필 사진" />
              <span v-else class="mypage-profile__letter">{{ avatarLetter }}</span>
            </q-avatar>
            <button
              type="button"
              class="mypage-profile__camera"
              aria-label="프로필 사진 변경"
              :disabled="avatarUploading"
              @click="pickAvatar"
            >
              <q-icon name="photo_camera" size="18px" />
            </button>
            <input
              ref="avatarInput"
              type="file"
              accept="image/*"
              class="mypage-profile__file"
              @change="onAvatarSelected"
            />
            <AvatarCropDialog v-model="cropOpen" :file="cropFile" :upload="uploadAvatar" />
          </div>

          <div class="mypage-profile__text">
            <div class="mypage-profile__name">{{ user?.name || '-' }}</div>
            <div v-if="profileSubLine" class="mypage-profile__email">{{ profileSubLine }}</div>
            <!-- 강사·기관 관리자 소속 — 콘솔 마이페이지에만 있어 학습자 화면에선 소속이 안 보였다 -->
            <div class="mypage-profile__affiliation">
              <MyAffiliationList />
            </div>
          </div>

          <q-btn
            outline
            no-caps
            color="primary"
            class="mypage-profile__logout"
            label="로그아웃"
            :loading="loggingOut"
            @click="doLogout"
          />
        </aside>

        <!-- 우: 정보 블록 -->
        <div class="mypage-main">
          <!-- ① 로그인 정보 -->
          <section class="mypage-card">
            <h2 class="mypage-card__title">로그인 정보</h2>
            <div class="mypage-card__body">
              <div class="mypage-line">
                <!-- 이메일 로그인이 없는(다대구 가입) 계정은 이 주소로 로그인할 수 없다 — '아이디'라 하면 로그인 주소로 읽혔다 -->
                <span class="mypage-line__label">{{ localLinked === false ? '계정 이메일' : '아이디' }}</span>
                <span class="mypage-line__value">{{ loginId }}</span>
              </div>
              <!-- 다대구로 가입한 계정은 이메일·비밀번호가 없다 — 바꿀 비밀번호가 없으니 행을 숨긴다.
                   다대구 가입이어도 이메일은 있을 수 있어 email 만 보면 [비밀번호 변경]과 '이메일 로그인 연결 안 됨'이
                   함께 떴다(누르면 404). 이메일 로그인 연결이 없다고 확인되면(localLinked === false) 숨긴다 -->
              <div v-if="user?.email && localLinked !== false" class="mypage-line">
                <span class="mypage-line__label">비밀번호</span>
                <span class="mypage-line__value">••••••••</span>
                <q-btn
                  outline
                  no-caps
                  dense
                  color="primary"
                  label="비밀번호 변경"
                  class="mypage-line__action"
                  @click="passwordOpen = true"
                />
              </div>
              <!-- 다대구로만 가입한 계정에 이메일 로그인을 붙이던 [이메일·비밀번호 연결]은 뺐다 — 로그인은 다대구로만(#112) -->
              <div class="mypage-line">
                <span class="mypage-line__label">다대구 연결</span>
                <span class="mypage-line__value">
                  <!-- 로그인 정보 한 줄이라 빨간 바탕 대신 흰 바탕 브랜드형 — [비밀번호 변경]과 같은 무게 -->
                  <DadaeguLinkButton
                    outline
                    :linked="dadaeguLinked"
                    :blocked-reason="dadaeguBlockedReason"
                    @linked="onDadaeguLinked"
                  />
                </span>
              </div>
            </div>
          </section>

          <!-- ② 사용자 정보 -->
          <section class="mypage-card">
            <h2 class="mypage-card__title">
              사용자 정보
              <q-btn
                v-if="!editing"
                flat
                no-caps
                dense
                class="mypage-card__more"
                label="회원 정보 수정 >"
                @click="startEdit"
              />
              <span v-else class="mypage-card__edit-actions">
                <q-btn flat no-caps dense label="취소" @click="cancelEdit" />
                <q-btn
                  unelevated
                  no-caps
                  dense
                  color="primary"
                  label="저장"
                  :loading="saving"
                  @click="saveProfile"
                />
              </span>
            </h2>

            <div class="mypage-card__body">
              <!-- 로그인 정보와 같은 '이름표 | 값' 줄 — 예전엔 바로 위 카드와 칸 모양이 달랐다 -->
              <template v-if="!editing">
                <div v-for="item in displayFields" :key="item.label" class="mypage-line">
                  <span class="mypage-line__label">{{ item.label }}</span>
                  <span class="mypage-line__value">
                    {{ item.value }}
                    <span v-if="item.note" class="mypage-field__note">{{ item.note }}</span>
                  </span>
                </div>
              </template>

              <div v-else class="mypage-fields">
                <q-input v-model="form.name" outlined dense label="이름" />
                <AppDateInput ref="birthDateInput" v-model="form.birthDate" outlined dense label="생년월일" default-view="Years" no-future />
                <q-input v-model="form.phone" outlined dense label="휴대폰번호" />
                <q-select
                  v-model="form.gender"
                  outlined
                  dense
                  emit-value
                  map-options
                  :options="GENDER_OPTIONS"
                  label="성별"
                />
              </div>
            </div>
          </section>

          <!-- 관심 분야(#142, 구·군 대신) — 학습자만. 사용자 정보와 따로 저장한다: 고르려면 [선택] 동의가 먼저라서.
               모두 지우고 저장하면 동의 철회로 기록된다 -->
          <section v-if="showInterest" class="mypage-card">
            <h2 class="mypage-card__title">
              관심 분야
              <q-btn
                v-if="!interestEditing"
                flat
                no-caps
                dense
                class="mypage-card__more"
                label="관심 분야 수정 >"
                @click="startInterestEdit"
              />
              <span v-else class="mypage-card__edit-actions">
                <q-btn flat no-caps dense label="취소" :disable="interestSaving" @click="interestEditing = false" />
                <q-btn
                  unelevated
                  no-caps
                  dense
                  color="primary"
                  label="저장"
                  :loading="interestSaving"
                  @click="saveInterest"
                />
              </span>
            </h2>
            <div class="mypage-card__body">
              <InterestPicker
                v-if="interestEditing"
                v-model="interestForm.codes"
                v-model:agreed="interestForm.agreed"
                :disabled="interestSaving"
                :show-title="false"
              />
              <!-- 폰에선 고르는 칸이 길어 카드 위 [저장]까지 다시 올라가야 했다 — 아래에도 둔다 -->
              <div v-if="interestEditing" class="mypage-interest__actions">
                <q-btn outline no-caps color="primary" label="취소" :disable="interestSaving" @click="interestEditing = false" />
                <q-btn unelevated no-caps color="primary" label="저장" :loading="interestSaving" @click="saveInterest" />
              </div>
              <template v-else>
                <!-- 고칠 때와 같은 쉬운 이름·순서 -->
                <ol v-if="interestItems.length" class="mypage-interest">
                  <li v-for="(item, index) in interestItems" :key="item.code" class="mypage-interest__item">
                    <span class="mypage-interest__order" aria-hidden="true">{{ index + 1 }}</span>{{ item.name }}
                  </li>
                </ol>
                <p v-else class="mypage-interest__empty">
                  아직 고른 관심 분야가 없어요. 고르면 관심 분야에 맞는 강좌를 먼저 추천해 드려요.
                </p>
              </template>
            </div>
          </section>

          <!-- 강사·기관 신청은 이름·이메일과 상관없는 일인데 사용자 정보 카드 아래에 붙어 있었다 — 따로 묶는다.
               강사·기관 관리자는 이메일로 따로 가입한다(#134) — 신청·현황 링크는 이메일 계정에만, 다대구 계정엔 안내만 -->
          <section v-if="localLinked === false" class="mypage-card">
            <h2 class="mypage-card__title">강사·기관 관리자로 참여하기</h2>
            <div class="mypage-card__body">
              <p class="mypage-staff-note">
                강사·기관 관리자는 이메일로 따로 가입해요. 이 다대구 계정은 학습용으로 그대로 쓸 수 있어요.
                <router-link to="/login/staff" class="mypage-staff-note__link">강사·기관 관리자 로그인</router-link>
              </p>
            </div>
          </section>
          <section v-else-if="localLinked" class="mypage-card">
            <h2 class="mypage-card__title">다른 역할로 참여하기</h2>
            <div class="mypage-card__body mypage-roles">
              <router-link to="/mypage/instructor-apply" class="mypage-role">
                <span class="mypage-role__text">
                  <span class="mypage-role__name">강사 신청</span>
                  <span class="mypage-role__desc">기관에서 강의를 열고 수강생을 관리합니다.</span>
                </span>
                <q-icon name="chevron_right" size="20px" class="mypage-role__go" aria-hidden="true" />
              </router-link>
              <!-- 이미 가입한 계정으로 기관을 등록한다 — 기관 관리자 회원가입은 새 계정을 만든다 -->
              <router-link to="/mypage/institution-apply" class="mypage-role">
                <span class="mypage-role__text">
                  <span class="mypage-role__name">기관 등록 신청</span>
                  <span class="mypage-role__desc">이 계정으로 교육 기관을 등록하고 운영합니다.</span>
                </span>
                <q-icon name="chevron_right" size="20px" class="mypage-role__go" aria-hidden="true" />
              </router-link>
            </div>
          </section>

          <!-- 알림 및 수신 설정(이메일·SMS)은 뺐다(#144) — 메일·문자를 보내는 코드가 없어 동작하지 않는 설정이었다 -->

          <!-- ③ 화면 설정 — 머리 아이콘과 같은 값. 설정을 찾는 사람은 마이페이지부터 연다 -->
          <section class="mypage-card">
            <h2 class="mypage-card__title">화면 설정</h2>
            <div class="mypage-card__body">
              <ThemeSettingCard />
            </div>
          </section>

          <div class="mypage-withdraw">
            <!-- 폰·태블릿에선 프로필 카드가 맨 위라 로그아웃이 첫 화면의 가장 큰 동작이었다 — 아래로 내린다 -->
            <q-btn
              outline
              no-caps
              color="primary"
              label="로그아웃"
              class="mypage-withdraw__logout"
              :loading="loggingOut"
              @click="doLogout"
            />
            <button type="button" class="mypage-withdraw__link" @click="withdrawOpen = true">
              회원 탈퇴
            </button>
          </div>
        </div>
      </div>
    </div>


    <PasswordChangeDialog v-model="passwordOpen" />
    <WithdrawDialog v-model="withdrawOpen" />

  </q-page>
</template>

<script>
import AppDateInput from '@/components/AppDateInput.vue';
import { BIRTH_DATE_INVALID } from '@/utils/api-error-message';
import AvatarCropDialog from '@/components/mypage/AvatarCropDialog.vue';
import MyAffiliationList from '@/components/mypage/MyAffiliationList.vue';
import { mapActions, mapState } from 'pinia';
import DadaeguLinkButton from '@/components/DadaeguLinkButton.vue';
import PasswordChangeDialog from '@/components/mypage/PasswordChangeDialog.vue';
import ThemeSettingCard from '@/components/ThemeSettingCard.vue';
import WithdrawDialog from '@/components/mypage/WithdrawDialog.vue';
import { storedFileContentUrl, uploadFile } from '@/services/file-api';
import InterestPicker from '@/components/auth/InterestPicker.vue';
import { isInterestInputEnabled } from '@/config/features';
import { listCourseClassifications } from '@/services/classification-api';
import { listMyIdentities } from '@/services/user-api';
import { useAuthStore } from '@/stores/auth-store';
import { formatDateKst } from '@/utils/datetime';
import { interestOptions } from '@/utils/interest';

const GENDER_OPTIONS = [
  { label: '남성', value: 'MALE' },
  { label: '여성', value: 'FEMALE' },
  { label: '밝히지 않음', value: 'UNDISCLOSED' },
];

export default {
  name: 'MyPage',
  components: {
    AppDateInput,
    AvatarCropDialog,
    DadaeguLinkButton,
    InterestPicker,
    MyAffiliationList,
    PasswordChangeDialog,
    ThemeSettingCard,
    WithdrawDialog,
  },
  data() {
    return {
      GENDER_OPTIONS,
      editing: false,
      saving: false,
      loggingOut: false,
      avatarUrl: '',
      avatarUploading: false,
      cropOpen: false,
      cropFile: null,
      identityVerified: false,
      dadaeguLinked: false,
      form: { name: '', phone: '', birthDate: '', gender: '' },
      interestEnabled: isInterestInputEnabled(),
      interestChoices: [],
      interestEditing: false,
      interestSaving: false,
      interestForm: { codes: [], agreed: false },
      passwordOpen: false,
      /** true/false = LOCAL identity 유무, null = 조회 실패(행을 숨긴다) */
      localLinked: null,
      withdrawOpen: false,
    };
  },
  computed: {
    ...mapState(useAuthStore, ['user', 'userId', 'avatarLetter', 'impersonating', 'isDemoSession', 'isLearner']),
    // 대리 로그인은 관리자 토큰으로 /users/me 를 부르므로 엉뚱한 계정에 연결된다 — 실제 로그인에서만 연다
    dadaeguBlockedReason() {
      return this.impersonating || this.isDemoSession ? '실제 로그인 계정에서만 연결할 수 있습니다' : '';
    },
    // 다대구로 가입한 계정은 email 이 null 이다 — '-' 나 UUID 대신 로그인 방식을 보여 준다
    loginId() {
      if (this.user?.email) return this.user.email;
      return this.dadaeguLinked === true ? '다대구 로그인' : '-';
    },
    // 이메일은 바로 옆 로그인 정보에 이미 나온다 — 여기는 로그인 방식만
    profileSubLine() {
      return this.dadaeguLinked === true ? '다대구 로그인 계정' : '';
    },
    // 관심 분야는 학습자만 쓴다(다른 역할이 값을 보내면 422)
    showInterest() {
      return this.interestEnabled && this.isLearner;
    },
    // 선택지를 못 받았으면 코드라도 보여 준다 — 빈칸보다 낫다
    interestItems() {
      const codes = this.user?.interestCodes || [];
      const byCode = new Map(this.interestChoices.map((item) => [item.code, item]));
      return codes.map((code) => byCode.get(code) || { code, name: code });
    },
    genderLabel() {
      if (!this.form.gender) return '-';
      return GENDER_OPTIONS.find((item) => item.value === this.form.gender)?.label || '-';
    },
    displayFields() {
      return [
        { label: '이름', value: this.user?.name || '-' },
        { label: '생년월일', value: formatDateKst(this.user?.birthDate) },
        {
          label: '휴대폰번호',
          value: this.user?.phone || '-',
          note: this.user?.phone && this.identityVerified === true ? '인증됨' : '',
        },
        { label: '성별', value: this.genderLabel },
      ];
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
    if (this.showInterest) this.loadInterestChoices();
  },
  methods: {
    ...mapActions(useAuthStore, ['updateMyProfile', 'logout', 'bootstrapSession']),
    syncFromUser() {
      if (!this.user) return;
      // 고치는 중엔 칸을 덮지 않는다 — 알림 스위치 저장도 내 정보를 새로 바꿔 이 함수가 도는데, 그때 적던 값이
      // 서버 값으로 되돌아가 [저장]하면 옛 값이 저장됐다(QA 중앙 #51). 알림·사진은 칸과 따로라 늘 맞춘다
      if (!this.editing) {
        this.form = {
          name: this.user.name || '',
          phone: this.user.phone || '',
          birthDate: this.user.birthDate ? String(this.user.birthDate).slice(0, 10) : '',
          gender: this.user.gender || '',
        };
      }
      this.avatarUrl = this.user.profileImageId
        ? storedFileContentUrl(this.user.profileImageId)
        : '';
    },
    // 조회 실패는 "미인증"과 다르다 — null 로 두어 인증 표시를 하지 않는다
    async loadIdentities() {
      if (!this.userId) return;
      try {
        const list = await listMyIdentities();
        this.identityVerified = list.some((item) => item.verifiedAt);
        this.dadaeguLinked = list.some((item) => item.provider === 'DADAEGU');
        this.localLinked = list.some((item) => item.provider === 'LOCAL');
      } catch {
        this.identityVerified = null;
        this.dadaeguLinked = null;
        this.localLinked = null;
      }
    },
    // 보기 칸의 이름만 쓴다 — 실패하면 코드로 보이고, 고치기 칸(InterestPicker)은 따로 불러와 다시 시도할 수 있다
    async loadInterestChoices() {
      try {
        this.interestChoices = interestOptions(await listCourseClassifications());
      } catch {
        this.interestChoices = [];
      }
    },
    startInterestEdit() {
      this.interestForm = { codes: [...(this.user?.interestCodes || [])], agreed: this.user?.interestAgreed === true };
      this.interestEditing = true;
    },
    // [] 를 보내면 서버가 관심 분야를 지우고 동의 철회를 기록한다. 값을 보낼 땐 동의도 같이 싣는다(동의 전 회원은 필수)
    async saveInterest() {
      const { codes, agreed } = this.interestForm;
      this.interestSaving = true;
      try {
        await this.updateMyProfile(codes.length ? { interestCodes: codes, agreedToInterest: agreed } : { interestCodes: [] });
        this.interestEditing = false;
        this.$q.notify({
          type: 'positive',
          message: codes.length ? '관심 분야를 저장했습니다.' : '관심 분야를 지우고 수집·이용 동의를 철회했습니다.',
          position: 'top',
        });
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error?.message || '관심 분야를 저장하지 못했습니다.', position: 'top' });
      } finally {
        this.interestSaving = false;
      }
    },
    // 연결되면 이름·전화·생년월일이 다대구 값으로 덮어써진다 — identity 와 내 정보를 다시 받는다
    async onDadaeguLinked() {
      await Promise.all([this.loadIdentities(), this.bootstrapSession()]);
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
      this.saving = true;
      try {
        await this.updateMyProfile({
          name: this.form.name.trim(),
          // 이메일은 보내지 않는다(#142 학습자 화면에서 뺐다) — 보내지 않으면 서버 값이 그대로 남는다
          phone: this.form.phone.trim() || null,
          birthDate: this.form.birthDate || null,
          gender: this.form.gender || null,
        });
        this.editing = false;
        this.syncFromUser();
        this.$q.notify({ type: 'positive', message: '회원 정보를 저장했습니다.', position: 'top' });
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error?.message || '저장에 실패했습니다.',
          position: 'top',
        });
      } finally {
        this.saving = false;
      }
    },
    pickAvatar() {
      this.$refs.avatarInput?.click();
    },
    async onAvatarSelected(event) {
      const file = event?.target?.files?.[0];
      if (event?.target) event.target.value = '';
      if (!file) return;
      if (!file.type.startsWith('image/')) {
        this.$q.notify({ type: 'warning', message: '이미지 파일만 올릴 수 있습니다.' });
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
    doLogout() {
      this.loggingOut = true;
      this.logout();
      // 다시 로그인하면 보던 화면으로 돌아오게 한다 (다른 계정이면 라우터 가드가 막는다)
      this.$router.push({ path: '/login', query: { redirect: this.$route.fullPath } });
    },
  },
};
</script>

<style scoped>
.mypage {
  padding: 24px 0;
  background: var(--db-bg-page);
}

.mypage-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.mypage-header {
  display: flex;
  align-items: center;
  height: 51px;
  /* 좌우 10px 이 있어 제목만 아래 카드보다 들여쓰여 보였다 — 다른 학습자 화면처럼 카드와 같은 선 */
  padding: 10px 0;
}

.mypage-header__title {
  margin: 0;
  font-size: 26px;
  line-height: 31px;
  font-weight: 700;
  color: var(--db-text-body);
}

/* 피그마 필드 px 미확보 — 좌 320 / 우 나머지 (52 결정) */
.mypage-grid {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}

.mypage-card {
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.mypage-card__title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  padding: 12px 16px;
  font-size: 16px;
  line-height: 24px;
  font-weight: 600;
  color: var(--db-text-title);
}

.mypage-card__more {
  margin-left: auto;
  color: var(--db-accent);
}

.mypage-card__edit-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.mypage-card__body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
}

/* 좌: 프로필 */
.mypage-profile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px;
}

.mypage-profile__avatar-wrap {
  position: relative;
  margin-bottom: 8px;
}

/* 자르기 전에 올린 길쭉한 사진도 찌그러지지 않게 — 가운데를 채워 자른다 */
.mypage-profile__avatar-wrap img {
  object-fit: cover;
}

.mypage-profile__letter {
  font-size: 40px;
  font-weight: 700;
}

.mypage-profile__camera {
  position: absolute;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 1px solid var(--db-border);
  border-radius: 50%;
  background: var(--db-bg-white);
  color: var(--db-text-body);
  cursor: pointer;
}

.mypage-profile__camera:disabled {
  cursor: default;
  opacity: 0.6;
}

.mypage-profile__file {
  display: none;
}

.mypage-profile__text {
  text-align: center;
}

.mypage-profile__name {
  font-size: 20px;
  line-height: 28px;
  font-weight: 700;
  color: var(--db-text-title);
}

.mypage-profile__email {
  font-size: 14px;
  line-height: 20px;
  color: var(--db-text-sub);
}

.mypage-profile__affiliation {
  margin-top: 4px;
}

.mypage-profile__affiliation:empty {
  display: none;
}

/* Quasar 기본 radius 3px — /learning [검색]과 같은 r6으로 맞춘다 */
.mypage-profile__logout {
  width: 100%;
  margin-top: 16px;
  border-radius: 6px;
}

/* 우: 정보 블록 */
.mypage-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.mypage-line {
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 36px;
}

.mypage-line__label {
  width: 96px;
  flex-shrink: 0;
  font-size: 14px;
  color: var(--db-text-sub);
}

.mypage-line__value {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 15px;
  font-weight: 500;
  color: var(--db-text-body);
}

.mypage-line__action {
  flex-shrink: 0;
}

/* dense 버튼은 좌우 여백이 4px 남짓이라 글자가 테두리에 붙어 보였다 — 글자 둘레에 숨 쉴 자리를 준다 */
.mypage-line__action.q-btn {
  /* 같은 카드의 다대구 연결 버튼(32px·좌우 14px)과 크기를 맞춘다 */
  min-height: 32px;
  padding: 0 14px;
  font-size: 14px;
  border-radius: 6px;
}

/* 폰: 라벨(96px) + 값 + 버튼이 한 줄에 안 들어가 버튼이 다음 줄로 밀리고 아이디가 잘렸다 —
 * 이름표를 위 한 줄로 올리고, 값과 버튼을 그 아래 한 줄에(버튼은 오른쪽 끝) */
@media (max-width: 599px) {
  .mypage-line {
    flex-wrap: wrap;
    gap: 2px 12px;
  }

  .mypage-line__label {
    flex-basis: 100%;
    width: auto;
    font-size: 14px;
  }

  .mypage-line__value {
    white-space: normal;
    overflow-wrap: anywhere;
  }

  .mypage-line__action.q-btn {
    /* 손가락으로 누르는 크기 */
    min-height: 40px;
  }
}

.mypage-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 24px;
}

.mypage-field__note {
  margin-left: 6px;
  font-size: 13px;
  font-weight: 400;
  color: var(--db-text-sub);
}

/* 관심 분야 보기 — 고른 분야를 이름 + 예시 칸으로. 고치기는 InterestPicker */
.mypage-interest {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.mypage-interest__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 14px 0 8px;
  border: 1px solid var(--db-border);
  border-radius: 999px;
  background: var(--db-bg-subtle);
  font-size: 15px;
  font-weight: 600;
  color: var(--db-text-title);
  word-break: keep-all;
}

.mypage-interest__order {
  display: inline-grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--db-primary);
  font-size: 12px;
  font-weight: 700;
  color: var(--db-bg-white);
}

/* 고치는 중 아래 [취소][저장] — 폰에서만. PC 는 카드 제목 줄의 버튼이 눈에 든다 */
.mypage-interest__actions {
  display: none;
}

@media (max-width: 599px) {
  .mypage-interest__actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 16px;
  }

  .mypage-interest__actions .q-btn {
    min-height: 44px;
    min-width: 88px;
  }
}

.mypage-interest__empty {
  margin: 0;
  font-size: 15px;
  line-height: 22px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

/* 다대구 계정 — 강사·기관 신청 대신 따로 가입한다는 안내(#134) */
.mypage-staff-note {
  margin: 0;
  font-size: 15px;
  line-height: 1.6;
  color: var(--db-text-body);
  word-break: keep-all;
}

.mypage-staff-note__link {
  display: inline-block;
  margin-left: 4px;
  font-weight: 600;
  color: var(--db-primary);
  text-decoration: underline;
  text-underline-offset: 3px;
  white-space: nowrap;
}

.mypage-roles {
  gap: 0;
  padding-top: 4px;
  padding-bottom: 4px;
}

.mypage-role {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 56px;
  padding: 8px 0;
  color: inherit;
  text-decoration: none;
}

.mypage-role + .mypage-role {
  border-top: 1px solid var(--db-border);
}

.mypage-role:hover .mypage-role__name {
  color: var(--db-accent);
}

.mypage-role:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
  border-radius: 4px;
}

.mypage-role__text {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.mypage-role__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--db-text-title);
}

.mypage-role__desc {
  font-size: 14px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.mypage-role__go {
  flex-shrink: 0;
  color: var(--db-text-sub);
}

/* 버튼이 아니라 텍스트 링크. 로그아웃은 한 칸 그리드(폰·태블릿)에서만 여기에 */
.mypage-withdraw {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.mypage-withdraw__logout {
  display: none;
  border-radius: 6px;
}

.mypage-withdraw__link {
  padding: 4px;
  background: none;
  border: none;
  font-size: 14px;
  color: var(--db-text-sub);
  text-decoration: underline;
  cursor: pointer;
}

@media (max-width: 1023px) {
  .mypage-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .mypage-fields {
    grid-template-columns: minmax(0, 1fr);
  }

  /* 프로필은 가로 한 줄 — 120px 아바타가 첫 화면 절반을 차지했다 */
  .mypage-profile {
    flex-direction: row;
    align-items: center;
    gap: 16px;
    padding: 16px;
  }

  .mypage-profile__avatar-wrap {
    margin-bottom: 0;
  }

  .mypage-profile__letter {
    font-size: 24px;
  }

  .mypage-profile__camera {
    right: -4px;
    bottom: -4px;
    width: 28px;
    height: 28px;
  }

  .mypage-profile__text {
    min-width: 0;
    text-align: left;
  }

  .mypage-profile__name {
    font-size: 18px;
    line-height: 26px;
    overflow-wrap: anywhere;
  }

  .mypage-profile__logout {
    display: none;
  }

  .mypage-withdraw {
    justify-content: space-between;
  }

  .mypage-withdraw__logout {
    display: inline-flex;
  }
}
</style>
