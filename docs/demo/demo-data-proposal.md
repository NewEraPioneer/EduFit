# Đề xuất dữ liệu demo và kịch bản bảo vệ EduFit

**Ngày lập:** 07/11/2026. **Cập nhật 08/11/2026:** đã triển khai bộ bổ sung V1.35–V1.36 vào DB local `edufit`, cổng 5434. Chi tiết tài khoản, các khác biệt so với đề xuất và kiểm chứng ở [defense-data-ready.md](defense-data-ready.md). Các mục bên dưới lưu lại thiết kế ban đầu.

## 1. Mục tiêu

Người xem cần nhìn thấy một hành trình có liên kết: phụ huynh tìm gia sư cho con → gửi yêu cầu → hai bên thống nhất lịch → học → nhận phản hồi và theo dõi tiến độ → xác nhận học phí hoặc xử lý khiếu nại → gia sư nhận thu nhập và yêu cầu rút. Admin có công việc thực tế để duyệt, xử lý và kiểm tra lịch sử.

Bộ dữ liệu sẽ gồm hai phần: **hồ sơ có lịch sử để xem ngay**, và **hồ sơ đang ở đúng bước chờ để thao tác trực tiếp**. Một tình huống khó như khiếu nại không dùng chung buổi với tình huống xác nhận thanh toán thông thường. Không cần tạo hàng nghìn bản ghi; cần các bản ghi có câu chuyện, đủ đa dạng và thống nhất giữa các màn hình.

Đề xuất bao phủ các luồng đang có trong source và các trường hợp nghiệp vụ quan trọng. Không coi seed là bằng chứng tính năng đã chạy đúng; từng kịch bản phải đi qua API/giao diện trước khi bàn giao.

## 2. V1.34 đã có gì và cần bổ sung gì

Nguồn đã đọc: `backend/app/src/main/resources/db/seed/V1.34__seed_demo_journeys.sql`, các seed trước nó và danh sách trang theo role trong frontend. Đây là kiểm kê source; chưa đối chiếu dữ liệu đã áp dụng trong DB của bạn.

| Nhóm | V1.34 hiện có | Đề xuất tiếp theo |
| --- | --- | --- |
| Tài khoản | Bốn role chính, student.khoi và nhiều gia sư khác nhau | Giữ bộ này để xem lịch sử; thêm nhóm thao tác trực tiếp và nhóm tài khoản mới chưa có dữ liệu |
| Khám phá | Gia sư Toán, Anh, Văn, KHTN; nhiều loại gia sư, khu vực, hình thức và giá | Thêm nhu cầu đủ/thiếu thông tin, tình huống khớp cao/khớp thấp/không có kết quả và gia sư có lịch bận thực tế |
| Xác minh | APPROVED, PENDING, REJECTED; thông tin bằng cấp | Chuẩn bị tệp minh chứng demo mở được; dành hồ sơ chờ riêng cho thao tác duyệt và từ chối |
| Gia đình | Một liên kết đã xác nhận và lời mời tới student.khoi đang chờ | Thêm tình huống từ chối, rút lời mời, thu hồi và danh sách nhiều con có tên rõ ràng |
| Kết nối | Request ACCEPTED/PENDING/REJECTED/CANCELLED/EXPIRED; ba lớp | Bổ sung cặp chưa có request để gửi mới; request có thể accept; case trùng lịch có chủ đích để bị chặn |
| Lịch học | Buổi đã học, sắp tới, hủy, vắng, đề xuất, từ chối/rút/hết hạn; đổi lịch một buổi | Chuẩn bị mốc còn hạn sát ngày demo, khoảng trống cho lịch mới, và hai phía đề xuất/đáp lại |
| Tiến độ | Lộ trình, cột mốc, tiêu chí, phản hồi, tự đánh giá, báo cáo kỳ | Thêm lớp chưa có lộ trình và lớp tiến bộ chậm để so sánh; một buổi chờ gia sư ghi phản hồi |
| Payment | Sổ cái, ví, nạp/rút, thu phí và khiếu nại ở nhiều trạng thái | Tách case thao tác mới khỏi case lịch sử; chuẩn bị số dư dễ tính, kiểm tra chéo ledger và ví |
| Đánh giá | Có review và tổng điểm gia sư | Một lớp đủ điều kiện nhưng chưa đánh giá để bấm tạo mới; một review còn quyền sửa |
| Notification/Admin | Có notification; có một số bản ghi audit payment | Bổ sung lịch sử admin và thông báo liên quan đúng bản ghi, kiểm chứng thao tác mới tự phát thông báo |

