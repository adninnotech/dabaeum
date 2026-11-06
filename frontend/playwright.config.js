import { existsSync, readFileSync } from 'node:fs';
import { defineConfig } from '@playwright/test';

/*
 * E2E — 로컬 dev 서버(http://localhost:9000, 실제 dev 백엔드에 붙음)를 연다.
 * 공유 dev 데이터라 지금은 조회·화면 이동만 한다(수강신청·이수 처리처럼 데이터를 만드는 흐름은 테스트 데이터가 정리되면 추가).
 * 관리자 흐름은 E2E_ADMIN_EMAIL / E2E_ADMIN_PASSWORD 가 있을 때만 돈다 — 값은 .env.e2e(깃 제외)나 셸 환경변수로만 넣는다.
 */
// .env.e2e(깃 제외, .env* 규칙) 가 있으면 읽는다 — 셸에 이미 있는 값이 우선
function loadLocalEnv(file) {
  if (!existsSync(file)) return;
  readFileSync(file, 'utf8')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith('#') && line.includes('='))
    .forEach((line) => {
      const [key, ...rest] = line.split('=');
      const name = key.trim();
      if (!(name in process.env)) process.env[name] = rest.join('=').trim();
    });
}
loadLocalEnv('.env.e2e');

const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:9000';

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: BASE_URL,
    // 브라우저를 따로 내려받지 않고 설치된 Chrome 을 쓴다
    channel: 'chrome',
    locale: 'ko-KR',
    timezoneId: 'Asia/Seoul',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop', use: { viewport: { width: 1440, height: 900 } } },
    { name: 'mobile', use: { viewport: { width: 375, height: 800 }, isMobile: true, hasTouch: true } },
  ],
  webServer: {
    command: 'DABAEUM_DEMO_AUTH=false npx quasar dev',
    url: BASE_URL,
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
