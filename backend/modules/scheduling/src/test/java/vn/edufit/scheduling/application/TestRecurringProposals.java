package vn.edufit.scheduling.application;

import java.util.*;
import vn.edufit.scheduling.domain.model.*;
import vn.edufit.scheduling.domain.repository.RecurringProposalRepository;

final class TestRecurringProposals implements RecurringProposalRepository {
  private final Map<UUID, RecurringRescheduleProposal> rows = new HashMap<>();
  public void lockClass(UUID id) {}
  public Optional<RecurringRescheduleProposal> findById(UUID id) { return Optional.ofNullable(rows.get(id)); }
  public Optional<RecurringRescheduleProposal> findByIdForUpdate(UUID id) { return findById(id); }
  public List<RecurringRescheduleProposal> findPendingByClassId(UUID id) {
    return rows.values().stream().filter(p -> p.getClassId().equals(id) && p.getStatus()==ProposalStatus.PENDING).toList();
  }
  public void save(RecurringRescheduleProposal p) { rows.put(p.getProposalId(), p); }
}
