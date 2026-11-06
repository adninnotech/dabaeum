-- LOCAL 신원은 provider_subject 에 이메일을 넣는데 칸(255)이 이메일 상한(320)보다 좁았다.
-- 256~320자 이메일 가입이 DB 오류로 실패해 409 IDENTITY_CONFLICT 로 잘못 안내됐다(QA VAL-01 후속).
-- 다대구 가입 토큰(tb_registration_tokens.provider_subject)과 같은 512 로 맞춘다.
-- varchar 를 넓히기만 하므로 행·유니크 인덱스를 다시 쓰지 않는다.

ALTER TABLE tb_user_identities
    ALTER COLUMN provider_subject TYPE VARCHAR(512);
