import { expect, test } from '@playwright/test';

// QA 중앙 #52 — 학생 회원 관리가 최신 100명만 받아 성별·수강·최근 접속을 그 안에서 걸렀다.
// 실제 수백 명인데 "총 14명". 필터·정렬·페이지를 GET /users 에 실어 서버가 거른다.
// 회원 구·군은 #142 에서 없어져 구·군 필터도 없다

const adminUser = {
  id: 'admin-user',
  name: '관리자',
  email: 'admin@example.test',
  roles: [{ role: 'PLATFORM_ADMIN' }],
};

const TOTALS = { all: 2031, ACTIVE: 2000, DORMANT: 12, recent: 45, female: 1180 };

function learner(index) {
  return {
    id: `learner-${index}`,
    name: `학생${index}`,
    email: `learner${index}@example.test`,
    status: 'ACTIVE',
    roles: [{ role: 'LEARNER' }],
    createdAt: '2026-10-01T00:00:00Z',
    enrollmentCount: 1,
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
      if (params.gender === 'FEMALE') totalElements = TOTALS.female;
      else if (params.status) totalElements = TOTALS[params.status] ?? 0;
      else if (params.createdFrom) totalElements = TOTALS.recent;
      else totalElements = TOTALS.all;
      if (size > 1) {
        listQueries.push(params);
        data = Array.from({ length: size }, (_, index) =>
          learner(Number(params.page) * size + index + 1),
        );
      } else {
        data = [learner(0)];
      }
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data, page: { totalElements } }),
    });
  });

  await page.goto('/admin/students');
  return listQueries;
}

test('a filter is sent to the server and the total is the server total', async ({ page }) => {
  const listQueries = await openWithMockedApi(page);
  await expect(page.getByText(`총 ${TOTALS.all}명`)).toBeVisible();

  // 요약 카드는 받은 행이 아니라 서버 총계 — '(최근 N건 기준)' 꼬리표가 없다
  await expect(page.getByText('최근 100건 기준')).toHaveCount(0);
  const main = page.getByRole('main');
  await expect(main).toContainText(/전체 회원\s*2031\s*명/);
  await expect(main).toContainText(/최근 가입\(30일\)\s*45\s*명/);
  await expect(main).toContainText(/비활성 회원\s*12\s*명/);

  await expect(page.getByRole('combobox', { name: '구·군' })).toHaveCount(0);
  await page.getByRole('combobox', { name: '성별' }).click();
  await page.getByRole('option', { name: '여성' }).click();
  await page.getByRole('button', { name: '검색', exact: true }).click();

  await expect(page.getByText(`총 ${TOTALS.female}명`)).toBeVisible();
  const last = listQueries.at(-1);
  expect(last).toMatchObject({
    role: 'LEARNER',
    gender: 'FEMALE',
    page: '0',
    sort: 'createdAt,desc',
  });
  expect(last).not.toHaveProperty('regionCode');
});

test('paging and sorting are requested from the server', async ({ page }) => {
  const listQueries = await openWithMockedApi(page);
  await expect(page.getByText(`총 ${TOTALS.all}명`)).toBeVisible();
  const initialSize = listQueries.at(-1).size;

  await page.getByRole('button', { name: '2', exact: true }).click();
  await expect.poll(() => listQueries.at(-1)?.page).toBe('1');
  await expect(page.getByText(`학생${Number(initialSize) + 1}`, { exact: true })).toBeVisible();

  await page
    .getByRole('button', { name: /최근 로그인/ })
    .first()
    .click();
  await expect.poll(() => listQueries.at(-1)?.sort).toMatch(/^lastLoginAt,(asc|desc)$/);
  expect(listQueries.at(-1).page).toBe('0');
});

test('enrollment, login and signup period filters map to server params', async ({ page }) => {
  const listQueries = await openWithMockedApi(page);
  await expect(page.getByText(`총 ${TOTALS.all}명`)).toBeVisible();

  await page.getByRole('combobox', { name: '수강' }).click();
  await page.getByRole('option', { name: '수강 없음' }).click();
  await page.getByRole('combobox', { name: '최근 접속' }).click();
  await page.getByRole('option', { name: '90일 넘게 없음' }).click();
  await page.getByRole('combobox', { name: '성별' }).click();
  await page.getByRole('option', { name: '여성' }).click();
  await page.getByRole('button', { name: '검색', exact: true }).click();

  await expect
    .poll(() => listQueries.at(-1))
    .toMatchObject({ hasEnrollment: 'false', lastLogin: 'OVER_90', gender: 'FEMALE' });

  await page.getByRole('button', { name: '7일', exact: true }).click();
  await expect.poll(() => listQueries.at(-1)?.createdFrom).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  expect(listQueries.at(-1).createdTo).toMatch(/^\d{4}-\d{2}-\d{2}$/);
});
