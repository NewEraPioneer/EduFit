# Discovery: Final Implementation Plan

Trạng thái: Phase 0-6 (T00-T15) hoàn tất trên `feat/discovery` theo phạm vi đã duyệt; chưa merge vào `main`.

Nguồn đối chiếu: `D:/backend_discovery_implementation_plan.md`, code hiện tại trên `feat/discovery`, SADS, module structure guide và master sprint plan trong `docs/`. Trước mỗi task phải đọc lại phần docs liên quan và code bị tác động.

## 1. Kết quả đối chiếu

Plan trước đã có các nhóm công việc chính nhưng thiếu cách chia commit, kiểm thử REST/AI cụ thể, metadata cấp học, kiểm chứng transaction của event và bước loại bỏ API full scan sau khi chuyển caller. Bản này bổ sung các phần đó.

| Yêu cầu trong plan bạn gửi | Task thực hiện | Ghi chú đối chiếu |
| --- | --- | --- |
| DiscoveryFacade, summary DTO, MatchingExecutedEvent | T02, T10 | Bổ sung actor để kiểm tra quyền, quota và audit |
| DiscoveryFacadeImpl | T10 | Dùng chung matching service với REST |
| domain/policy/MatchScorer và MatchWeights | T06, T07 | Mẫu code mục 7.2 còn tính 3 yếu tố; áp dụng công thức 5 yếu tố mục 2.3 |
| MatchScore đủ 5 thành phần | T07 | Cập nhật mapper và test cùng commit để build được |
| Giữ AvailabilitySlot, SubjectLevel, MatchCriteria, TutorCandidate | T04, T07 | Giữ vai trò các model; bổ sung metadata tối thiểu nếu scorer cần |
| Bỏ full scan, query ứng viên tại Profile | T05, T09, T14 | Có giới hạn và batch hydration; xóa method cũ sau khi chuyển caller |
| Sửa quyền Parent | T08 | Dùng confirmed parent link; bỏ workaround cho phép mọi goal |
| Log và event sau matching | T01, T10 | Kiểm tra cả failure/rollback, dữ liệu actor/student/goal |
| Move/rename entity và repository | T01 | Giữ mapping bảng hiện tại |
| Giữ routes và request/response | T03, T07, T12 | Giữ URL và field cũ; bổ sung breakdown mới theo cách tương thích |
| Search nhiều bộ lọc, phân trang, tutor detail | T03, T12 | Kiểm thử VERIFIED, dữ liệu rỗng, not found và tổ hợp filter |
| Two-phase hydration, OSIV false, không N+1 | T05, T12 | Đo query thực tế, tính cả count query và query phụ có chủ đích |
| AI grounded facts, Top 1, quota 10/ngày, timeout 15s | T11 | Kiểm tra wiring timeout thật, không chỉ mock exception |
| Parameterized scorer tests | T07 | Bao gồm mức giá 100/130/150%, overlap minutes, rating confidence |
| Application/architecture/Profile tests | T03-T14 | Test theo từng thay đổi, có integration PostgreSQL |
| Docs, flow, giải thích kiến trúc và Q&A | T15 | Diagram xuất ảnh và nhúng vào docs |

## 2. Quyết định cần áp dụng nhất quán

