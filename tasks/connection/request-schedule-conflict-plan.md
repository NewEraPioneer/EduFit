# Plan sửa lịch đề xuất trong yêu cầu kết nối

**Ngày rà soát:** 07/11/2026. **Branch thực hiện dự kiến:** `feat/connection-request-schedule` tại `C:/Users/ASUS/Desktop/EduFit/EduFit`. Tài liệu này là kế hoạch; chưa sửa runtime, schema hay migration. Danh sách task và commit ở [request-schedule-conflict-tasks.md](request-schedule-conflict-tasks.md).

## 1. Kết quả kiểm tra năm vấn đề

| # | Kết luận từ code hiện tại | Bằng chứng và giới hạn |
| --- | --- | --- |
| 1. Xóa hết lịch rồi gửi 400 | **Đúng.** `validateAvailabilitySlots([])` trả `valid: true`; `send()` truyền `desiredSlots: []`; `CreateRequestBody` dùng `@NotEmpty` và `ConnectionRequestInput` cũng chặn rỗng. | `frontend/src/shared/lib/dateTime.ts`, `frontend/src/features/connection/components/ConnectionRequestDialog.tsx`, `backend/modules/connection/.../web/request/CreateRequestBody.java`. Cần lấy lại lịch của `selected` khi không có slot riêng; không đổi lịch gốc. |
| 2. Lỗi bị giấu trong thẻ đóng | **Đúng.** Lỗi của `AvailabilitySlotPicker` nằm trong `<details>`, trong khi nút gửi ở ngoài và bị disable. | `ConnectionRequestDialog.tsx`; cần hiện một thông báo cạnh nút gửi khi lịch riêng không hợp lệ, kể cả `<details>` đóng. Tránh hai `role=alert` cùng đọc một lỗi. |
| 3. Lịch tự đặt lại | **Đúng về nguy cơ.** Effect phụ thuộc object `selected`; `loadNeeds()` lấy danh sách mới hoặc `tutor.offerings` có tham chiếu mới có thể tạo `selected` mới dù `needId` không đổi. | `ConnectionRequestDialog.tsx`. Đổi lịch mặc định chỉ khi ID nhu cầu đổi; tải lại cùng nhu cầu giữ bản chỉnh. Nếu nhu cầu đang chọn bị lưu trữ/xóa thì chọn nhu cầu hợp lệ và đặt lại có chủ đích. |
| 4. Trùng lịch gia sư chỉ chặn khi chấp nhận | **Đúng.** `request()` chưa gọi detector/SPI. `respondRequest()` kiểm tra lớp `ACTIVE` và buổi `SCHEDULED`; inbox có cảnh báo xung đột nhưng yêu cầu đã gửi. | `ConnectionRequestService.java`, `ConnectionQueryService.java`. Bổ sung kiểm tra sớm trước khi lưu request, vẫn giữ kiểm tra khi chấp nhận vì lịch có thể thay đổi trong thời gian chờ. Trả 409 với mã xung đột và slot bị trùng khi xác định được. |
| 5. Thiếu gate lịch định kỳ phía học sinh | **Đúng ở connection, nhưng hậu quả cần nói chính xác.** `request()` và `respondRequest()` không so lịch lớp/buổi của học sinh. Tuy vậy `ProposeSessionService` đã kiểm tra lịch học sinh và PostgreSQL có `ex_tutoring_session_no_student_overlap` cho `SCHEDULED`. | Có thể tạo lớp `ACTIVE` với lịch đề xuất trùng, nhưng DB không cho hai buổi `SCHEDULED` trùng giờ. Lỗi thường xuất hiện muộn khi xếp buổi, không phải chắc chắn đã ghi hai buổi trùng. |

## 2. Hợp đồng hành vi đích

