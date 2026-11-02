package vn.edufit.progress.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edufit.progress.application.dto.PlanTemplateViews.PlanTemplateView;
import vn.edufit.progress.application.service.PlanTemplateService;
import vn.edufit.progress.web.request.SavePlanTemplateRequest;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.response.ApiResponse;

/** REST Controller cho mẫu lộ trình (/api/v1/progress/templates). */
@RestController
@RequestMapping("/api/v1/progress/templates")
@RequiredArgsConstructor
public class PlanTemplateController {

  private final PlanTemplateService templateService;
  private final ObjectProvider<CurrentUser> currentUserProvider;

  /** {@code classId}: chỉ trả mẫu hợp môn × khối × mục tiêu của lớp (gia sư lập lộ trình). */
  @GetMapping
  public ResponseEntity<ApiResponse<List<PlanTemplateView>>> list(
      @RequestParam(value = "classId", required = false) UUID classId
  ) {
    return ResponseEntity.ok(ApiResponse.success(
        templateService.list(currentUser(), classId), "Lấy danh sách mẫu lộ trình thành công"));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<PlanTemplateView>> create(@Valid @RequestBody SavePlanTemplateRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
        templateService.create(currentUser(), req.toCommand()), "Đã lưu mẫu lộ trình"));
  }

  @PutMapping("/{templateId}")
  public ResponseEntity<ApiResponse<PlanTemplateView>> update(
      @PathVariable("templateId") UUID templateId,
      @Valid @RequestBody SavePlanTemplateRequest req
  ) {
    return ResponseEntity.ok(ApiResponse.success(
        templateService.update(currentUser(), templateId, req.toCommand()), "Đã cập nhật mẫu lộ trình"));
  }

  @DeleteMapping("/{templateId}")
  public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("templateId") UUID templateId) {
    templateService.delete(currentUser(), templateId);
    return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa mẫu lộ trình"));
  }

  private CurrentUser currentUser() {
    CurrentUser user = currentUserProvider.getIfAvailable();
    if (user == null || user.getUserId() == null) {
      throw new InvalidOperationException(ErrorCode.UNAUTHORIZED, "Phiên làm việc chưa được xác thực hoặc đã hết hạn");
    }
    return user;
  }
}
