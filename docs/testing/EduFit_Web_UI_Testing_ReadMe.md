# EduFit — Hướng Dẫn & Đặc Tả Kiểm Thử Tự Động Giao Diện Web (Automated Web UI Testing)

> **Hệ thống:** Nền tảng kết nối gia sư & học sinh EduFit (EduFit Tutoring Platform)  
> **Học phần:** SWP391 / SWT301 · **Phạm vi:** **Chỉ tập trung vào Nội dung B. Automated Web UI Testing**  
> **Công nghệ bắt buộc:** Selenium WebDriver 4.x (TypeScript) · Vitest · Page Object Model (POM) · Data-Driven Testing · Google Chrome  
> **Repository:** Sử dụng trực tiếp repository EduFit hiện có (nhánh chuẩn/solution đặt tại worktree `EduFit-system-e2e`)

---

## Mục lục

1. [Giới thiệu & Mục tiêu](#1-giới-thiệu--mục-tiêu)
2. [Kiến trúc & Công nghệ kiểm thử](#2-kiến-trúc--công-nghệ-kiểm-thử)
3. [Cấu trúc thư mục kiểm thử E2E](#3-cấu-trúc-thư-mục-kiểm-thử-e2e)
4. [Môi trường & Hướng dẫn cài đặt / khởi chạy](#4-môi-trường--hướng-dẫn-cài-đặt--khởi-chạy)
5. [Danh mục 10 Luồng kiểm thử & Đặc tả Test Case](#5-danh-mục-10-luồng-kiểm-thử--đặc-tả-test-case)
6. [Bảng phân công nhiệm vụ 5 thành viên](#6-bảng-phân-công-nhiệm-vụ-5-thành-viên)
7. [Quy ước chụp ảnh bằng chứng (Evidence Screenshots)](#7-quy-ước-chụp-ảnh-bằng-chứng-evidence-screenshots)
8. [Tiêu chí nghiệm thu & Thang điểm đánh giá](#8-tiêu-chí-nghiệm-thu--thang-điểm-đánh-giá)

---

## 1. Giới thiệu & Mục tiêu

### 1.1 Giới thiệu
**EduFit** là nền tảng công nghệ giáo dục kết nối trực tiếp gia sư, học sinh và phụ huynh. Để đảm bảo tính tin cậy tuyệt đối của giao diện người dùng thực tế trước khi nghiệm thu, đội ngũ kiểm thử triển khai bộ kiểm thử tự động trên trình duyệt thật (**Automated Web UI Testing — Lớp B**).

Bộ kiểm thử này mô phỏng hành vi của người dùng thật: từ thao tác nhập liệu, nhấn nút, điều hướng trang, tải file chứng chỉ đến kiểm tra các cảnh báo lỗi nghiệp vụ, cập nhật trạng thái đơn/lịch và hiển thị báo cáo.

### 1.2 Mục tiêu học phần & kỹ thuật
- **Tự động hóa hoàn toàn trên Chrome thật:** Không dùng mock UI hay giả lập dữ liệu ảo tại tầng giao diện. Trình duyệt mở thật, tương tác qua WebDriver.
- **Áp dụng mẫu thiết kế Page Object Model (POM):** Phân tách hoàn toàn giữa định vị phần tử (locators), hành động trên trang (page actions) và kịch bản kiểm thử (test specs).
- **Kiểm chứng tự động có tính lặp lại (Deterministic Verification):** Mỗi ca kiểm thử kết thúc bằng assertion rõ ràng và tự động chụp ảnh màn hình làm bằng chứng nghiệm thu (`reports/screenshots/`).
- **Phân công làm việc nhóm 5 người song song:** 5 thành viên cùng phát triển trên cùng repository có sẵn, chia việc độc lập theo Page Objects và Test Specs mà không gây xung đột mã nguồn.

---

## 2. Kiến trúc & Công nghệ kiểm thử

| Thành phần | Công nghệ / Thư viện | Vai trò |
|---|---|---|
| **Test Runner** | Vitest (`v2.x+`) | Quản lý vòng đời test suite (`describe`, `it`, `beforeAll`, `afterAll`), assertions (`expect`), thực thi tuần tự (`fileParallelism: false`). |
| **Automation Engine** | Selenium WebDriver 4.x (`selenium-webdriver`) | Điều khiển trình duyệt Google Chrome thông qua W3C WebDriver Protocol. |
| **Driver Management** | Selenium Manager (tích hợp sẵn trong Selenium 4.6+) | Tự động tải và đồng bộ phiên bản `chromedriver` phù hợp với Chrome trên máy. |
| **Design Pattern** | Page Object Model (POM) | Đóng gói DOM selectors và thao tác người dùng vào các lớp Page chuyên trách. |
| **Ngôn ngữ** | TypeScript 5.x | Đảm bảo tính chặt chẽ về kiểu dữ liệu (Type-Safe), autocomplete và giảm lỗi cú pháp. |
| **Dữ liệu kiểm thử** | JSON Data Fixtures + Generator | Lưu trữ tài khoản seed (`login-data.json`), file PDF mẫu (`resources/files/`), tránh hardcode giá trị ma thuật. |

---

## 3. Cấu trúc thư mục kiểm thử E2E

Toàn bộ mã nguồn kiểm thử tự động giao diện Web UI được tổ chức tập trung tại thư mục `frontend/tests/e2e/` của repository:

```
frontend/tests/e2e/
├── config/
│   └── selenium.config.ts          # Cấu hình baseUrl, browser, timeouts, headless
├── drivers/
│   └── driver-factory.ts           # Factory khởi tạo và tinh chỉnh Chrome WebDriver
├── pages/                          # Các lớp Page Object Model (POM)
│   ├── BasePage.ts                 # Lớp cơ sở: explicit wait, screenshot, URL check
│   ├── AuthPage.ts                 # Đăng nhập, đăng ký, quên mật khẩu
│   ├── KycPage.ts                  # Nộp bằng cấp, hàng đợi duyệt KYC của Admin
│   ├── CatalogPage.ts              # Quản lý danh mục môn học (Admin)
│   ├── ProfilePage.ts              # Hồ sơ gia sư & nhu cầu học của học sinh
│   ├── TutorsPage.ts               # Khám phá gia sư, bộ lọc & AI Match
│   ├── FamilyPage.ts               # Quản lý liên kết gia đình phụ huynh - học sinh
│   ├── ClassConnectionPage.ts      # Yêu cầu học & danh sách lớp học 2 phía
│   ├── SchedulePage.ts             # Lịch học, khung rảnh & điểm danh buổi học
│   ├── PaymentPage.ts              # Cổng nạp tiền (VietQR) & rút thu nhập gia sư
│   └── AdminManagementPage.ts      # Dashboard Admin, quản lý học viên, audit logs
├── reports/
│   └── screenshots/                # Nơi lưu trữ tự động toàn bộ ảnh bằng chứng (.png)
├── resources/                      # Dữ liệu kiểm thử tập trung (No hardcoding)
│   ├── files/
│   │   ├── valid-certificate.pdf   # File PDF hợp lệ nộp KYC
│   │   └── fake-script.pdf         # File script giả mạo đuôi PDF kiểm tra bảo mật
│   └── login-data.json             # Danh sách tài khoản seed & bộ dữ liệu kiểm thử
├── specs/                          # 10 Kịch bản kiểm thử tương ứng 10 Luồng
│   ├── 01-auth-flow.e2e.test.ts
│   ├── 02-kyc-flow.e2e.test.ts
│   ├── 03-catalog-profile-flow.e2e.test.ts
│   ├── 04-tutors-discovery-flow.e2e.test.ts
│   ├── 05-family-link-flow.e2e.test.ts
│   ├── 06-connection-classes-flow.e2e.test.ts
│   ├── 07-schedule-attendance-flow.e2e.test.ts
│   ├── 08-billing-payout-flow.e2e.test.ts
│   ├── 09-review-flow.e2e.test.ts
│   └── 10-admin-management-flow.e2e.test.ts
└── vitest.e2e.config.ts            # Cấu hình test runner Vitest chuyên biệt cho E2E
```

---

## 4. Môi trường & Hướng dẫn cài đặt / khởi chạy

### 4.1 Yêu cầu tiên quyết (Prerequisites)
1. **Node.js:** Phiên bản `>= 20.x` (LTS khuyến nghị).
2. **Google Chrome:** Phiên bản ổn định mới nhất.
3. **Docker Desktop:** Đang chạy để nạp container CSDL PostgreSQL 17.
4. **Java JDK 21:** Dùng để chạy Spring Boot backend.

### 4.2 Thiết lập hệ thống chạy thử nghiệm

Mở 3 terminal riêng biệt để chạy 3 tầng của hệ thống EduFit:

#### Terminal 1 — Khởi động CSDL & Backend API
```powershell
# Di chuyển vào thư mục backend
cd "backend"

# Khởi chạy ứng dụng Spring Boot (tự động kích hoạt Testcontainers hoặc Docker Postgres)
.\mvnw.cmd spring-boot:run
```
> Chờ terminal thông báo: `Started EduFitApplication in ... seconds` tại port `8080`.

#### Terminal 2 — Khởi động Giao diện Frontend Next.js
```powershell
# Di chuyển vào thư mục frontend
cd "frontend"

# Cài đặt dependencies (nếu chưa cài)
npm install

# Khởi chạy Next.js dev server
npm run dev
```
> Chờ terminal thông báo sẵn sàng tại `http://localhost:3000`.

#### Terminal 3 — Thực thi bộ kiểm thử Web UI E2E
```powershell
cd "frontend"

# Chạy ở chế độ TRỰC QUAN (bật cửa sổ Chrome thật để quan sát thao tác tự động):
$env:E2E_HEADLESS="false"
npm run test:e2e

# Hoặc chạy ở chế độ HEADLESS (chạy ngầm phục vụ CI/CD):
$env:E2E_HEADLESS="true"
npm run test:e2e

# Chạy một kịch bản đơn lẻ (ví dụ Luồng 01):
npx vitest run tests/e2e/specs/01-auth-flow.e2e.test.ts --config tests/e2e/vitest.e2e.config.ts
```

---

## 5. Danh mục 10 Luồng kiểm thử & Đặc tả Test Case

Tất cả các route đã được đối chiếu chuẩn xác với cấu trúc định tuyến thực tế tại `frontend/src/app/`.

### Luồng 01 — Xác thực, Đăng nhập đa vai trò & Phân quyền RBAC
*File spec:* `01-auth-flow.e2e.test.ts` · *Page Object:* `AuthPage.ts`

| Mã TC | Tên ca kiểm thử | Route | Dữ liệu vào | Thao tác chính | Kết quả mong đợi | Ảnh chụp bằng chứng |
|---|---|---|---|---|---|---|
| **TC_UI_01** | Form Đăng nhập hiển thị đủ thành phần | `/login` | — | Truy cập `/login` | Email input, Password input, nút Đăng nhập hiển thị đầy đủ | `TC_UI_01_navigate_login` |
| **TC_UI_02** | Điều hướng Đăng ký & chọn vai trò | `/login` → `/register` | Vai trò "Gia sư" | Bấm link đăng ký, chọn vai trò | URL chuyển sang `/register`, form hiển thị, chọn vai trò thành công | `TC_UI_02_register_role_selected` |
| **TC_UI_03** | Điều hướng Quên mật khẩu & gửi yêu cầu | `/login` → `/forgot-password` | `student@edufit.vn` | Nhập email, bấm gửi | Thông báo hướng dẫn khôi phục hiển thị | `TC_UI_03_forgot_password_submitted` |
| **TC_UI_04** | Đăng nhập sai mật khẩu hiện cảnh báo | `/login` | `wronguser@edufit.vn` / `WrongPass@123` | Nhập tài khoản sai, bấm Đăng nhập | Hiển thị thông báo lỗi màu đỏ, URL vẫn ở `/login` | `TC_UI_04_invalid_credentials_alert` |
| **TC_UI_05** | Validation chặn email sai định dạng | `/login` | `invalid-email-format` | Nhập email sai format, bấm Đăng nhập | HTML5 validation chặn, không gửi request | `TC_UI_05_email_validation` |
| **TC_UI_06** | Admin đăng nhập thành công | `/login` | `admin@edufit.vn` | Nhập tài khoản Admin, Đăng nhập | Chuyển hướng thành công vào `/admin/dashboard` | `TC_UI_06_admin_login` |
| **TC_UI_07** | Gia sư đăng nhập thành công | `/login` | `tutor@edufit.vn` | Nhập tài khoản Gia sư, Đăng nhập | Chuyển hướng thành công vào `/tutor/dashboard` | `TC_UI_07_tutor_login` |
| **TC_UI_08** | Học sinh đăng nhập thành công | `/login` | `student@edufit.vn` | Nhập tài khoản Học sinh, Đăng nhập | Chuyển hướng thành công vào `/student/dashboard` | `TC_UI_08_student_login` |
| **TC_UI_10** | Khách vãng lai bị chặn vào trang Admin | `/admin/dashboard` | Chưa đăng nhập | Mở trực tiếp link Admin khi chưa login | Bị chặn và tự động redirect về trang `/login` | `TC_UI_10_guest_blocked` |
| **TC_UI_11** | Học sinh bị chặn truy cập trang Admin | `/admin/dashboard` | Đăng nhập học sinh | Đăng nhập `student@`, mở link Admin | Bị chặn quyền (403 Forbidden hoặc redirect) | `TC_UI_11_student_blocked` |

---

### Luồng 02 — Quy trình xác minh bằng cấp KYC & Duyệt hồ sơ Admin
*File spec:* `02-kyc-flow.e2e.test.ts` · *Page Object:* `KycPage.ts`

| Mã TC | Tên ca kiểm thử | Route | Dữ liệu vào | Thao tác chính | Kết quả mong đợi | Ảnh chụp bằng chứng |
|---|---|---|---|---|---|---|
| **TC_UI_15** | Chặn tệp bằng cấp giả mạo (Magic Bytes) | `/tutor/verification` | `fake-script.pdf` | Tải lên file script giả đuôi pdf | Giao diện cảnh báo định dạng không hợp lệ | `TC_UI_15_fake_certificate` |
| **TC_UI_16** | Nộp hồ sơ bằng cấp hợp lệ | `/tutor/verification` | `valid-certificate.pdf` | Điền thông tin văn bằng, đính kèm PDF thật | Nộp thành công, trạng thái chuyển sang Chờ duyệt | `TC_UI_16_submit_certificate` |
| **TC_UI_17** | Hồ sơ xuất hiện trong hàng đợi Admin | `/admin/verification-queue` | Tài khoản Admin | Truy cập tab danh sách chờ duyệt | Hồ sơ gia sư vừa nộp xuất hiện trong hàng đợi | `TC_UI_17_queue_has_request` |
| **TC_UI_19** | Từ chối hồ sơ bắt buộc nhập lý do | `/admin/verification-queue` | Bỏ trống lý do | Bấm từ chối nhưng không nhập lý do | Form chặn thao tác, yêu cầu nhập lý do từ chối | `TC_UI_19_reject_requires_reason` |
| **TC_UI_18** | Admin phê duyệt hồ sơ hợp lệ | `/admin/verification-queue` | Bấm "Phê duyệt" | Xác nhận duyệt hồ sơ gia sư | Hồ sơ rời khỏi hàng đợi, trạng thái đã duyệt | `TC_UI_18_admin_approve` |

---

### Luồng 03 — Quản lý danh mục môn học & Thiết lập hồ sơ chuyên môn
*File spec:* `03-catalog-profile-flow.e2e.test.ts` · *Page Objects:* `CatalogPage.ts`, `ProfilePage.ts`

| Mã TC | Tên ca kiểm thử | Route | Dữ liệu vào | Thao tác chính | Kết quả mong đợi | Ảnh chụp bằng chứng |
|---|---|---|---|---|---|---|
| **TC_UI_21** | Admin mở biểu mẫu thêm môn học mới | `/admin/catalogs` | Tài khoản Admin | Bấm "Thêm môn học" | Modal/Form thêm môn học hiển thị đầy đủ | `TC_UI_21_add_subject` |
| **TC_UI_22** | Gia sư cập nhật thông tin hồ sơ | `/tutor/profile` | Tiểu sử, học phí, môn | Cập nhật biểu phí và giới thiệu bản thân | Thông báo lưu hồ sơ thành công, dữ liệu hiển thị đúng | `TC_UI_22_tutor_profile` |
| **TC_UI_24** | Học sinh cập nhật nhu cầu học tập | `/student/profile` | Khối lớp, nhu cầu học | Nhập nhu cầu học tập và lưu | Dữ liệu nhu cầu học tập được lưu và cập nhật | `TC_UI_24_student_profile` |

---

### Luồng 04 — Khám phá gia sư công khai & Gợi ý AI Match
*File spec:* `04-tutors-discovery-flow.e2e.test.ts` · *Page Object:* `TutorsPage.ts`

| Mã TC | Tên ca kiểm thử | Route | Dữ liệu vào | Thao tác chính | Kết quả mong đợi | Ảnh chụp bằng chứng |
|---|---|---|---|---|---|---|
| **TC_UI_26** | Khách tìm kiếm & lọc danh sách gia sư | `/tutors` | Chưa đăng nhập | Áp dụng bộ lọc môn học | Danh sách gia sư hiển thị thẻ kết quả tìm kiếm | `TC_UI_26_tutor_filter` |
| **TC_UI_28** | Học sinh dùng tính năng gợi ý AI Match | `/student/tutors` | Học sinh đăng nhập | Nhấn nút "Gợi ý AI Match" | Danh sách gia sư tương thích kèm điểm số và phân tích | `TC_UI_28_ai_match` |

---

### Luồng 05 — Quản lý liên kết gia đình Phụ huynh & Học sinh
*File spec:* `05-family-link-flow.e2e.test.ts` · *Page Object:* `FamilyPage.ts`

| Mã TC | Tên ca kiểm thử | Route | Dữ liệu vào | Thao tác chính | Kết quả mong đợi | Ảnh chụp bằng chứng |
|---|---|---|---|---|---|---|
| **TC_UI_30** | Phụ huynh gửi lời mời liên kết học sinh | `/parent/children` | `student@edufit.vn` | Nhập email con, bấm gửi lời mời | Lời mời xuất hiện trong danh sách Chờ xác nhận | `TC_UI_30_parent_invite` |
| **TC_UI_31** | Học sinh xem và chấp nhận lời mời | `/student/family` | Học sinh đăng nhập | Mở danh sách lời mời, bấm Chấp nhận | Trạng thái chuyển sang "Đã liên kết" | `TC_UI_31_student_accept` |

---

### Luồng 06 — Đề nghị kèm cặp & Đồng bộ lớp học hai phía
*File spec:* `06-connection-classes-flow.e2e.test.ts` · *Page Object:* `ClassConnectionPage.ts`

| Mã TC | Tên ca kiểm thử | Route | Dữ liệu vào | Thao tác chính | Kết quả mong đợi | Ảnh chụp bằng chứng |
|---|---|---|---|---|---|---|
| **TC_UI_34** | Gia sư xem danh sách yêu cầu học | `/tutor/requests` | Gia sư đăng nhập | Kiểm tra danh sách đề nghị kèm cặp | Danh sách yêu cầu học hiển thị chi tiết học sinh & môn | `TC_UI_34_tutor_accept` |
| **TC_UI_35** | Đồng bộ lớp học trên cả 2 cổng | `/tutor/classes` & `/student/classes` | Gia sư & Học sinh | Kiểm tra danh sách lớp học đang hoạt động | Lớp học hiển thị đồng nhất ở cả 2 tài khoản | `TC_UI_35_class_created` |

---

### Luồng 07 — Lập lịch giảng dạy & Thời khóa biểu buổi học
*File spec:* `07-schedule-attendance-flow.e2e.test.ts` · *Page Object:* `SchedulePage.ts`

| Mã TC | Tên ca kiểm thử | Route | Dữ liệu vào | Thao tác chính | Kết quả mong đợi | Ảnh chụp bằng chứng |
|---|---|---|---|---|---|---|
| **TC_UI_36** | Gia sư mở giao diện lịch làm việc | `/tutor/schedule` | Gia sư đăng nhập | Kiểm tra giao diện lịch tuần/tháng | Khung thời khóa biểu và các slot hiển thị trực quan | `TC_UI_36_add_session` |
| **TC_UI_37** | Học sinh kiểm tra thời khóa biểu học tập | `/student/schedule` | Học sinh đăng nhập | Truy cập xem lịch học cá nhân | Thời khóa biểu học tập hiển thị chính xác các buổi | `TC_UI_37_confirm_session` |

---

### Luồng 08 — Cổng thanh toán nạp tiền & Rút thu nhập gia sư
*File spec:* `08-billing-payout-flow.e2e.test.ts` · *Page Object:* `PaymentPage.ts`

| Mã TC | Tên ca kiểm thử | Route | Dữ liệu vào | Thao tác chính | Kết quả mong đợi | Ảnh chụp bằng chứng |
|---|---|---|---|---|---|---|
| **TC_UI_43** | Phụ huynh mở cổng nạp tiền VietQR | `/parent/billing` | Số tiền nạp test | Nhấn nút "Nạp tiền vào ví" | Hộp thoại hiển thị mã QR thanh toán (không test tiền thật) | `TC_UI_43_topup_qr` |
| **TC_UI_45** | Gia sư mở giao diện quản lý thu nhập & rút tiền | `/tutor/earnings` | Gia sư đăng nhập | Xem số dư ví và form tạo lệnh rút | Hiển thị số dư khả dụng và trường nhập thông tin ngân hàng | `TC_UI_45_payout_request` |

---

### Luồng 09 — Đánh giá gia sư & Quản lý phản hồi học viên
*File spec:* `09-review-flow.e2e.test.ts` · *Page Object:* `TutorsPage.ts` / `SchedulePage.ts`

| Mã TC | Tên ca kiểm thử | Route | Dữ liệu vào | Thao tác chính | Kết quả mong đợi | Ảnh chụp bằng chứng |
|---|---|---|---|---|---|---|
| **TC_UI_50** | Gia sư xem danh sách đánh giá & điểm trung bình | `/tutor/reviews` | Gia sư đăng nhập | Truy cập trang đánh giá | Điểm số trung bình (sao) và danh sách nhận xét hiển thị | `TC_UI_50_tutor_reviews` |

---

### Luồng 10 — Quản trị hệ thống, Tài khoản người học & Nhật ký kiểm toán
*File spec:* `10-admin-management-flow.e2e.test.ts` · *Page Object:* `AdminManagementPage.ts`

| Mã TC | Tên ca kiểm thử | Route | Dữ liệu vào | Thao tác chính | Kết quả mong đợi | Ảnh chụp bằng chứng |
|---|---|---|---|---|---|---|
| **TC_UI_51** | Admin kiểm tra Dashboard tổng quan | `/admin/dashboard` | Admin đăng nhập | Xem các khối chỉ số KPI hệ thống | Các thẻ KPI, biểu đồ thống kê hiển thị số liệu | `TC_UI_51_admin_dashboard` |
| **TC_UI_52** | Admin xem danh sách quản lý người học | `/admin/learners` | Admin đăng nhập | Truy cập trang danh sách học sinh | Bảng danh sách học sinh hiển thị đầy đủ thông tin | `TC_UI_52_lock_account` |
| **TC_UI_54** | Admin kiểm tra nhật ký kiểm toán (Audit Logs) | `/admin/audit-logs` | Admin đăng nhập | Truy cập trang audit logs | Dòng vết hoạt động hệ thống được ghi nhận rõ ràng | `TC_UI_54_audit_log` |

---

## 6. Bảng phân công nhiệm vụ 5 thành viên

Mô hình phân công đảm bảo **không giẫm chân nhau**: mỗi thành viên sở hữu các tệp Page Object và Kịch bản Spec riêng biệt.

| Thành viên | Trách nhiệm chính | Page Objects phụ trách | Kịch bản Spec & Mã TC phụ trách |
|---|---|---|---|
| **Thành viên A (Leader)** | - Quản trị repository & cấu hình test runner<br>- Thiết kế Framework cốt lõi: `DriverFactory`, `BasePage`, config<br>- Luồng Xác thực & Bảo mật phân quyền RBAC | `BasePage.ts`<br>`AuthPage.ts` | **Luồng 01:** `01-auth-flow.e2e.test.ts`<br>(TC_UI_01, 02, 03, 04, 05, 06, 07, 08, 10, 11) |
| **Thành viên B** | - Luồng Xác minh năng lực (KYC) & Duyệt Admin<br>- Luồng Danh mục môn học Admin & Hồ sơ cá nhân | `KycPage.ts`<br>`CatalogPage.ts`<br>`ProfilePage.ts` | **Luồng 02:** `02-kyc-flow.e2e.test.ts`<br>(TC_UI_15, 16, 17, 18, 19)<br>**Luồng 03:** `03-catalog-profile-flow.e2e.test.ts`<br>(TC_UI_21, 22, 24) |
| **Thành viên C** | - Luồng Khám phá gia sư công khai & AI Match<br>- Luồng Liên kết gia đình Phụ huynh & Học sinh | `TutorsPage.ts`<br>`FamilyPage.ts` | **Luồng 04:** `04-tutors-discovery-flow.e2e.test.ts`<br>(TC_UI_26, 28)<br>**Luồng 05:** `05-family-link-flow.e2e.test.ts`<br>(TC_UI_30, 31) |
| **Thành viên D** | - Luồng Yêu cầu kèm cặp & Lớp học 2 phía<br>- Luồng Lập lịch giảng dạy & Thời khóa biểu | `ClassConnectionPage.ts`<br>`SchedulePage.ts` | **Luồng 06:** `06-connection-classes-flow.e2e.test.ts`<br>(TC_UI_34, 35)<br>**Luồng 07:** `07-schedule-attendance-flow.e2e.test.ts`<br>(TC_UI_36, 37) |
| **Thành viên E** | - Luồng Cổng nạp tiền VietQR & Thu nhập gia sư<br>- Luồng Đánh giá nhận xét gia sư<br>- Luồng Quản trị Admin Dashboard & Audit Logs | `PaymentPage.ts`<br>`AdminManagementPage.ts` | **Luồng 08:** `08-billing-payout-flow.e2e.test.ts`<br>(TC_UI_43, 45)<br>**Luồng 09:** `09-review-flow.e2e.test.ts`<br>(TC_UI_50)<br>**Luồng 10:** `10-admin-management-flow.e2e.test.ts`<br>(TC_UI_51, 52, 54) |

---

## 7. Quy ước chụp ảnh bằng chứng (Evidence Screenshots)

Để đảm bảo báo cáo nghiệm thu minh bạch và khách quan, phương thức `takeScreenshot(stepName)` trong `BasePage` tự động chụp ảnh tại mỗi bước kiểm thử quan trọng:

1. **Đường dẫn lưu trữ:** `frontend/tests/e2e/reports/screenshots/`
2. **Quy tắc đặt tên file:**
   ```
   <Mã_Test_Case>_<Tên_Thao_Tác>_<ISO_Timestamp>.png
   ```
   *Ví dụ thực tế đã sinh ra:*
   - `TC_UI_01_navigate_login_2026-11-02T00-54-53-324Z.png`
   - `TC_UI_06_admin_login_2026-11-02T00-55-27-891Z.png`
   - `TC_UI_15_fake_certificate_2026-11-02T00-58-12-140Z.png`
   - `TC_UI_43_parent_billing_2026-11-02T01-03-33-203Z.png`
3. **Kích thước chuẩn trình duyệt:** Cố định `1920 × 1080` (Full HD Desktop) để hình ảnh giao diện sắc nét và không bị ẩn thanh điều hướng.

---

## 8. Tiêu chí nghiệm thu & Thang điểm đánh giá

### 8.1 Definition of Done (DoD) cho mỗi Test Spec
Một kịch bản kiểm thử giao diện chỉ được xem là hoàn tất khi:
- [ ] Áp dụng đúng 100% Page Object Model (không viết locator trực tiếp trong file spec).
- [ ] Lệnh kiểm thử Vitest chạy vượt qua với **Exit Code 0** (`Test Files: 1 passed, Tests: X passed`).
- [ ] Chụp đầy đủ ảnh bằng chứng màn hình với độ phân giải `1920x1080`.
- [ ] Không hardcode tài khoản hoặc selector CSS dễ vỡ (ưu tiên `data-testid`, text chuẩn).
- [ ] Pull Request được tạo đúng chuẩn, CI xanh và có ít nhất 1 thành viên khác duyệt (Approve).

### 8.2 Bảng phân bổ điểm đánh giá học phần (Tham khảo)

| Phần | Nội dung kiểm thử giao diện | Người phụ trách | Điểm |
|---|---|---|:---:|
| **Part 1** | Nền tảng framework Selenium + Luồng 01 (Xác thực & RBAC 10 TCs) | **Thành viên A** | 2.5 |
| **Part 2** | Luồng 02 (KYC & Duyệt Admin) + Luồng 03 (Danh mục & Hồ sơ) | **Thành viên B** | 2.0 |
| **Part 3** | Luồng 04 (Khám phá & AI Match) + Luồng 05 (Liên kết gia đình) | **Thành viên C** | 1.5 |
| **Part 4** | Luồng 06 (Kết nối lớp học) + Luồng 07 (Lập lịch & Thời khóa biểu) | **Thành viên D** | 2.0 |
| **Part 5** | Luồng 08 (Thanh toán ví) + Luồng 09 (Đánh giá) + Luồng 10 (Admin) | **Thành viên E** | 2.0 |
| **Tổng điểm** | **Bộ kiểm thử tự động Web UI hoàn chỉnh (Automated Web UI Testing)** | **Cả nhóm (5)** | **10.0** |
