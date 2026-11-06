import { expect, test } from '@playwright/test';

// 강사 콘솔 마이페이지(/instructor/mypage)엔 '에이디엔기관1 소속 강사'가 나오는데,
// 학습자 화면 마이페이지(/mypage)엔 소속이 없었다. 두 화면이 같은 소속 표시를 쓴다.

const user = {
  id: 'affiliation-user',
  name: '소속강사',
  email: 'affiliation@example.test',
  roles: [{ role: 'LEARNER' }, { role: 'INSTRUCTOR', institutionId: 'inst-1' }],
};

const institutions = [{ institutionId: 'inst-1', name: '에이디엔기관1', role: 'INSTRUCTOR' }];

async function openWithMockedApi(page, path, selectedRole) {
  await page.addInitScript(
    ({ sessionUser, role }) => {
      // Quasar LocalStorage 는 객체를 '__q_objt|' 접두사로 저장한다
      localStorage.setItem(
        'dabaeum.auth',
        `__q_objt|${JSON.stringify({
          token: 'e2e-token',
          user: sessionUser,
          activeUserId: sessionUser.id,
          selectedRole: role,
        })}`,
      );
    },
    { sessionUser: user, role: selectedRole },
  );

  await page.route('**/api/v1/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    let data = [];
    if (pathname.endsWith('/auth/session')) data = { userId: user.id, roles: ['LEARNER', 'INSTRUCTOR'] };
    else if (pathname.endsWith('/users/me/institutions')) data = institutions;
    else if (pathname.endsWith('/users/me')) data = user;
    else if (pathname.endsWith('/institutions/inst-1')) data = { id: 'inst-1', name: '에이디엔기관1' };

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data, page: { totalElements: Array.isArray(data) ? data.length : 0 } }),
    });
  });

  await page.goto(path);
}

for (const [label, path, role] of [
  ['learner', '/mypage', 'LEARNER'],
  ['instructor console', '/instructor/mypage', 'INSTRUCTOR'],
]) {
  test(`${label} mypage shows the instructor affiliation`, async ({ page }) => {
    await openWithMockedApi(page, path, role);
    const list = page.getByRole('list', { name: '소속 기관' });
    await expect(list).toBeVisible();
    await expect(list).toContainText('에이디엔기관1 · 강사');
  });
}
