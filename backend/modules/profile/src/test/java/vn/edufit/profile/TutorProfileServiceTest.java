package vn.edufit.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vn.edufit.profile.application.service.CatalogFixtures.*;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edufit.profile.api.event.ProfileUpdatedEvent;
import vn.edufit.profile.application.service.CatalogFixtures;
import vn.edufit.profile.application.service.LearningCatalogLoader;
import vn.edufit.profile.application.service.TutorProfileAssembler;
import vn.edufit.profile.application.service.TutorProfileService;
import vn.edufit.profile.infra.persistence.entity.TeachingMode;
import vn.edufit.profile.infra.persistence.entity.TutorOffering;
import vn.edufit.profile.infra.persistence.entity.TutorProfile;
import vn.edufit.profile.infra.persistence.entity.TutorType;
import vn.edufit.profile.infra.persistence.repository.TutorAvailabilitySlotRepository;
import vn.edufit.profile.infra.persistence.repository.TutorOfferingRepository;
import vn.edufit.profile.infra.persistence.repository.TutorProfileRepository;
import vn.edufit.profile.web.request.SaveTutorOfferingRequest;
import vn.edufit.profile.web.request.SetAvailabilitySlotsRequest;
import vn.edufit.profile.web.request.UpdateTutorProfileRequest;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.FieldValidationException;
import vn.edufit.shared.exception.InvalidOperationException;

@ExtendWith(MockitoExtension.class)
class TutorProfileServiceTest {

  @Mock private TutorProfileRepository tutorProfileRepository;
  @Mock private TutorOfferingRepository tutorOfferingRepository;
  @Mock private TutorAvailabilitySlotRepository tutorAvailabilitySlotRepository;
  @Mock private LearningCatalogLoader catalogLoader;
  @Mock private ApplicationEventPublisher eventPublisher;

  private TutorProfileService tutorProfileService;
  private final UUID userId = UUID.randomUUID();
  private final UUID tutorId = UUID.randomUUID();
  private TutorProfile profile;

