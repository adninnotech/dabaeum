-- 리뷰 후속: 인기 강좌 집계(매시)·통계·보존 삭제가 기간 컬럼으로 거르는데 인덱스가 없어 전체 스캔했다.
-- 테이블이 작아 잠금 시간은 짧다. CONCURRENTLY 는 Flyway 트랜잭션 안에서 쓸 수 없어 쓰지 않는다.

CREATE INDEX IF NOT EXISTS ix_tb_enrollments_applied_at ON tb_enrollments (applied_at);
CREATE INDEX IF NOT EXISTS ix_tb_enrollments_approved_at ON tb_enrollments (approved_at)
    WHERE approved_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_tb_completions_completed_at ON tb_completions (completed_at)
    WHERE status = 'COMPLETED';

-- 메뉴가 가리키는 페이지 삭제 검사와 FK RESTRICT 확인.
CREATE INDEX IF NOT EXISTS ix_tb_site_menus_content_page ON tb_site_menus (content_page_id)
    WHERE content_page_id IS NOT NULL;

-- 공유 링크 보존 삭제(만료·폐기 후 90일).
CREATE INDEX IF NOT EXISTS ix_tb_portfolio_shares_expires_at ON tb_portfolio_shares (expires_at);
CREATE INDEX IF NOT EXISTS ix_tb_portfolio_shares_revoked_at ON tb_portfolio_shares (revoked_at)
    WHERE revoked_at IS NOT NULL;
