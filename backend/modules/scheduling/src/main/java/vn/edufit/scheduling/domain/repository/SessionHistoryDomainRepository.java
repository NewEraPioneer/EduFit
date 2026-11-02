package vn.edufit.scheduling.domain.repository;

import java.util.List;
import java.util.UUID;
import vn.edufit.scheduling.domain.model.SessionHistory;

/**
 * Domain Repository interface quản lý lịch sử biến động buổi học (Audit Trail).
 */
public interface SessionHistoryDomainRepository {

  SessionHistory save(SessionHistory history);

  List<SessionHistory> findBySessionId(UUID sessionId);
}
