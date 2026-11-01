package vn.edufit.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vn.edufit.discovery.DiscoveryTestData.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edufit.ai.api.AiFeature;
import vn.edufit.ai.api.AiGateway;
import vn.edufit.ai.api.AiRequest;
import vn.edufit.ai.api.AiResponse;
import vn.edufit.ai.api.AiUnavailableException;
import vn.edufit.discovery.application.service.MatchExplanationService;
import vn.edufit.discovery.domain.model.EducationLevelOrder;
import vn.edufit.discovery.domain.model.MatchScore;
import vn.edufit.discovery.domain.policy.MatchScorer;
import vn.edufit.discovery.domain.policy.MatchWeights;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;

@ExtendWith(MockitoExtension.class)
class MatchExplanationServiceTest {

  @Mock private AiGateway aiGateway;

  private final MatchScorer scorer = new MatchScorer(
      new EducationLevelOrder(List.of(1, 2, LEVEL_8, LEVEL_9)), MatchWeights.standard());
  private final TutorDiscoveryProfileDto perfect = tutor(UUID.randomUUID(), "BOTH", HA_NOI, List.of(CAU_GIAY),
      List.of(slot(2, 18, 22)), BigDecimal.valueOf(5), 10, offering().build());
  private final TutorDiscoveryProfileDto overBudget = tutor(UUID.randomUUID(), "BOTH", HA_NOI, List.of(CAU_GIAY),
      List.of(slot(2, 18, 22)), BigDecimal.valueOf(4), 2, offering().price(450000).build());

  private MatchScore score(TutorDiscoveryProfileDto tutor) {
    return scorer.score(need().build(), tutor).orElseThrow();
  }

  private MatchExplanationService service() {
    return new MatchExplanationService(aiGateway);
  }

  @Test
  void ruleBasedNamesFirstMismatchOrConfirmsFullMatch() {
    assertTrue(service().ruleBased(score(perfect)).startsWith("Khớp cả 6 tiêu chí"));
    String warning = service().ruleBased(score(overBudget));
    assertTrue(warning.contains("cần trao đổi trước"), warning);
    assertTrue(warning.contains("vượt ngân sách"), warning);
  }

  @Test
  void aiOutputIsParsedPerCandidateAndOnlyStructuredFactsCrossBoundary() {
    when(aiGateway.complete(any())).thenReturn(new AiResponse(
        "1|Lịch học và khu vực đều thuận tiện cho con, có thể bắt đầu ngay tuần này.\n"
            + "2|Nhiều kinh nghiệm luyện đề nhưng học phí cao hơn mức gia đình dự tính.", 10, 10, true));

    var result = service().explainMatches(UUID.randomUUID(), need().build(),
        List.of(score(perfect), score(overBudget)), List.of(perfect, overBudget));

    assertTrue(result.stream().allMatch(MatchExplanationService.Explanation::aiGenerated));
    var request = ArgumentCaptor.forClass(AiRequest.class);
    verify(aiGateway).complete(request.capture());
    assertEquals(AiFeature.MATCH_EXPLANATION, request.getValue().feature());
    String facts = request.getValue().userPrompt();
    assertTrue(facts.contains("tiêu chí lệch"), facts);
    assertFalse(facts.contains(perfect.tutor().displayName()), "Tên gia sư không được gửi sang AI");
  }

  @Test
  void duplicateGenericAiOutputFallsBackToRules() {
    when(aiGateway.complete(any())).thenReturn(new AiResponse(
        "1|Gia sư phù hợp với nhu cầu học của bạn, nên cân nhắc.\n2|Gia sư phù hợp với nhu cầu học của bạn, nên cân nhắc.",
        10, 10, true));
    var result = service().explainMatches(UUID.randomUUID(), need().build(),
        List.of(score(perfect), score(overBudget)), List.of(perfect, overBudget));
    assertTrue(result.stream().noneMatch(MatchExplanationService.Explanation::aiGenerated));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void emptyOrFailedAiReturnsRuleBasedText(String text) {
    when(aiGateway.complete(any())).thenReturn(new AiResponse(text, 0, null, true));
    var result = service().explainMatches(UUID.randomUUID(), need().build(), List.of(score(perfect)), List.of(perfect));
    assertFalse(result.getFirst().aiGenerated());
    assertEquals(service().ruleBased(score(perfect)), result.getFirst().text());
  }

  @Test
  void unavailableAiFallsBackWithoutFailingMatching() {
    when(aiGateway.complete(any())).thenThrow(new AiUnavailableException("Quota exhausted"));
    var result = service().explainMatches(UUID.randomUUID(), need().build(), List.of(score(perfect)), List.of(perfect));
    assertFalse(result.getFirst().aiGenerated());
  }
}
