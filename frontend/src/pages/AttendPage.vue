<template>
  <!-- 디자인 파일 없음 — 수강생모바일 톤(흰 카드·회색 테두리·primary 버튼)에 맞춘 모바일 전용 화면 -->
  <div class="attend">
    <header class="attend__header">
      <BrandLogo />
    </header>

    <main class="attend__card">
      <div class="attend__icon" :class="`attend__icon--${view.tone}`">
        <q-spinner v-if="view.spinner" size="40px" color="primary" />
        <q-icon v-else :name="view.icon" size="44px" />
      </div>

      <!-- 상태가 바뀌면 화면 읽기 프로그램이 새 제목·설명을 읽게 -->
      <div class="attend__message" role="status" aria-live="polite">
        <h1 class="attend__title">{{ view.title }}</h1>
        <p class="attend__desc">{{ view.desc }}</p>
      </div>

      <!-- 처음 쓰는 사람은 앱에서 이 화면으로 돌아와야 한다는 걸 몰랐다 -->
      <ol v-if="view.steps" class="attend__steps">
        <!-- 지금 할 차례만 진하게 — 앱에서 그냥 돌아왔으면(pending) 아직 ①, 앱으로 넘어간 뒤엔 ② -->
        <li class="attend__step" :class="{ 'attend__step--now': phase === 'pending' }">
          <span class="attend__step-no" aria-hidden="true">1</span>다대구 앱에서 본인 인증
        </li>
        <li class="attend__step" :class="{ 'attend__step--now': phase !== 'pending' }">
          <span class="attend__step-no" aria-hidden="true">2</span>이 화면으로 돌아와 [앱에서 인증했어요]
        </li>
      </ol>

      <!-- 출석 완료: 어느 강좌·회차가 언제 기록됐는지 보여 줘야 강사에게 화면으로 확인시킬 수 있다 -->
      <dl v-if="phase === 'recorded' && recordedDetails.length" class="attend__details">
        <div v-for="item in recordedDetails" :key="item.label" class="attend__detail">
          <dt>{{ item.label }}</dt>
          <dd>{{ item.value }}</dd>
        </div>
      </dl>

      <div class="attend__actions">
        <!-- 이 화면에서 할 수 있는 유일한 행동이라 작은 글자 링크가 아니라 버튼으로 -->
        <router-link v-if="view.link" :to="view.link.to" class="attend__btn attend__btn--primary attend__btn--link">
          {{ view.link.label }}
        </router-link>
        <button
          v-if="view.continueHere"
          type="button"
          class="attend__btn attend__btn--line"
          @click="continueHere"
        >
          이 기기에서 계속하기
        </button>
        <!-- 앱에서 그냥 돌아왔으면(pending) 할 일은 앱 다시 열기 — 그때만 [다대구 앱 다시 열기]를 위 남색으로.
             인증은 했는데 반영이 늦은 사람을 위해 [앱에서 인증했어요]는 바로 아래에 남긴다 -->
        <button
          v-if="view.confirm"
          type="button"
          class="attend__btn"
          :class="appFirst ? 'attend__btn--line' : 'attend__btn--primary'"
          :disabled="checking"
          @click="checkResult"
        >
          {{ checking ? '확인 중…' : '앱에서 인증했어요' }}
        </button>
        <button
          v-if="view.openApp && deepLink"
          type="button"
          class="attend__btn"
          :class="appFirst ? 'attend__btn--primary attend__btn--first' : 'attend__btn--line'"
          @click="openApp"
        >
          다대구 앱 다시 열기
        </button>
        <button
          v-if="view.retry"
          type="button"
          class="attend__btn attend__btn--primary"
          :disabled="opening"
          @click="openSession"
        >
          다시 시도
        </button>
      </div>

      <!-- 이 화면은 로그인 없이 다대구로만 인증한다 — 출결 보기는 로그인이 필요해 보조 링크로 -->
      <router-link v-if="phase === 'recorded'" :to="attendanceLink" class="attend__link attend__link--block">
        내 출결 보기 (로그인 필요)
      </router-link>
    </main>
  </div>
</template>

<script>
import BrandLogo from '@/components/BrandLogo.vue';
import { getAttendSession, openAttendSession } from '@/services/attendance-api';
import { formatDateMinuteKst } from '@/utils/datetime';

const NEW_QR_MESSAGE = '강사 화면의 새 QR을 다시 찍어 주세요.';

