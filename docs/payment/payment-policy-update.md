# Payment: cập nhật xác nhận và tài khoản nhận tiền — 25/10/2026

> Tài liệu lịch sử 25/10. Từ 26/10, phần xác nhận sớm và hết 48h tự hoàn được thay bởi [payment-usability.md](payment-usability.md). Tài khoản nhận tiền mặc định tiếp tục dùng.


Quy tắc trong tài liệu này thay thế autoaccept 1h và hạn tranh chấp 24h trong tài liệu lịch sử. Nhánh triển khai: `feat/payment`. Không phát sinh giao dịch ngân hàng thật khi phát triển/kiểm thử.

## Xác nhận và khiếu nại

- Sau giờ kết thúc trên lịch, gia sư và phía người học có nút **Xác nhận buổi học** và **Khiếu nại**. Phụ huynh chỉ thao tác theo quyền payer/liên kết hiện có.
- Hai bên xác nhận chủ động khi buổi học COMPLETED: tất toán ngay, hoa hồng 10% phần thực tất toán.
- Chưa đủ xác nhận, không khiếu nại: sau `endAt + 24h` hệ thống tự tất toán khi Scheduling ghi COMPLETED, kể cả chưa bên nào bấm xác nhận. Không coi sự im lặng là xác nhận chủ động; cờ `autoAccepted` ghi nguồn tất toán tự động.
- Không tự tất toán ở giờ thứ nhất. Các điều kiện cấp tiền cũng dùng cửa sổ 24h, giá/nguồn tiền/điều kiện trạng thái khác giữ nguyên.
- Khiếu nại chỉ được mở từ endAt đến trước endAt+24h và trước tất toán. Khi mở, tiền tiếp tục held và không chuyển sang ví gia sư.
- Hạn giải quyết là `disputeOpenedAt + 48h`. Trong thời hạn này hai bên gửi báo cáo và số tiền đề nghị. Hai mức khớp thì chờ Admin; chờ Admin không tự hết hạn.
- Hết 48h chưa thống nhất: EXPIRED, hoàn toàn bộ khoản giữ về available của payer, không thu phí. Đây là kế thừa cách xử lý hết hạn đã chốt trước đây; thời hạn đổi từ 24h sang 48h.
- Job mặc định 30s, có thể thực thi sau mốc hạn; deadline/authorization ở backend mới là điều kiện quyết định. Hủy/vắng mặt không bị tính học phí.
- V1.19 chỉ bỏ đồng ý tự suy diễn theo rule 1h trên khoản còn chờ xác nhận. Giữ xác nhận chủ động, khoản tranh chấp và lịch sử terminal; không sửa migration cũ hoặc hoàn tác giao dịch đã tất toán.

## Tài khoản nhận tiền mặc định

- V1.20 lưu một tài khoản nhận tiền mặc định theo chủ ví Tutor. Chưa có tài khoản thì điền ngân hàng/BIN, số tài khoản, tên người nhận một lần; xác nhận đã kiểm tra rồi lưu. Đây là thông tin tự khai, không hiển thị nhãn đã xác minh ngân hàng.
- Lần rút sau chỉ nhập số tiền hoặc Rút toàn bộ, xem người nhận che số và xác nhận. Ô tiền ban đầu trống để không bị chặn ngầm bởi giá trị mặc định 100.000đ. Có giải thích mức tối thiểu 10.000đ và yêu cầu đang chờ.
- Server đọc tài khoản theo userId trong session và ID phiên bản. PUT thay đổi cần expectedAccountId khớp; ID đổi sau khi lưu để chặn form cũ ghi đè/rút nhầm. Cookie và CSRF bắt buộc; lưu không đồng nghĩa chuyển tiền.
- Mỗi withdrawal giữ snapshot ngân hàng/BIN/tài khoản/chủ tài khoản. Thay tài khoản mặc định không đổi người nhận của yêu cầu đã tạo. Một yêu cầu PENDING/PROCESSING mỗi Tutor; lặp cùng yêu cầu không giữ thêm tiền.
- Không tự suy ra tài khoản mặc định từ lịch sử, không gọi API tra tên ngân hàng hoặc gửi OTP mới trong phạm vi này. Kiểm tra người thụ hưởng vẫn thuộc quy trình Admin trước khi chi. Chưa bổ sung reauthentication riêng khi đổi tài khoản; đây là giới hạn cần xem xét trước khi bật chi tự động không qua Admin.
- Không tự bật kênh chi payOS hoặc thay quy trình Admin duyệt. Hoa hồng 10% đã tính khi tất toán, không thu thêm 10% khi rút. Phí rút EduFit hiện 0đ; chỉ đánh dấu ngân hàng thành công sau kết quả đã xác minh.

## Kiểm chứng phần mốc thời gian

RED: test domain phát hiện autoaccept ở 1h và tranh chấp hết hạn ở 24h; test FE phát hiện thiếu nhãn/nút và hạn mới. GREEN: 31 Payment unit tests, 18 PaymentFlowIT và 2 migration convention tests PASS; 7 FE session decision tests PASS. PostgreSQL 17 Testcontainers riêng: fresh 19 migration và upgrade có dữ liệu/validate/no-op PASS. Provider dùng mock; không chạy backend với khóa thật hoặc chuyển tiền ngân hàng. Bộ FlywayMigrationIT tổng quát sẽ được chạy trong gate cuối sau khi bổ sung tài khoản nhận tiền.

## Kiểm chứng tài khoản nhận tiền và gate cuối

31 Payment unit tests + 21 PaymentFlowIT + 5 FlywayMigrationIT + 2 migration convention = 59 PASS, không skip. Fresh 20 migration, upgrade từ V1.09/V1.10/V1.11/V1.13/V1.16/V1.17 và rerun/validate có bằng chứng trong test riêng. Test bảo vệ owner/role, ID cũ/ID của người khác, duplicate rút, snapshot pending, reset legacy inferred consent và freeze khi Scheduling đổi sang ABSENT. FE Payment 20 tests PASS; type-check/lint PASS. Browser xem component thật trên fixture riêng, Rút toàn bộ → xác nhận, đổi tài khoản; không lỗi console, không gọi API. Chưa kiểm chứng live payOS hoặc giao dịch ngân hàng; không chạy backend với .env thật.
