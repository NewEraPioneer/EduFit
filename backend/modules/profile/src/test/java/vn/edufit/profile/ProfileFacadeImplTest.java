package vn.edufit.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edufit.profile.api.dto.TutorSearchCriteria;
import vn.edufit.profile.application.service.LearningCatalogLoader;
import vn.edufit.profile.application.service.LearningNeedService;
import vn.edufit.profile.application.service.ProfileFacadeImpl;
import vn.edufit.profile.application.service.TutorProfileAssembler;
import vn.edufit.profile.infra.persistence.entity.EducationLevel;
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
import vn.edufit.shared.exception.InvalidOperationException;

@ExtendWith(MockitoExtension.class)
class ProfileFacadeImplTest {

  @Mock private TutorProfileRepository tutorProfileRepository;
  @Mock private StudentProfileRepository studentProfileRepository;
  @Mock private TutorOfferingRepository tutorOfferingRepository;
  @Mock private LearningNeedRepository learningNeedRepository;
  @Mock private LearningNeedSlotRepository learningNeedSlotRepository;
  @Mock private EducationLevelRepository educationLevelRepository;
  @Mock private LearningNeedService learningNeedService;
  @Mock private LearningCatalogLoader catalogLoader;
  @Mock private TutorProfileAssembler assembler;

  private ProfileFacadeImpl profileFacade;

  @BeforeEach
  void setUp() {
    profileFacade = facade(200);
  }

  private ProfileFacadeImpl facade(int candidateLimit) {
    return new ProfileFacadeImpl(tutorProfileRepository, studentProfileRepository, tutorOfferingRepository,
        learningNeedRepository, learningNeedSlotRepository, educationLevelRepository, learningNeedService,
        catalogLoader, assembler, candidateLimit);
  }

  private static TutorProfile tutor(UUID tutorId, UUID userId) {
    TutorProfile tutor = new TutorProfile(userId, "Cô Hà", TeachingMode.BOTH);
    ReflectionTestUtils.setField(tutor, "tutorId", tutorId);
    return tutor;
  }

  @Test
  @DisplayName("Ứng viên ghép đôi chỉ lọc theo tư cách: đã xác minh + có gói dạy môn, giới hạn theo cấu hình")
  void candidatesFilterOnlyByEligibilityWithConfiguredLimit() {
    var tutors = List.of(tutor(UUID.randomUUID(), UUID.randomUUID()));
    when(tutorProfileRepository.findVerifiedCandidatesBySubject(TutorStatus.VERIFIED, 1, PageRequest.of(0, 37)))
        .thenReturn(tutors);
    when(assembler.discoveryProfiles(tutors)).thenReturn(List.of());

    facade(37).findVerifiedCandidatesBySubject(1);

    verify(assembler).discoveryProfiles(tutors);
  }

  @Test
  void matchingRejectsMissingSubjectAndNonPositiveLimit() {
    assertThrows(InvalidOperationException.class, () -> profileFacade.findVerifiedCandidatesBySubject(null));
    assertThrows(IllegalArgumentException.class, () -> facade(0));
    verifyNoInteractions(tutorProfileRepository);
  }

  @Test
  @DisplayName("Tìm kiếm: chỉ bật lọc theo gói dạy khi người dùng chọn tiêu chí gói dạy")
  void searchEnablesOfferingFilterOnlyWhenNeeded() {
    when(tutorProfileRepository.searchVerifiedTutors(eq(TutorStatus.VERIFIED), any(), any(), any(Boolean.class),
        any(), any(), any(), any(), any(), any(Boolean.class), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any())).thenReturn(List.of());

    profileFacade.searchVerifiedTutors(new TutorSearchCriteria(4, 1, null, 3, 1, "graduate", "OFFLINE", 1, 5,
        100000L, 300000L, new BigDecimal("4.0"), " Hà ", (short) 2, LocalTime.of(18, 0), LocalTime.of(20, 0)), 50);

    verify(tutorProfileRepository).searchVerifiedTutors(TutorStatus.VERIFIED, TutorType.GRADUATE,
        TeachingMode.OFFLINE, true, TeachingMode.BOTH, 1, 5, new BigDecimal("4.0"), "Hà", true, 1, null, 4, 1,
        100000L, 300000L, 3, (short) 2, LocalTime.of(18, 0), LocalTime.of(20, 0), PageRequest.of(0, 50));

    profileFacade.searchVerifiedTutors(TutorSearchCriteria.none(), 10);
    verify(tutorProfileRepository).searchVerifiedTutors(TutorStatus.VERIFIED, null, null, false, TeachingMode.BOTH,
        null, null, null, null, false, null, null, null, null, null, null, null, null, null, null,
        PageRequest.of(0, 10));
  }

  @Test
  void searchRejectsInvalidTutorTypeAndMode() {
    var badType = new TutorSearchCriteria(null, null, null, null, null, "WIZARD", null, null, null, null, null,
        null, null, null, null, null);
    var badMode = new TutorSearchCriteria(null, null, null, null, null, null, "HYBRID", null, null, null, null,
        null, null, null, null, null);
    assertThrows(InvalidOperationException.class, () -> profileFacade.searchVerifiedTutors(badType, 10));
    assertThrows(InvalidOperationException.class, () -> profileFacade.searchVerifiedTutors(badMode, 10));
  }

