import { expect, test } from '@playwright/test';

// 기관 강사 관리 > 소속 강사 — 오른쪽 끝 작은 [수정] 글자를 눌러야만 창이 열렸다. 행(폰은 카드) 어디를 눌러도 연다.
// 창도 누구 메모인지(이름)가 연락처와 같은 크기 줄에 묻혀 있어, 이름을 제목으로 정보는 옅은 상자로 묶는다.

const admin = {
  id: 'inst-admin',
  name: '기관관리자',
  email: 'inst@example.test',
  roles: [{ role: 'INSTITUTION_ADMIN', institutionId: 'inst-1' }],
};

const instructor = {
  userId: 'teacher-1',
  name: '기관1_강사1',
  email: 'teacher1@example.com',
  phone: '010-1234-1234',
  birthDate: '1985-11-20',
  memo: '주말 강의 가능',
  status: 'ACTIVE',
};

async function openRoster(page) {
  const patches = [];
  await page.addInitScript((user) => {
    // Quasar LocalStorage 는 객체를 '__q_objt|' 접두사로 저장한다
    localStorage.setItem(
      'dabaeum.auth',
      `__q_objt|${JSON.stringify({ token: 'e2e-token', user, activeUserId: user.id, selectedRole: 'INSTITUTION_ADMIN' })}`,
    );
  }, admin);
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const { pathname } = new URL(request.url());
    let data = [];
    if (pathname.endsWith('/auth/session')) data = { userId: admin.id, roles: ['INSTITUTION_ADMIN'] };
    else if (pathname.endsWith('/users/me/institutions')) data = [{ institutionId: 'inst-1', name: '에이디엔기관1', role: 'INSTITUTION_ADMIN' }];
    else if (pathname.endsWith('/users/me')) data = admin;
    else if (pathname.endsWith(`/institutions/inst-1/instructors/${instructor.userId}`)) {
      patches.push({ method: request.method(), body: request.postDataJSON() });
      data = { ...instructor, ...request.postDataJSON() };
    } else if (pathname.endsWith('/institutions/inst-1/instructors')) data = [instructor];
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data, page: { totalElements: Array.isArray(data) ? data.length : 0 } }),
    });
  });
  await page.goto('/institution/instructors?tab=roster');
  return patches;
}

/** 이름(단추) — 표·폰 카드 모두 이것으로 창을 연다 */
function nameButton(page) {
  return page.getByRole('button', { name: new RegExp(instructor.name) }).first();
}

test('clicking anywhere on a row opens the edit dialog', async ({ page }) => {
  await openRoster(page);
  // 이름·[수정]이 아닌 이메일 글자를 눌러도 열린다(폰은 카드의 이메일 줄)
  await page.getByText(instructor.email).first().click();
  await expect(page.getByRole('dialog').getByRole('heading', { name: instructor.name })).toBeVisible();
});

test('the row opens from the keyboard and shows it is clickable', async ({ page }) => {
  await openRoster(page);
  const name = nameButton(page);
  await expect(name).toHaveCSS('cursor', 'pointer');
  await name.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('dialog').getByRole('heading', { name: instructor.name })).toBeVisible();
});

test('the edit dialog names the instructor and separates read-only info from the memo', async ({ page }) => {
  await openRoster(page);
  await nameButton(page).click();

  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('heading', { name: instructor.name })).toBeVisible();
  await expect(dialog).toContainText(instructor.email);

  const info = dialog.getByRole('region', { name: '강사 정보(읽기 전용)' });
  await expect(info).toContainText(instructor.phone);
  await expect(info).toContainText(instructor.birthDate);
  await expect(info).toContainText('플랫폼 관리자가 바꿉니다');

  await expect(dialog.getByRole('textbox', { name: '기관 메모' })).toHaveValue(instructor.memo);

  await dialog.getByRole('button', { name: '닫기' }).first().click();
  await expect(dialog).toBeHidden();
});

test('saving the memo sends it and closes the dialog', async ({ page }) => {
  const patches = await openRoster(page);
  await nameButton(page).click();

  const dialog = page.getByRole('dialog');
  await dialog.getByRole('textbox', { name: '기관 메모' }).fill('평일 저녁 강의 가능');
  await dialog.getByRole('button', { name: '저장' }).click();

  await expect(dialog).toBeHidden();
  expect(patches).toEqual([{ method: 'PATCH', body: { memo: '평일 저녁 강의 가능' } }]);
});
