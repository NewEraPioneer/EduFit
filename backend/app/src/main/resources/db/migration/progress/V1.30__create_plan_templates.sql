-- =========================================================================
-- EduFit Database Migration V1.30: Mẫu lộ trình theo môn × khối × mục tiêu (Giai đoạn 2, mục 3.4)
--   * owner_tutor_id NULL  = mẫu hệ thống do admin quản lý
--   * owner_tutor_id != NULL = mẫu riêng của gia sư
--   * Mỗi mẫu 3–8 cột mốc, mỗi cột mốc 2–6 buổi gợi ý và 1–4 tiêu chí đạt. Mẫu không có ngày:
--     gia sư đặt hạn khi áp dụng vào lớp.
-- =========================================================================

CREATE TABLE IF NOT EXISTS plan_template (
    template_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_tutor_id      UUID,
    subject_id          INT NOT NULL,
    education_level_id  INT NOT NULL,
    goal_tag_id         INT,
    title               VARCHAR(150) NOT NULL,
    description         VARCHAR(1000),
    created_by          UUID NOT NULL,
    version             INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_plan_template_owner FOREIGN KEY (owner_tutor_id) REFERENCES tutor_profile(tutor_id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_template_subject FOREIGN KEY (subject_id) REFERENCES subject(subject_id),
    CONSTRAINT fk_plan_template_level FOREIGN KEY (education_level_id) REFERENCES education_level(level_id),
    CONSTRAINT fk_plan_template_goal FOREIGN KEY (goal_tag_id) REFERENCES goal_tag(goal_tag_id),
    CONSTRAINT fk_plan_template_created_by FOREIGN KEY (created_by) REFERENCES account(user_id),
    CONSTRAINT chk_plan_template_title CHECK (length(btrim(title)) BETWEEN 1 AND 150)
);

CREATE INDEX IF NOT EXISTS idx_plan_template_catalog ON plan_template (subject_id, education_level_id, goal_tag_id);
CREATE INDEX IF NOT EXISTS idx_plan_template_owner ON plan_template (owner_tutor_id);

CREATE TABLE IF NOT EXISTS plan_template_milestone (
    template_milestone_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id           UUID NOT NULL,
    title                 VARCHAR(200) NOT NULL,
    description           VARCHAR(1000),
    weight                SMALLINT NOT NULL DEFAULT 1,
    suggested_sessions    SMALLINT NOT NULL,
    sort_order            INT NOT NULL,

    CONSTRAINT fk_plan_template_milestone_template FOREIGN KEY (template_id) REFERENCES plan_template(template_id) ON DELETE CASCADE,
    CONSTRAINT chk_plan_template_milestone_title CHECK (length(btrim(title)) BETWEEN 1 AND 200),
    CONSTRAINT chk_plan_template_milestone_weight CHECK (weight BETWEEN 1 AND 5),
    CONSTRAINT chk_plan_template_milestone_sessions CHECK (suggested_sessions BETWEEN 2 AND 6)
);

CREATE INDEX IF NOT EXISTS idx_plan_template_milestone_template ON plan_template_milestone (template_id, sort_order);

CREATE TABLE IF NOT EXISTS plan_template_criterion (
    template_milestone_id UUID NOT NULL,
    sort_order            INT NOT NULL,
    content               VARCHAR(200) NOT NULL,

    CONSTRAINT pk_plan_template_criterion PRIMARY KEY (template_milestone_id, sort_order),
    CONSTRAINT fk_plan_template_criterion_milestone FOREIGN KEY (template_milestone_id)
        REFERENCES plan_template_milestone(template_milestone_id) ON DELETE CASCADE,
    CONSTRAINT chk_plan_template_criterion_content CHECK (length(btrim(content)) BETWEEN 1 AND 200)
);
