# Dữ liệu bổ sung cho demo local

Đã nạp ngày 10/11/2026 vào DB `edufit`, PostgreSQL local cổng 5434. Schema vẫn **V1.38**, bộ dữ liệu nền V1.34 được giữ nguyên. Đây là script nạp riêng, không phải migration Flyway và không tự chạy khi triển khai.

## Tài khoản mới

Mật khẩu: **`Password123@`**. Đây là các tài khoản minh họa, không dùng cho production. Sáu tài khoản demo cũ giữ nguyên.

| Email | Tên | Môn | KYC |
| --- | --- | --- | --- |
| tutor.seed01@edufit.vn | Nguyễn Đức Huy | Toán | Đã duyệt |
| tutor.seed02@edufit.vn | Phạm Ngọc Ánh | Toán | Đã duyệt |
| tutor.seed03@edufit.vn | Trần Hoài Nam | Tiếng Anh | Đã duyệt |
| tutor.seed04@edufit.vn | Lê Mai Phương | Tiếng Anh | Đã duyệt |
| tutor.seed05@edufit.vn | Vũ Quang Minh | Ngữ Văn | Đã duyệt |
| tutor.seed06@edufit.vn | Đỗ Thu Hà | Ngữ Văn | Đã duyệt |
| tutor.seed07@edufit.vn | Bùi Anh Tuấn | KHTN — Vật lý | Đã duyệt |
| tutor.seed08@edufit.vn | Hoàng Minh Châu | KHTN — Hóa học | Đã duyệt |
| tutor.seed09@edufit.vn | Đặng Khánh Linh | Ngữ Văn | **Chờ Admin duyệt** |
| tutor.seed10@edufit.vn | Nguyễn Thanh Tùng | Tiếng Anh | **Chờ Admin duyệt** |

| Email | Tên | Lớp có dữ liệu |
| --- | --- | --- |
| student.seed01@edufit.vn | Phạm Gia Bảo | Toán — Đức Huy; Văn — Quang Minh |
| student.seed02@edufit.vn | Nguyễn Bảo Ngọc | Toán — Ngọc Ánh; Anh — Hoài Nam |
| student.seed03@edufit.vn | Trần Đức Anh | Anh — Hoài Nam |
| student.seed04@edufit.vn | Lê Khánh Vy | Anh — Mai Phương |
| student.seed05@edufit.vn | Vũ Minh Khang | Văn — Quang Minh |
| student.seed06@edufit.vn | Đỗ Thảo Nhi | Văn — Thu Hà |
| student.seed07@edufit.vn | Bùi Quốc Hưng | KHTN — Anh Tuấn |
| student.seed08@edufit.vn | Hoàng Ngọc Diệp | KHTN — Minh Châu |

Hai gia sư 09/10 có hồ sơ và gói dạy để trình diễn KYC, **chưa có lớp hoặc yêu cầu kết nối được nhận trước khi được duyệt**. Các lớp lịch sử thuộc 8 gia sư đã duyệt.

## Dữ liệu đã thêm

| Nhóm | Số lượng mới |
| --- | ---: |
| Gia sư / học sinh | 10 / 8 |
| Hồ sơ KYC | 8 lịch sử đã duyệt, 2 chờ duyệt |
| Tài liệu KYC PDF | 10, ghi rõ DEMO và không phải văn bằng thật |
| Gói dạy / nhu cầu học | 10 / 10 |
| Yêu cầu kết nối | 26: 10 đã nhận, 8 chờ, 4 từ chối, 4 hết hạn |
| Lớp của các tài khoản mới | 10 |
| Buổi của các tài khoản mới | 100: 60 hoàn thành, 10 hủy, 20 đã xếp lịch, 10 đề xuất |
| Đề xuất đổi lịch từng buổi | 10: 5 chờ, 5 từ chối |
| Lộ trình mới | 10, mỗi lộ trình 4 cột mốc và **12 tiêu chí** |
| Phản hồi / ghi chú / đánh giá năng lực của nhóm mới | 60 mỗi loại |
| Buổi bổ sung cho Minh Anh–Hoàng Lan và Minh Anh–Thùy Linh | 16: 12 hoàn thành, 2 đã xếp lịch, 2 đề xuất |
| Cột mốc / tiêu chí bổ sung vào 2 lộ trình demo cũ | 8 / 24 |
| Phản hồi / ghi chú / đánh giá năng lực bổ sung ở nhóm demo cũ | 12 mỗi loại |
| Khiếu nại mới ở tài khoản demo | 3, kèm 6 tin nhắn trao đổi |

