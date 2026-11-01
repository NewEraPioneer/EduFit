package vn.edufit.profile.application.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.profile.api.event.ProfileUpdatedEvent;
import vn.edufit.profile.infra.persistence.entity.GoalTag;
import vn.edufit.profile.infra.persistence.entity.TeachingMode;
import vn.edufit.profile.infra.persistence.entity.TutorAvailabilitySlot;
import vn.edufit.profile.infra.persistence.entity.TutorOffering;
import vn.edufit.profile.infra.persistence.entity.TutorProfile;
import vn.edufit.profile.infra.persistence.repository.TutorAvailabilitySlotRepository;
import vn.edufit.profile.infra.persistence.repository.TutorOfferingRepository;
import vn.edufit.profile.infra.persistence.repository.TutorProfileRepository;
import vn.edufit.profile.web.request.SaveTutorOfferingRequest;
import vn.edufit.profile.web.request.SetAvailabilitySlotsRequest;
import vn.edufit.profile.web.request.UpdateTutorProfileRequest;
import vn.edufit.profile.web.response.TutorProfileResponse;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.FieldValidationException;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;

@Service
public class TutorProfileService {

  private final TutorProfileRepository tutorProfileRepository;
  private final TutorOfferingRepository tutorOfferingRepository;
  private final TutorAvailabilitySlotRepository tutorAvailabilitySlotRepository;
  private final LearningCatalogLoader catalogLoader;
  private final TutorProfileAssembler assembler;
  private final ApplicationEventPublisher eventPublisher;

  public TutorProfileService(
      TutorProfileRepository tutorProfileRepository,
      TutorOfferingRepository tutorOfferingRepository,
      TutorAvailabilitySlotRepository tutorAvailabilitySlotRepository,
      LearningCatalogLoader catalogLoader,
      TutorProfileAssembler assembler,
      ApplicationEventPublisher eventPublisher
  ) {
    this.tutorProfileRepository = tutorProfileRepository;
    this.tutorOfferingRepository = tutorOfferingRepository;
    this.tutorAvailabilitySlotRepository = tutorAvailabilitySlotRepository;
    this.catalogLoader = catalogLoader;
    this.assembler = assembler;
    this.eventPublisher = eventPublisher;
  }

  @Transactional(readOnly = true)
  public TutorProfileResponse getProfileResponse(UUID userId) {
    TutorProfile profile = tutorProfileRepository.findByUserId(userId)
        .orElseThrow(() -> new EntityNotFoundException("Chưa có hồ sơ gia sư cho tài khoản này"));
    return toResponse(profile);
  }

  @Transactional(readOnly = true)
  public java.util.Optional<TutorProfileResponse> findProfileResponseByUserId(UUID userId) {
    return tutorProfileRepository.findByUserId(userId).map(this::toResponse);
  }

  @Transactional(readOnly = true)
  public TutorProfileResponse getProfileResponseById(UUID tutorId) {
    TutorProfile profile = tutorProfileRepository.findById(tutorId)
        .orElseThrow(() -> EntityNotFoundException.of("TutorProfile", tutorId));
    return toResponse(profile);
  }

  @Transactional
  public TutorProfileResponse updateProfile(CurrentUser currentUser, UpdateTutorProfileRequest request) {
    ensureAuthenticated(currentUser);
    UUID userId = currentUser.getUserId();

    // BR-07: dạy trực tiếp (OFFLINE/BOTH) bắt buộc có Tỉnh/Thành; Khu vực phải thuộc tỉnh đó
    boolean inPerson = request.teachingMode() != TeachingMode.ONLINE;
    Map<String, String> errors = new LinkedHashMap<>();
    if (inPerson) {
      catalogLoader.load().validateLocation(request.provinceId(), request.districtIdsOrEmpty(),
          "provinceId", "districtIds", errors);
    }
    if (!errors.isEmpty()) {
      throw new FieldValidationException(errors);
    }

    TutorProfile profile = tutorProfileRepository.findByUserId(userId)
        .orElseGet(() -> new TutorProfile(userId, request.displayName().trim(), request.teachingMode()));

    profile.setDisplayName(request.displayName().trim());
    profile.setHeadline(request.headline() != null ? request.headline().trim() : null);
    profile.setBio(request.bio() != null ? request.bio().trim() : null);
    profile.setTeachingMode(request.teachingMode());
    profile.declareTutorType(request.tutorType());
    profile.setTeachingArea(inPerson ? request.provinceId() : null,
        inPerson ? new LinkedHashSet<>(request.districtIdsOrEmpty()) : Set.of());
    profile.setExperienceYears(request.experienceYears());
    profile.setTeachingMethod(request.teachingMethod() != null ? request.teachingMethod().trim() : null);

    TutorProfile saved = tutorProfileRepository.save(profile);
    eventPublisher.publishEvent(ProfileUpdatedEvent.of(userId, "TUTOR", saved.getTutorId()));

    return toResponse(saved);
  }

