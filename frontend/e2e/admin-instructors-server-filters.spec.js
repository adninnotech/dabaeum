import { expect, test } from '@playwright/test';

// 강사 관리도 학생(QA 중앙 #52)처럼 최신 100명만 받아 상태를 그 안에서 걸렀다(지역 필터는 #142 에서 뺐다).
// 강사가 100명을 넘으면 숫자·목록이 모자란다. 필터·페이지를 GET /users 에 실어 서버가 거른다.

const adminUser = {
  id: 'admin-user',
  name: '관리자',
  email: 'admin@example.test',
  roles: [{ role: 'PLATFORM_ADMIN' }],
};

const TOTALS = { all: 312, ACTIVE: 290, DORMANT: 15, WITHDRAWN: 7 };

function instructor(index) {
  return {
    id: `instructor-${index}`,
    name: `강사${index}`,
    email: `instructor${index}@example.test`,
    status: 'ACTIVE',
    roles: [{ role: 'INSTRUCTOR', institutionName: '달서평생학습관' }],
    createdAt: '2026-10-01T00:00:00Z',
  };
}

/** 목록 요청(size>1)의 query 를 모아 둔다. size=1 은 요약 카드 집계 */
async function openWithMockedApi(page) {
  const listQueries = [];
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
    const url = new URL(route.request().url());
    const { pathname, searchParams } = url;
    let data = [];
    let totalElements = 0;

    if (pathname.endsWith('/auth/session')) {
      data = { userId: adminUser.id, roles: ['PLATFORM_ADMIN'] };
    } else if (pathname.endsWith('/users/me')) {
      data = adminUser;
    } else if (pathname.endsWith('/users')) {
      const params = Object.fromEntries(searchParams.entries());
      const size = Number(params.size);
      if (params.status) totalElements = TOTALS[params.status] ?? 0;
      else totalElements = TOTALS.all;
      if (size > 1) {
        listQueries.push(params);
        data = Array.from({ length: size }, (_, index) =>
          instructor(Number(params.page) * size + index + 1),
        );
      } else {
        data = [instructor(0)];
      }
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data, page: { totalElements } }),
    });
  });

  await page.goto('/admin/instructors');
  return listQueries;
}

test('instructor summary cards are server totals, not the received rows', async ({ page }) => {
  await openWithMockedApi(page);
  await expect(page.getByText(`총 ${TOTALS.all}명`)).toBeVisible();

  await expect(page.getByText(/최근 \d+건 기준/)).toHaveCount(0);
  const main = page.getByRole('main');
  await expect(main).toContainText(/전체 강사 수\s*312\s*명/);
  await expect(main).toContainText(/활성 강사\s*290\s*명/);
  await expect(main).toContainText(/비활성 강사\s*15\s*명/);
  await expect(main).toContainText(/탈퇴 강사\s*7\s*명/);
});

test('instructor status filter and paging are requested from the server', async ({ page }) => {
  const listQueries = await openWithMockedApi(page);
  await expect(page.getByText(`총 ${TOTALS.all}명`)).toBeVisible();
  const initialSize = listQueries.at(-1).size;
  expect(listQueries.at(-1)).toMatchObject({ role: 'INSTRUCTOR', page: '0' });

  await page.getByRole('button', { name: '2', exact: true }).click();
  await expect.poll(() => listQueries.at(-1)?.page).toBe('1');
  await expect(page.getByText(`강사${Number(initialSize) + 1}`, { exact: true })).toBeVisible();

  await expect(page.getByRole('combobox', { name: '구·군' })).toHaveCount(0);
  await page.getByRole('combobox', { name: '상태' }).click();
  await page.getByRole('option', { name: '비활성' }).click();
  await page.getByRole('button', { name: '검색', exact: true }).click();

  await expect(page.getByText(`총 ${TOTALS.DORMANT}명`)).toBeVisible();
  expect(listQueries.at(-1)).toMatchObject({ role: 'INSTRUCTOR', status: 'DORMANT', page: '0' });
  expect(listQueries.at(-1)).not.toHaveProperty('regionCode');
});
