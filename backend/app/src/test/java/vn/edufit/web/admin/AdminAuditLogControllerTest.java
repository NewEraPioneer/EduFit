package vn.edufit.web.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edufit.audit.AuditLog;
import vn.edufit.audit.AuditLogRepository;
import vn.edufit.shared.audit.AuditRecord;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAuditLogController Unit Tests")
class AdminAuditLogControllerTest {

  @Mock
  private AuditLogRepository auditLogRepository;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    AdminAuditLogController controller = new AdminAuditLogController(auditLogRepository);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  @DisplayName("GET /api/v1/admin/audit-logs trả về danh sách có phân trang")
  void getAuditLogs_WithoutFilter_ReturnsPagedList() throws Exception {
    UUID actorId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    Instant now = Instant.parse("2026-10-18T10:00:00Z");

    AuditRecord record = new AuditRecord(
        actorId,
        "TUTOR_VERIFICATION_REVOKED",
        "VERIFICATION_REQUEST",
        targetId,
        "Bằng cấp phát hiện giả mạo",
        now,
        Map.of("tutorId", UUID.randomUUID().toString())
    );
    AuditLog auditLog = new AuditLog(record);

    Page<AuditLog> page = new PageImpl<>(List.of(auditLog), PageRequest.of(0, 20), 1);
    when(auditLogRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(page);

    mockMvc.perform(get("/api/v1/admin/audit-logs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Lấy danh sách nhật ký kiểm toán thành công"))
        .andExpect(jsonPath("$.data.items[0].action").value("TUTOR_VERIFICATION_REVOKED"))
        .andExpect(jsonPath("$.data.items[0].targetType").value("VERIFICATION_REQUEST"))
        .andExpect(jsonPath("$.data.items[0].reason").value("Bằng cấp phát hiện giả mạo"))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.totalPages").value(1));

    verify(auditLogRepository).findAllByOrderByCreatedAtDesc(any(Pageable.class));
  }

  @Test
  @DisplayName("GET /api/v1/admin/audit-logs?action=TUTOR_VERIFICATION_REVOKED lọc theo hành động")
  void getAuditLogs_WithActionFilter_ReturnsFilteredList() throws Exception {
    UUID actorId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    Instant now = Instant.parse("2026-10-18T12:00:00Z");

    AuditRecord record = new AuditRecord(
        actorId,
        "TUTOR_VERIFICATION_REVOKED",
        "VERIFICATION_REQUEST",
        targetId,
        "Thu hồi do vi phạm",
        now,
        Map.of()
    );
    AuditLog auditLog = new AuditLog(record);

    Page<AuditLog> page = new PageImpl<>(List.of(auditLog), PageRequest.of(0, 10), 1);
    when(auditLogRepository.findByActionOrderByCreatedAtDesc(eq("TUTOR_VERIFICATION_REVOKED"), any(Pageable.class)))
        .thenReturn(page);

    mockMvc.perform(get("/api/v1/admin/audit-logs")
            .param("action", "TUTOR_VERIFICATION_REVOKED")
            .param("page", "0")
            .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.items[0].action").value("TUTOR_VERIFICATION_REVOKED"))
        .andExpect(jsonPath("$.data.size").value(10));

    verify(auditLogRepository).findByActionOrderByCreatedAtDesc(eq("TUTOR_VERIFICATION_REVOKED"), any(Pageable.class));
  }
}
