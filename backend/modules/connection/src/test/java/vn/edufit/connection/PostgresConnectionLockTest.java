package vn.edufit.connection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.edufit.connection.infra.locking.PostgresConnectionLock;

class PostgresConnectionLockTest {
  private final EntityManager em = mock(EntityManager.class);
  private final Query query = mock(Query.class);
  private final PostgresConnectionLock lock = new PostgresConnectionLock(em);

  @Test void oppositeDirectionsUseSamePairKeyAndDistinctLockNamespaces() {
    when(em.isJoinedToTransaction()).thenReturn(true);
    when(em.createNativeQuery(anyString())).thenReturn(query);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    UUID a = new UUID(0, 1), b = new UUID(0, 2);
    lock.lockInvitationPair(a, b);
    lock.lockInvitationPair(b, a);
    lock.lockStudent(a);
    lock.lockInvitationQuota(a);
    verify(query, times(2)).setParameter("key", "connection:invite:" + a + ":" + b);
    verify(query).setParameter("key", "connection:student:" + a);
    verify(query).setParameter("key", "connection:invite-quota:" + a);
    verify(query, times(4)).getSingleResult();
  }

  @Test void nativeLockCannotRunOutsideJoinedTransaction() {
    assertThrows(IllegalStateException.class, () -> lock.lockStudent(UUID.randomUUID()));
    verify(em, never()).createNativeQuery(anyString());
  }
}
