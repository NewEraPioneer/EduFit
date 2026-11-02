# ADR-002: Multi-Module Monolith (Backend) & Feature-based Architecture (Frontend) cho EduFit

- **Status:** Proposed
- **Related:** ADR-001 (Tech Stack), SRS EduFit, Section3_UseCaseModel_v2
- **Date:** 2026-09-25
- **Deciders:** Team (4 members)

## Context

ADR-001 đã chốt Spring Boot + PostgreSQL + JPA cho backend và Next.js (App Router)
cho frontend, nhưng chưa quyết định **cách tổ chức code bên trong** hai dự án đó.
ADR này chốt phần đó, dựa trên quan sát: 6 nhóm use case trong Use Case Model
(`Section3_UseCaseModel_v2.docx`) đã được nhóm theo tiêu chí *high cohesion, low
coupling*, tức là ranh giới nghiệp vụ đã tồn tại sẵn trong tài liệu — vấn đề chỉ
là ánh xạ đúng ranh giới đó vào code trước khi 4 người bắt đầu code song song ở
Sprint 1, để tránh xung đột merge và phụ thuộc chéo không kiểm soát được.

Ràng buộc: team 4 người, 7 tuần, ưu tiên hiểu bản chất hơn tốc độ thô (đã nêu ở
ADR-001), và **không** được chọn kiến trúc nặng hơn năng lực vận hành thực tế
(microservices, message broker) chỉ vì nghe "chuẩn công nghiệp".

---

## Decision 1: Backend — Multi-Module Monolith (Maven multi-module, một artifact chạy)

**Quyết định:** Một Maven reactor gồm nhiều module con, nhưng chỉ **một** module
(`app`) có `@SpringBootApplication`/`main()` và chạy như một tiến trình JVM duy
nhất. Các module nghiệp vụ còn lại là JAR thư viện thường (`packaging=jar`,
không có `main`), được `app` phụ thuộc và nạp chung vào một `ApplicationContext`.

Đây **không phải** microservices: không network call giữa các module, không
service discovery, không distributed transaction. Ranh giới giữa các module là
ranh giới **biên dịch** (Maven `pom.xml` dependency), được trình biên dịch cưỡng
chế, không dựa vào kỷ luật con người.

### Cây thư mục Backend

```
backend/
├── pom.xml                              # parent POM: packaging=pom, dependencyManagement, plugin versions
│
├── app/                                 # MODULE DUY NHẤT có main() — "root" của monolith
│   ├── pom.xml                          # phụ thuộc TẤT CẢ module platform/ + modules/
│   └── src/
│       ├── main/java/vn/edufit/
│       │   ├── EduFitApplication.java
│       │   └── config/
│       │       ├── SecurityConfig.java       # session cookie httpOnly, CSRF (ADR-001 Decision 5)
│       │       ├── WebConfig.java            # CORS dev, exception handler chung
│       │       ├── OpenApiConfig.java        # springdoc, nguồn sinh type cho frontend
│       │       └── SchedulingConfig.java     # bật @EnableScheduling cho các job FR-13/17/20/31
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── application-local.yml
│       └── test/java/vn/edufit/
│           └── ArchitectureTest.java         # ArchUnit — xem mục "Lưu ý #1"
│
├── platform/                            # module kỹ thuật, KHÔNG có use case riêng
│   ├── shared/                          # shared kernel: CurrentUser, Clock, Money, TimeInterval, lỗi chung
│   ├── ai/                              # cổng gọi LLM (timeout, log, giới hạn — FR-05, NFR-10)
│   ├── storage/                         # CredentialStorageService (interface) — NFR-08
│   └── audit/                           # ghi audit log — FR-02, FR-15
│
└── modules/                             # module nghiệp vụ, ánh xạ 1-1 nhóm use case trong SRS
    ├── iam/                             # UC1.1–1.4 (Group 1a)
    ├── profile/                         # UC1.5–1.9 (Group 1b)
    ├── verification/                    # UC1.10–1.12 (Group 1c)
    ├── discovery/                       # UC2.1–2.5 (Group 2) — không có bảng riêng, chỉ tổng hợp
    ├── connection/                      # UC3.1–3.5 (Group 3)
    ├── scheduling/                      # UC4.1–4.8 (Group 4)
    ├── progress/                        # UC5.1–5.5 (Group 5)
    ├── review/                          # UC6.1–6.2 (Group 6)
    └── notification/                    # UC6.3 (Group 6)
```

Cấu trúc bên trong **mỗi** module nghiệp vụ (ví dụ `scheduling`, module rủi ro
kỹ thuật cao nhất — sprint tuần 4):

