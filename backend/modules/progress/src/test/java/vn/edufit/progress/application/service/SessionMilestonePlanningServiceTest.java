package vn.edufit.progress.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.progress.application.dto.SessionMilestonesView;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.MilestoneStatus;
import vn.edufit.progress.domain.model.SessionMilestoneLink;
import vn.edufit.progress.domain.repository.LearningPlanDomainRepository;
import vn.edufit.progress.domain.repository.SessionMilestoneLinkDomainRepository;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.scheduling.api.dto.SessionSummaryView;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;

class SessionMilestonePlanningServiceTest {

  private final Instant now = Instant.parse("2026-11-02T10:00:00Z");
  private final UUID classId = UUID.randomUUID();
  private final UUID tutorId = UUID.randomUUID();
  private final UUID tutorUserId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();

  private SessionMilestoneLinkDomainRepository linkRepository;
  private SessionFacade sessionFacade;
  private ProfileFacade profileFacade;
  private ConnectionFacade connectionFacade;
  private SessionMilestonePlanningService service;
  private CurrentUser tutor;
  private Milestone learning;
  private Milestone achieved;

  @BeforeEach
  void setUp() {
    linkRepository = mock(SessionMilestoneLinkDomainRepository.class);
    sessionFacade = mock(SessionFacade.class);
    profileFacade = mock(ProfileFacade.class);
    connectionFacade = mock(ConnectionFacade.class);
    LearningPlanDomainRepository planRepository = mock(LearningPlanDomainRepository.class);
    service = new SessionMilestonePlanningService(linkRepository, planRepository, sessionFacade,
        new ProgressAccessGuard(connectionFacade, profileFacade), Clock.fixed(now, ZoneId.of("UTC")));

    tutor = mock(CurrentUser.class);
    when(tutor.hasRole("TUTOR")).thenReturn(true);
    when(tutor.getUserId()).thenReturn(tutorUserId);
    when(profileFacade.getTutorIdByUserId(tutorUserId)).thenReturn(tutorId);
    when(connectionFacade.findClassById(classId)).thenReturn(Optional.of(new ConnectionFacade.ClassDetailView(
        classId, studentId, tutorId, UUID.randomUUID(), "ACTIVE", now, null, 200000L)));
    givenSession("SCHEDULED");

    learning = new Milestone(UUID.randomUUID(), null, "Hệ phương trình", null, LocalDate.parse("2026-11-20"), 1,
        MilestoneStatus.IN_PROGRESS, null, false, 0, now, List.of());
    achieved = new Milestone(UUID.randomUUID(), null, "Phương trình bậc nhất", null, LocalDate.parse("2026-10-20"), 1,
        MilestoneStatus.ACHIEVED, LocalDate.parse("2026-10-18"), false, 1, now, List.of());
    LearningPlan plan = new LearningPlan(UUID.randomUUID(), classId, null, 0, now, now, List.of(learning, achieved));
    when(planRepository.findByClassId(classId)).thenReturn(Optional.of(plan));
    when(planRepository.findByClassIdIn(any())).thenReturn(List.of(plan));
  }

  private void givenSession(String status) {
    SessionSummaryView view = new SessionSummaryView(sessionId, classId, tutorId, studentId,
        now.plusSeconds(86400), now.plusSeconds(86400 + 5400), "ONLINE", status, false, now);
    when(sessionFacade.findSessionById(sessionId)).thenReturn(Optional.of(view));
    when(sessionFacade.findSessionsByIds(anyCollection())).thenReturn(List.of(view));
  }

  @Test
  @DisplayName("Gia sư gắn cột mốc dự kiến cho buổi chưa diễn ra: thay toàn bộ liên kết PLANNED")
  void planMilestones_replacesPlannedLinks() {
    SessionMilestonesView view = service.planMilestones(tutor, sessionId, List.of(learning.getMilestoneId()));

    verify(linkRepository).replacePlanned(sessionId, Set.of(learning.getMilestoneId()), tutorUserId, now);
    assertThat(view.linkType()).isEqualTo("PLANNED");
    assertThat(view.milestones()).extracting(SessionMilestonesView.MilestoneRef::title).containsExactly("Hệ phương trình");

    service.planMilestones(tutor, sessionId, List.of());
    verify(linkRepository).replacePlanned(sessionId, Collections.emptySet(), tutorUserId, now);
  }

  @Test
  @DisplayName("Từ chối: buổi đã diễn ra, cột mốc đã đạt, cột mốc lạ, quá 3 cột mốc, không phải gia sư lớp")
  void planMilestones_rejectsInvalidRequests() {
    assertThatThrownBy(() -> service.planMilestones(tutor, sessionId, List.of(achieved.getMilestoneId())))
        .isInstanceOf(InvalidOperationException.class).hasMessageContaining("đã đạt");
    assertThatThrownBy(() -> service.planMilestones(tutor, sessionId, List.of(UUID.randomUUID())))
        .isInstanceOf(InvalidOperationException.class).hasMessageContaining("không thuộc lộ trình");
    assertThatThrownBy(() -> service.planMilestones(tutor, sessionId,
        List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
        .isInstanceOf(InvalidOperationException.class).hasMessageContaining("tối đa 3");

    givenSession("COMPLETED");
    assertThatThrownBy(() -> service.planMilestones(tutor, sessionId, List.of(learning.getMilestoneId())))
        .isInstanceOf(InvalidOperationException.class).hasMessageContaining("chưa diễn ra");

    givenSession("SCHEDULED");
    when(profileFacade.getTutorIdByUserId(tutorUserId)).thenReturn(UUID.randomUUID());
    assertThatThrownBy(() -> service.planMilestones(tutor, sessionId, List.of(learning.getMilestoneId())))
        .isInstanceOf(ForbiddenOperationException.class);
    verify(linkRepository, never()).replacePlanned(any(), any(), any(), any());
  }

  @Test
  @DisplayName("Lịch: trả cột mốc hiệu lực kèm tên; người không thuộc lớp không thấy gì")
  void findSessionMilestones_resolvesTitlesAndFiltersByAccess() {
    when(linkRepository.findBySessionIdIn(List.of(sessionId))).thenReturn(List.of(
        new SessionMilestoneLink(sessionId, learning.getMilestoneId(), SessionMilestoneLink.Type.PLANNED)));

    assertThat(service.findSessionMilestones(tutor, List.of(sessionId))).singleElement().satisfies(v -> {
      assertThat(v.linkType()).isEqualTo("PLANNED");
      assertThat(v.milestones()).extracting(SessionMilestonesView.MilestoneRef::title).containsExactly("Hệ phương trình");
    });

    CurrentUser stranger = mock(CurrentUser.class);
    when(stranger.hasRole("PARENT")).thenReturn(true);
    when(stranger.getUserId()).thenReturn(UUID.randomUUID());
    assertThat(service.findSessionMilestones(stranger, List.of(sessionId))).isEmpty();
    verify(linkRepository, never()).findBySessionIdIn(eq(List.of()));
  }
}
