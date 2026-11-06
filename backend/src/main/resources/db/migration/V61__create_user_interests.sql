-- 회원 관심 분야 (docs/specs/interest-based-member-profile.md §4). 추가만 한다. 옛 서버에 영향이 없다.
CREATE TABLE tb_user_interests (
    user_id             UUID        NOT NULL,
    classification_code VARCHAR(11) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tb_user_interests PRIMARY KEY (user_id, classification_code),
    CONSTRAINT fk_tb_user_interests_user
        FOREIGN KEY (user_id) REFERENCES tb_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_tb_user_interests_classification
        FOREIGN KEY (classification_code) REFERENCES tb_course_classifications (code)
);

ALTER TABLE tb_user_agreements DROP CONSTRAINT ck_tb_user_agreements_type;
ALTER TABLE tb_user_agreements ADD CONSTRAINT ck_tb_user_agreements_type
    CHECK (agreement_type IN ('USAGE', 'PRIVACY', 'MARKETING', 'INTEREST'));

-- 추천 기준에 관심 분야를 더한다. 옛 REGION 값은 이력이라 그대로 허용한다.
ALTER TABLE tb_recommendation_impressions DROP CONSTRAINT ck_tb_recommendation_impressions_basis;
ALTER TABLE tb_recommendation_impressions ADD CONSTRAINT ck_tb_recommendation_impressions_basis
    CHECK (basis IN ('MODEL', 'PERSONAL', 'INTEREST', 'AGE_BAND', 'REGION', 'POPULAR'));
ALTER TABLE tb_recommendation_impressions DROP CONSTRAINT ck_tb_recommendation_impressions_reason;
ALTER TABLE tb_recommendation_impressions ADD CONSTRAINT ck_tb_recommendation_impressions_reason
    CHECK (reason_type IN ('MODEL_SCORE', 'SIMILAR_CLASSIFICATION', 'INTEREST', 'POPULAR_AGE_BAND', 'POPULAR_REGION', 'POPULAR'));
ALTER TABLE tb_course_recommendation_scores DROP CONSTRAINT ck_tb_course_recommendation_scores_top_factor;
ALTER TABLE tb_course_recommendation_scores ADD CONSTRAINT ck_tb_course_recommendation_scores_top_factor
    CHECK (top_factor IN ('CLASSIFICATION', 'INTEREST', 'REGION', 'AGE_BAND', 'PRICE', 'SCHEDULE', 'POPULARITY'));
