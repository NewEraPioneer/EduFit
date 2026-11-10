# EduFit — kịch bản bảo vệ đã đối chiếu với code (25 phút)

Bản sửa các điểm sai trong `EduFit_Defense_Demo_Script_25Min.md` do Antigravity soạn; không thay đổi bản gốc ngoài repo. Ngày rà soát: 10/11/2026. Nhánh sửa: `feat/complete-demo-user-flows`.

**Dữ liệu demo bắt nguồn từ V1.34; tính năng lưu lịch định kỳ và bắt đầu học cần schema V1.37–V1.38.** Không sửa/xóa lịch sử Flyway để quay phiên bản. Khởi động backend mới trên DB đã sao lưu sẽ chạy migration bổ sung; không tự reset seed.

## 1. Tài khoản, phân vai và phiên đăng nhập

Mật khẩu seed: `Password123@`.

| Thành viên | Tài khoản | Nhân vật |
| --- | --- | --- |
| Role A | student.khoi@edufit.vn | Lê Đức Khôi, gửi yêu cầu và xác nhận lịch |
| Role B | tutor.demo@edufit.vn | Nguyễn Hoàng Lan, nhận yêu cầu và dạy Toán |
| Role C | student.demo@edufit.vn | Trần Minh Anh, theo dõi tiến độ/đánh giá |
| Role C | tutor.linh@edufit.vn | Vũ Thùy Linh, nhận đánh giá Tiếng Anh |
| Role D | parent.demo@edufit.vn | Trần Văn Hưng, trả học phí và thương lượng |
| Role D | admin.demo@edufit.vn | Quản trị viên, quyết định học phí |

Chuẩn bị **sáu Chrome/Edge profile độc lập**, mỗi tài khoản một profile. Hai tab cùng profile vẫn dùng chung cookie; không đặt Minh Anh/Linh hoặc phụ huynh/Admin trong hai tab cùng profile. Bốn người trình bày có thể thao tác sáu cửa sổ.

## 2. Chuẩn bị dữ liệu trước ngày bảo vệ

Các bước dưới đây là checklist cho người tổng duyệt, chưa tự thực hiện trên DB demo khi sửa code.

1. **Đăng ký:** cả sáu email seed đều đã tồn tại. Nếu muốn đăng ký thật, chuẩn bị email mới do nhóm kiểm soát và nhận OTP được; tài khoản đó phát sinh ngoài sáu account seed. Nếu giữ đúng sáu account, chỉ giới thiệu form và nói rõ phần xác thực đã chuẩn bị sẵn; không bấm đăng ký bằng email Khôi rồi gọi đó là đăng ký thành công.
2. **Yêu cầu mới:** Khôi đã có request Toán đang chờ tới Hoàng Lan. Tạo một nhu cầu mới, gắn lời nhắn `DEMO BẢO VỆ — [mốc giờ]` để phân biệt. Không gửi lại đúng nhu cầu/gia sư đang PENDING; không nhận nhầm yêu cầu seed.
3. **Khung giờ:** Hoàng Lan seed chỉ khai báo Thứ 3/Thứ 6 19:00–21:00. Trước buổi demo, bổ sung Thứ 7 14:00–16:00 qua hồ sơ gia sư nếu chọn giờ này cho Khôi. Chọn hình thức, địa bàn, bộ sách và mục tiêu theo danh mục thực tế để nhu cầu đạt ACTIVE.
4. **Buổi mô phỏng đã học:** chuẩn bị một buổi Toán của Minh Anh–Hoàng Lan có giờ bắt đầu/kết thúc hợp lệ, học phí đã được phụ huynh giữ. Phải kết thúc trước cảnh ghi nhận kết quả. Giới thiệu đây là buổi được chuẩn bị trước, không phải buổi Khôi vừa tạo. Nếu muốn biểu diễn nút bắt đầu, thao tác trong thời gian buổi mô phỏng còn diễn ra; không thay giờ máy hoặc ghi hoàn thành sớm.
5. **Thanh toán:** phụ huynh có đủ số dư cho buổi mô phỏng. Vào chi tiết, xác nhận giữ tiền từ ví phụ huynh; số dư seed không đồng nghĩa mọi buổi mới đã giữ tiền. Không để hai người cùng cố cấp tiền từ hai ví cho một buổi.
6. **Phản hồi:** chọn cột mốc đang học của lớp Minh Anh, chuẩn bị nhận xét/bài tập và ít nhất một tiêu chí phù hợp để gia sư ghi nhận. Hoàn thành buổi học không tự tick tiêu chí hoặc tự làm tăng tiến độ.
7. **Review:** cặp Minh Anh–Linh chưa có review ở fixture ban đầu, đã có 5 buổi COMPLETED. Tổng duyệt tạo review sẽ tiêu thụ tình huống này; hôm bảo vệ dùng xem/sửa hoặc chuẩn bị lại riêng tình huống, không cố tạo lần hai.
8. **KYC:** bộ sáu account không có gia sư Mai và không có KYC chờ duyệt. Phần chính chỉ xem hồ sơ đã duyệt. Demo nộp/duyệt mới cần một tình huống chuẩn bị riêng, không giả định có sẵn.
9. **Dịch vụ ngoài:** kiểm tra mail OTP, AI, payOS nếu dùng. QR tạo thành công chưa có nghĩa ví đã cộng tiền; chờ giao dịch được xác minh mới là nạp thành công. Các phần này cần Internet dù FE/BE/DB chạy local.
10. Ghi lại mã/ngày của đúng buổi mô phỏng và request mới cho cả nhóm. Không ghi cứng ngày 24/10, số dư hoặc phần trăm tiến độ trong lời thuyết trình.

