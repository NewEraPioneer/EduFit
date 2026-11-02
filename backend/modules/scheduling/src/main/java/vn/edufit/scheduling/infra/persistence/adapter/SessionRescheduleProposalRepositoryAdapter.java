package vn.edufit.scheduling.infra.persistence.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edufit.scheduling.domain.model.ProposalStatus;
import vn.edufit.scheduling.domain.model.SessionRescheduleProposal;
import vn.edufit.scheduling.domain.repository.SessionRescheduleProposalDomainRepository;
import vn.edufit.scheduling.infra.persistence.entity.SessionRescheduleProposalJpaEntity;
import vn.edufit.scheduling.infra.persistence.mapper.SessionMapper;
import vn.edufit.scheduling.infra.persistence.repository.SpringDataSessionRescheduleProposalRepository;

/**
 * Outbound Persistence Adapter hiện thực hóa {@link SessionRescheduleProposalDomainRepository}.
 *
 * <p>Cung cấp khả năng lưu trữ và truy vấn đề xuất dời lịch (Reschedule Proposals)
 * thông qua Spring Data JPA và ánh xạ qua {@link SessionMapper}.
 */
@Component
@RequiredArgsConstructor
public class SessionRescheduleProposalRepositoryAdapter
    implements SessionRescheduleProposalDomainRepository {

  private final SpringDataSessionRescheduleProposalRepository springDataRepository;
  private final SessionMapper sessionMapper;

  @Override
  public Optional<SessionRescheduleProposal> findById(UUID proposalId) {
    return springDataRepository.findById(proposalId)
        .map(sessionMapper::toDomain);
  }

  @Override
  public Optional<SessionRescheduleProposal> findPendingBySessionId(UUID sessionId) {
    return springDataRepository.findBySessionIdAndStatus(sessionId, ProposalStatus.PENDING.name())
        .map(sessionMapper::toDomain);
  }

  @Override
  public SessionRescheduleProposal save(SessionRescheduleProposal proposal) {
    SessionRescheduleProposalJpaEntity entity = sessionMapper.toJpaEntity(proposal);
    SessionRescheduleProposalJpaEntity saved = springDataRepository.saveAndFlush(entity);
    return sessionMapper.toDomain(saved);
  }

  @Override
  public List<SessionRescheduleProposal> findExpiredPendingProposals(Instant now) {
    return springDataRepository.findExpiredPendingProposals(now)
        .stream()
        .map(sessionMapper::toDomain)
        .toList();
  }
}
