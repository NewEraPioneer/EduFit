# Deploy backend EduFit lên Render và PostgreSQL lên Supabase

Cập nhật: 02/11/2026. Hướng dẫn dành cho repository EduFit hiện tại: Java 21, Spring Boot, Maven nhiều module, PostgreSQL và Flyway. Phương án Free dùng để demo/thử nghiệm; chưa bảo đảm vận hành thanh toán đúng hạn khi service ngủ.

## 1. Bạn sẽ deploy những gì?

| Thành phần | Nơi chạy |
| --- | --- |
| Backend Java | Render Web Service, Docker runtime |
| Database PostgreSQL | Supabase Free |
| Frontend | Có thể dùng Vercel; không thuộc bước deploy backend này |
| Đăng nhập, phân quyền, session | Backend EduFit hiện tại; không chuyển sang Supabase Auth |
| Bằng cấp KYC | Code hiện lưu trên filesystem backend; Supabase Storage **chưa được tích hợp** |

```mermaid
flowchart LR
    User[Trình duyệt] --> FE[Frontend Vercel]
    FE -->|Proxy /api qua HTTPS| BE[Backend Render]
    BE -->|JDBC + TLS, session pooler| DB[(Supabase PostgreSQL)]
    PayOS[payOS] -->|Webhook HTTPS| BE
```

**Không cần mua tên miền để thử:** Render cấp địa chỉ `https://<ten-service>.onrender.com`.

> [!NOTE]
> Quy trình phân định môi trường (DEV/UAT/PROD), kiểm thử, phê duyệt phát hành và chiến lược rollback chi tiết được mô tả tại [environments-and-release.md](environments-and-release.md).

Hướng dẫn chưa được kiểm chứng bằng một lần deploy trên tài khoản cloud của bạn. Host DB, quyền extension, RAM và các tích hợp cần kiểm tra theo các bước bên dưới.

## 2. Chuẩn bị tài khoản và mã nguồn