```
modules/scheduling/
├── pom.xml                              # phụ thuộc: platform/shared, modules/connection (chỉ ../api), modules/profile (chỉ ../api)
└── src/
    ├── main/java/vn/edufit/scheduling/
    │   ├── api/                         # === BỀ MẶT CÔNG KHAI — module khác CHỈ được import từ đây ===
    │   │   ├── SessionFacade.java           # interface: proposeSession(), respondToProposal()...
    │   │   ├── dto/SessionStatsView.java     # DTO cho discovery/progress đọc, KHÔNG phải entity thật
    │   │   └── event/
    │   │       ├── SessionProposed.java      # domain event — notification lắng nghe
    │   │       ├── SessionScheduled.java
    │   │       ├── SessionCancelled.java
    │   │       ├── SessionOutcomeRecorded.java
    │   │       └── RecurringScheduleUpdated.java
    │   │
    │   ├── web/                         # controller + request/response DTO (tầng HTTP, riêng của module)
    │   │   └── SessionController.java
    │   │
    │   ├── application/                 # use case, ranh giới @Transactional — 1 class = 1 hành động người dùng
    │   │   ├── ProposeSessionService.java        # UC4.1: Đề xuất 1 buổi học
    │   │   ├── ProposeBatchSessionsService.java  # Đề xuất hàng loạt buổi học (Batch Propose)
    │   │   ├── RespondToProposalService.java     # UC4.3: Xác nhận 1 buổi học
    │   │   ├── RespondBatchSessionsService.java  # Xác nhận hàng loạt (Batch Respond)
    │   │   ├── RescheduleSessionService.java     # UC4.4: Đổi 1 buổi học lẻ
    │   │   ├── CancelSessionService.java         # UC4.5: Hủy 1 buổi học lẻ
    │   │   ├── RecordSessionOutcomeService.java  # UC4.6: Ghi nhận kết quả buổi học
    │   │   ├── GetSessionDetailsService.java     # UC4.7: Xem chi tiết buổi học (bảo mật quyền)
    │   │   ├── GetCalendarService.java           # UC4.7: Tra cứu lịch học theo ngày
    │   │   ├── ProposeRecurringScheduleService.java # UC4.8: Đề xuất đổi lịch định kỳ lớp (Bounded Horizon)
    │   │   ├── RespondRecurringScheduleService.java # UC4.8: Đồng thuận đổi lịch định kỳ (tái sinh buổi học)
    │   │   └── security/
    │   │       └── SchedulingSecurityResolver.java  # Ánh xạ user_id <-> profile_id & kiểm tra parent_link
    │   │
    │   ├── domain/                      # Java THUẦN — không Spring, không JPA — nơi giá trị thật nằm ở đây
    │   │   ├── Session.java                  # aggregate root, state machine BR-39–BR-48
    │   │   ├── SessionStatus.java             # enum + bảng chuyển trạng thái hợp lệ
    │   │   ├── ConflictPolicy.java            # BR-42: overlap [start, end) — 4 case biên trong SRS
    │   │   └── ProposalWindow.java            # BR-41, BR-43: luật thời gian đề xuất
    │   │
    │   └── infra/                       # adapter kỹ thuật
    │       ├── SessionRepository.java         # Spring Data JPA
    │       ├── SessionJpaEntity.java
    │       └── job/
    │           ├── ExpireProposalsJob.java    # FR-17, @Scheduled
    │           └── OverdueOutcomeReminderJob.java  # FR-20
    │
    ├── main/resources/db/migration/scheduling/
    │   └── V2026.09.25.1__create_session_tables.sql   # Flyway riêng của module, version theo timestamp
    │
    └── test/java/vn/edufit/scheduling/
        ├── domain/ConflictPolicyTest.java     # unit test thuần, không cần Spring context — chạy trong ms
        └── application/ProposeSessionServiceIT.java  # Testcontainers PostgreSQL
```

### Đồ thị phụ thuộc giữa các module

```
app ──────────► phụ thuộc mọi module (platform + modules)

notification ◄── (không ai phụ thuộc notification; nó chỉ NGHE event, không có api để module khác gọi)

discovery ────► profile.api, review.api, scheduling.api        (chỉ đọc, tổng hợp cho Search/Matching)
progress ─────► scheduling.api, connection.api
review ───────► scheduling.api, connection.api
scheduling ───► connection.api, profile.api
connection ───► profile.api, iam.api
verification ─► profile.api, storage, audit
profile ──────► shared
iam ──────────► shared
```

**Luật cứng:**
1. Module chỉ được import package `..api..` của module khác — không đụng
   `application`, `domain`, `infra` của module khác dù Maven cho phép về mặt kỹ
   thuật (xem mục "Lưu ý #1" — Maven không tự chặn việc này).
2. Không được có vòng phụ thuộc (Maven tự chặn ở mức module — build fail nếu
   A phụ thuộc B mà B lại phụ thuộc A).
3. Không dùng `@ManyToOne`/`@OneToMany` của JPA xuyên module. Xuyên module chỉ
   giữ ID và gọi `api` khi cần dữ liệu — tránh biến "nhiều module" thành "một
   entity graph dính chùm".

---

## Decision 2: Giao tiếp giữa module — đồng bộ qua `api`, bất đồng bộ qua Domain Event (EDA nội bộ)

**Quyết định:** Dùng **hai** cơ chế giao tiếp tuỳ loại quan hệ, không dùng
message broker (Kafka/RabbitMQ):

