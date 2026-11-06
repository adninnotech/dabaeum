import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const source = await readFile(new URL('../src/utils/enrollment-decision.js', import.meta.url), 'utf8');
const executable = source.replace(
  "import { statusLabel } from '@/utils/status';",
  "const statusLabel = (status) => ({ APPLIED: '신청', WITHDRAWN: '철회', CANCELLED: '취소' })[status] || status || '-';",
);
const { enrollmentBadgeLabel, enrollmentCancellationReason, isWaitlistBadgeSoft } = await import(
  `data:text/javascript;base64,${Buffer.from(executable).toString('base64')}`
);

test('WITHDRAWN enrollment is labeled as 수강 철회', () => {
  assert.equal(enrollmentBadgeLabel({ status: 'WITHDRAWN' }), '수강 철회');
});

test('cancellation reason is shown for cancelled applications and course-cancelled withdrawals', () => {
  assert.equal(enrollmentCancellationReason({ status: 'CANCELLED', cancellationReason: '과정 취소' }), '과정 취소');
  assert.equal(enrollmentCancellationReason({ status: 'WITHDRAWN', cancellationReason: '과정 취소' }), '과정 취소');
  assert.equal(enrollmentCancellationReason({ status: 'REJECTED', cancellationReason: '과정 취소' }), '');
});

test('only waitlisted rows get the soft badge', () => {
  assert.equal(isWaitlistBadgeSoft({ status: 'WAITLISTED', waitlistPosition: 1 }), true);
  assert.equal(isWaitlistBadgeSoft({ status: 'WAITLISTED' }), true);
  assert.equal(isWaitlistBadgeSoft({ status: 'APPLIED' }), false);
  assert.equal(isWaitlistBadgeSoft(null), false);
});
