package vn.edufit.progress.infra.persistence.mapper;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.MilestoneStatus;
import vn.edufit.progress.domain.model.PlanHistory;
import vn.edufit.progress.domain.model.SessionNote;
import vn.edufit.progress.infra.persistence.entity.LearningPlanJpaEntity;
import vn.edufit.progress.infra.persistence.entity.MilestoneJpaEntity;
import vn.edufit.progress.infra.persistence.entity.PlanHistoryJpaEntity;
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
        entity.getCreatedAt()
    );
  }

  public MilestoneJpaEntity toJpaEntity(Milestone domain) {
    if (domain == null) return null;
    return MilestoneJpaEntity.builder()
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
}
