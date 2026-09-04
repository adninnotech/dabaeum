<template>
  <q-page padding>
    <div class="page-inner">
      <q-btn
        flat
        dense
        color="primary"
        icon="arrow_back"
        label="지갑"
        class="q-mb-md"
        @click="$router.push('/wallet')"
      />

      <q-banner v-if="errorMessage" class="bg-red-1 text-negative q-mb-md rounded-borders">
        {{ errorMessage }}
        <template #action>
          <q-btn flat color="negative" label="다시 시도" @click="loadDetail" />
        </template>
      </q-banner>

      <div class="relative-position">
        <q-inner-loading :showing="loading" />

        <template v-if="credential">
          <div class="text-h5 text-weight-bold">
            {{ credential.courseTitle || credential.credentialNo || '수료증 상세' }}
          </div>
          <div class="row items-center q-gutter-sm q-mt-sm">
            <StatusBadge :status="credential.status" />
            <q-btn
              outline
              dense
              color="primary"
              icon="download"
              label="VC 문서"
              :loading="downloading"
              @click="downloadDocument"
            />
          </div>

          <q-list bordered class="rounded-borders q-mt-md">
            <q-item v-for="row in rows" :key="row.label">
              <q-item-section>
                <q-item-label caption>{{ row.label }}</q-item-label>
                <q-item-label class="break-all">{{ row.value }}</q-item-label>
              </q-item-section>
            </q-item>
          </q-list>

          <div class="text-subtitle1 text-weight-bold q-mt-lg q-mb-sm">검증 이력</div>
          <q-markup-table flat bordered>
            <thead>
              <tr>
                <th class="text-left">일시</th>
                <th class="text-left">유형</th>
                <th class="text-left">요청자</th>
                <th class="text-left">결과</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="verifications.length === 0">
                <td colspan="4" class="text-grey-7">검증 이력이 없습니다.</td>
              </tr>
              <tr v-for="item in verifications" :key="item.id || `${item.verifiedAt}-${item.result}`">
                <td>{{ $formatDateTimeKst(item.verifiedAt || item.createdAt) }}</td>
                <td>{{ item.verificationType || '-' }}</td>
                <td>{{ item.requesterType || '-' }} / {{ item.requesterId || '-' }}</td>
                <td>
                  <q-badge :color="resultColor(item)">
                    {{ item.result || item.verificationResult || item.status || '-' }}
                  </q-badge>
                </td>
              </tr>
            </tbody>
          </q-markup-table>
        </template>

        <div v-else-if="!loading" class="text-grey-7">수료증을 찾을 수 없습니다.</div>
      </div>
    </div>
  </q-page>
</template>

<script>
import { formatDatePeriodKst, formatDateTimeKst } from '@/utils/datetime';
import StatusBadge from '@/components/StatusBadge.vue';
import { downloadCredentialDocument, getCredential, listCredentialVerifications } from '@/services/credential-api';

export default {
  components: { StatusBadge },
  name: 'CredentialDetailPage',
  data() {
    return {
      credential: null,
      verifications: [],
      loading: false,
      downloading: false,
      errorMessage: '',
    };
  },
  computed: {
    rows() {
      const c = this.credential;
      if (!c) return [];
      return [
        { label: 'credentialNo', value: c.credentialNo || '-' },
        { label: 'issuer', value: c.issuerIdentifier || c.issuerId || '-' },
        { label: 'subject', value: c.subjectIdentifier || c.userId || '-' },
        { label: '유효기간', value: formatDatePeriodKst(c.validFrom, c.validUntil) },
        { label: 'vcHash', value: c.vcHash || '-' },
        { label: '발급일시', value: formatDateTimeKst(c.issuedAt || c.createdAt) },
        { label: 'versionNo', value: c.versionNo ?? '-' },
      ];
    },
  },
  watch: {
    '$route.params.credentialId'() {
      this.loadDetail();
    },
  },
  mounted() {
    this.loadDetail();
  },
  methods: {
    resultColor(item) {
      const value = String(item.result || item.verificationResult || item.status || '').toUpperCase();
      if (value === 'VALID' || value === 'OK' || value === 'SUCCESS') return 'positive';
      if (value === 'INVALID' || value === 'FAILED' || value === 'REVOKED') return 'negative';
      return 'grey';
    },
    async loadDetail() {
      const credentialId = this.$route.params.credentialId;
      if (!credentialId) {
        this.credential = null;
        this.verifications = [];
        this.errorMessage = '수료증 ID가 없습니다.';
        return;
      }

      this.loading = true;
      this.errorMessage = '';
      this.credential = null;
      this.verifications = [];

      try {
        this.credential = await getCredential(credentialId);
        try {
          const page = await listCredentialVerifications(credentialId, { page: 0, size: 50 });
          this.verifications = Array.isArray(page.data) ? page.data : [];
        } catch {
          this.verifications = [];
        }
      } catch (error) {
        this.credential = null;
        this.errorMessage = error.message || '수료증 정보를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    async downloadDocument() {
      const credentialId = this.credential?.id || this.$route.params.credentialId;
      if (!credentialId) return;
      this.downloading = true;
      try {
        const body = await downloadCredentialDocument(credentialId);
        const blob = new Blob([body], { type: 'application/vc+jwt' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `${this.credential?.credentialNo || credentialId}.jwt`;
        link.click();
        URL.revokeObjectURL(url);
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '문서를 받지 못했습니다.', position: 'top' });
      } finally {
        this.downloading = false;
      }
    },
  },
};
</script>

<style scoped>
.page-inner {
  max-width: 720px;
  margin: 0 auto;
}
.rounded-borders {
  border-radius: 12px;
}
.break-all {
  word-break: break-all;
}
</style>
