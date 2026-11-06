import { expect, test } from '@playwright/test';
import { adminCredentials, hasAdminCredentials, loginViaForm } from './helpers';

// 플랫폼 관리자 흐름 — 조회·화면 이동만. 자격 증명이 없으면 건너뛴다
test.describe('플랫폼 관리자', () => {
  test.skip(!hasAdminCredentials, 'E2E_ADMIN_EMAIL / E2E_ADMIN_PASSWORD 가 없음');

  test.beforeEach(async ({ page }) => {
    await loginViaForm(page, adminCredentials, '/admin');
  });

  test('블록체인 모니터링에 원장 요약·트랜잭션·배지 통계가 보인다', async ({ page }) => {
    await page.goto('/admin/blockchain');
    await expect(page.getByText('블록체인 모니터링', { exact: true })).toBeVisible();
    await expect(page.getByRole('region', { name: '원장 요약' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '최근 트랜잭션' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '학습배지 발급 통계' })).toBeVisible();
    // 개발용 자리표시가 다시 들어오지 않았는지
    await expect(page.getByText('API 계약 없음')).toHaveCount(0);
  });

  test('기관 콘솔은 기관을 고르기 전엔 비어 있고, 고르면 그 기관 데이터를 읽는다', async ({ page, isMobile }) => {
    test.skip(isMobile, '선택 목록 조작은 데스크톱에서');
    await page.goto('/institution/courses');
    await page.evaluate(() => sessionStorage.removeItem('dabaeum.console.institutionId'));
    await page.reload();

    await expect(page.getByText('기관을 고르면 그 기관의 강의·수강·강사 데이터를 봅니다.')).toBeVisible();

    const courseRequest = page.waitForRequest((request) => /\/institutions\/[0-9a-f-]{36}\/courses/.test(request.url()));
    await page.getByRole('combobox', { name: '보고 있는 기관' }).click();
    await page.locator('.q-menu .q-item').first().click();
    await courseRequest;

    await expect(page.getByText('기관을 고르면 그 기관의 강의·수강·강사 데이터를 봅니다.')).toHaveCount(0);
  });

  test('플랫폼 관리자는 강사 콘솔에서도 전체 과정을 본다', async ({ page }) => {
    // 실제 강사는 /instructors/me/courses(배정 과정만) — 관리자만 전체 /courses
    const requests = [];
    page.on('request', (request) => {
      if (request.url().includes('/api/v1/')) requests.push(new URL(request.url()).pathname);
    });
    await page.goto('/instructor');
    await expect(page.getByText('관리자 계정이라 모든 기관의 과정을 보여 줍니다.', { exact: false })).toBeVisible();
    await expect.poll(() => requests).toContain('/api/v1/courses');
    expect(requests).not.toContain('/api/v1/instructors/me/courses');
  });
});
