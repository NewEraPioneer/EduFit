# Script thuyết trình EduFit

Thời lượng ước tính: 18–22 phút (chưa tính demo). Lời thoại in thường, ghi chú cho người nói in nghiêng trong ngoặc. Số slide theo thứ tự trong bản Canva, có thể lệch 1 nếu bạn thêm/bớt trang.

---

## Phần 1. Bài toán và đối tượng

**Slide 1. EduFit** (~20 giây)
Xin chào thầy/cô và các bạn. Nhóm em xin trình bày đề tài EduFit, hệ thống kết nối gia sư và học sinh, đồng thời hỗ trợ theo dõi tiến độ và lập kế hoạch học tập.

**Slide 2. Nội dung trình bày** (~20 giây)
Bài trình bày gồm năm phần: bài toán và đối tượng, phạm vi và use case, kiến trúc và thiết kế hệ thống, các luồng nghiệp vụ chính, cuối cùng là demo và kết quả.

**Slide 3. Vấn đề và động lực** (~1 phút)
Thị trường gia sư hiện nay thiếu minh bạch. Phụ huynh thường tìm gia sư qua truyền miệng nên khó kiểm chứng chất lượng. Sau khi con học, phụ huynh cũng khó biết con tiến bộ đến đâu. Lịch học trao đổi qua tin nhắn nên dễ trùng, và thanh toán bằng tiền mặt thì thiếu minh bạch.
Nhìn sơ đồ bên phải: cột trái là cách làm hiện tại, cột phải là cách EduFit giải quyết từng điểm. Gia sư được xác minh, việc ghép cặp có điểm số, lịch học chống trùng, và có ví nội bộ để thanh toán.

**Slide 4. Đối tượng sử dụng** (~1 phút)
Hệ thống có bốn vai trò. Student tìm gia sư và học theo lộ trình. Parent liên kết với con để theo dõi tiến độ. Tutor lập hồ sơ, nhận lớp và ghi kết quả từng buổi học. Admin duyệt hồ sơ gia sư, xử lý tranh chấp và cấu hình hệ thống.
Ngoài ra có hai hệ thống bên ngoài: Gemini AI hỗ trợ các tính năng AI, và payOS xử lý thanh toán.

---

## Phần 2. Phạm vi và use case

**Slide 5. Phạm vi và yêu cầu chức năng** (~1 phút)
Hệ thống có 38 use case và 62 quy tắc nghiệp vụ, chia thành sáu nhóm: xác thực và hồ sơ; xác minh gia sư; tìm kiếm, ghép cặp và kết nối; lịch học, lộ trình và tiến độ; đánh giá và thông báo; ví nội bộ và thanh toán payOS.
Về ngoài phạm vi, nhóm chủ động không làm video call và chat thời gian thực trong phiên bản này, để tập trung vào luồng nghiệp vụ cốt lõi.

**Slide 6. Use case tổng quan** (~45 giây)
*(Chỉ vào sơ đồ SRS.)* Đây là sơ đồ tổng quan: bốn tác nhân là Student, Parent, Tutor, Admin, cùng hai hệ thống ngoài là Gemini AI và payOS. Các slide tiếp theo đi vào từng nhóm.

**Slide 7. Use case xác thực** (~30 giây)
Nhóm xác thực gồm đăng ký, đăng nhập. Nhóm quản lý hồ sơ phục vụ ba vai trò Student, Parent, Tutor.

**Slide 8. Hồ sơ và xác minh gia sư** (~40 giây)
Gia sư gửi hồ sơ kèm minh chứng, Admin duyệt hoặc từ chối. Quy tắc quan trọng: chỉ gia sư có trạng thái VERIFIED mới được nhận yêu cầu học. Đây là cơ chế bảo đảm chất lượng đầu vào.

**Slide 9. Tìm kiếm và ghép cặp** (~50 giây)
Học sinh tìm và lọc trong số gia sư đã xác minh. Điểm ghép được tính từ ba yếu tố: 40% mức phù hợp lịch, 30% đánh giá, 30% ngân sách. Gia sư mới chưa có đánh giá được gán điểm mặc định 50 để không bị thiệt thòi.

**Slide 10. Liên kết và kết nối** (~50 giây)
Lời mời liên kết Parent – Student hết hạn sau 14 ngày. Yêu cầu học gửi đến gia sư hết hạn sau 7 ngày. Khi gia sư chấp nhận, hệ thống tạo lớp ở trạng thái ACTIVE.

**Slide 11. Lịch học và buổi học** (~50 giây)
Một bên đề xuất buổi học, thời điểm bắt đầu cách hiện tại ít nhất 2 giờ, dài từ 30 đến 240 phút. Chỉ khi hai bên đồng thuận buổi học mới thành SCHEDULED. Sau buổi học, gia sư ghi kết quả là COMPLETED hoặc ABSENT.

**Slide 12. Lộ trình và tiến độ** (~45 giây)
Gia sư tạo lộ trình gồm các milestone có trọng số từ 1 đến 5, và ghi chú mỗi buổi gồm nội dung, bài về nhà, bước tiếp theo. Tiến độ phần trăm tính theo trọng số các milestone đã ACHIEVED.

