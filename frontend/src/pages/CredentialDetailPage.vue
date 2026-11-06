<template>
  <q-page class="learner-page">
    <div class="learner-container learner-page__body">
      <!-- 학습지갑과 같은 학습자 머리(h1) — 콘솔식 "← 지갑" 버튼·text-h5 제목을 걷어냈다 -->
      <LearnerPageHead
        :title="pageTitle"
        subtitle="받은 수료증의 내용과 진위 확인 이력을 봅니다."
        :back="{ label: '학습지갑', to: '/wallet' }"
      />

      <AppErrorBanner :message="errorMessage">
        <template #action>
          <q-btn flat color="negative" label="다시 시도" @click="loadDetail" />
        </template>
      </AppErrorBanner>

      <div class="relative-position">
        <q-inner-loading :showing="loading" />

        <template v-if="credential">
          <!-- 수료증다운 첫눈 — 예전엔 상태 뱃지 아래 바로 항목 목록뿐이었다. 남에게 화면을 보일 때도 이 카드 -->
          <section class="cred-card" :class="{ 'cred-card--void': credential.status !== 'ISSUED' || expired }">
            <div class="cred-card__top">
              <span class="cred-card__kind">수료증</span>
              <StatusBadge v-bind="credentialBadge(credential)" />
            </div>
            <h2 class="cred-card__course">{{ credential.courseTitle || '-' }}</h2>
            <p v-if="ownerName" class="cred-card__owner">{{ ownerName }}님은 위 과정을 이수했습니다.</p>
            <dl class="cred-card__facts">
              <div v-if="credential.institutionName" class="cred-card__fact">
                <dt>발급 기관</dt>
                <dd>{{ credential.institutionName }}</dd>
              </div>
              <div v-if="credential.issuedAt" class="cred-card__fact">
                <dt>발급일</dt>
                <dd>{{ formatDateKst(credential.issuedAt) }}</dd>
              </div>
              <div class="cred-card__fact cred-card__fact--wide">
                <dt>수료증 번호</dt>
                <dd class="cred-card__no">
                  <span class="cred-card__no-text">{{ credential.credentialNo || '-' }}</span>
                  <!-- 32자리를 옮겨 적기 어려웠다 -->
                  <q-btn
                    v-if="credential.credentialNo"
                    flat
                    dense
                    no-caps
                    size="sm"
                    icon="content_copy"
                    label="복사"
                    color="primary"
                    class="cred-card__copy"
                    @click="copyNo"
                  />
                </dd>
              </div>
            </dl>
          </section>

          <!-- 주 동작은 진위 확인 공유 — 파일 받기와 같은 작은 테두리 버튼이라 흐렸다 -->
          <div v-if="credential.status === 'ISSUED'" class="cred-actions">
            <q-btn
              v-if="credential.credentialNo"
              unelevated
              no-caps
              color="primary"
              icon="o_qr_code_2"
              label="진위 확인 QR·링크"
              class="cred-actions__main"
              @click="showVerifyShare = true"
            />
            <!-- 따로 받던 전자 수료증 파일(.jwt)은 뺐다 — 이미지 안에 들어 있다.
                 사람이 보는 수료증 그림 — 서명된 수료증(JWT)을 파일 안에 숨겨 넣어 그림 하나로 제출해도 검증된다.
                 성명·생년월일은 본인 것만 알 수 있어 주인일 때만 -->
            <q-btn
              v-if="ownerName"
              outline
              no-caps
              color="primary"
              icon="o_image"
              label="수료증 이미지(PNG)"
              :loading="savingImage"
              @click="saveCertificateImage"
            />
          </div>
          <p v-if="credential.status === 'ISSUED' && ownerName" class="cred-actions__note">
            수료증 이미지 파일 안에는 서명된 전자 수료증이 들어 있어, 이미지 한 장으로 제출해도 확인할 수 있습니다.
          </p>

          <!-- 유효기간이 지나면 공개 검증은 '유효기간 지남'인데 여기만 초록 '발급완료'였다 -->
          <div v-if="expired" class="cred-notice" role="status">
            {{ formatDateKst(credential.validUntil) }}에 유효기간이 끝났어요. 진위 확인에서도 '유효기간 지남'으로 나옵니다.
          </div>

          <!-- 대체된 옛 버전 — 예전엔 '학습지갑에서 최신 버전을 확인'이라고만 했다 -->
          <div v-if="credential.status === 'SUPERSEDED'" class="cred-notice" role="status">
            재발급되어 새 수료증으로 대체되었습니다.
            <router-link v-if="latestId" :to="`/wallet/credentials/${latestId}`" class="cred-notice__link">
              최신 버전 보기
            </router-link>
          </div>

          <CredentialVerifyShareDialog
            v-if="credential.credentialNo"
            v-model="showVerifyShare"
            :credential-no="credential.credentialNo"
          />

          <q-list bordered class="rounded-borders q-mt-md">
            <q-item v-for="row in rows" :key="row.label">
              <q-item-section>
                <q-item-label caption>{{ row.label }}</q-item-label>
                <q-item-label class="break-all">{{ row.value }}</q-item-label>
              </q-item-section>
            </q-item>
          </q-list>

          <h2 class="cred-section-title">검증 이력</h2>
          <p v-if="verifications.length === 0" class="cred-empty">검증 이력이 없습니다.</p>
          <template v-else>
            <q-markup-table flat bordered class="cred-table">
              <thead>
                <tr>
                  <th class="text-left">일시</th>
                  <th class="text-left">유형</th>
                  <th class="text-left">요청자</th>
                  <th class="text-left">결과</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in verifications" :key="item.id || `${item.verifiedAt}-${item.result}`">
                  <td>{{ $formatDateTimeKst(item.verifiedAt || item.createdAt) }}</td>
                  <td>{{ VERIFICATION_TYPE_LABEL[item.verificationType] || '-' }}</td>
                  <!-- 요청자 ID(UUID)는 보이지 않는다 — 누가 확인했는지 종류만 -->
                  <td>{{ REQUESTER_TYPE_LABEL[item.requesterType] || '-' }}</td>
                  <td>
                    <q-badge :color="resultColor(item)">
                      {{ VERIFY_RESULT_LABEL[resultCode(item)] || resultCode(item) || '-' }}
                    </q-badge>
                  </td>
                </tr>
              </tbody>
            </q-markup-table>
            <!-- 폰: 표의 '결과' 칸이 화면 밖으로 밀렸다 — 두 줄 카드 -->
            <ul class="cred-history">
              <li v-for="item in verifications" :key="item.id || `${item.verifiedAt}-${item.result}`" class="cred-history__item">
                <span class="cred-history__when">{{ $formatDateTimeKst(item.verifiedAt || item.createdAt) }}</span>
                <span class="cred-history__meta">
                  {{ VERIFICATION_TYPE_LABEL[item.verificationType] || '-' }} ·
                  {{ REQUESTER_TYPE_LABEL[item.requesterType] || '-' }}
                </span>
                <q-badge :color="resultColor(item)" class="cred-history__result">
                  {{ VERIFY_RESULT_LABEL[resultCode(item)] || resultCode(item) || '-' }}
                </q-badge>
              </li>
            </ul>
          </template>
        </template>

        <div v-else-if="!loading" class="text-grey-7">수료증을 찾을 수 없습니다.</div>
      </div>
    </div>
  </q-page>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import LearnerPageHead from '@/components/LearnerPageHead.vue';
