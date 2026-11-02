package vn.edufit.scheduling.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.edufit.scheduling.domain.model.SessionRescheduleProposal;

/**
 * Domain Repository interface quản lý đề xuất dời lịch buổi học.
 */
public interface SessionRescheduleProposalDomainRepository {

  Optional<SessionRescheduleProposal> findById(UUID proposalId);

  Optional<SessionRescheduleProposal> findPendingBySessionId(UUID sessionId);

  SessionRescheduleProposal save(SessionRescheduleProposal proposal);

  List<SessionRescheduleProposal> findExpiredPendingProposals(Instant now);
}
