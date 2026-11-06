-- 회원 구·군과 평생학번 제거 (docs/specs/interest-based-member-profile.md §4).
-- 삭제 마이그레이션이다. 되돌리려면 백업 복원뿐이다.
-- 이 파일 하나로 끝낸다: V61 까지의 같은 jar 는 이 컬럼을 읽지도 쓰지도 않으므로 적용 전후에 모두 동작한다.
-- 순서: 트리거 -> 함수 -> 카운터 테이블 -> 컬럼. (컬럼을 먼저 지우면 트리거 함수가 깨진다.)

-- 1. 평생학번 트리거와 함수 (V39)
DROP TRIGGER IF EXISTS trg_tb_users_issue_learner_no ON tb_users;
DROP TRIGGER IF EXISTS trg_tb_users_keep_learner_no ON tb_users;
DROP FUNCTION IF EXISTS fn_tb_users_issue_learner_no();
DROP FUNCTION IF EXISTS fn_tb_users_keep_learner_no();

-- 2. 신청 시점 구·군 스냅샷 트리거와 함수 (V44). 컬럼보다 먼저 지운다.
DROP TRIGGER IF EXISTS trg_tb_enrollments_snapshot_region ON tb_enrollments;
DROP FUNCTION IF EXISTS fn_tb_enrollments_snapshot_region();

-- 3. 평생학번 일련번호 카운터 (V39)
DROP TABLE IF EXISTS tb_learner_no_counters;

-- 4. 회원 컬럼. 딸린 제약(uq_tb_users_learner_no, ck_tb_users_learner_no, ck_tb_users_region_code)도 함께 사라진다.
ALTER TABLE tb_users
    DROP COLUMN IF EXISTS learner_no,
    DROP COLUMN IF EXISTS region_code,
    DROP COLUMN IF EXISTS region;

-- 5. 수강 신청의 구·군 스냅샷 컬럼 (V44). ck_tb_enrollments_applicant_region_code 도 함께 사라진다.
ALTER TABLE tb_enrollments
    DROP COLUMN IF EXISTS applicant_region_code;

-- 6. 인기 집계의 REGION 세그먼트. 행을 먼저 지운 뒤 CHECK 를 줄인다(V44 의 ck_tb_course_popularity_segment).
DELETE FROM tb_course_popularity WHERE segment_type = 'REGION';
ALTER TABLE tb_course_popularity DROP CONSTRAINT ck_tb_course_popularity_segment;
ALTER TABLE tb_course_popularity ADD CONSTRAINT ck_tb_course_popularity_segment
    CHECK (segment_type IN ('ALL', 'AGE_BAND'));

-- 남기는 것: tb_recommendation_impressions 의 basis·reason_type CHECK 에 있는 옛 REGION·POPULAR_REGION 값과
-- tb_course_recommendation_scores.top_factor 의 REGION 은 이력이라 지우지 않는다(V61 이 허용으로 둔다).
-- tb_public_courses.region_code 와 tb_institutions.region 은 회원 정보가 아니므로 건드리지 않는다.
-- tb_course_views 에는 구·군 스냅샷 컬럼이 없다(V44 가 만든 것은 enrollments 스냅샷뿐).