**Hai điểm cần rà kỹ trước khi dùng bộ cũ để demo:** V1.34 có request PENDING của student.demo vào giờ lớp đang học; gate chống trùng mới có thể từ chối accept — dùng case đó để minh họa xung đột, không dùng làm luồng thành công. Comment của seed ghi tutor.kien không có slot nghĩa là kín lịch, trong khi code đã có quy ước lịch rảnh trống là linh hoạt ở một số màn; muốn minh họa kín lịch phải có buổi SCHEDULED thực sự, không chỉ để mảng rỗng.

## 3. Tài khoản đề xuất

Giữ nguyên email và dữ liệu lịch sử của V1.34. Thêm **7 tài khoản minh họa** dưới miền giả `demo.edufit.test`, dùng trên môi trường demo với email được bắt bởi Mailpit.

| Email dự kiến | Vai trò / nhân vật | Mục đích |
| --- | --- | --- |
| `student.live@demo.edufit.test` | Học sinh Nguyễn Gia Bảo, lớp 9 | Thao tác tìm gia sư, gửi kết nối, đề xuất lịch, xác nhận buổi và đánh giá |
| `parent.live@demo.edufit.test` | Phụ huynh Nguyễn Thu Hà | Liên kết Gia Bảo, trả học phí, xem báo cáo và mở khiếu nại |
| `tutor.live@demo.edufit.test` | Gia sư Trần Quang Minh, Toán THCS | Nhận request mới, thống nhất lịch, ghi nhận buổi và rút thu nhập |
| `student.empty@demo.edufit.test` | Học sinh mới | Màn hình chưa có lớp, tạo nhu cầu và hướng dẫn bắt đầu |
| `parent.empty@demo.edufit.test` | Phụ huynh mới | Màn hình chưa liên kết con, gửi lời mời từ đầu |
| `tutor.new@demo.edufit.test` | Gia sư mới chưa xác minh | Hoàn thiện hồ sơ, AI hỗ trợ Bio, nộp hồ sơ KYC |
| `student.conflict@demo.edufit.test` | Học sinh có lịch bận định sẵn | Kiểm tra trùng lịch, nhu cầu chưa hoàn chỉnh và các bước bị chặn hợp lý |

Admin tiếp tục dùng `admin.demo@edufit.vn`. Nhóm xem lịch sử tiếp tục dùng `student.demo@edufit.vn`, `parent.demo@edufit.vn`, `tutor.demo@edufit.vn`. Đề xuất mật khẩu chung cho tài khoản demo mới: `Password123@`, lưu bằng hash đúng chuẩn dự án. Các tên và thông tin trong bộ này đều là dữ liệu minh họa.

Không tạo trước tài khoản đăng ký trực tiếp trong lúc trình bày. Dùng một email mới qua Mailpit để minh họa đăng ký → OTP → đăng nhập. Tài khoản đã tồn tại sẽ làm bước đăng ký này thất bại.

## 4. Danh sách tình huống sẽ chuẩn bị

### 4.1. Đăng ký, đăng nhập và hồ sơ

| Mã | Dữ liệu / điều kiện chuẩn bị | Thao tác và điều cần cho hội đồng thấy |
| --- | --- | --- |
| AUTH-01 | Email chưa tồn tại, Mailpit hoạt động | Đăng ký, lấy OTP, xác thực, vào onboarding đúng role |
| AUTH-02 | Tài khoản đã xác thực của từng role | Đăng nhập và điều hướng đúng cổng |
| AUTH-03 | Tài khoản demo có thể nhận email trong Mailpit | Quên mật khẩu; mã sai/hết hạn là test có kiểm soát, không lưu OTP thô trong seed |
| PROF-01 | student.empty chưa hoàn thiện hồ sơ | Nhập khối lớp, khu vực, tạo nhu cầu đầu tiên |
| PROF-02 | tutor.new có thông tin chuyên môn tối thiểu | Viết Bio, sửa bản AI gợi ý, lưu hồ sơ; bản lưu giữ nguyên sau tải lại |
| PROF-03 | parent.live có liên kết và parent.empty chưa liên kết | So sánh trải nghiệm trước/sau khi có dữ liệu con |

