<template>
  <!-- 피그마 근거 없음 — 로그인 버튼(docs/images/ui_design/로그인 버튼) 을 작게 변형 -->
  <span v-if="linked === true" class="dadaegu-linked">
    <q-icon name="check_circle" size="16px" />
    다대구 연결됨
  </span>
  <span v-else-if="linked === null" class="dadaegu-muted">연결 상태 확인 불가</span>
  <span v-else-if="blockedReason" class="dadaegu-muted">{{ blockedReason }}</span>
  <button
    v-else
    type="button"
    class="dadaegu-link"
    :class="{ 'dadaegu-link--outline': outline }"
    :disabled="busy"
    @click="start"
  >
    <strong>다대구</strong> {{ busy ? '연결 중…' : '연결하기' }}
  </button>
</template>

<script>
import { Notify } from 'quasar';
import { prepareDadaeguSdk, startDadaeguAuth } from '@/services/dadaegu-auth';
import { linkMyDadaeguIdentity } from '@/services/user-api';
import { toDadaeguLoginBody } from '@/utils/didlogin-loader';

// 로그인 화면(dabaeumDadaeguLoginCallback) 과 이름을 나눈다 (dadaegu-auth.js 참고)
const CALLBACK_NAME = 'dabaeumDadaeguLinkCallback';

function linkErrorMessage(error) {
  if (error?.payload?.code === 'IDENTITY_CONFLICT' || error?.status === 409) {
    return '이 다대구 계정은 이미 다른 회원에 연결돼 있거나, 이 계정에 이미 다대구가 연결돼 있습니다.';
  }
  if (error?.status === 401) return '다대구 인증에 실패했습니다. 다시 시도해 주세요.';
  return error?.message || '다대구 연결에 실패했습니다.';
}

export default {
  name: 'DadaeguLinkButton',
  props: {
    // true 연결됨 · false 미연결 · null 조회 실패
    linked: { type: Boolean, default: null },
    // 값이 있으면 버튼 대신 이 문구를 보여 준다 (예: 관리자 대리 로그인)
    blockedReason: { type: String, default: '' },
    /* 흰 바탕·빨간 글씨·빨간 테두리(브랜드 보조형) — 다른 정보 줄 사이에서 빨간 바탕이 화면 제목보다 먼저 눈에 걸리는 자리용(마이페이지).
     * 로그인 화면처럼 다대구가 주요 입구인 곳은 기본(빨간 바탕) */
    outline: { type: Boolean, default: false },
  },
  emits: ['linked'],
  data() {
    return { busy: false };
  },
  mounted() {
    // 폰에서 앱 인증 뒤 이 페이지로 돌아오면 SDK 가 init 때 결과를 조회해 콜백을 부른다
    window[CALLBACK_NAME] = (returnData) => this.finish(returnData);
    if (!this.blockedReason) prepareDadaeguSdk().catch(() => {});
  },
  beforeUnmount() {
    if (window[CALLBACK_NAME]) delete window[CALLBACK_NAME];
  },
  methods: {
    async start() {
      this.busy = true;
      try {
        await startDadaeguAuth(CALLBACK_NAME);
      } catch (error) {
        Notify.create({ type: 'negative', message: error.message || '다대구 연결을 시작하지 못했습니다.' });
      } finally {
        this.busy = false;
      }
    },
    // SDK 콜백. throw 하면 SDK 가 "콜백 함수에서 오류" alert 를 띄우므로 안에서 끝낸다
    async finish(returnData) {
      const body = toDadaeguLoginBody(returnData);
      if (!body) {
        Notify.create({ type: 'negative', message: '다대구 인증 결과를 읽지 못했습니다. 다시 시도해 주세요.' });
        return;
      }
      this.busy = true;
      try {
        await linkMyDadaeguIdentity(body);
        Notify.create({
          type: 'positive',
          message: '다대구를 연결했습니다. 이름·휴대폰번호·생년월일이 다대구 본인인증 정보로 바뀌었습니다.',
        });
        this.$emit('linked');
      } catch (error) {
        Notify.create({ type: 'negative', message: linkErrorMessage(error) });
      } finally {
        this.busy = false;
      }
    },
  },
};
</script>

<style scoped>
/* 로그인 버튼과 같은 var(--db-dadaegu) 바탕·흰 글씨·"다대구"만 굵게 */
.dadaegu-link {
  flex-shrink: 0;
  height: 32px;
  padding: 0 14px;
  border: 0;
  border-radius: 6px;
  background: var(--db-dadaegu);
  font-family: inherit;
  font-size: 14px;
  font-weight: 400;
  color: var(--db-on-color);
  cursor: pointer;
}

.dadaegu-link strong {
  font-weight: 800;
}

.dadaegu-link--outline {
  border: 1px solid var(--db-dadaegu);
  background: var(--db-bg-white);
  color: var(--db-dadaegu);
}

.dadaegu-link--outline:hover {
  background: color-mix(in srgb, var(--db-dadaegu) 6%, var(--db-bg-white));
}

.dadaegu-link:focus-visible {
  outline: 2px solid var(--db-dadaegu);
  outline-offset: 2px;
}

.dadaegu-link:disabled {
  cursor: default;
  opacity: 0.7;
}

.dadaegu-linked {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-status-green);
}

.dadaegu-muted {
  font-size: 14px;
  color: var(--db-text-sub);
}
</style>
