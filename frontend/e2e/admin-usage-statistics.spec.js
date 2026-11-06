import { expect, test } from '@playwright/test';

/* 관리자 이용 통계(백엔드 usage-statistics.md §6·§8) + 공개 화면 기록 2개(방문 POST /visits, 배너 클릭 keepalive fetch).
 * 첫 조회는 기간 없이 불러 서버 기본 기간·마지막 집계일을 받는다 */

const adminUser = {
  id: 'admin-user',
  name: '관리자',
  email: 'admin@example.test',
  roles: [{ role: 'PLATFORM_ADMIN' }],
};

const LAST_DAY = '2026-10-29';
const zero = { visitors: 0, searches: 0, courseViews: 0, enrollments: 0, bannerClicks: 0 };

function usageBody(query) {
  const from = query.from || '2026-09-30';
  const to = query.to || LAST_DAY;
  const monthly = query.interval === 'MONTH';
  return {
    from,
    to,
    interval: query.interval || 'DAY',
    totals: { visitors: 1200, searches: 300, courseViews: 800, enrollments: 40, bannerClicks: 25 },
    previousFrom: '2026-08-31',
    previousTo: '2026-09-29',
    previousTotals: {
      visitors: 1000,
      searches: 300,
      courseViews: 900,
      enrollments: 0,
      bannerClicks: 10,
    },
    rows: monthly
      ? [
          { period: '2026-09', ...zero, visitors: 30 },
          { period: '2026-10', ...zero, visitors: 1170 },
        ]
      : [
          { period: '2026-10-27', ...zero, visitors: 40, searches: 10 },
          { period: '2026-10-28', ...zero },
          { period: '2026-10-29', ...zero, visitors: 55, enrollments: 3 },
        ],
  };
}

function demographicsBody(metric) {
  const genders = [
    { key: 'MALE', label: '남성', count: metric === 'SEARCH' ? 7 : 400 },
    { key: 'FEMALE', label: '여성', count: 500 },
    { key: 'UNDISCLOSED', label: '밝히지 않음', count: 0 },
    { key: 'UNKNOWN', label: '미상', count: 0 },
  ];
  const ageBands = ['10', '20', '30', '40', '50', '60', '70', 'UNKNOWN'].map((key) => ({
    key,
    label: key === 'UNKNOWN' ? '미상' : `${key}대`,
    count: 10,
  }));
  return { metric, total: 1200, members: 900, guests: 300, genders, ageBands };
}

async function openWithMockedApi(page, { usageStatus = 200 } = {}) {
  const calls = [];
  await page.addInitScript((user) => {
    localStorage.setItem(
      'dabaeum.auth',
      `__q_objt|${JSON.stringify({ token: 'e2e-token', user, activeUserId: user.id, selectedRole: 'PLATFORM_ADMIN' })}`,
    );
  }, adminUser);

  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace(/^.*\/api\/v1/, '');
    const query = Object.fromEntries(url.searchParams.entries());
    calls.push({ method: route.request().method(), path, query });
    let data = [];
    if (path === '/auth/session') data = { userId: adminUser.id, roles: ['PLATFORM_ADMIN'] };
    else if (path === '/users/me') data = adminUser;
    else if (path === '/admin/statistics/usage') {
      if (usageStatus !== 200) {
        await route.fulfill({
          status: usageStatus,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 'VALIDATION_ERROR',
            message: 'to is after the last aggregated day',
          }),
        });
        return;
      }
      if (query.format === 'csv') {
        await route.fulfill({
          status: 200,
          contentType: 'text/csv',
          headers: { 'Content-Disposition': "attachment; filename*=UTF-8''usage.csv" },
          body: '﻿period,visitors\n2026-10-29,55\n',
        });
        return;
      }
      data = usageBody(query);
    } else if (path === '/admin/statistics/usage/demographics')
      data = demographicsBody(query.metric);
    else if (path === '/admin/statistics/usage/search-keywords') {
      data = {
        type: query.type,
        rows:
          query.type === 'POPULAR'
            ? [
                { keyword: '엑셀', searchers: 12 },
                { keyword: '요가', searchers: 5 },
              ]
            : [],
      };
    } else if (path === '/admin/statistics/usage/banners') {
      data = {
        rows: [
          { bannerId: 'b1', title: '가을 학기 모집', deleted: false, clickers: 20 },
          { bannerId: 'b2', title: '여름 특강', deleted: true, clickers: 5 },
        ],
      };
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data }),
    });
  });

  await page.goto('/admin/statistics/usage');
  return calls;
}

