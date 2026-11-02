package vn.edufit.connection;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import vn.edufit.connection.infra.persistence.entity.*;
import vn.edufit.shared.exception.ResourceConflictException;

class InvitationTransitionTest {
  private static final Instant NOW = Instant.parse("2026-10-11T00:00:00Z");
  private LinkInvitation invitation() { return new LinkInvitation(UUID.randomUUID(), UUID.randomUUID(), NOW); }
  static Stream<Arguments> terminalActions() {
    return Stream.of("ACCEPTED", "DECLINED", "WITHDRAWN", "EXPIRED")
        .flatMap(state -> Stream.of("ACCEPTED", "DECLINED", "WITHDRAWN").map(action -> Arguments.of(state, action)));
  }
  private void transition(LinkInvitation invitation, String action, Instant time) {
    switch (action) {
      case "ACCEPTED" -> invitation.accept(time);
      case "DECLINED" -> invitation.decline(time);
      case "WITHDRAWN" -> invitation.withdraw(time);
      case "EXPIRED" -> invitation.expire(invitation.getExpiresAt());
      default -> throw new AssertionError(action);
    }
  }
  @ParameterizedTest @MethodSource("terminalActions")
  void terminalRejectsEveryActionWithoutChangingTimestamps(String state, String action) {
    var invitation = invitation(); transition(invitation, state, NOW);
    Instant responded = invitation.getRespondedAt(), cooldown = invitation.getReinviteAfter();
    assertThrows(ResourceConflictException.class, () -> transition(invitation, action, NOW.plusSeconds(1)));
    invitation.expire(NOW.plus(Duration.ofDays(30)));
    assertEquals(state, invitation.getStatus());
    assertEquals(responded, invitation.getRespondedAt());
    assertEquals(cooldown, invitation.getReinviteAfter());
  }
  @ParameterizedTest @ValueSource(strings = {"ACCEPTED", "DECLINED", "WITHDRAWN"})
  void pendingTransitionsRecordExactTimestamp(String action) {
    var invitation = invitation(); transition(invitation, action, NOW.plusSeconds(1));
    assertEquals(action, invitation.getStatus());
    assertEquals(NOW.plusSeconds(1), invitation.getRespondedAt());
    assertEquals(action.equals("DECLINED") ? NOW.plusSeconds(1).plus(Duration.ofDays(7)) : null,
        invitation.getReinviteAfter());
  }
  @ParameterizedTest @ValueSource(longs = {-1, 0, 1})
  void expiryAtDeadlineIsIdempotent(long offset) {
    var invitation = invitation(); Instant time = invitation.getExpiresAt().plusNanos(offset);
    invitation.expire(time); invitation.expire(time);
    assertEquals(offset < 0 ? "PENDING" : "EXPIRED", invitation.getStatus());
    assertNull(invitation.getRespondedAt()); assertNull(invitation.getReinviteAfter());
  }
  @ParameterizedTest @CsvSource({"ACCEPTED,0", "DECLINED,0", "WITHDRAWN,0", "ACCEPTED,1", "DECLINED,1", "WITHDRAWN,1"})
  void actionCannotBypassExpiry(String action, long offset) {
    var invitation = invitation();
    assertThrows(ResourceConflictException.class, () -> transition(invitation, action, invitation.getExpiresAt().plusNanos(offset)));
    assertEquals("PENDING", invitation.getStatus()); assertNull(invitation.getRespondedAt());
  }
  @ParameterizedTest @ValueSource(longs = {-1, 0, 1})
  void declineCooldownEndsExactlyAtDeadline(long offset) {
    var invitation = invitation(); invitation.decline(NOW);
    assertEquals(offset < 0, vn.edufit.connection.application.policy.ConnectionTransitions
        .cooldownActive(invitation.getReinviteAfter(), NOW.plus(Duration.ofDays(7)).plusNanos(offset)));
    assertFalse(vn.edufit.connection.application.policy.ConnectionTransitions.cooldownActive(null, NOW));
  }
  @Test void revokedLinkCannotBeRevokedAgain() {
    var link = new ParentStudentLink(UUID.randomUUID(), UUID.randomUUID(), NOW);
    link.revoke(UUID.randomUUID(), "first", NOW.plusSeconds(1));
    assertThrows(ResourceConflictException.class, () -> link.revoke(UUID.randomUUID(), "second", NOW.plusSeconds(2)));
    assertEquals("REVOKED", link.getStatus());
  }
}
