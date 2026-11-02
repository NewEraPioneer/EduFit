package vn.edufit.notification.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA Entity biểu diễn bản ghi trong bảng {@code notification}.
 *
 * <p>Tuân thủ chuẩn thiết kế cơ sở dữ liệu EduFit (migration V1.07):
 * <ul>
 *   <li>{@code user_id}: Khóa ngoại tham chiếu người nhận thông báo trong bảng account.</li>
 *   <li>{@code about_student_id}: Khóa ngoại tham chiếu học sinh liên quan (hỗ trợ lọc cho phụ huynh theo BR-61).</li>
 *   <li>{@code is_read}: Cờ đánh dấu trạng thái đọc, mặc định là {@code false}.</li>
 * </ul>
 */
@Entity
@Table(name = "notification")
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "notification_id", nullable = false, updatable = false)
  private UUID notificationId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "about_student_id")
  private UUID aboutStudentId;

  @Column(name = "type", nullable = false, length = 60)
  private String type;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "short_text", nullable = false, length = 500)
  private String shortText;

  @Column(name = "target_type", length = 40)
  private String targetType;

  @Column(name = "target_id")
  private UUID targetId;

  @Column(name = "is_read", nullable = false)
  private boolean isRead;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Notification() {
    // Dành cho JPA Reflection
  }

  public Notification(
      UUID userId,
      UUID aboutStudentId,
      String type,
      String title,
      String shortText,
      String targetType,
      UUID targetId
  ) {
    this.userId = Objects.requireNonNull(userId, "userId không được phép null");
    this.aboutStudentId = aboutStudentId;
    this.type = Objects.requireNonNull(type, "type không được phép null");
    this.title = Objects.requireNonNull(title, "title không được phép null");
    this.shortText = Objects.requireNonNull(shortText, "shortText không được phép null");
    this.targetType = targetType;
    this.targetId = targetId;
    this.isRead = false;
    this.createdAt = Instant.now();
  }

  public Notification(
      UUID userId,
      UUID aboutStudentId,
      String type,
      String title,
      String shortText,
      String targetType,
      UUID targetId,
      Instant occurredAt
  ) {
    this.userId = Objects.requireNonNull(userId, "userId không được phép null");
    this.aboutStudentId = aboutStudentId;
    this.type = Objects.requireNonNull(type, "type không được phép null");
    this.title = Objects.requireNonNull(title, "title không được phép null");
    this.shortText = Objects.requireNonNull(shortText, "shortText không được phép null");
    this.targetType = targetType;
    this.targetId = targetId;
    this.isRead = false;
    this.createdAt = occurredAt != null ? occurredAt : Instant.now();
  }

  public void markAsRead() {
    this.isRead = true;
  }

  public UUID getNotificationId() {
    return notificationId;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getAboutStudentId() {
    return aboutStudentId;
  }

  public String getType() {
    return type;
  }

  public String getTitle() {
    return title;
  }

  public String getShortText() {
    return shortText;
  }

  public String getTargetType() {
    return targetType;
  }

  public UUID getTargetId() {
    return targetId;
  }

  public boolean isRead() {
    return isRead;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
