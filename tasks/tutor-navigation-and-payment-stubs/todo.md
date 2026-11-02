# Thành viên 3 — Executable tasks

Đọc cùng [plan.md](plan.md). Nhánh `fix/tutor-navigation-and-payment-stubs`; base `d904f79a7de5f1ac5235036eefb8eb6dd58fd6de`. Ngày18/10/2026. **Chỉ planning, chưa code.**

Có **11 task M3-00–M3-10**: 1 preparation/audit đã xong và 10 task còn lại. Chỉ tick khi có evidence, không tính phase/checkpoint thành task thêm. Các file ghi “mới” là đề xuất, chưa tồn tại.

## Phase 0 — Base và prerequisites

### [x] M3-00 — Pull main, tạo nhánh và audit phân công

- Đã fetch origin/pull --ff-only origin main, main=origin/main=base, branch tạo từ main sạch.
- Đã đọc `D:/edufit_work_breakdown_plan.md`, SRS UC5.5/Payment, D5/SADS/schema và source/nav/contracts.
- Phát hiện API/SDK/requests page chỉ có trên develop, không tự nhập về; đã ghi gate trong plan.
- Verification: Git status/refs và source tree inventory; không coi source audit là browser test.
- Không có runtime commit cho task chuẩn bị. Hai file plan/todo là output cần review.

### [x] M3-01 — Chốt prerequisite integration và contract History/Requests

- **Dependency:** M3-00; quyết định team về nguồn đưa code vào main/branch fix.
- **Scope:** biên bản gate/contract; không tự thực hiện broad merge hoặc viết Progress/Scheduling mới.
- **Acceptance:**
  1. G-REQUESTS có destination thực/nguồn component+SDK đúng base; G-HISTORY có danh sách source commit và dependencies/API contract thật.
  2. Map summary-class vs session detail/notes/plan; ghi field thiếu (subject/last session/global counts), date/status filtering/paging và auth ownership.
  3. Phương án integration/owner và checks được chốt; nếu chưa sẵn, đánh dấu gate pending và không mở task phụ thuộc.
- **Files:** plan/todo, `docs/tutor-navigation-and-payment-stubs/integration-contract.md` (mới); source/API files chỉ liệt kê sau audit dependency cụ thể.
- **Verify:** source SHA/contract examples + owner-module compatibility; nếu port API/backend thì cần checklist/commit prerequisite riêng đã chốt trước khi code.
- **Commit dự kiến (docs contract):** `docs(tutor): record history and requests integration prerequisites`.

**Checkpoint0:** chọn base main vẫn đúng; không giả định develop content đã có. Research/Payment không phụ thuộc gate code.

## Phase 1 — Research 3.1

### [x] M3-02 — Evidence và ma trận policy/conflict

- **Dependency:** M3-00; có thể làm trong khi gate History pending.
- **Acceptance:**
  1. Trace D5/SADS/UC4.5/SRS schema cũ, nêu payment journal vs payment gateway và conflict.
  2. Có so sánh per-session/tuần/tháng, actors/consent và decision register proposed/approved/open.
  3. Không số % hoàn/phạt/24h tự coi là rule đã duyệt, không chỉnh DB/SRS gốc.
- **Files:** `docs/tuition/tuition-policy-and-dispute-specification.md` (draft mới), bảng source/conflict trong appendix, todo.
- **Verify:** đối chiếu từng claim với source/assumption; review scope còn chỉ docs.
- **Commit:** `docs(tuition): audit payment scope and policy decisions`.

### [x] M3-03 — Hoàn thiện Tuition Policy & Dispute Specification và wireframe flow

- **Dependency:** M3-02.
- **Acceptance:**
  1. Đủ mô hình vận hành, outcome vs tuition confirmation vs reconciliation vs dispute, quyền/bằng chứng/lưu vết và ví dụ.
  2. Ma trận no-show/hủy sớm-muộn/lỗi kỹ thuật và đề xuất complaint24h/hoàn-phạt có cột cần team duyệt; không hứa escrow/auto-refund thật.
  3. Readable screen flow/wireframe appendix, acceptance scenarios và backlog phase sau; chưa code thanh toán.
