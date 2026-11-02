# Payment: kế hoạch chi tiết theo phase và task

> Quy tắc hiện hành: [quy tắc 26/10](../../docs/payment/payment-usability.md). Các phase/checkpoint cũ bên dưới giữ lịch sử; xác nhận không thu sớm, 48h chưa khớp giữ tiền chờ Admin.


Cập nhật 24/10/2026, nhánh `feat/payment`, đối chiếu code `3966d54`. Nguồn nghiệp vụ/kiến trúc: [Payment implementation plan](../../docs/payment/payment_implementation_plan.md). Theo cách tổ chức [Discovery](../plan.md): mỗi task có phạm vi, kiểm chứng và checkpoint; giữ ID/commit lịch sử Payment để không tạo số task giả.

## 1. Nguyên tắc thực hiện

1. Chỉ làm trong repository EduFit, branch `feat/payment`; không merge main/develop trong các task này.
2. Đọc phần doc/code liên quan trước task; giữ migration đã áp dụng, dùng migration mới.
3. Một increment có code/test tương ứng → kiểm chứng → review diff → commit → push `origin feat/payment`.
4. Không gom code ngoài task, không stage `.env`, secret hoặc artifact kiểm thử.
5. Nếu task cần nhiều commit để giữ build xanh, ghi rõ commit sửa lỗi/bổ sung; không tự tăng số phase hoặc đổi ID.
6. Chỉ đánh dấu hoàn thành khi có bằng chứng. Test mock không thay thế kiểm chứng merchant/live.

Đây là kế hoạch bàn giao phản ánh phần đã làm, không phải yêu cầu chạy lại mọi phase. [Todo](todo.md) giữ trạng thái; [verification-report](../../docs/payment/verification-report.md) giữ chi tiết thực chạy.

## 2. Đối chiếu yêu cầu và task

| Yêu cầu | Task | Bằng chứng cần có |
|---|---|---|
| Ví owner, available/held và ledger | P02 | Đủ/thiếu số dư, rollback, race |
| Chốt giá từng buổi và payer | P02, P04b | Giá đổi bị từ chối; refund về payer |
| 1h chỉ auto learner | P01, P02 | Domain boundary + PG flow |
| 24h và hai báo cáo thống nhất | P01, P02, P04b, P05 | Deadline/partial/zero/Admin |
| Commission 10% | P01, P02 | Chỉ trên settled amount, làm tròn xuống |
| QR/webhook, manual verify | P03 | Chữ ký/receipt/reference/idempotency |
| Reconcile nạp khi mất webhook | P08 | Owner/Admin, lease, signed lookup |
| Tutor rút, Admin review | P03, P09 | Reserve, manual hoặc payout, timeout giữ tiền |
| FE bốn vai trò, chi tiết lịch | P04a–P05, P10 | Behavior/browser/quyền/mobile |
| Notification nguyên tử | P08 | Sink failure rollback, duplicate một notify |
| Config không secret | P07, P11 | Readiness Admin-only, env/Compose, setup |
| Schema/kiến trúc/kiểm chứng | P06, P11 | Fresh/upgrade, startup, report |

## 3. Task chi tiết từng phase

### Phase 0 — Baseline và quy tắc

**P00 — Audit và chốt phạm vi**

- Đầu vào: develop `b1ea5cd`, yêu cầu ví/tranh chấp và xác nhận commission 10%.
- Việc làm: kiểm branch/dirty files, đọc docs tuition và facade Profile/Connection/Scheduling; ghi plan, actor, deadlines, scope FE/BE.
- AC: chỉ feat/payment; phân biệt tiền nội bộ/ngân hàng; không fake credit; task/kiểm chứng rõ.
- Commit: `435da1b`.
- Checkpoint 0: yêu cầu và base đủ để triển khai không tự đoán tỷ lệ phí.

### Phase 1 — Lõi nghiệp vụ

**P01 — Domain xác nhận/tranh chấp**

- Phụ thuộc P00. File chính: `SessionCharge.java`, `SessionChargeTest`.
- Việc làm: FUNDED/confirmation/dispute/Admin/terminal; tick 1h/24h; report mức tiền 0..amount.
- AC: chưa COMPLETED không tất toán; auto learner không auto tutor; đúng biên 24h từ chối report; báo cáo khớp chờ Admin; partial/zero/confirm lặp đúng.
- Kiểm chứng: RED/GREEN domain tests và compile module.
- Commit: `73195ff`.
- Checkpoint 1: quy tắc thời gian/state chạy độc lập Spring/DB.

