#!/bin/sh
set -e

# Đường dẫn lưu trữ bằng cấp (mặc định /app/var/credentials hoặc từ biến môi trường)
STORAGE_DIR="${STORAGE_CREDENTIALS_ROOT:-/app/var/credentials}"

# Tạo sẵn thư mục lưu trữ và cấp quyền toàn quyền cho user edufit
mkdir -p "$STORAGE_DIR"
chown -R edufit:edufit "$STORAGE_DIR" /app/var 2>/dev/null || true
chmod -R 775 "$STORAGE_DIR" 2>/dev/null || true

# Kiểm tra an toàn biến môi trường database khi chạy trên Cloud (UAT/PROD)
if [ "$SPRING_PROFILES_ACTIVE" = "uat" ] || [ "$SPRING_PROFILES_ACTIVE" = "prod" ]; then
  if [ -z "$DB_URL" ] && [ -z "$DB_HOST" ]; then
    echo "=================================================================" >&2
    echo "[LỖI CẤU HÌNH CLOUD] Thiếu biến môi trường DB_URL hoặc DB_HOST!" >&2
    echo "Vui lòng vào Render Dashboard -> Environment và điền:" >&2
    echo "  DB_URL=jdbc:postgresql://<SUPABASE_HOST>:5432/postgres?sslmode=require" >&2
    echo "  DB_USERNAME=postgres.<PROJECT_REF>" >&2
    echo "  DB_PASSWORD=<MAT_KHAU_DB>" >&2
    echo "=================================================================" >&2
    exit 1
  fi
fi

# Thực thi ứng dụng dưới quyền người dùng phi đặc quyền edufit với gosu
exec gosu edufit java ${JAVA_OPTS:-} -jar app.jar "$@"
