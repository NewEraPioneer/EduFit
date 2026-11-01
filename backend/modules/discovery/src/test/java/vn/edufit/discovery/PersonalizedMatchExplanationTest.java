package vn.edufit.discovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.edufit.ai.api.*;
import vn.edufit.discovery.application.service.MatchExplanationService;
import vn.edufit.discovery.domain.model.MatchScore;
import vn.edufit.profile.api.dto.*;

class PersonalizedMatchExplanationTest {
  private final AiGateway gateway = mock(AiGateway.class);
  private final MatchExplanationService service = new MatchExplanationService(gateway);

  @Test
  void batchExplainsEachTutorWithOneQuotaReservationAndLearnerContext() {
    var first = score();
    var second = score();
    when(gateway.complete(any())).thenReturn(new AiResponse(
        "1|Bạn nên cân nhắc người này vì lịch học đáp ứng đủ nhu cầu.\n"
        + "2|Lựa chọn này có học phí thấp hơn nhưng ít phản hồi hơn.", 50, 40, true));
    var results = service.explainMatches(UUID.randomUUID(), goal(), List.of(first, second),
        List.of(tutor(first.tutorId(), 200000), tutor(second.tutorId(), 150000)));
    assertEquals(2, results.size());
    assertTrue(results.stream().allMatch(MatchExplanationService.Explanation::aiGenerated));
    assertNotEquals(results.getFirst().text(), results.getLast().text());
    var request = ArgumentCaptor.forClass(AiRequest.class);
    verify(gateway).complete(request.capture());
    assertTrue(request.getValue().userPrompt().contains("300000"));
    assertTrue(request.getValue().userPrompt().contains("200000"));
    assertTrue(request.getValue().userPrompt().contains("150000"));
    assertTrue(request.getValue().userPrompt().contains("EXAM_PREPARATION"));
    assertTrue(request.getValue().userPrompt().contains("Toán"));
    assertTrue(request.getValue().userPrompt().contains("2030-06-01"));
    assertFalse(request.getValue().userPrompt().contains("private@example.test"));
  }

  @Test
  void validEntryIsKeptWhileMissingEntryFallsBackIndependently() {
    var first = score();
    var second = score();
    when(gateway.complete(any())).thenReturn(new AiResponse("1|Lịch học này phù hợp với lịch bạn đã chọn.", 1, 1, true));
    var results = service.explainMatches(UUID.randomUUID(), goal(), List.of(first, second),
        List.of(tutor(first.tutorId(), 200000), tutor(second.tutorId(), 150000)));
    assertTrue(results.getFirst().aiGenerated());
    assertFalse(results.getLast().aiGenerated());
    assertTrue(results.getLast().text().contains("150000"));
  }

  @Test
  void genericRepeatedModelOutputFallsBackForBothCandidates() {
    var first = score();
    var second = score();
    when(gateway.complete(any())).thenReturn(new AiResponse(
        "1|Lịch học đáp ứng nhu cầu của bạn.\n2|Lịch học đáp ứng nhu cầu của bạn.", 1, 1, true));
    var results = service.explainMatches(UUID.randomUUID(), goal(), List.of(first, second),
        List.of(tutor(first.tutorId(), 200000), tutor(second.tutorId(), 150000)));
    assertTrue(results.stream().noneMatch(MatchExplanationService.Explanation::aiGenerated));
  }

  @Test
  void stubNeverReceivesAiBadgeAndEmptyCandidatesDoNotCallGateway() {
    assertTrue(service.explainMatches(UUID.randomUUID(), goal(), List.of(), List.of()).isEmpty());
    verifyNoInteractions(gateway);
    var score = score();
    when(gateway.complete(any())).thenReturn(new AiResponse("1|Fake model answer from stub", 0, null, false));
    assertFalse(service.explainMatches(UUID.randomUUID(), goal(), List.of(score),
        List.of(tutor(score.tutorId(), 200000))).getFirst().aiGenerated());
  }

  @Test
  void missingDuplicateAndUnknownEntriesNeverAssignAnotherTutorsExplanation() {
    var first = score();
    var second = score();
    when(gateway.complete(any())).thenReturn(new AiResponse(
        "1|Một lý do.\n1|Lý do bị trùng.\n99|Không có gia sư này.", 1, 1, true));
    var results = service.explainMatches(UUID.randomUUID(), goal(), List.of(first, second),
        List.of(tutor(first.tutorId(), 200000), tutor(second.tutorId(), 150000)));
    assertTrue(results.stream().noneMatch(MatchExplanationService.Explanation::aiGenerated));
    assertNotEquals(results.getFirst().text(), results.getLast().text());
    assertFalse(results.getFirst().text().contains("100.00%"));
  }

  @Test
  void providerFailureStillReturnsPersonalizedFactsAndNoFalseAiFlag() {
    var score = score();
    when(gateway.complete(any())).thenThrow(new AiUnavailableException("Unavailable"));
    var result = service.explainMatches(UUID.randomUUID(), goal(), List.of(score),
        List.of(tutor(score.tutorId(), 350000))).getFirst();
    assertFalse(result.aiGenerated());
    assertTrue(result.text().contains("350000"));
    assertTrue(result.text().contains("vượt"));
    assertTrue(result.text().length() <= 180);
  }

  @Test
  void changingOnlyNumbersDoesNotCountAsPersonalizedModelOutput() {
    var first = score();
    var second = score();
    when(gateway.complete(any())).thenReturn(new AiResponse(
        "1|Bạn phù hợp với gia sư có 5 năm kinh nghiệm, học phí 200000 đồng.\n"
        + "2|Bạn phù hợp với gia sư có 8 năm kinh nghiệm, học phí 150000 đồng.", 1, 1, true));
    var result = service.explainMatches(UUID.randomUUID(), goal(), List.of(first, second),
        List.of(tutor(first.tutorId(), 200000), tutor(second.tutorId(), 150000)));
    assertTrue(result.stream().noneMatch(MatchExplanationService.Explanation::aiGenerated));
    assertNotEquals(result.getFirst().text(), result.getLast().text());
  }

  @Test
  void longModelOutputFallsBackInsteadOfFloodingTheCard() {
    var score = score();
    when(gateway.complete(any())).thenReturn(new AiResponse("1|" + "Nội dung dài. ".repeat(30), 1, 1, true));
    var result = service.explainMatches(UUID.randomUUID(), goal(), List.of(score),
        List.of(tutor(score.tutorId(), 200000))).getFirst();
    assertFalse(result.aiGenerated());
    assertTrue(result.text().length() <= 180);
  }

  private LearningGoalDiscoveryDto goal() {
    return new LearningGoalDiscoveryDto(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        501, 502, "ONLINE", "Hanoi", 100000L, 300000L, "ACTIVE", List.of(),
        "EXAM_PREPARATION", (short) 2, (short) 4, java.time.LocalDate.of(2030, 6, 1));
  }

  private MatchScore score() {
    return new MatchScore(UUID.randomUUID(), new BigDecimal("95"), new BigDecimal("100"),
        new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("80"),
        new BigDecimal("100"), 2, 8, Instant.EPOCH);
  }

  private TutorDiscoveryProfileDto tutor(UUID id, long price) {
    return new TutorDiscoveryProfileDto(new TutorSummaryDto(id, UUID.randomUUID(), "Tutor",
        "Math", "private@example.test", "ONLINE", "Hanoi", price, (short) 5, "Practice",
        "VERIFIED", Instant.EPOCH, new BigDecimal("4"), 8),
        List.of(new TutorSubjectDto(501, "Toán", 502, "THCS")), List.of(), Instant.EPOCH);
  }
}
