<template>
  <q-page padding>
    <div class="page-inner">
      <div class="app-page-header row items-center justify-between q-mb-lg">
        <div>
          <div class="app-page-header__title text-h5 text-weight-bold">학습지갑</div>
          <div class="app-page-header__subtitle text-grey-7">Credential · Badge 보관</div>
        </div>
        <q-btn outline color="primary" icon="refresh" label="새로고침" :loading="loading" @click="loadWallet" />
      </div>

      <q-banner v-if="!isLoggedIn" class="bg-blue-1 q-mb-md rounded-borders">
        로그인 후 학습지갑을 확인할 수 있습니다.
        <template #action>
          <q-btn flat color="primary" label="로그인" @click="goLogin" />
        </template>
      </q-banner>

      <q-banner v-if="errorMessage" class="bg-red-1 text-negative q-mb-md rounded-borders">
        {{ errorMessage }}
        <template #action>
          <q-btn flat color="negative" label="다시 시도" @click="loadWallet" />
        </template>
      </q-banner>

      <div class="relative-position">
        <q-inner-loading :showing="loading" />

        <q-tabs
          v-model="tab"
          dense
          class="text-primary"
          active-color="primary"
          indicator-color="primary"
          align="left"
        >
          <q-tab name="credentials" label="수료증(VC)" />
          <q-tab name="badges" label="배지" />
          <q-tab name="verify" label="검증" />
        </q-tabs>
        <q-separator />

        <q-tab-panels v-model="tab" animated>
          <q-tab-panel name="credentials" class="q-px-none">
            <div v-if="!loading && credentials.length === 0" class="text-grey-7 q-pa-md">
              발급된 수료증이 없습니다.
            </div>
            <div v-for="item in credentials" :key="item.id" class="q-mb-md">
              <q-card flat bordered>
                <q-card-section class="row items-center justify-between">
                  <div>
                    <div class="text-subtitle1 text-weight-bold">
                      {{ item.courseTitle || item.credentialNo || item.id }}
                    </div>
                    <div class="text-caption text-grey-7">
                      {{ item.credentialNo }} · v{{ item.versionNo || '-' }}
                    </div>
                  </div>
                  <StatusBadge :status="item.status" />
                </q-card-section>
                <q-separator />
                <q-card-section class="text-body2">
                  <div>유효기간: {{ $formatDateKst(item.validFrom) }} ~ {{ $formatDateKst(item.validUntil) }}</div>
                  <div class="ellipsis">vcHash: {{ item.vcHash || '-' }}</div>
                </q-card-section>
                <q-card-actions align="right">
                  <q-btn
                    flat
                    color="primary"
                    label="상세"
                    @click="$router.push(`/wallet/credentials/${item.id}`)"
                  />
                </q-card-actions>
              </q-card>
            </div>
          </q-tab-panel>

          <q-tab-panel name="badges" class="q-px-none">
            <div v-if="!loading && badges.length === 0" class="text-grey-7 q-pa-md">
              발급된 배지가 없습니다.
            </div>
            <div class="row q-col-gutter-md">
              <div v-for="badge in badges" :key="badge.id" class="col-12 col-sm-6">
                <q-card flat bordered class="text-center">
                  <q-card-section>
                    <q-avatar size="64px" color="primary" text-color="white" icon="military_tech" />
                    <div class="text-subtitle1 text-weight-bold q-mt-md">
                      {{ badge.badgeName || badge.name || '배지' }}
                    </div>
                    <q-badge class="q-mt-sm" :color="statusColor[badge.status] || 'grey'">
                      {{ badge.status || '-' }}
                    </q-badge>
                    <div class="text-caption text-grey-7 q-mt-sm">
                      {{ badge.badgeType || '-' }} · {{ badge.nftTokenId || '-' }}
                    </div>
                  </q-card-section>
                </q-card>
              </div>
            </div>
          </q-tab-panel>

          <q-tab-panel name="verify" class="q-px-none">
            <q-card flat bordered>
              <q-card-section>
                <div class="text-subtitle1 text-weight-bold">수료증 검증</div>
                <div class="text-caption text-grey-7">POST /credentials/verify · GET /vc/status/{credentialNo}</div>
                <q-input v-model="verifyNo" outlined dense class="q-mt-md" label="credentialNo" />
                <q-btn
                  class="q-mt-md"
                  color="primary"
                  unelevated
                  label="검증하기"
                  :loading="verifying"
                  :disable="!verifyNo.trim()"
                  @click="onVerify"
                />
                <q-btn
                  class="q-mt-md q-ml-sm"
                  outline
                  color="primary"
                  label="공개 상태 조회"
                  :loading="checkingPublic"
                  :disable="!verifyNo.trim()"
                  @click="onPublicStatus"
                />
                <div v-if="verifyResultLabel" class="q-mt-md">
                  결과:
                  <q-badge :color="verifyResultOk ? 'positive' : 'negative'">
                    {{ verifyResultLabel }}
                  </q-badge>
                </div>
                <pre v-if="verifyPayload" class="verify-payload q-mt-md">{{ verifyPayload }}</pre>
              </q-card-section>
            </q-card>
          </q-tab-panel>
        </q-tab-panels>
      </div>
    </div>
  </q-page>