const FAILED_MESSAGES = {
  // 이 DID 에 연결된 다배움 계정이 없다는 뜻 — 이메일로 가입한 회원도 다대구를 연결하지 않았으면 받는다.
  // "다대구로 로그인" 을 권하지 않는다: 이메일 회원이 다대구로 처음 로그인하면 수강신청 없는 별도 계정이 생긴다
  AUTHENTICATION_FAILED: {
    title: '다대구 계정이 연결된 회원이 아닙니다',
    // 수업 중이라 오늘 출석이 먼저 — 연결 방법은 그다음
    desc: '오늘 출석은 강사에게 처리를 요청해 주세요. 다음 수업부터 QR로 출석하려면 마이페이지에서 다대구를 연결해 주세요.',
    link: { to: '/mypage', label: '마이페이지에서 다대구 연결하기' },
  },
  ENROLLMENT_NOT_FOUND: {
    title: '수강 중인 강좌가 아닙니다',
    desc: '이 강좌에 승인된 수강신청이 없습니다. 담당 강사에게 문의해 주세요.',
  },
  ENROLLMENT_STATUS_CONFLICT: {
    title: '수강 중인 강좌가 아닙니다',
    desc: '이 강좌에 승인된 수강신청이 없습니다. 담당 강사에게 문의해 주세요.',
  },
  ATTENDANCE_CONFLICT: {
    title: '이미 출석했습니다',
    desc: '이 회차 출석은 이미 기록되어 있습니다.',
  },
};

const VIEWS = {
  opening: { tone: 'info', spinner: true, title: '출석 준비 중', desc: '다대구 앱을 여는 중입니다. 잠시만 기다려 주세요.' },
  waiting: {
    tone: 'info',
    icon: 'smartphone',
    title: '다대구 앱에서 인증해 주세요',
    desc: '앱에서 인증을 마치고 이 화면으로 돌아오면 출석이 확인됩니다.',
    confirm: true,
    openApp: true,
    steps: true,
  },
  pending: {
    tone: 'warn',
    icon: 'hourglass_empty',
    title: '아직 인증 전입니다',
    // 위 남색 버튼(다대구 앱 다시 열기)과 같은 순서로 안내한다
    desc: '다대구 앱을 다시 열어 인증을 마친 뒤 이 화면으로 돌아와 주세요.',
    confirm: true,
    openApp: true,
    steps: true,
  },
  // PC 에는 다대구 앱이 없어 '앱에서 인증해 주세요'에서 멈췄다 — 휴대폰으로 안내하고, 그래도 계속할 수는 있게
  desktop: {
    tone: 'info',
    icon: 'qr_code_scanner',
    title: '휴대폰에서 열어 주세요',
    desc: '강사 화면의 QR을 휴대폰 기본 카메라로 찍으면 이 출석 화면이 열립니다. 출석은 휴대폰의 다대구 앱으로 인증합니다.',
    continueHere: true,
  },
  // '다시 QR을 찍어 주세요. 강사 화면의 새 QR을 다시 찍어 주세요.' 로 같은 말을 두 번 했다
  expired: {
    tone: 'warn',
    icon: 'timer_off',
    title: '인증 시간이 지났습니다',
    desc: '다대구 인증을 기다리는 시간이 지났습니다. 강사 화면의 QR을 다시 찍어 주세요.',
  },
  qrInvalid: { tone: 'warn', icon: 'qr_code_2', title: 'QR이 만료되었습니다', desc: NEW_QR_MESSAGE },
  unavailable: {
    tone: 'error',
    icon: 'cloud_off',
    title: '다대구 연결에 실패했습니다',
    desc: '잠시 후 다시 시도해 주세요.',
    retry: true,
  },
};

function isMobileDevice() {
  return /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent);
}