1. Công thức: subject 30%, level 20%, price 20%, time 15%, rating 15%. Điểm thành phần và tổng nằm trong 0..100; rounding chỉ ở ranh giới đã thống nhất.
2. Subject, trạng thái VERIFIED và tương thích hình thức/khu vực là điều kiện bắt buộc. Level, price, time, rating là điểm mềm. Không loại ứng viên chỉ vì lệch cấp, vượt giá hoặc không trùng giờ; các trường hợp này có thể nhận điểm thành phần 0.
3. Cấp học exact = 100, liền kề = 50, còn lại = 0. Dùng thứ tự nghiệp vụ `education_level.sort_order`, không trừ numeric ID. Liền kề ban đầu được hiểu là hai cấp kế tiếp nhau trong danh sách đã sắp thứ tự; kiểm tra seed để bảo đảm ý nghĩa đúng với docs.
4. Giá: <= budgetMax đạt 100; tại 130% đạt 50; tại 150% đạt 0. Kiểm tra budgetMax = 0 và dữ liệu thiếu theo validation hiện có.
5. Time fit = phút giao nhau / tổng phút mong muốn. Hợp nhất slot chồng nhau theo ngày, dùng interval nửa mở và tránh đếm trùng cả tử số lẫn mẫu số.
6. Rating: `(ratingAvg / 5) * (0.5 + 0.5 * min(1, reviewCount / 5))`; tutor mới nhận 35/100. Không dùng phép chia nguyên cho confidence.
7. topN mặc định 5, tối đa 10; giá trị không hợp lệ xử lý thống nhất với HTTP validation hiện có và public facade.
8. Candidate cap khởi đầu 200, cấu hình được, pre-order ổn định. Đây là giới hạn tài nguyên và có thể bỏ sót tutor điểm cao ngoài tập ứng viên; T12 phải báo cáo ảnh hưởng ranking, không tuyên bố global Top N chính xác.
9. Public facade nhận actor tin cậy từ context xác thực, không tin userId/role do request body truyền lên. Không để lộ web DTO hoặc JPA entity.
10. Parent chỉ được match goal của học sinh có link CONFIRMED qua ConnectionFacade. Dependency một chiều `discovery -> connection`.
11. Application không import `discovery.web.*`; controller map HTTP DTO sang application input/output. Domain thuần Java.
12. Event được publish trong transaction của run thành công; listener có side effect chỉ xử lý AFTER_COMMIT. Log save lỗi phải rollback; không coi việc publish trong transaction là bằng chứng event đã được xử lý sau commit.
13. Các field response cũ giữ tên và kiểu; breakdown 5 yếu tố được bổ sung. Nếu cần đổi field, phải ghi rõ thay đổi contract trong docs và test trước khi triển khai.

## 3. Cách quản lý branch và commit

- Giữ nhánh làm việc `feat/discovery` theo yêu cầu của bạn. Hiện có thay đổi ngoài phạm vi tại `iam/api/AccountStatus.java`; giữ nguyên và không stage vào commit Discovery.
- Trước T08, base phải có ConnectionFacade và confirmed-link implementation từ `feat/connection`. Kiểm tra lịch sử trước khi tích hợp; ưu tiên base đã merge hai feature. Nếu cần integration riêng, ghi rõ commit nguồn và tránh trộn việc sửa Connection vào task Discovery.
- Không tự merge feature vào main trong bước lập plan này.
- T00 là commit tài liệu plan; T01-T15 mỗi task là đúng một commit gồm code và test liên quan. Không gom nhiều task vào một commit.
- Mỗi task: đọc docs -> sửa code/test -> chạy kiểm tra phù hợp -> review diff -> stage đường dẫn cụ thể -> commit -> push -> báo SHA, thay đổi và kết quả test để bạn review.
- Theo yêu cầu cập nhật, commit đồng nghĩa commit và push ngay sau mỗi task. Không tự merge. Nếu test fail thì task chưa hoàn thành; sửa trong phạm vi task trước khi commit.
- Checklist được cập nhật trong commit của task; ghi SHA trong báo cáo review, tránh cần commit phụ chỉ để ghi SHA của chính nó.

## 4. Phase và task theo từng commit

### Phase 0: Chốt phạm vi và baseline

**T00 — Chốt plan và checklist**

- Commit: `docs(discovery): finalize phased refactor plan`
- Phạm vi: `tasks/plan.md`, `tasks/todo.md`; kiểm tra branch, dirty files, docs và dependency Connection.
- Hoàn thành: requirement trong tài liệu nguồn đều có task; baseline test và trở ngại môi trường được ghi nhận trước T01.
- Kiểm tra: đối chiếu bảng mục 1; `git diff --check`; kiểm tra chỉ stage hai file kế hoạch.

### Phase 1: Chuẩn hóa cấu trúc và contract

**T01 — Chuẩn hóa persistence packages**

- Commit: `refactor(discovery): normalize matching log persistence packages`
- Phạm vi: chuyển `MatchingRunLog` thành `infra/persistence/entity/MatchingRunLogEntity`; repository vào `infra/persistence/repository`; sửa imports và test liên quan.
- Hoàn thành: mapping bảng/cột và hành vi log giữ nguyên; không tạo migration chỉ vì đổi package.
- Kiểm tra: compile, matching service tests, mapping đối chiếu migration V1.08.
- Phụ thuộc: T00.

