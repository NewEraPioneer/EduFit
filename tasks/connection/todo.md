# Connection: Task Chi Tiết Theo Phase & Commit/Push

**Trạng thái:** Phase 0 và Phase 1 (T02–T07) hoàn tất phạm vi cấu trúc trên `feat/connection` tại folder EduFit. Clean verify 348 tests pass không skip; strict coverage chưa đạt, ghi riêng trong [báo cáo Phase 1](../../docs/connection/phase1-validation.md). Chưa bắt đầu Phase 2. [Contract T01](../../docs/connection/connection-api-contract.md) phân biệt runtime hiện tại và đích của phase sau.

**Plan chi tiết:** [plan.md](plan.md), đặc biệt mục 0.4–0.6 chứa SHA, conflict thật và coupling tests. Mẫu workflow: Discovery `tasks/plan.md` và `tasks/todo.md`; Connection lưu riêng tại `tasks/connection/` để không ghi đè chúng. Scope backend Connection UC3.1–3.5 và integration contracts, không triển khai frontend.

**Quy mô bản cuối:** 8 phase, 26 nhóm T00–T25, thêm 2 prerequisite T00a/T00b. Tám nhóm lớn được thay bằng 21 task con bắt buộc tại mục 6, tổng cộng **41 task thực thi = 41 commit và push riêng**; không tính commit cha đã được thay thế.

## 1. Quy tắc thực thi giống Discovery

1. Chỉ dùng folder `C:/Users/ASUS/Desktop/EduFit/EduFit` để thực thi mọi task/code/test/docs Connection, trên nhánh `feat/connection`. T00 đã bảo toàn dirty Discovery changes vào stash riêng, detached checkout cũ và switch EduFit sang Connection. Không force/reset/ghi đè hoặc dùng cùng branch ở hai worktree. Chỉ push `HEAD:feat/connection`; không merge/push ngược vào main hoặc Discovery.
2. T00 là task tài liệu/baseline; T00a/T00b là hai prerequisite integration trước refactor. Mỗi **task thực thi hoặc task con bắt buộc ở mục 6** là đúng một commit gồm code, test và checklist; task cha đã chia chỉ là nhóm, không tạo thêm commit cha. Mechanical move/import có thể nhiều file nhưng phải cùng một thay đổi kiểm chứng được.
3. Mỗi task: đọc docs + code liên quan -> test tái hiện nếu sửa logic -> sửa nhỏ -> focused tests/affected callers -> review diff -> `git diff --check` -> stage đường dẫn cụ thể -> commit -> **push ngay** -> xác minh remote -> báo SHA/test/điểm cần review. Không chỉ commit local rồi chờ cuối phase mới push.
4. Không dùng `git add .`/`git add -A` cho toàn repo. File checklist được commit cùng task; SHA ghi trong báo cáo sau commit, tránh commit phụ chỉ để ghi SHA của chính nó.
5. Chỉ đánh dấu xong khi acceptance, verification và push đều đạt. Nếu code đã commit nhưng push lỗi, ghi `implemented/committed, push blocked`, giữ nguyên task đang mở, xử lý push trước task tiếp theo. Không force push.
6. Test fail thì sửa trong scope task; lỗi baseline ngoài scope phải ghi cụ thể, không âm thầm sửa vào commit Connection. DB/Docker unavailable không được gọi là DB test pass.
7. Checkpoint sau mỗi phase là bước xác minh/review evidence, không tự tạo feature commit riêng nếu không đổi file. Khi có thay đổi mới phải có task rõ. Không tự merge `main`/deploy. Các task đã chốt không cần xin phép lại cho từng thao tác thông thường.

Chuỗi lệnh mẫu sau khi test/review xong (chạy ở repo root; thay paths/message đúng task):

```powershell
git status --short
git diff --check
git add -- <exact-path-1> <exact-path-2> tasks/connection/todo.md
git diff --cached --stat
git diff --cached
git commit -m "<commit message của task>"
git push origin HEAD:feat/connection
git rev-parse HEAD
git ls-remote origin refs/heads/feat/connection
```

So SHA HEAD và SHA remote phải trùng. Nếu remote ahead, fetch/inspect rồi xử lý lịch sử phù hợp, không force. Chạy lệnh Maven từ `backend/`; Git từ repo root. `<...>` là placeholder, không copy chạy nguyên dạng.

## 2. Đồ thị phụ thuộc và phạm vi task

`T00 -> T00a (main) -> T00b (Discovery + baseline tích hợp) -> T01 -> structure (T02–T07) -> guards/transaction (T08–T09) -> event contracts (T10) -> use cases (T11–T15) -> expiry (T16–T17) -> query (T18–T19) -> verification (T20–T24) -> handover (T25)`.

Focused tests đi cùng từng task; T20–T22 bổ sung kiểm chứng ma trận/liên tầng/race, không trì hoãn mọi test tới cuối. Mỗi task tiếp theo chỉ chạy khi dependency hoàn tất và push đã xác nhận. Các branch tích hợp chưa sẵn sàng không được giả vờ pass T24.

## Phase 0 — Chốt plan, contract và baseline

### [x] T00 — Chốt bộ plan và ghi baseline thực thi

- **Mục tiêu:** Hai tài liệu đủ mapping yêu cầu và hiện trạng, cùng baseline tái lập được.
- **Files:** `tasks/connection/plan.md`, `tasks/connection/todo.md`; đọc docs cũ/code/SRS, Git history và test config.
- **Thực hiện:** Chuẩn bị nhánh `feat/connection` **ngay trong folder EduFit**: bảo toàn 3 dirty files Discovery bằng checkpoint/snapshot riêng và kiểm tra bản lưu; không đưa thay đổi đó vào commit Connection. Kiểm tra checkout cũ trước khi giải phóng attachment `feat/connection`, rồi switch folder EduFit sang nhánh này, không force hoặc dùng cùng branch ở hai nơi. Xác nhận baseline `43e7461` hay ghi HEAD mới; kiểm tra upstream/JDK/Maven/DB/migrations. Chọn process JDK 21 tại `C:/Program Files/Java/jdk-21.0.10`; không đổi global environment. T00a/T00b tích hợp base trước T01, chưa merge code trong task docs.
- **Acceptance:** Task/code/docs nằm đúng folder EduFit và nhánh feat/connection đã chuẩn bị an toàn; dirty Discovery changes được bảo toàn riêng; docs Connection ở `docs/connection/`, plan/task ở `tasks/connection/`. Mọi UC/BR/FR có task; baseline report/lỗi môi trường rõ, không coi surefire cũ là lần test mới.
- **Verification:** Chạy baseline `mvn -pl modules/connection -am test`; kiểm kê test nào thực sự chạy; `git diff --check`, links hai tài liệu và coverage table.
- **Phụ thuộc / scope:** Không; S, hai file docs.
- **Commit:** `docs(connection): finalize detailed refactor plan and phased tasks`.
- **Kết thúc:** Commit + push; báo SHA, baseline pass/fail/not-run. Plan hiện tại chưa có SHA/task hoàn thành.

### [x] T00a — Cập nhật main và giữ quality gates

- **Files:** Merge delta `origin/main` (`6755121` khi thực thi), gồm backend parent pom, frontend-auth và tooling; chỉ nhập upstream, không triển khai feature frontend riêng.
- **Thực hiện:** Tích hợp main bằng merge commit giữ shared history. Giữ JaCoCo 0.8.12, 80% LINE/BRANCH và mặc định false từ upstream; chạy strict verify bằng `-Djacoco.haltOnFailure=true` để báo gap. Resolve trùng version: giữ Connection V1.09 nguyên checksum; seed main mới nhập đổi tên sang V1.10, giữ nội dung. DB đã dùng seed main V1.09 cần đối chiếu lineage trước rollout, không tự repair. Không force push hay hạ ngưỡng.
- **Acceptance:** Main SHA được ghi và là ancestor của HEAD mới; Connection hiện có không mất; không overwrite docs Discovery. Delta tooling từ main được giữ nguyên.
- **Verification:** Diff merge + `mvnw.cmd -pl modules/connection -am test -B -ntp`; affected verify và baseline full `verify` có report gate/failed modules riêng. Không coi `test` đồng nghĩa coverage check pass.
- **Phụ thuộc / scope:** T00; integration prerequisite, không refactor code.
- **Commit:** `chore(connection): integrate main testing quality gates` (merge commit).
- **Kết thúc:** Commit + push + local/remote SHA, baseline coverage report; baseline gate fail phải được ghi trước task sau.

