# Thiết kế lại Dashboard & Progress — Các quyết định đã chốt

> Trạng thái: **đã chốt thiết kế; Giai đoạn 1 (migration V1.28) và Giai đoạn 2 (V1.29–V1.30) và Giai đoạn 3 (V1.31–V1.32) đã triển khai; E2E Playwright cho Giai đoạn 3 chưa có**. Tài liệu ghi lại kết quả buổi "grill" về dashboard tiến độ cho phụ huynh học sinh cấp 2, gắn với goal, milestone và lộ trình do gia sư lập.

## 1. Nguyên tắc cốt lõi

Phụ huynh không muốn xem dữ liệu thô. Họ muốn thấy **sự tiến bộ (delta)** và **sự an tâm**. Dashboard theo 3 tầng:

1. **Tầng 1 — 5 giây**: 3 thẻ trả lời "con học thế nào, có đi đúng hướng tới mục tiêu không?".
2. **Tầng 2 — Năng lực thực tế**: thanh tiến bộ theo cột mốc (mức đầu vào → mức hiện tại), kèm lộ trình cột mốc.
3. **Tầng 3 — Sổ liên lạc 10 giây**: phản hồi có cấu trúc sau mỗi buổi học (quick-tap).

Quy tắc dự án giữ nguyên: **frontend không tự tính %/trạng thái tiến độ**, mọi con số lấy từ API. Không hiển thị số giả; thiếu dữ liệu thì hiện "—" kèm lý do.

## 2. Hiện trạng liên quan (đã kiểm chứng trong mã nguồn)

- `session_note`: bất biến, một dòng cho mỗi buổi, chỉ có `noteText` tự do, tạo từ listener khi ghi nhận kết quả buổi học.
- `Milestone`: `title`, `description`, `targetDate`, `weight` (1–5), `status` (`NOT_STARTED`/`IN_PROGRESS`/`ACHIEVED`), `achievedDate`, `archived`, `sortOrder`. Có `plan_history` để ghi lịch sử.
- `tutoring_session`: đã có `cancelled_by`, `cancel_reason`, `is_late_cancel`, kết quả `COMPLETED`/`ABSENT`, `outcome_recorded_by`.
- Chưa có: điểm xuất phát, chỉ số năng lực theo thời gian, liên kết buổi học ↔ cột mốc, tiêu chí đạt.

## 3. Quyết định đã chốt

### 3.1 Người đọc chính (Q1)
Phụ huynh là người đọc chính. Học sinh xem bản rút gọn, giọng động viên. Gia sư có màn "việc cần làm" riêng.

### 3.2 Trạng thái cột mốc (Q7, Q15)
Giữ **3 trạng thái**: `NOT_STARTED` → `IN_PROGRESS` → `ACHIEVED`.
- `IN_PROGRESS` chuyển **tự động** khi buổi học đầu tiên được gắn vào cột mốc.
- `ACHIEVED` chỉ gia sư chốt, và chỉ khi đã tick đủ tiêu chí đạt.
- Cho phép `ACHIEVED → IN_PROGRESS` khi phát hiện con chưa vững: bắt buộc ghi lý do ngắn, lưu `plan_history`, giữ `achievedDate` cũ trong lịch sử, phụ huynh vẫn xem được.
- Không thêm trạng thái "cần ôn lại". Việc ôn lại thể hiện qua mức tự lập tụt xuống và chip trong phản hồi.
- Cột mốc không còn phù hợp dùng `archived`.

### 3.3 Bằng chứng tiến bộ (Q2, Q8, Q12)
Bỏ thang số 1–4 vì không có neo. Thay bằng:
- **Thang mức độ tự lập** (4 mức, mô tả bằng hành vi nhìn thấy được), gắn với từng cột mốc được dạy trong buổi:
  1. Chưa làm được
  2. Làm được khi có gợi ý
  3. Tự làm đúng
  4. Tự làm đúng cả dạng khó và giải thích lại được
- **Tiêu chí đạt**: gia sư viết 1–4 gạch đầu dòng cho mỗi cột mốc. Cột mốc chỉ được chốt khi tick đủ.
- **"Đúng x/y bài"**: nhập tùy chọn để có con số khách quan.
- **Một thang duy nhất**: bỏ emoji "mức hiểu bài theo buổi" ở biểu mẫu phản hồi. Delta hiển thị bằng chữ, ví dụ "từ *làm được khi có gợi ý* → *tự làm đúng*".
- **Điểm xuất phát** = lần chấm đầu của từng cột mốc (buổi đầu hoặc lúc lập lộ trình).

