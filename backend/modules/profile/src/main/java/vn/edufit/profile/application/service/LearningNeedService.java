package vn.edufit.profile.application.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.profile.api.dto.WeeklyAvailabilityDto;
import vn.edufit.profile.api.port.ParentStudentLinkPort;
import vn.edufit.profile.infra.persistence.entity.LearningNeed;
import vn.edufit.profile.infra.persistence.entity.LearningNeedSlot;
import vn.edufit.profile.infra.persistence.entity.NeedStatus;
import vn.edufit.profile.infra.persistence.entity.StudentProfile;
import vn.edufit.profile.infra.persistence.entity.TeachingMode;
import vn.edufit.profile.infra.persistence.repository.LearningNeedRepository;
import vn.edufit.profile.infra.persistence.repository.LearningNeedSlotRepository;
import vn.edufit.profile.infra.persistence.repository.StudentProfileRepository;
import vn.edufit.profile.web.request.SaveLearningNeedRequest;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.FieldValidationException;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Quản lý nhu cầu học. Học sinh thao tác nhu cầu của mình; phụ huynh có liên kết đã xác nhận
 * thao tác nhu cầu của con. Mỗi lần lưu ghi đè toàn bộ nhu cầu và khung giờ trong một transaction.
 */
@Service
public class LearningNeedService {

  private final LearningNeedRepository needs;
  private final LearningNeedSlotRepository slots;
  private final StudentProfileRepository students;
  private final LearningCatalogLoader catalogLoader;
  private final ParentStudentLinkPort parentLinks;

  public LearningNeedService(
      LearningNeedRepository needs,
      LearningNeedSlotRepository slots,
      StudentProfileRepository students,
      LearningCatalogLoader catalogLoader,
      ParentStudentLinkPort parentLinks) {
    this.needs = needs;
    this.slots = slots;
    this.students = students;
    this.catalogLoader = catalogLoader;
    this.parentLinks = parentLinks;
  }

  @Transactional(readOnly = true)
  public List<LearningNeedView> list(CurrentUser actor, UUID studentId) {
    if (isStudentWithoutProfile(actor, studentId)) {
      return List.of();
    }
    StudentProfile student = authorizedStudent(actor, studentId);
    return viewsFor(student);
  }

  /** Học sinh vừa đăng ký chưa có hồ sơ thì chưa có nhu cầu nào; không cần tạo hồ sơ chỉ để xem. */
  private boolean isStudentWithoutProfile(CurrentUser actor, UUID requestedStudentId) {
    return actor != null && actor.getUserId() != null && actor.hasRole("STUDENT")
        && requestedStudentId == null
        && students.findByUserId(actor.getUserId()).isEmpty();
  }

  @Transactional
  public LearningNeedView create(CurrentUser actor, SaveLearningNeedRequest input) {
    StudentProfile student = authorizedStudent(actor, input.studentId());
    LearningCatalog catalog = catalogLoader.load();
    LearningCatalog.Classification classification = validate(input, catalog);
    LearningNeed need = new LearningNeed(student.getStudentId(), actor.getUserId(), classification.subjectId());
    apply(need, classification, input);
    LearningNeed saved = needs.saveAndFlush(need);
    replaceSlots(saved, input);
    return refresh(saved, student, catalog);
  }

  @Transactional
  public LearningNeedView update(CurrentUser actor, UUID needId, SaveLearningNeedRequest input) {
    LearningNeed need = needs.findForUpdate(needId)
        .orElseThrow(() -> EntityNotFoundException.of("LearningNeed", needId));
    StudentProfile student = authorizedStudent(actor, need.getStudentId());
    if (input.studentId() != null && !input.studentId().equals(need.getStudentId())) {
      throw new ForbiddenOperationException("Không thể chuyển nhu cầu học sang học sinh khác.");
    }
    if (need.getStatus() == NeedStatus.ARCHIVED) {
      throw new InvalidOperationException("Nhu cầu đã lưu trữ không được chỉnh sửa.");
    }
    if (input.subjectId() != null && !input.subjectId().equals(need.getSubjectId())) {
      throw FieldValidationException.of("subjectId",
          "Không đổi được môn của nhu cầu đã tạo. Hãy tạo nhu cầu mới cho môn khác.");
    }
    LearningCatalog catalog = catalogLoader.load();
    LearningCatalog.Classification classification = validate(input, catalog);
    apply(need, classification, input);
    replaceSlots(need, input);
    return refresh(need, student, catalog);
  }

  @Transactional
  public LearningNeedView archive(CurrentUser actor, UUID needId) {
    LearningNeed need = needs.findForUpdate(needId)
        .orElseThrow(() -> EntityNotFoundException.of("LearningNeed", needId));
    StudentProfile student = authorizedStudent(actor, need.getStudentId());
    if (need.getStatus() != NeedStatus.ARCHIVED) {
      need.markStatus(NeedStatus.ARCHIVED);
    }
    LearningCatalog catalog = catalogLoader.load();
    return LearningNeedViews.from(need, student.getUserId(), slotViews(need.getNeedId()), catalog);
  }

  /** Đọc nội bộ cho các module khác; nơi gọi phải tự kiểm tra quyền trước. */
  @Transactional(readOnly = true)
  public List<LearningNeedView> viewsForStudent(UUID studentId) {
    StudentProfile student = students.findById(studentId)
        .orElseThrow(() -> EntityNotFoundException.of("StudentProfile", studentId));
    return viewsFor(student);
  }