Google OAuth phải dùng tài khoản Google thật được cho phép trong cấu hình; seed không giả lập phiên OAuth hoặc token đăng nhập.

### 4.2. Khám phá và AI ghép gia sư

Chuẩn bị khoảng **6 nhu cầu mới** phân bổ giữa các học sinh, thay vì gắn mọi case lên student.demo:

| Mã | Nhu cầu đề xuất | Kết quả mong muốn |
| --- | --- | --- |
| DISC-01 | Toán 9 luyện thi vào 10, Hà Nội, 250.000đ/buổi, online, lịch trống hợp lệ | Có ít nhất 3 gia sư đã xác minh với mức khớp khác nhau; xem giải thích điểm |
| DISC-02 | Tiếng Anh 9 nâng điểm, online, ngân sách 200.000đ, sách đã chọn | Lọc đúng môn, hướng học, giá và cấp lớp |
| DISC-03 | Ngữ Văn trực tiếp tại Đà Nẵng | Thấy ảnh hưởng khu vực và hình thức dạy |
| DISC-04 | Nhu cầu thiếu ngân sách/lịch hoặc trường bắt buộc theo mục tiêu | Có chỉ dẫn bổ sung, không coi nhu cầu thiếu là đủ điều kiện gửi |
| DISC-05 | Tổ hợp điều kiện không có gia sư phù hợp | Màn hình không có kết quả giải thích được, có đường sửa bộ lọc |
| DISC-06 | Cùng môn nhưng gia sư có mức giá/giờ không khớp | Hiện cảnh báo đúng; phân biệt lệch tiêu chí mềm với xung đột lịch đã đặt bị chặn |

Gia sư chưa xác minh không được hiển thị như gia sư đủ điều kiện nhận kết nối. Điểm AI và nội dung giải thích phải đi từ service thật hoặc chế độ stub đã công bố; không chèn điểm khớp giả vào frontend để trưng bày.

### 4.3. Liên kết phụ huynh — học sinh

| Mã | Dữ liệu chuẩn bị | Kịch bản |
| --- | --- | --- |
| FAM-01 | parent.demo đã liên kết student.demo; student.khoi còn lời mời hợp lệ | Nhận lời mời, phụ huynh chuyển giữa hai con và thấy dữ liệu riêng |
| FAM-02 | parent.empty và student.empty chưa liên kết | Gửi lời mời qua email, học sinh chấp nhận trực tiếp |
| FAM-03 | Một lời mời riêng còn chờ | Từ chối hoặc rút lời mời; xem trạng thái sau thao tác |
| FAM-04 | Một liên kết thử nghiệm không phải người trả phí các lớp chính | Hủy liên kết có xác nhận và lý do; không làm hỏng luồng payment đang trình bày |

Case hết hạn/cooldown chuẩn bị bằng dữ liệu lịch sử có thời gian nhất quán; không bắt hội đồng đợi thời gian thật.

### 4.4. Kết nối và lớp học

| Mã | Dữ liệu chuẩn bị | Kịch bản |
| --- | --- | --- |
| CON-01 | Cặp học sinh/gia sư/nhu cầu chưa có request hoặc lớp ACTIVE | Gửi yêu cầu mới, lịch riêng mặc định theo nhu cầu, gia sư xem thông tin rồi nhận |
| CON-02 | Một request PENDING còn hạn và không trùng lịch hai phía | Gia sư accept → có lớp mới với học phí từ gói dạy |
| CON-03 | Một request riêng còn chờ | Gia sư từ chối kèm lý do hoặc người gửi rút lại |
| CON-04 | student.conflict có lớp/buổi đã chốt cùng giờ | Đề xuất cho gia sư khác bị báo trùng phía học sinh; request không được lưu |
| CON-05 | Một gia sư có buổi SCHEDULED vào giờ đề xuất | Bị báo trùng phía gia sư; đổi sang giờ trống gửi được |
| CON-06 | Cặp đã có request PENDING | Gửi lại được phản hồi đúng, không tạo bản ghi trùng |
| CON-07 | Tài khoản phụ có đúng 5 request PENDING còn hạn, không làm kín tài khoản live | Minh họa giới hạn gửi chờ; hủy một request rồi gửi mới |
| CON-08 | Một lớp ACTIVE riêng không có buổi tương lai cản kết thúc | Kết thúc lớp, xem lịch sử và quyền thao tác sau kết thúc |