**T02 — Thêm public API contracts**

- Commit: `feat(discovery): define public matching facade and event contracts`
- Phạm vi: `api/DiscoveryFacade`, `api/dto/TutorMatchSummaryDto`, `api/event/MatchingExecutedEvent`.
- Hoàn thành: facade có actor-aware signature; event có userId/studentId/goalId/resultCount/executedAt; không expose entity/web DTO. Implementation nối ở T10.
- Kiểm tra: compile và review dependency public API.
- Phụ thuộc: T01.

**T03 — Tách application khỏi web DTO**

- Commit: `refactor(discovery): decouple application services from HTTP DTOs`
- Phạm vi: application input/output, DiscoveryQueryService, TutorMatchingService và mapping tại controller.
- Hoàn thành: application không import web; ba endpoint giữ routes, field cũ, validation và error mapping.
- Kiểm tra: service tests và controller JSON regression cho search/detail/match.
- Phụ thuộc: T02.

Checkpoint 1: package chuẩn, public contracts rõ, REST regression pass.

### Phase 2: Chuẩn bị dữ liệu ứng viên

**T04 — Cung cấp thứ tự cấp học cho matching**

- Commit: `feat(profile): expose education level ordering for matching`
- Phạm vi: metadata cấp học qua Profile public API và mapping sang Discovery model, dùng thay đổi nhỏ nhất phù hợp cấu trúc hiện tại.
- Hoàn thành: phân biệt exact/adjacent/other theo môn và thứ tự cấp học; xử lý sort_order thiếu/trùng rõ ràng. Không đổi SubjectLevel chỉ để tiện truy vấn nếu adapter giải quyết được.
- Kiểm tra: Profile mapping tests, fixture ID không liên tục, thứ tự không đồng nhất ID.
- Phụ thuộc: T03.

**T05 — Thêm bounded candidate query tại Profile**

- Commit: `perf(profile): add bounded discovery candidate queries`
- Phạm vi: candidate criteria/API, ProfileFacadeImpl, repository query, batch subjects/availability; cap 200 có cấu hình.
- Hoàn thành: lọc VERIFIED/subject/mode/area tại DB; giới hạn trước hydration; distinct ID và ordering ổn định; không lọc cứng các yếu tố mềm. Giữ method cũ tạm thời để caller hiện tại vẫn build.
- Kiểm tra: PostgreSQL integration tests cho filter, duplicate subjects, hơn 200 tutor và giới hạn query/result.
- Phụ thuộc: T04.

Checkpoint 2: API mới có dữ liệu đủ cho scorer, truy vấn có giới hạn và batch hydration.

### Phase 3: Hoàn thiện domain policy

**T06 — Di chuyển scorer về domain/policy**

- Commit: `refactor(discovery): move match scorer into domain policy`
- Phạm vi: move MatchScorer và unit test, cập nhật imports.
- Hoàn thành: chỉ đổi vị trí; test hiện có vẫn pass, domain không kéo Spring/JPA vào.
- Kiểm tra: scorer tests và compile.
- Phụ thuộc: T05.

**T07 — Triển khai công thức 5 yếu tố**

- Commit: `feat(discovery): implement five-factor matching policy`
- Phạm vi: MatchWeights, MatchScore, scorer, metadata model tối thiểu, mapper breakdown và explanation facts bị ảnh hưởng.
- Hoàn thành: đúng mục 2; weights không âm và tổng bằng 1; ranking tie-break ổn định theo score/time/reviews/registeredAt/tutorId; response cũ vẫn serialize được.
- Kiểm tra: parameterized tests cho từng yếu tố và tổng; giá 100/130/150%; level khác ID; slot chồng/chạm biên/khác ngày/rỗng; rating 0/1/5 reviews; budget 0; tie-break; JSON breakdown.
- Phụ thuộc: T04, T06.

Checkpoint 3: policy đủ 5 yếu tố, test thuần Java bao phủ các biên và ranking.

### Phase 4: Quyền truy cập và matching workflow

**T08 — Sửa quyền Student/Parent**

