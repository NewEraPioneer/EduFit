# Payment usability — 26/10/2026

Nhánh `feat/payment`, tiếp tục từ `13ebab2`. Người dùng đã đồng ý hướng trao đổi: cửa sổ phản hồi đủ 24h, khiếu nại 48h rồi chuyển Admin, phân rõ payer/student và ví/thu nhập. Không chuyển tiền thật, không cấu hình merchant hoặc tích hợp Meet/Zoom trong đợt này.

1. **U01 — Rule và dữ liệu:** V1.21 thêm thời điểm mở phản hồi, phân loại/bằng chứng dạng văn bản của một hồ sơ khiếu nại, kết quả Admin. Xác nhận không tất toán sớm; quá hạn tranh chấp giữ tiền chờ Admin. Giữ terminal history; không sửa migration cũ. Kiểm thử biên thời gian, báo hoàn thành muộn, source thay đổi và duplicate.
2. **U02 — Quyền và xử lý:** học sinh vẫn xác nhận/báo vấn đề; chỉ payer và tutor đề xuất mức tiền; Admin quyết định đủ/một phần/hoàn, có lý do. Thêm sự kiện thay đổi học phí, thông báo các phía và Admin, link đúng session. Kiểm phân quyền/transaction/idempotency.
3. **U03 — Trải nghiệm:** card trạng thái và next action, chỉ mở form trong chi tiết; ví tập trung nạp/rút/lịch sử; thu nhập chờ/khả dụng/đang rút; một hồ sơ có phân loại, nội dung, bằng chứng văn bản và hai phản hồi. Trạng thái chờ Admin không hứa tự hoàn. Kiểm FE/type/lint/build/browser bằng fixture.
4. **U04 — Bàn giao:** đồng bộ API/schema/rules/diagram/report, PG fresh/upgrade và regression, chia commit/push theo phần. Không đánh dấu live merchant hoặc cuộc gọi Zoom đã kiểm chứng.

Mốc 24h được lưu từ thời điểm muộn hơn giữa endAt và lúc hệ thống ghi nhận/mở cửa sổ phản hồi; fallback khi sự kiện bị mất không bắt đầu ngược về quá khứ. Khởi tạo và thông báo cùng transaction để người dùng không mất thời gian phản hồi. Đủ 48h chưa thống nhất chỉ chuyển Admin, không tự phán quyết.

## Bàn giao

- [x] U01 và U02: `2c3270b`, đã push; 111 tests BE PASS trong gate, listener định dạng giờ chạy lại 2 tests PASS.
- [x] U03: `20ad2f6`, đã push; 24 tests FE, type/lint/build và browser fixture PASS.
- [x] U04: docs/schema/API/runbook và diagram hiện hành, báo cáo đúng phạm vi; commit tài liệu sau hai commit code.

Không gọi payOS thật, không migrate DB vận hành. Người dùng restart backend để áp dụng V1.21; source/money terminal cũ không sửa. Chi tiết giới hạn tại [báo cáo](../../docs/payment/verification-report.md).