const usageCalls = (calls) =>
  calls.filter((c) => c.path === '/admin/statistics/usage' && c.query.format !== 'csv');

test('first load uses the server default period and shows summary with previous-period change', async ({
  page,
}) => {
  const calls = await openWithMockedApi(page);
  await expect(page.getByRole('heading', { name: '이용 통계', level: 1 })).toBeVisible();

  // 첫 요청은 기간 없이 — 서버가 마지막 집계일까지 30일을 정한다
  await expect.poll(() => usageCalls(calls).length).toBeGreaterThan(0);
  expect(usageCalls(calls)[0].query).toEqual({ interval: 'DAY' });
  await expect(page.getByLabel('종료일')).toHaveValue(LAST_DAY);
  await expect(page.getByLabel('시작일')).toHaveValue('2026-09-30');

  const tiles = page.locator('.usage__tile');
  // 기간 합계는 날마다 센 인원을 더한 값이라 '연인원'
  await expect(tiles.nth(0)).toContainText('방문 연인원');
  await expect(tiles.nth(0)).toContainText('1,200명');
  await expect(tiles.nth(0)).toContainText('앞 기간보다 ↑ 200명 (+20.0%)');
  await expect(tiles.nth(1)).toContainText('앞 기간과 같음');
  await expect(tiles.nth(2)).toContainText('앞 기간보다 ↓ 100명 (-11.1%)');
  // 앞 기간 0 이면 비율 없이 차이만, 수강 신청은 '건'
  await expect(tiles.nth(3)).toContainText('40건');
  await expect(tiles.nth(3)).toContainText('앞 기간보다 ↑ 40건');
  await expect(tiles.nth(3)).not.toContainText('%');

  await expect(
    page.getByText('어제까지 집계합니다. 같은 사람의 같은 날 행동은 한 번으로 셉니다.'),
  ).toBeVisible();
  await expect(
    page.getByText('연인원은 날마다 센 인원을 더한 값으로, 실제 회원 수와 다릅니다.', { exact: false }),
  ).toBeVisible();
  await expect(page.getByText('전환율')).toHaveCount(0);
});

test('demographics, keywords and banners are shown with the required notices', async ({ page }) => {
  const calls = await openWithMockedApi(page);
  await expect(page.locator('.usage-demo').getByText(/연인원\s*1,200명/)).toBeVisible();
  await expect(page.getByText(/비회원\s*300명/)).toBeVisible();

  await page.getByRole('button', { name: '검색', exact: true }).click();
  await expect
    .poll(() => calls.filter((c) => c.path.endsWith('/demographics')).at(-1)?.query.metric)
    .toBe('SEARCH');

  await expect(
    page.getByText('하루에 3명 이상이 검색한 검색어만 보입니다', { exact: false }),
  ).toBeVisible();
  await expect(page.getByRole('row', { name: /엑셀/ })).toContainText('12');
  await expect(page.getByRole('columnheader', { name: '검색 연인원' })).toBeVisible();
  await expect(page.getByText('결과가 0건이었던 검색어가 없습니다.')).toBeVisible();
  const keywordQueries = calls
    .filter((c) => c.path.endsWith('/search-keywords'))
    .map((c) => c.query);
  expect(keywordQueries).toEqual(
    expect.arrayContaining([
      expect.objectContaining({ type: 'POPULAR', limit: '20', from: '2026-09-30', to: LAST_DAY }),
      expect.objectContaining({ type: 'ZERO_RESULT', limit: '20' }),
    ]),
  );

  await expect(page.getByRole('row', { name: /여름 특강/ })).toContainText('(삭제됨)');
  await expect(page.getByRole('row', { name: /가을 학기 모집/ })).not.toContainText('(삭제됨)');
});

