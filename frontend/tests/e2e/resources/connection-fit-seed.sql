-- Test data only. Run against the isolated edufit_e2e_e database after dev migrations.
-- Tạo đúng 1 yêu cầu kết nối PENDING từ học sinh demo tới gia sư demo, gắn nhu cầu học demo,
-- để spec 11 so nhãn so khớp phía người gửi (học sinh) với phía người nhận (gia sư).
BEGIN;
DO $$ BEGIN
  IF current_database() <> 'edufit_e2e_e' THEN
    RAISE EXCEPTION 'This fixture requires the isolated edufit_e2e_e database';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM learning_need WHERE need_id = '55555555-5555-5555-5555-000000000001') THEN
    RAISE EXCEPTION 'Start backend with SPRING_PROFILES_ACTIVE=dev first';
  END IF;
END $$;

DELETE FROM connection_request r
USING student_profile s, account sa, tutor_profile t, account ta
WHERE r.student_id = s.student_id AND sa.user_id = s.user_id AND sa.email = 'student.demo@edufit.vn'
  AND r.tutor_id = t.tutor_id AND ta.user_id = t.user_id AND ta.email = 'tutor.demo@edufit.vn'
  AND r.status = 'PENDING';

INSERT INTO connection_request (student_id, tutor_id, need_id, sent_by_user_id, desired_slots, message, status, expires_at)
SELECT n.student_id, t.tutor_id, n.need_id, sa.user_id,
       '["TUE 19:00-21:00", "FRI 19:00-21:00"]'::jsonb,
       'E2E so khớp: góc nhìn gia sư', 'PENDING', NOW() + INTERVAL '3 days'
FROM learning_need n
JOIN student_profile s ON s.student_id = n.student_id
JOIN account sa ON sa.user_id = s.user_id AND sa.email = 'student.demo@edufit.vn'
CROSS JOIN tutor_profile t
JOIN account ta ON ta.user_id = t.user_id AND ta.email = 'tutor.demo@edufit.vn'
WHERE n.need_id = '55555555-5555-5555-5555-000000000001';
COMMIT;