- Commit: `fix(discovery): authorize parent matching through confirmed links`
- Phạm vi: dependency Connection public API, validateAccess và authorization tests.
- Hoàn thành: Student sở hữu goal được phép; Parent chỉ được phép với confirmed link; pending/rejected/revoked/no link bị từ chối; không có đường facade bỏ qua authorization.
- Kiểm tra: test role/ownership/link status, goal không tồn tại, bảo đảm request bị từ chối không gọi AI hoặc ghi run thành công; kiểm tra dependency cycle.
- Phụ thuộc: T03 và Connection contract đã hiện diện trên base.

**T09 — Chuyển orchestration sang bounded candidates**

- Commit: `perf(discovery): match against bounded profile candidates`
- Phạm vi: TutorMatchingService dùng API T05, mapping criteria/candidates, score/sort/topN.
- Hoàn thành: không gọi full scan; topN mặc định 5/max 10; xử lý empty candidates; cùng input cho kết quả thứ tự ổn định.
- Kiểm tra: service tests xác nhận criteria/cap, candidate count, topN và không gọi API cũ.
- Phụ thuộc: T05, T07, T08.

**T10 — Nối facade implementation, log và event**

- Commit: `feat(discovery): expose matching facade and publish execution events`
- Phạm vi: DiscoveryFacadeImpl, public summary mapping, ApplicationEventPublisher và transaction của matching run.
- Hoàn thành: REST/facade dùng chung nghiệp vụ; log và event cùng actor/student/goal/count; một run thành công kể cả 0 kết quả có một log và một event; failure không để lại run thành công. Listener có side effect nhận event sau commit.
- Kiểm tra: service tests về số lần save/publish và payload; transaction integration test cho commit/rollback; facade authorization regression.
- Phụ thuộc: T01, T02, T09.

**T11 — Kiểm chứng AI explanation và fallback**

- Commit: `test(discovery): verify grounded AI explanations and fallback`
- Phạm vi: MatchExplanationService tests, prompt/fallback facts và cấu hình timeout liên quan; sửa wiring nếu kiểm tra phát hiện thiếu.
- Hoàn thành: chỉ Top 1 gọi AI; quota 10/ngày theo actor; quota exhausted/timeout/unavailable dùng fallback; prompt chỉ lấy facts đã xác minh, không PII; explanation phản ánh điểm 5 yếu tố. Timeout gateway thực tế được cấu hình 15s.
- Kiểm tra: mock timeout/unavailable/success/quota, capture prompt, kiểm tra cấu hình gateway. Nếu cần kiểm chứng timeout runtime, dùng fake provider chậm thay vì gọi LLM thật.
- Phụ thuộc: T07, T10.

Checkpoint 4: matching qua REST và facade có quyền đúng, candidate bounded, AI fallback, log/event nhất quán.

### Phase 5: Kiểm chứng kiến trúc và hiệu năng

**T12 — Kiểm chứng search và PostgreSQL performance**

- Commit: `test(discovery): verify search hydration and candidate performance`
- Phạm vi: integration fixtures/tests cho Profile + Discovery, OSIV false, search/detail/match và báo cáo đo.
- Hoàn thành: query count không tăng tuyến tính theo số tutor; phân trang không duplicate/mất kết quả; đủ associations ngoài transaction; chỉ tutor VERIFIED xuất hiện; các filter và not found đúng contract.
- Kiểm tra: PostgreSQL/Testcontainers với 20, 200 và trên 200 tutor; tính cả count query; query plan và p95 trên môi trường ghi rõ. Đối chiếu NFR-09/NFR-14 theo đúng endpoint; tách thời gian AI khỏi DB/scorer. So bounded ranking với full dataset trong test để báo ảnh hưởng cap.
- Điều kiện môi trường: cần Docker/PostgreSQL; nếu chưa chạy được phải ghi blocked verification, không đánh dấu hiệu năng đã đạt bằng Mockito/H2.
- Phụ thuộc: T05, T09, T11.

**T13 — Cưỡng chế architecture boundaries**

- Commit: `test(discovery): enforce module and layer boundaries`
- Phạm vi: ArchitectureBoundaryTest và app architecture rules liên quan Discovery, dùng package thực `vn.edufit.discovery..`.
- Hoàn thành: domain thuần Java; application không phụ thuộc web; module ngoài chỉ gọi discovery.api; Discovery chỉ dùng public API module khác; không cycle Discovery/Connection. Rules thật sự tìm thấy class cần kiểm tra.
- Kiểm tra: chạy ArchUnit; thử vi phạm tạm để xác nhận rule fail rồi bỏ fixture/import vi phạm trước commit.
- Phụ thuộc: T10, T11.

