package vn.edufit.scheduling.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Aggregate Root đại diện cho một Buổi học (Tutoring Session) trong hệ thống EduFit.
 *
 * <p>Mục đích thiết kế & Nguyên tắc kiến trúc:
 * <ul>
 *   <li><b>Java POJO Thuần:</b> Không chứa bất kỳ phụ thuộc nào vào Spring Boot, Hibernate hay JPA.
 *       Mọi quy tắc bất biến (invariants) và luật nghiệp vụ đều được kiểm soát bên trong class này.</li>
 *   <li><b>Quan hệ 1-1:</b> Mỗi buổi học gắn liền với 1 lớp học ({@code classId}), đúng 1 Gia sư
 *       ({@code tutorId}) và đúng 1 Học sinh ({@code studentId}).</li>
 *   <li><b>Đóng gói trạng thái:</b> Trạng thái của buổi học chỉ có thể thay đổi thông qua các phương thức
 *       nghiệp vụ tường minh (ví dụ: {@link #accept(UUID, Instant)}, {@link #cancel(UUID, CancelReason, String, boolean, Instant)}).</li>
 * </ul>
 */
public class TutoringSession {

  private final UUID sessionId;
  private final UUID classId;
  private final UUID tutorId;
  private final UUID studentId;
  private Instant startAt;
  private Instant endAt;
  private SessionMode mode;
  private String placeOrLink;
  private String repeatNote;
  private String message;
  private SessionStatus status;
  private final UUID proposedBy;
  private UUID respondedBy;
  private Instant respondedAt;
  private String responseReason;
  private Instant expiresAt;
  private UUID cancelledBy;
  private Instant cancelledAt;
  private CancelReason cancelReason;
  private String cancelComment;
  private boolean lateCancel;
  private UUID outcomeRecordedBy;
  private Instant outcomeRecordedAt;
  private int version;
  private final Instant createdAt;

  /**
   * Constructor nội bộ khởi tạo toàn bộ trạng thái thực thể.
   */
  public TutoringSession(
      UUID sessionId,
      UUID classId,
      UUID tutorId,
      UUID studentId,
      Instant startAt,
      Instant endAt,
      SessionMode mode,
      String placeOrLink,
      String repeatNote,
      String message,
      SessionStatus status,
      UUID proposedBy,
      UUID respondedBy,
      Instant respondedAt,
      String responseReason,
      Instant expiresAt,
      UUID cancelledBy,
      Instant cancelledAt,
      CancelReason cancelReason,
      String cancelComment,
      boolean lateCancel,
      UUID outcomeRecordedBy,
      Instant outcomeRecordedAt,
      int version,
      Instant createdAt
  ) {
    this.sessionId = Objects.requireNonNull(sessionId, "sessionId không được null");
    this.classId = Objects.requireNonNull(classId, "classId không được null");
    this.tutorId = Objects.requireNonNull(tutorId, "tutorId không được null");
    this.studentId = Objects.requireNonNull(studentId, "studentId không được null");
    this.startAt = Objects.requireNonNull(startAt, "startAt không được null");
    this.endAt = Objects.requireNonNull(endAt, "endAt không được null");
    this.mode = Objects.requireNonNull(mode, "mode không được null");
    this.placeOrLink = placeOrLink;
    this.repeatNote = repeatNote;
    this.message = message;
    this.status = Objects.requireNonNull(status, "status không được null");
    this.proposedBy = Objects.requireNonNull(proposedBy, "proposedBy không được null");
    this.respondedBy = respondedBy;
    this.respondedAt = respondedAt;
    this.responseReason = responseReason;
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt không được null");
    this.cancelledBy = cancelledBy;
    this.cancelledAt = cancelledAt;
    this.cancelReason = cancelReason;
    this.cancelComment = cancelComment;
    this.lateCancel = lateCancel;
    this.outcomeRecordedBy = outcomeRecordedBy;
    this.outcomeRecordedAt = outcomeRecordedAt;
    this.version = version;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt không được null");
  }

  /**
   * Factory method khởi tạo một đề xuất buổi học mới (trạng thái {@link SessionStatus#PROPOSED}).
   */
  public static TutoringSession createProposed(
      UUID sessionId,
      UUID classId,
      UUID tutorId,
      UUID studentId,
      Instant startAt,
      Instant endAt,
      SessionMode mode,
      String placeOrLink,
      String repeatNote,
      String message,
      UUID proposedBy,
      Instant expiresAt,
      Instant now
  ) {
    return new TutoringSession(
        sessionId != null ? sessionId : UUID.randomUUID(),
        classId,
        tutorId,
        studentId,
        startAt,
        endAt,
        mode,
        placeOrLink,
        repeatNote,
        message,
        SessionStatus.PROPOSED,
        proposedBy,
        null,
        null,
        null,
        expiresAt,
        null,
        null,
        null,
        null,
        false,
        null,
        null,
        0,
        now
    );
  }

  /**
   * Alias cho {@link #createProposed}.
   */
  public static TutoringSession propose(
      UUID sessionId,
      UUID classId,
      UUID tutorId,
      UUID studentId,
      Instant startAt,
      Instant endAt,
      SessionMode mode,
      String placeOrLink,
      String repeatNote,
      String message,
      UUID proposedBy,
      Instant expiresAt,
      Instant now
  ) {
    return createProposed(
        sessionId, classId, tutorId, studentId, startAt, endAt, mode,
        placeOrLink, repeatNote, message, proposedBy, expiresAt, now
    );
  }

  /**
   * Factory method khởi tạo trực tiếp một buổi học đã được xác nhận (trạng thái {@link SessionStatus#SCHEDULED}).
   * Dùng cho nghiệp vụ đổi lịch định kỳ UC4.8 khi cả hai bên đã thống nhất qua quy trình đồng thuận.
   */
  public static TutoringSession createScheduled(
      UUID sessionId,
      UUID classId,
      UUID tutorId,
      UUID studentId,
      Instant startAt,
      Instant endAt,
      SessionMode mode,
      String placeOrLink,
      String repeatNote,
      String message,
      UUID proposedBy,
      UUID respondedBy,
      Instant now
  ) {
    return new TutoringSession(
        sessionId != null ? sessionId : UUID.randomUUID(),
        classId,
        tutorId,
        studentId,
        startAt,
        endAt,
        mode,
        placeOrLink,
        repeatNote,
        message,
        SessionStatus.SCHEDULED,
        proposedBy,
        respondedBy,
        now,
        "Đổi lịch học định kỳ đã được hai bên đồng thuận",
        startAt,
        null,
        null,
        null,
        null,
        false,
        null,
        null,
        0,
        now
    );
  }

  /**
   * Chấp nhận đề xuất buổi học -> chuyển sang trạng thái {@link SessionStatus#SCHEDULED}.
   */
  public void accept(UUID responderId, Instant now) {
    validateNotTerminal();
    if (this.status != SessionStatus.PROPOSED) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Chỉ có thể chấp nhận buổi học khi ở trạng thái PROPOSED. Hiện tại: " + this.status
      );
    }
    if (this.expiresAt.isBefore(now)) {
      this.status = SessionStatus.EXPIRED;
      throw new InvalidOperationException(ErrorCode.PROPOSAL_EXPIRED, "Đề xuất buổi học đã hết hạn phản hồi");
    }

    this.status = SessionStatus.SCHEDULED;
    this.respondedBy = Objects.requireNonNull(responderId, "responderId không được null");
    this.respondedAt = Objects.requireNonNull(now, "now không được null");
  }

  /**
   * Từ chối đề xuất buổi học -> chuyển sang trạng thái {@link SessionStatus#REJECTED}.
   */
  public void reject(UUID responderId, String reason, Instant now) {
    validateNotTerminal();
    if (this.status != SessionStatus.PROPOSED) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Chỉ có thể từ chối buổi học khi ở trạng thái PROPOSED. Hiện tại: " + this.status
      );
    }

    this.status = SessionStatus.REJECTED;
    this.respondedBy = Objects.requireNonNull(responderId, "responderId không được null");
    this.respondedAt = Objects.requireNonNull(now, "now không được null");
    this.responseReason = reason;
  }

  /**
   * Người đề xuất tự rút lại yêu cầu -> chuyển sang trạng thái {@link SessionStatus#WITHDRAWN}.
   */
  public void withdraw(UUID actorId, Instant now) {
    validateNotTerminal();
    if (!this.proposedBy.equals(actorId)) {
      throw new InvalidOperationException(ErrorCode.FORBIDDEN, "Chỉ người đề xuất mới có quyền rút lại yêu cầu");
    }
    if (this.status != SessionStatus.PROPOSED) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Chỉ có thể rút lại khi buổi học đang ở trạng thái PROPOSED"
      );
    }

    this.status = SessionStatus.WITHDRAWN;
    this.cancelledBy = actorId;
    this.cancelledAt = now;
    this.cancelComment = "Người đề xuất tự rút lại yêu cầu";
  }

  /**
   * Hết hạn phản hồi -> chuyển sang trạng thái {@link SessionStatus#EXPIRED}.
   */
  public void expire(Instant now) {
    validateNotTerminal();
    if (this.status != SessionStatus.PROPOSED) {
      throw new InvalidOperationException(ErrorCode.INVALID_OPERATION, "Chỉ buổi học PROPOSED mới có thể hết hạn");
    }
    this.status = SessionStatus.EXPIRED;
    this.respondedAt = now;
    this.responseReason = "Tự động hết hạn do quá thời gian phản hồi";
  }

  /**
   * Hủy buổi học đã được lên lịch (SCHEDULED) -> chuyển sang trạng thái {@link SessionStatus#CANCELLED}.
   */
  public void cancel(UUID actorId, CancelReason reason, String comment, boolean isLate, Instant now) {
    validateNotTerminal();
    if (this.status != SessionStatus.SCHEDULED) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Chỉ có thể hủy buổi học khi đang ở trạng thái SCHEDULED. Hiện tại: " + this.status
      );
    }

    this.status = SessionStatus.CANCELLED;
    this.cancelledBy = Objects.requireNonNull(actorId, "cancelledBy không được null");
    this.cancelledAt = Objects.requireNonNull(now, "cancelledAt không được null");
    this.cancelReason = reason;
    this.cancelComment = comment;
    this.lateCancel = isLate;
  }

  /**
   * Tự động hủy buổi học do lớp học kết thúc (Cascade Cancellation).
   * Áp dụng cho cả buổi học PROPOSED và SCHEDULED chưa diễn ra, giải phóng khóa PostgreSQL GiST exclusion.
   */
  public void cancelDueToClassEnded(UUID actorId, String reason, Instant now) {
    if (this.status.isTerminal()) {
      return;
    }
    this.status = SessionStatus.CANCELLED;
    this.cancelledBy = actorId != null ? actorId : this.tutorId;
    this.cancelledAt = Objects.requireNonNull(now, "now không được null");
    this.cancelReason = CancelReason.OTHER;
    this.cancelComment = (reason != null && !reason.isBlank()) ? reason : "Lớp học đã kết thúc";
    this.lateCancel = false;
  }

  /**
   * Ghi nhận kết quả buổi học (COMPLETED hoặc ABSENT) sau khi thời gian học đã kết thúc.
   */
  public void recordOutcome(SessionStatus outcome, UUID outcomeRecordedBy, Instant now) {
    validateNotTerminal();
    if (this.status != SessionStatus.SCHEDULED) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Chỉ có thể ghi nhận kết quả cho buổi học đang SCHEDULED. Hiện tại: " + this.status
      );
    }
    if (outcome != SessionStatus.COMPLETED && outcome != SessionStatus.ABSENT) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Kết quả chỉ có thể là COMPLETED hoặc ABSENT");
    }

    this.status = outcome;
    this.outcomeRecordedBy = Objects.requireNonNull(outcomeRecordedBy, "outcomeRecordedBy không được null");
    this.outcomeRecordedAt = Objects.requireNonNull(now, "now không được null");
  }

  /**
   * Cập nhật thời gian mới khi đổi lịch thành công (UC4.4).
   */
  public void updateTime(Instant newStartAt, Instant newEndAt) {
    this.startAt = Objects.requireNonNull(newStartAt, "newStartAt không được null");
    this.endAt = Objects.requireNonNull(newEndAt, "newEndAt không được null");
  }

  private void validateNotTerminal() {
    if (this.status.isTerminal()) {
      throw new InvalidOperationException(
          ErrorCode.SESSION_ALREADY_FINALIZED,
          "Buổi học đã kết thúc ở trạng thái " + this.status + ", không thể thay đổi!"
      );
    }
  }

  // ==================== GETTERS ====================
  public UUID getSessionId() { return sessionId; }
  public UUID getClassId() { return classId; }
  public UUID getTutorId() { return tutorId; }
  public UUID getStudentId() { return studentId; }
  public Instant getStartAt() { return startAt; }
  public Instant getEndAt() { return endAt; }
  public SessionMode getMode() { return mode; }
  public String getPlaceOrLink() { return placeOrLink; }
  public String getRepeatNote() { return repeatNote; }
  public String getMessage() { return message; }
  public SessionStatus getStatus() { return status; }
  public UUID getProposedBy() { return proposedBy; }
  public UUID getRespondedBy() { return respondedBy; }
  public Instant getRespondedAt() { return respondedAt; }
  public String getResponseReason() { return responseReason; }
  public Instant getExpiresAt() { return expiresAt; }
  public UUID getCancelledBy() { return cancelledBy; }
  public Instant getCancelledAt() { return cancelledAt; }
  public CancelReason getCancelReason() { return cancelReason; }
  public String getCancelComment() { return cancelComment; }
  public boolean isLateCancel() { return lateCancel; }
  public UUID getOutcomeRecordedBy() { return outcomeRecordedBy; }
  public Instant getOutcomeRecordedAt() { return outcomeRecordedAt; }
  public int getVersion() { return version; }
  public Instant getCreatedAt() { return createdAt; }
}
