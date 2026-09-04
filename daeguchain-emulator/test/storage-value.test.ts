import { test } from 'node:test';
import assert from 'node:assert/strict';
import { decodeStorageValue, encodeStorageValue, isStorageKey } from '../src/fabric/storage-value.js';

const HASH = 'a'.repeat(64);

test('백엔드 StorageValue 와 같은 형식으로 인코딩한다', () => {
  const encoded = encodeStorageValue({
    schemaVersion: 1,
    status: 'ACTIVE',
    vcHash: HASH,
    eventTime: new Date('2026-09-03T00:00:00.000Z'),
  });
  assert.equal(encoded, `1|A|${HASH}|1788393600000`);
  assert.deepEqual(decodeStorageValue(encoded), {
    schemaVersion: 1,
    status: 'ACTIVE',
    vcHash: HASH,
    eventTime: new Date(1788393600000),
  });
});

test('REVOKED·SUPERSEDED 코드를 왕복한다', () => {
  for (const [status, code] of [['REVOKED', 'R'], ['SUPERSEDED', 'S']] as const) {
    const encoded = encodeStorageValue({ schemaVersion: 1, status, vcHash: HASH, eventTime: new Date(1) });
    assert.equal(encoded, `1|${code}|${HASH}|1`);
    assert.equal(decodeStorageValue(encoded).status, status);
  }
});

test('체인코드 정규식을 벗어나는 값은 거부한다', () => {
  assert.throws(() => decodeStorageValue(`2|A|${HASH}|1`));
  assert.throws(() => decodeStorageValue(`1|X|${HASH}|1`));
  assert.throws(() => decodeStorageValue(`1|A|${'A'.repeat(64)}|1`));
  assert.throws(() => decodeStorageValue(`1|A|${HASH}|0`));
  assert.throws(() => decodeStorageValue(`1|A|${HASH}|01`));
  assert.throws(() => encodeStorageValue({ schemaVersion: 1, status: 'ACTIVE', vcHash: 'short', eventTime: new Date(1) }));
});

test('키는 대문자·숫자 16자만 허용한다', () => {
  assert.ok(isStorageKey('A1B2C3D4E5F6G7H8'));
  assert.ok(!isStorageKey('a1b2c3d4e5f6g7h8'));
  assert.ok(!isStorageKey('A1B2C3D4E5F6G7H'));
  assert.ok(!isStorageKey('A1B2C3D4E5F6G7H8X'));
});
