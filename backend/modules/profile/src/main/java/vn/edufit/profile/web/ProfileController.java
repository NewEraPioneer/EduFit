package vn.edufit.profile.web;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edufit.profile.application.service.StudentProfileService;
import vn.edufit.profile.application.service.TutorBioService;
import vn.edufit.profile.application.service.TutorProfileService;
import vn.edufit.profile.web.request.SaveTutorOfferingRequest;
import vn.edufit.profile.web.request.GenerateTutorBioRequest;
import vn.edufit.profile.web.request.SetAvailabilitySlotsRequest;
import vn.edufit.profile.web.request.UpdateStudentProfileRequest;
import vn.edufit.profile.web.request.UpdateTutorProfileRequest;
import vn.edufit.profile.web.response.GenerateTutorBioResponse;
import vn.edufit.profile.web.response.MyProfileResponse;
import vn.edufit.profile.web.response.StudentProfileResponse;
import vn.edufit.profile.web.response.TutorProfileResponse;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.response.ApiResponse;

/**
 * REST API Controller quản lý hồ sơ cá nhân của học sinh và gia sư (UC1.5 đến UC1.9).
 */
@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileController {

  private final StudentProfileService studentProfileService;
  private final TutorProfileService tutorProfileService;
  private final TutorBioService tutorBioService;
  private final ObjectProvider<CurrentUser> currentUserProvider;

  public ProfileController(
      StudentProfileService studentProfileService,
      TutorProfileService tutorProfileService,
      TutorBioService tutorBioService,
      ObjectProvider<CurrentUser> currentUserProvider
  ) {
    this.studentProfileService = studentProfileService;
    this.tutorProfileService = tutorProfileService;
    this.tutorBioService = tutorBioService;
    this.currentUserProvider = currentUserProvider;
  }

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<MyProfileResponse>> getMyProfile() {
    CurrentUser currentUser = getCurrentUser();

    StudentProfileResponse studentResp = studentProfileService
        .findProfileResponseByUserId(currentUser.getUserId())
        .orElse(null);

    TutorProfileResponse tutorResp = tutorProfileService
        .findProfileResponseByUserId(currentUser.getUserId())
        .orElse(null);

    MyProfileResponse myProfile = new MyProfileResponse(
        currentUser.getUserId(),
        currentUser.getEmail(),
        currentUser.getRoles(),
        studentResp,
        tutorResp
    );

    return ResponseEntity.ok(ApiResponse.success(myProfile, "Lấy thông tin tài khoản thành công"));
  }

  // =========================================================================
  // 1. ENDPOINTS HỒ SƠ HỌC VIÊN (STUDENT PROFILE)
  // =========================================================================

  @GetMapping("/students/me")
  public ResponseEntity<ApiResponse<StudentProfileResponse>> getMyStudentProfile() {
    CurrentUser currentUser = getCurrentUser();
    StudentProfileResponse response = studentProfileService.getProfileResponse(currentUser.getUserId());
    return ResponseEntity.ok(ApiResponse.success(response, "Lấy hồ sơ học viên thành công"));
  }

  @PutMapping("/students/me")
  public ResponseEntity<ApiResponse<StudentProfileResponse>> updateMyStudentProfile(
      @Valid @RequestBody UpdateStudentProfileRequest request
  ) {
    CurrentUser currentUser = getCurrentUser();
    StudentProfileResponse response = studentProfileService.updateProfile(currentUser, request);
    return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật hồ sơ học viên thành công"));
  }

  @GetMapping("/students/{studentId}")
  public ResponseEntity<ApiResponse<StudentProfileResponse>> getStudentProfileById(
      @PathVariable UUID studentId
  ) {
    StudentProfileResponse response = studentProfileService.getProfileResponseById(studentId);
    return ResponseEntity.ok(ApiResponse.success(response, "Lấy thông tin học viên thành công"));
  }

  // =========================================================================
  // 2. ENDPOINTS HỒ SƠ GIA SƯ (TUTOR PROFILE)
  // =========================================================================

  @GetMapping("/tutors/me")
  public ResponseEntity<ApiResponse<TutorProfileResponse>> getMyTutorProfile() {
    CurrentUser currentUser = getCurrentUser();
    TutorProfileResponse response = tutorProfileService.getProfileResponse(currentUser.getUserId());
    return ResponseEntity.ok(ApiResponse.success(response, "Lấy hồ sơ gia sư thành công"));
  }

  @PutMapping("/tutors/me")
  public ResponseEntity<ApiResponse<TutorProfileResponse>> updateMyTutorProfile(
      @Valid @RequestBody UpdateTutorProfileRequest request
  ) {
    CurrentUser currentUser = getCurrentUser();
    TutorProfileResponse response = tutorProfileService.updateProfile(currentUser, request);
    return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật hồ sơ gia sư thành công"));
  }

  @GetMapping("/tutors/{tutorId}")
  public ResponseEntity<ApiResponse<TutorProfileResponse>> getTutorProfileById(
      @PathVariable UUID tutorId
  ) {
    TutorProfileResponse response = tutorProfileService.getProfileResponseById(tutorId);
    return ResponseEntity.ok(ApiResponse.success(response, "Lấy thông tin gia sư thành công"));
  }

  @PostMapping("/tutors/me/offerings")
  public ResponseEntity<ApiResponse<TutorProfileResponse>> createTutorOffering(
      @Valid @RequestBody SaveTutorOfferingRequest request
  ) {
    TutorProfileResponse response = tutorProfileService.createOffering(getCurrentUser(), request);
    return ResponseEntity.ok(ApiResponse.success(response, "Thêm gói dạy thành công"));
  }

  @PutMapping("/tutors/me/offerings/{offeringId}")
  public ResponseEntity<ApiResponse<TutorProfileResponse>> updateTutorOffering(
      @PathVariable UUID offeringId,
      @Valid @RequestBody SaveTutorOfferingRequest request
  ) {
    TutorProfileResponse response = tutorProfileService.updateOffering(getCurrentUser(), offeringId, request);
    return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật gói dạy thành công"));
  }

  @DeleteMapping("/tutors/me/offerings/{offeringId}")
  public ResponseEntity<ApiResponse<TutorProfileResponse>> deleteTutorOffering(@PathVariable UUID offeringId) {
    TutorProfileResponse response = tutorProfileService.deleteOffering(getCurrentUser(), offeringId);
    return ResponseEntity.ok(ApiResponse.success(response, "Xóa gói dạy thành công"));
  }

  @PutMapping("/tutors/me/availability-slots")
  public ResponseEntity<ApiResponse<TutorProfileResponse>> setAvailabilitySlots(
      @Valid @RequestBody SetAvailabilitySlotsRequest request
  ) {
    CurrentUser currentUser = getCurrentUser();
    TutorProfileResponse response = tutorProfileService.setAvailabilitySlots(currentUser, request);
    return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật lịch rảnh thành công"));
  }

  @PostMapping("/tutors/me/generate-bio")
  public ResponseEntity<ApiResponse<GenerateTutorBioResponse>> generateTutorBio(
      @Valid @RequestBody(required = false) GenerateTutorBioRequest request
  ) {
    CurrentUser currentUser = getCurrentUser();
    GenerateTutorBioResponse response = tutorBioService.generateBio(currentUser, request);
    return ResponseEntity.ok(ApiResponse.success(response, "Tạo gợi ý tiểu sử gia sư thành công"));
  }

  private CurrentUser getCurrentUser() {
    CurrentUser user = currentUserProvider.getIfAvailable();
    if (user == null || user.getUserId() == null) {
      throw new ForbiddenOperationException("Yêu cầu cần được xác thực hoặc phiên đăng nhập đã hết hạn.");
    }
    return user;
  }
}
