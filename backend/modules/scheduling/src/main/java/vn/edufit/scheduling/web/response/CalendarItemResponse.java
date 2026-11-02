package vn.edufit.scheduling.web.response;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.scheduling.domain.model.TutoringSession;

/**
 * Response DTO gọn nhẹ hiển thị trên giao diện Lịch học (Calendar View) theo tháng/tuần/ngày.
 */
public record CalendarItemResponse(
    UUID sessionId,
    UUID classId,
    UUID tutorId,
    UUID studentId,
    Instant startAt,
    Instant endAt,
    String mode,
    String placeOrLink,
    String status,
    boolean isLateCancel
) {

  public static CalendarItemResponse from(TutoringSession s) {
    if (s == null) {
      return null;
    }
    return new CalendarItemResponse(
        s.getSessionId(),
        s.getClassId(),
        s.getTutorId(),
        s.getStudentId(),
        s.getStartAt(),
        s.getEndAt(),
        s.getMode() != null ? s.getMode().name() : null,
        s.getPlaceOrLink(),
        s.getStatus() != null ? s.getStatus().name() : null,
        s.isLateCancel()
    );
  }
}