test('daily range over 366 days is blocked and monthly view is suggested', async ({ page }) => {
  const calls = await openWithMockedApi(page);
  await expect(page.getByLabel('종료일')).toHaveValue(LAST_DAY);

  await page.getByRole('button', { name: '최근 1년' }).click();
  await expect.poll(() => usageCalls(calls).at(-1)?.query.from).toBe('2025-10-30');

  await page.getByLabel('시작일').fill('2024-01-01');
  await page.getByRole('button', { name: '조회', exact: true }).click();
  await expect(
    page.getByText('일별은 최대 366일입니다. 더 긴 기간은 월별로 봐 주세요.'),
  ).toBeVisible();

  await page.getByRole('button', { name: '월별' }).click();
  await page.getByRole('button', { name: '조회', exact: true }).click();
  await expect
    .poll(() => usageCalls(calls).at(-1)?.query)
    .toMatchObject({ from: '2024-01-01', interval: 'MONTH' });
  // 월별은 날마다 센 값을 더한 값 — 범례도 연인원
  await expect(page.locator('.trend__legend')).toContainText('방문 연인원');
});

test('CSV downloads with the applied period and interval', async ({ page }) => {
  const calls = await openWithMockedApi(page);
  await expect(page.getByLabel('종료일')).toHaveValue(LAST_DAY);
  await page.getByRole('button', { name: '월별' }).click();

  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'CSV 내려받기' }).click();
  expect((await download).suggestedFilename()).toBe('usage.csv');
  const csv = calls.find((c) => c.query.format === 'csv');
  expect(csv.query).toMatchObject({ from: '2026-09-30', to: LAST_DAY, interval: 'MONTH' });
});

test('server error is shown in Korean without breaking the page', async ({ page }) => {
  await openWithMockedApi(page, { usageStatus: 422 });
  await expect(page.getByRole('heading', { name: '이용 통계', level: 1 })).toBeVisible();
  await expect(page.locator('.usage__tiles')).toHaveCount(0);
  await expect(page.getByRole('alert').first()).toBeVisible();
});