Không thêm đánh giá sao, nạp tiền, rút tiền hoặc thông báo giả cho 18 tài khoản mới. Các hồ sơ, gói dạy, nhu cầu, lịch rảnh và lịch sử KYC là dữ liệu hỗ trợ để lớp và yêu cầu kết nối có quan hệ hợp lệ. Các buổi hoàn thành của nhóm mới là dữ liệu lịch sử để xem lịch/tiến độ/phản hồi, **không có khoản thanh toán giả gắn kèm**; tiếp tục dùng tài khoản demo chính để trình diễn ví.

Khôi được giữ nguyên để trình diễn người mới gửi yêu cầu và hình thành lớp trực tiếp. Phần tiến độ/phản hồi bổ sung thuộc hai lớp đã có của Minh Anh; phụ huynh Hưng xem được qua liên kết sẵn có.

## Mở nhanh các phần cần demo

1. **Tìm kiếm:** dùng học sinh hoặc tài khoản demo chính để xem các hồ sơ/gói dạy mới. Gia sư đang chờ KYC chưa xuất hiện như người đã được duyệt; số kết quả còn phụ thuộc bộ lọc và nhu cầu đang chọn.
2. **Lịch, đổi lịch:** đăng nhập `student.seed01` và `tutor.seed01`; mở tuần có buổi sắp tới. Các buổi được xếp theo tuần, không dồn cả lịch sử lên tuần hiện tại. Đề xuất đổi lịch chờ nằm ở buổi tương lai thứ hai của các lớp 01/03/05/07/09.
3. **Tiến độ, phản hồi:** mở lớp Toán của Gia Bảo–Đức Huy để xem 4 cột mốc, 12 tiêu chí, trạng thái đạt/đang học/chưa học và 6 phản hồi. Dùng Minh Anh/Hoàng Lan để xem thêm dữ liệu trong lớp trình diễn chính.
4. **KYC:** đăng nhập `admin.demo@edufit.vn` → **Xác minh gia sư** (`/admin/verification-queue`). Chọn Đặng Khánh Linh hoặc Nguyễn Thanh Tùng, mở PDF minh họa rồi duyệt/từ chối. Đặng Khánh Linh khai báo sinh viên; Nguyễn Thanh Tùng khai báo đã tốt nghiệp. Không coi tài liệu DEMO là văn bằng người thật.
5. **Khiếu nại:** dùng phụ huynh Hưng, gia sư và Admin mở cùng hồ sơ dưới đây. Khi đã xử lý trên UI, seed chạy lại sẽ không khôi phục trạng thái cũ.

## Ba hồ sơ khiếu nại mới

| Lớp / buổi bổ sung | Trạng thái ban đầu | Tình huống |
| --- | --- | --- |
| Toán Minh Anh–Hoàng Lan, buổi 6 | DISPUTED | Mạng gián đoạn, còn thời gian thương lượng 48h |
| Toán Minh Anh–Hoàng Lan, buổi 5 | AWAITING_ADMIN | Hai bên cùng đề nghị học phí 125.000đ; chờ Admin xác nhận |
| Anh Minh Anh–Thùy Linh, buổi 6 | ADMIN_REVIEW | Hai bên khác mức đề xuất, đã quá 48h; chờ Admin phân xử |

Ba buổi đã kết thúc, học phí 250.000đ + 250.000đ + 200.000đ. Đã chuyển **700.000đ từ khả dụng sang đang giữ** trong ví demo của phụ huynh, có ledger và audit tương ứng. Không tạo nạp hoặc chuyển khoản ngân hàng. Ngay sau nạp: khả dụng 1.300.000đ, đang giữ 1.700.000đ; số dư sẽ thay đổi khi người demo tiếp tục giao dịch.

