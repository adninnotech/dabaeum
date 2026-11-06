import { expect, test } from '@playwright/test';

// 중앙 #56 → #142 — 기관 대리 수강신청은 이름 + 생년월일 + 휴대전화가 모두 맞는 1명을 찾고, 확인한 뒤 신청한다.
// 평생학번이 없어졌고 다대구 회원은 이메일이 없다. 값이 틀려도·여러 명이어도 '없음'과 같은 404

const admin = {
  id: 'inst-admin',
  name: '기관관리자',
  email: 'inst@example.test',
  roles: [{ role: 'INSTITUTION_ADMIN', institutionId: 'inst-1' }],
};

const course = { id: 'course-1', title: '파이썬 입문', status: 'OPEN' };

const learner = { userId: '11111111-2222-3333-4444-555555555555', name: '홍길동' };
const BIRTH = '1990-01-15';
const PHONE = '01012345678';
const LOCKED_PHONE = '01055555555';
const SLOW_PHONE = '01099999999';
const SLOW_LEARNER = { userId: '99999999-0000-0000-0000-000000000000', name: '느린응답' };

/** 생년월일 칸은 숫자만 쳐도 된다(mask ####-##-##) — 달력이 열리면 Esc 로 닫는다 */
async function fillBirth(dialog, value) {
  const birth = dialog.getByRole('textbox', { name: '생년월일' });
  await birth.fill(value.replace(/-/g, ''));
  await birth.press('Tab');
}

async function fillLearner(dialog, { name = '홍길동', birth = BIRTH, phone = '010-1234-5678' } = {}) {
  await dialog.getByRole('textbox', { name: '이름' }).fill(name);
  await fillBirth(dialog, birth);
  await dialog.getByRole('textbox', { name: '휴대전화' }).fill(phone);
}

async function openEnrollments(page) {
  const calls = { lookups: [], proxies: [] };
  await page.addInitScript((user) => {
    // Quasar LocalStorage 는 객체를 '__q_objt|' 접두사로 저장한다
    localStorage.setItem(
      'dabaeum.auth',
      `__q_objt|${JSON.stringify({ token: 'e2e-token', user, activeUserId: user.id, selectedRole: 'INSTITUTION_ADMIN' })}`,
    );
  }, admin);
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const { pathname, searchParams } = url;
    let status = 200;
    let retryAfter = null;
    let data = [];
    if (pathname.endsWith('/auth/session')) data = { userId: admin.id, roles: ['INSTITUTION_ADMIN'] };
    else if (pathname.endsWith('/users/me/institutions')) data = [{ institutionId: 'inst-1', name: '에이디엔기관1', role: 'INSTITUTION_ADMIN' }];
    else if (pathname.endsWith('/users/me')) data = admin;
    else if (pathname.endsWith('/institutions/inst-1/courses')) data = [course];
    else if (pathname.endsWith('/institutions/inst-1/learners/lookup')) {
      // 개인정보가 접근 로그에 남지 않게 본문으로 받는다
      expect(request.method()).toBe('POST');
      expect([...searchParams.keys()]).toEqual([]);
      const query = request.postDataJSON();
      calls.lookups.push(query);
      // 느린 응답 — 기다리는 동안 입력을 바꾸는 경우
      if (query.phone === SLOW_PHONE) {
        await new Promise((resolve) => setTimeout(resolve, 800));
        data = SLOW_LEARNER;
      } else if (query.phone === LOCKED_PHONE) {
        status = 429;
        retryAfter = '86400';
      } else if (query.name === learner.name && query.birthDate === BIRTH && query.phone === PHONE) data = learner;
      else {
        status = 404;
        data = null;
      }
    } else if (pathname.endsWith(`/courses/${course.id}/proxy-enrollments`)) {
      calls.proxies.push(request.postDataJSON());
      status = 201;
      data = { id: 'enr-1', courseId: course.id, userId: learner.userId, status: 'APPLIED' };
    }
    await route.fulfill({
      status,
      headers: retryAfter ? { 'Retry-After': retryAfter } : {},
      contentType: 'application/json',
      body: JSON.stringify(
        status === 404
          ? { code: 'USER_NOT_FOUND', message: 'User not found' }
          : status === 429
          ? { code: 'RATE_LIMITED', message: 'Too many requests' }
          : { data, page: { totalElements: Array.isArray(data) ? data.length : 0 } },
      ),
    });
  });
  await page.goto('/institution/enrollments');
  await page.getByRole('button', { name: '대리 수강신청' }).click();
  return { calls, dialog: page.getByRole('dialog') };
}

