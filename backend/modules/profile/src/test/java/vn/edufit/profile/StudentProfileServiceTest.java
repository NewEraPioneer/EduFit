package vn.edufit.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import vn.edufit.profile.api.event.ProfileUpdatedEvent;
import vn.edufit.profile.application.service.StudentProfileService;
import vn.edufit.profile.infra.persistence.entity.StudentProfile;
import vn.edufit.profile.application.service.CatalogFixtures;
import vn.edufit.profile.application.service.LearningCatalogLoader;
import vn.edufit.profile.infra.persistence.repository.StudentProfileRepository;
import vn.edufit.profile.web.request.UpdateStudentProfileRequest;
import vn.edufit.profile.web.response.StudentProfileResponse;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.FieldValidationException;
import vn.edufit.shared.exception.ForbiddenOperationException;

@ExtendWith(MockitoExtension.class)
class StudentProfileServiceTest {

  @Mock
  private StudentProfileRepository studentProfileRepository;

  @Mock
  private LearningCatalogLoader learningCatalogLoader;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  private StudentProfileService studentProfileService;

  @BeforeEach
  void setUp() {
    studentProfileService = new StudentProfileService(
        studentProfileRepository,
        learningCatalogLoader,
        eventPublisher
    );
    lenient().when(learningCatalogLoader.load()).thenReturn(CatalogFixtures.catalog());
  }

  @Test
  @DisplayName("Lấy hồ sơ học viên tự động tạo mới nếu chưa tồn tại")
  void shouldCreateNewStudentProfileIfNotExists() {
    UUID userId = UUID.randomUUID();
    when(studentProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
    when(studentProfileRepository.save(any(StudentProfile.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    StudentProfileResponse response = studentProfileService.getProfileResponse(userId);

    assertNotNull(response);
    assertEquals(userId, response.userId());
    assertFalse(response.profileComplete());
  }

  @Test
  @DisplayName("Cập nhật hồ sơ học viên thành công và đánh dấu hoàn thành khi có đủ thông tin")
  void shouldUpdateProfileAndMarkComplete() {
    UUID userId = UUID.randomUUID();
    CurrentUser currentUser = mockCurrentUser(userId);
    StudentProfile existing = new StudentProfile(userId);

    when(studentProfileRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
    when(studentProfileRepository.save(any(StudentProfile.class))).thenAnswer(inv -> inv.getArgument(0));

    UpdateStudentProfileRequest request = new UpdateStudentProfileRequest(
        CatalogFixtures.LEVEL_9, CatalogFixtures.HA_NOI, CatalogFixtures.CAU_GIAY);
    StudentProfileResponse response = studentProfileService.updateProfile(currentUser, request);

    assertNotNull(response);
    assertTrue(response.profileComplete());
    assertEquals("Hà Nội", response.provinceName());
    assertEquals("Cầu Giấy", response.districtName());
    assertEquals("Lớp 9", response.educationLevelName());

    ArgumentCaptor<ProfileUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(ProfileUpdatedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertEquals("STUDENT", eventCaptor.getValue().profileType());
    assertEquals(userId, eventCaptor.getValue().userId());
  }

  @Test
  @DisplayName("Ném lỗi khi educationLevelId không tồn tại")
  void shouldThrowWhenEducationLevelNotFound() {
    UUID userId = UUID.randomUUID();
    CurrentUser currentUser = mockCurrentUser(userId);
    StudentProfile existing = new StudentProfile(userId);

    when(studentProfileRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

    UpdateStudentProfileRequest request = new UpdateStudentProfileRequest(999, CatalogFixtures.HA_NOI,
        CatalogFixtures.HAI_CHAU);
    var ex = assertThrows(FieldValidationException.class, () -> studentProfileService.updateProfile(currentUser, request));
    assertTrue(ex.getFieldErrors().containsKey("educationLevelId"));
    assertTrue(ex.getFieldErrors().containsKey("districtId"));
  }

  @Test
  @DisplayName("Ném lỗi khi chưa đăng nhập")
  void shouldThrowWhenUnauthenticated() {
    UpdateStudentProfileRequest request = new UpdateStudentProfileRequest(1, null, null);
    assertThrows(ForbiddenOperationException.class, () -> studentProfileService.updateProfile(null, request));
  }

  private CurrentUser mockCurrentUser(UUID userId) {
    return new CurrentUser() {
      @Override
      public UUID getUserId() {
        return userId;
      }

      @Override
      public String getEmail() {
        return "student@edufit.vn";
      }

      @Override
      public Set<String> getRoles() {
        return Set.of("STUDENT");
      }

      @Override
      public boolean hasRole(String role) {
        return "STUDENT".equals(role);
      }
    };
  }
}
