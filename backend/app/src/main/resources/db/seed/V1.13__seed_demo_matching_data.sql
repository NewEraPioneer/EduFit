-- Demo data used by the student matching screen.
-- All tutors are persisted and verified; the frontend must not maintain a duplicate sample list.

INSERT INTO account (user_id, email, password_hash, full_name, role, status) VALUES
    ('22222222-2222-2222-2222-222222222223', 'tutor.ha@edufit.vn', '$argon2id$v=19$m=65536,t=3,p=1$iOJcfWv2oO7dZrIj8BcOmA$OJP7aGorP6uOVeqsGRw4sq7Nqezhpo9svm0dHTIc1H8', 'Trần Thu Hà', 'TUTOR', 'ACTIVE'),
    ('22222222-2222-2222-2222-222222222224', 'tutor.quan@edufit.vn', '$argon2id$v=19$m=65536,t=3,p=1$iOJcfWv2oO7dZrIj8BcOmA$OJP7aGorP6uOVeqsGRw4sq7Nqezhpo9svm0dHTIc1H8', 'Lê Minh Quân', 'TUTOR', 'ACTIVE'),
    ('22222222-2222-2222-2222-222222222225', 'tutor.an@edufit.vn', '$argon2id$v=19$m=65536,t=3,p=1$iOJcfWv2oO7dZrIj8BcOmA$OJP7aGorP6uOVeqsGRw4sq7Nqezhpo9svm0dHTIc1H8', 'Phạm Ngọc An', 'TUTOR', 'ACTIVE'),
    ('22222222-2222-2222-2222-222222222226', 'tutor.nam@edufit.vn', '$argon2id$v=19$m=65536,t=3,p=1$iOJcfWv2oO7dZrIj8BcOmA$OJP7aGorP6uOVeqsGRw4sq7Nqezhpo9svm0dHTIc1H8', 'Nguyễn Hoàng Nam', 'TUTOR', 'ACTIVE'),
    ('22222222-2222-2222-2222-222222222227', 'tutor.thao@edufit.vn', '$argon2id$v=19$m=65536,t=3,p=1$iOJcfWv2oO7dZrIj8BcOmA$OJP7aGorP6uOVeqsGRw4sq7Nqezhpo9svm0dHTIc1H8', 'Đặng Thị Thảo', 'TUTOR', 'ACTIVE')
ON CONFLICT (email) DO NOTHING;

