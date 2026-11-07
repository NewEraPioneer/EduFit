package vn.edufit.payment.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

/**
 * Raised in the money transaction whenever a wallet ledger entry is written for a user.
 * {@code kind} is the ledger kind (TOPUP, WITHDRAW_RESERVED, WITHDRAW_PAID, ...); deciding whether and how
 * to tell the user is the notification module's job.
 */
public record WalletBalanceChangedEvent(UUID eventId, Instant occurredAt, UUID userId, UUID referenceId,
    String kind, long availableDelta, long heldDelta) implements BaseDomainEvent {
  public static WalletBalanceChangedEvent of(UUID userId, UUID referenceId, String kind,
      long availableDelta, long heldDelta) {
    return new WalletBalanceChangedEvent(UUID.randomUUID(), Instant.now(), userId, referenceId, kind,
        availableDelta, heldDelta);
  }

  @Override public UUID getEventId() { return eventId; }
  @Override public Instant getOccurredAt() { return occurredAt; }
}