  @Transactional
  public TutorProfileResponse createOffering(CurrentUser currentUser, SaveTutorOfferingRequest request) {
    ensureAuthenticated(currentUser);
    TutorProfile profile = getTutorByUserId(currentUser.getUserId());
    boolean duplicate = request.trackId() == null
        ? tutorOfferingRepository.existsByTutorIdAndSubjectIdAndTrackIdIsNull(profile.getTutorId(), request.subjectId())
        : tutorOfferingRepository.existsByTutorIdAndSubjectIdAndTrackId(
            profile.getTutorId(), request.subjectId(), request.trackId());
    if (duplicate) {
      throw FieldValidationException.of(request.trackId() == null ? "subjectId" : "trackId",
          "Bạn đã có gói dạy cho môn và nhánh này. Hãy chỉnh sửa gói hiện có.");
    }
    TutorOffering offering = new TutorOffering(profile.getTutorId(), request.subjectId(), request.trackId());
    define(offering, request);
    tutorOfferingRepository.save(offering);
    eventPublisher.publishEvent(ProfileUpdatedEvent.of(currentUser.getUserId(), "TUTOR", profile.getTutorId()));
    return toResponse(profile);
  }

  @Transactional
  public TutorProfileResponse updateOffering(CurrentUser currentUser, UUID offeringId, SaveTutorOfferingRequest request) {
    ensureAuthenticated(currentUser);
    TutorProfile profile = getTutorByUserId(currentUser.getUserId());
    TutorOffering offering = tutorOfferingRepository.findByOfferingIdAndTutorId(offeringId, profile.getTutorId())
        .orElseThrow(() -> EntityNotFoundException.of("TutorOffering", offeringId));
    if (!offering.getSubjectId().equals(request.subjectId()) || !Objects.equals(offering.getTrackId(), request.trackId())) {
      throw FieldValidationException.of("subjectId",
          "Không đổi được môn hoặc nhánh của gói dạy. Hãy tạo gói dạy mới.");
    }
    define(offering, request);
    tutorOfferingRepository.save(offering);
    eventPublisher.publishEvent(ProfileUpdatedEvent.of(currentUser.getUserId(), "TUTOR", profile.getTutorId()));
    return toResponse(profile);
  }

  @Transactional
  public TutorProfileResponse deleteOffering(CurrentUser currentUser, UUID offeringId) {
    ensureAuthenticated(currentUser);
    TutorProfile profile = getTutorByUserId(currentUser.getUserId());
    TutorOffering offering = tutorOfferingRepository.findByOfferingIdAndTutorId(offeringId, profile.getTutorId())
        .orElseThrow(() -> EntityNotFoundException.of("TutorOffering", offeringId));
    tutorOfferingRepository.delete(offering);
    eventPublisher.publishEvent(ProfileUpdatedEvent.of(currentUser.getUserId(), "TUTOR", profile.getTutorId()));
    return toResponse(profile);
  }

  @Transactional
  public TutorProfileResponse setAvailabilitySlots(CurrentUser currentUser, SetAvailabilitySlotsRequest request) {
    ensureAuthenticated(currentUser);
    TutorProfile profile = getTutorByUserId(currentUser.getUserId());

    List<SetAvailabilitySlotsRequest.SlotItem> rawSlots = request.slots() != null ? request.slots() : List.of();

    // Kiểm tra tính hợp lệ của từng khung giờ (start_time < end_time)
    for (SetAvailabilitySlotsRequest.SlotItem slot : rawSlots) {
      if (!slot.endTime().isAfter(slot.startTime())) {
        throw new InvalidOperationException(
            ErrorCode.VALIDATION_FAILED,
            "Giờ kết thúc phải sau giờ bắt đầu trong mỗi khung giờ"
        );
      }
    }

    // Kiểm tra chống trùng lấn các khung giờ trong cùng ngày (NFR-17 / Overlap check)
    Map<Short, List<SetAvailabilitySlotsRequest.SlotItem>> slotsByDay = rawSlots.stream()
        .collect(Collectors.groupingBy(SetAvailabilitySlotsRequest.SlotItem::dayOfWeek));

    for (Map.Entry<Short, List<SetAvailabilitySlotsRequest.SlotItem>> entry : slotsByDay.entrySet()) {
      List<SetAvailabilitySlotsRequest.SlotItem> daySlots = new ArrayList<>(entry.getValue());
      daySlots.sort(Comparator.comparing(SetAvailabilitySlotsRequest.SlotItem::startTime));

      for (int i = 0; i < daySlots.size() - 1; i++) {
        SetAvailabilitySlotsRequest.SlotItem cur = daySlots.get(i);
        SetAvailabilitySlotsRequest.SlotItem next = daySlots.get(i + 1);
        if (cur.endTime().isAfter(next.startTime())) {
          throw new InvalidOperationException(
              ErrorCode.SCHEDULE_OVERLAP,
              "Các khung giờ rảnh trong cùng ngày (Thứ " + entry.getKey() + ") không được trùng nhau"
          );
        }
      }
    }

    tutorAvailabilitySlotRepository.deleteByTutorId(profile.getTutorId());

    List<TutorAvailabilitySlot> newSlots = rawSlots.stream()
        .map(s -> new TutorAvailabilitySlot(profile.getTutorId(), s.dayOfWeek(), s.startTime(), s.endTime()))
        .toList();
    tutorAvailabilitySlotRepository.saveAll(newSlots);

    eventPublisher.publishEvent(ProfileUpdatedEvent.of(currentUser.getUserId(), "TUTOR", profile.getTutorId()));
    return toResponse(profile);
  }