### Phase 2 — Schema, ledger và học phí

**P02 — Reserve và settlement nguyên tử**

- Phụ thuộc P01 và facade nguồn. File chính: V1.16, PaymentStore, PaymentAccess, SessionPaymentService, PaymentTime/deadline job.
- Việc làm: ví/ledger/audit/mutex; snapshot giá/fee; cấp tiền, confirm/report/Admin review; deadline/refund đúng payer.
- AC: một charge/buổi; không âm available/held; parent confirmed/payer/student/tutor đúng quyền; fee 10% phần thực tất toán; unique key/race bảo vệ một lần; không HTTP khi giữ khóa.
- Kiểm chứng: PG flow/concurrency/rollback; migration và architecture; startup cả app phát hiện Clock ambiguity.
- Commit chính `a4d4ea4`; startup/time source `db23f8f`.
- Checkpoint 2: ghi tiền/state/audit cùng transaction; Clock riêng không xung đột IAM.

### Phase 3 — Nạp, rút thủ công và Admin BE

**P03 — Kênh nạp và đối soát ban đầu**

- Phụ thuộc P02. File chính: WalletService, PayosGateway, controllers, PaymentSettings/Store.
- Việc làm: tạo checkout/QR; signed webhook; manual bank-confirm; tutor withdrawal/reserve; Admin manual review và audit.
- AC: QR không credit; đúng receipt mới credit; reference duy nhất/trim; webhook không thay manual; rút chỉ tutor, thiếu số dư bị chặn; manual paid chỉ sau chuyển ngân hàng thật.
- Kiểm chứng: gateway/access/validation/wallet tests, PG history/ref regression, startup/browser.
- Commit chính `5deafa9`; history query `3f5b4d7`; dispute pagination `2c5c105`.
- Checkpoint 3: nạp/rút/manual API dùng được với lịch sử và phân trang thật.

### Phase 4 — FE Student/Parent/Tutor

**P04a — SDK và form ví**

- Phụ thuộc P03. Thêm types/SDK và WalletPanel; nạp/rút/lịch sử, capability thiếu config.
- AC: dữ liệu API, số tiền hợp lệ, đúng người thụ hưởng, QR không tự credit, loading/error/pending.
- Kiểm chứng: form behavior/type-check/lint. Commit `c32f518`.

**P04b — Thẻ học phí và tranh chấp**

- Phụ thuộc P04a. Thêm SessionPaymentCard/SDK fund/confirm/dispute/report.
- AC: hiển thị giá trước reserve; learner/tutor đúng quyền; deadline và mức báo cáo rõ; không double-submit.
- Kiểm chứng: 6 behavior cases của thẻ học phí trong gate FE. Commit `6fb85d9`.

**P04c — Workspace và chi tiết lịch**

- Phụ thuộc P04b. Nối Student payments, Parent billing, Tutor earnings và SessionBillingPanel trong lịch.
- AC: lọc kỳ, sổ/lịch sử, empty/error/loading, parent wallet riêng, không localStorage balance.
- Kiểm chứng: type-check/build và browser bốn vai trò cùng P05. Commit `0cb5c2a`.
- Checkpoint 4: ba task FE riêng, cùng một phase, khớp lịch sử P04a/P04b/P04c.

### Phase 5 — Admin FE và navigation

**P05 — Đối soát và duyệt báo cáo**

- Phụ thuộc P03/P04c. Thêm PaymentAdminWorkspace, routes payments/disputes và navigation.
- AC: manual receipt/reference/ghi chú rõ; xem hai báo cáo và chỉ duyệt đúng mức; bỏ redirect/stub/KPI tiền giả.
- Kiểm chứng: Admin forms, sidebar/CTA, quyền API403, browser.
- Commit `e80751b`.
- Checkpoint 5: Admin thao tác được trên dữ liệu thật của DB test.

### Phase 6 — Kiểm chứng và bàn giao bản đầu

**P06 — Integration, schema và tài liệu vận hành**

- Phụ thuộc P00–P05. Đồng bộ DBML, API/runbook/report, ghi giới hạn live/provider.
- AC: domain/PG/architecture/startup và FE gates có bằng chứng, không claim full repository nếu chỉ test phạm vi.
- Kiểm chứng: migration fresh/no-op; PG transaction/concurrency; build/browser. Chi tiết số lượng trong report.
- Commit `db147f9`. Hotfix local DB `1192d79` là lỗi kết nối môi trường, không sửa checksum migration.
- Checkpoint 6: bản đầu bàn giao được; payout khi đó còn thủ công, được mở rộng ở P09.

