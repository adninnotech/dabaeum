<template>
  <!-- 학습지갑 수료증 탭 — 한 수료증(그룹)에 한 줄. 재발급으로 대체된 옛 버전은 그 줄 아래에 접어 둔다 -->
  <div class="wallet-creds">
    <AppEmptyState
      v-if="!groups.length"
      compact
      description="강좌를 수료하면 여기에 수료증이 쌓입니다."
      action-label="나의 강좌"
      action-to="/learning"
    />
    <ul v-else class="wallet-creds__list">
      <li v-for="group in groups" :key="group.key" class="wallet-creds__group">
        <!-- 줄 전체를 누르면 상세(늘린 버튼), [QR·링크]는 그 위에 떠서 따로 눌린다 — 버튼 안에 버튼을 넣지 않는다 -->
        <div class="wallet-creds__row">
          <span class="wallet-creds__main">
            <button type="button" class="wallet-creds__open" @click="open(group.current)">
              <span class="wallet-creds__title">{{ titleOf(group.current) }}</span>
            </button>
            <!-- 32자리 번호가 강좌명 아래 줄을 다 차지했다(폰은 세 줄) — 번호는 상세에, 여기는 기관·발급일 -->
            <span class="wallet-creds__meta">
              <span v-if="reissueOf(group.current)" class="wallet-creds__chip">{{ reissueOf(group.current) }}</span>
              {{ metaOf(group.current) }}
            </span>
            <!-- 폰: 뱃지·QR·화살표가 140px 을 써 강좌명이 '[QA] 2026-10-01 수…'로 잘렸다 — 뱃지는 글 아래 줄로 -->
            <StatusBadge
              v-if="$q.screen.xs"
              v-bind="credentialBadge(group.current)"
              size="sm"
              class="wallet-creds__status wallet-creds__status--inline"
            />
            <span
              v-if="noteOf(group.current)"
              class="wallet-creds__note"
              :class="{ 'wallet-creds__note--plain': isCredentialExpired(group.current) }"
            >
              {{ noteOf(group.current) }}
            </span>
          </span>
          <StatusBadge v-if="!$q.screen.xs" v-bind="credentialBadge(group.current)" class="wallet-creds__status" />
          <q-btn
            v-if="canShare(group.current)"
            flat
            round
            dense
            icon="o_qr_code_2"
            color="primary"
            class="wallet-creds__share"
            :aria-label="`${titleOf(group.current)} 진위 확인 QR·링크`"
            @click="share(group.current)"
          >
            <q-tooltip>진위 확인 QR·링크</q-tooltip>
          </q-btn>
          <q-icon name="chevron_right" size="20px" class="wallet-creds__chevron" aria-hidden="true" />
        </div>

        <template v-if="group.previous.length">
          <button
            type="button"
            class="wallet-creds__toggle"
            :aria-expanded="isOpen(group.key) ? 'true' : 'false'"
            @click="toggle(group.key)"
          >
            이전 버전 {{ group.previous.length }}개
            <q-icon :name="isOpen(group.key) ? 'expand_less' : 'expand_more'" size="18px" aria-hidden="true" />
          </button>
          <ul v-if="isOpen(group.key)" class="wallet-creds__previous">
            <li v-for="item in group.previous" :key="item.id">
              <div class="wallet-creds__row wallet-creds__row--previous">
                <span class="wallet-creds__main">
                  <button type="button" class="wallet-creds__open" @click="open(item)">
                    <span class="wallet-creds__meta">{{ reissueOf(item) || '최초 발급' }} · {{ issuedOf(item) }}</span>
                  </button>
                </span>
                <StatusBadge :status="item.status" size="sm" soft class="wallet-creds__status" />
                <q-icon name="chevron_right" size="18px" class="wallet-creds__chevron" aria-hidden="true" />
              </div>
            </li>
          </ul>
        </template>
      </li>
    </ul>

    <CredentialVerifyShareDialog
      v-if="shareNo"
      v-model="shareOpen"
      :credential-no="shareNo"
    />
  </div>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import CredentialVerifyShareDialog from '@/components/wallet/CredentialVerifyShareDialog.vue';
import { LEARNER_FAILURE_TEXT } from '@/utils/credential-failure';
import { credentialBadge, isCredentialExpired } from '@/utils/credential-validity';
import { formatDateKst } from '@/utils/datetime';

/** 같은 그룹 안에서 지금 보여 줄 대표 — 대체되지 않은 것 중 가장 높은 버전 */
function pickCurrent(items) {
  const byVersion = [...items].sort((a, b) => (b.versionNo || 1) - (a.versionNo || 1));
  return byVersion.find((item) => item.status !== 'SUPERSEDED') || byVersion[0];
}

