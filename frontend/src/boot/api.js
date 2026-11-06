import { defineBoot } from '#q-app';
import { onSessionExpired } from '@/services/api-client';
import { canAccess } from '@/access/permissions';
import { useAuthStore } from '@/stores/auth-store';
import { loginRequiredRoute } from '@/utils/auth-redirect';

export default defineBoot(({ router }) => {
  const auth = useAuthStore();
  // 저장된 세션이 있을 때만 프로필 로드 (로그아웃 상태면 로그인 화면 사용)
  if (auth.token) {
    auth.bootstrapSession();
  }
  // 토큰 만료(401) — 세션을 비우고, 보던 화면을 redirect 로 들고 로그인 화면으로. 안내는 요청한 화면이 오류 문구로 띄운다.
  // 공개 화면에서 조회(GET)만 실패했으면 로그아웃만 하고 머문다 — 첫 화면을 열자마자 로그인으로 끌려가지 않게.
  // 저장·제출(GET 아님)이 실패했으면 공개 화면이어도 로그인으로 보낸다(QA PERM-04: 문의 제출).
  // 같은 화면이 동시에 보낸 요청이 여럿 401 이어도 첫 번째만 처리한다(토큰이 이미 비었으면 넘어간다)
  onSessionExpired(({ method }) => {
    if (!auth.token) return;
    auth.logout();
    const current = router.currentRoute.value;
    if (current.path === '/login') return;
    const stayable = method === 'GET' && canAccess(current, { isLoggedIn: false, roles: [] }).ok;
    if (!stayable) router.push(loginRequiredRoute(current.fullPath));
  });
});
