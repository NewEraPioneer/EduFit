# Hướng Dẫn Triển Khai Kiểm Thử E2E Toàn Hệ Thống EduFit

> **Mã tài liệu:** EDUFIT-GUIDE-E2E-SYSTEM-VERIFICATION
> **Phiên bản:** 5.0 (hiệu chỉnh theo mã nguồn thực tế, 02/11/2026)
> **Nhánh phát triển Git:** `test/system-e2e-verification` (tách độc lập từ `develop`, làm trong git worktree riêng)
> **Căn cứ:** [SRS Edufit.docx](SRS%20Edufit.docx) · [edufit_master_test_plan.md](../edufit_master_test_plan.md) · [payment-flow-and-rules.md](../payment/payment-flow-and-rules.md) · [api-contract.md (Payment)](../payment/api-contract.md)

> [!IMPORTANT]
> **Quy ước trạng thái trong tài liệu này** (để không nhầm "đã viết" với "đã chạy"):
> - **ĐÃ CHẠY**: có test, có kết quả exit code 0 ghi nhận được.
> - **ĐÃ VIẾT, CHƯA XÁC MINH**: có mã nhưng chưa có bằng chứng chạy lại trên môi trường sạch.
> - **CHƯA LÀM**: mới là thiết kế.
>
> Mọi con số "passed" chỉ được điền từ kết quả chạy thật. Tài liệu bản cũ ghi "22/22 Passed", con số đó **không đúng** và đã bị gỡ.

---

## 0. Phạm vi và hai lớp kiểm thử

Hệ thống kiểm thử gồm hai lớp độc lập, mỗi luồng nghiệp vụ được gán rõ cho lớp phù hợp:

| Lớp | Công cụ | Chạy gì | Dùng cho |
|---|---|---|---|
| **A. Backend E2E** | JUnit 5 + Testcontainers `postgres:17` + MockMvc/HTTP | API, ràng buộc CSDL (GiST, rollback, idempotent), phân quyền | Chiều sâu logic và toàn vẹn dữ liệu |
| **B. Automated Web UI Testing** | Selenium WebDriver 4 (TypeScript) + Vitest + Page Object Model | Thao tác trên Chrome thật qua giao diện | Báo cáo học phần / nghiệm thu giao diện |

Báo cáo giai đoạn này **chỉ trình bày Lớp B**, ở mức: mở trang, kiểm tra thành phần hiển thị, thực hiện thao tác chính, chụp ảnh bằng chứng. Chưa đi sâu từng bước nghiệp vụ bên trong.

**Không tự động hóa qua giao diện** (kiểm bằng Lớp A hoặc kiểm tay, phải nêu rõ trong báo cáo):
- Ma trận phân quyền 72 ô và IDOR (AC-18): bản chất là kiểm thử API.
- Webhook PayOS và chữ ký HMAC (AC-19): không có giao diện.
- Quét/thanh toán mã QR VietQR thật: không thể tự động hóa bằng Selenium.
- Quyết toán sau 24 giờ (AC-21), hủy sát giờ (<24h) và "buổi học đến giờ": cần can thiệp đồng hồ hoặc seed dữ liệu, thuộc Lớp A.

---

## PHẦN I: NỀN TẢNG

### 1.1. Bốn nguyên tắc bắt buộc

1. **Kiểm chứng bằng máy**: tác vụ hoàn tất khi lệnh chạy trả về exit code 0, không dựa vào việc đọc mã bằng mắt.
2. **Locator bền vững cho Selenium**: ưu tiên `data-testid`, `id`, `name`, role/`aria-label`; tránh class Tailwind (`.bg-rose-50`, `.p-6`) và XPath tuyệt đối. Hiện các Page Object còn dùng XPath `contains(., 'Nộp hồ sơ')` và `.badge`, cần chuyển dần sang `data-testid` (xem 1.4).
3. **Mô phỏng người dùng thật**: thao tác qua nhập liệu và click chính thức, không dùng các nút Quick Login dành cho dev.
4. **Không hardcode**: dữ liệu tĩnh nằm trong `frontend/tests/e2e/resources/*.json` (Frontend) và `backend/app/src/test/resources/data/*.csv` (Backend); dữ liệu sinh động dùng hàm generator để chạy lặp không đụng độ.

### 1.2. CSDL cho Lớp A — **ĐÃ CHẠY** (28/10/2026)

Chuẩn hóa `postgres:17` (Ubuntu Noble), đồng bộ với `compose.yaml`, múi giờ `Asia/Ho_Chi_Minh`. Cấu hình tại [`SystemLifecycleCrossRoleE2ETest.java`](../../backend/app/src/test/java/vn/edufit/e2e/SystemLifecycleCrossRoleE2ETest.java).

```java
static { System.setProperty("user.timezone", "Asia/Ho_Chi_Minh");
         java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh")); }

@Container
static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
    .withDatabaseName("edufit_e2e").withUsername("edufit").withPassword("secret")
    .withEnv("TZ", "Asia/Ho_Chi_Minh");
```

### 1.3. Selenium runner (Lớp B) — **ĐÃ VIẾT, CHƯA XÁC MINH**

- Thư mục: `frontend/tests/e2e/`. Lệnh chạy: `npm run test:e2e` (Vitest, cấu hình `tests/e2e/vitest.e2e.config.ts`, chạy tuần tự `fileParallelism: false`, timeout 60 s).
- Cấu hình [`selenium.config.ts`](../../frontend/tests/e2e/config/selenium.config.ts): `E2E_BASE_URL` (mặc định `http://localhost:3000`), `E2E_BROWSER` (mặc định `chrome`), `E2E_HEADLESS`, explicit wait 15 s. Ảnh lưu ở `frontend/tests/e2e/reports/screenshots/`.
- Selenium Manager (đi kèm Selenium 4.6+) tự quản lý chromedriver.

> [!WARNING]
> **Tồn đọng cần xử lý trước khi chạy ở máy khác:**
> 1. `selenium-webdriver` và `@types/selenium-webdriver` **chưa có trong `frontend/package.json`** (chỉ có `vitest`); gói đang nằm trong một `node_modules` ở thư mục gốc repo, không có `package.json` đi kèm. Phải thêm vào `frontend/package.json` và xóa `node_modules` thừa ở gốc.
> 2. Thư mục `frontend/tests/e2e/reports/` chưa được `.gitignore` (đang hiện là untracked).
> 3. Cờ headless hiện là `E2E_HEADLESS === 'true' || CI === 'true'`; mục 3.2 hướng dẫn bật trực quan bằng `E2E_HEADLESS="false"`, đúng với cấu hình này.

### 1.4. Page Object Model — **ĐÃ VIẾT, CHƯA XÁC MINH**

