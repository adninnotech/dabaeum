-- B-4 회원 상세의 관리자 메모. 기관별이 아니라 사이트 전역 메모다.
-- 강사 메모(tb_user_roles.memo)는 기관별이라 별개로 둔다.
ALTER TABLE tb_users
    ADD COLUMN admin_memo VARCHAR(1000);
