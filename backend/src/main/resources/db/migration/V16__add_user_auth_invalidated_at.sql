-- 발급 시각이 이 값보다 이르거나 같은 액세스 토큰을 거부한다.
-- 비밀번호 재설정과 계정 정지·탈퇴 시 갱신하며, 만료 전 토큰을 즉시 무효화하는 용도다.
-- NULL 이면 무효화 이력이 없다.

ALTER TABLE tb_users ADD COLUMN auth_invalidated_at TIMESTAMPTZ;
