<template>
  <q-page padding>
    <q-banner class="bg-blue-1 text-primary q-mb-md" rounded>
      <template #avatar>
        <q-icon name="hub" color="primary" />
      </template>
      VC Public API 연동: Context / Vocabulary / Issuer / Status. 체인 트랜잭션 KPI는 명세에 없어 조회하지 않습니다.
    </q-banner>

    <q-card flat bordered class="q-mb-md">
      <q-card-section class="row items-center justify-between">
        <div>
          <div class="text-subtitle1 text-weight-bold">VC Public</div>
          <div class="text-caption text-grey-7">인증 없이 조회</div>
        </div>
        <q-btn outline color="primary" icon="refresh" label="점검" :loading="vcLoading" @click="probeVcPublic" />
      </q-card-section>
      <q-separator />
      <q-card-section>
        <q-markup-table flat dense>
          <thead>
            <tr>
              <th class="text-left">API</th>
              <th>상태</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in vcRows" :key="row.name">
              <td class="text-left">{{ row.name }}</td>
              <td><q-badge :color="row.color">{{ row.status }}</q-badge></td>
            </tr>
          </tbody>
        </q-markup-table>
        <div class="row q-col-gutter-sm q-mt-md">
          <div class="col">
            <q-input v-model="statusNo" outlined dense label="credentialNo 공개 상태" />
          </div>
          <div class="col-auto">
            <q-btn color="primary" unelevated :loading="statusLoading" label="조회" :disable="!statusNo.trim()" @click="lookupStatus" />
          </div>
        </div>
        <pre v-if="statusPayload" class="verify-payload q-mt-md">{{ statusPayload }}</pre>
      </q-card-section>
    </q-card>

    <div class="app-page-header row items-center justify-between q-mb-lg">
      <div>
        <div class="app-page-header__title text-h5 text-weight-bold">블록체인 대시보드</div>
        <div class="app-page-header__subtitle text-grey-7">UI 골격 유지 · 연동 대기</div>
      </div>
      <q-btn outline color="primary" icon="refresh" label="수료증 수 새로고침" :loading="loading" @click="loadCredentialCount" />
    </div>

    <div class="row q-col-gutter-md q-mb-md">
      <div v-for="kpi in kpis" :key="kpi.label" class="col-12 col-sm-6 col-md-4 col-lg-2">
        <q-card flat bordered class="kpi-card">
          <q-card-section>
            <div class="text-caption text-grey-7">{{ kpi.label }}</div>
            <div class="text-h6 text-weight-bold q-mt-xs">{{ kpi.value }}</div>
            <div class="text-caption text-grey-6">{{ kpi.note }}</div>
          </q-card-section>
        </q-card>
      </div>
    </div>

    <div class="row q-col-gutter-md q-mb-md">
      <div class="col-12 col-lg-7">
        <q-card flat bordered>
          <q-card-section class="row items-center justify-between">
            <div class="text-subtitle1 text-weight-bold">1) 실시간 트랜잭션 처리 현황</div>
            <q-badge color="grey">준비중</q-badge>
          </q-card-section>
          <q-card-section>
            <div class="chart-box">라인 차트 영역 — API 계약 없음</div>
          </q-card-section>
        </q-card>
      </div>

      <div class="col-12 col-lg-5">
        <q-card flat bordered class="full-height">
          <q-card-section class="text-subtitle1 text-weight-bold">2) 블록 상태 / API 상태</q-card-section>
          <q-separator />
          <q-card-section>
            <div class="row q-col-gutter-md">
              <div class="col-12 col-sm-5">
                <div class="text-caption text-grey-7">Block Height</div>
                <div class="text-h6 text-weight-bold text-grey-6">—</div>
                <div class="text-caption q-mt-sm text-grey-6">체인 API 미계약</div>
              </div>
              <div class="col-12 col-sm-7">
                <q-markup-table flat dense>
                  <thead>
                    <tr>
                      <th class="text-left">API</th>
                      <th>상태</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="row in apiRows" :key="row.name">
                      <td class="text-left">{{ row.name }}</td>
                      <td>
                        <q-badge :color="row.color">{{ row.status }}</q-badge>
                      </td>
                    </tr>
                  </tbody>
                </q-markup-table>
              </div>
            </div>
          </q-card-section>
        </q-card>
      </div>
    </div>

    <div class="row q-col-gutter-md q-mb-md">
      <div class="col-12 col-lg-5">
        <q-card flat bordered>
          <q-card-section class="text-subtitle1 text-weight-bold">최근 트랜잭션 로그</q-card-section>
          <q-card-section class="text-grey-7">트랜잭션 조회 API 없음 — 표시할 데이터가 없습니다.</q-card-section>
        </q-card>
      </div>
      <div class="col-12 col-md-6 col-lg-3">
        <q-card flat bordered class="full-height">
          <q-card-section class="text-subtitle1 text-weight-bold">VC 수료증 (참고)</q-card-section>
          <q-card-section>
            <div class="text-caption text-grey-7">DEFAULT_USER_ID 보유 건수</div>
            <div class="text-h4 text-weight-bold">{{ credentialCount == null ? '—' : credentialCount }}</div>
            <div class="text-caption text-grey-6 q-mt-sm">listUserCredentials 기준</div>
          </q-card-section>
        </q-card>
      </div>
      <div class="col-12 col-md-6 col-lg-4">
        <q-card flat bordered class="full-height">
          <q-card-section class="text-subtitle1 text-weight-bold">NFT 학습배지 발급 통계</q-card-section>
          <q-card-section>
            <div class="chart-box chart-box--sm">도넛 차트 — API 계약 없음</div>
          </q-card-section>
        </q-card>
      </div>
    </div>

    <div class="row q-col-gutter-md">
      <div class="col-12 col-lg-7">
        <q-card flat bordered>
          <q-card-section class="text-subtitle1 text-weight-bold">서비스 KPI 대시보드</q-card-section>
          <q-card-section class="row q-col-gutter-md">
            <div v-for="item in serviceKpis" :key="item.label" class="col-6 col-md-3">
              <div class="mini-kpi">
                <div class="text-caption text-grey-7">{{ item.label }}</div>
                <div class="text-subtitle1 text-weight-bold">{{ item.value }}</div>
              </div>
            </div>
          </q-card-section>
        </q-card>
      </div>
      <div class="col-12 col-lg-5">
        <q-card flat bordered>
          <q-card-section class="text-subtitle1 text-weight-bold">알림 및 이상징후</q-card-section>
          <q-list separator>
            <q-item>
              <q-item-section avatar>
                <q-badge color="grey">안내</q-badge>
              </q-item-section>
              <q-item-section>
                <q-item-label>블록체인 모니터링 API 준비중</q-item-label>
                <q-item-label caption>계약 확정 후 연동 예정</q-item-label>
              </q-item-section>
            </q-item>
          </q-list>
        </q-card>
      </div>
    </div>
  </q-page>
