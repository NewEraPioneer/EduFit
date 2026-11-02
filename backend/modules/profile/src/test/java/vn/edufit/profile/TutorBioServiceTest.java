package vn.edufit.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import vn.edufit.ai.api.AiFeature;
import vn.edufit.ai.api.AiGateway;
import vn.edufit.ai.api.AiRequest;
import vn.edufit.ai.api.AiResponse;
import vn.edufit.ai.api.AiTimeoutException;
import vn.edufit.ai.api.AiUnavailableException;
import vn.edufit.ai.api.AiUsageQuery;
import vn.edufit.profile.application.service.TutorBioService;
import vn.edufit.profile.application.service.TutorProfileAssembler;
import vn.edufit.profile.infra.persistence.entity.EducationLevel;
import vn.edufit.profile.infra.persistence.entity.Subject;
import vn.edufit.profile.infra.persistence.entity.TeachingMode;
import vn.edufit.profile.infra.persistence.entity.TutorProfile;
import vn.edufit.profile.infra.persistence.repository.TutorProfileRepository;
import vn.edufit.profile.web.request.GenerateTutorBioRequest;
import vn.edufit.profile.web.response.GenerateTutorBioResponse;
import vn.edufit.shared.auth.CurrentUser;

@ExtendWith(MockitoExtension.class)
class TutorBioServiceTest {

  @Mock
  private AiGateway aiGateway;

  @Mock
  private AiUsageQuery aiUsageQuery;

  @Mock
  private TutorProfileRepository tutorProfileRepository;

  @Mock
  private TutorProfileAssembler tutorProfileAssembler;

  private TutorBioService tutorBioService;
  private CurrentUser testCurrentUser;
  private UUID testUserId;

  @BeforeEach
  void setUp() {
    tutorBioService = new TutorBioService(
        aiGateway,
        aiUsageQuery,
        tutorProfileRepository,
        tutorProfileAssembler
    );

    testUserId = UUID.randomUUID();
    testCurrentUser = new CurrentUser() {
      @Override
      public UUID getUserId() {
        return testUserId;
      }

      @Override
      public String getEmail() {
        return "tutor@edufit.vn";
      }

      @Override
      public Set<String> getRoles() {
        return Set.of("ROLE_TUTOR");
      }

      @Override
      public boolean hasRole(String role) {
        return "TUTOR".equalsIgnoreCase(role) || "ROLE_TUTOR".equalsIgnoreCase(role);
      }
    };
  }

  @Test
  @DisplayName("Sinh Bio thành công khi AI Gateway phản hồi tốt")
  void shouldGenerateBioSuccessfullyWhenAiAvailable() {
    when(aiUsageQuery.countRequestsSince(eq(testUserId), eq(AiFeature.TUTOR_BIO), any(Instant.class)))
        .thenReturn(2L);
    when(tutorProfileRepository.findByUserId(testUserId))
        .thenReturn(Optional.empty());

    String mockAiText = "Chào các em, thầy là Minh gia sư Toán 5 năm kinh nghiệm...";
    when(aiGateway.complete(any(AiRequest.class)))
        .thenReturn(new AiResponse(mockAiText, 350, 80, true));

    GenerateTutorBioRequest req = new GenerateTutorBioRequest(
        "Thầy Minh", List.of("Toán"), List.of("Cấp 3"), 5, "friendly", "exam", "Luyện thi ĐH"
    );

    GenerateTutorBioResponse resp = tutorBioService.generateBio(testCurrentUser, req);

    assertNotNull(resp);
    assertTrue(resp.isAiGenerated());
    assertEquals(mockAiText, resp.bio());
    assertEquals(7, resp.remainingQuota());
    verify(aiGateway).complete(any(AiRequest.class));
  }

  @Test
  @DisplayName("Tự động fallback template khi AI Gateway bị timeout 15s")
  void shouldFallbackWhenAiGatewayTimesOut() {
    when(aiUsageQuery.countRequestsSince(eq(testUserId), eq(AiFeature.TUTOR_BIO), any(Instant.class)))
        .thenReturn(1L);
    when(tutorProfileRepository.findByUserId(testUserId))
        .thenReturn(Optional.empty());

    when(aiGateway.complete(any(AiRequest.class)))
        .thenThrow(new AiTimeoutException("Cuộc gọi quá 15s"));

    GenerateTutorBioRequest req = new GenerateTutorBioRequest(
        "Cô Lan", List.of("Tiếng Anh"), List.of("Cấp 2"), 3, "professional", "foundation", null
    );

    GenerateTutorBioResponse resp = tutorBioService.generateBio(testCurrentUser, req);

    assertNotNull(resp);
    assertFalse(resp.isAiGenerated());
    assertTrue(resp.bio().contains("Tiếng Anh"));
    assertTrue(resp.bio().contains("3 năm"));
    assertEquals(0, resp.remainingQuota());
  }

