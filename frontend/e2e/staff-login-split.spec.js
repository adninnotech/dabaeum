import { expect, test } from '@playwright/test';

/* 학습자는 다대구로만, 강사·기관 관리자는 이메일로 따로 가입·로그인한다(#134).
 * /login(다대구) ↔ /login/staff(이메일) → /signup/staff(역할 선택) → /signup/form(강사) · /signup/institution */

async function mockApi(page, { roles = ['LEARNER'], identities = [] } = {}) {
  await page.route('**/api/v1/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    let data = [];
    if (pathname.endsWith('/auth/login')) data = { accessToken: 'e2e-token', session: { userId: 'u1', roles } };
    else if (pathname.endsWith('/auth/session')) data = { userId: 'u1', roles };
    else if (pathname.endsWith('/users/me/identities')) data = identities;
    else if (pathname.endsWith('/users/me')) data = { id: 'u1', name: '테스트', email: 't@example.com' };
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data, page: { totalElements: Array.isArray(data) ? data.length : 0 } }),
    });
  });
}

async function submitStaffLogin(page) {
  await page.getByPlaceholder('이메일을 입력해 주세요').fill('t@example.com');
  await page.getByPlaceholder('비밀번호를 입력해 주세요').fill('pw-test-1234');
  await page.getByRole('button', { name: '로그인', exact: true }).click();
}

test('학습자 로그인에서 강사·기관 이메일 로그인으로, 돌아갈 주소를 넘긴다', async ({ page }) => {
  await mockApi(page);
  await page.goto('/login?redirect=%2Fcourses');

  await page.getByRole('link', { name: '강사·기관 관리자 로그인' }).click();
  await expect(page).toHaveURL(/\/login\/staff\?redirect=(%2F|\/)courses$/);
  await expect(page.getByRole('heading', { name: '강사·기관 관리자 로그인', level: 1 })).toBeVisible();
  await expect(page.getByPlaceholder('이메일을 입력해 주세요')).toBeVisible();
  await expect(page.getByRole('button', { name: '비밀번호 찾기' })).toBeVisible();
  await expect(page.getByRole('link', { name: '다대구로 로그인' })).toHaveAttribute(
    'href',
    /\/login\?redirect=(%2F|\/)courses$/,
  );
});

test('강사·기관 회원가입은 일반 회원 없이 두 역할만 고른다', async ({ page }) => {
  await mockApi(page);
  await page.goto('/login/staff');
  await page.getByRole('link', { name: '회원가입' }).click();

  await expect(page).toHaveURL(/\/signup\/staff$/);
  await expect(page.getByRole('heading', { name: '강사·기관 관리자 회원가입', level: 1 })).toBeVisible();
  await expect(page.getByRole('button', { name: /일반 회원/ })).toHaveCount(0);
  await expect(page.getByRole('link', { name: '로그인', exact: true })).toHaveAttribute('href', '/login/staff');

  const instructor = page.getByRole('button', { name: /개인 강사/ });
  await expect(page.getByRole('button', { name: /기관 관리자/ })).toBeVisible();
  await instructor.click();
  await expect(instructor).toHaveAttribute('aria-pressed', 'true');
  await page.getByRole('button', { name: '다음' }).click();

  await expect(page).toHaveURL(/\/signup\/form\?role=INSTRUCTOR$/);
  await expect(page.getByRole('heading', { name: '강사 회원가입', level: 1 })).toBeVisible();
});

test('학습자용 이메일 가입 주소는 받지 않는다', async ({ page }) => {
  await mockApi(page);
  await page.goto('/signup/form?role=LEARNER');
  await expect(page).toHaveURL(/\/signup\/staff$/);
});

test('기관 관리자 가입 화면이 열린다', async ({ page }) => {
  await mockApi(page);
  await page.goto('/signup/institution');
  await expect(page).toHaveURL(/\/signup\/institution$/);
  await expect(page.getByRole('link', { name: '로그인', exact: true })).toHaveAttribute('href', '/login/staff');
});

test('강사 역할이 있으면 강사 콘솔로 간다', async ({ page }) => {
  await mockApi(page, { roles: ['INSTRUCTOR'] });
  await page.goto('/login/staff');
  await submitStaffLogin(page);
  await expect(page).toHaveURL(/\/instructor/);
});

test('강사·기관 역할이 없는 계정은 신청 현황을 안내하고 계정을 바꿀 수 있다', async ({ page }) => {
  await mockApi(page, { roles: ['LEARNER'] });
  await page.goto('/login/staff');
  await submitStaffLogin(page);

  await expect(page).toHaveURL(/\/login\/staff$/);
  await expect(page.getByRole('heading', { name: '아직 강사·기관 관리자 역할이 없어요' })).toBeVisible();
  await expect(page.getByRole('link', { name: '강사 신청 현황' })).toHaveAttribute('href', '/mypage/instructor-apply');
  await expect(page.getByRole('link', { name: '회원가입' })).toHaveCount(0);

  await page.getByRole('button', { name: '다른 계정으로 로그인' }).click();
  await expect(page.getByPlaceholder('이메일을 입력해 주세요')).toBeVisible();
});

async function openMyPage(page, identities) {
  const user = { id: 'u1', name: '테스트', email: '', roles: [{ role: 'LEARNER' }] };
  await page.addInitScript((sessionUser) => {
    // Quasar LocalStorage 는 객체를 '__q_objt|' 접두사로 저장한다
    localStorage.setItem(
      'dabaeum.auth',
      `__q_objt|${JSON.stringify({ token: 'e2e-token', user: sessionUser, activeUserId: sessionUser.id, selectedRole: 'LEARNER' })}`,
    );
  }, user);
  await mockApi(page, { identities });
  await page.goto('/mypage');
}

test('다대구 계정 마이페이지에는 강사·기관 신청 대신 따로 가입 안내만 있다', async ({ page }) => {
  await openMyPage(page, [{ provider: 'DADAEGU', verifiedAt: '2026-10-01T00:00:00Z' }]);

  await expect(page.getByRole('heading', { name: '강사·기관 관리자로 참여하기' })).toBeVisible();
  await expect(page.getByRole('link', { name: '강사·기관 관리자 로그인' })).toHaveAttribute('href', '/login/staff');
  await expect(page.getByRole('link', { name: /강사 신청/ })).toHaveCount(0);
  await expect(page.getByRole('link', { name: /기관 등록 신청/ })).toHaveCount(0);
});

test('이메일 계정 마이페이지에는 강사 신청·기관 등록 신청이 남는다', async ({ page }) => {
  await openMyPage(page, [{ provider: 'LOCAL' }]);

  await expect(page.getByRole('heading', { name: '다른 역할로 참여하기' })).toBeVisible();
  await expect(page.getByRole('link', { name: /강사 신청/ })).toBeVisible();
  await expect(page.getByRole('link', { name: /기관 등록 신청/ })).toBeVisible();
});