  public TutorProfileResponse toResponse(TutorProfile profile) {
    LearningCatalog catalog = catalogLoader.load();
    var summary = assembler.summary(profile, catalog);

    List<TutorProfileResponse.TutorSlotResponse> slots = tutorAvailabilitySlotRepository
        .findByTutorIdOrderByDayOfWeekAscStartTimeAsc(profile.getTutorId())
        .stream()
        .map(slot -> new TutorProfileResponse.TutorSlotResponse(
            slot.getSlotId(),
            slot.getDayOfWeek(),
            slot.getStartTime(),
            slot.getEndTime()
        ))
        .toList();

    return new TutorProfileResponse(
        profile.getTutorId(),
        profile.getUserId(),
        profile.getDisplayName(),
        profile.getHeadline(),
        profile.getBio(),
        summary.teachingMode(),
        summary.tutorType(),
        summary.tutorTypeVerified(),
        summary.provinceId(),
        summary.provinceName(),
        assembler.districts(profile.getDistrictIds(), catalog),
        profile.getExperienceYears(),
        profile.getTeachingMethod(),
        summary.status(),
        profile.getVerifiedAt(),
        profile.getRatingAvg(),
        profile.getReviewCount(),
        assembler.offeringsOf(profile.getTutorId()),
        slots,
        profile.getCreatedAt(),
        profile.getUpdatedAt()
    );
  }

  /**
   * Kiểm tra gói dạy theo luật danh mục. Bộ sách chỉ giữ khi có mục tiêu cần bộ sách,
   * tỉnh luyện đề chỉ giữ khi có mục tiêu Luyện thi vào 10; lỗi trả theo từng trường.
   */
  private void define(TutorOffering offering, SaveTutorOfferingRequest request) {
    LearningCatalog catalog = catalogLoader.load();
    Map<String, String> errors = new LinkedHashMap<>();
    Set<Integer> levelIds = new LinkedHashSet<>(request.levelIds());
    Set<Integer> goalTagIds = new LinkedHashSet<>(request.goalTagIds());
    catalog.validateOfferingScope(request.subjectId(), request.trackId(), levelIds, goalTagIds, errors);

    List<GoalTag> goals = goalTagIds.stream().map(id -> catalog.goalTag(id).orElse(null)).filter(Objects::nonNull).toList();
    boolean needsTextbook = goals.stream().anyMatch(GoalTag::requiresTextbook);
    boolean needsExamProvince = goals.stream().anyMatch(GoalTag::requiresExamProvince);

    Set<Integer> textbooks = needsTextbook ? new LinkedHashSet<>(request.textbooksOrEmpty()) : Set.of();
    if (needsTextbook && textbooks.isEmpty()) {
      errors.put("textbookSeriesIds", "Vui lòng chọn bộ sách bạn dạy được cho mục tiêu bám sát SGK/nâng điểm.");
    } else if (textbooks.stream().anyMatch(id -> !catalog.textbookAllowed(id, request.subjectId()))) {
      errors.put("textbookSeriesIds", "Có bộ sách không thuộc môn đã chọn.");
    }

    Set<Integer> examProvinces = needsExamProvince ? new LinkedHashSet<>(request.examProvincesOrEmpty()) : Set.of();
    if (needsExamProvince && examProvinces.isEmpty()) {
      errors.put("examProvinceIds", "Vui lòng chọn tỉnh/thành bạn luyện đề thi vào 10.");
    } else if (examProvinces.stream().anyMatch(id -> !catalog.activeProvince(id))) {
      errors.put("examProvinceIds", "Tỉnh/thành luyện đề không hợp lệ.");
    }

    if (!errors.isEmpty()) {
      throw new FieldValidationException(errors);
    }
    offering.define(levelIds, goalTagIds, textbooks, examProvinces, request.pricePerSession());
  }

  private TutorProfile getTutorByUserId(UUID userId) {
    return tutorProfileRepository.findByUserId(userId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy hồ sơ gia sư"));
  }

  private void ensureAuthenticated(CurrentUser currentUser) {
    if (currentUser == null || currentUser.getUserId() == null) {
      throw new ForbiddenOperationException("Yêu cầu cần được xác thực danh tính người dùng.");
    }
  }
}