Các trạng thái ACCEPTED/REJECTED/CANCELLED/EXPIRED cũ đã có sẽ được giữ để xem lịch sử. Không bổ sung lớp trùng lịch một cách vô tình; dữ liệu xung đột chỉ dành cho thao tác phải bị hệ thống từ chối.

### 4.5. Lịch tuần, đổi lịch và kết quả buổi học

Đề xuất thêm **2 lớp ACTIVE có mục đích riêng và 1 lớp đã kết thúc** bên cạnh ba lớp cũ; khoảng **12–16 buổi bổ sung**, số cuối cùng chốt sau đối chiếu toàn bộ trạng thái V1.34. Lịch của từng học sinh/gia sư được kiểm tra chung để không trùng giữa các nhóm.

| Mã | Buổi được chuẩn bị | Thao tác |
| --- | --- | --- |
| SCH-01 | Khung trống trong 7 ngày tới, lớp ACTIVE có quyền truy cập | Tạo đề xuất một buổi, bên còn lại chấp nhận; lịch tuần hiện đúng tên/môn/giờ |
| SCH-02 | Hai đề xuất còn hạn riêng biệt | Chấp nhận một; từ chối hoặc rút một, xem trạng thái và thông báo |
| SCH-03 | Hai buổi SCHEDULED trong tương lai | Đề nghị đổi một buổi từ gia sư; bên học sinh/phụ huynh đồng ý hoặc từ chối |
| SCH-04 | Lớp còn trống các tuần tiếp theo | Thao tác đề xuất đổi lịch tuần trực tiếp; xác nhận các buổi sinh ra có chống trùng |
| SCH-05 | Buổi đủ điều kiện hủy sớm và buổi hủy muộn riêng | Nhập lý do, xem lịch sử, thông báo, chuyên cần và trạng thái giữ/hoàn phí đúng rule hiện tại |
| SCH-06 | Buổi đã thực sự kết thúc gần giờ demo | Gia sư ghi nhận hoàn thành; một buổi khác ghi nhận vắng có lý do |
| SCH-07 | Buổi đã hoàn thành có phản hồi và buổi đã hủy có lịch sử | Mở chi tiết để trình bày đầy đủ bằng chứng và hành động trước đó |

Không seed “đề xuất đổi lịch tuần đang chờ” vào một bảng chưa tồn tại: cần kiểm tra implementation hiện tại có lưu bền vững hay giữ trong bộ nhớ. Nếu chưa lưu bền vững, khởi tạo tình huống này qua API trước buổi trình bày và ghi rõ sẽ mất khi restart.

### 4.6. Ví, học phí, khiếu nại và rút thu nhập

Chuẩn bị số tiền dễ tính: **250.000đ/buổi Toán**, phí nền tảng **10% = 25.000đ**, gia sư nhận **225.000đ**. Buổi Anh **200.000đ** thì phí **20.000đ**, nhận **180.000đ**. Có ví phụ huynh trả và ví học sinh tự trả.

