-- 이용 통계 시연용 더미 데이터 삭제 (docs/specs/usage-statistics.md §12). dabaeum_dev 전용.
-- 'demo' 로 시작하는 익명 키 행을 모두 지우고, 가장 이른 더미 날짜부터 집계 완료 기록을 지워 다시 집계하게 한다.

DO $$
BEGIN
    IF current_database() <> 'dabaeum_dev' THEN
        RAISE EXCEPTION 'usage demo data is for dabaeum_dev only (current: %)', current_database();
    END IF;
END $$;

DROP TABLE IF EXISTS demo_removed_from;
CREATE TEMP TABLE demo_removed_from AS
SELECT LEAST((SELECT MIN(event_date) FROM tb_usage_events WHERE viewer_key LIKE 'demo%'),
             (SELECT MIN(view_date) FROM tb_course_views WHERE viewer_key LIKE 'demo%')) AS f;

-- 이어 넣은 수강 신청(§12 결정 16)도 지운다. 처음부터 다시 집계해야 빠지므로 그 날짜도 다시 집계 범위에 든다.
CREATE TABLE IF NOT EXISTS demo_added_enrollments (id UUID NOT NULL PRIMARY KEY, added_on DATE NOT NULL);
DELETE FROM tb_enrollments e USING demo_added_enrollments a WHERE e.id = a.id;
DROP TABLE demo_added_enrollments;

DELETE FROM tb_usage_events WHERE viewer_key LIKE 'demo%';
DELETE FROM tb_course_views WHERE viewer_key LIKE 'demo%';
DELETE FROM tb_usage_aggregated_days a USING demo_removed_from r WHERE r.f IS NOT NULL AND a.stat_date >= r.f;

-- 가상 회원의 관심 분야와 그 동의 기록(add-interests.sql 이 넣은 것)도 지운다. 가상 회원 자체는 지우지 않는다.
DELETE FROM tb_user_interests i USING tb_users u WHERE u.id = i.user_id AND u.email LIKE '%@synthetic.invalid';
DELETE FROM tb_user_agreements a USING tb_users u
 WHERE u.id = a.user_id AND u.email LIKE '%@synthetic.invalid' AND a.agreement_type = 'INTEREST';
