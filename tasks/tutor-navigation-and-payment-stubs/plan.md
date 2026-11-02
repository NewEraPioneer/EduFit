# Thành viên 3 — Tutor navigation, Payment stubs và Tuition research

- Ngày audit: **18/10/2026**, Asia/Saigon.
- Trạng thái: **PLAN ĐỂ REVIEW — chưa triển khai chức năng**.
- Workspace: `C:/Users/ASUS/Desktop/EduFit/EduFit`.
- Nhánh: **`fix/tutor-navigation-and-payment-stubs`**, tạo trực tiếp từ main sau fetch/pull.
- Base: **`d904f79a7de5f1ac5235036eefb8eb6dd58fd6de`** = local main = origin/main tại thời điểm audit; PR20 sprint2-integrated-step1-step2.
- Checklist đi kèm: [todo.md](todo.md). Chỉ M3-00 (chuẩn bị/audit) hoàn tất.
- Nguồn phân công: `D:/edufit_work_breakdown_plan.md`, SHA256 `189DF90A7B893191DFCF62438FAAF05AE79AD2DA1DC588BD34DCA44AF0ADD4C4`.

## 1. Yêu cầu thực thi và cách hiểu tài liệu

Người dùng yêu cầu cập nhật main, tạo đúng nhánh trên, đọc docs/phần **thành viên 3**, lập plan **trước khi code**. Tài liệu phân công là nguồn yêu cầu nghiệp vụ, không tự cấp quyền triển khai các thành viên khác, merge develop, chạy thanh toán hoặc tạo PR. Sơ đồ trong tài liệu dùng develop; yêu cầu trực tiếp của người dùng chọn **main**, nên main là base của plan này. Các link `c:/University/...` trong tài liệu được đối chiếu với relative paths tại workspace hiện tại, không dùng làm đường dẫn để sửa file.

Đã fetch origin, `git pull --ff-only origin main` (Already up to date), tạo branch từ main sạch. Chỉ đọc origin/develop để kiểm chứng dependency; không merge/cherry-pick/copy code từ đó. Main/Connection/Discovery và các stash không bị chỉnh sửa để làm task này.

## 2. Thành viên 3 phải bàn giao những gì?

| Phân công | Đầu ra | Không bao gồm |
| --- | --- | --- |
| 3.1 Core research | Tài liệu **Tuition Policy & Dispute Specification**, quyết định/đề xuất/quy tắc, ví dụ và wireframe flow cho giai đoạn sau | Code thanh toán online, escrow, ví, giao dịch thật hoặc migration Payment |
| 3.2 Payment navigation | Ba URL tài chính có trạng thái sắp ra mắt rõ ràng, menu/CTA cùng thông điệp và không dẫn đến missing page | Bảng doanh thu giả, hóa đơn giả, nút nạp tiền/chuyển khoản/giải ngân hoạt động |
| 3.3 Tutor history/navigation | `/tutor/history` đọc dữ liệu thật theo UC5.5; dashboard dẫn đúng `/tutor/requests` khi đích sẵn sàng | Viết lại Connection/Scheduling/Progress; màn reviews của thành viên2 |

Các phần OAuth/profile/avatar của thành viên1, family/reviews/notification polling/chat buttons của thành viên2, Admin links/catalogs/KPI/audit và E2E toàn hệ thống của thành viên4 không thuộc scope. Có thể sửa **Sidebar dùng chung và CTA Payment trên dashboard Student/Parent** vì trực tiếp thuộc 3.2, nhưng chỉ sửa entry/label tài chính hoặc entry History liên quan.

## 3. Audit so với main mới nhất

Đây là kiểm tra source/route inventory, **chưa phải browser/runtime verification**.