| Loại quan hệ | Cơ chế | Ví dụ |
|---|---|---|
| Module A **cần dữ liệu** từ module B để hoàn thành nghiệp vụ của chính A | Gọi thẳng `B.api.XyzFacade` (đồng bộ, trong cùng transaction nếu cần) | `discovery` gọi `scheduling.api.SessionStatsQuery` để lấy số buổi hoàn thành hiển thị ở UC2.5 |
| Module A **không cần** biết ai xử lý tiếp, chỉ cần báo "việc X vừa xảy ra" | Phát `ApplicationEventPublisher` + module khác `@EventListener` (đồng bộ, cùng transaction, trong-process — không phải EDA qua broker) | `scheduling` phát `SessionProposed`; `notification` nghe và ghi thông báo (FR-16) |

**Vấn đề cụ thể được giải quyết** (bám sát yêu cầu: pattern nào cũng sinh ra để
giải một vấn đề — dưới đây liệt kê thẳng, không giải thích lan man):

- **Vấn đề:** gần như mọi use case (UC1.10/12, UC3.1–3.5, UC4.1–4.6, UC5.1,
  UC6.1) đều có postcondition "ghi thông báo cho bên kia" (FR-01, FR-12, FR-16,
  FR-25, FR-28). Nếu để mỗi module tự `import notification.NotificationService`
  và gọi thẳng, `notification` trở thành module bị phụ thuộc bởi **tất cả**
  module khác — vi phạm nguyên tắc low-coupling đã chọn khi chia nhóm use case.
- **Giải quyết:** module phát sự kiện, không biết ai nghe (`scheduling` không
  cần `import notification`). `notification` là module duy nhất *chỉ nghe*,
  không ai gọi vào nó qua `api`.
- **Trade-off chấp nhận:** dùng `@EventListener` **đồng bộ** (mặc định của
  Spring, không phải `@Async`) để việc ghi log nghiệp vụ và việc ghi thông báo
  nằm trong cùng transaction — nếu ghi thông báo lỗi, cả hành động gốc rollback
  theo đúng NFR-11 (atomic saves). Cái giá phải trả: nếu `notification` lỗi,
  hành động gốc (ví dụ đề xuất buổi học) cũng thất bại theo — chấp nhận được vì
  logic trong listener của `notification` cố tình giữ tối giản (chỉ insert một
  dòng), rủi ro lỗi thấp.
- **Vì sao không dùng Kafka/RabbitMQ:** một DB, một JVM duy nhất — không có bài
  toán "ghi 2 hệ thống khác nhau cần nhất quán" (dual-write) mà message broker
  được sinh ra để giải. Dùng broker ở đây là cõng thêm hạ tầng (ZooKeeper/Kraft,
  consumer group, retry, dead-letter) để giải quyết một vấn đề đội không có,
  không phù hợp năng lực 4 người/7 tuần.

**Ví dụ áp dụng cụ thể — luồng `iam` ↔ `verification`:**
`verification` cần biết tài khoản Tutor có tồn tại và `profile` đã hoàn chỉnh
(PRE-2 của UC1.10) — đây là **đọc dữ liệu**, nên `verification` gọi thẳng
`profile.api.ProfileQuery`, **không** dùng event. Ngược lại, khi Admin duyệt
verification xong (UC1.12, POST-1: "Tutor profile marked verified"), đây là
**thông báo một việc đã xảy ra** để module khác phản ứng (badge hiển thị ở
`discovery`, thông báo cho Tutor ở `notification`) — nên `verification` phát
event `TutorVerified`, không gọi thẳng `discovery` hay `notification`. Điểm mấu
chốt: **đọc dữ liệu → gọi `api`; báo trạng thái đã đổi → phát event.**

---

## Decision 3a: Domain layer thuần (Java, không Spring) — CHỈ áp dụng cho 3 module, không phải toàn bộ

Bản nháp trước ghi domain layer như một quy tắc chung cho mọi module, gây hiểu
lầm là toàn bộ 9 module đều phải tách domain thuần theo kiểu hexagonal đầy đủ.
**Đính chính: không phải vậy.** Quyết định chính xác, áp dụng ngay, không để
tuỳ nghi từng người:

| Module | Có package `domain/` thuần Java? | Lý do |
|---|:---:|---|
| `scheduling` | **CÓ** | `ConflictPolicy` (BR-42, 4 case biên), state machine `Session` (BR-39–BR-48), `ProposalWindow` (BR-41, BR-43). Sai một trong số này gây double-booking — lỗi dữ liệu im lặng, khó phát hiện bằng mắt khi demo. |
| `progress` | **CÓ** | Công thức tiến độ BR-51 (trọng số milestone) và các luật chuyển trạng thái milestone (BR-50). Sai công thức là sai số hiển thị cho phụ huynh — đúng trọng tâm "minh bạch hoá" của sản phẩm, không được sai. |
| `discovery` | **CÓ** | Hàm chấm điểm và xếp hạng BR-20/21/22/23. Cần tái lập được kết quả (NFR-14) — chỉ verify được bằng test thuần chạy nhanh, không phải qua tầng HTTP. |
| `iam`, `profile`, `verification`, `connection`, `review`, `notification` | **KHÔNG** | Logic ở đây là kiểm tra điều kiện + CRUD tuyến tính (một điều kiện đúng/sai, không có công thức hay state machine nhiều nhánh). Viết thẳng trong `@Service` dùng JPA entity, không tách `domain/` riêng. Tách ra đây chỉ sinh thêm lớp mapping Entity ↔ Domain Object mà không tăng độ tin cậy tương ứng. |

