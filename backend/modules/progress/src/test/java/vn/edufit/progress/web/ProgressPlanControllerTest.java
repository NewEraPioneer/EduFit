package vn.edufit.progress.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vn.edufit.progress.application.service.LearningPlanService;
import vn.edufit.progress.application.service.SessionNoteService;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.MilestoneStatus;
import vn.edufit.progress.web.request.CreatePlanRequest;
import vn.edufit.progress.web.request.UpdateMilestoneStatusRequest;
import vn.edufit.progress.web.request.UpdatePlanRequest;
import vn.edufit.progress.web.response.PlanResponse;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.response.ApiResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Controller: ProgressPlanController")
class ProgressPlanControllerTest {

  @Mock
  private LearningPlanService planService;

  @Mock
  private SessionNoteService sessionNoteService;

  @Mock
  private ObjectProvider<CurrentUser> currentUserProvider;

  private ProgressPlanController controller;
  private CurrentUser mockUser;

  @BeforeEach
  void setUp() {
    mockUser = mock(CurrentUser.class);
    when(mockUser.getUserId()).thenReturn(UUID.randomUUID());
    when(currentUserProvider.getIfAvailable()).thenReturn(mockUser);
    controller = new ProgressPlanController(planService, sessionNoteService, currentUserProvider);
  }

  @Test
  @DisplayName("POST /plans -> 201 CREATED")
  void createPlan_ReturnsCreated() {
    UUID classId = UUID.randomUUID();
    CreatePlanRequest req = new CreatePlanRequest(classId, "Summary", List.of());

    Milestone m = new Milestone(UUID.randomUUID(), UUID.randomUUID(), "M1", "Desc", LocalDate.now(), 1, MilestoneStatus.NOT_STARTED, null, false, 0, Instant.now());
    LearningPlan plan = new LearningPlan(UUID.randomUUID(), classId, "Summary", 0, Instant.now(), Instant.now(), List.of(m));

    when(planService.createPlan(eq(mockUser), eq(req.toCommand()))).thenReturn(plan);

    ResponseEntity<ApiResponse<PlanResponse>> resp = controller.createPlan(req);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(resp.getBody()).isNotNull();
    assertThat(resp.getBody().data().summary()).isEqualTo("Summary");
  }

  @Test
  @DisplayName("GET /plans/{classId} -> 200 OK")
  void getPlanByClassId_ReturnsOk() {
    UUID classId = UUID.randomUUID();
    LearningPlan plan = new LearningPlan(UUID.randomUUID(), classId, "Summary", 0, Instant.now(), Instant.now(), List.of());

    when(planService.getPlanByClassId(eq(mockUser), eq(classId))).thenReturn(plan);

    ResponseEntity<ApiResponse<PlanResponse>> resp = controller.getPlanByClassId(classId);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp.getBody()).isNotNull();
    assertThat(resp.getBody().data().classId()).isEqualTo(classId);
  }

  @Test
  @DisplayName("PUT /plans/{planId} -> 200 OK")
  void updatePlan_ReturnsOk() {
    UUID planId = UUID.randomUUID();
    UpdatePlanRequest req = new UpdatePlanRequest(0, "Updated", List.of());
    LearningPlan plan = new LearningPlan(planId, UUID.randomUUID(), "Updated", 1, Instant.now(), Instant.now(), List.of());

    when(planService.updatePlan(eq(mockUser), eq(planId), eq(req.toCommand()))).thenReturn(plan);

    ResponseEntity<ApiResponse<PlanResponse>> resp = controller.updatePlan(planId, req);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp.getBody()).isNotNull();
    assertThat(resp.getBody().data().summary()).isEqualTo("Updated");
  }

  @Test
  @DisplayName("PATCH /milestones/{milestoneId}/status -> 200 OK tra ve PlanResponse day du")
  void updateMilestoneStatus_ReturnsOkWithFullPlanResponse() {
    UUID milestoneId = UUID.randomUUID();
    UpdateMilestoneStatusRequest req = new UpdateMilestoneStatusRequest(0, MilestoneStatus.ACHIEVED, LocalDate.now());

    Milestone m = new Milestone(milestoneId, UUID.randomUUID(), "M1", "Desc", LocalDate.now(), 2, MilestoneStatus.ACHIEVED, LocalDate.now(), false, 0, Instant.now());
    LearningPlan plan = new LearningPlan(UUID.randomUUID(), UUID.randomUUID(), "Summary", 1, Instant.now(), Instant.now(), List.of(m));

    when(planService.updateMilestoneStatus(eq(mockUser), eq(milestoneId), eq(req.toCommand()))).thenReturn(plan);

    ResponseEntity<ApiResponse<PlanResponse>> resp = controller.updateMilestoneStatus(milestoneId, req);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp.getBody()).isNotNull();
    assertThat(resp.getBody().data()).isInstanceOf(PlanResponse.class);
    assertThat(resp.getBody().data().milestones().getFirst().status()).isEqualTo("ACHIEVED");
  }
}
