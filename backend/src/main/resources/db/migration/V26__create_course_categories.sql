-- 강좌 카테고리 마스터. Course.category(자유 문자열)는 그대로 두고,
-- 화면 셀렉트의 데이터원으로만 쓴다. 참조 무결성은 아직 걸지 않는다.
CREATE TABLE tb_course_categories (
    id UUID NOT NULL,
    name VARCHAR(50) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT pk_tb_course_categories PRIMARY KEY (id)
);

-- 살아 있는 카테고리끼리만 이름이 유일하다. 삭제 후 같은 이름 재등록을 허용한다.
CREATE UNIQUE INDEX uq_tb_course_categories_name_active
    ON tb_course_categories (name) WHERE deleted_at IS NULL;

CREATE INDEX ix_tb_course_categories_order
    ON tb_course_categories (sort_order, name) WHERE deleted_at IS NULL;

-- B-9 화면의 초기 9개.
INSERT INTO tb_course_categories (id, name, sort_order) VALUES
    (gen_random_uuid(), '인문교양',    1),
    (gen_random_uuid(), '사회복지',    2),
    (gen_random_uuid(), '교육/직무',   3),
    (gen_random_uuid(), 'IT/디지털',   4),
    (gen_random_uuid(), '문화/예술',   5),
    (gen_random_uuid(), '건강/웰빙',   6),
    (gen_random_uuid(), '취업/창업',   7),
    (gen_random_uuid(), '시민참여',    8),
    (gen_random_uuid(), '기타',        9);
