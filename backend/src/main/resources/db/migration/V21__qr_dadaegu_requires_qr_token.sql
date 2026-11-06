-- 다대구 DID 출결(D안)은 학생이 강사 QR(URL)을 찍어 시작하므로 우리 QR 토큰을 함께 저장한다.
-- V8 의 제약은 attendance_method = 'QR' 일 때만 qr_token_id 를 허용해, QR_DADAEGU 기록이 거부됐다.
-- 도메인 불변식(Attendance: case QR, QR_DADAEGU)과 같은 모양으로 맞춘다.
ALTER TABLE tb_attendance_records
    DROP CONSTRAINT ck_tb_attendance_records_qr_token_pair;

ALTER TABLE tb_attendance_records
    ADD CONSTRAINT ck_tb_attendance_records_qr_token_pair CHECK (
        (attendance_method IN ('QR', 'QR_DADAEGU') AND qr_token_id IS NOT NULL)
        OR (attendance_method NOT IN ('QR', 'QR_DADAEGU') AND qr_token_id IS NULL)
    );
