import { expect, test } from '@playwright/test';

/* 다대구 추가 가입은 ① 약관·개인정보 동의 → ② 관심 분야(선택) 두 단계다(#114 → #142 구·군 대신 관심 분야).
 * 관심 분야 입력은 기본 켬. 끈 빌드(VITE_ENABLE_INTEREST_INPUT=false)에선 ①에서 바로 가입한다 —
 * 그 빌드를 볼 땐 E2E_INTEREST_INPUT=0 으로 돌린다.
 * 가입 토큰은 메모리에만 있으므로 로그인 화면의 SDK 콜백을 직접 불러 가입 화면으로 들어간다 */

const INTEREST_ON = process.env.E2E_INTEREST_INPUT !== '0';

// dev 표준 분류 1단계와 같은 코드·이름 — 버튼 아래 예시(INTEREST_EXAMPLES)가 코드로 붙는다
const CLASSIFICATIONS = [
  ['TSIX_010000', '기초문해교육'],
  ['TSIX_020000', '학력보완교육'],
  ['TSIX_030000', '직업능력교육'],
  ['TSIX_040000', '문화예술교육'],
  ['TSIX_050000', '인문교양교육'],
  ['TSIX_060000', '시민참여교육'],
  ['TSIX_070000', '성인진로개발역량향상교육'],
].map(([code, name]) => ({ code, level: 1, name, enabled: true, children: [] }));

const TERMS_BODY = { USAGE: '제1조 목적\n이용약관 본문', PRIVACY: '제1조 수집 항목\n개인정보 본문' };

async function mockSignupApi(page, signupBodies, { termsFail = false } = {}) {
  await page.route('**/api/v1/**', (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace(/^.*\/api\/v1/, '');
    if (path === '/contents/terms') {
      if (termsFail) return route.fulfill({ status: 500, contentType: 'application/json', body: '{}' });
      const type = url.searchParams.get('type');
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: { type, title: type, version: '1', body: TERMS_BODY[type] } }),
      });
    }
    if (path === '/auth/dadaegu/login') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            registrationRequired: true,
            registrationToken: 'reg-token',
            expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
            maskedName: '홍*동',
          },
        }),
      });
    }
    if (path === '/course-classifications') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: CLASSIFICATIONS }) });
    }
    if (path === '/auth/dadaegu/signup') {
      signupBodies.push(route.request().postDataJSON());
      // 가입 뒤 로그인 흐름은 이 테스트 범위 밖 — 요청 본문만 본다
      return route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ message: '테스트용 실패' }),
      });
    }
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data: [], page: { totalElements: 0, totalPages: 1 } }),
    });
  });
}

async function openSignup(page) {
  await page.goto('/login');
  await page.waitForFunction(() => typeof window.dabaeumDadaeguLoginCallback === 'function');
  await page.evaluate(() => window.dabaeumDadaeguLoginCallback({ did: 'did:test:1' }));
  await expect(page).toHaveURL(/\/signup\/dadaegu/);
}

const nextButton = (page) => page.getByRole('button', { name: '다음', exact: true });
const finishButton = (page) => page.getByRole('button', { name: /가입 완료$/ });
const checkbox = (page, label) => page.locator('.signup-terms__check', { hasText: label }).locator('input');
const toggle = (page, name) => page.getByRole('button', { name: new RegExp(`^${name} (자세히 보기|접기)$`) });
const notice = (page) => page.getByRole('table', { name: '개인정보 수집·이용 안내' });

