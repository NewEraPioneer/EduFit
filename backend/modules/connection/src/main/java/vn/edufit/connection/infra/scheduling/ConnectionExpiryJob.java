package vn.edufit.connection.infra.scheduling;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.edufit.connection.application.service.ConnectionExpiryService;

@Component
@ConditionalOnProperty(name = "edufit.connection.expiry-job.enabled", havingValue = "true", matchIfMissing = true)
public class ConnectionExpiryJob {
  private static final Logger log = LoggerFactory.getLogger(ConnectionExpiryJob.class);

  private final ConnectionExpiryService expiryService;
  private final Clock clock;
  private final int batchSize;

  @org.springframework.beans.factory.annotation.Autowired
  public ConnectionExpiryJob(
      ConnectionExpiryService expiryService,
      Clock clock,
      @Value("${edufit.connection.expiry-job.batch-size:100}") int batchSize) {
    this.expiryService = expiryService;
    this.clock = clock;
    this.batchSize = batchSize > 0 ? batchSize : 100;
  }

  @Scheduled(
      fixedDelayString = "${edufit.connection.expiry-job.interval-ms:60000}",
      initialDelayString = "${edufit.connection.expiry-job.initial-delay-ms:10000}"
  )
  public BatchRunResult runExpiryCycle() {
    long startTime = System.currentTimeMillis();
    Instant now = clock.instant();

    int scannedInvitations = 0;
    int expiredInvitations = 0;
    int failedInvitations = 0;

    int scannedRequests = 0;
    int expiredRequests = 0;
    int failedRequests = 0;

    // Drain invitations in batches
    Set<UUID> attemptedInvitations = new HashSet<>();
    while (true) {
      List<UUID> candidates = expiryService.findExpiredInvitationCandidateIds(now, batchSize);
      List<UUID> toProcess = candidates.stream().filter(id -> !attemptedInvitations.contains(id)).toList();
      if (toProcess.isEmpty()) {
        break;
      }
      for (UUID id : toProcess) {
        attemptedInvitations.add(id);
        scannedInvitations++;
        try {
          if (expiryService.expireInvitation(id, now)) {
            expiredInvitations++;
          }
        } catch (Exception e) {
          failedInvitations++;
          log.warn("Failed to expire invitation id={}: {}", id, e.getMessage());
        }
      }
      if (candidates.size() < batchSize) {
        break;
      }
    }

    // Drain requests in batches
    Set<UUID> attemptedRequests = new HashSet<>();
    while (true) {
      List<UUID> candidates = expiryService.findExpiredRequestCandidateIds(now, batchSize);
      List<UUID> toProcess = candidates.stream().filter(id -> !attemptedRequests.contains(id)).toList();
      if (toProcess.isEmpty()) {
        break;
      }
      for (UUID id : toProcess) {
        attemptedRequests.add(id);
        scannedRequests++;
        try {
          if (expiryService.expireRequest(id, now)) {
            expiredRequests++;
          }
        } catch (Exception e) {
          failedRequests++;
          log.warn("Failed to expire request id={}: {}", id, e.getMessage());
        }
      }
      if (candidates.size() < batchSize) {
        break;
      }
    }

    long duration = System.currentTimeMillis() - startTime;
    log.info("Connection expiry cycle completed in {} ms: invitations [scanned={}, expired={}, failed={}], requests [scanned={}, expired={}, failed={}]",
        duration, scannedInvitations, expiredInvitations, failedInvitations,
        scannedRequests, expiredRequests, failedRequests);

    return new BatchRunResult(
        scannedInvitations, expiredInvitations, failedInvitations,
        scannedRequests, expiredRequests, failedRequests,
        duration
    );
  }

  public int getBatchSize() {
    return batchSize;
  }

  public record BatchRunResult(
      int scannedInvitations, int expiredInvitations, int failedInvitations,
      int scannedRequests, int expiredRequests, int failedRequests,
      long durationMillis
  ) {}
}
