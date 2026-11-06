-- 마이페이지의 알림 수신 설정.
-- 이메일은 수강 승인 안내처럼 서비스 운영상 꼭 전달해야 하는 안내가 있어 기본 수신(TRUE)이다.
-- SMS 는 발송 비용이 들고 수신자가 더 민감하게 받아들이므로, 명시적으로 켠 사람에게만 보낸다(FALSE).
--
-- 주의: NOT NULL DEFAULT 라 기존 행에도 이 기본값이 그대로 채워진다.
--       즉 마이그레이션 직후 기존 사용자 전원이 SMS 수신 거부 상태로 시작한다.
--       동의를 받은 적이 없는 사람에게 켜 둔 채로 두는 것보다 이쪽이 안전하다.
ALTER TABLE tb_users
    ADD COLUMN email_notification_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN sms_notification_enabled BOOLEAN NOT NULL DEFAULT FALSE;
