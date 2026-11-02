package vn.edufit.connection.application.service;

import java.time.Instant;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.connection.application.port.ConnectionLock;
import vn.edufit.connection.infra.persistence.entity.ConnectionInviteQuota;
import vn.edufit.connection.infra.persistence.repository.ConnectionInviteQuotaRepository;

@Service
public class InvitationQuotaService {
  private final Clock clock;
  private final ConnectionInviteQuotaRepository quotas;
  private final ConnectionLock lock;

  public InvitationQuotaService(ConnectionInviteQuotaRepository quotas, ConnectionLock lock, Clock clock) {
    this.clock = clock;
    this.quotas = quotas;
    this.lock = lock;
  }

  /** Keep the operation timestamp after quota lock acquisition, as in the original service. */
  public record Consumption(boolean allowed, Instant checkedAt) {}

  @Transactional(propagation = Propagation.MANDATORY)
  public Consumption consume(UUID userId) {
    lock.lockInvitationQuota(userId);
    Instant now = clock.instant();
    ConnectionInviteQuota quota = quotas.findById(userId).orElseGet(() -> new ConnectionInviteQuota(userId, now));
    if (!quota.consume(now)) return new Consumption(false, now);
    quotas.save(quota);
    return new Consumption(true, now);
  }
}