  @BeforeEach
  void setUp() {
    lenient().when(catalogLoader.load()).thenReturn(CatalogFixtures.catalog());
    var assembler = new TutorProfileAssembler(tutorOfferingRepository, tutorAvailabilitySlotRepository, catalogLoader);
    tutorProfileService = new TutorProfileService(tutorProfileRepository, tutorOfferingRepository,
        tutorAvailabilitySlotRepository, catalogLoader, assembler, eventPublisher);
    profile = new TutorProfile(userId, "Gia sư A", TeachingMode.ONLINE);
    ReflectionTestUtils.setField(profile, "tutorId", tutorId);
    lenient().when(tutorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    lenient().when(tutorProfileRepository.save(any(TutorProfile.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  private static UpdateTutorProfileRequest profileRequest(TeachingMode mode, Integer provinceId, List<Integer> districts) {
    return new UpdateTutorProfileRequest("Thầy Nam", "Gia sư Toán", "Giới thiệu", mode, TutorType.GRADUATE,
        provinceId, districts, (short) 5, "Học đi đôi với hành");
  }

  @Test
  @DisplayName("BR-07: dạy trực tiếp bắt buộc Tỉnh/Thành, lỗi trả theo trường")
  void inPersonRequiresProvince() {
    var ex = assertThrows(FieldValidationException.class,
        () -> tutorProfileService.updateProfile(actor(), profileRequest(TeachingMode.OFFLINE, null, List.of())));
    assertTrue(ex.getFieldErrors().containsKey("provinceId"));
  }

  @Test
  @DisplayName("Khu vực dạy phải thuộc tỉnh đã chọn")
  void districtsMustBelongToProvince() {
    var ex = assertThrows(FieldValidationException.class, () -> tutorProfileService.updateProfile(actor(),
        profileRequest(TeachingMode.BOTH, HA_NOI, List.of(HAI_CHAU))));
    assertTrue(ex.getFieldErrors().containsKey("districtIds"));
  }

  @Test
  @DisplayName("Cập nhật hồ sơ: lưu Tỉnh + nhiều Khu vực, loại gia sư chờ admin xác nhận, phát sự kiện")
  void updateProfileStoresTeachingAreaAndDeclaredType() {
    var resp = tutorProfileService.updateProfile(actor(), profileRequest(TeachingMode.BOTH, HA_NOI, List.of(CAU_GIAY)));

    assertEquals("Thầy Nam", resp.displayName());
    assertEquals("Hà Nội", resp.provinceName());
    assertEquals(List.of("Cầu Giấy"), resp.districts().stream().map(d -> d.name()).toList());
    assertEquals("GRADUATE", resp.tutorType());
    assertEquals(false, resp.tutorTypeVerified());
    ArgumentCaptor<ProfileUpdatedEvent> event = ArgumentCaptor.forClass(ProfileUpdatedEvent.class);
    verify(eventPublisher).publishEvent(event.capture());
    assertEquals("TUTOR", event.getValue().profileType());
  }

  @Test
  @DisplayName("Dạy trực tuyến thì bỏ khu vực dạy trực tiếp")
  void onlineClearsTeachingArea() {
    profile.setTeachingArea(HA_NOI, Set.of(CAU_GIAY));
    var resp = tutorProfileService.updateProfile(actor(), profileRequest(TeachingMode.ONLINE, HA_NOI, List.of(CAU_GIAY)));
    assertEquals(null, resp.provinceId());
    assertTrue(resp.districts().isEmpty());
  }

  @Test
  @DisplayName("Tạo gói dạy: mục tiêu cần bộ sách thì bắt buộc bộ sách; Luyện thi vào 10 bắt buộc tỉnh luyện đề")
  void offeringRequiresTextbookAndExamProvinceByGoal() {
    var request = new SaveTutorOfferingRequest(MATH, null, List.of(LEVEL_8, LEVEL_9),
        List.of(FOUNDATION, GRADE10_EXAM), List.of(), List.of(), 250000L);
    var ex = assertThrows(FieldValidationException.class, () -> tutorProfileService.createOffering(actor(), request));
    assertTrue(ex.getFieldErrors().containsKey("textbookSeriesIds"));
    assertTrue(ex.getFieldErrors().containsKey("examProvinceIds"));
    verify(tutorOfferingRepository, never()).save(any());
  }

  @Test
  @DisplayName("Tạo gói dạy hợp lệ: bộ sách/tỉnh không áp dụng bị bỏ đi")
  void offeringDropsNonApplicableTextbooksAndProvinces() {
    var request = new SaveTutorOfferingRequest(MATH, null, List.of(LEVEL_9), List.of(GIFTED),
        List.of(KNTT), List.of(HA_NOI), 400000L);
    ArgumentCaptor<TutorOffering> saved = ArgumentCaptor.forClass(TutorOffering.class);

    tutorProfileService.createOffering(actor(), request);

    verify(tutorOfferingRepository).save(saved.capture());
    assertEquals(Set.of(GIFTED), saved.getValue().getGoalTagIds());
    assertTrue(saved.getValue().getTextbookIds().isEmpty());
    assertTrue(saved.getValue().getExamProvinceIds().isEmpty());
    assertEquals(400000L, saved.getValue().getPricePerSession());
  }

  @Test
  @DisplayName("Không tạo 2 gói dạy cùng môn + nhánh")
  void duplicateOfferingRejected() {
    when(tutorOfferingRepository.existsByTutorIdAndSubjectIdAndTrackIdIsNull(tutorId, MATH)).thenReturn(true);
    var request = new SaveTutorOfferingRequest(MATH, null, List.of(LEVEL_9), List.of(GIFTED), null, null, 1L);
    var ex = assertThrows(FieldValidationException.class, () -> tutorProfileService.createOffering(actor(), request));
    assertTrue(ex.getFieldErrors().containsKey("subjectId"));
  }

  @Test
  @DisplayName("Sửa gói dạy giữ trạng thái xác minh bằng chứng của mục tiêu premium đã duyệt")
  void updateOfferingKeepsVerifiedEvidence() {
    TutorOffering offering = new TutorOffering(tutorId, MATH, null);
    offering.define(Set.of(LEVEL_9), Set.of(GIFTED), Set.of(), Set.of(), 300000L);
    offering.verifyGoalEvidence();
    UUID offeringId = UUID.randomUUID();
    when(tutorOfferingRepository.findByOfferingIdAndTutorId(offeringId, tutorId)).thenReturn(Optional.of(offering));

    tutorProfileService.updateOffering(actor(), offeringId, new SaveTutorOfferingRequest(MATH, null,
        List.of(LEVEL_8, LEVEL_9), List.of(GIFTED, GRADE10_EXAM), null, List.of(HA_NOI), 350000L));

    assertEquals(true, offering.goalEvidence().get(GIFTED));
    assertEquals(false, offering.goalEvidence().get(GRADE10_EXAM));
  }

  @Test
  @DisplayName("Ném lỗi khi đặt khung giờ kết thúc trước hoặc bằng giờ bắt đầu")
  void shouldThrowWhenSlotEndTimeBeforeStartTime() {
    SetAvailabilitySlotsRequest req = new SetAvailabilitySlotsRequest(List.of(
        new SetAvailabilitySlotsRequest.SlotItem((short) 1, LocalTime.of(10, 0), LocalTime.of(9, 0))
    ));
    assertThrows(InvalidOperationException.class, () -> tutorProfileService.setAvailabilitySlots(actor(), req));
  }

  @Test
  @DisplayName("NFR-17: Ném lỗi khi các khung giờ rảnh trong cùng ngày bị trùng lấn")
  void shouldThrowWhenAvailabilitySlotsOverlapOnSameDay() {
    SetAvailabilitySlotsRequest req = new SetAvailabilitySlotsRequest(List.of(
        new SetAvailabilitySlotsRequest.SlotItem((short) 1, LocalTime.of(8, 0), LocalTime.of(10, 0)),
        new SetAvailabilitySlotsRequest.SlotItem((short) 1, LocalTime.of(9, 30), LocalTime.of(11, 30))
    ));
    assertThrows(InvalidOperationException.class, () -> tutorProfileService.setAvailabilitySlots(actor(), req));
  }

  private CurrentUser actor() {
    return new CurrentUser() {
      @Override public UUID getUserId() { return userId; }
      @Override public String getEmail() { return "tutor@edufit.vn"; }
      @Override public Set<String> getRoles() { return Set.of("TUTOR"); }
      @Override public boolean hasRole(String role) { return "TUTOR".equals(role); }
    };
  }
}
