-- 다대구 DID 재인증을 거친 QR 출결을 일반 QR 출결과 구분해 기록한다.
-- 값만 추가하며 기존 행·컬럼은 건드리지 않는다.
ALTER TABLE tb_attendance_records DROP CONSTRAINT ck_tb_attendance_records_method;
ALTER TABLE tb_attendance_records ADD CONSTRAINT ck_tb_attendance_records_method
    CHECK (attendance_method IN ('QR', 'QR_DADAEGU', 'ADMIN', 'EXTERNAL'));
