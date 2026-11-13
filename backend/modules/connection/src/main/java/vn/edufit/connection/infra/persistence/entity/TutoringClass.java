package vn.edufit.connection.infra.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "class")
public class TutoringClass {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "class_id") private UUID id;
  @Column(name = "connection_request_id", nullable = false) private UUID connectionRequestId;
  @Column(name = "student_id", nullable = false) private UUID studentId;
  @Column(name = "tutor_id", nullable = false) private UUID tutorId;
  @Column(name = "need_id", nullable = false) private UUID needId;
  @Column(name = "price_per_session", nullable = false, updatable = false) private Long pricePerSession;
  @Column(name = "status", nullable = false) private String status;
  @Column(name = "started_at", nullable = false) private Instant startedAt;
  @Column(name = "ended_at") private Instant endedAt;
  @Column(name = "end_reason") private String endReason;
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(name = "agreed_slots", columnDefinition = "jsonb") private java.util.List<String> agreedSlots;

  public java.util.List<String> getAgreedSlots() { return agreedSlots; }
  public void updateAgreedSlots(java.util.List<String> slots) {
    if (slots == null || slots.isEmpty()) throw new IllegalArgumentException("Lịch thỏa thuận không được rỗng");
    agreedSlots = java.util.List.copyOf(slots);
  }

  protected TutoringClass() {}
  /** Học phí mỗi buổi được chốt từ gói dạy khi gia sư nhận yêu cầu; thanh toán dùng giá này. */
  public TutoringClass(ConnectionRequest request, long pricePerSession, Instant now) {
    connectionRequestId = request.getId();
    studentId = request.getStudentId();
    tutorId = request.getTutorId();
    needId = request.getNeedId();
    this.pricePerSession = pricePerSession;
    status = "ACTIVE";
    startedAt = now;
  }
  public UUID getId() { return id; }
  public UUID getConnectionRequestId() { return connectionRequestId; }
  public UUID getStudentId() { return studentId; }
  public UUID getTutorId() { return tutorId; }
  public UUID getNeedId() { return needId; }
  public Long getPricePerSession() { return pricePerSession; }
  public String getStatus() { return status; }
  public Instant getStartedAt() { return startedAt; }
  public Instant getEndedAt() { return endedAt; }
  public String getEndReason() { return endReason; }

  public void endClass(String reason, Instant now) {
    if (!"ACTIVE".equals(this.status)) {
      throw new IllegalStateException("Chỉ có thể kết thúc lớp học đang hoạt động");
    }
    this.status = "ENDED";
    this.endedAt = now;
    this.endReason = reason;
  }
}