### [x] T00b — Tích hợp Discovery và xử lý conflicts trên base main mới

- **Files:** Merge delta `origin/feat/discovery` (`dd9068e`), 6 conflicts thực tế gồm 3 file Connection/Profile và SecurityConfig/2 Flyway tests; giữ `tasks/plan.md`/`tasks/todo.md` của Discovery.
- **Thực hiện:** Mô phỏng lại trên base T00a. Resolve theo plan 0.4: union Connection tests với parameterized confirmed-link test; giữ Discovery Profile constructor/bounded candidate/catalog/search và Connection goal/expertise methods; hợp nhất tests. Không dùng ours/theirs hàng loạt. Không copy 3 dirty files từ checkout Discovery.
- **Acceptance:** Cả hai public API surface cùng tồn tại; không phục hồi full-scan API; Discovery docs giữ nguyên, Connection docs dưới namespace riêng; dependency graph không cycle.
- **Verification:** Reactor compile/tests Connection/Profile/Discovery tới app; chạy tường minh `MatchingWorkflowPostgresTest`, `DiscoveryReadPostgresTest`, `FlywayMigrationIT` và architecture tests. Đọc test reports để xác nhận PostgreSQL không skip. Baseline full verify + JaCoCo failures phân biệt với regression do resolution.
- **Phụ thuộc / scope:** T00a; integration prerequisite, ba conflict resolution cùng merge commit, chưa refactor.
- **Commit:** `chore(connection): integrate discovery and preserve shared profile contracts` (merge commit).
- **Kết thúc:** Commit + push + SHA base/merge/remote, conflict resolution và baseline evidence trước T01.

### [x] T01 — Chốt HTTP contract và quyết định còn mở

- **Files:** `docs/connection/connection-api-contract.md` (mới), `tasks/connection/plan.md`, `tasks/connection/todo.md`; đọc controller/shared handler/security.
- **Thực hiện:** Ghi 10 routes cũ, request/response theo role, JSON/error/status codes; actor từ context. Chốt boolean accept bắt buộc, reason/message limits, deadline inbox, generic invite và cooldown known-history. Inventory legacy slots, chốt grammar/writer version/reader compatibility. Chốt WITHDRAWN system closure, routes page và ADMIN lookup.
- **Acceptance:** Không có đổi shape âm thầm; quyết định grammar/closure được ghi rõ trước task phụ thuộc; list cũ giữ nguyên, các route bổ sung có scope quyền.
- **Verification:** Đối chiếu SRS/code/consumer thực tế; ghi fixtures JSON cho T04/T21, review không lộ PII. Chưa thay controller runtime ở task docs.
- **Phụ thuộc / scope:** T00b; S, 2–3 docs.
- **Commit:** `docs(connection): define compatible HTTP and transition contracts`.
- **Kết thúc:** Commit + push + báo quyết định cần người dùng review trước triển khai phụ thuộc nếu còn chưa chốt.

**Checkpoint 0:** main + Discovery đã tích hợp với evidence, không mất tests/public methods; JaCoCo baseline rõ, docs namespace riêng, contract đủ và không còn quyết định quyền/slots ngầm.

## Phase 1 — Chuẩn hóa cấu trúc và ranh giới

### [x] T02 — Di chuyển persistence về entity/repository

- **Files:** 5 entities, 5 repositories và imports caller/tests; một mechanical move duy nhất.
- **Thực hiện:** Move vào `infra/persistence/entity` và `repository`; giữ tên Java/mapping/table/column/status; không rename entity vô ích. Không thêm migration.
- **Acceptance:** Mapping không đổi; JPQL resolve đúng entity; không còn imports package cũ.
- **Verification:** Compile reactor, giữ 8 scenarios cũ và 5 confirmed-link invocations đã có từ Discovery; so V1.04/V1.09. Kiểm tra `MatchingWorkflowPostgresTest` repository/entity scans trên package mới cùng commit, chạy test này và architecture suite.
- **Phụ thuộc / scope:** T01; mechanical exception vì move 10 file cùng imports.
- **Commit:** `refactor(connection): normalize persistence packages`.
- **Kết thúc:** Commit + push + mapping/test evidence.

### [x] T03 — Tách implementation của public facade

- **Files:** `api/ConnectionFacade.java` (giữ), `application/service/ConnectionFacadeImpl.java`, `ConnectionService.java`, facade tests, `app/.../discovery/MatchingWorkflowPostgresTest.java`.
- **Thực hiện:** Chuyển hai public read methods sang bean duy nhất; giữ interface signatures/nested `ActiveClassView`. Service nội bộ không gọi self-invocation facade. Đổi import/@Import trong `MatchingWorkflowPostgresTest` sang FacadeImpl cùng task, cập nhật fixture cần thiết và assertions unit cũ gọi facade mới.
- **Acceptance:** Chỉ một bean implements facade; confirmed predicate và ACTIVE class lookup giữ hành vi; không lộ entity/web DTO.
- **Verification:** Facade tests confirmed/revoked/missing và active/ended/missing; context bean resolution; Discovery matching workflow PostgreSQL phải chạy sau đổi fixture, không chỉ compile caller.
- **Phụ thuộc / scope:** T02; M.
- **Commit:** `refactor(connection): isolate public facade implementation`.
- **Kết thúc:** Commit + push + consumer compatibility.

### [x] T04 — Tách DTO khỏi entity ở HTTP boundary

- **Files:** controller, `web/request`, `web/response`, `application/dto`, mapper/service result signatures và regression test.
- **Thực hiện:** Extract nested records, service trả application results; web không import persistence. Giữ tên/kiểu JSON cũ, đổi `accept` primitive thành nullable validated Boolean theo T01. Chưa thêm batch behavior; giữ mapping equivalent trước tối ưu.
- **Acceptance:** Không có entity rời service vào web; application không import web; JSON/error contract cũ pass.
- **Verification:** Controller JSON regression cho cả 10 routes/role shapes, missing accept trả validation; application tests. Nếu số DTO mechanical lớn, giữ cùng boundary change, không trộn logic use case.
- **Phụ thuộc / scope:** T03; M logic, nhiều DTO mechanical.
- **Commit:** `refactor(connection): separate application and HTTP DTOs`.
- **Kết thúc:** Commit + push + liệt kê field contract được giữ/bổ sung có chủ đích.

### [x] T05 — Tách lock và quota adapter

- **Files:** `ConnectionLock`, `PostgresConnectionLock`, `InvitationQuotaService`, service wiring, lock/quota tests.
- **Thực hiện:** Native SQL chuyển vào adapter; quota ở service nhỏ; giữ connection/transaction. Ghi thứ tự student -> pair -> row cho accept invitation, quota -> pair cho invite; sửa mọi path ngược thứ tự.
- **Acceptance:** Quota vẫn 10 attempts/cửa sổ 1h kể cả email lạ; không lock ngoài transaction; cùng key/order trên mọi writer.
- **Verification:** Unit key canonicalization/quota boundaries; focused PostgreSQL lock test hai transaction và transaction-end release. Full race matrix ở T22.
- **Phụ thuộc / scope:** T04; M.
- **Commit:** `refactor(connection): isolate transactional locking and invitation quota`.
- **Kết thúc:** Commit + push + lock order evidence.

### [x] T06 — Tách invitation/link use cases khỏi service lớn

- **Files:** `LinkInvitationService`, `ParentStudentLinkService`, controller wiring, service cũ và tests tương ứng.
- **Thực hiện:** Move invite/respond/withdraw/list links/revoke vào đúng service; authorization giữ cùng entry point; liên kết query qua DTO đã có. Không đồng thời đổi business rules.
- **Acceptance:** Routes cũ không đổi; không self-invocation mất transaction; revoke/create vẫn chia sẻ student lock.
- **Verification:** Invitation/link regression, constructor/context wiring, annotation transaction behavior smoke test.
- **Phụ thuộc / scope:** T05; M.
- **Commit:** `refactor(connection): split invitation and parent link use cases`.
- **Kết thúc:** Commit + push + suite evidence.

### [x] T07 — Tách request/query use cases, chuyển hết caller

