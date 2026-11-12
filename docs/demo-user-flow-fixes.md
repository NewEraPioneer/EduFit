# Hoàn thiện các luồng trước bảo vệ

Nhánh: `feat/complete-demo-user-flows`, bắt đầu từ `8d63d13` (đã có sửa ID lớp seed).

Phạm vi đã thống nhất ngày 10/11/2026: sửa các phát hiện trong lượt review, giữ nguyên dữ liệu demo đang chạy, không sửa migration đã áp dụng. Commit theo từng nhóm đã kiểm chứng.

## Các nhóm việc

- [x] Lịch từng buổi: bỏ trạng thái đã hủy/từ chối khỏi lịch bận; không ghi nhận hoàn thành trước giờ kết thúc; chặn phản hồi đổi lịch hết hạn.
- [x] Lịch định kỳ: lưu đề xuất DB, khóa xử lý đồng thời, kiểm tra quyền đọc, đồng bộ lịch thỏa thuận sau chấp thuận. Chặn trùng lịch thỏa thuận với lớp ACTIVE khác của cả học sinh và gia sư.
- [x] Thông báo: gửi/từ chối/hết hạn đổi lịch, điều hướng đúng buổi và tuần liên quan.
- [x] Đánh giá: cho phép đánh giá sau khi lớp kết thúc nếu đã học thật và chưa đánh giá cặp đó.
- [x] Thanh toán: giữ quyền xử lý giao dịch của chủ ví sau thu hồi liên kết; người học chọn ví và xác nhận giữ tiền trước buổi học; chưa giữ tiền thì chưa được bắt đầu học.
- [x] Kịch bản: sửa phiên đăng nhập, đăng ký bằng email mới, request trùng, ngày/trạng thái hết hạn, KYC và công thức phí; không ghi số liệu động thành hằng số.
- [x] Kiểm thử và rà soát cuối, cập nhật kết quả từng nhóm.

## Quyết định học phí

Người dùng đã chọn: người học chọn ví và xác nhận giữ tiền trước buổi học. Không tự động lấy tiền từ ví bất kỳ khi chốt lịch. Phí EduFit 10% học phí thực tế; cửa sổ phản hồi 24 giờ vẫn giữ nguyên khi hai bên xác nhận; thương lượng 48 giờ rồi chuyển Admin.

## Giới hạn thao tác dữ liệu

Không reset DB, sửa Flyway history, hoặc tạo giao dịch thanh toán thật trong quá trình triển khai. Kiểm thử DB dùng môi trường tách biệt. Migration mới chỉ có hiệu lực khi môi trường tương ứng được khởi động với code mới.

## Thay đổi schema và API

- `V1.37`: thêm `recurring_schedule_proposal` lưu đề xuất định kỳ và `class.agreed_slots` lưu lịch đang thỏa thuận; giữ nguyên lịch trong yêu cầu kết nối gốc làm lịch sử.
- `V1.38`: thêm `payment_charge.learning_started_at` và `learning_started_by`. Ghi nhận bắt đầu một lần; không tạo lần giữ tiền thứ hai.
- `GET /api/v1/payment/sessions/{id}/learning-access`: kiểm tra quyền, học phí và thời gian để hiển thị thao tác học.
- `POST /api/v1/payment/sessions/{id}/start`: yêu cầu học phí đã giữ, đúng học sinh/gia sư, buổi SCHEDULED và trong khung giờ. Phụ huynh có thể cấp học phí nhưng không bắt đầu thay học sinh.
- `PATCH /api/v1/scheduling/sessions/{id}/meeting-link`: gia sư của buổi học cập nhật URL http/https; không dùng liên kết chứa script hoặc thông tin đăng nhập.
- Các response lịch không trả link phòng ONLINE của buổi PROPOSED/SCHEDULED. Link được cấp qua luồng bắt đầu học; gia sư có form cập nhật phòng ở chi tiết buổi học.

Ví được chọn bằng tài khoản đang đăng nhập: học sinh trả từ ví học sinh, phụ huynh trả từ ví phụ huynh. Màn xác nhận ghi rõ chủ ví; hệ thống không tự lấy tiền của một tài khoản khác hoặc giữ tiền tự động khi chốt lịch.

Ngày 10/11/2026 đã đối chiếu DB local `edufit:5434`: Flyway ghi nhận 1.37 và 1.38 lúc 16:27; còn 6 tài khoản demo. Backend 8080 trả health 200. Không hạ schema hoặc sửa checksum. Fixture dùng cho browser test không có trong DB này. Sao lưu trước khi triển khai ở môi trường khác; không nạp lại seed demo vào DB có giao dịch thật.

