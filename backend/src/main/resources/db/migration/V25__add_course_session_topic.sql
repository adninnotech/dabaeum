-- 회차 커리큘럼의 "주제" 열. 온라인 회차는 날짜·시간 없이 주제만 갖는다.
ALTER TABLE tb_course_sessions
    ADD COLUMN topic VARCHAR(200);

ALTER TABLE tb_course_sessions
    ALTER COLUMN starts_at DROP NOT NULL,
    ALTER COLUMN ends_at DROP NOT NULL;

-- 둘 다 있거나 둘 다 없거나. 한쪽만 있는 상태는 허용하지 않는다.
ALTER TABLE tb_course_sessions ADD CONSTRAINT ck_tb_course_sessions_time_pair
    CHECK ((starts_at IS NULL) = (ends_at IS NULL));
