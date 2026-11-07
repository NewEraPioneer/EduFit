# Dữ liệu demo đã nạp — 08/11/2026

## Database và source

- Local: `localhost:5434`, database `edufit`, schema `public`, user theo `.env` (`edufit`). Không nạp Supabase.
- Branch: `feat/demo-defense-data`, tạo từ `develop`. Đây là branch dữ liệu; các tính năng trên branch khác phải được tích hợp vào build bảo vệ nếu cần trình diễn chúng.
- Flyway: V1.35 thêm bộ workshop; V1.36 hoàn thiện đánh giá cột mốc của phản hồi Tiếng Anh. V1.34 giữ nguyên checksum.
- Khi bắt đầu, Docker chưa có container/volume EduFit, DB local được khởi tạo mới. Sau nạp: **24 tài khoản, 6 lớp, 54 buổi, 4 lộ trình** (tổng cả V1.34 và bộ bổ sung).
- PostgreSQL đang chạy trong `edufit-db-1`. Backend cổng 18881 chỉ bật để kiểm tra và đã tắt. Chạy backend bình thường với DB cổng5434/profile dev khi dùng web.

## Tài khoản dùng ngay

Mật khẩu chung: **`Password123@`**.

| Email | Dùng làm gì | Số dư khả dụng / đang giữ lúc bàn giao |
| --- | --- | --- |
| `student.live@demo.edufit.test` | Gia Bảo: nhu cầu Toán/Anh, request chờ, lớp Anh có 3 buổi đã học, đủ dữ liệu để thử review | 400.000đ / 0đ |
| `parent.live@demo.edufit.test` | Thu Hà: đã liên kết Gia Bảo và Hoàng Nam, chuyển giữa hai con; ví còn nguyên để cấp tiền cho buổi mới | 1.000.000đ / 0đ |
| `tutor.live@demo.edufit.test` | Quang Minh: request Gia Bảo đang chờ, đang dạy Nam, có lịch sử thu nhập và tài khoản nhận mẫu | 900.000đ / 0đ |
| `student.conflict@demo.edufit.test` | Hoàng Nam: lớp Toán, 5 request chờ, tiến độ chậm, học phí chờ xác nhận và hồ sơ chờ Admin | 750.000đ / 750.000đ |
| `student.empty@demo.edufit.test` | Học sinh mới chưa hoàn thiện hồ sơ, không có lớp | Chưa tạo ví |
| `parent.empty@demo.edufit.test` | Phụ huynh chưa có liên kết hiệu lực | Chưa tạo ví |
| `tutor.new@demo.edufit.test` | Gia sư DRAFT để hoàn thiện hồ sơ và nộp KYC từ đầu | Chưa tạo ví |

Admin: `admin.demo@edufit.vn`. Các tài khoản lịch sử V1.34 vẫn dùng như tài liệu walkthrough.

## Bản ghi nên chọn cho từng bước

| Việc muốn trình diễn | Bản ghi thực tế |
| --- | --- |
| Nhận kết nối ngay | tutor.live → Yêu cầu học viên → request Gia Bảo, Toán 9, lời nhắn bắt đầu “DEMO A”, lịch Thứ2 18:00–19:30 |
| Gửi request mới từ đầu | student.live rút request DEMO A trước, rồi gửi lại; hoặc chọn một gia sư khác chưa có request. Không gửi trùng cùng cặp/nhu cầu |
| Lập lộ trình mới | Nhận request DEMO A tạo lớp Toán mới; lớp này chưa có lộ trình |
| Xem tiến độ có dữ liệu | student.live → lớp Tiếng Anh với Vũ Thùy Linh; parent.live → chọn Gia Bảo |
| Xem tiến độ cần chú ý | parent.live → chọn Hoàng Nam → lớp Toán với Quang Minh, cột mốc phương trình quá hạn và cảm nhận còn khó |
| Xem lịch trống của Quang Minh | Buổi học lớp Nam nằm Thứ3 18:00 tuần tới và tuần sau; dành Thứ2 18:00 cho lớp mới của Gia Bảo |
| Chốt buổi có sẵn | Lớp Tiếng Anh Gia Bảo có đề xuất sáng Chủ nhật tuần tiếp theo; dùng tutor.linh@edufit.vn và student.live để đáp lại |
| Xác nhận học phí | student.conflict → Ví & Học phí → buổi ghi “DEMO busy-review”, đã kết thúc vài giờ trước khi nạp, còn cửa sổ24h |
| Admin xác nhận kết quả hai bên | admin.demo → Khiếu nại → Hoàng Nam/Quang Minh, giá250.000đ, cả hai đồng ý125.000đ, trạng thái AWAITING_ADMIN |
| Đối chiếu phí10% | Thu nhập tutor.live: 4 buổi cũ ×250.000đ; mỗi buổi nhận225.000đ, tổng900.000đ. Đã có ledger và audit tương ứng |
| Tạo yêu cầu rút | tutor.live có900.000đ và chưa có yêu cầu chờ. Tài khoản mặc định ghi DEMO, số0000000000: chỉ minh họa tạo yêu cầu, không chuyển tiền thật |
| Xem nhiều trạng thái khiếu nại/nạp/rút | Dùng bộ V1.34 (parent.demo/tutor.demo/admin.demo) để xem DISPUTED, ADMIN_REVIEW, PAID/REJECTED/PENDING |
| Review mới | student.live chọn gia sư Linh của lớp Anh ACTIVE có3 buổi COMPLETED, chưa có review của Gia Bảo |
| KYC | Admin mở hồ sơ Ngô Thị Mai đang chờ, Trịnh Hải Long đã từ chối hoặc Quang Minh đã duyệt; các hồ sơ này có PDF DEMO mở được |
| OTP/Google/payOS/Gemini | Là thao tác dịch vụ thật cần cấu hình và rehearsal riêng; seed không giả token hoặc tạo QR có thể thanh toán |