- **Files:** Tuition spec, `docs/tuition/tuition-proposed-screen-flow.md` (mới), todo.
- **Verify:** walkthrough trường hợp hai bên và Parent revoked; label proposal nhất quán, source links đúng; không coi chưa phản hồi là đã đồng ý tiền.
- **Commit:** `docs(tuition): propose reconciliation safeguards and dispute flows`.

**Checkpoint1:** research draft đủ để team quyết định; không đánh dấu policy proposed thành rule sản phẩm.

## Phase 2 — Payment stubs 3.2

### [x] M3-04 — Ba route tài chính chỉ đọc và shared notice

- **Dependency:** M3-00; plan đã review. Không cần chờ Progress.
- **Acceptance:**
  1. `/student/payments`, `/parent/billing`, `/tutor/earnings` có page dùng đúng role layout, vào trực tiếp không404.
  2. Notice “Sắp ra mắt”, phí thỏa thuận trực tiếp; link về dashboard role, không số dư/hóa đơn/nút giao dịch giả/API tài chính.
  3. Loading/auth/layout/responsive phù hợp; không thêm Payment DB/backend.
- **Files dự kiến:** 3 `frontend/src/app/{role}/{payments|billing|earnings}/page.tsx` mới; shared read-only notice mới (vị trí theo convention frontend); test notice/role wrappers.
- **Verify:** focused component/render tests và type-check; browser direct URL/auth theo role khi server test sẵn sàng.
- **Commit:** `fix(payments): add honest coming-soon pages for finance routes`.

### [x] M3-05 — Sidebar badge và Payment CTA nhất quán

- **Dependency:** M3-04.
- **Acceptance:**
  1. Badge “Sắp ra mắt” cho3menu; collapse/Suspense fallback không mất ngữ nghĩa hoặc gán nhầm active state.
  2. CTA Payment tại StudentQuickActions/ParentQuickActions/ParentDashboardView cùng trỏ stubs và copy không giả hành động nạp/thanh toán đã hoạt động.
  3. Các role/menu/chat/family/Admin entries ngoài scope giữ nguyên.
- **Files:** Sidebar.tsx,3dashboard files nêu trên; test navigation/labels.
- **Verify:** route inventory trước/sau, keyboard/tooltip/mobile/collapsed+fallback và role smoke; frontend focused tests.
- **Commit:** `fix(navigation): label unavailable payment menus and actions consistently`.

**Checkpoint2:** mọi điểm vào Payment trong scope có đích thông tin thật; gắn badge nhưng vẫn404 không đạt.

## Phase 3 — Tutor navigation và History 3.3

### [x] M3-06 — Sửa dashboard tới hàng đợi thực

- **Dependency:** G-REQUESTS đã đạt theo M3-01. Có thể làm trước History khi destination sẵn.
- **Acceptance:**
  1. Onboarding step “Xem hàng đợi” trỏ `/tutor/requests`, không self-loop.
  2. Destination có route/component thật, load requests qua API đúng contract; lỗi API không giả thành queue rỗng.
  3. Sidebar/TutorPendingRequests cùng destination, không phát sinh404.
- **Files:** TutorDashboardView.tsx + regression navigation; request page/feature imports chỉ sửa nếu thuộc prerequisite đã duyệt.
- **Verify:** click flow bằng Tutor session qua browser; href regression và API responses thực.
- **Commit:** `fix(tutor): route dashboard request actions to the real inbox`.

### [x] M3-07 — Typed History API client và read hooks

- **Dependency:** G-HISTORY/M3-01 và upstream contracts đã sẵn.
- **Acceptance:**
  1. Reuse Progress SDK đang được tích hợp, typed summary/session/notes/plan/Page envelope; không tạo SDK trùng hoặc đổi auth.
  2. Filter/page/date mapping đúng server semantics; phân biệt class status/session status, abort hoặc ignore stale responses.
  3. Empty/error/401/403/missing-plan xử lý khác nhau; note read không lấy data ngoài selected class.
- **Files:** feature progress/history types, API adapter nếu thiếu, hook/read-state và focused tests (~3–5 file).
- **Verify:** URL/query/envelope/date boundaries/reordered response/error tests; không mock response production.
- **Commit:** `feat(tutor): connect typed teaching history reads and filters`.