### Phase 7 — Cấu hình provider

**P07 — Env và readiness**

- Phụ thuộc P06. Sửa .env.example/Compose passthrough, PaymentSettings/readiness và Admin API.
- AC: khóa để trống/`...` không bật provider; thiếu khóa vẫn khởi động; readiness không trả secret và không khẳng định khóa hợp lệ.
- Kiểm chứng: settings tests, thiếu cấu hình, Admin authorization.
- Commit `de6b336`.
- Checkpoint 7: người vận hành thấy được kênh nào đã đủ cấu hình.

### Phase 8 — Nạp phục hồi và thông báo

**P08 — Signed lookup và notification nguyên tử**

- Phụ thuộc P07. PayosTransport/Gateway, PaymentReconciliationService, ProviderJob, Store/NotificationSink, lease topup V1.17.
- AC: response ký đúng; order/amount/account/link/reference khớp; owner/Admin; lease 30s; webhook/query lặp không credit/notify hai lần; sink lỗi rollback tiền; HTTP ngoài khóa.
- Kiểm chứng: transport/gateway/reconciliation tests và PG duplicate/notification rollback.
- Commits: transport `d16e468`, triển khai chung P08/P09 `710b9af`.
- Checkpoint 8: webhook chậm có đường phục hồi bằng dữ liệu server-to-server.

### Phase 9 — Admin duyệt chi ngân hàng

**P09 — Payout và phục hồi kết quả**

- Phụ thuộc P07/P08. PayosPayoutGateway, PaymentPayoutService, metadata/state/lease rút V1.17.
- AC: khóa kênh chi riêng, có BIN; PROCESSING trước HTTP; UUID reference/key cố định; chỉ create một lần; timeout/mismatch không refund/repost; đúng terminal mới PAID/FAILED; chặn manual sau dispatch.
- Kiểm chứng: approve đồng thời, timeout, signature/header/key, lookup success/failure/mismatch; upgrade dữ liệu cũ không BIN tiếp tục manual.
- Commit chung `710b9af`; test bổ sung `354cfbb`.
- Checkpoint 9: code an toàn trước response không chắc chắn; chưa tuyên bố live payout hoạt động.

### Phase 10 — FE provider, thông báo và mobile

**P10 — Hoàn thiện trải nghiệm đối soát**

- Phụ thuộc P08/P09. Refresh topup, bank name/BIN, readiness/payout/reconcile Admin, route notification và DashboardShell mobile dialog.
- AC: PROCESSING chỉ cho reconcile; chọn đúng BIN; không nhầm config-ready với kết nối đã thành công; bốn role dùng được; mobile không overflow/menu dùng bàn phím được.
- Kiểm chứng: 25 tests/8 files ở gate cuối; type-check/lint/build; Chrome desktop/mobile với BE và DB test.
- Commits: mobile fix `a3dfd37`, FE provider `31ccac8`.
- Checkpoint 10: UI phản ánh trạng thái tiền do backend quyết định.

### Phase 11 — Bàn giao cuối và cấu hình

**P11 — Setup/API/schema/report**

- Phụ thuộc P10. Hướng dẫn 5 biến kênh thu, webhook, kênh chi riêng, xử lý unknown payout; đồng bộ schema/API/report.
- AC: không commit secret; phân biệt hoàn ví/hoàn ngân hàng và thu nhập/rút; giới hạn live rõ; commit đã push đúng nhánh.
- Commit `3966d54`.
- Checkpoint 11: code đã bàn giao, merchant thật còn do người vận hành cấu hình.

## 4. Cổng kiểm chứng trước commit tiếp theo

- Kiểm `git status`, branch và diff; chỉ stage phần task.
- Code thay đổi: chọn tests theo behavior/risk; migration phải chạy DB test; FE đổi behavior phải test tương ứng.
- Ngoại lệ được người dùng yêu cầu ngày 24/10: chưa chạy test hoặc gọi payOS khi tích hợp khóa thật. Hotfix V1.18 chỉ biên dịch main/test source; kiểm chứng migration và regression vẫn để mở trong checklist, không báo PASS thay cho kết quả chưa chạy.
- Docs-only: đối chiếu code/contract, link nội bộ, code fence, task ID/SHA và `git diff --check`; không cần khởi động lại tiền/DB chỉ để đổi Markdown.
- Ghi kết quả thực chạy, điểm chưa kiểm chứng và commit/push; không sửa lại lịch sử SHA đã bàn giao.

