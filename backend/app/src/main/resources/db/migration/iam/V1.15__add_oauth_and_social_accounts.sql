-- =========================================================================
-- EduFit Database Migration V1.15: Thêm hỗ trợ Đăng nhập bên thứ 3 (OAuth2)
-- và ảnh đại diện người dùng (Avatar)
-- =========================================================================

-- 1. Cho phép password_hash có thể null đối với tài khoản đăng nhập qua OAuth2/Social
ALTER TABLE account ALTER COLUMN password_hash DROP NOT NULL;

-- 2. Thêm cột avatar_url vào bảng account phục vụ hồ sơ và avatar từ mạng xã hội
ALTER TABLE account ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);

-- 3. Bảng ánh xạ định danh mạng xã hội (Google, Zalo,...) với tài khoản EduFit
CREATE TABLE IF NOT EXISTS user_social_account (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES account(user_id) ON DELETE CASCADE,
    provider            VARCHAR(50) NOT NULL, -- 'GOOGLE', 'ZALO'
    provider_user_id    VARCHAR(255) NOT NULL, -- sub/id từ provider
    email               VARCHAR(255),
    avatar_url          VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_social_provider_user UNIQUE (provider, provider_user_id)
);

CREATE INDEX IF NOT EXISTS idx_social_account_user ON user_social_account (user_id);
CREATE INDEX IF NOT EXISTS idx_social_account_provider ON user_social_account (provider, provider_user_id);
