package vn.edufit.progress.domain.model;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Entity miền đại diện cho Biên bản ghi nhớ buổi học (Session Note) - Bất biến.
 * Java POJO thuần túy, không phụ thuộc Spring Boot hay JPA.
 */
public class SessionNote {

  private final UUID noteId;
  private final UUID sessionId;
  private final UUID tutorId;
  private final String noteText;
  private final Instant createdAt;

  public SessionNote(UUID noteId, UUID sessionId, UUID tutorId, String noteText, Instant createdAt) {
    if (sessionId == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "sessionId không được để trống");
    }
    if (tutorId == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "tutorId không được để trống");
    }
    this.noteId = noteId != null ? noteId : UUID.randomUUID();
    this.sessionId = sessionId;
    this.tutorId = tutorId;
    this.noteText = noteText;
    this.createdAt = createdAt != null ? createdAt : Instant.now();
  }

  public static SessionNote create(UUID sessionId, UUID tutorId, String noteText, Instant now) {
    return new SessionNote(UUID.randomUUID(), sessionId, tutorId, noteText, now != null ? now : Instant.now());
  }

  public UUID getNoteId() { return noteId; }
  public UUID getSessionId() { return sessionId; }
  public UUID getTutorId() { return tutorId; }
  public String getNoteText() { return noteText; }
  public Instant getCreatedAt() { return createdAt; }
}