Ví dụ Admin chấp nhận học phí 125.000đ cho buổi Toán 250.000đ: hoàn 125.000đ về ví trả tiền, gia sư nhận 112.500đ, phí EduFit 12.500đ. Chưa tự xử lý bất kỳ hồ sơ nào trong DB demo khi nạp seed.

## Cách nạp lại trên một DB local khác

Chỉ dùng trên local đã có 6 account demo và schema V1.38. Sao lưu trước. Không chạy lại seed V1.34 vào DB đang có dữ liệu; không reset Flyway history.

Trong thư mục gốc repo, chạy:

```powershell
node database/demo/prepare-kyc-files.cjs
docker exec edufit-db-1 mkdir -p /tmp/edufit-enrichment-sql
docker cp database/demo/enrich-local-defense.sql edufit-db-1:/tmp/edufit-enrichment-sql/enrich-local-defense.sql
docker cp database/demo/verify-local-defense.sql edufit-db-1:/tmp/edufit-enrichment-sql/verify-local-defense.sql
docker exec edufit-db-1 psql -U edufit -d edufit -v ON_ERROR_STOP=1 -v allow_demo_seed=1 -v kyc_pdf_bytes=927 -f /tmp/edufit-enrichment-sql/enrich-local-defense.sql
```

File-generation companion ghi PDF dưới storage root local trong `.env`, mặc định `var/credentials`, và in số byte cần truyền vào SQL. Nếu mẫu PDF thay đổi, dùng số byte mới thay cho 927. Storage phải nằm trong checkout. Giữ cùng storage root khi chạy backend để Admin mở file được.

Toàn bộ nạp SQL nằm trong một transaction, có kiểm tra trước COMMIT. Chạy lại cùng bộ seed là no-op: không tạo tài khoản/buổi trùng và không giữ tiền lần hai. Nếu phát hiện một phần namespace email đã tồn tại từ nguồn khác, script từ chối ghi đè. Lịch và hạn phản hồi được tính từ lần nạp đầu; chạy lại không làm mới hạn và không đảo quyết định KYC/khiếu nại đã thao tác.

## Kiểm chứng và sao lưu

- Thử trước trên DB `edufit_enrichment_verify_20261110` phục hồi từ bản sao DB local.
- Trên backend kiểm thử cổng 18881: đăng nhập đủ 18 tài khoản, đọc lớp, 12 tiêu chí/lộ trình, phản hồi và KYC qua API đều pass.
- Thử Admin duyệt 09 và từ chối 10 **chỉ trên DB kiểm thử**; DB demo giữ cả hai ở PENDING.
- Kiểm tra Admin tải PDF qua API đúng quyền, đúng MIME/size và có nhãn minh họa.
- Kiểm tra phụ huynh đọc được cả 3 khiếu nại, ví khớp tổng ledger, không có lịch SCHEDULED chồng nhau.
- So sánh checksum: 6 dòng account demo cũ không thay đổi. Không thêm topup/withdrawal/review.
- DB local nạp thật đã pass toàn bộ kiểm tra SQL. Tại lúc kiểm tra cuối, backend 8080 đang tắt nên chưa lặp lại API qua backend local đang dùng; cần Run backend và frontend để xem dữ liệu trên web. Không dừng backend/frontend của người dùng trong lúc dọn bản kiểm thử.
- Backend kiểm thử đã dừng và DB bản sao đã dọn; các PDF trong storage local được giữ để Admin mở hồ sơ khi demo.
- Backup ngay trước nạp thật: `var/backups/edufit-before-enrichment-final-20261110-193438.dump`. Không commit backup, mật khẩu local, cookie hoặc các file upload vào Git.

Các kiểm tra SQL là điều kiện của **thời điểm nạp**, không phải yêu cầu các hồ sơ phải giữ nguyên trạng thái sau khi bạn demo. Các hạn chờ sẽ chuyển theo job nền đúng nghiệp vụ.