</template>

<script>
import { DEFAULT_INSTITUTION_ID, DEFAULT_USER_ID } from '@/config/api';
import {
  getPublicCredentialStatus,
  getVcContext,
  getVcIssuer,
  getVcVocabulary,
  listUserCredentials,
} from '@/services/credential-api';

export default {
  name: 'BlockchainDashboardPage',
  data() {
    return {
      loading: false,
      vcLoading: false,
      statusLoading: false,
      statusNo: '',
      statusPayload: '',
      credentialCount: null,
      vcRows: [
        { name: 'GET /vc/contexts/lifelong-education/v1', status: '미점검', color: 'grey' },
        { name: 'GET /vc/vocabulary/lifelong-education/v1', status: '미점검', color: 'grey' },
        { name: 'GET /vc/issuers/{institutionId}', status: '미점검', color: 'grey' },
      ],
      apiRows: [
        { name: 'Daegu Chain', status: '준비중', color: 'grey' },
        { name: '외부 검증', status: '준비중', color: 'grey' },
        { name: 'QR', status: '출결 API 사용', color: 'info' },
      ],
    };
  },
  computed: {
    kpis() {
      return [
        { label: '체인 API', value: '미계약', note: '준비중' },
        { label: '오늘 트랜잭션', value: '—', note: 'API 없음' },
        {
          label: 'VC 수료증 (참고)',
          value: this.credentialCount == null ? '—' : String(this.credentialCount),
          note: 'DEFAULT_USER_ID',
        },
        { label: 'NFT 배지 발급', value: '—', note: 'API 없음' },
        { label: '외부 API 호출', value: '—', note: 'API 없음' },
        { label: '실시간 활성 사용자', value: '—', note: 'API 없음' },
      ];
    },
    serviceKpis() {
      return [
        { label: '수강신청 건수', value: '—' },
        { label: '평균 응답시간', value: '—' },
        { label: '실패 알림', value: '—' },
        {
          label: 'VC 보유(참고)',
          value: this.credentialCount == null ? '—' : String(this.credentialCount),
        },
      ];
    },
  },
  mounted() {
    this.loadCredentialCount();
    this.probeVcPublic();
  },
  methods: {
    markVc(index, ok, message) {
      this.vcRows[index].status = ok ? 'OK' : message || '오류';
      this.vcRows[index].color = ok ? 'positive' : 'negative';
    },
    async probeVcPublic() {
      this.vcLoading = true;
      try {
        try {
          await getVcContext();
          this.markVc(0, true);
        } catch (error) {
          this.markVc(0, false, error.message);
        }
        try {
          await getVcVocabulary();
          this.markVc(1, true);
        } catch (error) {
          this.markVc(1, false, error.message);
        }
        try {
          await getVcIssuer(DEFAULT_INSTITUTION_ID);
          this.markVc(2, true);
        } catch (error) {
          this.markVc(2, false, error.message);
        }
      } finally {
        this.vcLoading = false;
      }
    },
    async lookupStatus() {
      const no = this.statusNo.trim();
      if (!no) return;
      this.statusLoading = true;
      this.statusPayload = '';
      try {
        const result = await getPublicCredentialStatus(no);
        this.statusPayload = JSON.stringify(result, null, 2);
      } catch (error) {
        this.statusPayload = error.message || '조회 실패';
      } finally {
        this.statusLoading = false;
      }
    },
    async loadCredentialCount() {
      this.loading = true;
      try {
        const result = await listUserCredentials(DEFAULT_USER_ID, { page: 0, size: 50 });
        const rows = Array.isArray(result.data) ? result.data : [];
        this.credentialCount = result.page?.totalElements ?? rows.length;
      } catch {
        this.credentialCount = null;
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped lang="scss">
.kpi-card,
.mini-kpi,
.chart-box {
  border-radius: 12px;
}

.mini-kpi {
  background: #f5f8ff;
  padding: 12px;
}

.chart-box {
  min-height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #789;
  background: linear-gradient(180deg, #f8fafc, #e8f0ff);
}

.chart-box--sm {
  min-height: 180px;
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
