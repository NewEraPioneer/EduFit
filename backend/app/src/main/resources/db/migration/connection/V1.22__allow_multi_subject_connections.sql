-- =========================================================================
-- EduFit Database Migration V1.22: Cho phép kết nối và mở lớp nhiều môn học giữa cùng 1 cặp học viên - gia sư
-- =========================================================================
-- Thay thế ràng buộc duy nhất (student_id, tutor_id) bằng (student_id, tutor_id, goal_id)
-- để học viên có thể gửi yêu cầu kết nối và mở lớp học cho các môn học/mục tiêu khác nhau với cùng một gia sư.

-- 1. Yêu cầu kết nối (CONNECTION_REQUEST):
-- Cho phép gửi yêu cầu kết nối với cùng gia sư nếu mục tiêu học tập (goal_id) khác nhau
DROP INDEX IF EXISTS uq_connection_request_pending;

CREATE UNIQUE INDEX IF NOT EXISTS uq_connection_request_pending
    ON connection_request (student_id, tutor_id, goal_id)
    WHERE (status = 'PENDING');

-- 2. Lớp học 1-1 / Hợp đồng giảng dạy (CLASS == ENGAGEMENT):
-- Cho phép duy trì các lớp học đang hoạt động (ACTIVE) cho các mục tiêu học tập khác nhau với cùng gia sư
DROP INDEX IF EXISTS uq_class_student_tutor_active;

CREATE UNIQUE INDEX IF NOT EXISTS uq_class_student_tutor_active
    ON class (student_id, tutor_id, goal_id)
    WHERE (status = 'ACTIVE');
