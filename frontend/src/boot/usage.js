import { defineBoot } from '#q-app';
import { recordVisitOncePerTab } from '@/services/usage-api';

// 이용 통계 방문자 — 앱을 처음 열 때 탭당 한 번. 로그인 상태면 저장된 토큰으로 회원으로 센다
export default defineBoot(() => {
  recordVisitOncePerTab();
});
