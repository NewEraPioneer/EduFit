package vn.edufit.connection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MethodArgumentNotValidException;
import vn.edufit.connection.application.service.ConnectionRequestService;
import vn.edufit.connection.application.service.ConnectionQueryService;
import vn.edufit.connection.application.service.LinkInvitationService;
import vn.edufit.connection.application.service.ParentStudentLinkService;
import vn.edufit.connection.application.dto.*;
import vn.edufit.connection.web.ConnectionController;
import vn.edufit.shared.auth.CurrentUser;

class ConnectionControllerTest {
  private final ConnectionRequestService service = mock(ConnectionRequestService.class);
  private final LinkInvitationService invitationService = mock(LinkInvitationService.class);
  private final ParentStudentLinkService linkService = mock(ParentStudentLinkService.class);
  private final ConnectionQueryService queries = mock(ConnectionQueryService.class);
  private final CurrentUser actor = mock(CurrentUser.class);
  private MockMvc mvc;
  private static final String ID = "10000000-0000-0000-0000-000000000001";
  private static final String BASE = "/api/v1/connections";

  @BeforeEach
  void setup() {
    @SuppressWarnings("unchecked") ObjectProvider<CurrentUser> users = mock(ObjectProvider.class);
    when(users.getObject()).thenReturn(actor);
    mvc = MockMvcBuilders.standaloneSetup(new ConnectionController(service, invitationService, linkService, queries, users)).build();
  }