test.describe('다대구 가입 — 관심 분야 입력을 끈 빌드', () => {
  test.skip(INTEREST_ON, 'E2E_INTEREST_INPUT=0 과 VITE_ENABLE_INTEREST_INPUT=false 로 띄운 dev 서버에서만');

  test('약관 동의가 마지막 단계 — 필수 동의 전엔 [가입 완료]가 막히고 구·군·관심 분야는 없다', async ({ page }) => {
    await mockSignupApi(page, []);
    await openSignup(page);

    await expect(page.locator('.signup-stepper__label--active')).toHaveText('약관 동의');
    await expect(page.getByLabel('거주 구·군')).toHaveCount(0);
    await expect(page.getByRole('heading', { name: /관심 분야/ })).toHaveCount(0);
    await expect(nextButton(page)).toHaveCount(0);
    await expect(page.getByRole('button', { name: '내용 보기' })).toHaveCount(0);

    await expect(finishButton(page)).toBeDisabled();
    await checkbox(page, '서비스 이용약관').check();
    await expect(finishButton(page)).toBeDisabled();
    await checkbox(page, '개인정보 수집·이용').check();
    await expect(finishButton(page)).toBeEnabled();
  });

  test('가입 요청엔 동의 결과만 실린다 — 구·군도 관심 분야도 보내지 않는다', async ({ page }) => {
    const bodies = [];
    await mockSignupApi(page, bodies);
    await openSignup(page);

    await checkbox(page, '서비스 이용약관').check();
    await checkbox(page, '개인정보 수집·이용').check();
    await finishButton(page).click();

    await expect.poll(() => bodies.length).toBe(1);
    expect(bodies[0]).toEqual({
      registrationToken: 'reg-token',
      agreedToUsage: true,
      agreedToPrivacy: true,
    });
  });
});

