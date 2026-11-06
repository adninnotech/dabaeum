import { expect, test } from '@playwright/test';

// 비로그인 공개 화면 — 강좌 목록·검색은 로그인 없이도 보여야 한다

test('홈 가운데 검색창으로 통합검색 결과로 간다', async ({ page }) => {
  await page.goto('/');
  const search = page.getByRole('search').first();
  await expect(search).toBeVisible();

  await search.getByRole('textbox', { name: '검색어' }).fill('교육');
  await search.getByRole('button', { name: '검색' }).click();

  await expect(page).toHaveURL(/\/search\?.*q=/);
  await expect(page.getByRole('heading', { name: /검색 결과/ })).toBeVisible();
});

test('검색 결과의 강좌를 누르면 강좌 상세가 열린다', async ({ page }) => {
  await page.goto('/search?q=%EA%B5%90%EC%9C%A1');
  await expect(page.getByRole('heading', { name: /검색 결과/ })).toBeVisible();

  const courseLinks = page.locator('a.search-row[href^="/courses/"]');
  const count = await courseLinks.count();
  test.skip(count === 0, 'dev 데이터에 "교육" 강좌가 없음');

  await courseLinks.first().click();
  await expect(page).toHaveURL(/\/courses\/[0-9a-f-]{36}/);
});

test('약관 공개 화면이 비로그인으로 열린다', async ({ page }) => {
  await page.goto('/policies/usage');
  const tabs = page.getByRole('navigation', { name: '약관 종류' });
  await expect(tabs).toBeVisible();
  // 하단 바닥글에도 같은 이름 링크가 있어 탭 안에서 찾는다
  await expect(tabs.getByRole('link', { name: '이용약관' })).toHaveAttribute(
    'aria-current',
    'page',
  );
});

test('공개 수료증 검증은 발급기관·발급일만 추가 공개하고 개인정보는 숨긴다', async ({ page }) => {
  await page.route('**/api/v1/credentials/verify', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        data: {
          id: '00000000-0000-4000-8000-000000000001',
          credentialId: '00000000-0000-4000-8000-000000000002',
          presentedCredentialNo: 'CERT-27D85BB1',
          verificationType: 'QR',
          requesterType: 'INDIVIDUAL',
          requesterId: null,
          result: 'VALID',
          verificationScope: 'PLATFORM',
          verifiedAt: '2026-10-21T00:00:00Z',
          createdAt: '2026-10-21T00:00:00Z',
          issuerName: '서울평생교육원',
          issuedAt: '2026-10-20T03:00:00Z',
          learnerName: '비공개 수강생',
          birthDate: '1990-01-01',
          contact: '010-1234-5678',
        },
        meta: { requestId: 'test', timestamp: '2026-10-21T00:00:00Z' },
      }),
    }),
  );

  await page.goto('/verify/CERT-27D85BB1');
  await expect(page.getByText('유효한 수료증입니다')).toBeVisible();
  await expect(page.getByText('서울평생교육원')).toBeVisible();
  await expect(page.getByText('2026.10.20')).toBeVisible();
  await expect(page.getByText(/비공개 수강생|1990-01-01|010-1234-5678/)).toHaveCount(0);
});

test('모바일 폭에서 홈이 가로로 넘치지 않는다', async ({ page, isMobile }) => {
  test.skip(!isMobile, '모바일 프로젝트에서만');
  await page.goto('/');
  await expect(page.getByRole('search').first()).toBeVisible();
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow).toBeLessThanOrEqual(0);
});
