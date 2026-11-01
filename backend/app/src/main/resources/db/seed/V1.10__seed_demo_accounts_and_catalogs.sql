-- =========================================================================
-- EduFit Database Migration V1.09: Seed Data for Catalogs & Demo Accounts
-- =========================================================================

-- 1. DANH MỤC CẤP HỌC (EDUCATION_LEVEL)
INSERT INTO education_level (level_id, name, sort_order, is_active) VALUES
    (1, 'Lớp 6', 1, true),
    (2, 'Lớp 7', 2, true),
    (3, 'Lớp 8', 3, true),
    (4, 'Lớp 9', 4, true)
ON CONFLICT (name) DO NOTHING;

-- 2. DANH MỤC MÔN HỌC (SUBJECT)
INSERT INTO subject (subject_id, name, is_active) VALUES
    (1, 'Toán', true),
    (2, 'Ngữ Văn', true),
    (3, 'Tiếng Anh', true),
    (4, 'Vật Lý', true),
    (5, 'Hóa Học', true),
    (6, 'Sinh Học', true)
ON CONFLICT (name) DO NOTHING;

-- 3. CÁC TÀI KHOẢN MẪU HỆ THỐNG (DEMO ACCOUNTS)
-- Mật khẩu chung: Password123@
-- Hash Argon2id: $argon2id$v=19$m=65536,t=3,p=1$iOJcfWv2oO7dZrIj8BcOmA$OJP7aGorP6uOVeqsGRw4sq7Nqezhpo9svm0dHTIc1H8

-- 3.1. Tài khoản Học sinh (STUDENT)
INSERT INTO account (user_id, email, password_hash, full_name, role, status) VALUES
    ('11111111-1111-1111-1111-111111111111', 'student.demo@edufit.vn', '$argon2id$v=19$m=65536,t=3,p=1$iOJcfWv2oO7dZrIj8BcOmA$OJP7aGorP6uOVeqsGRw4sq7Nqezhpo9svm0dHTIc1H8', 'Trần Minh Anh', 'STUDENT', 'ACTIVE')
ON CONFLICT (email) DO NOTHING;

-- 3.2. Tài khoản Gia sư (TUTOR)
INSERT INTO account (user_id, email, password_hash, full_name, role, status) VALUES
    ('22222222-2222-2222-2222-222222222222', 'tutor.demo@edufit.vn', '$argon2id$v=19$m=65536,t=3,p=1$iOJcfWv2oO7dZrIj8BcOmA$OJP7aGorP6uOVeqsGRw4sq7Nqezhpo9svm0dHTIc1H8', 'Nguyễn Hoàng Lan', 'TUTOR', 'ACTIVE')
ON CONFLICT (email) DO NOTHING;

-- 3.3. Tài khoản Phụ huynh (PARENT)
INSERT INTO account (user_id, email, password_hash, full_name, role, status) VALUES
    ('33333333-3333-3333-3333-333333333333', 'parent.demo@edufit.vn', '$argon2id$v=19$m=65536,t=3,p=1$iOJcfWv2oO7dZrIj8BcOmA$OJP7aGorP6uOVeqsGRw4sq7Nqezhpo9svm0dHTIc1H8', 'Trần Văn Hưng', 'PARENT', 'ACTIVE')
ON CONFLICT (email) DO NOTHING;

-- 3.4. Tài khoản Quản trị viên (ADMIN)
INSERT INTO account (user_id, email, password_hash, full_name, role, status) VALUES
    ('44444444-4444-4444-4444-444444444444', 'admin.demo@edufit.vn', '$argon2id$v=19$m=65536,t=3,p=1$iOJcfWv2oO7dZrIj8BcOmA$OJP7aGorP6uOVeqsGRw4sq7Nqezhpo9svm0dHTIc1H8', 'Quản Trị Viên EduFit', 'ADMIN', 'ACTIVE')
ON CONFLICT (email) DO NOTHING;

-- 4. KHỞI TẠO HỒ SƠ HỌC SINH MẪU
INSERT INTO student_profile (student_id, user_id, education_level_id, area, profile_complete) VALUES
    ('11111111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', 4, 'Cầu Giấy, Hà Nội', true)
ON CONFLICT (user_id) DO NOTHING;

-- 5. KHỞI TẠO HỒ SƠ GIA SƯ MẪU
INSERT INTO tutor_profile (
    tutor_id, user_id, display_name, headline, bio, teaching_mode, area,
    price_per_session, experience_years, teaching_method, status, rating_avg, review_count
) VALUES (
    '22222222-2222-2222-2222-000000000001',
    '22222222-2222-2222-2222-222222222222',
    'Cô Nguyễn Hoàng Lan',
    'Cử nhân Sư phạm Toán · 5 năm kinh nghiệm luyện thi vào 10',
    'Tận tâm, kiên nhẫn, phương pháp trực quan giúp học sinh nắm chắc bản chất kiến thức hình học và đại số.',
    'BOTH',
    'Cầu Giấy, Hà Nội',
    250000,
    5,
    'Phương pháp sơ đồ tư duy & luyện giải đề thực chiến theo ma trận tuyển sinh.',
    'VERIFIED',
    4.9,
    18
)
ON CONFLICT (user_id) DO NOTHING;

-- 6. GÁN MÔN DẠY CHO GIA SƯ MẪU (Toán - Lớp 9)
INSERT INTO tutor_subject (tutor_subject_id, tutor_id, subject_id, education_level_id) VALUES
    (gen_random_uuid(), '22222222-2222-2222-2222-000000000001', 1, 4)
ON CONFLICT (tutor_id, subject_id, education_level_id) DO NOTHING;
