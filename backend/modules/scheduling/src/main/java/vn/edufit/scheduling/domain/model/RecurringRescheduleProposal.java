package vn.edufit.scheduling.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Domain Aggregate đại diện cho một đề xuất đổi lịch học định kỳ của lớp học (UC4.8).
 *
 * <p>Theo Database Invariant (R4):
 * Không thêm bảng cơ sở dữ liệu mới; các đề xuất đổi lịch định kỳ đang chờ xử lý (in-flight)
 * được quản lý an toàn trên bộ nhớ RAM có thời gian hết hạn (TTL). Khi được đối tác chấp thuận,
 * các buổi học cụ thể mới được sinh ra và lưu bền vững vào bảng {@code tutoring_session}.
 */
public class RecurringRescheduleProposal {

  private final UUID proposalId;
  private final UUID classId;
  private final UUID tutorId;
  private final UUID studentId;
  private final UUID proposedBy;
  private final LocalDate effectiveFrom;
  private final LocalDate effectiveTo;
  private final List<String> newWeeklySlots;
  private final SessionMode defaultMode;
  private final String placeOrLink;
  private final String message;
  private ProposalStatus status;
  private UUID respondedBy;
  private Instant respondedAt;
  private String responseReason;
  private final Instant expiresAt;
  private final Instant createdAt;

  public RecurringRescheduleProposal(
      UUID proposalId,
      UUID classId,
      UUID tutorId,
      UUID studentId,
      UUID proposedBy,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      List<String> newWeeklySlots,
      SessionMode defaultMode,
      String placeOrLink,
      String message,
      ProposalStatus status,
      UUID respondedBy,
      Instant respondedAt,
      String responseReason,
      Instant expiresAt,
      Instant createdAt
  ) {
    this.proposalId = Objects.requireNonNull(proposalId, "proposalId không được null");
    this.classId = Objects.requireNonNull(classId, "classId không được null");
    this.tutorId = Objects.requireNonNull(tutorId, "tutorId không được null");
    this.studentId = Objects.requireNonNull(studentId, "studentId không được null");
    this.proposedBy = Objects.requireNonNull(proposedBy, "proposedBy không được null");
    this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom không được null");
    this.effectiveTo = Objects.requireNonNull(effectiveTo, "effectiveTo không được null");
    this.newWeeklySlots = Collections.unmodifiableList(
        Objects.requireNonNull(newWeeklySlots, "newWeeklySlots không được null")
    );
    this.defaultMode = defaultMode != null ? defaultMode : SessionMode.ONLINE;
    this.placeOrLink = placeOrLink;
    this.message = message;
    this.status = Objects.requireNonNull(status, "status không được null");
    this.respondedBy = respondedBy;
    this.respondedAt = respondedAt;
    this.responseReason = responseReason;
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt không được null");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt không được null");
  }

  public static RecurringRescheduleProposal create(
      UUID proposalId,
      UUID classId,
      UUID tutorId,
      UUID studentId,
      UUID proposedBy,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      List<String> newWeeklySlots,
      SessionMode defaultMode,
      String placeOrLink,
      String message,
      Instant expiresAt,
      Instant now
  ) {
    return new RecurringRescheduleProposal(
        proposalId != null ? proposalId : UUID.randomUUID(),
        classId,
        tutorId,
        studentId,
        proposedBy,
        effectiveFrom,
        effectiveTo,
        newWeeklySlots,
        defaultMode,
        placeOrLink,
        message,
        ProposalStatus.PENDING,
        null,
        null,
        null,
        expiresAt,
        now
    );
  }

  public void accept(UUID responderId, Instant now) {
    validatePending(now);
    this.status = ProposalStatus.ACCEPTED;
    this.respondedBy = Objects.requireNonNull(responderId, "responderId không được null");
    this.respondedAt = Objects.requireNonNull(now, "now không được null");
  }

  public void reject(UUID responderId, String reason, Instant now) {
    validatePending(now);
    this.status = ProposalStatus.REJECTED;
    this.respondedBy = Objects.requireNonNull(responderId, "responderId không được null");
    this.respondedAt = Objects.requireNonNull(now, "now không được null");
    this.responseReason = reason;
  }

  public void expire(Instant now) {
    if (this.status == ProposalStatus.PENDING) {
      this.status = ProposalStatus.EXPIRED;
      this.respondedAt = now;
      this.responseReason = "Tự động hết hạn do quá hạn phản hồi";
    }
  }

  public boolean isPending(Instant now) {
    if (this.status == ProposalStatus.PENDING && this.expiresAt.isBefore(now)) {
      expire(now);
      return false;
    }
    return this.status == ProposalStatus.PENDING;
  }

  private void validatePending(Instant now) {
    if (this.status != ProposalStatus.PENDING) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Đề xuất đổi lịch định kỳ không ở trạng thái PENDING. Trạng thái hiện tại: " + this.status
      );
    }
    if (this.expiresAt.isBefore(now)) {
      this.status = ProposalStatus.EXPIRED;
      throw new InvalidOperationException(
          ErrorCode.PROPOSAL_EXPIRED,
          "Đề xuất đổi lịch định kỳ đã hết thời hạn phản hồi"
      );
    }
  }

  // Getters
  public UUID getProposalId() { return proposalId; }
  public UUID getClassId() { return classId; }
  public UUID getTutorId() { return tutorId; }
  public UUID getStudentId() { return studentId; }
  public UUID getProposedBy() { return proposedBy; }
  public LocalDate getEffectiveFrom() { return effectiveFrom; }
  public LocalDate getEffectiveTo() { return effectiveTo; }
  public List<String> getNewWeeklySlots() { return newWeeklySlots; }
  public SessionMode getDefaultMode() { return defaultMode; }
  public String getPlaceOrLink() { return placeOrLink; }
  public String getMessage() { return message; }
  public ProposalStatus getStatus() { return status; }
  public UUID getRespondedBy() { return respondedBy; }
  public Instant getRespondedAt() { return respondedAt; }
  public String getResponseReason() { return responseReason; }
  public Instant getExpiresAt() { return expiresAt; }
  public Instant getCreatedAt() { return createdAt; }
}
