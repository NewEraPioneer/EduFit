-- Review display fixture only; not verification of review submission rules.
BEGIN;
DO $$ BEGIN
  IF current_database() <> 'edufit_e2e_e' THEN
    RAISE EXCEPTION 'This fixture requires the isolated edufit_e2e_e database';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM tutor_profile t JOIN account a USING (user_id) WHERE a.email = 'tutor.demo@edufit.vn') THEN
    RAISE EXCEPTION 'Start backend with SPRING_PROFILES_ACTIVE=dev first';
  END IF;
END $$;
INSERT INTO review (tutor_id, student_id, reviewer_user_id, reviewer_type, rating, comment, completed_sessions_at_review)
SELECT t.tutor_id, s.student_id, s.user_id, 'STUDENT', 5,
       'E2E #47: Gia sư giải thích dễ hiểu và chuẩn bị bài học chu đáo.', 5
FROM tutor_profile t JOIN account ta ON ta.user_id = t.user_id
CROSS JOIN student_profile s JOIN account sa ON sa.user_id = s.user_id
WHERE ta.email = 'tutor.demo@edufit.vn' AND sa.email = 'student.demo@edufit.vn'
ON CONFLICT (tutor_id, student_id) DO UPDATE SET rating = EXCLUDED.rating, comment = EXCLUDED.comment;
UPDATE tutor_profile t SET
  rating_avg = (SELECT avg(rating) FROM review WHERE tutor_id = t.tutor_id),
  review_count = (SELECT count(*) FROM review WHERE tutor_id = t.tutor_id)
WHERE t.user_id = (SELECT user_id FROM account WHERE email = 'tutor.demo@edufit.vn');
COMMIT;