for (const width of [320, 390]) {
  test(`usage page has no horizontal overflow at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 800 });
    await openWithMockedApi(page);
    await expect(page.locator('.usage__tile').first()).toContainText('1,200');
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - window.innerWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
  });
}

test('menu shows usage statistics under 통계 next to enrollment statistics', async ({
  page,
}, testInfo) => {
  // 폰은 사이드바가 서랍이라 열기 전에는 메뉴가 그려지지 않는다
  test.skip(testInfo.project.name === 'mobile', '사이드바는 PC 에서 확인');
  await openWithMockedApi(page);
  await expect(page.getByRole('link', { name: '수강·배지 통계', exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: '이용 통계', exact: true })).toBeVisible();
});

test.describe('public recording', () => {
  test('visit is recorded once per tab and banner click is sent without a token when logged out', async ({ page }) => {
    const posts = [];
    const clickAuth = [];
    await page.route('**/api/v1/**', async (route) => {
      const request = route.request();
      const path = new URL(request.url()).pathname.replace(/^.*\/api\/v1/, '');
      if (request.method() === 'POST') posts.push(path);
      if (path.endsWith('/clicks')) clickAuth.push(request.headers().authorization || null);
      const data =
        path === '/banners'
          ? [
              {
                id: 'banner-9',
                title: '가을 학기',
                buttonText: '보러 가기',
                buttonLink: '/courses',
              },
            ]
          : request.method() === 'POST'
            ? { accepted: true }
            : [];
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data, page: { totalElements: 0, totalPages: 1 } }),
      });
    });

    await page.goto('/');
    await expect.poll(() => posts.filter((p) => p === '/visits').length).toBe(1);
    await page.reload();
    await expect(
      page.locator('section.home-banner').getByRole('button', { name: '보러 가기' }),
    ).toBeVisible();
    expect(posts.filter((p) => p === '/visits').length).toBe(1);

    await page.locator('section.home-banner').getByRole('button', { name: '보러 가기' }).click();
    await expect(page).toHaveURL(/\/courses/);
    await expect.poll(() => posts.includes('/banners/banner-9/clicks')).toBe(true);
    expect(clickAuth).toEqual([null]);
  });

  test('banner click carries the token when logged in', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem(
        'dabaeum.auth',
        `__q_objt|${JSON.stringify({ token: 'e2e-token', user: { id: 'u1', name: '학생', roles: [{ role: 'LEARNER' }] }, activeUserId: 'u1', selectedRole: 'LEARNER' })}`,
      );
    });
    const clicks = [];
    await page.route('**/api/v1/**', async (route) => {
      const request = route.request();
      const path = new URL(request.url()).pathname.replace(/^.*\/api\/v1/, '');
      if (path.endsWith('/clicks')) clicks.push(request.headers().authorization || null);
      const data =
        path === '/banners'
          ? [{ id: 'banner-9', title: '가을 학기', buttonText: '보러 가기', buttonLink: '/courses' }]
          : path === '/users/me'
            ? { id: 'u1', name: '학생', roles: [{ role: 'LEARNER' }] }
            : [];
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data, page: { totalElements: 0, totalPages: 1 } }),
      });
    });
    await page.goto('/');
    await page.locator('section.home-banner').getByRole('button', { name: '보러 가기' }).click();
    await expect(page).toHaveURL(/\/courses/);
    await expect.poll(() => clicks).toEqual(['Bearer e2e-token']);
  });

  test('expired token on the visit call retries once without token and does not send the user to login', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem(
        'dabaeum.auth',
        `__q_objt|${JSON.stringify({ token: 'expired', user: { id: 'u1', name: '학생', roles: [{ role: 'LEARNER' }] }, activeUserId: 'u1', selectedRole: 'LEARNER' })}`,
      );
    });
    const visitAuth = [];
    await page.route('**/api/v1/**', async (route) => {
      const path = new URL(route.request().url()).pathname.replace(/^.*\/api\/v1/, '');
      if (path === '/visits') {
        const auth = route.request().headers().authorization || null;
        visitAuth.push(auth);
        await route.fulfill(
          auth
            ? { status: 401, contentType: 'application/json', body: '{"code":"AUTHENTICATION_FAILED"}' }
            : { status: 200, contentType: 'application/json', body: '{"data":{"accepted":true}}' },
        );
        return;
      }
      const data = path === '/users/me' ? { id: 'u1', name: '학생', roles: [{ role: 'LEARNER' }] } : [];
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data, page: { totalElements: 0, totalPages: 1 } }),
      });
    });
    await page.goto('/');
    // 401 이면 토큰 없이 한 번만 다시 보내 비회원으로 남긴다
    await expect.poll(() => visitAuth).toEqual(['Bearer expired', null]);
    await expect(page.locator('section.home-banner')).toBeVisible();
    await expect(page).not.toHaveURL(/\/login/);
  });

  test('visit failure does not affect the page', async ({ page }) => {
    await page.route('**/api/v1/**', async (route) => {
      const path = new URL(route.request().url()).pathname.replace(/^.*\/api\/v1/, '');
      if (path === '/visits') {
        await route.fulfill({ status: 500, body: '' });
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: [], page: { totalElements: 0, totalPages: 1 } }),
      });
    });
    await page.goto('/');
    await expect(page.locator('section.home-banner')).toBeVisible();
    await expect(page.getByRole('alert')).toHaveCount(0);
  });
});
