-- 기관 등록 신청: 신청자 한 명당 대기(PENDING) 신청은 하나다.
-- 서비스가 먼저 확인하고, 동시에 들어온 신청은 이 인덱스가 막는다(409 INSTITUTION_APPLICATION_CONFLICT).
-- 적용 전 dev 에 신청자별 PENDING 중복이 없음을 확인했다(2026-10-02).

CREATE UNIQUE INDEX uq_tb_institution_applications_pending
    ON tb_institution_applications (applicant_user_id)
    WHERE status = 'PENDING' AND applicant_user_id IS NOT NULL;
