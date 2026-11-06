import { expect, test } from '@playwright/test';

const adminUser = {
  id: 'admin-user',
  name: '관리자',
  email: 'admin@example.test',
  roles: [{ role: 'PLATFORM_ADMIN' }],
};

// 1×1 투명 PNG
const ONE_PIXEL_PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=',
  'base64',
);

async function openWithMockedApi(page, path) {
  await page.addInitScript((user) => {
    // Quasar LocalStorage 는 객체를 '__q_objt|' 접두사로 저장한다 — 그냥 JSON 을 넣으면 로그인으로 읽지 않는다
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
    const pathname = new URL(route.request().url()).pathname;
    // 사진 파일은 실제 이미지로 — JSON 을 주면 깨진 이미지(0×0)라 '보임' 검사가 실패한다
    if (pathname.includes('/files/')) {
      await route.fulfill({ status: 200, contentType: 'image/png', body: ONE_PIXEL_PNG });
      return;
    }
    let data = [];

    // 부팅 때 세션·내 정보를 다시 읽는다 — 빈 값이면 로그아웃 상태가 돼 관리자 화면에 못 들어간다
    if (pathname.endsWith('/auth/session')) {
      data = { userId: adminUser.id, roles: ['PLATFORM_ADMIN'] };
    } else if (pathname.endsWith('/users/me')) {
      data = adminUser;
    } else if (pathname.endsWith('/admin/inquiries')) {
      data = [
        {
          id: 'inquiry-1',
          userName: '학습자',
          profileImageId: 'profile-file-id',
          title: '문의 제목',
          status: 'PENDING',
          createdAt: '2026-10-21T00:00:00Z',
        },
        {
          id: 'inquiry-2',
          userName: '다른 학습자',
          profileImageId: null,
          title: '사진 없는 문의',
          status: 'PENDING',
          createdAt: '2026-10-21T00:00:00Z',
        },
      ];
    } else if (pathname.endsWith('/users/student-1')) {
      data = {
        id: 'student-1',
        name: '학생',
        email: 'student@example.test',
        profileImageId: 'profile-file-id',
      };
    } else if (pathname.endsWith('/users/student-1/learning-stats')) {
      data = {
        totalCount: 0,
        inProgressCount: 0,
        completedCount: 0,
        totalStudyMinutes: 0,
        averageProgressPercent: 0,
        inquiryCount: 0,
      };
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        data,
        page: { totalElements: Array.isArray(data) ? data.length : 0 },
      }),
    });
  });

  await page.goto(path);
}

test('admin inquiry and student detail show the user profile image', async ({ page }) => {
  await openWithMockedApi(page, '/admin/inquiries');
  await expect(page.locator('img[src$="/files/profile-file-id/content"]')).toBeVisible();
  await expect(page.locator('.cell-user__avatar').filter({ hasText: '다' })).toBeVisible();

  await openWithMockedApi(page, '/admin/students/student-1');
  await expect(page.locator('img[src$="/files/profile-file-id/content"]')).toBeVisible();
});