1. Đăng ký [Render](https://dashboard.render.com/) và [Supabase](https://supabase.com/dashboard).
2. Cho Render quyền đọc repository GitHub `dvtrug/EduFit`.
3. Chọn commit đã review để deploy. Với bản demo của team có thể chọn `develop`; môi trường thật nên dùng nhánh/release ổn định.
4. Chuẩn bị URL frontend nếu đã deploy, ví dụ `https://<frontend>.vercel.app`.
5. Không upload `.env` local lên GitHub. Nhập secret trong phần Environment của Render.

Render build trực tiếp từ GitHub, nên thay đổi chỉ nằm trên máy bạn chưa có hiệu lực trên cloud. Docker runtime cũng không tự chạy `compose.yaml` hay đọc `.env` trên máy bạn.

## 3. Tạo database Supabase

1. Trong Supabase chọn **New project** và chọn tổ chức của bạn.
2. Đặt tên, ví dụ `edufit-demo`.
3. Tạo mật khẩu database mạnh, lưu trong trình quản lý mật khẩu.
4. Chọn Singapore nếu có; nên cùng khu vực backend.
5. Chọn gói **Free**, đợi project sẵn sàng.

Mật khẩu database khác mật khẩu đăng nhập tài khoản Supabase. Backend cần mật khẩu database, không cần `anon key` hay `service_role key` để dùng JDBC.

### 3.1. Giữ database chỉ truy cập qua backend

Tạo project Supabase riêng cho EduFit. Bản hướng dẫn dùng schema `public` để tương thích cấu hình hiện tại, không dùng chung database với ứng dụng khác.

Trong phần cấu hình **Data API**, tắt Data API nếu bạn chỉ dùng JDBC cho backend như hướng dẫn này. Không cấp frontend quyền truy cập trực tiếp bảng tài khoản, ví hay session. Việc tắt Data API không tắt kết nối PostgreSQL/JDBC.

Nếu sau này cần dùng Supabase REST/GraphQL cho tính năng khác, thiết kế lại quyền/schema/RLS trước khi bật. Không chỉ bật Data API cho toàn bộ bảng EduFit.

Nguồn: [Supabase — Securing your API](https://supabase.com/docs/guides/api/securing-your-api).

### 3.2. Lấy thông tin kết nối đúng

Trong dashboard mở **Connect**, chọn **Session pooler**. Sao chép đúng host, user, port và database mà Supabase hiển thị.

| Trường | Dạng thường gặp, chỉ minh họa |
| --- | --- |
| Host | `aws-<index>-<region>.pooler.supabase.com` |
| Port | `5432` |
| Database | `postgres` |
| User | `postgres.<project-ref>` |
| Password | Mật khẩu database đã tạo |

Không tự suy ra host từ tên region. Không dùng username `postgres` của direct connection cho session pooler.

Session pooler phù hợp khi host kết nối qua IPv4 và backend cần session PostgreSQL lâu dài. Direct connection dùng được nếu môi trường có kết nối IP phù hợp; Supabase ưu tiên direct cho migration. Với lộ trình IPv4 này, dùng session pooler và kiểm tra Flyway ở lần khởi động đầu. **Không chọn Transaction pooler cổng 6543** cho cấu hình Flyway hiện tại: chế độ transaction có giới hạn về session state, prepared statements và advisory locks.

Nguồn: [Supabase — Connect to Postgres](https://supabase.com/docs/guides/database/connecting-to-postgres).

## 4. Tạo backend trên Render

Chọn **New → Web Service → repository GitHub EduFit**.

| Mục | Điền/chọn |
| --- | --- |
| Name | Ví dụ `edufit-backend-demo` |
| Branch | `develop` cho demo hoặc nhánh release bạn chọn |
| Region | Singapore nếu có |
| Language / Runtime | Docker |
| Root Directory | `backend` |
| Dockerfile Path | `./Dockerfile`, tính từ Root Directory |
| Docker Build Context Directory | `.` nếu giao diện có trường này, tính từ Root Directory |
| Docker Command | Để trống, dùng entrypoint có sẵn |
| Instance Type / Compute | Free để thử |
| Health Check Path | `/actuator/health` |

Các thao tác `COPY` trong Dockerfile cần thấy `pom.xml`, `app`, `platform`, `modules` trong cùng build context `backend`. Không đặt Root Directory thành `backend/app`.

Nếu không đặt Root Directory, cấu hình tương đương là Dockerfile `backend/Dockerfile` và build context `backend`. Chọn **một** cách, tránh đường dẫn bị ghép thành `backend/backend/Dockerfile`.

Dockerfile đã build Maven và chạy JAR; không cần điền lệnh `mvn spring-boot:run` hay lệnh chạy Java khác.

Nguồn: [Docker on Render](https://render.com/docs/docker), [Web Services](https://render.com/docs/web-services).

## 5. Điền biến môi trường Render

Vào **Environment** của Web Service. Thay các placeholder bằng giá trị thật; không giữ dấu `< >`. Không thêm dấu nháy bao quanh giá trị trong giao diện.

### 5.1. Nhóm bắt buộc cho lần chạy đầu

```dotenv
SERVER_PORT=10000
DB_URL=jdbc:postgresql://<HOST-SESSION-POOLER>:5432/postgres?sslmode=require
DB_USERNAME=postgres.<PROJECT-REF>
DB_PASSWORD=<DATABASE-PASSWORD>

SPRING_PROFILES_ACTIVE=prod
SPRING_FLYWAY_BASELINE_ON_MIGRATE=false

SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5
SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0
SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT=30000

SERVER_FORWARD_HEADERS_STRATEGY=framework
FRONTEND_BASE_URL=https://<FRONTEND-HOST>
PAYMENT_FRONTEND_URL=https://<FRONTEND-HOST>

AI_PROVIDER=stub
PAYOS_PAYOUT_ENABLED=false
MANAGEMENT_HEALTH_MAIL_ENABLED=false
```

Giải thích:

- `SERVER_PORT=10000` khớp cổng mặc định Render. Nếu bạn đổi biến `PORT` của Render, đặt `SERVER_PORT` cùng số; code hiện chưa đọc trực tiếp `${PORT}`.
- `DB_URL` phải bắt đầu bằng `jdbc:postgresql://`, không dùng nguyên URL `postgresql://user:password@...`. Tách user/password sang hai biến riêng để tránh lỗi ký tự đặc biệt.
- `sslmode=require` yêu cầu kết nối mã hóa. Khi vận hành thật, cân nhắc `verify-full` cùng chứng chỉ CA phù hợp thay vì chỉ kiểm tra có mã hóa.
- `prod` là tên profile để phân biệt môi trường; repository hiện không có `application-prod.yml`, nên vẫn dùng cấu hình chung. Nó **không tự hoàn thiện cấu hình production**. Không bật `dev` trên DB thật vì profile đó nạp tài khoản demo.
- Giảm pool từ mặc định 20 connection để tiết kiệm tài nguyên DB Free.
- `BASELINE_ON_MIGRATE=false` khiến database có bảng không rõ lịch sử bị từ chối thay vì tự baseline. Hướng dẫn giả định schema ứng dụng mới, chưa có dữ liệu EduFit.
- `AI_PROVIDER=stub` giúp xác minh hạ tầng trước khi bật Gemini. Đây là giá trị trên Render, độc lập `.env` local đang dùng Gemini thật.
- Heap Java chưa cố định theo cấu hình Compose local `-Xmx1024m`; cấu hình đó có thể vượt RAM instance Free. Nếu có OOM, đo RAM/tối ưu hoặc nâng compute, không chỉ tăng heap.

Để Render mặc định `PORT=10000`. Nếu health đã cấu hình đúng nhưng service báo không tìm thấy cổng, kiểm tra `SERVER_PORT` trong Environment và log Tomcat.

### 5.2. Bật Gemini sau khi backend đã UP

```dotenv
AI_PROVIDER=gemini
GEMINI_API_KEY=<KHOA-GEMINI-CUA-BAN>
GEMINI_MODEL=gemini-3.5-flash-lite
```

Lưu biến và redeploy/restart theo dashboard. Khóa Gemini chỉ nằm ở backend. Chi phí/quota Gemini độc lập với gói Render.

### 5.3. Bật payOS khi đã có frontend HTTPS

```dotenv
PAYOS_CLIENT_ID=<CLIENT-ID>
PAYOS_API_KEY=<API-KEY>
PAYOS_CHECKSUM_KEY=<CHECKSUM-KEY>
PAYMENT_BANK_ACCOUNT=<SO-TAI-KHOAN-DA-LIEN-KET>
PAYMENT_FRONTEND_URL=https://<FRONTEND-HOST>
PAYOS_PAYOUT_ENABLED=false
```

Đăng ký webhook kênh thu trong payOS theo địa chỉ:

```text
https://<BACKEND-HOST>.onrender.com/api/v1/payment/webhooks/payos
```

Đây là endpoint đang có trong code. Không sử dụng URL localhost và không đặt webhook sau lớp đăng nhập frontend. Backend có kiểm tra chữ ký webhook. Kênh chi là cấu hình riêng; không tự bật `PAYOS_PAYOUT_ENABLED=true` chỉ vì kênh nạp đã hoạt động.

Xem thêm [hướng dẫn payOS của dự án](../payment/payos-setup.md).

## 6. Deploy lần đầu và kiểm tra Flyway

1. Chọn deploy service; chờ Docker build và log ứng dụng.
2. Tìm log xác nhận đúng profile và đúng host PostgreSQL cloud.
3. Flyway phải validate và chạy các migration schema/danh mục.
4. Hibernate phải tạo được `EntityManagerFactory`, Tomcat phải khởi động.
5. Mở `https://<BACKEND-HOST>.onrender.com/actuator/health`.

Kết quả mong đợi là HTTP 200 với `"status":"UP"`. Trang `/` có thể trả 401/404 vì đây là backend, không phải trang giao diện.

Trong Supabase SQL Editor, kiểm tra:

```sql
select installed_rank, version, description, success
from public.flyway_schema_history
order by installed_rank;

select table_name
from information_schema.tables
where table_schema = 'public'
order by table_name;
```

Không kỳ vọng số migration giống local `dev`: V1.09, V1.10, V1.13, V1.27 là seed demo và không được nạp ở cấu hình `prod` này. Đối chiếu thêm migration mới theo commit thực sự được deploy.

Nếu không thấy bảng `public.flyway_schema_history`, xác nhận backend đã kết nối đúng project, database và không có biến schema khác ghi đè.

**Không dùng `repair`, tắt validate, `ddl-auto=update` hay xóa lịch sử để vượt lỗi.** Nếu đã có dữ liệu, sao lưu rồi phân tích lịch sử trước. Không import nguyên database local có tài khoản demo, secret hoặc giao dịch thử lên môi trường thật.

## 7. Kết nối frontend Vercel

Trong Environment của frontend Vercel, đặt:

```dotenv
BACKEND_URL=https://<BACKEND-HOST>.onrender.com
```

Không thêm `/api` ở cuối vì `frontend/next.config.ts` đã ghép `/api/:path*`. Redeploy frontend sau khi đổi biến này để cập nhật rewrite.

Code hiện gọi URL tương đối `/api/...`; giữ proxy qua frontend để browser dùng cùng origin. Không đổi một phần request sang gọi thẳng domain Render vì sẽ cần xử lý lại CORS và cookie giữa các site.

Kiểm tra bằng F12 → Network:

1. `/api/v1/auth/csrf` lấy được token.
2. Đăng ký tài khoản mới, đăng nhập, tải lại trang vẫn giữ phiên.
3. Request ghi dữ liệu gửi CSRF header và cookie đúng.
4. Cookie session có HttpOnly/Secure khi chạy HTTPS; kiểm tra phản hồi proxy và cơ chế forwarded headers thực tế.
5. Lưu hồ sơ rồi tải lại vẫn thấy dữ liệu.

Nếu cần Admin cho môi trường thật: tạo một tài khoản chủ dự án bằng luồng đăng ký thông thường trước, rồi người có quyền quản trị DB xác minh đúng tài khoản và nâng role qua SQL Editor:

```sql
-- Thay bằng email thực sự do chủ dự án kiểm soát, xem bản ghi trước khi cập nhật.
select user_id, email, role from public.account
where email = '<EMAIL-CHU-DU-AN>';

update public.account set role = 'ADMIN'
where email = '<EMAIL-CHU-DU-AN>'
returning user_id, email, role;
```

Đăng xuất/đăng nhập lại sau khi đổi role. Đây là thao tác bootstrap một lần bằng quyền DB, không phải API cho phép người dùng tự chọn Admin.

## 8. Những tính năng chưa thể xem là hoàn tất chỉ bằng deploy này

### 8.1. File bằng cấp KYC

`LocalCredentialStorageService` hiện lưu file trong `var/credentials`. Render Free không có persistent disk, nên upload có thể thành công nhưng file mất sau khi service restart/redeploy/ngủ; metadata trong DB vẫn còn.

Muốn giữ file lâu dài, chọn một hướng:

- Chuyển storage adapter sang Supabase Storage/R2/S3, bucket riêng tư và backend kiểm tra quyền tải file. **Cần viết tích hợp**, việc tạo bucket không tự đổi hành vi code.
- Dùng Render trả phí và gắn persistent disk vào đúng `STORAGE_CREDENTIALS_ROOT`; sao lưu cả file lẫn DB.

Không kết luận luồng KYC đã bền vững chỉ từ một lần upload thành công.

### 8.2. Email quên mật khẩu

Mailpit ở localhost chỉ dùng local. Render Free chặn outbound SMTP trên các cổng 25, 465, 587; nhập thông tin Gmail SMTP 587 không giải quyết giới hạn đó.

Cần chọn dịch vụ email có HTTPS API rồi tích hợp backend, hoặc dùng cổng SMTP khác được nhà cung cấp và host hỗ trợ, cấu hình đầy đủ xác thực/TLS. Code hiện để SMTP auth/starttls tắt, nên không chỉ thay `MAIL_HOST` là đủ. Chỉ tuyên bố email hoạt động sau khi đã thử gửi/nhận và kiểm tra link frontend thật.

### 8.3. Payment và các job theo lịch

Render Free tự ngủ. `PaymentDeadlineJob` hiện chạy bằng `@Scheduled` trong JVM, nên không chạy khi backend ngừng hoạt động. Tự tất toán sau 24h có thể bị trễ; webhook cũng có thể gặp cold start.

Bản Free phù hợp demo có kiểm soát. Trước khi dùng tiền thật, dùng backend chạy liên tục hoặc thiết kế lại job có trigger bền vững bên ngoài và kiểm tra khả năng xử lý sau downtime. Không dùng vòng ping giả làm bảo đảm vận hành thanh toán.

## 9. Lỗi thường gặp

| Lỗi | Kiểm tra/cách xử lý |
| --- | --- |
| Build không thấy `pom.xml`, `modules` | Root Directory và Docker build context phải là `backend` |
| Không tìm thấy cổng HTTP | `SERVER_PORT=10000`, `PORT` Render tương ứng; xem log Tomcat |
| `Network is unreachable` tới DB | Có thể dùng direct host IPv6; lấy Session pooler từ Connect |
| `password authentication failed` | Mật khẩu database, đúng project và đúng username của pooler |
| `Tenant or user not found` | Sai user dạng `postgres.<project-ref>` hoặc sai pooler host |
| Pool timeout / quá nhiều connection | Project DB có đang pause không; giảm pool, kiểm tra các service khác dùng DB |
| Flyway thiếu V1.09/V1.10/V1.13 | Thường dùng DB đã seed demo nhưng backend không chạy dev; không đổi prod sang dev để che lịch sử DB thật |
| Checksum mismatch | Mã migration khác bản đã áp dụng; đối chiếu đúng commit và lịch sử, không sửa checksum thủ công |
| Extension/permission/opclass lỗi | V1.01 cần `uuid-ossp`, `pgcrypto`, `btree_gist`; kiểm tra quyền và schema extension trong Supabase, giữ nguyên log để phân tích trước khi chạy tiếp |
| `unsupported startup parameter` | Code hiện truyền JDBC `options` cho timezone; xác minh pooler hỗ trợ. Nếu cần thay bằng connection-init SQL, phải đồng bộ cả kết nối Flyway; không tắt SSL hay validate |
| Hibernate báo thiếu bảng | Flyway chưa chạy đúng DB/schema; không bật Hibernate tự tạo bảng |
| Login 401/403 sau proxy | Cookie session, CSRF, forwarded headers và `BACKEND_URL`; không tắt CSRF để sửa |
| Frontend gọi `127.0.0.1:8080` | Chưa đặt `BACKEND_URL` trên Vercel hoặc chưa redeploy |
| Gemini yêu cầu key | Bật `gemini` thì phải có key; tạm dùng `stub` nếu chỉ kiểm hạ tầng |
| Exit/OOM | Kiểm tra RAM instance và JVM; Free có thể không đủ cho backend này |
| File KYC trả 404 sau redeploy | File nằm trên filesystem tạm; cần storage bền vững |

## 10. Chi phí và dữ liệu

- Render: chọn workspace Hobby và compute Free. Compute trả phí, disk, workspace nâng cấp và usage vượt hạn mức là khoản riêng. Có phương thức thanh toán thì có thể bị tính usage vượt hạn mức; theo dõi Billing.
- Free web service có 750 giờ/tháng dùng chung workspace và ngủ sau 15 phút không có traffic. Không lưu file quan trọng trên ổ tạm.
- Supabase Free hiện có quota database 500 MB, storage 1 GB và cơ chế pause khi ít hoạt động. Không đồng nghĩa backup tự động hoặc dung lượng không giới hạn; tự kiểm tra phương án xuất/khôi phục dữ liệu.
- Tên miền và Gemini/email/payOS có điều kiện tính phí riêng, không nằm trong miễn phí hosting.

Nguồn: [Render Free](https://render.com/docs/free), [Render billing FAQ](https://render.com/docs/faq), [Supabase pricing](https://supabase.com/pricing), [Supabase project pausing](https://supabase.com/docs/guides/platform/free-project-pausing).

## 11. Checklist hoàn tất bản demo

- [ ] Render build đúng commit và Dockerfile backend.
- [ ] Backend dùng DB cloud riêng; TLS kết nối thành công.
- [ ] Data API không công khai các bảng EduFit.
- [ ] Flyway chạy đúng lịch sử, Hibernate validate đạt.
- [ ] Health HTTP 200 và `status=UP`.
- [ ] Frontend proxy được API; đăng ký/đăng nhập/lưu hồ sơ đạt.
- [ ] Secret chỉ nằm trong Environment, không trong Git/frontend.
- [ ] Đã ghi rõ giới hạn KYC file, email và job payment của bản Free.
- [ ] Có bản xuất dữ liệu cần giữ và biết cách khôi phục.

Chỉ checklist hạ tầng đạt chưa có nghĩa đã đủ điều kiện dùng thanh toán thật. Các giới hạn chức năng ở mục 8 cần được giải quyết và kiểm thử trước khi mở vận hành thực tế.
