# EduFit

[![Backend CI](https://github.com/OWNER/REPOSITORY/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/OWNER/REPOSITORY/actions/workflows/backend-ci.yml)

EduFit hiện gồm backend Java 21 và Spring Boot 4.1 theo kiến trúc modular monolith, hạ tầng PostgreSQL 17 để chạy trên máy cá nhân, bộ quy ước mã nguồn có thể kiểm chứng và quy trình kiểm tra chất lượng bằng GitHub Actions.

## Yêu cầu môi trường

Cài đặt và khởi động Docker Desktop. Với quy trình chạy bằng Docker thông thường, bạn không cần cài Java hoặc Maven trên máy.

## Khởi động PostgreSQL và backend

Tại thư mục gốc của repository, chạy một lệnh:
.
```bash
docker compose up --build -d
```

Kiểm tra trạng thái của hai dịch vụ:

```bash
docker compose ps
docker compose logs backend
```

Backend đã sẵn sàng khi truy cập `http://localhost:8080/actuator/health` và nhận được phản hồi có `"status":"UP"`. Phản hồi health có kiểm tra trạng thái cơ sở dữ liệu ở bên trong nhưng chủ động ẩn thông tin chi tiết của từng thành phần.

Cấu hình mặc định cho môi trường local hoạt động mà không cần file `.env`. Nếu muốn đổi cổng hoặc thông tin đăng nhập, hãy sao chép `.env.example` thành `.env`; file `.env` đã được Git bỏ qua.

PostgreSQL mặc định được mở tại cổng `5433` trên máy host để tránh trùng với cổng phổ biến của PostgreSQL đã cài sẵn. Bên trong mạng Compose, các container vẫn kết nối qua cổng `5432`.

Nếu cổng `5433` đang được dự án khác sử dụng, đặt `POSTGRES_PORT=5434` (hoặc cổng trống khác) trong `.env`, rồi chạy `docker compose up -d db`. Backend chạy từ IDE cần nạp cùng `.env`: các biến `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT` được dùng làm cấu hình mặc định. Các biến `DB_URL`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`/`DB_USER`, `DB_PASSWORD` vẫn được ưu tiên nếu có. Không xóa volume khi đổi cổng; `POSTGRES_PASSWORD` phải khớp mật khẩu đã khởi tạo trong volume cũ.

## Dữ liệu demo chỉ có ở profile `dev`

Tài khoản demo (`*.demo@edufit.vn`, mật khẩu chung `Password123@`) và hồ sơ gia sư mẫu nằm ở `backend/app/src/main/resources/db/seed` và **chỉ được nạp khi backend chạy profile `dev`**. Chuỗi migration mặc định (`db/migration`) chỉ gồm schema và danh mục tham chiếu, dùng cho môi trường triển khai thật.

- `docker compose up` đã đặt sẵn `SPRING_PROFILES_ACTIVE=dev`.
- Chạy backend từ IDE/Maven trên máy local: đặt `SPRING_PROFILES_ACTIVE=dev`. Một CSDL local đã từng áp dụng V1.09/V1.10/V1.13 sẽ báo lỗi validate của Flyway nếu khởi động **không** có profile `dev`.
- Nút điền nhanh tài khoản demo ở trang đăng nhập chỉ hiện khi frontend có `NEXT_PUBLIC_ENABLE_DEMO_LOGIN=true` (ví dụ trong `frontend/.env.local`).
- Đăng nhập Google cần `GOOGLE_CLIENT_ID` (backend) trùng `NEXT_PUBLIC_GOOGLE_CLIENT_ID` (frontend); thiếu cấu hình thì đăng nhập Google bị từ chối.
- Email đặt lại mật khẩu được gửi qua SMTP (Mailpit tại `http://localhost:8025` khi chạy local); cấu hình bằng `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`, `FRONTEND_BASE_URL`.

## Khởi tạo hoặc kiểm tra schema rỗng

PostgreSQL entrypoint tự chạy `database/init/001-create-schema.sql` trong lần khởi động đầu tiên. Script có tính lặp an toàn và có thể chạy lại thủ công.

Trên Windows PowerShell:

```powershell
.\scripts\init-db.ps1
```

Trên macOS hoặc Linux:

```bash
sh ./scripts/init-db.sh
```

Volume có tên `edufit_postgres_data` giữ nguyên schema và dữ liệu trong tương lai khi chạy `docker compose down` rồi khởi động lại. Không chạy `docker compose down -v` trừ khi bạn chủ động muốn xóa dữ liệu cơ sở dữ liệu trên máy.

## Kiểm tra chất lượng backend trên máy

Test tích hợp dùng Testcontainers với đúng image `postgres:17` của `compose.yaml`, nên **Docker phải đang chạy**. Thiếu Docker thì test báo lỗi chứ không tự bỏ qua, để Exit Code 0 luôn có nghĩa là các ràng buộc trên CSDL thật đã được kiểm chứng.

Nếu máy đã cài Java 21, hãy dùng Maven Wrapper đi kèm dự án:

```bash
cd backend
sh ./mvnw -B -ntp verify
```

Trên Windows PowerShell, dùng:

```powershell
cd backend
.\mvnw.cmd -B -ntp verify
```

Xem `CONTRIBUTING.md` để biết quy tắc định dạng, đặt tên, branch, review và commit. Xem tài liệu kiến trúc và thiết kế tổng thể tại [`docs/software-architecture-design-specification.md`](docs/software-architecture-design-specification.md), tổng quan toàn diện tại `docs/PROJECT_OVERVIEW.md`, cũng như các quyết định kiến trúc trong `docs/ADR-001-tech-stack.md` và `docs/ADR-002-multi-module-architecture.md`. Lộ trình tổng thể & quy chuẩn Frontend được quy định tại [`docs/frontend-master-roadmap-and-architecture-plan.md`](docs/frontend-master-roadmap-and-architecture-plan.md), kèm kế hoạch hành động Giai đoạn 1 tại [`docs/frontend-phase1-demo-and-figma-refactor-plan.md`](docs/frontend-phase1-demo-and-figma-refactor-plan.md).

Tài liệu backend hiện thực: [mục lục](docs/README.md), [Discovery API contract](docs/discovery-api-contract.md), [Discovery flow và diagram](docs/discovery-module-flow.md), [Discovery performance](docs/discovery-performance-report.md).

## Thiết lập GitHub sau lần push đầu tiên

1. Thay `OWNER/REPOSITORY` trong badge CI ở đầu file bằng owner và tên repository thật trên GitHub.
2. Áp dụng các quy tắc bảo vệ branch trong `docs/github-branch-protection.md`.
3. Mở một PR thử nghiệm có lỗi lint hoặc lỗi biên dịch cố ý, sau đó xác nhận `Backend CI` thất bại và PR bị chặn merge.
4. Sửa lỗi và xác nhận cùng một kiểm tra chuyển sang trạng thái thành công trong vòng năm phút.

## Cấu hình Payment

Đọc [bộ tài liệu Payment](docs/payment/README.md) để xem giải trình kiến trúc, luồng tiền và task chi tiết từng phase theo mẫu Discovery.

Xem [hướng dẫn payOS](docs/payment/payos-setup.md) để bật nạp QR tự động, đăng ký webhook HTTPS và chi hộ sau Admin duyệt. Các biến mẫu trong `.env.example` để trống; kênh chi tắt mặc định. Trạng thái cấu hình hiển thị tại `/admin/payments`.
