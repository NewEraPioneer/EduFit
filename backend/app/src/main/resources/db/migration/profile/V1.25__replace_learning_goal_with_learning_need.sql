-- =========================================================================
-- EduFit Database Migration V1.25: Thay learning_goal bằng learning_need
-- =========================================================================
-- Nhu cầu học tự lưu khối lớp, môn, nhánh, đúng 1 mục tiêu và các điều kiện theo luật
-- (bộ sách, tỉnh dự thi). Bỏ current/desired level, deadline, budget_min và enum GoalType.
-- Khu vực nhập tay được thay bằng danh mục Tỉnh/Thành -> Khu vực.

-- 0. Hàm tạm nhận diện địa danh từ chuỗi khu vực nhập tay (chỉ tồn tại trong phiên migrate)
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

CREATE FUNCTION pg_temp.edufit_match_district(raw TEXT, in_province INT) RETURNS INT AS $$
    SELECT d.district_id
    FROM district d
    WHERE raw IS NOT NULL AND btrim(raw) <> ''
      AND (in_province IS NULL OR d.province_id = in_province)
      AND pg_temp.edufit_area_text(raw) LIKE '%' || pg_temp.edufit_area_text(d.name) || '%'
    ORDER BY length(d.name) DESC, d.district_id
    LIMIT 1
$$ LANGUAGE sql STABLE;

-- 1. HỒ SƠ HỌC SINH: khu vực mặc định theo danh mục (dùng để điền sẵn nhu cầu mới)
ALTER TABLE student_profile ADD COLUMN IF NOT EXISTS province_id INT;
ALTER TABLE student_profile ADD COLUMN IF NOT EXISTS district_id INT;
ALTER TABLE student_profile ADD CONSTRAINT fk_student_profile_province FOREIGN KEY (province_id) REFERENCES province(province_id);
ALTER TABLE student_profile ADD CONSTRAINT fk_student_profile_district FOREIGN KEY (district_id) REFERENCES district(district_id);

UPDATE student_profile SET province_id = pg_temp.edufit_match_province(area) WHERE area IS NOT NULL;
UPDATE student_profile sp
SET district_id = pg_temp.edufit_match_district(sp.area, sp.province_id)
WHERE sp.area IS NOT NULL;
UPDATE student_profile sp
SET province_id = d.province_id
FROM district d
WHERE sp.district_id = d.district_id AND sp.province_id IS NULL;

ALTER TABLE student_profile DROP COLUMN IF EXISTS area;

