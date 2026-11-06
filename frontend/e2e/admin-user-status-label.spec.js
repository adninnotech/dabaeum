import { expect, test } from '@playwright/test';

// QA 중앙 #53 — /admin/users 에서 탈퇴(WITHDRAWN) 회원이 수강 철회 라벨 '철회'로 보였다(학생 상세는 '탈퇴')

const adminUser = {
  id: 'admin-user',
  name: '관리자',
  email: 'admin@example.test',
  roles: [{ role: 'PLATFORM_ADMIN' }],
};

const withdrawnUser = {
  id: 'withdrawn-user',
  name: '탈퇴회원',
  email: 'withdrawn@example.test',
  status: 'WITHDRAWN',
  roles: [{ role: 'LEARNER' }],
  createdAt: '2026-10-01T00:00:00Z',
};

test('admin user list labels a withdrawn member as 탈퇴', async ({ page }) => {
  await page.addInitScript((user) => {
    // Quasar LocalStorage 는 객체를 '__q_objt|' 접두사로 저장한다
    localStorage.setItem(
      'dabaeum.auth',
      `__q_objt|${JSON.stringify({
        token: 'e2e-token',
        user,
        activeUserId: user.id,
        selectedRole: 'PLATFORM_ADMIN',
      })}`,
    );
  }, adminUser);

  await page.route('**/api/v1/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    let data = [];
    if (pathname.endsWith('/auth/session')) {
      data = { userId: adminUser.id, roles: ['PLATFORM_ADMIN'] };
    } else if (pathname.endsWith('/users/me')) {
      data = adminUser;
    } else if (pathname.endsWith('/users')) {
      data = [withdrawnUser];
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data, page: { totalElements: Array.isArray(data) ? data.length : 0 } }),
    });
  });

  await page.goto('/admin/users');
  await expect(page.getByText('탈퇴회원').first()).toBeVisible();
  await expect(page.locator('.status-badge').filter({ hasText: '탈퇴' }).first()).toBeVisible();
  await expect(page.locator('.status-badge').filter({ hasText: '철회' })).toHaveCount(0);
});