Không chạy lệnh `DROP SCHEMA` trong buổi bảo vệ. Migration gốc V1.34 tạo bộ demo đầy đủ, không tự thu gọn thành sáu account; quy trình tái lập sáu account là công việc riêng, phải sao lưu trước.

## 3. Timeline và các thao tác bàn giao

| Phút | Người thao tác | Việc thực hiện | Kết quả cần chỉ ra |
| --- | --- | --- | --- |
| 00:00–02:00 | A | Giới thiệu đăng ký: vai trò, họ tên/email/mật khẩu/OTP; dùng email mới nếu thực hiện thật. Chuyển sang Khôi đã có hồ sơ. | Phân biệt đăng ký thật và tài khoản chuẩn bị sẵn. |
| 02:00–05:00 | A | Tạo/chọn nhu cầu mới đủ thông tin; tìm Hoàng Lan; xem gói Toán 250.000đ/buổi và độ phù hợp. Thử một giờ đã bận để thấy cảnh báo/chặn, sau đó chọn giờ trống đã chuẩn bị. Gửi với lời nhắn nhận diện. | Yêu cầu hợp lệ PENDING; đề xuất giờ trùng bị chặn trước khi gửi thành công. |
| 05:00–07:00 | B rồi A | B mở inbox, làm mới và nhận đúng request vừa gửi. B và A mở lớp vừa sinh. | Cùng một request được chấp nhận tạo cùng một lớp ở hai đầu. |
| 07:00–09:00 | B rồi A | B đề xuất một buổi tương lai cho lớp Khôi; A xác nhận. A đề xuất đổi sang giờ trống khác; B chấp thuận. | PROPOSED → SCHEDULED; lịch cũ giữ đến khi đối tác chấp thuận đổi. |
| 09:00–12:00 | B | Nói rõ chuyển sang lớp Minh Anh có buổi chuẩn bị trước đã kết thúc. Ghi nhận COMPLETED, bài tập/nhận xét và tiêu chí đã học. | Nhật ký và phản hồi thuộc đúng Minh Anh; có học phí giữ nên mở cửa sổ phản hồi. |
| 12:00–15:00 | C / Minh Anh | Xem đúng buổi vừa được B ghi nhận, lộ trình và tiêu chí. Đọc phần trăm đang hiển thị, đối chiếu dữ liệu trước/sau nếu cần. | Tiến độ dựa trên bằng chứng/tiêu chí và trọng số; không đọc số 68% cố định. |
| 15:00–17:00 | C / Minh Anh rồi Linh | Minh Anh gửi review Linh nếu chưa có; chuyển profile Linh, làm mới trang review. | Đánh giá xuất hiện đúng cặp học sinh–gia sư, rating được cập nhật. |
| 17:00–19:00 | D / phụ huynh | Xem liên kết với Minh Anh và báo cáo; mở đúng buổi Toán B vừa hoàn thành. Cho xem ví trả và thời hạn. Xác nhận đã học nếu cần rồi báo vấn đề trong hạn. | Hai xác nhận không làm tiền chuyển ngay; báo vấn đề đóng băng khoản học phí. |
| 19:00–21:00 | D / phụ huynh và B | Phụ huynh gửi đề xuất học phí thực tế 125.000đ, gia sư gửi cùng mức và báo cáo đồng ý. | Hai mức giống nhau → AWAITING_ADMIN; chưa chuyển tiền. |
| 21:00–23:00 | D / Admin | Mở đúng hồ sơ mới, chọn “Dùng mức hai bên thống nhất”, xem lại rồi xác nhận. Phụ huynh/gia sư làm mới để đối chiếu delta ví. | Hoàn phụ huynh 125.000đ; gia sư nhận 112.500đ; phí EduFit 12.500đ. |
| 23:00–24:00 | D / Admin | Xem hồ sơ xác minh đã duyệt và nhật ký đúng phân hệ; giới thiệu trường hợp ADMIN_REVIEW có sẵn nếu còn thời gian. | Phân biệt duyệt thỏa thuận và phân xử khi hai bên chưa thống nhất. |
| 24:00–25:00 | Cả nhóm | Dự phòng chuyển profile/chờ tải, kết thúc mạch nghiệp vụ. | Không mở thêm nhánh mới khi đã quá giờ. |

