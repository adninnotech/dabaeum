import { expect } from '@playwright/test';

export const adminCredentials = {
  email: process.env.E2E_ADMIN_EMAIL || '',
  password: process.env.E2E_ADMIN_PASSWORD || '',
};

export const hasAdminCredentials = Boolean(adminCredentials.email && adminCredentials.password);

/** 로그인 화면으로 로그인 — 성공하면 로그인 화면을 벗어난다 */
export async function loginViaForm(page, { email, password }, redirect = '/') {
  // 이메일 로그인은 ?local=1 에서만 보인다 (#112 — 가입·로그인은 다대구로만)
  await page.goto(`/login?local=1&redirect=${encodeURIComponent(redirect)}`);
  await page.getByPlaceholder('이메일을 입력해 주세요').fill(email);
  await page.getByPlaceholder('비밀번호를 입력해 주세요').fill(password);
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await expect(page).not.toHaveURL(/\/login/);
}
