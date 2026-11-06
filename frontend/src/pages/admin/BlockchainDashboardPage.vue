<template>
  <!-- 블록체인 모니터링(계획서 plan.admin.blockchain.dashboard) — 계약에 있는 값만 보인다.
       계획서의 전일 대비·시간대 그래프·API 응답속도는 API 가 없어 두지 않는다(지어낸 숫자 금지) -->
  <q-page class="chain">
    <AppPageHeader
      title="블록체인 모니터링"
      subtitle="수료증 기록이 원장에 잘 쌓이는지, 공개 검증이 동작하는지 확인합니다."
      show-refresh
      :loading="loading"
      @refresh="loadChain"
    />

    <AppErrorBanner :message="chainError">
      <template #action>
        <q-btn flat dense no-caps color="negative" label="다시 시도" @click="loadChain" />
      </template>
    </AppErrorBanner>

    <!-- ① 요약 -->
    <section class="chain__kpis" aria-label="원장 요약">
      <article v-for="kpi in kpis" :key="kpi.key" class="kpi" :class="kpi.tone && `kpi--${kpi.tone}`">
        <p class="kpi__label">{{ kpi.label }}</p>
        <p class="kpi__value">{{ kpi.value }}</p>
        <p class="kpi__note">{{ kpi.note }}</p>
      </article>
    </section>
    <p v-if="loadedAt" class="chain__time">{{ loadedAt }} 기준</p>

    <div class="chain__grid">
      <!-- ② 최근 트랜잭션 -->
      <section class="panel" aria-labelledby="chain-tx-title">
        <header class="panel__head">
          <h2 id="chain-tx-title" class="panel__title">최근 트랜잭션</h2>
          <span class="panel__count">총 {{ formatCount(txTotal) }}건</span>
        </header>
        <!-- 폰: 표가 옆으로 밀려 결과·ID 가 화면 밖이었다 — 한 건씩 두 줄 -->
        <ul v-if="$q.screen.xs" class="tx-list">
          <li v-if="!loading && !transactions.length" class="tx-list__empty">아직 기록된 트랜잭션이 없습니다.</li>
          <li v-for="tx in transactions" :key="tx.id" class="tx-list__item">
            <div class="tx-list__head">
              <span class="tx-list__type">{{ TX_TYPE_LABEL[tx.type] || tx.type }}</span>
              <span class="tx-status" :class="`tx-status--${txTone(tx.status)}`">
                {{ TX_STATUS_LABEL[tx.status] || tx.status }}
              </span>
              <RouterLink
                v-if="tx.status === 'FAILED' && TX_TYPE_LABEL[tx.type]"
                to="/admin/credentials"
                class="tx-link"
              >
                수료증 관리
              </RouterLink>
            </div>
            <div class="tx-list__meta">
              {{ formatDateTimeKst(tx.requestedAt || tx.occurredAt) }} · 블록 {{ tx.blockNumber ?? '미확인' }}
              · <span class="tx-table__hash" :title="tx.txHash || ''">{{ shortHash(tx.txHash) }}</span>
            </div>
          </li>
        </ul>
        <div v-else class="panel__scroll">
          <table class="tx-table">
            <thead>
              <tr>
                <th scope="col">요청 시각</th>
                <th scope="col">작업</th>
                <th scope="col" class="tx-table__num">블록</th>
                <th scope="col">결과</th>
                <th scope="col">트랜잭션 ID</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!loading && !transactions.length">
                <td colspan="5" class="tx-table__empty">아직 기록된 트랜잭션이 없습니다.</td>
              </tr>
              <tr v-for="tx in transactions" :key="tx.id">
                <!-- 최근 목록이라 연도는 title 로만 — 칸이 넓어 ID 가 두 줄로 꺾이고 표가 옆으로 넘어갔다 -->
                <td class="tx-table__time" :title="formatDateTimeKst(tx.requestedAt || tx.occurredAt)">
                  {{ formatDateTimeKst(tx.requestedAt || tx.occurredAt).slice(5) }}
                </td>
                <td class="tx-table__type">{{ TX_TYPE_LABEL[tx.type] || tx.type }}</td>
                <td class="tx-table__num">{{ tx.blockNumber ?? '미확인' }}</td>
                <td class="tx-table__status">
                  <span class="tx-status" :class="`tx-status--${txTone(tx.status)}`">
                    {{ TX_STATUS_LABEL[tx.status] || tx.status }}
                  </span>
                  <span v-if="tx.reconciled" class="tx-reconciled" title="제출 결과 대신 원장 상태를 대조해 확정한 건">
                    대조 확정
                  </span>
                  <!-- 트랜잭션 응답(BlockchainTransactionSummary)에는 수료증·학습자 식별자가 없어 건별로 잇지 못한다 —
                       실패한 수료증 작업이면 발급 실패 건을 다시 발급하는 수료증 관리로 보낸다 -->
                  <RouterLink
                    v-if="tx.status === 'FAILED' && TX_TYPE_LABEL[tx.type]"
                    to="/admin/credentials"
                    class="tx-link"
                  >
                    수료증 관리
                  </RouterLink>
                </td>
                <td class="tx-table__hash" :title="tx.txHash || ''">{{ shortHash(tx.txHash) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="txPageCount > 1" class="panel__foot">
          <q-pagination
            :model-value="txPage"
            :max="txPageCount"
            :max-pages="5"
            direction-links
            color="primary"
            active-design="unelevated"
            @update:model-value="loadTransactions"
          />
        </div>
      </section>

      <div class="chain__side">
      <!-- ③ 이상 징후 -->
      <section class="panel" aria-labelledby="chain-alert-title">
        <header class="panel__head">
          <h2 id="chain-alert-title" class="panel__title">이상 징후</h2>
          <span class="panel__count">{{ formatCount(alertTotal) }}건</span>
        </header>
        <p class="panel__desc">
          원장 기록에 실패한 트랜잭션입니다. 반복되면 원장 연결을 확인해 주세요.
          <!-- 이상 징후 응답에도 대상 식별자가 없다 — 후속 조치 화면으로만 잇는다 -->
          발급에 실패한 수료증은 <RouterLink to="/admin/credentials" class="tx-link">수료증 관리</RouterLink>에서 다시 발급합니다.
        </p>
        <ul v-if="alerts.length" class="alert-list">
          <li v-for="alert in alerts" :key="alert.id" class="alert-item">
            <span class="alert-item__sev" :class="`alert-item__sev--${severityTone(alert.severity)}`">
              {{ severityLabel(alert.severity) }}
            </span>
            <div class="alert-item__body">
              <p class="alert-item__msg" :title="alert.message">{{ alertMessage(alert.message) }}</p>
              <p class="alert-item__time">{{ formatDateTimeKst(alert.occurredAt) }}</p>
            </div>
          </li>
        </ul>
        <p v-else-if="!loading" class="panel__empty">
          <q-icon name="check_circle" size="18px" aria-hidden="true" />
          이상 징후가 없습니다.
        </p>
      </section>

      <BadgeIssueDonut />
      </div>
    </div>

    <!-- ④ 공개 검증 서비스 -->
    <section class="panel" aria-labelledby="chain-vc-title">
      <header class="panel__head">
        <h2 id="chain-vc-title" class="panel__title">공개 검증 서비스</h2>
      </header>
      <p class="panel__desc">외부 기관이 로그인 없이 수료증을 확인할 때 쓰는 기능이 응답하는지 점검합니다.</p>
      <div class="vc-grid">
        <div>
          <div class="vc-controls">
            <q-select
              v-model="issuerInstitutionId"
              outlined
              dense
              emit-value
              map-options
              :options="institutionOptions"
              label="점검할 발급 기관"
              class="vc-controls__select"
            />
            <q-btn outline no-caps color="primary" label="점검" :loading="vcLoading" @click="probeVcPublic" />
          </div>
          <ul class="vc-list">
            <li v-for="row in vcRows" :key="row.key" class="vc-list__row">
              <span>{{ row.name }}</span>
              <span class="vc-state" :class="`vc-state--${row.state}`">{{ VC_STATE_LABEL[row.state] }}</span>
            </li>
          </ul>
        </div>
        <div>
          <!-- [점검]과 같은 테두리 버튼 — 입력칸 안 글자 버튼이라 모양이 달랐다 -->
          <div class="vc-controls">
            <q-input
              v-model="statusNo"
              outlined
              dense
              label="수료증 번호로 공개 상태 조회"
              class="vc-controls__select"
              @keydown="onLookupKeydown"
            />
            <q-btn
              outline
              no-caps
              color="primary"
              label="조회"
              :loading="statusLoading"
              :disable="!statusNo.trim()"
              @click="lookupStatus"
            />
          </div>
          <p v-if="statusResult" class="vc-result" role="status">{{ statusResult }}</p>
        </div>
      </div>
    </section>
  </q-page>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import AppPageHeader from '@/components/AppPageHeader.vue';
import BadgeIssueDonut from '@/components/console/BadgeIssueDonut.vue';
import { getBlockchainMetrics, listBlockchainAlerts, listBlockchainTransactions } from '@/services/blockchain-api';
import { getPublicCredentialStatus, getVcContext, getVcIssuer, getVcVocabulary } from '@/services/credential-api';
import { listInstitutions } from '@/services/institution-api';
import { formatDateTimeKst } from '@/utils/datetime';
import { STATUS_LABEL } from '@/utils/status';
import { toInstitutionOptions } from '@/utils/institution-option';

const TX_PAGE_SIZE = 10;
const ALERT_PAGE_SIZE = 20;
const TX_STATUS_LABEL = { PENDING: '대기', PROCESSING: '처리 중', CONFIRMED: '확정', FAILED: '실패' };
// 백엔드 BlockchainTransactionType — 모르는 값은 그대로 보인다
const TX_TYPE_LABEL = { VC_ANCHOR: '수료증 기록', VC_REVOKE: '수료증 폐기', VC_REISSUE: '수료증 재발급' };
// 이상 징후 message 는 "오류코드: 원문" — 아는 코드는 운영자 문장으로, 모르면 원문 그대로
const ALERT_CODE_LABEL = {
  CREDENTIAL_SIGNING_KEY_INACTIVE: '수료증 서명 키가 비활성 상태라 서명하지 못했습니다.',
  BLOCKCHAIN_SUBMIT_FAILED: '원장에 제출하지 못했습니다.',
  BLOCKCHAIN_CONNECTION_FAILED: '원장에 연결하지 못했습니다.',
  BLOCKCHAIN_COMMIT_TIMEOUT: '원장 확정 응답이 제한 시간 안에 오지 않았습니다.',
  BLOCKCHAIN_COMMIT_INVALID: '원장이 기록을 확정하지 않았습니다.',
  BLOCKCHAIN_RESPONSE_INVALID: '원장 응답을 해석하지 못했습니다.',
  BLOCKCHAIN_LEDGER_CONFLICT: '원장 기록이 서로 맞지 않습니다.',
  BLOCKCHAIN_ALREADY_EXISTS: '이미 원장에 있는 기록입니다.',
  BLOCKCHAIN_NOT_FOUND: '원장에서 기록을 찾지 못했습니다.',
  BLOCKCHAIN_READ_FAILED: '원장 기록을 읽지 못했습니다.',
  BLOCKCHAIN_WRITE_NOT_APPROVED: '원장 쓰기가 승인되지 않았습니다.',
  BLOCKCHAIN_COMPENSATION_PENDING: '되돌림 처리를 기다리는 중입니다.',
  BLOCKCHAIN_UNSUPPORTED_REFERENCE: '원장이 지원하지 않는 기록입니다.',
};
const VC_STATE_LABEL = { idle: '미점검', ok: '정상', fail: '응답 없음' };
const numberFormat = new Intl.NumberFormat('ko-KR');

function initialVcRows() {
  return [
    { key: 'context', name: '수료증 문맥 정보', state: 'idle' },
    { key: 'vocabulary', name: '수료증 용어 정의', state: 'idle' },
    { key: 'issuer', name: '발급 기관 정보', state: 'idle' },
  ];
}

export default {
  name: 'BlockchainDashboardPage',
  components: { AppErrorBanner, AppPageHeader, BadgeIssueDonut },
  data() {
    return {
      TX_STATUS_LABEL,
      TX_TYPE_LABEL,
      VC_STATE_LABEL,
      loading: false,
      loadedAt: '',
      chainError: '',
      metrics: null,
      transactions: [],
      txPage: 1,
      txTotal: 0,
      alerts: [],
      alertTotal: 0,
      vcRows: initialVcRows(),
      vcLoading: false,
      institutionOptions: [],
      issuerInstitutionId: null,
      statusNo: '',
      statusLoading: false,
      statusResult: '',
    };
  },
  computed: {
    kpis() {
      const m = this.metrics;
      const count = (value) => (value == null ? '—' : numberFormat.format(value));
      return [
        {
          key: 'height',
          label: '블록 높이',
          value: count(m?.blockHeight),
          note: m && m.blockHeight == null ? '원장이 높이 조회를 지원하지 않음' : '원장에 쌓인 블록',
        },
        { key: 'total', label: '전체 트랜잭션', value: count(m?.transactionTotal), note: '누적 요청' },
        {
          key: 'rate',
          label: '성공률',
          value: m?.successRate != null ? `${m.successRate.toFixed(1)}%` : '—',
          note: m ? `확정 ${count(m.transactionConfirmed)}건` : '확정 / 전체',
        },
        {
          key: 'failed',
          label: '실패',
          value: count(m?.transactionFailed),
          note: m?.transactionFailed ? '이상 징후에서 확인' : '실패 없음',
          tone: m?.transactionFailed ? 'bad' : '',
        },
        { key: 'credentials', label: '수료증 기록', value: count(m?.credentialIssuedCount), note: '원장에 기록된 발급' },
      ];
    },
    txPageCount() {
      return Math.max(1, Math.ceil(this.txTotal / TX_PAGE_SIZE));
    },
  },
  created() {
    this.loadChain();
    this.loadInstitutions();
  },
  methods: {
    formatDateTimeKst,
    formatCount(value) {
      return numberFormat.format(value || 0);
    },
    shortHash(hash) {
      if (!hash) return '없음';
      return hash.length > 12 ? `${hash.slice(0, 6)}…${hash.slice(-4)}` : hash;
    },
    txTone(status) {
      if (status === 'CONFIRMED') return 'ok';
      if (status === 'FAILED') return 'bad';
      return 'wait';
    },
    severityTone(severity) {
      const s = String(severity || '').toUpperCase();
      if (s.includes('ERROR') || s.includes('CRIT') || s.includes('HIGH')) return 'bad';
      if (s.includes('WARN')) return 'warn';
      return 'info';
    },
    severityLabel(severity) {
      return { bad: '실패', warn: '경고', info: '참고' }[this.severityTone(severity)];
    },
    alertMessage(message) {
      const code = String(message || '').split(':')[0].trim();
      return ALERT_CODE_LABEL[code] || message;
    },
    async loadChain() {
      this.loading = true;
      this.chainError = '';
      const [metrics, alerts] = await Promise.allSettled([
        getBlockchainMetrics(),
        listBlockchainAlerts({ page: 0, size: ALERT_PAGE_SIZE }),
        this.loadTransactions(this.txPage),
      ]);
      if (metrics.status === 'fulfilled') {
        this.metrics = metrics.value;
      } else {
        this.metrics = null;
        this.chainError = metrics.reason?.message || '원장 지표를 불러오지 못했습니다.';
      }
      if (alerts.status === 'fulfilled') {
        this.alerts = Array.isArray(alerts.value?.data) ? alerts.value.data : [];
        this.alertTotal = alerts.value?.page?.totalElements ?? this.alerts.length;
      } else {
        this.alerts = [];
        this.alertTotal = 0;
        this.chainError ||= alerts.reason?.message || '이상 징후를 불러오지 못했습니다.';
      }
      this.loadedAt = formatDateTimeKst(new Date().toISOString());
      this.loading = false;
    },
    async loadTransactions(page) {
      try {
        const result = await listBlockchainTransactions({ page: page - 1, size: TX_PAGE_SIZE });
        this.transactions = Array.isArray(result?.data) ? result.data : [];
        this.txTotal = result?.page?.totalElements ?? this.transactions.length;
        this.txPage = page;
      } catch (error) {
        this.transactions = [];
        this.chainError ||= error?.message || '트랜잭션을 불러오지 못했습니다.';
      }
    },
    async loadInstitutions() {
      try {
        const result = await listInstitutions({ page: 0, size: 100, status: 'ACTIVE' });
        this.institutionOptions = toInstitutionOptions(result?.data);
        this.issuerInstitutionId = this.institutionOptions[0]?.value || null;
      } catch (error) {
        this.institutionOptions = [];
        this.chainError ||= error?.message || '기관 목록을 불러오지 못했습니다.';
      }
    },
    async probeVcPublic() {
      this.vcLoading = true;
      const issuerCheck = this.issuerInstitutionId
        ? getVcIssuer(this.issuerInstitutionId)
        : Promise.reject(new Error('기관 없음'));
      const results = await Promise.allSettled([getVcContext(), getVcVocabulary(), issuerCheck]);
      this.vcRows = this.vcRows.map((row, index) => ({
        ...row,
        state: results[index].status === 'fulfilled' ? 'ok' : 'fail',
      }));
      this.vcLoading = false;
    },
    onLookupKeydown(event) {
      if (event.key === 'Enter' && !event.isComposing && this.statusNo.trim()) this.lookupStatus();
    },
    async lookupStatus() {
      const no = this.statusNo.trim();
      if (!no) return;
      this.statusLoading = true;
      this.statusResult = '';
      try {
        const result = await getPublicCredentialStatus(no);
        const code = String(result?.status || result?.credentialStatus || '').toUpperCase();
        this.statusResult = `${no} · ${STATUS_LABEL[code] || code || '상태 정보 없음'}`;
      } catch (error) {
        this.statusResult =
          error?.status === 404 ? '해당 번호의 수료증이 없습니다.' : error?.message || '조회하지 못했습니다.';
      } finally {
        this.statusLoading = false;
      }
    },
  },
};
</script>

<style scoped>
/* Hallmark · pre-emit critique: P4 H4 E4 S5 R5 V4
 * system-managed(--db-* 토큰, 콘솔 목록·카드 패턴) · 계획서 배치를 실제 계약 데이터로만 채움 */
.chain {
  display: flex;
  flex-direction: column;
  gap: 16px;
  /* 다른 관리자 화면과 같은 여백 — 위가 좁고 폰 좌우가 8px 이었다 */
  padding: 48px 40px 32px;
}

.chain__time {
  margin: -8px 0 0;
  font-size: 12px;
  color: var(--db-text-sub);
  text-align: right;
  font-variant-numeric: tabular-nums;
}

/* 요약 */
.chain__kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(170px, 100%), 1fr));
  gap: 12px;
}

