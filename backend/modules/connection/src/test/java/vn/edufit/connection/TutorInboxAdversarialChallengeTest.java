package vn.edufit.connection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.connection.api.TutorScheduleConflictChecker;
import vn.edufit.connection.application.dto.TutorInboxResult;
import vn.edufit.connection.application.policy.SlotConflictDetector;
import vn.edufit.connection.application.service.ConnectionQueryService;
import vn.edufit.connection.application.service.ConnectionRequestService;
import vn.edufit.connection.application.service.LinkInvitationService;
import vn.edufit.connection.application.service.ParentStudentLinkService;
import vn.edufit.connection.infra.persistence.entity.ConnectionRequest;
import vn.edufit.connection.infra.persistence.entity.TutoringClass;
import vn.edufit.connection.infra.persistence.repository.ConnectionRequestRepository;
import vn.edufit.connection.infra.persistence.repository.LinkInvitationRepository;
import vn.edufit.connection.infra.persistence.repository.ParentStudentLinkRepository;
import vn.edufit.connection.infra.persistence.repository.TutoringClassRepository;
import vn.edufit.connection.web.ConnectionController;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.api.AccountStatus;
import vn.edufit.iam.api.IamFacade;
import vn.edufit.iam.api.dto.UserSummaryView;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.shared.auth.CurrentUser;

@ExtendWith(MockitoExtension.class)
class TutorInboxAdversarialChallengeTest {

  @Mock LinkInvitationRepository invitations;
  @Mock ParentStudentLinkRepository links;
  @Mock ConnectionRequestRepository requests;
  @Mock TutoringClassRepository classes;
  @Mock ProfileFacade profile;
  @Mock IamFacade iam;
  @Mock ConnectionFacade facade;
  @Mock TutorScheduleConflictChecker scheduleConflictChecker;

  private ConnectionQueryService queryService;

  @BeforeEach
  void setUp() {
    queryService = new ConnectionQueryService(invitations, links, requests, classes, profile, iam, facade, ConnectionTestDefaults.expiryMock(), new SlotConflictDetector(), scheduleConflictChecker, ConnectionTestDefaults.noScheduledSessions(), Clock.systemUTC());
    lenient().when(profile.findTutorById(any())).thenAnswer(invocation ->
        java.util.Optional.of(ConnectionTestFixtures.tutorSummary(invocation.getArgument(0))));
    lenient().when(profile.findVerifiedTutorDetail(any())).thenAnswer(invocation ->
        java.util.Optional.of(ConnectionTestFixtures.tutorProfile(invocation.getArgument(0))));
  }

  private CurrentUser createTutorActor(UUID userId) {
    CurrentUser actor = mock(CurrentUser.class);
    lenient().when(actor.getUserId()).thenReturn(userId);
    when(actor.hasRole("TUTOR")).thenReturn(true);
    return actor;
  }

  @ParameterizedTest(name = "toSafeDisplayName(\"{0}\") should return \"{1}\"")
  @CsvSource(value = {
      "Minh, Minh",
      "John, John",
      "Nguyen Van, Nguyen Van",
      "Le Huong, Le Huong",
      "Nguyen Van Minh, Nguyen Minh",
      "Trần Thị Diệu Hoa, Trần Hoa",
      "Công Tằng Tôn Nữ Long, Công Long",
      "'  Nguyen    Van   Minh  ', Nguyen Minh",
      "'  Tran   Hoa  ', Tran Hoa"
  })
  @DisplayName("Adversarial test: toSafeDisplayName masks middle names and handles multi-word names")
  void testToSafeDisplayNameWordCounts(String input, String expected) {
    String result = ReflectionTestUtils.invokeMethod(ConnectionQueryService.class, "toSafeDisplayName", input);
    assertEquals(expected, result);
  }

