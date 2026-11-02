# Plan triển khai EduFit UAT: Render + Supabase

Ngày lập: 02/11/2026. Trạng thái: đang triển khai cấu hình; chưa chạy migration hoặc deploy cloud thật.

## 1. Mục tiêu

Bổ sung quy trình triển khai theo môi trường vào tài liệu dự án; kết nối backend Java với PostgreSQL Supabase đã tạo; chuẩn bị và xác minh bản UAT chạy trên Render. Production được chuẩn bị về cấu hình và quy trình, chưa phát hành trong đợt này.

UAT dùng để team tích hợp/kiểm thử/nghiệm thu, không coi bản Free là môi trường thanh toán thật. SIT là hoạt động kiểm thử tích hợp có thể thực hiện tại UAT. SAT phải được team định nghĩa nếu sử dụng, không tự tạo thêm môi trường chỉ theo tên viết tắt.

## 2. Hiện trạng đã kiểm tra

- Local đang ở `develop`, có `compose.yaml` đã sửa provider/model AI, chưa commit.
- Có `docs/deployment/render-supabase.md` chưa commit và `tasks/system-e2e/` chưa được Git theo dõi. Giữ riêng các thay đổi này, không stage toàn bộ repository.
- Backend có Dockerfile Java 21, entrypoint, `application.yml`, `application-dev.yml`; chưa có cấu hình UAT/prod chuyên biệt.
- `dev` nạp seed demo; CI hiện có frontend quality gate và Maven verify backend.
- Frontend proxy `/api` theo `BACKEND_URL`; auth dùng cookie session và CSRF, session lưu PostgreSQL.
- Người dùng đã tạo Supabase và kết nối được DBeaver. Chưa có thông tin để xác minh backend kết nối cùng database đó.
- Chưa có `render.yaml`; chưa xác minh deployment cloud bằng tài khoản người dùng.

## 3. Phạm vi và nguyên tắc

- UAT: `develop` -> service Render UAT -> DB Supabase UAT riêng.
- Production dự kiến: phiên bản đã nghiệm thu trên `main`/tag, DB và secret riêng; chỉ phát hành khi được yêu cầu riêng.
- Thực hiện trên nhánh `deploy/deploy-render-supabase`, tạo từ develop cập nhật. Bảo toàn thay đổi local và đối chiếu trước khi đưa vào commit.
- Secret để trong file local bị ignore hoặc secret của host. File YAML/example chỉ có tên biến và placeholder.
- Không coi `.env.uat.local` là file Spring tự đọc: cần launcher/import cấu hình rõ ràng, được kiểm tra thực tế.
- Không tắt Flyway validate, không sửa migration cũ/checksum/lịch sử để vượt lỗi.
- Không tự import database local hoặc đổi hướng `.env` local đang chạy sang Supabase.
- Mỗi task có commit sau khi xác minh; push đúng nhánh làm việc khi triển khai. Tạo PR vào develop, không tự merge hoặc tự phát hành production.

## 4. Các phase

### Phase 0 — Tài liệu và xác nhận môi trường đích

**T0.1 — Bổ sung quy trình triển khai vào tài liệu**

Tạo `docs/deployment/environments-and-release.md`, liên kết từ `render-supabase.md`: phân biệt nhánh, môi trường, loại kiểm thử; mô tả DEV/UAT/PROD, PR/CI/review, nghiệm thu, deploy, theo dõi và rollback. Ghi người chịu trách nhiệm xác nhận phát hành; không mặc định mọi CI skipped là thành công đủ điều kiện.

Hoàn thành khi người đọc biết deploy từ nhánh nào, dùng DB nào, ai nghiệm thu và cách xử lý khi phát hành lỗi.

Commit: `docs(deploy): define UAT and production release workflow`

**T0.2 — Xác minh thông tin Supabase**

Lấy host, port, database, username từ DBeaver/Connect của người dùng; xác minh đúng session pooler/direct phù hợp mạng. Mật khẩu được nhập vào secret local, không gửi vào tài liệu/log. Đọc bảng hiện có, lịch sử Flyway, version PostgreSQL và extension. Xác nhận đây là DB UAT riêng. Kiểm tra Data API không công khai bảng ứng dụng; thay đổi dashboard chỉ khi có quyền truy cập tài khoản người dùng.

