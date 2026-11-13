# Ghép develop và bảo toàn các luồng demo — 11/11/2026

Nhánh làm việc: `feat/complete-demo-user-flows`. Ghép `origin/develop` tại `75b5ac4` vào nhánh đang ở `a0e1980`; không merge ngược vào develop/main.

## Quyết định xử lý conflict

| Nhóm | Cách ghép |
| --- | --- |
| Giao diện, lưới lịch và thông báo | Dùng thiết kế mới của develop, hook deep link dùng chung, category và đánh dấu thông báo đã xem. |
| Lịch phụ huynh | Giữ callback chọn đúng con khi mở chi tiết buổi học; đóng buổi cũ khi người dùng chuyển con khác; bọc lịch trong Suspense theo develop. |
| Phòng học và kết quả | Giữ điều kiện học phí đã được giữ trước khi bắt đầu; không trả link ONLINE sớm; không cho ghi nhận kết quả trước endAt. |
| Lịch định kỳ | Giữ repository DB, quyền đọc, participant locks và cập nhật agreed_slots; dùng một sự kiện thông báo cho cả đợt đổi lịch như develop. |
| Đổi lịch từng buổi | Dùng sự kiện đề xuất/từ chối mới của develop, giữ sự kiện hết hạn/đóng từ nhánh demo. Listener đóng bỏ qua REJECTED để không gửi trùng với sự kiện từ chối chuyên biệt. |
| Test nâng cấp thanh toán | Dùng helper tạo account của develop, giữ migration schema tách khỏi seed demo và test không giữ tiền lần hai khi bắt đầu. |
| Test connection | Theo constructor notification mới của develop, giữ các dependency kiểm tra xung đột lịch mới trong ConnectionFacadeImpl. |

Mười file có conflict ban đầu đã được ghép thủ công; các file tự merge cũng được kiểm tra qua typecheck, test và browser smoke.

## Bảo toàn local

- Không sửa `.env`, key Google/payOS/Gemini hoặc truststore trong lần merge.
- Không chạy lại seed hoặc sửa Flyway history. Schema local tiếp tục V1.38, giữ dữ liệu nền V1.34 và bộ bổ sung 18 tài khoản.
- Kiểm tra trình duyệt dùng DB bản sao riêng, frontend 3001/backend 18881; không bắt đầu buổi Hoàng Lan 14:00–15:30 hoặc tạo lại review Thùy Linh trong DB demo.
- Không xử lý KYC/khiếu nại trên DB demo để kiểm thử merge. Không tạo QR hoặc giao dịch ngân hàng thật.
- Giữ nguyên các tài liệu untracked khác của người dùng.

## Các kiểm tra

Kết quả cuối:

- Backend: 1.644 test, 0 failure/error/skipped, 218 suite report. Lượt toàn bộ ban đầu có một test kiến trúc fail do file build cũ; sau khi dọn file đó, chạy lại đủ `ArchitectureTest` thành công. Các test khác pass trong lượt toàn bộ, gồm PaymentFlowIT, PaymentApplicationIT, RecurringProposalPersistenceIT, DemoJourneySeedIT và các test notification PostgreSQL mới.
- FE: 106 file, 972 test. Lượt toàn bộ có 971 pass và một test `LinkInvitationNotification` thiếu NotificationProvider sau thay đổi context; đã bọc Provider như các test cùng nhóm và chạy lại đủ 6 test file đó thành công. Không bỏ qua test.
- TypeScript và `npm run build` Next.js 16.3.6 pass; các route được tạo thành công.
- Chrome trên giao diện mới: học sinh chưa giữ học phí không có link/nút bắt đầu, API từ chối bắt đầu; giữ học phí xong bắt đầu được, thấy phòng học; gia sư mở cùng buổi thấy phòng và không ghi nhận kết quả sớm. Chỉ dùng fixture DB bản sao.
- API trên bản sao: 18 account mới đăng nhập được; 8 học sinh đọc lớp/12 tiêu chí/phản hồi; 10 gia sư đọc đúng trạng thái KYC; Admin mở PDF, duyệt/từ chối thành công; phụ huynh đọc được ba hồ sơ khiếu nại và tin nhắn.
- Các test bổ sung kiểm tra hết hạn đổi lịch vẫn có thông báo, từ chối không bị thông báo trùng, và phụ huynh mở đúng con rồi chuyển con khác.

Một lượt kiểm tra kiến trúc ban đầu phát hiện `MilestoneAchievementNotifier.class` còn sót trong `target/classes` dù source đã bị develop xóa. Đã xóa đúng file build cũ, giữ kiểm tra kiến trúc và chạy lại; không nới rule kiến trúc hoặc khôi phục mã thông báo cũ.

Không coi việc Git hết conflict là bằng chứng toàn bộ luồng chạy đúng. Kết quả kiểm thử và giới hạn môi trường được ghi nhận bên dưới trước khi commit merge.

## Trạng thái demo sau kiểm chứng

Đối chiếu DB local lúc 13:32 ngày 11/11/2026:

- Schema V1.38, 24 account.
- Buổi `a12b1e37-1062-6460-c34c-07cf4bdfc3b9` của Hoàng Lan–Minh Anh vẫn 14:00–15:30 ngày 11/11, SCHEDULED/FUNDED, `learning_started_at` trống.
- Cặp Minh Anh–Thùy Linh chưa có review, để người dùng đánh giá trực tiếp.
- `tutor.seed09` và `tutor.seed10` vẫn PENDING KYC.
- Backend/frontend preview đã dừng; DB bản sao đã dọn. Không có commit thay đổi DB demo hoặc key trong lần merge.

Không gọi payOS/Google/Gemini thật, không chuyển tiền ngân hàng và chưa tổng duyệt đủ kịch bản 25 phút với cả nhóm. Các luồng này vẫn phụ thuộc kết nối/dịch vụ bên ngoài và giờ buổi học. Giữ các mốc demo thực tế thay vì tuyên bố mọi tình huống đã được kiểm chứng 100%.
