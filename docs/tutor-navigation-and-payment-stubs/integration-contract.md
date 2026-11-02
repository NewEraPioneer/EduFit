# Biên bản Yêu cầu Tích hợp và Đặc tả Giao diện API (M3-01)

- **Ngày lập:** 18/10/2026
- **Nhánh phụ trách:** `fix/tutor-navigation-and-payment-stubs`
- **Base commit:** `d904f79a7de5f1ac5235036eefb8eb6dd58fd6de` (main)
- **Snapshot tham chiếu develop:** `7c3fa5ca0c841b9d144bfd0d40b3f1fd41afd5b2`

---

## 1. Mục đích biên bản

Tài liệu này ghi nhận hiện trạng phụ thuộc (prerequisites), các điểm chốt giao diện API (API contracts) và tình trạng của hai cổng kiểm soát kỹ thuật (**Gate G-REQUESTS** và **Gate G-HISTORY**) trước khi tiến hành viết code chức năng cho Lịch sử Giảng dạy và Điều hướng Hàng đợi của Gia sư.

Việc này nhằm ngăn chặn:
1. Sửa đường dẫn điều hướng trỏ tới một trang chết (dead link HTTP 404).
2. Tự ý gọi API hoặc tạo mã giả lập (mock data) trên môi trường production khi backend chưa được tích hợp vào `main`.
3. Xung đột trách nhiệm giữa các thành viên dự án khi chưa có thỏa thuận tích hợp.

---

## 2. Trạng thái các Cổng kiểm soát (Gate Status)

### 2.1. Cổng G-REQUESTS — Trang Hàng đợi yêu cầu `/tutor/requests`
- **Trạng thái:** **PENDING (ĐANG CHỜ TÍCH HỢP)**
- **Hiện trạng trên base `main` (`d904f79`):**
  - Route `frontend/src/app/tutor/requests/page.tsx`: **Chưa tồn tại** (truy cập báo 404).
  - Component `TutorDashboardView.tsx` (dòng 71) đang có liên kết trỏ tạm về `/tutor/dashboard`.
  - Component `TutorPendingRequests.tsx` trên dashboard có nút "Xem tất cả" trỏ về `/tutor/requests`.
- **Hiện trạng trên `origin/develop` (`7c3fa5c`):**
  - Đã có `frontend/src/app/tutor/requests/page.tsx` hiển thị component `TutorPendingRequests` từ `@/features/connection`.
  - Feature `connection` trên develop đã có: `TutorPendingRequests.tsx`, `RespondRequestModal.tsx`, `StudentPedagogicalProfileModal.tsx`, và API `connectionApi.getTutorRequests()`.
- **Quy tắc nghiệm thu Gate G-REQUESTS:**
  - Không được sửa nút "Xem hàng đợi yêu cầu học viên" trên `TutorDashboardView.tsx` trỏ sang `/tutor/requests` khi trang này chưa tồn tại và chưa render được danh sách yêu cầu thực tế từ backend Connection.
  - Phải tích hợp trang `/tutor/requests` hoàn chỉnh (kèm kết nối backend Connection hiện có tại `GET /api/v1/connections/requests`) trước khi đánh dấu hoàn thành M3-06.

---

### 2.2. Cổng G-HISTORY — Module Tiến độ & Lịch sử Giảng dạy UC5.5
- **Trạng thái:** **PENDING (ĐANG CHỜ TÍCH HỢP)**
- **Hiện trạng trên base `main` (`d904f79`):**
  - `backend/modules/progress`: Mới chỉ có file cấu hình `pom.xml`, chưa có mã nguồn Java (chưa có `ProgressDashboardController.java`, `LearningPlanService.java`, v.v.).
  - `frontend/src/features/progress`: Thư mục chưa tồn tại; chưa có `progressApi.ts`.
  - Route `/tutor/history`: Chưa tồn tại; Sidebar mục GIẢNG DẠY chưa có menu này.
