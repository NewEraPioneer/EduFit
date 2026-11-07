-- Each structured feedback must assess at least one taught milestone.
-- The live math class created by accepting the pending request remains the create-plan demo.
CREATE OR REPLACE FUNCTION pg_temp.def_id(k text) RETURNS uuid AS $$
 SELECT md5('edufit-defense:'||k)::uuid
$$ LANGUAGE sql IMMUTABLE;
INSERT INTO learning_plan(plan_id,class_id,summary,created_at)
VALUES(pg_temp.def_id('plan:english'),pg_temp.def_id('class:english'),
 'DEMO: củng cố ngữ pháp và phát triển kỹ năng viết đoạn văn',now()-interval '60 days');
INSERT INTO milestone(milestone_id,plan_id,title,description,target_date,weight,status,sort_order,created_at,suggested_sessions)
VALUES(pg_temp.def_id('milestone:english'),pg_temp.def_id('plan:english'),'Sử dụng thì quá khứ trong đoạn văn',
 'Chia đúng động từ và tự sửa lỗi trong đoạn 80 từ',(now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date+14,1,'IN_PROGRESS',0,now()-interval '59 days',5);
INSERT INTO milestone_criterion(criterion_id,milestone_id,content,sort_order,is_checked,created_at)
VALUES(pg_temp.def_id('criterion:english'),pg_temp.def_id('milestone:english'),'Viết đúng ít nhất 8/10 câu và tự phát hiện lỗi sai',0,false,now()-interval '59 days');
INSERT INTO milestone_assessment(assessment_id,milestone_id,session_id,level,correct_count,total_count,assessed_by,created_at,evidence_note)
SELECT pg_temp.def_id('assessment:english-'||n),pg_temp.def_id('milestone:english'),s.session_id,
 CASE WHEN n=1 THEN 1 ELSE 2 END,4+n,10,a.user_id,s.end_at+interval '30 minutes',
 'Cần gợi ý khi chia động từ; chưa tự kiểm tra đầy đủ'
FROM generate_series(1,3) n JOIN tutoring_session s ON s.session_id=pg_temp.def_id('session:english-'||n)
JOIN tutor_profile t USING(tutor_id) JOIN account a USING(user_id);
INSERT INTO session_milestone_link(link_id,session_id,milestone_id,link_type,created_by,created_at)
SELECT pg_temp.def_id('link:english-'||n),s.session_id,pg_temp.def_id('milestone:english'),'ACTUAL',t.user_id,s.end_at+interval '30 minutes'
FROM generate_series(1,3) n JOIN tutoring_session s ON s.session_id=pg_temp.def_id('session:english-'||n)
JOIN tutor_profile t USING(tutor_id);
UPDATE session_note SET note_text='DEMO: chia động từ đúng 7/10 câu; cần luyện thì quá khứ và tự kiểm tra lỗi.'
WHERE session_id IN (SELECT pg_temp.def_id('session:english-'||n) FROM generate_series(1,3) n);
UPDATE session_feedback SET parent_message='Con cần luyện thêm thì quá khứ; gia đình nhắc con hoàn thành đoạn văn 80 từ.'
WHERE session_id IN (SELECT pg_temp.def_id('session:english-'||n) FROM generate_series(1,3) n);
