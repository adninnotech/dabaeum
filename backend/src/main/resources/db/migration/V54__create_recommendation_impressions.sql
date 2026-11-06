-- 강좌 추천 노출·클릭 로그 (docs/specs/course-recommendation.md §7).
-- 2차 고도화(협업 필터링·가중치 조정)의 학습 데이터다. 로그인 사용자 응답만 남긴다. 보존 1년(data-retention-policy.md).
CREATE TABLE tb_recommendation_impressions (
    id           UUID        NOT NULL,
    user_id      UUID        NOT NULL,
    course_id    UUID        NOT NULL,
    basis        VARCHAR(20) NOT NULL,
    reason_type  VARCHAR(30) NOT NULL,
    match_level  SMALLINT    NOT NULL,
    position     SMALLINT    NOT NULL,
    shown_at     TIMESTAMPTZ NOT NULL,
    clicked_at   TIMESTAMPTZ,
    CONSTRAINT pk_tb_recommendation_impressions PRIMARY KEY (id),
    CONSTRAINT fk_tb_recommendation_impressions_user
        FOREIGN KEY (user_id) REFERENCES tb_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_tb_recommendation_impressions_course
        FOREIGN KEY (course_id) REFERENCES tb_courses (id) ON DELETE CASCADE,
    CONSTRAINT ck_tb_recommendation_impressions_basis
        CHECK (basis IN ('PERSONAL', 'AGE_BAND', 'REGION', 'POPULAR')),
    CONSTRAINT ck_tb_recommendation_impressions_reason
        CHECK (reason_type IN ('SIMILAR_CLASSIFICATION', 'POPULAR_AGE_BAND', 'POPULAR_REGION', 'POPULAR')),
    CONSTRAINT ck_tb_recommendation_impressions_match_level CHECK (match_level BETWEEN 0 AND 3),
    CONSTRAINT ck_tb_recommendation_impressions_position CHECK (position >= 1)
);

CREATE INDEX ix_tb_recommendation_impressions_user_shown
    ON tb_recommendation_impressions (user_id, shown_at DESC);
CREATE INDEX ix_tb_recommendation_impressions_shown
    ON tb_recommendation_impressions (shown_at);