**Tiêu chí để không cần tự hỏi lại mỗi lần thêm module:** một module cần
`domain/` thuần khi và chỉ khi logic của nó có **state machine với rủi ro
concurrent-modification (NFR-15/17)** hoặc **công thức tính toán nhiều bước**.
Nếu chỉ là "kiểm tra A đúng thì cho làm B" tuyến tính, không cần.

**Trade-off đã cân nhắc và chốt, không để mở:** Spring Boot cho tốc độ phát
triển nhanh hơn khi viết trực tiếp `@Service` + JPA — đúng, và đó là lý do 6/9
module trong bảng trên **không** tách domain thuần, để tận dụng đúng tốc độ đó.
Nhưng với 3 module còn lại, chi phí thật sự không phải "chậm hơn khi viết", mà
là **tốc độ chạy test khi sửa business rule**: test một hàm Java thuần (mili-
giây, không cần Spring context) so với test qua `@Service` có `@Autowired`
(cần context hoặc mock, chậm hơn và phải setup dữ liệu). Với 3 module này, quy
tắc sai sẽ gây lỗi dữ liệu im lặng chứ không phải lỗi hiển thị rõ ràng — đáng
để trả thêm vài class. Đây không phải lựa chọn "vì dự án lớn cần độc lập
framework 10 năm" — dự án này chỉ chạy 7 tuần rồi kết thúc, lý do là tốc độ
kiểm chứng đúng-sai của 3 bộ luật nghiệp vụ rủi ro cao nhất, không hơn.

**Phương án rút gọn nếu team vẫn thấy nặng:** không bắt buộc tách thư mục
`domain/` vật lý riêng. Được phép viết `ConflictPolicy`/`ProgressCalculator`/
`MatchScorer` như `static` method thuần trong cùng file `@Service`, miễn là
method đó không có tham số kiểu Spring (`Repository`, `EntityManager`...) để
vẫn gọi và unit-test độc lập được. Mất lợi ích tách thư mục, giữ được lợi ích
cốt lõi là tốc độ test — đây là mức tối thiểu chấp nhận được, không thấp hơn.

---

## Decision 3b: Flyway cho schema phân tán theo module

**Đính chính:** bản trước đặt file migration rải theo từng module
(`modules/iam/.../db/migration`, `modules/scheduling/.../db/migration`...).
Đó là **sai ý quyết định** — schema tuy được *sở hữu về mặt logic* bởi từng
module (module nào định nghĩa bảng của mình), nhưng **nơi lưu file migration
phải tập trung một chỗ**, không phân tán theo module. Lý do đổi lại:

- Chỉ có một module (`app`) chạy Flyway và chỉ có một `flyway_schema_history`
  toàn cục — vậy về bản chất **đã không có "nhiều pipeline migrate độc lập"**
  để tách theo module ngay từ đầu; phân tán file theo module chỉ tạo ảo giác
  độc lập trong khi cơ chế áp dụng vẫn là một dòng thời gian duy nhất.
- Phân tán làm một người muốn biết "version mới nhất toàn hệ thống là bao
  nhiêu" phải mở 9 thư mục ở 9 module khác nhau mới ráp lại được — sai lệch
  đúng thứ tự (Luật ở mục 3 dưới) dễ xảy ra hơn khi thông tin không nằm cùng
  một chỗ để nhìn thấy ngay.
- PR đổi schema sẽ dễ review hơn khi cả migration mới nằm trong đúng một thư
  mục, thay vì rải rác lẫn với code nghiệp vụ của module.

**Quyết định (đã sửa — tập trung):**

1. **Một database, một schema `public`, một `flyway_schema_history` dùng
   chung** — giữ nguyên như bản trước, không tách schema riêng theo module.