  @ParameterizedTest(name = "toSafeDisplayName on blank/null: \"{0}\" -> \"Học sinh\"")
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t", "\n"})
  @DisplayName("Adversarial test: toSafeDisplayName on null/empty/blank falls back to safe default 'Học sinh'")
  void testToSafeDisplayNameNullAndBlank(String blankInput) {
    String result = ReflectionTestUtils.invokeMethod(ConnectionQueryService.class, "toSafeDisplayName", blankInput);
    assertEquals("Học sinh", result);
  }

  @Test
  @DisplayName("Adversarial test: TutorInboxResponse serialization strictly avoids PII or credentials leakage via MockMvc")
  void testNoPiiOrCredentialsLeakedInTutorInboxResponseSerialization() throws Exception {
    UUID tutorId = UUID.randomUUID();
    CurrentUser actor = createTutorActor(tutorId);

    TutorInboxResult resultItem = new TutorInboxResult(UUID.randomUUID(), List.of("MONDAY 18:00-19:00"), "Xin chào thầy", "STUDENT", "PENDING", Instant.parse("2026-10-18T03:00:00Z"), "Nguyễn Minh", ConnectionTestFixtures.needNamed(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Toán học", "Lớp 10"), "Toán", List.of(), false, List.of());

    ConnectionQueryService mockQueries = mock(ConnectionQueryService.class);
    when(mockQueries.tutorInbox(actor)).thenReturn(List.of(resultItem));

    @SuppressWarnings("unchecked")
    ObjectProvider<CurrentUser> users = mock(ObjectProvider.class);
    when(users.getObject()).thenReturn(actor);

    ConnectionController controller = new ConnectionController(
        mock(ConnectionRequestService.class),
        mock(LinkInvitationService.class),
        mock(ParentStudentLinkService.class),
        mockQueries,
        users
    );

    MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

    MvcResult mvcResult = mvc.perform(get("/api/v1/connections/requests"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].studentDisplayName").value("Nguyễn Minh"))
        .andExpect(jsonPath("$.data[0].need.districtName").value("Cầu Giấy"))
        .andExpect(jsonPath("$.data[0].need.provinceName").value("Hà Nội"))
        .andExpect(jsonPath("$.data[0].phone").doesNotExist())
        .andExpect(jsonPath("$.data[0].email").doesNotExist())
        .andExpect(jsonPath("$.data[0].password").doesNotExist())
        .andExpect(jsonPath("$.data[0].secret").doesNotExist())
        .andExpect(jsonPath("$.data[0].token").doesNotExist())
        .andExpect(jsonPath("$.data[0].userId").doesNotExist())
        .andExpect(jsonPath("$.data[0].studentUserId").doesNotExist())
        .andExpect(jsonPath("$.data[0].parentUserId").doesNotExist())
        .andExpect(jsonPath("$.data[0].studentId").doesNotExist())
        .andExpect(jsonPath("$.data[0].parentId").doesNotExist())
        .andExpect(jsonPath("$.data[0].fullName").doesNotExist())
        .andReturn();

    String content = mvcResult.getResponse().getContentAsString();
    // Verify payload does not contain PII
    assertFalse(content.toLowerCase().contains("phone"));
    assertFalse(content.toLowerCase().contains("email"));
    assertFalse(content.toLowerCase().contains("password"));
    assertFalse(content.toLowerCase().contains("secret"));
    assertFalse(content.toLowerCase().contains("token"));
    assertFalse(content.contains("@edufit.vn"));
  }

  @Test
  @DisplayName("Adversarial test: Multiple requests in inbox have independent, accurate slot conflict detection")
  void testMultipleRequestsSlotConflictIsolationAndAccuracy() {
    UUID tutorUserId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    when(profile.getTutorIdByUserId(tutorUserId)).thenReturn(tutorId);

    // 1 Active Class for the tutor with recurring slot: MONDAY 18:00-20:00
    UUID activeReqId = UUID.randomUUID();
    ConnectionRequest activeReq = new ConnectionRequest(
        UUID.randomUUID(), tutorId, UUID.randomUUID(), UUID.randomUUID(),
        List.of("MONDAY 18:00-20:00"), null, Instant.now()
    );
    ReflectionTestUtils.setField(activeReq, "id", activeReqId);
    TutoringClass activeClass = new TutoringClass(activeReq, 200000L, Instant.now());
    when(classes.findByTutorIdAndStatus(tutorId, "ACTIVE")).thenReturn(List.of(activeClass));
    when(requests.findAllById(List.of(activeReqId))).thenReturn(List.of(activeReq));

    // Schedule SPI checker mock: conflicts ONLY on FRIDAY 15:00-17:00
    when(scheduleConflictChecker.hasConflictWithScheduledSessions(eq(tutorId), eq(List.of("FRIDAY 15:00-17:00"))))
        .thenReturn(true);
    when(scheduleConflictChecker.hasConflictWithScheduledSessions(eq(tutorId), eq(List.of("MONDAY 20:00-21:00"))))
        .thenReturn(false);

    // 4 Pending Requests:
    // Req 1: overlaps active class (MONDAY 18:30-19:30)
    UUID req1Id = UUID.randomUUID(), goal1 = UUID.randomUUID(), sender1 = UUID.randomUUID();
    ConnectionRequest r1 = new ConnectionRequest(UUID.randomUUID(), tutorId, goal1, sender1,
        List.of("MONDAY 18:30-19:30"), "Request 1", Instant.now().plusSeconds(3600));
    ReflectionTestUtils.setField(r1, "id", req1Id);

    // Req 2: adjacent to active class (MONDAY 20:00-21:00), no conflict
    UUID req2Id = UUID.randomUUID(), goal2 = UUID.randomUUID(), sender2 = UUID.randomUUID();
    ConnectionRequest r2 = new ConnectionRequest(UUID.randomUUID(), tutorId, goal2, sender2,
        List.of("MONDAY 20:00-21:00"), "Request 2", Instant.now().plusSeconds(3600));
    ReflectionTestUtils.setField(r2, "id", req2Id);

    // Req 3: conflicts with scheduled sessions SPI (FRIDAY 15:00-17:00)
    UUID req3Id = UUID.randomUUID(), goal3 = UUID.randomUUID(), sender3 = UUID.randomUUID();
    ConnectionRequest r3 = new ConnectionRequest(UUID.randomUUID(), tutorId, goal3, sender3,
        List.of("FRIDAY 15:00-17:00"), "Request 3", Instant.now().plusSeconds(3600));
    ReflectionTestUtils.setField(r3, "id", req3Id);

    // Req 4: multi-slot, one overlaps active class: SATURDAY 09:00-11:00 and MONDAY 19:00-20:30
    UUID req4Id = UUID.randomUUID(), goal4 = UUID.randomUUID(), sender4 = UUID.randomUUID();
    ConnectionRequest r4 = new ConnectionRequest(UUID.randomUUID(), tutorId, goal4, sender4,
        List.of("SATURDAY 09:00-11:00", "MONDAY 19:00-20:30"), "Request 4", Instant.now().plusSeconds(3600));
    ReflectionTestUtils.setField(r4, "id", req4Id);

    when(requests.findByTutorIdOrderByCreatedAtDesc(tutorId)).thenReturn(List.of(r1, r2, r3, r4));

    when(profile.findLearningNeeds(anyList())).thenReturn(Map.of(
        goal1, ConnectionTestFixtures.needNamed(goal1, UUID.randomUUID(), sender1, "Toán", "Lớp 10"),
        goal2, ConnectionTestFixtures.needNamed(goal2, UUID.randomUUID(), sender2, "Toán", "Lớp 10"),
        goal3, ConnectionTestFixtures.needNamed(goal3, UUID.randomUUID(), sender3, "Toán", "Lớp 10"),
        goal4, ConnectionTestFixtures.needNamed(goal4, UUID.randomUUID(), sender4, "Toán", "Lớp 10")
    ));

    when(iam.findUserSummariesByIds(anyList())).thenReturn(Map.of(
        sender1, new UserSummaryView(sender1, "s1@edufit.vn", "Nguyễn Văn Một", AccountRole.STUDENT, AccountStatus.ACTIVE),
        sender2, new UserSummaryView(sender2, "s2@edufit.vn", "Trần Thị Hai", AccountRole.STUDENT, AccountStatus.ACTIVE),
        sender3, new UserSummaryView(sender3, "s3@edufit.vn", "Lê Văn Ba", AccountRole.STUDENT, AccountStatus.ACTIVE),
        sender4, new UserSummaryView(sender4, "s4@edufit.vn", "Phạm Bốn", AccountRole.STUDENT, AccountStatus.ACTIVE)
    ));

    var results = queryService.tutorInbox(createTutorActor(tutorUserId));
    assertEquals(4, results.size());

    // Inspect Req 1
    var item1 = results.get(0);
    assertEquals("Nguyễn Một", item1.studentDisplayName());
    assertTrue(item1.hasSlotConflict(), "Req 1 should have slot conflict with active class");
    assertEquals(List.of("MONDAY 18:30-19:30"), item1.conflictingSlots());

    // Inspect Req 2
    var item2 = results.get(1);
    assertEquals("Trần Hai", item2.studentDisplayName());
    assertFalse(item2.hasSlotConflict(), "Req 2 (adjacent slot) should NOT have slot conflict");
    assertTrue(item2.conflictingSlots().isEmpty());

    // Inspect Req 3
    var item3 = results.get(2);
    assertEquals("Lê Ba", item3.studentDisplayName());
    assertTrue(item3.hasSlotConflict(), "Req 3 should have slot conflict with scheduled sessions SPI");

    // Inspect Req 4
    var item4 = results.get(3);
    assertEquals("Phạm Bốn", item4.studentDisplayName());
    assertTrue(item4.hasSlotConflict(), "Req 4 should have slot conflict for overlapping MONDAY slot");
    assertEquals(List.of("MONDAY 19:00-20:30"), item4.conflictingSlots());
  }

  @Test
  @DisplayName("Empirical Challenge: Check studentDisplayName resolution when request is sent by Parent")
  void testParentSentRequestStudentDisplayNameHydration() {
    UUID tutorUserId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    when(profile.getTutorIdByUserId(tutorUserId)).thenReturn(tutorId);
    when(classes.findByTutorIdAndStatus(tutorId, "ACTIVE")).thenReturn(List.of());

    UUID parentUserId = UUID.randomUUID();
    UUID studentUserId = UUID.randomUUID();
    UUID needId = UUID.randomUUID();

    ConnectionRequest request = new ConnectionRequest(
        UUID.randomUUID(), tutorId, needId, parentUserId,
        List.of("MONDAY 18:00-19:00"), "Tìm gia sư cho con", Instant.now().plusSeconds(3600)
    );
    when(requests.findByTutorIdOrderByCreatedAtDesc(tutorId)).thenReturn(List.of(request));

    LearningNeedView goal = ConnectionTestFixtures.needNamed(needId, UUID.randomUUID(), studentUserId, "Toán", "Lớp 10");
    when(profile.findLearningNeeds(List.of(needId))).thenReturn(Map.of(needId, goal));

    // When iam.findUserSummariesByIds is called with whatever ConnectionQueryService requests:
    when(iam.findUserSummariesByIds(argThat(list -> list.contains(parentUserId))))
        .thenReturn(Map.of(
            parentUserId, new UserSummaryView(parentUserId, "parent@edufit.vn", "Nguyễn Văn Bố", AccountRole.PARENT, AccountStatus.ACTIVE)
        ));

    var results = queryService.tutorInbox(createTutorActor(tutorUserId));
    assertEquals(1, results.size());
    var item = results.getFirst();

    // Observe what name is produced:
    // In current implementation, usersMap only fetched parentUserId, so it falls back to toSafeDisplayName(parent.fullName()) -> "Nguyễn Bố"
    assertEquals("Nguyễn Bố", item.studentDisplayName());
  }
}
