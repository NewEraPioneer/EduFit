# Giao diện ví và thanh toán — 26/10/2026

Triển khai trên `feat/payment`. Đợt này thay giao diện, giữ nguyên API/nghiệp vụ tiền và cửa sổ 24h/48h trong [quy tắc hiện hành](payment-usability.md). Không thêm migration hoặc biến merchant mới.

## Student và Parent

- Thẻ số dư khả dụng nổi bật; số tiền đang giữ hiển thị riêng. Parent dùng tiêu đề Ví học phí gia đình.
- Nhập số tiền hoặc chọn nhanh 100.000 / 200.000 / 500.000đ → **Tạo mã QR**. QR xuất hiện trong popup ngay trên trang.
- Nếu có yêu cầu còn hạn đang chờ, **Tiếp tục nạp tiền** mở lại cùng checkout, không tạo order mới. UI chặn gửi lặp khi đang tạo yêu cầu; backend create vẫn chưa có client idempotency key, không coi các yêu cầu mới là cùng một order.
- Danh sách học phí gọn, có tháng và bộ lọc Tất cả / Cần phản hồi / Đã xong; ban đầu tối đa 5 buổi, bấm thêm khi cần. Mũi tên dẫn đúng trang chi tiết theo role.
- Hoạt động gần đây chỉ hiển thị 4 dòng. **Xem tất cả giao dịch** mở dialog phân trang với biến động số dư hoặc yêu cầu nạp/rút. Đọc lịch sử không đổi số dư đang hiển thị ở trang chính.

## Popup QR

[payOS Checkout Script JS chính thức](https://payos.vn/docs/sdks/front-end/script-js/) hỗ trợ embedded checkout. FE tải trực tiếp `https://cdn.payos.vn/payos-checkout/v1/stable/payos-initialize.js`, đưa checkout HTTPS host `pay.payos.vn` vào iframe thông qua SDK. Không đưa Client ID/API Key/Checksum Key vào frontend; SDK chỉ nhận checkout URL và return URL của trang đang mở.

- Desktop chia hai cột: số tiền/nút kiểm tra và QR. Mobile xếp dọc, popup có thể cuộn, nút đóng luôn ở đầu.
- Manual QR hiển thị ảnh từ host `img.vietqr.io` trong popup, kèm nội dung chuyển khoản và sao chép; vẫn chờ Admin đối chiếu giao dịch thật.
- Callback `onSuccess` chỉ yêu cầu backend refresh/đối soát. Chỉ khi API trả PAID hoặc snapshot ví chứa PAID mới hiển thị **Nạp tiền thành công**; không tự tăng số dư từ callback, đóng cửa sổ hoặc nút Tôi đã chuyển tiền.
- SDK tải lỗi/cửa sổ provider đóng: hiển thị hướng dẫn thử lại hoặc kiểm tra tiền. Không tự tạo yêu cầu khác khi retry tải QR.
- Escape/nút X đóng popup, trả focus về nút đã mở; **đóng popup không đồng nghĩa hủy yêu cầu nạp**. SDK được dọn trước khi gỡ phần tử iframe; callback đến sau khi đóng không gọi API.
- Nếu popup bị chặn bởi mạng/CSP, cho phép nguồn script chính thức ở `script-src`, nguồn iframe payOS ở `frame-src` theo tài liệu provider; không tắt CSP/TLS. `PAYMENT_FRONTEND_URL` phải là frontend đang sử dụng như cấu hình trước đây. Webhook HTTPS và signed lookup tiếp tục là nguồn xác nhận tiền.

## Tutor

Trang chính hiển thị tiền có thể rút, thu nhập chờ tất toán và đang chờ chi trả. Thu nhập chờ là dự kiến sau phí, chưa rút được. Tài khoản ngân hàng mặc định được che số.

**Rút tiền** mở popup: nhập số tiền hoặc Rút toàn bộ → xem lại người nhận/số tiền/phí → Xác nhận rút tiền. Focus chuyển vào phần xem lại; lỗi API hiện trong popup. Pending/Processing hoặc thiếu số dư chặn yêu cầu mới. Đổi/thêm tài khoản cũng mở popup, giữ cơ chế ID phiên bản và snapshot người nhận cũ; không thêm xác minh ngân hàng tự động.

## Admin

Ba hàng đợi riêng: Chi trả gia sư / Nạp tiền / Hồ sơ vấn đề. Mặc định ưu tiên chi trả còn chờ, tiếp đến nạp, rồi hồ sơ; trang `/admin/disputes` vào thẳng hồ sơ. Số lượng là số liệu **trang hiện tại**, không phải tổng toàn hệ thống. Chọn gồm yêu cầu đã xử lý để xem terminal; phân trang giữ giới hạn API 50 dòng.

Cấu hình thu/chi và mã đối soát đặt trong phần mở rộng. Xác minh nạp, ghi nhận chuyển thủ công hoặc duyệt chi mở dialog yêu cầu ghi chú và reference như trước. PROCESSING chỉ đối soát, không cho đánh dấu chuyển tay/từ chối. Hồ sơ khiếu nại dẫn tới chi tiết Admin hiện có, không duyệt trực tiếp trên danh sách.

## Kiểm chứng và giới hạn

- 36 FE tests PASS: 8 quyết định buổi học, 8 ví/tài khoản/rút, 7 QR, 5 Admin đối soát, 2 Admin phân xử, 4 workspace, 2 notification routing. Sau chỉnh focus/layout, chạy lại 15 QR/withdraw tests PASS. Type-check, lint và production Webpack build PASS.
- Browser fixture dùng component thật, toàn bộ API và SDK/QR giả: Student/Parent/Tutor/Admin, lịch sử, popup, Escape/focus, bước xem lại rút và Admin; kiểm 320/768/1024/1440px không tràn ngang. Sau tải mới không lỗi console; các cảnh báo createRoot từ hot reload fixture không phải lỗi ứng dụng. Tab/server fixture đã dừng.
- Build đầu lỗi CA Google Fonts và vài lượt reset/timeout; dùng CA tin cậy Windows và chạy lại thành công. Không tắt kiểm chứng TLS hoặc thay font ứng dụng.
- Chưa kiểm chứng checkout live/merchant hoặc giao dịch ngân hàng. Bố cục iframe thực phụ thuộc payOS; QR minh họa trong ảnh preview không dùng chuyển tiền. Không chạy lại BE vì đợt này không đổi BE, API tiền hoặc migration.

Commit code: `bd5520f` popup QR; `557cebd` ví/thu nhập; `6bb05f0` Admin; `06a331e` responsive/focus. [Task](../../tasks/payment/wallet-ui-plan.md), [báo cáo](verification-report.md), [setup](payos-setup.md).
