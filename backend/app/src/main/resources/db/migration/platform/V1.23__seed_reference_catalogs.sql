-- =========================================================================
-- EduFit Database Migration V1.23: Reference Catalogs (mọi môi trường)
-- =========================================================================
-- Danh mục cấp học / môn học là dữ liệu tham chiếu bắt buộc của hệ thống.
-- Tài khoản và hồ sơ demo đã được tách sang classpath:db/seed (chỉ nạp ở profile dev).
-- Lặp an toàn: DB dev đã có danh mục từ seed V1.09 sẽ không bị thay đổi.

INSERT INTO education_level (level_id, name, sort_order, is_active) VALUES
    (1, 'Lớp 6', 1, true),
    (2, 'Lớp 7', 2, true),
    (3, 'Lớp 8', 3, true),
    (4, 'Lớp 9', 4, true)
ON CONFLICT DO NOTHING;

INSERT INTO subject (subject_id, name, is_active) VALUES
    (1, 'Toán', true),
    (2, 'Ngữ Văn', true),
    (3, 'Tiếng Anh', true),
    (4, 'Vật Lý', true),
    (5, 'Hóa Học', true),
    (6, 'Sinh Học', true)
ON CONFLICT DO NOTHING;

-- Đưa identity sequence vượt qua các ID đã chèn tường minh để Admin tạo danh mục mới không bị trùng khóa
SELECT setval(pg_get_serial_sequence('education_level', 'level_id'),
              (SELECT COALESCE(MAX(level_id), 1) FROM education_level));
SELECT setval(pg_get_serial_sequence('subject', 'subject_id'),
              (SELECT COALESCE(MAX(subject_id), 1) FROM subject));