  @ParameterizedTest
  @CsvSource({"invitation,omitted", "invitation,null", "request,omitted", "request,null"})
  void missingAcceptCannotSilentlyDecline(String kind, String value) throws Exception {
    String url = kind.equals("invitation") ? "/parent-student/respond" : "/requests/" + ID + "/respond";
    String body = kind.equals("invitation") ? "{\"invitationId\":\"" + ID + "\"" + (value.equals("null") ? ",\"accept\":null" : "") + "}"
        : (value.equals("null") ? "{\"accept\":null}" : "{}");
    mvc.perform(post(BASE + url).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(result -> assertInstanceOf(MethodArgumentNotValidException.class, result.getResolvedException()));
    verifyNoInteractions(service, invitationService, linkService, queries);
  }

  private static final UUID UUID_ID = UUID.fromString(ID);
  private static final Instant NOW = Instant.parse("2026-10-11T03:00:00Z");
  private static final Instant EXPIRES = Instant.parse("2026-10-18T03:00:00Z");
  private static final String LINK = "{\"linkId\":\"%s\",\"parentUserId\":\"%s\",\"studentId\":\"%s\",\"status\":\"CONFIRMED\",\"confirmedAt\":\"2026-10-11T03:00:00Z\"}".formatted(ID, ID, ID);
  private static final String REQUEST = "{\"requestId\":\"%s\",\"studentId\":\"%s\",\"tutorId\":\"%s\",\"needId\":\"%s\",\"status\":\"PENDING\",\"createdAt\":\"2026-10-11T03:00:00Z\",\"expiresAt\":\"2026-10-18T03:00:00Z\"}".formatted(ID, ID, ID, ID);
  private static final String CLASS = "{\"classId\":\"%s\",\"studentId\":\"%s\",\"tutorId\":\"%s\",\"needId\":\"%s\"}".formatted(ID, ID, ID, ID);

  private ConnectionRequestResult requestResult() {
    return new ConnectionRequestResult(UUID_ID, UUID_ID, UUID_ID, UUID_ID, "PENDING", NOW, EXPIRES,
        UUID_ID, List.of("Tue 19:00"), null);
  }

  private ResultActions success(ResultActions result) throws Exception {
    return result.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").isString()).andExpect(jsonPath("$.timestamp").isString());
  }

  private void data(ResultActions result, String expectedJson) throws Exception {
    success(result).andExpect(jsonPath("$.data").value(new ObjectMapper().readValue(expectedJson, Object.class)));
  }

  @Test void inviteKeepsGenericNullEnvelope() throws Exception {
    when(invitationService.invite(actor, "sample@example.test")).thenReturn(true);
    data(mvc.perform(post(BASE + "/parent-student/invite").contentType(MediaType.APPLICATION_JSON)
        .content("{\"targetEmail\":\"sample@example.test\"}")), "null");
  }

  @Test void invitationsKeepExactArrayShape() throws Exception {
    when(queries.invitations(actor)).thenReturn(List.of(new InvitationResult(UUID_ID, UUID_ID, UUID_ID, "PENDING", NOW, EXPIRES)));
    data(mvc.perform(get(BASE + "/parent-student/invitations")),
        "[{\"invitationId\":\"%s\",\"inviterUserId\":\"%s\",\"invitedUserId\":\"%s\",\"status\":\"PENDING\",\"createdAt\":\"2026-10-11T03:00:00Z\",\"expiresAt\":\"2026-10-18T03:00:00Z\"}]".formatted(ID, ID, ID));
  }

  @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
  void invitationResponseKeepsAcceptAndDeclineShapes(boolean accept) throws Exception {
    when(invitationService.respondInvitation(actor, UUID_ID, accept)).thenReturn(TransitionResult.success(accept ? new ParentStudentLinkResult(UUID_ID, UUID_ID, UUID_ID, "CONFIRMED", NOW) : null));
    data(mvc.perform(post(BASE + "/parent-student/respond").contentType(MediaType.APPLICATION_JSON)
        .content("{\"invitationId\":\"%s\",\"accept\":%s}".formatted(ID, accept))), accept ? LINK : "null");
    verify(invitationService).respondInvitation(actor, UUID_ID, accept);
  }

  @Test void withdrawKeepsNullEnvelope() throws Exception {
    when(invitationService.withdrawInvitation(actor, UUID_ID)).thenReturn(TransitionResult.success(null));
    data(mvc.perform(post(BASE + "/parent-student/invitations/" + ID + "/withdraw")), "null");
    verify(invitationService).withdrawInvitation(actor, UUID_ID);
  }

  @Test void linksKeepExactArrayShape() throws Exception {
    when(queries.links(actor)).thenReturn(List.of(new ParentStudentLinkResult(UUID_ID, UUID_ID, UUID_ID, "CONFIRMED", NOW)));
    data(mvc.perform(get(BASE + "/parent-student/links")), "[" + LINK + "]");
  }

  @Test void revokeKeepsOptionalBodyAndNullEnvelope() throws Exception {
    data(mvc.perform(delete(BASE + "/parent-student/links/" + ID)), "null");
    verify(linkService).revokeLink(actor, UUID_ID, null);
  }

  @Test void createRequestKeepsShapeAndInputs() throws Exception {
    when(service.request(actor, UUID_ID, UUID_ID, List.of("Tue 19:00"), "hello")).thenReturn(TransitionResult.success(requestResult()));
    data(mvc.perform(post(BASE + "/requests").contentType(MediaType.APPLICATION_JSON)
        .content("{\"needId\":\"%s\",\"tutorId\":\"%s\",\"desiredSlots\":[\"Tue 19:00\"],\"message\":\"hello\"}".formatted(ID, ID))), REQUEST);
  }

  @Test void learnerListDoesNotLeakInternalFields() throws Exception {
    when(queries.requests(actor, UUID_ID)).thenReturn(List.of(requestResult()));
    data(mvc.perform(get(BASE + "/requests").param("studentId", ID)), "[" + REQUEST + "]");
    verify(queries).requests(actor, UUID_ID);
  }

  private static TutorInboxResult inboxItem(List<String> slots) {
    var need = ConnectionTestFixtures.need(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, 4, "ACTIVE");
    return new TutorInboxResult(UUID_ID, slots, null, "PARENT", "PENDING", EXPIRES, "Nguyễn Minh", need, "Toán",
        List.of(new vn.edufit.profile.api.fit.FitCheck(vn.edufit.profile.api.fit.FitCriterion.LEVEL, 100, true, "Dạy Lớp 9"),
            new vn.edufit.profile.api.fit.FitCheck(vn.edufit.profile.api.fit.FitCriterion.BUDGET, 40, false,
                "Học phí 450.000đ/buổi vượt ngân sách 50%")),
        false, List.of());
  }

  @Test void tutorInboxShowsNeedAndFitWithoutInternalIdentifiers() throws Exception {
    when(actor.hasRole("TUTOR")).thenReturn(true);
    when(queries.tutorInbox(actor)).thenReturn(List.of(inboxItem(List.of("Tue 19:00"))));
    success(mvc.perform(get(BASE + "/requests")))
        .andExpect(jsonPath("$.data[0].requestId").value(ID))
        .andExpect(jsonPath("$.data[0].desiredSlots[0]").value("Tue 19:00"))
        .andExpect(jsonPath("$.data[0].senderRole").value("PARENT"))
        .andExpect(jsonPath("$.data[0].need.subjectName").value("Toán"))
        .andExpect(jsonPath("$.data[0].need.goalTagName").value("Chuyển cấp – Luyện thi vào 10"))
        .andExpect(jsonPath("$.data[0].need.examProvinceName").value("Hà Nội"))
        .andExpect(jsonPath("$.data[0].need.budgetMaxPerSession").value(300000))
        .andExpect(jsonPath("$.data[0].fitChecks[0].matched").value(true))
        .andExpect(jsonPath("$.data[0].fitChecks[1].matched").value(false))
        .andExpect(jsonPath("$.data[0].fitChecks[1].label").value("Học phí 450.000đ/buổi vượt ngân sách 50%"))
        // Không lộ định danh nội bộ của học sinh/phụ huynh
        .andExpect(jsonPath("$.data[0].need.studentId").doesNotExist())
        .andExpect(jsonPath("$.data[0].need.studentUserId").doesNotExist())
        .andExpect(jsonPath("$.data[0].need.createdBy").doesNotExist())
        .andExpect(jsonPath("$.data[0].sentByUserId").doesNotExist());
  }

  @Test void invitationsHydrateInviterMetadataAndCooldownToJson() throws Exception {
    Instant created = Instant.parse("2026-10-01T03:00:00Z");
    Instant expires = Instant.parse("2026-10-15T03:00:00Z");
    Instant reinvite = Instant.parse("2026-10-11T03:00:00Z");
    when(queries.invitations(actor)).thenReturn(List.of(new InvitationResult(UUID_ID, UUID_ID, UUID_ID,
        "DECLINED", created, expires, reinvite, "Phụ huynh mẫu", "PARENT")));
    data(mvc.perform(get(BASE + "/parent-student/invitations")),
        "[{\"invitationId\":\"%s\",\"inviterUserId\":\"%s\",\"invitedUserId\":\"%s\",\"status\":\"DECLINED\",\"createdAt\":\"2026-10-01T03:00:00Z\",\"expiresAt\":\"2026-10-15T03:00:00Z\",\"reinviteAfter\":\"2026-10-11T03:00:00Z\",\"inviterName\":\"Phụ huynh mẫu\",\"inviterRole\":\"PARENT\"}]".formatted(ID, ID, ID));
  }

  @Test void cancelKeepsNullEnvelope() throws Exception {
    when(service.cancelRequest(actor, UUID_ID)).thenReturn(TransitionResult.success(null));
    data(mvc.perform(post(BASE + "/requests/" + ID + "/cancel")), "null");
    verify(service).cancelRequest(actor, UUID_ID);
  }

  @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
  void tutorResponseKeepsClassOrNull(boolean accept) throws Exception {
    when(service.respondRequest(actor, UUID_ID, accept, "reason")).thenReturn(TransitionResult.success(accept ? new ClassResult(UUID_ID, UUID_ID, UUID_ID, UUID_ID) : null));
    data(mvc.perform(post(BASE + "/requests/" + ID + "/respond").contentType(MediaType.APPLICATION_JSON)
        .content("{\"accept\":%s,\"reason\":\"reason\"}".formatted(accept))), accept ? CLASS : "null");
    verify(service).respondRequest(actor, UUID_ID, accept, "reason");
  }
  @ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings={"empty","null","blank","oversize","message"})
  void createRejectsInvalidInputBeforeService(String kind) throws Exception {
    String slot=kind.equals("oversize")?"x".repeat(101):kind.equals("blank")?" ":"Tue 19:00";
    String slots=kind.equals("empty")?"[]":kind.equals("null")?"null":"[\""+slot+"\"]";
    String message=kind.equals("message")?"x".repeat(501):"";
    String body="{\"needId\":\""+ID+"\",\"tutorId\":\""+ID+"\",\"desiredSlots\":"+slots+",\"message\":\""+message+"\"}";
    mvc.perform(post(BASE+"/requests").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(result -> assertInstanceOf(MethodArgumentNotValidException.class,result.getResolvedException()));
    verifyNoInteractions(service);
  }

  @Test void invitationsPageKeepsExactPagedShape() throws Exception {
    when(queries.invitationsPage(actor, 0, 20)).thenReturn(PageResponse.of(List.of(new InvitationResult(UUID_ID, UUID_ID, UUID_ID, "PENDING", NOW, EXPIRES)), 0, 20, 1));
    String expected = "{\"items\":[{\"invitationId\":\"%s\",\"inviterUserId\":\"%s\",\"invitedUserId\":\"%s\",\"status\":\"PENDING\",\"createdAt\":\"2026-10-11T03:00:00Z\",\"expiresAt\":\"2026-10-18T03:00:00Z\"}],\"page\":0,\"size\":20,\"totalElements\":1,\"totalPages\":1}".formatted(ID, ID, ID);
    data(mvc.perform(get(BASE + "/parent-student/invitations/page")), expected);
  }

  @Test void linksPageKeepsExactPagedShape() throws Exception {
    when(queries.linksPage(actor, 0, 20)).thenReturn(PageResponse.of(List.of(new ParentStudentLinkResult(UUID_ID, UUID_ID, UUID_ID, "CONFIRMED", NOW)), 0, 20, 1));
    String expected = "{\"items\":[%s],\"page\":0,\"size\":20,\"totalElements\":1,\"totalPages\":1}".formatted(LINK);
    data(mvc.perform(get(BASE + "/parent-student/links/page")), expected);
  }

  @Test void requestsPageLearnerKeepsExactPagedShape() throws Exception {
    when(queries.requestsPage(actor, UUID_ID, 0, 20)).thenReturn(PageResponse.of(List.of(requestResult()), 0, 20, 1));
    String expected = "{\"items\":[%s],\"page\":0,\"size\":20,\"totalElements\":1,\"totalPages\":1}".formatted(REQUEST);
    data(mvc.perform(get(BASE + "/requests/page").param("studentId", ID)), expected);
  }

  @Test void requestsPageTutorKeepsPagedShape() throws Exception {
    when(actor.hasRole("TUTOR")).thenReturn(true);
    when(queries.tutorInboxPage(actor, 0, 20)).thenReturn(PageResponse.of(List.of(inboxItem(List.of("Tue 19:00"))), 0, 20, 1));
    success(mvc.perform(get(BASE + "/requests/page")))
        .andExpect(jsonPath("$.data.items[0].requestId").value(ID))
        .andExpect(jsonPath("$.data.items[0].need.educationLevelName").value("Lớp 9"))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(20))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.totalPages").value(1));
  }