| Mã | Dữ liệu / điều kiện | Kịch bản và số tiền kỳ vọng |
| --- | --- | --- |
| PAY-01 | parent.live có lịch sử nạp demo 1.000.000đ và ledger tương ứng, ban đầu không giữ tiền | Dễ giải thích số dư trước thao tác; lịch sử phải ghi nhận là dữ liệu demo, không giả chứng từ chuyển khoản thật |
| PAY-02 | Một buổi 250.000đ chưa được cấp tiền giữ, đủ điều kiện theo service | Cấp tiền giữ: khả dụng giảm 250.000đ, đang giữ tăng 250.000đ; không trừ hai lần |
| PAY-03 | Một buổi kết thúc gần đây, REVIEW_OPEN, hai bên chưa xác nhận | Bấm xác nhận, chỉ ra hạn phản hồi 24h. Trên cơ chế hiện tại xác nhận hai bên không có nghĩa chắc chắn tất toán ngay; phải kiểm tra rule hiển thị trước khi thuyết trình |
| PAY-04 | Một buổi đã tất toán 250.000đ | Đối chiếu người trả -250.000đ, gia sư +225.000đ, hoa hồng 25.000đ và audit |
| PAY-05 | Buổi riêng đủ điều kiện tất toán tự động sau 24h, không khiếu nại | Cho job xử lý trước/đúng lúc rehearsal; có ảnh hoặc lịch sử trước/sau để không chờ 24h tại chỗ |
| PAY-06 | Một buổi còn trong hạn để mở khiếu nại | Tiền tiếp tục giữ, chưa chuyển cho gia sư; hai bên trao đổi và gửi kết quả |
| PAY-07 | Một khiếu nại hai bên đã thống nhất, chờ Admin | Admin quyết định theo kết quả: ví dụ tất toán đủ hoặc hoàn một phần, tiền phân bổ và phí đúng chính sách hiện hành |
| PAY-08 | Một hồ sơ quá 48h chưa thống nhất và một hồ sơ đã đóng | Xem cách chuyển cho admin, tiền chưa tất toán tự động; giải thích theo trạng thái hiện có của hệ thống |
| PAY-09 | tutor.live có thu nhập đã tất toán, có tài khoản nhận tiền mẫu, chưa có yêu cầu rút PENDING | Bấm rút về tài khoản mặc định; số tiền được giữ khi yêu cầu đang chờ, không tạo hai yêu cầu đồng thời |
| PAY-10 | Yêu cầu rút riêng đang chờ, và lịch sử PAID/REJECTED cũ | Admin từ chối thì hoàn số dư; với thanh toán ra ngân hàng chỉ trình bày xác nhận demo, không tự chuyển tiền thật |
| PAY-11 | Ví học sinh phụ không đủ tiền | Hệ thống chỉ rõ thiếu bao nhiêu, không cho số dư âm hoặc vẫn coi đã cấp tiền giữ |
| PAY-12 | Cấu hình payOS thật hợp lệ và tài khoản có quyền nạp | Nhập tiền → popup QR → nhận callback/webhook → ví tăng đúng một lần. Đây là bước tích hợp riêng, seed không tạo QR/link checkout hoạt động giả |

Số dư cuối không gán tay độc lập: phải bằng tổng `payment_entry`. Mọi khoản giữ, tất toán, hoàn tiền và rút cần có dòng ledger/audit đúng nguồn tham chiếu. Case timeout được chuẩn bị theo đồng hồ thật của backend; không sửa thời gian trên máy demo để ép job.

### 4.7. Lộ trình, tiến độ, phản hồi và báo cáo

| Mã | Dữ liệu chuẩn bị | Giá trị khi trình bày |
| --- | --- | --- |
| PROG-01 | Giữ lộ trình Toán 6 cột mốc của student.demo, đủ phản hồi cũ | Mục tiêu → cột mốc → tiêu chí → kết quả từng buổi → phần trăm tiến độ tính từ dữ liệu |
| PROG-02 | Lớp live chưa có lộ trình | Gia sư tạo từ đầu hoặc dùng mẫu; học sinh/phụ huynh thấy nội dung vừa lưu |
| PROG-03 | Một lớp có cột mốc chậm, bài tập chưa hoàn thành, tự đánh giá thấp | Thấy cảnh báo cần chú ý, không chỉ toàn chỉ số đẹp |
| PROG-04 | Một buổi đã hoàn thành nhưng chưa ghi phản hồi | Gia sư ghi nhận xét, giao bài, đánh giá tiêu chí; học sinh chọn cảm nhận sau buổi |
| PROG-05 | Lịch sử đủ dài 30–60 ngày, chủ đề và mức tự lập có dữ liệu | Báo cáo kỳ, năng lực theo chủ đề, dự báo; trạng thái thiếu dữ liệu phải hiện đúng ở lớp mới |
| PROG-06 | Phụ huynh đã liên kết hai con với hai lớp khác nhau | Chuyển con/lớp thấy đúng dữ liệu, không lẫn báo cáo và lời nhắn riêng |