**Slide 13. Đánh giá và thông báo** (~40 giây)
Đánh giá từ 1 đến 5 sao, nội dung 10 đến 1000 ký tự, và chỉ được gửi sau buổi học COMPLETED. Thông báo hiển thị trong ứng dụng và được lưu 90 ngày.

---

## Phần 3. Kiến trúc và thiết kế hệ thống

**Slide 14. Ngữ cảnh hệ thống** (~40 giây)
*(Chỉ vào sơ đồ ngữ cảnh.)* Người dùng gồm bốn vai trò. Hệ thống tích hợp hai dịch vụ ngoài là Gemini AI và payOS. Email được xử lý như một module nội bộ chứ không phải hệ thống ngoài.

**Slide 15. Kiến trúc Modular Monolith** (~1 phút 15 giây)
Hệ thống là một ứng dụng Spring Boot dùng một cơ sở dữ liệu PostgreSQL, nhưng bên trong chia thành 10 module nghiệp vụ và 4 module nền tảng. Mỗi module chia bốn lớp: api, application, domain, infra.
Theo ADR-002, nhóm chọn monolith module hóa thay vì microservices vì đội nhỏ, cần triển khai và vận hành đơn giản, nhưng vẫn giữ ranh giới rõ để sau này có thể tách dịch vụ.

**Slide 16. Ranh giới module và giao tiếp** (~1 phút)
Các module chỉ gọi nhau qua Facade trong gói api, và dùng sự kiện miền cho giao tiếp nội bộ. Mỗi module sở hữu bảng dữ liệu riêng. Ranh giới này không chỉ là quy ước: ArchUnit kiểm tra tự động trong CI, vi phạm thì build thất bại.

**Slide 17. Cấu trúc gói mã nguồn** (~30 giây)
backend/modules chứa các module, backend/app ghép chúng thành một ứng dụng, frontend là Next.js. Sơ đồ là Code Packages trong SRS.

**Slide 18. Công nghệ và lý do lựa chọn** (~1 phút 15 giây)
Backend dùng Java 21, Spring Boot 4.1.1, Hibernate 7 và Flyway quản lý migration. Frontend dùng Next.js 16, React 19, Tailwind CSS 4. Dữ liệu trên PostgreSQL 17, tận dụng ràng buộc loại trừ để chống trùng lịch ngay ở tầng dữ liệu. Gemini đi qua AiGateway, thanh toán qua payOS. Kiểm thử gồm JUnit, Testcontainers, ArchUnit, Vitest và Selenium. Tiêu chí chung là công nghệ phổ biến, phù hợp quy mô đội nhỏ.

**Slide 19–23. Luồng màn hình 1–5** (~25 giây mỗi slide, tổng ~2 phút)
*(Chỉ lướt, nêu tên người dùng và đích đến của từng luồng; tránh đọc chi tiết từng màn hình.)* Mỗi sơ đồ mô tả hành trình người dùng qua các màn hình: từ đăng nhập, tới tìm gia sư, quản lý lớp, lịch học và ví. Chi tiết nằm trong SRS.

**Slide 24–25. Thiết kế CSDL** (~1 phút)
Sơ đồ ERD chia hai phần. Các bảng có khóa chính, khóa ngoại rõ ràng. Điểm đáng chú ý ở phần hai: ràng buộc trạng thái và ràng buộc loại trừ để không có hai buổi SCHEDULED chồng giờ của cùng một gia sư hoặc học sinh.

**Slide 26. Bảo mật và tích hợp** (~1 phút)
Parent chỉ xem được dữ liệu khi liên kết còn CONFIRMED. Mọi lời gọi AI đều đi qua AiGateway, có giới hạn và cơ chế dự phòng khi dịch vụ lỗi. Dữ liệu trẻ em được hạn chế tối đa. Các thao tác nhạy cảm đều được ghi nhật ký kiểm toán.

---

## Phần 4. Luồng nghiệp vụ chính

**Slide 27. Luồng 1: Đăng ký và xác minh gia sư** (~40 giây)
Gia sư gửi hồ sơ, Admin duyệt hoặc từ chối. Chỉ hồ sơ VERIFIED mới được hiển thị cho học sinh.

**Slide 28. Luồng 2: Tìm và ghép gia sư** (~40 giây)
Hệ thống lọc theo môn, lịch, ngân sách rồi chấm điểm ghép 40/30/30 và trả danh sách gợi ý theo điểm từ cao xuống thấp.

**Slide 29. Luồng 3: Kết nối và tạo lớp** (~50 giây)
Trước hết là liên kết Parent – Student, hết hạn sau 14 ngày nếu không phản hồi. Sau đó học sinh, hoặc phụ huynh thay mặt, gửi yêu cầu học tới gia sư VERIFIED. Gia sư chấp nhận thì hệ thống kiểm tra lại khung giờ và tạo lớp ACTIVE trong một giao dịch duy nhất, rồi thông báo cho học sinh và phụ huynh.

