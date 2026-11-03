package vn.edufit.progress.domain.model;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Một lần chấm mức độ tự lập của học sinh cho một cột mốc trong một buổi học.
 * Ghi nối tiếp (append-only): sửa thì thêm dòng mới, không cập nhật dòng cũ.
 */
public class MilestoneAssessment {

  public static final int MAX_TOTAL_COUNT = 100;
  public static final int MAX_EVIDENCE_LENGTH = 200;

  private final UUID assessmentId;
  private final UUID milestoneId;
  private final UUID sessionId;
  private final IndependenceLevel level;
  private final Integer correctCount;
  private final Integer totalCount;
  private final String evidenceNote;
  private final UUID assessedBy;
  private final Instant createdAt;

  public MilestoneAssessment(
      UUID assessmentId,
      UUID milestoneId,
      UUID sessionId,
      IndependenceLevel level,
      Integer correctCount,
      Integer totalCount,
      String evidenceNote,
      UUID assessedBy,
      Instant createdAt
  ) {
    if (milestoneId == null || sessionId == null || assessedBy == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Điểm chấm thiếu cột mốc, buổi học hoặc người chấm");
    }
    if (level == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Cần chọn mức độ tự lập cho cột mốc được dạy");
    }
    validateScore(correctCount, totalCount);
    String evidence = evidenceNote != null && !evidenceNote.isBlank() ? evidenceNote.trim() : null;
    if (evidence != null && evidence.length() > MAX_EVIDENCE_LENGTH) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED, "Câu bằng chứng tối đa " + MAX_EVIDENCE_LENGTH + " ký tự");
    }
    this.assessmentId = assessmentId != null ? assessmentId : UUID.randomUUID();
    this.milestoneId = milestoneId;
    this.sessionId = sessionId;
    this.level = level;
    this.correctCount = correctCount;
    this.totalCount = totalCount;
    this.evidenceNote = evidence;
    this.assessedBy = assessedBy;
    this.createdAt = createdAt != null ? createdAt : Instant.now();
  }

  private static void validateScore(Integer correct, Integer total) {
    if (correct == null && total == null) return;
    if (correct == null || total == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Cần nhập đủ cả số bài đúng và tổng số bài");
    }
    if (total < 1 || total > MAX_TOTAL_COUNT || correct < 0 || correct > total) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Số bài đúng phải từ 0 đến tổng số bài (tổng từ 1 đến " + MAX_TOTAL_COUNT + ")");
    }
  }

  public UUID getAssessmentId() { return assessmentId; }
  public UUID getMilestoneId() { return milestoneId; }
  public UUID getSessionId() { return sessionId; }
  public IndependenceLevel getLevel() { return level; }
  public Integer getCorrectCount() { return correctCount; }
  public Integer getTotalCount() { return totalCount; }
  public String getEvidenceNote() { return evidenceNote; }
  public boolean hasScore() { return correctCount != null && totalCount != null; }
  public boolean hasEvidence() { return hasScore() || evidenceNote != null; }
  public UUID getAssessedBy() { return assessedBy; }
  public Instant getCreatedAt() { return createdAt; }
}
