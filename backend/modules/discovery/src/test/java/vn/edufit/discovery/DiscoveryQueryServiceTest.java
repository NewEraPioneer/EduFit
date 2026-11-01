package vn.edufit.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vn.edufit.discovery.DiscoveryTestData.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.discovery.application.matching.MatchWeights;
import vn.edufit.discovery.application.service.DiscoveryQueryService;
import vn.edufit.discovery.application.service.DiscoveryQueryService.SortBy;
import vn.edufit.discovery.application.service.NeedAccessService;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.EducationLevelOrderDto;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;
import vn.edufit.profile.api.dto.TutorSearchCriteria;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;

@ExtendWith(MockitoExtension.class)
class DiscoveryQueryServiceTest {

  @Mock private ProfileFacade profileFacade;
  @Mock private ConnectionFacade connectionFacade;

  private DiscoveryQueryService service;
  private final UUID userId = UUID.randomUUID();
  private final UUID needId = UUID.randomUUID();
  private TutorDiscoveryProfileDto cheapButFar;
  private TutorDiscoveryProfileDto bestFit;
  private TutorDiscoveryProfileDto topRated;

  @BeforeEach
  void setUp() {
    service = new DiscoveryQueryService(profileFacade, new NeedAccessService(profileFacade, connectionFacade),
        MatchWeights.standard(), 500);
    lenient().when(profileFacade.findEducationLevelsForMatching()).thenReturn(List.of(
        new EducationLevelOrderDto(LEVEL_8, 3), new EducationLevelOrderDto(LEVEL_9, 4)));
    cheapButFar = tutor(new UUID(0, 1), "ONLINE", null, List.of(), List.of(), BigDecimal.valueOf(3), 2,
        offering().price(150000).levels(LEVEL_8).build());
    bestFit = tutor(new UUID(0, 2), "BOTH", HA_NOI, List.of(CAU_GIAY), List.of(slot(2, 18, 22)),
        BigDecimal.valueOf(4), 5, offering().price(280000).build());
    topRated = tutor(new UUID(0, 3), "ONLINE", null, List.of(), List.of(), BigDecimal.valueOf(5), 99,
        offering().price(400000).levels(LEVEL_8).build());
    lenient().when(profileFacade.searchVerifiedTutors(org.mockito.ArgumentMatchers.any(), anyInt()))
        .thenReturn(List.of(cheapButFar, bestFit, topRated));
  }

  private static CurrentUser student(UUID id) {
    return new CurrentUser() {
      @Override public UUID getUserId() { return id; }
      @Override public String getEmail() { return "s@edufit.vn"; }
      @Override public Set<String> getRoles() { return Set.of("STUDENT"); }
      @Override public boolean hasRole(String role) { return "STUDENT".equals(role); }
    };
  }

  private List<UUID> ids(org.springframework.data.domain.Page<vn.edufit.discovery.application.dto.DiscoveredTutor> page) {
    return page.getContent().stream().map(t -> t.profile().tutor().tutorId()).toList();
  }

  @Test
  @DisplayName("Không kèm nhu cầu: sắp theo đánh giá; giá tăng dần khi sắp theo giá")
  void sortsWithoutNeed() {
    var byRating = service.searchTutors(TutorSearchCriteria.none(), null, null, SortBy.RELEVANCE, false, 0, 10);
    assertEquals(List.of(topRated.tutor().tutorId(), bestFit.tutor().tutorId(), cheapButFar.tutor().tutorId()), ids(byRating));
    assertNull(byRating.getContent().getFirst().fit());

    var byPrice = service.searchTutors(TutorSearchCriteria.none(), null, null, SortBy.PRICE, true, 0, 10);
    assertEquals(cheapButFar.tutor().tutorId(), ids(byPrice).getFirst());
  }

  @Test
  @DisplayName("Kèm nhu cầu: tiêu chí của nhu cầu chỉ để xếp hạng, gia sư khớp nhiều nhất đứng đầu, không loại ai")
  void needOnlyReordersResults() {
    when(profileFacade.findLearningNeed(needId)).thenReturn(Optional.of(need().id(needId).student(UUID.randomUUID(), userId).build()));

    var page = service.searchTutors(TutorSearchCriteria.none(), needId, student(userId), SortBy.RELEVANCE, false, 0, 10);

    assertEquals(3, page.getTotalElements());
    assertEquals(bestFit.tutor().tutorId(), ids(page).getFirst());
    assertNotNull(page.getContent().getFirst().fit());
  }

  @Test
  void pagesInMemoryAfterSorting() {
    var page = service.searchTutors(TutorSearchCriteria.none(), null, null, SortBy.RATING, false, 1, 2);
    assertEquals(3, page.getTotalElements());
    assertEquals(2, page.getTotalPages());
    assertEquals(List.of(cheapButFar.tutor().tutorId()), ids(page));
  }

  @Test
  void needRequiresAuthenticatedOwner() {
    assertThrows(ForbiddenOperationException.class,
        () -> service.searchTutors(TutorSearchCriteria.none(), needId, null, SortBy.RELEVANCE, false, 0, 10));
    when(profileFacade.findLearningNeed(needId)).thenReturn(Optional.of(need().id(needId).build()));
    assertThrows(ForbiddenOperationException.class,
        () -> service.searchTutors(TutorSearchCriteria.none(), needId, student(userId), SortBy.RELEVANCE, false, 0, 10));
  }

  @Test
  void fitForSingleTutorAndHardFiltersAreForwarded() {
    when(profileFacade.findLearningNeed(needId)).thenReturn(Optional.of(need().id(needId).student(UUID.randomUUID(), userId).build()));
    when(profileFacade.findVerifiedTutorDetail(bestFit.tutor().tutorId())).thenReturn(Optional.of(bestFit));
    assertEquals(new BigDecimal("97.00"), service.fit(student(userId), needId, bestFit.tutor().tutorId()).fit().total());

    var criteria = new TutorSearchCriteria(LEVEL_9, MATH, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null);
    service.searchTutors(criteria, null, null, SortBy.RATING, false, 0, 10);
    verify(profileFacade).searchVerifiedTutors(criteria, 500);
  }

  @Test
  void invalidSortIsRejected() {
    assertThrows(InvalidOperationException.class, () -> DiscoveryQueryService.parseSort("distance"));
    assertEquals(SortBy.PRICE, DiscoveryQueryService.parseSort("price"));
  }
}
