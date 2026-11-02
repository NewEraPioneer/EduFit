package vn.edufit.progress.domain.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Thẻ 3 — "Chuyên cần & buổi tới".
 *
 * <p>Tỉ lệ chuyên cần của con = COMPLETED / (COMPLETED + ABSENT + hủy muộn do phía học sinh/phụ huynh).
 * Hủy sớm hợp lệ (từ 12 giờ trở lên) và hủy do gia sư không trừ chuyên cần, được đếm riêng để minh bạch.
 */
public final class AttendancePolicy {

  private AttendancePolicy() {}

  public enum AttendanceKind {
    COMPLETED,
    ABSENT,
    LATE_CANCEL_BY_FAMILY,
    EARLY_CANCEL_BY_FAMILY,
    CANCELLED_BY_TUTOR,
    CANCELLED_OTHER,
    SCHEDULED
  }

  /** Dữ liệu thô một buổi học, {@code cancelledBy} là user id của người hủy. */
  public record SessionRecord(UUID sessionId, Instant startAt, String status, UUID cancelledBy, boolean lateCancel) {}

  public record ClassifiedSession(UUID sessionId, Instant startAt, AttendanceKind kind) {}

  public record Summary(
      int attendedCount,
      int countedCount,
      Integer attendanceRate,
      int absentCount,
      int familyLateCancelCount,
      int familyEarlyCancelCount,
      int tutorCancelCount,
      Instant nextSessionStart,
      UUID nextSessionId
  ) {}

  /**
   * Phân loại buổi học theo chuyên cần. Buổi ở trạng thái đề xuất/từ chối/hết hạn không liên quan nên trả rỗng.
   *
   * @param tutorUserId    user id của gia sư lớp.
   * @param familyUserIds  user id của học sinh và các phụ huynh đã liên kết.
   */
  public static Optional<ClassifiedSession> classify(SessionRecord s, UUID tutorUserId, Set<UUID> familyUserIds) {
    AttendanceKind kind = switch (s.status()) {
      case "COMPLETED" -> AttendanceKind.COMPLETED;
      case "ABSENT" -> AttendanceKind.ABSENT;
      case "SCHEDULED" -> AttendanceKind.SCHEDULED;
      case "CANCELLED" -> classifyCancellation(s, tutorUserId, familyUserIds);
      default -> null;
    };
    return Optional.ofNullable(kind).map(k -> new ClassifiedSession(s.sessionId(), s.startAt(), k));
  }

  public static Summary summarize(Collection<ClassifiedSession> sessions, Instant now) {
    int completed = 0;
    int absent = 0;
    int familyLate = 0;
    int familyEarly = 0;
    int tutorCancels = 0;
    ClassifiedSession next = null;
    for (ClassifiedSession s : sessions) {
      switch (s.kind()) {
        case COMPLETED -> completed++;
        case ABSENT -> absent++;
        case LATE_CANCEL_BY_FAMILY -> familyLate++;
        case EARLY_CANCEL_BY_FAMILY -> familyEarly++;
        case CANCELLED_BY_TUTOR -> tutorCancels++;
        case SCHEDULED -> {
          if (s.startAt().isAfter(now) && (next == null || s.startAt().isBefore(next.startAt()))) {
            next = s;
          }
        }
        case CANCELLED_OTHER -> { }
      }
    }
    int counted = completed + absent + familyLate;
    Integer rate = counted == 0
        ? null
        : BigDecimal.valueOf(completed * 100L).divide(BigDecimal.valueOf(counted), 0, RoundingMode.HALF_UP).intValue();
    return new Summary(completed, counted, rate, absent, familyLate, familyEarly, tutorCancels,
        next != null ? next.startAt() : null, next != null ? next.sessionId() : null);
  }

  /** Các buổi học bị lỡ (vắng hoặc hủy), dùng cho ngăn kéo chi tiết và câu giải thích của thẻ 1. */
  public static List<ClassifiedSession> missedSessions(Collection<ClassifiedSession> sessions) {
    return sessions.stream()
        .filter(s -> s.kind() != AttendanceKind.COMPLETED && s.kind() != AttendanceKind.SCHEDULED)
        .sorted((a, b) -> b.startAt().compareTo(a.startAt()))
        .toList();
  }

  private static AttendanceKind classifyCancellation(SessionRecord s, UUID tutorUserId, Set<UUID> familyUserIds) {
    UUID by = s.cancelledBy();
    if (by != null && by.equals(tutorUserId)) return AttendanceKind.CANCELLED_BY_TUTOR;
    if (by != null && familyUserIds.contains(by)) {
      return s.lateCancel() ? AttendanceKind.LATE_CANCEL_BY_FAMILY : AttendanceKind.EARLY_CANCEL_BY_FAMILY;
    }
    return AttendanceKind.CANCELLED_OTHER;
  }
}
