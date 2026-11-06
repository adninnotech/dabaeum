import { expect, test } from '@playwright/test';

/* 학습자 가입·로그인은 다대구로만(#112). 학습자 이메일 가입(/signup)은 없고, 강사·기관 관리자 이메일 가입·로그인은
 * /login/staff 쪽에 따로 있다(#134, staff-login-split.spec.js). /login?local=1 이메일 폼은 QA 동안 남긴다 */

async function mockEmptyApi(page) {
  await page.route('**/api/v1/**', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data: [], page: { totalElements: 0, totalPages: 1 } }),
    }),
  );
}

for (const path of ['/signup']) {
  test(`옛 가입 주소 ${path} 는 로그인 화면으로 간다`, async ({ page }) => {
    await mockEmptyApi(page);
    await page.goto(path);
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('button', { name: '다대구 로그인' })).toBeVisible();
  });
}

test('가입 주소의 돌아갈 곳은 로그인으로 넘긴다', async ({ page }) => {
  await mockEmptyApi(page);
  await page.goto('/signup?redirect=%2Fcourses');
  await expect(page).toHaveURL(/\/login\?redirect=(%2F|\/)courses$/);
});

test('로그인 화면엔 이메일 폼·회원가입 링크 없이 다대구 로그인만 있다', async ({ page }) => {
  await mockEmptyApi(page);
  await page.goto('/login?need=1');

  await expect(page.getByRole('button', { name: '다대구 로그인' })).toBeVisible();
  await expect(page.getByPlaceholder('이메일을 입력해 주세요')).toHaveCount(0);
  await expect(page.getByRole('button', { name: '비밀번호 찾기' })).toHaveCount(0);
  await expect(page.getByRole('link', { name: '회원가입' })).toHaveCount(0);
  await expect(page.locator('.login__notice')).toContainText('다대구 로그인');
  await expect(page.locator('.login__register')).toContainText('다대구로 로그인하면 바로 가입돼요');
});

test('/login?local=1 이면 기존 계정용 이메일 로그인이 보인다', async ({ page }) => {
  await mockEmptyApi(page);
  await page.goto('/login?local=1');

  await expect(page.getByPlaceholder('이메일을 입력해 주세요')).toBeVisible();
  await expect(page.getByPlaceholder('비밀번호를 입력해 주세요')).toBeVisible();
  await expect(page.getByRole('button', { name: '비밀번호 찾기' })).toBeVisible();
  await expect(page.getByRole('button', { name: '다대구 로그인' })).toBeVisible();
});

test('비로그인 학습자 머리·폰 메뉴에 회원가입이 없다', async ({ page, isMobile }) => {
  await mockEmptyApi(page);
  await page.goto('/courses');

  if (isMobile) await page.getByRole('button', { name: '메뉴', exact: true }).click();
  await expect(page.getByRole('button', { name: '로그인', exact: true }).first()).toBeVisible();
  await expect(page.getByRole('link', { name: '회원가입' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '회원가입' })).toHaveCount(0);
});

test('다대구로만 가입한 계정의 마이페이지에 이메일·비밀번호 연결이 없다', async ({ page }) => {
  const user = { id: 'dadaegu-user', name: '다대구회원', email: '', roles: [{ role: 'LEARNER' }] };
  await page.addInitScript((sessionUser) => {
    // Quasar LocalStorage 는 객체를 '__q_objt|' 접두사로 저장한다
    localStorage.setItem(
      'dabaeum.auth',
      `__q_objt|${JSON.stringify({ token: 'e2e-token', user: sessionUser, activeUserId: sessionUser.id, selectedRole: 'LEARNER' })}`,
    );
  }, user);
  await page.route('**/api/v1/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    let data = [];
    if (pathname.endsWith('/auth/session')) data = { userId: user.id, roles: ['LEARNER'] };
    else if (pathname.endsWith('/users/me/identities')) data = [{ provider: 'DADAEGU', verifiedAt: '2026-10-01T00:00:00Z' }];
    else if (pathname.endsWith('/users/me')) data = user;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data, page: { totalElements: Array.isArray(data) ? data.length : 0 } }),
    });
  });

  await page.goto('/mypage');
  await expect(page.getByText('다대구 연결', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '이메일·비밀번호 연결' })).toHaveCount(0);
  await expect(page.getByText('이메일 로그인', { exact: true })).toHaveCount(0);
});