| File | Nội dung | Mức dùng thực tế |
|---|---|---|
| `drivers/driver-factory.ts` | Khởi tạo WebDriver | đang dùng |
| `pages/BasePage.ts` | Explicit wait, toast, modal, chụp ảnh | đang dùng |
| `pages/AuthPage.ts` | Đăng nhập, đăng ký, quên mật khẩu | đang dùng (spec 01) |
| `pages/KycPage.ts` | Nộp bằng cấp, hàng đợi duyệt của Admin | **chưa có spec** |
| `pages/SchedulePage.ts` | Lịch, khung rảnh, điểm danh | **chưa có spec** |

Dữ liệu: `resources/login-data.json` (tài khoản seed `admin@`, `tutor@`, `student@edufit.vn`, các case đăng nhập sai và validation).

---

## PHẦN II: CÁC LUỒNG HÀNH TRÌNH

Mỗi luồng gồm: nghiệp vụ, **kịch bản Lớp B (UI)** với route **đã đối chiếu `frontend/src/app`**, và **kịch bản Lớp A (Backend)**. Tên nút/nhãn trong kịch bản UI là dự kiến, phải đối chiếu lại với component thực khi viết spec.

### Bảng route chuẩn (đã đối chiếu thư mục `app`)

| Chức năng | Route đúng | Route sai trong bản cũ |
|---|---|---|
| Đăng nhập / đăng ký / quên mật khẩu | `/login`, `/register`, `/forgot-password` | — |
| Gia sư nộp hồ sơ xác minh | `/tutor/verification` | `/tutor/profile` (chỉ là hồ sơ) |
| Admin duyệt hồ sơ | `/admin/verification-queue` | `/admin/verifications` |
| Admin danh mục | `/admin/catalogs` | — |
| Admin quản lý người dùng | `/admin/learners`, `/admin/tutors-management` | `/admin/users` |
| Admin nhật ký kiểm toán | `/admin/audit-logs` | — |
| Tìm gia sư công khai | `/tutors`, `/tutors/[id]` | — |
| Phụ huynh: con cái / lịch / thanh toán | `/parent/children`, `/parent/schedule`, `/parent/billing` | `/parent/family`, `/parent/wallet` |
| Học sinh: liên kết gia đình / lớp / lịch | `/student/family`, `/student/classes`, `/student/schedule` | — |
| Gia sư: yêu cầu / lớp / lịch / thu nhập | `/tutor/requests`, `/tutor/classes`, `/tutor/schedule`, `/tutor/earnings` | `/tutor/wallet` |
| Đánh giá của gia sư | `/tutor/reviews` | — |

---

### Luồng 01 — Xác thực, phiên đăng nhập, khóa brute-force (AC-01, AC-02)

**Nghiệp vụ** (UC1.1–UC1.4, BR-01..05): mật khẩu Argon2id; phiên có trạng thái qua cookie `EDUFIT_SESSION` (`HttpOnly`, `SameSite=Lax`); sai 5 lần liên tiếp khóa 15 phút; đặt lại mật khẩu hủy mọi phiên cũ.

**Lớp A — ĐÃ CHẠY** (28/10/2026): `ac01_statefulSessionAndLockout`, `ac02_passwordResetAndSessionInvalidation`. Có sửa lỗi giá trị cao: thêm `noRollbackFor = InvalidOperationException.class` ở `AuthenticateAccountService` để `failed_attempts` được ghi nhận khi đăng nhập sai.
- `POST /api/v1/auth/login` hợp lệ → `200` + `Set-Cookie: EDUFIT_SESSION`; `GET /api/v1/auth/me` trả đúng `userId`, `email`, `role`.
- Sai lần 1–4 → `401 INVALID_CREDENTIALS`; lần 5 → `423 ACCOUNT_LOCKED`, `account.locked_until = now + 15 phút`.
- Quên mật khẩu → đặt lại → phiên cũ gọi `/auth/me` bị `401`.

**Lớp B — spec `01-auth-flow.e2e.test.ts`: ĐÃ VIẾT, CHƯA XÁC MINH, 4 test case** (bản cũ ghi sai 5 case P01–P05):

| Mã | Nội dung |
|---|---|
| TC_UI_01 | `/login` hiển thị ô email, ô mật khẩu, nút đăng nhập |
| TC_UI_02 | Từ login sang `/register`, form hiển thị, chọn vai trò "Gia sư" |
| TC_UI_03 | Từ login sang `/forgot-password`, gửi yêu cầu cho `student@edufit.vn` |
| TC_UI_04 | Đăng nhập sai mật khẩu, hiển thị thông báo lỗi |

Chưa có test cho: validation email sai định dạng (dữ liệu `TC_AUTH_VAL_01` đã có trong JSON nhưng spec chưa dùng), đăng nhập thành công theo vai trò, khóa tài khoản sau 5 lần.

---

### Luồng 02 — Xác minh bằng cấp (Apache Tika) và Admin duyệt KYC (AC-03, AC-04)

**Nghiệp vụ** (UC1.10–UC1.12, BR-06..10): gia sư nộp PDF/JPEG/PNG; Tika kiểm tra Magic Bytes, từ chối file thực thi/shell script đổi đuôi `.pdf`; Admin duyệt/từ chối/thu hồi.

**Lớp A — ĐÃ CHẠY** (28/10/2026): `ac03_…`, `ac04_…`.
- File giả → `400 INVALID_FILE_TYPE`. PDF `%PDF-1.4` → `201`, `verification_request.status = PENDING`.
- Admin duyệt (`action: APPROVE`) → `tutor_profile.status = VERIFIED`, cập nhật `verified_at`.

**Lớp B — CHƯA LÀM.** Bản cũ ghi "5 kịch bản Playwright đã chạy `02-kyc-verification.spec.ts`", nhưng **không có file nào như vậy** (cả thư mục `frontend/e2e/` đã không còn). Page Object `KycPage` đã có sẵn để dùng.
- Gia sư vào `/tutor/verification`, chọn file giả `.sh` đổi đuôi `.pdf`, nộp → thấy thông báo lỗi định dạng.
- Chọn PDF hợp lệ, nộp → huy hiệu trạng thái đổi sang chờ duyệt.
- Admin vào `/admin/verification-queue`, mở tab chờ duyệt, phê duyệt hồ sơ → hồ sơ rời hàng đợi.
- Bấm từ chối mà không nhập lý do → bị chặn (BR-06).
- Gia sư tải lại → huy hiệu thành "Đã xác minh".

---

### Luồng 03 — Danh mục, hồ sơ, khung rảnh tuần, nhu cầu học (AC-05, AC-06)

**Nghiệp vụ** (UC1.5–UC1.9, BR-11..15): Admin quản lý môn học và cấp lớp; gia sư cập nhật tiểu sử, học phí, gán môn, khung rảnh tuần; học sinh cập nhật khối lớp, khu vực và **nhu cầu học (Learning Need)**.

> [!NOTE]
> Bản cũ dùng thuật ngữ "mục tiêu học tập (Learning Goal)" và endpoint `/goals`. Sau commit `96ffa8d` ("replace learning goals with learning needs and tutor offerings"), khái niệm đã đổi sang Learning Need. Khi viết lại test AC-06 phải dùng đúng endpoint và tên hiện tại; tên test `ac06_studentGoalUpdate` có thể cần đổi.

