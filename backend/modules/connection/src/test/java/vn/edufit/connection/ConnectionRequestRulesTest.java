package vn.edufit.connection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edufit.connection.application.service.ConnectionRequestService;
import vn.edufit.connection.application.policy.ConnectionRequestInput;
import vn.edufit.connection.application.port.ConnectionLock;
import vn.edufit.connection.application.dto.TransitionResult;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.connection.infra.persistence.entity.*;
import vn.edufit.connection.infra.persistence.repository.*;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.*;
import vn.edufit.iam.api.*;
import vn.edufit.iam.api.dto.UserSummaryView;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.notification.NotificationSink;
import vn.edufit.shared.exception.*;

class ConnectionRequestRulesTest {
  final UUID student=UUID.randomUUID(), studentUser=UUID.randomUUID(), tutor=UUID.randomUUID(), tutorUser=UUID.randomUUID(), goal=UUID.randomUUID(), id=UUID.randomUUID();
  final Instant now=Instant.parse("2026-10-11T00:00:00Z");
  final ConnectionRequestRepository rows=mock(ConnectionRequestRepository.class);
  final TutoringClassRepository classes=mock(TutoringClassRepository.class);
  final ProfileFacade profile=mock(ProfileFacade.class);
  final IamFacade iam=mock(IamFacade.class);
  final ConnectionFacade facade=mock(ConnectionFacade.class);
  final ConnectionLock lock=mock(ConnectionLock.class);
  final NotificationSink sink=mock(NotificationSink.class);
  final org.springframework.context.ApplicationEventPublisher events=mock(org.springframework.context.ApplicationEventPublisher.class);
  ConnectionRequestService service;
  @BeforeEach void setup() {
    when(profile.findStudentById(student)).thenReturn(Optional.of(new StudentSummaryDto(student, studentUser, 4, null, null, true)));
    when(profile.findLearningNeed(goal)).thenReturn(Optional.of(ConnectionTestFixtures.need(goal, student, studentUser, 1, 4, "ACTIVE")));
    tutorStatus("VERIFIED");account(tutorUser,"TUTOR",AccountStatus.ACTIVE);
    when(profile.findTutorOfferings(tutor)).thenReturn(ConnectionTestFixtures.offerings(1, 4));
    when(profile.getTutorIdByUserId(tutorUser)).thenReturn(tutor);
    when(rows.save(any())).thenAnswer(call -> {var row=(ConnectionRequest)call.getArgument(0);org.springframework.test.util.ReflectionTestUtils.setField(row,"id",id);return row;});
    service=new ConnectionRequestService(rows, classes, profile, iam, lock, sink, facade, new ConnectionRequestInput("legacy-compatible"), events, ConnectionTestDefaults.expiryMock(), new vn.edufit.connection.application.policy.SlotConflictDetector(), ConnectionTestDefaults.noScheduleConflicts(), ConnectionTestDefaults.noScheduledSessions(), Clock.fixed(now,ZoneOffset.UTC));
  }
  private void account(UUID user,String role,AccountStatus status) {
    when(iam.findUserSummaryById(user)).thenReturn(Optional.of(new UserSummaryView(user,"actor@test.invalid","Actor",AccountRole.valueOf(role),status)));
  }
  CurrentUser actor(UUID user,String role) {
    var actor=mock(CurrentUser.class);when(actor.getUserId()).thenReturn(user);
    when(actor.hasRole(anyString())).thenAnswer(call -> role.equals(call.getArgument(0)));
    account(user,role,AccountStatus.ACTIVE);return actor;
  }
  private void tutorStatus(String status) {
    when(profile.findTutorById(tutor)).thenReturn(Optional.of(new TutorSummaryDto(tutor, tutorUser, "Tutor", null, null, null, null, false, null, null, null, null, status, now, null, 0)));
  }
  private TransitionResult<?> create(CurrentUser actor) { return service.request(actor,goal,tutor,List.of("Tue 19:00"),null); }
  private ConnectionRequest pending(UUID sender) {
    var row=new ConnectionRequest(student,tutor,goal,sender,List.of("Tue 19:00"),null,now);
    org.springframework.test.util.ReflectionTestUtils.setField(row,"id",id);
    when(rows.findById(id)).thenReturn(Optional.of(row));when(rows.lockById(id)).thenReturn(Optional.of(row));return row;
  }
  @ParameterizedTest @ValueSource(strings={"STUDENT","PARENT"}) void eligibleCurrentActorCanCreate(String role) {
    UUID user=role.equals("STUDENT")?studentUser:UUID.randomUUID();
    when(facade.hasConfirmedParentLink(user,student)).thenReturn(true);
    assertInstanceOf(TransitionResult.Success.class,create(actor(user,role)));
    verify(events).publishEvent(argThat((vn.edufit.connection.api.event.ConnectionRequestChangedEvent e) ->
        e.aggregateId().equals(id) && e.actorUserId().equals(user) && e.studentId().equals(student)
            && e.oldStatus() == null && "PENDING".equals(e.newStatus()) && e.classId() == null));
    verifyNoInteractions(sink);
  }
  @ParameterizedTest @ValueSource(strings={"STUDENT","PARENT"}) void inactiveActorCannotCreateOrCancel(String role) {
    UUID user=role.equals("STUDENT")?studentUser:UUID.randomUUID();var actor=actor(user,role);
    when(facade.hasConfirmedParentLink(user,student)).thenReturn(true);pending(user);
    account(user,role,AccountStatus.DEACTIVATED);
    assertThrows(ForbiddenOperationException.class,()->create(actor));
    assertThrows(ForbiddenOperationException.class,()->service.cancelRequest(actor,id));
    verify(rows,never()).save(any());verifyNoInteractions(sink);verifyNoInteractions(events);
  }
  @Test void inactiveTutorAccountCannotReceiveRequest() {
    account(tutorUser,"TUTOR",AccountStatus.DEACTIVATED);
    assertThrows(InvalidOperationException.class,()->create(actor(studentUser,"STUDENT")));
    verify(rows,never()).save(any());verifyNoInteractions(events);
  }
  @Test void unverifiedTutorCannotReceiveRequest() {
    tutorStatus("PENDING");assertThrows(InvalidOperationException.class,()->create(actor(studentUser,"STUDENT")));
    verifyNoInteractions(events);
  }
  @Test void changedGoalOwnerCannotCreateForDifferentAccount() {
    when(profile.findLearningNeed(goal)).thenReturn(Optional.of(ConnectionTestFixtures.need(goal, student, UUID.randomUUID(), 1, 4, "ACTIVE")));
    assertThrows(InvalidOperationException.class,()->create(actor(studentUser,"STUDENT")));verify(rows,never()).save(any());
    verifyNoInteractions(events);
  }
  @Test void inactiveGoalCannotCreate() {
    when(profile.findLearningNeed(goal)).thenReturn(Optional.of(ConnectionTestFixtures.need(goal, student, studentUser, 1, 4, "ARCHIVED")));
    assertThrows(InvalidOperationException.class,()->create(actor(studentUser,"STUDENT")));
    verifyNoInteractions(events);
  }
  @Test void expertiseMustMatchCurrentGoal() {
    when(profile.findTutorOfferings(tutor)).thenReturn(java.util.List.of());assertThrows(InvalidOperationException.class,()->create(actor(studentUser,"STUDENT")));
    verifyNoInteractions(events);
  }
  @ParameterizedTest @ValueSource(strings={"duplicate","limit","class"}) void conflictsDoNotWriteRequest(String kind) {
    if(kind.equals("duplicate")) when(rows.existsByStudentIdAndTutorIdAndNeedIdAndStatus(student,tutor,goal,"PENDING")).thenReturn(true);
    if(kind.equals("class")) when(classes.existsByStudentIdAndTutorIdAndNeedIdAndStatus(student,tutor,goal,"ACTIVE")).thenReturn(true);
    if(kind.equals("limit")) {
      when(rows.countByStudentIdAndStatus(student,"PENDING")).thenReturn(5L);
      when(rows.findByStudentIdAndStatus(student,"PENDING")).thenReturn(Collections.nCopies(5,new ConnectionRequest(student,tutor,goal,studentUser,List.of("Tue 19:00"),null,now)));
    }
    assertInstanceOf(TransitionResult.Conflict.class,create(actor(studentUser,"STUDENT")));verify(rows,never()).save(any());verifyNoInteractions(sink);
    verifyNoInteractions(events);
  }
  @Test void revokedParentCannotCreateOrCancel() {
    var actor=actor(UUID.randomUUID(),"PARENT");pending(actor.getUserId());
    assertThrows(ForbiddenOperationException.class,()->create(actor));assertThrows(ForbiddenOperationException.class,()->service.cancelRequest(actor,id));
    verifyNoInteractions(events);
  }
  @Test void studentCanCancelAnOlderParentRequest() {
    var row=pending(UUID.randomUUID());
    when(profile.findTutorById(tutor)).thenReturn(Optional.of(new TutorSummaryDto(tutor, tutorUser, "Tutor", null, null, null, null, false, null, null, null, null, "VERIFIED", now, null, 0)));
    assertInstanceOf(TransitionResult.Success.class,service.cancelRequest(actor(studentUser,"STUDENT"),id));
    assertEquals("CANCELLED",row.getStatus());
    verify(events).publishEvent(argThat((vn.edufit.connection.api.event.ConnectionRequestChangedEvent e) ->
        e.aggregateId().equals(id) && e.actorUserId().equals(studentUser) && e.studentId().equals(student)
            && "PENDING".equals(e.oldStatus()) && "CANCELLED".equals(e.newStatus()) && e.classId() == null));
  }
  @Test void inactiveTutorAccountCannotRespond() {
    pending(studentUser);
    var actor = actor(tutorUser, "TUTOR");
    account(tutorUser, "TUTOR", AccountStatus.DEACTIVATED);
    assertThrows(ForbiddenOperationException.class, () -> service.respondRequest(actor, id, true, null));
    assertThrows(ForbiddenOperationException.class, () -> service.respondRequest(actor, id, false, null));
    verify(classes, never()).save(any());
  }
  @Test void unverifiedTutorCannotRespond() {
    pending(studentUser);
    tutorStatus("PENDING");
    assertThrows(InvalidOperationException.class, () -> service.respondRequest(actor(tutorUser, "TUTOR"), id, true, null));
    assertThrows(InvalidOperationException.class, () -> service.respondRequest(actor(tutorUser, "TUTOR"), id, false, null));
    verify(classes, never()).save(any());
  }
  @Test void changedGoalOwnerCannotBeAccepted() {
    pending(studentUser);
    when(profile.findLearningNeed(goal)).thenReturn(Optional.of(ConnectionTestFixtures.need(goal, UUID.randomUUID(), studentUser, 1, 4, "ACTIVE")));
    assertThrows(InvalidOperationException.class, () -> service.respondRequest(actor(tutorUser, "TUTOR"), id, true, null));
    verify(classes, never()).save(any());
  }
  @Test void inactiveGoalCannotBeAccepted() {
    pending(studentUser);
    when(profile.findLearningNeed(goal)).thenReturn(Optional.of(ConnectionTestFixtures.need(goal, student, studentUser, 1, 4, "ARCHIVED")));
    assertThrows(InvalidOperationException.class, () -> service.respondRequest(actor(tutorUser, "TUTOR"), id, true, null));
    verify(classes, never()).save(any());
  }
  @Test void mismatchedExpertiseCannotBeAccepted() {
    pending(studentUser);
    when(profile.findTutorOfferings(tutor)).thenReturn(java.util.List.of());
    assertThrows(InvalidOperationException.class, () -> service.respondRequest(actor(tutorUser, "TUTOR"), id, true, null));
    verify(classes, never()).save(any());
  }
  @Test void rejectReasonLengthBoundaryEnforced() {
    pending(studentUser);
    String tooLong301 = "a".repeat(301);
    assertThrows(InvalidOperationException.class, () -> service.respondRequest(actor(tutorUser, "TUTOR"), id, false, tooLong301));
    UUID classId = UUID.randomUUID();
    when(classes.save(any())).thenAnswer(call -> {
      var row = (TutoringClass) call.getArgument(0);
      org.springframework.test.util.ReflectionTestUtils.setField(row, "id", classId);
      return row;
    });
    assertDoesNotThrow(() -> service.respondRequest(actor(tutorUser, "TUTOR"), id, true, tooLong301));
  }
  @Test void tutorRejectPublishesEventAndDoesNotCreateClass() {
    var row = pending(studentUser);
    assertInstanceOf(TransitionResult.Success.class, service.respondRequest(actor(tutorUser, "TUTOR"), id, false, "Busy"));
    assertEquals("REJECTED", row.getStatus());
    assertEquals("Busy", row.getRejectReason());
    verify(classes, never()).save(any());
    verify(events).publishEvent(argThat((vn.edufit.connection.api.event.ConnectionRequestChangedEvent e) ->
        e.aggregateId().equals(id) && e.actorUserId().equals(tutorUser) && e.studentId().equals(student)
            && "PENDING".equals(e.oldStatus()) && "REJECTED".equals(e.newStatus()) && e.classId() == null));
    verifyNoInteractions(sink);
  }
  @Test void tutorAcceptCreatesExactlyOneClassAndPublishesBothEvents() {
    var row = pending(studentUser);
    UUID classId = UUID.randomUUID();
    when(classes.save(any())).thenAnswer(call -> {
      var c = (TutoringClass) call.getArgument(0);
      org.springframework.test.util.ReflectionTestUtils.setField(c, "id", classId);
      return c;
    });
    var res = service.respondRequest(actor(tutorUser, "TUTOR"), id, true, null);
    assertInstanceOf(TransitionResult.Success.class, res);
    assertEquals("ACCEPTED", row.getStatus());
    verify(classes, times(1)).save(any());
    verify(events).publishEvent(argThat((Object obj) -> obj instanceof vn.edufit.connection.api.event.ConnectionRequestChangedEvent e
        && e.aggregateId().equals(id) && e.actorUserId().equals(tutorUser) && e.studentId().equals(student)
        && "PENDING".equals(e.oldStatus()) && "ACCEPTED".equals(e.newStatus()) && classId.equals(e.classId())));
    verify(events).publishEvent(argThat((Object obj) -> obj instanceof vn.edufit.connection.api.event.TutoringClassCreatedEvent e
        && e.aggregateId().equals(classId) && e.actorUserId().equals(tutorUser) && e.studentId().equals(student)
        && "ACTIVE".equals(e.newStatus()) && id.equals(e.requestId()) && tutor.equals(e.tutorId()) && goal.equals(e.needId())));
    verifyNoInteractions(sink);
  }

