package vn.edufit.web.admin;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edufit.audit.AuditLog;
import vn.edufit.audit.AuditLogRepository;
import vn.edufit.shared.response.ApiResponse;
import vn.edufit.web.admin.dto.AuditLogItemResponse;
import vn.edufit.web.admin.dto.PagedAuditLogsResponse;

/**
 * REST Controller cung cấp API tra cứu Nhật ký Kiểm toán hệ thống (Audit Logs) cho Quản trị viên.
 *
 * <p>Quy chuẩn kỹ thuật:
 * <ul>
 *   <li>Bảo vệ nghiêm ngặt bằng {@code ROLE_ADMIN}.</li>
 *   <li>Hỗ trợ phân trang và sắp xếp giảm dần theo thời gian tạo {@code createdAt} (mới nhất lên đầu).</li>
 *   <li>Hỗ trợ lọc tùy chọn theo mã hành động {@code action} (ví dụ: {@code TUTOR_VERIFICATION_REVOKED}).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditLogController {

  private final AuditLogRepository auditLogRepository;

  public AdminAuditLogController(AuditLogRepository auditLogRepository) {
    this.auditLogRepository = auditLogRepository;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<PagedAuditLogsResponse>> getAuditLogs(
      @RequestParam(name = "action", required = false) String action,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size
  ) {
    int pageNum = Math.max(0, page);
    int pageSize = (size <= 0) ? 20 : Math.min(size, 100);
    Pageable pageable = PageRequest.of(pageNum, pageSize);

    Page<AuditLog> logPage;
    if (action != null && !action.isBlank()) {
      logPage = auditLogRepository.findByActionOrderByCreatedAtDesc(action.trim(), pageable);
    } else {
      logPage = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    List<AuditLogItemResponse> items = logPage.getContent()
        .stream()
        .map(log -> new AuditLogItemResponse(
            log.getId(),
            log.getActorUserId(),
            log.getAction(),
            log.getTargetType(),
            log.getTargetId(),
            log.getReason(),
            log.getMetadata(),
            log.getCreatedAt()
        ))
        .toList();

    PagedAuditLogsResponse response = new PagedAuditLogsResponse(
        items,
        logPage.getNumber(),
        logPage.getSize(),
        logPage.getTotalElements(),
        logPage.getTotalPages()
    );

    return ResponseEntity.ok(ApiResponse.success(response, "Lấy danh sách nhật ký kiểm toán thành công"));
  }
}