1. Khung giờ trong yêu cầu chỉ là **đề xuất**, chưa đặt chỗ. Lịch riêng rỗng nghĩa là dùng `selected.availabilitySlots`. UI hiển thị rõ lịch cuối cùng sẽ gửi. Không gửi mảng rỗng; nếu cả lịch gốc cũng rỗng thì khóa gửi và báo cần hoàn thiện nhu cầu.
2. Lịch chỉnh theo từng yêu cầu gia sư, không sửa `LearningNeed`. Chuyển `needId` thì lấy mặc định của nhu cầu mới; tải lại cùng `needId` thì giữ bản chỉnh. Nếu nhu cầu được cập nhật ở modal trong khi đang chỉnh, không âm thầm ghi đè; thông báo cho người dùng và cho nút lấy lại lịch mới.
3. Trong `request()`, sau xác thực quyền/nhu cầu/tutor và trước `requests.save`, so **mọi** slot đề xuất với lớp `ACTIVE` và buổi `SCHEDULED` của cả gia sư lẫn học sinh. Một slot trùng thì từ chối yêu cầu, không tiêu thụ một trong năm suất `PENDING` và không phát event. Những slot chỉ chạm mép (`end == start`) được phép.
4. Trong `respondRequest(accept=true)`, kiểm tra lại cả hai phía trên dữ liệu mới nhất. Nếu lịch thay đổi khi request đang chờ, trả `409 SLOT_CONFLICT_WITH_ACTIVE_SCHEDULE`, không tạo lớp. Từ chối (`accept=false`) không bị gate lịch chặn.
5. Giữ nguyên bảo vệ khi xếp buổi và exclusion constraint của DB. Kiểm tra lịch định kỳ của connection không thể thay thế ràng buộc buổi học cụ thể.

## 3. Thiết kế backend dự kiến

- Tái dùng `SlotConflictDetector` cho lịch định kỳ từ các `ConnectionRequest` gắn với lớp `ACTIVE`. Bổ sung truy vấn lớp `ACTIVE` theo `studentId` (hiện repository chỉ có `findByStudentId`), loại trừ chính request/lớp liên quan nếu cần khi kiểm tra lại. Không tính các request `PENDING` như lịch đã đặt.
- Module connection không phụ thuộc trực tiếp scheduling. Giữ `TutorScheduleConflictChecker` cho gia sư; thêm SPI đọc lịch buổi `SCHEDULED` theo **studentId** trong connection, adapter ở scheduling và truy vấn repository tương ứng. Có thể trích parser/logic khoảng giờ dùng chung trong scheduling adapter, tránh hai bộ quy tắc lệch nhau.
- SPI hiện chỉ trả boolean và quét **56 ngày**. Muốn báo đúng slot trùng, trả tập slot hoặc chi tiết xung đột từ contract mới; không trả mã buổi/người khác. Tài liệu và test phải nêu rõ giới hạn 56 ngày. Buổi ngoài 56 ngày vẫn được kiểm tra khi xếp lịch và bởi DB; không được hứa chặn tuyệt đối mọi thời điểm trong tương lai.
- `ConnectionRequestInput` đang ở chế độ `legacy-compatible`, có thể nhận chuỗi không parse được; adapter sẽ bỏ qua chuỗi đó. Trước khi coi gate là đáng tin, chuẩn hóa/validate định dạng **đầu vào mới** (chuỗi tiếng Việt từ UI và canonical/legacy point hiện được test), nhưng giữ khả năng đọc dữ liệu cũ. Không bật `strict-v1` toàn hệ thống vì dạng tiếng Việt của UI không khớp regex hiện tại.
- Kiểm tra trường hợp lịch lớp cũ khác lịch đang dạy sau đổi lịch tuần: `TutoringClass` không lưu lịch tuần mới, còn logic connection đọc `desiredSlots` của request gốc. Xác định nguồn lịch `ACTIVE` đáng tin trước khi thay đổi gate; test đổi lịch và buổi đã lên lịch để tránh từ chối sai. Nếu chưa có nguồn lịch định kỳ cập nhật, nêu giới hạn và giữ bảo vệ buổi `SCHEDULED`.
- `lockStudent` hiện serialize các write connection cho cùng học sinh. Kiểm tra thêm race với lịch gia sư/scheduling thay đổi đồng thời: gate ở lúc gửi là phản hồi sớm; gate lúc accept và DB là lớp bảo vệ cuối. Không giả định phép đọc xung đột sớm là đặt chỗ.

## 4. Phạm vi và tiêu chí hoàn thành

Không đổi schema/Flyway trừ khi kiểm tra nguồn lịch đổi tuần chứng minh thật sự cần dữ liệu bền vững; khi đó phải tách quyết định và migration riêng. Không đổi business rule ghép gia sư, quota 5 request, thanh toán hoặc lịch buổi học. Không sửa các branch `develop`, `feat/connection` hay branch A–G khác.

Hoàn thành khi: test frontend cho rỗng/invalid/reload/đổi need qua; test backend cho bốn nguồn trùng (lớp và buổi của gia sư/học sinh), mép không trùng, reject path và race; test HTTP trả lỗi người dùng hiểu được; chạy Maven module connection+scheduling và frontend focused tests; kiểm tra PostgreSQL thật cho constraint/race nếu môi trường DB sẵn sàng. Mỗi task trong file đi kèm có commit và push riêng lên branch này sau khi qua gate.