**Slide 30. Luồng 4: Đặt lịch và chống trùng lịch** (~1 phút)
Đây là luồng có máy trạng thái. Buổi học bắt đầu ở PROPOSED, bên còn lại chấp nhận thì thành SCHEDULED sau khi kiểm tra trùng lịch hai phía. Các nhánh kết thúc gồm REJECTED, WITHDRAWN, EXPIRED. Sau giờ học là COMPLETED, ABSENT hoặc CANCELLED. Chốt chặn cuối cùng là ràng buộc loại trừ của PostgreSQL, nên dù code ứng dụng có lỗi cũng không thể có hai buổi chồng giờ.

**Slide 31. Luồng 5: Lộ trình và tiến độ** (~50 giây)
Sau mỗi buổi, gia sư ghi kết quả và ghi chú. Milestone đạt tiêu chí thì chuyển ACHIEVED, tiến độ được tính lại theo trọng số và dashboard cập nhật cho Student và Parent. Nếu thiếu dữ liệu, dashboard báo chưa có, không hiển thị số 0 hay ước lượng sai.

**Slide 32. Luồng 6: Đánh giá và thông báo** (~40 giây)
Chỉ được đánh giá khi đã có ít nhất một buổi COMPLETED với gia sư đó, mỗi cặp Student – gia sư một đánh giá. Khi lưu, hệ thống phát sự kiện để thông báo cho gia sư. Phụ huynh chỉ nhận thông báo khi liên kết còn CONFIRMED.

**Slide 33. Luồng 7: Ví nội bộ và thanh toán** (~1 phút 15 giây)
Học sinh hoặc phụ huynh nạp ví qua payOS bằng mã QR. Hệ thống đối soát với yêu cầu đã lưu rồi mới cộng số dư. Khi cấp tiền cho một buổi học, tiền chuyển từ available sang held. Sau buổi học có 24 giờ phản hồi; nếu không có vấn đề thì tất toán cho gia sư và ghi hoa hồng. Nếu có tranh chấp, mở trong 48 giờ, tiền vẫn được giữ và Admin quyết định tất toán hay hoàn lại. Gia sư yêu cầu rút tiền, hệ thống giữ tiền ngay lúc yêu cầu và Admin duyệt chi. Lưu ý: ví nội bộ chỉ là số dư trong cơ sở dữ liệu, tiền thật nằm ở ngân hàng.

---

## Phần 5. Demo và kết quả

**Slide 34. Demo** (~5–7 phút)
Em xin chuyển sang demo theo sáu bước:
1. Đăng ký gia sư, gửi hồ sơ, Admin duyệt.
2. Học sinh tìm và ghép gia sư, xem điểm ghép.
3. Kết nối với gia sư và tạo lớp.
4. Đề xuất, chấp nhận buổi học; gia sư ghi kết quả. *(Thử đề xuất một buổi trùng giờ để cho thấy hệ thống từ chối.)*
5. Xem tiến độ và gửi đánh giá.
6. Nạp ví, cấp tiền cho buổi học, xem trạng thái tất toán.

*(Chuẩn bị sẵn tài khoản Student, Parent, Tutor, Admin và dữ liệu mẫu trước giờ trình bày; nếu mạng hoặc payOS lỗi, dùng video quay sẵn cho bước 6.)*

**Slide 35. Kết quả và hướng phát triển** (~45 giây)
Nhóm đã hoàn thành các nhóm use case đề ra. Hạn chế hiện tại là chưa có chat và video call. Hướng phát triển là bổ sung hai tính năng này và gợi ý lộ trình học bằng AI sâu hơn.

**Slide 36. Kết thúc** (~15 giây)
Em xin cảm ơn thầy/cô và các bạn đã lắng nghe. Nhóm sẵn sàng trả lời câu hỏi.

---

## Câu hỏi có thể gặp

- **Vì sao chọn monolith thay vì microservices?** Đội nhỏ, một CSDL, giao dịch xuyên module (ví dụ tạo lớp) cần nhất quán; ranh giới module được ArchUnit bảo vệ nên vẫn tách được sau này (ADR-002).
- **Chống trùng lịch ở đâu?** Hai lớp: kiểm tra ứng dụng khi chấp nhận, và ràng buộc loại trừ PostgreSQL làm chốt chặn cuối.
- **Tiền thật nằm ở đâu?** Ở ngân hàng qua payOS; ví nội bộ chỉ là số dư trong CSDL, nạp chỉ cộng sau khi đối soát.
- **Điểm ghép tính thế nào?** 40% lịch, 30% đánh giá, 30% ngân sách; gia sư mới có điểm đánh giá mặc định 50.
- **Nếu Gemini lỗi?** AI chỉ đi qua AiGateway, có giới hạn và cơ chế dự phòng nên các luồng chính không phụ thuộc AI.
- **Vì sao không làm chat/video call?** Ngoài phạm vi phiên bản này để tập trung vào luồng nghiệp vụ cốt lõi; nằm trong hướng phát triển.