import { formatDateKst, formatDatePeriodKst, formatDateTimeKst } from '@/utils/datetime';
import StatusBadge from '@/components/StatusBadge.vue';
import CredentialVerifyShareDialog from '@/components/wallet/CredentialVerifyShareDialog.vue';
import { LEARNER_FAILURE_TEXT } from '@/utils/credential-failure';
import { getCourse } from '@/services/course-api';
import { getCurrentUser } from '@/services/user-api';
import { renderCertificatePng } from '@/utils/certificate-image';
import {
  downloadCredentialDocument,
  getCredential,
  listCredentialVerifications,
  listMyCredentials,
} from '@/services/credential-api';
import { mapState } from 'pinia';
import { useAuthStore } from '@/stores/auth-store';
import { copyToClipboard } from '@/utils/clipboard';
import { credentialBadge, isCredentialExpired } from '@/utils/credential-validity';

/** 증서 일자 — '2026년 10월 13일' */
function formatDateKorean(value) {
  const [year, month, day] = formatDateKst(value, '').split('.');
  return year ? `${year}년 ${Number(month)}월 ${Number(day)}일` : '';
}

const VERIFICATION_TYPE_LABEL = { QR: 'QR 스캔', API: '시스템 조회', ADMIN: '관리자 확인' };
const REQUESTER_TYPE_LABEL = {
  INDIVIDUAL: '개인',
  INSTITUTION: '교육기관',
  EXTERNAL_ORGANIZATION: '외부 기관',
  SYSTEM: '시스템',
};
const VERIFY_RESULT_LABEL = {
  VALID: '유효',
  INVALID: '무효',
  REVOKED: '폐기됨',
  SUPERSEDED: '대체됨',
  EXPIRED: '만료',
  NOT_FOUND: '없음',
  ERROR: '오류',
};

