package vn.edufit.connection.infra.persistence.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Detach only the initial read, then reload under lock; preserve unrelated managed writes. */
@Repository
public class FreshConnectionRowRepositoryImpl implements FreshConnectionRowRepository {
  @PersistenceContext private EntityManager entityManager;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public <T> Optional<T> lockFresh(Class<T> entityType, UUID id) {
    T initial = entityManager.find(entityType, id);
    if (initial == null) return Optional.empty();
    entityManager.detach(initial);
    return Optional.ofNullable(entityManager.find(entityType, id, LockModeType.PESSIMISTIC_WRITE));
  }
}
