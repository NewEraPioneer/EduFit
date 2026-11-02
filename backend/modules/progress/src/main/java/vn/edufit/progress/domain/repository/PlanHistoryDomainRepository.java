package vn.edufit.progress.domain.repository;

import java.util.List;
import java.util.UUID;
import vn.edufit.progress.domain.model.PlanHistory;

/**
 * Cổng giao tiếp kho dữ liệu (Repository Port) cho Lịch sử thay đổi {@link PlanHistory}.
 */
public interface PlanHistoryDomainRepository {

  PlanHistory save(PlanHistory history);

  List<PlanHistory> findByPlanId(UUID planId);
}
