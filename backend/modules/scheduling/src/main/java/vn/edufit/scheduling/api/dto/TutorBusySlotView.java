package vn.edufit.scheduling.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Public DTO biểu diễn một khung giờ bận (đã có lịch dạy chính thức SCHEDULED) của Gia sư.
 *
 * <p>Mục đích sử dụng:
 * <ul>
 *   <li>Phục vụ module {@code discovery} (Tìm kiếm & Khám phá gia sư): Khi phụ huynh hoặc học sinh
 *       lọc gia sư theo khung giờ rảnh, module discovery sẽ gọi {@code SessionFacade} để lấy danh sách
 *       khung giờ bận này và loại trừ khỏi lịch rảnh của gia sư.</li>
 * </ul>
 *
 * @param sessionId Mã buổi học đã lên lịch.
 * @param tutorId   Mã gia sư.
 * @param startAt   Thời điểm bắt đầu khung giờ bận (UTC).
 * @param endAt     Thời điểm kết thúc khung giờ bận (UTC).
 */
public record TutorBusySlotView(
    UUID sessionId,
    UUID tutorId,
    Instant startAt,
    Instant endAt
) {}
