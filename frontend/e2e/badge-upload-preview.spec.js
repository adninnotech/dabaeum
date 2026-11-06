import { expect, test } from '@playwright/test';

// QA 중앙 #55 참고 — 배지 정의 수정에서 새 그림을 올리면 저장 전 미리보기가 훈장 아이콘이었다.
// 저장 전 파일은 업로더 인증이 있어야 열리는데 <img> 는 인증 헤더를 못 보낸다(403).
// 고른 파일을 브라우저에서 바로 미리 보인다.

const adminUser = {
  id: 'admin-user',
  name: '관리자',
  email: 'admin@example.test',
  roles: [{ role: 'PLATFORM_ADMIN' }],
};

const definition = {
  id: 'def-tier',
  code: 'TIER_TEST',
  name: '단계 배지',
  active: true,
  imageFileId: null,
  imageUrl: null,
  rule: {
    type: 'TIERED',
    classificationCode: null,
    levels: [
      { level: 1, name: '씨앗', minCount: 1, imageFileId: null, imageUrl: null },
      { level: 2, name: '새싹', minCount: 3, imageFileId: null, imageUrl: null },
    ],
  },
};

// 1×1 PNG
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
);

async function openDefinition(page) {
  let uploads = 0;
  await page.addInitScript((user) => {
    // Quasar LocalStorage 는 객체를 '__q_objt|' 접두사로 저장한다
    localStorage.setItem(
      'dabaeum.auth',
      `__q_objt|${JSON.stringify({ token: 'e2e-token', user, activeUserId: user.id, selectedRole: 'PLATFORM_ADMIN' })}`,
    );
  }, adminUser);

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;
    // 저장 전 파일 본문 — 서버는 인증 없는 요청을 막는다
    if (/\/files\/[^/]+\/content$/.test(pathname)) {
      await route.fulfill({ status: 403, contentType: 'application/json', body: '{"code":"FORBIDDEN"}' });
      return;
    }
    let data = [];
    if (pathname.endsWith('/auth/session')) data = { userId: adminUser.id, roles: ['PLATFORM_ADMIN'] };
    else if (pathname.endsWith('/users/me')) data = adminUser;
    else if (pathname.endsWith('/admin/badge-definitions')) data = [definition];
    else if (pathname.endsWith('/files') && request.method() === 'POST') {
      uploads += 1;
      const id = `upload-${uploads}`;
      data = { id, url: `/api/v1/files/${id}/content` };
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data, page: { totalElements: Array.isArray(data) ? data.length : 0 } }),
    });
  });

  await page.goto('/admin/badge-definitions');
  await page.getByText('단계 배지', { exact: true }).first().click();
  return page.getByRole('dialog');
}

async function pick(page, trigger) {
  const chooser = page.waitForEvent('filechooser');
  await trigger.click();
  await (await chooser).setFiles({ name: 'badge.png', mimeType: 'image/png', buffer: PNG });
}

test('a newly uploaded badge image previews before saving', async ({ page }) => {
  const dialog = await openDefinition(page);

  await pick(page, dialog.getByRole('button', { name: '이미지 올리기', exact: true }));
  const mainImage = dialog.locator('.badge-form__image img');
  await expect(mainImage).toHaveAttribute('src', /^blob:/);
  await expect.poll(() => mainImage.evaluate((img) => img.naturalWidth)).toBeGreaterThan(0);

  await pick(page, dialog.getByRole('button', { name: '1단계 이미지 올리기' }));
  const tierImage = dialog.locator('.tier-table tbody tr').first().locator('img');
  await expect(tierImage).toHaveAttribute('src', /^blob:/);
  await expect.poll(() => tierImage.evaluate((img) => img.naturalWidth)).toBeGreaterThan(0);
});
