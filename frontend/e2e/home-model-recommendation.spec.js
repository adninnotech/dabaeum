import { expect, test } from '@playwright/test';

/* 홈 추천: basis=MODEL 이면 제목 "회원님을 위한 추천", 카드 이유는 topFactor 마다 한 문장(QA HOME-17, 문구 사용자 확정 2026-10-21).
 * 예전엔 MODEL 분기가 없어 기본 제목과 '지금 인기'로 떨어졌다. #142 — 구·군(REGION) 대신 관심 분야(INTEREST) */
const user = { id: 'learner-1', name: '학습자', email: 'learner@example.test', roles: ['LEARNER'] };
const FACTORS = ['CLASSIFICATION', 'INTEREST', 'SCHEDULE', null];

function course(index) {
  return {
    id: `course-${index}`,
    institutionId: 'inst-1',
    institutionName: '기관',
    title: `추천 강좌 ${index}`,
    courseCode: `C-${index}`,
    status: 'RECRUITING',
    educationType: 'OFFLINE',
    recruitEndDate: '2026-12-31',
    capacity: 20,
    enrolledCount: 1,
    tuitionType: 'FREE',
  };
}

async function mockHome(page, recommended) {
  // Quasar LocalStorage 는 객체를 '__q_objt|' 접두사로 저장한다
  await page.addInitScript((u) => {
    localStorage.setItem(
      'dabaeum.auth',
      `__q_objt|${JSON.stringify({ token: 'e2e-token', user: u, activeUserId: u.id, selectedRole: 'LEARNER' })}`,
    );
  }, user);

  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace(/^.*\/api\/v1/, '');
    let data = [];
    if (path === '/auth/session') data = { userId: user.id, roles: ['LEARNER'] };
    else if (path === '/users/me') data = user;
    else if (path === '/courses/recommended') data = recommended;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data, page: { totalElements: 0, totalPages: 1 } }),
    });
  });

  await page.goto('/');
  return page.locator('section[aria-labelledby="home-recommend-title"]');
}

test('모델 추천은 회원님을 위한 추천 제목과 topFactor 이유를 보인다', async ({ page }) => {
  const section = await mockHome(page, {
    basis: 'MODEL',
    items: FACTORS.map((topFactor, index) => ({
      course: course(index),
      impressionId: `imp-${index}`,
      reason: { type: 'MODEL_SCORE', score: 0.8, topFactor },
    })),
  });
  await expect(section.getByRole('heading', { name: '회원님을 위한 추천' })).toBeVisible();
  await expect(section.getByText('같은 분야를 들으셨어요')).toBeVisible();
  await expect(section.getByText('관심 분야에 맞는 강좌예요')).toBeVisible();
  await expect(section.getByText('가까운 지역 강좌예요')).toHaveCount(0);
  await expect(section.getByText('선호하시는 요일·시간대예요')).toBeVisible();
  // topFactor 가 없으면 기본 문장
  await expect(section.getByText('회원님과 비슷한 분들이 많이 신청했어요')).toBeVisible();
  await expect(section.getByText('지금 인기')).toHaveCount(0);

  // 360px 폰에서도 가장 긴 기본 문장이 말줄임 없이 다 보인다(세 줄까지 허용)
  await page.setViewportSize({ width: 360, height: 1400 });
  const longest = section.getByText('회원님과 비슷한 분들이 많이 신청했어요');
  await longest.scrollIntoViewIfNeeded();
  const clipped = await longest.evaluate((el) => el.scrollHeight > el.clientHeight + 1);
  expect(clipped).toBe(false);
});

test('관심 분야 추천은 관심 분야 제목과 분류 이름 이유를 보인다', async ({ page }) => {
  const section = await mockHome(page, {
    basis: 'INTEREST',
    items: ['문화예술교육', null].map((classificationName, index) => ({
      course: course(index),
      impressionId: `imp-${index}`,
      reason: { type: 'INTEREST', classificationCode: classificationName ? 'TSIX_030000' : null, classificationName },
    })),
  });
  await expect(section.getByRole('heading', { name: '관심 분야 강좌' })).toBeVisible();
  await expect(section.getByText('관심 분야: 문화예술교육')).toBeVisible();
  // 분류 이름이 없으면 일반 문장
  await expect(section.getByText('관심 분야 추천', { exact: true })).toBeVisible();
  await expect(section.getByText('지금 인기')).toHaveCount(0);
});
