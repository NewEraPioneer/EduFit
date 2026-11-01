# Hướng Dẫn Từng Bước Triển Khai Kiểm Thử Tích Hợp Tự Động Toàn Hệ Thống EduFit
## (Comprehensive Step-by-Step Full-Stack E2E Verification Guide & Execution Runbook)

> **Mã tài liệu:** EDUFIT-GUIDE-E2E-SYSTEM-VERIFICATION  
> **Phiên bản:** 3.0 (Tích hợp Full-Stack thống nhất: UI Trình duyệt + Backend API + CSDL Docker PostgreSQL 17 theo từng luồng nghiệp vụ)  
> **Nhánh phát triển Git:** `test/system-e2e-verification` (tách độc lập từ nhánh `develop`)  
> **Căn cứ tài liệu kỹ thuật & nghiệp vụ:**
> - [SRS Edufit.docx](file:///c:/University/Semester%205/SWP391/Edufit/EduFit/docs/SRS%20Edufit.docx)
> - [edufit_master_test_plan.md](file:///c:/University/Semester%205/SWP391/Edufit/EduFit/docs/edufit_master_test_plan.md)
> - [payment-flow-and-rules.md](file:///c:/University/Semester%205/SWP391/Edufit/EduFit/docs/payment/payment-flow-and-rules.md)
> - [api-contract.md (Payment)](file:///c:/University/Semester%205/SWP391/Edufit/EduFit/docs/payment/api-contract.md)
> - [edufit_work_breakdown_plan.md (Nhiệm vụ 4.4)](file:///c:/University/Semester%205/SWP391/Edufit/EduFit/docs/edufit_work_breakdown_plan.md)

---

## TRIẾT LÝ THIẾT KẾ: KIỂM THỬ E2E HỢP NHẤT TOÀN DIỆN (FULL-STACK UNIFIED E2E)

Kiểm thử End-to-End (E2E) thực sự của hệ thống không thể tách rời Frontend một nơi, Backend một nẻo. Một hành trình người dùng hoàn chỉnh luôn đi xuyên suốt 3 tầng:

```
[Trình duyệt Chromium (Playwright UI)]
               │
               ▼ (HTTP REST / Session Cookie / JSON / Multipart)
[Hệ thống Backend (Spring Boot 4.1.1 / Java 21 / Hibernate 7 / Modular Monolith / Apache Tika)]
               │
               ▼ (SQL / JDBC Hikari / Flyway / GiST / ACID Transactions)
[Cơ sở Dữ liệu Thực tế (Docker PostgreSQL 17 - Ubuntu Noble)]
```

Tài liệu này hướng dẫn chi tiết từng luồng hành trình người dùng (Journey) theo cấu trúc step-by-step. Trong mỗi luồng, ta kiểm chứng đồng thời cả:
1. **Trải nghiệm Giao diện Người dùng Thật (Playwright UI E2E):** Nhập form, click nút, quan sát Toast/Modal, kiểm tra bôi đỏ validation, điều hướng phân quyền, hỗ trợ chế độ bật màn hình (`--headed`) để trực tiếp quan sát.
2. **Chiều Sâu Logic & Tính Toàn Vẹn CSDL (Backend & Database Verification):** Kiểm tra mã lỗi HTTP chuẩn, Session Cookie `HttpOnly`, kiểm tra Magic Bytes file nhị phân, khóa chống trùng lịch GiST, tính nguyên tử All-or-Nothing, xác thực chữ ký số Webhook HMAC-SHA256 trên container Docker PostgreSQL 17 thật.

---

## MỤC LỤC TỔNG QUAN

- [PHẦN I: THIẾT LẬP NỀN TẢNG KIỂM THỬ TÍCH HỢP TOÀN HỆ THỐNG](#phần-i-thiết-lập-nền-tảng-kiểm-thử-tích-hợp-toàn-hệ-thống)
  - [Mục 1.1: Ba Nguyên Tắc Kỹ Thuật Bất Biến](#mục-11-ba-nguyên-tắc-kỹ-thuật-bất-biến)
  - [Mục 1.2: Thiết Lập CSDL Docker Testcontainers Chuẩn Hóa (PostgreSQL 17 Noble)](#mục-12-thiết-lập-csdl-docker-testcontainers-chuẩn-hóa-postgresql-17-noble)
  - [Mục 1.3: Cài Đặt & Cấu Hình Playwright Headed Runner & WebServer](#mục-13-cài-đặt--cấu-hình-playwright-headed-runner--webserver)
  - [Mục 1.4: Kiến Trúc Page Object Model (POM) Bền Vững & Chống Gãy UI](#mục-14-kiến-trúc-page-object-model-pom-bền-vững--chống-gãy-ui)
- [PHẦN II: 10 LUỒNG HÀNH TRÌNH E2E TOÀN DIỆN (FULL-STACK UNIFIED JOURNEYS)](#phần-ii-10-luồng-hành-trình-e2e-toàn-diện-full-stack-unified-journeys)
  - [Mục 2.1: Luồng 01 - Định Danh, Phiên Đăng Nhập, Argon2id & Khóa Brute-Force (AC-01, AC-02)](#mục-21-luồng-01---định-danh-phiên-đăng-nhập-argon2id--khóa-brute-force-ac-01-ac-02)
  - [Mục 2.2: Luồng 02 - Quét Bằng Cấp Magic Bytes (Apache Tika) & Admin Duyệt KYC (AC-03, AC-04)](#mục-22-luồng-02---quét-bằng-cấp-magic-bytes-apache-tika--admin-duyệt-kyc-ac-03-ac-04)
  - [Mục 2.3: Luồng 03 - Quản Trị Danh Mục & Thiết Lập Hồ Sơ, Khung Rảnh Tuần (AC-05, AC-06)](#mục-23-luồng-03---quản-trị-danh-mục--thiết-lập-hồ-sơ-khung-rảnh-tuần-ac-05-ac-06)
  - [Mục 2.4: Luồng 04 - Khám Phá Công Khai, Tìm Kiếm Đa Tiêu Chí & Gợi Ý AI Match (AC-07)](#mục-24-luồng-04---khám-phá-công-khai-tìm-kiếm-đa-tiêu-chí--gợi-ý-ai-match-ac-07)
  - [Mục 2.5: Luồng 05 - Kết Nối Gia Đình Phụ Huynh - Học Sinh & Rate Limiting (AC-08, AC-09)](#mục-25-luồng-05---kết-nối-gia-đình-phụ-huynh---học-sinh--rate-limiting-ac-08-ac-09)
  - [Mục 2.6: Luồng 06 - Đề Nghị Kèm Cặp Trực Tiếp / Đại Diện & Tạo Lớp Học (AC-10, AC-11)](#mục-26-luồng-06---đề-nghị-kèm-cặp-trực-tiếp--đại-diện--tạo-lớp-học-ac-10-ac-11)
  - [Mục 2.7: Luồng 07 - Lập Lịch Học, Khóa Chống Trùng Giờ GiST & Batch All-or-Nothing (AC-12, AC-13)](#mục-27-luồng-07---lập-lịch-học-khóa-chống-trùng-giờ-gist--batch-all-or-nothing-ac-12-ac-13)
  - [Mục 2.8: Luồng 08 - Đổi Lịch Đột Xuất, Hủy Muộn & Vòng Đời Điểm Danh Bất Biến (AC-14, AC-15)](#mục-28-luồng-08---đổi-lịch-đột-xuất-hủy-muộn--vòng-đời-điểm-danh-bất-biến-ac-14-ac-15)
  - [Mục 2.9: Luồng 09 - Ví Học Phí, Ký Quỹ Escrow, Quyết Toán Tự Động & Rút Tiền Napas247 (AC-19, AC-20, AC-21, AC-22)](#mục-29-luồng-09---ví-học-phí-ký-quỹ-escrow-quyết-toán-tự-động--rút-tiền-napas247-ac-19-ac-20-ac-21-ac-22)
  - [Mục 2.10: Luồng 10 - Đánh Giá Gia Sư Độc Bản, Ma Trận Phân Quyền 72 Ô & Kiểm Toán Admin (AC-16, AC-17, AC-18)](#mục-210-luồng-10---đánh-giá-gia-sư-độc-bản-ma-trận-phân-quyền-72-ô--kiểm-toán-admin-ac-16-ac-17-ac-18)
- [PHẦN III: HƯỚNG DẪN THỰC THI KIỂM CHỨNG TRỰC TIẾP TRÊN MÁY TÍNH](#phần-iii-hướng-dẫn-thực-thi-kiểm-chứng-trực-tiếp-trên-máy-tính)
  - [Mục 3.1: Chạy Kiểm Thử Backend & Quan Sát Docker PostgreSQL 17](#mục-31-chạy-kiểm-thử-backend--quan-sát-docker-postgresql-17)
  - [Mục 3.2: Chạy Playwright Trình Duyệt Bật Trực Quan (--headed / --slowmo)](#mục-32-chạy-playwright-trình-duyệt-bật-trực-quan---headed----slowmo)
  - [Mục 3.3: Báo Cáo Kết Quả, Bằng Chứng Video/Screenshot & File Báo Cáo HTML](#mục-33-báo-cáo-kết-quả-bằng-chứng-videoscreenshot--file-báo-cáo-html)
- [PHẦN IV: BẢNG NHẬT KÝ TIẾN ĐỘ & MA TRẬN ĐỐI CHIẾU 22 TIÊU CHÍ NGHIỆM THU](#phần-iv-bảng-nhật-ký-tiến-độ--ma-trận-đối-chiếu-22-tiêu-chí-nghiệm-thu)

---

## PHẦN I: THIẾT LẬP NỀN TẢNG KIỂM THỬ TÍCH HỢP TOÀN HỆ THỐNG

### Mục 1.1: Bốn Nguyên Tắc Kỹ Thuật Bắt Buộc

1. **Nguyên tắc 1: Kiểm chứng tự động khách quan (Deterministic Machine Verification):**
   - Mọi kịch bản phải được xác nhận bằng máy qua lệnh chạy trả về Exit Code 0.
   - Không dựa vào giả định lý thuyết hoặc xem code bằng mắt.
2. **Nguyên tắc 2: Chống gãy kiểm thử khi giao diện thay đổi (Resilient UI Locators):**
   - Chỉ sử dụng W3C Semantic Locators (`getByRole`, `getByLabel`, `getByPlaceholder`).
   - Tuyệt đối không dùng CSS class dễ vỡ (`.bg-amber-500`, `.p-6`) hoặc XPath tuyệt đối.
3. **Nguyên tắc 3: Mô phỏng người dùng thật 100% (Production User Simulation):**
   - Mọi thao tác đều thực hiện qua nhập bàn phím ảo, chọn file từ ổ đĩa và click nút chính thức.
   - Tuyệt đối không tương tác với các nút tiện ích dev (như nút Quick Login pills).
4. **Nguyên tắc 4: Tuyệt đối không Hardcode trong Kiểm thử (Zero Hardcoding):**
   - Toàn bộ dữ liệu tĩnh nhập tay phía Backend lưu trữ tại `backend/app/src/test/resources/data/*.csv` (`seed-accounts.csv`, `sample-credentials.csv`).
   - Toàn bộ dữ liệu tĩnh nhập tay phía Frontend lưu trữ tại `frontend/e2e/resources/*.json` (`seed-accounts.json`, `test-credentials.json`).
   - Dữ liệu sinh động dùng hàm generator (`generateUniqueTestUser()`) để chống đụng độ CSDL khi chạy lặp lại.

---

### Mục 1.2: Thiết Lập CSDL Docker Testcontainers Chuẩn Hóa (PostgreSQL 17 Noble)

> [!NOTE]
> **Checkpoint:** **ĐÃ HOÀN TẤT** (28/10/2026 - Testcontainers Exit Code 0)
> - Đã chuẩn hóa duy nhất trên `postgres:17` (Ubuntu 24.04 Noble glibc) đồng bộ 100% với `compose.yaml`.
> - Đã thiết lập cấu hình múi giờ IANA `Asia/Ho_Chi_Minh` xử lý triệt để lỗi handshake múi giờ trên môi trường Windows.

- **Vị trí cấu hình:** [`backend/app/src/test/java/vn/edufit/e2e/SystemLifecycleCrossRoleE2ETest.java`](file:///c:/University/Semester%205/SWP391/Edufit/EduFit/backend/app/src/test/java/vn/edufit/e2e/SystemLifecycleCrossRoleE2ETest.java)
- **Cấu hình container chuẩn trong Java 21:**
```java
static {
  System.setProperty("user.timezone", "Asia/Ho_Chi_Minh");
  java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
}

@Container
static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
    .withDatabaseName("edufit_e2e")
    .withUsername("edufit")
    .withPassword("secret")
    .withEnv("TZ", "Asia/Ho_Chi_Minh");
```

---

### Mục 1.3: Cài Đặt & Cấu Hình Playwright Headed Runner & WebServer

> [!NOTE]
> **Checkpoint:** **ĐÃ HOÀN TẤT** (28/10/2026 - Exit Code 0)
> - Đã cài đặt `@playwright/test` và binary Chromium headless v1248, FFmpeg v1013.
> - Đã cấu hình `webServer` tự động khởi động Next.js trong [`frontend/playwright.config.ts`](file:///c:/University/Semester%205/SWP391/Edufit/EduFit/frontend/playwright.config.ts).
> - Đã bổ sung script `"test:e2e": "playwright test"` trong [`frontend/package.json`](file:///c:/University/Semester%205/SWP391/Edufit/EduFit/frontend/package.json).

- **Thư mục làm việc:** `frontend/`
- **Cấu hình `frontend/playwright.config.ts`:**
  - `baseURL: 'http://localhost:3000'`
  - Hỗ trợ cả chế độ headless và headed (`--headed`).
  - Tự động bật Next.js server qua khối `webServer`:
    ```typescript
    webServer: {
      command: 'npm run dev',
      url: 'http://localhost:3000',
      reuseExistingServer: true,
      timeout: 120 * 1000,
    }
    ```

---

### Mục 1.4: Kiến Trúc Page Object Model (POM) Bền Vững & Chống Gãy UI

> [!NOTE]
> **Checkpoint:** **ĐÃ HOÀN TẤT** (28/10/2026 - TypeScript Compile Exit Code 0)
> - Đã xây dựng 4 Page Objects tại [`frontend/e2e/pages/`](file:///c:/University/Semester%205/SWP391/Edufit/EduFit/frontend/e2e/pages/):
>   - [`BasePage.ts`](file:///c:/University/Semester%205/SWP391/Edufit/EduFit/frontend/e2e/pages/BasePage.ts): Quản lý Modal, Toast, chờ URL.
>   - [`AuthPage.ts`](file:///c:/University/Semester%205/SWP391/Edufit/EduFit/frontend/e2e/pages/AuthPage.ts): Đóng gói form đăng ký, đăng nhập chuẩn (100% người dùng thật).
>   - [`SchedulePage.ts`](file:///c:/University/Semester%205/SWP391/Edufit/EduFit/frontend/e2e/pages/SchedulePage.ts): Đóng gói đề xuất lịch học, đổi lịch, điểm danh.
>   - [`PaymentPage.ts`](file:///c:/University/Semester%205/SWP391/Edufit/EduFit/frontend/e2e/pages/PaymentPage.ts): Đóng gói nạp tiền ví QR, bảng rút tiền gia sư, giải quyết tranh chấp.

---

## PHẦN II: 10 LUỒNG HÀNH TRÌNH E2E TOÀN DIỆN (FULL-STACK UNIFIED JOURNEYS)

---

### Mục 2.1: Luồng 01 - Định Danh, Phiên Đăng Nhập, Argon2id & Khóa Brute-Force (AC-01, AC-02)

> [!NOTE]
> **Checkpoint:** **ĐÃ HOÀN TẤT** (28/10/2026 - 2/2 Tests Passed Exit Code 0)
> - **Backend Test:** Đã kiểm chứng `ac01_statefulSessionAndLockout` và `ac02_passwordResetAndSessionInvalidation` trên Docker `postgres:17`.
> - **UI Test:** Đã tạo kịch bản [`frontend/e2e/specs/01-auth-flow.spec.ts`](file:///c:/University/Semester%205/SWP391/Edufit/EduFit/frontend/e2e/specs/01-auth-flow.spec.ts) bao gồm 5 kịch bản (P01 đến P05) đã kiểm chứng tự động 5/5 Passed Exit Code 0.
> - **Bug Fix giá trị cao:** Khắc phục lỗi Spring `@Transactional` rollback trong `AuthenticateAccountService` bằng `noRollbackFor = InvalidOperationException.class` để bảo đảm số lần đăng nhập sai `failed_attempts` được ghi nhận vào CSDL.

#### 1. Mục tiêu & Nghiệp vụ (UC1.1 – UC1.4, BR-01..05):
- Xác thực mật khẩu băm chuẩn Argon2id an toàn.
- Cấp phiên làm việc có trạng thái qua Cookie `EDUFIT_SESSION` (`HttpOnly`, `SameSite=Lax`).
- Cơ chế phòng thủ Brute-force: Sai mật khẩu 5 lần liên tiếp khóa tài khoản 15 phút.
- Quên mật khẩu: Gửi token reset qua email, cập nhật mật khẩu mới và lập tức hủy bỏ mọi phiên đăng nhập cũ (`Session Invalidation`).

#### 2. Kịch bản Giao diện Người dùng Thật (Playwright UI E2E):
- **Bước 2.1.1 (P01):** Mở trang `/login`, xác nhận các trường `Email tài khoản`, `Mật khẩu`, nút `Đăng nhập ngay`, liên kết Quên mật khẩu & Đăng ký miễn phí.
- **Bước 2.1.2 (P02):** Nhập email sai định dạng $\rightarrow$ Kiểm tra form hiển thị cảnh báo validation phía client, ngăn chặn submit và giữ nguyên URL `/login`.
- **Bước 2.1.3 (P03):** Người dùng từ trang đăng nhập bấm chuyển sang trang `/register` $\rightarrow$ Kiểm tra hiển thị đầy đủ 3 vai trò (Học sinh, Gia sư, Phụ huynh) $\rightarrow$ Tự gõ họ tên, email, mật khẩu chuẩn và kiểm tra sự hiện diện của nút "Tạo tài khoản EduFit".
- **Bước 2.1.4 (P04):** Bấm vào liên kết "Quên mật khẩu?" $\rightarrow$ Điều hướng sang `/forgot-password` $\rightarrow$ Nhập email tài khoản cần khôi phục $\rightarrow$ Bấm nút "Gửi liên kết đặt lại mật khẩu" $\rightarrow$ Màn hình hiển thị thông báo "Kiểm tra hộp thư đến của bạn" kèm địa chỉ email $\rightarrow$ Bấm nút "Quay lại Đăng nhập".
- **Bước 2.1.5 (P05):** Mô phỏng người dùng nhập sai mật khẩu thật trên form đăng nhập $\rightarrow$ Form bôi đỏ và hiển thị thông báo lỗi từ hệ thống.

#### 3. Kịch bản Thẩm định Chiều sâu Logic & CSDL (Backend Verification):
- Gửi `POST /api/v1/auth/login` với body hợp lệ $\rightarrow$ Trả về `200 OK` kèm header `Set-Cookie: EDUFIT_SESSION=...`.
- Dùng session gọi `GET /api/v1/auth/me` $\rightarrow$ Trả về đúng `userId`, `email`, `role`.
- Đăng nhập sai lần 1-4 $\rightarrow$ Trả về `401 Unauthorized` (`INVALID_CREDENTIALS`), số lần sai tăng dần trong bảng `account`.
- Đăng nhập sai lần 5 $\rightarrow$ Trả về `423 Locked` (`ACCOUNT_LOCKED`), cột `locked_until` trong bảng `account` được gán mốc $now + 15$ phút.
- Yêu cầu quên mật khẩu `POST /api/v1/auth/forgot-password` $\rightarrow$ Đặt lại mật khẩu mới `POST /api/v1/auth/reset-password` $\rightarrow$ Thử dùng session cũ gọi `/api/v1/auth/me` bị chặn `401 Unauthorized`.

---

### Mục 2.2: Luồng 02 - Quét Bằng Cấp Magic Bytes (Apache Tika) & Admin Duyệt KYC (AC-03, AC-04)

> [!NOTE]
> **Checkpoint:** **ĐÃ HOÀN TẤT** (28/10/2026 - Backend JUnit 5 & Playwright Exit Code 0)
> - Đã kiểm chứng phòng thủ tệp giả mạo: Tika phát hiện Magic Bytes không phải PDF và trả mã `INVALID_FILE_TYPE` (HTTP 400).
> - Đã kiểm chứng nộp hồ sơ bằng cấp hợp lệ chuyển trạng thái sang `PENDING_VERIFICATION` (HTTP 201).
> - Đã kiểm chứng Quản trị viên duyệt hồ sơ, chuyển trạng thái gia sư sang `VERIFIED` và cập nhật `verified_at`.
> - Đã kiểm chứng 5 kịch bản UI Playwright (`02-kyc-verification.spec.ts`): Client validation chặn file lạ, gửi hồ sơ, Admin queue 3 tabs và validation bắt buộc lý do khi từ chối hồ sơ (BR-06).

#### 1. Mục tiêu & Nghiệp vụ (UC1.10 – UC1.12, BR-06..10):
- Gia sư nộp bằng cấp/chứng chỉ (PDF, JPEG, PNG).
- Hệ thống phòng thủ mã độc bằng Apache Tika: Phân tích Magic Bytes phần đầu tệp nhị phân, phát hiện và từ chối các file thực thi / shell script cố tình đổi phần mở rộng thành `.pdf`.
- Quản trị viên (Admin) xem xét hàng đợi KYC, kiểm tra tính hợp lệ và phê duyệt / từ chối / thu hồi trạng thái xác minh.

#### 2. Kịch bản Giao diện Người dùng Thật (Playwright UI E2E):
- **Bước 2.2.1:** Gia sư đăng nhập, truy cập `/tutor/profile` $\rightarrow$ Bấm nút "Nộp hồ sơ xác minh bằng cấp".
- **Bước 2.2.2:** Tải lên tệp giả mạo (file `.sh` đổi đuôi thành `fake.pdf`) $\rightarrow$ Giao diện hiển thị Toast lỗi: *"Định dạng file bằng cấp không hợp lệ"*.
- **Bước 2.2.3:** Tải lên tệp PDF chuẩn từ thư mục test, điền trường cấp và năm tốt nghiệp $\rightarrow$ Bấm "Gửi hồ sơ" $\rightarrow$ Giao diện chuyển trạng thái sang huy hiệu "Đang chờ duyệt (PENDING)".
- **Bước 2.2.4:** Đổi sang phiên Admin truy cập `/admin/verifications` $\rightarrow$ Thấy hồ sơ của gia sư xuất hiện trong hàng đợi $\rightarrow$ Bấm nút "Phê duyệt" $\rightarrow$ Hồ sơ biến mất khỏi hàng đợi pending và chuyển sang danh sách đã duyệt.
- **Bước 2.2.5:** Gia sư tải lại trang cá nhân $\rightarrow$ Huy hiệu cập nhật thành "Đã xác minh (VERIFIED)".

#### 3. Kịch bản Thẩm định Chiều sâu Logic & CSDL (Backend Verification):
- Gửi `POST /api/v1/verifications` (Multipart Form) với tệp giả mạo $\rightarrow$ Tika phát hiện vi phạm, trả về `400 Bad Request` với mã `INVALID_FILE_TYPE`.
- Gửi `POST /api/v1/verifications` với tệp PDF chuẩn `%PDF-1.4` $\rightarrow$ Trả về `201 Created`, bản ghi trong bảng `verification_request` có `status = PENDING`.
- Admin gọi `GET /api/v1/admin/verifications/pending` $\rightarrow$ Kiểm tra thấy `requestId`.
- Admin gọi `POST /api/v1/admin/verifications/{id}/review` với `action: APPROVE` $\rightarrow$ Trả về `200 OK`, bảng `tutor_profile` cập nhật `status = VERIFIED`.

---

### Mục 2.3: Luồng 03 - Quản Trị Danh Mục & Thiết Lập Hồ Sơ, Khung Rảnh Tuần (AC-05, AC-06)

> [!NOTE]
> **Checkpoint:** **ĐÃ HOÀN TẤT** (28/10/2026 - 100% Passed: Backend 6/6 tests, Frontend Playwright 5/5 tests, Exit Code 0)
> - **Backend Test:** Đã kiểm chứng `ac05_catalogAndAvailabilitySetup` và `ac06_studentGoalUpdate` trong [`SystemLifecycleCrossRoleE2ETest.java`](file:///c:/University/Semester%205/SWP391/Edufit/EduFit/backend/app/src/test/java/vn/edufit/e2e/SystemLifecycleCrossRoleE2ETest.java) trên Docker `postgres:17` Ubuntu Noble:
>   - Admin tạo môn học mới "Toán Chuyên 9" (`POST /api/v1/admin/catalogs/subjects`).
>   - Thử xóa cứng môn học bị chặn `400 Bad Request` (`INVALID_OPERATION`) bảo vệ khóa ngoại CSDL.
>   - Gia sư cập nhật hồ sơ (`PUT /api/v1/profiles/tutors/me`), gán môn (`POST /api/v1/profiles/tutors/me/subjects`), thiết lập khung rảnh tuần (`PUT /api/v1/profiles/tutors/me/availability-slots`).
>   - Học sinh cập nhật khối lớp & khu vực (`PUT /api/v1/profiles/students/me`), tạo mục tiêu học tập (`POST /api/v1/profiles/students/me/goals`).
> - **UI Test:** Đã tạo kịch bản [`frontend/e2e/specs/03-catalog-profile.spec.ts`](file:///c:/University/Semester%205/SWP391/Edufit/EduFit/frontend/e2e/specs/03-catalog-profile.spec.ts) bao gồm 5 kịch bản (P01 đến P05) chạy mượt mà 5/5 Passed Exit Code 0 (7.1s).
> - **Tuân thủ Quy tắc 9:** Dữ liệu tĩnh Backend nạp từ `sample-catalogs.csv`, `sample-tutor-profile.csv`, `sample-student-goal.csv`; dữ liệu tĩnh Frontend nạp từ `sample-catalogs.json`, `sample-profiles.json`. Tuyệt đối không hardcode.
> - **Cải tiến W3C a11y:** Bổ sung ARIA role (`role="status"` / `role="alert"`) và `aria-live="polite"` cho component [`Toast.tsx`](file:///c:/University/Semester%205/SWP391/Edufit/EduFit/frontend/src/shared/ui/Toast.tsx).

#### 1. Mục tiêu & Nghiệp vụ (UC1.5 – UC1.9, BR-11..15):
- Admin quản lý danh mục môn học (`Subject`) và cấp lớp (`GradeLevel`).
- Gia sư cập nhật tiểu sử, học phí theo giờ, gán môn giảng dạy và thiết lập lịch rảnh tuần (Availability Slots).
- Học sinh cập nhật mục tiêu học tập (Learning Goal) và khu vực.

#### 2. Kịch bản Giao diện Người dùng Thật (Playwright UI E2E):
- **Bước 2.3.1:** Admin truy cập `/admin/catalogs` $\rightarrow$ Thêm môn mới "Toán Chuyên 9" $\rightarrow$ Môn học hiển thị ngay trong bảng danh mục.
- **Bước 2.3.2:** Gia sư vào trang hồ sơ $\rightarrow$ Chọn môn "Toán Chuyên 9", đặt mức giá 200,000 VND/giờ.
- **Bước 2.3.3:** Gia sư mở bảng lịch rảnh tuần $\rightarrow$ Chọn khung Thứ 2 & Thứ 4 (18:00 - 20:00) $\rightarrow$ Bấm lưu $\rightarrow$ Toast báo lưu thành công.
- **Bước 2.3.4:** Học sinh vào trang cá nhân $\rightarrow$ Nhập mục tiêu: *"Ôn thi vào lớp 10 chuyên Toán"* $\rightarrow$ Lưu thành công.

#### 3. Kịch bản Thẩm định Chiều sâu Logic & CSDL (Backend Verification):
- Admin gọi `POST /api/v1/admin/catalogs/subjects` $\rightarrow$ Thêm bản ghi vào bảng `subject`.
- Gia sư gọi `PUT /api/v1/profile/tutor/availability` $\rightarrow$ Cập nhật các bản ghi trong `tutor_availability`.
- Học sinh gọi `PUT /api/v1/profile/student/goals` $\rightarrow$ Cập nhật bảng `student_profile`.

---

### Mục 2.4: Luồng 04 - Khám Phá Công Khai, Tìm Kiếm Đa Tiêu Chí & Gợi Ý AI Match (AC-07)

#### 1. Mục tiêu & Nghiệp vụ (UC2.1 – UC2.5, BR-16..20):
- Khách vãng lai (Guest) và học sinh tìm kiếm gia sư theo môn học, khu vực, mức giá, điểm đánh giá.
- Học sinh sử dụng tính năng gợi ý thông minh (AI Match) dựa trên mục tiêu học tập và lịch rảnh tương thích.

#### 2. Kịch bản Giao diện Người dùng Thật (Playwright UI E2E):
- **Bước 2.4.1:** Không đăng nhập, truy cập `/tutors` $\rightarrow$ Chọn bộ lọc môn "Toán" và lọc theo đánh giá từ 4 sao trở lên $\rightarrow$ Danh sách gia sư thỏa mãn điều kiện hiển thị đầy đủ thẻ thông tin.
- **Bước 2.4.2:** Đăng nhập tài khoản Học sinh $\rightarrow$ Bấm nút "Gợi ý AI Match" $\rightarrow$ Modal hiển thị danh sách gia sư kèm điểm tương thích (Match Score) và lý do sư phạm phù hợp.

#### 3. Kịch bản Thẩm định Chiều sâu Logic & CSDL (Backend Verification):
- Guest gọi `GET /api/v1/discovery/tutors?subjectId=...&minRating=4` $\rightarrow$ Kiểm tra danh sách chỉ trả về các gia sư có `status = VERIFIED`.
- Học sinh gọi `POST /api/v1/discovery/ai-match` $\rightarrow$ Trả về payload gồm danh sách gia sư kèm `compatibilityScore` và `rationale`.

---

### Mục 2.5: Luồng 05 - Kết Nối Gia Đình Phụ Huynh - Học Sinh & Rate Limiting (AC-08, AC-09)

#### 1. Mục tiêu & Nghiệp vụ (UC2.6 – UC2.9, BR-21..25):
- Phụ huynh gửi lời mời liên kết tới tài khoản con cái (Học sinh).
- Bảo vệ chống spam: Giới hạn tần suất (Rate Limit) tối đa 10 lời mời / giờ cho mỗi phụ huynh.
- Học sinh duyệt chấp thuận lời mời $\rightarrow$ Trạng thái chuyển `ACTIVE`.
- Hủy liên kết $\rightarrow$ Thu hồi ngay quyền truy cập mục tiêu học tập và tiến độ của học sinh đối với phụ huynh đó.

#### 2. Kịch bản Giao diện Người dùng Thật (Playwright UI E2E):
- **Bước 2.5.1:** Phụ huynh vào `/parent/family` $\rightarrow$ Nhập email học sinh $\rightarrow$ Bấm "Gửi lời mời liên kết" $\rightarrow$ Lời mời hiển thị trong danh sách chờ.
- **Bước 2.5.2:** Học sinh đăng nhập, vào chuông thông báo hoặc `/student/family` $\rightarrow$ Thấy lời mời từ phụ huynh $\rightarrow$ Bấm "Chấp nhận" $\rightarrow$ Huy hiệu chuyển sang "Đã liên kết".
- **Bước 2.5.3:** Học sinh bấm "Hủy liên kết" $\rightarrow$ Xác nhận modal $\rightarrow$ Mối liên kết bị xóa. Phụ huynh tải lại trang không còn thấy thông tin học tập của con.

#### 3. Kịch bản Thẩm định Chiều sâu Logic & CSDL (Backend Verification):
- Phụ huynh gọi `POST /api/v1/connections/parent-student/invite` $\rightarrow$ Tạo bản ghi trong `parent_student_connection` trạng thái `PENDING`.
- Mô phỏng gửi liên tiếp trên 10 request trong 1 giờ $\rightarrow$ Trả về `429 Too Many Requests` với mã `RATE_LIMIT_EXCEEDED`.
- Học sinh gọi `POST /api/v1/connections/parent-student/{id}/accept` $\rightarrow$ Cập nhật trạng thái `ACTIVE`.
- Sau khi hủy liên kết, phụ huynh cố gọi API xem mục tiêu học sinh $\rightarrow$ Trả về `403 Forbidden`.

---

### Mục 2.6: Luồng 06 - Đề Nghị Kèm Cặp Trực Tiếp / Đại Diện & Tạo Lớp Học (AC-10, AC-11)

#### 1. Mục tiêu & Nghiệp vụ (UC3.1 – UC3.4, BR-26..30):
- Học sinh gửi đề nghị học trực tiếp tới gia sư kèm môn học và yêu cầu cụ thể.
- Phụ huynh gửi đề nghị học đại diện thay cho con đã liên kết.
- Gia sư xem xét và chấp nhận đề nghị $\rightarrow$ Tự động khởi tạo lớp học (`Class`) ở trạng thái `ACTIVE`.
- Chống Race Condition: Ngăn chặn xử lý trùng lặp khi 2 luồng cùng chấp thuận 1 đề nghị.

#### 2. Kịch bản Giao diện Người dùng Thật (Playwright UI E2E):
- **Bước 2.6.1:** Học sinh vào trang chi tiết gia sư $\rightarrow$ Bấm "Gửi yêu cầu học" $\rightarrow$ Chọn môn "Toán Chuyên 9", điền ghi chú $\rightarrow$ Submit form.
- **Bước 2.6.2:** Gia sư đăng nhập, vào `/tutor/requests` $\rightarrow$ Thấy yêu cầu mới từ học sinh $\rightarrow$ Bấm nút "Chấp nhận đề nghị".
- **Bước 2.6.3:** Hệ thống tự động chuyển hướng sang danh sách lớp học `/tutor/classes` $\rightarrow$ Lớp học mới xuất hiện ở trạng thái "Đang hoạt động".

#### 3. Kịch bản Thẩm định Chiều sâu Logic & CSDL (Backend Verification):
- Học sinh gọi `POST /api/v1/connections/requests` $\rightarrow$ Bản ghi tạo trong `connection_request` trạng thái `PENDING`.
- Gia sư gọi `POST /api/v1/connections/requests/{id}/respond` với `action: ACCEPT` $\rightarrow$ Bản ghi trong bảng `class` được tạo với `status = ACTIVE`.
- Mô phỏng 2 request đồng thời chấp nhận cùng 1 request ID $\rightarrow$ 1 request thành công `200 OK`, request thứ hai bị chặn bằng lỗi xung đột `409 Conflict`, không tạo 2 lớp học trùng.

---

### Mục 2.7: Luồng 07 - Lập Lịch Học, Khóa Chống Trùng Giờ GiST & Batch All-or-Nothing (AC-12, AC-13)

#### 1. Mục tiêu & Nghiệp vụ (UC4.1 – UC4.5, BR-31..38):
- Gia sư hoặc học sinh đề xuất buổi học đơn lẻ.
- Khóa chống trùng lịch tuyệt đối ở tầng CSDL: PostgreSQL GiST Exclusion Constraint ngăn chặn triệt để 2 buổi học có khung giờ giao nhau cho cùng một gia sư hoặc học sinh.
- Lập lịch hàng loạt (Batch Scheduling): Đề xuất một chuỗi 8 buổi học trong tháng theo nguyên tắc nguyên tử giao dịch (All-or-Nothing) - nếu 1 buổi bị trùng giờ, toàn bộ chuỗi bị rollback, không tạo dữ liệu rác.

#### 2. Kịch bản Giao diện Người dùng Thật (Playwright UI E2E):
- **Bước 2.7.1:** Gia sư vào chi tiết lớp học $\rightarrow$ Bấm "Thêm buổi học" $\rightarrow$ Chọn ngày Thứ 6, từ 19:00 đến 21:00 $\rightarrow$ Buổi học hiển thị trên bảng lịch ở trạng thái "Chờ xác nhận".
- **Bước 2.7.2:** Học sinh vào xem lịch học $\rightarrow$ Bấm "Xác nhận buổi học" $\rightarrow$ Buổi học chuyển sang trạng thái "Đã chốt lịch (CONFIRMED)".
- **Bước 2.7.3:** Cố tình thêm một buổi học khác đè lên khung giờ 19:30 - 20:30 $\rightarrow$ Giao diện chặn lại và hiển thị Toast: *"Khung giờ này đã bị trùng với một buổi học khác"*.

#### 3. Kịch bản Thẩm định Chiều sâu Logic & CSDL (Backend Verification):
- Gia sư gửi `POST /api/v1/sessions` $\rightarrow$ Tạo bản ghi trong `session` trạng thái `CONFIRMED`.
- Cố tình gửi request tạo buổi học thứ hai trùng giờ $\rightarrow$ Database kích hoạt GiST exclusion constraint vi phạm `tsrange`, ném lỗi `409 Conflict` với mã `SCHEDULE_OVERLAP`.
- Gửi request batch gồm 8 buổi học, trong đó buổi thứ 5 cố tình trùng giờ $\rightarrow$ Toàn bộ batch bị rollback, đếm số bản ghi trong `session` tăng thêm bằng 0.

---

### Mục 2.8: Luồng 08 - Đổi Lịch Đột Xuất, Hủy Muộn & Vòng Đời Điểm Danh Bất Biến (AC-14, AC-15)

#### 1. Mục tiêu & Nghiệp vụ (UC4.6 – UC4.10, BR-39..45):
- Đổi lịch học đột xuất: Bên đề xuất gửi giờ mới, bên kia chấp thuận $\rightarrow$ Cập nhật thời gian buổi học.
- Hủy buổi học:
  - Hủy trước 24 giờ: Hủy bình thường (`isLateCancel = false`).
  - Hủy sát giờ (dưới 24 giờ): Ghi nhận cờ phạt hủy muộn (`isLateCancel = true`).
- Điểm danh & Biên bản học tập bất biến:
  - Gia sư điểm danh (`ATTENDED` / `ABSENT`), nhập nội dung giảng dạy và bài tập về nhà $\rightarrow$ Buổi học chuyển sang `COMPLETED`.
  - Biên bản buổi học đã hoàn thành trở thành bất biến (không thể sửa đổi hay xóa).
  - Tự động cập nhật công thức tính tiến độ học tập có trọng số của học sinh.

#### 2. Kịch bản Giao diện Người dùng Thật (Playwright UI E2E):
- **Bước 2.8.1:** Học sinh bấm "Yêu cầu đổi lịch" cho buổi học $\rightarrow$ Chọn giờ mới $\rightarrow$ Gia sư thấy thông báo và bấm "Đồng ý đổi lịch".
- **Bước 2.8.2:** Thử nghiệm hủy buổi học sát giờ (<24h) $\rightarrow$ Hộp thoại xác nhận cảnh báo phạt hủy muộn $\rightarrow$ Buổi học hiển thị nhãn "Đã hủy muộn".
- **Bước 2.8.3:** Buổi học đến giờ hoàn thành $\rightarrow$ Gia sư mở form điểm danh $\rightarrow$ Chọn "Có mặt", gõ nội dung *"Hình học không gian chương 2"*, giao bài tập $\rightarrow$ Bấm "Hoàn tất điểm danh" $\rightarrow$ Thẻ buổi học chuyển màu xanh `COMPLETED`.

#### 3. Kịch bản Thẩm định Chiều sâu Logic & CSDL (Backend Verification):
- Gửi `POST /api/v1/sessions/{id}/reschedule` $\rightarrow$ Cập nhật khung giờ buổi học.
- Gửi `POST /api/v1/sessions/{id}/cancel` dưới 24h $\rightarrow$ Bản ghi cập nhật `status = CANCELLED` và `is_late_cancel = true`.
- Gia sư gửi `POST /api/v1/sessions/{id}/attendance` $\rightarrow$ Cập nhật `session` thành `COMPLETED`, lưu `attendance_record`.
- Cố tình gọi API sửa nội dung buổi học đã completed $\rightarrow$ Trả về `400 Bad Request` do vi phạm tính bất biến.

---

### Mục 2.9: Luồng 09 - Ví Học Phí, Ký Quỹ Escrow, Quyết Toán Tự Động & Rút Tiền Napas247 (AC-19, AC-20, AC-21, AC-22)

#### 1. Mục tiêu & Nghiệp vụ (PR #35, UC5.1 – UC5.8, BR-46..55):
- Nạp tiền vào Ví học phí qua cổng thanh toán PayOS (QR Checkout chuẩn VietQR).
- Xác thực Webhook bảo mật: Kiểm tra chữ ký số HMAC-SHA256, đảm bảo tính bất biến và idempotent (không cộng tiền 2 lần cho cùng 1 giao dịch).
- Tạm giữ ký quỹ học phí (Escrow Hold): Khi chốt lịch học, tiền học phí tương ứng bị khóa trong ví học sinh/phụ huynh. Nếu số dư khả dụng không đủ, chặn không cho xác nhận lịch học.
- Tự động quyết toán (Settlement): Sau khi buổi học kết thúc 24h mà không có tranh chấp, tiền ký quỹ tự động giải phóng chuyển vào ví thu nhập của gia sư.
- Xử lý khiếu nại tranh chấp (Dispute): Học sinh/Phụ huynh gửi khiếu nại $\rightarrow$ Đóng băng quyết toán $\rightarrow$ Admin phân xử hoàn tiền hoặc thanh toán cho gia sư.
- Rút tiền thu nhập gia sư (Payout): Gia sư cấu hình tài khoản ngân hàng thụ hưởng (Napas247) và gửi lệnh rút tiền.

#### 2. Kịch bản Giao diện Người dùng Thật (Playwright UI E2E):
- **Bước 2.9.1:** Phụ huynh vào trang ví `/parent/wallet` $\rightarrow$ Bấm nút "Nạp tiền học phí" $\rightarrow$ Nhập số tiền 500,000 VND $\rightarrow$ Dialog hiện mã QR VietQR PayOS thật.
- **Bước 2.9.2:** Sau khi thanh toán thành công, số dư ví tự động nhảy số lên 500,000 VND kèm thông báo Toast.
- **Bước 2.9.3:** Lịch học được xác nhận $\rightarrow$ Mục "Số dư tạm giữ (Escrow)" hiển thị số tiền học phí bị khóa, số dư khả dụng giảm tương ứng.
- **Bước 2.9.4:** Gia sư vào trang ví thu nhập `/tutor/wallet` $\rightarrow$ Nhập số tài khoản ngân hàng thụ hưởng (Vietcombank - Napas247) $\rightarrow$ Tạo lệnh rút tiền thu nhập.

#### 3. Kịch bản Thẩm định Chiều sâu Logic & CSDL (Backend Verification):
- Tạo yêu cầu nạp ví `POST /api/v1/payments/topup` $\rightarrow$ Trả về `checkoutUrl` và mã giao dịch.
- Mô phỏng gửi Webhook `POST /api/v1/payments/webhook` kèm chữ ký HMAC-SHA256 hợp lệ $\rightarrow$ Trả về `200 OK`, số dư ví trong bảng `wallet` tăng đúng số tiền.
- Gửi lại Webhook lần 2 với cùng mã giao dịch $\rightarrow$ Hệ thống nhận diện idempotent, bỏ qua xử lý trùng, không cộng tiền lần 2.
- Gọi API chốt lịch khi số dư < học phí $\rightarrow$ Trả về lỗi `400 Bad Request` với mã `INSUFFICIENT_WALLET_BALANCE`.
- Gọi API quyết toán `POST /api/v1/payments/escrow/{id}/settle` $\rightarrow$ Tiền chuyển từ `escrow_deposit` sang số dư khả dụng của ví gia sư.
- Gia sư gọi `POST /api/v1/payments/payout` $\rightarrow$ Tạo bản ghi trong `payout_request` trạng thái `PENDING`.

---

### Mục 2.10: Luồng 10 - Đánh Giá Gia Sư Độc Bản, Ma Trận Phân Quyền 72 Ô & Kiểm Toán Admin (AC-16, AC-17, AC-18)

#### 1. Mục tiêu & Nghiệp vụ (UC6.1 – UC6.4, BR-56..61, NFR-SEC-01..05):
- Đánh giá gia sư độc bản (One-Review-Per-Class): Mỗi học sinh chỉ được đánh giá gia sư 1 lần duy nhất cho mỗi lớp học đã hoàn thành (chấm sao từ 1-5, gắn tag nhanh, viết nhận xét). Tự động cập nhật điểm uy tín trung bình của gia sư.
- Quản trị viên khóa người dùng vi phạm: Admin khóa tài khoản gia sư hoặc học sinh $\rightarrow$ Toàn bộ phiên làm việc của người dùng bị vô hiệu hóa ngay lập tức.
- Nhật ký kiểm toán có cấu trúc (Admin Audit Trail): Mọi thao tác quản trị nhạy cảm của Admin đều được ghi vào bảng kiểm toán.
- Ma trận phân quyền 72 ô (4 vai trò + Guest x 18 endpoints) & Chống IDOR: Ngăn chặn tuyệt đối việc người dùng dùng ID của người khác để xem/sửa dữ liệu riêng tư.

#### 2. Kịch bản Giao diện Người dùng Thật (Playwright UI E2E):
- **Bước 2.10.1:** Học sinh vào chi tiết lớp học đã kết thúc $\rightarrow$ Bấm nút "Đánh giá gia sư" $\rightarrow$ Chọn 5 sao, chọn tag "Nhiệt tình", viết nhận xét $\rightarrow$ Gửi đánh giá $\rightarrow$ Đánh giá xuất hiện trên trang cá nhân công khai của gia sư.
- **Bước 2.10.2:** Thử bấm nút gửi đánh giá lần 2 $\rightarrow$ Nút đánh giá bị vô hiệu hóa hoặc hệ thống báo *"Bạn đã đánh giá lớp học này rồi"*.
- **Bước 2.10.3:** Admin vào `/admin/users` $\rightarrow$ Tìm tài khoản vi phạm $\rightarrow$ Bấm nút "Khóa tài khoản" kèm lý do $\rightarrow$ Tài khoản đó bị đá văng ra khỏi hệ thống ngay lập tức nếu đang đăng nhập.

#### 3. Kịch bản Thẩm định Chiều sâu Logic & CSDL (Backend Verification):
- Học sinh gửi `POST /api/v1/reviews` $\rightarrow$ Tạo bản ghi trong `tutor_review`, cập nhật `average_rating` trong `tutor_profile`.
- Gửi lại request đánh giá lần 2 cho cùng lớp học $\rightarrow$ Trả về `409 Conflict` với mã `REVIEW_ALREADY_EXISTS`.
- Admin gọi `POST /api/v1/admin/users/{id}/lock` $\rightarrow$ Cập nhật `account.status = LOCKED`.
- Thực thi ma trận kiểm thử 72 ô kiểm tra tính phân quyền: Học sinh gọi API duyệt KYC $\rightarrow$ `403 Forbidden`; Gia sư xem ví học sinh khác $\rightarrow$ `403 Forbidden` / `404 Not Found` (Chống IDOR).

---

## PHẦN III: HƯỚNG DẪN THỰC THI KIỂM CHỨNG TRỰC TIẾP TRÊN MÁY TÍNH

Phần này hướng dẫn các câu lệnh để bất kỳ ai cũng có thể tự tay chạy, trực tiếp quan sát bằng mắt và kiểm chứng tính tự động chính xác trên máy tính:

---

### Mục 3.1: Chạy Kiểm Thử Backend & Quan Sát Docker PostgreSQL 17

Mở cửa sổ **PowerShell** và chạy lệnh:
```powershell
cd "c:\University\Semester 5\SWP391\Edufit\EduFit\backend"
.\mvnw.cmd test -pl app -Dtest=SystemLifecycleCrossRoleE2ETest
```

#### Những gì bạn sẽ thấy:
1. Mở Docker Desktop $\rightarrow$ Thấy container `postgres:17` tự bật lên.
2. Console in ra log Spring Boot, Flyway migration tạo các bảng CSDL thật.
3. Kết thúc in ra: `BUILD SUCCESS` kèm số lượng test passed.

---

### Mục 3.2: Chạy Playwright Trình Duyệt Bật Trực Quan (--headed / --slowmo)

Mở cửa sổ **PowerShell** thứ hai và chạy lệnh:
```powershell
cd "c:\University\Semester 5\SWP391\Edufit\EduFit\frontend"
npx playwright test --headed --slowmo=800
```

#### Những gì bạn sẽ thấy:
1. Cửa sổ trình duyệt **Chromium thật tự động bật lên trên màn hình**.
2. Con trỏ tự động gõ từng chữ cái email, mật khẩu vào ô input.
3. Nút bấm tự động được click, các thông báo lỗi và thành công tự động phản hồi trước mắt bạn mà không cần chạm vào chuột hay bàn phím.

---

### Mục 3.3: Báo Cáo Kết Quả, Bằng Chứng Video/Screenshot & File Báo Cáo HTML

1. **Xem báo cáo HTML của Playwright:**
   ```powershell
   cd frontend
   npx playwright show-report
   ```
2. **Xem video quay lại thao tác trình duyệt khi gặp lỗi:**
   - Thư mục lưu video: `frontend/test-results/` (định dạng `.webm`).
3. **Xem báo cáo độ phủ Backend (JaCoCo):**
   - Mở file trên trình duyệt: [`backend/app/target/site/jacoco/index.html`](file:///c:/University/Semester%205/SWP391/Edufit/EduFit/backend/app/target/site/jacoco/index.html).

---

## PHẦN IV: BẢNG NHẬT KÝ TIẾN ĐỘ & MA TRẬN ĐỐI CHIẾU 22 TIÊU CHÍ NGHIỆM THU

| Mã tiêu chí | Tên tiêu chí nghiệm thu | Phương thức kiểm chứng máy tự động | Trạng thái |
|:---:|---|---|:---:|
| **AC-01** | Đăng nhập cấp Session Cookie, Argon2id & Khóa Brute-force 5 lần | `SystemLifecycleCrossRoleE2ETest#ac01_statefulSessionAndLockout` | **HOÀN TẤT** |
| **AC-02** | Quên mật khẩu, reset token & Hủy bỏ phiên đăng nhập cũ (Session Invalidation) | `SystemLifecycleCrossRoleE2ETest#ac02_passwordResetAndSessionInvalidation` | **HOÀN TẤT** |
| **AC-03** | Quét Magic Bytes (Apache Tika), chặn shell script & Nộp bằng cấp PDF chuẩn | `SystemLifecycleCrossRoleE2ETest#ac03_magicBytesInspectionAndKycSubmission` | **HOÀN TẤT** |
| **AC-04** | Admin duyệt KYC chuyển trạng thái VERIFIED & Thu hồi hồ sơ | `SystemLifecycleCrossRoleE2ETest#ac04_adminKycReviewAndProfileVerification` | **HOÀN TẤT** |
| **AC-05** | Admin thêm môn học/cấp lớp & Gia sư cấu hình khung rảnh tuần (Availability) | `SystemLifecycleCrossRoleE2ETest#ac05_catalogAndAvailabilitySetup` | *Chờ chạy* |
| **AC-06** | Học sinh cập nhật mục tiêu học tập (Learning Goal) & Khối lớp | `SystemLifecycleCrossRoleE2ETest#ac06_studentGoalUpdate` | *Chờ chạy* |
| **AC-07** | Tìm kiếm đa tiêu chí & Gợi ý AI Match tính điểm tương thích (Match Score) | `SystemLifecycleCrossRoleE2ETest#ac07_publicSearchAndAiMatch` | *Chờ chạy* |
| **AC-08** | Lời mời liên kết Phụ huynh - Con cái & Giới hạn Rate Limit 10 lời mời/giờ | `SystemLifecycleCrossRoleE2ETest#ac08_familyInvitationAndRateLimit` | *Chờ chạy* |
| **AC-09** | Học sinh chấp nhận lời mời & Hủy liên kết thu hồi quyền xem mục tiêu | `SystemLifecycleCrossRoleE2ETest#ac09_familyAcceptAndRevokeAccess` | *Chờ chạy* |
| **AC-10** | Đề nghị kèm cặp trực tiếp (Học sinh) & Đề nghị kèm cặp đại diện (Phụ huynh) | `SystemLifecycleCrossRoleE2ETest#ac10_connectionRequestSubmission` | *Chờ chạy* |
| **AC-11** | Chấp nhận đề nghị, khởi tạo lớp học & Chống Race Condition chấp nhận kép | `SystemLifecycleCrossRoleE2ETest#ac11_classCreationAndRaceCondition` | *Chờ chạy* |
| **AC-12** | Đề xuất buổi học đơn lẻ & Khóa chống trùng lịch GiST exclusion constraint | `SystemLifecycleCrossRoleE2ETest#ac12_singleSessionAntiCollision` | *Chờ chạy* |
| **AC-13** | Đề xuất lịch học hàng loạt & Tính nguyên tử giao dịch All-or-Nothing | `SystemLifecycleCrossRoleE2ETest#ac13_batchSchedulingAtomicity` | *Chờ chạy* |
| **AC-14** | Đổi lịch đột xuất, gắn cờ phạt hủy muộn (<24h) & Đổi lịch tuần hoàn | `SystemLifecycleCrossRoleE2ETest#ac14_advancedSchedulingLifecycle` | *Chờ chạy* |
| **AC-15** | Điểm danh buổi học, biên bản bất biến & Công thức tính tiến độ trọng số | `SystemLifecycleCrossRoleE2ETest#ac15_attendanceAndProgressCalculation` | *Chờ chạy* |
| **AC-16** | Đánh giá gia sư độc bản (1-5 sao, quick tags) & Cập nhật điểm uy tín | `SystemLifecycleCrossRoleE2ETest#ac16_tutorReviewAndReputationUpdate` | *Chờ chạy* |
| **AC-17** | Khóa người dùng vi phạm & Nhật ký kiểm toán có cấu trúc của Admin | `SystemLifecycleCrossRoleE2ETest#ac17_adminUserLockAndAuditTrail` | *Chờ chạy* |
| **AC-18** | Ma trận phân quyền 72 ô (4 vai trò + Guest x 18 endpoint) & Chống IDOR | `CrossRoleAuthorizationMatrixTest#ac18_verifyFullRbacAndIdorMatrix` | *Chờ chạy* |
| **AC-19** | Nạp tiền ví học phí qua PayOS QR Checkout & Xác thực Webhook HMAC-SHA256 | `SystemLifecycleCrossRoleE2ETest#ac19_walletTopupAndWebhookVerification` | *Chờ chạy* |
| **AC-20** | Tạm giữ ký quỹ học phí buổi học (Escrow Hold) & Chặn lịch khi thiếu số dư | `SystemLifecycleCrossRoleE2ETest#ac20_sessionEscrowHoldAndRelease` | *Chờ chạy* |
| **AC-21** | Tự động quyết toán học phí sau 24h & Xử lý khiếu nại tranh chấp Admin | `SystemLifecycleCrossRoleE2ETest#ac21_settlementAndDisputeResolution` | *Chờ chạy* |
| **AC-22** | Cấu hình tài khoản thụ hưởng Napas247 & Rút tiền thu nhập gia sư | `SystemLifecycleCrossRoleE2ETest#ac22_tutorPayoutAndWithdrawal` | *Chờ chạy* |

---

### Bảng Nhật Ký Tiến Độ Triển Khai Thực Nghiệm

| Ngày thực hiện | Hạng mục công việc chi tiết | Trạng thái máy | Bằng chứng thực nghiệm |
|:---:|---|:---:|---|
| **28/10/2026** | **Git Setup:** Tạo nhánh `test/system-e2e-verification` từ `develop` | **HOÀN TẤT** | Nhánh sạch, đồng bộ PR #35. |
| **28/10/2026** | **Mục 1.2:** Chuẩn hóa CSDL `postgres:17` Noble & Sửa Timezone | **HOÀN TẤT** | Testcontainers kết nối thành công, không lỗi múi giờ. |
| **28/10/2026** | **Mục 1.3 & 1.4:** Cài đặt Playwright, cấu hình WebServer & Xây dựng 4 Page Objects | **HOÀN TẤT** | `npm run type-check` Exit Code 0, W3C Semantic Locators. |
| **28/10/2026** | **Mục 2.1:** Luồng 01 - Xác thực, Session & Khóa Brute-force (AC-01, AC-02) | **HOÀN TẤT** | 2/2 Tests Passed Exit Code 0 trên Docker PostgreSQL 17 thật; fix bug rollback transaction IAM. Commit `ea5fcb1`. |
| **28/10/2026** | **Mục 2.2:** Luồng 02 - Quét Bằng Cấp Magic Bytes (Apache Tika) & Admin Duyệt KYC (AC-03, AC-04) | **HOÀN TẤT** | 4/4 Backend Tests Passed, 5/5 Playwright Tests Passed (10/10 tests Luồng 01+02 passed in 15.6s). Exit Code 0. |
| *Tiếp theo* | **Mục 2.3:** Luồng 03 - Quản Trị Danh Mục & Thiết Lập Hồ Sơ, Khung Rảnh Tuần (AC-05, AC-06) | *Sẵn sàng* | Viết test kiểm chứng Admin Catalog, Tutor Availability, Student Goals. |
