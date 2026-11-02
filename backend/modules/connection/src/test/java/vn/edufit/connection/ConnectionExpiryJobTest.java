package vn.edufit.connection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edufit.connection.application.service.ConnectionExpiryService;
import vn.edufit.connection.infra.scheduling.ConnectionExpiryJob;

class ConnectionExpiryJobTest {
  private ConnectionExpiryService expiryService;
  private Clock clock;
  private Instant now;

  @BeforeEach
  void setUp() {
    expiryService = mock(ConnectionExpiryService.class);
    clock = mock(Clock.class);
    now = Instant.parse("2026-10-14T10:00:00Z");
    when(clock.instant()).thenReturn(now);
  }

  @Test
  void noCandidatesReturnsZeroCounts() {
    ConnectionExpiryJob job = new ConnectionExpiryJob(expiryService, clock, 100);
    when(expiryService.findExpiredInvitationCandidateIds(now, 100)).thenReturn(List.of());
    when(expiryService.findExpiredRequestCandidateIds(now, 100)).thenReturn(List.of());

    var result = job.runExpiryCycle();
    assertEquals(0, result.scannedInvitations());
    assertEquals(0, result.expiredInvitations());
    assertEquals(0, result.failedInvitations());
    assertEquals(0, result.scannedRequests());
    assertEquals(0, result.expiredRequests());
    assertEquals(0, result.failedRequests());
    verify(expiryService, never()).expireInvitation(any(), any());
    verify(expiryService, never()).expireRequest(any(), any());
  }

  @Test
  void singleBatchAllSuccess() {
    ConnectionExpiryJob job = new ConnectionExpiryJob(expiryService, clock, 100);
    UUID invId = UUID.randomUUID();
    UUID reqId = UUID.randomUUID();

    when(expiryService.findExpiredInvitationCandidateIds(now, 100)).thenReturn(List.of(invId));
    when(expiryService.findExpiredRequestCandidateIds(now, 100)).thenReturn(List.of(reqId));
    when(expiryService.expireInvitation(invId, now)).thenReturn(true);
    when(expiryService.expireRequest(reqId, now)).thenReturn(true);

    var result = job.runExpiryCycle();
    assertEquals(1, result.scannedInvitations());
    assertEquals(1, result.expiredInvitations());
    assertEquals(0, result.failedInvitations());
    assertEquals(1, result.scannedRequests());
    assertEquals(1, result.expiredRequests());
    assertEquals(0, result.failedRequests());

    verify(expiryService).expireInvitation(invId, now);
    verify(expiryService).expireRequest(reqId, now);
  }

  @Test
  void multiBatchDrainingProcessesAllCandidatesAcrossBatches() {
    ConnectionExpiryJob job = new ConnectionExpiryJob(expiryService, clock, 2);
    UUID id1 = UUID.randomUUID(), id2 = UUID.randomUUID(), id3 = UUID.randomUUID();

    // 1st batch returns 2 items (full batchSize)
    // 2nd batch returns 1 item (< batchSize, signals completion)
    when(expiryService.findExpiredInvitationCandidateIds(now, 2))
        .thenReturn(List.of(id1, id2))
        .thenReturn(List.of(id3));
    when(expiryService.findExpiredRequestCandidateIds(now, 2)).thenReturn(List.of());

    when(expiryService.expireInvitation(id1, now)).thenReturn(true);
    when(expiryService.expireInvitation(id2, now)).thenReturn(true);
    when(expiryService.expireInvitation(id3, now)).thenReturn(true);

    var result = job.runExpiryCycle();
    assertEquals(3, result.scannedInvitations());
    assertEquals(3, result.expiredInvitations());
    assertEquals(0, result.failedInvitations());

    verify(expiryService).expireInvitation(id1, now);
    verify(expiryService).expireInvitation(id2, now);
    verify(expiryService).expireInvitation(id3, now);
  }

  @Test
  void itemFailureDoesNotCrashJobAndAllowsRemainingItemsToProcess() {
    ConnectionExpiryJob job = new ConnectionExpiryJob(expiryService, clock, 100);
    UUID failId = UUID.randomUUID(), successId = UUID.randomUUID();

    when(expiryService.findExpiredInvitationCandidateIds(now, 100)).thenReturn(List.of(failId, successId));
    when(expiryService.findExpiredRequestCandidateIds(now, 100)).thenReturn(List.of());

    when(expiryService.expireInvitation(failId, now)).thenThrow(new RuntimeException("Lock acquisition timeout"));
    when(expiryService.expireInvitation(successId, now)).thenReturn(true);

    var result = job.runExpiryCycle();
    assertEquals(2, result.scannedInvitations());
    assertEquals(1, result.expiredInvitations());
    assertEquals(1, result.failedInvitations());

    verify(expiryService).expireInvitation(failId, now);
    verify(expiryService).expireInvitation(successId, now);
  }

  @Test
  void failedItemIsRetriedInSubsequentCycle() {
    ConnectionExpiryJob job = new ConnectionExpiryJob(expiryService, clock, 100);
    UUID problematicId = UUID.randomUUID();

    // Cycle 1: fails
    when(expiryService.findExpiredInvitationCandidateIds(now, 100)).thenReturn(List.of(problematicId));
    when(expiryService.findExpiredRequestCandidateIds(now, 100)).thenReturn(List.of());
    when(expiryService.expireInvitation(problematicId, now)).thenThrow(new RuntimeException("Temporary deadlock"));

    var result1 = job.runExpiryCycle();
    assertEquals(1, result1.scannedInvitations());
    assertEquals(0, result1.expiredInvitations());
    assertEquals(1, result1.failedInvitations());

    // Cycle 2: succeeding now
    Instant later = now.plusSeconds(60);
    when(clock.instant()).thenReturn(later);
    when(expiryService.findExpiredInvitationCandidateIds(later, 100)).thenReturn(List.of(problematicId));
    when(expiryService.findExpiredRequestCandidateIds(later, 100)).thenReturn(List.of());
    when(expiryService.expireInvitation(problematicId, later)).thenReturn(true);

    var result2 = job.runExpiryCycle();
    assertEquals(1, result2.scannedInvitations());
    assertEquals(1, result2.expiredInvitations());
    assertEquals(0, result2.failedInvitations());

    verify(expiryService).expireInvitation(problematicId, later);
  }

  @Test
  void terminalCandidatesReturnFalseAndAreCountedAsScannedNotExpired() {
    ConnectionExpiryJob job = new ConnectionExpiryJob(expiryService, clock, 100);
    UUID terminalId = UUID.randomUUID();

    when(expiryService.findExpiredRequestCandidateIds(now, 100)).thenReturn(List.of(terminalId));
    when(expiryService.findExpiredInvitationCandidateIds(now, 100)).thenReturn(List.of());
    // Already accepted/cancelled or already expired concurrently
    when(expiryService.expireRequest(terminalId, now)).thenReturn(false);

    var result = job.runExpiryCycle();
    assertEquals(1, result.scannedRequests());
    assertEquals(0, result.expiredRequests());
    assertEquals(0, result.failedRequests());
  }
}