**Lớp A — ĐÃ CHẠY, cần chạy lại sau thay đổi Learning Need** (28/10/2026, trước commit chuyển đổi): Admin tạo môn "Toán Chuyên 9" (`POST /api/v1/admin/catalogs/subjects`); xóa cứng môn bị chặn `400 INVALID_OPERATION`; gia sư cập nhật hồ sơ (`PUT /api/v1/profiles/tutors/me`), gán môn (`POST …/tutors/me/subjects`), khung rảnh (`PUT …/tutors/me/availability-slots`); học sinh cập nhật hồ sơ (`PUT /api/v1/profiles/students/me`).
(Bản cũ còn liệt kê thêm `/profile/tutor/availability` và `/profile/student/goals`, không khớp endpoint thật nên đã gỡ.)

**Lớp B — CHƯA XÁC MINH.** Bản cũ nói đã có `03-catalog-profile.spec.ts` (5/5 pass), nhưng file này không còn trong repo. Coi như **chưa làm** cho tới khi viết lại bằng Selenium.
- Admin vào `/admin/catalogs`, thêm môn mới, môn hiện trong bảng.
- Gia sư vào hồ sơ (`/tutor/profile`), chọn môn, đặt mức giá, lưu khung rảnh, thấy thông báo thành công.
- Học sinh vào `/student/profile` và cập nhật nhu cầu học.

---

### Luồng 04 — Khám phá công khai và gợi ý AI Match (AC-07)

**Nghiệp vụ** (UC2.1–UC2.5, BR-16..20): khách và học sinh tìm gia sư theo môn, khu vực, giá, điểm đánh giá; học sinh dùng AI Match theo nhu cầu học và lịch rảnh.

**Lớp A — CHƯA LÀM.** Guest gọi API tìm kiếm chỉ trả gia sư `VERIFIED`; học sinh gọi AI Match nhận danh sách có điểm tương thích và lý do. Đường dẫn endpoint phải đọc lại từ controller khi viết test (bản cũ ghi `/discovery/...`, chưa đối chiếu).

**Lớp B — CHƯA LÀM.**
- Không đăng nhập, vào `/tutors`, áp bộ lọc môn và đánh giá → danh sách gia sư hiển thị thẻ thông tin.
- Mở `/tutors/[id]` → trang chi tiết hiển thị.
- Học sinh đăng nhập, vào `/student/tutors` và dùng gợi ý AI Match → thấy danh sách có điểm.

---

### Luồng 05 — Liên kết gia đình và giới hạn tần suất (AC-08, AC-09)

**Nghiệp vụ** (UC2.6–UC2.9, BR-21..25): phụ huynh mời liên kết học sinh; tối đa 10 lời mời/giờ/phụ huynh; học sinh chấp nhận → `ACTIVE`; hủy liên kết thu hồi quyền xem.

**Lớp A — CHƯA LÀM.** Mời → `PENDING`; hơn 10 yêu cầu trong 1 giờ → `429 RATE_LIMIT_EXCEEDED`; chấp nhận → `ACTIVE`; sau hủy liên kết, phụ huynh xem dữ liệu của con bị `403`.

**Lớp B — CHƯA LÀM.**
- Phụ huynh vào `/parent/children`, nhập email học sinh, gửi lời mời → lời mời hiện ở danh sách chờ.
- Học sinh vào `/student/family`, thấy lời mời, chấp nhận → huy hiệu "Đã liên kết".
- Học sinh hủy liên kết, xác nhận → phụ huynh tải lại không còn dữ liệu của con.

---

### Luồng 06 — Đề nghị kèm cặp và tạo lớp học (AC-10, AC-11)

**Nghiệp vụ** (UC3.1–UC3.4, BR-26..30): học sinh hoặc phụ huynh (đại diện) gửi đề nghị tới gia sư; gia sư chấp nhận thì tạo `Class` `ACTIVE`; chống race condition khi hai luồng cùng chấp nhận.

**Lớp A — CHƯA LÀM.** Tạo đề nghị → `PENDING`; chấp nhận → tạo lớp `ACTIVE`; hai request đồng thời cùng chấp nhận → một `200`, một `409`, không tạo hai lớp.

**Lớp B — CHƯA LÀM.**
- Học sinh vào `/tutors/[id]`, gửi yêu cầu học (chọn môn, ghi chú).
- Gia sư vào `/tutor/requests`, thấy yêu cầu, chấp nhận.
- Lớp mới hiện ở `/tutor/classes` với trạng thái đang hoạt động. (Bản cũ nói "tự chuyển hướng"; chưa xác nhận hành vi này, chỉ kiểm tra lớp xuất hiện.)

---

### Luồng 07 — Lập lịch, chống trùng giờ GiST, lập lịch hàng loạt (AC-12, AC-13)

**Nghiệp vụ** (UC4.1–UC4.5, BR-31..38): đề xuất buổi học; ràng buộc GiST Exclusion ở CSDL chặn trùng giờ cho cùng gia sư hoặc học sinh; lập lịch hàng loạt theo All-or-Nothing.

**Lớp A — CHƯA LÀM.** Tạo buổi học; buổi thứ hai trùng giờ → `409 SCHEDULE_OVERLAP`; batch 8 buổi có 1 buổi trùng → rollback, số bản ghi `tutoring_session` tăng 0. (Tên bảng bản cũ là `session`; thực tế là `tutoring_session`.)

**Lớp B — CHƯA LÀM.**
- Gia sư vào `/tutor/schedule`, thêm buổi học → buổi hiện trạng thái chờ xác nhận.
- Học sinh vào `/student/schedule`, xác nhận buổi học.
- Thêm buổi trùng giờ → giao diện báo lỗi trùng lịch.

---

### Luồng 08 — Đổi lịch, hủy, điểm danh và biên bản bất biến (AC-14, AC-15)

**Nghiệp vụ** (UC4.6–UC4.10, BR-39..45): đổi lịch hai bên đồng ý; hủy trước 24 giờ bình thường, dưới 24 giờ gắn `is_late_cancel = true`; điểm danh kết thúc buổi, **biên bản `session_note` bất biến**, mỗi buổi đúng một biên bản.

> [!NOTE]
> Bản cũ ghi kết quả điểm danh là `ATTENDED`/`ABSENT`. Mô hình hiện tại ghi nhận kết quả **COMPLETED / ABSENT**; cần đối chiếu lại trước khi viết test. Phần "tiến độ có trọng số" của AC-15 đang được thiết kế lại trong nhánh dashboard (xem `docs/progress/`), nên test AC-15 phải chờ nhánh đó ổn định.

**Lớp A — CHƯA LÀM.** Đổi lịch cập nhật khung giờ; hủy <24h → `CANCELLED`, `is_late_cancel = true`; điểm danh → buổi `COMPLETED`; sửa biên bản đã hoàn thành → `400`.

