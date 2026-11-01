package vn.edufit.profile.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.EducationLevelOrderDto;
import vn.edufit.profile.api.dto.TutorCandidateCriteria;
import vn.edufit.profile.api.dto.LearningGoalDiscoveryDto;
import vn.edufit.profile.api.dto.LearningGoalView;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;
import vn.edufit.profile.api.dto.TutorSearchCriteria;
import vn.edufit.profile.api.dto.TutorSubjectDto;
import vn.edufit.profile.api.dto.ConnectionGoalDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.profile.api.dto.WeeklyAvailabilityDto;
import vn.edufit.profile.infra.persistence.entity.EducationLevel;
import vn.edufit.profile.infra.persistence.entity.LearningGoal;
import vn.edufit.profile.infra.persistence.entity.StudentProfile;
import vn.edufit.profile.infra.persistence.entity.TeachingMode;
import vn.edufit.profile.infra.persistence.entity.TutorProfile;
import vn.edufit.profile.infra.persistence.entity.TutorStatus;
import vn.edufit.profile.infra.persistence.entity.Subject;
import vn.edufit.profile.infra.persistence.repository.EducationLevelRepository;
import vn.edufit.profile.infra.persistence.repository.GoalAvailabilitySlotRepository;
import vn.edufit.profile.infra.persistence.repository.LearningGoalRepository;
import vn.edufit.profile.infra.persistence.repository.StudentProfileRepository;
import vn.edufit.profile.infra.persistence.repository.SubjectRepository;
import vn.edufit.profile.infra.persistence.repository.TutorAvailabilitySlotRepository;
import vn.edufit.profile.infra.persistence.repository.TutorProfileRepository;
import vn.edufit.profile.infra.persistence.repository.TutorSubjectRepository;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

@Service
public class ProfileFacadeImpl implements ProfileFacade {

  private final TutorProfileRepository tutorProfileRepository;
  private final StudentProfileRepository studentProfileRepository;
  private final TutorSubjectRepository tutorSubjectRepository;
  private final TutorAvailabilitySlotRepository tutorAvailabilitySlotRepository;
  private final LearningGoalRepository learningGoalRepository;
  private final GoalAvailabilitySlotRepository goalAvailabilitySlotRepository;
  private final EducationLevelRepository educationLevelRepository;
  private final SubjectRepository subjectRepository;
  private final int candidateLimit;

