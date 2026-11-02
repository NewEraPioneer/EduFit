package vn.edufit.discovery.application.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.edufit.discovery.api.DiscoveryFacade;
import vn.edufit.discovery.api.dto.TutorMatchSummaryDto;
import vn.edufit.shared.auth.CurrentUser;

@Service
public class DiscoveryFacadeImpl implements DiscoveryFacade {

  private final TutorMatchingService matchingService;

  public DiscoveryFacadeImpl(TutorMatchingService matchingService) {
    this.matchingService = matchingService;
  }

  @Override
  public List<TutorMatchSummaryDto> findTopMatchesForNeed(CurrentUser actor, UUID needId, int topN) {
    return matchingService.match(actor, needId, topN).stream()
        .map(result -> new TutorMatchSummaryDto(result.score().tutorId(),
            result.profile().tutor().displayName(), result.score().total(),
            result.explanation(), result.aiGenerated()))
        .toList();
  }
}