## Kiểm chứng

- Frontend: 93 file, 700 test pass; TypeScript pass.
- Backend: kết quả cuối 1.605 test, 0 failure, 0 error, 0 skipped, 213 suite report. Lượt toàn bộ ban đầu có 9 lỗi khởi tạo test context của `MatchingWorkflowPostgresTest`; đã bổ sung `SlotConflictDetector` vào context và chạy lại đủ 9 test này thành công. Các test còn lại pass trong lượt toàn bộ; không bỏ qua test lỗi.
- Test luồng tiền `PaymentFlowIT` đã pass trên PostgreSQL riêng, gồm nâng cấp schema từ 1.16 và không giữ/trừ tiền lần nữa khi bắt đầu học nhiều lần. Test nâng cấp chỉ chạy migration schema; seed demo được kiểm chứng riêng bằng `DemoJourneySeedIT` trên DB mới.
- Test hồi quy lịch kiểm tra lịch hủy không còn bận, giờ kết thúc, hạn phản hồi, quyền xem, lưu lại đề xuất qua repository mới, và lịch thỏa thuận không chồng lớp khác.
- Selenium/Chrome trên frontend 3001/backend 18881/DB riêng: đăng nhập học sinh → mở đúng buổi qua `sessionId` → không có link/nút bắt đầu khi chưa giữ tiền → API từ chối bắt đầu với HTTP 400 → giữ 250.000đ → bắt đầu và thấy phòng học → gia sư mở cùng buổi thấy phòng và chưa được ghi nhận kết quả trước giờ kết thúc. Sổ cái có đúng 1 lần giữ tiền, audit có đúng 1 `LESSON_STARTED`.
- Ảnh kiểm tra lưu local trong `var/demo-tools/funded-lesson-student.png` và `funded-lesson-tutor.png`; không đưa dữ liệu phiên/cookie vào Git.
- Backend/frontend preview đã dừng, DB tạm cho browser test đã xóa sau khi đối chiếu. Backend 8080 và frontend 3000 hiện hữu không bị dừng trong thao tác dọn môi trường kiểm thử.

## Giới hạn kết luận

Kiểm thử này xác minh các lỗi đã sửa; chưa phải tổng duyệt đủ 25 phút với cả bốn người. Không gọi payOS, không chuyển/rút tiền ngân hàng, không gửi OTP thật hoặc gọi Gemini thật trong lượt kiểm thử này. Truy cập phòng được kiểm tra trong EduFit; chưa có webhook Zoom/Meet chứng minh thời lượng tham dự thực tế. Các cảnh demo phụ thuộc giờ học, hạn phản hồi và dịch vụ ngoài vẫn cần tổng duyệt theo `demo-defense-script-reviewed.md`.

## Chạy lại kiểm thử

Trong `frontend`:

```powershell
npx vitest run --maxWorkers=1 --no-file-parallelism
npx tsc --noEmit
```

Trong `backend`, dùng JDK 21 và Docker đang chạy cho Testcontainers:

```powershell
mvn '-Dtest=*Test,PaymentFlowIT,PaymentApplicationIT,RecurringProposalPersistenceIT,DemoJourneySeedIT' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dmaven.compiler.useIncrementalCompilation=false' test
```

Các test DB tạo môi trường riêng. Không đặt `PAYMENT_TEST_JDBC` tới DB demo hoặc DB thật. Không chạy nhiều Maven build đồng thời trong cùng checkout.

## Các commit chính

| Commit | Nội dung |
| --- | --- |
| `564fc16` | Lịch hủy không còn bận; chỉ ghi nhận kết quả sau giờ kết thúc; chặn phản hồi hết hạn. |
| `fb4a8d3` | Lưu đề xuất định kỳ, khóa giao dịch, quyền đọc và lịch thỏa thuận mới. |
| `a424025` | Đánh giá lớp đã kết thúc khi đủ điều kiện. |
| `fb15429` | Thông báo đổi lịch và mở đúng buổi/tuần/đúng con của phụ huynh. |
| `62c0a4d` | Chủ ví giữ quyền xem/xử lý khoản đã trả sau khi thu hồi liên kết. |
| `362db66` | Đổi lịch định kỳ không chồng lớp ACTIVE khác của học sinh hoặc gia sư. |
| `be46b02` | Giữ học phí trước khi bắt đầu, cấp link phòng và ghi nhận bắt đầu một lần. |

Fetch GitHub cuối lượt: `origin/develop` ở `80250d8`. Các thay đổi thuộc nhánh `feat/complete-demo-user-flows`; không merge vào develop hoặc main trong lượt này.
