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
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;

/**
 * Unit Test cho {@link SessionScheduleConflictCheckerAdapter}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Adapter: Kiểm tra xung đột lịch học của gia sư (SessionScheduleConflictCheckerAdapter)")
class SessionScheduleConflictCheckerAdapterTest {

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

  @Test
  @DisplayName("Trả về false khi desiredSlots rỗng hoặc null")
  void shouldReturnFalseWhenSlotsEmpty() {
    assertFalse(adapter.hasConflictWithScheduledSessions(tutorId, null));
    assertFalse(adapter.hasConflictWithScheduledSessions(tutorId, List.of()));
    assertFalse(adapter.hasConflictWithScheduledSessions(null, List.of("MONDAY 08:00-10:00")));
  }

  @Test
  @DisplayName("Trả về false khi gia sư không có buổi học SCHEDULED nào trong tương lai")
  void shouldReturnFalseWhenNoBusySessions() {
    when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any())).thenReturn(Collections.emptyList());

    boolean hasConflict = adapter.hasConflictWithScheduledSessions(tutorId, List.of("MONDAY 14:00-16:00"));
    assertFalse(hasConflict);
  }

  @Test
  @DisplayName("Trả về false khi buổi học diễn ra vào thứ khác (không trùng ngày trong tuần)")
  void shouldReturnFalseWhenDifferentDayOfWeek() {
    // Session is on Tuesday 14:00-16:00
    ZonedDateTime sessionStart = nowZdt.plusDays(1).withHour(14).withMinute(0);
    ZonedDateTime sessionEnd = sessionStart.plusHours(2);
    TutoringSession session = createScheduledSession(sessionStart, sessionEnd);

    when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any())).thenReturn(List.of(session));

    // Candidate slot is Monday 14:00-16:00
    boolean hasConflict = adapter.hasConflictWithScheduledSessions(tutorId, List.of("MONDAY 14:00-16:00"));
    assertFalse(hasConflict);
  }

  @Test
  @DisplayName("Trả về true khi buổi học trùng thứ và giao thoa khung giờ (Canonical format)")
  void shouldReturnTrueWhenOverlappingCanonical() {
    // Session is Monday 14:00-16:00
    ZonedDateTime sessionStart = nowZdt.withHour(14).withMinute(0);
    ZonedDateTime sessionEnd = sessionStart.plusHours(2);
    TutoringSession session = createScheduledSession(sessionStart, sessionEnd);

    when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any())).thenReturn(List.of(session));

    // Candidate slot is Monday 15:00-17:00 (overlaps 15:00-16:00)
    boolean hasConflict = adapter.hasConflictWithScheduledSessions(tutorId, List.of("MONDAY 15:00-17:00"));
    assertTrue(hasConflict);
  }

  @Test
  @DisplayName("Trả về true khi kiểm tra với định dạng tiếng Việt (Thứ 2 · 14:00–16:00)")
  void shouldReturnTrueWhenOverlappingVietnamesePattern() {
    ZonedDateTime sessionStart = nowZdt.withHour(14).withMinute(0);
    ZonedDateTime sessionEnd = sessionStart.plusHours(2);
    TutoringSession session = createScheduledSession(sessionStart, sessionEnd);

    when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any())).thenReturn(List.of(session));

    boolean hasConflict = adapter.hasConflictWithScheduledSessions(tutorId, List.of("Thứ 2 · 13:30–15:30"));
    assertTrue(hasConflict);
  }

  @Test
  @DisplayName("Trả về false khi tiếp xúc đúng biên thời gian (Boundary Touch không xung đột)")
  void shouldReturnFalseWhenExactBoundaryTouch() {
    // Session is Monday 14:00-16:00
    ZonedDateTime sessionStart = nowZdt.withHour(14).withMinute(0);
    ZonedDateTime sessionEnd = sessionStart.plusHours(2);
    TutoringSession session = createScheduledSession(sessionStart, sessionEnd);

    when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any())).thenReturn(List.of(session));

    // Candidate is Monday 16:00-18:00 (exact boundary touch at 16:00)
    boolean hasConflict = adapter.hasConflictWithScheduledSessions(tutorId, List.of("MONDAY 16:00-18:00"));
    assertFalse(hasConflict);
  }
}
