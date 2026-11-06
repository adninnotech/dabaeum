-- 시연 중에만 QA 시험 강좌를 공개 목록에서 뺀다 (docs/specs/usage-statistics.md §12 결정 12). dabaeum_dev 전용.
-- 대상: 가상 데이터(SYN-)가 아니고 제목이 'QA' 로 시작하는 공개 강좌. 상태만 DRAFT 로 바꾸고 원래 상태는
-- demo_qa_course_backup 에 남긴다. 신청·출결·이수·배지는 건드리지 않는다. 기관은 건드리지 않는다(QA 요청).
-- 시연이 끝나면 바로 show-qa-courses.sql 로 되돌린다. 바꾸기 전·되돌린 뒤 QA 세션에 알린다.

DO $$
BEGIN
    IF current_database() <> 'dabaeum_dev' THEN
        RAISE EXCEPTION 'demo course hiding is for dabaeum_dev only (current: %)', current_database();
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS demo_qa_course_backup (
    id         UUID        NOT NULL PRIMARY KEY,
    status     VARCHAR(30) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO demo_qa_course_backup (id, status, updated_at)
SELECT id, status, updated_at FROM tb_courses
 WHERE deleted_at IS NULL AND status NOT IN ('DRAFT', 'CANCELLED')
   AND course_code NOT LIKE 'SYN-%' AND title LIKE 'QA%'
ON CONFLICT DO NOTHING;

UPDATE tb_courses c SET status = 'DRAFT'
  FROM demo_qa_course_backup b WHERE b.id = c.id AND c.status <> 'DRAFT';
