package vn.edufit.progress.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vn.edufit.progress.application.dto.ProgressViews.ParentDashboardView;
import vn.edufit.progress.application.dto.ProgressViews.StudentDashboardView;
import vn.edufit.progress.application.dto.ProgressViews.TeachingHistoryItemView;
import vn.edufit.progress.application.service.ProgressDashboardService;
import vn.edufit.progress.web.response.ProgressDashboardResponses.ParentDashboardResponse;
import vn.edufit.progress.web.response.ProgressDashboardResponses.StudentDashboardResponse;
import vn.edufit.progress.web.response.ProgressDashboardResponses.TeachingHistoryItem;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.response.ApiResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Controller: ProgressDashboardController")
class ProgressDashboardControllerTest {

  @Mock
  private ProgressDashboardService dashboardService;

  @Mock
  private ObjectProvider<CurrentUser> currentUserProvider;

  private ProgressDashboardController controller;
  private CurrentUser mockUser;

  @BeforeEach
  void setUp() {
    mockUser = mock(CurrentUser.class);
    when(mockUser.getUserId()).thenReturn(UUID.randomUUID());
    when(currentUserProvider.getIfAvailable()).thenReturn(mockUser);
    controller = new ProgressDashboardController(dashboardService, currentUserProvider);
  }

  @Test
  @DisplayName("GET /dashboard/student/{studentId} -> 200 OK")
  void getStudentDashboard_ReturnsOk() {
    UUID studentId = UUID.randomUUID();
    StudentDashboardView viewData = new StudentDashboardView(
        studentId, "Student Name", List.of(), null, null, null, null, null,
        null, null, null, List.of(), List.of(), false
    );

    when(dashboardService.getStudentDashboard(eq(mockUser), eq(studentId), any(), eq("30D"))).thenReturn(viewData);

    ResponseEntity<ApiResponse<StudentDashboardResponse>> resp = controller.getStudentDashboard(studentId, null, "30D");

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp.getBody()).isNotNull();
    assertThat(resp.getBody().data().studentId()).isEqualTo(studentId);
  }

  @Test
  @DisplayName("GET /dashboard/parent -> 200 OK")
  void getParentDashboard_ReturnsOk() {
    ParentDashboardView viewData = new ParentDashboardView(List.of());

    when(dashboardService.getParentDashboard(eq(mockUser))).thenReturn(viewData);

    ResponseEntity<ApiResponse<ParentDashboardResponse>> resp = controller.getParentDashboard();

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp.getBody()).isNotNull();
  }

  @Test
  @DisplayName("GET /teaching-history -> 200 OK")
  void getTeachingHistory_ReturnsOk() {
    Page<TeachingHistoryItemView> pageData = new PageImpl<>(List.of());

    when(dashboardService.getTeachingHistory(eq(mockUser), eq(0), eq(10), any(), any(), any(), any()))
        .thenReturn(pageData);

    ResponseEntity<ApiResponse<Page<TeachingHistoryItem>>> resp = controller.getTeachingHistory(
        0, 10, null, null, null, null
    );

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp.getBody()).isNotNull();
  }
}
