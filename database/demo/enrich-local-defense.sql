\set ON_ERROR_STOP on
\if :{?allow_demo_seed}
\else
  \echo 'Run explicitly with -v allow_demo_seed=1 on the local demo database.'
  \quit 3
\endif
BEGIN;
SET LOCAL TIME ZONE 'Asia/Ho_Chi_Minh';
SELECT pg_advisory_xact_lock(hashtextextended('edufit-local-defense-enrichment-20261110',0));
SELECT id FROM payment_mutex WHERE id=true FOR UPDATE;
CREATE FUNCTION pg_temp.eid(k text) RETURNS uuid LANGUAGE sql IMMUTABLE AS
$$ SELECT md5('edufit-local-defense-20261110:'||k)::uuid $$;
SELECT EXISTS(SELECT 1 FROM account WHERE user_id=pg_temp.eid('tutor-user:1')) AS already_loaded \gset
\if :already_loaded
  \echo 'This enrichment batch is already loaded; no data or balances changed.'
\else
DO $$ BEGIN
  IF current_database() NOT IN ('edufit','edufit_enrichment_verify_20261110') THEN
    RAISE EXCEPTION 'This seed is restricted to the local demo and its verification copy';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM flyway_schema_history WHERE version='1.38' AND success) THEN
    RAISE EXCEPTION 'Schema V1.38 is required; do not alter Flyway history';
  END IF;
  IF (SELECT count(*) FROM account WHERE email IN ('student.demo@edufit.vn','student.khoi@edufit.vn',
      'tutor.demo@edufit.vn','tutor.linh@edufit.vn','parent.demo@edufit.vn','admin.demo@edufit.vn'))<>6 THEN
    RAISE EXCEPTION 'The six original demonstration accounts must exist';
  END IF;
  IF EXISTS(SELECT 1 FROM account WHERE email ~ '^(tutor|student)\.seed[0-9]+@edufit\.vn$') THEN
    RAISE EXCEPTION 'Seed email namespace is already used; refusing to overwrite accounts';
  END IF;
END $$;

-- Supporting profiles/catalog associations only; no extra parents, admins, reviews,
-- notifications, topups, or withdrawals are seeded for the 18 new accounts.
CREATE TEMP TABLE et ON COMMIT DROP AS
SELECT v.*,pg_temp.eid('tutor-user:'||i) user_id,pg_temp.eid('tutor:'||i) tutor_id,
       pg_temp.eid('offering:'||i) offering_id,
       ((i-1)%7+1)::smallint dow,(CASE WHEN i<=7 THEN 17 ELSE 19 END)::int start_hour
FROM (VALUES
 (1,'Nguyễn Đức Huy',1,NULL::int,220000,6,'GRADUATE'),
 (2,'Phạm Ngọc Ánh',1,NULL,180000,3,'UNIVERSITY_STUDENT'),
 (3,'Trần Hoài Nam',3,2,250000,5,'GRADUATE'),
 (4,'Lê Mai Phương',3,2,200000,2,'UNIVERSITY_STUDENT'),
 (5,'Vũ Quang Minh',2,NULL,240000,7,'GRADUATE'),
 (6,'Đỗ Thu Hà',2,NULL,190000,3,'GRADUATE'),
 (7,'Bùi Anh Tuấn',7,4,260000,5,'GRADUATE'),
 (8,'Hoàng Minh Châu',7,3,230000,4,'GRADUATE'),
 (9,'Đặng Khánh Linh',2,NULL,170000,2,'UNIVERSITY_STUDENT'),
 (10,'Nguyễn Thanh Tùng',3,2,210000,3,'GRADUATE')
) v(i,full_name,subject_id,track_id,price,years,tutor_type);
CREATE TEMP TABLE es ON COMMIT DROP AS
SELECT v.*,pg_temp.eid('student-user:'||i) user_id,pg_temp.eid('student:'||i) student_id
FROM (VALUES (1,'Phạm Gia Bảo'),(2,'Nguyễn Bảo Ngọc'),(3,'Trần Đức Anh'),(4,'Lê Khánh Vy'),
 (5,'Vũ Minh Khang'),(6,'Đỗ Thảo Nhi'),(7,'Bùi Quốc Hưng'),(8,'Hoàng Ngọc Diệp')) v(i,full_name);
INSERT INTO account(user_id,email,password_hash,full_name,role,status,created_at,updated_at)
SELECT t.user_id,'tutor.seed'||lpad(t.i::text,2,'0')||'@edufit.vn',a.password_hash,t.full_name,'TUTOR','ACTIVE',
       now()-interval '90 days',now()-interval '1 day'
