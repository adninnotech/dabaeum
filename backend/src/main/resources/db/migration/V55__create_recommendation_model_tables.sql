-- 강좌 추천 2차 — 모델 점수 배치 기록과 회원별 점수 (docs/specs/course-recommendation.md §12.5)
-- 밤 배치(ml/recommend/run.py)가 run 1행을 쓰고, 한 트랜잭션에서 점수 테이블 전체를 최신 run 의 행으로 교체한다.
-- 보존: 점수는 최신 run 의 행만, run 기록은 1년(data-retention-policy.md). 점수는 탈퇴·강좌 삭제 시 CASCADE.

CREATE TABLE tb_recommendation_model_runs (
    id                   UUID         NOT NULL,
    started_at           TIMESTAMPTZ  NOT NULL,
    finished_at          TIMESTAMPTZ,
    result               VARCHAR(30)  NOT NULL,
    model_type           VARCHAR(20),
    positive_samples     INTEGER,
    negative_samples     INTEGER,
    user_count           INTEGER,
    course_count         INTEGER,
    auc                  NUMERIC(5,4),
    precision_at_6       NUMERIC(5,4),
    rules_precision_at_6 NUMERIC(5,4),
    feature_importance   JSONB,
    report_path          VARCHAR(300),
    message              VARCHAR(500),
    CONSTRAINT pk_tb_recommendation_model_runs PRIMARY KEY (id),
    CONSTRAINT ck_tb_recommendation_model_runs_result
        CHECK (result IN ('RUNNING', 'TRAINED', 'SKIPPED_INSUFFICIENT_DATA', 'SKIPPED_WORSE_THAN_RULES', 'FAILED')),
    CONSTRAINT ck_tb_recommendation_model_runs_model_type
        CHECK (model_type IS NULL OR model_type IN ('XGBOOST', 'RANDOM_FOREST'))
);
CREATE INDEX ix_tb_recommendation_model_runs_started ON tb_recommendation_model_runs (started_at DESC);

-- score: 신청 확률(0~1). top_factor: 그 강좌 점수에 가장 크게 기여한 feature 묶음(§12.5 reason.topFactor).
CREATE TABLE tb_course_recommendation_scores (
    user_id      UUID          NOT NULL,
    course_id    UUID          NOT NULL,
    score        NUMERIC(6,5)  NOT NULL,
    top_factor   VARCHAR(20)   NOT NULL,
    run_id       UUID          NOT NULL,
    computed_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_tb_course_recommendation_scores PRIMARY KEY (user_id, course_id),
    CONSTRAINT fk_tb_course_recommendation_scores_user   FOREIGN KEY (user_id)   REFERENCES tb_users (id)   ON DELETE CASCADE,
    CONSTRAINT fk_tb_course_recommendation_scores_course FOREIGN KEY (course_id) REFERENCES tb_courses (id) ON DELETE CASCADE,
    CONSTRAINT fk_tb_course_recommendation_scores_run    FOREIGN KEY (run_id)    REFERENCES tb_recommendation_model_runs (id),
    CONSTRAINT ck_tb_course_recommendation_scores_score CHECK (score >= 0 AND score <= 1),
    CONSTRAINT ck_tb_course_recommendation_scores_top_factor
        CHECK (top_factor IN ('CLASSIFICATION', 'REGION', 'AGE_BAND', 'PRICE', 'SCHEDULE', 'POPULARITY'))
);
CREATE INDEX ix_tb_course_recommendation_scores_user ON tb_course_recommendation_scores (user_id, score DESC);

-- §12.5 노출 로그는 그대로 남긴다. basis=MODEL·reason_type=MODEL_SCORE 항목도 기록되도록 V54 의 CHECK 를 넓힌다.
ALTER TABLE tb_recommendation_impressions DROP CONSTRAINT ck_tb_recommendation_impressions_basis;
ALTER TABLE tb_recommendation_impressions ADD CONSTRAINT ck_tb_recommendation_impressions_basis
    CHECK (basis IN ('MODEL', 'PERSONAL', 'AGE_BAND', 'REGION', 'POPULAR'));
ALTER TABLE tb_recommendation_impressions DROP CONSTRAINT ck_tb_recommendation_impressions_reason;
ALTER TABLE tb_recommendation_impressions ADD CONSTRAINT ck_tb_recommendation_impressions_reason
    CHECK (reason_type IN ('MODEL_SCORE', 'SIMILAR_CLASSIFICATION', 'POPULAR_AGE_BAND', 'POPULAR_REGION', 'POPULAR'));
