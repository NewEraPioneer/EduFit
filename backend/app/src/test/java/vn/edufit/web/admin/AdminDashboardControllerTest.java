package vn.edufit.web.admin;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.api.IamFacade;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.verification.api.VerificationFacade;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminDashboardController Unit Test")
class AdminDashboardControllerTest {

  @Mock
  private IamFacade iamFacade;

  @Mock
  private ConnectionFacade connectionFacade;

  @Mock
  private SessionFacade sessionFacade;

  @Mock
  private VerificationFacade verificationFacade;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    AdminDashboardController controller = new AdminDashboardController(
        iamFacade,
        connectionFacade,
        sessionFacade,
        verificationFacade
    );
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  @DisplayName("GET /api/v1/admin/dashboard/stats returns real aggregated database numbers")
  void getDashboardStats_ReturnsAggregatedStats() throws Exception {
    when(iamFacade.countUsersByRole(AccountRole.TUTOR)).thenReturn(320L);
    when(iamFacade.countUsersByRole(AccountRole.STUDENT)).thenReturn(750L);
    when(iamFacade.countUsersByRole(AccountRole.PARENT)).thenReturn(170L);
    when(connectionFacade.countActiveClasses()).thenReturn(142L);
    when(sessionFacade.countCompletedSessions()).thenReturn(580L);
    when(verificationFacade.countPendingRequests()).thenReturn(8L);

    mockMvc.perform(get("/api/v1/admin/dashboard/stats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Lấy số liệu thống kê quản trị thành công"))
        .andExpect(jsonPath("$.data.totalUsers").value(1240)) // 320 + 750 + 170 (không tính admin)
        .andExpect(jsonPath("$.data.totalTutors").value(320))
        .andExpect(jsonPath("$.data.totalStudents").value(750))
        .andExpect(jsonPath("$.data.totalParents").value(170))
        .andExpect(jsonPath("$.data.activeClasses").value(142))
        .andExpect(jsonPath("$.data.completedSessions").value(580))
        .andExpect(jsonPath("$.data.pendingKyc").value(8));

    verify(iamFacade).countUsersByRole(AccountRole.TUTOR);
    verify(iamFacade).countUsersByRole(AccountRole.STUDENT);
    verify(iamFacade).countUsersByRole(AccountRole.PARENT);
    verify(connectionFacade).countActiveClasses();
    verify(sessionFacade).countCompletedSessions();
    verify(verificationFacade).countPendingRequests();
  }
}