### 3.4 Độ chi tiết lộ trình (Q9)
- Không dùng khóa học định sẵn, không chi tiết đến từng buổi.
- Có **mẫu lộ trình theo môn × khối × mục tiêu** (ví dụ Toán 9 ôn thi vào 10): gia sư nhân bản và sửa, lưu được mẫu riêng. Admin quản lý mẫu hệ thống.
- Mỗi lộ trình **3–8 cột mốc**, mỗi cột mốc khoảng **2–6 buổi**. Độ mịn hơn nằm trong danh sách tiêu chí.
- Cột mốc cuối **bắt buộc có `targetDate`**; các mốc giữa tùy chọn, hệ thống nội suy.
- Bản nháp lộ trình bằng AI để giai đoạn sau.

### 3.5 Liên kết cột mốc với lịch học (Q10)
Bảng liên kết buổi học ↔ cột mốc, hai loại:
- **Dự kiến**: tùy chọn, gắn khi xếp lịch; lịch hiển thị tên cột mốc.
- **Thực tế**: bắt buộc, chọn khi phản hồi sau buổi (mặc định cột mốc đang học, tốn 1 chạm).
- Buổi đã diễn ra thì "thực tế" luôn thắng "dự kiến".

### 3.6 Biểu mẫu phản hồi 10 giây (Q3, Q4, Q12, Q18)
Gồm:
1. Mức tự lập cho từng cột mốc được dạy (bắt buộc).
2. Chip việc về nhà: có / không / đã giao (bắt buộc).
3. Chip thái độ, nỗ lực (tùy chọn, chỉ lời mang tính xây dựng).
4. Một câu nhận xét tối đa 140 ký tự (tùy chọn, dùng lại `noteText`).
5. Lời nhắn riêng cho phụ huynh (tùy chọn, xem 3.12).

Vận hành:
- Nhúng vào bước "Kết thúc buổi học" để không thành việc riêng; ưu tiên điện thoại.
- Bấm "để sau" thì nhắc lúc +12h và +24h; sau 48h đánh dấu "bổ sung muộn".
- Gia sư được sửa trong 24h (có dấu "đã chỉnh sửa").
- Tỉ lệ phản hồi đầy đủ hiện trên hồ sơ gia sư và cho admin. **Không khóa** tiền rút của gia sư.

### 3.7 Thẻ 1 — "Có đúng hướng không?" (Q6, Q11)
Tính ở backend, theo **ngày trễ so với `targetDate`** (không theo % trọng số), có ân hạn 7 ngày:

| Trạng thái | Điều kiện |
|---|---|
| Vượt tiến độ | Cột mốc kế tiếp đã đạt trước hạn |
| Đúng tiến độ | Không có cột mốc nào quá hạn mà chưa đạt |
| Cần chú ý | Có cột mốc quá hạn 1–7 ngày |
| Chậm | Có cột mốc quá hạn hơn 7 ngày, hoặc từ 2 cột mốc quá hạn |
| Chưa đủ dữ liệu | Lộ trình không có mốc ngày nào |

Luôn kèm **câu giải thích** ("Cột mốc *Hệ phương trình* trễ 2 ngày so với kế hoạch"). Nếu trễ do nghỉ buổi, ghi rõ nguyên nhân.

### 3.8 Thẻ 2 — "Tiến độ mục tiêu chính" (Q13, Q16, Q17)
Bố cục:
- **Tiêu đề**: tên mục tiêu chính (từ nhu cầu học).
- **Số lớn**: % tiến độ có trọng số, do backend tính.
- **Chip thay đổi**: so với 30 ngày trước ("+20%", "Không đổi", "—").
- **Dòng phụ**: bước tiến cụ thể gần nhất.

Cách tính:
- **% tiến độ** = Σ(trọng số × tỉ lệ hoàn thành cột mốc) / Σ trọng số. Cột mốc `ACHIEVED` tính 100%; cột mốc đang học tính **số tiêu chí đã tick / tổng tiêu chí**.
- **30 ngày cuốn chiếu**: cửa sổ luôn là "30 ngày gần nhất tính đến hôm nay" (02/11 → 03/10–02/11), không theo tháng lịch, để thẻ không trống đầu tháng.
- **% 30 ngày trước** tính lại từ `achievedDate` và `plan_history`. Nếu lộ trình đã bị điều chỉnh trong kỳ, ghi chú "Lộ trình đã được điều chỉnh".
- **Delta mỗi cột mốc** = mức chấm gần nhất − mức ngay trước cửa sổ (hoặc điểm xuất phát), cần ít nhất 2 lần chấm ở 2 buổi khác nhau. Dòng phụ hiển thị cột mốc tăng nhiều nhất (hòa thì lấy gần nhất).
- **Chuỗi dự phòng cho dòng phụ**: (1) cột mốc tăng mức; (2) cột mốc vừa đạt; (3) tiêu chí vừa tick; (4) "Chưa có thay đổi mức trong 30 ngày. Đang tập trung: X" kèm nhận xét mới nhất; (5) "Đang thiết lập điểm xuất phát".
- **Khi tụt mức**: hiện ở dòng phụ, nhãn "Cần củng cố: X", màu trung tính (không đỏ).

