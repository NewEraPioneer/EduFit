-- Additive defense workshop. Dev/demo only; all seven passwords: Password123@.
-- V1.34 stays immutable. Stable IDs use a separate namespace; no existing user is deleted.
CREATE OR REPLACE FUNCTION pg_temp.def_id(k text) RETURNS uuid AS $$
  SELECT md5('edufit-defense:' || k)::uuid
$$ LANGUAGE sql IMMUTABLE;
CREATE OR REPLACE FUNCTION pg_temp.def_time(d date, t time) RETURNS timestamptz AS $$
  SELECT (d + t) AT TIME ZONE 'Asia/Ho_Chi_Minh'
$$ LANGUAGE sql IMMUTABLE;

CREATE TEMP TABLE defense_people ON COMMIT DROP AS
SELECT k, pg_temp.def_id('user:'||k) AS uid, pg_temp.def_id('profile:'||k) AS pid,
       email||'@demo.edufit.test' AS email, name, role
FROM (VALUES
 ('student-live','student.live','Nguyễn Gia Bảo','STUDENT'),
 ('student-empty','student.empty','Đặng Minh Châu','STUDENT'),
 ('student-conflict','student.conflict','Lê Hoàng Nam','STUDENT'),
 ('parent-live','parent.live','Nguyễn Thu Hà','PARENT'),
 ('parent-empty','parent.empty','Đặng Thanh Hương','PARENT'),
 ('tutor-live','tutor.live','Trần Quang Minh','TUTOR'),
 ('tutor-new','tutor.new','Phạm Ngọc Anh','TUTOR')
) p(k,email,name,role);
INSERT INTO account(user_id,email,password_hash,full_name,role,status,created_at,updated_at)
SELECT p.uid,p.email,a.password_hash,p.name,p.role,'ACTIVE',now()-interval '70 days',now()
FROM defense_people p CROSS JOIN account a WHERE a.email='student.demo@edufit.vn';
INSERT INTO student_profile(student_id,user_id,education_level_id,profile_complete,province_id,district_id,created_at)
SELECT pid,uid,CASE WHEN k='student-empty' THEN NULL ELSE 4 END,k<>'student-empty',
       CASE WHEN k='student-empty' THEN NULL ELSE 1 END,CASE WHEN k='student-empty' THEN NULL ELSE 26 END,now()-interval '69 days'
FROM defense_people WHERE role='STUDENT';
INSERT INTO tutor_profile(tutor_id,user_id,display_name,headline,bio,teaching_mode,experience_years,
 teaching_method,status,verified_at,tutor_type,tutor_type_verified,province_id,created_at)
SELECT pid,uid,name,CASE WHEN k='tutor-live' THEN 'Gia sư Toán THCS · Lộ trình rõ ràng, phản hồi từng buổi' END,
 CASE WHEN k='tutor-live' THEN 'Tôi đồng hành cùng học sinh lớp 8–9, củng cố kiến thức và rèn cách tự kiểm tra bài làm.' END,
 'ONLINE',CASE WHEN k='tutor-live' THEN 4 END,
 CASE WHEN k='tutor-live' THEN 'Đánh giá đầu vào, luyện tập theo mức độ và phản hồi có dẫn chứng.' END,
 CASE WHEN k='tutor-live' THEN 'VERIFIED' ELSE 'DRAFT' END,
 CASE WHEN k='tutor-live' THEN now()-interval '65 days' END,
 CASE WHEN k='tutor-live' THEN 'GRADUATE' END,k='tutor-live',1,now()-interval '69 days'
