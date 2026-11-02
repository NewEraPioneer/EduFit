# Giao diện ví/payment — 26/10/2026

Nhánh `feat/payment`, tiếp tục từ `e47828e`. Người dùng yêu cầu giao diện gọn cho mọi role, bỏ danh sách nạp/rút dài trên trang chính, QR mở popup tại chỗ. Giữ quy tắc tiền 24h/48h và phí đã chốt; không gọi merchant hoặc chuyển tiền thật khi test.

1. **Popup nạp:** dialog hỗ trợ bàn phím/focus; payOS embedded theo CDN chính thức, manual QR hiện ảnh; kiểm trạng thái backend mới báo thành công, resume yêu cầu đang chờ, retry/lỗi không tạo giao dịch giả. Kiểm test flow/success/cancel/SDK fail và domain URL.
2. **Student/Parent/Tutor:** số dư và CTA nổi bật, số tiền gợi ý, rút/tài khoản ngân hàng trong dialog; danh sách buổi gọn có lọc, hoạt động gần đây tối đa vài dòng; lịch sử đầy đủ theo yêu cầu trong dialog phân trang.
3. **Admin:** tổng quan yêu cầu cần xử lý, các tab nạp/rút/hồ sơ; cấu hình thu gọn, điều khiển xác minh trong dialog, giữ kiểm reference/approval và PROCESSING không chi lại.
4. **Kiểm chứng/bàn giao:** test FE, type/lint/production build, browser fixture desktop/mobile/bàn phím, cập nhật docs; commit/push riêng từng phần. Preview không gọi payOS hoặc database thật.

Nguồn tích hợp: https://payos.vn/docs/sdks/front-end/script-js/ — script tải trực tiếp CDN, embedded, cleanup exit; callback FE chỉ yêu cầu backend xác minh, không ghi tiền.

## Hoàn thành

- [x] Phần 1: `bd5520f`, popup QR chính thức/manual và kiểm verified status; đã push.
- [x] Phần 2: `557cebd`, trang ví/thu nhập gọn và lịch sử theo yêu cầu; đã push.
- [x] Phần 3: `6bb05f0`, 3 hàng đợi Admin + dialog xác minh; đã push.
- [x] Phần 4: `06a331e` chỉnh responsive/focus; 36 FE tests và type/lint/build PASS, browser fixture desktop/mobile. Docs/schema nghiệp vụ hiện có giữ rule tiền; cập nhật hướng dẫn UI và sơ đồ nạp. Không có migration/backend/env thay đổi.

Đây là bốn phần công việc, không coi số commit là số task mới. Phần tài liệu được commit riêng khi bàn giao; kiểm thử live merchant tiếp tục để người dùng thực hiện có kiểm soát.