export default {
  components: { AppErrorBanner, CredentialVerifyShareDialog, LearnerPageHead, StatusBadge },
  name: 'CredentialDetailPage',
  data() {
    return {
      credential: null,
      showVerifyShare: false,
      verifications: [],
      loading: false,
      savingImage: false,
      errorMessage: '',
      VERIFICATION_TYPE_LABEL,
      REQUESTER_TYPE_LABEL,
      VERIFY_RESULT_LABEL,
      // 대체된 옛 버전일 때 같은 수료증의 최신 버전 id
      latestId: '',
    };
  },
  computed: {
    ...mapState(useAuthStore, ['user', 'userId']),
    expired() {
      return isCredentialExpired(this.credential);
    },
    /** 카드의 이름은 이 수료증의 주인일 때만 — 관리자가 열면 관리자 이름이 찍히지 않게.
     * 효력 있는(발급완료) 수료증에서만 — 폐기는 이수 확정 취소일 수 있어 '이수했습니다'가 틀린 말이 된다 */
    ownerName() {
      if (this.credential?.status !== 'ISSUED') return '';
      const subject = String(this.credential?.subjectIdentifier || '');
      if (!this.userId || !subject.includes(String(this.userId))) return '';
      return this.user?.name || '';
    },
    /** 불러오기 전·실패 때도 머리는 그대로 두고 제목만 바꾼다 */
    pageTitle() {
      const c = this.credential;
      return (c && (c.courseTitle || c.credentialNo)) || '수료증 상세';
    },
    rows() {
      const c = this.credential;
      if (!c) return [];
      return [
        // 발급자·대상자 식별자(DID/UUID)와 해시는 학습자에게 의미가 없어 뺐다 — 파일에는 들어 있다
        // 번호·강좌·기관은 위 카드에 — 여기는 기간·회차·상태
        ...this.periodRows(c),
        // 학습지갑과 같은 말 — 'n번째 발급' 대신 '최초 발급 / 재발급 n회차'
        { label: '발급 회차', value: Number(c.versionNo) > 1 ? `재발급 ${Number(c.versionNo) - 1}회차` : '최초 발급' },
        ...this.statusRows(c),
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
    credentialBadge,
    formatDateKst,
    copyNo() {
      copyToClipboard(this.credential.credentialNo, { successMessage: '수료증 번호를 복사했습니다.' });
    },
    /** 같은 묶음(credentialGroupId)에서 대체되지 않은 가장 높은 버전 — 내 지갑 목록에서 고른다 */
    async loadLatest(c) {
      this.latestId = '';
      if (c?.status !== 'SUPERSEDED' || !c.credentialGroupId) return;
      try {
        const page = await listMyCredentials({ page: 0, size: 50 });
        const latest = (page.data || [])
          .filter((item) => item.credentialGroupId === c.credentialGroupId && item.status !== 'SUPERSEDED')
          .sort((a, b) => (b.versionNo || 1) - (a.versionNo || 1))[0];
        this.latestId = latest?.id && latest.id !== c.id ? latest.id : '';
      } catch {
        this.latestId = '';
      }
    },
    /** 발급되지 않은(실패) 수료증엔 유효기간·발급일시가 없다 — 요청 일시만 보인다 */
    periodRows(c) {
      if (c.status === 'FAILED' || !c.issuedAt) {
        return [{ label: '요청 일시', value: formatDateTimeKst(c.createdAt) }];
      }
      return [
        {
          label: '유효기간',
          value: c.validUntil ? formatDatePeriodKst(c.validFrom, c.validUntil) : `${formatDateKst(c.validFrom)} ~ 만료 없음`,
        },
        { label: '발급일시', value: formatDateTimeKst(c.issuedAt) },
      ];
    },
    /** 효력이 없는 수료증은 왜·언제 그렇게 됐는지 함께 보인다 */
    statusRows(c) {
      if (c.status === 'REVOKED') {
        return [
          { label: '폐기일시', value: formatDateTimeKst(c.revokedAt || c.updatedAt) },
          { label: '폐기 사유', value: c.revocationReason || '-' },
        ];
      }
      // SUPERSEDED 안내는 위 알림 줄([최신 버전 보기])이 맡는다
      if (c.status === 'FAILED') {
        // 실패 코드(failureCode)는 관리자용 — 학습자에겐 일반 문구만
        return [{ label: '상태 안내', value: LEARNER_FAILURE_TEXT }];
      }
      return [];
    },
    resultCode(item) {
      return String(item.result || item.verificationResult || item.status || '').toUpperCase();
    },
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
        this.loadLatest(this.credential);
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
    /** 교육기간 — 강좌를 못 읽으면(삭제·권한) '-' 로 두고 이미지는 만든다 */
    async coursePeriod(courseId) {
      if (!courseId) return '-';
      try {
        const course = await getCourse(courseId);
        return course?.startDate ? formatDatePeriodKst(course.startDate, course.endDate) : '-';
      } catch {
        return '-';
      }
    },
    async saveCertificateImage() {
      const c = this.credential;
      if (!c?.id) return;
      this.savingImage = true;
      try {
        const [jwt, me, period] = await Promise.all([
          downloadCredentialDocument(c.id),
          getCurrentUser(),
          this.coursePeriod(c.courseId),
        ]);
        const blob = await renderCertificatePng({
          credentialNo: c.credentialNo,
          name: me?.name || this.ownerName,
          birthDate: formatDateKst(me?.birthDate, '-'),
          courseTitle: c.courseTitle || '-',
          period,
          issuedDate: formatDateKorean(c.issuedAt),
          institutionName: c.institutionName || '',
          jwt: String(jwt || '').trim(),
        });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `수료증_${c.credentialNo || c.id}.png`;
        link.click();
        URL.revokeObjectURL(url);
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '수료증 이미지를 만들지 못했습니다.', position: 'top' });
      } finally {
        this.savingImage = false;
      }
    },
  },
};
</script>

