# Vận hành và đối soát Payment

> Cập nhật 26/10/2026: [quy tắc hiện hành và trải nghiệm Payment](payment-usability.md). Xác nhận không tất toán sớm; đủ 24h phản hồi mới tự thu; hết 48h tranh chấp chuyển Admin và tiếp tục giữ tiền.

Cập nhật 24/10/2026 theo code `3966d54`. Đọc [cơ chế](payment_implementation_plan.md), [API](api-contract.md) và [cấu hình payOS](payos-setup.md) trước khi vận hành. Không chỉnh balance bằng SQL hoặc tạo receipt giả để xử lý sự cố.

## 1. Kiểm tra trước khi bật kênh thanh toán

1. Đúng repository/branch và môi trường DB; migration V1.16 đến V1.20 áp dụng bằng Flyway. V1.18 chưa được thực thi trong đợt sửa tài khoản ảo, cần kiểm tra lúc nâng cấp.
2. Env thu/chi được truyền vào process/container; restart sau thay đổi env theo setup.
3. Admin mở /admin/payments, kiểm readiness; đây không phải phép thử khóa/ngân hàng live.
4. Kênh thu có ngân hàng nhận đúng, webhook HTTPS public đúng path.
5. Nếu chi: credentials/quyền dịch vụ/nguồn tiền chi riêng; không mặc định tiền thu tự chuyển sang nguồn chi.
6. Khóa chỉ backend, không log/commit/copy vào ảnh trao đổi.

## 2. Nạp qua payOS

Student/Parent tạo yêu cầu trong ví; kiểm đúng số tiền/tài khoản trên checkout trước chuyển. Ví chỉ tăng khi webhook hoặc signed lookup hợp lệ, không theo ảnh biên lai/return URL.

Khi đã chuyển mà ví chưa tăng:

1. Mở đúng yêu cầu nạp, bấm Kiểm tra tiền đã chuyển; owner/Admin mới được refresh.
2. Job cũng query theo batch; lease mỗi khoản 30s, không bấm liên tục.
3. Đối chiếu order, số tiền, tài khoản nhận, link/reference và trạng thái provider.
4. Kiểm public webhook, log lỗi không secret và liên kết đúng kênh thu.
5. Nếu nhiều lần chuyển/sai tổng tiền/mismatch: giữ trạng thái chờ, hỗ trợ đối soát với provider. Không manual-confirm khoản PAYOS.
6. Nếu đã credit, gọi lặp không được tăng lần hai; kiểm entry và notification cùng reference.

Có thời hạn checkout 30 phút không đồng nghĩa đã hủy giao dịch ngân hàng. Code hiện chưa đồng bộ mọi trạng thái hết hạn/hủy thành topup CANCELLED. Không hứa tự hoàn ngân hàng khi QR hết hạn.

## 3. Nạp QR thủ công

Cấu hình `PAYMENT_BANK_BIN`, `PAYMENT_BANK_ACCOUNT`, `PAYMENT_BANK_HOLDER`. Tạo yêu cầu MANUAL, chuyển đúng tiền/nội dung, Admin kiểm sao kê thật rồi nhập transaction reference duy nhất tại /admin/payments.

Chỉ bấm xác nhận khi đã nhận được tiền; tạo QR không credit. Không dùng endpoint confirm để từ chối yêu cầu, vì endpoint này chỉ xác nhận. Bản hiện tại chưa có luồng Admin cancel topup riêng.

## 4. Học phí từng buổi

- Payer kiểm giá rồi giữ tiền cho buổi; chưa có gate bắt buộc cấp tiền trước học.
- Tutor ghi hoàn thành ở detail hoặc Scheduling. Cửa sổ lưu/thông báo cùng transaction: reviewStartedAt=max(endAt,lúc mở), deadline=+24h. Nếu hoàn thành muộn hoặc legacy chưa mở, cấp đủ thời gian mới, không thu theo endAt cũ.
- Student/Parent payer còn link và Tutor chỉ xác nhận việc học. Hai xác nhận không tất toán sớm; vẫn báo vấn đề được trước hạn.
- Hết reviewDeadline, COMPLETED và không có case: job tất toán. Job mặc định 30s nên có độ trễ hiển thị/thực thi.
- Hủy/vắng mặt chưa có case được hoàn; đã có case giữ tiền đến quyết định Admin. Không sửa ledger hay reset deadline bằng SQL.

