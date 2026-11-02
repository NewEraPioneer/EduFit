# Payment verification

Đây là lịch sử bằng chứng theo từng đợt, không phải một lần chạy tất cả test. Bản code tham chiếu khi chuẩn hóa docs 24/10/2026 là `3966d54`: V1.17, có reconcile nạp và payout sau Admin duyệt; 74 case BE khác nhau qua các gate, gate FE cuối 25 tests/8 files. Bản sửa tài khoản ảo V1.18 sau đó chỉ được biên dịch, chưa chạy test/migration/provider theo yêu cầu người dùng; xem mục cuối. Chưa kiểm chứng giao dịch payOS/merchant thật. Các mô tả payout thủ công và số lượng test ít hơn bên dưới thuộc bản đầu, trước P07–P11.

Tài liệu đọc kèm: [giải trình kiến trúc](payment_implementation_plan.md), [task từng phase](../../tasks/payment/plan.md), [API](api-contract.md), [setup](payos-setup.md), [runbook](runbook.md).

- P00: `435da1b` — plan/checklist đã push.
- P01: domain state machine: RED testCompile thiếu SessionCharge; GREEN 7 tests SessionChargeTest, Maven reactor SUCCESS. Test mốc 1h/24h, uncompleted session, double confirm, báo cáo lệch, partial và full refund.

- P02: clean Maven reactor PASS: SessionChargeTest (7), ArchitectureTest (3), ArchitectureBoundaryTest (28), FlywayMigrationScriptTest (2). Lần đầu ArchitectureBoundaryTest lỗi do stale target từ branch cũ; clean đã xác minh hết lỗi. PostgreSQL flow test đang bổ sung, không coi naming test là test migration thực thi.

- P03: module Payment 17 tests PASS (domain 7, payOS 4, access 2, validation 1, wallet webhook 3); package app reactor SUCCESS. HMAC verifier khởi tạo HTTP client lazy để chạy offline; không dùng constructor failure làm test chữ ký.
- PostgreSQL 17 PaymentFlowIT: 10/10 PASS, 16 migrations trên schema mới; validate/rerun no-op. Concurrent confirm, insufficient-funds rollback, parent payer vs student confirmer, 1h auto, 24h timeout, matching reports chờ Admin, partial fee, cancelled-session rejection, duplicate ref/whitespace, withdrawal rejection.
- Independent review: sửa chặn Admin charge buổi chưa COMPLETED, bắt buộc receiving account payOS, trim reference và lưu manual QR để mở lại. Review lại không còn defect trong fixes. User chọn không thêm cross-model review.

- Startup smoke phát hiện Clock bean mới gây ambiguity ở IAM. Đã thay bằng PaymentTime wrapper, không đăng ký Clock toàn cục; thêm PaymentApplicationIT khởi động cả app và kiểm tra HTTP health 200 / anonymous wallet 401. Gate cuối: 17 Payment unit tests + 44 app/PG/architecture tests = 61 PASS, package SUCCESS.
- FE: 24 tests / 8 files PASS sau tách test ví và cập nhật nhãn; type-check PASS; lint Payment 0 errors, 0 warnings. Production build Webpack PASS bằng CA Windows (không disable TLS); default Turbopack có lỗi spawn CSS riêng máy.

- Browser phát hiện lỗi `ORDER BY created_at is ambiguous` ở topup/withdrawal history. Regression test tái hiện RED trên PostgreSQL; sửa projection thành danh sách cột duy nhất và sort theo timestamp gốc + id. Bao phủ ví từng chủ sở hữu, danh sách Admin, dữ liệu rỗng/có dữ liệu và offset.
- Sau sửa history: PaymentFlowIT 11/11 + PaymentApplicationIT 1/1 PASS, package reactor SUCCESS; không thay migration.

- History hotfix `3f5b4d7` đã push.
- P04a: SDK typed + WalletPanel + 2 tests cấu hình kênh nạp/không tự credit. Nằm trong FE gate 24 PASS, type-check/lint PASS.
- Browser Admin phát hiện thiếu bind `offset` ở danh sách tranh chấp. Regression RED tái hiện syntax error; GREEN 11 PaymentFlowIT PASS sau sửa, gồm hàng đợi rỗng/có báo cáo và phân trang.

