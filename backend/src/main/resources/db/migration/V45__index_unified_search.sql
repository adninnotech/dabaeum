-- 평생학습플랫폼 고도화 계획 6 — 통합검색 부분일치 인덱스 (설계서 §5.2). pg_trgm 은 V34 에서 설치했다.
-- 2자 검색어는 트라이그램 인덱스를 못 타지만 데이터 규모에서 허용한다.

CREATE INDEX ix_tb_courses_title_trgm ON tb_courses USING gin (LOWER(title) gin_trgm_ops);
CREATE INDEX ix_tb_institutions_name_trgm ON tb_institutions USING gin (LOWER(name) gin_trgm_ops);
CREATE INDEX ix_tb_notices_title_trgm ON tb_notices USING gin (LOWER(title) gin_trgm_ops);
CREATE INDEX ix_tb_faqs_question_trgm ON tb_faqs USING gin (LOWER(question) gin_trgm_ops);
