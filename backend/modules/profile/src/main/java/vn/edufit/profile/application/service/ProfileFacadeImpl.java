package vn.edufit.profile.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.EducationLevelOrderDto;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;
import vn.edufit.profile.api.dto.TutorOfferingDto;
import vn.edufit.profile.api.dto.TutorSearchCriteria;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.profile.infra.persistence.entity.EducationLevel;
import vn.edufit.profile.infra.persistence.entity.LearningNeed;
import vn.edufit.profile.infra.persistence.entity.StudentProfile;
import vn.edufit.profile.infra.persistence.entity.TeachingMode;
import vn.edufit.profile.infra.persistence.entity.TutorOffering;
import vn.edufit.profile.infra.persistence.entity.TutorProfile;
import vn.edufit.profile.infra.persistence.entity.TutorStatus;
import vn.edufit.profile.infra.persistence.entity.TutorType;
import vn.edufit.profile.infra.persistence.repository.EducationLevelRepository;
import vn.edufit.profile.infra.persistence.repository.LearningNeedRepository;
import vn.edufit.profile.infra.persistence.repository.LearningNeedSlotRepository;
import vn.edufit.profile.infra.persistence.repository.StudentProfileRepository;
import vn.edufit.profile.infra.persistence.repository.TutorOfferingRepository;
import vn.edufit.profile.infra.persistence.repository.TutorProfileRepository;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

@Service
public class ProfileFacadeImpl implements ProfileFacade {

  private final TutorProfileRepository tutorProfileRepository;
  private final StudentProfileRepository studentProfileRepository;
  private final TutorOfferingRepository tutorOfferingRepository;
  private final LearningNeedRepository learningNeedRepository;
  private final LearningNeedSlotRepository learningNeedSlotRepository;
  private final EducationLevelRepository educationLevelRepository;
  private final LearningNeedService learningNeedService;
  private final LearningCatalogLoader catalogLoader;
  private final TutorProfileAssembler assembler;
  private final int candidateLimit;

