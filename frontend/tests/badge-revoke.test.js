import assert from 'node:assert/strict';
import test from 'node:test';

import { revokeCause } from '../src/utils/badge-revoke.js';

// QA 중앙 #54 — 관리자가 사유를 넣어 폐기한 배지에도 "조건을 다시 채우면 돌아옵니다"가 나왔다

test('수료 확정 취소로 자동 폐기된 배지는 AUTO', () => {
  for (const reason of [
    '수료 확정 취소로 발급 조건 미충족',
    '수료 확정 취소로 강좌 이수 3건 — 첫 단계 미달',
  ]) {
    assert.deepEqual(revokeCause({ lastEvent: { type: 'REVOKED', reason, occurredAt: '2026-10-22T00:00:00Z' } }), {
      kind: 'AUTO',
      reason,
    });
  }
});

test('관리자가 사유를 넣어 폐기한 배지는 MANUAL 과 그 사유', () => {
  assert.deepEqual(
    revokeCause({ lastEvent: { type: 'REVOKED', reason: '부정 이수 확인', occurredAt: '2026-10-22T00:00:00Z' } }),
    { kind: 'MANUAL', reason: '부정 이수 확인' },
  );
});

test('마지막 변동이 없거나 폐기가 아니면 원인을 모른다', () => {
  assert.deepEqual(revokeCause({}), { kind: 'UNKNOWN', reason: '' });
  assert.deepEqual(revokeCause(null), { kind: 'UNKNOWN', reason: '' });
  assert.deepEqual(revokeCause({ lastEvent: { type: 'ISSUED', reason: 'x' } }), { kind: 'UNKNOWN', reason: '' });
});