test.describe('다대구 가입 — 관심 분야 입력을 켠 빌드', () => {
  test.skip(!INTEREST_ON, '관심 분야 입력을 끈 빌드에선 위 묶음이 본다');

  // 버튼 이름은 '분류 이름 + 예시' — 분류 이름으로 시작하는 버튼
  const chip = (page, name) => page.getByRole('button', { name: new RegExp(`^${name}`) });
  const interestConsent = (page) => page.locator('.interest-picker__check input');

  test('동의하면 관심 분야 단계로 가고, 이전 단계로 돌아가도 체크가 남는다', async ({ page }) => {
    await mockSignupApi(page, []);
    await openSignup(page);

    await expect(nextButton(page)).toBeDisabled();
    await checkbox(page, '전체 동의').check();
    await nextButton(page).click();

    await expect(page.locator('.signup-stepper__label--active')).toHaveText('정보 입력');
    await expect(page.getByRole('heading', { name: /관심 분야/ })).toBeVisible();
    await expect(page.getByLabel('거주 구·군')).toHaveCount(0);
    await expect(page.getByRole('region', { name: '개인정보 수집·이용 전체 본문' })).toHaveCount(0);

    await page.getByRole('button', { name: '이전 단계(약관 동의)' }).click();
    await expect(checkbox(page, '전체 동의')).toBeChecked();
    await expect(checkbox(page, '개인정보 수집·이용')).toBeChecked();
    await expect(nextButton(page)).toBeEnabled();
  });

  test('분류 이름 대신 쉬운 이름, 앞 단계 동의와 따로라는 안내와 나중에 바꿀 수 있다는 안내', async ({ page }) => {
    await mockSignupApi(page, []);
    await openSignup(page);
    await checkbox(page, '전체 동의').check();
    await nextButton(page).click();

    for (const name of ['한글·스마트폰 배우기', '검정고시·학력', '자격증·취업', '운동·취미·예술', '인문·건강·요리', '환경·시민 활동', '진로·인생 설계']) {
      await expect(chip(page, name)).toBeVisible();
    }
    await expect(page.getByRole('button', { name: /문화예술교육|학력보완교육/ })).toHaveCount(0);
    await expect(page.getByText('앞 단계의 전체 동의에는 들어 있지 않아요')).toBeVisible();
    await expect(page.getByText('나중에 마이페이지에서 바꿀 수 있어요')).toBeVisible();
  });

  test('동의 전에 누르면 막지 않고 이유를 알리며 동의 칸으로 간다 — 3개를 넘기면 하나를 빼라고 알린다', async ({ page }) => {
    await mockSignupApi(page, []);
    await openSignup(page);
    await checkbox(page, '서비스 이용약관').check();
    await checkbox(page, '개인정보 수집·이용').check();
    await nextButton(page).click();

    // 막힌(disabled) 버튼은 키보드로 갈 수도 없었다 — 누를 수 있고, 누르면 이유가 나온다
    await expect(chip(page, '검정고시·학력')).toBeEnabled();
    await chip(page, '검정고시·학력').click();
    await expect(page.getByRole('alert')).toHaveText('먼저 [선택] 관심 분야 수집·이용 동의에 체크해 주세요.');
    await expect(interestConsent(page)).toBeFocused();
    await expect(chip(page, '검정고시·학력')).toHaveAttribute('aria-pressed', 'false');
    await expect(page.getByRole('button', { name: '건너뛰고 가입 완료' })).toBeEnabled();

    await interestConsent(page).check();
    await expect(page.getByRole('alert')).toHaveCount(0);
    for (const name of ['검정고시·학력', '자격증·취업', '운동·취미·예술']) await chip(page, name).click();
    // 고른 순서 번호
    await expect(chip(page, '검정고시·학력')).toHaveAttribute('aria-pressed', 'true');
    await expect(chip(page, '운동·취미·예술')).toHaveAccessibleName(/^운동·취미·예술\s*,\s*3번째$/);
    await expect(page.getByText('3개를 모두 골랐어요')).toBeVisible();
    await chip(page, '인문·건강·요리').click();
    await expect(page.getByRole('alert')).toContainText('하나를 먼저 빼 주세요');
    await expect(chip(page, '인문·건강·요리')).toHaveAttribute('aria-pressed', 'false');
    await expect(page.getByRole('button', { name: '가입 완료', exact: true })).toBeVisible();

    await interestConsent(page).uncheck();
    await expect(chip(page, '검정고시·학력')).toHaveAttribute('aria-pressed', 'false');
    await expect(page.getByRole('button', { name: '건너뛰고 가입 완료' })).toBeVisible();
  });

  test('[자세히 보기]로 관심 분야 수집·이용 안내를 펼친다 — 목적·기간 강조', async ({ page }) => {
    await mockSignupApi(page, []);
    await openSignup(page);
    await checkbox(page, '전체 동의').check();
    await nextButton(page).click();

    await page.getByRole('button', { name: /^관심 분야 수집·이용 안내 (자세히 보기|접기)$/ }).click();
    const table = page.getByRole('table', { name: '관심 분야 수집·이용 안내' });
    await expect(table.getByRole('rowheader')).toHaveText(['이용 목적', '수집 항목', '보유·이용 기간', '동의 거부 권리']);
    await expect(table.locator('strong')).toHaveText([/맞춘 강좌 추천/, /동의를 철회하거나/]);
  });

  test('가입 요청에 관심 분야와 선택 동의가 실린다', async ({ page }) => {
    const bodies = [];
    await mockSignupApi(page, bodies);
    await openSignup(page);
    await checkbox(page, '서비스 이용약관').check();
    await checkbox(page, '개인정보 수집·이용').check();
    await nextButton(page).click();
    await interestConsent(page).check();
    await chip(page, '자격증·취업').click();
    await chip(page, '검정고시·학력').click();
    await page.getByRole('button', { name: '가입 완료', exact: true }).click();

    await expect.poll(() => bodies.length).toBe(1);
    expect(bodies[0]).toEqual({
      registrationToken: 'reg-token',
      interestCodes: ['TSIX_030000', 'TSIX_020000'],
      agreedToInterest: true,
      agreedToUsage: true,
      agreedToPrivacy: true,
    });
  });

  test('건너뛰면 빈 관심 분야와 동의 안 함으로 보낸다', async ({ page }) => {
    const bodies = [];
    await mockSignupApi(page, bodies);
    await openSignup(page);
    await checkbox(page, '서비스 이용약관').check();
    await checkbox(page, '개인정보 수집·이용').check();
    await nextButton(page).click();
    await page.getByRole('button', { name: '건너뛰고 가입 완료' }).click();

    await expect.poll(() => bodies.length).toBe(1);
    expect(bodies[0]).toMatchObject({ interestCodes: [], agreedToInterest: false });
  });

  for (const width of [320, 390]) {
    test(`폰 폭 ${width}px 에서 관심 분야 버튼이 가로로 넘치지 않는다`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      await mockSignupApi(page, []);
      await openSignup(page);
      await checkbox(page, '전체 동의').check();
      await nextButton(page).click();
      await interestConsent(page).check();
      const longest = chip(page, '한글·스마트폰 배우기');
      await expect(longest).toBeEnabled();
      const clipped = await longest.evaluate((el) => el.scrollWidth > el.clientWidth + 1);
      expect(clipped).toBe(false);
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      expect(overflow).toBeLessThanOrEqual(0);
    });
  }
});

