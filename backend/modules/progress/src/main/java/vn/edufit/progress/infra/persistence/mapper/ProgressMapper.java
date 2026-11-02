package vn.edufit.progress.infra.persistence.mapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import vn.edufit.progress.domain.model.AttitudeChip;
import vn.edufit.progress.domain.model.HomeworkStatus;
import vn.edufit.progress.domain.model.IndependenceLevel;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.MilestoneAssessment;
import vn.edufit.progress.domain.model.MilestoneCriterion;
import vn.edufit.progress.domain.model.MilestoneStatus;
import vn.edufit.progress.domain.model.PlanHistory;
import vn.edufit.progress.domain.model.SessionFeedback;
import vn.edufit.progress.domain.model.SessionNote;
import vn.edufit.progress.infra.persistence.entity.LearningPlanJpaEntity;
import vn.edufit.progress.infra.persistence.entity.MilestoneAssessmentJpaEntity;
import vn.edufit.progress.infra.persistence.entity.MilestoneCriterionJpaEntity;
import vn.edufit.progress.infra.persistence.entity.MilestoneJpaEntity;
import vn.edufit.progress.infra.persistence.entity.PlanHistoryJpaEntity;
import vn.edufit.progress.infra.persistence.entity.SessionFeedbackJpaEntity;
import vn.edufit.progress.infra.persistence.entity.SessionNoteJpaEntity;

@Component
public class ProgressMapper {