- **Hiện trạng trên `origin/develop` (`7c3fa5c`):**
  - Đầy đủ controller: `ProgressDashboardController.java`, `ProgressPlanController.java`.
  - Domain, service, repository, flyway migration cho `learning_plan`, `milestone`, `session_note`.
  - Frontend SDK: `progressApi.ts` với các phương thức `getTeachingHistory`, `getClassNotes`, `getPlanByClassId`.
- **Quy tắc nghiệm thu Gate G-HISTORY:**
  - M3-07, M3-08, M3-09 chỉ được triển khai khi backend `progress` và frontend SDK được tích hợp chính thức vào nhánh làm việc.
  - Tuyệt đối không hardcode dữ liệu mock trong runtime production để qua mặt khâu kiểm thử.

---

## 3. Đặc tả API Contract chi tiết (Tham chiếu từ `origin/develop`)

Khi tích hợp backend `progress`, frontend của Thành viên 3 sẽ tiêu thụ các endpoint theo hợp đồng sau:

### 3.1. Danh sách tổng quan Lớp học đã/đang dạy (Teaching History Summary)
- **Endpoint:** `GET /api/v1/progress/teaching-history`
- **Quyền truy cập:** Vai trò `TUTOR` (được xác thực qua cookie phiên `EDUFIT_SESSION`).
- **Query Parameters:**
  - `page` (int, default: 0): Số trang (0-indexed).
  - `size` (int, default: 10): Kích thước trang.
  - `studentName` (string, optional): Lọc theo tên học sinh (tìm kiếm mờ / contains ignore case).
  - `status` (string, optional): Trạng thái lớp học (`ACTIVE`, `COMPLETED`, `TERMINATED`).
  - `from` (Instant, optional): Mốc thời gian bắt đầu lọc (ISO-8601 UTC).
  - `to` (Instant, optional): Mốc thời gian kết thúc lọc (ISO-8601 UTC).
- **Cấu trúc DTO phản hồi (`ApiResponse<Page<TeachingHistoryItem>>`):**
  ```json
  {
    "success": true,
    "message": "Lấy lịch sử giảng dạy thành công",
    "data": {
      "content": [
        {
          "classId": "UUID",
          "studentId": "UUID",
          "studentName": "Nguyễn Văn A",
          "goalId": "UUID",
          "goalTitle": "Luyện thi đại học môn Toán",
          "status": "COMPLETED",
          "progressPercentage": 85,
          "startedAt": "2026-09-01T08:00:00Z",
          "endedAt": "2026-10-15T10:00:00Z",
          "sessionStats": {
            "classId": "UUID",
            "completedCount": 12,
            "absentCount": 1,
            "cancelledCount": 0,
            "upcomingCount": 0,
            "nextSessionStart": null,
            "lastSessionStart": "2026-10-15T08:00:00Z"
          }
        }
      ],
      "pageable": { ... },
      "totalElements": 1,
      "totalPages": 1,
      "number": 0,
      "size": 10
    }
  }
  ```
- **Lưu ý nghiệp vụ:**
  - Mỗi bản ghi trong `content` đại diện cho một **Lớp học (Class/Engagement)**, không phải một buổi học đơn lẻ.
  - `sessionStats` cung cấp số đếm các buổi: `completedCount` (buổi dạy thành công), `absentCount` (vắng mặt), `cancelledCount` (hủy).

---

### 3.2. Dòng thời gian Buổi học & Ghi chú buổi dạy (Session Notes Timeline)
- **Endpoint:** `GET /api/v1/progress/classes/{classId}/notes`
- **Quyền truy cập:** Gia sư sở hữu lớp học (`TutorId` trùng với gia sư phụ trách lớp).
- **Query Parameters:**
  - `classId` (Path Variable, UUID): Mã lớp học được chọn từ danh sách tổng quan.
  - `from` (Instant, optional): Lọc từ ngày.
  - `to` (Instant, optional): Lọc đến ngày.
  - `page` (int, default: 0): Số trang.
  - `size` (int, default: 20): Số lượng buổi trên một trang.