FROM et t CROSS JOIN account a WHERE a.email='student.demo@edufit.vn'
UNION ALL
SELECT s.user_id,'student.seed'||lpad(s.i::text,2,'0')||'@edufit.vn',a.password_hash,s.full_name,'STUDENT','ACTIVE',
       now()-interval '65 days',now()-interval '1 day'
FROM es s CROSS JOIN account a WHERE a.email='student.demo@edufit.vn';
INSERT INTO student_profile(student_id,user_id,education_level_id,profile_complete,province_id,district_id,created_at)
SELECT student_id,user_id,4,true,1,26,now()-interval '65 days' FROM es;
INSERT INTO tutor_profile(tutor_id,user_id,display_name,headline,bio,teaching_mode,experience_years,teaching_method,
                          status,verified_at,tutor_type,tutor_type_verified,province_id,created_at)
SELECT t.tutor_id,t.user_id,t.full_name,
       'Gia sư '||s.name||' THCS · '||t.years||' năm kinh nghiệm',
       'Hồ sơ minh họa cho demo EduFit. Hỗ trợ học sinh lớp 8–9 môn '||s.name||
       ', xây dựng lộ trình theo tiêu chí và ghi phản hồi cụ thể sau từng buổi.',
       'ONLINE',t.years,'Kiểm tra đầu vào, hướng dẫn từng bước, luyện tập và phản hồi theo tiêu chí.',
       CASE WHEN t.i<=8 THEN 'VERIFIED' ELSE 'PENDING_VERIFICATION' END,
       CASE WHEN t.i<=8 THEN now()-interval '80 days' END,t.tutor_type,t.i<=8,1,now()-interval '90 days'
FROM et t JOIN subject s USING(subject_id);
INSERT INTO tutor_offering(offering_id,tutor_id,subject_id,track_id,price_per_session)
SELECT offering_id,tutor_id,subject_id,track_id,price FROM et;
INSERT INTO tutor_offering_level(offering_id,level_id)
SELECT offering_id,l FROM et CROSS JOIN generate_series(3,4) l;
INSERT INTO tutor_offering_goal(offering_id,goal_tag_id)
SELECT offering_id,2 FROM et;
INSERT INTO tutor_offering_textbook(offering_id,series_id)
SELECT offering_id,CASE WHEN subject_id=3 THEN 4 ELSE 1 END FROM et;
INSERT INTO tutor_availability_slot(slot_id,tutor_id,day_of_week,start_time,end_time)
SELECT pg_temp.eid('availability:'||t.i||':'||d),t.tutor_id,d,
       make_time(t.start_hour,0,0),make_time(t.start_hour+2,0,0)
FROM et t CROSS JOIN generate_series(1,7) d;
INSERT INTO tutor_availability_slot(slot_id,tutor_id,day_of_week,start_time,end_time)
SELECT pg_temp.eid('availability-extra:'||t.i||':'||d),t.tutor_id,d,'15:00','16:30'
FROM et t CROSS JOIN generate_series(1,7) d WHERE t.i<=8;

-- Every verified background tutor has an approval history; 09/10 are still pending
-- and deliberately have no accepted request/class before the live Admin decision.
INSERT INTO verification_request(request_id,tutor_id,status,submitted_at,reviewed_by,reviewed_at)
SELECT pg_temp.eid('kyc:'||i),tutor_id,CASE WHEN i<=8 THEN 'APPROVED' ELSE 'PENDING' END,
       CASE WHEN i<=8 THEN now()-interval '82 days' ELSE now()-interval '2 hours' END,
       CASE WHEN i<=8 THEN '44444444-4444-4444-4444-444444444444'::uuid END,
       CASE WHEN i<=8 THEN now()-interval '80 days' END FROM et;
INSERT INTO credential(credential_id,request_id,type,institution,year,note)
SELECT pg_temp.eid('credential:'||i),pg_temp.eid('kyc:'||i),
       CASE WHEN tutor_type='UNIVERSITY_STUDENT' THEN 'STUDENT_CARD' ELSE 'DEGREE' END,
       'EDUFIT DEMO — đơn vị minh họa, không phải văn bằng thật',2024,
       'Tài liệu dùng riêng cho buổi bảo vệ. Không xác nhận danh tính hoặc trình độ người thật.' FROM et;
-- A file-generation companion writes these valid PDF samples under local credential storage.
INSERT INTO credential_file(file_id,credential_id,file_name,storage_key,content_type,size_bytes,uploaded_at)
SELECT pg_temp.eid('credential-file:'||i),pg_temp.eid('credential:'||i),
       'EDUFIT-DEMO-KYC-'||lpad(i::text,2,'0')||'.pdf',
       'enrichment-20261110/kyc-'||lpad(i::text,2,'0')||'.pdf','application/pdf',:kyc_pdf_bytes,now()-interval '2 hours'
FROM et;

