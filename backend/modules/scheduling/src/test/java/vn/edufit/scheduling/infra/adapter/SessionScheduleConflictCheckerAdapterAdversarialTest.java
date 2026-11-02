package vn.edufit.scheduling.infra.adapter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;

/**
 * Adversarial Empirical Test Suite for {@link SessionScheduleConflictCheckerAdapter}.
 *
 * <p>Stress tests slot format normalization, boundary intervals, multi-session lists,
 * and edge case inputs.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Adversarial Empirical Challenge: SessionScheduleConflictCheckerAdapter")
class SessionScheduleConflictCheckerAdapterAdversarialTest {

  @Mock
  private TutoringSessionDomainRepository sessionRepository;

  private Clock fixedClock;
  private SessionScheduleConflictCheckerAdapter adapter;

  private final UUID tutorId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID classId = UUID.randomUUID();
  private final ZoneId vnZone = ZoneId.of("Asia/Ho_Chi_Minh");

  // Fixed now: Monday 2026-10-26 08:00:00 ICT
  private final ZonedDateTime nowZdt = ZonedDateTime.of(2026, 10, 26, 8, 0, 0, 0, vnZone);
  private final Instant now = nowZdt.toInstant();

  @BeforeEach
  void setUp() {
    fixedClock = Clock.fixed(now, vnZone);
    adapter = new SessionScheduleConflictCheckerAdapter(sessionRepository, fixedClock);
  }

  private TutoringSession createScheduledSession(ZonedDateTime startZdt, ZonedDateTime endZdt) {
    return new TutoringSession(
        UUID.randomUUID(), classId, tutorId, studentId,
        startZdt.toInstant(), endZdt.toInstant(), SessionMode.ONLINE,
        null, null, null, SessionStatus.SCHEDULED,
        studentId, tutorId, now, null, startZdt.toInstant().minusSeconds(7200),
        null, null, null, null, false, null, null, 1, now
    );
  }

  // =========================================================================
  // 1. CANONICAL FORMAT VARIATIONS
  // =========================================================================
  @Nested
  @DisplayName("1. Canonical Slot Format Variations")
  class CanonicalFormatTests {

    @ParameterizedTest(name = "Slot: {0}")
    @ValueSource(strings = {
        "MONDAY 14:00-16:00",
        "MON 14:00-16:00",
        "monday 14:00-16:00",
        "MonDay 14:00-16:00",
        "MONDAY 14:00–16:00",  // En-dash
        "MONDAY 14:00—16:00",  // Em-dash
        "  MONDAY   14:00  -  16:00  ",
        "MON 13:30-14:30",     // Partial overlap
        "MON 15:30-16:30"      // Partial overlap
    })
    @DisplayName("Overlapping canonical formats must detect conflict")
    void shouldDetectConflictForCanonicalVariations(String slot) {
      ZonedDateTime sessionStart = nowZdt.withHour(14).withMinute(0);
      ZonedDateTime sessionEnd = sessionStart.plusHours(2);
      TutoringSession session = createScheduledSession(sessionStart, sessionEnd);

      when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any()))
          .thenReturn(List.of(session));

      boolean result = adapter.hasConflictWithScheduledSessions(tutorId, List.of(slot));
      assertTrue(result, "Should detect conflict for: " + slot);
    }

    @ParameterizedTest(name = "Day: {0}")
    @ValueSource(strings = {
        "MONDAY 10:00-12:00",
        "TUESDAY 10:00-12:00",
        "WEDNESDAY 10:00-12:00",
        "THURSDAY 10:00-12:00",
        "FRIDAY 10:00-12:00",
        "SATURDAY 10:00-12:00",
        "SUNDAY 10:00-12:00",
        "MON 10:00-12:00",
        "TUE 10:00-12:00",
        "WED 10:00-12:00",
        "THU 10:00-12:00",
        "FRI 10:00-12:00",
        "SAT 10:00-12:00",
        "SUN 10:00-12:00"
    })
    @DisplayName("All 7 days of the week in full and abbreviated canonical forms")
    void shouldSupportAllDaysOfWeek(String slot) {
      // Find what day the slot represents by parsing day name
      String dayStr = slot.split(" ")[0].toUpperCase();
      int dayOffset = switch (dayStr) {
        case "MON", "MONDAY" -> 0;
        case "TUE", "TUESDAY" -> 1;
        case "WED", "WEDNESDAY" -> 2;
        case "THU", "THURSDAY" -> 3;
        case "FRI", "FRIDAY" -> 4;
        case "SAT", "SATURDAY" -> 5;
        case "SUN", "SUNDAY" -> 6;
        default -> throw new IllegalArgumentException();
      };

      ZonedDateTime sessionStart = nowZdt.plusDays(dayOffset).withHour(10).withMinute(30);
      ZonedDateTime sessionEnd = sessionStart.plusHours(1);
      TutoringSession session = createScheduledSession(sessionStart, sessionEnd);

      when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any()))
          .thenReturn(List.of(session));

      boolean result = adapter.hasConflictWithScheduledSessions(tutorId, List.of(slot));
      assertTrue(result, "Should detect conflict on day " + dayStr);
    }
  }

  // =========================================================================
  // 2. VIETNAMESE LOCALIZED FORMAT VARIATIONS
  // =========================================================================
  @Nested
  @DisplayName("2. Vietnamese Localized Format Variations")
  class VietnameseFormatTests {

    @ParameterizedTest(name = "Slot: {0}")
    @ValueSource(strings = {
        "Thứ 2 · 14:00–16:00",
        "Thứ 2 • 14:00–16:00",
        "Thứ 2, 14:00-16:00",
        "Thứ 2 14:00-16:00",
        "thứ 2 · 14:00-16:00",
        "Thứ 2 · 8:00-9:30"
    })
    @DisplayName("Vietnamese Monday variations must detect conflict")
    void shouldDetectConflictForVietnameseMonday(String slot) {
      int startHour = slot.contains("8:00") ? 8 : 14;
      ZonedDateTime sessionStart = nowZdt.withHour(startHour).withMinute(30);
      ZonedDateTime sessionEnd = sessionStart.plusMinutes(45);
      TutoringSession session = createScheduledSession(sessionStart, sessionEnd);

      when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any()))
          .thenReturn(List.of(session));

      boolean result = adapter.hasConflictWithScheduledSessions(tutorId, List.of(slot));
      assertTrue(result, "Should detect conflict for Vietnamese slot: " + slot);
    }

    @ParameterizedTest(name = "Sunday slot: {0}")
    @ValueSource(strings = {
        "Chủ Nhật · 09:00–11:00",
        "Chủ Nhật 09:00-11:00",
        "chủ nhật · 09:00-11:00",
        "CN · 09:00–11:00",
        "cn 09:00-11:00",
        "CN · 09:00" // Point in time -> 09:00-10:00
    })
    @DisplayName("Vietnamese Sunday / CN variations must detect conflict")
    void shouldDetectConflictForSundayVariations(String slot) {
      // Sunday is 6 days after Monday 2026-10-26
      ZonedDateTime sessionStart = nowZdt.plusDays(6).withHour(9).withMinute(30);
      ZonedDateTime sessionEnd = sessionStart.plusHours(1);
      TutoringSession session = createScheduledSession(sessionStart, sessionEnd);

      when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any()))
          .thenReturn(List.of(session));

      boolean result = adapter.hasConflictWithScheduledSessions(tutorId, List.of(slot));
      assertTrue(result, "Should detect conflict for Sunday slot: " + slot);
    }

    @Test
    @DisplayName("Unaccented 'Thu 2' and 'chunhat' do not match regex despite parseVnDay switch cases")
    void unaccentedVietnameseLimitation() {
      // VN_PATTERN requires 'Thứ' or 'Chủ Nhật' or 'CN'
      // Unaccented 'Thu 2' returns false because parseSlot returns null
      assertFalse(adapter.hasConflictWithScheduledSessions(tutorId, List.of("Thu 2 · 14:00-16:00")));
      assertFalse(adapter.hasConflictWithScheduledSessions(tutorId, List.of("chunhat · 09:00-11:00")));
    }
  }

  // =========================================================================
  // 3. LEGACY POINT-IN-TIME FORMATS
  // =========================================================================
  @Nested
  @DisplayName("3. Legacy Point-in-Time Formats")
  class LegacyPointInTimeTests {

    @Test
    @DisplayName("Legacy 'MON 14:00' conflicts with session 14:15-15:00")
    void legacyPointInTimeConflictsWithSession() {
      ZonedDateTime sessionStart = nowZdt.withHour(14).withMinute(15);
      ZonedDateTime sessionEnd = nowZdt.withHour(15).withMinute(0);
      TutoringSession session = createScheduledSession(sessionStart, sessionEnd);

      when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any()))
          .thenReturn(List.of(session));

      assertTrue(adapter.hasConflictWithScheduledSessions(tutorId, List.of("MON 14:00")));
    }

    @Test
    @DisplayName("Legacy 'FRIDAY 18:30' does not conflict with session 19:45-20:45")
    void legacyPointInTimeDoesNotConflictOutside60MinWindow() {
      // Friday is 4 days after Monday
      ZonedDateTime sessionStart = nowZdt.plusDays(4).withHour(19).withMinute(45);
      ZonedDateTime sessionEnd = sessionStart.plusHours(1);
      TutoringSession session = createScheduledSession(sessionStart, sessionEnd);

      when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any()))
          .thenReturn(List.of(session));

      // Candidate slot window is 18:30 to 19:30 -> session is 19:45 to 20:45 -> no overlap
      assertFalse(adapter.hasConflictWithScheduledSessions(tutorId, List.of("FRIDAY 18:30")));
    }
  }

  // =========================================================================
  // 4. BOUNDARY & INTERVAL PRECISION
  // =========================================================================
  @Nested
  @DisplayName("4. Boundary Overlaps & Interval Arithmetic")
  class BoundaryPrecisionTests {

    @Test
    @DisplayName("Exact boundary touch (left): session 14:00-16:00 vs slot 12:00-14:00 -> NO conflict")
    void exactLeftBoundaryNoConflict() {
      ZonedDateTime sessionStart = nowZdt.withHour(14).withMinute(0);
      ZonedDateTime sessionEnd = nowZdt.withHour(16).withMinute(0);
      TutoringSession session = createScheduledSession(sessionStart, sessionEnd);

      when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any()))
          .thenReturn(List.of(session));

      assertFalse(adapter.hasConflictWithScheduledSessions(tutorId, List.of("MONDAY 12:00-14:00")));
    }

    @Test
    @DisplayName("Exact boundary touch (right): session 14:00-16:00 vs slot 16:00-18:00 -> NO conflict")
    void exactRightBoundaryNoConflict() {
      ZonedDateTime sessionStart = nowZdt.withHour(14).withMinute(0);
      ZonedDateTime sessionEnd = nowZdt.withHour(16).withMinute(0);
      TutoringSession session = createScheduledSession(sessionStart, sessionEnd);

      when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any()))
          .thenReturn(List.of(session));

      assertFalse(adapter.hasConflictWithScheduledSessions(tutorId, List.of("MONDAY 16:00-18:00")));
    }

    @Test
    @DisplayName("1-minute overlap (left): session 14:00-16:00 vs slot 13:00-14:01 -> MUST conflict")
    void oneMinuteOverlapLeftMustConflict() {
      ZonedDateTime sessionStart = nowZdt.withHour(14).withMinute(0);
      ZonedDateTime sessionEnd = nowZdt.withHour(16).withMinute(0);
      TutoringSession session = createScheduledSession(sessionStart, sessionEnd);

      when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any()))
          .thenReturn(List.of(session));

      assertTrue(adapter.hasConflictWithScheduledSessions(tutorId, List.of("MONDAY 13:00-14:01")));
    }

    @Test
    @DisplayName("1-minute overlap (right): session 14:00-16:00 vs slot 15:59-17:00 -> MUST conflict")
    void oneMinuteOverlapRightMustConflict() {
      ZonedDateTime sessionStart = nowZdt.withHour(14).withMinute(0);
      ZonedDateTime sessionEnd = nowZdt.withHour(16).withMinute(0);
      TutoringSession session = createScheduledSession(sessionStart, sessionEnd);

      when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any()))
          .thenReturn(List.of(session));

      assertTrue(adapter.hasConflictWithScheduledSessions(tutorId, List.of("MONDAY 15:59-17:00")));
    }

    @Test
    @DisplayName("Slot is strict subset of session: session 14:00-16:00 vs slot 14:30-15:30 -> MUST conflict")
    void subsetIntervalMustConflict() {
      ZonedDateTime sessionStart = nowZdt.withHour(14).withMinute(0);
      ZonedDateTime sessionEnd = nowZdt.withHour(16).withMinute(0);
      TutoringSession session = createScheduledSession(sessionStart, sessionEnd);

      when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any()))
          .thenReturn(List.of(session));

      assertTrue(adapter.hasConflictWithScheduledSessions(tutorId, List.of("MONDAY 14:30-15:30")));
    }

    @Test
    @DisplayName("Slot is strict superset of session: session 14:00-16:00 vs slot 13:00-17:00 -> MUST conflict")
    void supersetIntervalMustConflict() {
      ZonedDateTime sessionStart = nowZdt.withHour(14).withMinute(0);
      ZonedDateTime sessionEnd = nowZdt.withHour(16).withMinute(0);
      TutoringSession session = createScheduledSession(sessionStart, sessionEnd);

      when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any()))
          .thenReturn(List.of(session));

      assertTrue(adapter.hasConflictWithScheduledSessions(tutorId, List.of("MONDAY 13:00-17:00")));
    }
  }

  // =========================================================================
  // 5. MULTI-SLOT & MULTI-SESSION SCENARIOS
  // =========================================================================
  @Nested
  @DisplayName("5. Multi-Slot and Multi-Session Lists")
  class MultiListTests {

    @Test
    @DisplayName("Multiple desired slots where only 1 conflicts -> MUST return true")
    void shouldReturnTrueWhenOneOfMultipleSlotsConflicts() {
      ZonedDateTime sessionStart = nowZdt.plusDays(2).withHour(18).withMinute(0); // Wednesday
      ZonedDateTime sessionEnd = sessionStart.plusHours(2);
      TutoringSession session = createScheduledSession(sessionStart, sessionEnd);

      when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any()))
          .thenReturn(List.of(session));

      List<String> desiredSlots = List.of(
          "MONDAY 08:00-10:00",
          "WEDNESDAY 19:00-21:00", // Conflicts with Wed 18:00-20:00
          "FRIDAY 14:00-16:00"
      );

      assertTrue(adapter.hasConflictWithScheduledSessions(tutorId, desiredSlots));
    }

    @Test
    @DisplayName("Multiple scheduled sessions where first does not conflict but second does -> MUST return true")
    void shouldReturnTrueWhenSecondSessionConflicts() {
      ZonedDateTime s1Start = nowZdt.withHour(8).withMinute(0);
      ZonedDateTime s1End = s1Start.plusHours(2); // Mon 08:00-10:00
      TutoringSession s1 = createScheduledSession(s1Start, s1End);

      ZonedDateTime s2Start = nowZdt.withHour(15).withMinute(0);
      ZonedDateTime s2End = s2Start.plusHours(2); // Mon 15:00-17:00
      TutoringSession s2 = createScheduledSession(s2Start, s2End);

      when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any()))
          .thenReturn(List.of(s1, s2));

      List<String> desiredSlots = List.of("MONDAY 16:00-18:00"); // Conflicts with s2
      assertTrue(adapter.hasConflictWithScheduledSessions(tutorId, desiredSlots));
    }

    @Test
    @DisplayName("Future session in week 5 (35 days later) -> weekly recurring slot MUST detect conflict")
    void shouldDetectConflictForFutureWeekSession() {
      // 5 weeks later on Monday 14:00-16:00
      ZonedDateTime sessionStart = nowZdt.plusWeeks(5).withHour(14).withMinute(0);
      ZonedDateTime sessionEnd = sessionStart.plusHours(2);
      TutoringSession session = createScheduledSession(sessionStart, sessionEnd);

      when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any()))
          .thenReturn(List.of(session));

      assertTrue(adapter.hasConflictWithScheduledSessions(tutorId, List.of("MONDAY 14:00-16:00")));
    }
  }

  // =========================================================================
  // 6. RESILIENCY & EDGE CASES
  // =========================================================================
  @Nested
  @DisplayName("6. Resiliency & Edge Cases")
  class ResiliencyTests {

    @Test
    @DisplayName("List with null elements -> nulls are filtered and valid slots are checked")
    void shouldFilterNullElementsInSlotList() {
      ZonedDateTime sessionStart = nowZdt.withHour(14).withMinute(0);
      ZonedDateTime sessionEnd = sessionStart.plusHours(2);
      TutoringSession session = createScheduledSession(sessionStart, sessionEnd);

      when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any()))
          .thenReturn(List.of(session));

      List<String> slotsWithNulls = Arrays.asList(null, "MONDAY 15:00-17:00", null);
      assertTrue(adapter.hasConflictWithScheduledSessions(tutorId, slotsWithNulls));
    }

    @Test
    @DisplayName("Invalid / unparseable string -> safely returns false without exception")
    void shouldHandleUnparseableStringGracefully() {
      assertFalse(adapter.hasConflictWithScheduledSessions(tutorId, List.of("INVALID STRING", "", "   ")));
    }

    @Test
    @DisplayName("Inverted interval (start > end) -> no conflict detected and no crash")
    void shouldHandleInvertedIntervalGracefully() {
      ZonedDateTime sessionStart = nowZdt.withHour(14).withMinute(0);
      ZonedDateTime sessionEnd = sessionStart.plusHours(2);
      TutoringSession session = createScheduledSession(sessionStart, sessionEnd);

      when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any()))
          .thenReturn(List.of(session));

      assertFalse(adapter.hasConflictWithScheduledSessions(tutorId, List.of("MONDAY 16:00-14:00")));
    }
  }
}