  @Test
  void tutorAcceptRejectsWhenDesiredSlotConflictsWithActiveClass() {
    var row = pending(studentUser);
    UUID activeReqId = UUID.randomUUID();
    var activeReq = new ConnectionRequest(UUID.randomUUID(), tutor, UUID.randomUUID(), UUID.randomUUID(),
        List.of("TUESDAY 18:30-19:30"), null, now);
    org.springframework.test.util.ReflectionTestUtils.setField(activeReq, "id", activeReqId);

    var activeClass = new TutoringClass(activeReq, 200000L, now);
    when(classes.findByTutorIdAndStatus(tutor, "ACTIVE")).thenReturn(List.of(activeClass));
    when(rows.findAllById(List.of(activeReqId))).thenReturn(List.of(activeReq));

    var ex = assertThrows(ResourceConflictException.class, () ->
        service.respondRequest(actor(tutorUser, "TUTOR"), id, true, null));
    assertEquals(ErrorCode.SLOT_CONFLICT_WITH_ACTIVE_SCHEDULE, ex.getErrorCode());
    assertEquals("PENDING", row.getStatus());
    verify(classes, never()).save(any());
  }

  @Test
  void tutorAcceptRejectsWhenDesiredSlotConflictsWithScheduledSession() {
    var row = pending(studentUser);
    vn.edufit.connection.api.TutorScheduleConflictChecker checker = mock(vn.edufit.connection.api.TutorScheduleConflictChecker.class);
    when(checker.hasConflictWithScheduledSessions(tutor, List.of("Tue 19:00"))).thenReturn(true);

    ConnectionRequestService serviceWithChecker = new ConnectionRequestService(rows, classes, profile, iam, lock, sink, facade, new ConnectionRequestInput("legacy-compatible"), events, ConnectionTestDefaults.expiryMock(), new vn.edufit.connection.application.policy.SlotConflictDetector(), checker, ConnectionTestDefaults.noScheduledSessions(), Clock.fixed(now, ZoneOffset.UTC));

    var ex = assertThrows(ResourceConflictException.class, () ->
        serviceWithChecker.respondRequest(actor(tutorUser, "TUTOR"), id, true, null));
    assertEquals(ErrorCode.SLOT_CONFLICT_WITH_ACTIVE_SCHEDULE, ex.getErrorCode());
    assertEquals("PENDING", row.getStatus());
    verify(classes, never()).save(any());
  }