CREATE TEMP TABLE ec ON COMMIT DROP AS
SELECT c.i,c.student_no,t.i tutor_no,s.student_id,s.user_id student_user,t.tutor_id,t.user_id tutor_user,
       t.subject_id,t.track_id,t.price,sub.name subject_name,
       pg_temp.eid('class:'||c.i) class_id,pg_temp.eid('need:'||c.i) need_id,
       pg_temp.eid('request-accepted:'||c.i) request_id,
       ((c.i-1)%7+1)::int dow,CASE WHEN c.i<=7 THEN 17 ELSE 19 END start_hour
FROM (VALUES (1,1,1),(2,2,2),(3,3,3),(4,4,4),(5,5,5),(6,6,6),(7,7,7),(8,8,8),(9,1,5),(10,2,3)) c(i,student_no,tutor_no)
JOIN es s ON s.i=c.student_no JOIN et t ON t.i=c.tutor_no JOIN subject sub ON sub.subject_id=t.subject_id;
-- Two tutors teach a second class at 19:00, so expose that availability too.
INSERT INTO tutor_availability_slot(slot_id,tutor_id,day_of_week,start_time,end_time)
SELECT pg_temp.eid('availability-second:'||i),tutor_id,dow,'19:00','21:00' FROM ec WHERE i IN (9,10);
INSERT INTO learning_need(need_id,student_id,created_by,education_level_id,subject_id,track_id,goal_tag_id,
                         textbook_series_id,mode,province_id,district_id,budget_max_per_session,status,created_at)
SELECT need_id,student_id,student_user,4,subject_id,track_id,2,CASE WHEN subject_id=3 THEN 4 ELSE 1 END,'ONLINE',1,26,price+50000,'ACTIVE',
       now()-interval '63 days' FROM ec;
INSERT INTO learning_need_slot(slot_id,need_id,day_of_week,start_time,end_time)
SELECT pg_temp.eid('need-slot:'||i),need_id,dow,make_time(start_hour,0,0),make_time(start_hour+1,30,0) FROM ec;
INSERT INTO connection_request(request_id,student_id,tutor_id,need_id,sent_by_user_id,desired_slots,message,status,
                               created_at,expires_at,responded_at,version)
SELECT request_id,student_id,tutor_id,need_id,student_user,
       jsonb_build_array((ARRAY['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'])[dow]||
          ' '||lpad(start_hour::text,2,'0')||':00-'||lpad((start_hour+1)::text,2,'0')||':30'),
       'Em muốn học '||subject_name||' theo lộ trình và nhận phản hồi sau mỗi buổi. [Dữ liệu minh họa]',
       'ACCEPTED',now()-interval '62 days',now()-interval '59 days',now()-interval '61 days',1 FROM ec;
INSERT INTO class(class_id,connection_request_id,student_id,tutor_id,need_id,status,started_at,price_per_session)
SELECT class_id,request_id,student_id,tutor_id,need_id,'ACTIVE',now()-interval '60 days',price FROM ec;

CREATE TEMP TABLE er ON COMMIT DROP AS
SELECT c.*,CASE c.tutor_no WHEN 1 THEN 2 WHEN 2 THEN 1 WHEN 3 THEN 4 WHEN 4 THEN 3
       WHEN 5 THEN 6 WHEN 6 THEN 5 WHEN 7 THEN 8 ELSE 7 END target_no
FROM ec c WHERE c.i<=8;
INSERT INTO connection_request(request_id,student_id,tutor_id,need_id,sent_by_user_id,desired_slots,message,status,
                               reject_reason,created_at,expires_at,responded_at)
SELECT pg_temp.eid('request-extra:'||r.i||':'||k),r.student_id,t.tutor_id,r.need_id,r.student_user,
       jsonb_build_array((ARRAY['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'])[(r.dow%7)+1]||
           ' 15:00-16:30'),
       CASE k WHEN 1 THEN 'Em muốn tham khảo lịch học phù hợp hơn, mong thầy/cô phản hồi.'
         WHEN 2 THEN 'Em xin học thêm vào khung giờ đã đề xuất.' ELSE 'Yêu cầu trước đây đã hết thời hạn phản hồi.' END,
       CASE k WHEN 1 THEN 'PENDING' WHEN 2 THEN 'REJECTED' ELSE 'EXPIRED' END,
       CASE WHEN k=2 THEN 'Thầy/cô chưa thể nhận thêm học sinh trong khung giờ này.' END,
       CASE WHEN k=1 THEN now()-interval '1 hour' ELSE now()-interval '20 days' END,
       CASE WHEN k=1 THEN now()+interval '71 hours' ELSE now()-interval '17 days' END,
       CASE WHEN k=2 THEN now()-interval '19 days' WHEN k=3 THEN now()-interval '17 days' END