2. **Toàn bộ file migration nằm tập trung tại một nơi duy nhất**, trong chính
   module `app` — nơi Flyway thực sự chạy — chia theo thư mục con mang tên
   module chỉ để dễ đọc, **không phải để phân quyền vật lý**:
   ```
   app/src/main/resources/db/migration/
   ├── iam/
   │   └── V2026.09.29.01__create_account_table.sql
   ├── profile/
   │   ├── V2026.09.30.01__create_tutor_profile_table.sql
   │   └── V2026.09.30.02__create_student_profile_table.sql
   ├── verification/
   ├── connection/
   ├── scheduling/
   │   └── V2026.10.13.01__create_session_table.sql
   ├── progress/
   ├── review/
   ├── notification/
   ├── ai/                     # FR-05: bảng ai_request_log — xem Decision 3c
   └── audit/                  # FR-02, FR-15: bảng audit_log — xem Decision 3c
   ```
   Cấu hình chỉ cần **một** location, Flyway tự quét đệ quy hết các thư mục
   con bên trong:
   ```yaml
   spring:
     flyway:
       locations: classpath:db/migration
       out-of-order: false        # ép thứ tự nghiêm ngặt — bắt lỗi version đặt sai ngay khi migrate
       validate-on-migrate: true  # migration đã áp dụng bị sửa checksum → build fail ngay, không âm thầm lệch
       baseline-on-migrate: false
   ```
   "Sở hữu" ở đây chỉ còn là quy ước tổ chức thư mục + `CODEOWNERS`
   (`db/migration/scheduling/** @nguoi-phu-trach-scheduling`), không phải ranh
   giới Maven — khác với code Java, nơi ranh giới module vẫn là compile-time
   như Decision 1.

3. **Quy tắc đặt version vẫn là điểm mấu chốt giải quyết thứ tự phụ thuộc**,
   và giờ *dễ tuân thủ hơn* vì tất cả nằm một chỗ. Version dùng định dạng
   `V{yyyy}.{MM}.{dd}.{seq}`:
   - `{yyyy}.{MM}.{dd}`: ngày viết migration.
   - `{seq}`: số thứ tự trong ngày đó của **chính người viết** (2 chữ số), để
     hai người cùng viết migration trong một ngày không trùng version.
   - **Luật bắt buộc:** nếu migration tạo cột tham chiếu (FK) sang bảng của
     module khác, version phải **muộn hơn** version tạo bảng đó. Trước khi
     viết migration mới, mở thẳng `app/src/main/resources/db/migration/` (một
     thư mục, thấy hết toàn bộ) để biết version mới nhất, tránh chèn version
     thấp hơn version đã chạy (bị chặn bởi `out-of-order: false`).

4. **FK xuyên module: dùng FK constraint thật ở tầng DB**, không chỉ lưu ID
   trần rồi tự kiểm tra ở tầng ứng dụng — hệ quả trực tiếp của việc chọn
   Postgres một DB duy nhất (ADR-001). Đây **không** mâu thuẫn với luật "module
   Java chỉ gọi `api`, không đụng bảng module khác": ranh giới `api`/`domain`
   là ở tầng ứng dụng (ai được gọi hàm của ai), FK là ràng buộc ở tầng dữ liệu
   (không cho `session` trỏ tới `tutor_id` không tồn tại). Hai ranh giới độc
   lập nhau, không cần khớp nhau, và việc migration nay tập trung vật lý không
   làm thay đổi luật này ở tầng code.

5. **Không có migration hạ cấp (down-migration) tự động** — Flyway Community
   không hỗ trợ. Sửa sai bằng migration mới (forward-fix), không sửa lại file
   đã áp dụng.

6. **CI bắt buộc chạy `flyway migrate` trên Testcontainers Postgres sạch mỗi
   PR** — bắt lỗi thứ tự version sớm, trước khi merge.

**Trade-off chấp nhận (đã đổi so với bản phân tán):** tập trung file vào `app`
nghĩa là `app` — vốn đã phụ thuộc mọi module ở tầng code (Decision 1) — giờ
cũng là nơi duy nhất "biết" toàn bộ schema. Đây không phải phụ thuộc mới, chỉ
là làm tường minh một phụ thuộc vốn đã tồn tại. Đổi lại, một migration sai ở
bất kỳ module nào vẫn chặn toàn bộ khởi động (giống bản phân tán, không đổi),
nhưng giờ dễ phát hiện *trước khi* nó xảy ra hơn, vì toàn bộ dòng thời gian
schema nằm trong tầm mắt ở một thư mục thay vì phải ráp lại từ 9 nơi.

---

## Decision 3c: Module `platform/*` — vai trò, domain purity, sở hữu dữ liệu

Bốn module trong `platform/` không có use case riêng nên dễ bị bỏ sót khi áp
hai quyết định trên (3a, 3b). Chốt rõ từng module, không để ngầm hiểu:

| Module | Có `domain/` thuần? | Sở hữu bảng DB? | Vai trò chính xác |
|---|:---:|:---:|---|
| `shared` | Không áp dụng — **toàn bộ module này vốn đã là Java thuần** | Không | Kiểu dữ liệu dùng chung: `TimeInterval`, `Money`, model lỗi. **Không import bất kỳ package `org.springframework.*` nào** — đây là điều kiện bắt buộc để `scheduling`/`progress`/`discovery` import được `shared` vào `domain/` của chúng mà không phá luật "domain không phụ thuộc Spring" ở Decision 3a. `CurrentUser` chỉ là **interface** ở đây; implementation thật (đọc từ `SecurityContext`) đặt ở `app/config`, vì nó cần Spring Security. |
| `ai` | Không | **Có** — `ai_request_log` (FR-05) | Cổng gọi LLM: timeout 15s (NFR-10), giới hạn số lần gọi, ghi log không lưu nội dung. Chỉ là adapter gọi HTTP ra ngoài + ghi một dòng log, không có business rule nhiều nhánh → không cần tách domain, viết thẳng `@Service`. |
| `storage` | Không | Không (chỉ lưu file, metadata file thuộc về `verification.CredentialDocument`) | Interface `CredentialStorageService` (`store/retrieve/delete`) — Ports & Adapters thuần tuý. Implementation local disk (dev) và Supabase (deploy) theo ADR-001 Decision 6. |
| `audit` | Không | **Có** — `audit_log` (FR-02, FR-15) | Ghi log quyết định Admin (approve/reject verification, revoke link). Insert-only, không có luật phức tạp → không cần domain riêng. |

