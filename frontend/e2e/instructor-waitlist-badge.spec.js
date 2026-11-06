import { expect, test } from '@playwright/test';

// 강사 수강 신청 관리 — '신청'과 '대기 N번'이 같은 주황 꽉 찬 뱃지라 글자를 읽기 전엔 구분이 안 됐다.
// 신청(바로 처리할 것)은 꽉 찬 뱃지 그대로, 대기(자리가 나야 처리)는 연한 뱃지로 나눈다.

const instructor = {
  id: 'teacher-1',
  name: '강사1',
  email: 'teacher1@example.test',
  roles: [{ role: 'INSTRUCTOR', institutionId: 'inst-1' }],
};

const course = { id: 'course-1', title: 'QA 정원1 강의', instructorRole: 'MAIN' };

const enrollments = [
  { id: 'e-1', userId: 'u-1', name: '학생4', status: 'APPLIED', appliedAt: '2026-10-23T06:45:52Z' },
  { id: 'e-2', userId: 'u-2', name: '학생3', status: 'WAITLISTED', waitlistPosition: 1, appliedAt: '2026-10-23T06:45:52Z' },
];

async function openApps(page, theme, path = '/instructor/enrollment-apps') {
  await page.addInitScript(
    ({ user, theme: mode }) => {
      // Quasar LocalStorage 는 객체를 '__q_objt|' 접두사로 저장한다
      localStorage.setItem(
        'dabaeum.auth',
        `__q_objt|${JSON.stringify({ token: 'e2e-token', user, activeUserId: user.id, selectedRole: 'INSTRUCTOR' })}`,
      );
      localStorage.setItem('dabaeum.theme', mode);
    },
    { user: instructor, theme },
  );
  await page.route('**/api/v1/**', async (route) => {
    const { pathname } = new URL(route.request().url());
    let data = [];
    if (pathname.endsWith('/auth/session')) data = { userId: instructor.id, roles: ['INSTRUCTOR'] };
    else if (pathname.endsWith('/users/me')) data = instructor;
    else if (pathname.endsWith('/instructors/me/courses')) data = [course];
    else if (pathname.endsWith(`/courses/${course.id}`)) data = { ...course, status: 'RECRUITING', institutionId: 'inst-1', capacity: 1 };
    else if (pathname.endsWith(`/courses/${course.id}/enrollments`)) data = enrollments;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data, page: { totalElements: Array.isArray(data) ? data.length : 0 } }),
    });
  });
  await page.goto(path);
}

function badge(page, label) {
  return page.locator('.status-badge:visible', { hasText: new RegExp(`^\\s*${label}\\s*$`) }).first();
}

// 강의 상세 '수강 신청 현황' 탭은 공용 표가 아니라 뱃지를 따로 그려 한 번 빠졌다(#108)
const screens = [
  ['enrollment apps', '/instructor/enrollment-apps'],
  ['course detail applications tab', `/instructor/courses/${course.id}?tab=applications`],
];

for (const [screen, path] of screens) for (const theme of ['light', 'dark']) {
  test(`${screen}: waitlisted badge is soft while applied stays solid (${theme})`, async ({ page }) => {
    await openApps(page, theme, path);
    const applied = badge(page, '신청');
    const waitlisted = badge(page, '대기 1번');
    await expect(applied).toBeVisible();
    await expect(waitlisted).toBeVisible();
    await expect(applied).not.toHaveClass(/status-badge--soft/);
    await expect(waitlisted).toHaveClass(/status-badge--soft/);
    // 채움이 달라야 한다 — 같은 색이면 다시 구분이 안 된다
    const bg = (el) => el.evaluate((node) => getComputedStyle(node).backgroundColor);
    expect(await bg(applied)).not.toBe(await bg(waitlisted));
  });
}