FROM er r JOIN et t ON t.i=r.target_no CROSS JOIN generate_series(1,3) k
WHERE k=1 OR r.i<=4;

-- Stable, non-overlapping weekly history and three future weeks per background class.
CREATE TEMP TABLE exs ON COMMIT DROP AS
SELECT c.*,n,pg_temp.eid('session:'||c.i||':'||n) session_id,
       ((date_trunc('week',now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date+c.dow-1+
          CASE WHEN n<=6 THEN -7*(7-n) WHEN n=7 THEN -50 ELSE 7*(n-7) END)
          +make_time(c.start_hour,0,0)) AT TIME ZONE 'Asia/Ho_Chi_Minh' start_at,
       CASE WHEN n<=6 THEN 'COMPLETED' WHEN n=7 THEN 'CANCELLED' WHEN n<=9 THEN 'SCHEDULED' ELSE 'PROPOSED' END status
FROM ec c CROSS JOIN generate_series(1,10) n;
INSERT INTO tutoring_session(session_id,class_id,tutor_id,student_id,start_at,end_at,mode,place_or_link,message,status,
                             proposed_by,responded_by,responded_at,expires_at,cancelled_by,cancelled_at,cancel_reason,
                             cancel_comment,outcome_recorded_by,outcome_recorded_at,created_at)
SELECT session_id,class_id,tutor_id,student_id,start_at,start_at+interval '90 minutes','ONLINE',
       'https://example.com/edufit-demo-room','[Minh họa] '||subject_name||' — buổi '||n,status,tutor_user,
       CASE WHEN status<>'PROPOSED' THEN student_user END,
       CASE WHEN status<>'PROPOSED' THEN LEAST(start_at-interval '2 days',now()-interval '1 day') END,
       CASE WHEN status='PROPOSED' THEN now()+interval '71 hours' ELSE LEAST(start_at-interval '1 day',now()) END,
       CASE WHEN status='CANCELLED' THEN student_user END,CASE WHEN status='CANCELLED' THEN start_at-interval '2 days' END,
       CASE WHEN status='CANCELLED' THEN 'PERSONAL_REASON' END,
       CASE WHEN status='CANCELLED' THEN 'Gia đình có việc, hai bên đã thống nhất hủy sớm.' END,
       CASE WHEN status='COMPLETED' THEN tutor_user END,CASE WHEN status='COMPLETED' THEN start_at+interval '95 minutes' END,
       LEAST(start_at-interval '3 days',now()-interval '2 days') FROM exs;
INSERT INTO session_reschedule_proposal(proposal_id,session_id,proposed_by,new_start_at,new_end_at,reason,status,
                                       expires_at,responded_by,responded_at,response_reason,created_at)
SELECT pg_temp.eid('reschedule:'||i),session_id,student_user,start_at+interval '1 day',start_at+interval '1 day 90 minutes',
       'Em có lịch kiểm tra ở trường, xin chuyển buổi học sang ngày hôm sau.',
       CASE WHEN i%2=1 THEN 'PENDING' ELSE 'REJECTED' END,
       now()+interval '47 hours',CASE WHEN i%2=0 THEN tutor_user END,
       CASE WHEN i%2=0 THEN now()-interval '20 minutes' END,
       CASE WHEN i%2=0 THEN 'Thầy/cô không rảnh ngày đề xuất; giữ nguyên lịch cũ.' END,
       now()-interval '1 hour' FROM exs WHERE n=9;

-- Main accounts: append data only inside their two existing active classes.
CREATE TEMP TABLE emain ON COMMIT DROP AS
SELECT row_number() OVER(ORDER BY c.class_id)::int i,c.class_id,c.student_id,c.tutor_id,
       sp.user_id student_user,tp.user_id tutor_user,c.price_per_session price,s.name subject_name,lp.plan_id,
       (SELECT COALESCE(max(sort_order),-1)+1 FROM milestone WHERE plan_id=lp.plan_id) sort_base
FROM class c JOIN student_profile sp USING(student_id) JOIN tutor_profile tp USING(tutor_id)
JOIN learning_need ln ON ln.need_id=c.need_id JOIN subject s ON s.subject_id=ln.subject_id
JOIN learning_plan lp USING(class_id)
WHERE c.class_id IN ('77777777-7777-7777-7777-000000000001','77777777-7777-7777-7777-000000000002');
CREATE FUNCTION pg_temp.free_start(t uuid,s uuid,at_time timestamptz) RETURNS timestamptz LANGUAGE plpgsql AS $$
DECLARE candidate timestamptz:=at_time; attempts int:=0;
BEGIN
  WHILE EXISTS(SELECT 1 FROM tutoring_session WHERE (tutor_id=t OR student_id=s)
    AND status IN ('SCHEDULED','COMPLETED','ABSENT','PROPOSED')
    AND start_at<candidate+interval '90 minutes' AND end_at>candidate) LOOP
    candidate:=candidate-interval '2 hours'; attempts:=attempts+1;
    IF attempts>100 THEN RAISE EXCEPTION 'No safe demonstration session slot found'; END IF;
  END LOOP;
  RETURN candidate;
END $$;
CREATE TEMP TABLE ems ON COMMIT DROP AS
SELECT m.*,n,pg_temp.eid('main-session:'||i||':'||n) session_id,
       CASE WHEN n<=6 THEN 'COMPLETED' WHEN n=7 THEN 'SCHEDULED' ELSE 'PROPOSED' END status,
       NULL::timestamptz start_at FROM emain m CROSS JOIN generate_series(1,8) n;
DO $$ DECLARE r record; at_time timestamptz; BEGIN
  FOR r IN SELECT * FROM ems ORDER BY i,n LOOP
    at_time:=CASE WHEN r.n<=6 THEN
        ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date-(28-3*r.n)+make_time(10+2*r.i,0,0)) AT TIME ZONE 'Asia/Ho_Chi_Minh'
      ELSE ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date+(r.n-6)*7+make_time(10+2*r.i,0,0)) AT TIME ZONE 'Asia/Ho_Chi_Minh' END;
    IF r.i=1 AND r.n=5 THEN at_time:=now()-interval '8 hours'; END IF;
    IF r.i=1 AND r.n=6 THEN at_time:=now()-interval '4 hours'; END IF;
    IF r.i=2 AND r.n=6 THEN at_time:=now()-interval '73 hours'; END IF;
    at_time:=pg_temp.free_start(r.tutor_id,r.student_id,at_time);
    UPDATE ems SET start_at=at_time WHERE session_id=r.session_id;
    INSERT INTO tutoring_session(session_id,class_id,tutor_id,student_id,start_at,end_at,mode,place_or_link,message,status,
      proposed_by,responded_by,responded_at,expires_at,outcome_recorded_by,outcome_recorded_at,created_at)
    VALUES(r.session_id,r.class_id,r.tutor_id,r.student_id,at_time,at_time+interval '90 minutes','ONLINE',
      'https://example.com/edufit-demo-room','[Bổ sung demo] '||r.subject_name||' — buổi '||r.n,r.status,r.tutor_user,
      CASE WHEN r.status<>'PROPOSED' THEN r.student_user END,CASE WHEN r.status<>'PROPOSED' THEN LEAST(at_time-interval '2 days',now()-interval '1 day') END,
      CASE WHEN r.status='PROPOSED' THEN now()+interval '71 hours' ELSE at_time-interval '1 day' END,
      CASE WHEN r.status='COMPLETED' THEN r.tutor_user END,
      CASE WHEN r.status='COMPLETED' THEN at_time+interval '95 minutes' END,LEAST(at_time-interval '3 days',now()-interval '2 days'));
  END LOOP;