**T14 — Xóa full-scan API cũ và chạy regression**

- Commit: `refactor(profile): remove obsolete full-scan matching API`
- Phạm vi: xóa findVerifiedTutorsForMatching và helper chỉ còn phục vụ API cũ; không xóa findByStatus nếu caller khác vẫn dùng.
- Hoàn thành: không còn caller/method matching full scan; các module phụ thuộc vẫn build.
- Kiểm tra: rg toàn backend; test Discovery/Profile/Connection và app architecture; git diff --check. Lỗi baseline ngoài phạm vi được báo riêng, không âm thầm sửa vào commit này.
- Phụ thuộc: T12, T13.

Checkpoint 5: regression pass, architecture rules có hiệu lực, bằng chứng PostgreSQL/performance được lưu.

### Phase 6: Docs bàn giao

**T15 — Cập nhật docs và diagram dạng ảnh**

- Commit: `docs(discovery): document matching architecture and execution flows`
- Phạm vi: docs Discovery hiện có, API contract liên quan, hình diagram và mục lục/link docs.
- Hoàn thành: giải thích package/class có gì; flow search/detail/match; facade và event; Parent authorization; công thức/fixtures 5 yếu tố; query cap và giới hạn ranking; AI/quota/timeout/fallback; log transaction; cách chạy test và Q&A. Diagram package/dependency/sequence được render thành ảnh nhúng trong docs.
- Kiểm tra: đối chiếu tên class/method/routes với code; mở ảnh kiểm tra chữ và luồng; link không hỏng; không còn công thức 3 yếu tố hoặc workaround Parent truy cập mọi goal.
- Phụ thuộc: T14.

## 5. Definition of Done và lệnh kiểm tra

Các lệnh Maven chạy từ thư mục `backend`, dùng Maven wrapper nếu repo cung cấp. Không dùng test selector làm reactor phụ thuộc thất bại vì không tìm thấy test; chọn cấu hình phù hợp với pom thực tế.

```powershell
mvn -pl modules/discovery,modules/profile,modules/connection -am test
mvn -pl app -am test
git diff --check
git status --short
```

- Mỗi task có commit riêng với tests liên quan và báo cáo review.
- PostgreSQL integration, query-count và transaction tests chạy thực tế; ghi dataset/môi trường/kết quả p95, không suy diễn đạt NFR từ unit test.
- Ba REST endpoints và public facade đều được kiểm chứng.
- Docs và ảnh flow khớp implementation cuối.
- Thay đổi AccountStatus ngoài phạm vi được giữ ngoài các commit Discovery.
- Phase 1 đã được cho phép triển khai; các phase sau tiếp tục theo phạm vi được duyệt.

## 6. Execution Notes

- T15 / Checkpoint 6 PASS: synchronized Discovery package/class ownership, HTTP and Java API contracts, search/detail/matching flows, confirmed Parent authorization, five-factor formulas and worked 66.95 fixture, bounded-candidate ranking limits, AI quota/deadline/fallback, transaction/event semantics, test commands and Q&A. Added docs index/API contract, updated architecture/overview/performance cross-links, and embedded four PNG diagrams with editable SVG sources and a reproducible renderer. All four images opened for visual review; text-bound and nonblank-pixel checks PASS. Local links, two JSON examples and score arithmetic checked; no Mermaid blocks in Discovery handover docs. Repeated full T14 reactor command: 300 tests PASS, zero failures/errors/skips, including 38 actual PostgreSQL cases and 9 app architecture checks. Original T12 timing snapshot retained, not replaced by rerun timings. Final diff/whitespace review PASS. Documentation/tooling only; unrelated AccountStatus excluded, no main merge. Candidate-cap recall loss, non-atomic AI quota, non-durable events and unverified security-filter behavior remain explicitly documented limitations, not silently declared fixed.