  @Test void adminLinksPageKeepsExactPagedShape() throws Exception {
    when(actor.hasRole("ADMIN")).thenReturn(true);
    when(queries.adminLinksPage(actor, "parent@example.com", 0, 20))
        .thenReturn(PageResponse.of(List.of(new ParentStudentLinkResult(UUID_ID, UUID_ID, UUID_ID, "CONFIRMED", NOW)), 0, 20, 1));
    String expected = "{\"items\":[%s],\"page\":0,\"size\":20,\"totalElements\":1,\"totalPages\":1}".formatted(LINK);
    data(mvc.perform(get(BASE + "/admin/parent-student/links").param("email", "parent@example.com")), expected);
  }

  @Test void adminLinksPageMissingEmailReturns400() throws Exception {
    mvc.perform(get(BASE + "/admin/parent-student/links"))
        .andExpect(status().isBadRequest());
  }

  @Test void classesReturnsTutoringClassesList() throws Exception {
    when(queries.classes(actor)).thenReturn(List.of(new TutoringClassResult(
        UUID_ID, UUID_ID, UUID_ID, UUID_ID, "ACTIVE", "Học sinh Minh", "Gia sư Nam", "Toán", "Lớp 10", "PROPOSED", 0, 0, 0, 0, NOW, null
    )));
    data(mvc.perform(get(BASE + "/classes")),
        "[{\"classId\":\"%s\",\"studentId\":\"%s\",\"tutorId\":\"%s\",\"needId\":\"%s\",\"status\":\"ACTIVE\",\"studentDisplayName\":\"Học sinh Minh\",\"tutorDisplayName\":\"Gia sư Nam\",\"subjectName\":\"Toán\",\"educationLevelName\":\"Lớp 10\",\"scheduleStatus\":\"PROPOSED\",\"sessionCount\":0,\"completedCount\":0,\"scheduledCount\":0,\"totalCount\":0,\"startedAt\":\"2026-10-11T03:00:00Z\"}]".formatted(ID, ID, ID, ID));
    verify(queries).classes(actor);
  }

