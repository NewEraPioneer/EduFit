-- =========================================================================
-- EduFit Database Migration V1.31: Progress dashboard redesign — Giai đoạn 3
--   * Danh mục chủ đề chuẩn theo môn × khối do admin quản lý (trục của biểu đồ radar)
--   * Cột mốc: số buổi gợi ý (dự báo "còn X buổi cho Y cột mốc") và chủ đề chuẩn (tùy chọn)
--   * Báo cáo kỳ mỗi 30 ngày kể từ ngày bắt đầu lớp: bản nháp chờ gia sư gửi, tự gửi sau 48h
-- =========================================================================

-- 1. DANH MỤC CHỦ ĐỀ CHUẨN: lưu trữ (archived) thay vì xóa vì cột mốc cũ vẫn tham chiếu
CREATE TABLE IF NOT EXISTS topic_catalog (
    topic_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id          INT NOT NULL,
    education_level_id  INT NOT NULL,
    name                VARCHAR(120) NOT NULL,
    sort_order          INT NOT NULL DEFAULT 0,
    is_archived         BOOLEAN NOT NULL DEFAULT false,
    created_by          UUID NOT NULL,
    version             INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_topic_catalog_subject FOREIGN KEY (subject_id) REFERENCES subject(subject_id),
    CONSTRAINT fk_topic_catalog_level FOREIGN KEY (education_level_id) REFERENCES education_level(level_id),
    CONSTRAINT fk_topic_catalog_created_by FOREIGN KEY (created_by) REFERENCES account(user_id),
    CONSTRAINT chk_topic_catalog_name CHECK (length(btrim(name)) BETWEEN 1 AND 120)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_topic_catalog_name
    ON topic_catalog (subject_id, education_level_id, lower(btrim(name)));

-- 2. CỘT MỐC: số buổi gợi ý (2–6, cột mốc cũ để trống) và chủ đề chuẩn
ALTER TABLE milestone
    ADD COLUMN IF NOT EXISTS suggested_sessions SMALLINT,
    ADD COLUMN IF NOT EXISTS topic_id UUID;

ALTER TABLE milestone
    ADD CONSTRAINT chk_milestone_suggested_sessions
        CHECK (suggested_sessions IS NULL OR suggested_sessions BETWEEN 2 AND 6),
    ADD CONSTRAINT fk_milestone_topic FOREIGN KEY (topic_id) REFERENCES topic_catalog(topic_id);

CREATE INDEX IF NOT EXISTS idx_milestone_topic ON milestone (topic_id);

-- 3. BÁO CÁO KỲ: ảnh chụp 3 thẻ tại cuối kỳ, mỗi lớp một báo cáo cho mỗi kỳ 30 ngày
CREATE TABLE IF NOT EXISTS period_report (
    report_id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id                UUID NOT NULL,
    period_index            INT NOT NULL,
    period_start            DATE NOT NULL,
    period_end              DATE NOT NULL,
    on_track_status         VARCHAR(30) NOT NULL,
    on_track_explanation    VARCHAR(500) NOT NULL,
    progress_percentage     SMALLINT,
    progress_delta          SMALLINT,
    attended_count          INT NOT NULL,
    counted_count           INT NOT NULL,
    highlight               VARCHAR(500),
    next_focus_title        VARCHAR(200),
    tutor_summary           VARCHAR(300),
    status                  VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    auto_send_at            TIMESTAMPTZ NOT NULL,
    sent_at                 TIMESTAMPTZ,
    version                 INT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_period_report_class_period UNIQUE (class_id, period_index),
    CONSTRAINT fk_period_report_class FOREIGN KEY (class_id) REFERENCES class(class_id) ON DELETE CASCADE,
    CONSTRAINT chk_period_report_index CHECK (period_index >= 1),
    CONSTRAINT chk_period_report_window CHECK (period_end > period_start),
    CONSTRAINT chk_period_report_status CHECK (status IN ('DRAFT', 'SENT')),
    CONSTRAINT chk_period_report_sent CHECK ((status = 'SENT') = (sent_at IS NOT NULL)),
    CONSTRAINT chk_period_report_summary
        CHECK (tutor_summary IS NULL OR length(btrim(tutor_summary)) BETWEEN 1 AND 300)
);

CREATE INDEX IF NOT EXISTS idx_period_report_due ON period_report (status, auto_send_at);

-- Cột mốc đạt trong kỳ (giữ tên tại thời điểm chụp)
CREATE TABLE IF NOT EXISTS period_report_achievement (
    report_id           UUID NOT NULL,
    sort_order          INT NOT NULL,
    milestone_title     VARCHAR(200) NOT NULL,

    CONSTRAINT pk_period_report_achievement PRIMARY KEY (report_id, sort_order),
    CONSTRAINT fk_period_report_achievement_report FOREIGN KEY (report_id)
        REFERENCES period_report(report_id) ON DELETE CASCADE
);
