# EduFit — Hướng Dẫn Làm Việc Nhóm Với Git & GitHub (Chuẩn Production) Cho Kiểm Thử Tự Động Web UI

> **Áp dụng cho:** Hệ thống EduFit — **Nội dung B. Automated Web UI Testing**  
> **Tài liệu đặc tả:** [EduFit_Web_UI_Testing_ReadMe.md](EduFit_Web_UI_Testing_ReadMe.md)  
> **Nhánh chuẩn (Solution Reference):** Worktree `EduFit-system-e2e` (nhánh `test/system-e2e-verification`)  
> **Quy mô nhóm:** Mặc định **5 thành viên (A, B, C, D, E)**  
> **Mục tiêu:** Cả 5 người làm việc **song song** trên repository EduFit có sẵn mà **không giẫm chân nhau**, mọi thay đổi đều **truy vết được 1:1** (Issue → Nhánh → Commit → Pull Request → Code Review → Squash Merge), đạt tiêu chuẩn chất lượng cao nhất.

---

## Mục lục

1. [Nguyên tắc chung](#1-nguyên-tắc-chung)
2. [Mô hình nhánh & quy ước đặt tên](#2-mô-hình-nhánh--quy-ước-đặt-tên)
3. [Vai trò & phân công 5 thành viên](#3-vai-trò--phân-công-5-thành-viên)
4. [Sprint 0 — Thiết lập nền tảng framework (Leader)](#4-sprint-0--thiết-lập-nền-tảng-framework-leader)
5. [Sprint 0 — Thành viên chuẩn bị môi trường](#5-sprint-0--thành-viên-chuẩn-bị-môi-trường)
6. [Quy trình thực hiện một ca kiểm thử (Vòng đời một issue)](#6-quy-trình-thực-hiện-một-ca-kiểm-thử-vòng-đời-một-issue)
7. [Hướng dẫn chi tiết theo từng người & từng luồng kiểm thử](#7-hướng-dẫn-chi-tiết-theo-từng-người--từng-luồng-kiểm-thử)
8. [Tận dụng Worktree `EduFit-system-e2e` làm Solution Reference](#8-tận-dụng-worktree-edufit-system-e2e-làm-solution-reference)
9. [Pull Request & Code Review Checklist](#9-pull-request--code-review-checklist)
10. [Xử lý xung đột (Merge Conflict)](#10-xử-lý-xung-đột-merge-conflict)
11. [Kế hoạch sprint gợi ý & Definition of Done](#11-kế-hoạch-sprint-gợi-ý--definition-of-done)
12. [Phụ lục: Template mẫu & Cấu hình CI](#12-phụ-lục-template-mẫu--cấu-hình-ci)
13. [Cheat sheet lệnh Git & Lệnh chạy Selenium Test](#13-cheat-sheet-lệnh-git--lệnh-chạy-selenium-test)
14. [Checklist làm việc nhóm kiểm thử](#14-checklist-làm-việc-nhóm-kiểm-thử)

---

## 1. Nguyên tắc chung

| # | Nguyên tắc cốt lõi |
|---|---|
| **1** | **Không ai commit/push trực tiếp** lên `main` và `develop` — Mọi kịch bản kiểm thử hay Page Object mới đều phải đi qua **Pull Request (PR)**. |
| **2** | **Không có issue thì không viết test.** Mỗi luồng kiểm thử là 1 issue; mỗi issue là 1 nhánh; mỗi nhánh kết thúc bằng 1 PR được review. |
| **3** | **Nhánh nhỏ, sống ngắn** (tối đa 1–2 ngày). PR lý tưởng gồm 1 Page Object + 1 Spec file (< 300 dòng) để review kỹ lưỡng. |
| **4** | **Commit nhỏ, chuẩn Conventional Commits.** Tiền tố bắt buộc: `test(...)`, `chore(...)`, `fix(...)`. Mỗi commit phải chạy máy được. |
| **5** | **Đồng bộ thường xuyên với `develop`** (ít nhất 1 lần mỗi ngày trước khi bắt đầu code) để triệt tiêu nguy cơ xung đột. |
| **6** | **Chốt chặn tự động tối cao (Deterministic Machine Verification):** Test phải chạy trên máy với **Exit Code 0** và sinh đầy đủ ảnh chụp màn hình bằng chứng. Không nghiệm thu bằng mắt hay suy đoán. |
| **7** | **Không hardcode:** Dữ liệu tài khoản lấy từ `resources/login-data.json`; file PDF lấy từ `resources/files/`; không rải thông tin nhạy cảm vào code. |
| **8** | **Trao đổi tập trung trên GitHub:** Góp ý kỹ thuật, giải thích lỗi diễn ra trực tiếp trên Issue và PR; nhóm chat chỉ dùng để nhắc nhở. |

---

## 2. Mô hình nhánh & quy ước đặt tên

### 2.1 Mô hình tích hợp liên tục (Trunk-based with Feature Branches)

Tận dụng repository EduFit hiện có, toàn bộ đội kiểm thử làm việc trên các nhánh tính năng rẽ nhánh từ `develop`:

```
develop (nhánh chính tích hợp)
   ●──────────────────────────────●────────────────────────●────────────────● (develop luôn xanh)
    \                            / \                      / \              /
     ●──● (Squash PR #1)────────/   \                    /   \            /
      test/1-auth-flow (Member A)    ●──● (Squash PR #2)/     \          /
                                      test/2-kyc-flow (B)      ●──● (PR)/
                                                                test/4-discovery (C)
```

| Nhánh | Base từ | Merge vào | Kiểu merge | Điều kiện merge |
|---|---|---|---|---|
| `develop` | — | — | — | Nhánh tích hợp chung, luôn chạy được test suite. |
| `test/<n>-<mô-tả>` | `develop` | `develop` | **Squash and Merge** | Vitest E2E exit code 0 + ít nhất 1 review approval. |
| `chore/<n>-<mô-tả>` | `develop` | `develop` | **Squash and Merge** | Cấu hình runner, thêm dependency, sửa driver. |
| `fix/<n>-<mô-tả>` | `develop` | `develop` | **Squash and Merge** | Sửa locator khi UI thay đổi. |

### 2.2 Quy ước đặt tên nhánh

```
<loại>/<số-issue>-<mô-tả-ngắn-kebab-case>
```

| Ví dụ hợp lệ | Ý nghĩa | Thành viên phụ trách |
|---|---|:---:|
| `test/1-auth-flow-specs` | Viết kịch bản Luồng 01 (Auth & RBAC), Issue #1 | **A** |
| `test/2-kyc-verification-flow` | Viết kịch bản Luồng 02 (KYC & Duyệt), Issue #2 | **B** |
| `test/3-catalog-profile-flow` | Viết kịch bản Luồng 03 (Danh mục & Hồ sơ), Issue #3 | **B** |
| `test/4-tutors-discovery-flow` | Viết kịch bản Luồng 04 (Khám phá & AI Match), Issue #4 | **C** |
| `test/5-family-link-flow` | Viết kịch bản Luồng 05 (Liên kết gia đình), Issue #5 | **C** |
| `test/6-connection-classes-flow`| Viết kịch bản Luồng 06 (Yêu cầu học & Lớp học), Issue #6 | **D** |
| `test/7-schedule-attendance-flow`| Viết kịch bản Luồng 07 (Lịch & Thời khóa biểu), Issue #7 | **D** |
| `test/8-billing-payout-flow` | Viết kịch bản Luồng 08 (Nạp tiền & Rút tiền), Issue #8 | **E** |
| `test/9-review-flow` | Viết kịch bản Luồng 09 (Đánh giá gia sư), Issue #9 | **E** |
| `test/10-admin-management-flow` | Viết kịch bản Luồng 10 (Admin Dashboard & Audit), Issue #10 | **E** |

*Quy tắc:* Viết thường, không dấu, dùng gạch nối `-`, luôn gắn liền với số issue.

### 2.3 Quy ước commit (Conventional Commits)

```
<type>(<scope>): <subject>

[Nội dung mô tả chi tiết: lý do thay đổi, selector/bước test được thêm]

Refs #<issue>
```

| Tiền tố `type` | Mục đích sử dụng trong E2E testing |
|---|---|
| `test` | Thêm mới hoặc cập nhật kịch bản kiểm thử Selenium E2E |
| `feat` | Thêm phương thức mới cho Page Object Model (POM) |
| `fix` | Sửa bộ định vị phần tử (locators) hoặc xử lý flaky wait |
| `chore` | Cập nhật cấu hình Selenium, Vitest, npm dependencies |
| `docs` | Bổ sung tài liệu đặc tả, hướng dẫn chạy kiểm thử |

*Ví dụ commit đúng chuẩn:*
- `feat(pom): add kyc verification methods to KycPage (Refs #2)`
- `test(e2e): add automated test cases for tutor kyc flow (Refs #2)`
- `fix(pom): improve explicit wait for toast alert in AuthPage (Refs #1)`

---

## 3. Vai trò & phân công 5 thành viên

### 3.1 Ma trận phân công nhiệm vụ 1:1

Để tránh xung đột, mỗi thành viên được phân công sở hữu độc quyền các tệp Page Object và Spec:

```
                  ┌──────────────────────────────────────────────┐
                  │          Thành viên A (Team Leader)          │
                  │   Framework Foundation, BasePage, AuthPage   │
                  │              Luồng 01 (10 TCs)               │
                  └──────────────────────┬───────────────────────┘
                                         │ Cung cấp framework
         ┌───────────────────┬───────────┴───────────┬───────────────────┐
         ▼                   ▼                       ▼                   ▼
   Thành viên B        Thành viên C            Thành viên D        Thành viên E
  KycPage.ts          TutorsPage.ts      ClassConnectionPage.ts   PaymentPage.ts
  CatalogPage.ts      FamilyPage.ts           SchedulePage.ts     AdminManagementPage.ts
  ProfilePage.ts     Luồng 04 & 05           Luồng 06 & 07        Luồng 08, 09, 10
  Luồng 02 & 03         (4 TCs)                 (4 TCs)               (6 TCs)
     (8 TCs)
```

| Thành viên | Vai trò trong nhóm | Page Objects phụ trách | Kịch bản Spec & Test Cases | Reviewer chính |
|:---:|---|---|---|:---:|
| **A (Leader)** | - Khởi tạo cấu hình Selenium & Vitest runner<br>- Duyệt PR tích hợp vào `develop`<br>- Luồng Xác thực & Phân quyền | `BasePage.ts`<br>`AuthPage.ts` | **Luồng 01:** `01-auth-flow.e2e.test.ts`<br>(TC_UI_01 → TC_UI_11) | **B** |
| **B** | - Luồng Nộp bằng cấp & Admin duyệt KYC<br>- Luồng Danh mục môn học & Hồ sơ cá nhân | `KycPage.ts`<br>`CatalogPage.ts`<br>`ProfilePage.ts` | **Luồng 02:** `02-kyc-flow.e2e.test.ts`<br>(TC_UI_15 → TC_UI_19)<br>**Luồng 03:** `03-catalog-profile-flow.e2e.test.ts`<br>(TC_UI_21, 22, 24) | **A** |
| **C** | - Luồng Khám phá gia sư & Gợi ý AI Match<br>- Luồng Mời liên kết gia đình Phụ huynh - Học sinh | `TutorsPage.ts`<br>`FamilyPage.ts` | **Luồng 04:** `04-tutors-discovery-flow.e2e.test.ts`<br>(TC_UI_26, 28)<br>**Luồng 05:** `05-family-link-flow.e2e.test.ts`<br>(TC_UI_30, 31) | **D** |
| **D** | - Luồng Đề nghị học & Đồng bộ lớp học 2 phía<br>- Luồng Lập lịch & Thời khóa biểu buổi học | `ClassConnectionPage.ts`<br>`SchedulePage.ts` | **Luồng 06:** `06-connection-classes-flow.e2e.test.ts`<br>(TC_UI_34, 35)<br>**Luồng 07:** `07-schedule-attendance-flow.e2e.test.ts`<br>(TC_UI_36, 37) | **C** |
| **E** | - Luồng Cổng nạp VietQR & Rút thu nhập<br>- Luồng Xem đánh giá gia sư<br>- Luồng Admin Dashboard & Nhật ký kiểm toán | `PaymentPage.ts`<br>`AdminManagementPage.ts` | **Luồng 08:** `08-billing-payout-flow.e2e.test.ts`<br>(TC_UI_43, 45)<br>**Luồng 09:** `09-review-flow.e2e.test.ts`<br>(TC_UI_50)<br>**Luồng 10:** `10-admin-management-flow.e2e.test.ts`<br>(TC_UI_51, 52, 54) | **A** |

### 3.2 Ma trận phụ thuộc thực thi giữa các luồng

```
Sprint 0 (Leader A): Hoàn thiện Foundation (BasePage, DriverFactory, vitest config, login-data.json)
                                        │
           ┌────────────────────────────┼────────────────────────────┐
           ▼                            ▼                            ▼
  Nhóm B: Luồng 02, 03         Nhóm C: Luồng 04, 05         Nhóm E: Luồng 10 (Admin)
 (Tutor KYC -> Admin duyệt)   (Khám phá & Gia đình)       (Dashboard & Audit logs)
           │                            │                            │
           └────────────────────────────┼────────────────────────────┘
                                        ▼
                               Nhóm D: Luồng 06, 07
                       (Gia sư đã duyệt -> Tạo lớp & Lịch)
                                        │
                                        ▼
                               Nhóm E: Luồng 08, 09
                         (Ví thanh toán & Đánh giá lớp)
```

> **Lưu ý nghiệp vụ:** Các thành viên B, C, D, E khi chạy kiểm thử độc lập sử dụng trực tiếp tài khoản seed có sẵn trong `resources/login-data.json` (`admin@edufit.vn`, `tutor@edufit.vn`, `student@edufit.vn`, `parent@edufit.vn`) nên có thể triển khai song song ngay mà không cần chờ dữ liệu từ các luồng khác.

---

## 4. Sprint 0 — Thiết lập nền tảng framework (Leader)

*Thực hiện bởi Leader (Thành viên A) trên nhánh `chore/0-e2e-framework-setup`:*

### Bước 1 — Cài đặt thư viện kiểm thử vào `frontend/package.json`
Đảm bảo các dependency sau có mặt trong `frontend/package.json`:
```bash
cd frontend
npm install --save-dev selenium-webdriver @types/selenium-webdriver vitest
```

### Bước 2 — Cấu hình Git Ignore cho thư mục báo cáo kiểm thử
Bổ sung vào `.gitignore` ở thư mục gốc và thư mục frontend:
```gitignore
# E2E Test reports and screenshots
frontend/tests/e2e/reports/screenshots/*.png
!frontend/tests/e2e/reports/screenshots/.gitkeep
```

### Bước 3 — Tạo khung BasePage & DriverFactory chuẩn
Leader đảm bảo `driver-factory.ts` và `BasePage.ts` hỗ trợ:
- Điều hướng URL với timeout an toàn.
- Chụp ảnh tự động `takeScreenshot(stepName)`.
- Chờ phần tử hiển thị (`waitForVisible`), chờ URL chứa từ khóa (`waitForUrlContains`).
- Xử lý tương tác Chrome ở chế độ Headless / Trực quan qua biến môi trường `E2E_HEADLESS`.

### Bước 4 — Mở PR nền tảng lên `develop`
Leader mở PR `chore/0-e2e-framework-setup → develop`, các thành viên review và squash merge để làm cột mốc cho toàn nhóm.

---

## 5. Sprint 0 — Thành viên chuẩn bị môi trường

Mỗi thành viên (B, C, D, E) thực hiện các bước sau trên máy của mình:

```powershell
# 1. Cấu hình thông tin Git cá nhân trùng với tài khoản GitHub
git config --global user.name "Nguyen Van B"
git config --global user.email "b.nguyen@edufit.vn"
git config --global core.autocrlf true

# 2. Đồng bộ nhánh develop mới nhất từ remote
git fetch origin
git switch develop
git pull origin develop

# 3. Cài đặt các gói npm ở frontend
cd frontend
npm install

# 4. Kiểm tra Selenium WebDriver với Google Chrome trên máy
npm run test:e2e
```
> Nếu Vitest khởi chạy thành công (dù chưa có test case nào hoặc chạy spec mẫu pass), môi trường đã sẵn sàng!

---

## 6. Quy trình thực hiện một ca kiểm thử (Vòng đời một issue)

Mọi thành viên tuân thủ nghiêm ngặt 8 bước sau:

```
[Issue trên Board] ──► Nhận việc & tạo nhánh `test/<n>-...`
       │
       ▼
[Viết Page Object] ──► Thêm phương thức trong `pages/<Tên>Page.ts`
       │
       ▼
[Viết Test Spec]   ──► Viết kịch bản trong `specs/<n>-<tên>-flow.e2e.test.ts`
       │
       ▼
[Chạy máy xác minh]──► `npx vitest run ...` đạt EXIT CODE 0
       │
       ▼
[Chụp ảnh bằng chứng]► Ảnh tự sinh trong `reports/screenshots/`
       │
       ▼
[Commit & Push]    ──► Conventional Commits, push lên nhánh cá nhân
       │
       ▼
[Mở Pull Request]  ──► Điền PR template, gắn link issue `Closes #<n>`
       │
       ▼
[Review & Merge]   ──► Reviewer góp ý, giải quyết thảo luận, Squash Merge
```

### Chi tiết các bước thực hiện:

#### Bước 1: Nhận Issue & Tạo nhánh từ `develop` mới nhất
```powershell
git switch develop
git pull origin develop
git switch -c test/2-kyc-verification-flow
```

#### Bước 2: Bổ sung phương thức vào Page Object tương ứng
- Mở file Page Object của mình (ví dụ: `frontend/tests/e2e/pages/KycPage.ts`).
- Định nghĩa các locator bền vững: ưu tiên `By.id(...)`, `By.css('[data-testid="..."]')`, `By.xpath("//button[contains(., '...')]")`.
- Viết các hàm hành động trả về `Promise<void>` hoặc `Promise<boolean>` rõ ràng.

#### Bước 3: Viết kịch bản kiểm thử trong thư mục `specs/`
- Mở hoặc tạo file spec: `frontend/tests/e2e/specs/02-kyc-flow.e2e.test.ts`.
- Sử dụng cấu trúc chuẩn:
  ```typescript
  import { describe, it, beforeAll, afterAll, expect } from 'vitest';
  import { WebDriver } from 'selenium-webdriver';
  import { DriverFactory } from '../drivers/driver-factory';
  import { KycPage } from '../pages/KycPage';

  describe('Luồng 02 - Quy trình nộp & duyệt hồ sơ KYC', () => {
    let driver: WebDriver;
    let kycPage: KycPage;

    beforeAll(async () => {
      driver = await DriverFactory.createDriver();
      kycPage = new KycPage(driver);
    });

    afterAll(async () => {
      if (driver) await driver.quit();
    });

    it('TC_UI_15: Gia sư nộp file giả mạo bị cảnh báo định dạng', async () => {
      // Các bước thực hiện
      await kycPage.navigateVerification();
      await kycPage.uploadCertificate('fake-script.pdf');
      await kycPage.takeScreenshot('TC_UI_15_fake_certificate');
      // Assertions
      const hasError = await kycPage.isErrorMessageDisplayed();
      expect(hasError).toBe(true);
    });
  });
  ```

#### Bước 4: Chạy máy xác minh độc lập
Chạy riêng spec đang phát triển để kiểm tra:
```powershell
npx vitest run tests/e2e/specs/02-kyc-flow.e2e.test.ts --config tests/e2e/vitest.e2e.config.ts
```
> Yêu cầu chốt chặn: **Exit code 0**, không có assertion nào bị fail.

#### Bước 5: Kiểm tra ảnh chụp bằng chứng
Mở thư mục `frontend/tests/e2e/reports/screenshots/` để xác nhận file ảnh PNG đã được tạo với tên đúng quy ước (`TC_UI_XX_...`).

#### Bước 6: Commit nhỏ và đẩy lên nhánh cá nhân
```powershell
git status
git add frontend/tests/e2e/pages/KycPage.ts frontend/tests/e2e/specs/02-kyc-flow.e2e.test.ts
git commit -m "test(kyc): implement automated ui test for verification queue" -m "Refs #2"
git push -u origin test/2-kyc-verification-flow
```

#### Bước 7: Mở Pull Request
- Base branch: `develop`.
- Head branch: `test/2-kyc-verification-flow`.
- Điền đầy đủ PR template: đính kèm kết quả test chạy máy và danh sách ảnh chụp.
- Gắn người review tương ứng theo phân công.

#### Bước 8: Tiếp thu review và Squash Merge
Sau khi được Reviewer approve, thực hiện **Squash and Merge** trên GitHub, xóa nhánh remote và cập nhật nhánh local.

---

## 7. Hướng dẫn chi tiết theo từng người & từng luồng kiểm thử

### 7.1 Thành viên A — Luồng 01: Xác thực & Phân quyền RBAC (Issue #1)
- **Files:** `pages/AuthPage.ts`, `specs/01-auth-flow.e2e.test.ts`.
- **Nội dung:** 10 Test Cases (TC_UI_01 → TC_UI_11).
- **Thao tác chính:**
  - Kiểm tra form hiển thị (`/login`).
  - Kiểm tra form chọn vai trò (`/register`).
  - Kiểm tra thông báo lỗi khi đăng nhập sai tài khoản.
  - Kiểm tra validation email sai cú pháp tại client.
  - Đăng nhập thành công với 3 vai trò: Admin, Gia sư, Học sinh; xác minh điều hướng vào đúng dashboard tương ứng.
  - Kiểm tra bảo mật: Khách vãng lai và Học sinh cố tình vào `/admin/dashboard` bị chặn và chuyển hướng.
- **Lệnh kiểm chứng máy:**
  ```powershell
  npx vitest run tests/e2e/specs/01-auth-flow.e2e.test.ts --config tests/e2e/vitest.e2e.config.ts
  ```

### 7.2 Thành viên B — Luồng 02 & 03: KYC Bằng cấp & Danh mục Môn học (Issue #2, #3)
- **Files:** `pages/KycPage.ts`, `pages/CatalogPage.ts`, `pages/ProfilePage.ts`, `specs/02-kyc-flow.e2e.test.ts`, `specs/03-catalog-profile-flow.e2e.test.ts`.
- **Nội dung:** 8 Test Cases (TC_UI_15 → TC_UI_19, TC_UI_21, 22, 24).
- **Thao tác chính:**
  - Luồng 02: Gia sư nộp file giả mạo `fake-script.pdf` → báo lỗi. Nộp `valid-certificate.pdf` → trạng thái chờ duyệt. Admin vào `/admin/verification-queue`, mở tab chờ duyệt, từ chối không có lý do bị chặn, phê duyệt hồ sơ thành công.
  - Luồng 03: Admin mở modal thêm môn học tại `/admin/catalogs`. Gia sư cập nhật tiểu sử, giá tiền, môn học tại `/tutor/profile`. Học sinh thiết lập nhu cầu học tại `/student/profile`.
- **Lệnh kiểm chứng máy:**
  ```powershell
  npx vitest run tests/e2e/specs/02-kyc-flow.e2e.test.ts --config tests/e2e/vitest.e2e.config.ts
  npx vitest run tests/e2e/specs/03-catalog-profile-flow.e2e.test.ts --config tests/e2e/vitest.e2e.config.ts
  ```

### 7.3 Thành viên C — Luồng 04 & 05: Khám phá gia sư & Liên kết gia đình (Issue #4, #5)
- **Files:** `pages/TutorsPage.ts`, `pages/FamilyPage.ts`, `specs/04-tutors-discovery-flow.e2e.test.ts`, `specs/05-family-link-flow.e2e.test.ts`.
- **Nội dung:** 4 Test Cases (TC_UI_26, 28, 30, 31).
- **Thao tác chính:**
  - Luồng 04: Khách vãng lai tìm kiếm và lọc gia sư theo môn học tại `/tutors`. Học sinh đăng nhập và bấm "Gợi ý AI Match" tại `/student/tutors`, danh sách gia sư hiển thị kèm điểm tương thích.
  - Luồng 05: Phụ huynh vào `/parent/children`, gửi lời mời liên kết tới `student@edufit.vn`. Học sinh vào `/student/family`, xem danh sách lời mời và chấp nhận.
- **Lệnh kiểm chứng máy:**
  ```powershell
  npx vitest run tests/e2e/specs/04-tutors-discovery-flow.e2e.test.ts --config tests/e2e/vitest.e2e.config.ts
  npx vitest run tests/e2e/specs/05-family-link-flow.e2e.test.ts --config tests/e2e/vitest.e2e.config.ts
  ```

### 7.4 Thành viên D — Luồng 06 & 07: Lớp học kèm cặp & Thời khóa biểu (Issue #6, #7)
- **Files:** `pages/ClassConnectionPage.ts`, `pages/SchedulePage.ts`, `specs/06-connection-classes-flow.e2e.test.ts`, `specs/07-schedule-attendance-flow.e2e.test.ts`.
- **Nội dung:** 4 Test Cases (TC_UI_34, 35, 36, 37).
- **Thao tác chính:**
  - Luồng 06: Gia sư vào `/tutor/requests` xem danh sách đề nghị học. Kiểm tra danh sách lớp học hiển thị đồng nhất tại `/tutor/classes` và `/student/classes`.
  - Luồng 07: Gia sư kiểm tra giao diện lập lịch tại `/tutor/schedule`. Học sinh kiểm tra thời khóa biểu học tập tại `/student/schedule`.
- **Lệnh kiểm chứng máy:**
  ```powershell
  npx vitest run tests/e2e/specs/06-connection-classes-flow.e2e.test.ts --config tests/e2e/vitest.e2e.config.ts
  npx vitest run tests/e2e/specs/07-schedule-attendance-flow.e2e.test.ts --config tests/e2e/vitest.e2e.config.ts
  ```

### 7.5 Thành viên E — Luồng 08, 09 & 10: Thanh toán, Đánh giá & Quản trị Admin (Issue #8, #9, #10)
- **Files:** `pages/PaymentPage.ts`, `pages/AdminManagementPage.ts`, `specs/08-billing-payout-flow.e2e.test.ts`, `specs/09-review-flow.e2e.test.ts`, `specs/10-admin-management-flow.e2e.test.ts`.
- **Nội dung:** 6 Test Cases (TC_UI_43, 45, 50, 51, 52, 54).
- **Thao tác chính:**
  - Luồng 08: Phụ huynh mở cổng thanh toán nạp tiền VietQR tại `/parent/billing`. Gia sư kiểm tra số dư và giao diện rút tiền tại `/tutor/earnings`.
  - Luồng 09: Gia sư xem điểm đánh giá và phản hồi học viên tại `/tutor/reviews`.
  - Luồng 10: Admin xem Dashboard thống kê tại `/admin/dashboard`, xem danh sách học viên tại `/admin/learners`, xem nhật ký kiểm toán tại `/admin/audit-logs`.
- **Lệnh kiểm chứng máy:**
  ```powershell
  npx vitest run tests/e2e/specs/08-billing-payout-flow.e2e.test.ts --config tests/e2e/vitest.e2e.config.ts
  npx vitest run tests/e2e/specs/09-review-flow.e2e.test.ts --config tests/e2e/vitest.e2e.config.ts
  npx vitest run tests/e2e/specs/10-admin-management-flow.e2e.test.ts --config tests/e2e/vitest.e2e.config.ts
  ```

---

## 8. Tận dụng Worktree `EduFit-system-e2e` làm Solution Reference

Trong repository này, nhánh `test/system-e2e-verification` tại worktree `EduFit-system-e2e` đóng vai trò là **chuẩn tham chiếu hoàn chỉnh (Solution Benchmark)**:

1. **Vị trí của worktree giải pháp:**
   `C:/University/Semester 5/SWP391/Edufit/EduFit-system-e2e`
2. **Khi nào cần tham khảo:**
   - Khi không chắc chắn về cấu trúc DOM hoặc selector CSS bền vững của một trang.
   - Khi gặp lỗi thời gian chờ (`TimeoutError`) và muốn đối chiếu cơ chế explicit wait chuẩn.
   - Khi cần kiểm tra các kịch bản mẫu và thứ tự gọi hàm trong test suite.
3. **Cách đối chiếu nhanh bằng lệnh:**
   ```powershell
   # So sánh trực tiếp file đang viết với file trong nhánh chuẩn
   git diff test/system-e2e-verification -- frontend/tests/e2e/pages/KycPage.ts
   ```

> **Nguyên tắc đạo đức & học tập:** Thành viên tham khảo để hiểu cơ chế hoạt động của Page Object Model, nhưng phải tự tay gõ mã nguồn, chạy kiểm chứng trên máy của mình và giải thích được từng dòng code khi bảo vệ trước Reviewer.

---

## 9. Pull Request & Code Review Checklist

Mỗi PR trước khi được merge phải được ít nhất 1 thành viên khác soi kỹ theo checklist sau:

### 9.1 Checklist dành cho Tác giả PR (Author)
- [ ] Mã nguồn tuân thủ đúng Page Object Model: không chứa trực tiếp câu lệnh `driver.findElement(...)` trong file spec.
- [ ] Chạy lệnh Vitest cục bộ đạt kết quả: `1 passed` với Exit Code 0.
- [ ] Đã kiểm tra thư mục `reports/screenshots/` có đủ ảnh chụp bằng chứng cho mọi Test Case.
- [ ] Không chứa file rác, file `.env` cá nhân, hoặc file log.
- [ ] Tiêu đề PR đúng chuẩn Conventional Commits (ví dụ: `test(kyc): add automated test cases for tutor verification queue`).

### 9.2 Checklist dành cho Reviewer
- [ ] **Tính bền vững của Selector:** Có dùng Tailwind classes dễ đổi như `.bg-blue-600` hay `.px-4` không? Yêu cầu đổi sang `data-testid`, `name`, hoặc text chuẩn.
- [ ] **Chống Sleep tĩnh:** Có đoạn code nào dùng `new Promise(resolve => setTimeout(resolve, 3000))` không? Bắt buộc thay thế bằng `driver.wait(until.elementLocated(...))` trong `BasePage`.
- [ ] **Tính toàn vẹn của Assertions:** Các assertion (`expect(...)`) có kiểm tra đúng kết quả nghiệp vụ không, hay chỉ assert `true === true` đối phó?
- [ ] **Dọn dẹp tài nguyên:** Khối `afterAll` có đảm bảo gọi `await driver.quit()` để không bị rò rỉ tiến trình Chrome chạy ngầm không?

---

## 10. Xử lý xung đột (Merge Conflict)

### 10.1 Biện pháp phòng tránh
1. **Phân chia độc quyền:** Mỗi thành viên chỉ chỉnh sửa Page Object và Spec thuộc phạm vi của mình (đã phân công ở mục 3.1).
2. **File dùng chung (`BasePage.ts`, `vitest.e2e.config.ts`, `package.json`):** Chỉ Leader A được phép chỉnh sửa. Nếu thành viên khác cần thêm method dùng chung vào `BasePage`, phải báo cho Leader qua issue hoặc PR riêng.
3. **Rebase hàng ngày:** Trước khi bắt đầu viết test mỗi buổi sáng, rebase với `origin/develop`:
   ```powershell
   git fetch origin
   git rebase origin/develop
   ```

### 10.2 Quy trình giải quyết khi bị xung đột trong lúc rebase
```powershell
# 1. Khi Git dừng lại và báo xung đột:
git status

# 2. Mở các file bị conflict, tìm đoạn:
# <<<<<<< HEAD (mã trên develop)
# =======
# >>>>>>> (commit của bạn)

# 3. Chỉnh sửa giữ lại nội dung đúng nhất của cả hai bên.
# 4. Đánh dấu đã sửa và tiếp tục rebase:
git add <file-đã-sửa>
git rebase --continue

# 5. Chạy lại test suite để đảm bảo mã vẫn xanh:
npm run test:e2e

# 6. Đẩy lại lên nhánh cá nhân:
git push --force-with-lease
```

---

## 11. Kế hoạch sprint gợi ý & Definition of Done

### 11.1 Lộ trình triển khai (3 Sprint)

| Giai đoạn | Thời lượng | Mục tiêu hoàn thành | Kết quả bàn giao |
|---|---|---|---|
| **Sprint 0** | Ngày 1–2 | Nền tảng framework Selenium, BasePage, DriverFactory, cấu hình Vitest, login data | Nhánh `develop` chạy được spec mẫu, Chrome headless hoạt động |
| **Sprint 1** | Ngày 3–7 | Triển khai Luồng 01 (Auth), Luồng 02 (KYC), Luồng 03 (Catalog), Luồng 04 (Discovery), Luồng 05 (Family) | 5 Luồng cốt lõi hoàn thành, sinh đủ ảnh bằng chứng cho 26 TCs |
| **Sprint 2** | Ngày 8–12 | Triển khai Luồng 06 (Lớp học), Luồng 07 (Lập lịch), Luồng 08 (Ví), Luồng 09 (Review), Luồng 10 (Admin) | Toàn bộ 10 Luồng hoàn tất (32 TCs), chạy full test suite xanh |
| **Sprint 3** | Ngày 13–14 | Rà soát toàn diện, xuất báo cáo HTML/Word, kiểm thử hồi quy (Regression Test) | Báo cáo nghiệm thu hoàn chỉnh nộp học phần |

### 11.2 Definition of Done (DoD)
Một ca kiểm thử chỉ được đóng và nghiệm thu khi:
1. Đã bóc tách đủ các bước và kết quả mong đợi theo tài liệu đặc tả.
2. Mã nguồn Page Object và Test Spec nằm trên nhánh feature, đã qua review của đồng đội.
3. Chạy `npm run test:e2e` cục bộ trả về Exit Code 0.
4. Ảnh chụp màn hình tại các bước quan trọng lưu đầy đủ tại `reports/screenshots/`.
5. PR được Squash Merge vào nhánh `develop`.

---

## 12. Phụ lục: Template mẫu & Cấu hình CI

### 12.1 Template Issue cho Test Case Mới (`.github/ISSUE_TEMPLATE/test_case.md`)
```markdown
---
name: Web UI Test Case
about: Đề xuất triển khai ca kiểm thử tự động giao diện
title: "[TEST] Luồng XX - TC_UI_YY: <Tên ngắn gọn>"
labels: "type: test, module: ui-e2e"
---

## 1. Thông tin ca kiểm thử
- **Mã Test Case:** TC_UI_...
- **Luồng:** Luồng ...
- **Trang / URL kiểm thử:** `/...`
- **Vai trò người dùng:** Guest / Học sinh / Gia sư / Phụ huynh / Admin

## 2. Tiêu chí kiểm thử
- [ ] Bước 1: Điều hướng tới trang ...
- [ ] Bước 2: Thao tác nhập liệu / click ...
- [ ] Bước 3: Chụp ảnh bằng chứng tên `TC_UI_...`
- [ ] Bước 4: Assertion xác minh kết quả hiển thị

## 3. Page Object liên quan
- Cần bổ sung phương thức trong `frontend/tests/e2e/pages/...Page.ts`
```

### 12.2 Template Pull Request Kiểm Thử (`.github/pull_request_template.md`)
```markdown
## Mô tả thay đổi
Triển khai kịch bản kiểm thử tự động Web UI cho:
Closes #<Số_Issue>

## Các Test Cases đã hoàn thành
- [x] TC_UI_XX: <Mô tả>
- [x] TC_UI_YY: <Mô tả>

## Kết quả kiểm chứng tự động (Machine Verification)
- Lệnh thực thi: `npx vitest run tests/e2e/specs/...`
- Kết quả: `Exit Code 0 (X passed)`
- Ảnh chụp màn hình đã sinh ra tại: `frontend/tests/e2e/reports/screenshots/`
  1. `TC_UI_XX_....png`
  2. `TC_UI_YY_....png`

## Checklist tự kiểm tra
- [x] Không hardcode selector động, không dùng class Tailwind dễ vỡ
- [x] Dùng explicit wait qua BasePage, không dùng `setTimeout` tùy tiện
- [x] Đã gọi `driver.quit()` trong `afterAll`
```

---

## 13. Cheat sheet lệnh Git & Lệnh chạy Selenium Test

### Lệnh Git làm việc nhóm hàng ngày:
```powershell
# 1. Bắt đầu ngày mới: cập nhật develop
git switch develop
git pull origin develop

# 2. Tạo nhánh làm việc cho một issue
git switch -c test/3-catalog-profile-flow

# 3. Xem các file đang thay đổi
git status

# 4. Thêm file và commit đúng chuẩn
git add frontend/tests/e2e/pages/CatalogPage.ts frontend/tests/e2e/specs/03-catalog-profile-flow.e2e.test.ts
git commit -m "test(catalog): add automated specs for admin subject creation (Refs #3)"

# 5. Đẩy nhánh lên GitHub để mở PR
git push -u origin test/3-catalog-profile-flow
```

### Lệnh thực thi kiểm thử Selenium E2E:
```powershell
# Di chuyển vào frontend
cd frontend

# Chạy toàn bộ 10 kịch bản kiểm thử E2E (chạy tuần tự):
npm run test:e2e

# Chạy kiểm thử có hiển thị trình duyệt Chrome thật (Trực quan):
$env:E2E_HEADLESS="false"; npm run test:e2e

# Chạy kiểm thử ngầm (Headless):
$env:E2E_HEADLESS="true"; npm run test:e2e

# Chạy một kịch bản đơn lẻ (Ví dụ Luồng 02):
npx vitest run tests/e2e/specs/02-kyc-flow.e2e.test.ts --config tests/e2e/vitest.e2e.config.ts
```

---

## 14. Checklist làm việc nhóm kiểm thử

Trước khi nghiệm thu toàn bộ bài tập lớn học phần:

- [ ] Cả 5 thành viên đều có các commit và Pull Request được merge vào nhánh `develop`.
- [ ] Toàn bộ 10 kịch bản kiểm thử (`01-auth-flow` đến `10-admin-management-flow`) đều chạy thành công trên môi trường tích hợp.
- [ ] Thư mục `reports/screenshots/` có đầy đủ ảnh chụp màn hình tương ứng với từng Test Case theo đúng định dạng.
- [ ] Báo cáo Word / Markdown tổng kết phản ánh trung thực kết quả chạy máy với số liệu cụ thể.
- [ ] Nhánh `develop` luôn trong trạng thái build thành công và test xanh 100%.