- P04a `c32f518` và Admin pagination hotfix `2c5c105` đã push.
- P04b: SessionPaymentCard + 6 tests: xác nhận chủ động, chặn trước COMPLETED, mở tranh chấp, báo cáo mức tiền một phần, hết hạn/phân quyền, xác nhận giá trước cấp tiền. FE gate PASS.

- P04b `6fb85d9` đã push.
- P04c: workspace thật cho Student/Parent/Tutor, lọc tháng, sổ ví/lịch sử/phân trang và polling; tích hợp học phí vào modal buổi học. Type-check và production build PASS.

- P04c `0cb5c2a` đã push.
- P05: Admin nạp/rút/duyệt hai báo cáo; navigation mới, bỏ redirect cũ `/admin/disputes`, bỏ KPI tiền giả. 2 Admin form tests + 7 sidebar/CTA tests PASS (đã xử lý act warning trong test Parent).
- Browser Chrome production frontend + Spring Boot + PostgreSQL riêng: 4 vai trò PASS; QR không tự credit, Admin manual bank-confirm cập nhật đúng ví, ví Parent độc lập, Tutor không thể rút khi số dư 0, Student Admin API 403, disputes route không redirect, không pageerror. Không có chuyển tiền thật; bank confirmation được mô phỏng trên DB riêng.

- P05 `e80751b` đã push. P06: đồng bộ 7 bảng vào DBML, đánh dấu tài liệu tuition cũ được thay thế, bổ sung lệnh flow/startup tests trong runbook.

## Tổng hợp kiểm chứng bản đầu (trước P07–P11)

- Backend gate ban đầu: 61 PASS (17 unit Payment + 33 architecture/migration naming + 10 PG flows + 1 app startup). Sau bổ sung regression history/Admin: 11 PG flows PASS; startup 1 PASS; package SUCCESS. Tổng 62 test case khác nhau qua các gate, không tuyên bố chạy lại toàn bộ repository.
- FE: 24 tests / 8 files PASS; Sidebar 7/7 chạy lại sau sửa act warning; type-check PASS; lint phần Payment 0 errors/0 warnings. Production Webpack build PASS. Các thay đổi sau build là test/format và backend query fixes.
- Browser kiểm tra 4 vai trò PASS trên Chrome với production frontend và API thật. Ảnh màn hình đã xem cho Student/Parent/Tutor/Admin; không lỗi hiển thị ở viewport kiểm tra 1440px.
- Flyway: thực thi 16 migrations trên schema PostgreSQL 17 mới, validate và migrate lại không áp dụng thêm. Giữ nguyên migration history trước Payment.
- Không kiểm chứng live payOS/tiền ngân hàng thật vì chưa có bộ cấu hình merchant thật. Test chữ ký và kiểm tra payload là offline; manual bank-confirm chỉ mô phỏng trên DB riêng. Payout ngân hàng vẫn do Admin thực hiện bên ngoài và xác nhận sau đối soát.
- Tất cả commit chỉ push `origin/feat/payment`; không merge develop/main. Danh sách commit có thể xem `git log b1ea5cd..feat/payment --oneline`.

## Sửa kết nối local khi Flyway báo 28P01

Backend đang trỏ cổng 5433 thuộc PostgreSQL dự án khác. Đã bật lại container EduFit với volume cũ tại cổng local 5434; `.env` local giữ credentials của container EduFit. Cấu hình backend hỗ trợ fallback `POSTGRES_*` cùng Docker Compose, vẫn ưu tiên `DB_*`. Maven package PASS; khởi động với `.env` thật, validate 16 migrations, apply V1.15/V1.16 thành công và HTTP health UP. Dữ liệu cũ được giữ nguyên.

## Hoàn thiện payOS 23/10/2026