## 4. Lời chuyển cảnh cần nói chính xác

**A → B:** “Khôi vừa gửi yêu cầu với nhu cầu và lịch riêng. Gia sư sẽ mở đúng yêu cầu đó để xem xét; lúc này hai bên chưa có lớp mới.”

**Lớp Khôi → lớp Minh Anh:** “Lớp Khôi vừa được tạo và chốt lịch tương lai. Để trình bày phần sau buổi học trong thời gian bảo vệ, nhóm chuyển sang Minh Anh, có buổi học mô phỏng được chuẩn bị trước.”

**B → C:** “Gia sư đã ghi nhận kết quả và phản hồi cho buổi của Minh Anh. Nhật ký và tiến độ ở phần tiếp theo thuộc chính lớp này. Các tiêu chí cần được gia sư ghi nhận, không tự hoàn thành chỉ vì hết giờ.”

**C → D:** “Sau kết quả học tập là quyền theo dõi và học phí của phụ huynh. Buổi Toán này sử dụng ví phụ huynh; các khoản Anh Minh Anh tự trả vẫn thuộc ví Minh Anh.”

**Phụ huynh/gia sư → Admin:** “Hai bên thống nhất học phí thực tế 125.000đ. Đây là khoản học phí trước phí nền tảng, chưa phải số tiền gia sư thực nhận. Admin sẽ xem xét và xác nhận.”

## 5. Luật nghiệp vụ để trả lời hội đồng

| Chủ đề | Cách giải thích đúng với implementation |
| --- | --- |
| Lịch rảnh và lịch bận | Lịch rảnh là thông tin đối chiếu độ phù hợp; xung đột lớp ACTIVE/buổi SCHEDULED là kiểm tra chặn. Không khẳng định lịch rảnh là một lớp chặn tuyệt đối. |
| Chống trùng | Có kiểm tra ở gửi/nhận kết nối và lịch buổi cụ thể, cùng ràng buộc DB cho các buổi SCHEDULED. Không tuyên bố mọi trạng thái và tình huống đều “không thể có lỗi”. |
| Chốt lịch | Yêu cầu kết nối tạo lớp; đề xuất buổi cần phản hồi mới thành SCHEDULED. Không đồng nhất hai loại chấp nhận. |
| Bắt đầu học | Người học/phụ huynh xác nhận giữ tiền từ ví của tài khoản mình; học sinh/gia sư bắt đầu trong khung giờ đã lên lịch. Bắt đầu không trừ học phí lần nữa. Link phòng học có thể được gia sư cập nhật bằng URL http/https hợp lệ. |
| Hoàn thành | Gia sư ghi kết quả sau giờ kết thúc; COMPLETED yêu cầu đã có học phí giữ. ABSENT là kết quả điểm danh khác, không tự gọi là buổi đã dạy thành công. |
| Xác nhận | Hai bên ghi nhận đã học/đã dạy vẫn giữ đủ cửa sổ phản hồi 24 giờ. Nếu không có khiếu nại, hệ thống xử lý tất toán khi đủ điều kiện và hết hạn. |
| Khiếu nại | Trong 24 giờ mở vấn đề; có 48 giờ thương lượng tính từ lúc mở. Cùng mức → AWAITING_ADMIN; hết hạn chưa thống nhất → ADMIN_REVIEW. Tiền tiếp tục giữ cho tới quyết định. |
| Phí | EduFit thu 10% phần học phí thực tế được tất toán. 150.000đ học phí được chấp nhận thì gia sư nhận 135.000đ và phí 15.000đ. |
| Tất toán/rút | Tất toán là chuyển số dư trong ví nội bộ. Chuyển ra ngân hàng là luồng rút riêng; tạo/duyệt yêu cầu rút chưa đồng nghĩa ngân hàng đã trả thành công. |
| Thu hồi liên kết | Chấm dứt quyền xem hồ sơ con, nhưng phụ huynh vẫn xử lý các giao dịch do chính ví mình trả. Không cho quyền đọc các buổi khác của con qua lịch sử thanh toán. |
| Review | Tối thiểu một buổi hoàn thành, quan hệ lớp ACTIVE hoặc ENDED, chưa có review của cặp học sinh–gia sư. Không phải mỗi lớp một review; học sinh/phụ huynh không có hai lượt độc lập cho cùng cặp. |
| Audit | Có audit chung, session_history, payment_audit và ledger. Tra cứu đúng nơi; không khẳng định tất cả xuất hiện trong một màn Audit Log hay dữ liệu có tính bất biến mật mã nếu chưa triển khai. |
| Meeting | Nút truy cập trong EduFit có kiểm tra học phí. Không khẳng định đã có webhook Zoom đo chính xác thời lượng/tham dự nếu chưa có tích hợp đó. |
| Công nghệ | Spring Boot 4.1.1, Next.js 16.3.6, PostgreSQL 17. V1.34 là phiên bản seed, không phải phiên bản thư viện Flyway. |

