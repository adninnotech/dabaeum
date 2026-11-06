-- 가상 데이터 규모 요약(읽기만 한다)
SELECT (SELECT COUNT(*) FROM tb_institutions WHERE institution_code LIKE 'SYN-%' AND deleted_at IS NULL) AS institutions,
       (SELECT COUNT(*) FROM tb_courses WHERE course_code LIKE 'SYN-%' AND deleted_at IS NULL) AS courses,
       (SELECT COUNT(*) FROM tb_users WHERE email LIKE '%@synthetic.invalid') AS members,
       (SELECT COUNT(*) FROM tb_enrollments e JOIN tb_users u ON u.id = e.user_id WHERE u.email LIKE '%@synthetic.invalid') AS enrollments,
       (SELECT COUNT(*) FROM tb_completions cp JOIN tb_enrollments e ON e.id = cp.enrollment_id JOIN tb_users u ON u.id = e.user_id
         WHERE u.email LIKE '%@synthetic.invalid' AND cp.status = 'COMPLETED') AS completions;
SELECT c.status, COUNT(*) AS courses, ROUND(AVG(c.capacity), 1) AS avg_capacity,
       ROUND(AVG((SELECT COUNT(*) FROM tb_enrollments e WHERE e.course_id = c.id AND e.status = 'APPROVED')), 1) AS avg_approved,
       MAX((SELECT COUNT(*) FROM tb_enrollments e WHERE e.course_id = c.id AND e.status = 'APPROVED')) AS max_approved,
       ROUND(AVG((SELECT COUNT(*) FROM tb_enrollments e WHERE e.course_id = c.id AND e.status = 'APPLIED')), 1) AS avg_pending
  FROM tb_courses c WHERE c.course_code LIKE 'SYN-%' AND c.deleted_at IS NULL GROUP BY 1 ORDER BY 1;
