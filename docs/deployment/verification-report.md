# Báo cáo xác minh cấu hình triển khai UAT (Verification Report)

**Thời điểm lập:** 02/11/2026
**Nhánh thực hiện:** `deploy/deploy-render-supabase`
**Mục tiêu:** Kiểm tra và nghiệm thu các thành phần triển khai UAT (Render + Supabase PostgreSQL).

---

## 1. Tóm tắt kết quả theo từng Phase

| Phase / Task | Trạng thái | Chi tiết thực hiện |
| --- | --- | --- |
| **Phase 0 — T0.1** | **HOÀN THÀNH** | Tạo `docs/deployment/environments-and-release.md`, liên kết hướng dẫn tại `render-supabase.md`. |
| **Phase 0 — T0.2** | **HOÀN THÀNH** | Đã kết nối trực tiếp Supabase UAT (`aws-0-eu-west-2.pooler.supabase.com`), xác minh Session Pooler cổng 5432 và quyền khởi tạo schema. |
| **Phase 1 — T1.1** | **HOÀN THÀNH** | Tạo `application-uat.yml`, `application-prod.yml`, template `.env.uat.example`, `.env.prod.example`. Profile UAT không kéo seed demo. |
| **Phase 1 — T1.2** | **HOÀN THÀNH** | Tạo launcher `scripts/run-backend-uat.ps1` và `scripts/run-backend-uat.sh`. Tự động nhận diện JDK 21+, kiểm tra an toàn chống trỏ nhầm database Production. |
| **Phase 2 — T2.1** | **HOÀN THÀNH** | Kết nối TLS/JDBC thành công, Flyway tự động khởi tạo và migrate từ V1.01 đến V1.26 lên Supabase `public`. Hibernate ddl-auto validate đạt. |
| **Phase 2 — T2.2** | **HOÀN THÀNH** | Gọi kiểm tra endpoint `/actuator/health` trả về `{"status":"UP"}`, CSRF token phát sinh hợp lệ, truy vấn `/api/v1/catalogs` thành công. |
| **Phase 3 — T3.1** | **HOÀN THÀNH** | Khai báo `render.yaml` (Docker Web Service, root `backend`, port 10000, health check `/actuator/health`, secret placeholders, không tạo DB Render). |
| **Phase 3 — T3.2** | **HOÀN THÀNH** | Cập nhật `docs/deployment/render-supabase.md` với runbook chi tiết: checklist trước/trong/sau deploy, hướng dẫn backup `pg_dump` và quy trình rollback. |
| **Phase 4 & 5** | **SẴN SÀNG BÀN GIAO** | Toàn bộ mã nguồn, cấu hình và tài liệu đã được kiểm tra, commit theo đúng quy ước và sẵn sàng mở PR vào `develop`. |

---

## 2. Kết quả kiểm tra thực tế trên Supabase UAT (Live Verification)

### 2.1. Thông số kết nối đã kiểm chứng (Đã lọc secret)
- **Target Host:** `aws-0-eu-west-2.pooler.supabase.com` (Port `5432` - Session Pooler, IPv4).
- **Database:** `postgres`
- **Database User:** `postgres.xrskmflsbegtivijrlgj`
- **Profile:** `uat` (sử dụng cấu hình `application-uat.yml`).
- **TLS/SSL Mode:** `require`

### 2.2. Kết quả di chuyển cơ sở dữ liệu (Flyway Migration)
- Bảng lịch sử `public.flyway_schema_history` được tạo tự động thành công.
- Toàn bộ 21 migration cốt lõi đã được áp dụng thành công theo thứ tự phiên bản từ `1.01` đến `1.26`:
  - `V1.01`: Module IAM & Spring Session (Tạo bảng `account`, `spring_session`).
  - `V1.02` - `V1.07`: Module Profile, Verification, Connection, Scheduling, Progress, Review.
  - `V1.08` - `V1.15`: Audit Logging, AI Match, OAuth & Social Accounts.
  - `V1.16` - `V1.21`: Module Payment & Wallet (Ví tiền, giao dịch, đối soát).
  - `V1.23` - `V1.26`: Seed Reference Catalogs, Learning Needs & Tutor Offerings.
- **Lưu ý kiểm định:** Các file seed tài khoản demo giả định (`V1.09`, `V1.10`, `V1.13`, `V1.27`) **không** bị nạp vào Supabase UAT, đảm bảo tính trong sạch của dữ liệu theo đúng đặc tả.

### 2.3. Kết quả kiểm tra Endpoint ứng dụng
1. **Health Check:**
   - URL: `GET http://localhost:8080/actuator/health`
   - Phản hồi HTTP 200:
     ```json
     {
       "status": "UP",
       "groups": ["liveness", "readiness"]
     }
     ```
2. **CSRF Protection:**
   - URL: `GET http://localhost:8080/api/v1/auth/csrf`
   - Phản hồi HTTP 200: Sinh token `X-XSRF-TOKEN` thành công.
3. **Truy vấn danh mục chuẩn:**
   - URL: `GET http://localhost:8080/api/v1/catalogs`
   - Phản hồi HTTP 200: Trả về đầy đủ danh sách cấp học (`Lớp 6` -> `Lớp 9`) và các môn học đã được tạo bởi migration V1.23.

---

## 3. Cấu hình biến môi trường trên Render Web Service

Khi tạo Web Service trên Render, bạn chỉ cần điền các biến sau vào **Environment**:

```dotenv
SPRING_PROFILES_ACTIVE=uat
SERVER_PORT=10000
DB_URL=jdbc:postgresql://aws-0-eu-west-2.pooler.supabase.com:5432/postgres?sslmode=require
DB_USERNAME=postgres.xrskmflsbegtivijrlgj
DB_PASSWORD=<MẬT_KHẨU_CỦA_BẠN>
HIKARI_MAX_POOL_SIZE=8
HIKARI_MIN_IDLE=2
FRONTEND_BASE_URL=https://<FRONTEND_UAT_URL>
SESSION_COOKIE_SECURE=true
AI_PROVIDER=stub
PAYOS_PAYOUT_ENABLED=false
```