  public ProfileFacadeImpl(
      TutorProfileRepository tutorProfileRepository,
      StudentProfileRepository studentProfileRepository,
      TutorOfferingRepository tutorOfferingRepository,
      LearningNeedRepository learningNeedRepository,
      LearningNeedSlotRepository learningNeedSlotRepository,
      EducationLevelRepository educationLevelRepository,
      LearningNeedService learningNeedService,
      LearningCatalogLoader catalogLoader,
      TutorProfileAssembler assembler,
      @Value("${edufit.discovery.matching.candidate-limit:200}") int candidateLimit
  ) {
    this.tutorProfileRepository = tutorProfileRepository;
    this.studentProfileRepository = studentProfileRepository;
    this.tutorOfferingRepository = tutorOfferingRepository;
    this.learningNeedRepository = learningNeedRepository;
    this.learningNeedSlotRepository = learningNeedSlotRepository;
    this.educationLevelRepository = java.util.Objects.requireNonNull(educationLevelRepository);
    this.learningNeedService = learningNeedService;
    this.catalogLoader = catalogLoader;
    this.assembler = assembler;
    if (candidateLimit < 1) {
      throw new IllegalArgumentException("Matching candidate limit must be positive.");
    }
    this.candidateLimit = candidateLimit;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TutorSummaryDto> findTutorByUserId(UUID userId) {
    return tutorProfileRepository.findByUserId(userId).map(assembler::summary);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TutorSummaryDto> findTutorById(UUID tutorId) {
    return tutorProfileRepository.findById(tutorId).map(assembler::summary);
  }

  @Override
  @Transactional(readOnly = true)
  public List<TutorDiscoveryProfileDto> searchVerifiedTutors(TutorSearchCriteria criteria, int limit) {
    if (limit < 1) {
      throw new IllegalArgumentException("Search limit must be positive.");
    }
    TutorSearchCriteria safe = criteria != null ? criteria : TutorSearchCriteria.none();
    TeachingMode mode = parseTeachingMode(safe.mode());
    TutorType tutorType = parseTutorType(safe.tutorType());
    boolean filterOffering = safe.levelId() != null || safe.subjectId() != null || safe.trackId() != null
        || safe.goalTagId() != null || safe.textbookSeriesId() != null || safe.minPrice() != null
        || safe.maxPrice() != null;
    return assembler.discoveryProfiles(tutorProfileRepository.searchVerifiedTutors(
        TutorStatus.VERIFIED,
        tutorType,
        mode,
        mode == TeachingMode.ONLINE || mode == TeachingMode.OFFLINE,
        TeachingMode.BOTH,
        safe.provinceId(),
        safe.districtId(),
        safe.minRating(),
        normalizeBlank(safe.keyword()),
        filterOffering,
        safe.subjectId(),
        safe.trackId(),
        safe.levelId(),
        safe.textbookSeriesId(),
        safe.minPrice(),
        safe.maxPrice(),
        safe.goalTagId(),
        safe.dayOfWeek(),
        safe.availableFrom(),
        safe.availableTo(),
        PageRequest.of(0, limit)));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TutorDiscoveryProfileDto> findVerifiedTutorDetail(UUID tutorId) {
    return tutorProfileRepository.findById(tutorId)
        .filter(tutor -> tutor.getStatus() == TutorStatus.VERIFIED)
        .map(tutor -> assembler.discoveryProfiles(List.of(tutor)).getFirst());
  }

  @Override
  @Transactional(readOnly = true)
  public List<TutorDiscoveryProfileDto> findVerifiedTutorDetails(List<UUID> tutorIds) {
    if (tutorIds == null || tutorIds.isEmpty()) {
      return List.of();
    }
    return assembler.discoveryProfiles(
        tutorProfileRepository.findByTutorIdInAndStatus(tutorIds, TutorStatus.VERIFIED));
  }

  @Override
  @Transactional(readOnly = true)
  public List<TutorDiscoveryProfileDto> findVerifiedCandidatesBySubject(Integer subjectId) {
    if (subjectId == null || subjectId < 1) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Matching requires a valid subject ID.");
    }
    return assembler.discoveryProfiles(tutorProfileRepository.findVerifiedCandidatesBySubject(
        TutorStatus.VERIFIED, subjectId, PageRequest.of(0, candidateLimit)));
  }

  @Override
  @Transactional(readOnly = true)
  public List<EducationLevelOrderDto> findEducationLevelsForMatching() {
    var levels = educationLevelRepository.findAllByOrderBySortOrderAscLevelIdAsc();
    Set<Integer> sortOrders = new HashSet<>();
    for (EducationLevel level : levels) {
      if (level.getSortOrder() == null || !sortOrders.add(level.getSortOrder())) {
        throw new InvalidOperationException("Education level sort orders must be present and unique for matching.");
      }
    }
    return levels.stream()
        .map(level -> new EducationLevelOrderDto(level.getLevelId(), level.getSortOrder()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<LearningNeedView> findLearningNeed(UUID needId) {
    if (needId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(findLearningNeeds(List.of(needId)).get(needId));
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, LearningNeedView> findLearningNeeds(Collection<UUID> needIds) {
    if (needIds == null || needIds.isEmpty()) {
      return Map.of();
    }
    List<LearningNeed> needs = learningNeedRepository.findAllById(needIds);
    if (needs.isEmpty()) {
      return Map.of();
    }
    Set<UUID> studentIds = needs.stream().map(LearningNeed::getStudentId).collect(Collectors.toSet());
    Map<UUID, UUID> studentUserIds = studentProfileRepository.findAllById(studentIds).stream()
        .collect(Collectors.toMap(StudentProfile::getStudentId, StudentProfile::getUserId));
    return LearningNeedViews.fromAll(
            needs,
            studentUserIds,
            learningNeedSlotRepository.findByNeedIdInOrderByDayOfWeekAscStartTimeAsc(
                needs.stream().map(LearningNeed::getNeedId).toList()),
            catalogLoader.load())
        .stream()
        .collect(Collectors.toMap(LearningNeedView::needId, Function.identity()));
  }

  @Override
  @Transactional(readOnly = true)
  public List<LearningNeedView> findLearningNeedsForStudent(UUID studentId) {
    return learningNeedService.viewsForStudent(studentId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<TutorOfferingDto> findTutorOfferings(UUID tutorId) {
    return assembler.offeringsOf(tutorId);
  }

  @Override
  @Transactional(readOnly = true)
  public UUID getTutorIdByUserId(UUID userId) {
    return tutorProfileRepository.findByUserId(userId)
        .map(TutorProfile::getTutorId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy hồ sơ gia sư cho người dùng này"));
  }

  @Override
  @Transactional
  public void markTutorPendingVerification(UUID tutorId) {
    TutorProfile tutor = tutor(tutorId);
    tutor.markPendingVerification();
    tutorProfileRepository.save(tutor);
  }

  @Override
  @Transactional
  public void markTutorVerified(UUID tutorId, Instant verifiedAt) {
    TutorProfile tutor = tutor(tutorId);
    tutor.markVerified(verifiedAt);
    tutorProfileRepository.save(tutor);
  }

  @Override
  @Transactional
  public void markTutorRejected(UUID tutorId) {
    TutorProfile tutor = tutor(tutorId);
    tutor.markRejected();
    tutorProfileRepository.save(tutor);
  }

  @Override
  @Transactional
  public void markTutorUnverified(UUID tutorId) {
    TutorProfile tutor = tutor(tutorId);
    tutor.markUnverified();
    tutorProfileRepository.save(tutor);
  }

  @Override
  @Transactional
  public void applyVerificationApproval(
      UUID tutorId, String confirmedTutorType, Collection<UUID> evidencedOfferingIds) {
    TutorProfile tutor = tutor(tutorId);
    TutorType type = parseTutorType(confirmedTutorType);
    if (type != null) {
      tutor.confirmTutorType(type);
      tutorProfileRepository.save(tutor);
    }
    if (evidencedOfferingIds != null && !evidencedOfferingIds.isEmpty()) {
      List<TutorOffering> offerings = tutorOfferingRepository.findByOfferingIdInAndTutorId(evidencedOfferingIds, tutorId);
      offerings.forEach(TutorOffering::verifyGoalEvidence);
      tutorOfferingRepository.saveAll(offerings);
    }
  }

  @Override
  @Transactional
  public void revokeOfferingEvidence(UUID tutorId) {
    List<TutorOffering> offerings = tutorOfferingRepository.findByTutorIdOrderByCreatedAtAsc(tutorId);
    offerings.forEach(TutorOffering::revokeGoalEvidence);
    tutorOfferingRepository.saveAll(offerings);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<StudentSummaryDto> findStudentByUserId(UUID userId) {
    return studentProfileRepository.findByUserId(userId).map(this::toStudentDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<StudentSummaryDto> findStudentById(UUID studentId) {
    return studentProfileRepository.findById(studentId).map(this::toStudentDto);
  }

  @Override
  @Transactional(readOnly = true)
  public UUID getStudentIdByUserId(UUID userId) {
    return studentProfileRepository.findByUserId(userId)
        .map(StudentProfile::getStudentId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy hồ sơ học sinh cho người dùng này"));
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsTutorByUserId(UUID userId) {
    return tutorProfileRepository.existsByUserId(userId);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsStudentByUserId(UUID userId) {
    return studentProfileRepository.existsByUserId(userId);
  }

  @Override
  @Transactional
  public void updateTutorRatingStats(UUID tutorId, double ratingAvg, int reviewCount) {
    if (tutorId == null) {
      return;
    }
    TutorProfile tutor = tutor(tutorId);
    tutor.setRatingAvg(BigDecimal.valueOf(ratingAvg).setScale(2, RoundingMode.HALF_UP));
    tutor.setReviewCount(Math.max(0, reviewCount));
    tutorProfileRepository.save(tutor);
  }

  private TutorProfile tutor(UUID tutorId) {
    return tutorProfileRepository.findById(tutorId)
        .orElseThrow(() -> EntityNotFoundException.of("TutorProfile", tutorId));
  }

  private StudentSummaryDto toStudentDto(StudentProfile profile) {
    return new StudentSummaryDto(
        profile.getStudentId(),
        profile.getUserId(),
        profile.getEducationLevelId(),
        profile.getProvinceId(),
        profile.getDistrictId(),
        profile.isProfileComplete()
    );
  }

  private TeachingMode parseTeachingMode(String rawMode) {
    String normalized = normalizeBlank(rawMode);
    if (normalized == null) {
      return null;
    }
    try {
      return TeachingMode.valueOf(normalized.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Hình thức dạy không hợp lệ. Giá trị hợp lệ: ONLINE, OFFLINE, BOTH"
      );
    }
  }

  private TutorType parseTutorType(String rawType) {
    String normalized = normalizeBlank(rawType);
    if (normalized == null) {
      return null;
    }
    try {
      return TutorType.valueOf(normalized.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Loại gia sư không hợp lệ.");
    }
  }

  private String normalizeBlank(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
