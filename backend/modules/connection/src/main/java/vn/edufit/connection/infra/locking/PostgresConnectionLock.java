package vn.edufit.connection.infra.locking;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.connection.application.port.ConnectionLock;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class PostgresConnectionLock implements ConnectionLock {
  private final EntityManager entityManager;

  public PostgresConnectionLock(EntityManager entityManager) { this.entityManager = entityManager; }

  @Override public void lockStudent(UUID studentId) { lock("student:" + studentId); }
  @Override public void lockInvitationQuota(UUID userId) { lock("invite-quota:" + userId); }

  @Override public void lockInvitationPair(UUID firstUserId, UUID secondUserId) {
    String pair = firstUserId.compareTo(secondUserId) < 0
        ? firstUserId + ":" + secondUserId : secondUserId + ":" + firstUserId;
    lock("invite:" + pair);
  }

  private void lock(String key) {
    if (!entityManager.isJoinedToTransaction()) {
      throw new IllegalStateException("Connection locks require a joined transaction");
    }
    entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(cast(:key as text), 0))")
        .setParameter("key", "connection:" + key).getSingleResult();
  }
}