- **Files:** `ConnectionRequestService`, `ConnectionQueryService`, controller, service cũ, tests và `app/.../ArchitectureBoundaryTest.java` expected class assertion.
- **Thực hiện:** Move create/list/cancel/respond request; service query sở hữu metadata mapping thay controller. Xóa `ConnectionService` sau scan sạch; đổi expected class trong Discovery `ArchitectureBoundaryTest` sang FacadeImpl cùng commit, giữ Discovery layer assertions khác; direct sinks giữ đến task event migration.
- **Acceptance:** Controller chỉ auth/validate/map; class creation vẫn trong transaction accept; không caller còn dùng service cũ.
- **Verification:** Request/class regression, scan toàn backend, compile/tests affected modules.
- **Phụ thuộc / scope:** T06; M.
- **Commit:** `refactor(connection): split request and scoped query services`.
- **Kết thúc:** Commit + push + scan caller.

**Checkpoint 1 — cấu trúc hoàn tất:** DTO boundary/facade compatible, 10 routes regression pass, lock adapter cùng transaction. Clean full verify 348 tests pass (0 fail/error/skip); strict coverage gate FAIL, chưa chứng nhận80%. Evidence, counters và giới hạn: [Phase 1 validation](../../docs/connection/phase1-validation.md).

## Phase 2 — Guard, clock, transaction và event foundation

### [x] T08 — Chuẩn hóa transition guards và thời gian

- **Files:** `ConnectionTransitions`, entity mutators liên quan, Clock wiring, parameterized tests.
- **Thực hiện:** Central expected-state guard; reject terminal transitions; inject **Clock có sẵn từ IAM**, một now mỗi operation; loại magic durations theo cấu hình/default đã chốt. Không tạo Clock unconditional trùng IAM/Scheduling. Bắt buộc thực thi T08a–T08c ở mục 6, không gom một commit.
- **Acceptance:** Deadline equality expired; timestamp/cooldown chính xác; retry terminal không mutation/event.
- **Verification:** Parameterized state/action matrix, expiry ±1ns, quota/reset/cooldown; no real sleeps; context IAM + Connection resolve đúng một Clock, fixed Clock override test không ảnh hưởng IAM flow.
- **Phụ thuộc / scope:** T07; thực thi ba slice M T08a–T08c tại mục 6.
- **Commit:** Không tạo commit cha; dùng ba commit của T08a–T08c.
- **Kết thúc:** Commit + push + state matrix result.

### [x] T09 — Thay noRollbackFor rộng bằng kết quả transition rõ ràng

- **Files:** transition result DTO, invitation/request services, controller mapper, transaction tests tại app.
- **Thực hiện:** Expire thành công trả typed conflict result; HTTP map sau service transaction. Write failures rollback mặc định; constraint errors map ngoài transaction. Không catch DB exception rồi tiếp tục ghi.
- **Acceptance:** Expiry được lưu dù HTTP báo conflict; accept/link/class failure không để state accepted; outer rollback được tôn trọng.
- **Verification:** PostgreSQL commit/rollback/failure tests qua Spring proxy, bao gồm outer transaction và lỗi flush/deferred constraint; focused tests không chỉ verify Mockito.
- **Phụ thuộc / scope:** T08; M.
- **Commit:** `fix(connection): make expiry and write rollback semantics explicit`.
- **Kết thúc:** Commit + push + transaction evidence.

### [x] T10 — Thêm public event contract và consumer nền

- **Files:** 4 `api/event` records, notification listener skeleton/dispatch, notification pom, event wiring tests.
- **Thực hiện:** Event IDs/aggregate IDs/actor/student/status/time; no PII/admin reason. Listener BEFORE_COMMIT gọi writer nội bộ cùng transaction; chỉ đăng ký xử lý các type đã chuyển publisher ở tasks sau, không nhân đôi sinks cũ.
- **Acceptance:** Consumer không chạy ngoài transaction; DB listener failure rollback; Connection không import notification internals, class-created không gửi duplicate accepted notification.
- **Verification:** Compile reactor, context loading, transactional event test commit/rollback; inspect dependency cycles.
- **Phụ thuộc / scope:** T09; M, event records mechanical.
- **Commit:** Không tạo commit cha; dùng T10a/T10b tại mục 6.
- **Kết thúc:** Commit + push + event/consumer transaction semantics.

**Checkpoint 2:** guards/time đúng biên; expiry conflict được persist riêng với failed writes rollback; public events và listener transaction được kiểm chứng.

## Phase 3 — Hoàn thiện từng use case theo chiều dọc

### [x] T11 — Hoàn thiện invitation hai chiều, consent và privacy

- **Files:** invitation service/repository, listener invitation branch, contract và service/HTTP tests.
- **Thực hiện:** ACTIVE opposite role/self/unknown; pending pair/cooldown queries bounded; accept revalidate hai account; system closure khi link đã tồn tại theo T01. Chuyển create/accept/decline notification từ direct sink sang events, giữ withdraw behavior.
- **Acceptance:** Chưa accept không có quyền; chỉ recipient phản hồi/inviter withdraw; generic response không lộ email, known-history cooldown có thời điểm; không duplicate notification.
- **Verification:** Two-way happy/negative/state/cooldown/quota tests; DB invitation+link+notification rollback smoke; HTTP shape/privacy.
- **Phụ thuộc / scope:** T10b; thực thi T11a/T11b tại mục 6.
- **Commit:** Không tạo commit cha; dùng T11a/T11b.
- **Kết thúc:** Commit + push + UC3.1/3.2 acceptance report.

### [x] T12 — Hoàn thiện revoke và audit snapshot

- **Files:** link service, structured AuditSink input + adapter/entity, migration mới nếu cần, revoke listener branch, tests.
- **Thực hiện:** Student ownership/Admin mandatory reason; audit snapshot cả hai accounts/time/link/reason cùng transaction; Admin notify Student + Parent, Student revoke notify Parent. Giữ sink API cũ cho verification caller. Chốt migration version trên base thực tế.
- **Acceptance:** Parent không revoke; audit reason không lộ notification/response; failure audit/notification rollback revoke; lịch sử và dữ liệu Student giữ nguyên.
- **Verification:** PostgreSQL revoke/audit/notification atomicity; migration mới phải cập nhật `FlywayMigrationIT` count/version và script tests/fixtures cùng commit T12a, clean+upgrade. Audit API cũ vẫn compile; tasks T12a–T12c bắt buộc theo mục 6.
- **Phụ thuộc / scope:** T10, T11; M mỗi slice.
- **Commit:** Không tạo commit cha; dùng T12a–T12c tại mục 6.
- **Kết thúc:** Commit + push + UC3.3/audit evidence.

### [x] T13 — Hoàn thiện create/cancel request và input validation

- **Files:** request service, slot validator, request DTO validation, request listener branch, focused tests.
- **Thực hiện:** Goal owner/ACTIVE, tutor VERIFIED + ACTIVE/expertise; max5 qua COUNT sau expire, duplicate/ACTIVE class. Parse slots theo T01, message ≤500; cancel chỉ PENDING có quyền hiện tại. Parent create/cancel notify Student; cancel notify Tutor. Chuyển direct sinks create/cancel sang event.
- **Acceptance:** Quyền actor không dựa sentBy một mình; expired không chiếm pending quota; slots sai reject nhưng legacy reader còn đọc được; thông báo đúng người.
- **Verification:** Unit/HTTP boundaries và PostgreSQL pending count/constraint smoke, Parent revoked denied; notification recipient matrix.
- **Phụ thuộc / scope:** T12c và quyết định slots T01; thực thi T13a–T13c tại mục 6.
- **Commit:** Không tạo commit cha; dùng T13a–T13c.
- **Kết thúc:** Commit + push + UC3.4/validation evidence.

### [x] T14 — Hoàn thiện accept/reject và tạo đúng một class

