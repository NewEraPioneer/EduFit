-- Demo (chỉ profile dev): bổ sung dữ liệu theo mô hình nhu cầu học / gói dạy mới
-- để màn AI Match, Discovery và hộp thư gia sư có đủ mục tiêu, bộ sách, tỉnh dự thi.

-- Nhu cầu demo: Toán lớp 9, Luyện thi vào 10 tại Hà Nội
UPDATE learning_need
SET exam_province_id = (SELECT province_id FROM province WHERE code = 'HA_NOI')
WHERE need_id = '55555555-5555-5555-5555-000000000001' AND exam_province_id IS NULL;

UPDATE learning_need n
SET status = 'ACTIVE'
WHERE n.need_id = '55555555-5555-5555-5555-000000000001'
  AND n.status = 'INCOMPLETE'
  AND n.education_level_id IS NOT NULL
  AND n.goal_tag_id IS NOT NULL
  AND n.mode IS NOT NULL
  AND (n.mode = 'ONLINE' OR n.province_id IS NOT NULL)
  AND n.budget_max_per_session IS NOT NULL
  AND EXISTS (SELECT 1 FROM learning_need_slot s WHERE s.need_id = n.need_id);

-- Loại gia sư đã được admin xác nhận
UPDATE tutor_profile SET tutor_type = 'IN_SERVICE_TEACHER', tutor_type_verified = true
WHERE tutor_id IN ('22222222-2222-2222-2222-000000000002', '22222222-2222-2222-2222-000000000005');
UPDATE tutor_profile SET tutor_type = 'GRADUATE', tutor_type_verified = true
WHERE tutor_id IN ('22222222-2222-2222-2222-000000000003', '22222222-2222-2222-2222-000000000004');
UPDATE tutor_profile SET tutor_type = 'UNIVERSITY_STUDENT', tutor_type_verified = false
WHERE tutor_id = '22222222-2222-2222-2222-000000000006';

-- Gói dạy Toán: mỗi gia sư một hồ sơ mục tiêu khác nhau để thấy rõ xếp hạng lọc mềm
INSERT INTO tutor_offering_goal (offering_id, goal_tag_id, evidence_verified)
SELECT o.offering_id, g.goal_tag_id, g.is_premium AND o.tutor_id = '22222222-2222-2222-2222-000000000004'
FROM tutor_offering o
JOIN goal_tag g ON g.code IN ('FOUNDATION', 'GRADE_BOOST', 'GRADE10_EXAM', 'GIFTED')
WHERE o.subject_id = (SELECT subject_id FROM subject WHERE name = 'Toán')
  AND (
    (o.tutor_id = '22222222-2222-2222-2222-000000000002' AND g.code IN ('GRADE_BOOST', 'GRADE10_EXAM'))
    OR (o.tutor_id = '22222222-2222-2222-2222-000000000003' AND g.code IN ('FOUNDATION', 'GRADE_BOOST'))
    OR (o.tutor_id = '22222222-2222-2222-2222-000000000004' AND g.code IN ('GIFTED', 'GRADE10_EXAM'))
    OR (o.tutor_id = '22222222-2222-2222-2222-000000000005' AND g.code IN ('FOUNDATION', 'GRADE10_EXAM'))
    OR (o.tutor_id = '22222222-2222-2222-2222-000000000006' AND g.code IN ('FOUNDATION', 'GRADE_BOOST', 'GIFTED'))
    OR (o.tutor_id = '22222222-2222-2222-2222-000000000001' AND g.code IN ('FOUNDATION', 'GRADE_BOOST', 'GRADE10_EXAM'))
  )
ON CONFLICT DO NOTHING;

INSERT INTO tutor_offering_level (offering_id, level_id)
SELECT o.offering_id, l.level_id
FROM tutor_offering o
JOIN education_level l ON l.name = 'Lớp 8'
WHERE o.tutor_id IN ('22222222-2222-2222-2222-000000000002', '22222222-2222-2222-2222-000000000005')
ON CONFLICT DO NOTHING;

INSERT INTO tutor_offering_textbook (offering_id, series_id)
SELECT o.offering_id, ts.series_id
FROM tutor_offering o
JOIN textbook_series ts ON ts.code IN ('KNTT', 'CTST')
WHERE EXISTS (
    SELECT 1 FROM tutor_offering_goal og JOIN goal_tag g ON g.goal_tag_id = og.goal_tag_id
    WHERE og.offering_id = o.offering_id AND g.requires_textbook)
ON CONFLICT DO NOTHING;

INSERT INTO tutor_offering_exam_province (offering_id, province_id)
SELECT o.offering_id, p.province_id
FROM tutor_offering o
JOIN province p ON p.code = 'HA_NOI'
WHERE EXISTS (
    SELECT 1 FROM tutor_offering_goal og JOIN goal_tag g ON g.goal_tag_id = og.goal_tag_id
    WHERE og.offering_id = o.offering_id AND g.requires_exam_province)
ON CONFLICT DO NOTHING;