Hoàn thành khi phân loại được DB rỗng hay có dữ liệu. Nếu có dữ liệu, xuất backup và xác nhận cách khôi phục trước mọi migration. Ghi kết quả đã lọc secret vào verification report; nếu thông tin/tài khoản chưa có, ghi rõ chặn ở đâu.

Commit báo cáo cùng T2.2; không tạo commit chỉ chứa credentials hoặc báo cáo dự đoán.

### Phase 1 — Cấu hình môi trường và chạy local an toàn

**T1.1 — Tạo profile UAT/prod và mẫu biến**

Thêm `application-uat.yml`, `application-prod.yml` cho những khác biệt thực sự cần; giữ schema thuộc Flyway, không nạp seed demo mặc định. Cấu hình port host, forwarded headers, pool DB phù hợp và log không lộ dữ liệu. Chuẩn bị template riêng cho UAT/prod và phân loại biến bắt buộc/tùy chọn. Không vô tình override profile test đang có.

Hoàn thành khi profile production/UAT không kéo `db/seed`, key được tham chiếu từ môi trường và thiếu cấu hình bắt buộc báo lỗi có thể hiểu được. `prod` không được mô tả là đã sẵn sàng production chỉ vì tồn tại file.

Commit: `chore(config): add UAT and production configuration templates`

**T1.2 — Launcher chạy backend với Supabase UAT**

Tạo script local dùng JDK 21 và nạp cấu hình một cách tường minh; credentials trong `.env.uat.local` được Git ignore. Khi import dạng properties phải đối chiếu chính xác tên property/placeholder, không giả định mọi biến uppercase tự được bind như OS environment. Giữ nguyên cấu hình chạy database local hiện tại. Kiểm tra script không in secret, không mặc định chạy migration vào production.

Hoàn thành khi người dùng có lệnh riêng để chạy UAT, biết DB đích trước khi khởi động và tiếp tục chạy local bình thường bằng cấu hình cũ.

Commit: `chore(deploy): add a local launcher for Supabase UAT`

### Phase 2 — Kết nối và xác minh dữ liệu

**T2.1 — JDBC/Flyway trên Supabase**

Thực hiện sau T0.2, T1.1, T1.2. Kết nối TLS, pool nhỏ, user/password tách URL. Đánh giá quyền extension, timezone/startup options, advisory lock và session pooler bằng thực tế. Với DB có bảng/histories, dừng để đối chiếu nếu không tương thích; không tự repair/baseline.

Sau backup nếu cần, khởi động backend UAT: Flyway validate/migrate thành công, Hibernate validate đạt, health HTTP 200/UP. Số migration dựa trên commit và profile UAT thực tế, không yêu cầu bằng local dev.

Commit sửa cấu hình nếu có: `fix(config): align Supabase JDBC settings for UAT`

**T2.2 — Kiểm tra nghiệp vụ tối thiểu và lập báo cáo**

Tạo dữ liệu UAT được đánh dấu riêng: đăng ký/đăng nhập/đăng xuất, session sau reload, lưu và đọc hồ sơ. Đối chiếu dữ liệu trong Supabase. Chạy kiểm tra hiện có phù hợp; kiểm tra cấu hình bằng test nếu phát sinh logic launcher/config cần bảo vệ. Không chạy E2E tạo dữ liệu trên production. Ghi rõ command, commit, môi trường và kết quả thật vào `docs/deployment/verification-report.md`.

Hoàn thành khi backend local đã dùng Supabase UAT và các luồng mục tiêu đạt; không đưa password, token, cookie vào báo cáo.

Commit: `docs(deploy): record Supabase UAT verification`

### Phase 3 — Cấu hình Render và hoàn thiện hướng dẫn

**T3.1 — Khai báo service Render**

Thêm `render.yaml` nếu phù hợp việc quản lý deploy bằng Git: build context backend, Dockerfile đúng, port, health, vùng và biến runtime. Chọn compute Free cho demo theo yêu cầu, kiểm tra khả năng chạy RAM thực tế. Mặc định deploy có kiểm soát; nếu dùng auto-deploy sau CI, kiểm tra các gate bắt buộc thực sự chạy.

Database đặt ngoài Render trên Supabase, không tạo thêm Render PostgreSQL. Secret nhập qua dashboard/environment group. Không giả định thay đổi Compose tự được Render áp dụng.

Commit: `chore(deploy): configure Render backend for UAT`

