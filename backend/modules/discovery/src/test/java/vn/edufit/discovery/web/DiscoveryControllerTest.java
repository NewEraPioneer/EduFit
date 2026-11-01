package vn.edufit.discovery.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edufit.discovery.DiscoveryTestData.*;

import java.math.BigDecimal;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import vn.edufit.ai.api.AiGateway;
import vn.edufit.ai.api.AiResponse;
import vn.edufit.ai.api.AiTimeoutException;
import vn.edufit.ai.api.AiUnavailableException;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.discovery.application.service.DiscoveryQueryService;
import vn.edufit.discovery.application.service.MatchExplanationService;
import vn.edufit.discovery.application.service.NeedAccessService;
import vn.edufit.discovery.application.service.TutorMatchingService;
import vn.edufit.discovery.domain.policy.MatchWeights;
import vn.edufit.discovery.infra.persistence.repository.MatchingRunLogRepository;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.EducationLevelOrderDto;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;
import vn.edufit.profile.api.dto.TutorSearchCriteria;
import vn.edufit.shared.auth.CurrentUser;

@ExtendWith(MockitoExtension.class)
class DiscoveryControllerTest {

  @Mock private ProfileFacade profileFacade;
  @Mock private MatchingRunLogRepository logRepository;
  @Mock private ObjectProvider<CurrentUser> currentUserProvider;
  @Mock private CurrentUser currentUser;

  private final UUID userId = UUID.randomUUID();
  private final UUID needId = UUID.randomUUID();
  private TutorDiscoveryProfileDto profile;

  @BeforeEach
  void setUp() {
    profile = tutor(UUID.randomUUID(), "BOTH", HA_NOI, List.of(CAU_GIAY), List.of(slot(2, 18, 22)),
        BigDecimal.valueOf(5), 10, offering().build());
    lenient().when(profileFacade.findEducationLevelsForMatching()).thenReturn(List.of(
        new EducationLevelOrderDto(LEVEL_8, 3), new EducationLevelOrderDto(LEVEL_9, 4)));
  }

  private MockMvc mvc(MatchExplanationService explanations) {
    var access = new NeedAccessService(profileFacade, mock(ConnectionFacade.class));
    return MockMvcBuilders.standaloneSetup(new DiscoveryController(
        new DiscoveryQueryService(profileFacade, access, MatchWeights.standard(), 500),
        new TutorMatchingService(profileFacade, logRepository, explanations, access,
            mock(ApplicationEventPublisher.class), MatchWeights.standard(), mock(PlatformTransactionManager.class)),
        currentUserProvider
    )).build();
  }

  private void loggedInStudent() {
    when(currentUserProvider.getIfAvailable()).thenReturn(currentUser);
    lenient().when(currentUser.getUserId()).thenReturn(userId);
    lenient().when(currentUser.hasRole("STUDENT")).thenReturn(true);
    when(profileFacade.findLearningNeed(needId)).thenReturn(Optional.of(
        need().id(needId).student(UUID.randomUUID(), userId).build()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUCCESS", "TIMEOUT", "UNAVAILABLE"})
  void aiFailuresStillReturnSuccessfulMatchingWithFitLabels(String outcome) throws Exception {
    var gateway = mock(AiGateway.class);
    loggedInStudent();
    when(profileFacade.findVerifiedCandidatesBySubject(MATH)).thenReturn(List.of(profile));
    if (outcome.equals("SUCCESS")) {
      when(gateway.complete(any())).thenReturn(new AiResponse("1|Lịch học và khu vực đều thuận tiện cho gia đình.", 1, 1, true));
    } else {
      when(gateway.complete(any())).thenThrow(outcome.equals("TIMEOUT")
          ? new AiTimeoutException("Deadline") : new AiUnavailableException("Offline"));
    }
    mvc(new MatchExplanationService(gateway))
        .perform(post("/api/v1/discovery/match").contentType(MediaType.APPLICATION_JSON)
            .content("{\"needId\":\"" + needId + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].matchScore").value(100))
        .andExpect(jsonPath("$.data[0].aiGenerated").value(outcome.equals("SUCCESS")))
        .andExpect(jsonPath("$.data[0].tutorInfo.fit.checks[0].matched").value(true))
        .andExpect(jsonPath("$.data[0].tutorInfo.offerings[0].pricePerSession").value(250000));
    verify(logRepository).save(any());
  }

  @Test
  void searchForwardsHardFiltersAndReturnsCardsWithOfferings() throws Exception {
    when(profileFacade.searchVerifiedTutors(any(), anyInt())).thenReturn(List.of(profile));

    mvc(mock(MatchExplanationService.class)).perform(get("/api/v1/discovery/tutors")
            .param("levelId", "4").param("subjectId", "1").param("goalTagId", "1").param("textbookSeriesId", "1")
            .param("tutorType", "GRADUATE").param("mode", "OFFLINE").param("provinceId", "1").param("districtId", "5")
            .param("maxPrice", "300000").param("sortBy", "price").param("sortDirection", "asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].tutorId").value(profile.tutor().tutorId().toString()))
        .andExpect(jsonPath("$.data.content[0].minPricePerSession").value(250000))
        .andExpect(jsonPath("$.data.content[0].districts[0].name").value("Cầu Giấy"))
        .andExpect(jsonPath("$.data.content[0].fit").doesNotExist());

    verify(profileFacade).searchVerifiedTutors(eq(new TutorSearchCriteria(4, 1, null, 1, 1, "GRADUATE", "OFFLINE", 1, 5,
        null, 300000L, null, null, null, null, null)), eq(500));
  }

  @Test
  void searchWithNeedAddsFitAndRejectsAnonymous() throws Exception {
    when(profileFacade.searchVerifiedTutors(any(), anyInt())).thenReturn(List.of(profile));
    // Không có handler lỗi chung ở test standalone: ngoại lệ nghiệp vụ được bọc trong ServletException
    var anonymous = org.junit.jupiter.api.Assertions.assertThrows(jakarta.servlet.ServletException.class, () ->
        mvc(mock(MatchExplanationService.class)).perform(get("/api/v1/discovery/tutors").param("needId", needId.toString())));
    org.junit.jupiter.api.Assertions.assertInstanceOf(
        vn.edufit.shared.exception.ForbiddenOperationException.class, anonymous.getCause());

    loggedInStudent();
    mvc(mock(MatchExplanationService.class)).perform(get("/api/v1/discovery/tutors").param("needId", needId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].fit.matchScore").value(100));
  }

  @Test
  void fitEndpointReturnsLabelsForOneTutor() throws Exception {
    loggedInStudent();
    when(profileFacade.findVerifiedTutorDetail(profile.tutor().tutorId())).thenReturn(Optional.of(profile));
    mvc(mock(MatchExplanationService.class))
        .perform(get("/api/v1/discovery/needs/{needId}/tutors/{tutorId}/fit", needId, profile.tutor().tutorId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.fit.offeringName").value("Toán"))
        .andExpect(jsonPath("$.data.fit.checks.length()").value(6));
  }

  @Test
  void invalidSortParametersAreRejected() throws Exception {
    var invalid = org.junit.jupiter.api.Assertions.assertThrows(jakarta.servlet.ServletException.class, () ->
        mvc(mock(MatchExplanationService.class)).perform(get("/api/v1/discovery/tutors").param("sortBy", "distance")));
    org.junit.jupiter.api.Assertions.assertInstanceOf(
        vn.edufit.shared.exception.InvalidOperationException.class, invalid.getCause());
  }
}