END $$;

-- Four milestones x three criteria = 12 criteria per new class; append the same
-- amount to each original plan without rewriting its existing progress/history.
INSERT INTO learning_plan(plan_id,class_id,summary,created_at,updated_at)
SELECT pg_temp.eid('plan:'||i),class_id,'Lộ trình '||subject_name||' lớp 9: nền tảng, vận dụng, bài tập tổng hợp và tự đánh giá.',
       now()-interval '59 days',now()-interval '2 days' FROM ec;
CREATE TEMP TABLE ep ON COMMIT DROP AS
SELECT 'new:'||i key,i,class_id,student_id,tutor_id,student_user,tutor_user,subject_name,pg_temp.eid('plan:'||i) plan_id,0 sort_base FROM ec
UNION ALL SELECT 'main:'||i,i,class_id,student_id,tutor_id,student_user,tutor_user,subject_name,plan_id,sort_base FROM emain;
CREATE TEMP TABLE etopic ON COMMIT DROP AS SELECT * FROM (VALUES
 ('Toán',1,'Căn thức và điều kiện xác định','Xác định điều kiện có nghĩa của biểu thức','Rút gọn biểu thức có căn','Kiểm tra và giải thích kết quả'),
 ('Toán',2,'Hệ phương trình và bài toán thực tế','Lập hệ từ dữ kiện đề bài','Giải hệ bằng hai phương pháp','Đối chiếu nghiệm với điều kiện thực tế'),
 ('Toán',3,'Hình học đường tròn','Nhận diện góc và cung tương ứng','Trình bày chứng minh có căn cứ','Vận dụng quan hệ tiếp tuyến'),
 ('Toán',4,'Luyện đề và quản lý thời gian','Phân bổ thời gian làm từng phần','Hoàn thành bài trong thời gian quy định','Tự phân loại và sửa lỗi sai'),
 ('Tiếng Anh',1,'Ngữ pháp trong ngữ cảnh','Phân biệt thì theo dấu hiệu ngữ cảnh','Chia động từ đúng trong đoạn văn','Giải thích lựa chọn cấu trúc'),
 ('Tiếng Anh',2,'Đọc hiểu theo chủ đề','Xác định ý chính của đoạn văn','Tìm chi tiết để chứng minh đáp án','Suy luận nghĩa của từ theo ngữ cảnh'),
 ('Tiếng Anh',3,'Viết đoạn văn có cấu trúc','Viết câu chủ đề rõ ý','Liên kết các ý bằng từ nối phù hợp','Tự sửa lỗi ngữ pháp và chính tả'),
 ('Tiếng Anh',4,'Ôn tập và bài kiểm tra tổng hợp','Hoàn thành bài nghe hiểu ngắn','Viết đoạn văn trong thời gian giới hạn','Tự nhận diện điểm cần cải thiện'),
 ('Ngữ Văn',1,'Đọc hiểu văn bản','Xác định nội dung và thông điệp','Nhận diện biện pháp nghệ thuật','Dẫn chứng đúng để giải thích nhận định'),
 ('Ngữ Văn',2,'Lập luận và dẫn chứng','Xây dựng luận điểm rõ ràng','Chọn dẫn chứng phù hợp','Giải thích mối liên hệ dẫn chứng và luận điểm'),
 ('Ngữ Văn',3,'Viết đoạn nghị luận','Viết mở đoạn đúng trọng tâm','Triển khai ý mạch lạc','Chỉnh sửa diễn đạt và lỗi chính tả'),
 ('Ngữ Văn',4,'Luyện đề và tự biên tập','Lập dàn ý trước khi viết','Hoàn thiện bài trong thời gian giới hạn','Tự đánh giá bài theo tiêu chí'),
 ('Khoa học tự nhiên',1,'Kiến thức và đại lượng nền tảng','Giải thích ý nghĩa của đại lượng','Đổi đơn vị chính xác','Phân biệt điều kiện áp dụng công thức'),
 ('Khoa học tự nhiên',2,'Phân tích và giải bài tập','Tóm tắt dữ kiện bằng ký hiệu','Lựa chọn công thức hoặc phương trình phù hợp','Kiểm tra đơn vị và tính hợp lý của kết quả'),
 ('Khoa học tự nhiên',3,'Thực nghiệm và giải thích hiện tượng','Mô tả trình tự thực nghiệm','Đọc và giải thích bảng số liệu','Liên hệ hiện tượng với kiến thức đã học'),
 ('Khoa học tự nhiên',4,'Bài tập tổng hợp','Kết hợp hai nội dung trong một bài','Trình bày lời giải có lập luận','Tự sửa sai dựa trên bằng chứng')
) v(subject_name,n,title,c1,c2,c3);
CREATE TEMP TABLE emil ON COMMIT DROP AS
SELECT p.*,t.n,t.title,t.c1,t.c2,t.c3,pg_temp.eid('milestone:'||p.key||':'||t.n) milestone_id
FROM ep p JOIN etopic t USING(subject_name);
CREATE TEMP TABLE efb ON COMMIT DROP AS
SELECT 'new:'||i key,n,session_id,class_id,student_id,tutor_id,student_user,tutor_user,start_at,subject_name FROM exs WHERE n<=6
UNION ALL SELECT 'main:'||i,n,session_id,class_id,student_id,tutor_id,student_user,tutor_user,start_at,subject_name FROM ems WHERE n<=6;
INSERT INTO milestone(milestone_id,plan_id,title,description,target_date,weight,status,achieved_date,sort_order,
                      suggested_sessions,created_at)
