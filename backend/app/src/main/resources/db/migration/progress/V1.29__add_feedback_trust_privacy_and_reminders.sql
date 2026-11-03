-- =========================================================================
-- EduFit Database Migration V1.29: Progress dashboard redesign — Giai đoạn 2
--   * Lời nhắn riêng cho phụ huynh và cờ "bổ sung muộn" trên phản hồi buổi học
--   * Câu bằng chứng cho điểm chấm (chống chấm nhảy mức)
--   * Cảm nhận 1 chạm của học sinh sau buổi học (session_self_rating)
--   * Nhật ký nhắc phản hồi +12h/+24h, chống gửi trùng (feedback_reminder)
-- =========================================================================

-- 1. PHẢN HỒI BUỔI HỌC: lời nhắn riêng cho phụ huynh + bổ sung muộn (sau 48h kể từ khi buổi học kết thúc)
ALTER TABLE session_feedback
    ADD COLUMN IF NOT EXISTS parent_message VARCHAR(300),
    ADD COLUMN IF NOT EXISTS submitted_late BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE session_feedback
    ADD CONSTRAINT chk_session_feedback_parent_message
        CHECK (parent_message IS NULL OR length(btrim(parent_message)) BETWEEN 1 AND 300);

-- 2. ĐIỂM CHẤM: câu bằng chứng khi tăng từ 2 mức trở lên trong một buổi
ALTER TABLE milestone_assessment
    ADD COLUMN IF NOT EXISTS evidence_note VARCHAR(200);

ALTER TABLE milestone_assessment
    ADD CONSTRAINT chk_milestone_assessment_evidence
        CHECK (evidence_note IS NULL OR length(btrim(evidence_note)) BETWEEN 1 AND 200);

-- 3. CẢM NHẬN 1 CHẠM CỦA HỌC SINH (1 = còn khó, 2 = tạm ổn, 3 = hiểu bài), mỗi buổi một dòng, chọn lại thì ghi đè
CREATE TABLE IF NOT EXISTS session_self_rating (
    rating_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID NOT NULL,
    class_id        UUID NOT NULL,
    student_id      UUID NOT NULL,
    rating          SMALLINT NOT NULL,
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_session_self_rating_session UNIQUE (session_id),
    CONSTRAINT fk_session_self_rating_session FOREIGN KEY (session_id) REFERENCES tutoring_session(session_id) ON DELETE CASCADE,
    CONSTRAINT fk_session_self_rating_class FOREIGN KEY (class_id) REFERENCES class(class_id) ON DELETE CASCADE,
    CONSTRAINT fk_session_self_rating_student FOREIGN KEY (student_id) REFERENCES student_profile(student_id) ON DELETE CASCADE,
    CONSTRAINT fk_session_self_rating_created_by FOREIGN KEY (created_by) REFERENCES account(user_id),
    CONSTRAINT chk_session_self_rating_value CHECK (rating BETWEEN 1 AND 3)
);

CREATE INDEX IF NOT EXISTS idx_session_self_rating_class ON session_self_rating (class_id, created_at);

-- 4. NHẮC PHẢN HỒI: mỗi buổi học nhận tối đa một lần nhắc cho mỗi mốc (+12h, +24h)
CREATE TABLE IF NOT EXISTS feedback_reminder (
    reminder_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID NOT NULL,
    stage           VARCHAR(10) NOT NULL,
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_feedback_reminder UNIQUE (session_id, stage),
    CONSTRAINT fk_feedback_reminder_session FOREIGN KEY (session_id) REFERENCES tutoring_session(session_id) ON DELETE CASCADE,
    CONSTRAINT chk_feedback_reminder_stage CHECK (stage IN ('H12', 'H24'))
);