  @Test
  void tutorRejectSucceedsEvenIfDesiredSlotsConflictWithActiveClass() {
    var row = pending(studentUser);
    UUID activeReqId = UUID.randomUUID();
    var activeReq = new ConnectionRequest(UUID.randomUUID(), tutor, UUID.randomUUID(), UUID.randomUUID(),
        List.of("Tue 19:00"), null, now);
    org.springframework.test.util.ReflectionTestUtils.setField(activeReq, "id", activeReqId);

    var activeClass = new TutoringClass(activeReq, 200000L, now);
    when(classes.findByTutorIdAndStatus(tutor, "ACTIVE")).thenReturn(List.of(activeClass));
    when(rows.findAllById(List.of(activeReqId))).thenReturn(List.of(activeReq));

    assertDoesNotThrow(() -> service.respondRequest(actor(tutorUser, "TUTOR"), id, false, "Declining"));
    assertEquals("REJECTED", row.getStatus());
    verify(classes, never()).save(any());
  }

  @Test
  void multiSubjectConnectionRequestAndClassAllowedBetweenSameStudentAndTutor() {
    UUID goal1 = goal;
    UUID goal2 = UUID.randomUUID();

    // 1. Student already has ACTIVE class for goal 1 with tutor
    when(classes.existsByStudentIdAndTutorIdAndNeedIdAndStatus(student, tutor, goal1, "ACTIVE")).thenReturn(true);
    // But does NOT have active class or pending request for goal 2 with tutor
    when(classes.existsByStudentIdAndTutorIdAndNeedIdAndStatus(student, tutor, goal2, "ACTIVE")).thenReturn(false);
    when(rows.existsByStudentIdAndTutorIdAndNeedIdAndStatus(student, tutor, goal2, "PENDING")).thenReturn(false);

    when(profile.findLearningNeed(goal2))
        .thenReturn(Optional.of(ConnectionTestFixtures.need(goal2, student, studentUser, 2, 4, "ACTIVE")));
    when(profile.findTutorOfferings(tutor)).thenReturn(List.of(
        ConnectionTestFixtures.offerings(1, 4).getFirst(), ConnectionTestFixtures.offerings(2, 4).getFirst()));

    // Act: Student sends request for goal 2
    var result = service.request(actor(studentUser, "STUDENT"), goal2, tutor, List.of("Thu 19:00"), "Muốn học thêm môn Lý");

    // Assert: Request creation succeeds for goal 2 even with existing active class for goal 1
    assertInstanceOf(TransitionResult.Success.class, result);
    verify(rows, times(1)).save(any(ConnectionRequest.class));

    // 2. But if student sends duplicate request for goal 1, it's rejected with conflict
    var duplicateResult = service.request(actor(studentUser, "STUDENT"), goal1, tutor, List.of("Fri 19:00"), "Gửi trùng môn Toán");
    assertInstanceOf(TransitionResult.Conflict.class, duplicateResult);
  }
}