Mốc thời gian được tính lúc nạp khoảng đêm 07/11/2026. Request mới còn6 ngày; đề xuất lịch còn2 ngày; học phí chờ xác nhận chỉ còn phần thời gian của cửa sổ24h. Job nền có thể thay đổi trạng thái khi backend chạy. Lấy ngày giờ thực tế trên UI; không mặc định dữ liệu còn nguyên sau nhiều ngày.

## Tệp KYC mẫu

Asset: `scripts/demo/assets/defense-demo.pdf`, có chữ DEMO rõ ràng. Đã cài vào `var/credentials` và `backend/var/credentials` trên máy này. Khi chuyển máy hoặc dùng root storage khác, chạy từ repo root:

```powershell
./scripts/demo/install-assets.ps1
```

Nếu backend chạy container thì cần copy cùng file vào storage root của container/volume (mặc định `/app/var/credentials/defense-demo.pdf`); script trên chỉ cài cho backend chạy trên máy host. Download API file đã được kiểm tra trả200 ở backend host.

## Kiểm chứng đã chạy

- `DefenseDemoSeedIT`: 5 test PostgreSQL17 Testcontainers pass; `DemoJourneySeedIT` V1.34: 5 pass; không skip.
- Kiểm tra tài khoản, quan hệ, quota5, ví=ledger, không âm theo từng thời điểm, tiền gia sư+hoa hồng=tiền tất toán, thời gian lớp/buổi và không trùng SCHEDULED.
- Backend thật kết nối DB5434: 7 tài khoản mới và Admin đăng nhập200; đọc ví/lớp, nhu cầu, dashboard học sinh/phụ huynh, tutor todos, danh sách học phí, hàng chờ KYC/khiếu nại và tải PDF đều200.
- Trong kiểm chứng phát hiện phản hồi Tiếng Anh thiếu đánh giá cột mốc; đã bổ sung qua V1.36 và kiểm tra lại API tiến độ200.
- Chưa chạy trọn kịch bản bấm browser hoặc gọi thanh toán/Gemini thật. API đọc được không có nghĩa mọi event từ thao tác mới đã được kiểm chứng.

## Sao lưu và làm mới trước bảo vệ

Snapshot local trong folder bị Git ignore:

- `var/backups/edufit-before-defense.dump`: trước khi nạp public.
- `var/backups/edufit-defense-ready.dump`: sau khi nạp và kiểm tra DB/API.

Không chạy lại SQL V1.35/V1.36 bằng tay và không sửa checksum. Nếu đã bấm demo làm thay đổi dữ liệu, phục hồi snapshot vào **database demo riêng** khi cần reset toàn bộ; không tự ghi đè DB có dữ liệu thật. Khi bảo vệ vào ngày khác, cần chuẩn bị lại các mốc thời gian và trạng thái trên bản demo riêng, vì snapshot không tự dời cửa sổ24h/48h. Lịch sử dài hạn V1.34 vẫn dùng để xem báo cáo.

Để bật riêng DB đúng lớp cấu hình dev:

```powershell
docker compose -f compose.yaml -f compose.dev.yaml up -d db
```

Branch này chưa tích hợp các branch UI A–G hoặc fix lịch connection riêng. Khi trình bày chống trùng ở lúc gửi/đổi lịch đề xuất, dùng build đã tích hợp `feat/connection-request-schedule`; việc thêm seed không tự đưa code của branch đó vào đây.
