-- 시연 중 DRAFT 로 뺀 QA 시험 강좌를 원래 상태로 되돌린다 (docs/specs/usage-statistics.md §12 결정 12). dabaeum_dev 전용.
-- 시연 중 누가 상태를 바꿨으면(DRAFT 가 아니면) 덮어쓰지 않고 목록으로 알린다. 되돌린 뒤 백업과 대조 결과를 찍는다.

DO $$
BEGIN
    IF current_database() <> 'dabaeum_dev' THEN
        RAISE EXCEPTION 'demo course hiding is for dabaeum_dev only (current: %)', current_database();
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS demo_qa_course_backup (id UUID NOT NULL PRIMARY KEY, status VARCHAR(30) NOT NULL,
                                                  updated_at TIMESTAMPTZ NOT NULL);

SELECT 'changed during demo (not restored)' AS note, c.title, c.status AS now, b.status AS original
  FROM demo_qa_course_backup b JOIN tb_courses c ON c.id = b.id WHERE c.status <> 'DRAFT';

UPDATE tb_courses c SET status = b.status
  FROM demo_qa_course_backup b WHERE b.id = c.id AND c.status = 'DRAFT';

SELECT COUNT(*) AS backed_up,
       COUNT(*) FILTER (WHERE c.status = b.status) AS matches_original
  FROM demo_qa_course_backup b JOIN tb_courses c ON c.id = b.id;

DROP TABLE demo_qa_course_backup;