**Lớp B — CHƯA LÀM** (chỉ phần không phụ thuộc đồng hồ):
- Học sinh gửi yêu cầu đổi lịch, gia sư đồng ý.
- Gia sư mở form điểm danh cho buổi đủ điều kiện, nhập nội dung, hoàn tất → thẻ buổi học đổi trạng thái.
- Kịch bản "hủy muộn" và "buổi đến giờ" chỉ kiểm ở Lớp A.

---

### Luồng 09 — Ví, ký quỹ, quyết toán, rút tiền (AC-19..AC-22)

**Nghiệp vụ** (UC5.1–UC5.8, BR-46..55): nạp ví qua PayOS (VietQR); webhook HMAC-SHA256 idempotent; escrow khi chốt lịch (thiếu số dư thì chặn); quyết toán tự động sau 24 giờ; khiếu nại đóng băng quyết toán; gia sư rút tiền về tài khoản ngân hàng (Napas247).

**Lớp A — CHƯA LÀM.** Tạo yêu cầu nạp → `checkoutUrl`; webhook hợp lệ cộng ví đúng số tiền; webhook lặp không cộng lần hai; chốt lịch khi số dư thấp → `400 INSUFFICIENT_WALLET_BALANCE`; quyết toán chuyển escrow sang ví gia sư; rút tiền tạo `payout_request` `PENDING`. Phải đọc lại [api-contract.md](../payment/api-contract.md) để lấy đúng đường dẫn (bản cũ viết `/payments/topup`, `/payments/escrow/{id}/settle`).

**Lớp B — CHƯA LÀM, phạm vi giới hạn:**
- Phụ huynh vào `/parent/billing` mở hộp thoại nạp tiền và thấy mã QR hiển thị. **Không thanh toán thật**; việc cộng số dư kiểm bằng Lớp A.
- Gia sư vào `/tutor/earnings`, nhập tài khoản thụ hưởng và tạo lệnh rút.
- Admin vào `/admin/disputes` xem danh sách khiếu nại.

---

### Luồng 10 — Đánh giá gia sư, phân quyền, kiểm toán Admin (AC-16, AC-17, AC-18)

**Nghiệp vụ** (UC6.1–UC6.4, BR-56..61, NFR-SEC-01..05): mỗi học sinh đánh giá gia sư một lần cho mỗi lớp hoàn thành (1–5 sao, quick tag, nhận xét) và điểm trung bình được cập nhật; Admin khóa tài khoản vi phạm (phiên bị vô hiệu); thao tác nhạy cảm được ghi nhật ký kiểm toán; ma trận quyền 72 ô, chống IDOR.

**Lớp A — CHƯA LÀM.** Đánh giá tạo `tutor_review` và cập nhật điểm trung bình; đánh giá lần hai → `409 REVIEW_ALREADY_EXISTS`; Admin khóa → `account.status = LOCKED`; ma trận 72 ô và IDOR (AC-18) kiểm bằng `CrossRoleAuthorizationMatrixTest`.

**Lớp B — CHƯA LÀM.**
- Học sinh đánh giá gia sư cho lớp đủ điều kiện; đánh giá hiển thị trên hồ sơ công khai; gửi lần hai bị chặn.
- Gia sư vào `/tutor/reviews` thấy đánh giá.
- Admin vào `/admin/learners` hoặc `/admin/tutors-management`, khóa tài khoản kèm lý do; vào `/admin/audit-logs` thấy bản ghi tương ứng.

---

## PHẦN III: HƯỚNG DẪN CHẠY

### 3.1. Backend (Lớp A)

```powershell
cd "c:\University\Semester 5\SWP391\Edufit\EduFit\backend"
.\mvnw.cmd test -pl app -Dtest=SystemLifecycleCrossRoleE2ETest
```
Cần Docker Desktop đang chạy; kỳ vọng `BUILD SUCCESS`.

### 3.2. Selenium (Lớp B)

Điều kiện: backend và frontend đang chạy (`http://localhost:3000`), đã seed tài khoản `admin@`, `tutor@`, `student@edufit.vn`.

```powershell
cd "c:\University\Semester 5\SWP391\Edufit\frontend"
npm install
$env:E2E_HEADLESS="false"   # bật Chrome trực quan; bỏ dòng này để chạy không đầu
npm run test:e2e
```
Ảnh bằng chứng: `frontend/tests/e2e/reports/screenshots/<Tên_TestCase>_<thời_gian>.png`.

### 3.3. Báo cáo độ phủ Backend

Mở `backend/app/target/site/jacoco/index.html` sau khi chạy `mvnw test`.

---

## PHẦN IV: MA TRẬN 22 TIÊU CHÍ NGHIỆM THU

Cột trạng thái tách riêng hai lớp. "—" nghĩa là lớp đó không áp dụng hoặc không tự động hóa.

| Mã | Tiêu chí | Lớp A (Backend) | Lớp B (Selenium UI) |
|:---:|---|:---:|:---:|
| AC-01 | Đăng nhập, session, Argon2id, khóa brute-force | ĐÃ CHẠY | Một phần (TC_UI_01, 04) |
| AC-02 | Quên mật khẩu, hủy phiên cũ | ĐÃ CHẠY | Một phần (TC_UI_03) |
| AC-03 | Magic Bytes, nộp bằng cấp | ĐÃ CHẠY | CHƯA LÀM |
| AC-04 | Admin duyệt KYC | ĐÃ CHẠY | CHƯA LÀM |
| AC-05 | Danh mục và khung rảnh tuần | ĐÃ CHẠY, chạy lại sau đổi Learning Need | CHƯA LÀM |
| AC-06 | Nhu cầu học (trước là mục tiêu học tập) | Cần cập nhật theo Learning Need | CHƯA LÀM |
| AC-07 | Tìm kiếm, AI Match | CHƯA LÀM | CHƯA LÀM |
| AC-08 | Mời liên kết, rate limit | CHƯA LÀM | CHƯA LÀM |
| AC-09 | Chấp nhận và hủy liên kết | CHƯA LÀM | CHƯA LÀM |
| AC-10 | Đề nghị kèm cặp | CHƯA LÀM | CHƯA LÀM |
| AC-11 | Tạo lớp, race condition | CHƯA LÀM | CHƯA LÀM |
| AC-12 | Buổi đơn lẻ, chống trùng GiST | CHƯA LÀM | CHƯA LÀM |
| AC-13 | Lập lịch hàng loạt All-or-Nothing | CHƯA LÀM | — |
| AC-14 | Đổi lịch, hủy muộn | CHƯA LÀM | Một phần (đổi lịch) |
| AC-15 | Điểm danh, biên bản bất biến, tiến độ | CHƯA LÀM (chờ nhánh dashboard) | Một phần (điểm danh) |
| AC-16 | Đánh giá gia sư độc bản | CHƯA LÀM | CHƯA LÀM |
| AC-17 | Khóa người dùng, kiểm toán | CHƯA LÀM | CHƯA LÀM |
| AC-18 | Ma trận phân quyền 72 ô, IDOR | CHƯA LÀM | — |
| AC-19 | Nạp ví, webhook HMAC | CHƯA LÀM | — (chỉ hiển thị QR) |
| AC-20 | Escrow, chặn khi thiếu số dư | CHƯA LÀM | — |
| AC-21 | Quyết toán 24 giờ, khiếu nại | CHƯA LÀM | Một phần (xem danh sách khiếu nại) |
| AC-22 | Tài khoản thụ hưởng, rút tiền | CHƯA LÀM | CHƯA LÀM |

