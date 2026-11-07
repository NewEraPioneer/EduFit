# Task thực thi: lịch đề xuất và chống trùng khi kết nối

Kế hoạch chi tiết: [request-schedule-conflict-plan.md](request-schedule-conflict-plan.md). Đây là checklist **chưa thực hiện**, không đánh dấu pass trước khi có test và SHA. Mỗi task một commit trên `feat/connection-request-schedule`, push ngay sau khi test; không merge branch trong quy trình này.

| Thứ tự | Task | Phụ thuộc | Commit dự kiến |
| --- | --- | --- | --- |
| 1 | T01 — Rỗng lịch riêng dùng lịch nhu cầu | Không | `fix(connection-ui): fall back to need slots when custom slots are empty` |
| 2 | T02 — Hiện lý do vô hiệu hóa nút gửi | T01 | `fix(connection-ui): show schedule validation beside submit` |
| 3 | T03 — Giữ lịch đang chỉnh khi reload cùng nhu cầu | T01 | `fix(connection-ui): preserve proposed slots for the same need` |
| 4 | T04 — Xác định nguồn lịch học sinh và triển khai SPI | T01–T03 | `feat(connection): expose student scheduled-session conflicts` |
| 5 | T05 — Chặn trùng lịch học sinh khi chấp nhận | T04 | `fix(connection): reject student schedule conflicts on acceptance` |
| 6 | T06 — Chặn trùng lịch hai phía khi gửi | T05 | `fix(connection): validate both schedules before request creation` |
| 7 | T07 — Kiểm chứng liên tầng và race | T06 | `test(connection): verify schedule conflict gates end to end` |

## T01 — Rỗng lịch riêng dùng lịch nhu cầu

- Test trước: xóa hết slot trong dialog rồi gửi; `desiredSlots` bằng lịch gốc, không rỗng, nhu cầu gốc không thay đổi. Kiểm tra chuyển qua gia sư khác vẫn mặc định theo nhu cầu.
- Nếu lịch gốc rỗng hoặc nhu cầu không `ACTIVE`, không gửi và báo cần bổ sung nhu cầu. UI cho biết rỗng nghĩa là lịch mặc định.
- File: `frontend/src/features/connection/components/ConnectionRequestDialog.tsx`, `frontend/src/__tests__/features/connection/connectionRequestDialog.test.tsx`.

## T02 — Lỗi lịch hiển thị ngoài thẻ đóng

- Test: nhập giờ kết thúc trước giờ bắt đầu và hai slot chồng nhau; đóng `<details>` vẫn thấy lý do khóa gửi cạnh nút. Sửa hợp lệ thì lỗi biến mất và nút bật.
- Dùng kết quả validation của **lịch sẽ gửi** sau fallback T01; tránh hai live alert trùng nội dung.
- File: dialog và test của T01; nếu cần sửa `AvailabilitySlotPicker` để kiểm soát vị trí lỗi.

## T03 — Không mất lịch vừa chỉnh

- Test: chỉnh lịch; `loadNeeds()` trả object mới cùng `needId` thì lịch riêng giữ nguyên. Chọn `needId` khác thì dùng lịch của nhu cầu mới. Lưu nhu cầu mới nhưng vẫn chọn nhu cầu cũ thì không ghi đè.
- Định nghĩa rõ hành vi khi bản thân nhu cầu đang chọn được sửa: giữ bản chỉnh và cho người dùng chủ động lấy lịch mặc định mới.
- File: dialog, test dialog.

## T04 — SPI lịch học sinh và nguồn dữ liệu

- Viết test đỏ rồi triển khai SPI theo `studentId`, buổi `SCHEDULED` trong 56 ngày, mép thời gian không trùng, format tiếng Việt/canonical/legacy. Xác định query repository và nguồn lịch lớp `ACTIVE` sau đổi lịch tuần.
- Thiết kế response xung đột không lộ tên/ID lịch của người khác. Tách chuẩn hóa writer mới và khả năng đọc dữ liệu legacy; tránh kiểm tra boolean âm thầm bỏ qua input mới không parse được.
- Files dự kiến: `backend/modules/connection/.../api/StudentScheduleConflictChecker.java`, `backend/modules/scheduling/.../infra/adapter/*`, `TutoringSessionDomainRepository`, persistence repository/adapter, test adapter và parser. Phần runtime gate ở T05–T06.

## T05 — Gate lịch học sinh lúc chấp nhận

- Dưới `lockStudent`, so lịch đề xuất với lớp `ACTIVE` của học sinh và buổi `SCHEDULED` qua SPI; giữ gate gia sư hiện có. Conflict trả 409, request vẫn PENDING, lớp mới không được tạo; từ chối request vẫn làm được.
- Test lớp gia sư A trùng khi B accept, buổi đã xếp trùng, và lịch chạm mép được phép. Test đổi lịch trong lúc request chờ.
- Files: `ConnectionRequestService`, `TutoringClassRepository`, test `ConnectionRequestRulesTest`/stress tests, adapter theo T04.

## T06 — Gate sớm cho cả hai phía lúc gửi

- Sau kiểm tra quyền, nhu cầu và tutor, trước save/event/quota `PENDING`, dùng cùng quy tắc của T05 cho gia sư và học sinh. Trả `409 SLOT_CONFLICT_WITH_ACTIVE_SCHEDULE` với slot trùng nếu có; request không được lưu và không chiếm suất chờ.
- Vẫn kiểm tra lại ở T05 vì lịch có thể đổi trước khi gia sư nhận. Không coi request `PENDING` của người khác là lịch đã đặt.
- Test cả bốn nguồn trùng, trường hợp nhiều slot có một slot trùng, và non-conflict; test HTTP hiển thị message đủ rõ trong dialog.
- Files: `ConnectionRequestService`, shared conflict policy/helper, controller/frontend tests nếu contract thay đổi.

## T07 — Checkpoint tích hợp

- Chạy frontend focused tests, Maven connection+scheduling tests, typecheck và `git diff --check`. Nếu DB local có thể dùng, chạy integration PostgreSQL cho hai request/accept cạnh tranh và exclusion constraint của buổi `SCHEDULED`.
- Rà source-of-truth lịch sau UC đổi lịch tuần và giới hạn horizon 56 ngày; ghi rõ trường hợp chưa thể chặn sớm nhưng vẫn bị chặn ở lịch buổi/DB. Không báo "100% chống trùng" nếu chưa có bằng chứng.
- Bổ sung báo cáo kiểm chứng với lệnh, số test và giới hạn thực tế; commit tài liệu cùng test tích hợp rồi push và so SHA remote. Giữ `develop` nguyên vẹn.
