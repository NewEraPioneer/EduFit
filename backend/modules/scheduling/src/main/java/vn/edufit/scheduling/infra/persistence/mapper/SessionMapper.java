package vn.edufit.scheduling.infra.persistence.mapper;

import org.springframework.stereotype.Component;
import vn.edufit.scheduling.domain.model.CancelReason;
import vn.edufit.scheduling.domain.model.ProposalStatus;
import vn.edufit.scheduling.domain.model.SessionHistory;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionRescheduleProposal;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.infra.persistence.entity.SessionHistoryJpaEntity;
import vn.edufit.scheduling.infra.persistence.entity.SessionRescheduleProposalJpaEntity;
import vn.edufit.scheduling.infra.persistence.entity.TutoringSessionJpaEntity;

/**
 * Mapper chuyển đổi hai chiều giữa Domain Model (POJO) và JPA Entity.
 *
 * <p>Mục đích kiến trúc:
 * <ul>
 *   <li>Bảo vệ tầng Domain độc lập 100% với Hibernate/JPA.</li>
 *   <li>Đảm bảo mọi chuyển đổi dữ liệu, Enum parse, và ánh xạ trường đều được tập trung tại một nơi.</li>
 * </ul>
 */
@Component
public class SessionMapper {

  /**
   * Chuyển đổi từ Domain Model {@link TutoringSession} sang JPA Entity {@link TutoringSessionJpaEntity}.
   */
  public TutoringSessionJpaEntity toJpaEntity(TutoringSession domain) {
    if (domain == null) {
      return null;
    }
    return TutoringSessionJpaEntity.builder()
        .sessionId(domain.getSessionId())
        .classId(domain.getClassId())
        .tutorId(domain.getTutorId())
        .studentId(domain.getStudentId())
        .startAt(domain.getStartAt())
        .endAt(domain.getEndAt())
        .mode(domain.getMode().name())
        .placeOrLink(domain.getPlaceOrLink())
        .repeatNote(domain.getRepeatNote())
        .message(domain.getMessage())
        .status(domain.getStatus().name())
        .proposedBy(domain.getProposedBy())
        .respondedBy(domain.getRespondedBy())
        .respondedAt(domain.getRespondedAt())
        .responseReason(domain.getResponseReason())
        .expiresAt(domain.getExpiresAt())
        .cancelledBy(domain.getCancelledBy())
        .cancelledAt(domain.getCancelledAt())
        .cancelReason(domain.getCancelReason() != null ? domain.getCancelReason().name() : null)
        .cancelComment(domain.getCancelComment())
        .lateCancel(domain.isLateCancel())
        .outcomeRecordedBy(domain.getOutcomeRecordedBy())
        .outcomeRecordedAt(domain.getOutcomeRecordedAt())
        .version(domain.getVersion())
        .createdAt(domain.getCreatedAt())
        .build();
  }

  /**
   * Chuyển đổi từ JPA Entity {@link TutoringSessionJpaEntity} sang Domain Model {@link TutoringSession}.
   */
  public TutoringSession toDomain(TutoringSessionJpaEntity entity) {
    if (entity == null) {
      return null;
    }
    return new TutoringSession(
        entity.getSessionId(),
        entity.getClassId(),
        entity.getTutorId(),
        entity.getStudentId(),
        entity.getStartAt(),
        entity.getEndAt(),
        SessionMode.valueOf(entity.getMode()),
        entity.getPlaceOrLink(),
        entity.getRepeatNote(),
        entity.getMessage(),
        SessionStatus.valueOf(entity.getStatus()),
        entity.getProposedBy(),
        entity.getRespondedBy(),
        entity.getRespondedAt(),
        entity.getResponseReason(),
        entity.getExpiresAt(),
        entity.getCancelledBy(),
        entity.getCancelledAt(),
        entity.getCancelReason() != null ? CancelReason.valueOf(entity.getCancelReason()) : null,
        entity.getCancelComment(),
        entity.isLateCancel(),
        entity.getOutcomeRecordedBy(),
        entity.getOutcomeRecordedAt(),
        entity.getVersion(),
        entity.getCreatedAt()
    );
  }

  /**
   * Chuyển đổi từ Domain Model sang Entity cho {@link SessionRescheduleProposal}.
   */
  public SessionRescheduleProposalJpaEntity toJpaEntity(SessionRescheduleProposal domain) {
    if (domain == null) {
      return null;
    }
    return SessionRescheduleProposalJpaEntity.builder()
        .proposalId(domain.getProposalId())
        .sessionId(domain.getSessionId())
        .proposedBy(domain.getProposedBy())
        .newStartAt(domain.getNewStartAt())
        .newEndAt(domain.getNewEndAt())
        .reason(domain.getReason())
        .status(domain.getStatus().name())
        .expiresAt(domain.getExpiresAt())
        .respondedBy(domain.getRespondedBy())
        .respondedAt(domain.getRespondedAt())
        .responseReason(domain.getResponseReason())
        .createdAt(domain.getCreatedAt())
        .build();
  }

  /**
   * Chuyển đổi từ Entity sang Domain Model cho {@link SessionRescheduleProposal}.
   */
  public SessionRescheduleProposal toDomain(SessionRescheduleProposalJpaEntity entity) {
    if (entity == null) {
      return null;
    }
    return new SessionRescheduleProposal(
        entity.getProposalId(),
        entity.getSessionId(),
        entity.getProposedBy(),
        entity.getNewStartAt(),
        entity.getNewEndAt(),
        entity.getReason(),
        ProposalStatus.valueOf(entity.getStatus()),
        entity.getExpiresAt(),
        entity.getRespondedBy(),
        entity.getRespondedAt(),
        entity.getResponseReason(),
        entity.getCreatedAt()
    );
  }

  /**
   * Chuyển đổi từ Domain Model sang Entity cho {@link SessionHistory}.
   */
  public SessionHistoryJpaEntity toJpaEntity(SessionHistory domain) {
    if (domain == null) {
      return null;
    }
    return SessionHistoryJpaEntity.builder()
        .historyId(domain.getHistoryId())
        .sessionId(domain.getSessionId())
        .rescheduleProposalId(domain.getRescheduleProposalId())
        .action(domain.getAction())
        .actorUserId(domain.getActorUserId())
        .reason(domain.getReason())
        .createdAt(domain.getCreatedAt())
        .build();
  }

  /**
   * Chuyển đổi từ Entity sang Domain Model cho {@link SessionHistory}.
   */
  public SessionHistory toDomain(SessionHistoryJpaEntity entity) {
    if (entity == null) {
      return null;
    }
    return new SessionHistory(
        entity.getHistoryId(),
        entity.getSessionId(),
        entity.getRescheduleProposalId(),
        entity.getAction(),
        entity.getActorUserId(),
        entity.getReason(),
        entity.getCreatedAt()
    );
  }
}