**T3.2 — Cập nhật runbook thực tế**

Cập nhật `render-supabase.md` theo cấu hình đã xác minh: Supabase, Render, Vercel proxy, TLS/cookie, AI, payOS, cách bootstrap Admin, backup và xử lý lỗi. Tạo checklist trước/trong/sau deploy và rollback. Rollback image không rollback schema hoặc giao dịch bên ngoài; cần xác minh bản cũ tương thích trước khi rollback.

Commit: `docs(deploy): document UAT deployment and rollback checks`

### Phase 4 — Deploy UAT trên tài khoản người dùng

Sau khi code/config đã được kiểm thử và push đúng nhánh, có tài khoản Render/GitHub/Supabase được người dùng đăng nhập/ủy quyền. Liên kết đúng commit: service UAT có thể tạm deploy nhánh cấu hình đã duyệt để xác minh trước PR; chuyển nguồn sang develop sau khi PR thực sự được team merge. Không tự merge chỉ để deploy.

Nhập secret bằng cơ chế bảo mật của host, deploy, đọc log đã lọc secret, kiểm tra health và bộ smoke. Nếu frontend chưa deploy, xác minh backend riêng và ghi rõ kiểm thử browser xuyên Vercel còn chờ.

Kiểm tra E2E session/CSRF qua proxy, lưu dữ liệu, upload/download KYC và hành vi sau restart khi storage đã sẵn sàng. Gemini/payOS chỉ kiểm tra giao dịch/call thật khi phạm vi test đã được thống nhất, không tự phát sinh chuyển tiền/rút tiền.

Hoàn thành khi có URL UAT hoạt động, commit được deploy, báo cáo kết quả và danh sách giới hạn. Nếu thiếu tài khoản/secret/tài nguyên, ghi rõ việc còn chờ; không báo đã deploy.

Commit bằng chứng đã lọc dữ liệu: `docs(deploy): record Render UAT smoke results`

### Phase 5 — Đóng gói bàn giao và điều kiện production

Rà soát diff, chạy các gate phù hợp thay đổi và CI bắt buộc; kiểm tra không stage secrets hoặc thay đổi không thuộc phạm vi. Push nhánh và tạo PR vào develop. Production chỉ được đề xuất phát hành sau khi giải quyết các điều kiện ở mục 5 và người phụ trách nghiệm thu chấp nhận bản phát hành.

Hoàn thành khi PR review được, report phân biệt đã kiểm tra/chưa kiểm tra và người dùng có thể lặp lại cách chạy.

## 5. Điều kiện production chưa được giải quyết chỉ bằng kết nối DB

| Nội dung | Việc cần có trước vận hành thật |
| --- | --- |
| KYC file | Persistent storage/private object storage tích hợp thật; kiểm tra file còn sau restart/redeploy |
| Email | Provider HTTPS API hoặc SMTP khả dụng trên host, xác thực/TLS đầy đủ; thử gửi và link reset |
| Payment deadline | Backend luôn hoạt động hoặc trigger bên ngoài bền vững; kiểm tra xử lý sau downtime |
| Payment tiền thật | Webhook chữ ký, idempotency và không tất toán lặp khi deploy/retry; quy trình đối soát |
| Database | DB production riêng, không chứa tài khoản seed demo; backup và thử restore |
| Cấu hình/secret | Tách UAT/prod, HTTPS/cookie, quyền DB tối thiểu; secret không vào Git |
| Phát hành | Release commit/tag, nghiệm thu, theo dõi và phương án sửa tiến lên/rollback tương thích |

Các thay đổi storage/email/scheduler lớn cần task triển khai riêng sau khi chốt hướng; phase này ghi đúng giới hạn, không tuyên bố đã hoàn thành chúng.

## 6. Thông tin còn cần trước bước kết nối

1. Supabase host, port, database, username và xác nhận project dành cho UAT.
2. Mật khẩu DB qua cấu hình secret local/host, không đưa vào plan hoặc commit.
3. Xác nhận DB có dữ liệu cần giữ không, trạng thái Data API và khả năng backup.
4. Tài khoản Render/GitHub có quyền liên kết repo; URL frontend nếu đã có.

Chỉ bước phụ thuộc thông tin đó mới chờ; việc bổ sung tài liệu/cấu hình mẫu có thể làm độc lập khi bắt đầu triển khai.