## 5. Hồ sơ vấn đề và phân xử

1. Participant mở case sau endAt, trước reviewDeadline nếu đã mở cửa sổ. Chọn loại, mô tả và bằng chứng văn bản.
2. Tiền tiếp tục held, hạn hai bên phản hồi openedAt+48h. Student không phải payer vẫn bổ sung thông tin, chỉ payer/tutor gửi mức tiền.
3. Hai mức khớp -> AWAITING_ADMIN. Chưa khớp đủ 48h -> ADMIN_REVIEW, không tự hoàn. Theo dõi cả hai state trong hàng đợi /admin/disputes.
4. Admin mở đúng detail, đọc bằng chứng và phản hồi; chọn học phí 0..A, nhập lý do. Đối chiếu kỹ khi nguồn chưa COMPLETED, vì quyền phân xử không phụ thuộc nhãn hoàn thành như auto-settlement.
5. Xem lại số hoàn ví/tutor nhận/fee rồi xác nhận. S>0 chỉ sau endAt; S=0 hoàn toàn bộ. Kết quả/thời điểm/lý do được lưu và thông báo.
6. Không timeout Admin, không appeal/reopen terminal trong bản này. Nếu notification lỗi, transaction mở hạn/ghi tiền rollback; kiểm log và thử lại nghiệp vụ đúng, không cộng tay.

Commission=floor(S*10%), tutor=S-commission, hoàn payer=A-S. Hoàn là về ví nội bộ, không trả ngân hàng. [Giải thích đầy đủ](payment-usability.md).

## 6. Rút thủ công

Tutor yêu cầu rút, available sang held. Admin kiểm người thụ hưởng, chuyển tiền ngoài app và xác minh kết quả cuối. Chỉ sau đó dùng review approve=true với reference thật và note. Nếu chưa chuyển và từ chối, approve=false trả held về available.

Chỉ xử lý manual khi PENDING; không chuyển ngoài app cho yêu cầu đã PROCESSING. Legacy thiếu BIN dùng manual hoặc từ chối để gửi lại, không đoán BIN từ tên tự do.

## 7. Rút qua kênh chi payOS

Admin chọn Duyệt và chuyển qua payOS, ghi note. Backend lưu PROCESSING trước gửi, một UUID/reference/key cố định; UI chỉ cho đối soát sau dispatch.

| Kết quả | Xử lý |
|---|---|
| Signed SUCCEEDED và khớp transaction | PAID, trừ held một lần |
| Signed FAILED/CANCELLED/REVERSED và khớp | FAILED, hoàn held một lần |
| PROCESSING/RECEIVED/ON_HOLD | Giữ tiền, tiếp tục lookup |
| Timeout/mismatch/chữ ký sai/chưa tìm thấy | Giữ PROCESSING, không hoàn, không gửi lệnh mới |

API refresh và job dùng lease 60s. Nếu crash giữa lưu PROCESSING và provider nhận request, có thể không tìm thấy lệnh: lưu withdrawal UUID/provider ID, đối chiếu với hỗ trợ provider. Không reset trạng thái/retry create/chi thủ công trước khi xác minh chắc chắn không phát sinh chi; chưa có UI xử lý tự động trường hợp này.

## 8. Sự cố thường gặp

| Dấu hiệu | Kiểm tra |
|---|---|
| Flyway SQLState 28P01 | Sai DB endpoint/credentials; kiểm process/container/port trước, không sửa migration |
| Local 5433 trỏ DB dự án khác | Phiên triển khai đã dùng EduFit 5434; đọc env hiện tại, không đổi mật khẩu/kill DB dự án khác |
| Validate checksum mismatch | Xác định migration bị đổi/nhánh/schema; giữ dữ liệu, không tự repair/clean/reset |
| UI báo liên hệ Admin | Backend thiếu config kênh thu và manual; kiểm env process và readiness |
| Readiness true nhưng tạo checkout lỗi | Kiểm khóa/quyền/bank/kết nối provider; readiness chỉ kiểm đủ biến |
| Dữ liệu checkout/tài khoản nhận không hợp lệ ở bản cũ | Kiểm phiên bản; V1.18 hỗ trợ tài khoản ảo theo checkout đã ký. Không đổi PAYMENT_BANK_ACCOUNT thành tài khoản ảo; restart backend để Flyway thêm cột mới |
| Webhook không vào local | Dùng HTTPS public/tunnel, cập nhật khi URL đổi; không đăng ký localhost |
| Có tiền ngân hàng nhưng ví chờ | Làm quy trình reconcile mục 2, không cộng tay |
| Rút PROCESSING lâu | Lookup/hỗ trợ provider mục 7, không hoàn/chi thêm |
| Notification sink lỗi | Transaction tiền rollback; kiểm DB/log và source receipt rồi thử lại đúng nghiệp vụ |

