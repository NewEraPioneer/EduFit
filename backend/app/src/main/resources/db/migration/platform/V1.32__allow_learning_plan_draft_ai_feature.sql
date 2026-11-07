-- =========================================================================
-- EduFit Database Migration V1.32: Cho phép ghi nhật ký AI cho tính năng
-- "Bản nháp lộ trình bằng AI" (Progress, Giai đoạn 3). Khớp enum AiFeature.
-- =========================================================================

ALTER TABLE ai_request_log DROP CONSTRAINT IF EXISTS chk_ai_log_feature;

ALTER TABLE ai_request_log
    ADD CONSTRAINT chk_ai_log_feature
        CHECK (feature IN ('TUTOR_BIO', 'MATCH_EXPLANATION', 'LEARNING_PLAN_DRAFT'));
