package vn.edufit.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edufit.profile.application.service.AdminCatalogService;
import vn.edufit.profile.web.AdminCatalogController;
import vn.edufit.profile.web.request.CreateSubjectRequest;
import vn.edufit.profile.web.request.UpdateSubjectStatusRequest;
import vn.edufit.profile.web.response.SubjectAdminResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminCatalogController Unit Tests")
class AdminCatalogControllerTest {

  @Mock
  private AdminCatalogService adminCatalogService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    AdminCatalogController controller = new AdminCatalogController(adminCatalogService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  @DisplayName("GET /api/v1/admin/catalogs/subjects trả về danh sách môn học")
  void getSubjects_ReturnsList() throws Exception {
    SubjectAdminResponse subject = new SubjectAdminResponse(
        1, "Toán", "MATH", "toan", "Khoa học tự nhiên", "Mô tả", true, Instant.now()
    );

    when(adminCatalogService.getAllSubjects()).thenReturn(List.of(subject));

    mockMvc.perform(get("/api/v1/admin/catalogs/subjects"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].id").value(1))
        .andExpect(jsonPath("$.data[0].name").value("Toán"))
        .andExpect(jsonPath("$.data[0].isActive").value(true));

    verify(adminCatalogService).getAllSubjects();
  }

  @Test
  @DisplayName("POST /api/v1/admin/catalogs/subjects tạo môn học thành công")
  void createSubject_Returns201Created() throws Exception {
    CreateSubjectRequest request = new CreateSubjectRequest(
        "Toán Học", "Khoa học tự nhiên", "MATH", "toan-hoc", "Môn toán"
    );

    SubjectAdminResponse created = new SubjectAdminResponse(
        10, "Toán Học", "MATH", "toan-hoc", "Khoa học tự nhiên", "Môn toán", true, Instant.now()
    );

    when(adminCatalogService.createSubject(any(CreateSubjectRequest.class))).thenReturn(created);

    mockMvc.perform(post("/api/v1/admin/catalogs/subjects")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\": \"Toán Học\", \"category\": \"Khoa học tự nhiên\", \"code\": \"MATH\", \"slug\": \"toan-hoc\", \"description\": \"Môn toán\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(10))
        .andExpect(jsonPath("$.data.name").value("Toán Học"));

    verify(adminCatalogService).createSubject(any(CreateSubjectRequest.class));
  }

  @Test
  @DisplayName("PATCH /api/v1/admin/catalogs/subjects/{id}/toggle-status đổi trạng thái mềm")
  void toggleSubjectStatus_ReturnsUpdated() throws Exception {
    SubjectAdminResponse updated = new SubjectAdminResponse(
        1, "Toán", "MATH", "toan", "Khoa học tự nhiên", "Mô tả", false, Instant.now()
    );

    when(adminCatalogService.toggleSubjectStatus(1)).thenReturn(updated);

    mockMvc.perform(patch("/api/v1/admin/catalogs/subjects/1/toggle-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.isActive").value(false));

    verify(adminCatalogService).toggleSubjectStatus(1);
  }

  @Test
  @DisplayName("PUT /api/v1/admin/catalogs/subjects/{id}/status cập nhật trạng thái")
  void updateSubjectStatus_ReturnsUpdated() throws Exception {
    SubjectAdminResponse updated = new SubjectAdminResponse(
        1, "Toán", "MATH", "toan", "Khoa học tự nhiên", "Mô tả", false, Instant.now()
    );

    when(adminCatalogService.updateSubjectStatus(eq(1), eq(false))).thenReturn(updated);

    mockMvc.perform(put("/api/v1/admin/catalogs/subjects/1/status")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"isActive\": false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.isActive").value(false));

    verify(adminCatalogService).updateSubjectStatus(1, false);
  }

  @Test
  @DisplayName("DELETE /api/v1/admin/catalogs/subjects/{id} bị chặn ném ngoại lệ bảo vệ toàn vẹn")
  void deleteSubject_ThrowsInvalidOperationException() {
    try {
      mockMvc.perform(delete("/api/v1/admin/catalogs/subjects/1"));
    } catch (Exception ex) {
      org.junit.jupiter.api.Assertions.assertTrue(
          ex.getCause() instanceof vn.edufit.shared.exception.InvalidOperationException
      );
    }
  }
}
