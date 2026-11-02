package vn.edufit.scheduling.infra.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import vn.edufit.scheduling.api.event.SessionExpiredEvent;
import vn.edufit.scheduling.domain.model.ProposalStatus;
import vn.edufit.scheduling.domain.model.SessionHistory;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionRescheduleProposal;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.SessionHistoryDomainRepository;
import vn.edufit.scheduling.domain.repository.SessionRescheduleProposalDomainRepository;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test: ExpireProposalsJob - Quét đề xuất hết hạn (FR-17, BR-43)")
class ExpireProposalsJobTest {

  @Mock
  private TutoringSessionDomainRepository sessionRepository;

  @Mock
  private SessionRescheduleProposalDomainRepository proposalRepository;

  @Mock
  private SessionHistoryDomainRepository historyRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  private final Instant fixedNow = Instant.parse("2026-10-15T12:00:00Z");
  private final Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);

  private ExpireProposalsJob job;

  @BeforeEach
  void setUp() {
    job = new ExpireProposalsJob(
        sessionRepository,
        proposalRepository,
        historyRepository,
        eventPublisher,
        fixedClock
    );
  }

  @Test
  @DisplayName("Khi không có đề xuất nào hết hạn, không thực hiện lưu hay bắn sự kiện")
  void sweepExpiredProposals_whenEmpty_doesNothing() {
    when(sessionRepository.findExpiredProposals(fixedNow)).thenReturn(List.of());
    when(proposalRepository.findExpiredPendingProposals(fixedNow)).thenReturn(List.of());

    job.sweepExpiredProposals();

    verify(sessionRepository, never()).save(any());
    verify(proposalRepository, never()).save(any());
    verify(historyRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("Quét và chuyển trạng thái các đề xuất buổi học quá hạn sang EXPIRED thành công")
  void sweepExpiredProposals_withExpiredSessions_updatesStatusAndPublishesEvent() {
    UUID sessionId = UUID.randomUUID();
    UUID classId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();

    Instant startAt = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant endAt = startAt.plus(1, ChronoUnit.HOURS);
    Instant expiresAt = fixedNow.minus(1, ChronoUnit.MINUTES); // đã quá hạn

    TutoringSession expiredSession = TutoringSession.createProposed(
        sessionId,
        classId,
        tutorId,
        studentId,
        startAt,
        endAt,
        SessionMode.ONLINE,
        "https://meet.google.com/test",
        null,
        "Đề xuất học thử",
        tutorId,
        expiresAt,
        fixedNow.minus(24, ChronoUnit.HOURS)
    );

    when(sessionRepository.findExpiredProposals(fixedNow)).thenReturn(List.of(expiredSession));
    when(proposalRepository.findExpiredPendingProposals(fixedNow)).thenReturn(List.of());

    job.sweepExpiredProposals();

    assertThat(expiredSession.getStatus()).isEqualTo(SessionStatus.EXPIRED);
    verify(sessionRepository).save(expiredSession);

    ArgumentCaptor<SessionHistory> historyCaptor = ArgumentCaptor.forClass(SessionHistory.class);
    verify(historyRepository).save(historyCaptor.capture());
    assertThat(historyCaptor.getValue().getAction()).isEqualTo("EXPIRE");
    assertThat(historyCaptor.getValue().getSessionId()).isEqualTo(sessionId);

    ArgumentCaptor<SessionExpiredEvent> eventCaptor = ArgumentCaptor.forClass(SessionExpiredEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().sessionId()).isEqualTo(sessionId);
    assertThat(eventCaptor.getValue().tutorId()).isEqualTo(tutorId);
    assertThat(eventCaptor.getValue().studentId()).isEqualTo(studentId);
  }

  @Test
  @DisplayName("Quét và chuyển trạng thái các đề xuất dời lịch quá hạn sang EXPIRED thành công")
  void sweepExpiredProposals_withExpiredRescheduleProposals_updatesStatusAndLogsHistory() {
    UUID proposalId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();

    Instant newStartAt = fixedNow.plus(3, ChronoUnit.DAYS);
    Instant newEndAt = newStartAt.plus(1, ChronoUnit.HOURS);
    Instant expiresAt = fixedNow.minus(10, ChronoUnit.MINUTES);

    SessionRescheduleProposal expiredProposal = SessionRescheduleProposal.create(
        proposalId,
        sessionId,
        tutorId,
        newStartAt,
        newEndAt,
        "Bận việc đột xuất",
        expiresAt,
        fixedNow.minus(24, ChronoUnit.HOURS)
    );

    when(sessionRepository.findExpiredProposals(fixedNow)).thenReturn(List.of());
    when(proposalRepository.findExpiredPendingProposals(fixedNow)).thenReturn(List.of(expiredProposal));

    job.sweepExpiredProposals();

    assertThat(expiredProposal.getStatus()).isEqualTo(ProposalStatus.EXPIRED);
    verify(proposalRepository).save(expiredProposal);

    ArgumentCaptor<SessionHistory> historyCaptor = ArgumentCaptor.forClass(SessionHistory.class);
    verify(historyRepository).save(historyCaptor.capture());
    assertThat(historyCaptor.getValue().getAction()).isEqualTo("RESCHEDULE_EXPIRE");
    assertThat(historyCaptor.getValue().getRescheduleProposalId()).isEqualTo(proposalId);
  }
}