### 3.9 Thẻ 3 — "Chuyên cần & buổi tới" (Q14)
- **Tỉ lệ chuyên cần của con** = `COMPLETED / (COMPLETED + ABSENT + hủy muộn do phía học sinh/phụ huynh)`.
- Hủy sớm hợp lệ (từ 12 giờ trở lên) và hủy do gia sư **không trừ** chuyên cần của con; hiện riêng ở dòng phụ ("1 buổi dời do gia sư") để minh bạch với phụ huynh.
- Bố cục: dòng chính "Đi học 7/8 buổi"; dòng phụ "Buổi tới: Thứ 5 18:00 · học *Hệ phương trình*"; huy hiệu "Có bài về nhà" nếu buổi trước có giao. Chi tiết (lịch sử hủy, vắng) nằm trong ngăn kéo.
- Phân biệt phía hủy dựa trên `cancelled_by` (lưu user id); cần kiểm tra cách so khớp với gia sư/học sinh khi triển khai.

### 3.10 Trạng thái trống theo giai đoạn của lớp (Q22)
Thẻ không bao giờ hiện số giả; thiếu thì hiện "—" kèm lý do.

| Giai đoạn | Thẻ 1 | Thẻ 2 | Thẻ 3 |
|---|---|---|---|
| S0: chưa có lộ trình | "Gia sư chưa lập lộ trình" | Tên mục tiêu, % là "—" | Bình thường |
| S1: có lộ trình, chưa học buổi nào | "Chưa đến hạn cột mốc đầu" | 0%, dòng phụ "Buổi đầu: đánh giá điểm xuất phát" | Bình thường |
| S2: 1–2 buổi, chưa đủ 2 lần chấm | Tính nếu có hạn | % hiện, chip "—", "Đang thiết lập điểm xuất phát" | Bình thường |
| S3: đủ dữ liệu | Đầy đủ | Đầy đủ | Đầy đủ |

### 3.11 Độ tin cậy dữ liệu do gia sư chấm (Q19)
Làm ngay:
1. Tăng từ 2 mức trở lên trong một buổi thì bắt buộc nhập "đúng x/y bài" hoặc một câu bằng chứng.
2. Không được chấm mức 4 khi chưa tick đủ tiêu chí.
3. Học sinh có tùy chọn bấm 1 chạm cảm nhận sau buổi (3 biểu tượng); hiện cho phụ huynh dạng xu hướng, không gắn với điểm của gia sư.

Hoãn: cảnh báo admin khi một gia sư luôn chấm mức cao nhất.

### 3.12 Quyền riêng tư giữa học sinh và phụ huynh (Q20)
- Cột mốc, mức tự lập và chip hiện cho cả học sinh và phụ huynh.
- Chip thái độ chỉ gồm lời xây dựng (ví dụ "Cần tập trung hơn"), không có nhãn tiêu cực.
- Ô **"Lời nhắn riêng cho phụ huynh"** (tùy chọn) chỉ phụ huynh thấy.

### 3.13 Báo cáo kỳ (Q21)
Tạo tự động mỗi 30 ngày kể từ ngày bắt đầu lớp (khớp cửa sổ cuốn chiếu): ảnh chụp 3 thẻ, cột mốc đạt trong kỳ, bước tiến lớn nhất, trọng tâm kỳ tới, một câu tổng kết tùy chọn của gia sư. Thông báo cho phụ huynh; tự gửi sau 48h kể cả khi gia sư không viết thêm.

### 3.14 Màn hình gia sư (Q23)
- Ngăn **"Việc cần làm"** trên dashboard gia sư: buổi chưa phản hồi; lớp chưa có lộ trình; cột mốc quá hạn; lộ trình thiếu ngày cuối; báo cáo kỳ chờ gửi.
- Biểu mẫu 10 giây ưu tiên điện thoại: mỗi cột mốc được dạy là một hàng với 4 nút mức tự lập; bên dưới là chip thái độ, chip việc về nhà và 2 ô tùy chọn.

### 3.15 Nhiều con, nhiều lớp (Q24)
- Bộ chọn con ở trên cùng; tab lớp (môn) bên dưới. "Mục tiêu chính" của thẻ 2 là mục tiêu của lớp đang chọn.
- Lớp mặc định = lớp có buổi sắp tới gần nhất.
- Thẻ 1 phía trên bộ chọn con hiển thị trạng thái **xấu nhất** trong các lớp của con, kèm tên lớp.

