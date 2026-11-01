package vn.edufit.profile.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vn.edufit.profile.application.service.CatalogFixtures.*;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
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
import vn.edufit.shared.exception.FieldValidationException;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;

@ExtendWith(MockitoExtension.class)
class LearningNeedServiceTest {

  @Mock private LearningNeedRepository needs;
  @Mock private LearningNeedSlotRepository slots;
  @Mock private StudentProfileRepository students;
  @Mock private LearningCatalogLoader catalogLoader;
  @Mock private ParentStudentLinkPort parentLinks;

  private LearningNeedService service;
  private final UUID studentUserId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID parentUserId = UUID.randomUUID();
  private StudentProfile student;
  private final List<LearningNeedSlot> savedSlots = new ArrayList<>();

  @BeforeEach
  void setUp() {
    service = new LearningNeedService(needs, slots, students, catalogLoader, parentLinks);
    student = new StudentProfile(studentUserId);
    ReflectionTestUtils.setField(student, "studentId", studentId);
    lenient().when(catalogLoader.load()).thenReturn(CatalogFixtures.catalog());
    lenient().when(students.findByUserId(studentUserId)).thenReturn(Optional.of(student));
    lenient().when(students.findById(studentId)).thenReturn(Optional.of(student));
    lenient().when(needs.saveAndFlush(any())).thenAnswer(invocation -> {
      LearningNeed need = invocation.getArgument(0);
      if (need.getNeedId() == null) ReflectionTestUtils.setField(need, "needId", UUID.randomUUID());
      return need;
    });
    lenient().when(slots.saveAllAndFlush(any())).thenAnswer(invocation -> {
      savedSlots.clear();
      invocation.<List<LearningNeedSlot>>getArgument(0).forEach(savedSlots::add);
      return savedSlots;
    });
    lenient().when(slots.findByNeedIdInOrderByDayOfWeekAscStartTimeAsc(anyCollection()))
        .thenAnswer(invocation -> List.copyOf(savedSlots));
  }

  private static CurrentUser actor(UUID userId, String role) {
    return new CurrentUser() {
      @Override public UUID getUserId() { return userId; }
      @Override public String getEmail() { return role.toLowerCase() + "@edufit.vn"; }
      @Override public Set<String> getRoles() { return Set.of(role); }
      @Override public boolean hasRole(String value) { return role.equals(value); }
    };
  }

  private static SaveLearningNeedRequest onboardingRequest(UUID forStudent) {
    return new SaveLearningNeedRequest(forStudent, LEVEL_9, MATH, null, GRADE10_EXAM, null, HA_NOI, null,
        null, null, null, null, null);
  }

  private static SaveLearningNeedRequest completeRequest(UUID forStudent) {
    return new SaveLearningNeedRequest(forStudent, LEVEL_9, MATH, null, GRADE10_EXAM, null, HA_NOI, null,
        TeachingMode.OFFLINE, HA_NOI, CAU_GIAY, 300000L,
        List.of(new SaveLearningNeedRequest.Slot((short) 2, LocalTime.of(19, 0), LocalTime.of(21, 0))));
  }

  @Test
  @DisplayName("Onboarding chỉ có phân loại: nhu cầu ở trạng thái chưa đủ thông tin, liệt kê trường còn thiếu")
  void classificationOnlyNeedIsIncomplete() {
    var view = service.create(actor(studentUserId, "STUDENT"), onboardingRequest(null));
    assertEquals("INCOMPLETE", view.status());
    assertTrue(view.missingFields().containsAll(List.of("mode", "budgetMaxPerSession", "availabilitySlots")));
    assertEquals("Hà Nội", view.examProvinceName());
  }

  @Test
  @DisplayName("Nhu cầu đủ điều kiện học thì ACTIVE và tạo cùng khung giờ trong một lời gọi")
  void completeNeedIsActive() {
    var view = service.create(actor(studentUserId, "STUDENT"), completeRequest(null));
    assertEquals("ACTIVE", view.status());
    assertEquals(1, view.availabilitySlots().size());
    assertEquals("Cầu Giấy", view.districtName());
  }

