# Kiểm chứng lịch đề xuất kết nối — 07/11/2026

Branch: `feat/connection-request-schedule`. Không đổi schema/Flyway. Mỗi task T01–T06 có commit/push riêng, ghi trong [checklist](request-schedule-conflict-tasks.md).

## Đã triển khai

- Rỗng lịch riêng dùng lịch của nhu cầu; nếu cả lịch nhu cầu cũng rỗng thì khóa gửi. Lỗi validation hiển thị cạnh nút gửi dù phần chỉnh lịch đóng; picker trong dialog không phát thêm alert trùng.
- Bản chỉnh gắn với nhu cầu, học sinh và gia sư. Reload object cùng ID giữ lịch; chọn nhu cầu khác lấy mặc định mới. Có nút chủ động lấy lại lịch mới nhất của nhu cầu.
- `request()` kiểm tra lớp ACTIVE và buổi SCHEDULED của cả hai phía trước khi lưu/phát event. `respondRequest(accept=true)` kiểm tra lại. Từ chối vẫn thực hiện được. Request mới không parse được hoặc tự chồng lấn bị báo validation thay vì âm thầm bỏ qua kiểm tra.
- SPI `TutorScheduleConflictChecker` thêm phương thức bắt buộc cho studentId; scheduling thêm query SCHEDULED theo studentId, sử dụng index sẵn có. Không phụ thuộc ngược scheduling từ connection.
- Khóa transaction theo student rồi tutor giúp serialize các accept connection cùng gia sư. Giữ nguyên ràng buộc DB khi xếp buổi; khóa connection không đặt chỗ và không serialize các write scheduling.
- Sửa phép so khoảng giờ của adapter cho buổi kéo qua nửa đêm tại Việt Nam.

## Kết quả thực chạy

- Frontend: `npx vitest run src/__tests__/features/connection/connectionRequestDialog.test.tsx --reporter=dot`: **12 pass**. Bao gồm xóa hết lịch, lỗi ngoài thẻ đóng, reload cùng nhu cầu giữ lịch và đổi nhu cầu lấy mặc định.
- `npx tsc --noEmit`: pass.
- Maven JDK21, `-pl modules/scheduling -am test`, tập test `ConnectionRequestRulesTest,SlotConflictStressChallengeTest,ConnectionControllerTest,ConnectionRequestInputTest,SessionScheduleConflictCheckerAdapterTest,SessionScheduleConflictCheckerAdapterAdversarialTest` và `-Dsurefire.failIfNoSpecifiedTests=false`: **180 pass, 0 failure/error/skip** (122 connection, 58 scheduling).
- Gate phủ cả bốn nguồn xung đột, không save/event khi trùng, request còn PENDING khi accept bị chặn, khoảng chạm mép được phép; adapter có test UTC+7 và qua nửa đêm.
- Lượt Maven toàn reactor dừng ở `MatchingCandidatesPostgresTest` thuộc Profile: không tìm được Docker. `docker info` báo không có pipe `dockerDesktopLinuxEngine`. Vì thế chưa chứng nhận full reactor hoặc race PostgreSQL thật. Lượt đầu trong sandbox cũng bị giới hạn pipe attach của Mockito; lượt kết quả trên chạy ngoài sandbox.

## Giới hạn cần giữ khi review

1. SPI kiểm tra buổi học trong 56 ngày, tương tự logic gia sư trước đó. Buổi xa hơn vẫn được bảo vệ khi xếp lịch và bởi exclusion constraint; không tuyên bố chặn mọi xung đột tương lai tại connection.
2. Lịch định kỳ lớp ACTIVE vẫn lấy từ `desiredSlots` của request đã được chấp nhận. Hệ thống hiện không lưu nguồn lịch tuần mới bền vững trong TutoringClass sau đổi lịch tuần. Có thể chặn theo lịch gốc dù các buổi thực tế đã đổi; cần tách bài toán cập nhật nguồn lịch lớp, không sửa lịch sử request hoặc migration ngầm trong hotfix này.
3. Chưa thực hiện browser E2E và kiểm chứng tranh chấp đồng thời PostgreSQL. T07 còn mở riêng phần này; unit test và mock không thay cho bằng chứng DB thật.
