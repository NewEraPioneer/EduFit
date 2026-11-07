-- =========================================================================
-- EduFit Demo Seed V1.34: dữ liệu demo đầy đủ (chỉ profile dev, mật khẩu chung Password123@)
-- =========================================================================
-- Chạy một lần khi tạo CSDL dev mới (docker compose down -v để dựng lại). Không có chế độ, không có điều kiện.
--
-- TÀI KHOẢN DEMO (ba tài khoản trình diễn luồng chính, gắn với nhau):
--   student.demo@edufit.vn  Học sinh Trần Minh Anh (lớp 9, Cầu Giấy, Hà Nội)
--   parent.demo@edufit.vn   Phụ huynh Trần Văn Hưng (đã liên kết với student.demo, là người trả học phí lớp Toán)
--   tutor.demo@edufit.vn    Gia sư Nguyễn Hoàng Lan (Toán lớp 8-9, Hà Nội, đã xác minh)
--   admin.demo@edufit.vn    Quản trị viên (xử lý khiếu nại học phí, duyệt hồ sơ gia sư, duyệt rút tiền)
--
-- Gia sư đa dạng để khám phá/ghép cặp (không gắn lớp học):
--   tutor.ha / quan / an / nam / thao  Toán, Hà Nội hoặc online                       (V1.13)
--   tutor.linh   Tiếng Anh (Học thuật + Cambridge), online, TP.HCM, sinh viên         VERIFIED
--   tutor.hung   Ngữ Văn, dạy trực tiếp Đà Nẵng, giáo viên đã nghỉ hưu                VERIFIED
--   tutor.phuong KHTN (Vật lý / Hóa học), Hà Nội, online + trực tiếp                  VERIFIED
--   tutor.kien   Toán, TP.HCM, KHÔNG có khung giờ rảnh (quy ước: đã kín lịch)         VERIFIED
--   tutor.mai    Toán lớp 6-7, hồ sơ đang chờ xác minh                                PENDING_VERIFICATION
--   tutor.nhi    Hồ sơ nháp, chưa khai báo gì thêm                                    DRAFT
--   tutor.long   Hồ sơ bị từ chối xác minh                                            UNVERIFIED
-- Học sinh phụ: student.khoi (lớp 8, Hà Đông) gửi yêu cầu cho tutor.demo và được parent.demo mời liên kết.
--
-- HÀNH TRÌNH CỦA BA TÀI KHOẢN CHÍNH
--   Lớp 1  student.demo + tutor.demo   Toán 9 luyện thi vào 10, 250.000đ/buổi, học Thứ 3 (online) và Thứ 6 (trực tiếp)
--          20 buổi đã qua đủ trạng thái, 4 buổi sắp tới, lộ trình 6 cột mốc, 2 báo cáo kỳ, parent.demo trả học phí.
--   Lớp 2  student.demo + tutor.linh   Tiếng Anh học thuật, 200.000đ/buổi, Thứ 7 online, student.demo tự trả học phí.
--   Lớp 3  student.demo + tutor.ha     Toán 8 (đã kết thúc), parent.demo trả học phí, student.demo đã đánh giá gia sư.
--
-- Mọi mốc thời gian tính theo thời điểm nạp dữ liệu (giờ Asia/Ho_Chi_Minh): lịch học bám Thứ 3 và Thứ 6 19:00
-- (khớp khung giờ rảnh của tutor.demo và nhu cầu học của student.demo), Thứ 7 09:00 cho lớp tiếng Anh.
-- Các trạng thái đang chờ (xác nhận 24h, khiếu nại 48h, đề xuất 3 ngày) còn hạn ngay sau khi nạp, sau vài ngày
-- job nền sẽ tự chuyển. Muốn dữ liệu tươi cho buổi demo: docker compose down -v rồi up lại.

-- 0. HÀM TẠM (chỉ tồn tại trong phiên migrate)
CREATE FUNCTION pg_temp.today() RETURNS DATE AS $$
    SELECT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
$$ LANGUAGE sql STABLE;

CREATE FUNCTION pg_temp.hn(d DATE, t TIME) RETURNS TIMESTAMPTZ AS $$
    SELECT (d + t) AT TIME ZONE 'Asia/Ho_Chi_Minh'
$$ LANGUAGE sql IMMUTABLE;

CREATE FUNCTION pg_temp.uid(seed TEXT) RETURNS UUID AS $$
    SELECT md5('edufit-demo:' || seed)::uuid
$$ LANGUAGE sql IMMUTABLE;