### Nhật ký tiến độ (chỉ ghi việc có bằng chứng)

| Ngày | Hạng mục | Trạng thái | Bằng chứng |
|:---:|---|:---:|---|
| 28/10/2026 | Tạo nhánh `test/system-e2e-verification` từ `develop` | ghi nhận | Chưa có nhánh `test/*` ở local tại 02/11, cần tạo lại bằng worktree |
| 28/10/2026 | Chuẩn hóa `postgres:17`, múi giờ | ĐÃ CHẠY | Testcontainers kết nối được |
| 28/10/2026 | AC-01..AC-04 (Lớp A) | ĐÃ CHẠY | `SystemLifecycleCrossRoleE2ETest` |
| 01/11/2026 | Chuyển Playwright → Selenium, dựng POM, spec 01 | ĐÃ VIẾT, CHƯA XÁC MINH | Commit `a557235`; có ảnh TC_UI_01 và TC_UI_04 trong `reports/screenshots/` |
| 02/11/2026 | Rà soát tài liệu so với mã (bản 5.0) | HOÀN TẤT | Phần "Tồn đọng" ở 1.3 |

---

## PHẦN IV-B: DANH MỤC TEST CASE GIAO DIỆN (LỚP B) THEO TOÀN BỘ HỆ THỐNG

Danh mục này là nguồn gốc của số lượng test case trong báo cáo. Mỗi case ở mức **hiển thị + một thao tác chính**, chụp ảnh bằng chứng; chưa đi sâu từng bước nghiệp vụ bên trong. Route đã đối chiếu `frontend/src/app`.

**Ưu tiên:** **P1** = đưa vào báo cáo đợt đầu (luồng chính, ổn định); **P2** = làm sau. Các case thuộc trang tiến độ/dashboard để **P2-chờ** vì đang được thiết kế lại ở nhánh khác.

