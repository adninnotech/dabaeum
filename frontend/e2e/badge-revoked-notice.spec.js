import { expect, test } from '@playwright/test';

// QA 중앙 #54 — 관리자가 사유를 넣어 폐기한 배지에도 "조건을 다시 채우면 돌아옵니다"가 나왔다

const learner = {
  id: 'badge-learner',
  name: '배지학생',
  email: 'badge@example.test',
  roles: [{ role: 'LEARNER' }],
};

const definitions = [
  { id: 'def-auto', name: '첫 수료', criteria: '강좌 1건 이수', rule: null },
  { id: 'def-manual', name: '성실 학습', criteria: '출석 90% 이상', rule: null },
];

function revokedBadge(definitionId, reason) {
  return {
    id: `badge-${definitionId}`,
    userId: learner.id,
    definitionId,
    badgeType: 'COMPLETION',
    badgeName: definitionId,
    status: 'REVOKED',
    issuedAt: '2026-10-01T00:00:00Z',
    revokedAt: '2026-10-22T03:00:00Z',
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-22T03:00:00Z',
    issuerName: '다배움',
    lastEvent: { type: 'REVOKED', reason, occurredAt: '2026-10-22T03:00:00Z' },
  };
}

test('badge cards tell automatic and admin revocations apart', async ({ page }) => {
  await page.addInitScript((user) => {
    // Quasar LocalStorage 는 객체를 '__q_objt|' 접두사로 저장한다
    localStorage.setItem(
      'dabaeum.auth',
      `__q_objt|${JSON.stringify({ token: 'e2e-token', user, activeUserId: user.id, selectedRole: 'LEARNER' })}`,
    );
  }, learner);

  await page.route('**/api/v1/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    let data = [];
    if (pathname.endsWith('/auth/session')) {
      data = { userId: learner.id, roles: ['LEARNER'] };
    } else if (pathname.endsWith('/users/me')) {
      data = learner;
    } else if (pathname.endsWith('/badge-definitions')) {
      data = definitions;
    } else if (pathname.endsWith(`/users/${learner.id}/badges`)) {
      data = [
        revokedBadge('def-auto', '수료 확정 취소로 발급 조건 미충족'),
        revokedBadge('def-manual', '부정 출석 확인'),
      ];
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data, page: { totalElements: Array.isArray(data) ? data.length : 0 } }),
    });
  });

  await page.goto('/badges');

  const autoCard = page.locator('li').filter({ hasText: '첫 수료' });
  const manualCard = page.locator('li').filter({ hasText: '성실 학습' });
  await expect(autoCard).toContainText('수료 확정이 취소되어');
  await expect(autoCard).toContainText('조건을 다시 채우면 돌아옵니다.');
  await expect(manualCard).toContainText('관리자가 폐기했습니다(사유: 부정 출석 확인)');
  await expect(manualCard).toContainText('조건을 다시 채워도 돌아오지 않습니다.');
  await expect(manualCard).not.toContainText('조건을 다시 채우면 돌아옵니다');
});
