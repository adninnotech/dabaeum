<template>
  <!-- 수료증 상세 > [진위 확인 QR·링크] — 제출처가 로그인 없이 찍거나 열어 보는 공개 검증 주소 -->
  <q-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)">
    <q-card class="vs-dialog">
      <q-card-section class="vs-dialog__head">
        <div>
          <h2 class="vs-dialog__title">진위 확인 QR·링크</h2>
          <p class="vs-dialog__sub">
            제출처가 QR을 찍거나 링크를 열면 로그인 없이 이 수료증의 발급 상태를 확인합니다.
          </p>
        </div>
        <q-btn flat round dense icon="close" aria-label="닫기" @click="close" />
      </q-card-section>

      <q-card-section class="vs-dialog__body">
        <div class="vs-dialog__frame">
          <img v-if="qrImage" :src="qrImage" :alt="`수료증 ${credentialNo} 진위 확인 QR`" class="vs-dialog__img" />
          <div v-else-if="errorMessage" class="vs-dialog__error" role="alert">{{ errorMessage }}</div>
          <q-spinner v-else size="40px" color="primary" />
        </div>
        <!-- 제출처가 보게 될 확인 화면을 바로 열어 본다 — 새 탭이라 이 창은 그대로.
             긴 주소(…/verify/CERT-…)를 그대로 늘어놓으면 읽히지도 않고 어지러워 글자로 감춘다(주소는 [링크 복사]로) -->
        <a
          :href="verifyUrl"
          target="_blank"
          rel="noopener"
          class="vs-dialog__url"
          aria-label="제출처에 보이는 확인 화면 미리 보기 (새 창)"
        >
          제출처에 보이는 화면 미리 보기<q-icon name="o_open_in_new" size="16px" class="vs-dialog__url-icon" />
        </a>
        <p class="vs-dialog__note">
          확인 화면에는 이름·강좌가 나오지 않고 발급 상태만 나옵니다. 수료증 파일이나 사본과 함께 전달해 주세요.
        </p>
      </q-card-section>

      <q-card-actions class="vs-dialog__actions">
        <q-btn unelevated no-caps color="primary" icon="o_link" label="링크 복사" @click="copyLink" />
        <q-btn
          outline
          no-caps
          color="primary"
          icon="o_download"
          label="QR 이미지 저장"
          :disable="!qrImage"
          @click="saveImage"
        />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import QRCode from 'qrcode';
import { copyToClipboard } from '@/utils/clipboard';

export default {
  name: 'CredentialVerifyShareDialog',
  props: {
    modelValue: {
      type: Boolean,
      default: false,
    },
    credentialNo: {
      type: String,
      required: true,
    },
  },
  emits: ['update:modelValue'],
  data() {
    return {
      qrImage: '',
      errorMessage: '',
    };
  },
  computed: {
    verifyUrl() {
      const { href } = this.$router.resolve({
        name: 'credential-verify-no',
        params: { credentialNo: this.credentialNo },
      });
      return new URL(href, window.location.origin).toString();
    },
  },
  watch: {
    modelValue: {
      immediate: true,
      handler(open) {
        if (open) this.render();
      },
    },
    credentialNo() {
      this.qrImage = '';
      if (this.modelValue) this.render();
    },
  },
  methods: {
    async render() {
      if (this.qrImage) return;
      this.errorMessage = '';
      const url = this.verifyUrl;
      try {
        const image = await QRCode.toDataURL(url, { width: 480, margin: 1 });
        // 그리는 사이 다른 수료증으로 바뀌었으면 버린다
        if (url === this.verifyUrl) this.qrImage = image;
      } catch {
        this.errorMessage = 'QR을 만들지 못했습니다. 링크 복사를 이용해 주세요.';
      }
    },
    copyLink() {
      copyToClipboard(this.verifyUrl, { successMessage: '진위 확인 링크를 복사했습니다.' });
    },
    saveImage() {
      const link = document.createElement('a');
      link.href = this.qrImage;
      link.download = `${this.credentialNo}-verify-qr.png`;
      link.click();
    },
    close() {
      this.$emit('update:modelValue', false);
    },
  },
};
</script>

<style scoped>
.vs-dialog {
  width: 420px;
  max-width: calc(100vw - 32px);
  border-radius: 12px;
}

.vs-dialog__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.vs-dialog__title {
  margin: 0;
  font-size: 18px;
  line-height: 26px;
  font-weight: 700;
  color: var(--db-text-title);
}

.vs-dialog__sub {
  margin: 4px 0 0;
  font-size: 14px;
  line-height: 19px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.vs-dialog__body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding-top: 0;
}

.vs-dialog__frame {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 240px;
  max-width: 100%;
  aspect-ratio: 1;
  padding: 12px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.vs-dialog__img {
  width: 100%;
  height: 100%;
  image-rendering: pixelated;
}

.vs-dialog__error {
  font-size: 14px;
  text-align: center;
  color: var(--db-status-pink);
  word-break: keep-all;
}

.vs-dialog__url {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  max-width: 100%;
  font-size: 15px;
  font-weight: 600;
  color: var(--db-accent);
  text-align: center;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.vs-dialog__url:hover {
  text-decoration-thickness: 2px;
}

.vs-dialog__url:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
  border-radius: 2px;
}

.vs-dialog__url-icon {
  margin-left: 4px;
}

.vs-dialog__note {
  margin: 0;
  padding: 8px 12px;
  font-size: 13px;
  line-height: 18px;
  color: var(--db-text-body);
  background: var(--db-bg-subtle);
  border-radius: 6px;
  word-break: keep-all;
}

.vs-dialog__actions {
  display: flex;
  justify-content: center;
  gap: 8px;
  padding: 0 16px 16px;
}
</style>