Vì `ai` và `audit` sở hữu bảng thật, migration của chúng vẫn nằm trong cùng
thư mục tập trung `app/src/main/resources/db/migration/{ai,audit}/` như mọi
module nghiệp vụ khác (Decision 3b) — `platform/` không phải ngoại lệ ở việc
migration tập trung, chỉ là ngoại lệ ở việc **không có use case riêng** để lên
SRS.

Đồ thị phụ thuộc (bổ sung cho đồ thị ở Decision 1): mọi module nghiệp vụ được
phép phụ thuộc `platform/shared`; `verification` phụ thuộc thêm `platform/
storage` và `platform/audit`; module nào gọi AI (`profile` cho UC1.7,
`discovery` cho UC2.4) phụ thuộc thêm `platform/ai`. Không module nghiệp vụ
nào cần phụ thuộc lẫn hai module `platform/ai` hay `platform/audit` gọi ngược
lại — chúng chỉ được gọi tới, không tự gọi ra module nghiệp vụ nào.

---

## Decision 4: Frontend — Package-by-Feature kết hợp Route Groups, ưu tiên Server Component

**Quyết định:** `app/` chỉ là lớp định tuyến mỏng (routing + layout ghép), toàn
bộ logic nằm ở `features/`, đặt tên **trùng tên module backend** để một người
phụ trách module `scheduling` thấy cùng một tên ở cả hai phía khi làm việc.

### Cây thư mục Frontend

```
frontend/src/
├── app/                                  # CHỈ định tuyến + ghép component, không chứa logic nghiệp vụ
│   ├── layout.tsx                        # root layout, font, theme
│   │
│   ├── (public)/                         # route group — KHÔNG thêm tiền tố URL, dùng để chia layout
│   │   ├── layout.tsx                    # header/footer công khai
│   │   ├── page.tsx                      # Landing
│   │   └── tutors/[id]/page.tsx          # Public Tutor Search & Detail (UC2.1, UC2.5 — xem Lưu ý #7)
│   │
│   ├── (auth)/                           # route group — layout riêng, không có header/sidebar
│   │   ├── layout.tsx
│   │   ├── login/  register/  verify-email/  forgot-password/
│   │
│   ├── (learner)/                        # route group DÙNG CHUNG Student + Parent (self-scope vs link-scope)
│   │   ├── layout.tsx                    # guard role STUDENT|PARENT; Parent chọn student trong layout này
│   │   ├── find-tutors/page.tsx          # UC2.1, UC2.2
│   │   ├── matching/page.tsx             # UC2.3, UC2.4
│   │   ├── requests/page.tsx             # UC3.4 (gửi/huỷ), theo dõi trạng thái
│   │   ├── calendar/page.tsx             # UC4.1, 4.3, 4.4, 4.5 (giao diện chung, phân nhánh theo role)
│   │   └── progress/page.tsx             # UC5.3
│   │
│   ├── student/                          # THƯ MỤC THẬT, không phải route group — xem Lưu ý #2
│   │   ├── layout.tsx                    # guard role STUDENT
│   │   ├── profile/page.tsx              # UC1.8, UC1.9
│   │   └── link-parent/page.tsx          # UC3.1, UC3.2 (phía Student)
│   │
│   ├── parent/
│   │   ├── layout.tsx                    # guard role PARENT
│   │   ├── children/page.tsx             # UC5.4 Multi-Student Dashboard
│   │   └── link-student/page.tsx         # UC3.1, UC3.2 (phía Parent)
│   │
│   ├── tutor/
│   │   ├── layout.tsx                    # guard role TUTOR
│   │   ├── profile/page.tsx              # UC1.5, UC1.6, UC1.7
│   │   ├── verification/page.tsx         # UC1.10, UC1.11
│   │   ├── requests/page.tsx             # UC3.5
│   │   ├── students/[engagementId]/
│   │   │   ├── plan/page.tsx             # UC5.1
│   │   │   └── sessions/[sessionId]/page.tsx  # UC4.6 + UC5.2
│   │   └── reviews/page.tsx              # UC6.2
│   │
│   └── admin/
│       ├── layout.tsx                    # guard role ADMIN
│       ├── verification-queue/page.tsx   # UC1.12
│       └── links/page.tsx                # UC3.3 (Admin revoke)
│
├── features/                             # LOGIC THẬT — tên trùng module backend
│   ├── auth/  profile/  verification/  discovery/
│   ├── connection/  scheduling/  progress/  review/  notification/
│   │   └── (mỗi feature)
│   │       ├── api/
│   │       │   ├── server.ts             # fetch phía server — 'server-only', gắn cookie, gọi Spring trực tiếp
│   │       │   └── client.ts             # fetch phía client — qua rewrite /api/*, kèm CSRF token
│   │       ├── components/               # UI riêng của feature (không xuất ra ngoài trừ qua index.ts)
│   │       ├── hooks/                    # TanStack Query hooks (polling notification, v.v.)
│   │       ├── schemas/                  # zod — validate UI-side, PHẢN CHIẾU luật backend, không thay thế
│   │       ├── mappers/                  # DTO (generated) → view model UI dùng
│   │       └── index.ts                  # === BỀ MẶT CÔNG KHAI DUY NHẤT của feature ===
│   └── ...
│
└── shared/
    ├── ui/                                # design system: Button, Dialog, Table... (theo Figma tokens)
    ├── lib/                               # http client, session helper, format ngày giờ (NFR-18: Asia/Ho_Chi_Minh)
    └── api/generated/                     # sinh từ OpenAPI (springdoc) — KHÔNG sửa tay, CI kiểm tra đồng bộ
```

