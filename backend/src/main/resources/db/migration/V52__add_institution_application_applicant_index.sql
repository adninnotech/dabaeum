-- 신청자 본인의 기관 등록 신청 이력(GET /institution-applications/me, 최신순).
-- V51 이 dev 에 먼저 적용돼 체크섬을 지키려고 따로 둔다.

CREATE INDEX ix_tb_institution_applications_applicant_created_at
    ON tb_institution_applications (applicant_user_id, created_at DESC);