- T14 / Checkpoint 5 PASS: removed obsolete full-scan Profile facade method, its implementation and repository findByStatus after confirming no production caller remained. Removed two obsolete never-call assertions; exact bounded-query assertions remain. Retained shared batch hydration used by detail/candidates. rg finds no legacy method/query references anywhere in backend. Full reactor command: `mvnw.cmd -pl app -am test '-Dtest=*Test,FlywayMigrationIT' '-Dsurefire.failIfNoSpecifiedTests=false' -B -ntp`. All 17 reactor projects SUCCESS; 300 tests PASS, zero failures/errors/skips: Shared 22, AI 12, Storage 6, IAM 32, Profile 44, Verification 16, Connection 13, Discovery 104, app 51. Includes 38 actual PostgreSQL cases (6 candidate + 22 reads/performance + 9 workflow + 1 migration), 9 app architecture checks, all 9 Flyway migrations. Repeated cap oracle still gives 97.00 uncapped vs 78.50 bounded, Top 5 recall 4/5. Diff reviewed and whitespace check PASS; unrelated AccountStatus excluded. No main merge. Phase 6 remains for final docs/diagram synchronization (old diagram snapshots are not the current API reference).

- T13: replaced vacuous vn.edufit.modules patterns with actual module packages. Cross-module dependencies must target api, other modules cannot depend on notification, and root-package slices must be cycle-free (including Discovery/Connection). Discovery domain permits only Java/own domain; application cannot depend on web; public API cannot expose implementation; outside code can only access Discovery API; Discovery's external EduFit dependencies must use public APIs/shared kernel. Empty rules fail explicitly; import guard confirms actual scorer/services/facade/controller and Connection/Profile implementation classes. Temporary application -> controller field produced the expected architecture failure, then probe removed and clean reactor run passed all 9 app architecture checks, zero skips. No production architecture refactors; full regression remains T14.

- T12: actual PostgreSQL 17.11 with all 9 Flyway migrations and detached DTO reads; 22 new integration cases PASS. Search 5 SQL statements including count, detail 3, candidates 3, matching without AI 8 remain fixed at 20/200/240 tutors; empty first page needs 1. Covers individual/combined filters, VERIFIED, 404, overlapping/touching slots and complete non-duplicate pagination. Initial RED exposed PostgreSQL lower(bytea) on null keyword/area parameters; explicit HQL String casts fixed nullable binding. Regression PASS: Profile 44, Discovery 104, app PostgreSQL 31 (22 new + 9 workflow), zero skips. Local p95, representative EXPLAIN and adversarial cap recall recorded in docs/discovery-performance-report.md; no production SLA/security-filter certification.

- T11: tests cover quota 0/9 success, quota 10 fallback, actor-based rolling 24h lookup (Parent pays own quota), exactly one Top 1 AI call, no AI for empty results, grounded five-factor/mode/price prompt with no bio/contact PII, timeout/unavailable/usage-error fallback, and HTTP 200 with correct aiGenerated. Found socket connect/read timeouts did not bound total provider wait: new Spring-wiring test RED (100ms configured, slow provider returned SUCCESS at ~2008ms). Added shared gateway deadline using managed virtual-thread executor, Future timeout/cancellation and existing caller-thread audit logging; GREEN timeout at ~129ms with one TIMEOUT log and cancelled provider. Default Spring timeout 15s and application.yml 15s verified. Cancellation is best-effort, not forced provider termination; DB audit duration is outside provider deadline. Existing quota count-then-call is not an atomic reservation for concurrent requests; event is in-process, not durable delivery. Updated current text docs; final diagrams remain T15.
- Checkpoint 4 PASS: reactor through app compiles; Discovery 104, Profile 44 (6 PostgreSQL cases), Connection 13, AI 12 (including 2 architecture rules and 2 runtime wiring/deadline cases), app 18 (9 PostgreSQL transaction/link states, 1 real Flyway, 2 migration script, 6 exception mapping) all PASS with zero skips. Total selected regression 191 tests; 16 PostgreSQL integration cases actually executed. Tests do not certify security filters, p95/global ranking under cap, or corrected app architecture rules (T12/T13). No external LLM calls. Diff check PASS; unrelated AccountStatus remains excluded.