| Hạng mục | Main d904f79 | Kết luận cho plan |
| --- | --- | --- |
| `frontend/src/shared/ui/Sidebar.tsx` | Ba entry `/student/payments`, `/parent/billing`, `/tutor/earnings`, chưa có pages tương ứng | Thêm route thông tin tạm và badge; audit CTA ngoài Sidebar |
| Payment CTA ngoài Sidebar | StudentQuickActions; ParentQuickActions; ParentDashboardView cùng trỏ URL Payment | Chỉ sửa Sidebar sẽ còn đường vào chưa đồng nhất |
| TutorDashboardView onboarding step “Xem hàng đợi” | Trỏ `/tutor/dashboard` | Đích cần `/tutor/requests`, nhưng chưa được đổi mù quáng |
| `/tutor/requests` | **Chưa có page trên main**; Sidebar/TutorPendingRequests đã có link tới đó | Cần destination prerequisite, không sửa một self-link thành dead link mới |
| `/tutor/history` và menu History | Chưa có page/menu | Tạo route và entry thuộc nhóm GIẢNG DẠY |
| `backend/modules/progress` | Chỉ có pom, chưa có ProgressDashboardController/service | Không có API history để nối ngay trên main |
| `frontend/src/features/progress/api/progressApi.ts` | **Chưa có trên main** | Không được gọi SDK “đã có sẵn” như mô tả trong phân công |
| Connection backend | Đã có GET `/api/v1/connections/requests` với Tutor inbox, POST respond | Không cần xây lại backend Connection chỉ để có trang yêu cầu |
| origin/develop `7c3fa5ca0c841b9d144bfd0d40b3f1fd41afd5b2` | Có Progress controller/service/SDK, Scheduling API và `/tutor/requests` cùng component Connection | Đây là nguồn dependency cần team tích hợp, không phải code đã có ở branch này |
| SRS Payment so với D5/SADS | SRS phần schema cũ còn Payment; SADS/D5 loại bỏ payment/escrow; UC4.5 không áp phí phạt | Phải ghi conflict, tách nghiên cứu đề xuất khỏi nghiệp vụ đã duyệt |

Các source cần giữ ownership hẹp:

- `frontend/src/features/dashboard/components/tutor/TutorDashboardView.tsx`.
- `frontend/src/features/dashboard/components/tutor/TutorPendingRequests.tsx` và `TutorQuickActions.tsx` chỉ audit liên kết; không tự sửa cả dashboard/KPI/scheduling.
- `frontend/src/shared/ui/Sidebar.tsx`: có cả role config, render chính và Suspense fallback; hành vi badge/nav cần nhất quán cả hai.
- `frontend/src/features/dashboard/components/student/StudentQuickActions.tsx`.
- `frontend/src/features/dashboard/components/parent/ParentQuickActions.tsx`, `ParentDashboardView.tsx`.
- `frontend/src/app/tutor/layout.tsx`: role/session gate hiện có; giữ quy tắc này khi thêm pages.

## 4. Dependency phải giải quyết trước History và Requests

### 4.1. Gate G-REQUESTS

Trang đích `/tutor/requests` phải tồn tại và sử dụng backend phù hợp base trước khi chốt sửa navigation. Ưu tiên chủ module đưa implementation đang có vào main qua quy trình team, rồi cập nhật branch fix từ main bằng commit tích hợp reviewable. Nếu nhóm muốn port riêng vào branch fix: cần chốt danh sách source commit, component, SDK, shared types và compatibility trước; đó là prerequisite riêng, không tự copy page import một feature chưa tồn tại.

Không tạo một trang giả/empty cố định rồi báo hàng đợi đã hoạt động. Không đổi link sang một route 404, không tự nhập cả develop hoặc feat/connection chỉ để có page.

### 4.2. Gate G-HISTORY

Ưu tiên team đưa Progress/Scheduling/SDK cùng public facade dependencies vào main, sau đó cập nhật branch fix. Lựa chọn selective integration từ develop chỉ được thực hiện sau khi phương án và scope được chốt trong M3-01; không mặc định cho phép qua tài liệu phân công. Cần kiểm kê dependencies và Flyway version thực tế lúc đó, không lấy history từ cuộc refactor Connection cũ làm manifest của main này.