Nội dung nhận xét cần cụ thể như “giải đúng 7/10 bài phương trình, còn sai khi đổi dấu”, thay vì lặp “học tốt” ở mọi buổi. Độ tiến bộ hợp lý, có tăng/đứng yên/giảm, không ép tất cả lên 100%.

### 4.8. Đánh giá gia sư và quản trị

| Mã | Dữ liệu chuẩn bị | Kịch bản |
| --- | --- | --- |
| REV-01 | Lớp đang ACTIVE đủ số buổi theo `ReviewService`, chưa có review cho đúng người đánh giá | Tạo đánh giá trên UI, hồ sơ công khai cập nhật điểm và số review |
| REV-02 | Review vừa tạo còn được sửa; review cũ có nội dung riêng | Sửa review hợp lệ, không cộng thêm một review mới |
| REV-03 | Lớp mới chưa đủ điều kiện | Bị chặn với lý do cụ thể. Case review sau kết thúc lớp cần kiểm chứng riêng do từng có khoảng trống ở service |
| ADM-01 | Hai hồ sơ KYC chờ độc lập có tệp mở được; một hồ sơ bị từ chối sẵn | Duyệt một, từ chối một với lý do; xem quyền nhận kết nối sau thay đổi |
| ADM-02 | Môn/chủ đề/mẫu lộ trình thử nghiệm chưa được lớp chính sử dụng | Tạo hoặc sửa danh mục mà không làm hỏng lịch sử demo |
| ADM-03 | Account phụ ACTIVE và account phụ bị khóa nếu enum/service hiện hỗ trợ | Quản lý tài khoản, giải thích quyền; không khóa tài khoản trình bày chính |
| ADM-04 | Audit có actor, hành động, đối tượng, thời gian và lý do nhất quán | Lọc theo thao tác vừa làm và mở chi tiết; không bịa audit cho hành động không tồn tại |

Ảnh/bằng cấp dùng tài liệu mẫu có chữ **DEMO**, lưu ở storage thực sự phục vụ được trên môi trường demo. Không trỏ tới đường dẫn trên máy người khác hoặc tài liệu thật chứa thông tin cá nhân.

### 4.9. Thông báo và điều hướng

Chuẩn bị notification đã đọc/chưa đọc, thông báo liên quan các con khác nhau và thông báo có đích đến còn tồn tại. Trình bày ít nhất: lời mời gia đình, request kết nối, đề xuất/xác nhận/hủy/đổi lịch, phản hồi tiến độ và trạng thái payment nếu event hiện có hỗ trợ.

Sau mỗi thao tác trực tiếp, kiểm tra thông báo được backend phát ra và bấm tới đúng bản ghi. Seed notification chỉ giúp màn hình có lịch sử; không chứng minh các event đã hoạt động. Nếu hành động nào chưa có event hoặc navigation tương ứng thì ghi thành lỗi sản phẩm riêng, không chèn notification để che thiếu sót.

## 5. Kịch bản bảo vệ đề xuất trong 12–15 phút

| Thời gian | Tài khoản | Nội dung | Dữ liệu sẵn sàng |
| --- | --- | --- | --- |
| 0–2 phút | student.demo / parent.demo | Tổng quan hành trình và kết quả sử dụng lâu dài | Lớp, lịch, ví, lộ trình, báo cáo có lịch sử |
| 2–5 phút | student.live / tutor.live | Tìm gia sư → gửi request với lịch riêng → nhận kết nối | Cặp chưa có lớp; giờ trống để gửi thành công |
| 5–7 phút | Nhóm live | Đề xuất lịch → xác nhận → thử giờ bị trùng | Buổi hợp lệ và một case xung đột được chuẩn bị riêng |
| 7–10 phút | tutor.demo / parent.demo | Buổi đã kết thúc → phản hồi → tiến độ và xác nhận học phí | Buổi đủ điều kiện thao tác; số tiền dễ đối chiếu |
| 10–12 phút | parent.demo / admin.demo | Khiếu nại → kết quả hai bên → quyết định admin | Hồ sơ đang chờ đúng bước, còn hạn |
| 12–15 phút | tutor.live / admin.demo | Thu nhập, rút về tài khoản mặc định, KYC và audit | Ví đủ tiền, yêu cầu rút/hồ sơ chờ riêng |