| Mã | Nhóm / Luồng | Vai trò | Route | Mô tả ngắn | Ưu tiên | Trạng thái |
|---|---|---|---|---|:---:|:---:|
| TC_UI_01 | Xác thực (L01) | Guest | `/login` | Form đăng nhập hiển thị đủ thành phần | P1 | đã viết |
| TC_UI_02 | Xác thực (L01) | Guest | `/register` | Chuyển sang đăng ký, chọn vai trò | P1 | đã viết |
| TC_UI_03 | Xác thực (L01) | Guest | `/forgot-password` | Gửi yêu cầu khôi phục mật khẩu | P1 | đã viết |
| TC_UI_04 | Xác thực (L01) | Guest | `/login` | Đăng nhập sai hiện lỗi | P1 | đã viết |
| TC_UI_05 | Xác thực (L01) | Guest | `/login` | Email sai định dạng bị chặn ở client | P1 | chưa làm |
| TC_UI_06 | Xác thực (L01) | Admin | `/login` → `/admin/dashboard` | Đăng nhập thành công, vào đúng trang theo vai trò | P1 | chưa làm |
| TC_UI_07 | Xác thực (L01) | Gia sư | `/login` → `/tutor/dashboard` | Như trên cho gia sư | P1 | chưa làm |
| TC_UI_08 | Xác thực (L01) | Học sinh | `/login` → `/student/dashboard` | Như trên cho học sinh | P1 | chưa làm |
| TC_UI_09 | Xác thực (L01) | Mọi vai trò | `/…` | Đăng xuất, quay về `/login` | P2 | chưa làm |
| TC_UI_10 | Phân quyền | Guest | `/admin/dashboard` | Chưa đăng nhập bị chuyển về `/login` | P1 | chưa làm |
| TC_UI_11 | Phân quyền | Học sinh | `/admin/dashboard` | Học sinh vào trang admin bị chặn/chuyển hướng | P1 | chưa làm |
| TC_UI_12 | Onboarding | Học sinh | `/onboarding/student` | Form onboarding hiển thị và gửi được | P2 | chưa làm |
| TC_UI_13 | Onboarding | Gia sư | `/onboarding/tutor` | Như trên | P2 | chưa làm |
| TC_UI_14 | Onboarding | Phụ huynh | `/onboarding/parent` | Như trên | P2 | chưa làm |
| TC_UI_15 | KYC (L02) | Gia sư | `/tutor/verification` | Chọn file giả `.sh` đổi đuôi `.pdf` → báo lỗi định dạng | P1 | chưa làm |
| TC_UI_16 | KYC (L02) | Gia sư | `/tutor/verification` | Nộp PDF hợp lệ → trạng thái chờ duyệt | P1 | chưa làm |
| TC_UI_17 | KYC (L02) | Admin | `/admin/verification-queue` | Hồ sơ vừa nộp xuất hiện ở tab chờ duyệt | P1 | chưa làm |
| TC_UI_18 | KYC (L02) | Admin | `/admin/verification-queue` | Phê duyệt → hồ sơ rời tab chờ duyệt | P1 | chưa làm |
| TC_UI_19 | KYC (L02) | Admin | `/admin/verification-queue` | Từ chối bắt buộc nhập lý do (BR-06) | P1 | chưa làm |
| TC_UI_20 | KYC (L02) | Gia sư | `/tutor/verification` | Sau duyệt hiện trạng thái "Đã xác minh" | P2 | chưa làm |
| TC_UI_21 | Danh mục (L03) | Admin | `/admin/catalogs` | Thêm môn học, hiện trong bảng | P1 | chưa làm |
| TC_UI_22 | Hồ sơ (L03) | Gia sư | `/tutor/profile` | Cập nhật hồ sơ, giá, môn, lưu thành công | P1 | chưa làm |
| TC_UI_23 | Hồ sơ (L03) | Gia sư | `/tutor/profile` | Thiết lập khung rảnh tuần | P2 | chưa làm |
| TC_UI_24 | Hồ sơ (L03) | Học sinh | `/student/profile` | Cập nhật hồ sơ và nhu cầu học | P1 | chưa làm |
| TC_UI_25 | Hồ sơ (L03) | Phụ huynh | `/parent/profile` | Cập nhật hồ sơ phụ huynh | P2 | chưa làm |
| TC_UI_26 | Khám phá (L04) | Guest | `/tutors` | Danh sách gia sư và bộ lọc theo môn | P1 | chưa làm |
| TC_UI_27 | Khám phá (L04) | Guest | `/tutors/[id]` | Trang chi tiết gia sư hiển thị | P1 | chưa làm |
| TC_UI_28 | Khám phá (L04) | Học sinh | `/student/tutors` | Gợi ý AI Match hiển thị danh sách có điểm | P1 | chưa làm |
| TC_UI_29 | Khám phá (L04) | Phụ huynh | `/parent/tutors` | Phụ huynh xem danh sách gia sư | P2 | chưa làm |
| TC_UI_30 | Gia đình (L05) | Phụ huynh | `/parent/children` | Gửi lời mời liên kết, hiện ở danh sách chờ | P1 | chưa làm |
| TC_UI_31 | Gia đình (L05) | Học sinh | `/student/family` | Thấy lời mời và chấp nhận | P1 | chưa làm |
| TC_UI_32 | Gia đình (L05) | Học sinh | `/student/family` | Hủy liên kết, có hộp thoại xác nhận | P2 | chưa làm |
| TC_UI_33 | Kết nối (L06) | Học sinh | `/tutors/[id]` | Gửi yêu cầu học tới gia sư | P1 | chưa làm |
| TC_UI_34 | Kết nối (L06) | Gia sư | `/tutor/requests` | Thấy yêu cầu và chấp nhận | P1 | chưa làm |
| TC_UI_35 | Kết nối (L06) | Gia sư, học sinh | `/tutor/classes`, `/student/classes` | Lớp mới xuất hiện ở cả hai phía | P1 | chưa làm |
| TC_UI_36 | Lịch học (L07) | Gia sư | `/tutor/schedule` | Thêm buổi học, hiện trạng thái chờ xác nhận | P1 | chưa làm |
| TC_UI_37 | Lịch học (L07) | Học sinh | `/student/schedule` | Xác nhận buổi học | P1 | chưa làm |
| TC_UI_38 | Lịch học (L07) | Gia sư | `/tutor/schedule` | Thêm buổi trùng giờ → báo lỗi trùng lịch | P1 | chưa làm |
| TC_UI_39 | Lịch học (L07) | Học sinh | trang lớp | Banner đề xuất lịch định kỳ, đồng ý/từ chối | P2 | chưa làm |
| TC_UI_40 | Lịch học (L08) | Học sinh, gia sư | `/student/schedule` | Yêu cầu đổi lịch, bên kia đồng ý | P2 | chưa làm |
| TC_UI_41 | Điểm danh (L08) | Gia sư | `/tutor/schedule` | Mở form điểm danh, hoàn tất, trạng thái đổi | P1 | chưa làm |
| TC_UI_42 | Lịch sử (L08) | Gia sư | `/tutor/history` | Lịch sử buổi học hiển thị | P2 | chưa làm |
| TC_UI_43 | Thanh toán (L09) | Phụ huynh | `/parent/billing` | Mở hộp thoại nạp tiền, thấy mã QR (không thanh toán thật) | P1 | chưa làm |
| TC_UI_44 | Thanh toán (L09) | Học sinh | `/student/payments` | Trang thanh toán hiển thị số dư và giao dịch | P2 | chưa làm |
| TC_UI_45 | Thanh toán (L09) | Gia sư | `/tutor/earnings` | Trang thu nhập, tạo lệnh rút | P1 | chưa làm |
| TC_UI_46 | Thanh toán (L09) | Admin | `/admin/payments` | Danh sách giao dịch hiển thị | P2 | chưa làm |
| TC_UI_47 | Khiếu nại (L09) | Admin | `/admin/disputes` | Danh sách khiếu nại và trang chi tiết | P2 | chưa làm |
| TC_UI_48 | Đánh giá (L10) | Học sinh | trang lớp | Gửi đánh giá 1–5 sao, quick tag | P1 | chưa làm |
| TC_UI_49 | Đánh giá (L10) | Học sinh | trang lớp | Gửi lần hai bị chặn | P1 | chưa làm |
| TC_UI_50 | Đánh giá (L10) | Gia sư | `/tutor/reviews` | Đánh giá hiển thị | P2 | chưa làm |
| TC_UI_51 | Quản trị (L10) | Admin | `/admin/dashboard` | Trang tổng quan admin hiển thị | P1 | chưa làm |
| TC_UI_52 | Quản trị (L10) | Admin | `/admin/learners` | Khóa tài khoản kèm lý do | P1 | chưa làm |
| TC_UI_53 | Quản trị (L10) | Admin | `/admin/tutors-management` | Danh sách gia sư quản trị hiển thị | P2 | chưa làm |
| TC_UI_54 | Quản trị (L10) | Admin | `/admin/audit-logs` | Thao tác khóa tài khoản có trong nhật ký | P1 | chưa làm |
| TC_UI_55 | Quản trị | Admin | `/admin/family-links`, `/admin/links` | Trang liên kết gia đình/kết nối hiển thị | P2 | chưa làm |
| TC_UI_56 | Quản trị | Admin | `/admin/settings` | Trang cài đặt hệ thống hiển thị | P2 | chưa làm |
| TC_UI_57 | Dashboard | Học sinh | `/student/dashboard` | Thẻ tổng quan và lớp đang học hiển thị | P2-chờ | chưa làm |
| TC_UI_58 | Dashboard | Phụ huynh | `/parent/dashboard` | Chọn con và các thẻ KPI hiển thị | P2-chờ | chưa làm |
| TC_UI_59 | Dashboard | Gia sư | `/tutor/dashboard` | Thẻ tổng quan gia sư hiển thị | P2-chờ | chưa làm |
| TC_UI_60 | Tiến độ | Học sinh | `/student/progress` | Trang tiến độ hiển thị | P2-chờ | chưa làm |
| TC_UI_61 | Tiến độ | Phụ huynh | `/parent/progress` | Trang tiến độ của con hiển thị | P2-chờ | chưa làm |
| TC_UI_62 | Cài đặt | Học sinh, gia sư, phụ huynh | `/…/settings` | Trang cài đặt hiển thị (3 vai trò) | P2 | chưa làm |

**Thống kê:** 62 case. P1 = 37 (đưa vào báo cáo đợt đầu), P2 = 20, P2-chờ = 5 (đợi nhánh dashboard/tiến độ ổn định). Đã viết: 4. Không thuộc Lớp B (xem Phần 0): AC-13, AC-18, AC-19, AC-20 và phần cộng số dư/quyết toán của AC-21.

---

## PHẦN V: MẪU BÁO CÁO "AUTOMATED WEB UI TESTING"

> [!IMPORTANT]
> Báo cáo phục vụ nghiệm thu học phần (SWT301 / SWP391), **chỉ trình bày Lớp B**: thao tác người dùng, dữ liệu, kết quả hiển thị, ảnh bằng chứng. Phần "Phạm vi" của báo cáo phải nêu rõ các mục ở Phần 0 không nằm trong kiểm thử giao diện.

### 5.1. Tổng hợp thực thi