Không viết lại toàn bộ Progress/Scheduling trong task UI. Trong thời gian gate chưa đạt, research và Payment stubs vẫn làm độc lập; phần History không được tick hoàn tất bằng mock/fixture production hoặc thông báo “API chưa có” thay cho chức năng.

### 4.3. Contract quan sát trên develop — chỉ là bằng chứng, chưa dùng được trên main

| Capability | Contract quan sát | Giới hạn cần chốt |
| --- | --- | --- |
| History summary | GET `/api/v1/progress/teaching-history`, params page/size/studentName/status/from/to; page mặc định0, size10 | Mỗi item là **class/Engagement**, không phải một buổi học |
| Summary item | classId/studentId/studentName/goalId/goalTitle/status/progressPercentage/startedAt/endedAt/sessionStats | status là class status; startedAt/endedAt là mốc lớp, không phải giờ buổi học hoặc lastSessionAt |
| Paging | ApiResponse bọc Spring Page `content,totalElements,totalPages,number,size,...` | Không dùng envelope `{items,...}` của plan Connection cũ |
| Notes timeline | GET `/api/v1/progress/classes/{classId}/notes`, params from/to/page/size | DTO có sessionId/classId/startAt/endAt/status/noteText/recordedAt; cần xác nhận trả đủ sessions chưa có note và server ownership |
| Learning plan | Progress SDK/controller có read plan theo classId | Xác nhận endpoint/permission và empty state; không link tới page chưa có |
| Filters | studentName, class status; from/to hiện được chuyển xuống tính sessionStats | Không được gọi đây là filter loại class rows theo ngày hoặc filter attendance status nếu backend chưa thực hiện |

Gaps cần biên bản trong M3-01: subject label, last session date, tổng COMPLETED toàn Tutor (không cộng riêng page hiện tại rồi ghi “tổng”), bộ lọc session status/lớp/học viên, notes completeness, ordering ổn định/pagination, quyền Tutor sở hữu class, date-range inclusivity/timezone và read learning plan. Chỉ thêm/điều chỉnh API tối thiểu qua owner module sau chốt contract; không suy ra giá trị thiếu từ title/text hoặc biến null thành0.

## 5. Nghiên cứu học phí — nội dung tài liệu 3.1

Đầu ra dự kiến: `docs/tuition/tuition-policy-and-dispute-specification.md` và wireframe-flow appendix cùng domain. Chưa tạo spec/policy cuối trong lượt planning này.

### 5.1. Phân loại quyết định

1. **Đã chốt:** D5 bỏ Payment/escrow khỏi hệ thống hiện tại; phí trả trực tiếp giữa các bên; không tự thêm chế tài thu tiền vào code. Giá học phí trong Tutor profile là giá chào dạy, không phải số dư hoặc tiền đã nhận.
2. **Đề xuất cho giai đoạn sau:** nhật ký đối soát/mô phỏng xác nhận theo từng buổi, nhóm tuần/tháng chỉ là cách tổng hợp; không gọi là cổng thanh toán thật.
3. **Cần team phê duyệt:** ai xác nhận, ai được đại diện Student, bằng chứng tối thiểu, thời điểm mở/đóng khiếu nại24h, cách xử lý hai bên không phản hồi, sửa kết quả, no-show hai bên, hủy sát giờ, mức hoàn/phạt. Chưa đặt con số tài chính hoặc mặc nhiên gọi Student signature là chức năng đã có.

### 5.2. Đề cương spec phải đủ

