#!/bin/sh
set -e

# Đường dẫn lưu trữ bằng cấp (mặc định /app/var/credentials hoặc từ biến môi trường)
STORAGE_DIR="${STORAGE_CREDENTIALS_ROOT:-/app/var/credentials}"

# Tạo sẵn thư mục lưu trữ và cấp quyền toàn quyền cho user edufit
mkdir -p "$STORAGE_DIR"
chown -R edufit:edufit "$STORAGE_DIR" /app/var 2>/dev/null || true
chmod -R 775 "$STORAGE_DIR" 2>/dev/null || true

# Thực thi ứng dụng dưới quyền người dùng phi đặc quyền edufit với gosu
exec gosu edufit java ${JAVA_OPTS:-} -jar app.jar "$@"
