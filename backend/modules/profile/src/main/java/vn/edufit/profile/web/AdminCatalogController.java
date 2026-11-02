package vn.edufit.profile.web;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edufit.profile.application.service.AdminCatalogService;
import vn.edufit.profile.web.request.CreateSubjectRequest;
import vn.edufit.profile.web.request.UpdateSubjectStatusRequest;
import vn.edufit.profile.web.response.SubjectAdminResponse;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.response.ApiResponse;

/**
 * REST Controller quản lý Danh mục Môn học (Admin Catalogs) dành cho Quản trị viên.
 *
 * <p>Đặc tả kỹ thuật:
 * <ul>
 *   <li>Bảo vệ bảo mật với {@code ROLE_ADMIN} tại tầng SecurityFilterChain (/api/v1/admin/**).</li>
 *   <li>Cung cấp đầy đủ các thao tác tra cứu, tạo mới và chuyển đổi mềm (soft-toggle).</li>
 *   <li>Nghiêm cấm xóa cứng (Hard Delete) qua giao thức HTTP DELETE để bảo vệ toàn vẹn khóa ngoại CSDL.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/catalogs")
public class AdminCatalogController {

  private final AdminCatalogService adminCatalogService;

  public AdminCatalogController(AdminCatalogService adminCatalogService) {
    this.adminCatalogService = adminCatalogService;
  }

  @GetMapping("/subjects")
  public ResponseEntity<ApiResponse<List<SubjectAdminResponse>>> getSubjects() {
    List<SubjectAdminResponse> subjects = adminCatalogService.getAllSubjects();
    return ResponseEntity.ok(ApiResponse.success(subjects, "Lấy danh sách môn học quản trị thành công"));
  }

  @PostMapping("/subjects")
  public ResponseEntity<ApiResponse<SubjectAdminResponse>> createSubject(
      @Valid @RequestBody CreateSubjectRequest request
  ) {
    SubjectAdminResponse created = adminCatalogService.createSubject(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(created, "Tạo môn học mới thành công"));
  }

  @PatchMapping("/subjects/{id}/toggle-status")
  public ResponseEntity<ApiResponse<SubjectAdminResponse>> toggleSubjectStatus(
      @PathVariable("id") Integer id
  ) {
    SubjectAdminResponse updated = adminCatalogService.toggleSubjectStatus(id);
    return ResponseEntity.ok(ApiResponse.success(updated, "Chuyển đổi trạng thái môn học thành công"));
  }

  @PutMapping("/subjects/{id}/status")
  public ResponseEntity<ApiResponse<SubjectAdminResponse>> updateSubjectStatusPut(
      @PathVariable("id") Integer id,
      @RequestBody(required = false) UpdateSubjectStatusRequest request
  ) {
    Boolean isActive = (request != null) ? request.isActive() : null;
    SubjectAdminResponse updated = adminCatalogService.updateSubjectStatus(id, isActive);
    return ResponseEntity.ok(ApiResponse.success(updated, "Cập nhật trạng thái môn học thành công"));
  }

  @PatchMapping("/subjects/{id}/status")
  public ResponseEntity<ApiResponse<SubjectAdminResponse>> updateSubjectStatusPatch(
      @PathVariable("id") Integer id,
      @RequestBody(required = false) UpdateSubjectStatusRequest request
  ) {
    Boolean isActive = (request != null) ? request.isActive() : null;
    SubjectAdminResponse updated = adminCatalogService.updateSubjectStatus(id, isActive);
    return ResponseEntity.ok(ApiResponse.success(updated, "Cập nhật trạng thái môn học thành công"));
  }

  @DeleteMapping("/subjects/{id}")
  public ResponseEntity<ApiResponse<Void>> deleteSubject(@PathVariable("id") Integer id) {
    throw new InvalidOperationException(
        ErrorCode.INVALID_OPERATION,
        "Không được phép xóa cứng môn học để bảo toàn tính toàn vẹn dữ liệu hệ thống"
    );
  }
}