FROM defense_people WHERE role='TUTOR';
INSERT INTO tutor_offering(offering_id,tutor_id,subject_id,track_id,price_per_session)
VALUES(pg_temp.def_id('offering:math'),pg_temp.def_id('profile:tutor-live'),1,NULL,250000);
INSERT INTO tutor_offering_level(offering_id,level_id)
SELECT pg_temp.def_id('offering:math'),v FROM generate_series(3,4) v;
INSERT INTO tutor_offering_goal(offering_id,goal_tag_id,evidence_verified)
SELECT pg_temp.def_id('offering:math'),v,false FROM unnest(ARRAY[1,2,4]) v;
INSERT INTO tutor_offering_textbook(offering_id,series_id)
SELECT pg_temp.def_id('offering:math'),series_id FROM textbook_series;
INSERT INTO tutor_offering_exam_province(offering_id,province_id) VALUES(pg_temp.def_id('offering:math'),1);
INSERT INTO tutor_availability_slot(slot_id,tutor_id,day_of_week,start_time,end_time)
SELECT pg_temp.def_id('availability:'||d),pg_temp.def_id('profile:tutor-live'),d,'18:00','21:00'
FROM generate_series(1,7) d;
-- Give Linh a distinct Sunday window; existing Saturday sessions remain untouched.
INSERT INTO tutor_availability_slot(slot_id,tutor_id,day_of_week,start_time,end_time)
VALUES(pg_temp.def_id('availability:linh'), '22222222-2222-2222-2222-000000000007',7,'10:00','12:00');
INSERT INTO verification_request(request_id,tutor_id,status,submitted_at,reviewed_by,reviewed_at)
VALUES(pg_temp.def_id('verification:live'),pg_temp.def_id('profile:tutor-live'),'APPROVED',
 now()-interval '66 days','44444444-4444-4444-4444-444444444444',now()-interval '65 days');
INSERT INTO credential(credential_id,request_id,type,institution,year,note)
VALUES(pg_temp.def_id('credential:live'),pg_temp.def_id('verification:live'),'DEGREE',
 'Trường đại học mẫu — DEMO',2022,'Tài liệu minh họa, không phải văn bằng thật');

-- Six needs, including an incomplete need and a deliberately restrictive search.
CREATE TEMP TABLE defense_needs ON COMMIT DROP AS
SELECT * FROM (VALUES
 ('math','student-live',1,NULL::int,4,NULL::int,'ACTIVE',250000::bigint),
 ('english','student-live',3,2,2,4,'ACTIVE',200000),
 ('old-math','student-live',1,NULL,1,1,'ARCHIVED',250000),
 ('busy-math','student-conflict',1,NULL,1,1,'ACTIVE',250000),
 ('incomplete','student-conflict',3,2,2,4,'INCOMPLETE',NULL),
 ('restrictive','student-live',2,NULL,2,1,'ACTIVE',10000)
) n(k,person,subject,track,goal,book,status,budget);
INSERT INTO learning_need(need_id,student_id,created_by,education_level_id,subject_id,track_id,goal_tag_id,
 textbook_series_id,exam_province_id,mode,province_id,district_id,budget_max_per_session,status,created_at)
SELECT pg_temp.def_id('need:'||k),pg_temp.def_id('profile:'||person),pg_temp.def_id('user:'||person),4,subject,track,goal,
 book,CASE WHEN goal=4 THEN 1 END,CASE WHEN k='restrictive' THEN 'OFFLINE' ELSE 'ONLINE' END,
 CASE WHEN k='restrictive' THEN 4 ELSE 1 END,CASE WHEN k='restrictive' THEN NULL ELSE 26 END,
 budget,status,now()-interval '65 days' FROM defense_needs;
INSERT INTO learning_need_slot(slot_id,need_id,day_of_week,start_time,end_time)
SELECT pg_temp.def_id('need-slot:'||k),pg_temp.def_id('need:'||k),
 CASE k WHEN 'english' THEN 7 WHEN 'busy-math' THEN 2 WHEN 'restrictive' THEN 1 ELSE 1 END,
 CASE k WHEN 'english' THEN '10:00'::time ELSE '18:00'::time END,
 CASE k WHEN 'english' THEN '11:30'::time ELSE '19:30'::time END
FROM defense_needs WHERE status<>'INCOMPLETE';