Không in khóa hoặc payload chứa thông tin nhạy cảm khi báo lỗi. Ghi ID/order/reference, thời điểm và kết quả cần đối soát.

V1.18 lưu tài khoản nhận theo từng topup; receipt phải khớp snapshot qua accountNumber/virtualAccountNumber. Hàng cũ NULL fallback tài khoản gốc. Nếu chưa chuyển tiền cho order lỗi có thể tạo yêu cầu mới sau khi nâng cấp. Nếu đã chuyển, giữ order/reference và đối soát hỗ trợ; không tạo/chuyển thêm hoặc cập nhật snapshot bằng tài khoản do webhook cung cấp. Regression/migration đã chạy trong DB test riêng, chưa live; xem [báo cáo](verification-report.md).

## 9. Tái kiểm thử trong môi trường riêng

Từ repository root:

```powershell
& .\backend\mvnw.cmd -f backend/pom.xml -pl modules/payment -am test
& .\backend\mvnw.cmd -f backend/pom.xml -pl app -am test '-Dtest=PaymentFlowIT,PaymentApplicationIT' '-Dsurefire.failIfNoSpecifiedTests=false'
```

Integration tests dùng Testcontainers postgres:17-alpine, hoặc `PAYMENT_TEST_JDBC`, `PAYMENT_TEST_USER`, `PAYMENT_TEST_PASSWORD` tới DB test riêng có quyền tạo schema. Không dùng DB vận hành. Tests không được coi skip vì thiếu Docker là pass.

Từ frontend:

```powershell
npm test -- src/__tests__/features/payment src/__tests__/shared/DashboardShell.test.tsx
npm run type-check
npm run build -- --webpack
```

Chạy thêm sidebar/dashboard tests khi đổi navigation. Windows/Turbopack từng lỗi spawn CSS; gate đã dùng Webpack. Môi trường TLS proxy dùng CA tin cậy, không tắt xác minh TLS.

[Báo cáo](verification-report.md) ghi test đã chạy; provider dùng mock, chưa live. Khả năng/môi trường test payOS cần đối chiếu tài liệu chính thức lúc sử dụng; không coi thông tin sandbox cũ trong lịch sử là điều kiện đã xác nhận cho merchant hiện tại.

## 10. Nâng cấp V1.21

Restart backend để Flyway thêm thời điểm cửa sổ, metadata hồ sơ, bảng phản hồi và ADMIN_REVIEW. Không sửa migration cũ/repair/clean/reset. Terminal cũ được giữ nguyên; charge pending chưa có review_started_at được job mở hạn và thông báo khi COMPLETED. Theo dõi hàng đợi Admin sau 48h vì tiền còn giữ, không tự hoàn. FE detail/notification polling 15s khi tab hiển thị và khi focus; không dùng polling để suy ra kết quả chuyển tiền.

## 11. Ghép develop: Connection V1.22 sau Payment V1.21

Payment V1.16–V1.21 giữ nguyên tên và nội dung vì database Payment đã ghi nhận các version này. Migration mở kết nối nhiều môn của develop dùng V1.22; các câu lệnh tạo lại unique index không đổi. Database sạch chạy 22 migration; database Payment ở V1.21 chỉ chạy thêm V1.22 và giữ nguyên số dư ví.

Trước khi triển khai, đọc `flyway_schema_history` của **từng** database. Nếu bản ghi V1.16 có description `create payment wallet and settlement`, có thể nâng cấp bình thường rồi kiểm tra hai index `uq_connection_request_pending`, `uq_class_student_tutor_active` đều chứa `goal_id`. Nếu V1.16 lại là migration Connection của develop, **dừng triển khai**: đó là lịch sử khác, cần sao lưu và lập phương án chuyển lịch sử riêng trên bản sao database. Không dùng `repair`, `clean` hay sửa tay lịch sử Flyway để lách lỗi rồi chạy Payment.