export default {
  name: 'AttendPage',
  components: { BrandLogo },
  data() {
    return {
      attendId: null,
      deepLink: '',
      phase: 'opening',
      studentName: '',
      // 결과 응답에 강좌·회차·기록 시각이 오면 쓰고, 없으면 이 화면이 확인한 시각만 보인다
      recordedInfo: null,
      errorCode: '',
      errorText: '',
      opening: false,
      checking: false,
    };
  },
  computed: {
    token() {
      const t = this.$route.query.t;
      return typeof t === 'string' ? t : '';
    },
    view() {
      if (this.phase === 'recorded') {
        return {
          tone: 'success',
          icon: 'check_circle',
          title: `${this.studentName || '수강생'}님 출석 완료`,
          desc: '출석이 기록되었습니다. 이 화면을 닫아도 됩니다.',
        };
      }
      if (this.phase === 'failed') {
        const known = FAILED_MESSAGES[this.errorCode];
        return {
          tone: 'error',
          icon: 'error_outline',
          title: known?.title || '출석을 기록하지 못했습니다',
          desc: known?.desc || '담당 강사에게 문의해 주세요.',
          link: known?.link || null,
        };
      }
      if (this.phase === 'error') {
        return { tone: 'error', icon: 'error_outline', title: '출석을 시작하지 못했습니다', desc: this.errorText };
      }
      return VIEWS[this.phase] || VIEWS.opening;
    },
    recordedDetails() {
      const info = this.recordedInfo || {};
      return [
        { label: '강좌', value: info.courseTitle },
        { label: '회차', value: info.sessionNo ? `${info.sessionNo}회차` : '' },
        { label: info.fromServer ? '출석 시각' : '확인 시각', value: formatDateMinuteKst(info.recordedAt, '') },
      ].filter((item) => item.value);
    },
    /** 수강신청 id 를 알면 그 강좌 출결로, 모르면 학습관리로 (비로그인이면 라우터가 로그인으로 보낸다) */
    attendanceLink() {
      const enrollmentId = this.recordedInfo?.enrollmentId;
      return enrollmentId ? `/enrollment/${enrollmentId}` : '/learning';
    },
    appFirst() {
      return this.phase === 'pending';
    },
    isDone() {
      return ['recorded', 'failed', 'expired', 'qrInvalid', 'error'].includes(this.phase);
    },
  },
  created() {
    // PC 면 출석 세션을 열기 전에 휴대폰으로 안내한다(세션을 열면 다대구 앱 주소로 넘어가려다 멈춘다)
    if (this.token && !isMobileDevice()) {
      this.phase = 'desktop';
      return;
    }
    this.openSession();
  },
  mounted() {
    document.addEventListener('visibilitychange', this.onVisible);
    window.addEventListener('pageshow', this.onVisible);
  },
  beforeUnmount() {
    document.removeEventListener('visibilitychange', this.onVisible);
    window.removeEventListener('pageshow', this.onVisible);
  },
  methods: {
    continueHere() {
      this.openSession();
    },
    async openSession() {
      if (!this.token) {
        this.showError('QR 주소가 올바르지 않습니다. 강사 화면의 QR을 다시 찍어 주세요.');
        return;
      }
      this.opening = true;
      this.phase = 'opening';
      try {
        const data = await openAttendSession(this.token);
        if (!data?.attendId || !data?.deepLink) throw new Error('출석 세션 응답이 올바르지 않습니다.');
        this.attendId = data.attendId;
        this.deepLink = data.deepLink;
        this.phase = 'waiting';
        this.openApp();
      } catch (error) {
        this.handleOpenError(error);
      } finally {
        this.opening = false;
      }
    },
    handleOpenError(error) {
      const code = error?.payload?.code;
      if (code === 'ATTENDANCE_QR_INVALID' || code === 'ATTENDANCE_QR_EXPIRED' || error?.status === 422) {
        this.phase = 'qrInvalid';
      } else if (error?.status === 503) {
        this.phase = 'unavailable';
      } else {
        this.showError(error?.message || '잠시 후 다시 시도해 주세요.');
      }
    },
    showError(message) {
      this.errorText = message;
      this.phase = 'error';
    },
    openApp() {
      if (this.deepLink) window.location.href = this.deepLink;
    },
    // 앱에서 돌아온 순간에만 확인한다. 타이머 폴링은 하지 않는다 (서버가 부를 때마다 다대구에 묻는다)
    onVisible() {
      if (document.visibilityState !== 'visible') return;
      if (!this.attendId || this.isDone) return;
      this.checkResult();
    },
    async checkResult() {
      if (!this.attendId || this.checking || this.isDone) return;
      this.checking = true;
      try {
        const data = await getAttendSession(this.attendId);
        this.applyState(data);
      } catch (error) {
        if (error?.status === 404) this.phase = 'expired';
        else this.$q.notify({ type: 'negative', message: error?.message || '결과를 확인하지 못했습니다.', position: 'top' });
      } finally {
        this.checking = false;
      }
    },
    /**
     * 계약(AttendSession)은 지금 attendId·state·studentName 만 준다. 강좌·회차·기록 시각이
     * 붙어 오면 그대로 쓰고, 기록 시각이 없으면 이 화면이 RECORDED 를 받은 시각을 "확인 시각"으로 둔다
     */
    toRecordedInfo(data) {
      const serverTime = data.recordedAt || data.checkedAt || '';
      return {
        courseTitle: data.courseTitle || '',
        sessionNo: data.sessionNo || null,
        enrollmentId: data.enrollmentId || null,
        recordedAt: serverTime || new Date().toISOString(),
        fromServer: !!serverTime,
      };
    },
    applyState(data) {
      const state = data?.state;
      if (state === 'RECORDED') {
        this.studentName = data.studentName || '';
        this.recordedInfo = this.toRecordedInfo(data);
        this.phase = 'recorded';
      } else if (state === 'FAILED') {
        this.errorCode = data.errorCode || '';
        this.phase = 'failed';
      } else if (state === 'EXPIRED') {
        this.phase = 'expired';
      } else {
        this.phase = 'pending';
      }
    },
  },
};
</script>