P07: Compose truyền toàn bộ cấu hình Payment; .env.example để trống secrets; .env local bổ sung biến thiếu không thay giá trị đã có. API Admin configuration chỉ trả readiness/frontend URL/webhook path. PaymentSettingsTest và gateway/wallet tests PASS; placeholder `...` không bật kênh live.

P07 `de6b336` đã push. Gateway `d16e468` đã push: ký/xác minh BODY cho kênh thu, HEADER + URI encoding cho kênh chi; đối chiếu API và source SDK chính thức, không thực hiện request giao dịch thật.

P08/P09: PostgreSQL 17 fresh 17 migrations + upgrade V1.16 có số dư/rút chờ, rerun no-op PASS. Gate backend 73 PASS (23 Payment unit + 50 app/architecture/PG/startup). Payout concurrent approval chỉ gọi create một lần, timeout giữ tiền và không repost, processing không được manual paid/refund, signed success ghi sổ một lần, failure trả ví, mismatch/unauthorized không release. Notification failure rollback cùng ví; duplicate credit chỉ notify một lần. Topup reconcile owner-only, không credit khi sai amount/account hoặc timeout; lease chặn refresh storm. Package SUCCESS. Provider calls dùng mock/test fixtures, chưa xác minh merchant live.

P08/P09 `710b9af` đã push; bổ sung header-signature + stable idempotency test `354cfbb` (3/3 PayosTransportTest PASS). Tổng 74 test case BE riêng biệt qua các gate (73 trong gate package + 1 case chữ ký bổ sung), không tuyên bố toàn repository được chạy lại.

P10: FE/notification 24 tests PASS, sau phát hiện mobile sidebar chiếm chiều ngang đã thêm native dialog navigation và RED/GREEN test. Gate cuối 25 tests / 8 files PASS; type-check/lint Payment + DashboardShell PASS, production Webpack build PASS. Chrome dùng backend thật + schema PG test riêng: 4 vai trò, manual topup không tự credit, Admin bank-confirm cập nhật đúng ví, persisted PAYMENT_TOPUP notification đúng một lần mỗi lần credit, readiness Admin-only, unsigned webhook bị từ chối, 390px không overflow và menu mở/đóng bằng Escape; zero page errors. Đã xem ảnh mobile và desktop. Test payout API bên ngoài dùng HTTP/provider mock; không chuyển tiền thật.

P10 mobile fix `a3dfd37` và FE provider `31ccac8` đã push. P11: hướng dẫn `payos-setup.md`, README, API và DBML đã đồng bộ V1.17. Kênh thu 5 biến để người dùng điền; kênh chi có bộ khóa riêng và PAYOS_PAYOUT_ENABLED=false mặc định. Không có secret trong commit; .env local ignored. Các tiến trình FE/BE/PG dành riêng cho kiểm thử đã dừng. Toàn bộ thay đổi trên feat/payment, không merge main/develop.

## Chuẩn hóa docs theo mẫu Discovery — 24/10/2026

- Đọc bản Discovery gốc, tasks/plan.md và tasks/todo.md; đối chiếu Payment domain/application/store/controllers, V1.16/V1.17, FE SDK và lịch sử Git tại code `3966d54`.
- Bổ sung payment_implementation_plan.md theo mục 0–9: audit delta, mục tiêu, cơ chế tiền/state/time/fee, boundary/workflows Mermaid, từng file/schema, quyết định/việc mở, phase, verification và Q&A.
- Bổ sung tasks/payment/plan.md, tổ chức lại checklist theo 12 phase và 14 task lịch sử; giữ SHA thực tế, tách checkpoint khỏi task và việc vận hành còn chờ.
- Đồng bộ API/runbook/setup và chỉ mục; implementation-plan.md cũ tiếp tục dẫn tới nguồn chính. Ghi đúng khác biệt webhook/lookup, endpoint confirm/payout, trạng thái topup và commission snapshot; không tuyên bố chức năng chưa có.
- Kiểm tra tài liệu: 9 Markdown trong docs/payment và tasks/payment, 53 link nội bộ, 6 khối Mermaid có fence cân bằng, 21 commit tham chiếu, thứ tự mục 0–9/phase 0–11 và 14 ID task đều PASS. File/test được tham chiếu đã đối chiếu trong repository.
- Đây là thay đổi Markdown; không thay code/SQL/env, không chạy lại gate BE/FE hay chuyển tiền thật. Số test ở các phần trước là bằng chứng lịch sử, không phải test mới của đợt docs.