**Luật cứng:** feature không được import trực tiếp vào ruột của feature khác
(`features/scheduling/components/...` không được import bởi `features/progress`),
chỉ được import qua `index.ts`. Cưỡng chế bằng `eslint-plugin-boundaries` chạy
trong CI — tương đương ArchUnit ở backend.

### Vì sao chọn kiến trúc này (giải quyết trade-off gì)

- **Vấn đề "bất đối xứng dữ liệu" giữa hai bên:** Next.js chỉ giải quyết phần
  *vị trí gọi* (Server Component gọi Spring Boot ở phía server, gần backend,
  không lộ ra trình duyệt) và phần *cookie httpOnly được chuyển tiếp tự nhiên*.
  Nó **không** tự giải quyết việc type Java và TypeScript lệch nhau — phần đó
  giải bằng OpenAPI làm nguồn duy nhất (`shared/api/generated/`), không phải
  do bản thân Next.js.
- **Vì sao package-by-feature thay vì chia theo loại file (`components/`,
  `hooks/`, `pages/` gộp chung):** với 6+ nhóm nghiệp vụ và 4 người làm song
  song, chia theo loại file khiến mỗi thay đổi nghiệp vụ chạm vào nhiều thư mục
  cùng lúc → xung đột merge cao. Chia theo feature giữ toàn bộ một nghiệp vụ
  trong một thư mục, khớp cách backend đã chia, giảm chi phí "dịch" giữa hai
  phía khi review code.
- **Vì sao route group `(learner)` dùng chung Student/Parent:** SRS đã tự nêu
  rõ (mục 3.6 `edufit-context-diagram-analysis.md`) rằng Search/Matching/xem
  tiến độ là *Shared, khác nhau ở cách resolve scope* — không phải hai UI khác
  nhau, chỉ khác nguồn dữ liệu (self-scope vs link-scope). Tách route riêng
  cho hai role ở đây sẽ nhân đôi UI vô ích; gộp route, xử lý khác biệt bằng
  logic chọn scope trong `layout.tsx`.
- **Vì sao Server Component là mặc định:** đẩy phần đọc dữ liệu (không tương
  tác) lên server giảm bundle JS gửi về trình duyệt, và giữ toàn bộ logic đọc
  dữ liệu nhạy cảm (session cookie, gọi trực tiếp Spring nội bộ) không lộ ra
  client. Chỉ những phần thật sự cần state/tương tác phía trình duyệt (kéo thả
  lịch, popup AI, form, đếm thông báo polling) mới đánh dấu `"use client"`.
  Trade-off: phải tách nhỏ component hơn (một trang có thể có Server Component
  cha bọc Client Component con), tốn thêm một bước thiết kế so với "toàn bộ là
  Client Component" — chấp nhận được vì đổi lại hiệu năng và bảo mật tốt hơn
  đáng kể mà không cần thêm hạ tầng.

---

## Bổ sung — Lưu ý tối ưu / trade-off khi triển khai

1. **Maven không tự chặn việc module A import `application`/`domain` của module
   B** (dù A đã khai báo dependency đúng theo đồ thị) — nó chỉ chặn A import
   module A **chưa** khai báo dependency. Muốn ép "chỉ được đụng `api`", phải
   thêm **ArchUnit** test trong `app/src/test` chạy trong CI. Không có bước
   này, ranh giới `api` chỉ là quy ước, dễ bị phá vỡ dần qua các sprint.