- Current scope vs proposed future scope, stakeholder Student/Parent/Tutor/Admin, quyền đại diện Parent khi link bị revoke.
- So sánh per-session/weekly/monthly; khuyến nghị mô hình đơn giản và trade-offs, phân biệt học phí thỏa thuận/đã dạy/được xác nhận/đã đối soát.
- Lifecycle riêng cho session outcome, xác nhận học phí, đối soát và dispute; không gộp “COMPLETED” với “đã trả tiền”.
- Ma trận attended/Student absent/Tutor absent/early cancellation/late cancellation/technical failure; điều kiện bằng chứng, trách nhiệm bên nào, manual resolution và quyền sửa/audit trail.
- Cửa sổ khiếu nại24h là **đề xuất cần định nghĩa mốc bắt đầu/timezone/thông báo/ngoại lệ**, không phải mặc nhiên quy định đã được SRS hiện hành chấp nhận.
- Chính sách hoàn/phạt có bảng lựa chọn, lý do và cột “chưa duyệt”; không hứa auto-refund/escrow/giải ngân khi EduFit không giữ tiền.
- Dispute flow từ mở → bổ sung bằng chứng → phản hồi → đề xuất xử lý → đóng/mở lại có điều kiện; ai được xem gì và thời hạn lưu dữ liệu dự kiến.
- Wireframe bằng mô tả màn hình/flow: học phí thỏa thuận → lịch sử buổi → xem bằng chứng → xác nhận/không đồng ý → đối soát mô phỏng/dispute → kết quả. Các nút trong wireframe là đề xuất, chưa triển khai UI giao dịch.
- Traceability tới D5/SADS/SRS, các conflict, decision log, ví dụ tính toán minh họa gắn giả định, acceptance scenarios và backlog triển khai tương lai tách khỏi branch fix.

Không tự sửa SRS gốc/schema dùng chung để che mâu thuẫn. Kết quả research cần review nghiệp vụ của team trước khi biến thành invariant sản phẩm; trong branch này chỉ bàn giao tài liệu, không tích hợp cổng thanh toán.

## 6. Phương án Payment stubs — làm được trên main hiện tại

**Đề xuất:** giữ entry tài chính với badge “Sắp ra mắt”, dẫn tới **ba trang thông tin read-only có thật** thay vì link chết. Cách này xử lý cả menu, CTA hiện có và bookmark nhập URL trực tiếp. Dùng chung một component thông báo, wrapper page theo role:

- `/student/payments`.
- `/parent/billing`.
- `/tutor/earnings`.

Thông điệp người dùng: chức năng quản lý học phí/thu nhập chưa khả dụng; thỏa thuận và thanh toán học phí trực tiếp giữa các bên. Không số dư0/thu nhập0/hóa đơn giả/nút thanh toán giả, không API request tài chính, không lưu localStorage để giả giao dịch. Có link quay lại dashboard đúng role.

Sidebar đã có `badge`; ưu tiên tận dụng thay vì refactor toàn nav engine. Badge/tooltip khi collapsed và Suspense fallback phải thống nhất. Chuẩn hóa các CTA đang ghi “Nạp học phí/Thanh toán” thành thông tin học phí kèm trạng thái sắp ra mắt; không sửa nút chat hoặc chức năng gia đình của thành viên2.

Giữ Tutor/Student/Parent layout role gates hiện có. Badge đơn thuần mà href vẫn không có destination không đạt acceptance. Không cần DB migration/backend endpoint cho stubs.

## 7. Thiết kế History UC5.5 — sau G-HISTORY

### 7.1. Luồng đọc theo lớp → buổi học