  private List<LearningNeedView> viewsFor(StudentProfile student) {
    var owned = needs.findByStudentIdOrderByCreatedAtAscNeedIdAsc(student.getStudentId());
    if (owned.isEmpty()) return List.of();
    return LearningNeedViews.fromAll(
        owned,
        Map.of(student.getStudentId(), student.getUserId()),
        slots.findByNeedIdInOrderByDayOfWeekAscStartTimeAsc(owned.stream().map(LearningNeed::getNeedId).toList()),
        catalogLoader.load());
  }

  private StudentProfile authorizedStudent(CurrentUser actor, UUID requestedStudentId) {
    if (actor == null || actor.getUserId() == null) {
      throw new ForbiddenOperationException("Yêu cầu cần được xác thực.");
    }
    if (actor.hasRole("STUDENT")) {
      // Hồ sơ học sinh chỉ được tạo khi gọi API hồ sơ; học sinh vừa đăng ký có thể vào thẳng form nhu cầu.
      StudentProfile own = students.findByUserId(actor.getUserId())
          .orElseGet(() -> students.saveAndFlush(new StudentProfile(actor.getUserId())));
      if (requestedStudentId != null && !requestedStudentId.equals(own.getStudentId())) {
        throw new ForbiddenOperationException("Học sinh chỉ quản lý được nhu cầu học của mình.");
      }
      return own;
    }
    if (actor.hasRole("PARENT")) {
      if (requestedStudentId == null) {
        throw FieldValidationException.of("studentId", "Vui lòng chọn con cần quản lý nhu cầu học.");
      }
      if (!parentLinks.hasConfirmedLink(actor.getUserId(), requestedStudentId)) {
        throw new ForbiddenOperationException("Chỉ phụ huynh có liên kết đã xác nhận được quản lý nhu cầu học của con.");
      }
      return students.findById(requestedStudentId)
          .orElseThrow(() -> EntityNotFoundException.of("StudentProfile", requestedStudentId));
    }
    throw new ForbiddenOperationException("Chỉ học sinh hoặc phụ huynh được quản lý nhu cầu học.");
  }

  private LearningCatalog.Classification validate(SaveLearningNeedRequest input, LearningCatalog catalog) {
    Map<String, String> errors = new LinkedHashMap<>();
    var classification = catalog.validateNeed(new LearningCatalog.Classification(
        input.educationLevelId(), input.subjectId(), input.trackId(), input.goalTagId(),
        input.textbookSeriesId(), input.examProvinceId(), input.targetSchoolId()), errors);
    boolean inPerson = input.mode() == TeachingMode.OFFLINE || input.mode() == TeachingMode.BOTH;
    if (inPerson && (input.provinceId() != null || input.districtId() != null)) {
      catalog.validateLocation(input.provinceId(),
          input.districtId() == null ? List.of() : List.of(input.districtId()),
          "provinceId", "districtId", errors);
    }
    validateSlots(input.slotsOrEmpty(), errors);
    if (!errors.isEmpty() || classification.isEmpty()) {
      throw new FieldValidationException(errors);
    }
    return classification.get();
  }

  private void validateSlots(List<SaveLearningNeedRequest.Slot> requested, Map<String, String> errors) {
    var ordered = requested.stream()
        .sorted(Comparator.comparing(SaveLearningNeedRequest.Slot::dayOfWeek)
            .thenComparing(SaveLearningNeedRequest.Slot::startTime))
        .toList();
    for (int index = 1; index < ordered.size(); index++) {
      var previous = ordered.get(index - 1);
      var current = ordered.get(index);
      if (previous.dayOfWeek().equals(current.dayOfWeek()) && current.startTime().isBefore(previous.endTime())) {
        errors.put("availabilitySlots", "Các khung giờ trong cùng ngày không được trùng hoặc giao nhau.");
        return;
      }
    }
  }

  private void apply(LearningNeed need, LearningCatalog.Classification classification, SaveLearningNeedRequest input) {
    need.classify(
        classification.educationLevelId(),
        classification.trackId(),
        classification.goalTagId(),
        classification.textbookSeriesId(),
        classification.examProvinceId(),
        classification.targetSchoolId());
    need.arrange(input.mode(), input.provinceId(), input.districtId(), input.budgetMaxPerSession());
  }

  private void replaceSlots(LearningNeed need, SaveLearningNeedRequest input) {
    slots.deleteForNeed(need.getNeedId());
    slots.saveAllAndFlush(input.slotsOrEmpty().stream()
        .map(slot -> new LearningNeedSlot(need.getNeedId(), slot.dayOfWeek(), slot.startTime(), slot.endTime()))
        .toList());
  }

  private LearningNeedView refresh(LearningNeed need, StudentProfile student, LearningCatalog catalog) {
    List<WeeklyAvailabilityDto> current = slotViews(need.getNeedId());
    need.markStatus(LearningNeedViews.statusFor(need, catalog, current.size()));
    LearningNeed saved = needs.saveAndFlush(need);
    return LearningNeedViews.from(saved, student.getUserId(), current, catalog);
  }

  private List<WeeklyAvailabilityDto> slotViews(UUID needId) {
    return slots.findByNeedIdInOrderByDayOfWeekAscStartTimeAsc(List.of(needId)).stream()
        .map(slot -> new WeeklyAvailabilityDto(slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime()))
        .toList();
  }
}