## Giải thích nghiệp vụ và ảnh diagram — 24/10/2026

- Bổ sung [luồng, rule và cơ chế Payment](payment-flow-and-rules.md): ba vòng đời tiền, available/held, QR checkout hiện tại, receipt/lookup/manual, fund/confirmation/1h, dispute/report/24h/Admin, fee/partial/refund, withdrawal/manual/payout và 30 rule có nguồn thực thi.
- Thêm 7 PNG nhúng trực tiếp vào tài liệu nghiệp vụ và kiến trúc, kèm 7 SVG chỉnh sửa được. Có script Python/Pillow để tái tạo; không gọi provider hoặc thay đổi dữ liệu ứng dụng.
- Render cả 7 ảnh PASS với kiểm khung chữ/box không chồng; đã xem toàn bộ qua contact sheet và xem riêng ảnh tranh chấp/rút tiền ở kích thước chi tiết. Nội dung tiền/thời gian/nhánh xử lý đã đối chiếu code hiện tại.
- Kiểm tra tài liệu: link nội bộ và image reference, thứ tự đủ PAY-01..PAY-30, PNG đọc được/không trống, SVG XML hợp lệ, AST Python và git diff --check PASS. Các file/link cũ vẫn tồn tại.
- Không thay code FE/BE, schema, env hoặc chạy giao dịch live. Không chạy lại các gate test nghiệp vụ chỉ vì bổ sung tài liệu/ảnh; các số test trước đây giữ nguyên phạm vi lịch sử.

## Sửa kiểm tra tài khoản ảo khi tạo checkout — 24/10/2026

