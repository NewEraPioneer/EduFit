package vn.edufit.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.discovery.application.service.MatchExplanationService;
import vn.edufit.discovery.application.service.DiscoveryQueryService;
import vn.edufit.discovery.application.service.TutorMatchingService;
import vn.edufit.discovery.infra.persistence.entity.MatchingRunLogEntity;
import vn.edufit.discovery.infra.persistence.repository.MatchingRunLogRepository;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.EducationLevelOrderDto;
import vn.edufit.profile.api.dto.LearningGoalDiscoveryDto;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;
import vn.edufit.profile.api.dto.TutorCandidateCriteria;
import vn.edufit.profile.api.dto.TutorSubjectDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.profile.api.dto.WeeklyAvailabilityDto;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;

@ExtendWith(MockitoExtension.class)
class TutorMatchingServiceTest {

  @Mock
  private ProfileFacade profileFacade;
  @Mock
  private MatchingRunLogRepository logRepository;
  @Mock
  private MatchExplanationService explanationService;
  @Mock private ConnectionFacade connectionFacade;
  @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

  private TutorMatchingService matchingService;
  private UUID userId;
  private UUID goalId;
  private WeeklyAvailabilityDto monday;

  @BeforeEach
  void setUp() {
    matchingService = new TutorMatchingService(profileFacade, logRepository, explanationService,
        new DiscoveryQueryService(profileFacade), connectionFacade, eventPublisher, org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class));
    userId = UUID.randomUUID();
    goalId = UUID.randomUUID();
    monday = new WeeklyAvailabilityDto((short) 1, LocalTime.of(18, 0), LocalTime.of(20, 0));
  }

  @Test
  @DisplayName("Xếp hạng gia sư đủ hard filter, tạo giải thích và ghi matching run")
  void shouldRankEligibleTutorsAndLogRun() {
    TutorDiscoveryProfileDto best = tutor(BigDecimal.valueOf(5), 20, 250_000L);
    TutorDiscoveryProfileDto second = tutor(BigDecimal.valueOf(4), 8, 320_000L);
    when(profileFacade.findLearningGoalForDiscovery(goalId)).thenReturn(Optional.of(goal(userId)));
    when(profileFacade.findVerifiedCandidatesBySubject(any())).thenReturn(List.of(second, best));
    when(explanationService.explainMatches(eq(userId), any(), any(), any()))
        .thenReturn(List.of(new MatchExplanationService.Explanation("Giải thích AI", true),
            new MatchExplanationService.Explanation("Giải thích theo quy tắc", false)));

    var result = matchingService.match(currentUser(userId), goalId, 5);

    assertEquals(2, result.size());
    assertEquals(best.tutor().tutorId(), result.getFirst().score().tutorId());
    assertEquals(true, result.getFirst().aiGenerated());
    assertEquals("Giải thích theo quy tắc", result.get(1).explanation());
    verify(logRepository).save(any(MatchingRunLogEntity.class));
    org.mockito.Mockito.verifyNoInteractions(connectionFacade);
    var event = org.mockito.ArgumentCaptor.forClass(vn.edufit.discovery.api.event.MatchingExecutedEvent.class);
    verify(eventPublisher).publishEvent(event.capture());
    assertEquals(2, event.getValue().resultCount());
  }

  @Test
  @DisplayName("Từ chối ghép đôi goal không thuộc người dùng hiện tại")
  void shouldRejectForeignGoal() {
    when(profileFacade.findLearningGoalForDiscovery(goalId)).thenReturn(Optional.of(goal(UUID.randomUUID())));

    assertThrows(
        ForbiddenOperationException.class,
        () -> matchingService.match(currentUser(userId), goalId, 5)
    );
  }

  @ParameterizedTest
  @CsvSource({"80,90.00,50.00", "41,90.00,50.00", "3,80.00,0.00"})
  void matchingLoadsCatalogOnceAndAppliesSoftLevelFit(int level, String total, String levelFit) {
    var base = tutor(BigDecimal.valueOf(5), 10, 200000);
    var candidate = new TutorDiscoveryProfileDto(base.tutor(), List.of(new TutorSubjectDto(1, "Math", level, "Level")),
        base.availabilitySlots(), base.registeredAt());
    when(profileFacade.findLearningGoalForDiscovery(goalId)).thenReturn(Optional.of(goal(userId)));
    when(profileFacade.findEducationLevelsForMatching()).thenReturn(List.of(
        new EducationLevelOrderDto(80, 10), new EducationLevelOrderDto(2, 30),
        new EducationLevelOrderDto(41, 60), new EducationLevelOrderDto(12, 90)));
    when(profileFacade.findVerifiedCandidatesBySubject(any())).thenReturn(List.of(candidate));
    when(explanationService.explainMatches(eq(userId), any(), any(), any()))
        .thenReturn(List.of(new MatchExplanationService.Explanation("Explanation", false)));

    var result = matchingService.match(currentUser(userId), goalId, 5).getFirst();

    assertEquals(new BigDecimal(total), result.score().total());
    assertEquals(new BigDecimal(levelFit), result.score().levelFit());
    verify(profileFacade).findEducationLevelsForMatching();
  }

  @Test
  @DisplayName("Từ chối role ngoài STUDENT/PARENT trước khi đọc goal")
  void shouldRejectNonLearnerRole() {
    assertThrows(
        ForbiddenOperationException.class,
        () -> matchingService.match(currentUser(userId, "TUTOR"), goalId, 5)
    );
    org.mockito.Mockito.verifyNoInteractions(profileFacade);
  }

  private LearningGoalDiscoveryDto goal(UUID ownerId) {
    return new LearningGoalDiscoveryDto(
        goalId, UUID.randomUUID(), ownerId, 1, 2, "ONLINE", "Hà Nội",
        100_000L, 300_000L, "ACTIVE", List.of(monday)
    );
  }

  @Test
  void linkedParentCanMatchStudentGoal() {
    var goal = goal(UUID.randomUUID());
    when(profileFacade.findLearningGoalForDiscovery(goalId)).thenReturn(Optional.of(goal));
    when(connectionFacade.hasConfirmedParentLink(userId, goal.studentId())).thenReturn(true);
    assertEquals(0, matchingService.match(currentUser(userId, "PARENT"), goalId, 5).size());
    verify(logRepository).save(any(MatchingRunLogEntity.class));
  }

  @Test
  void parentWithoutConfirmedLinkIsDeniedEvenWithMatchingUserId() {
    var goal = goal(userId);
    when(profileFacade.findLearningGoalForDiscovery(goalId)).thenReturn(Optional.of(goal));
    assertThrows(ForbiddenOperationException.class,
        () -> matchingService.match(currentUser(userId, "PARENT"), goalId, 5));
    verify(connectionFacade).hasConfirmedParentLink(userId, goal.studentId());
    org.mockito.Mockito.verifyNoInteractions(explanationService, logRepository);
    org.mockito.Mockito.verify(profileFacade, org.mockito.Mockito.never()).findEducationLevelsForMatching();
  }

  @Test
  void studentCannotBorrowParentLinkForForeignGoal() {
    when(profileFacade.findLearningGoalForDiscovery(goalId)).thenReturn(Optional.of(goal(UUID.randomUUID())));
    assertThrows(ForbiddenOperationException.class,
        () -> matchingService.match(currentUser(userId), goalId, 5));
    org.mockito.Mockito.verifyNoInteractions(connectionFacade, explanationService, logRepository);
  }

  @Test
  void missingGoalDoesNotCallConnectionAiOrLog() {
    assertThrows(EntityNotFoundException.class,
        () -> matchingService.match(currentUser(userId, "PARENT"), goalId, 5));
    org.mockito.Mockito.verifyNoInteractions(connectionFacade, explanationService, logRepository);
  }

  @ParameterizedTest
  @CsvSource({"INACTIVE,2,300000,true", "ACTIVE,,300000,true", "ACTIVE,2,0,true", "ACTIVE,2,300000,false"})
  void invalidGoalDoesNotExecuteMatching(String status, Integer level, long budget, boolean hasSlots) {
    var goal = new LearningGoalDiscoveryDto(goalId, UUID.randomUUID(), userId, 1, level,
        "ONLINE", "Hanoi", 0L, budget, status, hasSlots ? List.of(monday) : List.of());
    when(profileFacade.findLearningGoalForDiscovery(goalId)).thenReturn(Optional.of(goal));
    assertThrows(InvalidOperationException.class, () -> matchingService.match(currentUser(userId), goalId, 5));
    org.mockito.Mockito.verifyNoInteractions(connectionFacade, explanationService, logRepository);
  }

  @Test
  void missingActorIsDeniedBeforeReadingGoal() {
    assertThrows(ForbiddenOperationException.class, () -> matchingService.match(null, goalId, 5));
    org.mockito.Mockito.verifyNoInteractions(profileFacade, connectionFacade, explanationService, logRepository, eventPublisher);
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(ints = {-1, 0, 11, Integer.MAX_VALUE})
  void invalidTopNStopsBeforeDataAccess(int topN) {
    assertThrows(InvalidOperationException.class, () -> matchingService.match(currentUser(userId), goalId, topN));
    org.mockito.Mockito.verifyNoInteractions(profileFacade, connectionFacade, explanationService, logRepository);
  }

  @Test
  void missingGoalIdStopsBeforeDataAccess() {
    assertThrows(InvalidOperationException.class, () -> matchingService.match(currentUser(userId), null, 5));
    org.mockito.Mockito.verifyNoInteractions(profileFacade, connectionFacade, explanationService, logRepository);
  }

  @Test
  void emptyBoundedCandidatesStillLogWithoutCallingAi() {
    var goal = goal(userId);
    when(profileFacade.findLearningGoalForDiscovery(goalId)).thenReturn(Optional.of(goal));
    assertEquals(List.of(), matchingService.match(currentUser(userId), goalId, 5));
    verify(profileFacade).findVerifiedCandidatesBySubject(new TutorCandidateCriteria(1, "ONLINE", "Hà Nội"));
    org.mockito.Mockito.verifyNoInteractions(explanationService);
    var log = org.mockito.ArgumentCaptor.forClass(MatchingRunLogEntity.class);
    verify(logRepository).save(log.capture());
    assertEquals(0, log.getValue().getResultCount());
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(ints = {1, 5, 10})
  void boundedCandidatesProduceStableTopN(int topN) {
    var candidates = java.util.stream.IntStream.range(0, 12)
        .mapToObj(i -> tutor(BigDecimal.valueOf(5), 10, 200000)).toList();
    when(profileFacade.findLearningGoalForDiscovery(goalId)).thenReturn(Optional.of(goal(userId)));
    when(profileFacade.findVerifiedCandidatesBySubject(any())).thenReturn(candidates);
    when(explanationService.explainMatches(eq(userId), any(), any(), any()))
        .thenAnswer(inv -> java.util.Collections.nCopies(((List<?>) inv.getArgument(2)).size(),
            new MatchExplanationService.Explanation("Explanation", false)));
    var first = matchingService.match(currentUser(userId), goalId, topN);
    var second = matchingService.match(currentUser(userId), goalId, topN);
    assertEquals(topN, first.size());
    assertEquals(first, second);
    assertEquals(candidates.stream().map(p -> p.tutor().tutorId()).sorted().limit(topN).toList(),
        first.stream().map(p -> p.score().tutorId()).toList());
    verify(profileFacade, org.mockito.Mockito.times(2))
        .findVerifiedCandidatesBySubject(new TutorCandidateCriteria(1, "ONLINE", "Hà Nội"));
  }

  @Test
  void successfulEmptyRunPublishesExactlyOneEventMatchingLog() {
    var goal = goal(userId);
    when(profileFacade.findLearningGoalForDiscovery(goalId)).thenReturn(Optional.of(goal));
    matchingService.match(currentUser(userId), goalId, 5);
    var log = org.mockito.ArgumentCaptor.forClass(MatchingRunLogEntity.class);
    var event = org.mockito.ArgumentCaptor.forClass(vn.edufit.discovery.api.event.MatchingExecutedEvent.class);
    verify(logRepository).save(log.capture());
    verify(eventPublisher).publishEvent(event.capture());
    assertEquals(log.getValue().getUserId(), event.getValue().userId());
    assertEquals(log.getValue().getStudentId(), event.getValue().studentId());
    assertEquals(log.getValue().getGoalId(), event.getValue().goalId());
    assertEquals(log.getValue().getResultCount(), event.getValue().resultCount());
    assertEquals(log.getValue().getCreatedAt(), event.getValue().executedAt());
    org.mockito.Mockito.verifyNoMoreInteractions(logRepository, eventPublisher);
  }

  @Test
  void failedLogSaveDoesNotPublishEvent() {
    when(profileFacade.findLearningGoalForDiscovery(goalId)).thenReturn(Optional.of(goal(userId)));
    when(logRepository.save(any())).thenThrow(new IllegalStateException("Storage unavailable"));
    assertThrows(IllegalStateException.class, () -> matchingService.match(currentUser(userId), goalId, 5));
    org.mockito.Mockito.verifyNoInteractions(eventPublisher);
  }

  @Test
  void facadeMapsAuthorizedMatchingToPublicSummary() {
    var tutor = tutor(BigDecimal.valueOf(5), 10, 200000);
    when(profileFacade.findLearningGoalForDiscovery(goalId)).thenReturn(Optional.of(goal(userId)));
    when(profileFacade.findVerifiedCandidatesBySubject(any())).thenReturn(List.of(tutor));
    when(explanationService.explainMatches(eq(userId), any(), any(), any()))
        .thenReturn(List.of(new MatchExplanationService.Explanation("Grounded explanation", true)));
    var facade = new vn.edufit.discovery.application.service.DiscoveryFacadeImpl(matchingService);
    var summary = facade.findTopMatchesForGoal(currentUser(userId), goalId, 5).getFirst();
    assertEquals(tutor.tutor().tutorId(), summary.tutorId());
    assertEquals(tutor.tutor().displayName(), summary.displayName());
    assertEquals(new BigDecimal("100.00"), summary.matchScore());
    assertEquals("Grounded explanation", summary.explanation());
    assertEquals(true, summary.aiGenerated());
  }

  @Test
  void linkedParentUsesOneOwnAiQuotaForAllRankedTutors() {
    var gateway = org.mockito.Mockito.mock(vn.edufit.ai.api.AiGateway.class);
    var goal = goal(UUID.randomUUID());
    when(profileFacade.findLearningGoalForDiscovery(goalId)).thenReturn(Optional.of(goal));
    when(connectionFacade.hasConfirmedParentLink(userId, goal.studentId())).thenReturn(true);
    when(profileFacade.findVerifiedCandidatesBySubject(any()))
        .thenReturn(List.of(tutor(BigDecimal.valueOf(5), 10, 200000), tutor(BigDecimal.valueOf(4), 8, 300000)));
    when(gateway.complete(any())).thenReturn(new vn.edufit.ai.api.AiResponse(
        "1|Lịch người đầu phù hợp đầy đủ.\n2|Học phí người thứ hai cần cân nhắc.", 1, 1, true));
    var service = new TutorMatchingService(profileFacade, logRepository, new MatchExplanationService(gateway),
        new DiscoveryQueryService(profileFacade), connectionFacade, eventPublisher, org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class));
    var results = service.match(currentUser(userId, "PARENT"), goalId, 5);
    assertEquals(2, results.size());
    assertEquals(true, results.getFirst().aiGenerated());
    assertEquals(true, results.get(1).aiGenerated());
    var request = org.mockito.ArgumentCaptor.forClass(vn.edufit.ai.api.AiRequest.class);
    verify(gateway).complete(request.capture());
    assertEquals(userId, request.getValue().userId());
    org.mockito.Mockito.verifyNoMoreInteractions(gateway);
  }

  private TutorDiscoveryProfileDto tutor(BigDecimal rating, int reviews, long price) {
    UUID tutorId = UUID.randomUUID();
    TutorSummaryDto summary = new TutorSummaryDto(
        tutorId, UUID.randomUUID(), "Gia sư", "Toán", "Bio", "ONLINE", "Hà Nội",
        price, (short) 5, "Thực hành", "VERIFIED", Instant.now(), rating, reviews
    );
    return new TutorDiscoveryProfileDto(
        summary,
        List.of(new TutorSubjectDto(1, "Toán", 2, "THCS")),
        List.of(monday),
        Instant.parse("2025-01-01T00:00:00Z")
    );
  }

  private CurrentUser currentUser(UUID id) {
    return currentUser(id, "STUDENT");
  }

  private CurrentUser currentUser(UUID id, String role) {
    return new CurrentUser() {
      @Override
      public UUID getUserId() {
        return id;
      }

      @Override
      public String getEmail() {
        return "student@example.com";
      }

      @Override
      public Set<String> getRoles() {
        return Set.of(role);
      }

      @Override
      public boolean hasRole(String role) {
        return getRoles().contains(role);
      }
    };
  }
}
