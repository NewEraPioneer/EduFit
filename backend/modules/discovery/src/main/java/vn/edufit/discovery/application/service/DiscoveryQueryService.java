package vn.edufit.discovery.application.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.discovery.application.dto.DiscoveredTutor;
import vn.edufit.discovery.domain.model.MatchScore;
import vn.edufit.discovery.domain.policy.MatchScorer;
import vn.edufit.discovery.domain.policy.MatchWeights;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;
import vn.edufit.profile.api.dto.TutorOfferingDto;
import vn.edufit.profile.api.dto.TutorSearchCriteria;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Discovery (tìm thủ công): tiêu chí người dùng tự bấm là lọc cứng; khi kèm nhu cầu học,
 * các tiêu chí của nhu cầu chỉ dùng để sắp xếp (gia sư khớp nhiều hơn đứng trước).
 */
@Service
public class DiscoveryQueryService {

  public enum SortBy { RELEVANCE, RATING, PRICE, EXPERIENCE }

  private final ProfileFacade profileFacade;
  private final NeedAccessService needAccess;
  private final MatchWeights weights;
  private final int searchLimit;

  public DiscoveryQueryService(
      ProfileFacade profileFacade,
      NeedAccessService needAccess,
      MatchWeights weights,
      @Value("${edufit.discovery.matching.search-limit:500}") int searchLimit) {
    this.profileFacade = profileFacade;
    this.needAccess = needAccess;
    this.weights = weights;
    if (searchLimit < 1) {
      throw new IllegalArgumentException("Discovery search limit must be positive.");
    }
    this.searchLimit = searchLimit;
  }

  @Transactional(readOnly = true)
  public Page<DiscoveredTutor> searchTutors(
      TutorSearchCriteria criteria, UUID needId, CurrentUser actor, SortBy sortBy, boolean ascending,
      int page, int size) {
    Optional<LearningNeedView> need = needId == null ? Optional.empty()
        : Optional.of(needAccess.authorizedNeed(actor, needId));
    var scorer = new MatchScorer(needAccess.educationLevelOrder(), weights);
    List<DiscoveredTutor> found = profileFacade.searchVerifiedTutors(criteria, searchLimit).stream()
        .map(profile -> new DiscoveredTutor(profile, need.flatMap(n -> scorer.score(n, profile)).orElse(null)))
        .toList();
    List<DiscoveredTutor> sorted = found.stream()
        .sorted(comparator(sortBy, ascending, criteria, need.isPresent()))
        .toList();
    int from = Math.min(page * size, sorted.size());
    int to = Math.min(from + size, sorted.size());
    return new PageImpl<>(sorted.subList(from, to), PageRequest.of(page, size), sorted.size());
  }

  @Transactional(readOnly = true)
  public TutorDiscoveryProfileDto getTutorDetail(UUID tutorId) {
    return profileFacade.findVerifiedTutorDetail(tutorId)
        .orElseThrow(() -> EntityNotFoundException.of("TutorProfile", tutorId));
  }

  /** Độ khớp của một gia sư với nhu cầu học (cho trang chi tiết và hộp thoại gửi yêu cầu). */
  @Transactional(readOnly = true)
  public DiscoveredTutor fit(CurrentUser actor, UUID needId, UUID tutorId) {
    LearningNeedView need = needAccess.authorizedNeed(actor, needId);
    TutorDiscoveryProfileDto profile = getTutorDetail(tutorId);
    MatchScore score = new MatchScorer(needAccess.educationLevelOrder(), weights).score(need, profile)
        .orElseThrow(() -> new InvalidOperationException("Gia sư không dạy môn " + need.subjectName()));
    return new DiscoveredTutor(profile, score);
  }

  public static SortBy parseSort(String value) {
    try {
      return SortBy.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw new InvalidOperationException("sortBy phải là relevance, rating, price hoặc experience");
    }
  }

  private Comparator<DiscoveredTutor> comparator(
      SortBy sortBy, boolean ascending, TutorSearchCriteria criteria, boolean hasNeed) {
    Comparator<DiscoveredTutor> byRating = Comparator
        .comparing((DiscoveredTutor t) -> Objects.requireNonNullElse(t.profile().tutor().ratingAvg(), BigDecimal.ZERO))
        .thenComparing(t -> Objects.requireNonNullElse(t.profile().tutor().reviewCount(), 0));
    Comparator<DiscoveredTutor> primary = switch (sortBy) {
      case RELEVANCE -> hasNeed
          ? Comparator.comparing(DiscoveredTutor::fit, MatchScore.rankingOrder().reversed())
          : byRating;
      case RATING -> byRating;
      case PRICE -> Comparator.comparingLong(t -> lowestPrice(t.profile(), criteria));
      case EXPERIENCE -> Comparator.comparing(t -> Objects.requireNonNullElse(t.profile().tutor().experienceYears(), (short) 0));
    };
    Comparator<DiscoveredTutor> ordered = ascending ? primary : primary.reversed();
    return ordered.thenComparing(t -> t.profile().tutor().tutorId());
  }

  private static long lowestPrice(TutorDiscoveryProfileDto profile, TutorSearchCriteria criteria) {
    return profile.offerings().stream()
        .filter(o -> criteria == null || criteria.subjectId() == null || criteria.subjectId().equals(o.subjectId()))
        .mapToLong(TutorOfferingDto::pricePerSession)
        .min().orElse(Long.MAX_VALUE);
  }
}
