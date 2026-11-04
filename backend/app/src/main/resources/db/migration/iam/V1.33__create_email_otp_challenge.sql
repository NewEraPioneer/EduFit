-- =========================================================================
-- EduFit Database Migration V1.33: Thử thách mã OTP qua email (IAM)
-- =========================================================================
-- Một dòng = một thử thách OTP 6 số đang chờ người dùng nhập, theo mục đích:
--   * REGISTRATION   : đăng ký chờ xác thực email. Tài khoản (bảng account) CHỈ được tạo khi
--                      nhập đúng mã, nên email chưa xác thực coi như chưa đăng ký.
--   * PASSWORD_RESET : quên mật khẩu, nhập đúng mã mới được cấp token đặt lại mật khẩu.
--
-- Bảo mật:
--   * code_hash = SHA-256(challenge_id || ':' || code), không lưu mã thô; challenge_id đóng vai trò salt.
--   * failed_attempts giới hạn số lần nhập sai cho mỗi mã; send_count + send_window_started_at
--     giới hạn số lần gửi mã trong một cửa sổ thời gian (chống brute-force và spam hộp thư).
--   * Mỗi (purpose, email) chỉ có tối đa MỘT thử thách chưa tiêu thụ (unique partial index).

CREATE TABLE IF NOT EXISTS email_otp_challenge (
    challenge_id           UUID PRIMARY KEY,
    purpose                VARCHAR(30)  NOT NULL,
    email                  VARCHAR(255) NOT NULL,
    code_hash              VARCHAR(64)  NOT NULL,
    failed_attempts        INT          NOT NULL DEFAULT 0,
    send_count             INT          NOT NULL DEFAULT 1,
    send_window_started_at TIMESTAMPTZ  NOT NULL,
    last_sent_at           TIMESTAMPTZ  NOT NULL,
    expires_at             TIMESTAMPTZ  NOT NULL,
    consumed_at            TIMESTAMPTZ,
    password_hash          VARCHAR(255),
    full_name              VARCHAR(150),
    role                   VARCHAR(20),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_otp_purpose CHECK (purpose IN ('REGISTRATION', 'PASSWORD_RESET')),
    CONSTRAINT chk_otp_counters CHECK (failed_attempts >= 0 AND send_count >= 1),
    CONSTRAINT chk_otp_code_hash CHECK (code_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_otp_registration_payload CHECK (
        purpose <> 'REGISTRATION'
        OR (password_hash IS NOT NULL AND full_name IS NOT NULL AND role IN ('STUDENT', 'PARENT', 'TUTOR'))
    ),
    CONSTRAINT chk_otp_reset_has_no_payload CHECK (
        purpose <> 'PASSWORD_RESET'
        OR (password_hash IS NULL AND full_name IS NULL AND role IS NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_email_otp_challenge_active
    ON email_otp_challenge (purpose, email)
    WHERE consumed_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_email_otp_challenge_expires_at
    ON email_otp_challenge (expires_at);
