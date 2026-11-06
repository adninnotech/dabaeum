import { expect, test } from '@playwright/test';

/* 회차 일괄 생성 창 — 요일을 여러 개 고를 수 있고, 키보드로 친 날짜로도 회차 수가 계산되며,
 * POST /courses/{id}/sessions/bulk 본문이 계약대로 { repeat } 인지(예전엔 { sessions: { repeat } } 로 거부됐다) */
const user = {
  id: 'teacher-1',
  name: '강사1',
  email: 'teacher@example.test',
  roles: ['INSTRUCTOR'],
};
const COURSE_ID = 'course-1';
const course = {
  id: COURSE_ID,
  institutionId: 'inst-1',
  title: '일괄 생성 과정',
  courseCode: 'C-1',
  status: 'DRAFT',
  educationType: 'OFFLINE',
  startDate: '2026-11-10',
  endDate: '2026-11-30',
  capacity: 20,
};

test('회차 일괄 생성: 키보드 날짜·여러 요일로 회차 수를 세고 { repeat } 로 보낸다', async ({
  page,
  isMobile,
}) => {
  test.skip(isMobile, '데스크톱 프로젝트에서만');
  let bulkBody = null;

  // Quasar LocalStorage 는 객체를 '__q_objt|' 접두사로 저장한다
  await page.addInitScript((u) => {
    localStorage.setItem(
      'dabaeum.auth',
      `__q_objt|${JSON.stringify({ token: 'e2e-token', user: u, activeUserId: u.id, selectedRole: 'INSTRUCTOR' })}`,
    );
  }, user);

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname.replace(/^.*\/api\/v1/, '');
    if (request.method() === 'POST' && path === `/courses/${COURSE_ID}/sessions/bulk`) {
      bulkBody = request.postDataJSON();
    }
    let data = [];
    if (path === '/auth/session') data = { userId: user.id, roles: ['INSTRUCTOR'] };
    else if (path === '/users/me') data = user;
    else if (path === `/courses/${COURSE_ID}`) data = course;
    else if (path === `/courses/${COURSE_ID}/instructors`)
      data = [{ userId: user.id, role: 'MAIN', name: user.name }];
    else if (path === '/instructors/me/courses') data = [course];
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data, page: { totalElements: 0, totalPages: 1 } }),
    });
  });

  await page.goto(`/instructor/courses/${COURSE_ID}?tab=curriculum`);
  await page.getByRole('button', { name: '일괄 생성' }).click();
  const dialog = page.locator('.session-form');
  await dialog.getByLabel('시작일').fill('2026-11-11');
  await dialog.getByLabel('종료일').fill('2026-11-24');
  await dialog.getByRole('button', { name: '화', exact: true }).click();
  await dialog.getByRole('button', { name: '목', exact: true }).click();
  await dialog.getByLabel('시작', { exact: true }).fill('19:00');
  await dialog.getByLabel('종료', { exact: true }).fill('21:00');

  // 11-11~11-24 의 화·목: 11-12, 11-17, 11-19, 11-24
  await expect(dialog.locator('.session-form__help')).toHaveText(/2026-11-12 부터 4개 회차/);
  await dialog.getByRole('button', { name: '4개 만들기' }).click();
  await expect.poll(() => bulkBody).not.toBeNull();
  expect(bulkBody).toEqual({
    repeat: {
      firstDate: '2026-11-12',
      daysOfWeek: ['TUESDAY', 'THURSDAY'],
      startTime: '19:00',
      endTime: '21:00',
      count: 4,
    },
  });
});
