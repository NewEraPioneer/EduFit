package vn.edufit.discovery.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edufit.discovery.application.service.DiscoveryQueryService;
import vn.edufit.discovery.application.service.MatchExplanationService;
import vn.edufit.discovery.application.service.TutorMatchingService;
import vn.edufit.discovery.infra.persistence.repository.MatchingRunLogRepository;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.LearningGoalDiscoveryDto;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;
import vn.edufit.profile.api.dto.TutorSearchCriteria;
import vn.edufit.profile.api.dto.TutorSubjectDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.profile.api.dto.WeeklyAvailabilityDto;
import vn.edufit.shared.auth.CurrentUser;

@ExtendWith(MockitoExtension.class)
class DiscoveryControllerTest {

  @Mock private ProfileFacade profileFacade;
  @Mock private MatchingRunLogRepository logRepository;
  @Mock private MatchExplanationService explanationService;
  @Mock private ObjectProvider<CurrentUser> currentUserProvider;
  @Mock private CurrentUser currentUser;

  private MockMvc mvc;
  private TutorDiscoveryProfileDto profile;

  @BeforeEach
  void setUp() {
    mvc = mvc(explanationService);
    profile = new TutorDiscoveryProfileDto(
        new TutorSummaryDto(
            UUID.randomUUID(), UUID.randomUUID(), "Tutor A", "Math", "x".repeat(200),
            "ONLINE", "Hanoi", 200_000L, (short) 5, "Practice", "VERIFIED",
            Instant.parse("2025-01-01T00:00:00Z"), BigDecimal.valueOf(5), 10
        ),
        List.of(new TutorSubjectDto(1, "Math", 2, "Secondary")),
        List.of(new WeeklyAvailabilityDto((short) 1, LocalTime.of(18, 0), LocalTime.of(20, 0))),
        Instant.parse("2025-01-01T00:00:00Z")
    );
  }