## 6. Khi có sự cố

- **Đăng ký trùng email:** chuyển sang email mới hoặc đăng nhập account seed, nói rõ bước đang làm; không giả vờ OTP đã thành công.
- **Request trùng:** kiểm tra nhu cầu mới và lời nhắn; không bấm gửi liên tục. Có thể trình bày yêu cầu seed đang chờ nhưng phải nói đây là dữ liệu đã chuẩn bị.
- **Không thấy dữ liệu bên đối tác:** dùng nút làm mới hoặc tải lại; chuông thông báo có polling, không hứa mọi màn cập nhật đồng thời tức thì.
- **Buổi chưa đến giờ/chưa kết thúc:** dùng buổi đã chuẩn bị đúng mốc; không bỏ kiểm tra thời gian để bảo vệ.
- **Khoản đã hết hạn phản hồi:** không thể biến khoản SETTLED trở lại khiếu nại bằng thao tác bình thường. Dùng buổi mới đã chuẩn bị; hồ sơ ADMIN_REVIEW cũ chỉ để giới thiệu phân xử.
- **Mất mạng:** phần dữ liệu local có thể tiếp tục; OTP mail, Google, AI và payOS cần kiểm tra riêng. Dùng ảnh/video kết quả tổng duyệt, ghi rõ là tư liệu dự phòng.
- **KYC hàng chờ rỗng:** xem lịch sử đã duyệt; không tìm gia sư Mai trong bộ sáu account.

## 7. Điều kiện chốt tổng duyệt

- [ ] Sáu profile độc lập, đúng người ở từng màn.
- [ ] Backend đã migrate bổ sung 1.37 và 1.38, giữ nguyên dữ liệu và checksum cũ.
- [ ] Luồng Khôi gửi → Hoàng Lan nhận → hai bên thấy lớp → đề xuất/xác nhận/đổi lịch đã tập qua UI.
- [ ] Buổi Minh Anh chuẩn bị đúng giờ và đúng ví; bắt đầu cần giữ tiền, không ghi hoàn thành trước endAt.
- [ ] Gia sư có nội dung phản hồi để Minh Anh và phụ huynh nhìn thấy ở cảnh sau.
- [ ] Tình huống review Linh còn tạo được hoặc đã chọn nhánh xem/sửa minh bạch.
- [ ] Một hồ sơ mới đi đủ báo vấn đề → hai đề xuất cùng mức → Admin quyết định → delta ví khớp phí 10%.
- [ ] Người trình bày đọc số liệu thực tế, không đọc cứng phần trăm, ngày học, số dư hoặc hạn phản hồi từ bản cũ.
- [ ] Tổng duyệt dưới 24 phút, còn một phút dự phòng. Không tuyên bố “100% Quality Gates” chỉ từ một nhóm test.
