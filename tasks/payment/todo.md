# Payment: commit checklist

> Quy tắc hiện hành: [quy tắc 26/10](../../docs/payment/payment-usability.md). Các phase/checkpoint cũ bên dưới giữ lịch sử; xác nhận không thu sớm, 48h chưa khớp giữ tiền chờ Admin.


Nguồn chi tiết: [plan theo phase](plan.md) và [giải trình kiến trúc](../../docs/payment/payment_implementation_plan.md). Cập nhật 24/10/2026 trên `feat/payment`.

Giữ ID task đã triển khai. P04a/P04b/P04c là ba task trong Phase 4; commit hotfix không làm tăng số phase. Mỗi increment: kiểm chứng, review diff, commit rồi push đúng nhánh.

## Phase 0 — Baseline

- [x] P00: Audit base develop, branch, quy tắc ví/1h/24h và commission 10%. Commit `435da1b`.
- [x] Checkpoint 0: Plan đủ phạm vi, không giả định fee.

## Phase 1 — Domain

- [x] P01: SessionCharge state/time/report/terminal, domain tests. Commit `73195ff`.
- [x] Checkpoint 1: Auto learner không thay tutor, biên 24h rõ.

## Phase 2 — Ledger và session

- [x] P02: V1.16, wallet/held/entry/audit/mutex, snapshot, quyền, settlement/deadline. Commit `a4d4ea4`; startup/time fix `db23f8f`.
- [x] Checkpoint 2: PG transaction/idempotency/race, hoàn đúng payer, không Clock ambiguity.

## Phase 3 — Nạp/rút/Admin backend

- [x] P03: QR/payOS webhook, manual credit, tutor withdrawal/Admin review. Commit `5deafa9`.
- [x] Hotfix history `3f5b4d7`, pagination tranh chấp `2c5c105`.
- [x] Checkpoint 3: Receipt/reference đúng mới ghi tiền, history dùng được.

## Phase 4 — Learner/Tutor frontend

- [x] P04a: Typed SDK và form ví/nạp/rút. Commit `c32f518`.
- [x] P04b: Cấp tiền/xác nhận/dispute/report theo buổi. Commit `6fb85d9`.
- [x] P04c: Workspace ba role, tích hợp chi tiết lịch. Commit `0cb5c2a`.
- [x] Checkpoint 4: Dữ liệu API, quyền, loading/error/empty/pending, không số dư giả.

## Phase 5 — Admin frontend

- [x] P05: Đối soát nạp/rút/báo cáo, navigation và bỏ KPI/stub tiền giả. Commit `e80751b`.
- [x] Checkpoint 5: Admin flow/quyền/browser.

## Phase 6 — Bàn giao đầu

- [x] P06: PG/startup/architecture, FE type/lint/build/browser, DBML/API/runbook/report. Commit `db147f9`.
- [x] Hotfix local DB config `1192d79`, không sửa checksum migration.
- [x] Checkpoint 6: Bằng chứng và giới hạn ghi rõ.

## Phase 7 — Cấu hình provider

- [x] P07: Env/Compose passthrough, readiness Admin-only, secrets để trống. Commit `de6b336`.
- [x] Checkpoint 7: Thiếu khóa vẫn chạy, readiness không lộ secret.

## Phase 8 — Phục hồi nạp và notification

- [x] P08: Signed lookup, owner/Admin, lease, idempotent credit và atomic notification. Transport `d16e468`, triển khai chung P08/P09 `710b9af`.
- [x] Checkpoint 8: Mismatch không credit; duplicate một credit/notify; sink failure rollback.

## Phase 9 — Chi ngân hàng

- [x] P09: Admin dispatch, PROCESSING trước HTTP, stable key/reference, lookup, terminal verified. Commit chung `710b9af`; test `354cfbb`.
- [x] Checkpoint 9: Timeout giữ tiền/không repost, manual bị chặn sau dispatch, PG concurrency/upgrade.

## Phase 10 — FE provider và mobile

- [x] P10: Refresh nạp, chọn BIN, readiness/payout Admin, notification routing, mobile dialog. Commits `a3dfd37`, `31ccac8`.
- [x] Checkpoint 10: FE 25 tests/8 files ở gate cuối, type/lint/build và Chrome desktop/mobile.