.kpi {
  padding: 16px 18px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.kpi p {
  margin: 0;
}

.kpi__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--db-text-sub);
}

.kpi .kpi__value {
  margin-top: 6px;
  font-size: 28px;
  line-height: 1.15;
  font-weight: 700;
  color: var(--db-text-title);
  font-variant-numeric: tabular-nums;
}

.kpi .kpi__note {
  margin-top: 4px;
  font-size: 12px;
  color: var(--db-text-sub);
}

.kpi--bad {
  border-color: var(--db-status-pink);
}

.kpi--bad .kpi__value {
  color: var(--db-status-pink);
}

/* 패널 */
.chain__grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.chain__side {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding: 18px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.panel__head {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.panel__title {
  margin: 0;
  font-size: 17px;
  line-height: 24px;
  font-weight: 700;
  letter-spacing: normal;
  color: var(--db-text-title);
}

.panel__count {
  font-size: 13px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.panel__desc {
  margin: -4px 0 0;
  font-size: 13px;
  color: var(--db-text-sub);
}

.panel__empty {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 8px 0;
  font-size: 14px;
  color: var(--db-status-green);
}

.panel__scroll {
  overflow-x: auto;
}

.panel__foot {
  display: flex;
  justify-content: center;
  padding-top: 4px;
}

/* 트랜잭션 표 */
.tx-table {
  width: 100%;
  min-width: 0;
  border-collapse: collapse;
  font-size: 13px;
}

.tx-table th {
  padding: 8px;
  border-bottom: 1px solid var(--db-border);
  background: var(--db-bg-table-head);
  font-weight: 600;
  text-align: left;
  color: var(--db-text-body);
  white-space: nowrap;
}

.tx-table td {
  padding: 9px 8px;
  border-bottom: 1px solid var(--db-border-light);
  color: var(--db-text-body);
  vertical-align: middle;
}

.tx-table .tx-table__num {
  text-align: right;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.tx-table__time {
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.tx-table__type {
  white-space: nowrap;
}

.tx-table__hash {
  white-space: nowrap;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  color: var(--db-text-sub);
}

.tx-table .tx-table__empty {
  padding: 24px 10px;
  text-align: center;
  color: var(--db-text-sub);
}

.tx-status {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 700;
  color: var(--db-on-color);
}

.tx-status--ok {
  background: var(--db-status-green);
}

.tx-status--bad {
  background: var(--db-status-pink);
}

.tx-status--wait {
  background: var(--db-status-orange);
}

.tx-reconciled {
  margin-left: 6px;
  font-size: 11px;
  color: var(--db-text-sub);
}

.tx-link {
  color: var(--db-accent);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.tx-table__status .tx-link {
  margin-left: 6px;
  font-size: 12px;
}

/* 이상 징후 */
.alert-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.alert-item {
  display: flex;
  gap: 10px;
  padding: 10px 0;
  border-top: 1px solid var(--db-border-light);
}

.alert-item__sev {
  flex-shrink: 0;
  align-self: flex-start;
  padding: 1px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 700;
  color: var(--db-on-color);
}

.alert-item__sev--bad {
  background: var(--db-status-pink);
}

.alert-item__sev--warn {
  background: var(--db-status-orange);
}

.alert-item__sev--info {
  background: var(--db-status-gray);
}

.alert-item__body {
  min-width: 0;
}

.alert-item__msg {
  margin: 0;
  font-size: 14px;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.alert-item__time {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

/* 공개 검증 */
.vc-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 24px;
}

.vc-controls {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.vc-controls__select {
  flex: 1;
  min-width: 0;
}

.vc-list {
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
}

.vc-list__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 0;
  border-top: 1px solid var(--db-border-light);
  font-size: 14px;
  color: var(--db-text-body);
}

.vc-state {
  font-size: 12px;
  font-weight: 700;
}

.vc-state--idle {
  color: var(--db-text-sub);
}

.vc-state--ok {
  color: var(--db-status-green);
}

.vc-state--fail {
  color: var(--db-status-pink);
}

.vc-result {
  margin: 10px 0 0;
  font-size: 14px;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

/* 넓은 화면: 여백을 맞추자 auto-fit 이 4+1 로 꺾였다 — 다섯 칸 한 줄 */
@media (min-width: 1024px) {
  .chain__kpis {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }
}

/* 폰 트랜잭션 목록 */
.tx-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.tx-list__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 0;
  border-bottom: 1px solid var(--db-border-light);
}

.tx-list__head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.tx-list__type {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-title);
}

.tx-list__meta {
  font-size: 12px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.tx-list__empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: var(--db-text-sub);
}

@media (max-width: 1023px) {
  .chain {
    padding: 24px 16px;
  }

  .chain__grid,
  .vc-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

/* 폰: 숫자 카드 5장이 한 장씩 쌓여 내용 전에 650px 을 썼다 — 두 칸, 작게 */
@media (max-width: 599px) {
  .chain__kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .kpi {
    padding: 12px;
  }

  .kpi .kpi__value {
    font-size: 22px;
  }

  .kpi .kpi__note {
    font-size: 11px;
  }
}
</style>