</template>

<script>
import StatusBadge from '@/components/StatusBadge.vue';
import { mapState } from 'pinia';
import { DEFAULT_INSTITUTION_ID } from '@/config/api';
import { listMyCredentials, listUserBadges, listUserCredentials, getPublicCredentialStatus, verifyCredential } from '@/services/credential-api';
import { useAuthStore } from '@/stores/auth-store';

export default {
  components: { StatusBadge },
  name: 'WalletPage',
  data() {
    return {
      tab: 'credentials',
      credentials: [],
      badges: [],
      loading: false,
      verifying: false,
      checkingPublic: false,
      errorMessage: '',
      verifyNo: '',
      verifyResultLabel: '',
      verifyResultOk: false,
      verifyPayload: '',
    };
  },
  computed: {
    ...mapState(useAuthStore, ['isLoggedIn', 'userId']),
  },
  mounted() {
    this.loadWallet();
  },
  methods: {
    goLogin() {
      this.$router.push({ path: '/login', query: { redirect: '/wallet' } });
    },
    async loadWallet() {
      if (!this.isLoggedIn) {
        this.credentials = [];
        this.badges = [];
        return;
      }

      this.loading = true;
      this.errorMessage = '';
      try {
        let credPage;
        try {
          credPage = await listMyCredentials({ page: 0, size: 50, sort: 'createdAt,desc' });
        } catch {
          credPage = await listUserCredentials(this.userId, { page: 0, size: 50, sort: 'createdAt,desc' });
        }
        const badgePage = await listUserBadges(this.userId, { page: 0, size: 50, sort: 'createdAt,desc' }).catch(() => ({ data: [] }));
        this.credentials = Array.isArray(credPage.data) ? credPage.data : [];
        this.badges = Array.isArray(badgePage.data) ? badgePage.data : [];
      } catch (error) {
        this.credentials = [];
        this.badges = [];
        this.errorMessage = error.message || '학습지갑을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    async onVerify() {
      const credentialNo = this.verifyNo.trim();
      if (!credentialNo) return;

      this.verifying = true;
      this.verifyResultLabel = '';
      this.verifyResultOk = false;
      this.verifyPayload = '';
      try {
        const result = await verifyCredential({
          credentialNo,
          verificationType: 'API',
          requesterType: 'INSTITUTION',
          requesterId: DEFAULT_INSTITUTION_ID,
        });
        const status =
          result?.result ||
          result?.verificationResult ||
          result?.status ||
          (result?.valid === true ? 'VALID' : result?.valid === false ? 'INVALID' : 'OK');
        this.verifyResultLabel = String(status);
        this.verifyResultOk =
          String(status).toUpperCase() === 'VALID' ||
          String(status).toUpperCase() === 'OK' ||
          result?.valid === true;
        this.verifyPayload = JSON.stringify(result, null, 2);
      } catch (error) {
        this.verifyResultLabel = 'INVALID';
        this.verifyResultOk = false;
        this.verifyPayload = error.message || '검증 실패';
        this.$q.notify({
          type: 'negative',
          message: error.message || '검증에 실패했습니다.',
          position: 'top',
        });
      } finally {
        this.verifying = false;
      }
    },
    async onPublicStatus() {
      const credentialNo = this.verifyNo.trim();
      if (!credentialNo) return;
      this.checkingPublic = true;
      this.verifyResultLabel = '';
      this.verifyResultOk = false;
      this.verifyPayload = '';
      try {
        const result = await getPublicCredentialStatus(credentialNo);
        const status = result?.status || result?.credentialStatus || 'OK';
        this.verifyResultLabel = String(status);
        this.verifyResultOk = String(status).toUpperCase() !== 'REVOKED' && String(status).toUpperCase() !== 'INVALID';
        this.verifyPayload = JSON.stringify(result, null, 2);
      } catch (error) {
        this.verifyResultLabel = 'ERROR';
        this.verifyResultOk = false;
        this.verifyPayload = error.message || '공개 상태 조회 실패';
        this.$q.notify({ type: 'negative', message: error.message || '공개 상태 조회에 실패했습니다.', position: 'top' });
      } finally {
        this.checkingPublic = false;
      }
    },
  },
};
</script>

<style scoped>
.page-inner {
  max-width: 900px;
  margin: 0 auto;
}
.rounded-borders {
  border-radius: 12px;
}
.verify-payload {
  margin: 0;
  padding: 12px;
  background: #f5f7fb;
  border-radius: 8px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
