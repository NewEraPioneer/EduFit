-- Read-only assertions. Included before COMMIT by enrich-local-defense.sql.
DO $$
DECLARE found_count int;
BEGIN
  SELECT count(*) INTO found_count FROM account WHERE email ~ '^tutor\.seed[0-9]{2}@edufit\.vn$' AND role='TUTOR';
  IF found_count<>10 THEN RAISE EXCEPTION 'Expected 10 new tutors, found %',found_count; END IF;
  SELECT count(*) INTO found_count FROM account WHERE email ~ '^student\.seed[0-9]{2}@edufit\.vn$' AND role='STUDENT';
  IF found_count<>8 THEN RAISE EXCEPTION 'Expected 8 new students, found %',found_count; END IF;
  IF EXISTS(SELECT 1 FROM account WHERE email ~ '^(tutor|student)\.seed[0-9]{2}@edufit\.vn$'
      AND status<>'ACTIVE') THEN RAISE EXCEPTION 'All new accounts must be active'; END IF;
  IF (SELECT count(*) FROM class WHERE class_id IN (SELECT md5('edufit-local-defense-20261110:class:'||i)::uuid FROM generate_series(1,10) i))<>10 THEN
    RAISE EXCEPTION 'Expected ten background classes'; END IF;
  IF (SELECT count(*) FROM tutoring_session WHERE session_id IN (SELECT md5('edufit-local-defense-20261110:session:'||i||':'||n)::uuid
      FROM generate_series(1,10) i CROSS JOIN generate_series(1,10) n))<>100 THEN RAISE EXCEPTION 'Expected 100 background sessions'; END IF;
  IF (SELECT count(*) FROM tutoring_session WHERE session_id IN (SELECT md5('edufit-local-defense-20261110:main-session:'||i||':'||n)::uuid
      FROM generate_series(1,2) i CROSS JOIN generate_series(1,8) n))<>16 THEN RAISE EXCEPTION 'Expected 16 new main-account sessions'; END IF;
  IF EXISTS(SELECT p.plan_id FROM learning_plan p JOIN class c USING(class_id) JOIN student_profile sp USING(student_id)
      JOIN account a ON a.user_id=sp.user_id JOIN milestone m USING(plan_id) LEFT JOIN milestone_criterion cr USING(milestone_id)
      WHERE a.email ~ '^student\.seed[0-9]{2}@edufit\.vn$' GROUP BY p.plan_id HAVING count(cr.criterion_id)<>12) THEN
    RAISE EXCEPTION 'Each new plan must have twelve criteria'; END IF;
  IF (SELECT count(*) FROM session_feedback WHERE feedback_id IN
      (SELECT md5('edufit-local-defense-20261110:feedback:'||session_id)::uuid FROM tutoring_session
       WHERE session_id IN (SELECT md5('edufit-local-defense-20261110:session:'||i||':'||n)::uuid
        FROM generate_series(1,10) i CROSS JOIN generate_series(1,6) n)))<>60 THEN RAISE EXCEPTION 'Expected sixty background feedback records'; END IF;
  IF EXISTS(SELECT 1 FROM session_feedback f JOIN tutoring_session s USING(session_id)
      JOIN tutor_profile tp ON tp.tutor_id=s.tutor_id
      WHERE f.feedback_id=md5('edufit-local-defense-20261110:feedback:'||f.session_id)::uuid
       AND (s.status<>'COMPLETED' OR f.class_id<>s.class_id OR f.created_by<>tp.user_id OR f.created_at<s.end_at OR f.created_at>now())) THEN
    RAISE EXCEPTION 'Feedback must belong to the correct completed lesson and tutor'; END IF;
  IF EXISTS(SELECT 1 FROM tutoring_session a JOIN tutoring_session b ON a.session_id<b.session_id
      AND (a.tutor_id=b.tutor_id OR a.student_id=b.student_id)
      AND a.start_at<b.end_at AND b.start_at<a.end_at WHERE a.status='SCHEDULED' AND b.status='SCHEDULED') THEN
    RAISE EXCEPTION 'Scheduled lessons must not overlap'; END IF;
  IF EXISTS(SELECT 1 FROM class c JOIN tutor_profile t USING(tutor_id) JOIN account a USING(user_id)
      WHERE a.email IN ('tutor.seed09@edufit.vn','tutor.seed10@edufit.vn')) THEN
    RAISE EXCEPTION 'Pending KYC tutors must have no classes before approval'; END IF;
  IF (SELECT count(*) FROM verification_request v JOIN tutor_profile t USING(tutor_id) JOIN account a USING(user_id)
      WHERE a.email IN ('tutor.seed09@edufit.vn','tutor.seed10@edufit.vn') AND v.status='PENDING')<>2 THEN
    RAISE EXCEPTION 'Expected two live KYC queue items'; END IF;
  IF (SELECT count(*) FROM credential_file cf JOIN credential c USING(credential_id) JOIN verification_request v USING(request_id)
      JOIN tutor_profile t USING(tutor_id) JOIN account a USING(user_id)
      WHERE a.email ~ '^tutor\.seed[0-9]{2}@edufit\.vn$' AND cf.content_type='application/pdf' AND cf.size_bytes>0)<>10 THEN
    RAISE EXCEPTION 'Expected ten demo credential files'; END IF;
  IF EXISTS(SELECT 1 FROM payment_wallet w LEFT JOIN
      (SELECT user_id,sum(available_delta) available,sum(held_delta) held FROM payment_entry WHERE user_id IS NOT NULL GROUP BY user_id) e USING(user_id)
      WHERE w.available<>COALESCE(e.available,0) OR w.held<>COALESCE(e.held,0)) THEN
    RAISE EXCEPTION 'Wallet balances must equal their existing ledger plus new reserve entries'; END IF;
  IF (SELECT count(*) FROM payment_charge WHERE session_id IN
      (md5('edufit-local-defense-20261110:main-session:1:5')::uuid,
       md5('edufit-local-defense-20261110:main-session:1:6')::uuid,
       md5('edufit-local-defense-20261110:main-session:2:6')::uuid)
      AND status IN ('DISPUTED','AWAITING_ADMIN','ADMIN_REVIEW'))<>3 THEN
    RAISE EXCEPTION 'Expected three new main-account dispute cases'; END IF;
  IF EXISTS(SELECT 1 FROM payment_charge p JOIN class c USING(class_id) JOIN student_profile sp ON sp.student_id=c.student_id
      JOIN account a ON a.user_id=sp.user_id WHERE a.email ~ '^student\.seed[0-9]{2}@edufit\.vn$') THEN
    RAISE EXCEPTION 'No financial fixtures are permitted for the new background accounts'; END IF;
END $$;
SELECT 'PASS: accounts, classes, sessions, progress, KYC, disputes, and wallet ledger' AS verification;
