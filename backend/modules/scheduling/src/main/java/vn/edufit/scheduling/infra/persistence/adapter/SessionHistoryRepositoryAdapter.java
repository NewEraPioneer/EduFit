package vn.edufit.scheduling.infra.persistence.adapter;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edufit.scheduling.domain.model.SessionHistory;
import vn.edufit.scheduling.domain.repository.SessionHistoryDomainRepository;
import vn.edufit.scheduling.infra.persistence.entity.SessionHistoryJpaEntity;
import vn.edufit.scheduling.infra.persistence.mapper.SessionMapper;
import vn.edufit.scheduling.infra.persistence.repository.SpringDataSessionHistoryRepository;

/**
 * Outbound Persistence Adapter hiện thực hóa {@link SessionHistoryDomainRepository}.
 *
 * <p>Cung cấp khả năng lưu vết lịch sử biến động buổi học (Audit Trail)
 * thông qua Spring Data JPA và ánh xạ qua {@link SessionMapper}.
 */
@Component
@RequiredArgsConstructor
public class SessionHistoryRepositoryAdapter implements SessionHistoryDomainRepository {

  private final SpringDataSessionHistoryRepository springDataRepository;
  private final SessionMapper sessionMapper;

  @Override
  public SessionHistory save(SessionHistory history) {
    SessionHistoryJpaEntity entity = sessionMapper.toJpaEntity(history);
    SessionHistoryJpaEntity saved = springDataRepository.save(entity);
    return sessionMapper.toDomain(saved);
  }

  @Override
  public List<SessionHistory> findBySessionId(UUID sessionId) {
    return springDataRepository.findBySessionIdOrderByCreatedAtDesc(sessionId)
        .stream()
        .map(sessionMapper::toDomain)
        .toList();
  }
}