  @Test
  void shouldExposeBusinessOrderingWithoutChangingLevelIds() {
    var grade9 = new EducationLevel("Lớp 9", 4, true);
    ReflectionTestUtils.setField(grade9, "levelId", 40);
    var grade6 = new EducationLevel("Lớp 6", 1, true);
    ReflectionTestUtils.setField(grade6, "levelId", 7);
    when(educationLevelRepository.findAllByOrderBySortOrderAscLevelIdAsc()).thenReturn(List.of(grade6, grade9));

    var levels = profileFacade.findEducationLevelsForMatching();

    assertEquals(List.of(7, 40), levels.stream().map(level -> level.levelId()).toList());
  }

  @Test
  void shouldRejectAmbiguousLevelOrdering() {
    var first = new EducationLevel("Lớp 6", 1, true);
    var second = new EducationLevel("Lớp 7", 1, true);
    when(educationLevelRepository.findAllByOrderBySortOrderAscLevelIdAsc()).thenReturn(List.of(first, second));
    assertThrows(InvalidOperationException.class, () -> profileFacade.findEducationLevelsForMatching());
  }

  @Test
  @DisplayName("Ném ngoại lệ khi getTutorIdByUserId không tìm thấy")
  void shouldThrowWhenTutorNotFoundByUserId() {
    UUID userId = UUID.randomUUID();
    when(tutorProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
    assertThrows(EntityNotFoundException.class, () -> profileFacade.getTutorIdByUserId(userId));
  }

  @Test
  @DisplayName("Chuyển trạng thái xác minh của gia sư")
  void shouldTransitionTutorVerificationStatus() {
    UUID tutorId = UUID.randomUUID();
    TutorProfile tutor = tutor(tutorId, UUID.randomUUID());
    when(tutorProfileRepository.findById(tutorId)).thenReturn(Optional.of(tutor));

    profileFacade.markTutorPendingVerification(tutorId);
    assertEquals(TutorStatus.PENDING_VERIFICATION, tutor.getStatus());

    Instant verifiedAt = Instant.parse("2026-11-01T00:00:00Z");
    profileFacade.markTutorVerified(tutorId, verifiedAt);
    assertEquals(TutorStatus.VERIFIED, tutor.getStatus());
    assertEquals(verifiedAt, tutor.getVerifiedAt());

    profileFacade.markTutorUnverified(tutorId);
    assertEquals(TutorStatus.UNVERIFIED, tutor.getStatus());
  }

  @Test
  @DisplayName("Duyệt hồ sơ: xác nhận loại gia sư và bằng chứng mục tiêu premium của đúng gói dạy")
  void approvalConfirmsTutorTypeAndOfferingEvidence() {
    UUID tutorId = UUID.randomUUID();
    TutorProfile tutor = tutor(tutorId, UUID.randomUUID());
    tutor.declareTutorType(TutorType.IN_SERVICE_TEACHER);
    when(tutorProfileRepository.findById(tutorId)).thenReturn(Optional.of(tutor));
    TutorOffering offering = new TutorOffering(tutorId, 1, null);
    offering.define(Set.of(4), Set.of(3), Set.of(), Set.of(), 300000L);
    UUID offeringId = UUID.randomUUID();
    when(tutorOfferingRepository.findByOfferingIdInAndTutorId(Set.of(offeringId), tutorId))
        .thenReturn(List.of(offering));

    assertFalse(offering.goalEvidence().get(3));
    profileFacade.applyVerificationApproval(tutorId, "IN_SERVICE_TEACHER", Set.of(offeringId));

    assertTrue(tutor.isTutorTypeVerified());
    assertTrue(offering.goalEvidence().get(3));
  }

  @Test
  @DisplayName("Duyệt hồ sơ không kèm bằng chứng gói dạy thì không đụng tới gói dạy")
  void approvalWithoutEvidenceSkipsOfferings() {
    UUID tutorId = UUID.randomUUID();
    when(tutorProfileRepository.findById(tutorId)).thenReturn(Optional.of(tutor(tutorId, UUID.randomUUID())));
    profileFacade.applyVerificationApproval(tutorId, null, Set.of());
    verify(tutorOfferingRepository, never()).findByOfferingIdInAndTutorId(anyCollection(), any());
  }

  @Test
  @DisplayName("Tìm học sinh theo userId trả khu vực mặc định từ danh mục")
  void shouldFindStudentByUserId() {
    UUID userId = UUID.randomUUID();
    StudentProfile student = new StudentProfile(userId);
    student.setEducationLevelId(4);
    student.setLocation(1, 5);
    when(studentProfileRepository.findByUserId(userId)).thenReturn(Optional.of(student));

    var dto = profileFacade.findStudentByUserId(userId).orElseThrow();

    assertEquals(4, dto.educationLevelId());
    assertEquals(1, dto.provinceId());
    assertEquals(5, dto.districtId());
  }

  @Test
  void learningNeedLookupsHandleEmptyInput() {
    assertEquals(Map.of(), profileFacade.findLearningNeeds(List.of()));
    assertEquals(Map.of(), profileFacade.findLearningNeeds(null));
    assertTrue(profileFacade.findLearningNeed(null).isEmpty());
    verifyNoInteractions(learningNeedRepository);
  }
}