  @Test
  @DisplayName("Học sinh vừa đăng ký chưa có hồ sơ: tạo nhu cầu tự khởi tạo hồ sơ học sinh")
  void newStudentWithoutProfileGetsProfileCreated() {
    UUID freshUserId = UUID.randomUUID();
    when(students.findByUserId(freshUserId)).thenReturn(Optional.empty());
    when(students.saveAndFlush(any(StudentProfile.class))).thenAnswer(invocation -> {
      StudentProfile created = invocation.getArgument(0);
      ReflectionTestUtils.setField(created, "studentId", UUID.randomUUID());
      return created;
    });

    var view = service.create(actor(freshUserId, "STUDENT"), onboardingRequest(null));

    assertEquals("INCOMPLETE", view.status());
    verify(students).saveAndFlush(any(StudentProfile.class));
  }

  @Test
  @DisplayName("Học sinh chưa có hồ sơ xem danh sách nhu cầu: trả danh sách rỗng, không lỗi, không tạo hồ sơ")
  void newStudentWithoutProfileListsEmpty() {
    UUID freshUserId = UUID.randomUUID();
    when(students.findByUserId(freshUserId)).thenReturn(Optional.empty());

    assertTrue(service.list(actor(freshUserId, "STUDENT"), null).isEmpty());
    verify(students, never()).saveAndFlush(any(StudentProfile.class));
  }

  @Test
  @DisplayName("Tổ hợp sai luật trả lỗi theo đúng trường")
  void invalidCombinationReturnsFieldErrors() {
    var request = new SaveLearningNeedRequest(null, LEVEL_6, MATH, null, GRADE10_EXAM, null, HA_NOI, null,
        null, null, null, null, null);
    var ex = assertThrows(FieldValidationException.class,
        () -> service.create(actor(studentUserId, "STUDENT"), request));
    assertTrue(ex.getFieldErrors().containsKey("goalTagId"));
    verify(needs, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("Phụ huynh đã liên kết được tạo nhu cầu cho con, created_by là phụ huynh")
  void linkedParentCreatesNeedForChild() {
    when(parentLinks.hasConfirmedLink(parentUserId, studentId)).thenReturn(true);
    var view = service.create(actor(parentUserId, "PARENT"), completeRequest(studentId));
    assertEquals(parentUserId, view.createdBy());
    assertEquals(studentId, view.studentId());
  }

  @Test
  @DisplayName("Phụ huynh chưa liên kết bị từ chối")
  void unlinkedParentIsForbidden() {
    when(parentLinks.hasConfirmedLink(parentUserId, studentId)).thenReturn(false);
    assertThrows(ForbiddenOperationException.class,
        () -> service.create(actor(parentUserId, "PARENT"), completeRequest(studentId)));
  }

  @Test
  @DisplayName("Không đổi được môn của nhu cầu đã tạo")
  void subjectIsImmutable() {
    LearningNeed existing = new LearningNeed(studentId, studentUserId, SCIENCE);
    ReflectionTestUtils.setField(existing, "needId", UUID.randomUUID());
    when(needs.findForUpdate(existing.getNeedId())).thenReturn(Optional.of(existing));
    var ex = assertThrows(FieldValidationException.class,
        () -> service.update(actor(studentUserId, "STUDENT"), existing.getNeedId(), completeRequest(null)));
    assertTrue(ex.getFieldErrors().containsKey("subjectId"));
  }

  @Test
  @DisplayName("Lưu trữ nhu cầu; nhu cầu đã lưu trữ không sửa được")
  void archiveThenRejectEdits() {
    LearningNeed existing = new LearningNeed(studentId, studentUserId, MATH);
    ReflectionTestUtils.setField(existing, "needId", UUID.randomUUID());
    when(needs.findForUpdate(existing.getNeedId())).thenReturn(Optional.of(existing));

    var view = service.archive(actor(studentUserId, "STUDENT"), existing.getNeedId());
    assertEquals("ARCHIVED", view.status());
    assertEquals(NeedStatus.ARCHIVED, existing.getStatus());
    assertThrows(InvalidOperationException.class,
        () -> service.update(actor(studentUserId, "STUDENT"), existing.getNeedId(), completeRequest(null)));
  }
}