export default {
  name: 'WalletCredentialList',
  components: { AppEmptyState, CredentialVerifyShareDialog, StatusBadge },
  props: {
    credentials: {
      type: Array,
      default: () => [],
    },
  },
  data() {
    return { openKeys: [], shareOpen: false, shareNo: '' };
  },
  computed: {
    groups() {
      const byGroup = this.credentials.reduce((acc, item) => {
        const key = item.credentialGroupId || item.id;
        return { ...acc, [key]: [...(acc[key] || []), item] };
      }, {});
      return Object.entries(byGroup).map(([key, items]) => {
        const current = pickCurrent(items);
        const previous = items
          .filter((item) => item.id !== current.id)
          .sort((a, b) => (b.versionNo || 1) - (a.versionNo || 1));
        return { key, current, previous };
      });
    },
  },
  methods: {
    credentialBadge,
    isCredentialExpired,
    titleOf(item) {
      return item.courseTitle || item.credentialNo || '수료증';
    },
    issuedOf(item) {
      const when = item.issuedAt || item.validFrom;
      return when ? `${formatDateKst(when)} 발급` : '발급 전';
    },
    /* '~ 만료 없음' 만으로는 무슨 날짜인지 몰랐다 — 기간이 있을 때만 붙인다 */
    validityOf(item) {
      return item.validUntil ? `${formatDateKst(item.validUntil)}까지 유효` : '';
    },
    /** 학습자에게 "v2" 같은 내부 버전 표기는 낯설다 — 재발급된 것만 회차로 알린다 */
    reissueOf(item) {
      const version = Number(item.versionNo) || 1;
      return version > 1 ? `재발급 ${version - 1}회차` : '';
    },
    metaOf(item) {
      return [item.institutionName, this.issuedOf(item), this.validityOf(item)].filter(Boolean).join(' · ');
    },
    /** 폐기·실패는 뱃지 색만으로는 왜인지 몰랐다 */
    noteOf(item) {
      if (item.status === 'REVOKED') return item.revocationReason ? `폐기 사유: ${item.revocationReason}` : '';
      if (item.status === 'FAILED') return LEARNER_FAILURE_TEXT;
      if (isCredentialExpired(item)) return `${formatDateKst(item.validUntil)}에 유효기간이 끝났어요. 진위 확인에서도 '유효기간 지남'으로 나옵니다.`;
      return '';
    },
    canShare(item) {
      return item.status === 'ISSUED' && !!item.credentialNo;
    },
    share(item) {
      this.shareNo = item.credentialNo;
      this.shareOpen = true;
    },
    isOpen(key) {
      return this.openKeys.includes(key);
    },
    toggle(key) {
      this.openKeys = this.isOpen(key) ? this.openKeys.filter((k) => k !== key) : [...this.openKeys, key];
    },
    open(item) {
      this.$router.push(`/wallet/credentials/${item.id}`);
    },
  },
};
</script>

<style scoped>
.wallet-creds__list,
.wallet-creds__previous {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}

.wallet-creds__list {
  gap: 12px;
}

.wallet-creds__group {
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.wallet-creds__row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-radius: 8px;
}

.wallet-creds__row:hover {
  background: var(--db-bg-subtle);
}

.wallet-creds__row:has(.wallet-creds__open:focus-visible),
.wallet-creds__toggle:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: -2px;
}

.wallet-creds__open {
  display: block;
  min-width: 0;
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  text-align: left;
  color: inherit;
  cursor: pointer;
  outline: none;
}

/* 줄 전체를 누르는 영역 */
.wallet-creds__open::after {
  content: '';
  position: absolute;
  inset: 0;
}

/* QR 버튼은 늘린 영역 위 */
.wallet-creds__share {
  position: relative;
  z-index: 1;
  flex-shrink: 0;
}

.wallet-creds__chip {
  display: inline-block;
  margin-right: 6px;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  color: var(--db-accent);
  background: var(--db-accent-soft);
}

.wallet-creds__note {
  font-size: 14px;
  color: var(--db-status-pink);
  overflow-wrap: anywhere;
}

/* 기간 만료는 잘못된 게 아니라 끝난 것 — 폐기·실패의 빨간 글씨 대신 회색 */
.wallet-creds__note--plain {
  color: var(--db-text-sub);
}

.wallet-creds__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.wallet-creds__title {
  display: block;
  overflow: hidden;
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--db-text-title);
}

.wallet-creds__meta {
  overflow-wrap: anywhere;
  font-size: 14px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.wallet-creds__status,
.wallet-creds__chevron {
  flex-shrink: 0;
}

.wallet-creds__chevron {
  color: var(--db-text-sub);
}

/* 예전엔 줄 왼쪽에 작게 붙어 누르기 어려웠다 — 구분선 아래 한 줄, 높이 40px */
.wallet-creds__toggle {
  display: flex;
  align-items: center;
  gap: 2px;
  width: 100%;
  min-height: 40px;
  padding: 0 20px;
  border: 0;
  border-top: 1px solid var(--db-border);
  background: none;
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  color: var(--db-text-sub);
  cursor: pointer;
}

.wallet-creds__previous {
  border-top: 1px solid var(--db-border);
}

.wallet-creds__row--previous {
  padding: 12px 20px;
}

@media (max-width: 599px) {
  .wallet-creds__row {
    padding: 14px 16px;
  }

  .wallet-creds__toggle {
    padding: 0 16px;
  }

  .wallet-creds__row {
    gap: 8px;
  }

  .wallet-creds__status--inline {
    align-self: flex-start;
    margin-top: 2px;
  }
}
</style>
