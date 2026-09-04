-- dev bearer 토큰(DevBearerAuthenticationFilter)이 사용하는 전용 시스템 계정.
-- 종전에는 principal id 가 실제 사용자(강사1, 00000000-…0001)와 같아 dev 토큰으로 한 행위가
-- 그 사용자의 것으로 감사 컬럼(confirmed_by, applied_by 등)에 기록됐다.
-- 실제 사용자와 겹치지 않는 예약 id 를 두고 PLATFORM_ADMIN 역할을 부여한다.
-- 이미 있으면 건너뛴다.

INSERT INTO tb_users (id, name, email, status, created_at, updated_at)
VALUES (
    'ffffffff-0000-0000-0000-000000000001',
    '개발 관리자 (dev bearer)',
    NULL,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO tb_user_roles (id, user_id, institution_id, role, created_at)
VALUES (
    'ffffffff-0000-0000-0000-000000000002',
    'ffffffff-0000-0000-0000-000000000001',
    NULL,
    'PLATFORM_ADMIN',
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;