- **Cấu trúc DTO phản hồi (`ApiResponse<Page<SessionNoteTimelineResponse>>`):**
  ```json
  {
    "success": true,
    "message": "Lấy danh sách ghi chú buổi học thành công",
    "data": {
      "content": [
        {
          "sessionId": "UUID",
          "classId": "UUID",
          "startAt": "2026-10-15T08:00:00Z",
          "endAt": "2026-10-15T10:00:00Z",
          "status": "COMPLETED",
          "noteText": "Học sinh nắm vững lý thuyết hình học không gian, làm bài tập đầy đủ.",
          "recordedAt": "2026-10-15T10:05:00Z"
        }
      ],
      "totalElements": 12,
      "totalPages": 1,
      "number": 0,
      "size": 20
    }
  }
  ```
- **Lưu ý hiển thị:**
  - `noteText` là văn bản thuần (plain text), frontend cần render an toàn, chống XSS.
  - Nếu buổi học chưa có ghi chú (`noteText == null`), hiển thị trạng thái "Chưa có ghi chú".

---

### 3.3. Kế hoạch học tập chi tiết (Learning Plan - Read Only)
- **Endpoint:** `GET /api/v1/progress/plans/{classId}`
- **Quyền truy cập:** Gia sư hoặc Học sinh/Phụ huynh liên quan tới lớp học.
- **Cấu trúc phản hồi:**
  - Trả về `PlanResponse` chứa danh sách các cột mốc (`milestones`), tỉ lệ hoàn thành kế hoạch.
  - Nếu lớp học chưa được thiết lập kế hoạch học tập, API trả về mã lỗi 404 hoặc response rỗng; frontend cần hiển thị empty-state thân thiện "Lớp học chưa khởi tạo kế hoạch học tập".

---

## 4. Các điểm thiếu hụt (Gaps) và Hướng xử lý trên Giao diện

| Thiếu hụt | Hiện trạng backend | Hướng giải quyết trên Frontend (M3-08, M3-09) |
|---|---|---|
| **Tên môn học (Subject)** | `TeachingHistoryItem` chỉ có `goalTitle`, chưa có trường `subjectName` riêng biệt | Hiển thị `goalTitle` làm tiêu đề học tập chính; không tự chế trường mới |
| **Tổng số buổi dạy toàn thời gian** | API trả về theo từng lớp trong trang hiện tại, chưa có endpoint tính `totalCompletedSessionsAllClasses` | Chỉ hiển thị số buổi hoàn thành của từng lớp (`item.sessionStats.completedCount`); không cộng dồn các lớp trên một trang để ghi là "tổng số buổi toàn hệ thống" |
| **Thời gian buổi dạy gần nhất** | `TeachingHistoryItem.sessionStats.lastSessionStart` có thể `null` nếu lớp chưa có buổi nào | Định dạng hiển thị ngày giờ thân thiện theo múi giờ Việt Nam (`Asia/Ho_Chi_Minh`), hiển thị "Chưa có buổi học" nếu null |

---

## 5. Lộ trình thực hiện khả thi

1. **Giai đoạn hiện tại (Độc lập, không bị chặn):**
   - Triển khai **Phase 1 (Research 3.1):** Soạn thảo hoàn chỉnh `docs/tuition/tuition-policy-and-dispute-specification.md` và `docs/tuition/tuition-proposed-screen-flow.md`.
   - Triển khai **Phase 2 (Payment stubs 3.2):** Xây dựng 3 trang thông tin "Sắp ra mắt" và cập nhật Sidebar/Dashboard CTAs để dọn sạch lỗi 404.
2. **Giai đoạn tiếp theo (Khi Gates mở):**
   - Phối hợp đưa các module/component từ `develop` vào nhánh để mở G-REQUESTS và G-HISTORY.
   - Triển khai Phase 3 (History & Requests) theo đúng các contract đã chốt ở mục 3.
