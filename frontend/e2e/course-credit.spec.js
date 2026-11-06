import { expect, test } from '@playwright/test';

test('강좌 상세에 학점은행 인정 여부와 학점을 표시한다', async ({ page }) => {
  const id = '2d7a1ee3-0000-4000-8000-000000000001';
  const course = {
    id,
    institutionId: '1ca6d1e2-5398-4188-bfe6-e08da954b53d',
    courseCode: 'C-DONE',
    title: '학점은행 과정',
    educationType: 'ONLINE',
    creditBankEligible: true,
    creditValue: 3,
    status: 'COMPLETED',
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
    enrolledCount: 0,
  };

  await page.route('**/api/v1/**', async (route) => {
    if (
      route.request().method() === 'GET' &&
      new URL(route.request().url()).pathname.endsWith(`/courses/${id}`)
    ) {
      await route.fulfill({ json: { data: course, meta: {} } });
      return;
    }
    await route.fulfill({ status: 404, json: { message: 'Not found' } });
  });

  await page.goto(`/courses/${id}`);
  await expect(page.getByRole('heading', { name: '학점은행 과정' })).toBeVisible();
  await expect(page.locator('.detail-meta__row:has(dt:text-is("학점은행")) dd')).toHaveText('인정');
  await expect(page.locator('.detail-meta__row:has(dt:text-is("학점")) dd')).toHaveText('3학점');

  course.courseCode = 'C-BASIC';
  course.title = '일반 과정';
  course.creditBankEligible = false;
  course.creditValue = null;
  await page.reload();
  await expect(page.getByRole('heading', { name: '일반 과정' })).toBeVisible();
  await expect(page.locator('.detail-meta__row:has(dt:text-is("학점은행")) dd')).toHaveText(
    '해당 없음',
  );
  await expect(page.locator('.detail-meta__row:has(dt:text-is("학점")) dd')).toHaveText('없음');
});