### 3.16 Dữ liệu và ranh giới module (Q25, Q26)
- Toàn bộ bảng mới nằm trong module `progress`: tiêu chí cột mốc, điểm chấm cột mốc, phản hồi buổi học, liên kết buổi ↔ cột mốc (dự kiến/thực tế), mẫu lộ trình, báo cáo kỳ. Module `scheduling` không biết cột mốc; UI lịch gọi API của `progress`.
- Điểm chấm lưu dạng **ghi nối tiếp** (sửa thì thêm dòng mới, giữ lịch sử). `session_note` cũ giữ làm ô nhận xét tùy chọn.
- **Dữ liệu cũ**: cột mốc `ACHIEVED` vẫn tính 100%; cột mốc chưa có tiêu chí tính 0% cho đến khi đạt (đúng hành vi hiện tại). Chỉ **bắt buộc tiêu chí** với cột mốc tạo mới hoặc được sửa sau này. Không backfill dữ liệu giả.

## 4. Giai đoạn triển khai (Q27)

| Giai đoạn | Nội dung |
|---|---|
| **1 — Nền tảng** | Tiêu chí cột mốc; điểm chấm; biểu mẫu 10 giây gắn "thực tế" vào cột mốc; 3 thẻ (đúng hướng, tiến độ mục tiêu chính với % một phần, chuyên cần) kèm trạng thái trống |
| **2** | Liên kết "dự kiến" trên lịch; mẫu lộ trình; ngăn "Việc cần làm"; nhắc phản hồi; 1 chạm của học sinh; chống chấm nhảy mức; lời nhắn riêng cho phụ huynh |
| **3** | Báo cáo kỳ; dự báo "còn X buổi cho Y cột mốc"; danh mục chủ đề chuẩn kèm radar; bản nháp lộ trình bằng AI |

Mỗi giai đoạn phải qua các cổng kiểm tra tự động của dự án (AGENTS.md): Maven/JUnit 5, ArchUnit, Testcontainers, Vitest, `tsc`, và E2E Playwright dùng dữ liệu kiểm thử tập trung (`frontend/e2e/resources`, `test-constants.ts`).

## 5. Ngoài phạm vi hiện tại

- Điểm số bài kiểm tra do gia sư nhập (trước/sau).
- Khóa tiền rút của gia sư khi thiếu phản hồi.
- Khóa học định sẵn.

## 6. Ghi chú triển khai Giai đoạn 3

- **Dự báo** (`ForecastPolicy`): khi đã đạt ít nhất một cột mốc thì dùng nhịp thực tế (số buổi trung bình đã dùng cho một cột mốc đạt, làm tròn lên); chưa đạt cột mốc nào thì dùng số buổi gợi ý 2–6 của gia sư, thiếu gợi ý ở bất kỳ cột mốc còn lại nào thì hiện "—" kèm lý do. Mỗi cột mốc còn lại trừ số buổi đã học cho nó (tối thiểu 1). Ngày dự kiến xong chỉ hiện khi lớp học đều trong 28 ngày gần nhất.
- **Danh mục chủ đề** (`topic_catalog`): admin quản lý tại `/admin/topics`; hệ thống không tự có chủ đề nào. Chủ đề đã dùng chỉ được ngừng sử dụng, không xóa. Radar cần điểm chấm ở ít nhất 3 chủ đề, ít hơn thì hiện lý do.
- **Bản nháp AI** (`POST /api/v1/progress/plans/draft`): chỉ gửi môn, khối, mục tiêu, chương trình, bộ sách và tên chủ đề — không gửi dữ liệu cá nhân. 10 lượt/giờ/gia sư. AI lỗi, hết lượt hoặc trả sai hình dạng thì báo lý do và gợi ý dùng mẫu. Bản nháp không có ngày; gia sư phải đặt hạn cột mốc cuối.
- **Báo cáo kỳ** (`period_report`): job tạo bản nháp khi kỳ 30 ngày kết thúc, gia sư viết câu tổng kết trong ngăn "Việc cần làm" và gửi ngay, không thì tự gửi sau 48 giờ cho phụ huynh và học sinh. Học sinh/phụ huynh chỉ thấy bản đã gửi.

## 7. Việc tồn đọng từ đợt dọn dữ liệu giả

- Backend `ProgressDashboardService` vẫn dựng tiêu đề kiểu `"Mục tiêu " + goalType`; nên sửa tại nguồn.
- `ConnectionRequestDialog` còn hardcode `currentLevel = 1`, `desiredLevel = 3`; cần ô nhập thật.
- Chưa chạy E2E Playwright cho các dashboard.
