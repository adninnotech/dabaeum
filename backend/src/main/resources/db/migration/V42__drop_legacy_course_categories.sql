-- 평생학습플랫폼 고도화 계획 3 — 옛 강좌 카테고리 제거 (설계서 §12.1)
-- 강좌는 이제 표준 분류 classification_code(V41)만 쓴다. 옛 자유 문자열과 평면 카테고리 마스터(V26)는
-- 운영 데이터가 없어 이관하지 않고 지운다. 코드는 이 마이그레이션 전에 이미 두 곳을 읽지 않는다.

ALTER TABLE tb_courses DROP COLUMN category;

DROP TABLE tb_course_categories;
