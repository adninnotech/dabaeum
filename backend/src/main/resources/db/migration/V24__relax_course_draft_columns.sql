-- 임시저장(DRAFT)은 일정·정원 없이 저장할 수 있어야 한다.
-- 공개 상태에서는 여전히 값이 있어야 하므로 CHECK 로 상태별 조건을 건다.
ALTER TABLE tb_courses
    ALTER COLUMN start_date DROP NOT NULL,
    ALTER COLUMN end_date DROP NOT NULL,
    ALTER COLUMN capacity DROP NOT NULL;

-- 기존 ck_tb_courses_dates 는 NULL 을 통과시키므로 그대로 두어도 되지만,
-- 의도를 분명히 하려고 다시 건다.
ALTER TABLE tb_courses DROP CONSTRAINT ck_tb_courses_dates;
ALTER TABLE tb_courses ADD CONSTRAINT ck_tb_courses_dates
    CHECK (start_date IS NULL OR end_date IS NULL OR start_date <= end_date);

ALTER TABLE tb_courses DROP CONSTRAINT ck_tb_courses_capacity;
ALTER TABLE tb_courses ADD CONSTRAINT ck_tb_courses_capacity
    CHECK (capacity IS NULL OR capacity >= 1);

-- DRAFT 와 CANCELLED 가 아니면 세 값이 모두 있어야 한다.
ALTER TABLE tb_courses ADD CONSTRAINT ck_tb_courses_published_requires_schedule
    CHECK (status IN ('DRAFT', 'CANCELLED')
           OR (start_date IS NOT NULL AND end_date IS NOT NULL AND capacity IS NOT NULL));
