import { expect, test } from '@playwright/test';

// 강사 문의 관리 — 줄 오른쪽 '답변대기' 상태 뱃지(28px)와 [답변]·[보기] 버튼 높이가 달라 들쭉날쭉했다.

const instructor = {
  id: 'inquiry-instructor',
  name: '문의강사',
  email: 'inquiry@example.test',
  roles: [{ role: 'INSTRUCTOR', institutionId: 'inst-1' }],
};

const inquiries = [
  { id: 'q-open', title: '수업 자료 문의', status: 'OPEN', courseTitle: '파이썬 입문', userName: '학생1', createdAt: '2026-10-20T01:00:00Z' },
  { id: 'q-done', title: '출결 문의', status: 'ANSWERED', courseTitle: '파이썬 입문', userName: '학생2', createdAt: '2026-10-19T01:00:00Z', answeredAt: '2026-10-19T02:00:00Z' },
];

async function openInquiries(page) {
  await page.addInitScript((user) => {
    // Quasar LocalStorage 는 객체를 '__q_objt|' 접두사로 저장한다
    localStorage.setItem(
      'dabaeum.auth',
      `__q_objt|${JSON.stringify({ token: 'e2e-token', user, activeUserId: user.id, selectedRole: 'INSTRUCTOR' })}`,
    );
  }, instructor);
  await page.route('**/api/v1/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    let data = [];
    if (pathname.endsWith('/auth/session')) data = { userId: instructor.id, roles: ['INSTRUCTOR'] };
    else if (pathname.endsWith('/users/me')) data = instructor;
    else if (pathname.endsWith('/instructors/me/inquiries')) data = inquiries;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data, page: { totalElements: Array.isArray(data) ? data.length : 0 } }),
    });
  });

  await page.goto('/instructor/inquiries');
}

test('status badge and action button in an inquiry row share one height', async ({ page }) => {
  test.skip(test.info().project.name === 'mobile', '폰에선 뱃지가 정보 줄로 내려간다');
  await openInquiries(page);
  await expectSameHeight(page.locator('.inquiry-side').first(), '답변');

  // 답변한 문의의 [보기]도 같은 높이 — '전체'에서 보인다
  await page.getByRole('button', { name: /^전체/ }).click();
  await expectSameHeight(page.locator('.inquiry-side').nth(1), '보기');
});

async function expectSameHeight(side, buttonName) {
  const button = side.getByRole('button', { name: buttonName });
  await expect(button).toBeVisible();
  const badgeBox = await side.locator('.status-badge').boundingBox();
  const buttonBox = await button.boundingBox();
  expect(Math.round(buttonBox.height)).toBe(Math.round(badgeBox.height));
  // 세로 가운데도 맞는다
  expect(Math.abs(badgeBox.y + badgeBox.height / 2 - (buttonBox.y + buttonBox.height / 2))).toBeLessThan(1);
}

test('only the reply button is a solid fill — status badges are soft', async ({ page }) => {
  test.skip(test.info().project.name === 'mobile', '폰에선 뱃지가 정보 줄로 내려간다');
  await openInquiries(page);
  await page.getByRole('button', { name: /^전체/ }).click();
  const badges = page.locator('.inquiry-side .status-badge');
  await expect(badges).toHaveCount(2);
  // 흰 글자 = 진한 바탕. 상태 뱃지는 연한 바탕에 색 글자
  for (const badge of await badges.all()) {
    await expect(badge).toHaveClass(/status-badge--soft/);
    await expect(badge).not.toHaveCSS('color', 'rgb(255, 255, 255)');
  }
  await expect(page.getByRole('button', { name: '답변', exact: true })).toHaveCSS('color', 'rgb(255, 255, 255)');
});

