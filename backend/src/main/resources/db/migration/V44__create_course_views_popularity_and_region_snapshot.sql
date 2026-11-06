-- 평생학습플랫폼 고도화 계획 5 — 조회 로그·인기 집계·신청 구군 스냅샷 (설계서 §8, §9)

-- 계획 4 후속: 배지 정의 이미지용 파일 용도.
ALTER TABLE tb_files DROP CONSTRAINT ck_tb_files_purpose;
ALTER TABLE tb_files ADD CONSTRAINT ck_tb_files_purpose
    CHECK (purpose IN
        ('PROFILE', 'COURSE_THUMBNAIL', 'INQUIRY', 'COURSE_ATTACHMENT', 'INSTITUTION_LOGO',
         'NOTICE_ATTACHMENT', 'BANNER', 'BADGE_IMAGE'));

-- §8.1 비로그인 조회자 익명화용 일별 salt. 서버마다 만들면 같은 사람이 두 번 세지므로 DB 에 하루 1행.
CREATE TABLE tb_view_salts (
    view_date DATE  NOT NULL,
    salt      BYTEA NOT NULL,
    CONSTRAINT pk_tb_view_salts PRIMARY KEY (view_date)
);

-- §8.1 강좌 상세 조회. 같은 사람이 하루에 몇 번 열어도 PK 로 1건. IP·UA 원문은 저장하지 않는다.
CREATE TABLE tb_course_views (
    course_id  UUID        NOT NULL,
    viewer_key CHAR(64)    NOT NULL,
    view_date  DATE        NOT NULL,
    user_id    UUID,
    viewed_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_course_views PRIMARY KEY (course_id, viewer_key, view_date),
    CONSTRAINT fk_tb_course_views_course FOREIGN KEY (course_id) REFERENCES tb_courses (id) ON DELETE CASCADE
);
CREATE INDEX ix_tb_course_views_date ON tb_course_views (view_date);

-- §8.2 인기 집계. 배치가 통째로 다시 쓴다.
CREATE TABLE tb_course_popularity (
    course_id        UUID        NOT NULL,
    segment_type     VARCHAR(20) NOT NULL,
    segment_key      VARCHAR(10) NOT NULL,
    window_days      INTEGER     NOT NULL,
    enrollment_count INTEGER     NOT NULL,
    view_count       INTEGER     NOT NULL,
    score            NUMERIC     NOT NULL,
    computed_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_tb_course_popularity PRIMARY KEY (course_id, segment_type, segment_key, window_days),
    CONSTRAINT fk_tb_course_popularity_course FOREIGN KEY (course_id) REFERENCES tb_courses (id) ON DELETE CASCADE,
    CONSTRAINT ck_tb_course_popularity_segment CHECK (segment_type IN ('ALL', 'REGION', 'AGE_BAND'))
);
CREATE INDEX ix_tb_course_popularity_rank
    ON tb_course_popularity (segment_type, segment_key, window_days, score DESC);

-- §9.3 신청 시점 구·군 스냅샷. 이사해도 과거 통계가 바뀌지 않는다.
ALTER TABLE tb_enrollments ADD COLUMN applicant_region_code CHAR(2);

UPDATE tb_enrollments e
   SET applicant_region_code = u.region_code
  FROM tb_users u
 WHERE u.id = e.user_id;

ALTER TABLE tb_enrollments
    ALTER COLUMN applicant_region_code SET NOT NULL,
    ADD CONSTRAINT ck_tb_enrollments_applicant_region_code
        CHECK (applicant_region_code IN ('11', '14', '17', '20', '23', '26', '29', '71', '72', '99'));

-- 신청을 만드는 모든 경로(본인·대리·일괄·테스트 SQL)가 앱 수정 없이 채워지도록 트리거로 복사한다.
CREATE FUNCTION fn_tb_enrollments_snapshot_region() RETURNS trigger AS $$
BEGIN
    SELECT region_code INTO NEW.applicant_region_code FROM tb_users WHERE id = NEW.user_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_tb_enrollments_snapshot_region
    BEFORE INSERT ON tb_enrollments
    FOR EACH ROW EXECUTE FUNCTION fn_tb_enrollments_snapshot_region();
