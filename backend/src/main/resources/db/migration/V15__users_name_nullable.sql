-- 다대구 DID(OnlyDid) 로그인 사용자는 이름을 받지 않는다. 개인정보 미보관 원칙.
ALTER TABLE tb_users ALTER COLUMN name DROP NOT NULL;
