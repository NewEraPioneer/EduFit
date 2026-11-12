package vn.edufit.scheduling.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.edufit.scheduling.domain.model.RecurringRescheduleProposal;

public interface RecurringProposalRepository {
  void lockClass(UUID classId);
  Optional<RecurringRescheduleProposal> findById(UUID id);
  Optional<RecurringRescheduleProposal> findByIdForUpdate(UUID id);
  List<RecurringRescheduleProposal> findPendingByClassId(UUID classId);
  void save(RecurringRescheduleProposal proposal);
}
