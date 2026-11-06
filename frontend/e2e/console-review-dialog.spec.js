import { expect, test } from '@playwright/test';

// 강사 수강평 관리 — 수강평 줄을 눌러도 아무 일이 없었다. 누르면 수강평 상세 + 그 강의 별점 요약 창을 띄우고,
// 누를 수 있다는 걸 hover 로 알린다.

const instructor = {
  id: 'review-instructor',
  name: '수강평강사',
  email: 'review@example.test',
  roles: [{ role: 'INSTRUCTOR', institutionId: 'inst-1' }],
};

const course = { id: 'course-1', title: '파이썬 입문', status: 'OPEN' };

const reviews = [
  {
    id: 'review-1',
    courseId: course.id,
    rating: 4,
    content: '강의 자료가 알차고 설명이 친절했습니다.',
    createdAt: '2026-10-20T01:00:00Z',
    userName: '학생1',
    courseTitle: course.title,
  },
];

const summary = {
  totalCount: 12,
  averageRating: 4.3,
  distribution: { star5: 7, star4: 3, star3: 1, star2: 1, star1: 0 },
};

async function openReviews(page) {
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
    else if (pathname.endsWith('/instructors/me/courses')) data = [course];
    else if (pathname.endsWith(`/courses/${course.id}/reviews/summary`)) data = summary;
    else if (pathname.endsWith(`/courses/${course.id}/reviews`)) data = reviews;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data, page: { totalElements: Array.isArray(data) ? data.length : 0 } }),
    });
  });
  await page.goto('/instructor/reviews');
}

test('clicking a review opens its detail with the course rating summary', async ({ page }) => {
  await openReviews(page);
  await page.getByText(reviews[0].content).click();

  const dialog = page.getByRole('dialog');
  await expect(dialog).toContainText(course.title);
  await expect(dialog).toContainText('학생1');
  await expect(dialog).toContainText('4점');
  await expect(dialog).toContainText(reviews[0].content);
  await expect(dialog).toContainText(/평균\s*4\.3/);
  await expect(dialog).toContainText('12건');
  await expect(dialog.getByRole('meter', { name: '5점 7건' })).toBeVisible();

  await dialog.getByRole('link', { name: '강의 보기' }).click();
  await expect(page).toHaveURL(new RegExp(`/instructor/courses/${course.id}`));
});

test('a review row is keyboard reachable and highlights on hover', async ({ page }) => {
  test.skip(test.info().project.name === 'mobile', 'hover 는 마우스 기기만');
  await openReviews(page);
  const row = page.getByRole('button', { name: /학생1/ });
  await expect(row).toBeVisible();
  const before = await row.evaluate((el) => getComputedStyle(el).backgroundColor);
  await row.hover();
  await expect.poll(() => row.evaluate((el) => getComputedStyle(el).backgroundColor)).not.toBe(before);
  await expect(row).toHaveCSS('cursor', 'pointer');

  await row.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('dialog')).toContainText(reviews[0].content);
});