INSERT INTO parent_student_link(link_id,parent_user_id,student_id,status,confirmed_at,version)
SELECT pg_temp.def_id('link:'||k),pg_temp.def_id('user:parent-live'),pid,'CONFIRMED',now()-interval '65 days',0
FROM defense_people WHERE k IN ('student-live','student-conflict');
INSERT INTO parent_student_link(link_id,parent_user_id,student_id,status,confirmed_at,revoked_at,revoked_by,revoke_reason,version)
VALUES(pg_temp.def_id('link:revoked'),pg_temp.def_id('user:parent-empty'),pg_temp.def_id('profile:student-conflict'),
 'REVOKED',now()-interval '30 days',now()-interval '20 days',pg_temp.def_id('user:parent-empty'),'DEMO: đổi người theo dõi',1);
INSERT INTO link_invitation(invitation_id,inviter_user_id,invited_user_id,status,created_at,expires_at,responded_at,version)
SELECT pg_temp.def_id('invitation:'||k),pg_temp.def_id('user:parent-live'),pg_temp.def_id('user:student-empty'),
 k,now()-make_interval(days=>age),CASE WHEN k='PENDING' THEN now()+interval '6 days' ELSE now()-make_interval(days=>age-7) END,
 CASE WHEN k='PENDING' THEN NULL ELSE now()-make_interval(days=>age-1) END,CASE WHEN k='PENDING' THEN 0 ELSE 1 END
FROM (VALUES('PENDING',1),('DECLINED',45),('WITHDRAWN',35),('EXPIRED',25)) v(k,age);

CREATE TEMP TABLE defense_classes ON COMMIT DROP AS
SELECT k,pg_temp.def_id('class:'||k) AS cid,pg_temp.def_id('request:'||k) AS rid,
 pg_temp.def_id('profile:'||person) AS sid,pg_temp.def_id('user:'||person) AS suid,
 CASE WHEN k='english' THEN '22222222-2222-2222-2222-000000000007'::uuid ELSE pg_temp.def_id('profile:tutor-live') END AS tid,
 CASE WHEN k='english' THEN '22222222-2222-2222-2222-222222222228'::uuid ELSE pg_temp.def_id('user:tutor-live') END AS tuid,
 pg_temp.def_id('need:'||need) AS nid,price,status,slots::jsonb
FROM (VALUES
 ('english','student-live','english',200000::bigint,'ACTIVE','["SUNDAY 10:00-11:30"]'),
 ('busy','student-conflict','busy-math',250000,'ACTIVE','["TUESDAY 18:00-19:30"]'),
 ('ended','student-live','old-math',250000,'ENDED','["MONDAY 18:00-19:30"]')
) c(k,person,need,price,status,slots);
INSERT INTO connection_request(request_id,student_id,tutor_id,need_id,sent_by_user_id,desired_slots,message,status,
 created_at,expires_at,responded_at,version)
SELECT rid,sid,tid,nid,suid,slots,'DEMO: lớp chuẩn bị cho buổi bảo vệ','ACCEPTED',now()-interval '64 days',
 now()-interval '57 days',now()-interval '63 days',1 FROM defense_classes;
INSERT INTO class(class_id,connection_request_id,student_id,tutor_id,need_id,status,started_at,ended_at,end_reason,price_per_session)
SELECT cid,rid,sid,tid,nid,status,now()-interval '62 days',CASE WHEN status='ENDED' THEN now()-interval '25 days' END,
 CASE WHEN status='ENDED' THEN 'DEMO: hoàn tất chuyên đề nền tảng' END,price FROM defense_classes;
-- A separate pending request: math live has NO active class with tutor.live.
INSERT INTO connection_request(request_id,student_id,tutor_id,need_id,sent_by_user_id,desired_slots,message,status,created_at,expires_at)
VALUES(pg_temp.def_id('request:accept-ready'),pg_temp.def_id('profile:student-live'),pg_temp.def_id('profile:tutor-live'),
 pg_temp.def_id('need:math'),pg_temp.def_id('user:parent-live'),'["MONDAY 18:00-19:30"]','DEMO A: yêu cầu có sẵn; hủy trước nếu muốn gửi mới từ cùng nhu cầu','PENDING',now()-interval '1 hour',now()+interval '6 days');
