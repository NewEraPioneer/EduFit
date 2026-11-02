package vn.edufit.connection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import vn.edufit.connection.application.service.ParentStudentLinkService;
import vn.edufit.connection.application.port.ConnectionLock;
import vn.edufit.connection.infra.persistence.entity.ParentStudentLink;
import vn.edufit.connection.infra.persistence.repository.ParentStudentLinkRepository;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.iam.api.*;
import vn.edufit.iam.api.dto.UserSummaryView;
import vn.edufit.shared.audit.*;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.notification.NotificationSink;
import vn.edufit.shared.exception.*;

class LinkRevocationTest {
  private final UUID id=UUID.randomUUID(), parent=UUID.randomUUID(), student=UUID.randomUUID(), studentUser=UUID.randomUUID();
  private final Instant now=Instant.parse("2026-10-11T00:00:00Z");
  private final ParentStudentLinkRepository rows=mock(ParentStudentLinkRepository.class);
  private final ProfileFacade profile=mock(ProfileFacade.class);
  private final IamFacade iam=mock(IamFacade.class);
  private final AuditSink audit=mock(AuditSink.class);
  private final NotificationSink notifications=mock(NotificationSink.class);
  private final ConnectionLock lock=mock(ConnectionLock.class);
  private final org.springframework.context.ApplicationEventPublisher events=mock(org.springframework.context.ApplicationEventPublisher.class);
  private ParentStudentLink row;
  private ParentStudentLinkService service;
  @BeforeEach void setup() {
    row=new ParentStudentLink(parent,student,now.minusSeconds(60));
    when(rows.findById(id)).thenReturn(Optional.of(row));when(rows.lockById(id)).thenReturn(Optional.of(row));
    when(profile.findStudentById(student)).thenReturn(Optional.of(new StudentSummaryDto(student, studentUser, 4, null, null, true)));
    service=new ParentStudentLinkService(rows,profile,lock,audit,events,iam,Clock.fixed(now,ZoneOffset.UTC));
  }
  private CurrentUser actor(UUID user,String role) {
    var actor=mock(CurrentUser.class);when(actor.getUserId()).thenReturn(user);
    when(actor.hasRole(anyString())).thenAnswer(call -> role.equals(call.getArgument(0)));
    when(iam.findUserSummaryById(user)).thenReturn(Optional.of(new UserSummaryView(user,"actor@test.invalid","Actor",AccountRole.valueOf(role),AccountStatus.ACTIVE)));
    return actor;
  }
  @ParameterizedTest @ValueSource(strings={"STUDENT","ADMIN","PARENT"})
  void ownerOrAdminSnapshotIncludesBothAccountsAndBusinessTime(String role) {
    UUID user=role.equals("STUDENT")?studentUser:(role.equals("PARENT")?parent:UUID.randomUUID());
    service.revokeLink(actor(user,role),id,"private reason");
    var capture=ArgumentCaptor.forClass(AuditRecord.class);verify(audit).record(capture.capture());
    var record=capture.getValue();assertEquals(user,record.actorUserId());assertEquals(id,record.targetId());
    assertEquals(now,record.occurredAt());assertEquals("private reason",record.reason());
    assertEquals(Map.of("parentUserId",parent.toString(),"studentId",student.toString(),"studentUserId",studentUser.toString()),record.metadata());
    assertEquals("REVOKED",row.getStatus());verify(rows,never()).delete(any());
    var eventCapture=ArgumentCaptor.forClass(vn.edufit.connection.api.event.ParentStudentLinkChangedEvent.class);
    verify(events).publishEvent(eventCapture.capture());var event=eventCapture.getValue();
    assertEquals(user,event.actorUserId());assertEquals(studentUser,event.studentUserId());
    assertEquals(now,event.occurredAt());assertEquals("CONFIRMED",event.oldStatus());assertEquals("REVOKED",event.newStatus());
    verifyNoInteractions(notifications);
  }
  @Test void otherParentCannotRevoke() {
    assertThrows(ForbiddenOperationException.class,()->service.revokeLink(actor(UUID.randomUUID(),"PARENT"),id,"reason"));
    verifyNoInteractions(audit,notifications);assertEquals("CONFIRMED",row.getStatus());
  }
  @Test void otherStudentCannotRevoke() {
    assertThrows(ForbiddenOperationException.class,()->service.revokeLink(actor(UUID.randomUUID(),"STUDENT"),id,null));
    verifyNoInteractions(audit,notifications);assertEquals("CONFIRMED",row.getStatus());
  }
  @Test void adminRequiresNonblankReason() {
    var admin=actor(UUID.randomUUID(),"ADMIN");
    for(String reason:Arrays.asList(null,"","  ")) assertThrows(InvalidOperationException.class,()->service.revokeLink(admin,id,reason));
    verifyNoInteractions(audit,notifications);assertEquals("CONFIRMED",row.getStatus());
  }
  @Test void deactivatedActorCannotRevoke() {
    var actor=actor(studentUser,"STUDENT");
    when(iam.findUserSummaryById(studentUser)).thenReturn(Optional.of(new UserSummaryView(studentUser,"actor@test.invalid","Actor",AccountRole.STUDENT,AccountStatus.DEACTIVATED)));
    assertThrows(ForbiddenOperationException.class,()->service.revokeLink(actor,id,null));
    assertEquals("CONFIRMED",row.getStatus());verifyNoInteractions(audit,notifications);
  }
  @Test void missingStudentMetadataCannotProduceIncompleteAudit() {
    when(profile.findStudentById(student)).thenReturn(Optional.empty());
    assertThrows(InvalidOperationException.class,()->service.revokeLink(actor(UUID.randomUUID(),"ADMIN"),id,"reason"));
    assertEquals("CONFIRMED",row.getStatus());verifyNoInteractions(audit,notifications);
  }
  @Test void retryDoesNotRepeatAuditOrNotification() {
    var actor=actor(studentUser,"STUDENT");service.revokeLink(actor,id,null);
    assertThrows(ResourceConflictException.class,()->service.revokeLink(actor,id,null));
    verify(audit,times(1)).record(any(AuditRecord.class));
  }
}
