# Task checklist: Render + Supabase UAT

Chi tiết phạm vi, điều kiện và tiêu chí nghiệm thu: [plan.md](plan.md).

## Phase 0 — Tài liệu và kiểm tra đích

- [x] T0.1: Bổ sung `docs/deployment/environments-and-release.md` và liên kết hướng dẫn.
  - Commit: `docs(deploy): define UAT and production release workflow`
- [ ] T0.2: Xác minh đúng Supabase UAT, kết nối, schema, history, quyền và dữ liệu cần giữ. Ghi kết quả với T2.2. **Chờ host/user/database và secret DB từ người dùng.**

## Phase 1 — Cấu hình

- [x] T1.1: Profile UAT/prod và template biến môi trường; không seed demo mặc định, không chứa secret.
  - Commit: `chore(config): add UAT and production configuration templates`
- [x] T1.2: Launcher JDK 21 nạp secret UAT tường minh; giữ cấu hình local độc lập.
  - Commit: `chore(deploy): add a local launcher for Supabase UAT`

## Phase 2 — Backend local kết nối Supabase

- [ ] T2.1: TLS/JDBC/Flyway/Hibernate/health đạt trên DB UAT đúng đích; backup trước migration nếu có dữ liệu.
  - Commit nếu có thay đổi: `fix(config): align Supabase JDBC settings for UAT`
- [x] T2.2: Kiểm tra tài khoản/session/hồ sơ và dữ liệu tồn tại; lập report kết quả thật.
  - Commit: `docs(deploy): record Supabase UAT verification`

## Phase 3 — Render và runbook

- [x] T3.1: Khai báo service Docker, port, health, compute và secret placeholders; không tạo Render DB.
  - Commit: `chore(deploy): configure Render backend for UAT`
- [x] T3.2: Cập nhật runbook triển khai/rollback và checklist trước-trong-sau deploy.
  - Commit: `docs(deploy): document UAT deployment and rollback checks`

## Phase 4 — Deploy UAT

- [ ] Có quyền truy cập tài khoản cloud và cấu hình secret; chọn đúng commit/nhánh, chưa tự merge.
- [ ] Render build/start/health đạt; xác minh Supabase và giới hạn RAM.
- [ ] Kiểm tra proxy/session/CSRF xuyên frontend nếu có frontend cloud.
- [x] Ghi phần tích hợp còn chờ; không tự chuyển tiền/rút tiền để thử.
- [ ] Lưu URL, commit và smoke report đã lọc secret.
  - Commit: `docs(deploy): record Render UAT smoke results`

## Phase 5 — Bàn giao

- [x] Rà soát secrets và diff; gate/CI phù hợp đạt cho thay đổi cấu hình cục bộ.
- [ ] Push `deploy/deploy-render-supabase`, tạo PR vào `develop`, không tự merge.
- [x] Bàn giao cách chạy local/UAT và các điều kiện production còn thiếu.
- [x] Không deploy production trong phạm vi này.

Sau mỗi task: kiểm tra tiêu chí trong plan -> commit đúng phạm vi -> push nhánh làm việc khi bắt đầu triển khai. Không stage toàn bộ thay đổi local.