  public LearningPlan toDomain(LearningPlanJpaEntity entity) {
    if (entity == null) return null;

    List<Milestone> milestones = new ArrayList<>();
    if (entity.getMilestones() != null) {
      for (MilestoneJpaEntity m : entity.getMilestones()) {
        milestones.add(toDomain(m));
      }
    }

    return new LearningPlan(
        entity.getPlanId(),
        entity.getClassId(),
        entity.getSummary(),
        entity.getVersion(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        milestones
    );
  }

  public LearningPlanJpaEntity toJpaEntity(LearningPlan domain) {
    if (domain == null) return null;

    LearningPlanJpaEntity entity = LearningPlanJpaEntity.builder()
        .planId(domain.getPlanId())
        .classId(domain.getClassId())
        .summary(domain.getSummary())
        .version(domain.getVersion())
        .createdAt(domain.getCreatedAt())
        .updatedAt(domain.getUpdatedAt())
        .milestones(new ArrayList<>())
        .build();

    if (domain.getMilestones() != null) {
      for (Milestone m : domain.getMilestones()) {
        MilestoneJpaEntity mEntity = toJpaEntity(m);
        entity.addMilestone(mEntity);
      }
    }

    return entity;
  }

  public Milestone toDomain(MilestoneJpaEntity entity) {
    if (entity == null) return null;
    return new Milestone(
        entity.getMilestoneId(),
        entity.getPlan() != null ? entity.getPlan().getPlanId() : null,
        entity.getTitle(),
        entity.getDescription(),
        entity.getTargetDate(),
        entity.getWeight(),
        MilestoneStatus.valueOf(entity.getStatus()),
        entity.getAchievedDate(),
        entity.isArchived(),
        entity.getSortOrder(),
        entity.getCreatedAt(),
        entity.getCriteria().stream()
            .map(c -> new MilestoneCriterion(
                c.getCriterionId(), c.getContent(), c.getSortOrder(), c.isChecked(), c.getCheckedAt(), c.getCreatedAt()))
            .toList()
    );
  }

  public MilestoneJpaEntity toJpaEntity(Milestone domain) {
    if (domain == null) return null;
    MilestoneJpaEntity entity = MilestoneJpaEntity.builder()
        .milestoneId(domain.getMilestoneId())
        .title(domain.getTitle())
        .description(domain.getDescription())
        .targetDate(domain.getTargetDate())
        .weight((short) domain.getWeight())
        .status(domain.getStatus().name())
        .achievedDate(domain.getAchievedDate())
        .archived(domain.isArchived())
        .sortOrder(domain.getSortOrder())
        .createdAt(domain.getCreatedAt())
        .build();
    for (MilestoneCriterion c : domain.getCriteria()) {
      entity.addCriterion(MilestoneCriterionJpaEntity.builder()
          .criterionId(c.getCriterionId())
          .content(c.getContent())
          .sortOrder(c.getSortOrder())
          .checked(c.isChecked())
          .checkedAt(c.getCheckedAt())
          .createdAt(c.getCreatedAt())
          .build());
    }
    return entity;
  }

  public PlanHistory toDomain(PlanHistoryJpaEntity entity) {
    if (entity == null) return null;
    return new PlanHistory(
        entity.getHistoryId(),
        entity.getPlanId(),
        entity.getMilestoneId(),
        entity.getAction(),
        entity.getActorUserId(),
        entity.getOldValue(),
        entity.getNewValue(),
        entity.getCreatedAt()
    );
  }

  public PlanHistoryJpaEntity toJpaEntity(PlanHistory domain) {
    if (domain == null) return null;
    return PlanHistoryJpaEntity.builder()
        .historyId(domain.getHistoryId())
        .planId(domain.getPlanId())
        .milestoneId(domain.getMilestoneId())
        .action(domain.getAction())
        .actorUserId(domain.getActorUserId())
        .oldValue(domain.getOldValue())
        .newValue(domain.getNewValue())
        .createdAt(domain.getCreatedAt())
        .build();
  }

  public SessionNote toDomain(SessionNoteJpaEntity entity) {
    if (entity == null) return null;
    return new SessionNote(
        entity.getNoteId(),
        entity.getSessionId(),
        entity.getTutorId(),
        entity.getNoteText(),
        entity.getCreatedAt()
    );
  }

  public SessionNoteJpaEntity toJpaEntity(SessionNote domain) {
    if (domain == null) return null;
    return SessionNoteJpaEntity.builder()
        .noteId(domain.getNoteId())
        .sessionId(domain.getSessionId())
        .tutorId(domain.getTutorId())
        .noteText(domain.getNoteText())
        .createdAt(domain.getCreatedAt())
        .build();
  }

  public SessionFeedbackJpaEntity toJpaEntity(SessionFeedback domain) {
    return SessionFeedbackJpaEntity.builder()
        .feedbackId(domain.getFeedbackId())
        .sessionId(domain.getSessionId())
        .classId(domain.getClassId())
        .homeworkStatus(domain.getHomeworkStatus().name())
        .attitudeChips(domain.getAttitudeChips().isEmpty()
            ? null
            : domain.getAttitudeChips().stream().map(Enum::name).collect(Collectors.joining(",")))
        .parentMessage(domain.getParentMessage())
        .submittedLate(domain.isSubmittedLate())
        .createdBy(domain.getCreatedBy())
        .createdAt(domain.getCreatedAt())
        .build();
  }

  public SessionFeedback toDomain(SessionFeedbackJpaEntity entity, List<MilestoneAssessment> assessments) {
    List<AttitudeChip> chips = entity.getAttitudeChips() == null || entity.getAttitudeChips().isBlank()
        ? List.of()
        : Arrays.stream(entity.getAttitudeChips().split(",")).map(String::trim).map(AttitudeChip::valueOf).toList();
    return new SessionFeedback(
        entity.getFeedbackId(),
        entity.getSessionId(),
        entity.getClassId(),
        HomeworkStatus.valueOf(entity.getHomeworkStatus()),
        chips,
        entity.getParentMessage(),
        entity.getCreatedBy(),
        entity.getCreatedAt(),
        entity.isSubmittedLate(),
        assessments
    );
  }

  public MilestoneAssessmentJpaEntity toJpaEntity(MilestoneAssessment domain) {
    return MilestoneAssessmentJpaEntity.builder()
        .assessmentId(domain.getAssessmentId())
        .milestoneId(domain.getMilestoneId())
        .sessionId(domain.getSessionId())
        .level((short) domain.getLevel().value())
        .correctCount(domain.getCorrectCount() != null ? domain.getCorrectCount().shortValue() : null)
        .totalCount(domain.getTotalCount() != null ? domain.getTotalCount().shortValue() : null)
        .evidenceNote(domain.getEvidenceNote())
        .assessedBy(domain.getAssessedBy())
        .createdAt(domain.getCreatedAt())
        .build();
  }

  public MilestoneAssessment toDomain(MilestoneAssessmentJpaEntity entity) {
    return new MilestoneAssessment(
        entity.getAssessmentId(),
        entity.getMilestoneId(),
        entity.getSessionId(),
        IndependenceLevel.fromValue(entity.getLevel()),
        entity.getCorrectCount() != null ? entity.getCorrectCount().intValue() : null,
        entity.getTotalCount() != null ? entity.getTotalCount().intValue() : null,
        entity.getEvidenceNote(),
        entity.getAssessedBy(),
        entity.getCreatedAt()
    );
  }
}
