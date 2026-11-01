-- =========================================================================
-- EduFit Database Migration V1.26: Thay tutor_subject bằng gói dạy (tutor_offering)
-- =========================================================================
-- Mỗi gói dạy = 1 môn (+ nhánh), nhiều khối lớp, nhiều mục tiêu (theo goal_tag_rule),
-- bộ sách, tỉnh luyện đề và học phí riêng. Học phí chung trên hồ sơ bị bỏ.
-- Hồ sơ gia sư dùng danh mục Tỉnh/Thành + nhiều Khu vực thay cho ô khu vực nhập tay.

CREATE FUNCTION pg_temp.edufit_area_text(raw TEXT) RETURNS TEXT AS $$
    SELECT ' ' || regexp_replace(lower(coalesce(raw, '')), '[,.;/()\-]+', ' ', 'g') || ' '
$$ LANGUAGE sql IMMUTABLE;

CREATE FUNCTION pg_temp.edufit_match_province(raw TEXT) RETURNS INT AS $$
    SELECT p.province_id
    FROM province p
    WHERE raw IS NOT NULL AND btrim(raw) <> ''
      AND (pg_temp.edufit_area_text(raw) LIKE '% ' || lower(p.name) || ' %'
           OR (p.code = 'HO_CHI_MINH' AND pg_temp.edufit_area_text(raw) ~ '(hcm|hồ chí minh|ho chi minh|sài gòn|sai gon)')
           OR (p.code = 'HA_NOI' AND pg_temp.edufit_area_text(raw) ~ '(ha noi|hanoi)'))
    ORDER BY length(p.name) DESC, p.province_id
    LIMIT 1
$$ LANGUAGE sql STABLE;

-- 1. HỒ SƠ GIA SƯ: loại gia sư (admin xác nhận) và khu vực dạy trực tiếp theo danh mục
ALTER TABLE tutor_profile ADD COLUMN IF NOT EXISTS tutor_type VARCHAR(30);
ALTER TABLE tutor_profile ADD COLUMN IF NOT EXISTS tutor_type_verified BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE tutor_profile ADD COLUMN IF NOT EXISTS province_id INT;
ALTER TABLE tutor_profile ADD CONSTRAINT chk_tutor_type
    CHECK (tutor_type IS NULL OR tutor_type IN ('UNIVERSITY_STUDENT', 'GRADUATE', 'IN_SERVICE_TEACHER', 'RETIRED_TEACHER'));
ALTER TABLE tutor_profile ADD CONSTRAINT fk_tutor_profile_province
    FOREIGN KEY (province_id) REFERENCES province(province_id);

CREATE TABLE IF NOT EXISTS tutor_district (
    tutor_id    UUID NOT NULL,
    district_id INT NOT NULL,

    CONSTRAINT pk_tutor_district PRIMARY KEY (tutor_id, district_id),
    CONSTRAINT fk_tutor_district_tutor FOREIGN KEY (tutor_id) REFERENCES tutor_profile(tutor_id) ON DELETE CASCADE,
    CONSTRAINT fk_tutor_district_district FOREIGN KEY (district_id) REFERENCES district(district_id)
);

UPDATE tutor_profile SET province_id = pg_temp.edufit_match_province(area)
WHERE area IS NOT NULL AND teaching_mode <> 'ONLINE';

INSERT INTO tutor_district (tutor_id, district_id)
SELECT t.tutor_id, d.district_id
FROM tutor_profile t
JOIN district d ON pg_temp.edufit_area_text(t.area) LIKE '%' || pg_temp.edufit_area_text(d.name) || '%'
WHERE t.area IS NOT NULL AND t.teaching_mode <> 'ONLINE'
  AND (t.province_id IS NULL OR d.province_id = t.province_id)
ON CONFLICT DO NOTHING;

UPDATE tutor_profile t
SET province_id = d.province_id
FROM tutor_district td
JOIN district d ON d.district_id = td.district_id
WHERE td.tutor_id = t.tutor_id AND t.province_id IS NULL;

-- 2. GÓI DẠY
CREATE TABLE IF NOT EXISTS tutor_offering (
    offering_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tutor_id            UUID NOT NULL,
    subject_id          INT NOT NULL,
    track_id            INT,
    price_per_session   BIGINT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_tutor_offering UNIQUE NULLS NOT DISTINCT (tutor_id, subject_id, track_id),
    CONSTRAINT fk_tutor_offering_tutor FOREIGN KEY (tutor_id) REFERENCES tutor_profile(tutor_id) ON DELETE CASCADE,
    CONSTRAINT fk_tutor_offering_subject FOREIGN KEY (subject_id) REFERENCES subject(subject_id),
    CONSTRAINT fk_tutor_offering_track FOREIGN KEY (track_id) REFERENCES subject_track(track_id),
    CONSTRAINT chk_tutor_offering_price CHECK (price_per_session >= 0)
);

CREATE INDEX IF NOT EXISTS idx_tutor_offering_subject ON tutor_offering (subject_id, tutor_id);

CREATE TABLE IF NOT EXISTS tutor_offering_level (
    offering_id UUID NOT NULL,
    level_id    INT NOT NULL,

    CONSTRAINT pk_tutor_offering_level PRIMARY KEY (offering_id, level_id),
    CONSTRAINT fk_offering_level_offering FOREIGN KEY (offering_id) REFERENCES tutor_offering(offering_id) ON DELETE CASCADE,
    CONSTRAINT fk_offering_level_level FOREIGN KEY (level_id) REFERENCES education_level(level_id)
);