2. **`(learner)` là route group, `student/`/`parent`/`tutor`/`admin` là thư mục
   thật** — đây không phải tuỳ tiện. Route group (`(...)`) không tạo segment
   URL, nên nếu `(student)/dashboard` và `(parent)/dashboard` cùng tồn tại,
   cả hai cùng map vào `/dashboard` → Next.js báo lỗi build do trùng route.
   Route group chỉ dùng khi các nhóm **không bao giờ** có URL trùng nhau
   (`(public)` và `(auth)` không có trang trùng tên).

3. **EDA nội bộ chỉ dùng `@EventListener` đồng bộ, không dùng `@Async`** trừ
   khi đo được nghẽn thật (ví dụ gửi email). `@Async` tách listener khỏi
   transaction gốc — lúc đó NFR-11 (atomic saves) không còn đúng cho phần việc
   trong listener, phải tự xử lý lại tính nhất quán (ví dụ Outbox Pattern) —
   một mức phức tạp không cần thiết cho quy mô đồ án.

4. **`ConflictPolicy` (BR-42) và các lớp trong `domain/` phải test bằng unit
   test thuần**, không cần Spring context hay Testcontainers — đây là lý do
   chính để tách domain khỏi framework. Testcontainers PostgreSQL chỉ dùng cho
   tầng `application`/`infra`, nơi cần hành vi thật của DB (row lock cho
   NFR-17, unique constraint cho NFR-24) — H2 không mô phỏng đúng các hành vi
   này.

5. **Job định kỳ (FR-13, 17, 20, 31) đặt trong `infra/job/` của module sở hữu
   dữ liệu đó**, không gom vào một module "scheduler" chung — tránh biến module
   đó thành nơi phụ thuộc bởi tất cả module khác (lặp lại đúng lỗi mà EDA ở
   Decision 2 đang tránh cho `notification`).

6. **Đồng bộ contract qua OpenAPI phải nằm trong CI, không phải thao tác tay:**
   springdoc sinh spec ở backend → `orval`/`openapi-typescript` sinh lại type ở
   frontend → CI fail nếu spec thay đổi mà code sinh chưa cập nhật. Thiếu bước
   này, "bất đối xứng dữ liệu" quay lại đúng như trước khi tách hai dự án.

7. **Điểm chưa khớp trong SRS cần chốt trước khi code `(public)/tutors`:**
   bảng Screen Authorization cho phép Guest dùng Public Tutor Search, nhưng
   UC2.1 lại ghi PRE-1 là "đã đăng nhập". Route `tutors/` ở trên tạm đặt trong
   `(public)` theo bảng Screen Authorization — team cần chốt lại cái nào đúng
   trước Sprint 1, vì nó quyết định route nằm ở `(public)` hay `(learner)`.

## Alternatives considered

- **Spring Modulith (ranh giới chỉ ở mức package, không phải Maven module):**
  triển khai nhanh hơn (không cần nhiều `pom.xml`), có công cụ kiểm tra ranh
  giới riêng (`ApplicationModules.verify()`). Bị loại vì ranh giới chỉ được
  cưỡng chế bằng annotation/test tự nguyện chạy, không chặn ở compile-time như
  Maven module — với đội 4 người mới học, cưỡng chế cứng ở compile-time an
  toàn hơn cho việc giữ kỷ luật kiến trúc suốt 7 tuần.
- **Chia mỗi module thành `xxx-api`/`xxx-impl` (tách cả API interface thành
  module riêng):** cưỡng chế còn chặt hơn, nhưng gấp đôi số `pom.xml` và
  overhead cho lợi ích tăng thêm ở quy mô 9 module — hoãn lại, chỉ làm nếu
  ArchUnit (Lưu ý #1) cho thấy ranh giới vẫn bị vi phạm nhiều dù đã cảnh báo.
- **Feature-Sliced Design (FSD) đầy đủ cho frontend** (thêm layer `entities`,
  `widgets`, `pages`, `processes`): mô hình chi tiết hơn, phù hợp SPA lớn
  không có App Router. Với Next.js App Router đã tự cung cấp layer routing/
  layout, áp FSD đầy đủ chồng lấn vai trò — chọn bản rút gọn (`app/` mỏng +
  `features/`) là đủ cho quy mô 9 feature.

## Consequences

- Sprint 0 cần dựng đủ khung `pom.xml` cho toàn bộ 9 module + `platform/`
  trước khi chia việc, và một lát cắt dọc hoàn chỉnh (UC1.1 Register) chạy
  xuyên suốt: migration → domain → application → api → OpenAPI → generated
  client → trang Next — để xác nhận toàn bộ chuỗi hoạt động trước khi nhân
  rộng ra các module khác.
- Thêm chi phí bảo trì: mỗi module có `pom.xml` riêng, mỗi feature frontend có
  `index.ts` riêng — nhiều boilerplate hơn một cấu trúc phẳng, đổi lại ranh
  giới rõ và ít xung đột merge giữa 4 người trong 7 tuần.
- ArchUnit test và `eslint-plugin-boundaries` phải được thêm ngay từ Sprint 0,
  không phải "làm sau" — nếu không, ranh giới module sẽ bị xói mòn dần và tới
  Sprint 4–5 (module rủi ro cao nhất) sẽ khó gỡ.