<style scoped>
.attend {
  min-height: 100vh;
  padding: 16px 16px 32px;
  background: var(--db-bg-page);
}

.attend__header {
  display: flex;
  justify-content: center;
  padding: 8px 0 24px;
}

.attend__card {
  display: flex;
  flex-direction: column;
  align-items: center;
  max-width: 420px;
  margin: 0 auto;
  padding: 32px 20px 24px;
  border: 1px solid var(--db-border);
  border-radius: 12px;
  background: var(--db-bg-white);
  text-align: center;
}

.attend__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80px;
  height: 80px;
  margin-bottom: 20px;
  border-radius: 50%;
  background: var(--db-bg-active);
  color: var(--db-primary);
}

.attend__icon--success {
  background: color-mix(in srgb, var(--db-status-green) 12%, var(--db-bg-white));
  color: var(--db-status-green);
}

.attend__icon--warn {
  background: color-mix(in srgb, var(--db-status-orange) 10%, var(--db-bg-white));
  color: var(--db-status-orange);
}

.attend__icon--error {
  background: color-mix(in srgb, var(--db-status-pink) 10%, var(--db-bg-white));
  color: var(--db-status-pink);
}

.attend__message {
  width: 100%;
}

.attend__title {
  margin: 0 0 8px;
  /* '…아닙니 / 다'처럼 글자 하나만 꺾이지 않게 */
  word-break: keep-all;
  overflow-wrap: anywhere;
  font-size: 22px;
  line-height: 30px;
  font-weight: 700;
  color: var(--db-text-title);
}

.attend__desc {
  margin: 0;
  font-size: 15px;
  line-height: 22px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.attend__actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  margin-top: 24px;
}

.attend__actions:empty {
  display: none;
}

.attend__btn {
  height: 52px;
  border-radius: 8px;
  font-family: inherit;
  font-size: 17px;
  font-weight: 600;
  cursor: pointer;
}

.attend__btn--primary {
  border: 0;
  background: var(--db-primary);
  color: var(--db-on-color);
}

.attend__btn--line {
  border: 1px solid var(--db-border);
  background: var(--db-bg-white);
  color: var(--db-text-body);
}

.attend__btn--first {
  order: -1;
}

.attend__btn:disabled {
  cursor: default;
  opacity: 0.6;
}

.attend__link {
  color: var(--db-primary);
  font-weight: 600;
}

.attend__link--block {
  margin-top: 12px;
  font-size: 15px;
}

.attend__btn--link {
  display: flex;
  align-items: center;
  justify-content: center;
  text-decoration: none;
}

.attend__steps {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  margin: 20px 0 0;
  padding: 0;
  list-style: none;
  text-align: left;
}

.attend__step {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 15px;
  color: var(--db-text-body);
  background: var(--db-bg-subtle);
  word-break: keep-all;
}

.attend__step-no {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  font-size: 14px;
  font-weight: 700;
  color: var(--db-on-color);
  background: var(--db-text-sub);
}

.attend__step--now {
  font-weight: 600;
  color: var(--db-text-title);
}

.attend__step--now .attend__step-no {
  background: var(--db-primary);
}

.attend__details {
  width: 100%;
  margin: 20px 0 0;
  padding: 12px 16px;
  border-radius: 8px;
  background: var(--db-bg-subtle);
  text-align: left;
}

.attend__detail {
  display: flex;
  gap: 12px;
  padding: 4px 0;
  font-size: 15px;
  line-height: 22px;
}

.attend__detail dt {
  flex-shrink: 0;
  width: 64px;
  color: var(--db-text-sub);
}

.attend__detail dd {
  margin: 0;
  color: var(--db-text-title);
  font-weight: 600;
  word-break: keep-all;
}
</style>
