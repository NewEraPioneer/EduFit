package vn.edufit.connection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edufit.connection.application.port.ConnectionLock;
import vn.edufit.connection.application.service.InvitationQuotaService;
import vn.edufit.connection.infra.persistence.entity.ConnectionInviteQuota;
import vn.edufit.connection.infra.persistence.repository.ConnectionInviteQuotaRepository;

class InvitationQuotaServiceTest {
  @Test void consumptionUsesInjectedClockExactlyAtResetBoundary() {
    Instant boundary = Instant.parse("2026-10-11T04:00:00Z");
    UUID actor = UUID.randomUUID();
    var quota = new ConnectionInviteQuota(actor, boundary.minusSeconds(3600));
    for (int i = 0; i < 10; i++) quota.consume(boundary.minusSeconds(3600));
    var repository = mock(ConnectionInviteQuotaRepository.class);
    when(repository.findById(actor)).thenReturn(Optional.of(quota));
    var result = new InvitationQuotaService(repository, mock(ConnectionLock.class),
        Clock.fixed(boundary, ZoneOffset.UTC)).consume(actor);
    assertTrue(result.allowed());
    assertEquals(boundary, result.checkedAt());
  }

  @Test void quotaLocksBeforeReadingAndCountsTenThenDeniesEleventh() {
    UUID actor = UUID.randomUUID();
    var repository = mock(ConnectionInviteQuotaRepository.class);
    var lock = mock(ConnectionLock.class);
    var quota = new ConnectionInviteQuota(actor, Instant.now());
    when(repository.findById(actor)).thenReturn(Optional.of(quota));
    var service = new InvitationQuotaService(repository, lock, Clock.systemUTC());
    for (int i = 0; i < 10; i++) assertTrue(service.consume(actor).allowed());
    assertFalse(service.consume(actor).allowed());
    var order = inOrder(lock, repository);
    order.verify(lock).lockInvitationQuota(actor);
    order.verify(repository).findById(actor);
    verify(repository, times(10)).save(quota);
  }

  @ParameterizedTest @ValueSource(longs = {-1, 0, 1})
  void quotaResetAtExactlyOneHour(long nanosFromBoundary) {
    Instant start = Instant.parse("2026-10-11T03:00:00Z");
    var quota = new ConnectionInviteQuota(UUID.randomUUID(), start);
    for (int i = 0; i < 10; i++) assertTrue(quota.consume(start));
    assertEquals(nanosFromBoundary >= 0, quota.consume(start.plusSeconds(3600).plusNanos(nanosFromBoundary)));
  }

  @Test void firstAttemptPersistsNewQuotaInSameRepository() {
    UUID actor = UUID.randomUUID();
    var repository = mock(ConnectionInviteQuotaRepository.class);
    var lock = mock(ConnectionLock.class);
    when(repository.findById(actor)).thenReturn(Optional.empty());
    assertTrue(new InvitationQuotaService(repository, lock, Clock.systemUTC()).consume(actor).allowed());
    verify(repository).save(any(ConnectionInviteQuota.class));
  }
}
