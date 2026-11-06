import { expect, test } from '@playwright/test';

// #144 — 알림 및 수신 설정(이메일·SMS)은 메일·문자 발송 코드가 없어 화면에서 뺐다(학습자·운영 콘솔 둘 다).
// 학습자 머리글 알림 벨도 뺐다. 남은 [회원 정보 수정]은 그대로 저장된다. 수신 설정 API 는 부르지 않는다.

const OLD_PHONE = '010-1111-2222';
const NEW_PHONE = '010-9999-8888';

function makeUser(roles) {
  return {
    id: 'mypage-user',
    name: '마이페이지',
    email: 'mypage@example.test',
    phone: OLD_PHONE,
    birthDate: '1990-01-15',
    roles: roles.map((role) => ({ role })),
  };
}

async function openWithMockedApi(page, path, { roles, selectedRole }) {
  let user = makeUser(roles);
  const profileBodies = [];
  const notifyCalls = [];

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
    const request = route.request();
    const pathname = new URL(request.url()).pathname;
    let data = [];

    if (pathname.endsWith('/auth/session')) {
      data = { userId: user.id, roles };
    } else if (pathname.endsWith('/users/me/notification-preferences')) {
      notifyCalls.push(request.method());
    } else if (pathname.endsWith('/users/me') && request.method() === 'PUT') {
      const body = request.postDataJSON();
      profileBodies.push(body);
      user = { ...user, ...body };
      data = user;
    } else if (pathname.endsWith('/users/me')) {
      data = user;
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data, page: { totalElements: Array.isArray(data) ? data.length : 0 } }),
    });
  });

  await page.goto(path);
  return { profileBodies, notifyCalls };
}

async function expectNoNotifyPrefsAndSaveEdit(page, { profileBodies, notifyCalls }) {
  await expect(page.getByRole('button', { name: '회원 정보 수정 >' })).toBeVisible();
  await expect(page.getByText(/알림 및 (수신|광고) 설정/)).toHaveCount(0);
  await expect(page.getByText('이메일 알림', { exact: true })).toHaveCount(0);
  await expect(page.getByText('SMS 알림', { exact: true })).toHaveCount(0);

  await page.getByRole('button', { name: '회원 정보 수정 >' }).click();
  await page.getByLabel('휴대폰번호').fill(NEW_PHONE);
  await page.getByRole('button', { name: '저장', exact: true }).click();
  await expect(page.getByText('회원 정보를 저장했습니다.')).toBeVisible();
  expect(profileBodies.at(-1)?.phone).toBe(NEW_PHONE);
  await expect(page.getByText(NEW_PHONE)).toBeVisible();
  expect(notifyCalls).toEqual([]);
}

test('learner mypage has no notification settings and still saves edits', async ({ page }) => {
  const ctx = await openWithMockedApi(page, '/mypage', { roles: ['LEARNER'], selectedRole: 'LEARNER' });
  // 머리글 알림 벨도 뺐다 — 알림을 만드는 곳이 없어 늘 비어 있었다
  await expect(page.getByRole('button', { name: '알림', exact: true })).toHaveCount(0);
  await expectNoNotifyPrefsAndSaveEdit(page, ctx);
});

test('console mypage has no notification settings and still saves edits', async ({ page }) => {
  const ctx = await openWithMockedApi(page, '/instructor/mypage', {
    roles: ['INSTRUCTOR', 'LEARNER'],
    selectedRole: 'INSTRUCTOR',
  });
  await expectNoNotifyPrefsAndSaveEdit(page, ctx);
});