- **Files:** request service, class repository, respond DTO, event listener branch, tests.
- **Thực hiện:** Tutor đích ACTIVE/VERIFIED; request PENDING/còn hạn; revalidate goal.studentId == request.studentId, ACTIVE và expertise. Reject optional reason ≤300. Accept + class cùng transaction, publish request transition và class-created; chỉ một nguồn accepted notification.
- **Acceptance:** Exactly one ACTIVE class/request; failed class insert không để request ACCEPTED; Student nhận kết quả, Parent gửi chỉ nhận khi còn link; reject không class.
- **Verification:** Happy/reject/goal-owner changed/tutor account DEACTIVATED tests; PostgreSQL class failure rollback; terminal retry không duplicate.
- **Phụ thuộc / scope:** T13; M.
- **Commit:** `fix(connection): enforce atomic tutor response and class creation`.
- **Kết thúc:** Commit + push + UC3.5/class evidence.

### [x] T15 — Kiểm tra quyền Parent xuyên các entry point sau revoke

- **Files:** access helper/link/query/request services, notification recipient/read scope có sẵn, authorization tests.
- **Thực hiện:** Kiểm tra create/list/cancel và read facade scope; không cache link qua request. Parent đã revoke không nhận thông báo học tập mới; kiểm tra API notification read nếu có. Request Parent cũ vẫn xử lý bởi Student/Tutor. Ghi rõ dependency UC6.3 nếu API đọc chưa triển khai.
- **Acceptance:** Request kế tiếp của Parent bị chặn; Student data không mất; không mở quyền admin/tutor nhầm; không tuyên bố notification privacy E2E nếu thiếu reader.
- **Verification:** Confirmed->revoked->relinked matrix; integration quyền Student/Parent nhiều con; assertions không lộ nội dung notification sau revoke nếu reader hiện diện.
- **Phụ thuộc / scope:** T12–T14; M.
- **Commit:** `fix(connection): enforce current parent consent across connection access`.
- **Kết thúc:** Commit + push + scope/dependency report.

**Checkpoint 3:** UC3.1–3.5 hoạt động qua REST và service, notification/audit đủ theo ma trận, Student ownership giữ nguyên.

## Phase 4 — Expiry tự động và xử lý song song với người dùng

### [x] T16 — Shared expiry service và on-access guards

- **Files:** `ConnectionExpiryService`, expiry repository queries, invitation/request integrations, event tests.
- **Thực hiện:** Expire invitation/request qua cùng guard/lock order, bounded IDs; recheck sau lock. Listener expiry báo sender/current-authorized parties; chỉ transition mới publish. On-access gọi cùng logic, không nhiều version expiry khác nhau.
- **Acceptance:** now == expiry chuyển EXPIRED, terminal no-op; expired giải phóng partial unique/pending count; không duplicate event/notification.
- **Verification:** Unit clock boundary + PostgreSQL on-access expire commit/rollback, access-vs-expiry race smoke.
- **Phụ thuộc / scope:** T15; M.
- **Commit:** `feat(connection): centralize bounded invitation and request expiry`.
- **Kết thúc:** Commit + push + expiry state evidence.

### [x] T17 — Scheduler expiry nhiều batch, retry an toàn

- **Files:** `ConnectionExpiryJob`, config/scheduling wiring, job tests, expiry service refinements nếu cần.
- **Thực hiện:** Job interval/batch configurable (đề xuất 60s/100); gọi transactional bean qua proxy, transaction ngắn; stable ordering, không giữ nhiều student locks trái thứ tự. Log counters/duration/error không PII; failure item retry ở lần quét tiếp theo.
- **Acceptance:** Không cần user access vẫn EXPIRED + notification; chạy lại/hai instance không double transition; một item lỗi không làm job ngừng vĩnh viễn.
- **Verification:** PostgreSQL multiple batches/restart/two schedulers/user race, transaction proxy kiểm chứng; config disable cho test phù hợp.
- **Phụ thuộc / scope:** T16; M.
- **Commit:** `feat(connection): schedule automatic expiry with safe retries`.
- **Kết thúc:** Commit + push + FR-13 evidence.

**Checkpoint 4:** job thật chạy, guards không phụ thuộc độ trễ job, expiry không phá max5/unique/notification.

## Phase 5 — Query, privacy và lịch sử

### [x] T18 — Batch hydrate inbox và bổ sung deadline

- **Files:** Profile/IAM public batch APIs + implementations/repositories, query service/output, inbox HTTP mapper/tests.
- **Thực hiện:** Batch goals/senders theo IDs một trang hoặc tập list; bỏ lookup per-item/controller; deadline từ ConnectionGoalDto. Invitation cũng batch inviterName/inviterRole/reinviteAfter cho lịch sử đã scope để đáp ứng consent UC3.2/cooldown UC3.1; POST invite vẫn generic. Safe missing metadata theo T01 (controlled409, không giả role/drop item); BR-37 whitelist fields.
- **Acceptance:** Inbox chỉ thuộc Tutor hiện tại, không contact/Parent profile/goal khác; field cũ giữ, deadline bổ sung; query metadata không tăng theo N.
- **Verification:** JSON privacy + batch interaction tests; PostgreSQL query counts 1/20/100, OSIV false; Profile/IAM regression. Bắt buộc T18a/T18b/T18c riêng commit/push. Giữ mọi Profile constructor caller, gồm `MatchingCandidatesPostgresTest`, cùng commit khi constructor thay đổi; không đổi enum IAM ngoài ACTIVE/DEACTIVATED.
- **Phụ thuộc / scope:** T17; M mỗi slice.
- **Commit:** Không tạo commit cha; dùng T18a–T18c tại mục 6.
- **Kết thúc:** Commit + push + query-count evidence.

### [x] T19 — Paginated history và Admin link lookup

- **Files:** list/page repository methods, query service, routes/DTOs mới, contract/tests, index migration nếu EXPLAIN yêu cầu.
- **Thực hiện:** Thêm page endpoints theo T01, default20/max100 và stable tie-breaker; giữ list endpoints/shape cũ. Admin lookup by either party email, minimal output. COUNT/EXISTS thay full history load cho pending/cooldown; đề xuất index từ số đo.
- **Acceptance:** Không mất/nhân bản item khi page cùng timestamp; Parent scope hiện tại; Admin lookup đúng role, không expose chung; không silent truncation list cũ.
- **Verification:** Pagination/filter/missing/empty/security tests; PostgreSQL EXPLAIN/query counts gồm count query; index migration nếu có phải update migration manifest/count tests cùng commit. Bắt buộc T19a/T19b riêng theo mục 6.
- **Phụ thuộc / scope:** T18; M mỗi slice.
- **Commit:** Không tạo commit cha; dùng T19a/T19b tại mục 6.
- **Kết thúc:** Commit + push + API/performance evidence, giới hạn legacy list ghi rõ.

**Checkpoint 5:** BR-37 đầy đủ, metadata không N+1, list compatibility và page/Admin authorization pass.

## Phase 6 — Kiểm chứng toàn diện, integration và cleanup

### [x] T20 — Hoàn thiện ma trận unit/service tests

- **Files:** parameterized state/authorization/time/input tests và resources tại module Connection; sửa CSV minh họa Connection trong `docs/in-development-testing-specification.md` theo oracle đã chốt.
- **Thực hiện:** Bù các ô còn thiếu sau focused tests; giữ đủ 8 scenarios cũ; tests chia theo service. Cover IDs account/profile, parent multiple students, inactive accounts, cooldown/quota boundaries và event recipients. Thay CSV47/49giờ/states/error codes sai bằng TTL14ngày/statuses/shared error codes thật; không tạo enums từ ví dụ cũ.
- **Acceptance:** Mọi transition terminal/role/owner có negative case; mọi bug đã sửa có regression; không test chỉ phản chiếu cách implementation được viết.
- **Verification:** `mvn -pl modules/connection -am test`, đọc report counts/skip; review matrix -> test name.
- **Phụ thuộc / scope:** T19; M.
- **Commit:** `test(connection): complete transition permission and boundary matrices`.
- **Kết thúc:** Commit + push + matrix/report.

### [x] T21 — REST với security filter thật và error contract

