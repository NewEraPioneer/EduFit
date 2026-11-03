# Kết quả chuẩn bị và kiểm thử Smoke UAT (Render UAT Smoke Results)

**Thời điểm:** 02/11/2026
**Nhánh nguồn:** `deploy/deploy-render-supabase`
**Mục tiêu:** Ghi nhận kết quả kiểm tra cục bộ và danh mục kiểm thử khói (Smoke Test) khi deploy lên Render Cloud.

---

## 1. Trạng thái các điều kiện tiên quyết (Prerequisites)

| Thành phần | Trạng thái | Đánh giá |
| --- | --- | --- |
| **Mã nguồn & Cấu hình UAT** | **ĐÃ HOÀN TẤT** | `application-uat.yml` và `render.yaml` đã được thiết lập đúng chuẩn. |
| **Launcher Local UAT** | **ĐÃ HOÀN TẤT** | `scripts/run-backend-uat.ps1` (PowerShell) và `scripts/run-backend-uat.sh` (Bash) đã được kiểm thử chạy tốt trên JDK 21. |
| **Tài liệu & Runbook** | **ĐÃ HOÀN TẤT** | Đầy đủ quy trình môi trường (`environments-and-release.md`), runbook triển khai/rollback (`render-supabase.md`), và mẫu biến (`.env.uat.example`). |
| **Tài khoản Cloud Render/Supabase** | **CHỜ THAO TÁC CỦA NGƯỜI DÙNG** | Cần người dùng ủy quyền repo trên Render Dashboard và nhập các khóa bí mật vào Environment. |

---

## 2. Kế hoạch kiểm thử khói (Smoke Test Checklist trên Cloud)

Khi Web Service trên Render hoàn tất build từ nhánh `deploy/deploy-render-supabase`, thực hiện bộ smoke test theo thứ tự:

### 2.1. Kiểm tra Hạ tầng & Khởi động
- [ ] Endpoint `/actuator/health` phản hồi HTTP 200: `{"status":"UP"}`.
- [ ] Bảng `flyway_schema_history` trong Supabase được khởi tạo với toàn bộ migration hợp lệ.
- [ ] Log Tomcat không có lỗi out-of-memory (OOM) hoặc kết nối DB bị từ chối.

### 2.2. Kiểm tra Xác thực & Phiên làm việc (Session & CSRF)
- [ ] Endpoint `/api/v1/auth/csrf` trả về token CSRF hợp lệ.
- [ ] Đăng ký tài khoản kiểm thử UAT: `POST /api/v1/auth/register`.
- [ ] Đăng nhập: `POST /api/v1/auth/login` -> Trả về cookie `EDUFIT_SESSION`.
- [ ] Cookie session có cờ `HttpOnly`, `SameSite=Lax`, và `Secure` (qua Render HTTPS proxy).

### 2.3. Kiểm tra Nghiệp vụ Cốt lõi
- [ ] Đọc danh mục cấp học và môn học: `GET /api/v1/catalogs`.
- [ ] Cập nhật hồ sơ người dùng (Gia sư / Học sinh) và tải lại để xác nhận tính bền vững của dữ liệu trong PostgreSQL.

---

## 3. Các lưu ý quan trọng về tích hợp bên ngoài

1. **payOS (Cổng thanh toán):**
   - Trên môi trường UAT, chỉ kiểm thử với tài khoản Test/Sandbox được cung cấp bởi payOS.
   - Tuyệt đối không thực hiện giao dịch rút tiền hoặc nạp tiền bằng tài khoản ngân hàng thật trên bản UAT.
2. **AI Platform (Google Gemini):**
   - Mặc định khởi chạy ở chế độ `AI_PROVIDER=stub` để kiểm tra kết nối hạ tầng ổn định trước.
   - Chỉ chuyển sang `AI_PROVIDER=gemini` sau khi đã cấu hình `GEMINI_API_KEY` hợp lệ trong Render Environment.
3. **Lưu trữ file KYC:**
   - Bản Free của Render không có ổ đĩa cố định (persistent disk). File bằng cấp tải lên sẽ bị xóa khi service khởi động lại; đây là giới hạn đã được ghi nhận và cần nâng cấp sang Supabase Storage/S3 ở giai đoạn production.
