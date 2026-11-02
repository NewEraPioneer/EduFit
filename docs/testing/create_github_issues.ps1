<#
.SYNOPSIS
    Script tự động tạo danh sách 12 GitHub Issues cho dự án kiểm thử tự động EduFit Web UI.
.DESCRIPTION
    Sử dụng GitHub REST API để tạo tự động 12 Issues tương ứng với phân công 5 thành viên.
.EXAMPLE
    $env:GITHUB_TOKEN = "ghp_xxxxxxxxxxxxxxxxxxxx"
    .\create_github_issues.ps1 -Repo "dvtrug/EduFit"
#>

param (
    [string]$Repo = "dvtrug/EduFit",
    [string]$Token = $env:GITHUB_TOKEN
)

if (-not $Token) {
    $Token = Read-Host "Nhap GitHub Personal Access Token (PAT) co quyen 'repo' hoac 'issues'"
}

if (-not $Token) {
    Write-Error "Token khong duoc de trong. Thoat chuong trinh."
    exit 1
}

$headers = @{
    "Authorization" = "Bearer $Token"
    "Accept"        = "application/vnd.github+json"
    "User-Agent"    = "PowerShell-EduFit-Issue-Creator"
}

$issues = @(
    @{
        title = "[CHORE] Thiết lập Framework Selenium E2E & Vitest Runner"
        body = @"
## Mô tả
Thiết lập toàn bộ hạ tầng và kiến trúc nền tảng cho bộ kiểm thử tự động Web UI (Lớp B) của hệ thống EduFit, đóng gói cấu hình Selenium WebDriver 4.x tích hợp với test runner Vitest và chuẩn hóa lớp cơ sở Page Object Model.

## Hạng mục công việc liên quan
- Cấu hình file `frontend/tests/e2e/config/selenium.config.ts`.
- Xây dựng `DriverFactory` tự động quản lý trình duyệt Google Chrome thông qua Selenium Manager.
- Triển khai `BasePage` đóng gói các phương thức: `navigate`, `waitForVisible`, `waitForUrlContains`, `takeScreenshot`.
- Cấu hình runner Vitest chuyên biệt tại `frontend/tests/e2e/vitest.e2e.config.ts` (chế độ tuần tự `fileParallelism: false`, timeout 60s).
- Bổ sung tài nguyên kiểm thử chuẩn: `resources/login-data.json`, `resources/files/fake-script.pdf`, `resources/files/valid-certificate.pdf`.
- Cập nhật script `"test:e2e"` trong `frontend/package.json` và cấu hình bỏ qua ảnh chụp màn hình trong `.gitignore`.

## Tiêu chí chấp nhận
- [ ] Lệnh `npm run test:e2e` trong thư mục `frontend/` khởi chạy thành công mà không gặp lỗi thiếu driver.
- [ ] Trình duyệt Google Chrome khởi chạy đúng độ phân giải chuẩn `1920x1080`.
- [ ] Hỗ trợ chuyển đổi linh hoạt chế độ Headless / Trực quan qua biến môi trường `$env:E2E_HEADLESS`.
- [ ] Hàm `takeScreenshot()` lưu ảnh chính xác vào thư mục `frontend/tests/e2e/reports/screenshots/`.
- [ ] Không commit các file ảnh screenshot nhị phân vào Git tree (đã được `.gitignore`).

## Ghi chú kỹ thuật
- Tệp tin chính: `frontend/tests/e2e/drivers/driver-factory.ts`, `frontend/tests/e2e/pages/BasePage.ts`.
- Nhánh thực hiện: `chore/1-e2e-framework-setup`.
- Phụ trách: Thành viên A (Leader).
"@
        labels = @("type: chore", "module: ui-e2e")
    },
    @{
        title = "[TEST] Luồng 01: Xác thực, Đăng nhập đa vai trò & Phân quyền RBAC"
        body = @"
## Mô tả
Triển khai bộ kiểm thử tự động giao diện cho toàn bộ luồng Xác thực, Quên mật khẩu, Đăng ký và Hàng rào bảo mật phân quyền theo vai trò (Role-Based Access Control) trên hệ thống EduFit.

## Test Cases liên quan
- **TC_UI_01:** Màn hình Đăng nhập hiển thị đầy đủ các trường form và liên kết điều hướng.
- **TC_UI_02:** Điều hướng sang màn hình Đăng ký tài khoản và hiển thị chọn 3 vai trò (Gia sư, Phụ huynh, Học sinh).
- **TC_UI_03:** Điều hướng sang màn hình Quên mật khẩu và gửi yêu cầu khôi phục thành công.
- **TC_UI_04:** Đăng nhập sai mật khẩu hiển thị cảnh báo lỗi màu đỏ trực quan.
- **TC_UI_05:** Validation chặn email sai định dạng tại client, không submit form.
- **TC_UI_06:** Quản trị viên (Admin) đăng nhập thành công vào `/admin/dashboard`.
- **TC_UI_07:** Gia sư (Tutor) đăng nhập thành công điều hướng vào `/tutor/dashboard`.
- **TC_UI_08:** Học sinh (Student) đăng nhập thành công điều hướng vào `/student/dashboard`.
- **TC_UI_10:** Khách vãng lai chưa đăng nhập truy cập `/admin/dashboard` bị chặn và chuyển về `/login`.
- **TC_UI_11:** Học sinh cố tình truy cập `/admin/dashboard` bị chặn quyền và chuyển hướng.

## Tiêu chí chấp nhận
- [ ] Hoàn thành Page Object `AuthPage.ts` với đầy đủ locators và methods tương tác.
- [ ] Kịch bản `01-auth-flow.e2e.test.ts` thực thi tuần tự 10 ca kiểm thử.
- [ ] Kiểm chứng máy đạt kết quả Exit Code 0: `10 passed`.
- [ ] Sinh đầy đủ 10 ảnh bằng chứng màn hình với tiền tố `TC_UI_01_` đến `TC_UI_11_` tại `reports/screenshots/`.
- [ ] Áp dụng tài khoản seed từ `resources/login-data.json`, không hardcode chuỗi mật khẩu trong spec.

## Ghi chú kỹ thuật
- Page Object: `frontend/tests/e2e/pages/AuthPage.ts`.
- Spec: `frontend/tests/e2e/specs/01-auth-flow.e2e.test.ts`.
- Nhánh thực hiện: `test/2-auth-flow-specs`.
- Phụ trách: Thành viên A (Leader).
"@
        labels = @("type: test", "module: auth")
    },
    @{
        title = "[TEST] Luồng 02: Xác minh bằng cấp KYC Gia sư & Phê duyệt Admin"
        body = @"
## Mô tả
Tự động hóa kiểm thử giao diện cho quy trình nộp hồ sơ bằng cấp xác minh năng lực gia sư (KYC), kiểm tra cơ chế chặn file giả mạo định dạng (Magic Bytes) và quy trình thẩm định, phê duyệt của Quản trị viên.

## Test Cases liên quan
- **TC_UI_15:** Gia sư chọn tệp giả mạo không đúng định dạng (`fake-script.pdf`) bị từ chối/cảnh báo.
- **TC_UI_16:** Gia sư nộp hồ sơ chứng minh năng lực với tệp PDF hợp lệ (`valid-certificate.pdf`) thành công, trạng thái chuyển sang Chờ duyệt.
- **TC_UI_17:** Quản trị viên truy cập hàng đợi duyệt KYC (`/admin/verification-queue`) thấy hồ sơ vừa nộp.
- **TC_UI_19:** Admin từ chối hồ sơ bắt buộc phải nhập lý do giải thích (Quy tắc BR-06).
- **TC_UI_18:** Admin thẩm định và phê duyệt hồ sơ bằng cấp hợp lệ, hồ sơ rời khỏi hàng đợi chờ duyệt.

## Tiêu chí chấp nhận
- [ ] Hoàn thiện Page Object `KycPage.ts` hỗ trợ tải file nhị phân và tương tác hàng đợi duyệt.
- [ ] Tệp kịch bản `02-kyc-flow.e2e.test.ts` chạy thông suốt cả 5 test cases.
- [ ] Kiểm chứng máy đạt Exit Code 0: `5 passed`.
- [ ] Sinh đầy đủ 5 ảnh bằng chứng màn hình.

## Ghi chú kỹ thuật
- Page Object: `frontend/tests/e2e/pages/KycPage.ts`.
- Spec: `frontend/tests/e2e/specs/02-kyc-flow.e2e.test.ts`.
- Nhánh thực hiện: `test/3-kyc-verification-flow`.
- Phụ trách: Thành viên B.
"@
        labels = @("type: test", "module: kyc")
    },
    @{
        title = "[TEST] Luồng 03: Quản lý danh mục môn học & Thiết lập hồ sơ chuyên môn"
        body = @"
## Mô tả
Kiểm thử tự động giao diện quản lý danh mục môn học của Quản trị viên và chức năng cập nhật thông tin hồ sơ chuyên môn của Gia sư cũng như thiết lập nhu cầu học tập của Học sinh.

## Test Cases liên quan
- **TC_UI_21:** Quản trị viên truy cập trang danh mục (`/admin/catalogs`) và mở biểu mẫu thêm môn học mới.
- **TC_UI_22:** Gia sư truy cập màn hình hồ sơ cá nhân (`/tutor/profile`) cập nhật tiểu sử, môn học và mức giá thành công.
- **TC_UI_24:** Học sinh truy cập hồ sơ cá nhân (`/student/profile`) và thiết lập nhu cầu học tập (Learning Needs).

## Tiêu chí chấp nhận
- [ ] Xây dựng 2 Page Objects: `CatalogPage.ts` và `ProfilePage.ts`.
- [ ] Kịch bản `03-catalog-profile-flow.e2e.test.ts` chạy thành công không flaky.
- [ ] Kiểm chứng máy đạt Exit Code 0: `3 passed`.
- [ ] Sinh đầy đủ 3 ảnh bằng chứng màn hình.

## Ghi chú kỹ thuật
- Page Objects: `frontend/tests/e2e/pages/CatalogPage.ts`, `frontend/tests/e2e/pages/ProfilePage.ts`.
- Spec: `frontend/tests/e2e/specs/03-catalog-profile-flow.e2e.test.ts`.
- Nhánh thực hiện: `test/4-catalog-profile-flow`.
- Phụ trách: Thành viên B.
"@
        labels = @("type: test", "module: profile")
    },
    @{
        title = "[TEST] Luồng 04: Khám phá gia sư công khai & Gợi ý AI Match"
        body = @"
## Mô tả
Tự động hóa luồng trải nghiệm tìm kiếm gia sư dành cho khách vãng lai (tìm kiếm theo môn học, bộ lọc) và tính năng đề xuất gia sư thông minh ứng dụng trí tuệ nhân tạo (AI Match) cho học sinh.

## Test Cases liên quan
- **TC_UI_26:** Khách vãng lai truy cập `/tutors`, áp dụng bộ lọc môn học và kiểm tra danh sách kết quả thẻ gia sư.
- **TC_UI_28:** Học sinh đăng nhập và truy cập trang tìm kiếm gia sư kèm tính năng gợi ý AI Match (`/student/tutors`), hiển thị danh sách tương thích kèm điểm số.

## Tiêu chí chấp nhận
- [ ] Hoàn thành Page Object `TutorsPage.ts`.
- [ ] Kịch bản `04-tutors-discovery-flow.e2e.test.ts` kiểm chứng chính xác bộ lọc và panel AI Match.
- [ ] Lệnh kiểm chứng máy đạt Exit Code 0: `2 passed`.
- [ ] Sinh ảnh bằng chứng màn hình.

## Ghi chú kỹ thuật
- Page Object: `frontend/tests/e2e/pages/TutorsPage.ts`.
- Spec: `frontend/tests/e2e/specs/04-tutors-discovery-flow.e2e.test.ts`.
- Nhánh thực hiện: `test/5-tutors-discovery-flow`.
- Phụ trách: Thành viên C.
"@
        labels = @("type: test", "module: discovery")
    },
    @{
        title = "[TEST] Luồng 05: Quản lý liên kết gia đình Phụ huynh - Học sinh"
        body = @"
## Mô tả
Kiểm thử tự động quy trình kết nối gia đình giữa tài khoản Phụ huynh và Học sinh: gửi lời mời liên kết qua email và chấp nhận lời mời từ phía học sinh.

## Test Cases liên quan
- **TC_UI_30:** Phụ huynh truy cập trang quản lý con em (`/parent/children`) và gửi lời mời liên kết tới học sinh.
- **TC_UI_31:** Học sinh truy cập trang liên kết gia đình (`/student/family`), kiểm tra danh sách lời mời và xác nhận liên kết.

## Tiêu chí chấp nhận
- [ ] Hoàn thiện Page Object `FamilyPage.ts`.
- [ ] Kịch bản `05-family-link-flow.e2e.test.ts` thực thi mô phỏng luồng 2 vai trò.
- [ ] Kiểm chứng máy đạt Exit Code 0: `2 passed`.
- [ ] Sinh đầy đủ 2 ảnh bằng chứng màn hình.

## Ghi chú kỹ thuật
- Page Object: `frontend/tests/e2e/pages/FamilyPage.ts`.
- Spec: `frontend/tests/e2e/specs/05-family-link-flow.e2e.test.ts`.
- Nhánh thực hiện: `test/6-family-link-flow`.
- Phụ trách: Thành viên C.
"@
        labels = @("type: test", "module: family")
    },
    @{
        title = "[TEST] Luồng 06: Đề nghị kèm cặp & Đồng bộ lớp học hai phía"
        body = @"
## Mô tả
Tự động hóa quy trình xem và xử lý đề nghị kèm cặp của Gia sư và kiểm tra tính đồng bộ của danh sách lớp học đang hoạt động trên cả hai cổng thông tin Gia sư và Học sinh.

## Test Cases liên quan
- **TC_UI_34:** Gia sư truy cập trang yêu cầu học (`/tutor/requests`) và kiểm tra danh sách các đề nghị kèm cặp từ học viên.
- **TC_UI_35:** Danh sách lớp học hoạt động hiển thị đồng bộ ở cả hai cổng `/tutor/classes` và `/student/classes`.

## Tiêu chí chấp nhận
- [ ] Hoàn thành Page Object `ClassConnectionPage.ts`.
- [ ] Kịch bản `06-connection-classes-flow.e2e.test.ts` thực hiện kiểm tra đa người dùng.
- [ ] Kiểm chứng máy đạt Exit Code 0: `2 passed`.
- [ ] Sinh đầy đủ 2 ảnh bằng chứng màn hình.

## Ghi chú kỹ thuật
- Page Object: `frontend/tests/e2e/pages/ClassConnectionPage.ts`.
- Spec: `frontend/tests/e2e/specs/06-connection-classes-flow.e2e.test.ts`.
- Nhánh thực hiện: `test/7-connection-classes-flow`.
- Phụ trách: Thành viên D.
"@
        labels = @("type: test", "module: classes")
    },
    @{
        title = "[TEST] Luồng 07: Lập lịch giảng dạy & Thời khóa biểu buổi học"
        body = @"
## Mô tả
Kiểm thử tự động giao diện quản lý lịch dạy của Gia sư và kiểm tra thời khóa biểu học tập của Học sinh trên hệ thống.

## Test Cases liên quan
- **TC_UI_36:** Gia sư truy cập trang lịch giảng dạy (`/tutor/schedule`) và kiểm tra khung thời khóa biểu làm việc.
- **TC_UI_37:** Học sinh truy cập trang lịch học (`/student/schedule`) và kiểm tra hiển thị thời khóa biểu học tập cá nhân.

## Tiêu chí chấp nhận
- [ ] Hoàn thiện Page Object `SchedulePage.ts`.
- [ ] Kịch bản `07-schedule-attendance-flow.e2e.test.ts` chạy trơn tru trên trình duyệt.
- [ ] Kiểm chứng máy đạt Exit Code 0: `2 passed`.
- [ ] Sinh đầy đủ 2 ảnh bằng chứng màn hình.

## Ghi chú kỹ thuật
- Page Object: `frontend/tests/e2e/pages/SchedulePage.ts`.
- Spec: `frontend/tests/e2e/specs/07-schedule-attendance-flow.e2e.test.ts`.
- Nhánh thực hiện: `test/8-schedule-attendance-flow`.
- Phụ trách: Thành viên D.
"@
        labels = @("type: test", "module: schedule")
    },
    @{
        title = "[TEST] Luồng 08: Cổng thanh toán nạp tiền VietQR & Rút thu nhập gia sư"
        body = @"
## Mô tả
Tự động hóa kiểm thử giao diện cổng thanh toán nạp tiền ví phụ huynh (hiển thị mã QR PayOS/VietQR - không thanh toán tiền thật) và màn hình quản lý số dư thu nhập, tạo lệnh rút tiền của Gia sư.

## Test Cases liên quan
- **TC_UI_43:** Phụ huynh truy cập cổng thanh toán `/parent/billing`, mở hộp thoại nạp tiền và hiển thị mã QR VietQR.
- **TC_UI_45:** Gia sư truy cập trang thu nhập `/tutor/earnings`, kiểm tra số dư khả dụng và biểu mẫu tạo lệnh rút tiền.

## Tiêu chí chấp nhận
- [ ] Xây dựng Page Object `PaymentPage.ts`.
- [ ] Kịch bản `08-billing-payout-flow.e2e.test.ts` kiểm chứng hiển thị modal QR và số dư ví.
- [ ] Kiểm chứng máy đạt Exit Code 0: `2 passed`.
- [ ] Sinh đầy đủ 2 ảnh bằng chứng màn hình.

## Ghi chú kỹ thuật
- Page Object: `frontend/tests/e2e/pages/PaymentPage.ts`.
- Spec: `frontend/tests/e2e/specs/08-billing-payout-flow.e2e.test.ts`.
- Nhánh thực hiện: `test/9-billing-payout-flow`.
- Phụ trách: Thành viên E.
"@
        labels = @("type: test", "module: payment")
    },
    @{
        title = "[TEST] Luồng 09: Đánh giá gia sư & Quản lý phản hồi học viên"
        body = @"
## Mô tả
Kiểm thử tự động giao diện quản lý đánh giá của Gia sư: kiểm tra hiển thị điểm số trung bình (sao) và danh sách nhận xét, phản hồi từ học sinh sau các buổi học.

## Test Cases liên quan
- **TC_UI_50:** Gia sư truy cập trang đánh giá (`/tutor/reviews`), kiểm tra hiển thị khối điểm trung bình và các nhận xét từ học viên.

## Tiêu chí chấp nhận
- [ ] Kịch bản `09-review-flow.e2e.test.ts` chạy đạt kết quả tốt.
- [ ] Kiểm chứng máy đạt Exit Code 0: `1 passed`.
- [ ] Sinh ảnh bằng chứng màn hình.

## Ghi chú kỹ thuật
- Spec: `frontend/tests/e2e/specs/09-review-flow.e2e.test.ts`.
- Nhánh thực hiện: `test/10-review-flow`.
- Phụ trách: Thành viên E.
"@
        labels = @("type: test", "module: review")
    },
    @{
        title = "[TEST] Luồng 10: Quản trị hệ thống, Quản lý người học & Nhật ký kiểm toán"
        body = @"
## Mô tả
Tự động hóa kiểm thử các màn hình trung tâm quản trị của Quản trị viên (Admin): Dashboard thống kê tổng quan hệ thống, danh sách quản lý tài khoản người học và màn hình nhật ký kiểm toán (Audit Logs).

## Test Cases liên quan
- **TC_UI_51:** Quản trị viên truy cập trang tổng quan (`/admin/dashboard`), hiển thị các khối KPI và biểu đồ thống kê.
- **TC_UI_52:** Quản trị viên truy cập trang quản lý người học (`/admin/learners`), hiển thị danh sách tài khoản học sinh.
- **TC_UI_54:** Quản trị viên truy cập nhật ký kiểm toán (`/admin/audit-logs`), hiển thị vết các hoạt động trên hệ thống.

## Tiêu chí chấp nhận
- [ ] Hoàn thành Page Object `AdminManagementPage.ts`.
- [ ] Kịch bản `10-admin-management-flow.e2e.test.ts` chạy qua cả 3 màn hình quản trị.
- [ ] Kiểm chứng máy đạt Exit Code 0: `3 passed`.
- [ ] Sinh đầy đủ 3 ảnh bằng chứng màn hình.

## Ghi chú kỹ thuật
- Page Object: `frontend/tests/e2e/pages/AdminManagementPage.ts`.
- Spec: `frontend/tests/e2e/specs/10-admin-management-flow.e2e.test.ts`.
- Nhánh thực hiện: `test/11-admin-management-flow`.
- Phụ trách: Thành viên E.
"@
        labels = @("type: test", "module: admin")
    },
    @{
        title = "[DOCS] Hoàn thiện Báo cáo tổng kết nghiệm thu & Release v1.0.0"
        body = @"
## Mô tả
Tổng hợp toàn bộ kết quả kiểm thử tự động Web UI từ 10 kịch bản của 5 thành viên, xuất báo cáo tổng kết theo mẫu học phần SWP391/SWT301, đính kèm bảng thống kê và phát hành bản phát hành chính thức `v1.0.0`.

## Hạng mục hoàn thành
- [ ] Chạy kiểm thử hồi quy toàn bộ hệ thống (`npm run test:e2e`), đảm bảo 100% test cases passed.
- [ ] Tổng hợp toàn bộ ảnh chụp màn hình bằng chứng từ `reports/screenshots/` vào tài liệu nghiệm thu.
- [ ] Hoàn thiện file báo cáo Word/PDF theo mẫu template đính kèm của học phần.
- [ ] Cập nhật bảng đóng góp của 5 thành viên đối chiếu 1:1.
- [ ] Tạo tag Git `v1.0.0` và xuất bản GitHub Release.

## Tiêu chí chấp nhận
- [ ] Báo cáo phản ánh trung thực kết quả chạy máy với số liệu cụ thể.
- [ ] Không còn test case nào ở trạng thái lỗi hoặc bị bỏ qua.
- [ ] Release notes ghi nhận đầy đủ danh sách Pull Request đã merge.
"@
        labels = @("type: docs", "module: report")
    }
)

Write-Host "Bat dau tao 12 Issues tren repo $Repo..." -ForegroundColor Cyan

foreach ($issue in $issues) {
    $payload = @{
        title  = $issue.title
        body   = $issue.body
        labels = $issue.labels
    } | ConvertTo-Json -Depth 5

    try {
        $uri = "https://api.github.com/repos/$Repo/issues"
        $resp = Invoke-RestMethod -Uri $uri -Method Post -Headers $headers -Body ([System.Text.Encoding]::UTF8.GetBytes($payload)) -ContentType "application/json; charset=utf-8"
        Write-Host " -> Da tao Issue #$($resp.number): $($issue.title)" -ForegroundColor Green
    }
    catch {
        Write-Host " -> Loi khi tao '$($issue.title)': $_" -ForegroundColor Red
    }
}

Write-Host "Hoan tat!" -ForegroundColor Green