  private MockMvc mvc(MatchExplanationService explanations) {
    return MockMvcBuilders.standaloneSetup(new DiscoveryController(
        new DiscoveryQueryService(profileFacade),
        new TutorMatchingService(profileFacade, logRepository, explanations, new DiscoveryQueryService(profileFacade),
            org.mockito.Mockito.mock(vn.edufit.connection.api.ConnectionFacade.class),
            org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class), org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class)),
        currentUserProvider
    )).build();
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUCCESS", "TIMEOUT", "UNAVAILABLE", "QUOTA"})
  void aiFailuresAndQuotaStillReturnSuccessfulMatchingHttpResponse(String outcome) throws Exception {
    var gateway = org.mockito.Mockito.mock(vn.edufit.ai.api.AiGateway.class);
    UUID userId = UUID.randomUUID(), goalId = UUID.randomUUID();
    when(currentUserProvider.getIfAvailable()).thenReturn(currentUser);
    when(currentUser.getUserId()).thenReturn(userId);
    when(currentUser.hasRole("STUDENT")).thenReturn(true);
    when(profileFacade.findLearningGoalForDiscovery(goalId)).thenReturn(Optional.of(new LearningGoalDiscoveryDto(
        goalId, UUID.randomUUID(), userId, 1, 2, "ONLINE", "Hanoi", 0L, 300000L, "ACTIVE", profile.availabilitySlots())));
    when(profileFacade.findVerifiedCandidatesBySubject(any())).thenReturn(List.of(profile));
    if (outcome.equals("SUCCESS")) {
      when(gateway.complete(any())).thenReturn(new vn.edufit.ai.api.AiResponse("1|Grounded AI explanation", 1, 1));
    } else {
      when(gateway.complete(any())).thenThrow(outcome.equals("TIMEOUT")
          ? new vn.edufit.ai.api.AiTimeoutException("Deadline") : new vn.edufit.ai.api.AiUnavailableException("Offline"));
    }
    mvc(new MatchExplanationService(gateway))
        .perform(post("/api/v1/discovery/match").contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalId\":\"" + goalId + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].matchScore").value(100))
        .andExpect(jsonPath("$.data[0].aiGenerated").value(outcome.equals("SUCCESS")))
        .andExpect(jsonPath("$.data[0].explanation").isNotEmpty());
    verify(logRepository).save(any());
    verify(gateway).complete(any());
  }

  @Test
  void searchPreservesFiltersPaginationAndPublicCard() throws Exception {
    var pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.ASC, "pricePerSession"));
    var criteria = new TutorSearchCriteria(
        1, 2, "Hanoi", "ONLINE", 100_000L, 300_000L, new BigDecimal("4.5"),
        "Math", (short) 1, LocalTime.of(18, 0), LocalTime.of(20, 0)
    );
    when(profileFacade.searchTutors(criteria, pageable))
        .thenReturn(new PageImpl<>(List.of(profile.tutor()), pageable, 5));
    when(profileFacade.findVerifiedTutorDetails(List.of(profile.tutor().tutorId())))
        .thenReturn(List.of(profile));

    mvc.perform(get("/api/v1/discovery/tutors")
            .param("subjectId", "1").param("levelId", "2").param("area", "Hanoi")
            .param("mode", "ONLINE").param("minPrice", "100000").param("maxPrice", "300000")
            .param("minRating", "4.5").param("keyword", "Math").param("dayOfWeek", "1")
            .param("availableFrom", "18:00").param("availableTo", "20:00")
            .param("page", "1").param("size", "2").param("sortBy", "price").param("sortDirection", "asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.totalElements").value(5))
        .andExpect(jsonPath("$.data.number").value(1))
        .andExpect(jsonPath("$.data.content[0].tutorId").value(profile.tutor().tutorId().toString()))
        .andExpect(jsonPath("$.data.content[0].displayName").value("Tutor A"))
        .andExpect(jsonPath("$.data.content[0].shortBio").value("x".repeat(177) + "..."))
        .andExpect(jsonPath("$.data.content[0].verified").value(true))
        .andExpect(jsonPath("$.data.content[0].subjects[0].subjectId").value(1))
        .andExpect(jsonPath("$.data.content[0].userId").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].bio").doesNotExist());
    verify(profileFacade).searchTutors(criteria, pageable);
  }

  @Test
  void detailPreservesPublicFieldsAndAssociations() throws Exception {
    UUID tutorId = profile.tutor().tutorId();
    when(profileFacade.findVerifiedTutorDetail(tutorId)).thenReturn(Optional.of(profile));

    mvc.perform(get("/api/v1/discovery/tutors/{tutorId}", tutorId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.tutorId").value(tutorId.toString()))
        .andExpect(jsonPath("$.data.bio").value("x".repeat(200)))
        .andExpect(jsonPath("$.data.teachingMethod").value("Practice"))
        .andExpect(jsonPath("$.data.subjects[0].educationLevelId").value(2))
        .andExpect(jsonPath("$.data.availabilitySlots[0].dayOfWeek").value(1))
        .andExpect(jsonPath("$.data.verified").value(true))
        .andExpect(jsonPath("$.data.userId").doesNotExist());
  }

  @Test
  void matchPreservesDefaultTopNScoreBreakdownAndExplanation() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID goalId = UUID.randomUUID();
    when(currentUserProvider.getIfAvailable()).thenReturn(currentUser);
    when(currentUser.getUserId()).thenReturn(userId);
    when(currentUser.hasRole("STUDENT")).thenReturn(true);
    when(profileFacade.findLearningGoalForDiscovery(goalId)).thenReturn(Optional.of(
        new LearningGoalDiscoveryDto(goalId, UUID.randomUUID(), userId, 1, 2, "ONLINE", "Hanoi",
            100_000L, 300_000L, "ACTIVE", profile.availabilitySlots())
    ));
    List<TutorDiscoveryProfileDto> candidates = java.util.stream.IntStream.range(0, 6)
        .mapToObj(index -> new TutorDiscoveryProfileDto(
            new TutorSummaryDto(UUID.randomUUID(), UUID.randomUUID(), "Tutor " + index,
                "Math", "Bio", "ONLINE", "Hanoi", 200_000L, (short) 5,
                "Practice", "VERIFIED", Instant.parse("2025-01-01T00:00:00Z"), BigDecimal.valueOf(5), 10),
            profile.subjects(), profile.availabilitySlots(), profile.registeredAt()
        )).toList();
    when(profileFacade.findVerifiedCandidatesBySubject(any())).thenReturn(candidates);
    when(explanationService.explainMatches(eq(userId), any(), any(), any()))
        .thenReturn(java.util.Collections.nCopies(5,
            new MatchExplanationService.Explanation("Grounded explanation", true)));

    mvc.perform(post("/api/v1/discovery/match").contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalId\":\"" + goalId + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.length()").value(5))
        .andExpect(jsonPath("$.data[0].matchScore").value(100))
        .andExpect(jsonPath("$.data[0].scoreBreakdown.scheduleFit").value(100))
        .andExpect(jsonPath("$.data[0].scoreBreakdown.ratingFit").value(100))
        .andExpect(jsonPath("$.data[0].scoreBreakdown.budgetFit").value(100))
        .andExpect(jsonPath("$.data[0].scoreBreakdown.subjectFit").value(100))
        .andExpect(jsonPath("$.data[0].scoreBreakdown.levelFit").value(100))
        .andExpect(jsonPath("$.data[0].scoreBreakdown.overlappingSlots").value(1))
        .andExpect(jsonPath("$.data[0].explanation").value("Grounded explanation"))
        .andExpect(jsonPath("$.data[0].aiGenerated").value(true))
        .andExpect(jsonPath("$.data[0].tutorInfo.verified").value(true))
        .andExpect(jsonPath("$.data[0].tutorInfo.userId").doesNotExist())
        .andExpect(jsonPath("$.data[1].aiGenerated").value(true));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 11})
  void matchRejectsInvalidTopNBeforeCallingApplication(int topN) throws Exception {
    mvc.perform(post("/api/v1/discovery/match").contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalId\":\"" + UUID.randomUUID() + "\",\"topN\":" + topN + "}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(profileFacade, logRepository, explanationService, currentUserProvider);
  }

  @Test
  void searchRejectsInvalidPriceRange() throws Exception {
    mvc.perform(get("/api/v1/discovery/tutors").param("minPrice", "300000").param("maxPrice", "100000"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(profileFacade);
  }
}