test.describe('다대구 가입 첫 단계 — 개인정보 동의', () => {

  test('[내용 보기] 팝업 없이 줄 끝 [자세히 보기]로 그 자리에서 펼치고 접는다', async ({ page }) => {
    await mockSignupApi(page, []);
    await openSignup(page);

    const privacy = toggle(page, '개인정보 수집·이용');
    await expect(privacy).toHaveAttribute('aria-expanded', 'false');
    await expect(privacy).toContainText('자세히 보기');
    await expect(notice(page)).toBeHidden();

    await privacy.click();
    await expect(privacy).toHaveAttribute('aria-expanded', 'true');
    await expect(privacy).toContainText('접기');
    // 법(제15조 ②) 순서: 목적 → 항목 → 기간 → 거부 권리
    await expect(notice(page).getByRole('rowheader')).toHaveText(['이용 목적', '수집 항목', '보유·이용 기간', '동의 거부 권리']);
    // 보유·이용 기간은 법이 정한 "중요한 내용"이라 강조한다
    await expect(notice(page).locator('strong')).toHaveText([/회원 탈퇴 시까지/]);
    await expect(page.getByRole('region', { name: '개인정보 수집·이용 전체 본문' })).toContainText('개인정보 본문');
    await expect(page.locator('.q-dialog')).toHaveCount(0);

    await toggle(page, '이용약관').click();
    await expect(page.getByRole('region', { name: '이용약관 전체 본문' })).toContainText('이용약관 본문');
    // 이용약관에는 개인정보 표가 따로 붙지 않는다
    await expect(page.getByRole('table')).toHaveCount(1);

    await privacy.click();
    await expect(privacy).toHaveAttribute('aria-expanded', 'false');
    await expect(notice(page)).toBeHidden();
    // 펼치기는 체크와 따로 논다
    await expect(checkbox(page, '개인정보 수집·이용')).not.toBeChecked();
  });

  test('[선택] 홍보 알림 수신 동의는 없다 — 동의는 필수 두 줄뿐(#144)', async ({ page }) => {
    await mockSignupApi(page, []);
    await openSignup(page);

    await expect(page.getByText('홍보 알림')).toHaveCount(0);
    await expect(page.locator('.signup-terms__row')).toHaveCount(2);
  });

  test('본문을 못 불러오면 다시 시도할 수 있고, 표는 그대로 보인다', async ({ page }) => {
    await mockSignupApi(page, [], { termsFail: true });
    await openSignup(page);

    await toggle(page, '개인정보 수집·이용').click();
    await expect(notice(page)).toBeVisible();
    await expect(page.getByRole('alert').filter({ hasText: '본문을 불러오지 못했습니다' })).toBeVisible();

    await page.unrouteAll({ behavior: 'ignoreErrors' });
    await mockSignupApi(page, []);
    await page.getByRole('button', { name: '다시 시도' }).click();
    await expect(page.getByRole('region', { name: '개인정보 수집·이용 전체 본문' })).toContainText('개인정보 본문');
  });

  for (const width of [320, 390]) {
    test(`폰 폭 ${width}px 에서 펼친 표·본문이 가로로 넘치지 않는다`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      await mockSignupApi(page, []);
      await openSignup(page);

      await toggle(page, '개인정보 수집·이용').click();
      await expect(notice(page)).toBeVisible();
      await expect(page.getByRole('region', { name: '개인정보 수집·이용 전체 본문' })).toBeVisible();
      const box = await notice(page).boundingBox();
      expect(box.x).toBeGreaterThanOrEqual(0);
      expect(box.x + box.width).toBeLessThanOrEqual(width);
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      expect(overflow).toBeLessThanOrEqual(0);
    });
  }
});