  @Test
  @DisplayName("Kích hoạt fallback và không gọi AI khi vượt quá hạn ngạch 10 lần/giờ")
  void shouldFallbackWhenQuotaExceeded() {
    when(aiUsageQuery.countRequestsSince(eq(testUserId), eq(AiFeature.TUTOR_BIO), any(Instant.class)))
        .thenReturn(10L);
    when(tutorProfileRepository.findByUserId(testUserId))
        .thenReturn(Optional.empty());

    GenerateTutorBioRequest req = new GenerateTutorBioRequest(
        "Thầy Hùng", List.of("Vật Lý"), List.of("Cấp 3"), 4, "concise", "exam", null
    );

    GenerateTutorBioResponse resp = tutorBioService.generateBio(testCurrentUser, req);

    assertNotNull(resp);
    assertFalse(resp.isAiGenerated());
    assertTrue(resp.bio().contains("Vật Lý"));
    assertEquals(0, resp.remainingQuota());
    verify(aiGateway, never()).complete(any());
  }

  @Test
  @DisplayName("Tự động nạp thông tin từ database nếu request để trống")
  void shouldLoadTutorContextFromDatabaseWhenRequestFieldsAreNull() {
    UUID tutorId = UUID.randomUUID();
    TutorProfile profile = new TutorProfile(testUserId, "Thầy Bách DB", TeachingMode.ONLINE);
    profile.setExperienceYears((short) 6);

    when(aiUsageQuery.countRequestsSince(eq(testUserId), eq(AiFeature.TUTOR_BIO), any(Instant.class)))
        .thenReturn(0L);
    when(tutorProfileRepository.findByUserId(testUserId))
        .thenReturn(Optional.of(profile));

    when(aiGateway.complete(any(AiRequest.class)))
        .thenReturn(new AiResponse("Bio sinh từ DB...", 200, 50, true));

    GenerateTutorBioRequest req = new GenerateTutorBioRequest(
        null, null, null, null, "friendly", "exam", null
    );

    GenerateTutorBioResponse resp = tutorBioService.generateBio(testCurrentUser, req);

    assertNotNull(resp);
    assertTrue(resp.isAiGenerated());
    assertEquals("Bio sinh từ DB...", resp.bio());
  }

  @Test
  @DisplayName("Gửi nội dung bản thảo draftBio và chỉ dẫn ưu tiên profile khi gia sư có bản nháp")
  void shouldPassDraftBioAndConflictRulesInPromptWhenDraftBioProvided() {
    when(aiUsageQuery.countRequestsSince(eq(testUserId), eq(AiFeature.TUTOR_BIO), any(Instant.class)))
        .thenReturn(0L);
    when(tutorProfileRepository.findByUserId(testUserId))
        .thenReturn(Optional.empty());

    String draftBio = "Tôi đã dạy 10 năm kinh nghiệm và chuyên dạy Hóa học bằng sơ đồ tư duy.";
    GenerateTutorBioRequest req = new GenerateTutorBioRequest(
        "Thầy Nam", List.of("Toán"), List.of("Lớp 9"), 4, "friendly", "exam", null, draftBio
    );

    org.mockito.ArgumentCaptor<AiRequest> captor = org.mockito.ArgumentCaptor.forClass(AiRequest.class);
    when(aiGateway.complete(captor.capture()))
        .thenReturn(new AiResponse("Bio hoàn chỉnh kết hợp sơ đồ tư duy cho môn Toán 4 năm...", 150, 40, true));

    GenerateTutorBioResponse resp = tutorBioService.generateBio(testCurrentUser, req);

    assertNotNull(resp);
    assertTrue(resp.isAiGenerated());
    AiRequest captured = captor.getValue();
    assertNotNull(captured);
    assertTrue(captured.userPrompt().contains(draftBio));
    assertTrue(captured.userPrompt().contains("BẮT BUỘC PHẢI ƯU TIÊN VÀ SỬA THEO THÔNG TIN HỒ SƠ CHÍNH THỨC"));
    assertTrue(captured.systemPrompt().contains("STRICT CONFLICT RESOLUTION"));
  }
}
