import assert from 'node:assert/strict';
import test from 'node:test';

import { INTEREST_LABELS, INTEREST_MAX, interestNames, interestOptions, toggleInterest } from '../src/utils/interest.js';

// #142 — 관심 분야는 강좌 분류 1단계 중 0~3개(선택). 구·군 대신 추천에 쓴다

const TREE = [
  { code: 'TSIX_010000', level: 1, name: '기초문해교육', enabled: true, children: [{ code: 'TSIX_010100', level: 2, name: '문자해득' }] },
  { code: 'TSIX_020000', level: 1, name: '학력보완교육', enabled: true, children: [] },
  { code: 'TSIX_030000', level: 1, name: '꺼진 분류', enabled: false, children: [] },
  { code: 'TSIX_040000', level: 1, name: '문화예술교육', children: [] },
  { level: 1, name: '코드 없음' },
];

test('선택지는 사용 중인 1단계만, 순서 그대로 — 분류 이름 대신 쉬운 이름', () => {
  assert.deepEqual(interestOptions(TREE), [
    { code: 'TSIX_010000', name: '한글·스마트폰 배우기' },
    { code: 'TSIX_020000', name: '검정고시·학력' },
    { code: 'TSIX_040000', name: '운동·취미·예술' },
  ]);
  assert.deepEqual(interestOptions(null), []);
});

test('쉬운 이름은 표준 분류 코드에 묶는다 — 7개 모두, 모르는 코드는 서버 이름', () => {
  const codes = ['010000', '020000', '030000', '040000', '050000', '060000', '070000'].map((n) => `TSIX_${n}`);
  codes.forEach((code) => assert.ok(INTEREST_LABELS[code], code));
  assert.equal(interestOptions([{ code: 'TSIX_990000', name: '새 분류' }])[0].name, '새 분류');
});

test('누르면 넣고 다시 누르면 뺀다 — 원래 배열은 그대로', () => {
  const before = ['TSIX_010000'];
  const added = toggleInterest(before, 'TSIX_020000');
  assert.deepEqual(added, ['TSIX_010000', 'TSIX_020000']);
  assert.deepEqual(before, ['TSIX_010000']);
  assert.deepEqual(toggleInterest(added, 'TSIX_010000'), ['TSIX_020000']);
});

test(`${INTEREST_MAX}개를 넘으면 더 넣지 않는다`, () => {
  const full = ['A', 'B', 'C'];
  assert.equal(INTEREST_MAX, 3);
  assert.equal(toggleInterest(full, 'D'), full);
  assert.deepEqual(toggleInterest(full, 'B'), ['A', 'C']);
});

test('코드 → 쉬운 이름(고른 순서), 모르는 코드는 뺀다', () => {
  const options = interestOptions(TREE);
  assert.deepEqual(interestNames(['TSIX_040000', 'NOPE', 'TSIX_010000'], options), ['운동·취미·예술', '한글·스마트폰 배우기']);
  assert.deepEqual(interestNames(undefined, options), []);
});