-- Dedicated quota case; never blocks student.live's main workshop.
INSERT INTO connection_request(request_id,student_id,tutor_id,need_id,sent_by_user_id,desired_slots,message,status,created_at,expires_at)
SELECT pg_temp.def_id('request:quota:'||n),pg_temp.def_id('profile:student-conflict'),
 ('22222222-2222-2222-2222-00000000000'||n)::uuid,pg_temp.def_id('need:busy-math'),pg_temp.def_id('user:student-conflict'),
 '["SATURDAY 16:00-17:30"]','DEMO QUOTA: năm yêu cầu còn chờ, dùng riêng cho kiểm tra giới hạn','PENDING',
 now()-interval '1 hour',now()+interval '6 days' FROM generate_series(1,5) n;

CREATE TEMP TABLE defense_sessions ON COMMIT DROP AS
SELECT key, c.*, pg_temp.def_id('session:'||key) AS sessid,
 CASE WHEN age IS NOT NULL THEN now()-make_interval(days=>age,hours=>hours) ELSE
 pg_temp.def_time((date_trunc('week',now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date+7+day_offset),tm) END AS start_at,
 state,pay
FROM (VALUES
 ('ended-1','ended',55,3,NULL::int,NULL::time,'COMPLETED','SETTLED'),
 ('ended-2','ended',48,3,NULL,NULL,'COMPLETED','SETTLED'),
 ('ended-3','ended',41,3,NULL,NULL,'COMPLETED','SETTLED'),
 ('ended-4','ended',34,3,NULL,NULL,'COMPLETED','SETTLED'),
 ('english-1','english',21,6,NULL,NULL,'COMPLETED','SETTLED'),
 ('english-2','english',14,6,NULL,NULL,'COMPLETED','SETTLED'),
 ('english-3','english',7,6,NULL,NULL,'COMPLETED','SETTLED'),
 ('english-next','english',NULL,NULL,6,'10:00','SCHEDULED',NULL),
 ('english-proposed','english',NULL,NULL,13,'10:00','PROPOSED',NULL),
 ('busy-review','busy',0,4,NULL,NULL,'COMPLETED','AWAITING_CONFIRMATION'),
 ('busy-disputed','busy',0,8,NULL,NULL,'COMPLETED','DISPUTED'),
 ('busy-next','busy',NULL,NULL,1,'18:00','SCHEDULED','FUNDED'),
 ('busy-next2','busy',NULL,NULL,8,'18:00','SCHEDULED',NULL),
 ('busy-cancelled','busy',4,3,NULL,NULL,'CANCELLED',NULL),
 ('busy-absent','busy',10,3,NULL,NULL,'ABSENT',NULL)
) s(key,cls,age,hours,day_offset,tm,state,pay) JOIN defense_classes c ON c.k=s.cls;
INSERT INTO tutoring_session(session_id,class_id,tutor_id,student_id,start_at,end_at,mode,place_or_link,message,status,
 proposed_by,responded_by,responded_at,expires_at,outcome_recorded_by,outcome_recorded_at,
 cancelled_by,cancelled_at,cancel_reason,cancel_comment,created_at)
SELECT sessid,cid,tid,sid,start_at,start_at+interval '90 minutes','ONLINE',
 'https://meet.google.com/demo-only','DEMO '||key,state,tuid,CASE WHEN state<>'PROPOSED' THEN suid END,
 CASE WHEN state<>'PROPOSED' THEN LEAST(start_at-interval '2 days',now()-interval '1 hour') END,
 CASE WHEN state='PROPOSED' THEN now()+interval '2 days' ELSE start_at-interval '2 hours' END,
 CASE WHEN state IN ('COMPLETED','ABSENT') THEN tuid END,
 CASE WHEN state IN ('COMPLETED','ABSENT') THEN start_at+interval '100 minutes' END,
 CASE WHEN state='CANCELLED' THEN suid END,CASE WHEN state='CANCELLED' THEN start_at-interval '1 day' END,
 CASE WHEN state='CANCELLED' THEN 'PERSONAL_REASON' END,CASE WHEN state='CANCELLED' THEN 'DEMO: gia đình có việc riêng' END,
 LEAST(start_at-interval '3 days',now()-interval '2 hours') FROM defense_sessions;
INSERT INTO session_history(history_id,session_id,action,actor_user_id,reason,created_at)
SELECT pg_temp.def_id('session-history:'||key),sessid,
 CASE WHEN state IN ('COMPLETED','ABSENT') THEN 'OUTCOME_'||state WHEN state='SCHEDULED' THEN 'ACCEPTED' ELSE state END,
 CASE WHEN state IN ('SCHEDULED','CANCELLED') THEN suid ELSE tuid END,
 'DEMO: dữ liệu lịch sử chuẩn bị trước buổi trình bày',
 CASE WHEN state='CANCELLED' THEN start_at-interval '1 day' ELSE LEAST(start_at+interval '100 minutes',now()-interval '1 hour') END
FROM defense_sessions;

-- All seed topups explicitly marked MANUAL/DEMO. No payOS transaction is claimed.
INSERT INTO payment_topup(id,order_code,user_id,amount,provider,status,transaction_reference,expires_at,created_at)
SELECT pg_temp.def_id('topup:'||person),9100000000+n,pg_temp.def_id('user:'||person),amount,'MANUAL','PAID',
 'DEFENSE-DEMO-ONLY-'||n,now()-interval '65 days'+interval '15 minutes',now()-interval '65 days'
FROM (VALUES('student-live',2000000::bigint,1),('parent-live',1000000,2),('student-conflict',1500000,3)) t(person,amount,n);
INSERT INTO payment_entry(user_id,event_key,kind,available_delta,held_delta,reference_id,created_at)
SELECT user_id,'topup:'||id,'TOPUP',amount,0,id,created_at FROM payment_topup WHERE transaction_reference LIKE 'DEFENSE-DEMO-ONLY-%';
INSERT INTO payment_charge(session_id,class_id,student_id,tutor_user_id,payer_user_id,amount,commission_bps,end_at,status,
 learner_accepted,tutor_accepted,auto_accepted,settled_amount,review_started_at,dispute_opened_at,dispute_reason,
 dispute_category,dispute_reporter_id,learner_report,tutor_report,learner_amount,tutor_amount,updated_at)
SELECT sessid,cid,sid,tuid,suid,price,1000,start_at+interval '90 minutes',CASE WHEN pay='DISPUTED' THEN 'AWAITING_ADMIN' ELSE pay END,
 pay='SETTLED',pay IN ('SETTLED','AWAITING_CONFIRMATION'),false,CASE WHEN pay='SETTLED' THEN price ELSE 0 END,
 CASE WHEN state='COMPLETED' THEN start_at+interval '100 minutes' END,
 CASE WHEN pay='DISPUTED' THEN start_at+interval '2 hours' END,
 CASE WHEN pay='DISPUTED' THEN 'DEMO: mất kết nối, hai bên thống nhất chỉ tính nửa buổi' END,'OTHER',
 CASE WHEN pay='DISPUTED' THEN suid END,
 CASE WHEN pay='DISPUTED' THEN 'Gia đình đồng ý tất toán 125.000đ cho phần đã học.' END,
 CASE WHEN pay='DISPUTED' THEN 'Gia sư đồng ý tất toán 125.000đ, hoàn phần còn lại.' END,
 CASE WHEN pay='DISPUTED' THEN 125000 END,CASE WHEN pay='DISPUTED' THEN 125000 END,
 CASE WHEN pay='SETTLED' THEN start_at+interval '26 hours' ELSE now()-interval '30 minutes' END
FROM defense_sessions WHERE pay IS NOT NULL;
INSERT INTO payment_entry(user_id,event_key,kind,available_delta,held_delta,reference_id,created_at)
SELECT suid,'reserve:'||sessid,'SESSION_RESERVED',-price,price,sessid,LEAST(start_at-interval '2 days',now()-interval '2 hours')
FROM defense_sessions WHERE pay IS NOT NULL;
INSERT INTO payment_entry(user_id,event_key,kind,available_delta,held_delta,reference_id,created_at)
SELECT suid,'settle-payer:'||sessid,'SESSION_SETTLED',0,-price,sessid,start_at+interval '26 hours' FROM defense_sessions WHERE pay='SETTLED';
INSERT INTO payment_entry(user_id,event_key,kind,available_delta,held_delta,reference_id,created_at)
SELECT tuid,'settle-tutor:'||sessid,'TUITION_INCOME',price*9/10,0,sessid,start_at+interval '26 hours' FROM defense_sessions WHERE pay='SETTLED';
INSERT INTO payment_entry(user_id,event_key,kind,available_delta,held_delta,reference_id,created_at)
SELECT NULL,'commission:'||sessid,'COMMISSION',price/10,0,sessid,start_at+interval '26 hours' FROM defense_sessions WHERE pay='SETTLED';
INSERT INTO payment_wallet(user_id,available,held)
SELECT e.user_id,sum(e.available_delta),sum(e.held_delta) FROM payment_entry e
WHERE e.user_id IN (SELECT uid FROM defense_people) GROUP BY e.user_id;
-- Linh already has a wallet in V1.34: add only this workshop's new income.
UPDATE payment_wallet w SET available=w.available+x.income FROM (
 SELECT tuid,sum(price*9/10) income FROM defense_sessions WHERE k='english' AND pay='SETTLED' GROUP BY tuid
) x WHERE w.user_id=x.tuid;
INSERT INTO payment_audit(id,actor_id,reference_id,action,detail,created_at)
SELECT pg_temp.def_id('payment-audit:'||key),suid,sessid,'DEMO_SEEDED','DEMO ONLY; status='||pay||'; VND='||price,now()
FROM defense_sessions WHERE pay IS NOT NULL;
INSERT INTO payment_case_message(id,session_id,author_id,side,message,created_at)
SELECT pg_temp.def_id('case:'||side),sessid,CASE WHEN side='PAYER' THEN suid ELSE tuid END,side,
 'DEMO: hai bên thống nhất 125.000đ; đã chuyển Admin xác nhận.',now()-interval '30 minutes'
FROM defense_sessions CROSS JOIN (VALUES('PAYER'),('TUTOR')) m(side) WHERE pay='DISPUTED';
INSERT INTO payment_payout_account(user_id,id,bank_name,bank_bin,bank_account,bank_holder,updated_at)
VALUES(pg_temp.def_id('user:tutor-live'),pg_temp.def_id('payout:live'),'DEMO BANK — KHÔNG CHUYỂN TIỀN','970436','0000000000','TRAN QUANG MINH',now());

-- A deliberately slow roadmap; English live class stays without a plan for creation demo.
INSERT INTO learning_plan(plan_id,class_id,summary,created_at)
VALUES(pg_temp.def_id('plan:slow'),pg_temp.def_id('class:busy'),'DEMO: củng cố nền tảng, cần thêm thời gian tự luyện',now()-interval '50 days');
INSERT INTO milestone(milestone_id,plan_id,title,description,target_date,weight,status,sort_order,created_at)
SELECT pg_temp.def_id('milestone:'||n),pg_temp.def_id('plan:slow'),title,'Tự giải và kiểm tra kết quả từng bước',
 (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date-3,1,CASE WHEN n=1 THEN 'IN_PROGRESS' ELSE 'NOT_STARTED' END,n,now()-interval '49 days'
FROM (VALUES(1,'Giải phương trình bậc nhất'),(2,'Vận dụng hệ thức lượng')) m(n,title);
INSERT INTO milestone_criterion(criterion_id,milestone_id,content,sort_order,is_checked,created_at)
SELECT pg_temp.def_id('criterion:'||n),pg_temp.def_id('milestone:'||n),'Giải đúng ít nhất 8/10 bài không cần gợi ý',0,false,now()-interval '49 days'
FROM generate_series(1,2) n;
INSERT INTO session_feedback(feedback_id,session_id,class_id,homework_status,attitude_chips,created_by,created_at,parent_message)
SELECT pg_temp.def_id('feedback:'||key),sessid,cid,'ASSIGNED','FOCUSED',tuid,start_at+interval '2 hours',
 'Con cần tự luyện thêm phép đổi dấu; gia đình hỗ trợ kiểm tra 5 bài tập ngắn.'
FROM defense_sessions WHERE key IN ('english-1','english-2','english-3','busy-disputed');
INSERT INTO session_note(note_id,session_id,tutor_id,note_text,created_at)
SELECT pg_temp.def_id('note:'||key),sessid,tid,'DEMO: giải đúng 7/10 bài; còn nhầm khi chuyển vế và đổi dấu.',start_at+interval '2 hours'
FROM defense_sessions WHERE key IN ('english-1','english-2','english-3','busy-disputed');
INSERT INTO milestone_assessment(assessment_id,milestone_id,session_id,level,correct_count,total_count,assessed_by,created_at,evidence_note)
SELECT pg_temp.def_id('assessment:slow'),pg_temp.def_id('milestone:1'),sessid,2,5,10,tuid,start_at+interval '2 hours',
 'Cần gợi ý khi đổi dấu, chưa đạt tiêu chí 8/10' FROM defense_sessions WHERE key='busy-disputed';
INSERT INTO session_milestone_link(link_id,session_id,milestone_id,link_type,created_by,created_at)
SELECT pg_temp.def_id('milestone-link:slow'),sessid,pg_temp.def_id('milestone:1'),'ACTUAL',tuid,start_at+interval '2 hours'
FROM defense_sessions WHERE key='busy-disputed';
INSERT INTO session_self_rating(rating_id,session_id,class_id,student_id,rating,created_by,created_at,updated_at)
SELECT pg_temp.def_id('self-rating:slow'),sessid,cid,sid,1,suid,start_at+interval '3 hours',start_at+interval '3 hours'
FROM defense_sessions WHERE key='busy-disputed';

INSERT INTO notification(notification_id,user_id,about_student_id,type,title,short_text,target_type,target_id,is_read,created_at)
VALUES(pg_temp.def_id('notification:request'),pg_temp.def_id('user:tutor-live'),pg_temp.def_id('profile:student-live'),
 'CONNECTION_REQUEST','DEMO A: Nguyễn Gia Bảo muốn học Toán','Yêu cầu còn hạn, lịch Thứ 2 18:00 không trùng lịch lớp hiện tại.',NULL,NULL,false,now()),
 (pg_temp.def_id('notification:parent'),pg_temp.def_id('user:parent-live'),pg_temp.def_id('profile:student-conflict'),
 'PAYMENT_SESSION','DEMO: Hoàng Nam có buổi cần phản hồi','Mở Ví & Học phí để xác nhận hoặc báo vấn đề.',NULL,NULL,false,now());
INSERT INTO audit_log(audit_id,actor_user_id,action,target_type,target_id,reason,created_at)
VALUES(pg_temp.def_id('audit:verified'),'44444444-4444-4444-4444-444444444444','APPROVE_VERIFICATION','VERIFICATION_REQUEST',
 pg_temp.def_id('verification:live'),'DEMO: phê duyệt hồ sơ minh họa Trần Quang Minh',now()-interval '65 days');

-- Real, watermarked PDF asset shipped in scripts/demo/assets (install with install-assets.ps1).
INSERT INTO credential_file(file_id,credential_id,file_name,storage_key,content_type,size_bytes)
SELECT pg_temp.def_id('credential-file:'||credential_id),credential_id,'defense-demo.pdf','defense-demo.pdf','application/pdf',806
FROM credential WHERE credential_id=pg_temp.def_id('credential:live') OR request_id IN (
 SELECT request_id FROM verification_request WHERE tutor_id IN (
 '22222222-2222-2222-2222-000000000011','22222222-2222-2222-2222-000000000013'));