Đăng ký/OTP, Google, payOS, AI và các trường hợp lỗi là phần mở rộng khi hội đồng hỏi. Có thể demo payOS thật với số tiền được bạn chốt; không mặc định phát sinh chuyển khoản trong seed.

## 6. Cách bổ sung và làm mới dữ liệu dự kiến

1. Kiểm tra DB đích, `flyway_schema_history`, số dư và các tài khoản thực tế trước khi viết SQL. Không suy luận DB đã có V1.34 chỉ vì source có file.
2. Tạo branch riêng cho dữ liệu demo từ base đã chọn. Xác định các tính năng mới đang ở branch khác cần tích hợp trước để kịch bản không gọi UI/API chưa có trong build demo.
3. Giữ checksum các migration đã áp dụng, gồm V1.34. Nếu cần seed phiên bản mới, fetch rồi kiểm tra version đã được team dùng; **chưa chốt tên V1.35** trong bản đề xuất này.
4. Seed mới dùng ID có namespace xác định và quan hệ khóa ngoại đầy đủ. Kiểm tra unique, lịch trùng, enum và quyền trước khi nạp. Chỉ môi trường dev/demo nhận seed này.
5. Với dữ liệu phụ thuộc ngày giờ: chuẩn bị công cụ làm mới **riêng nhóm dữ liệu demo được quản lý**, dùng transaction và báo cáo thay đổi; mặc định giữ dữ liệu người dùng khác. Chi tiết công cụ và cách xử lý tham chiếu sẽ thiết kế khi bạn duyệt danh sách này.
6. Chạy trên bản sao DB demo trước; kiểm tra sổ cái, lộ trình, thống kê và các role. Chốt ảnh/kết quả từng bước, sau đó mới áp dụng DB demo bạn chọn.
7. Trước buổi bảo vệ, rehearsal và kiểm tra còn hạn các request, đề xuất, cửa sổ 24h/48h. Sau rehearsal, khôi phục snapshot riêng của DB demo hoặc làm mới nhóm case live. Không dùng xóa toàn bộ volume làm thao tác mặc định.

## 7. Điều kiện bàn giao bộ dữ liệu

- Bốn role đăng nhập được; tài khoản xem lịch sử và tài khoản thao tác trực tiếp có hướng dẫn riêng.
- Mỗi mã kịch bản trên có email, màn hình bắt đầu, bản ghi cần chọn, thao tác và kết quả mong đợi; mã ID kỹ thuật chỉ để trong tài liệu người trình bày.
- Không có buổi SCHEDULED trùng của cùng gia sư/học sinh; thời gian bắt đầu/kết thúc/trạng thái và lịch sử khớp nhau.
- Giá lớp, khoản giữ, tiền tất toán, hoa hồng 10%, ví và ledger cân bằng; không tạo thanh toán hoặc chứng từ ngân hàng thật bằng seed.
- Báo cáo tiến độ, điểm review và thống kê được tính từ dữ liệu chi tiết; không điền số tổng trái với nguồn.
- File KYC mở được; link trong notification tới đúng tài nguyên; dữ liệu của con này không xuất hiện ở con khác.
- Có danh sách tính năng chưa hoàn chỉnh được phát hiện khi rehearsal, tách khỏi vấn đề thiếu dữ liệu.

## 8. Những gì bạn sẽ nhận khi duyệt triển khai

1. Bộ SQL/seed bổ sung với version đã kiểm tra không trùng team.
2. Danh sách tài khoản, mật khẩu demo và vai trò sử dụng.
3. Kịch bản bấm theo từng bước cho phần bảo vệ chính và phần câu hỏi mở rộng.
4. Cách chuẩn bị/làm mới dữ liệu có thời hạn trước ngày demo.
5. Báo cáo đã kiểm thử trên database và giao diện; ghi rõ các tích hợp chưa dùng dịch vụ thật.

**Tiến độ hiện tại:** dữ liệu đã nạp và kiểm tra DB/API; dùng báo cáo `defense-data-ready.md` để chọn đúng tình huống thực tế khi rehearsal giao diện.