-- Tiến độ (%) của một lộ trình tại một thời điểm, cùng công thức ProgressCalculator: trọng số x tỉ lệ hoàn thành
CREATE FUNCTION pg_temp.progress_at(p_plan UUID, p_at TIMESTAMPTZ) RETURNS INT AS $$
    SELECT CASE WHEN sum(weight) = 0 THEN NULL ELSE round(100.0 * sum(weight * share) / sum(weight))::int END
    FROM (
        SELECT m.weight,
               CASE
                   WHEN m.status = 'ACHIEVED' AND m.achieved_date <= (p_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date THEN 1.0
                   WHEN NOT EXISTS (SELECT 1 FROM milestone_criterion c WHERE c.milestone_id = m.milestone_id) THEN 0.0
                   ELSE (SELECT count(*) FILTER (WHERE c.is_checked AND c.checked_at <= p_at)::numeric / count(*)
                         FROM milestone_criterion c WHERE c.milestone_id = m.milestone_id)
               END AS share
        FROM milestone m WHERE m.plan_id = p_plan AND NOT m.is_archived
    ) x
$$ LANGUAGE sql STABLE;

-- =========================================================================
-- 1. TÀI KHOẢN MỚI (Argon2id của Password123@, cùng hash với V1.09)
-- =========================================================================
INSERT INTO account (user_id, email, password_hash, full_name, role, status)
SELECT v.user_id::uuid, v.email,
       '$argon2id$v=19$m=65536,t=3,p=1$iOJcfWv2oO7dZrIj8BcOmA$OJP7aGorP6uOVeqsGRw4sq7Nqezhpo9svm0dHTIc1H8',
       v.full_name, v.role, 'ACTIVE'
FROM (VALUES
    ('11111111-1111-1111-1111-111111111112', 'student.khoi@edufit.vn',   'Lê Đức Khôi',        'STUDENT'),
    ('22222222-2222-2222-2222-222222222228', 'tutor.linh@edufit.vn',     'Vũ Thùy Linh',       'TUTOR'),
    ('22222222-2222-2222-2222-222222222229', 'tutor.hung@edufit.vn',     'Bùi Quang Hưng',     'TUTOR'),
    ('22222222-2222-2222-2222-22222222222a', 'tutor.phuong@edufit.vn',   'Hoàng Minh Phương',  'TUTOR'),
    ('22222222-2222-2222-2222-22222222222b', 'tutor.kien@edufit.vn',     'Đỗ Trung Kiên',      'TUTOR'),
    ('22222222-2222-2222-2222-22222222222c', 'tutor.mai@edufit.vn',      'Ngô Thị Mai',        'TUTOR'),
    ('22222222-2222-2222-2222-22222222222d', 'tutor.nhi@edufit.vn',      'Phan Yến Nhi',       'TUTOR'),
    ('22222222-2222-2222-2222-22222222222e', 'tutor.long@edufit.vn',     'Trịnh Hải Long',     'TUTOR')
) AS v(user_id, email, full_name, role);

-- Mốc tạo của tài khoản demo lùi xa hơn mọi hoạt động bên dưới
UPDATE account SET created_at = now() - interval '200 days', updated_at = now() - interval '200 days'
WHERE user_id::text ~ '^(11111111-1111-1111-1111|22222222-2222-2222-2222|33333333-3333-3333-3333|44444444-4444-4444-4444)-';

-- =========================================================================
-- 2. HỒ SƠ HỌC SINH & NHU CẦU HỌC
-- =========================================================================
UPDATE student_profile SET created_at = now() - interval '195 days', updated_at = now() - interval '195 days'
WHERE student_id = '11111111-1111-1111-1111-000000000001';

INSERT INTO student_profile (student_id, user_id, education_level_id, profile_complete, province_id, district_id, created_at, updated_at)
VALUES ('11111111-1111-1111-1111-000000000002', '11111111-1111-1111-1111-111111111112', 3, true, 1, 19,
        now() - interval '30 days', now() - interval '30 days');

-- Nhu cầu 1 (student.demo, Toán 9 luyện thi vào 10) đã có ở V1.27; ngày tạo lùi lại cho đúng dòng thời gian
UPDATE learning_need SET created_at = now() - interval '190 days', updated_at = now() - interval '190 days'
WHERE need_id = '55555555-5555-5555-5555-000000000001';

INSERT INTO learning_need (need_id, student_id, created_by, education_level_id, subject_id, track_id, goal_tag_id,
                           textbook_series_id, mode, province_id, district_id, budget_max_per_session, status, created_at, updated_at)
VALUES
    -- 2. student.demo: Tiếng Anh học thuật lớp 9, online, đang theo học với tutor.linh
    ('55555555-5555-5555-5555-000000000002', '11111111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111',
     4, 3, 2, 2, 4, 'ONLINE', 1, 26, 220000, 'ACTIVE', now() - interval '60 days', now() - interval '60 days'),
    -- 3. student.demo: Toán lớp 8 từ năm trước, đã lưu trữ sau khi kết thúc lớp
    ('55555555-5555-5555-5555-000000000003', '11111111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111',
     3, 1, NULL, 1, 2, 'OFFLINE', 1, 26, 260000, 'ARCHIVED', now() - interval '200 days', now() - interval '100 days'),
    -- 4. student.khoi: Toán lớp 8 lấy lại gốc, học trực tiếp tại Hà Đông
    ('55555555-5555-5555-5555-000000000004', '11111111-1111-1111-1111-000000000002', '11111111-1111-1111-1111-111111111112',
     3, 1, NULL, 1, 1, 'OFFLINE', 1, 19, 250000, 'ACTIVE', now() - interval '20 days', now() - interval '20 days');

INSERT INTO learning_need_slot (slot_id, need_id, day_of_week, start_time, end_time) VALUES
    (pg_temp.uid('slot:n2:sat'), '55555555-5555-5555-5555-000000000002', 6, '09:00', '11:00'),
    (pg_temp.uid('slot:n3:tue'), '55555555-5555-5555-5555-000000000003', 2, '19:00', '21:00'),
    (pg_temp.uid('slot:n4:sat'), '55555555-5555-5555-5555-000000000004', 6, '14:00', '16:00'),
    (pg_temp.uid('slot:n4:sun'), '55555555-5555-5555-5555-000000000004', 7, '14:00', '16:00');

-- =========================================================================
-- 3. HỒ SƠ GIA SƯ
-- =========================================================================
-- Gia sư V1.09/V1.13: xác minh từ lâu, tạo hồ sơ trước mọi hoạt động bên dưới
UPDATE tutor_profile SET created_at = now() - interval '190 days', updated_at = now() - interval '100 days',
                         verified_at = now() - interval '180 days'
WHERE tutor_id::text LIKE '22222222-2222-2222-2222-00000000000_';

-- tutor.demo: dạy thêm lớp 8, thêm khu vực và khung giờ Thứ 6 để khớp lịch học
UPDATE tutor_profile SET province_id = 1 WHERE tutor_id = '22222222-2222-2222-2222-000000000001';
INSERT INTO tutor_district (tutor_id, district_id) VALUES
    ('22222222-2222-2222-2222-000000000001', 20) ON CONFLICT DO NOTHING;
INSERT INTO tutor_availability_slot (slot_id, tutor_id, day_of_week, start_time, end_time) VALUES
    (pg_temp.uid('avail:demo:fri'), '22222222-2222-2222-2222-000000000001', 5, '19:00', '21:00');
INSERT INTO tutor_offering_level (offering_id, level_id)
SELECT offering_id, 3 FROM tutor_offering WHERE tutor_id = '22222222-2222-2222-2222-000000000001'
ON CONFLICT DO NOTHING;

INSERT INTO tutor_profile (tutor_id, user_id, display_name, headline, bio, teaching_mode, experience_years, teaching_method,
                           status, verified_at, rating_avg, review_count, tutor_type, tutor_type_verified, province_id,
                           created_at, updated_at)
VALUES
    ('22222222-2222-2222-2222-000000000007', '22222222-2222-2222-2222-222222222228', 'Cô Vũ Thùy Linh',
     'Sinh viên Sư phạm Anh · IELTS 7.5 · Dạy tiếng Anh THCS và Cambridge',
     'Hai năm dạy kèm tiếng Anh THCS, tập trung vào nền tảng ngữ pháp, từ vựng theo chủ đề và luyện thi Cambridge KET/PET.',
     'ONLINE', 2, 'Học qua tình huống thực tế, sửa lỗi sau từng buổi và giao bài nghe nói ngắn mỗi tuần.',
     'VERIFIED', now() - interval '120 days', NULL, 0, 'UNIVERSITY_STUDENT', true, 2, now() - interval '130 days', now() - interval '120 days'),
    ('22222222-2222-2222-2222-000000000008', '22222222-2222-2222-2222-222222222229', 'Thầy Bùi Quang Hưng',
     'Giáo viên Ngữ văn đã nghỉ hưu · 30 năm giảng dạy THCS',
     'Ba mươi năm đứng lớp, chuyên rèn kỹ năng làm văn nghị luận và cảm thụ thơ cho học sinh lớp 8-9, luyện thi vào 10.',
     'OFFLINE', 30, 'Phân tích mẫu, lập dàn ý, viết và chữa bài chi tiết từng đoạn.',
     'VERIFIED', now() - interval '110 days', NULL, 0, 'RETIRED_TEACHER', true, 4, now() - interval '125 days', now() - interval '110 days'),
    ('22222222-2222-2222-2222-000000000009', '22222222-2222-2222-2222-22222222222a', 'Cô Hoàng Minh Phương',
     'Cử nhân Sư phạm Vật lý · Dạy Khoa học tự nhiên lớp 8-9',
     'Bốn năm dạy Vật lý và Hóa học THCS, xây dựng bài giảng thí nghiệm trực quan và hệ thống bài tập phân tầng.',
     'BOTH', 4, 'Thí nghiệm mô phỏng, sơ đồ tư duy công thức và luyện đề theo chuyên đề.',
     'VERIFIED', now() - interval '100 days', NULL, 0, 'GRADUATE', true, 1, now() - interval '115 days', now() - interval '100 days'),
    ('22222222-2222-2222-2222-000000000010', '22222222-2222-2222-2222-22222222222b', 'Thầy Đỗ Trung Kiên',
     'Giáo viên Toán THCS · Luyện thi chuyên và vào 10 tại TP.HCM',
     'Tám năm giảng dạy, nhận nhóm nhỏ và học sinh ôn thi chuyên. Hiện đã kín lịch dạy trong tuần.',
     'BOTH', 8, 'Giải đề theo chuyên đề, chấm bài chi tiết và theo dõi sai sót theo từng dạng.',
     'VERIFIED', now() - interval '90 days', NULL, 0, 'IN_SERVICE_TEACHER', true, 2, now() - interval '105 days', now() - interval '90 days'),
    ('22222222-2222-2222-2222-000000000011', '22222222-2222-2222-2222-22222222222c', 'Cô Ngô Thị Mai',
     'Sinh viên Sư phạm Toán · Dạy Toán lớp 6-7',
     'Dạy kèm Toán lớp 6-7 cho học sinh cần củng cố kiến thức nền, kiên nhẫn và dễ gần.',
     'ONLINE', 1, 'Giải thích từng bước bằng ví dụ gần gũi, luyện tập ngắn sau mỗi nội dung.',
     'PENDING_VERIFICATION', NULL, NULL, 0, 'UNIVERSITY_STUDENT', false, 1, now() - interval '3 days', now() - interval '2 days'),
    ('22222222-2222-2222-2222-000000000012', '22222222-2222-2222-2222-22222222222d', 'Phan Yến Nhi',
     NULL, NULL, 'ONLINE', NULL, NULL, 'DRAFT', NULL, NULL, 0, NULL, false, NULL,
     now() - interval '1 day', now() - interval '1 day'),
    ('22222222-2222-2222-2222-000000000013', '22222222-2222-2222-2222-22222222222e', 'Thầy Trịnh Hải Long',
     'Sinh viên Kỹ thuật · Dạy Toán lớp 8',
     'Dạy Toán lớp 8 cho học sinh cần củng cố kiến thức, có kinh nghiệm kèm bạn bè và em họ.',
     'ONLINE', 1, 'Giải bài mẫu rồi cho học sinh tự làm lại.',
     'UNVERIFIED', NULL, NULL, 0, 'UNIVERSITY_STUDENT', false, 1, now() - interval '25 days', now() - interval '18 days');

-- Gói dạy (môn, hướng học, giá) + cấp lớp, mục tiêu, bộ sách, tỉnh luyện đề
INSERT INTO tutor_offering (offering_id, tutor_id, subject_id, track_id, price_per_session) VALUES
    (pg_temp.uid('off:linh:acad'), '22222222-2222-2222-2222-000000000007', 3, 2, 200000),
    (pg_temp.uid('off:linh:cert'), '22222222-2222-2222-2222-000000000007', 3, 1, 280000),
    (pg_temp.uid('off:hung:van'),  '22222222-2222-2222-2222-000000000008', 2, NULL, 240000),
    (pg_temp.uid('off:phuong:ly'), '22222222-2222-2222-2222-000000000009', 7, 4, 270000),
    (pg_temp.uid('off:phuong:hoa'),'22222222-2222-2222-2222-000000000009', 7, 3, 270000),
    (pg_temp.uid('off:kien:toan'), '22222222-2222-2222-2222-000000000010', 1, NULL, 300000),
    (pg_temp.uid('off:mai:toan'),  '22222222-2222-2222-2222-000000000011', 1, NULL, 180000),
    (pg_temp.uid('off:long:toan'), '22222222-2222-2222-2222-000000000013', 1, NULL, 150000);

INSERT INTO tutor_offering_level (offering_id, level_id) VALUES
    (pg_temp.uid('off:linh:acad'), 3), (pg_temp.uid('off:linh:acad'), 4),
    (pg_temp.uid('off:linh:cert'), 3), (pg_temp.uid('off:linh:cert'), 4),
    (pg_temp.uid('off:hung:van'), 3),  (pg_temp.uid('off:hung:van'), 4),
    (pg_temp.uid('off:phuong:ly'), 3), (pg_temp.uid('off:phuong:ly'), 4),
    (pg_temp.uid('off:phuong:hoa'), 3),(pg_temp.uid('off:phuong:hoa'), 4),
    (pg_temp.uid('off:kien:toan'), 3), (pg_temp.uid('off:kien:toan'), 4),
    (pg_temp.uid('off:mai:toan'), 1),  (pg_temp.uid('off:mai:toan'), 2),
    (pg_temp.uid('off:long:toan'), 3);

-- Mục tiêu: 1 Lấy lại gốc, 2 Nâng điểm, 3 HSG/thi chuyên (cao cấp), 4 Luyện thi vào 10, 5 KET (cao cấp), 6 PET (cao cấp)
INSERT INTO tutor_offering_goal (offering_id, goal_tag_id, evidence_verified) VALUES
    (pg_temp.uid('off:linh:acad'), 1, false), (pg_temp.uid('off:linh:acad'), 2, false),
    (pg_temp.uid('off:linh:cert'), 5, true),  (pg_temp.uid('off:linh:cert'), 6, false),
    (pg_temp.uid('off:hung:van'), 1, false),  (pg_temp.uid('off:hung:van'), 2, false),
    (pg_temp.uid('off:hung:van'), 3, true),   (pg_temp.uid('off:hung:van'), 4, false),
    (pg_temp.uid('off:phuong:ly'), 1, false), (pg_temp.uid('off:phuong:ly'), 2, false),
    (pg_temp.uid('off:phuong:hoa'), 1, false),(pg_temp.uid('off:phuong:hoa'), 2, false),
    (pg_temp.uid('off:phuong:hoa'), 4, false),
    (pg_temp.uid('off:kien:toan'), 1, false), (pg_temp.uid('off:kien:toan'), 2, false),
    (pg_temp.uid('off:kien:toan'), 3, true),  (pg_temp.uid('off:kien:toan'), 4, false),
    (pg_temp.uid('off:mai:toan'), 1, false),  (pg_temp.uid('off:mai:toan'), 2, false),
    (pg_temp.uid('off:long:toan'), 1, false);

-- Bộ sách (mục tiêu 1 và 2 bắt buộc khai báo bộ sách)
INSERT INTO tutor_offering_textbook (offering_id, series_id) VALUES
    (pg_temp.uid('off:linh:acad'), 4), (pg_temp.uid('off:linh:acad'), 5),
    (pg_temp.uid('off:hung:van'), 1),  (pg_temp.uid('off:hung:van'), 2),
    (pg_temp.uid('off:phuong:ly'), 1), (pg_temp.uid('off:phuong:ly'), 3),
    (pg_temp.uid('off:phuong:hoa'), 1),(pg_temp.uid('off:phuong:hoa'), 3),
    (pg_temp.uid('off:kien:toan'), 1), (pg_temp.uid('off:kien:toan'), 2),
    (pg_temp.uid('off:mai:toan'), 1),
    (pg_temp.uid('off:long:toan'), 2);

-- Tỉnh luyện đề thi vào 10 (mục tiêu 4)
INSERT INTO tutor_offering_exam_province (offering_id, province_id) VALUES
    (pg_temp.uid('off:hung:van'), 4),
    (pg_temp.uid('off:phuong:hoa'), 1),
    (pg_temp.uid('off:kien:toan'), 2);

-- Khu vực dạy trực tiếp
INSERT INTO tutor_district (tutor_id, district_id) VALUES
    ('22222222-2222-2222-2222-000000000008', 62), ('22222222-2222-2222-2222-000000000008', 61),
    ('22222222-2222-2222-2222-000000000009', 19), ('22222222-2222-2222-2222-000000000009', 22),
    ('22222222-2222-2222-2222-000000000010', 49), ('22222222-2222-2222-2222-000000000010', 40);

-- Khung giờ rảnh. tutor.kien (000000000010) cố ý KHÔNG có khung nào: quy ước là đã kín lịch.
INSERT INTO tutor_availability_slot (slot_id, tutor_id, day_of_week, start_time, end_time) VALUES
    (pg_temp.uid('avail:linh:wed'),    '22222222-2222-2222-2222-000000000007', 3, '19:00', '21:00'),
    (pg_temp.uid('avail:linh:sat'),    '22222222-2222-2222-2222-000000000007', 6, '08:00', '12:00'),
    (pg_temp.uid('avail:linh:sun'),    '22222222-2222-2222-2222-000000000007', 7, '14:00', '18:00'),
    (pg_temp.uid('avail:hung:mon'),    '22222222-2222-2222-2222-000000000008', 1, '18:30', '20:30'),
    (pg_temp.uid('avail:hung:thu'),    '22222222-2222-2222-2222-000000000008', 4, '18:30', '20:30'),
    (pg_temp.uid('avail:hung:sat'),    '22222222-2222-2222-2222-000000000008', 6, '08:00', '11:00'),
    (pg_temp.uid('avail:phuong:tue'),  '22222222-2222-2222-2222-000000000009', 2, '19:00', '21:00'),
    (pg_temp.uid('avail:phuong:thu'),  '22222222-2222-2222-2222-000000000009', 4, '19:00', '21:00'),
    (pg_temp.uid('avail:mai:wed'),     '22222222-2222-2222-2222-000000000011', 3, '19:00', '21:00');

-- =========================================================================
-- 4. XÁC MINH HỒ SƠ GIA SƯ
-- =========================================================================
-- Gia sư đã xác minh: hồ sơ APPROVED do admin.demo duyệt
INSERT INTO verification_request (request_id, tutor_id, status, submitted_at, reviewed_by, reviewed_at)
SELECT pg_temp.uid('ver:' || t.tutor_id), t.tutor_id, 'APPROVED',
       t.verified_at - interval '3 days', '44444444-4444-4444-4444-444444444444', t.verified_at
FROM tutor_profile t WHERE t.status = 'VERIFIED';

INSERT INTO verification_request (request_id, tutor_id, status, rejection_reason, submitted_at, reviewed_by, reviewed_at) VALUES
    (pg_temp.uid('ver:mai'), '22222222-2222-2222-2222-000000000011', 'PENDING', NULL,
     now() - interval '2 days', NULL, NULL),
    (pg_temp.uid('ver:long'), '22222222-2222-2222-2222-000000000013', 'REJECTED',
     'Ảnh thẻ sinh viên bị mờ, không đọc được thông tin. Vui lòng nộp lại ảnh rõ nét và bổ sung bảng điểm.',
     now() - interval '20 days', '44444444-4444-4444-4444-444444444444', now() - interval '18 days');

INSERT INTO credential (credential_id, request_id, type, institution, year, note, offering_id)
SELECT pg_temp.uid('cred:' || v.tutor_id || ':' || v.type), r.request_id, v.type, v.institution, v.year, v.note, v.offering_id
FROM (VALUES
    ('22222222-2222-2222-2222-000000000001'::uuid, 'DEGREE',           'Đại học Sư phạm Hà Nội',          2019::smallint, 'Cử nhân Sư phạm Toán', NULL::uuid),
    ('22222222-2222-2222-2222-000000000002',       'TEACHING_LICENCE', 'Sở GD&ĐT Hà Nội',                 2018, 'Giáo viên Toán THCS', NULL),
    ('22222222-2222-2222-2222-000000000003',       'DEGREE',           'Đại học Giáo dục - ĐHQGHN',       2020, NULL, NULL),
    ('22222222-2222-2222-2222-000000000004',       'DEGREE',           'Đại học Sư phạm Hà Nội',          2021, 'Hệ chất lượng cao', NULL),
    ('22222222-2222-2222-2222-000000000005',       'TEACHING_LICENCE', 'Sở GD&ĐT Hà Nội',                 2016, 'Thạc sĩ Toán học', NULL),
    ('22222222-2222-2222-2222-000000000006',       'STUDENT_CARD',     'Đại học Sư phạm Hà Nội',          2024, NULL, NULL),
    ('22222222-2222-2222-2222-000000000007',       'STUDENT_CARD',     'Đại học Ngoại ngữ - ĐH Quốc gia', 2024, NULL, NULL),
    ('22222222-2222-2222-2222-000000000007',       'CERTIFICATE',      'Cambridge Assessment English',    2023, 'Chứng chỉ giảng dạy Cambridge KET', pg_temp.uid('off:linh:cert')),
    ('22222222-2222-2222-2222-000000000008',       'TEACHING_LICENCE', 'Sở GD&ĐT Đà Nẵng',                1995, 'Giáo viên Ngữ văn THCS, nghỉ hưu 2023', NULL),
    ('22222222-2222-2222-2222-000000000008',       'CERTIFICATE',      'Sở GD&ĐT Đà Nẵng',                2012, 'Giáo viên dạy giỏi cấp thành phố, bồi dưỡng HSG', pg_temp.uid('off:hung:van')),
    ('22222222-2222-2222-2222-000000000009',       'DEGREE',           'Đại học Sư phạm Hà Nội',          2021, 'Cử nhân Sư phạm Vật lý', NULL),
    ('22222222-2222-2222-2222-000000000010',       'TEACHING_LICENCE', 'Sở GD&ĐT TP.HCM',                 2017, NULL, NULL),
    ('22222222-2222-2222-2222-000000000010',       'CERTIFICATE',      'Sở GD&ĐT TP.HCM',                 2019, 'Giáo viên bồi dưỡng đội tuyển Toán cấp thành phố', pg_temp.uid('off:kien:toan')),
    ('22222222-2222-2222-2222-000000000011',       'STUDENT_CARD',     'Đại học Sư phạm Hà Nội',          2025, NULL, NULL),
    ('22222222-2222-2222-2222-000000000013',       'STUDENT_CARD',     'Đại học Bách khoa Hà Nội',        2025, 'Ảnh bị mờ', NULL)
) AS v(tutor_id, type, institution, year, note, offering_id)
JOIN verification_request r ON r.tutor_id = v.tutor_id;

-- =========================================================================
-- 5. LIÊN KẾT PHỤ HUYNH - HỌC SINH
-- =========================================================================
INSERT INTO link_invitation (invitation_id, inviter_user_id, invited_user_id, status, created_at, expires_at, responded_at, version) VALUES
    -- Đã chấp nhận từ lâu, tạo liên kết đang hiệu lực của parent.demo với student.demo
    (pg_temp.uid('inv:demo'), '33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111',
     'ACCEPTED', now() - interval '194 days', now() - interval '187 days', now() - interval '193 days', 1),
    -- Đang chờ: parent.demo mời thêm student.khoi (demo luồng student.khoi chấp nhận lời mời)
    (pg_temp.uid('inv:khoi'), '33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111112',
     'PENDING', now() - interval '1 day', now() + interval '6 days', NULL, 0);

INSERT INTO parent_student_link (link_id, parent_user_id, student_id, status, confirmed_at, version) VALUES
    (pg_temp.uid('link:demo'), '33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-000000000001',
     'CONFIRMED', now() - interval '193 days', 0);

-- =========================================================================
-- 6. YÊU CẦU KẾT NỐI & LỚP HỌC
-- =========================================================================
-- Lịch tuần của lớp: Thứ 3 / Thứ 6 19:00 (lớp 1), Thứ 7 09:00 (lớp 2). Ngày bắt đầu lớp tính lùi từ buổi học
-- đầu tiên nên luôn hợp lệ dù nạp dữ liệu vào thứ mấy.
CREATE TEMP TABLE demo_grid ON COMMIT DROP AS
SELECT 'c1'::text AS cls, d, pg_temp.hn(d, '19:00') AS s, pg_temp.hn(d, '20:30') AS e,
       CASE WHEN extract(isodow FROM d) = 2 THEN 'ONLINE' ELSE 'OFFLINE' END AS mode
FROM (SELECT dt::date AS d FROM generate_series(pg_temp.today() - 85, pg_temp.today() + 20, interval '1 day') g(dt)
      WHERE extract(isodow FROM dt) IN (2, 5)) x
UNION ALL
SELECT 'c2', d, pg_temp.hn(d, '09:00'), pg_temp.hn(d, '10:30'), 'ONLINE'
FROM (SELECT dt::date AS d FROM generate_series(pg_temp.today() - 50, pg_temp.today() + 20, interval '1 day') g(dt)
      WHERE extract(isodow FROM dt) = 6) y;

-- r = thứ tự: P1 là buổi đã qua gần nhất, F1 là buổi sắp tới gần nhất
CREATE TEMP TABLE demo_rank ON COMMIT DROP AS
SELECT cls, d, s, e, mode,
       CASE WHEN e <= now() THEN 'P' ELSE 'F' END AS k,
       row_number() OVER (PARTITION BY cls, e <= now()
                          ORDER BY CASE WHEN e <= now() THEN -extract(epoch FROM s) ELSE extract(epoch FROM s) END) AS r
FROM demo_grid;

CREATE TEMP TABLE demo_cls ON COMMIT DROP AS
SELECT c.key, c.class_id, c.request_id, c.student_id, c.tutor_id, c.need_id, c.student_user, c.tutor_user, c.payer_user, c.price,
       c.started_at
FROM (VALUES
    ('c1', '77777777-7777-7777-7777-000000000001'::uuid, '66666666-6666-6666-6666-000000000001'::uuid,
     '11111111-1111-1111-1111-000000000001'::uuid, '22222222-2222-2222-2222-000000000001'::uuid,
     '55555555-5555-5555-5555-000000000001'::uuid, '11111111-1111-1111-1111-111111111111'::uuid,
     '22222222-2222-2222-2222-222222222222'::uuid, '33333333-3333-3333-3333-333333333333'::uuid, 250000::bigint,
     (SELECT s FROM demo_rank WHERE cls = 'c1' AND k = 'P' AND r = 20) - interval '8 days'),
    ('c2', '77777777-7777-7777-7777-000000000002', '66666666-6666-6666-6666-000000000002',
     '11111111-1111-1111-1111-000000000001', '22222222-2222-2222-2222-000000000007',
     '55555555-5555-5555-5555-000000000002', '11111111-1111-1111-1111-111111111111',
     '22222222-2222-2222-2222-222222222228', '11111111-1111-1111-1111-111111111111', 200000,
     (SELECT s FROM demo_rank WHERE cls = 'c2' AND k = 'P' AND r = 5) - interval '8 days'),
    ('c3', '77777777-7777-7777-7777-000000000003', '66666666-6666-6666-6666-000000000003',
     '11111111-1111-1111-1111-000000000001', '22222222-2222-2222-2222-000000000002',
     '55555555-5555-5555-5555-000000000003', '11111111-1111-1111-1111-111111111111',
     '22222222-2222-2222-2222-222222222223', '33333333-3333-3333-3333-333333333333', 250000,
     now() - interval '190 days')
) AS c(key, class_id, request_id, student_id, tutor_id, need_id, student_user, tutor_user, payer_user, price, started_at);

INSERT INTO connection_request (request_id, student_id, tutor_id, need_id, sent_by_user_id, desired_slots, message, status,
                                reject_reason, created_at, expires_at, responded_at, version)
SELECT c.request_id, c.student_id, c.tutor_id, c.need_id, c.student_user,
       CASE c.key WHEN 'c1' THEN '["Tue 19:00","Fri 19:00"]' WHEN 'c2' THEN '["Sat 09:00"]' ELSE '["Tue 19:00"]' END::jsonb,
       CASE c.key WHEN 'c1' THEN 'Em muốn ôn Toán lớp 9 để thi vào 10, mong cô nhận dạy vào tối Thứ 3 và Thứ 6 ạ.'
                  WHEN 'c2' THEN 'Em muốn cải thiện tiếng Anh trên lớp, học online vào sáng Thứ 7.'
                  ELSE 'Em cần củng cố Toán lớp 8.' END,
       'ACCEPTED', NULL, c.started_at - interval '3 days', c.started_at - interval '3 days' + interval '3 days',
       c.started_at - interval '2 days', 1
FROM demo_cls c;

INSERT INTO connection_request (request_id, student_id, tutor_id, need_id, sent_by_user_id, desired_slots, message, status,
                                reject_reason, created_at, expires_at, responded_at, version) VALUES
    -- Phụ huynh gửi hộ student.demo tới tutor.nam: đang chờ phản hồi
    ('66666666-6666-6666-6666-000000000004', '11111111-1111-1111-1111-000000000001', '22222222-2222-2222-2222-000000000005',
     '55555555-5555-5555-5555-000000000001', '33333333-3333-3333-3333-333333333333', '["Fri 19:00"]'::jsonb,
     'Gia đình muốn tham khảo thêm một gia sư luyện đề thi vào 10, mong thầy phản hồi giúp.',
     'PENDING', NULL, now() - interval '1 day', now() + interval '2 days', NULL, 0),
    ('66666666-6666-6666-6666-000000000005', '11111111-1111-1111-1111-000000000001', '22222222-2222-2222-2222-000000000003',
     '55555555-5555-5555-5555-000000000001', '11111111-1111-1111-1111-111111111111', '["Tue 19:00"]'::jsonb,
     'Em muốn học thêm chuyên đề hình học vào Thứ 3.',
     'REJECTED', 'Hiện lịch dạy tối Thứ 3 của thầy đã kín, mong em thông cảm.',
     now() - interval '40 days', now() - interval '37 days', now() - interval '39 days', 1),
    ('66666666-6666-6666-6666-000000000006', '11111111-1111-1111-1111-000000000001', '22222222-2222-2222-2222-000000000006',
     '55555555-5555-5555-5555-000000000001', '11111111-1111-1111-1111-111111111111', NULL,
     'Em muốn học online ạ.',
     'CANCELLED', NULL, now() - interval '35 days', now() - interval '32 days', now() - interval '34 days', 1),
    ('66666666-6666-6666-6666-000000000007', '11111111-1111-1111-1111-000000000001', '22222222-2222-2222-2222-000000000004',
     '55555555-5555-5555-5555-000000000001', '11111111-1111-1111-1111-111111111111', NULL,
     'Em muốn học online với cô vào cuối tuần ạ.',
     'EXPIRED', NULL, now() - interval '30 days', now() - interval '27 days', now() - interval '27 days', 1),
    -- student.khoi gửi tutor.demo: hộp thư đến của tutor.demo
    ('66666666-6666-6666-6666-000000000008', '11111111-1111-1111-1111-000000000002', '22222222-2222-2222-2222-000000000001',
     '55555555-5555-5555-5555-000000000004', '11111111-1111-1111-1111-111111111112', '["Sat 14:00"]'::jsonb,
     'Em là học sinh lớp 8, muốn lấy lại gốc môn Toán, học trực tiếp tại Hà Đông vào chiều Thứ 7 ạ.',
     'PENDING', NULL, now() - interval '5 hours', now() + interval '3 days' - interval '5 hours', NULL, 0);

INSERT INTO class (class_id, connection_request_id, student_id, tutor_id, need_id, status, started_at, ended_at, end_reason, price_per_session)
SELECT c.class_id, c.request_id, c.student_id, c.tutor_id, c.need_id,
       CASE c.key WHEN 'c3' THEN 'ENDED' ELSE 'ACTIVE' END, c.started_at,
       CASE c.key WHEN 'c3' THEN now() - interval '100 days' END,
       CASE c.key WHEN 'c3' THEN 'Học sinh đã hoàn thành chương trình lớp 8 và chuyển sang học lớp 9' END,
       c.price
FROM demo_cls c;

-- =========================================================================
-- 7. BUỔI HỌC
-- =========================================================================
-- Kịch bản từng buổi (P = đã qua, F = sắp tới), pay = trạng thái học phí
CREATE TEMP TABLE demo_plan ON COMMIT DROP AS
SELECT * FROM (VALUES
    ('c1','P',1,'COMPLETED','AWAIT'),   ('c1','P',2,'COMPLETED','ADMIN'),    ('c1','P',3,'COMPLETED','SETTLED'),
    ('c1','P',4,'ABSENT','REFUND'),      ('c1','P',5,'CANCELLED','REFUND'),   ('c1','P',6,'COMPLETED','AUTO'),
    ('c1','P',7,'COMPLETED','SETTLED'),  ('c1','P',8,'COMPLETED','SETTLED'),  ('c1','P',9,'CANCELLED',NULL),
    ('c1','P',10,'COMPLETED','SETTLED'), ('c1','P',11,'COMPLETED','SETTLED'), ('c1','P',12,'COMPLETED','SETTLED'),
    ('c1','P',13,'COMPLETED','SETTLED'), ('c1','P',14,'COMPLETED','SETTLED'), ('c1','P',15,'COMPLETED','SETTLED'),
    ('c1','P',16,'COMPLETED','SETTLED'), ('c1','P',17,'COMPLETED','SETTLED'), ('c1','P',18,'COMPLETED','SETTLED'),
    ('c1','P',19,'COMPLETED','SETTLED'), ('c1','P',20,'COMPLETED','SETTLED'),
    ('c1','F',1,'SCHEDULED','FUNDED'),   ('c1','F',2,'SCHEDULED','FUNDED'),   ('c1','F',3,'SCHEDULED',NULL),
    ('c1','F',4,'PROPOSED',NULL),
    ('c2','P',1,'COMPLETED','AWAIT'),    ('c2','P',2,'COMPLETED','SETTLED'),  ('c2','P',3,'COMPLETED','AUTO'),
    ('c2','P',4,'COMPLETED','SETTLED'),  ('c2','P',5,'COMPLETED','SETTLED'),
    ('c2','F',1,'SCHEDULED','FUNDED'),   ('c2','F',2,'PROPOSED',NULL)
) AS p(cls, k, r, status, pay);

CREATE TEMP TABLE demo_s ON COMMIT DROP AS
SELECT g.cls || ':' || g.k || g.r AS label, pg_temp.uid('session:' || g.cls || ':' || g.k || g.r) AS session_id,
       g.cls, c.class_id, c.tutor_id, c.student_id, c.student_user, c.tutor_user, c.payer_user, c.price,
       g.s AS start_at, g.e AS end_at, g.mode, p.status, p.pay, g.k, g.r,
       -- gia sư đề xuất, học sinh chấp nhận trong ngày
       CASE WHEN p.status = 'PROPOSED' THEN now() - interval '3 hours'
            ELSE GREATEST(c.started_at + interval '1 day', LEAST(g.s - interval '5 days', now() - interval '1 day')) END AS created_at
FROM demo_rank g
JOIN demo_plan p ON p.cls = g.cls AND p.k = g.k AND p.r = g.r
JOIN demo_cls c ON c.key = g.cls;

-- Buổi học bù ngoài lịch tuần của lớp 1: học hôm qua 16:00, học phí đang bị khiếu nại (còn trong hạn 48 giờ)
INSERT INTO demo_s
SELECT 'c1:makeup', pg_temp.uid('session:c1:makeup'), 'c1', c.class_id, c.tutor_id, c.student_id, c.student_user, c.tutor_user,
       c.payer_user, c.price, pg_temp.hn(pg_temp.today() - 1, '16:00'), pg_temp.hn(pg_temp.today() - 1, '17:30'),
       'ONLINE', 'COMPLETED', 'DISPUTED', 'M', 0, pg_temp.hn(pg_temp.today() - 3, '10:00')
FROM demo_cls c WHERE c.key = 'c1';

-- Lớp 3 (đã kết thúc): 4 buổi cũ, học phí đã tất toán
INSERT INTO demo_s
SELECT 'c3:P' || n, pg_temp.uid('session:c3:P' || n), 'c3', c.class_id, c.tutor_id, c.student_id, c.student_user, c.tutor_user,
       c.payer_user, c.price, pg_temp.hn(pg_temp.today() - (115 + 7 * n), '19:00'), pg_temp.hn(pg_temp.today() - (115 + 7 * n), '20:30'),
       'OFFLINE', 'COMPLETED', 'SETTLED', 'P', n, pg_temp.hn(pg_temp.today() - (120 + 7 * n), '10:00')
FROM demo_cls c, generate_series(1, 4) n WHERE c.key = 'c3';

-- Thời điểm gia sư ghi nhận kết quả buổi học
ALTER TABLE demo_s ADD COLUMN rec TIMESTAMPTZ;
UPDATE demo_s SET rec = CASE
    WHEN status = 'COMPLETED' AND pay = 'AWAIT' THEN LEAST(now(), GREATEST(end_at + interval '30 minutes', now() - interval '3 hours'))
    WHEN status = 'COMPLETED' THEN end_at + interval '45 minutes'
    WHEN status = 'ABSENT' THEN end_at + interval '15 minutes' END;

INSERT INTO tutoring_session (session_id, class_id, tutor_id, student_id, start_at, end_at, mode, place_or_link, repeat_note, message,
                              status, proposed_by, responded_by, responded_at, response_reason, expires_at,
                              cancelled_by, cancelled_at, cancel_reason, cancel_comment, is_late_cancel,
                              outcome_recorded_by, outcome_recorded_at, version, created_at)
SELECT s.session_id, s.class_id, s.tutor_id, s.student_id, s.start_at, s.end_at, s.mode,
       CASE s.mode WHEN 'ONLINE' THEN 'https://meet.google.com/edufit-' || s.cls || '-demo'
                   ELSE 'Tại nhà học sinh – Cầu Giấy, Hà Nội' END,
       CASE WHEN s.label = 'c1:makeup' THEN 'Buổi học bù ngoài lịch tuần'
            WHEN s.cls = 'c1' THEN 'Lịch cố định Thứ 3 và Thứ 6 hằng tuần' WHEN s.cls = 'c2' THEN 'Lịch cố định Thứ 7 hằng tuần' END,
       CASE WHEN s.status = 'PROPOSED' THEN 'Cô đề xuất thêm buổi này, em xác nhận giúp cô nhé.' END,
       s.status,
       s.tutor_user,
       CASE WHEN s.status <> 'PROPOSED' THEN s.student_user END,
       CASE WHEN s.status <> 'PROPOSED' THEN s.created_at + interval '3 hours' END,
       NULL,
       s.created_at + interval '3 days',
       CASE WHEN s.status = 'CANCELLED' THEN CASE WHEN s.r = 5 THEN s.student_user ELSE s.tutor_user END END,
       CASE WHEN s.status = 'CANCELLED' THEN CASE WHEN s.r = 5 THEN s.start_at - interval '5 hours' ELSE s.start_at - interval '2 days' END END,
       CASE WHEN s.status = 'CANCELLED' THEN CASE WHEN s.r = 5 THEN 'PERSONAL_REASON' ELSE 'SCHEDULE_CONFLICT' END END,
       CASE WHEN s.status = 'CANCELLED' THEN CASE WHEN s.r = 5 THEN 'Con bị ốm, xin phép nghỉ buổi này.' ELSE 'Cô có lịch họp chuyên môn đột xuất.' END END,
       (s.status = 'CANCELLED' AND s.r = 5),
       CASE WHEN s.rec IS NOT NULL THEN s.tutor_user END, s.rec, 1, s.created_at
FROM demo_s s;

-- Buổi học ngoài lịch tuần của lớp 1: bị từ chối, bị gia sư rút lại, hết hạn không ai trả lời
INSERT INTO tutoring_session (session_id, class_id, tutor_id, student_id, start_at, end_at, mode, place_or_link, message, status,
                              proposed_by, responded_by, responded_at, response_reason, expires_at, version, created_at)
SELECT pg_temp.uid('session:c1:' || v.key), c.class_id, c.tutor_id, c.student_id, v.start_at, v.start_at + interval '90 minutes',
       'ONLINE', 'https://meet.google.com/edufit-c1-demo', v.message, v.status, c.tutor_user,
       v.responder, v.responded_at, v.reason, v.created_at + interval '3 days', 1, v.created_at
FROM demo_cls c
CROSS JOIN LATERAL (VALUES
    ('rejected', 'REJECTED', pg_temp.hn(pg_temp.today() + 8, '14:00'), 'Cô muốn thêm một buổi ôn đề chiều Chủ nhật tuần sau.',
     c.student_user, now() - interval '1 day' + interval '2 hours', 'Chiều đó con có lịch thi thử ở trường, cô dời giúp con ạ.', now() - interval '2 days'),
    ('withdrawn', 'WITHDRAWN', pg_temp.hn(pg_temp.today() + 4, '14:00'), 'Cô đề xuất buổi ôn tập bù vào chiều Thứ 2 tuần sau.',
     c.tutor_user, now() - interval '20 hours', 'Cô có việc đột xuất nên rút lại đề xuất này.', now() - interval '1 day'),
    ('expired', 'EXPIRED', pg_temp.hn(pg_temp.today() - 20, '14:00'), 'Cô đề xuất buổi ôn tập trước kỳ kiểm tra.',
     NULL::uuid, NULL::timestamptz, NULL::text, now() - interval '24 days')
) AS v(key, status, start_at, message, responder, responded_at, reason, created_at)
WHERE c.key = 'c1';

-- Lịch sử thao tác
INSERT INTO session_history (history_id, session_id, reschedule_proposal_id, action, actor_user_id, reason, created_at)
SELECT pg_temp.uid('hist:propose:' || session_id), session_id, NULL, 'PROPOSE', proposed_by, NULL, created_at FROM tutoring_session;
INSERT INTO session_history (history_id, session_id, reschedule_proposal_id, action, actor_user_id, reason, created_at)
SELECT pg_temp.uid('hist:resp:' || session_id), session_id, NULL,
       CASE status WHEN 'REJECTED' THEN 'REJECT' WHEN 'WITHDRAWN' THEN 'CANCEL' ELSE 'ACCEPT' END,
       responded_by, response_reason, responded_at
FROM tutoring_session WHERE responded_at IS NOT NULL;
INSERT INTO session_history (history_id, session_id, reschedule_proposal_id, action, actor_user_id, reason, created_at)
SELECT pg_temp.uid('hist:cancel:' || session_id), session_id, NULL, 'CANCEL', cancelled_by, cancel_comment, cancelled_at
FROM tutoring_session WHERE status = 'CANCELLED';
INSERT INTO session_history (history_id, session_id, reschedule_proposal_id, action, actor_user_id, reason, created_at)
SELECT pg_temp.uid('hist:outcome:' || session_id), session_id, NULL, 'OUTCOME_' || status, outcome_recorded_by, NULL, outcome_recorded_at
FROM tutoring_session WHERE outcome_recorded_at IS NOT NULL;
INSERT INTO session_history (history_id, session_id, reschedule_proposal_id, action, actor_user_id, reason, created_at)
SELECT pg_temp.uid('hist:expire:' || session_id), session_id, NULL, 'EXPIRE', NULL, NULL, expires_at
FROM tutoring_session WHERE status = 'EXPIRED';

-- Đề xuất đổi lịch: ACCEPTED / REJECTED / EXPIRED trong quá khứ, hai đề xuất PENDING (một của gia sư, một của học sinh)
INSERT INTO session_reschedule_proposal (proposal_id, session_id, proposed_by, new_start_at, new_end_at, reason, status, expires_at,
                                         responded_by, responded_at, response_reason, created_at)
SELECT pg_temp.uid('resched:' || s.label), s.session_id,
       CASE WHEN v.by_tutor THEN s.tutor_user ELSE s.student_user END,
       CASE v.status WHEN 'ACCEPTED' THEN s.start_at ELSE s.start_at + interval '1 day' END,
       CASE v.status WHEN 'ACCEPTED' THEN s.end_at ELSE s.end_at + interval '1 day' END,
       v.reason, v.status,
       CASE v.status WHEN 'PENDING' THEN now() + interval '40 hours' ELSE s.start_at - interval '2 days' END,
       CASE WHEN v.status IN ('ACCEPTED', 'REJECTED') THEN CASE WHEN v.by_tutor THEN s.student_user ELSE s.tutor_user END END,
       CASE WHEN v.status IN ('ACCEPTED', 'REJECTED') THEN s.start_at - interval '3 days' END,
       v.response, CASE v.status WHEN 'PENDING' THEN now() - interval '8 hours' ELSE s.start_at - interval '5 days' END
FROM (VALUES
    ('c1:P7',  'ACCEPTED', true,  'Cô xin đổi sang giờ này vì trùng lịch họp.', 'Dạ được cô.'),
    ('c1:P8',  'REJECTED', false, 'Con xin dời sang hôm sau được không cô?', 'Cô đã kín lịch hôm sau, mình giữ lịch cũ nhé.'),
    ('c1:P10', 'EXPIRED',  true,  'Cô đề nghị dời sang tối hôm sau.', NULL),
    ('c1:F2',  'PENDING',  true,  'Cô có buổi họp phụ huynh tối hôm đó, xin dời sang tối hôm sau.', NULL),
    ('c1:F3',  'PENDING',  false, 'Con có lịch thi thử, xin dời buổi này sang tối hôm sau được không cô?', NULL)
) AS v(label, status, by_tutor, reason, response)
JOIN demo_s s ON s.label = v.label;

INSERT INTO session_history (history_id, session_id, reschedule_proposal_id, action, actor_user_id, reason, created_at)
SELECT pg_temp.uid('hist:rp:' || proposal_id), session_id, proposal_id, 'RESCHEDULE_PROPOSE', proposed_by, reason, created_at
FROM session_reschedule_proposal;
INSERT INTO session_history (history_id, session_id, reschedule_proposal_id, action, actor_user_id, reason, created_at)
SELECT pg_temp.uid('hist:rr:' || proposal_id), session_id, proposal_id,
       CASE status WHEN 'ACCEPTED' THEN 'RESCHEDULE_ACCEPT' WHEN 'REJECTED' THEN 'RESCHEDULE_REJECT' ELSE 'RESCHEDULE_EXPIRE' END,
       responded_by, response_reason, COALESCE(responded_at, expires_at)
FROM session_reschedule_proposal WHERE status <> 'PENDING';

-- =========================================================================
-- 8. THANH TOÁN
-- =========================================================================
-- Sổ cái payment_entry là nguồn sự thật; ví (payment_wallet) được dựng lại từ sổ cái ở cuối phần này.
-- Khóa sổ cái theo đúng PaymentStore: topup:, reserve:, settle-payer:, settle-tutor:, commission:, withdraw-reserve:,
-- withdraw-result:. Phí nền tảng 1000 bps (10%).
CREATE TEMP TABLE demo_pay ON COMMIT DROP AS
SELECT s.session_id, s.class_id, s.student_id, s.tutor_user, s.payer_user, s.price AS amount, s.end_at, s.pay, s.rec,
       s.label, s.status AS session_status,
       GREATEST(s.created_at + interval '4 hours', LEAST(s.start_at - interval '2 days', now() - interval '1 hour')) AS funded_at,
       CASE s.pay
           WHEN 'SETTLED' THEN s.rec + interval '3 hours'
           WHEN 'AUTO' THEN s.rec + interval '24 hours 10 minutes'
           WHEN 'REFUND' THEN CASE s.status WHEN 'ABSENT' THEN s.rec + interval '10 minutes'
                                            ELSE s.start_at - interval '5 hours' + interval '10 minutes' END
       END AS settle_at,
       CASE s.pay WHEN 'DISPUTED' THEN s.end_at + interval '2 hours'
                  WHEN 'ADMIN' THEN s.end_at + interval '4 hours' END AS dispute_at
FROM demo_s s WHERE s.pay IS NOT NULL;

INSERT INTO payment_charge (session_id, class_id, student_id, tutor_user_id, payer_user_id, amount, commission_bps, end_at, status,
                            learner_accepted, tutor_accepted, auto_accepted, dispute_reason, dispute_opened_at, learner_report,
                            tutor_report, learner_amount, tutor_amount, settled_amount, updated_at, review_started_at,
                            dispute_category, dispute_evidence, dispute_reporter_id)
SELECT p.session_id, p.class_id, p.student_id, p.tutor_user, p.payer_user, p.amount, 1000, p.end_at,
       CASE p.pay WHEN 'FUNDED' THEN 'FUNDED' WHEN 'AWAIT' THEN 'AWAITING_CONFIRMATION' WHEN 'DISPUTED' THEN 'DISPUTED'
                  WHEN 'ADMIN' THEN 'ADMIN_REVIEW' WHEN 'REFUND' THEN 'REFUNDED' ELSE 'SETTLED' END,
       p.pay = 'SETTLED', p.pay IN ('SETTLED', 'AWAIT'), p.pay = 'AUTO',
       CASE p.pay WHEN 'DISPUTED' THEN 'Buổi học bị mất kết nối khoảng 30 phút giữa chừng nên con học không đủ thời lượng đã đặt.'
                  WHEN 'ADMIN' THEN 'Buổi học chỉ kéo dài khoảng 45 phút trong khi đã đặt 90 phút.' END,
       p.dispute_at,
       CASE p.pay WHEN 'ADMIN' THEN 'Con ra khỏi phòng học lúc 19:45. Gia đình đề nghị chỉ thanh toán 125.000đ cho 45 phút đã học.' END,
       CASE p.pay WHEN 'ADMIN' THEN 'Tôi dạy đủ 90 phút, học sinh tự thoát phòng sớm do mất kết nối ở phía gia đình. Đề nghị thanh toán đủ.' END,
       CASE p.pay WHEN 'ADMIN' THEN 125000 END,
       CASE p.pay WHEN 'ADMIN' THEN p.amount END,
       CASE WHEN p.pay IN ('SETTLED', 'AUTO') THEN p.amount ELSE 0 END,
       COALESCE(p.settle_at, p.dispute_at, p.rec, p.funded_at),
       CASE WHEN p.session_status = 'COMPLETED' THEN p.rec END,
       CASE p.pay WHEN 'DISPUTED' THEN 'TECHNICAL' WHEN 'ADMIN' THEN 'DURATION' ELSE 'OTHER' END,
       CASE p.pay WHEN 'DISPUTED' THEN 'Ảnh chụp màn hình lúc phòng học báo mất kết nối từ 16:40 đến 17:10.'
                  WHEN 'ADMIN' THEN 'Ảnh chụp nhật ký phòng học cho thấy học sinh rời phòng lúc 19:45.' END,
       CASE WHEN p.pay IN ('DISPUTED', 'ADMIN') THEN p.payer_user END
FROM demo_pay p;

INSERT INTO payment_case_message (id, session_id, author_id, side, message, created_at)
SELECT pg_temp.uid('case:' || p.label || ':' || m.n), p.session_id,
       CASE m.side WHEN 'PAYER' THEN p.payer_user ELSE p.tutor_user END, m.side, m.message, p.dispute_at + m.delay
FROM demo_pay p
JOIN (VALUES
    ('ADMIN', 1, 'PAYER', 'Gia đình đính kèm nhật ký phòng học. Con rời phòng lúc 19:45 do không nghe được tiếng cô.', interval '2 hours'),
    ('ADMIN', 2, 'TUTOR', 'Cô vẫn ở trong phòng đến 20:30 và có ghi hình màn hình. Cô xin gửi bổ sung nếu cần.', interval '5 hours'),
    ('ADMIN', 3, 'PAYER', 'Gia đình đồng ý dạy bù nhưng đề nghị trừ học phí phần thời gian không học được.', interval '9 hours'),
    ('DISPUTED', 1, 'TUTOR', 'Cô đã dạy bù 15 phút cuối để đủ nội dung. Mong gia đình xem lại thời lượng thực tế.', interval '3 hours')
) AS m(pay, n, side, message, delay) ON m.pay = p.pay;

INSERT INTO payment_entry (user_id, event_key, kind, available_delta, held_delta, reference_id, created_at)
SELECT p.payer_user, 'reserve:' || p.session_id, 'SESSION_RESERVED', -p.amount, p.amount, p.session_id, p.funded_at FROM demo_pay p;
INSERT INTO payment_entry (user_id, event_key, kind, available_delta, held_delta, reference_id, created_at)
SELECT p.payer_user, 'settle-payer:' || p.session_id, 'SESSION_SETTLED',
       CASE WHEN p.pay = 'REFUND' THEN p.amount ELSE 0 END, -p.amount, p.session_id, p.settle_at
FROM demo_pay p WHERE p.pay IN ('SETTLED', 'AUTO', 'REFUND');
INSERT INTO payment_entry (user_id, event_key, kind, available_delta, held_delta, reference_id, created_at)
SELECT p.tutor_user, 'settle-tutor:' || p.session_id, 'TUITION_INCOME', p.amount - p.amount * 1000 / 10000, 0, p.session_id, p.settle_at
FROM demo_pay p WHERE p.pay IN ('SETTLED', 'AUTO');
INSERT INTO payment_entry (user_id, event_key, kind, available_delta, held_delta, reference_id, created_at)
SELECT NULL, 'commission:' || p.session_id, 'COMMISSION', p.amount * 1000 / 10000, 0, p.session_id, p.settle_at
FROM demo_pay p WHERE p.pay IN ('SETTLED', 'AUTO');

INSERT INTO payment_audit (id, actor_id, reference_id, action, detail, created_at)
SELECT pg_temp.uid('audit:fund:' || p.session_id), p.payer_user, p.session_id, 'SESSION_FUNDED',
       'VND=' || p.amount || '; commissionBps=1000', p.funded_at FROM demo_pay p;
INSERT INTO payment_audit (id, actor_id, reference_id, action, detail, created_at)
SELECT pg_temp.uid('audit:result:' || p.session_id), NULL, p.session_id, 'FINANCIAL_RESULT',
       CASE p.pay WHEN 'REFUND' THEN 'REFUNDED' ELSE 'SETTLED' END || '; amount=' || CASE WHEN p.pay = 'REFUND' THEN 0 ELSE p.amount END
           || '; fee=' || CASE WHEN p.pay = 'REFUND' THEN 0 ELSE p.amount * 1000 / 10000 END, p.settle_at
FROM demo_pay p WHERE p.pay IN ('SETTLED', 'AUTO', 'REFUND');

-- Nạp tiền qua payOS: parent.demo trả học phí lớp 1 và 3, student.demo tự trả lớp 2
INSERT INTO payment_topup (id, order_code, user_id, amount, provider, status, checkout_url, provider_id, transaction_reference,
                           expires_at, created_at)
SELECT pg_temp.uid('topup:' || v.key), v.order_code, v.user_id, v.amount, 'PAYOS', v.status, NULL,
       CASE WHEN v.status = 'PAID' THEN 'payos-demo-' || v.order_code END,
       CASE WHEN v.status = 'PAID' THEN 'FT-DEMO-' || v.order_code END,
       now() - make_interval(days => v.days_ago) + interval '15 minutes', now() - make_interval(days => v.days_ago)
FROM (VALUES
    ('parent1', 9000000001::bigint, '33333333-3333-3333-3333-333333333333'::uuid, 2000000::bigint, 'PAID', 160),
    ('parent2', 9000000002, '33333333-3333-3333-3333-333333333333', 2000000, 'PAID', 80),
    ('parent3', 9000000003, '33333333-3333-3333-3333-333333333333', 2000000, 'PAID', 50),
    ('parent4', 9000000004, '33333333-3333-3333-3333-333333333333', 2000000, 'PAID', 22),
    ('student1', 9000000005, '11111111-1111-1111-1111-111111111111', 1500000, 'PAID', 45),
    ('student2', 9000000006, '11111111-1111-1111-1111-111111111111', 300000, 'CANCELLED', 12)
) AS v(key, order_code, user_id, amount, status, days_ago);

INSERT INTO payment_entry (user_id, event_key, kind, available_delta, held_delta, reference_id, created_at)
SELECT t.user_id, 'topup:' || t.id, 'TOPUP', t.amount, 0, t.id, t.created_at + interval '2 minutes'
FROM payment_topup t WHERE t.status = 'PAID';
INSERT INTO payment_audit (id, actor_id, reference_id, action, detail, created_at)
SELECT pg_temp.uid('audit:topup:' || t.id), NULL, t.id, 'TOPUP_CONFIRMED', t.transaction_reference, t.created_at + interval '2 minutes'
FROM payment_topup t WHERE t.status = 'PAID';

-- Rút tiền của tutor.demo: một khoản đã chuyển, một khoản bị từ chối, một khoản đang chờ admin duyệt
INSERT INTO payment_payout_account (user_id, id, bank_name, bank_bin, bank_account, bank_holder, updated_at) VALUES
    ('22222222-2222-2222-2222-222222222222', pg_temp.uid('payout:demo'), 'Vietcombank', '970436', '0011001234567',
     'NGUYEN HOANG LAN', now() - interval '31 days');

INSERT INTO payment_withdrawal (id, user_id, amount, status, bank_name, bank_account, bank_holder, transaction_reference,
                                admin_note, created_at, bank_bin, payout_requested_at)
VALUES
    (pg_temp.uid('wd:paid'), '22222222-2222-2222-2222-222222222222', 1500000, 'PAID', 'Vietcombank', '0011001234567',
     'NGUYEN HOANG LAN', 'VCB-DEMO-0001', 'Đã chuyển khoản', now() - interval '30 days', '970436', now() - interval '30 days'),
    (pg_temp.uid('wd:rejected'), '22222222-2222-2222-2222-222222222222', 300000, 'REJECTED', 'Vietcombank', '0011001234566',
     'NGUYEN HOANG LAN', NULL, 'Số tài khoản không khớp tên chủ tài khoản, vui lòng kiểm tra lại.', now() - interval '45 days', '970436', NULL),
    (pg_temp.uid('wd:pending'), '22222222-2222-2222-2222-222222222222', 500000, 'PENDING', 'Vietcombank', '0011001234567',
     'NGUYEN HOANG LAN', NULL, NULL, now() - interval '1 day', '970436', NULL);

INSERT INTO payment_entry (user_id, event_key, kind, available_delta, held_delta, reference_id, created_at)
SELECT w.user_id, 'withdraw-reserve:' || w.id, 'WITHDRAW_RESERVED', -w.amount, w.amount, w.id, w.created_at FROM payment_withdrawal w;
INSERT INTO payment_entry (user_id, event_key, kind, available_delta, held_delta, reference_id, created_at)
SELECT w.user_id, 'withdraw-result:' || w.id,
       CASE w.status WHEN 'PAID' THEN 'WITHDRAW_PAID' ELSE 'WITHDRAW_REJECTED' END,
       CASE w.status WHEN 'PAID' THEN 0 ELSE w.amount END, -w.amount, w.id, w.created_at + interval '1 day'
FROM payment_withdrawal w WHERE w.status IN ('PAID', 'REJECTED');
INSERT INTO payment_audit (id, actor_id, reference_id, action, detail, created_at)
SELECT pg_temp.uid('audit:wd:' || w.id), '44444444-4444-4444-4444-444444444444', w.id,
       CASE w.status WHEN 'PAID' THEN 'WITHDRAW_PAID' ELSE 'WITHDRAW_REJECTED' END,
       COALESCE(w.admin_note, '') || ' | ' || COALESCE(w.transaction_reference, ''), w.created_at + interval '1 day'
FROM payment_withdrawal w WHERE w.status IN ('PAID', 'REJECTED');

-- Ví = tổng sổ cái
INSERT INTO payment_wallet (user_id, available, held)
SELECT user_id, sum(available_delta), sum(held_delta) FROM payment_entry WHERE user_id IS NOT NULL GROUP BY user_id;

-- =========================================================================
-- 9. LỘ TRÌNH, PHẢN HỒI BUỔI HỌC, TIẾN ĐỘ
-- =========================================================================
-- Danh mục chủ đề chuẩn do admin.demo tạo
INSERT INTO topic_catalog (topic_id, subject_id, education_level_id, name, sort_order, created_by) VALUES
    (pg_temp.uid('topic:can-thuc'),  1, 4, 'Căn bậc hai và biểu thức chứa căn', 1, '44444444-4444-4444-4444-444444444444'),
    (pg_temp.uid('topic:ham-so'),    1, 4, 'Hàm số bậc nhất và đồ thị', 2, '44444444-4444-4444-4444-444444444444'),
    (pg_temp.uid('topic:he-pt'),     1, 4, 'Hệ phương trình bậc nhất hai ẩn', 3, '44444444-4444-4444-4444-444444444444'),
    (pg_temp.uid('topic:lap-pt'),    1, 4, 'Giải bài toán bằng cách lập phương trình', 4, '44444444-4444-4444-4444-444444444444'),
    (pg_temp.uid('topic:duong-tron'),1, 4, 'Đường tròn và góc với đường tròn', 5, '44444444-4444-4444-4444-444444444444'),
    (pg_temp.uid('topic:de-thi'),    1, 4, 'Luyện đề tuyển sinh vào lớp 10', 6, '44444444-4444-4444-4444-444444444444'),
    (pg_temp.uid('topic:en-grammar'),3, 4, 'Ngữ pháp trọng tâm lớp 9', 1, '44444444-4444-4444-4444-444444444444'),
    (pg_temp.uid('topic:en-vocab'),  3, 4, 'Từ vựng và đọc hiểu theo chủ đề', 2, '44444444-4444-4444-4444-444444444444'),
    (pg_temp.uid('topic:en-writing'),3, 4, 'Viết đoạn văn ngắn', 3, '44444444-4444-4444-4444-444444444444');

INSERT INTO learning_plan (plan_id, class_id, summary, version, created_at, updated_at)
SELECT pg_temp.uid('plan:' || c.key), c.class_id,
       CASE c.key
           WHEN 'c1' THEN 'Củng cố Đại số lớp 9 (căn thức, hàm số, hệ phương trình, giải bài toán bằng lập phương trình), sau đó luyện Hình học và đề thi tuyển sinh vào lớp 10 Hà Nội.'
           ELSE 'Củng cố ngữ pháp cốt lõi, mở rộng từ vựng và đọc hiểu, rồi luyện viết đoạn văn ngắn để cải thiện điểm tiếng Anh trên lớp.' END,
       0, c.started_at + interval '1 day', now() - interval '2 days'
FROM demo_cls c WHERE c.key IN ('c1', 'c2');

-- Phản hồi sau buổi học theo thứ tự thời gian của từng lớp (buổi đang chờ xác nhận học phí hoặc đang khiếu nại chưa có phản hồi:
-- đây là việc cần làm của gia sư). pos = thứ tự từ buổi cũ nhất.
CREATE TEMP TABLE demo_fb ON COMMIT DROP AS
SELECT f.cls, f.pos, f.ms, f.level, f.correct, f.total, f.evidence, f.hw, f.chips, f.msg, f.checks, f.achieve, f.late, f.note, f.rating
FROM (VALUES
    ('c1', 1, 'm1', 1, 3, 10, NULL, 'ASSIGNED', 'FOCUSED', 'Con còn lúng túng khi rút gọn căn thức, cô giao 10 bài rút gọn để làm lại.', ARRAY[]::int[], false, false, 'Ôn lại hằng đẳng thức, giao bài về nhà dạng rút gọn cơ bản.', NULL::int),
    ('c1', 2, 'm1', 2, 6, 10, NULL, 'PREVIOUSLY_ASSIGNED', 'FOCUSED,ASKS_QUESTIONS', 'Làm được khi có gợi ý, tiến bộ rõ so với buổi trước.', ARRAY[]::int[], false, false, NULL, NULL),
    ('c1', 3, 'm1', 2, 7, 10, NULL, 'ASSIGNED', 'HARD_WORKING', 'Đã nắm cách tìm điều kiện xác định, cần luyện thêm dạng chứa tham số.', ARRAY[]::int[], false, false, NULL, 2),
    ('c1', 4, 'm1', 3, 9, 10, NULL, 'NONE', 'FOCUSED,HARD_WORKING', 'Con tự làm đúng cả ba tiêu chí, chốt đạt cột mốc căn thức.', ARRAY[0,1,2], true, false, 'Chốt cột mốc căn bậc hai, sang hàm số bậc nhất từ buổi sau.', 3),
    ('c1', 5, 'm2', 1, NULL, NULL, NULL, 'ASSIGNED', 'FOCUSED', 'Chưa quen cách vẽ đồ thị hàm số, cần luyện lập bảng giá trị.', ARRAY[]::int[], false, false, NULL, NULL),
    ('c1', 6, 'm2', 2, 5, 8, NULL, 'PREVIOUSLY_ASSIGNED', 'ASKS_QUESTIONS', 'Làm được khi có gợi ý, còn nhầm hệ số góc ở bài hai đường thẳng song song.', ARRAY[]::int[], false, true, 'Phản hồi gửi muộn do gia sư bận kỳ thi cuối kỳ.', NULL),
    ('c1', 7, 'm2', 3, 7, 8, NULL, 'NONE', 'FOCUSED', 'Tự làm đúng cả ba tiêu chí, đạt cột mốc hàm số bậc nhất.', ARRAY[0,1,2], true, false, NULL, NULL),
    ('c1', 8, 'm3', 2, 4, 8, NULL, 'ASSIGNED', 'HARD_WORKING', 'Giải hệ bằng phương pháp thế còn chậm, cần luyện thêm.', ARRAY[]::int[], false, false, NULL, NULL),
    ('c1', 9, 'm3', 2, 6, 8, NULL, 'ASSIGNED', 'FOCUSED,ASKS_QUESTIONS', 'Đã thành thạo phương pháp cộng đại số.', ARRAY[]::int[], false, false, NULL, NULL),
    ('c1', 10, 'm3', 3, 8, 9, NULL, 'NONE', 'FOCUSED', 'Tự giải đúng hệ có tham số cơ bản, chốt đạt cột mốc hệ phương trình.', ARRAY[0,1,2], true, false, 'Cột mốc hệ phương trình đạt sớm hơn kế hoạch.', 2),
    ('c1', 11, 'm4', 1, 2, 6, NULL, 'ASSIGNED', 'NEEDS_MORE_PRACTICE', 'Con chưa biết đặt ẩn cho bài toán chuyển động, cô hướng dẫn lại cách lập bảng.', ARRAY[]::int[], false, false, NULL, NULL),
    ('c1', 12, 'm4', 2, 4, 6, NULL, 'PREVIOUSLY_ASSIGNED', 'FOCUSED', 'Lập được phương trình khi có gợi ý ở bài chuyển động.', ARRAY[]::int[], false, false, NULL, NULL),
    ('c1', 13, 'm4', 2, 5, 6, NULL, 'ASSIGNED', 'FOCUSED,HARD_WORKING', 'Đã lập đúng phương trình dạng chuyển động, chuyển sang dạng năng suất.', ARRAY[0], false, false, NULL, NULL),
    ('c1', 14, 'm4', 3, 5, 6, NULL, 'ASSIGNED', 'FOCUSED', 'Tự lập phương trình bài năng suất – công việc.', ARRAY[1], false, false, NULL, 3),
    ('c1', 15, 'm4', 3, 6, 7, NULL, 'ASSIGNED', 'ASKS_QUESTIONS', 'Còn thiếu bước đối chiếu điều kiện khi trình bày, cần luyện thêm.', ARRAY[]::int[], false, false, 'Dặn trình bày đủ bước đối chiếu điều kiện.', NULL),
    ('c1', 16, 'm5', 1, NULL, NULL, NULL, 'ASSIGNED', 'FOCUSED', 'Bắt đầu chương đường tròn, con cần học thuộc các định lý về góc.', ARRAY[]::int[], false, false, 'Buổi đầu chương đường tròn.', 2),
    ('c2', 1, 'e1', 1, 4, 10, NULL, 'ASSIGNED', 'FOCUSED', 'Con còn nhầm các thì hiện tại và quá khứ, cô giao bài luyện thì.', ARRAY[]::int[], false, false, NULL, NULL),
    ('c2', 2, 'e1', 2, 6, 10, NULL, 'PREVIOUSLY_ASSIGNED', 'ASKS_QUESTIONS', 'Đúng phần lớn khi có gợi ý, cần luyện thêm câu điều kiện.', ARRAY[]::int[], false, false, NULL, NULL),
    ('c2', 3, 'e1', 2, 8, 10, NULL, 'ASSIGNED', 'HARD_WORKING', 'Tự làm đúng 8/10 câu, còn nhầm câu bị động.', ARRAY[0], false, false, NULL, 3),
    ('c2', 4, 'e1', 3, 9, 10, NULL, 'NONE', 'FOCUSED,HARD_WORKING', 'Tự làm đúng cả dạng bị động và câu điều kiện, chốt đạt cột mốc ngữ pháp.', ARRAY[1,2], true, false, 'Chốt ngữ pháp cốt lõi, buổi sau chuyển sang từ vựng theo chủ đề.', 3)
) AS f(cls, pos, ms, level, correct, total, evidence, hw, chips, msg, checks, achieve, late, note, rating);

CREATE TEMP TABLE demo_fb_s ON COMMIT DROP AS
SELECT f.*, s.session_id, s.end_at, s.tutor_user, s.student_user, s.class_id, s.start_at,
       CASE WHEN f.late THEN s.end_at + interval '60 hours' ELSE s.end_at + interval '90 minutes' END AS fb_at
FROM demo_fb f
JOIN (SELECT cls, session_id, end_at, start_at, tutor_user, student_user, class_id,
             row_number() OVER (PARTITION BY cls ORDER BY start_at) AS pos
      FROM demo_s WHERE status = 'COMPLETED' AND pay NOT IN ('AWAIT', 'DISPUTED') AND cls IN ('c1', 'c2')) s
  ON s.cls = f.cls AND s.pos = f.pos;

-- Cột mốc: trạng thái và ngày đạt suy ra từ phản hồi; hạn của cột mốc đã đạt sớm hơn ngày đạt 4 ngày
CREATE TEMP TABLE demo_ms ON COMMIT DROP AS
SELECT m.cls, m.ms, m.sort, m.title, m.descr, m.weight, m.target_off, m.topic, m.suggested FROM (VALUES
    ('c1', 'm1', 0, 'Căn bậc hai và biểu thức chứa căn', 'Rút gọn, tìm điều kiện xác định và giải phương trình chứa căn.', 2, 0, 'can-thuc', 4),
    ('c1', 'm2', 1, 'Hàm số bậc nhất và đồ thị', 'Vẽ đồ thị, xác định hệ số góc, vị trí tương đối của hai đường thẳng.', 3, 0, 'ham-so', 4),
    ('c1', 'm3', 2, 'Hệ phương trình bậc nhất hai ẩn', 'Giải hệ bằng phương pháp thế và cộng đại số, hệ chứa tham số cơ bản.', 3, 0, 'he-pt', 5),
    ('c1', 'm4', 3, 'Giải bài toán bằng cách lập phương trình', 'Các dạng chuyển động, năng suất – công việc, trình bày lời giải đầy đủ.', 4, 12, 'lap-pt', 6),
    ('c1', 'm5', 4, 'Đường tròn và góc với đường tròn', 'Tứ giác nội tiếp, góc nội tiếp, tiếp tuyến, cung và hình quạt.', 3, 35, 'duong-tron', 6),
    ('c1', 'm6', 5, 'Luyện đề thi tuyển sinh vào lớp 10', 'Làm đề theo cấu trúc Sở GD&ĐT Hà Nội, rèn tốc độ và cách trình bày.', 3, 65, 'de-thi', 5),
    ('c2', 'e1', 0, 'Ngữ pháp cốt lõi lớp 9', 'Các thì, câu bị động và câu điều kiện thường gặp trong bài kiểm tra.', 3, 0, 'en-grammar', 4),
    ('c2', 'e2', 1, 'Từ vựng và đọc hiểu chủ đề trường học', 'Mở rộng từ vựng theo chủ đề, kỹ thuật đọc hiểu tìm ý chính.', 3, 20, 'en-vocab', 4),
    ('c2', 'e3', 2, 'Viết đoạn văn ngắn', 'Viết đoạn 80–100 từ có câu chủ đề, ý hỗ trợ và câu kết.', 2, 45, 'en-writing', 3)
) AS m(cls, ms, sort, title, descr, weight, target_off, topic, suggested);

INSERT INTO milestone (milestone_id, plan_id, title, description, target_date, weight, status, achieved_date, is_archived, sort_order,
                       created_at, suggested_sessions, topic_id)
SELECT pg_temp.uid('ms:' || m.cls || ':' || m.ms), pg_temp.uid('plan:' || m.cls), m.title, m.descr,
       CASE WHEN a.achieved_on IS NOT NULL THEN a.achieved_on + 4 ELSE pg_temp.today() + m.target_off END,
       m.weight,
       CASE WHEN a.achieved_on IS NOT NULL THEN 'ACHIEVED' WHEN a.started THEN 'IN_PROGRESS' ELSE 'NOT_STARTED' END,
       a.achieved_on, false, m.sort, c.started_at + interval '1 day', m.suggested, pg_temp.uid('topic:' || m.topic)
FROM demo_ms m
JOIN demo_cls c ON c.key = m.cls
LEFT JOIN LATERAL (
    SELECT true AS started,
           (SELECT (max(f2.fb_at) AT TIME ZONE 'Asia/Ho_Chi_Minh')::date FROM demo_fb_s f2 WHERE f2.cls = m.cls AND f2.ms = m.ms AND f2.achieve) AS achieved_on
    FROM demo_fb_s f WHERE f.cls = m.cls AND f.ms = m.ms LIMIT 1
) a ON true;

INSERT INTO milestone_criterion (criterion_id, milestone_id, content, sort_order, is_checked, checked_at, created_at)
SELECT pg_temp.uid('cr:' || cr.cls || ':' || cr.ms || ':' || cr.sort), pg_temp.uid('ms:' || cr.cls || ':' || cr.ms), cr.content, cr.sort,
       ck.at IS NOT NULL, ck.at, c.started_at + interval '1 day'
FROM (VALUES
    ('c1','m1',0,'Rút gọn được biểu thức chứa căn bậc hai'), ('c1','m1',1,'Tìm được điều kiện xác định của căn thức'),
    ('c1','m1',2,'Giải được phương trình chứa căn đơn giản'),
    ('c1','m2',0,'Vẽ được đồ thị hàm số y = ax + b'), ('c1','m2',1,'Xác định hệ số góc và vị trí hai đường thẳng'),
    ('c1','m2',2,'Viết được phương trình đường thẳng đi qua hai điểm'),
    ('c1','m3',0,'Giải được hệ bằng phương pháp thế'), ('c1','m3',1,'Giải được hệ bằng phương pháp cộng đại số'),
    ('c1','m3',2,'Giải được hệ chứa tham số cơ bản'),
    ('c1','m4',0,'Lập được phương trình bài toán chuyển động'), ('c1','m4',1,'Lập được phương trình bài toán năng suất – công việc'),
    ('c1','m4',2,'Trình bày lời giải đầy đủ có đối chiếu điều kiện'),
    ('c1','m5',0,'Chứng minh được tứ giác nội tiếp'), ('c1','m5',1,'Vận dụng góc nội tiếp và góc ở tâm'),
    ('c1','m5',2,'Vận dụng tính chất tiếp tuyến, cát tuyến'), ('c1','m5',3,'Tính độ dài cung và diện tích hình quạt'),
    ('c1','m6',0,'Làm đề thi thử đạt từ 8 điểm'), ('c1','m6',1,'Hoàn thành đề trong 120 phút'),
    ('c2','e1',0,'Phân biệt và dùng đúng các thì cơ bản'), ('c2','e1',1,'Chuyển được câu chủ động sang bị động'),
    ('c2','e1',2,'Dùng đúng câu điều kiện loại 1 và 2'),
    ('c2','e2',0,'Nắm 40 từ vựng chủ đề trường học'), ('c2','e2',1,'Tìm đúng ý chính của đoạn đọc ngắn'),
    ('c2','e3',0,'Viết đoạn văn có câu chủ đề rõ ràng'), ('c2','e3',1,'Dùng từ nối để liên kết các ý')
) AS cr(cls, ms, sort, content)
JOIN demo_cls c ON c.key = cr.cls
LEFT JOIN LATERAL (
    SELECT min(f.fb_at) AS at FROM demo_fb_s f WHERE f.cls = cr.cls AND f.ms = cr.ms AND cr.sort = ANY (f.checks)
) ck ON true;

INSERT INTO session_feedback (feedback_id, session_id, class_id, homework_status, attitude_chips, created_by, created_at, parent_message, submitted_late)
SELECT pg_temp.uid('fb:' || f.session_id), f.session_id, f.class_id, f.hw, NULLIF(f.chips, ''), f.tutor_user, f.fb_at,
       f.msg, f.late
FROM demo_fb_s f;

INSERT INTO milestone_assessment (assessment_id, milestone_id, session_id, level, correct_count, total_count, assessed_by, created_at, evidence_note)
SELECT pg_temp.uid('as:' || f.session_id), pg_temp.uid('ms:' || f.cls || ':' || f.ms), f.session_id, f.level, f.correct, f.total,
       f.tutor_user, f.fb_at, f.evidence
FROM demo_fb_s f;

INSERT INTO session_milestone_link (link_id, session_id, milestone_id, link_type, created_by, created_at)
SELECT pg_temp.uid('lk:a:' || f.session_id), f.session_id, pg_temp.uid('ms:' || f.cls || ':' || f.ms), 'ACTUAL', f.tutor_user, f.fb_at
FROM demo_fb_s f;
INSERT INTO session_milestone_link (link_id, session_id, milestone_id, link_type, created_by, created_at)
SELECT pg_temp.uid('lk:p:' || s.session_id || v.ms), s.session_id, pg_temp.uid('ms:' || s.cls || ':' || v.ms), 'PLANNED', s.tutor_user,
       now() - interval '1 day'
FROM (VALUES ('c1:F1', 'm4'), ('c1:F2', 'm4'), ('c1:F2', 'm5'), ('c1:F3', 'm5'), ('c2:F1', 'e2')) AS v(label, ms)
JOIN demo_s s ON s.label = v.label;

INSERT INTO session_note (note_id, session_id, tutor_id, note_text, created_at)
SELECT pg_temp.uid('note:' || f.session_id), f.session_id, s.tutor_id, f.note, f.fb_at
FROM demo_fb_s f JOIN demo_s s ON s.session_id = f.session_id WHERE f.note IS NOT NULL;

INSERT INTO session_self_rating (rating_id, session_id, class_id, student_id, rating, created_by, created_at, updated_at)
SELECT pg_temp.uid('sr:' || f.session_id), f.session_id, f.class_id, s.student_id, f.rating, f.student_user,
       f.end_at + interval '3 hours', f.end_at + interval '3 hours'
FROM demo_fb_s f JOIN demo_s s ON s.session_id = f.session_id WHERE f.rating IS NOT NULL;

-- Lịch sử lộ trình
INSERT INTO plan_history (history_id, plan_id, milestone_id, action, actor_user_id, old_value, new_value, created_at)
SELECT pg_temp.uid('ph:plan:' || c.key), pg_temp.uid('plan:' || c.key), NULL, 'PLAN_CREATED', c.tutor_user, NULL, 'Tạo lộ trình học', c.started_at + interval '1 day'
FROM demo_cls c WHERE c.key IN ('c1', 'c2');
INSERT INTO plan_history (history_id, plan_id, milestone_id, action, actor_user_id, old_value, new_value, created_at)
SELECT pg_temp.uid('ph:ms:' || m.milestone_id), m.plan_id, m.milestone_id, 'MILESTONE_CREATED', c.tutor_user, NULL, m.title, m.created_at
FROM milestone m JOIN demo_cls c ON pg_temp.uid('plan:' || c.key) = m.plan_id;
INSERT INTO plan_history (history_id, plan_id, milestone_id, action, actor_user_id, old_value, new_value, created_at)
SELECT pg_temp.uid('ph:start:' || m.milestone_id), m.plan_id, m.milestone_id, 'MILESTONE_STATUS_CHANGED', c.tutor_user,
       'NOT_STARTED', 'IN_PROGRESS', st.at
FROM milestone m JOIN demo_cls c ON pg_temp.uid('plan:' || c.key) = m.plan_id
JOIN LATERAL (SELECT min(f.fb_at) AS at FROM demo_fb_s f WHERE pg_temp.uid('ms:' || f.cls || ':' || f.ms) = m.milestone_id) st ON st.at IS NOT NULL;
INSERT INTO plan_history (history_id, plan_id, milestone_id, action, actor_user_id, old_value, new_value, created_at)
SELECT pg_temp.uid('ph:done:' || m.milestone_id), m.plan_id, m.milestone_id, 'MILESTONE_STATUS_CHANGED', c.tutor_user,
       'IN_PROGRESS', 'ACHIEVED', f.fb_at
FROM milestone m JOIN demo_cls c ON pg_temp.uid('plan:' || c.key) = m.plan_id
JOIN demo_fb_s f ON pg_temp.uid('ms:' || f.cls || ':' || f.ms) = m.milestone_id AND f.achieve
WHERE m.status = 'ACHIEVED';

-- Báo cáo kỳ 30 ngày: mọi kỳ đã trọn vẹn tính từ ngày bắt đầu lớp, gia sư đã gửi
CREATE TEMP TABLE demo_period ON COMMIT DROP AS
SELECT c.key AS cls, c.class_id, c.tutor_user, pg_temp.uid('plan:' || c.key) AS plan_id, i AS idx,
       (c.started_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + 30 * (i - 1) AS ps,
       (c.started_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + 30 * (i - 1) + 29 AS pe
FROM demo_cls c
CROSS JOIN LATERAL generate_series(1, ((pg_temp.today() - (c.started_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date) / 30)::int) i
WHERE c.key IN ('c1', 'c2');

INSERT INTO period_report (report_id, class_id, period_index, period_start, period_end, on_track_status, on_track_explanation,
                           progress_percentage, progress_delta, attended_count, counted_count, highlight, next_focus_title,
                           tutor_summary, status, auto_send_at, sent_at, version, created_at)
SELECT pg_temp.uid('report:' || p.cls || ':' || p.idx), p.class_id, p.idx, p.ps, p.pe, 'ON_TRACK',
       COALESCE((SELECT 'Cột mốc kế tiếp “' || m.title || '”, hạn ' || to_char(m.target_date, 'DD/MM/YYYY')
                 FROM milestone m WHERE m.plan_id = p.plan_id AND NOT m.is_archived AND m.target_date IS NOT NULL
                   AND (m.status <> 'ACHIEVED' OR m.achieved_date > p.pe) ORDER BY m.sort_order LIMIT 1),
                'Không có cột mốc nào quá hạn'),
       pg_temp.progress_at(p.plan_id, pg_temp.hn(p.pe, '23:59')),
       CASE WHEN p.idx > 1 THEN pg_temp.progress_at(p.plan_id, pg_temp.hn(p.pe, '23:59'))
                              - pg_temp.progress_at(p.plan_id, pg_temp.hn(p.ps - 1, '23:59')) END,
       (SELECT count(*) FROM tutoring_session s WHERE s.class_id = p.class_id AND s.status = 'COMPLETED'
          AND (s.start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date BETWEEN p.ps AND p.pe),
       (SELECT count(*) FROM tutoring_session s WHERE s.class_id = p.class_id AND s.status IN ('COMPLETED', 'ABSENT')
          AND (s.start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date BETWEEN p.ps AND p.pe),
       COALESCE((SELECT 'Đạt cột mốc: ' || string_agg(m.title, '; ' ORDER BY m.sort_order) FROM milestone m
                 WHERE m.plan_id = p.plan_id AND m.achieved_date BETWEEN p.ps AND p.pe),
                'Duy trì nhịp học đều đặn, hoàn thành bài tập về nhà.'),
       (SELECT m.title FROM milestone m WHERE m.plan_id = p.plan_id AND NOT m.is_archived
          AND (m.status <> 'ACHIEVED' OR m.achieved_date > p.pe) ORDER BY m.sort_order LIMIT 1),
       'Con tiếp thu tốt và làm bài tập đều. Cần luyện thêm các bài có lời văn để tăng tốc độ và trình bày chặt chẽ hơn.',
       'SENT', pg_temp.hn(p.pe + 1, '00:00') + interval '48 hours', pg_temp.hn(p.pe + 1, '09:00'), 0, pg_temp.hn(p.pe + 1, '00:05')
FROM demo_period p;

INSERT INTO period_report_achievement (report_id, sort_order, milestone_title)
SELECT pg_temp.uid('report:' || p.cls || ':' || p.idx), (row_number() OVER (PARTITION BY p.cls, p.idx ORDER BY m.sort_order))::int - 1, m.title
FROM demo_period p JOIN milestone m ON m.plan_id = p.plan_id AND m.achieved_date BETWEEN p.ps AND p.pe;

-- =========================================================================
-- 10. ĐÁNH GIÁ GIA SƯ
-- =========================================================================
INSERT INTO review (review_id, tutor_id, student_id, reviewer_user_id, reviewer_type, rating, comment, is_edited,
                    completed_sessions_at_review, created_at, updated_at)
SELECT pg_temp.uid('review:' || v.key), v.tutor_id, '11111111-1111-1111-1111-000000000001', v.reviewer, v.reviewer_type, v.rating,
       v.comment, false,
       (SELECT count(*) FROM tutoring_session s WHERE s.tutor_id = v.tutor_id AND s.student_id = '11111111-1111-1111-1111-000000000001'
          AND s.status = 'COMPLETED' AND s.end_at < v.at),
       v.at, v.at
FROM (VALUES
    ('demo', '22222222-2222-2222-2222-000000000001'::uuid, '33333333-3333-3333-3333-333333333333'::uuid, 'PARENT', 5,
     'Cô dạy rất tận tâm, có báo cáo tiến độ rõ ràng sau từng buổi nên gia đình luôn nắm được con đang ở đâu.', now() - interval '6 days'),
    ('ha', '22222222-2222-2222-2222-000000000002', '11111111-1111-1111-1111-111111111111', 'STUDENT', 4,
     'Cô giảng dễ hiểu, cho nhiều bài tập để luyện. Mong cô cho thêm đề thi thử vào cuối khóa.', now() - interval '98 days')
) AS v(key, tutor_id, reviewer, reviewer_type, rating, comment, at);

-- Điểm gia sư tính lại từ bảng review với các gia sư có đánh giá thật
UPDATE tutor_profile t SET rating_avg = r.avg_rating, review_count = r.cnt
FROM (SELECT tutor_id, round(avg(rating)::numeric, 2) AS avg_rating, count(*) AS cnt FROM review GROUP BY tutor_id) r
WHERE r.tutor_id = t.tutor_id;

-- =========================================================================
-- 11. THÔNG BÁO
-- =========================================================================
INSERT INTO notification (notification_id, user_id, about_student_id, type, title, short_text, target_type, target_id, is_read, created_at)
SELECT pg_temp.uid('notif:' || v.key), v.user_id, v.about, v.type, v.title, v.short_text, v.target_type, v.target_id, v.is_read, v.at
FROM (VALUES
    ('st-session', '11111111-1111-1111-1111-111111111111'::uuid, NULL::uuid, 'SESSION_PROPOSED', 'Gia sư đề xuất một buổi học mới',
     'Cô Nguyễn Hoàng Lan đề xuất thêm một buổi ôn tập. Hãy xem và phản hồi trong 3 ngày.', 'SESSION_PROPOSAL',
     (SELECT session_id FROM demo_s WHERE label = 'c1:F4'), false, now() - interval '3 hours'),
    ('st-resched', '11111111-1111-1111-1111-111111111111', NULL, 'SESSION_UPDATE', 'Gia sư xin đổi lịch một buổi học',
     'Cô xin dời buổi học sắp tới sang tối hôm sau. Hãy chấp nhận hoặc từ chối.', 'SESSION_UPDATE',
     (SELECT session_id FROM demo_s WHERE label = 'c1:F2'), false, now() - interval '8 hours'),
    ('st-ms', '11111111-1111-1111-1111-111111111111', NULL, 'MILESTONE_ACHIEVED', 'Bạn vừa đạt một cột mốc',
     'Cột mốc “Hệ phương trình bậc nhất hai ẩn” đã được gia sư ghi nhận là đạt.', NULL, NULL, true, now() - interval '30 days'),
    ('pa-report', '33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-000000000001', 'PERIOD_REPORT_SENT',
     'Báo cáo tiến độ kỳ mới của Trần Minh Anh', 'Gia sư đã gửi báo cáo tiến độ 30 ngày. Hãy xem con đang tiến bộ thế nào.',
     NULL, NULL, false, now() - interval '12 days'),
    ('pa-pay', '33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-000000000001', 'PAYMENT_SESSION',
     'Buổi học cần xác nhận học phí', 'Buổi học gần nhất đã kết thúc. Hãy xác nhận hoặc khiếu nại trong 24 giờ.',
     NULL, NULL, false, now() - interval '3 hours'),
    ('tu-conn', '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-000000000002', 'CONNECTION_REQUEST',
     'Học sinh mới gửi yêu cầu kết nối', 'Lê Đức Khôi (lớp 8) muốn học Toán lấy lại gốc với bạn.', NULL, NULL, false, now() - interval '5 hours'),
    ('tu-review', '22222222-2222-2222-2222-222222222222', NULL, 'REVIEW_RECEIVED', 'Bạn nhận được một đánh giá mới',
     'Phụ huynh của Trần Minh Anh đã đánh giá 5 sao.', NULL, NULL, true, now() - interval '6 days'),
    ('tu-pay', '22222222-2222-2222-2222-222222222222', NULL, 'PAYMENT_SESSION', 'Học phí buổi học đang được admin xem xét',
     'Phụ huynh khiếu nại thời lượng một buổi học. Hồ sơ đã chuyển cho quản trị viên.', NULL, NULL, false, now() - interval '2 days'),
    ('ad-ver', '44444444-4444-4444-4444-444444444444', NULL, 'VERIFICATION_REQUEST', 'Hồ sơ gia sư chờ duyệt',
     'Cô Ngô Thị Mai vừa nộp hồ sơ xác minh gia sư.', 'VERIFICATION_REQUEST', pg_temp.uid('ver:mai'), false, now() - interval '2 days'),
    ('ad-pay', '44444444-4444-4444-4444-444444444444', NULL, 'PAYMENT_ADMIN_REVIEW', 'Có khiếu nại học phí chờ phân xử',
     'Một khoản học phí 250.000đ đã quá hạn thương lượng, cần admin phân xử.', NULL, NULL, false, now() - interval '2 days'),
    ('kh-inv', '11111111-1111-1111-1111-111111111112', NULL, 'LINK_INVITATION', 'Lời mời liên kết phụ huynh',
     'Trần Văn Hưng mời liên kết tài khoản để theo dõi việc học của bạn.', NULL, NULL, false, now() - interval '1 day')
) AS v(key, user_id, about, type, title, short_text, target_type, target_id, is_read, at);