test('find a learner by name, birth date and phone, confirm, then enroll', async ({ page }) => {
  const { calls, dialog } = await openEnrollments(page);
  const apply = dialog.getByRole('button', { name: '신청', exact: true });
  await expect(apply).toBeDisabled();
  // 평생학번·이메일로 찾는 칸은 없다
  await expect(dialog.getByRole('radio')).toHaveCount(0);
  await expect(dialog).not.toContainText('평생학번');

  await dialog.getByRole('textbox', { name: '이름' }).fill(' 홍길동 ');
  await fillBirth(dialog, BIRTH);
  await expect(dialog.getByRole('button', { name: '찾기' })).toBeDisabled();
  await dialog.getByRole('textbox', { name: '휴대전화' }).fill('010-1234-5678');
  await dialog.getByRole('button', { name: '찾기' }).click();
  await expect.poll(() => calls.lookups.at(-1)).toEqual({ name: '홍길동', birthDate: BIRTH, phone: PHONE });

  const found = dialog.getByRole('group', { name: '찾은 학습자' });
  await expect(found).toContainText('홍길동');
  await expect(found).toContainText(`생년월일 ${BIRTH} 일치`);
  await expect(found).toContainText('휴대전화 일치');
  await expect(apply).toBeEnabled();

  await apply.click();
  await expect.poll(() => calls.proxies.length).toBe(1);
  expect(calls.proxies[0]).toEqual({ userId: learner.userId });
});

test('a mismatch gets one not-found message and changing input clears the found learner', async ({ page }) => {
  const { calls, dialog } = await openEnrollments(page);
  await fillLearner(dialog, { birth: '1991-01-15' });
  await dialog.getByRole('textbox', { name: '휴대전화' }).press('Enter');
  await expect(dialog.getByRole('alert')).toContainText('이름·생년월일·휴대전화가 모두 일치하는 대리 신청 가능 학습자가 없습니다.');
  expect(calls.lookups.at(-1)).toEqual({ name: '홍길동', birthDate: '1991-01-15', phone: PHONE });
  await expect(dialog.getByRole('button', { name: '신청', exact: true })).toBeDisabled();

  await fillBirth(dialog, BIRTH);
  await dialog.getByRole('button', { name: '찾기' }).click();
  await expect(dialog.getByRole('group', { name: '찾은 학습자' })).toContainText('홍길동');

  // 입력을 바꾸면 찾은 사람은 지운다 — 다른 사람으로 신청되지 않게
  await dialog.getByRole('textbox', { name: '이름' }).fill('홍길순');
  await expect(dialog.getByRole('group', { name: '찾은 학습자' })).toHaveCount(0);
  await expect(dialog.getByRole('button', { name: '신청', exact: true })).toBeDisabled();
});

test('a malformed phone is caught before calling the server and announced', async ({ page }) => {
  const { calls, dialog } = await openEnrollments(page);
  await fillLearner(dialog, { phone: '02-123-4567' });
  await dialog.getByRole('button', { name: '찾기' }).click();
  await expect(dialog.getByRole('alert')).toContainText('휴대전화 번호를 확인해 주세요.');
  expect(calls.lookups).toHaveLength(0);
});

test('a slow lookup answer is dropped once the input changed', async ({ page }) => {
  const { calls, dialog } = await openEnrollments(page);
  await fillLearner(dialog, { phone: SLOW_PHONE });
  await dialog.getByRole('button', { name: '찾기' }).click();
  await expect.poll(() => calls.lookups.length).toBe(1);

  // 응답이 오기 전에 다른 번호로 바꾼다 — 늦게 온 옛 결과가 새 입력 위에 뜨면 다른 사람이 신청된다
  await dialog.getByRole('textbox', { name: '휴대전화' }).fill(PHONE);
  await page.waitForTimeout(1200);
  await expect(dialog.getByRole('group', { name: '찾은 학습자' })).toHaveCount(0);
  await expect(dialog.getByRole('button', { name: '신청', exact: true })).toBeDisabled();
});

test('pressing Enter repeatedly while looking up sends one request', async ({ page }) => {
  const { calls, dialog } = await openEnrollments(page);
  await fillLearner(dialog, { phone: SLOW_PHONE });
  const input = dialog.getByRole('textbox', { name: '휴대전화' });
  await input.press('Enter');
  await input.press('Enter');
  await input.press('Enter');
  await expect(dialog.getByRole('group', { name: '찾은 학습자' })).toContainText(SLOW_LEARNER.name);
  expect(calls.lookups).toHaveLength(1);
});

test('a locked phone number shows the rate-limit notice in hours', async ({ page }) => {
  const { dialog } = await openEnrollments(page);
  await fillLearner(dialog, { phone: LOCKED_PHONE });
  await dialog.getByRole('button', { name: '찾기' }).click();
  const alert = dialog.getByRole('alert');
  await expect(alert).toContainText('같은 학습자를 여러 번 조회');
  await expect(alert).toContainText('24시간 뒤 다시 시도해 주세요.');
});