- T10: DiscoveryFacadeImpl delegates to the same authorized transactional service as REST and maps public summaries (0..100 score). Each successful run, including zero results, saves one log then publishes one event with identical actor/student/goal/count/time. Consumers must use AFTER_COMMIT; this is not a durable outbox. Event unit test RED before wiring, then GREEN. Discovery 94/94, app transaction integration 9/9, exception handler 6/6 PASS, no skips. PostgreSQL 17 integration uses all 9 real Flyway migrations and Hibernate validation; confirms commit, outer-transaction listener delay, rollback, deferred FK failure and real confirmed/revoked/pending/declined/missing link states through facade and controller entry. Controller invocation tests service wiring, not HTTP/security filters. Test fixtures explicitly flush before JDBC assertions inside transactions; production flush behavior unchanged.

- T09: matching calls bounded Profile API with subject/mode/area only; soft level/price/time/rating remain scoring factors. Service validates non-null goalId and topN 1..10 before reads; REST default stays 5. Nine new cases RED then GREEN; Discovery 91/91 PASS without skips. Tests capture exact criteria, prohibit old full-scan calls, cover empty/log/no-AI and deterministic top 1/5/10. Database cap remains Profile responsibility, proven in T05 PostgreSQL tests; global ranking/cap performance report remains T12. Old API retained until T14.

- T08: authorized STUDENT ownership or PARENT via ConnectionFacade confirmed-link lookup using studentId, not studentUserId. Parent role alone or matching userId cannot bypass link checks; Student cannot borrow a link. Null actor, foreign/missing/invalid goals stop before AI/log. RED parent test reproduced ForbiddenOperationException before implementation; GREEN Discovery 82/82 and Connection 13/13, no skips. Link-state unit cases verify the facade queries only CONFIRMED (repository mocked, not PostgreSQL status evidence). Transaction/facade integration remains T10. Dependency is public Connection API only, no reverse Discovery dependency.

- Phase 4 prerequisite: integrated Connection commit `43e7461` from `origin/feat/connection` without overwriting Profile bounded candidates/catalog wiring. Discovery -> Connection -> Profile/IAM has no reverse dependency. Kept unrelated AccountStatus change unstaged. Updated Flyway integration assertions for V1.09. Regression PASS: Connection 8, Profile 44 (6 PostgreSQL cases), Discovery 73, app handler/script 8; actual PostgreSQL 17 Flyway integration 1/1 PASS with all 9 migrations. Prerequisite is a separate commit before T08.

- T07: triển khai MatchWeights 30/20/20/15/15 và MatchScore đủ subject/level/price/time/rating; giữ tên field HTTP cũ, thêm subjectFit/levelFit. Scorer chỉ hard-filter subject/mode/area; level dùng catalog business order, giá giảm qua mốc 130%/150%, time dùng union/intersection duration theo ngày, rating có confidence và mặc định 35. Tổng tính từ điểm chưa round; phép chia scale 12, output scale 2 HALF_UP. overlappingSlots giữ nghĩa số slot yêu cầu ban đầu có giao để tương thích, tie-break chuyển sang scheduleFit rồi reviews/registeredAt/tutorId. Matching tái sử dụng DiscoveryQueryService.getEducationLevelOrder một lần mỗi run; chưa đổi full-scan caller (T09). Prompt/fallback có đủ 5 điểm; docs policy được đồng bộ, toàn bộ diagram vẫn thuộc T15.
- T07 verification: 19 cases mới chạy RED trên công thức cũ (9 assertion failures, 1 error do tutor bị lọc), sau đó GREEN. Regression tới app PASS: Discovery 73/73 (40 scorer, 4 weights, ranking, catalog, application/HTTP/explanation), Profile 44/44 gồm 6 PostgreSQL Testcontainers cases, GlobalExceptionHandler 6/6; không skip. Sau chỉnh wiring tái sử dụng query helper, chạy lại Discovery 73/73 và GlobalExceptionHandler 6/6 tới app PASS. `git diff --check` PASS; rà soát domain không import Spring/JPA/Hibernate/Profile. Không coi test MockMvc standalone là kiểm chứng security filters, không tuyên bố p95 hoặc transaction event (T10/T12/T13 còn lại).
- Checkpoint 3 PASS: policy Java thuần đủ 5 yếu tố; các biên cấp học, 100/130/150% giá, overlapping/touching/empty slots, rating confidence, zero budget, ranking ổn định và JSON breakdown đã kiểm chứng. Phase 4 cần Connection confirmed-link API trước T08 như prerequisite đã ghi.

