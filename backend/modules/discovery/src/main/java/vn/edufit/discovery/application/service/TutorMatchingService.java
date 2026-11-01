package vn.edufit.discovery.application.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edufit.discovery.api.event.MatchingExecutedEvent;
import vn.edufit.discovery.application.dto.TutorMatchResult;
import vn.edufit.discovery.domain.model.MatchScore;
import vn.edufit.discovery.domain.policy.MatchScorer;
import vn.edufit.discovery.domain.policy.MatchWeights;
import vn.edufit.discovery.infra.persistence.entity.MatchingRunLogEntity;
import vn.edufit.discovery.infra.persistence.repository.MatchingRunLogRepository;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * AI Match: lọc mềm toàn bộ. Ứng viên chỉ cần đã xác minh và có gói dạy đúng môn;
 * gia sư khớp nhiều tiêu chí hơn đứng trước, mỗi kết quả kèm nhãn ✔/⚠ từng tiêu chí.
 */
@Service
public class TutorMatchingService {

  private final ProfileFacade profileFacade;
  private final MatchingRunLogRepository matchingRunLogRepository;
  private final MatchExplanationService explanationService;
  private final NeedAccessService needAccess;
  private final ApplicationEventPublisher eventPublisher;
  private final MatchWeights weights;
  private final TransactionOperations transactionOperations;

  public TutorMatchingService(
      ProfileFacade profileFacade,
      MatchingRunLogRepository matchingRunLogRepository,
      MatchExplanationService explanationService,
      NeedAccessService needAccess,
      ApplicationEventPublisher eventPublisher,
      MatchWeights weights,
      PlatformTransactionManager transactionManager
  ) {
    this.profileFacade = profileFacade;
    this.matchingRunLogRepository = matchingRunLogRepository;
    this.explanationService = explanationService;
    this.needAccess = needAccess;
    this.eventPublisher = eventPublisher;
    this.weights = weights;
    // Lưu log và phát MatchingExecutedEvent phải cùng một transaction; không có chế độ chạy không transaction
    this.transactionOperations = new TransactionTemplate(java.util.Objects.requireNonNull(transactionManager));
  }

  public List<TutorMatchResult> match(CurrentUser currentUser, UUID needId, int topN) {
    if (currentUser == null || currentUser.getUserId() == null
        || (!currentUser.hasRole("STUDENT") && !currentUser.hasRole("PARENT"))) {
      throw new ForbiddenOperationException("Chỉ học sinh hoặc phụ huynh được yêu cầu ghép đôi gia sư.");
    }
    if (needId == null || topN < 1 || topN > 10) {
      throw new InvalidOperationException("needId là bắt buộc và topN phải từ 1 đến 10.");
    }
    LearningNeedView need = needAccess.authorizedNeed(currentUser, needId);
    if (!need.readyForMatching()) {
      throw new InvalidOperationException(
          "Nhu cầu học cần bổ sung thông tin trước khi ghép đôi: " + String.join(", ", need.missingFields()));
    }
    var scorer = new MatchScorer(needAccess.educationLevelOrder(), weights);

    List<TutorDiscoveryProfileDto> profiles = profileFacade.findVerifiedCandidatesBySubject(need.subjectId());
    Map<UUID, TutorDiscoveryProfileDto> byTutorId = profiles.stream()
        .collect(Collectors.toMap(profile -> profile.tutor().tutorId(), Function.identity()));
    List<MatchScore> ranked = profiles.stream()
        .map(profile -> scorer.score(need, profile))
        .flatMap(Optional::stream)
        .sorted(MatchScore.rankingOrder())
        .limit(topN)
        .toList();

    var rankedProfiles = ranked.stream().map(score -> byTutorId.get(score.tutorId())).toList();
    List<MatchExplanationService.Explanation> explanations = ranked.isEmpty() ? List.of()
        : explanationService.explainMatches(currentUser.getUserId(), need, ranked, rankedProfiles);
    List<TutorMatchResult> results = IntStream.range(0, ranked.size())
        .mapToObj(i -> new TutorMatchResult(ranked.get(i), rankedProfiles.get(i),
            explanations.get(i).text(), explanations.get(i).aiGenerated()))
        .toList();
    var log = new MatchingRunLogEntity(currentUser.getUserId(), need.studentId(), need.needId(), results.size());
    transactionOperations.executeWithoutResult(status -> {
      matchingRunLogRepository.save(log);
      eventPublisher.publishEvent(new MatchingExecutedEvent(
          log.getUserId(), log.getStudentId(), log.getNeedId(), log.getResultCount(), log.getCreatedAt()));
    });
    return results;
  }
}