## Phase 11 — Bàn giao cuối

- [x] P11: payOS setup thu/chi/webhook/unknown payout, API/schema/report. Commit `3966d54`.
- [x] Checkpoint 11: Code bàn giao, secrets không commit, merchant/live còn chờ.

## Viết lại docs theo Discovery — 24/10/2026

- [x] Giải trình mục 0–9: cơ chế, sơ đồ Mermaid, từng file/schema, ma trận kiểm thử và Q&A.
- [x] Task chi tiết từng phase có dependency/AC/kiểm chứng/SHA; giữ lịch sử task.
- [x] Chuẩn hóa API/runbook/chỉ mục; giữ liên kết implementation-plan cũ.
- [x] Đối chiếu code, link tương đối và diff; kết quả tại verification-report.

## Bổ sung giải thích luồng và ảnh diagram — 24/10/2026

- [x] Bổ sung tài liệu luồng/rule/cơ chế và 7 ảnh diagram PNG/SVG; đối chiếu code, kiểm bố cục ảnh và liên kết tài liệu. Đây là bàn giao docs, không phải kiểm chứng giao dịch live.

## Hotfix tài khoản ảo — 24/10/2026

- [x] Bản sửa checkout tài khoản ảo: snapshot từ response đã ký, đối chiếu webhook/lookup, V1.18 và nguồn regression; chỉ biên dịch, chưa chạy test/migration/provider theo yêu cầu người dùng. Xem [báo cáo](../../docs/payment/verification-report.md).
- [x] Regression/fresh/upgrade V1.18 đã bao phủ trong gate DB test riêng ngày 25–26/10; không đồng nghĩa live merchant PASS.

## Vận hành còn chờ — không đánh dấu đã làm

- [x] Cập nhật 25/10: xác nhận/khiếu nại 24h, giải quyết 48h, bỏ autoaccept 1h; xem [quy tắc mới](../../docs/payment/payment-policy-update.md).
- [x] Tài khoản nhận tiền mặc định và rút theo tài khoản đã lưu; không nhập lại mỗi lần. Commit `208c592`; mốc thời gian `715da01`.

- [ ] Xác thực merchant/ngân hàng và điều kiện dùng cho EduFit.
- [ ] Điền khóa kênh thu, tài khoản nhận, frontend URL và restart backend.
- [ ] Cấu hình HTTPS public webhook và xác minh hoạt động thực tế.
- [ ] Nếu cần payout: kích hoạt kênh chi, khóa riêng, nguồn tiền/hạn mức/điều kiện provider.
- [ ] Kiểm chứng giao dịch live được chủ tài khoản cho phép, đối soát ngân hàng/ví/ledger/notification.

Các kết quả test đã ghi ở [verification-report](../../docs/payment/verification-report.md); lần sửa Markdown này không phải lần chạy lại các gate code.

## Usability 26/10 — thay quy tắc xác nhận sớm / tự hoàn khi hết tranh chấp

- [x] U01/U02: cửa sổ 24h được lưu/thông báo; xác nhận không thu sớm; 48h chuyển ADMIN_REVIEW vẫn held; payer/student; case/evidence/comments/resolve. Commit `2c3270b` đã push.
- [x] U03 (`20ad2f6`, đã push): danh sách gọn + chi tiết riêng; ba số dư Tutor; Admin xem lại tiền; notification đúng hồ sơ; polling 15s/focus. 24 FE tests, type/lint/build và browser fixture PASS.
- [x] U04: rules/API/schema/runbook/diagrams và báo cáo kiểm thử; xem [quy tắc hiện hành](../../docs/payment/payment-usability.md).
- [ ] Restart backend để Flyway áp dụng V1.21 trong môi trường người dùng; không sửa/repair migration cũ.
- [ ] Kiểm chứng live provider, webhook công khai và vận hành Admin theo dõi hồ sơ; chưa nằm trong bằng chứng mock/Testcontainers.

## Giao diện ví gọn và QR popup — 26/10

- [x] Hoàn thành [4 phần UI](wallet-ui-plan.md): popup QR, Student/Parent/Tutor, Admin, kiểm chứng/bàn giao. 36 FE tests, type/lint/build PASS; browser fixture đủ 4 role và 4 breakpoint, chưa giao dịch live.