- **Files:** controller/security integration tests tại app, JSON fixtures, SecurityConfig và GlobalExceptionHandler cùng regression tests nếu cần mapping400.
- **Thực hiện:** 10 routes cũ + routes mới; unauthenticated/wrong role/wrong owner/DEACTIVATED; malformed/nullable validation, envelope/conflict409/rate limit429. Kiểm chứng SPA CSRF mà main `d208371` đã bật: client gửi token hiện có, thiếu/sai token403, hợp lệ đi tới nghiệp vụ. Giữ policy main mới/CORS/session, không mở lại exemption `/api/v1/**` của baseline cũ. Mapping malformed JSON/UUID thành400, regression shared handler callers. Assert whitelist privacy/no PII và scoped Admin lookup.
- **Acceptance:** Filter thật, không chỉ mocked CurrentUser; accept missing không thành decline; error code/status đúng T01; Connection writes có CSRF protection và các API khác giữ policy main mới. Ghi rõ điều kiện client token trước rollout.
- **Verification:** Chạy tests qua filter chain; kiểm tra actual report. Controller direct invocation không tính là HTTP/filter coverage.
- **Phụ thuộc / scope:** T20; M.
- **Commit:** `fix(connection): enforce secured REST contracts and privacy`.
- **Kết thúc:** Commit + push + route coverage/evidence.

### [x] T22 — PostgreSQL migration, atomicity và concurrency matrix

- **Files:** app integration tests/support fixtures, migration tests và test config cần thiết.
- **Thực hiện:** Thực hiện toàn bộ mục 8.3 của plan; hai transaction độc lập + barrier. Bao gồm double invite/accept, accept-cancel-expire, revoke-create, request thứ5/6, quota10/11, relink, listener/audit/class failure và outer rollback. Clean install + upgrade DB có dữ liệu.
- **Acceptance:** DB invariants vẫn đúng sau commit; không swallowed rollback-only exception; không duplicate class/notification/audit; migrations cũ checksum nguyên vẹn.
- **Verification:** PostgreSQL thật đúng version dự án; report không skip; bounded test timeout, không random sleeps. Bắt buộc T22a/T22b/T22c tại mục 6, mỗi task riêng commit/push.
- **Phụ thuộc / scope:** T21; M mỗi slice.
- **Commit:** Không tạo commit cha; dùng T22a–T22c.
- **Kết thúc:** Commit + push + DB version/results; môi trường chưa chạy thì task chưa xong.

### [x] T23 — ArchUnit đúng package và chống pass rỗng

- **Files:** Connection boundary test, app ArchitectureTest trên base mới nhất, test dependency pom nếu cần.
- **Thực hiện:** Reuse/fix rule đang có, không ghi đè Discovery rule đã hoàn thiện. Target `vn.edufit.connection..` và package modules thực tế; assert imported classes >0; cấm web->infra/application->web/API->JPA và cross-module non-api/cycle.
- **Acceptance:** Negative probe làm rule fail; probe được xóa trước commit; positive suite pass trên source thật.
- **Verification:** ArchUnit report/negative probe evidence, compile cả affected module; review không allowEmptyShould che lỗi.
- **Phụ thuộc / scope:** T22; M.
- **Commit:** `test(connection): enforce nonempty architecture boundaries`.
- **Kết thúc:** Commit + push + rule evidence.

### [x] T24 — Tích hợp Discovery/consumer và loại bỏ legacy caller

- **Files:** caller integration tests tại app/Discovery, wiring tối thiểu, obsolete imports/methods nếu scan chứng minh không còn dùng.
- **Thực hiện:** Base Discovery đã tích hợp ở T00b; đây là final regression, không phải lúc mới merge. Kiểm tra Parent match/revoke denial/Student access; facade ACTIVE class cho Scheduling contract. Scheduling `cfa9a8f` có MockConnectionValidationAdapter default true, chưa gọi facade thật; ghi production adapter/classId + profile-ID mapping là dependency bàn giao Scheduling riêng. Scan legacy remnants; không xóa shared sinks còn caller khác, không thêm `connection -> discovery`.
- **Acceptance:** Dependency một chiều, callers không gãy; test intermodule chạy trên code tích hợp thật; phần Scheduling chưa có chỉ báo contract-level, không fake E2E.
- **Verification:** Discovery/Profile/IAM/Connection/Notification/Audit/app suites; OSIV/query report và full reactor checkpoint. Nếu cần integrate branch phải thành task/prerequisite commit riêng, không giấu trong cleanup.
- **Phụ thuộc / scope:** T23 và Discovery base tích hợp; M.
- **Commit:** `refactor(connection): finalize consumer integration and remove legacy paths`.
- **Kết thúc:** Commit + push + source SHA/integration report.

**Checkpoint 6:** ma trận test thực chạy, DB race/migration/atomicity pass, ArchUnit không rỗng, integration đúng base; chạy `mvn verify` nếu config đầy đủ và ghi failures ngoài scope riêng.

## Phase 7 — Docs bàn giao giống Discovery

### [x] T25 — Đồng bộ API, flow, diagram, Q&A và báo cáo cuối

- **Files:** `docs/connection/connection-implementation-plan.md`, `docs/connection/connection-module-flow.md`, `docs/connection/connection-api-contract.md`, `docs/connection/connection-verification-report.md`, diagram assets, hai file tasks.
- **Thực hiện:** Thay mô tả skeleton/expiry chỉ on-access bằng final code; diagram architecture/invite/revoke/request-class/expiry và transaction/event semantics. Export ảnh/SVG bằng pipeline repo hiện có nếu khả dụng, mở kiểm tra trước nhúng. Cập nhật Q&A theo implementation, số đo query/p95/dataset và test report thật.
- **Acceptance:** Người đọc biết file/entrypoint -> rule -> transaction -> DB -> event -> consumer; yêu cầu mapping không sót; rủi ro/dependency/not-run ghi rõ. Hai plan và docs cũ không mâu thuẫn.
- **Verification:** Links/diagram render/JSON examples kiểm tra; report test counts/skip/runtime/env, `git diff --check`; final scope diff không chứa thay đổi ngoài task.
- **Phụ thuộc / scope:** T24; docs batch có thể nhiều file, không thêm runtime feature.
- **Commit:** `docs(connection): document final flows contracts and verification evidence`.
- **Kết thúc:** Commit + push + SHA final, task/subtask commit list, tổng kết acceptance và phần còn blocked; không tự merge.

**Checkpoint cuối:** Checklist chỉ xanh khi có evidence, mọi task commit đã push, không gộp task; bộ docs phản ánh code và không che phần chưa kiểm chứng.

## 3. Checklist lặp lại trước/sau mỗi commit

- [ ] Đúng branch/base; đã đọc docs/source task; dependency completed và pushed.
- [ ] Chỉ sửa scope task; giữ nguyên dirty files ngoài phạm vi.
- [ ] Acceptance pass; focused/affected tests có report, DB tests cần thiết không skipped.
- [ ] Diff review cả quyền, transaction, constraint, privacy và public contract.
- [ ] `git diff --check` pass, chỉ stage explicit paths + checklist liên quan.
- [ ] Một commit cho task/subtask; commit message đúng nội dung cuối.
- [ ] Push ngay; remote SHA khớp local, không force push.
- [ ] Báo SHA, thay đổi, test results, giới hạn/điểm cần review rồi mới sang task sau.

## 4. Mẫu báo cáo sau mỗi task

```text
Task: Txx — tên task (hoặc Txxa nếu đã tách trước khi làm)
Branch/base: feat/connection / <base SHA>
Commit: <SHA> — <message>
Push: origin/feat/connection xác nhận cùng SHA / blocked kèm lý do
Thay đổi: <hành vi/cấu trúc cụ thể>
Acceptance: <từng điều kiện và evidence>
Verification: <command, suite, passed/failed/skipped/not-run, report>
Ngoài phạm vi/giới hạn: <nếu có>
Task kế tiếp: <ID, dependency đã đủ hay còn thiếu>
```

## 5. Execution Notes

### T00 — 11/10/2026

- Folder EduFit đã ở `feat/connection` từ base `43e7461`; checkout EduFit-connection detached, không còn giữ branch. Không thay đổi refs `main`/`feat/discovery`.
- Discovery WIP stash `6f2a3f29f0cfd5e979a4d8813b2a6df8581e86ee`, patch SHA256 `DEFFE85B00477A595D949D36D67E04E2F349CE7E928043C4D75E11804B43D38E`; bản patch nguồn và stash khớp. Backup tại `.codex-doc-review/connection-phase0/` (ignored); giữ stash, không apply vào Connection. Stash docs riêng chỉ làm backup khi switch, không apply đè README Discovery.
- Baseline JDK 21.0.10/Maven wrapper 3.9.16: Connection 8, Profile 26, IAM 32, AI 10, Shared 22 = **98 tests, 0 failures/errors/skips**. Dùng log lần chạy mới, không cộng XML cũ từ Discovery.
- Lần chạy sandbox bị kẹt Mockito attach pipe; đã dừng và chạy lại ngoài sandbox thành công, không sửa code/TLS bypass.
- Fetch mới đưa `origin/main` tới `6755121`; T00a cần resolve FlywayMigrationScriptTest conflict và ghi JaCoCo mặc định false của upstream, chạy strict gate riêng để thấy gap. Xem report; chưa thực hiện refactor Phase 1.