## 5. Việc vận hành còn chờ, không tính là task code đã xong

1. Đăng ký/xác thực merchant và xác nhận mô hình nghiệp vụ được phép.
2. Điền khóa kênh thu và đúng tài khoản nhận vào env, restart backend.
3. Cấu hình HTTPS public webhook, kiểm tra đăng ký webhook và receipt thật.
4. Nếu dùng payout: kích hoạt kênh chi, credentials riêng, nguồn tiền/hạn mức và điều kiện mạng của provider.
5. Kiểm chứng giao dịch live được chủ tài khoản cho phép; đối soát ví/ledger/notification và ngân hàng; không phát sinh giao dịch chỉ để hoàn tất docs.

## 6. Viết lại tài liệu theo mẫu Discovery — 24/10/2026

- Việc làm: tài liệu kiến trúc mục 0–9, workflow Mermaid, file/schema mapping, Q&A; task từng phase và chỉ mục; đồng bộ API/runbook.
- AC: một nguồn chính về nghiệp vụ; đường dẫn cũ còn dùng được; trạng thái code tách khỏi merchant/live; không thay code, SQL hoặc secrets.
- Kiểm chứng: link tương đối, file/test/route/SHA tham chiếu và diff. Kết quả đợt docs ghi trong verification-report.

## 7. Hotfix tài khoản ảo payOS — 24/10/2026

- Phạm vi: tạo checkout chấp nhận tài khoản ảo theo response có chữ ký và đúng order/amount/VND; V1.18 lưu snapshot tài khoản nhận; webhook/lookup so receipt với snapshot. Tách message lỗi URL/link/order/amount/VND/account để chẩn đoán rõ hơn.
- AC: không bỏ kiểm chữ ký; không dùng tài khoản tự khai từ webhook làm chuẩn; không thay snapshot/link đã gắn; receipt sai không credit; duplicate chỉ một credit; giữ dữ liệu cũ NULL/fallback tài khoản gốc; không đổi env hoặc migration cũ.
- Nguồn regression: PayosGatewayTest, WalletServiceTest, PaymentReconciliationTest, PaymentFlowIT; cập nhật migration count lên 18. Chưa chạy theo yêu cầu người dùng. Maven offline test-compile BUILD SUCCESS; không start backend, migrate DB hay gọi provider.
- Bàn giao: cập nhật DBML/API/setup/runbook/luồng/report; commit/push riêng hotfix trên feat/payment. Fresh/upgrade V1.18 và regression vẫn còn chờ thực thi; kết quả ghi tại [verification-report](../../docs/payment/verification-report.md).

## 8. Cập nhật 25/10 — 24h/48h và tài khoản nhận tiền

- Rule thay thế: [payment-policy-update](../../docs/payment/payment-policy-update.md).
- Slice 1: bỏ autoaccept 1h, hai nút xác nhận/khiếu nại, tự tất toán sau 24h không khiếu nại khi COMPLETED, giải quyết 48h; V1.19 bảo vệ dữ liệu cũ. Commit `715da01`.
- Slice 2: một tài khoản nhận tiền theo owner, version ID, rút theo recipient đã lưu, snapshot withdrawal, UI xem lại trước gửi; V1.20, CSRF/role, stale/foreign ID và duplicate. Giữ Admin duyệt, không bật chi live. Commit `208c592`.
- Slice 3: đồng bộ API/schema/luồng/diagram/bằng chứng. Gate cuối 59 BE + 20 FE PASS, types/lint và browser fixture. Không test merchant/tiền thật hoặc production Next build trong đợt này.

## 9. Usability và phân xử — 26/10/2026

Theo [task đợt này](usability-plan.md) và [quy tắc hiện hành](../../docs/payment/payment-usability.md).

- U01/U02 — Backend/domain/migration/quyền/event: `2c3270b`, đã push. AC: không thu sớm; full notified 24h; 48h chưa khớp giữ tiền chờ Admin; Student không payer không quyết định số tiền; resolve có reason/range; atomic notify và idempotency. Gate 111 BE tests PASS.
- U03 — FE detail/summary/ba số dư/hồ sơ Admin và deep link: 24 FE tests, type/lint/production build và browser fixture PASS; commit `20ad2f6` đã push.
- U04 — Docs/schema/API/runbook/diagrams và kiểm liên kết; không đổi merchant/live data. Chưa có Meet/Zoom webhook hoặc giao dịch live.
