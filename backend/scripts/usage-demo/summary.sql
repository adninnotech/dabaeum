-- 이용 통계 더미 데이터 미리보기. 더미('demo') 행만 센다.
\echo '== 월별 더미 건수 (방문 / 검색 / 배너 클릭 / 강좌 조회)'
SELECT m, SUM(visits) AS visits, SUM(searches) AS searches, SUM(clicks) AS banner_clicks, SUM(views) AS course_views
  FROM (SELECT to_char(event_date, 'YYYY-MM') AS m,
               COUNT(*) FILTER (WHERE event_type = 'VISIT') AS visits,
               COUNT(*) FILTER (WHERE event_type = 'SEARCH') AS searches,
               COUNT(*) FILTER (WHERE event_type = 'BANNER_CLICK') AS clicks, 0 AS views
          FROM tb_usage_events WHERE viewer_key LIKE 'demo%' GROUP BY 1
        UNION ALL
        SELECT to_char(view_date, 'YYYY-MM'), 0, 0, 0, COUNT(*)
          FROM tb_course_views WHERE viewer_key LIKE 'demo%' GROUP BY 1) x
 GROUP BY m ORDER BY m;
\echo '== 최근 14일 방문자'
SELECT event_date, to_char(event_date, 'Dy') AS dow, COUNT(*) AS visitors
  FROM tb_usage_events WHERE viewer_key LIKE 'demo%' AND event_type = 'VISIT'
 GROUP BY event_date ORDER BY event_date DESC LIMIT 14;
\echo '== 검색어 상위 15 (결과 0건 여부)'
SELECT target_key AS keyword, COUNT(*) AS searches, bool_or(zero_result) AS zero_result
  FROM tb_usage_events WHERE viewer_key LIKE 'demo%' AND event_type = 'SEARCH'
 GROUP BY 1 ORDER BY 2 DESC LIMIT 15;
\echo '== 결과 0건 검색어'
SELECT target_key AS keyword, COUNT(*) AS searches
  FROM tb_usage_events WHERE viewer_key LIKE 'demo%' AND event_type = 'SEARCH' AND zero_result
 GROUP BY 1 ORDER BY 2 DESC;
\echo '== 방문 회원·성별·연령대'
SELECT member_type, gender, COUNT(*) FROM tb_usage_events
 WHERE viewer_key LIKE 'demo%' AND event_type = 'VISIT' GROUP BY 1, 2 ORDER BY 1, 3 DESC;
SELECT age_band, COUNT(*) FROM tb_usage_events
 WHERE viewer_key LIKE 'demo%' AND event_type = 'VISIT' AND member_type = 'MEMBER' GROUP BY 1 ORDER BY 1;