-- 2. BẢNG NHU CẦU HỌC (LEARNING_NEED)
-- Cột phân loại được phép NULL chỉ để giữ dữ liệu cũ chưa đủ thông tin (status = INCOMPLETE);
-- backend bắt buộc đủ phân loại hợp lệ ở mọi lần tạo/sửa.
CREATE TABLE IF NOT EXISTS learning_need (
    need_id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id              UUID NOT NULL,
    created_by              UUID NOT NULL,
    education_level_id      INT,
    subject_id              INT NOT NULL,
    track_id                INT,
    goal_tag_id             INT,
    textbook_series_id      INT,
    exam_province_id        INT,
    target_school_id        INT,
    mode                    VARCHAR(20),
    province_id             INT,
    district_id             INT,
    budget_max_per_session  BIGINT,
    status                  VARCHAR(20) NOT NULL DEFAULT 'INCOMPLETE',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_learning_need_student FOREIGN KEY (student_id) REFERENCES student_profile(student_id) ON DELETE CASCADE,
    CONSTRAINT fk_learning_need_creator FOREIGN KEY (created_by) REFERENCES account(user_id),
    CONSTRAINT fk_learning_need_level FOREIGN KEY (education_level_id) REFERENCES education_level(level_id),
    CONSTRAINT fk_learning_need_subject FOREIGN KEY (subject_id) REFERENCES subject(subject_id),
    CONSTRAINT fk_learning_need_track FOREIGN KEY (track_id) REFERENCES subject_track(track_id),
    CONSTRAINT fk_learning_need_goal_tag FOREIGN KEY (goal_tag_id) REFERENCES goal_tag(goal_tag_id),
    CONSTRAINT fk_learning_need_textbook FOREIGN KEY (textbook_series_id) REFERENCES textbook_series(series_id),
    CONSTRAINT fk_learning_need_exam_province FOREIGN KEY (exam_province_id) REFERENCES province(province_id),
    CONSTRAINT fk_learning_need_school FOREIGN KEY (target_school_id) REFERENCES specialized_school(school_id),
    CONSTRAINT fk_learning_need_province FOREIGN KEY (province_id) REFERENCES province(province_id),
    CONSTRAINT fk_learning_need_district FOREIGN KEY (district_id) REFERENCES district(district_id),
    CONSTRAINT chk_learning_need_mode CHECK (mode IS NULL OR mode IN ('ONLINE', 'OFFLINE', 'BOTH')),
    CONSTRAINT chk_learning_need_budget CHECK (budget_max_per_session IS NULL OR budget_max_per_session > 0),
    CONSTRAINT chk_learning_need_status CHECK (status IN ('INCOMPLETE', 'ACTIVE', 'ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_learning_need_student ON learning_need (student_id, status);

CREATE TABLE IF NOT EXISTS learning_need_slot (
    slot_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    need_id     UUID NOT NULL,
    day_of_week SMALLINT NOT NULL,
    start_time  TIME NOT NULL,
    end_time    TIME NOT NULL,

    CONSTRAINT fk_need_slot_need FOREIGN KEY (need_id) REFERENCES learning_need(need_id) ON DELETE CASCADE,
    CONSTRAINT chk_need_slot_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_need_slot_time CHECK (end_time > start_time)
);

CREATE INDEX IF NOT EXISTS idx_learning_need_slot_need ON learning_need_slot (need_id, day_of_week);

-- 3. CHUYỂN DỮ LIỆU learning_goal -> learning_need (giữ nguyên ID để đổi khóa ngoại)
WITH legacy AS (
    SELECT
        g.goal_id,
        g.student_id,
        sp.user_id,
        sp.education_level_id AS level_id,
        el.name AS level_name,
        s.name AS subject_name,
        g.goal_type,
        g.mode,
        g.area,
        g.budget_max,
        g.status,
        g.created_at,
        g.updated_at
    FROM learning_goal g
    JOIN student_profile sp ON sp.student_id = g.student_id
    JOIN subject s ON s.subject_id = g.subject_id
    LEFT JOIN education_level el ON el.level_id = sp.education_level_id
),
mapped AS (
    SELECT
        l.*,
        CASE WHEN l.subject_name IN ('Vật Lý', 'Hóa Học', 'Sinh Học')
             THEN (SELECT subject_id FROM subject WHERE name = 'Khoa học tự nhiên')
             ELSE (SELECT subject_id FROM subject WHERE name = l.subject_name) END AS new_subject_id,
        CASE
            WHEN l.subject_name IN ('Vật Lý', 'Hóa Học', 'Sinh Học') AND l.level_name IN ('Lớp 6', 'Lớp 7') THEN 'SCI_INTEGRATED'
            WHEN l.subject_name = 'Vật Lý' AND l.level_name IN ('Lớp 8', 'Lớp 9') THEN 'SCI_PHYSICS'
            WHEN l.subject_name = 'Hóa Học' AND l.level_name IN ('Lớp 8', 'Lớp 9') THEN 'SCI_CHEMISTRY'
            WHEN l.subject_name = 'Tiếng Anh' THEN 'ENG_ACADEMIC'
            ELSE NULL -- Sinh học lớp 8–9 chưa có nhánh tương ứng: để trống cho người dùng chọn lại
        END AS track_code,
        CASE l.goal_type
            WHEN 'CONSOLIDATE' THEN 'FOUNDATION'
            WHEN 'IMPROVE_GRADES' THEN 'GRADE_BOOST'
            WHEN 'ADVANCED_STUDY' THEN 'GIFTED'
            WHEN 'EXAM_PREPARATION' THEN
                CASE WHEN l.level_name IN ('Lớp 8', 'Lớp 9') THEN 'GRADE10_EXAM'
                     WHEN l.level_name IN ('Lớp 6', 'Lớp 7') THEN 'GRADE_BOOST'
                     ELSE NULL END
            ELSE 'FOUNDATION'
        END AS tag_code,
        pg_temp.edufit_match_province(l.area) AS area_province_id
    FROM legacy l
)
INSERT INTO learning_need (
    need_id, student_id, created_by, education_level_id, subject_id, track_id, goal_tag_id,
    mode, province_id, district_id, budget_max_per_session, status, created_at, updated_at
)
SELECT
    m.goal_id,
    m.student_id,
    m.user_id,
    m.level_id,
    m.new_subject_id,
    t.track_id,
    -- Chỉ giữ mục tiêu nếu tổ hợp đúng luật; sai luật thì để trống cho người dùng chọn lại
    CASE WHEN EXISTS (
        SELECT 1 FROM goal_tag_rule r
        WHERE r.goal_tag_id = gt.goal_tag_id AND r.subject_id = m.new_subject_id
          AND r.level_id = m.level_id AND r.track_id IS NOT DISTINCT FROM t.track_id
    ) THEN gt.goal_tag_id ELSE NULL END,
    m.mode,
    CASE WHEN m.mode = 'ONLINE' THEN NULL ELSE m.area_province_id END,
    CASE WHEN m.mode = 'ONLINE' THEN NULL ELSE pg_temp.edufit_match_district(m.area, m.area_province_id) END,
    CASE WHEN m.budget_max > 0 THEN m.budget_max ELSE NULL END,
    CASE WHEN m.status = 'ARCHIVED' THEN 'ARCHIVED' ELSE 'INCOMPLETE' END,
    m.created_at,
    m.updated_at
FROM mapped m
LEFT JOIN subject_track t ON t.code = m.track_code
LEFT JOIN goal_tag gt ON gt.code = m.tag_code;

-- Khu vực chỉ nhận diện được quận thì suy ra tỉnh từ quận
UPDATE learning_need n
SET province_id = d.province_id
FROM district d
WHERE n.district_id = d.district_id AND n.province_id IS NULL;

INSERT INTO learning_need_slot (slot_id, need_id, day_of_week, start_time, end_time)
SELECT s.slot_id, s.goal_id, s.day_of_week, s.start_time, s.end_time
FROM goal_availability_slot s
JOIN learning_need n ON n.need_id = s.goal_id;

-- Nhu cầu đủ thông tin theo luật thì chuyển ACTIVE; còn thiếu (bộ sách, tỉnh dự thi, nhánh...) giữ INCOMPLETE
UPDATE learning_need n
SET status = 'ACTIVE'
FROM goal_tag gt
WHERE n.status = 'INCOMPLETE'
  AND gt.goal_tag_id = n.goal_tag_id
  AND n.education_level_id IS NOT NULL
  AND (n.track_id IS NOT NULL OR NOT EXISTS (
        SELECT 1 FROM subject_track st WHERE st.subject_id = n.subject_id AND st.is_active))
  AND (NOT gt.requires_textbook OR n.textbook_series_id IS NOT NULL)
  AND (NOT gt.requires_exam_province OR n.exam_province_id IS NOT NULL)
  AND n.mode IS NOT NULL
  AND (n.mode = 'ONLINE' OR n.province_id IS NOT NULL)
  AND n.budget_max_per_session IS NOT NULL
  AND EXISTS (SELECT 1 FROM learning_need_slot s WHERE s.need_id = n.need_id);

-- 4. ĐỔI goal_id -> need_id ở các bảng tham chiếu
ALTER TABLE connection_request DROP CONSTRAINT IF EXISTS fk_conn_req_goal;
ALTER TABLE connection_request RENAME COLUMN goal_id TO need_id;
ALTER TABLE connection_request ADD CONSTRAINT fk_conn_req_need
    FOREIGN KEY (need_id) REFERENCES learning_need(need_id) ON DELETE CASCADE;

ALTER TABLE class DROP CONSTRAINT IF EXISTS fk_class_goal;
ALTER TABLE class RENAME COLUMN goal_id TO need_id;
ALTER TABLE class ADD CONSTRAINT fk_class_need
    FOREIGN KEY (need_id) REFERENCES learning_need(need_id) ON DELETE CASCADE;

ALTER TABLE matching_run_log DROP CONSTRAINT IF EXISTS fk_matching_log_goal;
ALTER TABLE matching_run_log RENAME COLUMN goal_id TO need_id;
ALTER TABLE matching_run_log ADD CONSTRAINT fk_matching_log_need
    FOREIGN KEY (need_id) REFERENCES learning_need(need_id) ON DELETE CASCADE;

-- 5. BỎ MÔ HÌNH CŨ
DROP TABLE IF EXISTS goal_availability_slot;
DROP TABLE IF EXISTS learning_goal;
