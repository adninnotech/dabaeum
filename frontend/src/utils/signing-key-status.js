/* 기관 서명키 현황(GET /institutions/{id}/signing-keys) → 관리자 화면용 대표 상태 한 개.
 * 규칙은 백엔드 합의(2026-10-02) 순서 그대로 — 위에서 먼저 맞는 것을 쓴다. */

const PENDING_OPERATION = ['PREPARED', 'UNCONFIRMED', 'RECORDED'];
// 워커가 승인 15초 뒤 처음 돌고 이후 30초 주기 — 여유 두고 60초 안이면 "생성 대기"로 보인다
const AWAITING_WINDOW_MS = 60000;

export const SIGNING_KEY_STATE = {
  NEEDS_REVIEW: { key: 'NEEDS_REVIEW', label: '확인 필요', color: '#a65300' },
  AWAITING: { key: 'AWAITING', label: '생성 대기', color: '#2a5fcc' },
  NONE: { key: 'NONE', label: '없음', color: '#727379' },
  ERROR: { key: 'ERROR', label: '오류', color: '#c72656' },
  PROVISIONING: { key: 'PROVISIONING', label: '생성 중', color: '#2a5fcc' },
  COMPROMISED: { key: 'COMPROMISED', label: '유출', color: '#c72656' },
  REVOKED: { key: 'REVOKED', label: '폐지', color: '#727379' },
  SUSPENDED: { key: 'SUSPENDED', label: '정지', color: '#a65300' },
  ACTIVE: { key: 'ACTIVE', label: '정상', color: '#237a54' },
};

// [서명키 만들기]·[다시 시도]는 이 두 상태에서만 보인다
const RETRYABLE = ['NONE', 'ERROR'];

function withinAwaitingWindow(approvedAt) {
  const time = approvedAt ? new Date(approvedAt).getTime() : NaN;
  return Number.isFinite(time) && Date.now() - time < AWAITING_WINDOW_MS;
}

/**
 * @param {object} overview signing-keys 응답 data
 * @param {string} [approvedAt] 기관 승인·생성 시각 — 60초 안이면 키 없음을 "생성 대기"로 본다
 * @returns {{ key, label, color, canCreate: boolean }}
 */
export function summarizeSigningKeys(overview, approvedAt) {
  const operations = Array.isArray(overview?.operations) ? overview.operations : [];
  const current = (Array.isArray(overview?.keys) ? overview.keys : []).filter((k) => k.current);
  const statuses = current.map((k) => k.status);
  const credential = current.find((k) => k.purpose === 'credential');

  const pick = () => {
    if (operations.some((op) => PENDING_OPERATION.includes(op.status))) {
      return SIGNING_KEY_STATE.NEEDS_REVIEW;
    }
    if (!current.length) {
      return withinAwaitingWindow(approvedAt) ? SIGNING_KEY_STATE.AWAITING : SIGNING_KEY_STATE.NONE;
    }
    if (statuses.includes('ERROR')) return SIGNING_KEY_STATE.ERROR;
    if (statuses.includes('PROVISIONING')) return SIGNING_KEY_STATE.PROVISIONING;
    if (statuses.includes('COMPROMISED')) return SIGNING_KEY_STATE.COMPROMISED;
    if (credential?.status === 'REVOKED') return SIGNING_KEY_STATE.REVOKED;
    if (credential?.status === 'SUSPENDED') return SIGNING_KEY_STATE.SUSPENDED;
    if (current.length === 2 && statuses.every((s) => s === 'ACTIVE')) {
      const version = credential?.currentVersion;
      return {
        ...SIGNING_KEY_STATE.ACTIVE,
        label: version != null ? `정상 v${version}` : SIGNING_KEY_STATE.ACTIVE.label,
      };
    }
    // 한쪽만 만들어졌거나 규칙 밖의 조합 — 다시 만들기 대상
    return SIGNING_KEY_STATE.ERROR;
  };

  const state = pick();
  return { ...state, canCreate: RETRYABLE.includes(state.key) };
}

export const SIGNING_KEY_HINT = {
  NEEDS_REVIEW:
    '서명키 작업 결과를 확인하지 못했습니다. 운영자가 OpenBao 감사 로그를 보고 판정해야 합니다.',
  AWAITING: '기관 승인 직후 자동으로 만들고 있습니다. 잠시 뒤 새로고침해 주세요.',
  NONE: '자동 생성이 멈췄거나 아직 만들지 않았습니다. 서명키가 없으면 수료증을 발급할 수 없습니다.',
  ERROR: '서명키를 만들지 못했습니다. 다시 시도해 주세요.',
  PROVISIONING: '서명키를 만드는 중입니다.',
  COMPROMISED: '서명키가 유출 의심으로 막혀 있습니다. 운영자 조치가 필요합니다.',
  REVOKED: '수료증 서명키가 폐지되어 발급할 수 없습니다.',
  SUSPENDED: '수료증 서명키가 정지되어 발급할 수 없습니다.',
  ACTIVE: '',
};
