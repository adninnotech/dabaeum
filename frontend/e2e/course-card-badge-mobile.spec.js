import { expect, test } from '@playwright/test';

/* 강좌 찾기 폰 폭: 사진이 96px 로 줄면 마감 임박 배지 '모집중 · 3일 남음' 이 사진보다 넓어 잘리고 사진을 덮었다(#110).
 * 폰에선 '3일 남음' 만 사진 안에, PC 는 앞말까지 그대로 */
function daysLater(days) {
  const d = new Date(Date.now() + 9 * 3600 * 1000 + days * 86400 * 1000);
  return d.toISOString().slice(0, 10);
}

const course = {
  id: 'course-1',
  title: 'QA R3 정원1 1023',
  institutionName: '다배움 기관',
  status: 'RECRUITING',
  educationType: 'OFFLINE',
  recruitStartDate: daysLater(-3),
  recruitEndDate: daysLater(3),
  thumbnailUrl:
    'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
};

async function mockApi(page) {
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace(/^.*\/api\/v1/, '');
    const data = path === '/courses' ? [course] : [];
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data, page: { totalElements: data.length, totalPages: 1 } }),
    });
  });
}

function cardOf(page) {
  return page.locator('.course-card').filter({ hasText: course.title }).first();
}

for (const width of [320, 390]) {
  test(`${width}px 에서 마감 임박 배지가 사진 안에 들어간다`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 });
    await mockApi(page);
    await page.goto('/courses');

    const card = cardOf(page);
    const badge = card.locator('.course-card__badge');
    await expect(badge).toBeVisible();
    await expect(badge).toHaveText('3일 남음', { useInnerText: true });
    await expect(badge).toHaveAttribute('aria-label', '모집중 · 3일 남음');

    const thumb = await card.locator('.course-card__thumb').boundingBox();
    const box = await badge.boundingBox();
    expect(box.x + box.width).toBeLessThanOrEqual(thumb.x + thumb.width);
    expect(box.y + box.height).toBeLessThanOrEqual(thumb.y + thumb.height);
  });
}

test('PC 는 마감 임박 배지에 모집중 앞말까지 보인다', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await mockApi(page);
  await page.goto('/courses');

  await expect(cardOf(page).locator('.course-card__badge')).toHaveText(/모집중\s*·\s*3일 남음/, { useInnerText: true });
});
