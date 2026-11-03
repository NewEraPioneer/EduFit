package vn.edufit.progress.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.progress.domain.model.SelfRating;
import vn.edufit.progress.domain.model.SessionSelfRating;
import vn.edufit.progress.domain.repository.SessionSelfRatingDomainRepository;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.scheduling.api.dto.SessionSummaryView;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;

class SessionSelfRatingServiceTest {

  private final Instant now = Instant.parse("2026-11-02T10:00:00Z");
  private final UUID classId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID studentUserId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();

  private SessionSelfRatingDomainRepository ratingRepository;
  private SessionFacade sessionFacade;
  private ProfileFacade profileFacade;
  private SessionSelfRatingService service;
  private CurrentUser student;

  @BeforeEach
  void setUp() {
    ratingRepository = mock(SessionSelfRatingDomainRepository.class);
    sessionFacade = mock(SessionFacade.class);
    profileFacade = mock(ProfileFacade.class);
    ConnectionFacade connectionFacade = mock(ConnectionFacade.class);
    service = new SessionSelfRatingService(ratingRepository, sessionFacade,
        new ProgressAccessGuard(connectionFacade, profileFacade), Clock.fixed(now, ZoneId.of("UTC")));

    student = mock(CurrentUser.class);
    when(student.hasRole("STUDENT")).thenReturn(true);
    when(student.getUserId()).thenReturn(studentUserId);
    when(profileFacade.getStudentIdByUserId(studentUserId)).thenReturn(studentId);
    when(connectionFacade.findClassById(classId)).thenReturn(Optional.of(new ConnectionFacade.ClassDetailView(
        classId, studentId, UUID.randomUUID(), UUID.randomUUID(), "ACTIVE", now, null, 200000L)));
    givenSession("COMPLETED");
    when(ratingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  private void givenSession(String status) {
    when(sessionFacade.findSessionById(sessionId)).thenReturn(Optional.of(new SessionSummaryView(
        sessionId, classId, UUID.randomUUID(), studentId, now.minusSeconds(7200), now.minusSeconds(3600),
        "ONLINE", status, false, now.minusSeconds(86400))));
  }

  @Test
  @DisplayName("Học sinh chọn cảm nhận lần đầu: tạo bản ghi gắn lớp và học sinh")
  void rate_createsRating() {
    when(ratingRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

    assertThat(service.rate(student, sessionId, 3)).isEqualTo(3);

    ArgumentCaptor<SessionSelfRating> captor = ArgumentCaptor.forClass(SessionSelfRating.class);
    verify(ratingRepository).save(captor.capture());
    assertThat(captor.getValue().getClassId()).isEqualTo(classId);
    assertThat(captor.getValue().getStudentId()).isEqualTo(studentId);
    assertThat(captor.getValue().getRating()).isEqualTo(SelfRating.CONFIDENT);
  }

  @Test
  @DisplayName("Chọn lại thì ghi đè bản ghi cũ")
  void rate_overwritesExistingRating() {
    SessionSelfRating existing = new SessionSelfRating(UUID.randomUUID(), sessionId, classId, studentId,
        SelfRating.STRUGGLING, studentUserId, now.minusSeconds(600), now.minusSeconds(600));
    when(ratingRepository.findBySessionId(sessionId)).thenReturn(Optional.of(existing));

    assertThat(service.rate(student, sessionId, 2)).isEqualTo(2);
    assertThat(existing.getRating()).isEqualTo(SelfRating.OKAY);
    assertThat(existing.getUpdatedAt()).isEqualTo(now);
  }

  @Test
  @DisplayName("Từ chối: không phải học sinh của lớp, buổi chưa hoàn thành, giá trị ngoài 1–3")
  void rate_rejectsInvalidRequests() {
    CurrentUser parent = mock(CurrentUser.class);
    when(parent.hasRole("PARENT")).thenReturn(true);
    when(parent.getUserId()).thenReturn(UUID.randomUUID());
    assertThatThrownBy(() -> service.rate(parent, sessionId, 2)).isInstanceOf(ForbiddenOperationException.class);

    assertThatThrownBy(() -> service.rate(student, sessionId, 4)).isInstanceOf(InvalidOperationException.class);

    givenSession("SCHEDULED");
    assertThatThrownBy(() -> service.rate(student, sessionId, 2))
        .isInstanceOf(InvalidOperationException.class)
        .hasMessageContaining("đã hoàn thành");
    verify(ratingRepository, never()).save(any());
  }
}
