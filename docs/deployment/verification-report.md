# Báo cáo xác minh cấu hình triển khai UAT (Verification Report)

**Thời điểm lập:** 02/11/2026  
**Nhánh thực hiện:** `deploy/deploy-render-supabase`  
**Mục tiêu:** Kiểm tra và nghiệm thu các thành phần triển khai UAT (Render + Supabase PostgreSQL).

---

## 1. Tóm tắt kết quả theo từng Phase

| Phase / Task | Trạng thái | Chi tiết thực hiện |
| --- | --- | --- |
| **Phase 0 — T0.1** | **HOÀN THÀNH** | Tạo `docs/deployment/environments-and-release.md`, liên kết hướng dẫn tại `render-supabase.md`. |
| **Phase 0 — T0.2** | **CHỜ THÔNG TIN CLOUD** | Đã chuẩn bị mẫu kết nối và script kiểm tra; chờ người dùng cung cấp thông tin Supabase UAT qua `.env.uat.local`. |
| **Phase 1 — T1.1** | **HOÀN THÀNH** | Tạo `application-uat.yml`, `application-prod.yml`, template `.env.uat.example`, `.env.prod.example`. Profile UAT không kéo seed demo. |
| **Phase 1 — T1.2** | **HOÀN THÀNH** | Tạo launcher `scripts/run-backend-uat.ps1` và `scripts/run-backend-uat.sh`. Tự động nhận diện JDK 21+, kiểm tra an toàn chống trỏ nhầm database Production. |
| **Phase 2 — T2.1 & T2.2** | **CHỜ THÔNG TIN CLOUD** | Cơ chế launcher và kết nối JDBC đã sẵn sàng; cần thông tin host/password của project Supabase UAT từ người dùng để chạy migration thật. |
| **Phase 3 — T3.1** | **HOÀN THÀNH** | Khai báo `render.yaml` (Docker Web Service, root `backend`, port 10000, health check `/actuator/health`, secret placeholders, không tạo DB Render). |
| **Phase 3 — T3.2** | **HOÀN THÀNH** | Cập nhật `docs/deployment/render-supabase.md` với runbook chi tiết: checklist trước/trong/sau deploy, hướng dẫn backup `pg_dump` và quy trình rollback. |
| **Phase 4 & 5** | **SẴN SÀNG BÀN GIAO** | Toàn bộ mã nguồn, cấu hình và tài liệu đã được kiểm tra, commit theo đúng quy ước và sẵn sàng mở PR vào `develop`. |

---

## 2. Kết quả kiểm tra cục bộ (Local Verification Checks)

### 2.1. Kiểm tra môi trường JDK 21
- **Trình thực thi:** `C:\Program Files\Java\jdk-21.0.10\bin\java.exe`
- **Phiên bản:** `java version "21.0.10" 2026-01-20 LTS`
- **Kết quả:** Launcher script `scripts/run-backend-uat.ps1` tự động phát hiện và gán `JAVA_HOME` thành công mà không gây xung đột với JDK 8 mặc định của hệ thống.

### 2.2. Kiểm tra tính năng của Launcher UAT
- **Kiểm tra khi thiếu file `.env.uat.local`:**
  - Script đưa ra hướng dẫn rõ ràng: `Copy-Item .env.uat.example .env.uat.local`, thoát an toàn không crash.
- **Kiểm tra khi nạp cấu hình hợp lệ (cờ `-CheckOnly`):**
  - Đọc thành công các thuộc tính `DB_URL`, `DB_HOST`, `DB_USER`.
  - Profile kích hoạt: `uat`.
  - Mật khẩu được ẩn hoàn toàn trên màn hình console (`:****@`).
  - Kiểm tra an toàn: Nếu chuỗi kết nối chứa từ khóa `prod` hoặc `production`, launcher sẽ lập tức dừng và báo lỗi bảo mật.

### 2.3. Kiểm tra tính toàn vẹn của Profile UAT (`application-uat.yml`)
- Cấu hình Flyway chỉ đọc `classpath:db/migration` (tuyệt đối không nạp `classpath:db/seed`).
- Cấu hình Hikari pool: `maximum-pool-size: 8`, `minimum-idle: 2` (phù hợp với giới hạn connection của Supabase Free Session Pooler).
- Cấu hình Cookie session: Bật `secure: true`, `same-site: lax`, `http-only: true`, `forward-headers-strategy: framework` cho môi trường HTTPS qua Render proxy.

---

## 3. Các bước tiếp theo để người dùng chạy thử nghiệm Supabase thật

Để chạy backend kết nối trực tiếp với Supabase UAT của bạn:

1. **Tạo file cấu hình cá nhân (file này đã được Git ignore):**
   ```powershell
   Copy-Item .env.uat.example .env.uat.local
   ```
2. **Điền thông tin Supabase từ Dashboard (chọn Session Pooler, cổng 5432):**
   Mở file `.env.uat.local` và cập nhật:
   ```dotenv
   DB_URL=jdbc:postgresql://<SUPABASE_POOLER_HOST>:5432/postgres?sslmode=require
   DB_USERNAME=postgres.<PROJECT_REF>
   DB_PASSWORD=<MAT_KHAU_DB_SUPABASE>
   ```
3. **Chạy kiểm tra kết nối:**
   ```powershell
   .\scripts\run-backend-uat.ps1 -CheckOnly
   ```
4. **Khởi chạy backend kết nối Supabase và chạy migration:**
   ```powershell
   .\scripts\run-backend-uat.ps1
   ```
   - Flyway sẽ tự động kết nối và tạo toàn bộ bảng trong schema `public`.
   - Mở trình duyệt tại `http://localhost:8080/actuator/health` để xác nhận trạng thái `{"status":"UP"}`.
