import { expect, test } from '@playwright/test';

/* 홈 배너: PC 이미지만 있고 모바일 이미지가 없으면 폰·태블릿은 그림 배너 판 없이 기본 배너 모양이다(QA dabaeum-issues#49).
 * 판(여백 24·32px)이 붙으면 360px 에서 글 판이 배너보다 길어져 버튼이 잘렸다. PC 는 그림 배너 그대로.
 * 배너가 2장 이상이면 버튼이 1/2 표시에 가려지지 않아야 한다(#49 재확인 2차) */
const banner = {
  id: 'banner-1',
  title: '배움이 쌓이면 지갑에 남습니다',
  subtitle: '수료하면 디지털 수료증과 배지가 학습지갑에 바로 쌓입니다. 언제든 꺼내 보여 줄 수 있어요.',
  badgeText: '학습지갑',
  buttonText: '학습지갑 보기',
  buttonLink: '/wallet',
  pcImageFileId: 'file-pc',
  mobileImageFileId: null,
};

async function mockApi(page) {
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace(/^.*\/api\/v1/, '');
    if (path.startsWith('/files/')) {
      // 1×1 PNG
      const png = Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
        'base64',
      );
      await route.fulfill({ status: 200, contentType: 'image/png', body: png });
      return;
    }
    const data = path === '/banners' ? [banner, { ...banner, id: 'banner-2' }] : [];
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data, page: { totalElements: data.length, totalPages: 1 } }),
    });
  });
}

for (const width of [320, 360, 375, 412]) {
  test(`모바일 이미지 없는 배너는 ${width}px 에서 판 없이 버튼까지 배너 안에 보인다`, async ({ page }) => {
    await page.setViewportSize({ width, height: 800 });
    await mockApi(page);
    await page.goto('/');

    const section = page.locator('section.home-banner');
    await expect(section.getByRole('heading', { name: banner.title })).toBeVisible();
    await expect(section).not.toHaveClass(/home-banner--image/);

    const box = await section.boundingBox();
    const button = await section.getByRole('button', { name: banner.buttonText }).boundingBox();
    expect(button.y).toBeGreaterThanOrEqual(box.y);
    expect(button.y + button.height).toBeLessThanOrEqual(box.y + box.height);
    const indicator = await section.locator('.home-banner__indicator').boundingBox();
    expect(button.y + button.height).toBeLessThanOrEqual(indicator.y);
  });
}

test('PC 는 PC 이미지 그림 배너 그대로다', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await mockApi(page);
  await page.goto('/');

  const section = page.locator('section.home-banner');
  await expect(section.getByRole('heading', { name: banner.title })).toBeVisible();
  await expect(section).toHaveClass(/home-banner--image/);
});
