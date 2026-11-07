-- =========================================================================
-- EduFit Database Migration V1.28: Progress dashboard redesign — Giai đoạn 1
--   * Tiêu chí đạt của cột mốc (milestone_criterion)
--   * Phản hồi 10 giây sau buổi học (session_feedback)
--   * Liên kết buổi học <-> cột mốc (session_milestone_link, ACTUAL ở giai đoạn 1)
--   * Điểm chấm mức độ tự lập, ghi nối tiếp (milestone_assessment)
-- Không backfill dữ liệu giả cho các cột mốc cũ.
-- =========================================================================

-- 1. TIÊU CHÍ ĐẠT CỦA CỘT MỐC
CREATE TABLE IF NOT EXISTS milestone_criterion (
    criterion_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    milestone_id    UUID NOT NULL,
    content         VARCHAR(200) NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    is_checked      BOOLEAN NOT NULL DEFAULT false,
    checked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_milestone_criterion_milestone FOREIGN KEY (milestone_id) REFERENCES milestone(milestone_id) ON DELETE CASCADE,
    CONSTRAINT chk_milestone_criterion_content CHECK (length(btrim(content)) BETWEEN 1 AND 200),
    CONSTRAINT chk_milestone_criterion_checked CHECK ((is_checked AND checked_at IS NOT NULL) OR (NOT is_checked AND checked_at IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_milestone_criterion_milestone ON milestone_criterion (milestone_id, sort_order);

-- 2. PHẢN HỒI 10 GIÂY SAU BUỔI HỌC (một phản hồi cho mỗi buổi)
CREATE TABLE IF NOT EXISTS session_feedback (
    feedback_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID NOT NULL,
    class_id        UUID NOT NULL,
    homework_status VARCHAR(30) NOT NULL,
    attitude_chips  VARCHAR(300),
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_session_feedback_session UNIQUE (session_id),
    CONSTRAINT fk_session_feedback_session FOREIGN KEY (session_id) REFERENCES tutoring_session(session_id) ON DELETE CASCADE,
    CONSTRAINT fk_session_feedback_class FOREIGN KEY (class_id) REFERENCES class(class_id) ON DELETE CASCADE,
    CONSTRAINT fk_session_feedback_created_by FOREIGN KEY (created_by) REFERENCES account(user_id),
    CONSTRAINT chk_session_feedback_homework CHECK (homework_status IN ('ASSIGNED', 'NONE', 'PREVIOUSLY_ASSIGNED'))
);

CREATE INDEX IF NOT EXISTS idx_session_feedback_class ON session_feedback (class_id, created_at);

-- 3. LIÊN KẾT BUỔI HỌC <-> CỘT MỐC (PLANNED dành cho giai đoạn 2)
CREATE TABLE IF NOT EXISTS session_milestone_link (
    link_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID NOT NULL,
    milestone_id    UUID NOT NULL,
    link_type       VARCHAR(20) NOT NULL,
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_session_milestone_link UNIQUE (session_id, milestone_id, link_type),
    CONSTRAINT fk_session_milestone_link_session FOREIGN KEY (session_id) REFERENCES tutoring_session(session_id) ON DELETE CASCADE,
    CONSTRAINT fk_session_milestone_link_milestone FOREIGN KEY (milestone_id) REFERENCES milestone(milestone_id) ON DELETE CASCADE,
    CONSTRAINT fk_session_milestone_link_created_by FOREIGN KEY (created_by) REFERENCES account(user_id),
    CONSTRAINT chk_session_milestone_link_type CHECK (link_type IN ('PLANNED', 'ACTUAL'))
);

CREATE INDEX IF NOT EXISTS idx_session_milestone_link_milestone ON session_milestone_link (milestone_id, link_type);

-- 4. ĐIỂM CHẤM MỨC ĐỘ TỰ LẬP (ghi nối tiếp, không sửa/xóa dòng cũ)
CREATE TABLE IF NOT EXISTS milestone_assessment (
    assessment_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    milestone_id    UUID NOT NULL,
    session_id      UUID NOT NULL,
    level           SMALLINT NOT NULL,
    correct_count   SMALLINT,
    total_count     SMALLINT,
    assessed_by     UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_milestone_assessment_milestone FOREIGN KEY (milestone_id) REFERENCES milestone(milestone_id) ON DELETE CASCADE,
    CONSTRAINT fk_milestone_assessment_session FOREIGN KEY (session_id) REFERENCES tutoring_session(session_id) ON DELETE CASCADE,
    CONSTRAINT fk_milestone_assessment_assessed_by FOREIGN KEY (assessed_by) REFERENCES account(user_id),
    CONSTRAINT chk_milestone_assessment_level CHECK (level BETWEEN 1 AND 4),
    CONSTRAINT chk_milestone_assessment_score CHECK (
        (correct_count IS NULL AND total_count IS NULL)
        OR (total_count > 0 AND correct_count >= 0 AND correct_count <= total_count)
    )
);

CREATE INDEX IF NOT EXISTS idx_milestone_assessment_milestone ON milestone_assessment (milestone_id, created_at);
CREATE INDEX IF NOT EXISTS idx_milestone_assessment_session ON milestone_assessment (session_id);
