package vn.edufit.profile.application.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.profile.api.event.ProfileUpdatedEvent;
import vn.edufit.profile.infra.persistence.entity.District;
import vn.edufit.profile.infra.persistence.entity.EducationLevel;
import vn.edufit.profile.infra.persistence.entity.Province;
import vn.edufit.profile.infra.persistence.entity.StudentProfile;
import vn.edufit.profile.infra.persistence.repository.StudentProfileRepository;
import vn.edufit.profile.web.request.UpdateStudentProfileRequest;
import vn.edufit.profile.web.response.StudentProfileResponse;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.FieldValidationException;
import vn.edufit.shared.exception.ForbiddenOperationException;

@Service
public class StudentProfileService {

  private final StudentProfileRepository studentProfileRepository;
  private final LearningCatalogLoader learningCatalogLoader;
  private final ApplicationEventPublisher eventPublisher;

  public StudentProfileService(
      StudentProfileRepository studentProfileRepository,
      LearningCatalogLoader learningCatalogLoader,
      ApplicationEventPublisher eventPublisher
  ) {
    this.studentProfileRepository = studentProfileRepository;
    this.learningCatalogLoader = learningCatalogLoader;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public StudentProfile getOrCreateProfile(UUID userId) {
    return studentProfileRepository.findByUserId(userId)
        .orElseGet(() -> studentProfileRepository.save(new StudentProfile(userId)));
  }

  @Transactional
  public StudentProfileResponse getProfileResponse(UUID userId) {
    StudentProfile profile = getOrCreateProfile(userId);
    return toResponse(profile);
  }

  @Transactional(readOnly = true)
  public java.util.Optional<StudentProfileResponse> findProfileResponseByUserId(UUID userId) {
    return studentProfileRepository.findByUserId(userId).map(this::toResponse);
  }

  @Transactional(readOnly = true)
  public StudentProfileResponse getProfileResponseById(UUID studentId) {
    StudentProfile profile = studentProfileRepository.findById(studentId)
        .orElseThrow(() -> EntityNotFoundException.of("StudentProfile", studentId));
    return toResponse(profile);
  }

  @Transactional
  public StudentProfileResponse updateProfile(CurrentUser currentUser, UpdateStudentProfileRequest request) {
    ensureAuthenticated(currentUser);
    UUID userId = currentUser.getUserId();

    StudentProfile profile = studentProfileRepository.findByUserId(userId)
        .orElseGet(() -> new StudentProfile(userId));

    LearningCatalog catalog = learningCatalogLoader.load();
    Map<String, String> errors = new LinkedHashMap<>();
    if (request.educationLevelId() != null && catalog.level(request.educationLevelId())
        .filter(level -> Boolean.TRUE.equals(level.getIsActive())).isEmpty()) {
      errors.put("educationLevelId", "Khối lớp không hợp lệ.");
    }
    if (request.provinceId() != null || request.districtId() != null) {
      catalog.validateLocation(request.provinceId(),
          request.districtId() == null ? List.of() : List.of(request.districtId()),
          "provinceId", "districtId", errors);
    }
    if (!errors.isEmpty()) {
      throw new FieldValidationException(errors);
    }
    profile.setEducationLevelId(request.educationLevelId());
    profile.setLocation(request.provinceId(), request.districtId());
    profile.setProfileComplete(profile.getEducationLevelId() != null);

    StudentProfile saved = studentProfileRepository.save(profile);
    eventPublisher.publishEvent(ProfileUpdatedEvent.of(userId, "STUDENT", saved.getStudentId()));

    return toResponse(saved);
  }

  public StudentProfileResponse toResponse(StudentProfile profile) {
    LearningCatalog catalog = learningCatalogLoader.load();
    return new StudentProfileResponse(
        profile.getStudentId(),
        profile.getUserId(),
        profile.getEducationLevelId(),
        catalog.level(profile.getEducationLevelId()).map(EducationLevel::getName).orElse(null),
        profile.getProvinceId(),
        catalog.province(profile.getProvinceId()).map(Province::getName).orElse(null),
        profile.getDistrictId(),
        catalog.district(profile.getDistrictId()).map(District::getName).orElse(null),
        profile.isProfileComplete(),
        profile.getCreatedAt(),
        profile.getUpdatedAt()
    );
  }

  private void ensureAuthenticated(CurrentUser currentUser) {
    if (currentUser == null || currentUser.getUserId() == null) {
      throw new ForbiddenOperationException("Yêu cầu cần được xác thực danh tính người dùng.");
    }
  }
}
