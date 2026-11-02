package vn.edufit.scheduling.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edufit.scheduling.api.dto.ClassSessionStatsView;
import vn.edufit.scheduling.api.dto.SessionSummaryView;
import vn.edufit.scheduling.api.dto.SessionTimelineView;
import vn.edufit.scheduling.application.service.SessionFacadeImpl;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;

class SessionFacadeImplTest {

  private TutoringSessionDomainRepository sessionRepository;
  private SessionFacadeImpl sessionFacade;

  @BeforeEach
  void setUp() {
    sessionRepository = mock(TutoringSessionDomainRepository.class);
    sessionFacade = new SessionFacadeImpl(sessionRepository);
  }

  private TutoringSession buildSession(UUID sessionId, UUID classId, Instant startAt, Instant endAt, SessionStatus status) {
    return new TutoringSession(
        sessionId,
        classId,
        UUID.randomUUID(),
        UUID.randomUUID(),
        startAt,
        endAt,
        SessionMode.ONLINE,
        null,
        null,
        null,
        status,
        UUID.randomUUID(),
        null,
        null,
        null,
        startAt.plusSeconds(3600),
        null,
        null,
        null,
        null,
        false,
        null,
        null,
        0,
        Instant.now()
    );
  }

  @Test
  @DisplayName("findSessionById: tra ve SessionSummaryView khi ton tai")
  void findSessionById_Found() {
    UUID sessionId = UUID.randomUUID();
    UUID classId = UUID.randomUUID();
    Instant start = Instant.parse("2026-10-20T10:00:00Z");
    Instant end = Instant.parse("2026-10-20T12:00:00Z");

    TutoringSession session = buildSession(sessionId, classId, start, end, SessionStatus.SCHEDULED);

    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

    Optional<SessionSummaryView> result = sessionFacade.findSessionById(sessionId);

    assertThat(result).isPresent();
    assertThat(result.get().sessionId()).isEqualTo(sessionId);
    assertThat(result.get().classId()).isEqualTo(classId);
    assertThat(result.get().status()).isEqualTo("SCHEDULED");
  }

  @Test
  @DisplayName("findSessionsByClassId: tra ve danh sach SessionTimelineView")
  void findSessionsByClassId_Success() {
    UUID classId = UUID.randomUUID();
    Instant from = Instant.parse("2026-10-01T00:00:00Z");
    Instant to = Instant.parse("2026-10-31T23:59:59Z");

    UUID s1Id = UUID.randomUUID();
    TutoringSession s1 = buildSession(s1Id, classId, Instant.parse("2026-10-10T10:00:00Z"), Instant.parse("2026-10-10T12:00:00Z"), SessionStatus.COMPLETED);

    when(sessionRepository.findByClassId(classId, from, to)).thenReturn(List.of(s1));

    List<SessionTimelineView> result = sessionFacade.findSessionsByClassId(classId, from, to);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).sessionId()).isEqualTo(s1Id);
    assertThat(result.get(0).status()).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("getSessionStatsByClassIds: tinh toan chinh xac gom nhom cho cac lop")
  void getSessionStatsByClassIds_AggregatesCorrectly() {
    UUID classId1 = UUID.randomUUID();
    UUID classId2 = UUID.randomUUID();
    Instant now = Instant.now();

    TutoringSession s1 = buildSession(UUID.randomUUID(), classId1, now.minusSeconds(7200), now.minusSeconds(3600), SessionStatus.COMPLETED);
    TutoringSession s2 = buildSession(UUID.randomUUID(), classId1, now.plusSeconds(3600), now.plusSeconds(7200), SessionStatus.SCHEDULED);
    TutoringSession s3 = buildSession(UUID.randomUUID(), classId2, now.minusSeconds(10000), now.minusSeconds(6000), SessionStatus.ABSENT);

    when(sessionRepository.findByClassIds(eq(List.of(classId1, classId2)), any(), any()))
        .thenReturn(List.of(s1, s2, s3));

    Map<UUID, ClassSessionStatsView> stats = sessionFacade.getSessionStatsByClassIds(List.of(classId1, classId2), null, null);

    assertThat(stats).containsKeys(classId1, classId2);
    ClassSessionStatsView stats1 = stats.get(classId1);
    assertThat(stats1.completedCount()).isEqualTo(1);
    assertThat(stats1.upcomingCount()).isEqualTo(1);
    assertThat(stats1.absentCount()).isEqualTo(0);
    assertThat(stats1.nextSessionStart()).isEqualTo(s2.getStartAt());

    ClassSessionStatsView stats2 = stats.get(classId2);
    assertThat(stats2.completedCount()).isEqualTo(0);
    assertThat(stats2.absentCount()).isEqualTo(1);
    assertThat(stats2.upcomingCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("countCompletedSessions: tra ve tong so luong buoi hoc COMPLETED")
  void countCompletedSessions_ReturnsRepositoryCount() {
    when(sessionRepository.countCompletedSessions()).thenReturn(25L);
    assertThat(sessionFacade.countCompletedSessions()).isEqualTo(25L);
    verify(sessionRepository).countCompletedSessions();
  }
}