  @Test void endClassReturnsEndedClassResponse() throws Exception {
    when(service.endClass(eq(actor), eq(UUID_ID), eq("Lý do kết thúc lớp"))).thenReturn(
        TransitionResult.success(new TutoringClassResult(
            UUID_ID, UUID_ID, UUID_ID, UUID_ID, "ENDED", "Học sinh Minh", "Gia sư Nam", "Toán", "Lớp 10", "PROPOSED", 5, NOW, NOW
        ))
    );
    mvc.perform(post(BASE + "/classes/" + ID + "/end")
            .contentType("application/json")
            .content("{\"reason\":\"Lý do kết thúc lớp\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ENDED"));
    verify(service).endClass(eq(actor), eq(UUID_ID), eq("Lý do kết thúc lớp"));
  }

  @Test void endClassReturnsBadRequestWhenReasonIsBlank() throws Exception {
    mvc.perform(post(BASE + "/classes/" + ID + "/end")
            .contentType("application/json")
            .content("{\"reason\":\"   \"}"))
        .andExpect(status().isBadRequest());
  }

  @Test void endClassReturnsBadRequestWhenReasonExceeds300Chars() throws Exception {
    String longReason = "a".repeat(301);
    mvc.perform(post(BASE + "/classes/" + ID + "/end")
            .contentType("application/json")
            .content("{\"reason\":\"" + longReason + "\"}"))
        .andExpect(status().isBadRequest());
  }
}