- Bằng chứng người dùng: POST `/api/v1/payment/topups` trả 400 `INVALID_OPERATION`, message `Dữ liệu checkout/tài khoản nhận không hợp lệ`. Message cũ gộp lỗi URL/link/tài khoản; chưa có response provider thật nên không khẳng định đã quan sát được field nào sai trong giao dịch đó.
- Đối chiếu code thấy `PayosGateway.create` buộc accountNumber từ response trùng tài khoản gốc trong cấu hình. [FAQ payOS mục 6](https://payos.vn/docs/cau-hoi-thuong-gap/) xác nhận VietQR Pro cấp tài khoản ảo riêng cho đơn, có thể khác tài khoản gốc; [API chính thức](https://payos.vn/docs/api/) mô tả response có chữ ký và accountNumber/virtualAccountNumber trên receipt. Điều kiện cũ từ chối trường hợp hợp lệ này.
- Sửa create: giữ xác minh chữ ký transport, kiểm URL bằng URI/HTTPS/host, kiểm link ID, order/amount/VND và tài khoản hợp lệ; trả message riêng theo nguyên nhân. Lưu tài khoản từ response có chữ ký theo từng topup. Không đổi env hoặc so tài khoản ảo với ngân hàng gốc.
- Sửa webhook và signed lookup cùng đối chiếu snapshot qua accountNumber hoặc virtualAccountNumber, giữ kiểm amount/link/reference/idempotency và transaction. Store không cho thay snapshot/link đã gắn. Không dùng webhook làm nguồn tạo snapshot.
- V1.18 chỉ thêm cột nullable `provider_account_number` và constraint định dạng; DBML đã đồng bộ. Giữ nguyên migration cũ và số dư. NULL giữ dữ liệu legacy/manual, fallback tài khoản gốc; đơn cũ dùng tài khoản ảo thiếu snapshot cần hỗ trợ đối soát nếu đã chuyển, không tự học tài khoản từ receipt.
- Viết nguồn regression: create với virtual account; sai order/amount/VND/URL/account bị từ chối; verify giữ virtualAccountNumber; webhook không bypass bằng tài khoản gốc khi snapshot khác; reconcile đúng/sai snapshot; PostgreSQL duplicate webhook một credit; upgrade V1.17 giữ dữ liệu và để snapshot NULL. Cập nhật các assertion số migration lên 18. **Các test này chưa chạy.**
- Đã chạy duy nhất Maven offline `test-compile -DskipTests` với Java 21, reactor 18 module: **BUILD SUCCESS**. Lệnh chỉ biên dịch main/test source, không thực thi test, không start backend, không migrate DB, không gọi payOS. Có warning deprecated/unchecked từ code hiện có; không lỗi biên dịch.
- Kiểm tĩnh tài liệu: 10 Markdown, 97 liên kết local không hỏng; 18 file migration gồm V1.18 mới, không sửa migration cũ; `git diff --check` không lỗi whitespace. `.env` vẫn ignored và không được theo dõi bởi Git.
- Tài liệu setup/API/runbook/luồng/plan/checklist được đồng bộ. Người dùng cần khởi động lại backend để Flyway áp dụng V1.18. Chỉ tạo yêu cầu mới khi chưa chuyển tiền cho yêu cầu lỗi; nếu đã chuyển, giữ order/reference để đối soát, không chuyển thêm.

## Saved recipient, 24h settlement / 48h dispute — 25/10/2026

- Rule và phạm vi hiện hành: [payment-policy-update](payment-policy-update.md). Commit phần thời gian `715da01` đã push trước khi làm tài khoản nhận tiền.
- V1.19 chỉ bỏ inferred consent 1h trên charge đang chờ; V1.20 lưu một tài khoản nhận tiền theo Tutor. Không sửa migration đã áp dụng, env merchant hoặc dữ liệu vận hành.
- Gate cuối: 31 Payment unit + 21 PaymentFlowIT + 5 FlywayMigrationIT + 2 convention = 59 PASS, không skip. Fresh 20 migrations; upgrade có dữ liệu, validate/rerun no-op. PostgreSQL 17 Testcontainers riêng, mock provider, không dùng DB live.
- Bao phủ: đúng biên 24h/48h, không tất toán khi chưa COMPLETED/có khiếu nại, không bị source ABSENT phá freeze, hai báo cáo chờ Admin, partial fee/refund, duplicate và owner/role/stale recipient ID, người nhận snapshot không đổi theo tài khoản mặc định.
- FE Payment 20 tests/4 files PASS; type-check và lint Payment PASS. Browser component fixture tại localhost riêng: xem tài khoản che số, Rút toàn bộ → xác nhận, đổi tài khoản, hai action buổi học; không lỗi console. Fixture execute không gọi API. Không tuyên bố E2E ứng dụng đăng nhập + DB live đã được kiểm chứng.
- Preview Vite ban đầu lỗi resolve React/esbuild thiếu tài nguyên; sửa cấu hình fixture/giới hạn thread rồi quan sát được UI thật. Không phải lỗi production bundle. Chưa chạy lại production Next build trong đợt này.
- API/schema/runbook/plan/luồng/diagram được đồng bộ. Kênh chi vẫn cần kích hoạt/cấu hình riêng; giữ Admin duyệt. Chưa có OTP/reauth riêng khi thay người nhận hoặc xác minh tên chủ ngân hàng tự động; không hiển thị nhãn verified.

- Phần tài khoản nhận tiền và freeze bổ sung đã push ở `208c592`; phần thời hạn ở `715da01`. Static docs: 11 Markdown, 106 liên kết local không hỏng; render/kiểm khung chữ và xem ảnh luồng mới PASS. `.env` và preview/test logs vẫn ignored.

## Payment usability, đầy đủ cửa sổ phản hồi — 26/10/2026

- Backend `2c3270b`: V1.21; xác nhận việc học không thu sớm, full reviewStartedAt+24h được lưu/thông báo cùng transaction; hết 48h chưa thống nhất ADMIN_REVIEW tiếp tục giữ tiền. Student không payer không đề xuất tiền; case chung, phản hồi có idempotency/giới hạn, Admin phân xử 0..A kèm lý do; event dùng API module.
- RED: 3 domain tests phát hiện hai xác nhận thu sớm và hết 48h tự hoàn, 5 FE assertions phát hiện hành vi/nhãn cũ. GREEN gate Maven app/reactor: **111 tests PASS, 0 failure/error/skip** gồm 32 Payment unit, 24 PaymentFlowIT, 2 PaymentApplicationIT, 5 FlywayMigrationIT, 2 migration convention, 31 architecture, 9 IAM, 4 Scheduling, 2 PaymentNotificationListener. Reactor 18 modules BUILD SUCCESS.
- PostgreSQL 17 Testcontainers riêng: fresh/upgrade đến V1.21, validate/rerun no-op, dữ liệu legacy/terminal; các migration cũ không sửa. PaymentApplicationIT thực khởi động Spring, chuyển Scheduling outcome, mở cửa sổ và lưu đúng hai notification khi payer=student; duplicate event không tạo thêm. Flow kiểm notification failure rollback window, biên thời gian, partial refund/fee, freeze khi source đổi, duplicate tiền và quyền/stale recipient.
- Lượt đầu Docker chưa chạy gây skip/error hạ tầng; bật Docker Desktop rồi chạy lại toàn bộ gate thành công, không đếm skip là pass. Sau khi định dạng hạn thông báo sang giờ Việt Nam, chạy lại riêng 2 listener tests PASS; lượt sandbox self-attach Mockito bị treo, dừng tiến trình kiểm thử và chạy ngoài sandbox thành công.
- Frontend `20ad2f6`: **24 tests / 6 files PASS**: 8 session decisions, 7 wallet/recipient, 4 Admin bank flows, 2 Admin adjudication preview/error, 2 notification routing, 1 polling header. Sửa fixture Admin đủ field theo PaymentSession, chạy lại hai test đó PASS. TypeScript noEmit và lint phạm vi Payment/notification/header PASS (0 warning/error). Production `next build --webpack` PASS, có đủ 4 route chi tiết động; warning package-lock Desktop ngoài repo không thuộc thay đổi Payment.
- Build sandbox đầu bị EPERM khi tạo .next; sau khi chạy ngoài sandbox build biên dịch được, phát hiện fixture test thiếu field, đã bổ sung đúng type rồi build lại thành công. Không bỏ qua typecheck hoặc tắt TLS/validation.
- Browser component thật trên Vite fixture riêng: xác nhận chỉ ghi việc học, form phân loại/bằng chứng, hồ sơ chung ADMIN_REVIEW, preview Admin 100k -> hoàn 50k/tutor 45k/fee 5k, 3 số dư Tutor và tài khoản che số. Không submit phân xử thật; execute fixture không gọi work/API, API đọc Admin trả fixture trong bộ nhớ. Lỗi Next Link trong Vite ban đầu được sửa bằng Link shim chỉ cho fixture; sau sửa UI render và không phát sinh lỗi mới. Tab/server fixture đã dừng. Đây không phải E2E đăng nhập/DB vận hành.
- Đồng bộ rules/API/schema/runbook/task, banner supersede bản 25/10 và 3 diagram thay đổi; render có kiểm khung chữ/overlap và xem ảnh. Số/bằng chứng cũ ở các mục trước giữ lịch sử, không dùng làm kết quả mới.
- Không gọi merchant/payOS/Meet/Zoom thật; không thay env hoặc bật kênh chi. V1.21 cần restart backend; không repair/clean/reset DB. Admin cần theo dõi hàng đợi giữ tiền; chưa có SLA/nhắc trước hạn, upload bằng chứng, appeal, reauthentication riêng đổi ngân hàng hoặc webhook đo giờ học.

- Kiểm tĩnh cuối: 13 Markdown / 125 liên kết local hợp lệ; 7 cặp PNG/SVG đọc/parse được; generator kiểm khung chữ và overlap PASS; git diff --check PASS. Preview/logs và .env không được Git theo dõi. Backend và FE đã push riêng `2c3270b`, `20ad2f6`; commit tài liệu kết thúc đợt này.

## Thiết kế lại giao diện ví và QR popup — 26/10/2026

- Các commit đã push: `bd5520f` popup QR + SDK chính thức, `557cebd` ví learner/tutor và lịch sử theo yêu cầu, `6bb05f0` hàng đợi Admin/dialog, `06a331e` responsive/focus và regression lỗi rút. Tài liệu tại [wallet-ui](wallet-ui.md).
- RED trước code: 5 QR tests và 4 workspace tests fail theo UI cũ; test Admin mới fail vì chưa tách hàng đợi/dialog. GREEN gate cuối **36 tests / 7 files PASS**, type-check/lint Payment PASS. Sau CSS/focus/error polish chạy lại 15 QR/withdraw tests PASS. Production `next build --webpack` PASS (giai đoạn sinh trang 52/52 và các route dynamic chi tiết).
- Google Fonts ban đầu gặp CA và ECONNRESET/timeout. Giữ xác minh TLS, dùng CA Windows tin cậy, kiểm truy cập CSS/font CDN rồi build lại thành công; có warning package-lock ngoài repo như trước. Không thay font, dependency hoặc Next config.
- Browser component thật trên fixture riêng: 4 role, popup QR không đổi trang, lịch sử theo yêu cầu, Escape/focus-return, focus phần xem lại rút, Admin verify chưa submit; 320/768/1024/1440 không tràn ngang. HMR fixture từng báo createRoot trùng, tải mới không có error/warn; iframe và API đều mock, QR có nhãn DEMO. Đã reset viewport, đóng tab/server tự tạo. Không tuyên bố live payOS hoặc E2E DB vận hành đã qua.
- Không đổi BE/nghiệp vụ tiền/SQL/env. Không chạy lại Maven cho thay đổi FE này; không tạo/chuyển tiền ngân hàng. Callback SDK không cộng ví; retry SDK không tạo order khác, tiếp tục order pending không gọi create, ID tài khoản cũ vẫn bị chặn khi rút.

- Kiểm docs cuối: 15 Markdown, 135 local links hợp lệ, 7 cặp PNG/SVG đọc được; render diagram nạp popup và xem ảnh PASS, git diff --check PASS. .env/logs/preview vẫn ignored.

## Ghép develop vào feat/payment — 27/10/2026

- Giải bốn conflict theo route/menu KYC và cấu hình proxy của develop, giữ route Payment, webhook public/CSRF exception và cấu hình DB/merchant của Payment. Payment V1.16–V1.21 không đổi; migration Connection đổi sang V1.22, thân SQL giữ nguyên. Compose local giữ độ bền ghi PostgreSQL thay vì tắt fsync/full_page_writes.
- PostgreSQL 17 Testcontainers: **34/34** test trọng tâm PASS (FlywayMigrationIT 6, convention 2, PaymentFlowIT 24, PaymentApplicationIT 2). Gồm database sạch lên V1.22, nâng cấp từ Payment V1.21 lên V1.22 giữ số dư và lịch sử, kiểm index mới, validate/rerun no-op; không gọi payOS thật.
- Frontend: Payment **37/37** PASS, kiểm thử Sidebar Admin **2/2** PASS, TypeScript/lint các file conflict PASS; production `next build --webpack` PASS và sinh đủ trang Payment cho Student/Parent/Tutor/Admin.
- `docker compose config --quiet` PASS. Container PostgreSQL cục bộ đang dùng lệnh mặc định `postgres`; chỉ đọc lịch sử DB cục bộ thấy Payment V1.16–V1.21 đã áp dụng. Không chạy migration V1.22 trên DB này, không đổi tiền/dữ liệu vận hành. Database team đã áp dụng Connection V1.16 cần kế hoạch chuyển lịch sử riêng trước khi dùng bản ghép; xem [runbook](runbook.md).
