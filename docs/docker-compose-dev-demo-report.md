# Báo cáo: tách compose dev/demo và bỏ compose.override.yaml

Nhánh: `chore/docker-compose-dev-demo-split` (tạo từ `fix/remove-frontend-fake-data`). Chưa commit, chưa push.

## Mục tiêu và phạm vi

Phương án 1 đã thống nhất: bỏ `compose.override.yaml`, tách compose thành nền / dev, thêm `compose.demo.yaml` để demo local bằng một lệnh. Không làm `compose.prod.yaml`, CI/CD, hay deploy (chưa chốt nơi deploy).
`frontend/Dockerfile` là phụ thuộc bắt buộc của `compose.demo.yaml` (service `frontend` cần image), không phải mở rộng phạm vi.

## Thay đổi

| File | Thay đổi |
|---|---|
| `compose.override.yaml` | Xóa. Nội dung (JAVA_OPTS, healthcheck 20s) chuyển sang `compose.dev.yaml`. |
| `compose.yaml` | Chỉ còn `db` + `backend`. `POSTGRES_DB/USER/PASSWORD` và `AI_PROVIDER` bắt buộc (`${VAR:?}`), không còn mật khẩu mặc định. Bỏ `mail`, bỏ `ports`, bỏ mount `database/init`, bỏ `SPRING_PROFILES_ACTIVE` mặc định `dev`. Giữ `postgres:17`. Các khóa tích hợp tùy chọn (Gemini, Google, PayOS) vẫn có default rỗng có chủ đích. |
| `compose.dev.yaml` (mới) | Đặt cứng profile `dev`, Mailpit (ghim `v1.21`), publish cổng db/backend/mail, mount `database/init`, `JAVA_OPTS` dev (vẫn chỉnh được qua `.env`), URL frontend. |
| `compose.demo.yaml` (mới) | Thêm service `frontend`; `BACKEND_URL=http://backend:8080` và `NEXT_PUBLIC_ENABLE_DEMO_LOGIN=true` truyền qua `build.args`. |
| `frontend/Dockerfile`, `frontend/.dockerignore` (mới) | Multi-stage, `npm run build` rồi `npm start`, chạy user `node`. |
| `frontend/.env.example` (mới), `frontend/.gitignore` | Mẫu 3 biến cho `npm run dev`. Thêm `!.env.example` vì `.env*` đang bị ignore. |
| `.env.example` | Đầu file chọn môi trường qua `COMPOSE_FILE` (phân cách `,` qua `COMPOSE_PATH_SEPARATOR` để chạy cả Windows và Linux). Mật khẩu mẫu chạy được ngay. Thêm `FRONTEND_PORT`. |
| `README.md` | Cập nhật mục khởi động dev, thêm mục demo một lệnh, sửa dòng về profile `dev`. |

## Bỏ giá trị mặc định của AI Provider và model

Theo yêu cầu bổ sung, `AI_PROVIDER` và `GEMINI_MODEL` chỉ lấy từ file env, không còn giá trị ngầm ở bất kỳ tầng nào:
- `compose.yaml`, `.env.example`: bỏ default (xem trên).
- `backend/app/src/main/resources/application.yml`: `${AI_PROVIDER}` không còn mặc định `gemini`; `${GEMINI_MODEL:}` không còn mặc định `gemini-3.5-flash-lite`.
- `AiProperties`: bỏ fallback `stub` (thiếu provider thì ném lỗi `AI_PROVIDER is required`) và bỏ fallback model. `base-url` của Gemini vẫn giữ giá trị mặc định vì là hằng số endpoint, không phải lựa chọn của người dùng.
- Test: `AiConfigurationTest`, `AiGatewayServiceTest`, `AiDeadlineTest` cập nhật theo; thêm test cho thiếu provider, stub không cần model, gemini thiếu model.
- Kiểm chứng: `./mvnw verify` toàn backend với `AI_PROVIDER`/`GEMINI_MODEL` không đặt: 228 test, 0 lỗi, BUILD SUCCESS. Backend trong Docker với `AI_PROVIDER=stub` lên healthy.

## Đối chiếu kiểm chứng

| Tiêu chí | Kết quả thực nghiệm |
|---|---|
| Xóa `compose.override.yaml` | `git rm`, file không còn. |
| Base không có default cho biến bắt buộc | Không có `.env`: `docker compose -f compose.yaml config` báo `required variable ... is missing` cho `POSTGRES_*` và `AI_PROVIDER`. |
| Dev và demo hợp lệ | `docker compose config -q` thành công cho tổ hợp dev và dev+demo. |
| Demo chạy một lệnh | `docker compose -f compose.yaml -f compose.dev.yaml -f compose.demo.yaml up --build -d`: db, mail, backend ở trạng thái healthy, frontend chạy. |
| Backend và seed | `/actuator/health` trả `UP`. |
| Frontend và proxy | `localhost:3000` trả 200. Đăng nhập `student.demo@edufit.vn` qua `localhost:3000/api/v1/auth/login` (kèm CSRF) trả 200, `/auth/me` trả 200 với cookie phiên. Chứng minh rewrite `/api` hoạt động trong container. |
| Không vỡ test image | `ContainerImageParityTest` chạy qua Maven, kết quả ở dòng đầu mục này (surefire report). |

## Hạn chế và việc chưa làm

- Chưa kiểm tra bằng mắt nút điền nhanh tài khoản demo trên trình duyệt (cờ build đã truyền, grep HTML trang login không thấy chuỗi do render phía client).
- Đã tắt stack bằng `down` (không `-v`), volume dữ liệu còn nguyên.
- Thanh toán PayOS và Gemini thật cần khóa; `AI_PROVIDER` và `GEMINI_MODEL` không có mặc định trong compose và `.env.example`; người chạy phải tự đặt (`stub` cho demo offline).
- README còn nhắc `scripts/init-db.*` không tồn tại. Có từ trước, không thuộc phạm vi.
- Chưa có `compose.prod.yaml`, job CI (docker build, `next build`, smoke test) và deploy.
- Khi chạy thử tôi đã `cp .env.example .env` ở thư mục gốc. `.env` bị Git bỏ qua. Nếu bạn đã có `.env` riêng trước đó thì đã bị ghi đè.
