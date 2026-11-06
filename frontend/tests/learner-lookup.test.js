import assert from 'node:assert/strict';
import test from 'node:test';

import { lookupQuery, retryAfterText } from '../src/utils/learner-lookup.js';

// 중앙 #56 → #142 — 기관 대리 수강신청은 이름 + 생년월일 + 휴대전화가 모두 맞는 1명만 찾는다.
// 평생학번이 없어졌고 다대구 회원은 이메일이 없다. 서버에 보내기 전 형식을 거른다

const BIRTH = '1990-01-15';
const input = (over = {}) => ({ name: '홍길동', birthDate: BIRTH, phone: '010-1234-5678', ...over });

test('세 값이 맞으면 이름은 앞뒤 공백을 지우고 휴대전화는 숫자만 보낸다', () => {
  const expected = { query: { name: '홍길동', birthDate: BIRTH, phone: '01012345678' }, error: '' };
  assert.deepEqual(lookupQuery(input()), expected);
  assert.deepEqual(lookupQuery(input({ name: '  홍길동 ', phone: '010 1234 5678' })), expected);
  assert.deepEqual(lookupQuery(input({ phone: '01012345678' })), expected);
});

test('예전 010-XXX-XXXX(10자리) 번호도 받는다', () => {
  assert.equal(lookupQuery(input({ phone: '011-123-4567' })).query.phone, '0111234567');
});

test('휴대전화 모양이 아니면 오류', () => {
  const message = '휴대전화 번호를 확인해 주세요.';
  assert.equal(lookupQuery(input({ phone: '1234' })).error, message);
  assert.equal(lookupQuery(input({ phone: '02-123-4567' })).error, message);
  assert.equal(lookupQuery(input({ phone: '010-1234-567a' })).query, null);
});

test('생년월일이 없는 날짜·형식 오류·미래면 오류', () => {
  const message = '생년월일을 YYYY-MM-DD 로 입력해 주세요.';
  assert.equal(lookupQuery(input({ birthDate: '1990-02-30' })).error, message);
  assert.equal(lookupQuery(input({ birthDate: '900115' })).error, message);
  assert.equal(lookupQuery(input({ birthDate: '2999-01-01' })).error, message);
});

test('하나라도 비어 있으면 오류 문구 없이 query 도 없다 — [찾기] 버튼만 막는다', () => {
  const empty = { query: null, error: '' };
  assert.deepEqual(lookupQuery(input({ name: '  ' })), empty);
  assert.deepEqual(lookupQuery(input({ birthDate: '' })), empty);
  assert.deepEqual(lookupQuery(input({ phone: '' })), empty);
  assert.deepEqual(lookupQuery(), empty);
});

test('이름이 100자를 넘으면 오류', () => {
  assert.equal(lookupQuery(input({ name: '가'.repeat(101) })).error, '이름은 100자까지 입력할 수 있습니다.');
});

test('조회 제한(429) 남은 시간 — 초·분·시간으로 읽기 좋게', () => {
  assert.equal(retryAfterText(45), '45초');
  assert.equal(retryAfterText(90), '2분');
  assert.equal(retryAfterText(3600), '1시간');
  assert.equal(retryAfterText(86400), '24시간');
  assert.equal(retryAfterText(5400), '1시간 30분');
  assert.equal(retryAfterText(null), '');
  assert.equal(retryAfterText(0), '');
});