INSERT INTO tutor_profile (
    tutor_id, user_id, display_name, headline, bio, teaching_mode, area,
    price_per_session, experience_years, teaching_method, status, verified_at,
    rating_avg, review_count
) VALUES
    ('22222222-2222-2222-2222-000000000002', '22222222-2222-2222-2222-222222222223',
     'Cô Trần Thu Hà', 'Thủ khoa Sư phạm Toán · Chuyên ôn thi vào lớp 10',
     'Sáu năm đồng hành cùng học sinh THCS, tập trung củng cố nền tảng và kỹ năng giải đề.',
     'BOTH', 'Cầu Giấy, Hà Nội', 250000, 6, 'Lộ trình theo tuần kết hợp luyện đề tuyển sinh.',
     'VERIFIED', NOW(), 4.90, 128),
    ('22222222-2222-2222-2222-000000000003', '22222222-2222-2222-2222-222222222224',
     'Thầy Lê Minh Quân', 'Gia sư Toán THCS · Phương pháp phản xạ nhanh',
     'Năm năm kinh nghiệm hỗ trợ học sinh mất gốc và nâng cao điểm kiểm tra trên lớp.',
     'BOTH', 'Nam Từ Liêm, Hà Nội', 220000, 5, 'Chẩn đoán lỗ hổng và luyện bài theo chuyên đề.',
     'VERIFIED', NOW(), 4.80, 96),
    ('22222222-2222-2222-2222-000000000004', '22222222-2222-2222-2222-222222222225',
     'Cô Phạm Ngọc An', 'Cử nhân Sư phạm Toán chất lượng cao',
     'Bốn năm bồi dưỡng học sinh khá giỏi THCS và luyện thi chuyên.',
     'ONLINE', NULL, 280000, 4, 'Sơ đồ tư duy, bài tập phân tầng và phản hồi sau mỗi buổi.',
     'VERIFIED', NOW(), 4.80, 84),
    ('22222222-2222-2222-2222-000000000005', '22222222-2222-2222-2222-222222222226',
     'Thầy Nguyễn Hoàng Nam', 'Thạc sĩ Toán học · Củng cố kiến thức THCS',
     'Kiên nhẫn với học sinh mất gốc, xây dựng thói quen tự học bền vững.',
     'BOTH', 'Thanh Xuân, Hà Nội', 260000, 7, 'Học từ bản chất, luyện tập có hướng dẫn và đánh giá định kỳ.',
     'VERIFIED', NOW(), 4.90, 110),
    ('22222222-2222-2222-2222-000000000006', '22222222-2222-2222-2222-222222222227',
     'Cô Đặng Thị Thảo', 'Gia sư Toán THCS · Học trực quan, dễ hiểu',
     'Theo sát từng chuyên đề và điều chỉnh tốc độ học theo năng lực của học sinh.',
     'ONLINE', NULL, 230000, 4, 'Minh họa trực quan và luyện tập ngắn sau từng nội dung.',
     'VERIFIED', NOW(), 4.70, 65)
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO tutor_subject (tutor_subject_id, tutor_id, subject_id, education_level_id) VALUES
    (gen_random_uuid(), '22222222-2222-2222-2222-000000000002', 1, 4),
    (gen_random_uuid(), '22222222-2222-2222-2222-000000000003', 1, 4),
    (gen_random_uuid(), '22222222-2222-2222-2222-000000000004', 1, 4),
    (gen_random_uuid(), '22222222-2222-2222-2222-000000000005', 1, 4),
    (gen_random_uuid(), '22222222-2222-2222-2222-000000000006', 1, 4)
ON CONFLICT (tutor_id, subject_id, education_level_id) DO NOTHING;

INSERT INTO tutor_availability_slot (slot_id, tutor_id, day_of_week, start_time, end_time) VALUES
    (gen_random_uuid(), '22222222-2222-2222-2222-000000000001', 2, '19:00', '21:00'),
    (gen_random_uuid(), '22222222-2222-2222-2222-000000000002', 2, '18:30', '21:00'),
    (gen_random_uuid(), '22222222-2222-2222-2222-000000000002', 5, '19:00', '21:00'),
    (gen_random_uuid(), '22222222-2222-2222-2222-000000000003', 2, '19:00', '20:30'),
    (gen_random_uuid(), '22222222-2222-2222-2222-000000000003', 5, '18:30', '20:30'),
    (gen_random_uuid(), '22222222-2222-2222-2222-000000000004', 2, '20:00', '21:30'),
    (gen_random_uuid(), '22222222-2222-2222-2222-000000000005', 5, '19:00', '21:00'),
    (gen_random_uuid(), '22222222-2222-2222-2222-000000000006', 2, '19:30', '21:00')
ON CONFLICT (slot_id) DO NOTHING;

INSERT INTO learning_goal (
    goal_id, student_id, subject_id, current_level, desired_level, goal_type,
    deadline, mode, area, budget_min, budget_max, status
) VALUES (
    '55555555-5555-5555-5555-000000000001',
    '11111111-1111-1111-1111-000000000001',
    1, 1, 4, 'EXAM_PREPARATION', '2030-06-01', 'BOTH',
    'Cầu Giấy, Hà Nội', 150000, 300000, 'ACTIVE'
)
ON CONFLICT (goal_id) DO NOTHING;

INSERT INTO goal_availability_slot (slot_id, goal_id, day_of_week, start_time, end_time) VALUES
    ('55555555-5555-5555-5555-000000000011', '55555555-5555-5555-5555-000000000001', 2, '19:00', '21:00'),
    ('55555555-5555-5555-5555-000000000012', '55555555-5555-5555-5555-000000000001', 5, '19:00', '21:00')
ON CONFLICT (slot_id) DO NOTHING;
