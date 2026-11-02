# Payment — tài liệu ví, học phí và chi trả

> Cập nhật 26/10/2026: [quy tắc hiện hành và trải nghiệm Payment](payment-usability.md). Xác nhận không tất toán sớm; đủ 24h phản hồi mới tự thu; hết 48h tranh chấp chuyển Admin và tiếp tục giữ tiền.

Cập nhật 24/10/2026 trên `feat/payment`, gồm bản sửa tài khoản ảo V1.18; phạm vi biên dịch/kiểm thử ghi tại báo cáo. Đọc theo thứ tự:

| Tài liệu | Dùng khi nào? |
|---|---|
| [Giao diện ví gọn và popup QR](wallet-ui.md) | Student/Parent/Tutor/Admin, nạp QR tại chỗ, lịch sử theo yêu cầu |
| [Quy tắc và trải nghiệm 26/10](payment-usability.md) | Đọc trước: đủ 24h, giữ tiền chờ Admin, quyền payer/student và chi tiết hồ sơ |
| [Luồng, rule và cơ chế Payment — có ảnh diagram](payment-flow-and-rules.md) | Đọc từ góc nhìn người dùng: 7 ảnh, từng bước xử lý, 30 rule và ví dụ tiền trước/sau |
| [Giải trình kiến trúc và cơ chế](payment_implementation_plan.md) | Nghiệp vụ, tiền, module, file, schema và Q&A |
| [Task chi tiết từng phase](../../tasks/payment/plan.md) | Phạm vi, phụ thuộc, AC, commit/checkpoint |
| [Checklist](../../tasks/payment/todo.md) | Trạng thái code và việc vận hành còn mở |
| [API contract](api-contract.md) | FE/BE, phân quyền, validation |
| [Cấu hình payOS](payos-setup.md) | Env thu/chi, webhook, restart |
| [Runbook](runbook.md) | Thao tác và đối soát sự cố |
| [Báo cáo kiểm chứng](verification-report.md) | Bằng chứng thực chạy và giới hạn |

Nạp trước, giữ tiền theo buổi, hai phía xác nhận; tự tất toán sau đủ 24h từ cửa sổ được lưu/thông báo, không khiếu nại khi COMPLETED; giải quyết khiếu nại 48h; báo cáo khớp hoặc quá 48h chờ Admin, tiền vẫn giữ; hoa hồng 10% phần thực tất toán. Thu nhập vào ví tutor trước, rút ngân hàng là workflow riêng.

Code tích hợp đã có; khóa, ngân hàng, public webhook và giao dịch live còn cần cấu hình/kiểm chứng. Không ghi secret vào docs/Git.
