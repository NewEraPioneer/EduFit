#!/usr/bin/env bash
# =============================================================================
# EduFit Backend UAT Local Launcher (Bash)
# Khởi chạy backend Java 21 kết nối tới Supabase PostgreSQL môi trường UAT
# =============================================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${REPO_ROOT}/.env.uat.local"

echo "================================================="
echo "       EduFit Backend - Supabase UAT Launcher     "
echo "================================================="

# 1. Kiểm tra Java version
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
else
    JAVA_CMD="java"
fi

if ! command -v "$JAVA_CMD" >/dev/null 2>&1; then
    echo "LỖI: Không tìm thấy lệnh java. Vui lòng cài đặt JDK 21+."
    exit 1
fi

JAVA_VER="$("$JAVA_CMD" -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)"
if [ -n "$JAVA_VER" ] && [ "$JAVA_VER" -lt 21 ]; then
    echo "LỖI: Java hiện tại là phiên bản $JAVA_VER. Yêu cầu JDK 21+."
    exit 1
fi
echo "[OK] Đang dùng Java phiên bản: $JAVA_VER"

# 2. Kiểm tra file cấu hình
if [ ! -f "$ENV_FILE" ]; then
    echo "[CẢNH BÁO] Không tìm thấy file: $ENV_FILE"
    echo "Vui lòng sao chép file mẫu và cấu hình thông tin kết nối Supabase UAT:"
    echo "  cp .env.uat.example .env.uat.local"
    exit 1
fi
echo "[OK] Đọc cấu hình từ: $ENV_FILE"

# 3. Export biến môi trường từ .env.uat.local
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

export SPRING_PROFILES_ACTIVE="uat"

# 4. Kiểm tra an toàn
TARGET_DB="${DB_URL:-${DB_HOST}}"
if [[ "$TARGET_DB" =~ (prod|production) ]]; then
    echo "LỖI AN TOÀN: DB_URL hoặc DB_HOST chứa từ khóa 'prod/production'. Hủy bỏ khởi chạy!"
    exit 1
fi

echo "-------------------------------------------------"
echo "  Môi trường:  UAT"
echo "  Profile:     uat"
echo "  Flyway:      chỉ chạy db/migration (KHÔNG nạp seed demo)"
echo "-------------------------------------------------"

# 5. Khởi chạy Backend qua Maven Wrapper
cd "${REPO_ROOT}/backend"
exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=uat