SELECT milestone_id,plan_id,title,'Mục tiêu bổ sung để quan sát tiến độ học tập theo tiêu chí.',
       (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date+(n-2)*7,1,
       CASE n WHEN 1 THEN 'ACHIEVED' WHEN 2 THEN 'IN_PROGRESS' ELSE 'NOT_STARTED' END,
       CASE WHEN n=1 THEN (SELECT (f.start_at+interval '100 minutes')::date FROM efb f WHERE f.key=emil.key AND f.n=3) END,
       sort_base+n-1,3,now()-interval '59 days' FROM emil;
INSERT INTO milestone_criterion(criterion_id,milestone_id,content,sort_order,is_checked,checked_at,created_at)
SELECT pg_temp.eid('criterion:'||m.key||':'||m.n||':'||k),milestone_id,(ARRAY[c1,c2,c3])[k],k-1,
       n=1 OR n=2 AND k<=i%3,
       CASE WHEN n=1 THEN (SELECT f.start_at+interval '100 minutes' FROM efb f WHERE f.key=m.key AND f.n=3)
            WHEN n=2 AND k<=i%3 THEN (SELECT f.start_at+interval '100 minutes' FROM efb f WHERE f.key=m.key AND f.n=6) END,
       now()-interval '59 days' FROM emil m CROSS JOIN generate_series(1,3) k;
INSERT INTO session_feedback(feedback_id,session_id,class_id,homework_status,attitude_chips,created_by,created_at,parent_message,submitted_late)
SELECT pg_temp.eid('feedback:'||session_id),session_id,class_id,
       CASE WHEN n%3=0 THEN 'NONE' ELSE 'ASSIGNED' END,
       CASE WHEN n<=2 THEN 'FOCUSED' WHEN n<=4 THEN 'FOCUSED,ASKS_QUESTIONS' ELSE 'HARD_WORKING' END,
       tutor_user,start_at+interval '100 minutes',
       CASE n WHEN 1 THEN 'Cần hỗ trợ thêm khi bắt đầu dạng bài mới; đã có bài tập ngắn để luyện.'
        WHEN 2 THEN 'Đã giải thích được các bước cơ bản, cần chú ý kiểm tra điều kiện.'
        WHEN 3 THEN 'Hoàn thành được bài nền tảng độc lập, đã đạt các tiêu chí của cột mốc đầu.'
        WHEN 4 THEN 'Đã thử bài vận dụng; cần cải thiện cách chọn dữ kiện quan trọng.'
        WHEN 5 THEN 'Chủ động đặt câu hỏi, trình bày rõ hơn và biết tự sửa lỗi.'
        ELSE 'Đã có tiến bộ ở bài vận dụng; buổi sau tiếp tục củng cố tiêu chí chưa đạt.' END,false FROM efb;
INSERT INTO session_note(note_id,session_id,tutor_id,note_text,created_at)
SELECT pg_temp.eid('note:'||f.session_id),f.session_id,f.tutor_id,
       'Nội dung: '||m.title||'. '||
       CASE f.n WHEN 1 THEN 'Khảo sát cách làm, sửa lỗi nền tảng và giao 5 bài tự luyện.'
        WHEN 2 THEN 'Chữa bài đã giao; yêu cầu học sinh giải thích lựa chọn của mình.'
        WHEN 3 THEN 'Kiểm tra lại ba tiêu chí nền tảng và tổng kết lỗi thường gặp.'
        WHEN 4 THEN 'Hướng dẫn bài vận dụng, tách dữ kiện và lập kế hoạch giải.'
        WHEN 5 THEN 'Học sinh tự làm 8/10 câu; đã biết kiểm tra lại kết quả.'
        ELSE 'Tổng hợp phản hồi, giao bài luyện phần còn yếu cho buổi tiếp theo.' END,
       f.start_at+interval '100 minutes'
FROM efb f JOIN emil m ON m.key=f.key AND m.n=CASE WHEN f.n<=3 THEN 1 ELSE 2 END;
INSERT INTO session_milestone_link(link_id,session_id,milestone_id,link_type,created_by,created_at)
SELECT pg_temp.eid('actual:'||f.session_id),f.session_id,m.milestone_id,'ACTUAL',f.tutor_user,f.start_at+interval '100 minutes'
FROM efb f JOIN emil m ON m.key=f.key AND m.n=CASE WHEN f.n<=3 THEN 1 ELSE 2 END;
INSERT INTO milestone_assessment(assessment_id,milestone_id,session_id,level,correct_count,total_count,assessed_by,created_at,evidence_note)
SELECT pg_temp.eid('assessment:'||f.session_id),m.milestone_id,f.session_id,
       (ARRAY[1,2,3,2,3,3])[f.n],(ARRAY[4,6,9,6,8,9])[f.n],10,f.tutor_user,f.start_at+interval '100 minutes',
       'Đánh giá từ bài tự làm và phần giải thích trong buổi học, không chỉ theo số buổi đã học.'
FROM efb f JOIN emil m ON m.key=f.key AND m.n=CASE WHEN f.n<=3 THEN 1 ELSE 2 END;
INSERT INTO plan_history(history_id,plan_id,milestone_id,action,actor_user_id,new_value,created_at)
SELECT pg_temp.eid('plan-history:'||milestone_id),plan_id,milestone_id,'MILESTONE_CREATED',tutor_user,title,now()-interval '59 days' FROM emil;

-- Three new disputes for the main demo only, backed by real demo-wallet reserves.
-- These are internal fixtures; no payOS transaction, topup or bank transfer is fabricated.
CREATE TEMP TABLE edis ON COMMIT DROP AS
SELECT s.*,CASE WHEN i=1 AND n=5 THEN 'AWAITING_ADMIN' WHEN i=1 THEN 'DISPUTED' ELSE 'ADMIN_REVIEW' END charge_status,
       '33333333-3333-3333-3333-333333333333'::uuid payer_user
FROM ems s WHERE i=1 AND n IN (5,6) OR i=2 AND n=6;
DO $$ DECLARE needed bigint; BEGIN
  SELECT sum(price) INTO needed FROM edis;
  IF (SELECT available FROM payment_wallet WHERE user_id='33333333-3333-3333-3333-333333333333')<needed THEN
    RAISE EXCEPTION 'Insufficient existing demo wallet funds for dispute fixtures; no artificial credit is allowed';
  END IF;
END $$;
INSERT INTO payment_charge(session_id,class_id,student_id,tutor_user_id,payer_user_id,amount,commission_bps,end_at,status,
                           review_started_at,dispute_reason,dispute_opened_at,dispute_category,dispute_evidence,
                           dispute_reporter_id,learner_report,tutor_report,learner_amount,tutor_amount,updated_at,
                           learning_started_at,learning_started_by)
SELECT session_id,class_id,student_id,tutor_user,payer_user,price,1000,start_at+interval '90 minutes',charge_status,
       start_at+interval '95 minutes',
       CASE charge_status WHEN 'DISPUTED' THEN 'Kết nối mạng gián đoạn, gia đình và gia sư đang trao đổi phương án học bù.'
        WHEN 'AWAITING_ADMIN' THEN 'Buổi học kết thúc sớm; hai bên thống nhất tính một nửa học phí.'
        ELSE 'Chưa thống nhất phần nội dung đã hoàn thành; đề nghị Admin xem xét bằng chứng.' END,
       start_at+interval '2 hours',
       CASE charge_status WHEN 'DISPUTED' THEN 'TECHNICAL' WHEN 'AWAITING_ADMIN' THEN 'DURATION' ELSE 'QUALITY' END,
       '[DỮ LIỆU MINH HỌA] Nội dung trao đổi được mô phỏng; không có chứng từ ngân hàng thật.',payer_user,
       CASE WHEN charge_status<>'DISPUTED' THEN 'Gia đình đề nghị tính một nửa học phí cho nội dung đã học.' END,
       CASE WHEN charge_status='AWAITING_ADMIN' THEN 'Gia sư đồng ý mức một nửa học phí; nhờ Admin xác nhận.'
        WHEN charge_status='ADMIN_REVIEW' THEN 'Gia sư đề nghị tính đủ học phí vì đã giao đủ nội dung.' END,
       CASE WHEN charge_status<>'DISPUTED' THEN price/2 END,
       CASE WHEN charge_status='AWAITING_ADMIN' THEN price/2 WHEN charge_status='ADMIN_REVIEW' THEN price END,
       now(),start_at,tutor_user FROM edis;
INSERT INTO payment_entry(id,user_id,event_key,kind,available_delta,held_delta,reference_id,created_at)
SELECT pg_temp.eid('reserve-entry:'||session_id),payer_user,'reserve:'||session_id,'SESSION_RESERVED',-price,price,
       session_id,start_at-interval '1 day' FROM edis;
UPDATE payment_wallet SET available=available-x.amount,held=held+x.amount
FROM (SELECT payer_user,sum(price) amount FROM edis GROUP BY payer_user) x WHERE user_id=x.payer_user;
INSERT INTO payment_audit(id,actor_id,reference_id,action,detail,created_at)
SELECT pg_temp.eid('fund-audit:'||session_id),payer_user,session_id,'SESSION_FUNDED',
       '[DEMO ENRICHMENT] Existing wallet reserve; no bank transaction',start_at-interval '1 day' FROM edis
UNION ALL SELECT pg_temp.eid('dispute-audit:'||session_id),payer_user,session_id,'DISPUTE_OPENED',
       '[DEMO ENRICHMENT] '||charge_status,start_at+interval '2 hours' FROM edis;
INSERT INTO payment_case_message(id,session_id,author_id,side,message,created_at)
SELECT pg_temp.eid('case-message:'||session_id||':'||side),session_id,
       CASE WHEN side='PAYER' THEN payer_user ELSE tutor_user END,side,
       CASE WHEN side='PAYER' THEN 'Gia đình đã đọc phản hồi buổi học và mong thống nhất mức học phí phù hợp.'
        ELSE 'Gia sư đã ghi nhận nội dung trao đổi và sẽ phối hợp cung cấp thông tin cho Admin.' END,
       start_at+interval '3 hours' FROM edis CROSS JOIN (VALUES ('PAYER'),('TUTOR')) v(side);

-- Abort atomically if the new dataset violates its own invariants.
\ir verify-local-defense.sql
\endif
COMMIT;
