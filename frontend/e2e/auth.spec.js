import { expect, test } from '@playwright/test';

test('로그인하지 않고 관리자 콘솔을 열면 로그인 화면으로 보낸다', async ({ page }) => {
  await page.goto('/admin/blockchain');
  await expect(page).toHaveURL(/\/login\?redirect=(%2F|\/)admin(%2F|\/)blockchain/);
});

test('없는 계정으로 로그인하면 실패 안내를 보이고 머문다', async ({ page }) => {
  await page.goto('/login?local=1');
  // 실제 계정 잠금을 건드리지 않게 존재할 수 없는 주소(.invalid)를 쓴다
  await page
    .getByPlaceholder('이메일을 입력해 주세요')
    .fill(`e2e-nobody-${Date.now()}@example.invalid`);
  await page.getByPlaceholder('비밀번호를 입력해 주세요').fill('not-a-real-password-1!');
  await page.getByRole('button', { name: '로그인', exact: true }).click();

  await expect(page.locator('.local-login__feedback')).toBeVisible();
  await expect(page).toHaveURL(/\/login/);
});

test('429 안내에는 재시도 시간이 나온다', async ({ page }) => {
  await page.route('**/api/v1/auth/login', (route) =>
    route.fulfill({
      status: 429,
      headers: { 'Retry-After': '12', 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: 'RATE_LIMITED', message: 'Too many requests' }),
    }),
  );
  await page.goto('/login?local=1');
  await page.getByPlaceholder('이메일을 입력해 주세요').fill('test@example.invalid');
  await page.getByPlaceholder('비밀번호를 입력해 주세요').fill('not-a-real-password-1!');
  await page.getByRole('button', { name: '로그인', exact: true }).click();

  await expect(page.locator('.local-login__feedback')).toContainText('12초 뒤 다시 시도해 주세요.');
});