### [x] M3-08 — History class summary và menu entry

- **Dependency:** M3-07.
- **Acceptance:**
  1. `/tutor/history` và Tutor Sidebar có entry; chỉ Tutor, danh sách lớp/student/goal/status/counts/progress từ API.
  2. Server-supported student/class/date filters, pagination/reset và loading/empty/error/retry; chưa đủ metadata không bịa0 hoặc total toàn Tutor từ một page.
  3. Responsive/keyboard/labels, chọn class cho detail; không dùng class time làm session time.
- **Files:** route mới, history view/filter/table components theo convention, Sidebar entry và view tests; tránh vượt scope5file bằng reuse component hợp lý.
- **Verify:** summary states, paging/filter và no-history/unknown-stats tests; browser/API smoke.
- **Commit:** `feat(tutor): add scoped teaching history overview`.

### [x] M3-09 — Session/notes detail và read-only plan

- **Dependency:** M3-08, session/notes/plan contract và server ownership đã chốt.
- **Acceptance:**
  1. Selected class hiển thị sessions/date/status/attendance outcome và notes/plan đúng scope; không chỉ có những buổi đã có ghi chú nếu requirement cần đủ sessions.
  2. Filter session/date không lẫn class status; notes plain text, thiếu note/plan có empty state thật, giữ filter/page khi quay lại.
  3. Tutor khác class bị server từ chối; không email/PII ngoài lớp, không ghi/sửa outcome từ History.
- **Files:** detail/read hook hoặc component/session-note/plan panel và tests (~3–5file).
- **Verify:** data ownership403, escaping/XSS string, no-note/no-plan, stale selection, date/status filters; browser selection/back/retry và actual backend source.
- **Commit:** `feat(tutor): show scoped session notes in teaching history`.

**Checkpoint3:** UC5.5 class → sessions/notes hoạt động trên backend thật; requests không404. Nếu prerequisite thiếu, chưa đạt checkpoint dù UI fixtures pass.

## Phase 4 — Verification và bàn giao

### [x] M3-10 — Scoped regression, browser evidence và final report

- **Dependency:** M3-03/M3-05/M3-06/M3-09 đạt acceptance; blocker phải ghi riêng.
- **Acceptance:**
  1. Frontend type-check/lint/test/build, browser direct-url/nav/history states và auth checks có kết quả thật, không nhận not-run/skipped là pass.
  2. Backend nếu chạm API/prerequisite phải verify modules + consumers; source-main snapshot và conflict/ownership review rõ, không merge vào main/develop tự động.
  3. Research đề xuất vs rule đã chốt, route trước/sau, contract gaps/dependencies, commits và residual risks được ghi đầy đủ.
- **Files:** `docs/tutor-navigation-and-payment-stubs/verification-report.md` (mới), plan/todo và test gaps phù hợp; không runtime feature mới trong docs task.
- **Verify:** npm scripts ở frontend, Maven affected verify nếu cần, real browser; git diff --check + owned scope diff.
- **Commit:** `docs(tutor): record navigation history and tuition verification`.

## Workflow sau khi plan được review

- Mỗi task code phải đọc frontend/AGENTS và local Next docs trước; scope/task-owned changes, regression có giá trị và affected checks.
- Stage đúng paths, một commit theo task, push `origin fix/tutor-navigation-and-payment-stubs` theo workflow team; không force push/merge/deploy.
- Báo task, commit, push status, checks/pass/fail/skip/not-run và gate cần phối hợp. Không gộp các nhóm3.1–3.3 trong một commit.
- Nếu owner chưa cung cấp prerequisite, tiếp tục research/payment độc lập, báo History/Requests chưa hoàn thành; không tự mở rộng thành refactor backend.

## Evidence lượt planning

- Main đã cập nhật và branch đã tạo; working tree sạch trước khi tạo plan/todo.
- Source main và origin/develop đã đối chiếu; source docx đọc bằng XML để xác minh UC5.5/Payment vs D5.
- Chưa sửa file runtime, chưa tích hợp develop, chưa chạy browser/build/tests, chưa triển khai Payment/History/navigation hoặc spec học phí cuối.