Lượt review cuối đã fetch remote thành công, đọc committed source và dirty diff ở checkout Discovery, mô phỏng merge bằng merge-tree; kết quả/SHA tại plan 0.4–0.5. Đã sửa hai tài liệu và chuyển vào namespace `tasks/connection/`. Chưa chạy baseline Maven/PostgreSQL trên base tích hợp, chưa merge code, chưa commit/push. Không tick task triển khai trước.

Theo yêu cầu workspace cập nhật, đã chuyển hai tài liệu này từ folder EduFit-connection sang **EduFit/tasks/connection/** và gom docs/flow/SVG Connection hiện có vào **EduFit/docs/connection/**. Chưa switch branch hoặc sửa code; checkout cũ không bị xóa. Các task sau chỉ thực hiện tại folder EduFit trên feat/connection sau bước chuẩn bị an toàn của T00.

### T00a — 11/10/2026

- T00 commit/push: `1fcad8f13ffee85aa64ec6075fcf512bd128fd04`.
- Merge main `6755121` vào Connection; conflict Flyway test và duplicate version được giải quyết, seed nhập từ main thành V1.10 với nội dung nguyên vẹn. Connection V1.09 không đổi checksum.
- Clean full verify: 133 tests, 0 fail/error/skip; migration bổ sung: 4 tests gồm clean/upgrade/manifest, đều pass. Strict verify fail tại Shared LINE 75%/BRANCH 71%; ghi baseline, không sửa module ngoài scope hoặc hạ ngưỡng.
- Xem báo cáo Phase 0 cho lệnh/log/giới hạn lịch sử database. Chưa refactor Phase 1.

### T00b — 11/10/2026

- T00a commit/push `e5871742bdcee9a024db29b8191f4fe9ab6a27b7`; tích hợp Discovery `dd9068e` và resolve đủ 6 file theo báo cáo Phase 0.
- Clean full verify **301 tests**, 0 failure/error/skip, 3:58 phút. Connection13, Profile44, Discovery104, App52; PostgreSQL matching workflow9/read22/candidates6/Flyway2 đều không skip. Architecture boundary6 + architecture3 pass.
- Strict coverage vẫn fail tại Shared75%/71%, baseline không đổi. Giữ upstream config, không sửa ngoài scope. `git diff --cached --check` sạch. Docs task/plan Discovery giữ nguyên bytes; main/Discovery refs và stash WIP giữ nguyên.

### T01 — 11/10/2026

- T00b commit/push `12c1e9b025cb049790a9db2d8021c187ab59dbf6`; main/Discovery đã là ancestors của Connection, nguồn không bị push ngược.
- Contract chốt10routes/schema/errors, required accept, consent/cooldown, WITHDRAWN system closure, deadline/privacy, page/Admin lookup. Slots v1 + compatibility/strict rollout gate giữ legacy reader; chưa inventory DB thật, không tự bật strict.
- Bổ sung yêu cầu SRS inviter name/role vào T18c; CSV48giờ cũ sửa tại T20; main CSRF bypass được ghi baseline và scope fix riêng Connection tại T21. Không sửa runtime các hành vi mới trong T01.
- Fixtures JSON/link docs được kiểm tra; review diff/docs namespace, không mất tasks/plan.md/tasks/todo.md của Discovery. Phase 1 vẫn chưa bắt đầu.

### Phase 1 — 11/10/2026

- T02 `e08e96c`, T03 `09d62da`, T04 `c558b9e`, T05 `71a08ab`, T06 `0b6e81c` đã commit/push riêng lên feat/connection. T07 dùng commit `refactor(connection): split request and scoped query services`, chứa cập nhật checkpoint này.
- T07 xóa service cũ; chuyển hết caller và expected architecture class trong cùng task. Discovery production code và tasks/plan.md/tasks/todo.md giữ nguyên; không thay migration/API facade.
- Clean reactor verify 348 tests pass, Connection 55 + App 57, PostgreSQL không skip. Strict verify tái sử dụng execution data full run vẫn FAIL tại Shared baseline; Connection75.12% lines /47.73% branches còn gap (baseline warning41%/22%). Không hạ ngưỡng; quality gate strict vẫn chưa đạt.
- Main/Discovery refs không đổi, stash WIP Discovery nguyên vẹn. Chi tiết từng task/logs/review/giới hạn ở [báo cáo Phase 1](../../docs/connection/phase1-validation.md). Dừng trước T08a.

### Hotfix Flyway — 11/10/2026 (ngoài Phase2)

- [x] F01 — Khôi phục seed main V1.09 đúng checksum, giữ nguyên V1.10, chuyển SQL Connection sang V1.11; cập nhật manifest và regression DB main. Commit/push `e616f4b`, affected clean verify75 tests pass không skip.
- [x] F02 — Backup/clone DB local, kiểm chứng fingerprints31 bảng cũ, migrate/validate/rerun local và khởi động backend Hibernate validate thành công. Đồng bộ flow/manifest và ghi [báo cáo hotfix](../../docs/connection/flyway-fix-validation.md); commit docs riêng sau F01.
- Các tham chiếu Connection V1.09 trong execution notes Phase0/Phase1 là lịch sử trước hotfix; manifest hiện hành là V1.09 seed, V1.10 seed, V1.11 Connection. DB lineage cũ Connection V1.09 cần đối soát riêng như báo cáo, không auto repair. Chưa bắt đầu T08a.

## 6. Task con bắt buộc — mỗi dòng là một commit và push

Các nhóm bên dưới **thay thế commit cha**, không còn là gợi ý “nếu lớn thì tách”. Mỗi task con kế thừa acceptance/verification/end-of-task của nhóm cha, đồng thời phải đáp ứng điều kiện riêng dưới đây; thêm focused tests cùng commit. Files là phạm vi chính, imports/fixtures liên quan phải sửa để reactor vẫn build.

| Task thực thi | Files / thay đổi có thể review riêng | Acceptance và verification riêng | Phụ thuộc | Commit message |
| --- | --- | --- | --- | --- |
| [x] T08a | Clock injection vào services, config/context tests | Dùng Clock IAM hiện có, chỉ một candidate; quota/time fixed-clock tests pass | T07 | `refactor(connection): reuse shared clock for deterministic time` |
| [x] T08b | Invitation/link mutator guards, policy và parameterized tests | Terminal reject, deadline/cooldown đúng biên; focused link/invite regression | T08a | `fix(connection): guard invitation and link transitions` |
| [x] T08c | Request mutator guard/policy và tests | Request terminal/action matrix và expiry boundary pass | T08b | `fix(connection): guard request transitions` |
| [x] T10a | Public event records + contract tests | Payload Java/public IDs, no PII, compile all callers | T09 | `feat(connection): define public transition event records` |
| [x] T10b | Notification listener/wiring/pom + transactional tests | BEFORE_COMMIT failure rollback, no outside-transaction execution, graph acyclic | T10a | `feat(notification): consume connection transitions transactionally` |
| [x] T11a | Invitation service/repository + tests | Two-way consent, ACTIVE/DEACTIVATED, generic response, known-history cooldown, closure | T10b | `fix(connection): enforce invitation consent and privacy` |
| [x] T11b | Invitation event publishers + notification listener/tests | Create/accept/decline events + notifications đúng một lần; rollback atomic | T11a | `refactor(connection): publish invitation transition events` |
| [x] T12a | Structured audit input + adapter/entity + migration/tests | Old sink API vẫn hoạt động; snapshot đủ accounts/reason; Flyway count/fixtures update, clean+upgrade pass | T11b | `feat(audit): persist structured connection revocation snapshots` |
| [x] T12b | Link revoke service + DB/authorization tests | Student/Admin đúng quyền; audit failure rollback link; không cascade delete | T12a | `fix(connection): record revocation and audit atomically` |
| [x] T12c | Revoke event + listener/tests | Admin notify cả hai, Student notify Parent; reason không lộ, rollback pass | T12b | `fix(notification): complete link revocation recipients` |
| [x] T13a | Slot validator + rollout config + request validation DTO/tests | GrammarT01; tests legacy-compatible/strict-v1, legacy read nguyên vẹn; strict chỉ bật sau data/client inventory; message/slot biên pass | T12c, T01 | `fix(connection): validate connection request slots and message` |
| [x] T13b | Create/cancel request rules/repository + tests | Owner/goal/tutor/limits/ACTIVE class/cancel guard pass; no unauthorized write | T13a | `fix(connection): enforce request creation and cancellation rules` |
| [x] T13c | Request create/cancel events + listener/tests | Tutor/Student notification đúng ma trận, không duplicate direct sink | T13b | `fix(notification): notify request creation and cancellation parties` |
| [x] T18a | Profile public batch goal method + implementation/repo/tests | Additive API, giữ bounded candidates/catalog; sửa constructor fixtures cùng commit nếu cần; Profile tests | T17 | `feat(profile): expose batch goal metadata for connection` |
| [x] T18b | IAM public batch summaries + implementation/repo/tests | Additive API; không đổi auth/enum; IDs chỉ trả summary cần thiết; IAM regression | T18a | `feat(iam): expose batch sender summaries for connection` |
| [x] T18c | Connection query/inbox/invitation DTO/mapper + query/JSON tests | Batch deadline + inviterName/role/reinviteAfter, generic POST/privacy; missing metadata409; counts1/20/100 và OSIV pass | T18b | `perf(connection): batch scoped inbox and invitation metadata` |
| [x] T19a | Paged history repo/service/routes + tests/index nếu cần | List cũ giữ shape; page stable/default20/max100; migration test update nếu có | T18c | `feat(connection): add compatible scoped history paging` |
| [x] T19b | Admin lookup query/route/DTO + security tests | Chỉ ADMIN, tối thiểu dữ liệu, lookup theo email hai phía, no public enumeration | T19a | `feat(connection): add authorized admin link lookup` |
| [x] T22a | PostgreSQL Flyway clean/upgrade tests/support | Migrations/checksums/data history/JSONB/constraints pass, no skip | T21 | `test(connection): verify migration upgrades and retained history` |
| [x] T22b | Atomicity/listener/audit/class failure tests | Outer rollback/commit/flush failures đúng, không nửa vời; no skipped DB tests | T22a | `test(connection): verify atomic transition side effects` |
| [x] T22c | Concurrency tests/barriers/fixtures | Toàn bộ race matrix trong plan 8.3 bằng separate connections; không random sleeps | T22b | `test(connection): verify concurrent requests and state transitions` |

Dependency của task cha kế tiếp dùng **task con cuối nhóm**: T09 sau T08c; T11a sau T10b; T14 sau T13c; T20 sau T19b; T23 sau T22c. T14–T17 và các task chưa chia vẫn mỗi task một commit/push. Không tạo thêm commit tổng kết cha.

**Quality gate áp dụng mọi task code:** focused tests + affected-module `verify`; checkpoint chạy full reactor verify và strict `-Djacoco.haltOnFailure=true`. Upstream default false chỉ cung cấp report/warnings, không chứng minh coverage gate pass. Failure từ baseline ngoài scope phải báo module/counter/report riêng, không hạ ngưỡng hoặc đánh dấu strict suite pass. Lệnh chỉ chạy `test` không chứng minh gate ở phase `verify`.

### Phase 2 execution

- T08a: shared Clock and deterministic boundary/context tests; evidence in [Phase 2 validation](../../docs/connection/phase2-validation.md).

- T08b/T08c: terminal/action matrices và deadline/cooldown guards đã verify và push riêng.
- T09: typed conflicts, expiry commit trước HTTP409 và default rollback cho required write failures; PostgreSQL flush/deferred/outer-transaction evidence đã pass.
- T10a/T10b: event records và listener BEFORE_COMMIT/writer MANDATORY nền đã verify. Chưa chuyển production publishers hoặc hoàn tất recipient matrix; việc này đi cùng các task Phase3/expiry.
- Checkpoint2 functional: `clean verify` toàn reactor 437 tests pass, zero failure/error/skip. Strict coverage tái sử dụng full-run execution data vẫn FAIL tại Shared (LINE78/103=75.73%, BRANCH20/28=71.43%). Connection LINE385/486=79.22%, BRANCH99/194=51.03%; Notification LINE17/23=73.91%, BRANCH3/4=75.00%, cũng dưới ngưỡng80%. Không hạ gate và không tuyên bố strict pass. Chi tiết [Phase2 validation](../../docs/connection/phase2-validation.md).
- Dừng sau T10b; Phase3/T11a chưa bắt đầu. Main/Discovery refs, hai task files Discovery và các stash cũ giữ nguyên; WIP riêng của người dùng không đưa vào commit.


### Merge main mới — 14/10/2026 (ngoài Phase3)

- [x] M01 — Tích hợp main `d208371`; resolve4 conflicts theo main security/manifest13 và giữ Connection service decomposition. Port detach/reload vào persistence fragment, thích nghi LearningGoal integration wiring.
- Focused verify pass; full clean Test/IT verify **656 tests pass, zero failure/error/skip**, Connection PostgreSQL18, Flyway IT3 và main E2E7. Strict coverage vẫn fail tại Shared75.73%/71.43%; Connection77.94%/50.51%, Notification73.91%/75.00% còn gap, không hạ ngưỡng.
- T21 cập nhật theo SPA CSRF của main mới, vẫn pending; Phase3 chưa bắt đầu. [Merge main validation](../../docs/connection/main-merge-validation.md) ghi chi tiết/review/logs/giới hạn. M01 nằm trong merge commit và push feat/connection.

### Phase3 execution — 14/10/2026

- T11a: consent/privacy, bounded pending/cooldown and system closure implemented;182 focused verify tests pass. [Phase3 validation](../../docs/connection/phase3-validation.md) records audit, RED/GREEN and scope.

- T11b: invitation create/accept/decline publish events; BEFORE_COMMIT notification consumer replaces direct writes. 194 focused tests pass, including real entrypoint and database rollback checks; see Phase3 validation.

- T12a: immutable structured audit input, JSONB persistence and V1.14 metadata migration. 18 audit/Flyway/architecture tests pass, including legacy rows/API and upgrade rerun.

- T12b: current ACTIVE actor/Student ownership/Admin reason guards and structured revoke audit in one transaction. 112 affected tests pass with Docker restored, zero skips.

- T12c: revoke event and mandatory notification consumer; Admin receives no private learner notification, Parent/Student recipient matrix and three-write rollback verified. 131 tests pass, zero skips.

- T13a: strict-v1 parser + default legacy-compatible rollout and HTTP/service validation. 291 focused tests pass, including localized/legacy writers and Discovery-AI E2E.

- T13b: ACTIVE actor/tutor account, current goal account ownership, COUNT-based pending limit and cancel guards. 121 tests pass, including PostgreSQL sixth request/expiry quota and Discovery-AI E2E.

- T13c: Request create and cancel publish ConnectionRequestChangedEvent with complete ID metadata, removing direct notification on create. BEFORE_COMMIT listener/writer delivers CONNECTION_REQUESTED to Tutor and Student (if Parent sent), and CONNECTION_CANCELLED to Tutor and Student (if Parent cancelled, without notifying Student about their own cancellation of older Parent requests). 323 focused verify tests pass (Connection 192, App 131), zero failures/errors/skips.
- T14: Tutor response (accept/reject) enforced atomically with class creation and current-authorized recipients; 344 tests pass with zero skips.
- T15: Current parent consent enforced across all connection access entrypoints; 457 tests pass with zero skips. Checkpoint 3 full clean verify 763 tests pass.

### Phase 4 execution — 14/10/2026

- T16: Centralized bounded invitation and request expiry service with stable ordering candidate IDs, lock recheck, and on-access integration. BEFORE_COMMIT listener delivers LINK_EXPIRED to inviter and CONNECTION_EXPIRED to tutor and current-authorized sender. 112 focused tests pass, 26 PostgreSQL tests pass with zero failures/errors/skips. Evidence recorded in docs/connection/phase4-validation.md.
- T17: Scheduled ConnectionExpiryJob with configurable interval/batch size, multi-batch candidate draining, transactional Spring proxy isolation, safe retries, and non-PII logging. 6 unit tests pass, 29 PostgreSQL tests pass (+3 new tests: unassisted background expiry, multi-batch processing, concurrent scheduler dedup). Checkpoint 4 completed; 124 regression tests pass with zero failures/errors/skips. Evidence recorded in docs/connection/phase4-validation.md.

### Phase 5 execution — 14/10/2026

- T18a: Additive ProfileFacade.findGoalsForConnection batch method querying goals and student metadata in 2 queries without N+1. Preserved ProfileFacadeImpl constructor and bounded candidates logic. 24 ProfileFacadeImplTest pass, 55 profile tests pass (including 6 PostgreSQL tests), 114 Discovery tests pass. Evidence recorded in docs/connection/phase5-validation.md.
- T18b: Additive IamFacade.findUserSummariesByIds batch method querying user accounts in 1 batch query without N+1. Returns Map<UUID, UserSummaryView> containing only non-sensitive summary fields (id, email, fullName, role, status), without exposing password hashes or lockout details. 7 unit tests pass in IamFacadeImplTest, all 39 IAM tests pass with zero failures/errors/skips. Evidence recorded in docs/connection/phase5-validation.md.
- T18c: Batch inbox and invitation metadata with deadline and inviter name/role. Hydrates goals via ProfileFacade.findGoalsForConnection and senders/inviters via IamFacade.findUserSummariesByIds in O(1) constant queries without N+1. Enforces controlled 409 CONFLICT_DETECTED on missing metadata and strict BR-37 whitelist fields. 19 ConnectionQueryServiceTest pass, 24 ConnectionControllerTest pass, 222 connection tests pass, 30 PostgreSQL tests in ConnectionTransactionPostgresTest pass (+1 query-count test with 100 requests). Evidence recorded in docs/connection/phase5-validation.md.
- T19a: Compatible scoped history paging for invitations, links, and connection requests/tutor inbox. Implemented PageResponse<T> application DTO, paged repository queries with stable DESC tie-breakers, 1 <= size <= 50 validation (400 Bad Request on invalid inputs), and REST endpoints (/parent-student/invitations/page, /parent-student/links/page, /requests/page). Legacy unpaged endpoints fully preserved. 30 ConnectionQueryServiceTest pass, 28 ConnectionControllerTest pass, 237 connection unit tests pass, 31 PostgreSQL tests pass (including multi-page DESC ordering and out-of-bounds handling). Evidence recorded in docs/connection/phase5-validation.md.
- T19b: Authorized Admin link lookup by either parent or student email via GET /admin/parent-student/links. Enforced strict ADMIN role check (403 Forbidden for non-admin), anti-enumeration clean empty page on missing email, minimal LinkResponse output excluding revoked links and internal audit reasons. 38 ConnectionQueryServiceTest pass (+6 new tests), 30 ConnectionControllerTest pass (+2 new tests), 247 connection unit tests pass, 32 PostgreSQL tests pass (including admin lookup by parent/student email and exclusion of revoked links). Checkpoint 5 completed; full BR-37 compliance, zero N+1 metadata queries, deterministic pagination, and authorized Admin link lookup verified. Evidence recorded in docs/connection/phase5-validation.md.
- T20: Complete transition permission and boundary matrices. Synchronized docs/in-development-testing-specification.md Section 7.2 with true 14-day invitation TTL, 7-day request TTL, real entity statuses, and shared error codes (NONE, CONFLICT_DETECTED, FORBIDDEN_ACCESS). Added link-invitation-rules.csv and connection-request-matrix.csv under testdata/. Implemented ConnectionMatrixDdtTest (24 tests) parameterized from CSV via JUnit 5 @CsvFileSource, along with strict account vs profile ID distinction, multiple student links per parent isolation, and nanosecond-level 7-day cooldown boundary. All 271 connection module unit tests pass with zero failures/errors/skips. Evidence recorded in docs/connection/phase6-validation.md.
- T21: Secured REST contracts, privacy and CSRF verification. Created ConnectionHttpSecurityPostgresTest with full Spring Boot context, real PostgreSQL testcontainer, Spring Security filter chain and session filter. Verified unauthenticated 401 UNAUTHORIZED on all connection endpoints, SPA CSRF token protection (403 without/wrong token, 200 with valid token), 403 FORBIDDEN on unauthorized admin link lookups, 400 VALIDATION_FAILED on malformed UUID/JSON/missing accept, and strict BR-37 whitelist privacy (zero leakage of student/parent emails, passwords, contacts, or unrequested goals). All 5 integration tests pass with zero failures/skips. Evidence recorded in docs/connection/phase6-validation.md.
- T22a: PostgreSQL Flyway clean/upgrade tests and retained history verification. Implemented connectionUpgradePreservesHistoricalDataAndEnforcesIntegrityConstraints in FlywayMigrationIT, executing migration upgrade from V1.10 baseline to V1.14 on real PostgreSQL testcontainer. Verified historical data retention across link_invitation, parent_student_link (REVOKED), connection_request (PENDING/REJECTED), and complex JSONB desired_slots with version column defaults (0). Verified V1.11 relink support after revocation, partial unique indexes (uq_parent_student_confirmed, uq_connection_request_pending, uq_link_invitation_pending, uq_class_student_tutor_active), and check constraints. All 5 FlywayMigrationIT tests and 2 FlywayMigrationScriptTest pass with zero failures/errors/skips. Evidence recorded in docs/connection/phase6-validation.md.
- T22b: Atomic transition side effects and failure rollback matrix. Added and verified comprehensive atomicity test cases in ConnectionTransactionPostgresTest: outer transaction rollback for invitation acceptance and request creation leaving zero partial rows in PostgreSQL; BEFORE_COMMIT transactional notification listener failure rollback for cancel, reject, and student revoke preserving previous states (PENDING/CONFIRMED); and audit/class persistence constraint failure rollback. All 37 integration tests pass with zero failures/errors/skips on Testcontainers PostgreSQL 17. Evidence recorded in docs/connection/phase6-validation.md.
- T22c: Concurrency tests and race matrix. Implemented and verified comprehensive multi-threaded race conditions in ConnectionTransactionPostgresTest on Testcontainers PostgreSQL 17: opposite-direction double invite pair locking, concurrent quota consumption exceeding 10 attempts rejecting the 11th, concurrent double accept invitation creating exactly one confirmed link, concurrent accept vs decline/withdraw invitation resolving to single terminal state, concurrent 5th and 6th pending request to different tutors enforcing max 5 pending limit, concurrent double accept request creating exactly one class, concurrent accept vs cancel request resolving deterministically, and concurrent revoke vs parent create request. All 45 integration tests pass with zero failures/errors/skips. Evidence recorded in docs/connection/phase6-validation.md.
- T23: Non-empty ArchUnit architecture boundary enforcement with negative-probe evidence. Verified rules restricting application->web, web->infra, API->JPA, and enforcing public API access for outsiders and public dependencies for Connection. Negative probe in ConnectionFacadeImpl introducing application->web dependency failed ArchUnit exactly as expected. All 14 architecture tests pass with zero failures/errors/skips. Evidence recorded in docs/connection/phase6-validation.md.
- T24: Finalize consumer integration and remove legacy paths. Verified Discovery integration (Parent confirmed matching, immediate revoke denial, Student goal access retention) and AiDiscoveryConnectionE2ETest (7/7 passed). Audited Scheduling contract (ConnectionFacade.findActiveClass) and cataloged production adapter handover dependency. Scanned 50 source files for dead code/deprecated APIs. Full reactor build passed (919 tests GREEN). Checkpoint 6 completed. Evidence recorded in docs/connection/phase6-validation.md.

### Phase 7 execution — 14/10/2026

- T25: Synchronized complete documentation set and final verification handover. Updated docs/connection/connection-implementation-plan.md, connection-module-flow.md (with architecture and sequence diagrams), connection-api-contract.md, and created comprehensive docs/connection/connection-verification-report.md. Full reactor verify passed with 919 tests GREEN, zero failures, zero errors, zero skips. All task checklists marked complete. Evidence recorded in docs/connection/connection-verification-report.md.