- T06: chuyển MatchScorer và unit test sang domain/policy, cập nhật import application; không đổi thuật toán. Clean reactor build và Discovery 29/29 tests PASS, gồm 5 scorer cases giữ nguyên hành vi; không skip. Domain vẫn thuần Java, không Spring/JPA.
- T05: thêm TutorCandidateCriteria và Profile API bounded candidates; lọc VERIFIED/subject/mode/area trong DB bằng EXISTS, không duplicate tutor khi dạy nhiều cấp. Cap mặc định 200 qua DISCOVERY_MATCHING_CANDIDATE_LIMIT, phải dương; pre-order rating/reviews/createdAt/tutorId. BOTH nhận tutor dạy online ở mọi khu vực hoặc tutor offline cùng khu vực. Batch subject/name/level projection và availability giữ 3 query khi có kết quả, 1 query khi rỗng. PostgreSQL 17 Testcontainers chạy thật: 6/6 integration cases PASS, gồm 205 tutor/200 kết quả và kiểm tra SQL cap, dữ liệu mềm không bị loại, ordering lặp lại ổn định. Tổng Profile 44/44 và Discovery 29/29 tests PASS, không skip. Test dùng Hibernate create-drop cho schema cô lập, chưa thay thế kiểm chứng Flyway/p95/ranking-cap tại T12. API full scan và caller hiện tại giữ nguyên đến T09/T14.
- Checkpoint 2 PASS: metadata nghiệp vụ và candidate read DTO đủ dữ liệu cho scorer; query bounded/batch hydration được kiểm chứng trên PostgreSQL. Không tuyên bố đạt NFR hiệu năng hoặc global Top N từ các test này.
- T05 build verification: `mvnw.cmd -pl app -am test-compile -DskipTests -B -ntp` PASS toàn bộ 17 reactor projects; bước này kiểm tra compilation tới app, không phải full app test suite. `git diff --check` PASS.

- T04: Profile API trả catalog levelId/sortOrder theo business order; giữ cả cấp đã ngừng hoạt động để không tạo quan hệ liền kề mới. sort_order thiếu/trùng bị từ chối rõ ràng. Discovery map sang EducationLevelOrder thuần Java; ID không liên tục và khoảng cách sort_order không ảnh hưởng adjacency theo vị trí. Không đổi HTTP DTO hay scorer. Profile 30/30 và Discovery 29/29 tests PASS; diff check PASS.

- Baseline ngày 2026-10-10: `mvnw.cmd -pl modules/discovery -am test -B -ntp` PASS; Discovery 12 tests, dependencies 91 tests.
- Runtime kiểm tra: JDK 21.0.10. Maven dùng `-Djavax.net.ssl.trustStoreType=Windows-ROOT` để xác thực HTTPS bằng kho chứng chỉ Windows; không thay cấu hình repo hoặc tắt xác thực TLS.
- Connection trên base hiện vẫn là skeleton; confirmed-link API cần được tích hợp trước T08. Phase 1 không phụ thuộc implementation đó.
- T01: move/rename entity + repository, cập nhật caller/test; mapping đối chiếu V1.08 không đổi. Discovery 12/12 tests PASS, reactor compile PASS. Test dùng Mockito cần chạy ngoài sandbox để attach Java agent.
- T02: thêm DiscoveryFacade(CurrentUser, goalId, topN), TutorMatchSummaryDto và MatchingExecutedEvent. Reactor compile PASS; public API chỉ phụ thuộc Java, discovery.api và shared. Chưa có facade implementation/publisher cho tới T10.
- T03: QueryService nhận TutorSearchCriteria/trả Profile API read DTO; MatchingService nhận actor/goalId/topN và trả TutorMatchResult. HTTP mapping nằm tại controller/response; application không import discovery.web. Search fallback và pagination metadata được giữ.
- Checkpoint 1 PASS: clean reactor build tới app, Discovery 20/20 tests (gồm 6 HTTP regression cases), GlobalExceptionHandler 6/6 tests. Tests HTTP dùng standalone MockMvc với services thật và dependency mock; chưa kiểm chứng security filters/DB (ngoài phạm vi Phase 1).
