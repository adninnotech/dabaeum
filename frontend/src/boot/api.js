import { defineBoot } from '#q-app';
import { useAuthStore } from '@/stores/auth-store';

export default defineBoot(() => {
  const auth = useAuthStore();
  // 저장된 세션이 있을 때만 프로필 로드 (로그아웃 상태면 로그인 화면 사용)
  if (auth.token) {
    auth.bootstrapSession();
  }
});