- **Dự án:** EduFit — nền tảng kết nối gia sư và học sinh
- **Môi trường:** Google Chrome bản ổn định, 1920×1080, Base URL `http://localhost:3000`
- **Công nghệ:** Selenium WebDriver 4.x (TypeScript), Vitest, Page Object Model, Explicit Wait, Data-Driven Testing
- **Số test case / số đạt:** *điền từ kết quả chạy thật*. Tại 02/11/2026: 4 case đã viết (TC_UI_01..04), **chưa có lần chạy xác minh được ghi lại**.

### 5.2. Bảng đặc tả chi tiết

Dùng cột: Mã · Tên · Dữ liệu vào · Bước thao tác · Kết quả mong đợi · Kết quả thực tế · Ảnh · Trạng thái. Chỉ điền "Kết quả thực tế" và "PASSED" sau khi chạy.

| Mã | Tên | Dữ liệu vào | Bước | Kết quả mong đợi | Ảnh | Trạng thái |
|---|---|---|---|---|---|:---:|
| TC_UI_01 | Form Đăng nhập hiển thị | — | Vào `/login`, kiểm tra ô email, mật khẩu, nút đăng nhập | Ba thành phần hiển thị | `TC_UI_01_navigate_login` | chờ chạy lại |
| TC_UI_02 | Chuyển sang Đăng ký, chọn vai trò | Chọn "Gia sư" | Bấm liên kết đăng ký, chọn vai trò | URL chứa `/register`, form hiển thị, vai trò được chọn | `TC_UI_02_register_role_selected` | chờ chạy lại |
| TC_UI_03 | Yêu cầu khôi phục mật khẩu | `student@edufit.vn` | Vào `/forgot-password`, nhập email, gửi | Màn hình xác nhận đã gửi | `TC_UI_03_forgot_password_submitted` | chờ chạy lại |
| TC_UI_04 | Đăng nhập sai | `wronguser@edufit.vn` / `WrongPassword@123` (từ `login-data.json`) | Nhập thông tin sai, bấm đăng nhập | Hiển thị thông báo lỗi | `TC_UI_04_invalid_credentials_alert` | chờ chạy lại |
| TC_UI_05 | Email sai định dạng bị chặn | Email `invalid-email-format` (`TC_AUTH_VAL_01`) | 1. Vào `/login`<br>2. Nhập email sai định dạng và mật khẩu hợp lệ<br>3. Bấm đăng nhập | Form hiện cảnh báo validation, không gửi yêu cầu, URL vẫn là `/login` | `TC_UI_05_email_validation` | chưa làm |
| TC_UI_06 | Admin đăng nhập thành công | `admin@edufit.vn` (theo `login-data.json`) | 1. Vào `/login`<br>2. Nhập tài khoản admin<br>3. Bấm đăng nhập | Chuyển tới `/admin/dashboard`, hiển thị menu quản trị | `TC_UI_06_admin_login` | chưa làm |
| TC_UI_07 | Gia sư đăng nhập thành công | `tutor@edufit.vn` | Như TC_UI_06 | Chuyển tới `/tutor/dashboard` | `TC_UI_07_tutor_login` | chưa làm |
| TC_UI_08 | Học sinh đăng nhập thành công | `student@edufit.vn` | Như TC_UI_06 | Chuyển tới `/student/dashboard` | `TC_UI_08_student_login` | chưa làm |
| TC_UI_10 | Chưa đăng nhập bị chặn trang admin | Không có | 1. Mở trình duyệt mới, chưa đăng nhập<br>2. Truy cập `/admin/dashboard` | Bị chuyển về `/login`, không thấy nội dung admin | `TC_UI_10_guest_blocked` | chưa làm |
| TC_UI_11 | Học sinh không vào được trang admin | `student@edufit.vn` | 1. Đăng nhập học sinh<br>2. Truy cập `/admin/dashboard` | Bị chặn hoặc chuyển hướng, không hiển thị trang quản trị | `TC_UI_11_student_blocked` | chưa làm |
| TC_UI_15 | Từ chối tệp bằng cấp giả mạo | Tệp `.sh` đổi đuôi `.pdf` (đặt trong `resources/files/`) | 1. Gia sư đăng nhập, vào `/tutor/verification`<br>2. Chọn tệp giả<br>3. Gửi hồ sơ | Thông báo lỗi định dạng, hồ sơ không được gửi | `TC_UI_15_fake_certificate` | chưa làm |
| TC_UI_16 | Nộp bằng cấp hợp lệ | Tệp PDF hợp lệ (`resources/files/`), thông tin bằng cấp sinh động | 1. Chọn PDF<br>2. Điền thông tin bằng cấp<br>3. Gửi hồ sơ | Huy hiệu chuyển sang trạng thái chờ duyệt | `TC_UI_16_submit_certificate` | chưa làm |
| TC_UI_17 | Hồ sơ xuất hiện trong hàng đợi Admin | Hồ sơ từ TC_UI_16 | 1. Admin đăng nhập<br>2. Vào `/admin/verification-queue`<br>3. Mở tab chờ duyệt | Hồ sơ của gia sư hiển thị trong danh sách | `TC_UI_17_queue_has_request` | chưa làm |
| TC_UI_18 | Admin phê duyệt hồ sơ | Hồ sơ từ TC_UI_17 | 1. Bấm phê duyệt trên hồ sơ | Hồ sơ rời tab chờ duyệt, có thông báo thành công | `TC_UI_18_admin_approve` | chưa làm |
| TC_UI_19 | Từ chối phải có lý do | Hồ sơ chờ duyệt, lý do để trống | 1. Bấm từ chối<br>2. Không nhập lý do, xác nhận | Giao diện chặn và yêu cầu nhập lý do (BR-06) | `TC_UI_19_reject_requires_reason` | chưa làm |
| TC_UI_21 | Admin thêm môn học | Tên môn sinh động (generator) | 1. Admin vào `/admin/catalogs`<br>2. Thêm môn mới<br>3. Lưu | Môn mới hiện ngay trong bảng danh mục | `TC_UI_21_add_subject` | chưa làm |
| TC_UI_22 | Gia sư cập nhật hồ sơ | Tiểu sử, học phí/giờ, môn (từ `resources/`) | 1. Gia sư vào `/tutor/profile`<br>2. Sửa thông tin, chọn môn<br>3. Lưu | Thông báo lưu thành công, dữ liệu giữ nguyên sau khi tải lại | `TC_UI_22_tutor_profile` | chưa làm |
| TC_UI_24 | Học sinh cập nhật hồ sơ và nhu cầu học | Khối lớp, khu vực, nhu cầu (từ `resources/`) | 1. Học sinh vào `/student/profile`<br>2. Nhập thông tin<br>3. Lưu | Thông báo lưu thành công, dữ liệu hiển thị lại đúng | `TC_UI_24_student_profile` | chưa làm |
| TC_UI_26 | Lọc danh sách gia sư theo môn | Môn học bất kỳ có gia sư | 1. Không đăng nhập, vào `/tutors`<br>2. Chọn bộ lọc môn | Danh sách chỉ gồm gia sư thỏa bộ lọc, mỗi thẻ có thông tin cơ bản | `TC_UI_26_tutor_filter` | chưa làm |
| TC_UI_27 | Xem chi tiết gia sư | Một gia sư trong danh sách | 1. Bấm vào thẻ gia sư | Mở `/tutors/[id]` với tiểu sử, môn, đánh giá | `TC_UI_27_tutor_detail` | chưa làm |
| TC_UI_28 | Gợi ý AI Match | Học sinh có nhu cầu học | 1. Học sinh vào `/student/tutors`<br>2. Bấm gợi ý AI Match | Hiển thị danh sách gia sư kèm điểm tương thích và lý do | `TC_UI_28_ai_match` | chưa làm |
| TC_UI_30 | Phụ huynh gửi lời mời liên kết | Email học sinh seed | 1. Phụ huynh vào `/parent/children`<br>2. Nhập email, gửi lời mời | Lời mời hiện trong danh sách chờ | `TC_UI_30_parent_invite` | chưa làm |
| TC_UI_31 | Học sinh chấp nhận lời mời | Lời mời từ TC_UI_30 | 1. Học sinh vào `/student/family`<br>2. Bấm chấp nhận | Trạng thái chuyển sang "Đã liên kết" | `TC_UI_31_student_accept` | chưa làm |
| TC_UI_33 | Học sinh gửi yêu cầu học | Môn học, ghi chú sinh động | 1. Vào `/tutors/[id]`<br>2. Bấm gửi yêu cầu học<br>3. Chọn môn, điền ghi chú, gửi | Thông báo gửi thành công | `TC_UI_33_send_request` | chưa làm |
| TC_UI_34 | Gia sư chấp nhận yêu cầu | Yêu cầu từ TC_UI_33 | 1. Gia sư vào `/tutor/requests`<br>2. Bấm chấp nhận | Yêu cầu chuyển sang đã xử lý, có thông báo thành công | `TC_UI_34_tutor_accept` | chưa làm |
| TC_UI_35 | Lớp học xuất hiện hai phía | Lớp từ TC_UI_34 | 1. Gia sư mở `/tutor/classes`<br>2. Học sinh mở `/student/classes` | Lớp mới hiện ở trạng thái đang hoạt động ở cả hai trang | `TC_UI_35_class_created` | chưa làm |
| TC_UI_36 | Gia sư thêm buổi học | Ngày, giờ bắt đầu/kết thúc hợp lệ | 1. Gia sư vào `/tutor/schedule`<br>2. Thêm buổi học | Buổi học hiện trên lịch ở trạng thái chờ xác nhận | `TC_UI_36_add_session` | chưa làm |
| TC_UI_37 | Học sinh xác nhận buổi học | Buổi học từ TC_UI_36 | 1. Học sinh vào `/student/schedule`<br>2. Bấm xác nhận | Buổi học chuyển sang trạng thái đã chốt | `TC_UI_37_confirm_session` | chưa làm |
| TC_UI_38 | Chặn buổi học trùng giờ | Khung giờ chồng lên buổi ở TC_UI_36 | 1. Thêm buổi học có khung giờ giao nhau | Hiển thị thông báo lỗi trùng lịch, không tạo buổi mới | `TC_UI_38_overlap_blocked` | chưa làm |
| TC_UI_41 | Điểm danh buổi học | Buổi đủ điều kiện điểm danh (dữ liệu seed), nội dung dạy sinh động | 1. Gia sư mở form điểm danh<br>2. Chọn kết quả, nhập nội dung<br>3. Hoàn tất | Trạng thái buổi học đổi sang hoàn thành | `TC_UI_41_attendance` | chưa làm |
| TC_UI_43 | Hiển thị mã QR nạp ví | Số tiền hợp lệ (từ `resources/`) | 1. Phụ huynh vào `/parent/billing`<br>2. Bấm nạp tiền, nhập số tiền | Hộp thoại hiển thị mã QR. Không thanh toán thật | `TC_UI_43_topup_qr` | chưa làm |
| TC_UI_45 | Gia sư tạo lệnh rút tiền | Tài khoản ngân hàng test (từ `resources/`) | 1. Gia sư vào `/tutor/earnings`<br>2. Nhập tài khoản thụ hưởng, tạo lệnh rút | Lệnh rút xuất hiện với trạng thái chờ xử lý | `TC_UI_45_payout_request` | chưa làm |
| TC_UI_48 | Học sinh đánh giá gia sư | 5 sao, một quick tag, nhận xét sinh động | 1. Mở lớp đã hoàn thành<br>2. Bấm đánh giá gia sư<br>3. Chọn sao, tag, nhập nhận xét, gửi | Đánh giá được ghi nhận, có thông báo thành công | `TC_UI_48_submit_review` | chưa làm |
| TC_UI_49 | Chặn đánh giá lần hai | Lớp đã đánh giá ở TC_UI_48 | 1. Mở lại lớp đó<br>2. Thử đánh giá lần nữa | Nút đánh giá bị vô hiệu hoặc báo đã đánh giá | `TC_UI_49_duplicate_review` | chưa làm |
| TC_UI_51 | Trang tổng quan admin | Admin đã đăng nhập | 1. Vào `/admin/dashboard` | Các khối thống kê và menu quản trị hiển thị | `TC_UI_51_admin_dashboard` | chưa làm |
| TC_UI_52 | Admin khóa tài khoản | Tài khoản học sinh test, lý do sinh động | 1. Vào `/admin/learners`<br>2. Chọn tài khoản, bấm khóa<br>3. Nhập lý do, xác nhận | Tài khoản hiện trạng thái bị khóa | `TC_UI_52_lock_account` | chưa làm |
| TC_UI_54 | Nhật ký ghi thao tác khóa | Thao tác từ TC_UI_52 | 1. Vào `/admin/audit-logs` | Bản ghi khóa tài khoản hiện với người thực hiện và thời gian | `TC_UI_54_audit_log` | chưa làm |

**Ghi chú về dữ liệu:** các tệp mẫu (PDF hợp lệ, tệp giả) đặt trong `frontend/tests/e2e/resources/files/`; giá trị nhập sinh động dùng hàm generator; tài khoản seed lấy từ `login-data.json`. Các case phụ thuộc nhau (15→16→17→18, 30→31, 33→34→35, 36→37, 48→49, 52→54) phải chạy tuần tự trong cùng một spec theo luồng. 25 case P2 và P2-chờ (TC_UI_09, 12–14, 20, 23, 25, 29, 32, 39, 40, 42, 44, 46, 47, 50, 53, 55–62) sẽ được đặc tả khi triển khai.

Bản cũ có TC_UI_05 (file giả) và TC_UI_06 (Admin duyệt) ghi PASSED; hai case đó **chưa có mã**, đã chuyển về "chưa làm".
