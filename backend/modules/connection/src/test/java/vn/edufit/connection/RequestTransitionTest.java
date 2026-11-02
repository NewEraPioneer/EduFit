package vn.edufit.connection;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import vn.edufit.connection.infra.persistence.entity.ConnectionRequest;
import vn.edufit.shared.exception.ResourceConflictException;

class RequestTransitionTest {
  private static final Instant NOW = Instant.parse("2026-10-11T00:00:00Z");
  private ConnectionRequest request() {
    return new ConnectionRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        UUID.randomUUID(), List.of("Tue 19:00"), null, NOW);
  }
  static Stream<Arguments> terminalActions() {
    return Stream.of("ACCEPTED", "REJECTED", "CANCELLED", "EXPIRED")
        .flatMap(state -> Stream.of("ACCEPTED", "REJECTED", "CANCELLED").map(action -> Arguments.of(state, action)));
  }
  private void transition(ConnectionRequest request, String action, Instant time) {
    switch (action) {
      case "ACCEPTED" -> request.accept(time);
      case "REJECTED" -> request.reject("reason", time);
      case "CANCELLED" -> request.cancel(time);
      case "EXPIRED" -> request.expire(request.getExpiresAt());
      default -> throw new AssertionError(action);
    }
  }
  @ParameterizedTest @MethodSource("terminalActions")
  void terminalRejectsAllActionsAndRetainsReasonAndTimestamp(String state, String action) throws Exception {
    var request = request(); transition(request, state, NOW);
    var responded = ConnectionRequest.class.getDeclaredField("respondedAt"); responded.setAccessible(true);
    Object timestamp = responded.get(request); String reason = request.getRejectReason();
    assertThrows(ResourceConflictException.class, () -> transition(request, action, NOW.plusSeconds(1)));
    request.expire(NOW.plus(Duration.ofDays(30)));
    assertEquals(state, request.getStatus()); assertEquals(reason, request.getRejectReason());
    assertEquals(timestamp, responded.get(request));
  }
  @ParameterizedTest @ValueSource(longs = {-1, 0, 1})
  void expiryIsInclusiveAndIdempotent(long offset) {
    var request = request(); assertEquals(NOW.plus(Duration.ofDays(7)), request.getExpiresAt());
    Instant now = request.getExpiresAt().plusNanos(offset);
    request.expire(now); request.expire(now);
    assertEquals(offset < 0 ? "PENDING" : "EXPIRED", request.getStatus());
    assertNull(request.getRejectReason());
  }
  @ParameterizedTest @CsvSource({"ACCEPTED,-1", "REJECTED,-1", "CANCELLED,-1", "ACCEPTED,0", "REJECTED,0", "CANCELLED,0", "ACCEPTED,1", "REJECTED,1", "CANCELLED,1"})
  void pendingActionChecksDeadline(String action, long offset) throws Exception {
    var request = request(); Instant time = request.getExpiresAt().plusNanos(offset);
    if (offset < 0) {
      transition(request, action, time); assertEquals(action, request.getStatus());
      var responded = ConnectionRequest.class.getDeclaredField("respondedAt"); responded.setAccessible(true);
      assertEquals(time, responded.get(request));
    } else {
      assertThrows(ResourceConflictException.class, () -> transition(request, action, time));
      assertEquals("PENDING", request.getStatus()); assertNull(request.getRejectReason());
    }
  }
}