1. Thêm `/tutor/history` và entry “Lịch sử giảng dạy” trong GIẢNG DẠY của Tutor Sidebar.
2. Danh sách lớp của Tutor đang đăng nhập: học viên, môn/mục tiêu khi contract có, class status, counts COMPLETED/ABSENT/CANCELLED, tiến độ hoặc “chưa có kế hoạch”, last-session date khi API cung cấp.
3. Chọn một class mở panel/detail **trong History**, giữ state filter/page. Tải sessions theo class đang chọn; không load toàn bộ notes của mọi class để render list.
4. Hiển thị ngày/giờ, session status/attendance outcome, note do Tutor viết, recorded time; chọn xem note và learning plan read-only theo contract thật, không thay outcome.
5. Filter riêng class/student/class status ở summary; date/session status ở detail hoặc server-supported contract đã thống nhất. Reset page khi filter đổi, giữ selection chỉ khi còn thuộc scope; chống response cũ ghi đè request mới.

### 7.2. Yêu cầu chất lượng

- Loading skeleton; empty thật khi API trả rỗng; filtered-empty khác no-history; error + retry;403 không được biến thành rỗng,404 learning plan không tồn tại khác lỗi mạng.
- Không coi missing stats/progress là0; không tính global total bằng cộng một page. Counts COMPLETED dùng nguồn chuẩn khớp public profile theo NFR-23; giữ ABSENT/CANCELLED riêng.
- Reuse apiClient/fetchApi và auth/session/CORS/CSRF policy hiện có; không tạo JWT flow hoặc đọc DB từ frontend.
- Backend phải scope theo Tutor hiện tại và ownership của class/session/notes, không tin tutorId/studentId do client gửi. Tutor khác class, Student/Parent/Admin không được lộ nội dung.
- Notes render plain text, không dangerouslySetInnerHTML; không email/PII không cần thiết, không session ngoài class.
- Dùng date/time utilities đã có; chốt mapping local date range → Instant và biên inclusivity trước khi gửi from/to. Không hard-code mốc UTC làm lệch ngày ở Việt Nam.
- Responsive/mobile, table headers/labels, keyboard focus khi mở/đóng detail, badge có text không chỉ màu; giữ hệ thiết kế dự án.
- Chưa có contract thì chưa code luồng đọc bằng API URL/field tự đoán. Không mock dữ liệu production để làm trang trông hoàn tất.

## 8. Phases và nhiệm vụ thực thi

| Phase | Task | Kết quả |
| --- | --- | --- |
| 0 — Base & dependency | M3-00, M3-01 | Base main đã xác nhận; chốt nguồn tích hợp/contract/gates |
| 1 — Research | M3-02, M3-03 | Evidence/conflict matrix; spec đề xuất + wireframe/decision log |
| 2 — Payment | M3-04, M3-05 | Ba route thông tin, badge/CTA nhất quán, không dead link trong scope |
| 3 — Tutor | M3-06, M3-07, M3-08, M3-09 | Requests destination + fix link; history API/list/detail/notes |
| 4 — Verification | M3-10 | Scope tests, browser evidence, docs và final diff review |

Chi tiết acceptance/dependency/files/verification và commit dự kiến ở todo. Research và Payment có thể tiếp tục trong lúc chờ gate History; task chờ dependency không được tự tick done. Đây là dependency graph, không yêu cầu tạo agent/chat phụ.

Mỗi task implementation sau khi plan được review: scope nhỏ, thêm regression có ý nghĩa, chạy focused checks/affected validation, review diff, commit riêng và push nhánh fix theo workflow đã thống nhất. Không gom research/payment/history/API prerequisites thành một commit. Không tự merge main/develop/PR hoặc deploy.

## 9. Verification dự kiến — chưa chạy trong lượt planning