-- evidence_verified: mục tiêu premium (HSG & Thi chuyên, chứng chỉ quốc tế) đã được admin duyệt bằng chứng
CREATE TABLE IF NOT EXISTS tutor_offering_goal (
    offering_id         UUID NOT NULL,
    goal_tag_id         INT NOT NULL,
    evidence_verified   BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT pk_tutor_offering_goal PRIMARY KEY (offering_id, goal_tag_id),
    CONSTRAINT fk_offering_goal_offering FOREIGN KEY (offering_id) REFERENCES tutor_offering(offering_id) ON DELETE CASCADE,
    CONSTRAINT fk_offering_goal_tag FOREIGN KEY (goal_tag_id) REFERENCES goal_tag(goal_tag_id)
);

CREATE TABLE IF NOT EXISTS tutor_offering_textbook (
    offering_id UUID NOT NULL,
    series_id   INT NOT NULL,

    CONSTRAINT pk_tutor_offering_textbook PRIMARY KEY (offering_id, series_id),
    CONSTRAINT fk_offering_textbook_offering FOREIGN KEY (offering_id) REFERENCES tutor_offering(offering_id) ON DELETE CASCADE,
    CONSTRAINT fk_offering_textbook_series FOREIGN KEY (series_id) REFERENCES textbook_series(series_id)
);

CREATE TABLE IF NOT EXISTS tutor_offering_exam_province (
    offering_id UUID NOT NULL,
    province_id INT NOT NULL,

    CONSTRAINT pk_tutor_offering_exam_province PRIMARY KEY (offering_id, province_id),
    CONSTRAINT fk_offering_exam_offering FOREIGN KEY (offering_id) REFERENCES tutor_offering(offering_id) ON DELETE CASCADE,
    CONSTRAINT fk_offering_exam_province FOREIGN KEY (province_id) REFERENCES province(province_id)
);

-- 3. CHUYỂN DỮ LIỆU tutor_subject -> tutor_offering (mỗi môn/nhánh một gói, gom các lớp)
CREATE TEMP TABLE legacy_offering_level ON COMMIT DROP AS
SELECT
    ts.tutor_id,
    CASE WHEN s.name IN ('Vật Lý', 'Hóa Học', 'Sinh Học')
         THEN (SELECT subject_id FROM subject WHERE name = 'Khoa học tự nhiên')
         ELSE ts.subject_id END AS subject_id,
    (SELECT track_id FROM subject_track WHERE code = CASE
        WHEN s.name IN ('Vật Lý', 'Hóa Học', 'Sinh Học') AND el.name IN ('Lớp 6', 'Lớp 7') THEN 'SCI_INTEGRATED'
        WHEN s.name = 'Vật Lý' AND el.name IN ('Lớp 8', 'Lớp 9') THEN 'SCI_PHYSICS'
        WHEN s.name = 'Hóa Học' AND el.name IN ('Lớp 8', 'Lớp 9') THEN 'SCI_CHEMISTRY'
        WHEN s.name = 'Tiếng Anh' THEN 'ENG_ACADEMIC'
        ELSE NULL END) AS track_id,
    ts.education_level_id AS level_id,
    t.price_per_session
FROM tutor_subject ts
JOIN subject s ON s.subject_id = ts.subject_id
JOIN education_level el ON el.level_id = ts.education_level_id
JOIN tutor_profile t ON t.tutor_id = ts.tutor_id;

INSERT INTO tutor_offering (tutor_id, subject_id, track_id, price_per_session)
SELECT DISTINCT tutor_id, subject_id, track_id, price_per_session
FROM legacy_offering_level
ON CONFLICT DO NOTHING;

INSERT INTO tutor_offering_level (offering_id, level_id)
SELECT o.offering_id, l.level_id
FROM legacy_offering_level l
JOIN tutor_offering o ON o.tutor_id = l.tutor_id AND o.subject_id = l.subject_id
    AND o.track_id IS NOT DISTINCT FROM l.track_id
ON CONFLICT DO NOTHING;

-- 4. BẰNG CHỨNG CHUYÊN MÔN GẮN VỚI GÓI DẠY (cho mục tiêu premium)
ALTER TABLE credential ADD COLUMN IF NOT EXISTS offering_id UUID;
ALTER TABLE credential ADD CONSTRAINT fk_credential_offering
    FOREIGN KEY (offering_id) REFERENCES tutor_offering(offering_id) ON DELETE SET NULL;

-- 5. HỌC PHÍ CHỐT THEO LỚP: lớp học lưu học phí tại thời điểm nhận yêu cầu,
-- để thanh toán không đổi khi gia sư sửa giá gói dạy sau này
ALTER TABLE class ADD COLUMN IF NOT EXISTS price_per_session BIGINT;
UPDATE class c SET price_per_session = t.price_per_session
FROM tutor_profile t WHERE t.tutor_id = c.tutor_id;
ALTER TABLE class ALTER COLUMN price_per_session SET NOT NULL;
ALTER TABLE class ADD CONSTRAINT chk_class_price CHECK (price_per_session >= 0);

-- 6. BỎ MÔ HÌNH CŨ
DROP TABLE IF EXISTS tutor_subject;
ALTER TABLE tutor_profile DROP COLUMN IF EXISTS price_per_session;
ALTER TABLE tutor_profile DROP COLUMN IF EXISTS area;
