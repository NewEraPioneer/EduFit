package vn.edufit.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static vn.edufit.discovery.DiscoveryTestData.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.discovery.api.event.MatchingExecutedEvent;
import vn.edufit.discovery.application.matching.MatchWeights;
import vn.edufit.discovery.application.service.DiscoveryFacadeImpl;
import vn.edufit.discovery.application.service.MatchExplanationService;
import vn.edufit.discovery.application.service.NeedAccessService;
import vn.edufit.discovery.application.service.TutorMatchingService;
import vn.edufit.discovery.infra.persistence.entity.MatchingRunLogEntity;
import vn.edufit.discovery.infra.persistence.repository.MatchingRunLogRepository;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.EducationLevelOrderDto;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;

@ExtendWith(MockitoExtension.class)
class TutorMatchingServiceTest {

  @Mock private ProfileFacade profileFacade;
  @Mock private MatchingRunLogRepository logRepository;
  @Mock private MatchExplanationService explanationService;
  @Mock private ConnectionFacade connectionFacade;
  @Mock private ApplicationEventPublisher eventPublisher;

  private TutorMatchingService matchingService;
  private final UUID userId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID needId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    matchingService = new TutorMatchingService(profileFacade, logRepository, explanationService,
        new NeedAccessService(profileFacade, connectionFacade), eventPublisher, MatchWeights.standard(),
        mock(PlatformTransactionManager.class));
    lenient().when(profileFacade.findEducationLevelsForMatching()).thenReturn(List.of(
        new EducationLevelOrderDto(1, 1), new EducationLevelOrderDto(2, 2),
        new EducationLevelOrderDto(LEVEL_8, 3), new EducationLevelOrderDto(LEVEL_9, 4)));
    lenient().when(explanationService.explainMatches(any(), any(), any(), any())).thenAnswer(invocation ->
        IntStream.range(0, invocation.<List<?>>getArgument(2).size())
            .mapToObj(i -> new MatchExplanationService.Explanation("Giải thích " + i, false)).toList());
  }

  private LearningNeedView ownNeed() {
    return need().id(needId).student(studentId, userId).build();
  }

  private static CurrentUser actor(UUID id, String... roles) {
    return new CurrentUser() {
      @Override public UUID getUserId() { return id; }
      @Override public String getEmail() { return "user@edufit.vn"; }
      @Override public Set<String> getRoles() { return Set.of(roles); }
      @Override public boolean hasRole(String role) { return Set.of(roles).contains(role); }
    };
  }

  @Test
  @DisplayName("Lọc mềm: mọi gia sư dạy đúng môn đều có mặt, ai khớp nhiều tiêu chí hơn đứng trước")
  void ranksAllSubjectTutorsBySoftFit() {
    TutorDiscoveryProfileDto perfect = tutor(UUID.randomUUID(), "BOTH", HA_NOI, List.of(CAU_GIAY),
        List.of(slot(2, 18, 22)), BigDecimal.valueOf(5), 10, offering().build());
    TutorDiscoveryProfileDto wrongLevelAndOnline = tutor(UUID.randomUUID(), "ONLINE", null, List.of(),
        List.of(slot(2, 18, 22)), BigDecimal.valueOf(5), 50, offering().levels(1).build());
    when(profileFacade.findLearningNeed(needId)).thenReturn(Optional.of(ownNeed()));
    when(profileFacade.findVerifiedCandidatesBySubject(MATH)).thenReturn(List.of(wrongLevelAndOnline, perfect));

    var result = matchingService.match(actor(userId, "STUDENT"), needId, 5);

    assertEquals(2, result.size());
    assertEquals(perfect.tutor().tutorId(), result.getFirst().score().tutorId());
    assertEquals(wrongLevelAndOnline.tutor().tutorId(), result.get(1).score().tutorId());
    assertTrue(result.get(1).score().checks().stream().anyMatch(check -> !check.matched()));
    verify(logRepository).save(any(MatchingRunLogEntity.class));
    var event = ArgumentCaptor.forClass(MatchingExecutedEvent.class);
    verify(eventPublisher).publishEvent(event.capture());
    assertEquals(2, event.getValue().resultCount());
    assertEquals(needId, event.getValue().needId());
  }

  @Test
  @DisplayName("Nhu cầu chưa đủ thông tin thì báo trường còn thiếu, không chạy ghép đôi")
  void incompleteNeedIsRejected() {
    when(profileFacade.findLearningNeed(needId)).thenReturn(Optional.of(
        need().id(needId).student(studentId, userId).status("INCOMPLETE").build()));
    var ex = assertThrows(InvalidOperationException.class, () -> matchingService.match(actor(userId, "STUDENT"), needId, 5));
    assertTrue(ex.getMessage().contains("mode"));
    verify(profileFacade, never()).findVerifiedCandidatesBySubject(any());
    verifyNoInteractions(logRepository, eventPublisher);
  }

  @Test
  void linkedParentCanMatchChildNeed() {
    UUID parent = UUID.randomUUID();
    when(profileFacade.findLearningNeed(needId)).thenReturn(Optional.of(ownNeed()));
    when(connectionFacade.hasConfirmedParentLink(parent, studentId)).thenReturn(true);
    when(profileFacade.findVerifiedCandidatesBySubject(MATH)).thenReturn(List.of());
    assertTrue(matchingService.match(actor(parent, "PARENT"), needId, 5).isEmpty());
  }

  @Test
  void unlinkedParentAndForeignStudentAreDenied() {
    when(profileFacade.findLearningNeed(needId)).thenReturn(Optional.of(ownNeed()));
    assertThrows(ForbiddenOperationException.class, () -> matchingService.match(actor(UUID.randomUUID(), "PARENT"), needId, 5));
    assertThrows(ForbiddenOperationException.class, () -> matchingService.match(actor(UUID.randomUUID(), "STUDENT"), needId, 5));
    verify(profileFacade, never()).findVerifiedCandidatesBySubject(any());
  }

  @Test
  void archivedOrMissingNeedIsRejected() {
    when(profileFacade.findLearningNeed(needId)).thenReturn(Optional.of(
        need().id(needId).student(studentId, userId).status("ARCHIVED").build()));
    assertThrows(InvalidOperationException.class, () -> matchingService.match(actor(userId, "STUDENT"), needId, 5));
    UUID missing = UUID.randomUUID();
    when(profileFacade.findLearningNeed(missing)).thenReturn(Optional.empty());
    assertThrows(EntityNotFoundException.class, () -> matchingService.match(actor(userId, "STUDENT"), missing, 5));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 11})
  void invalidTopNStopsBeforeDataAccess(int topN) {
    assertThrows(InvalidOperationException.class, () -> matchingService.match(actor(userId, "STUDENT"), needId, topN));
    verifyNoInteractions(profileFacade);
  }

  @Test
  void nonLearnerRoleIsDeniedBeforeReadingNeed() {
    assertThrows(ForbiddenOperationException.class, () -> matchingService.match(actor(userId, "TUTOR"), needId, 5));
    assertThrows(ForbiddenOperationException.class, () -> matchingService.match(null, needId, 5));
    verifyNoInteractions(profileFacade);
  }

  @Test
  void emptyCandidatesStillLogWithoutCallingAi() {
    when(profileFacade.findLearningNeed(needId)).thenReturn(Optional.of(ownNeed()));
    when(profileFacade.findVerifiedCandidatesBySubject(MATH)).thenReturn(List.of());
    assertTrue(matchingService.match(actor(userId, "STUDENT"), needId, 5).isEmpty());
    verify(explanationService, never()).explainMatches(any(), any(), any(), any());
    verify(logRepository).save(any(MatchingRunLogEntity.class));
  }

  @Test
  void topNLimitsResultsAndFacadeMapsSummary() {
    var tutors = IntStream.range(0, 7).mapToObj(i -> tutor(UUID.randomUUID(), "BOTH", HA_NOI, List.of(CAU_GIAY),
        List.of(slot(2, 18, 22)), BigDecimal.valueOf(5), i, offering().build())).toList();
    when(profileFacade.findLearningNeed(needId)).thenReturn(Optional.of(ownNeed()));
    when(profileFacade.findVerifiedCandidatesBySubject(MATH)).thenReturn(tutors);

    var summaries = new DiscoveryFacadeImpl(matchingService).findTopMatchesForNeed(actor(userId, "STUDENT"), needId, 3);

    assertEquals(3, summaries.size());
    assertEquals("Giải thích 0", summaries.getFirst().explanation());
    verify(explanationService).explainMatches(eq(userId), any(), any(), any());
  }
}