- Đọc hướng dẫn Next.js local tại `frontend/node_modules/next/dist/docs/` trước khi code, theo frontend/AGENTS.md; không dùng API/convention theo trí nhớ nếu khác version.
- Vitest/Testing Library: Payment notice/role links/badges/CTA; requests href; History success/loading/empty/error/retry, filter/page/reset/race, selected-class notes và escaping. Reuse fixtures test-only; không test trùng implementation chỉ để tăng count.
- Frontend gates từ frontend/: `npm run type-check`, `npm run lint`, `npm run test`, `npm run build`; phân biệt lỗi baseline ngoài scope với lỗi mới, không disable rules để làm green.
- Browser thật: Student/Parent/Tutor tới đúng stub, collapsed/sidebar fallback/direct URL không404; dashboard Tutor mở requests thật; history fetch thật và pagination/detail. Dùng dữ liệu đã cấp/test fixtures của môi trường test, không sửa DB cá nhân vô cớ.
- Auth: guest redirect/login; sai role không thấy History; backend403 cho class Tutor khác; session expiry, slow/reordered request,401/403/500 không giả thành no-data.
- Nếu có prerequisite/API backend thay đổi, chạy tests owner modules + consumer và Maven verify với Java21; PostgreSQL integration nếu chạm query/scope. Không thay migration cũ, không sửa source backend chỉ vì frontend cần field tự nghĩ ra.
- Research review: traceability D5/UC5.5/BR55/FR27/NFR07/22/23; bảng policy có trạng thái proposed/approved/open; không lẫn code thật với wireframe.
- Final: git diff --check, chỉ file scope, route inventory trước/sau và test counts/pass/fail/skip/not-run có evidence. E2E toàn4vai trò của thành viên4 không được nhận là đã hoàn thành bằng smoke tests Member3.

## 10. Rủi ro, phối hợp và quyết định cần review

| Rủi ro/điểm cần chốt | Hướng xử lý trong plan |
| --- | --- |
| Assignment thực tế dựa develop nhưng user chọn main | Giữ main; gate prerequisite review riêng, không tự đổi base |
| Progress/Scheduling/request page chưa vào main | Ưu tiên owner tích hợp vào main; selective port chỉ sau chốt scope/compatibility |
| SRS schema Payment cũ trái D5 và UC4.5 | Research ghi conflict; đề xuất tương lai không áp vào code |
| History summary API không phải session ledger | Master/detail, chốt nguồn sessions/notes và fields thiếu |
| Date filter API hiện chỉ đổi stats | Chốt semantics hoặc bổ sung server capability có owner; không ghi đã lọc rows nếu chưa làm |
| Shared Sidebar/ParentDashboard cùng team chỉnh | Diff hẹp, không format/rewrite toàn file; rerun role regression khi main thay đổi |
| History total/subject/last session chưa đủ contract | Không suy đoán/ghi0; chốt field/aggregation hoặc ghi dependency chưa đạt |
| Requests link target chưa sẵn | G-REQUESTS trước khi sửa và browser verification |

Hai điểm quan trọng để review trước implementation: **cách đưa prerequisite Progress/Scheduling/Requests vào base main**, và **phạm vi đề xuất học phí theo D5**. Các lựa chọn Payment badge + informational stubs và History master/detail là đề xuất trong plan, chưa triển khai.

## 11. Definition of Done

1. Spec nghiên cứu đủ các nội dung 3.1, phân biệt đã duyệt/đề xuất/chưa quyết định, có conflict/source/flow; không có code chuyển tiền.
2. Mọi entry Payment trong scope và direct URL tới nội dung đúng role, sắp ra mắt rõ ràng, không404/transaction giả.
3. Tutor dashboard dẫn đúng requests có thật; History đọc dữ liệu thật đúng class/Tutor, sessions/notes/filter/page/state đầy đủ theo contract đã chốt.
4. Focused/frontend/backend-affected/browser/security checks có evidence; thiếu prerequisite hoặc not-run không được báo hoàn tất.
5. Task/commit/docs đi đúng namespace; team ownership không bị mở rộng; không merge/deploy tự động.

**Trạng thái bàn giao lượt này:** đã pull main, tạo nhánh, đọc phân công/SRS/SADS/schema/source và viết plan/checklist. Chưa thay runtime frontend/backend, chưa chạy build/test/browser, chưa import develop, chưa tạo Tuition spec cuối hoặc triển khai bất kỳ task chức năng nào.