  public ProfileFacadeImpl(
      TutorProfileRepository tutorProfileRepository,
      StudentProfileRepository studentProfileRepository,
      TutorSubjectRepository tutorSubjectRepository,
      TutorAvailabilitySlotRepository tutorAvailabilitySlotRepository,
      LearningGoalRepository learningGoalRepository,
      GoalAvailabilitySlotRepository goalAvailabilitySlotRepository,
      EducationLevelRepository educationLevelRepository,
      SubjectRepository subjectRepository,
      @Value("${edufit.discovery.matching.candidate-limit:200}") int candidateLimit
  ) {
    this.tutorProfileRepository = tutorProfileRepository;
    this.studentProfileRepository = studentProfileRepository;
    this.tutorSubjectRepository = tutorSubjectRepository;
    this.tutorAvailabilitySlotRepository = tutorAvailabilitySlotRepository;
    this.learningGoalRepository = learningGoalRepository;
    this.goalAvailabilitySlotRepository = goalAvailabilitySlotRepository;
    this.educationLevelRepository = java.util.Objects.requireNonNull(educationLevelRepository);
    this.subjectRepository = java.util.Objects.requireNonNull(subjectRepository);
    if (candidateLimit < 1) {
      throw new IllegalArgumentException("Matching candidate limit must be positive.");
    }
    this.candidateLimit = candidateLimit;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TutorSummaryDto> findTutorByUserId(UUID userId) {
    return tutorProfileRepository.findByUserId(userId).map(this::toTutorDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TutorSummaryDto> findTutorById(UUID tutorId) {
    return tutorProfileRepository.findById(tutorId).map(this::toTutorDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TutorSummaryDto> searchTutors(TutorSearchCriteria criteria, Pageable pageable) {
    TutorSearchCriteria safeCriteria = criteria != null
        ? criteria
        : new TutorSearchCriteria(null, null, null, null, null, null, null);
    TeachingMode mode = parseTeachingMode(safeCriteria.mode());
    String area = normalizeBlank(safeCriteria.area());
    boolean includeBothMode = mode == TeachingMode.ONLINE || mode == TeachingMode.OFFLINE;
    // Offset pages need a total order when many tutors share price/rating/experience.
    org.springframework.data.domain.Sort sort = pageable.getSort();
    if (sort.getOrderFor("tutorId") == null) {
      sort = sort.and(org.springframework.data.domain.Sort.by("tutorId"));
    }
    Pageable stablePageable = pageable.isPaged()
        ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort)
        : Pageable.unpaged(sort);

    return tutorProfileRepository.searchVerifiedTutors(
        TutorStatus.VERIFIED,
        safeCriteria.subjectId(),
        safeCriteria.levelId(),
        area,
        mode,
        includeBothMode,
        TeachingMode.BOTH,
        safeCriteria.minPrice(),
        safeCriteria.maxPrice(),
        safeCriteria.minRating(),
        normalizeBlank(safeCriteria.keyword()),
        safeCriteria.dayOfWeek(),
        safeCriteria.availableFrom(),
        safeCriteria.availableTo(),
        stablePageable
    ).map(this::toTutorDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TutorDiscoveryProfileDto> findVerifiedTutorDetail(UUID tutorId) {
    return tutorProfileRepository.findById(tutorId)
        .filter(tutor -> tutor.getStatus() == TutorStatus.VERIFIED)
        .map(tutor -> toDiscoveryProfiles(List.of(tutor)).getFirst());
  }

  @Override
  @Transactional(readOnly = true)
  public List<TutorDiscoveryProfileDto> findVerifiedTutorDetails(List<UUID> tutorIds) {
    if (tutorIds == null || tutorIds.isEmpty()) {
      return List.of();
    }
    return toDiscoveryProfiles(
        tutorProfileRepository.findByTutorIdInAndStatus(tutorIds, TutorStatus.VERIFIED)
    );
  }

  @Override
  @Transactional(readOnly = true)
  public List<TutorDiscoveryProfileDto> findVerifiedCandidatesBySubject(TutorCandidateCriteria criteria) {
    if (criteria == null || criteria.subjectId() == null || criteria.subjectId() < 1) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Matching requires a valid subject ID.");
    }
    TeachingMode mode = parseTeachingMode(criteria.mode());
    if (mode == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Matching requires a teaching mode.");
    }
    String area = normalizeBlank(criteria.area());
    area = area == null ? null : area.toLowerCase(Locale.ROOT);
    boolean allowOnline = mode == TeachingMode.ONLINE || mode == TeachingMode.BOTH;
    boolean allowOffline = mode == TeachingMode.OFFLINE || mode == TeachingMode.BOTH;
    if (!allowOnline && area == null) {
      return List.of();
    }
    return toDiscoveryProfiles(tutorProfileRepository.findVerifiedCandidatesBySubject(
        TutorStatus.VERIFIED, criteria.subjectId(), allowOnline, allowOffline, area,
        PageRequest.of(0, candidateLimit)
    ));
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
  public Optional<LearningGoalDiscoveryDto> findLearningGoalForDiscovery(UUID goalId) {
    return learningGoalRepository.findById(goalId).flatMap(goal ->
        studentProfileRepository.findById(goal.getStudentId())
            .map(student -> toLearningGoalDto(goal, student))
    );
  }

  @Override
  @Transactional(readOnly = true)
  public List<LearningGoalView> findLearningGoalsForStudent(UUID studentId) {
    StudentProfile student = studentProfileRepository.findById(studentId)
        .orElseThrow(() -> EntityNotFoundException.of("StudentProfile", studentId));
    var goals = learningGoalRepository.findByStudentIdOrderByCreatedAtAscGoalIdAsc(studentId);
    if (goals.isEmpty()) return List.of();
    return LearningGoalViews.fromAll(goals, student, goalAvailabilitySlotRepository
        .findByGoalIdInOrderByDayOfWeekAscStartTimeAsc(goals.stream().map(LearningGoal::getGoalId).toList()));
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
    TutorProfile tutor = tutorProfileRepository.findById(tutorId)
        .orElseThrow(() -> EntityNotFoundException.of("TutorProfile", tutorId));
    tutor.markPendingVerification();
    tutorProfileRepository.save(tutor);
  }

  @Override
  @Transactional
  public void markTutorVerified(UUID tutorId, Instant verifiedAt) {
    TutorProfile tutor = tutorProfileRepository.findById(tutorId)
        .orElseThrow(() -> EntityNotFoundException.of("TutorProfile", tutorId));
    tutor.markVerified(verifiedAt);
    tutorProfileRepository.save(tutor);
  }

  @Override
  @Transactional
  public void markTutorRejected(UUID tutorId) {
    TutorProfile tutor = tutorProfileRepository.findById(tutorId)
        .orElseThrow(() -> EntityNotFoundException.of("TutorProfile", tutorId));
    tutor.markRejected();
    tutorProfileRepository.save(tutor);
  }

  @Override
  @Transactional
  public void markTutorUnverified(UUID tutorId) {
    TutorProfile tutor = tutorProfileRepository.findById(tutorId)
        .orElseThrow(() -> EntityNotFoundException.of("TutorProfile", tutorId));
    tutor.markUnverified();
    tutorProfileRepository.save(tutor);
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
  @Transactional(readOnly = true)
  public Optional<ConnectionGoalDto> findGoalForConnection(UUID goalId) {
    return learningGoalRepository.findById(goalId).flatMap(goal -> studentProfileRepository
        .findById(goal.getStudentId()).map(student -> {
          String subjectName = goal.getSubjectId() != null
              ? subjectRepository.findById(goal.getSubjectId()).map(Subject::getName).orElse(null)
              : null;
          String levelName = student.getEducationLevelId() != null
              ? educationLevelRepository.findById(student.getEducationLevelId()).map(EducationLevel::getName).orElse(null)
              : null;
          String area = (goal.getArea() != null && !goal.getArea().isBlank()) ? goal.getArea() : student.getArea();
          return new ConnectionGoalDto(
              goal.getGoalId(),
              goal.getStudentId(),
              student.getUserId(),
              goal.getSubjectId(),
              student.getEducationLevelId(),
              goal.getGoalType() != null ? goal.getGoalType().name() : null,
              goal.getDeadline(),
              goal.getStatus() != null ? goal.getStatus().name() : null,
              goal.getCurrentLevel() != null ? goal.getCurrentLevel().intValue() : null,
              goal.getDesiredLevel() != null ? goal.getDesiredLevel().intValue() : null,
              goal.getBudgetMin(),
              goal.getBudgetMax(),
              area,
              subjectName,
              levelName
          );
        }));
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, ConnectionGoalDto> findGoalsForConnection(List<UUID> goalIds) {
    if (goalIds == null || goalIds.isEmpty()) {
      return Map.of();
    }
    List<LearningGoal> goals = learningGoalRepository.findAllById(goalIds);
    if (goals.isEmpty()) {
      return Map.of();
    }
    Set<UUID> studentIds = goals.stream().map(LearningGoal::getStudentId).collect(Collectors.toSet());
    Map<UUID, StudentProfile> students = studentProfileRepository.findAllById(studentIds).stream()
        .collect(Collectors.toMap(StudentProfile::getStudentId, s -> s));

    Set<Integer> subjectIds = goals.stream().map(LearningGoal::getSubjectId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
    Map<Integer, String> subjects = !subjectIds.isEmpty()
        ? subjectRepository.findAllById(subjectIds).stream().collect(Collectors.toMap(Subject::getSubjectId, Subject::getName))
        : Map.of();

    Set<Integer> levelIds = students.values().stream().map(StudentProfile::getEducationLevelId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
    Map<Integer, String> levels = (educationLevelRepository != null && !levelIds.isEmpty())
        ? educationLevelRepository.findAllById(levelIds).stream().collect(Collectors.toMap(EducationLevel::getLevelId, EducationLevel::getName))
        : Map.of();

    Map<UUID, ConnectionGoalDto> result = new HashMap<>();
    for (LearningGoal goal : goals) {
      StudentProfile student = students.get(goal.getStudentId());
      if (student != null) {
        String subjectName = subjects.get(goal.getSubjectId());
        String levelName = levels.get(student.getEducationLevelId());
        String area = (goal.getArea() != null && !goal.getArea().isBlank()) ? goal.getArea() : student.getArea();
        result.put(goal.getGoalId(), new ConnectionGoalDto(
            goal.getGoalId(),
            goal.getStudentId(),
            student.getUserId(),
            goal.getSubjectId(),
            student.getEducationLevelId(),
            goal.getGoalType() != null ? goal.getGoalType().name() : null,
            goal.getDeadline(),
            goal.getStatus() != null ? goal.getStatus().name() : null,
            goal.getCurrentLevel() != null ? goal.getCurrentLevel().intValue() : null,
            goal.getDesiredLevel() != null ? goal.getDesiredLevel().intValue() : null,
            goal.getBudgetMin(),
            goal.getBudgetMax(),
            area,
            subjectName,
            levelName
        ));
      }
    }
    return result;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean tutorTeaches(UUID tutorId, Integer subjectId, Integer educationLevelId) {
    return tutorSubjectRepository.existsByTutorIdAndSubjectIdAndEducationLevelId(
        tutorId, subjectId, educationLevelId);
  }

  private TutorSummaryDto toTutorDto(TutorProfile profile) {
    return new TutorSummaryDto(
        profile.getTutorId(),
        profile.getUserId(),
        profile.getDisplayName(),
        profile.getHeadline(),
        profile.getBio(),
        profile.getTeachingMode() != null ? profile.getTeachingMode().name() : null,
        profile.getArea(),
        profile.getPricePerSession(),
        profile.getExperienceYears(),
        profile.getTeachingMethod(),
        profile.getStatus() != null ? profile.getStatus().name() : null,
        profile.getVerifiedAt(),
        profile.getRatingAvg(),
        profile.getReviewCount()
    );
  }

  private StudentSummaryDto toStudentDto(StudentProfile profile) {
    return new StudentSummaryDto(
        profile.getStudentId(),
        profile.getUserId(),
        profile.getEducationLevelId(),
        profile.getArea(),
        profile.isProfileComplete()
    );
  }

  private List<TutorDiscoveryProfileDto> toDiscoveryProfiles(List<TutorProfile> profiles) {
    if (profiles.isEmpty()) {
      return List.of();
    }
    Set<UUID> tutorIds = profiles.stream().map(TutorProfile::getTutorId).collect(Collectors.toSet());
    var tutorSubjects = tutorSubjectRepository.findDiscoverySubjectsByTutorIdIn(tutorIds);
    Map<UUID, List<TutorSubjectDto>> subjectsByTutor = tutorSubjects.stream()
        .collect(Collectors.groupingBy(
            item -> item.getTutorId(),
            Collectors.mapping(item -> new TutorSubjectDto(
                item.getSubjectId(),
                item.getSubjectName(),
                item.getEducationLevelId(),
                item.getEducationLevelName()
            ), Collectors.toList())
        ));
    Map<UUID, List<WeeklyAvailabilityDto>> slotsByTutor = tutorAvailabilitySlotRepository
        .findByTutorIdInOrderByDayOfWeekAscStartTimeAsc(tutorIds).stream()
        .collect(Collectors.groupingBy(
            slot -> slot.getTutorId(),
            Collectors.mapping(
                slot -> new WeeklyAvailabilityDto(slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime()),
                Collectors.toList()
            )
        ));

    return profiles.stream()
        .map(profile -> new TutorDiscoveryProfileDto(
            toTutorDto(profile),
            subjectsByTutor.getOrDefault(profile.getTutorId(), List.of()),
            slotsByTutor.getOrDefault(profile.getTutorId(), List.of()),
            profile.getCreatedAt()
        ))
        .toList();
  }

  private LearningGoalDiscoveryDto toLearningGoalDto(LearningGoal goal, StudentProfile student) {
    List<WeeklyAvailabilityDto> slots = goalAvailabilitySlotRepository
        .findByGoalIdOrderByDayOfWeekAscStartTimeAsc(goal.getGoalId()).stream()
        .map(slot -> new WeeklyAvailabilityDto(slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime()))
        .toList();

    return new LearningGoalDiscoveryDto(
        goal.getGoalId(),
        goal.getStudentId(),
        student.getUserId(),
        goal.getSubjectId(),
        student.getEducationLevelId(),
        goal.getMode().name(),
        goal.getArea(),
        goal.getBudgetMin(),
        goal.getBudgetMax(),
        goal.getStatus().name(),
        slots, goal.getGoalType().name(), goal.getCurrentLevel(), goal.getDesiredLevel(), goal.getDeadline()
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

  private String normalizeBlank(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  @Override
  @Transactional
  public void updateTutorRatingStats(UUID tutorId, double ratingAvg, int reviewCount) {
    if (tutorId == null) {
      return;
    }
    TutorProfile tutor = tutorProfileRepository.findById(tutorId)
        .orElseThrow(() -> EntityNotFoundException.of("TutorProfile", tutorId));
    tutor.setRatingAvg(BigDecimal.valueOf(ratingAvg).setScale(2, RoundingMode.HALF_UP));
    tutor.setReviewCount(Math.max(0, reviewCount));
    tutorProfileRepository.save(tutor);
  }
}