<style scoped>
.rounded-borders {
  border-radius: 12px;
}

.break-all {
  word-break: break-all;
}

/* 수료증 카드 */
.cred-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 24px;
  border: 1px solid var(--db-border);
  border-top: 4px solid var(--db-primary);
  border-radius: 12px;
  background: var(--db-bg-white);
}

/* 효력이 없는 수료증(폐기·대체·실패)은 띠를 회색으로 */
.cred-card--void {
  border-top-color: var(--db-text-sub);
}

.cred-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.cred-card__kind {
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--db-text-sub);
}

.cred-card__course {
  margin: 0;
  font-size: 24px;
  line-height: 1.3;
  font-weight: 700;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.cred-card__owner {
  margin: 0;
  font-size: 15px;
  color: var(--db-text-body);
}

.cred-card__facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 20px;
  margin: 8px 0 0;
  padding-top: 14px;
  border-top: 1px solid var(--db-border);
}

.cred-card__fact {
  min-width: 0;
}

.cred-card__fact--wide {
  grid-column: 1 / -1;
}

.cred-card__fact dt {
  font-size: 13px;
  color: var(--db-text-sub);
}

.cred-card__fact dd {
  margin: 2px 0 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--db-text-title);
}

.cred-card__no {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 8px;
}

.cred-card__no-text {
  min-width: 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 14px;
  overflow-wrap: anywhere;
}

/* 동작 */
.cred-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}

.cred-actions :deep(.q-btn) {
  min-height: 44px;
  border-radius: 6px;
}

.cred-actions__note {
  margin: 8px 0 0;
  font-size: 14px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.cred-notice {
  margin-top: 16px;
  padding: 12px 14px;
  font-size: 14px;
  color: var(--db-text-body);
  background: var(--db-bg-subtle);
  border-radius: 8px;
}

.cred-notice__link {
  margin-left: 6px;
  font-weight: 600;
  color: var(--db-accent);
}

.cred-section-title {
  margin: 24px 0 8px;
  font-size: 16px;
  font-weight: 700;
  color: var(--db-text-title);
}

.cred-empty {
  margin: 0;
  font-size: 14px;
  color: var(--db-text-sub);
}

.cred-history {
  display: none;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.cred-history__item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 2px 8px;
  padding: 12px 14px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
  background: var(--db-bg-white);
}

.cred-history__when {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-title);
  font-variant-numeric: tabular-nums;
}

.cred-history__meta {
  grid-column: 1;
  font-size: 14px;
  color: var(--db-text-sub);
}

.cred-history__result {
  grid-column: 2;
  grid-row: 1 / 3;
  align-self: center;
}

@media (max-width: 599px) {
  .cred-card {
    padding: 18px 16px;
  }

  .cred-card__course {
    font-size: 20px;
  }

  .cred-actions__main {
    flex: 1 1 100%;
  }

  .cred-table {
    display: none;
  }

  .cred-history {
    display: flex;
  }
}
</style>
