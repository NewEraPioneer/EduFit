package vn.edufit.payment.application;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.payment.domain.SessionCharge;
import vn.edufit.shared.auth.CurrentUser;
class PaymentAccessTest {
 final ProfileFacade profiles=mock(ProfileFacade.class);
 final ConnectionFacade links=mock(ConnectionFacade.class);
 final PaymentAccess access=new PaymentAccess(profiles,links);
 CurrentUser actor(UUID id,String role) {var a=mock(CurrentUser.class);when(a.getUserId()).thenReturn(id);when(a.hasRole(role)).thenReturn(true);return a;}
 @Test void payerRetainsFinancialAccessAfterLinkRevocationButOtherParentsCannotAct() {
  UUID parent=UUID.randomUUID(),student=UUID.randomUUID(),tutor=UUID.randomUUID(),studentUser=UUID.randomUUID();
  var charge=new PaymentViews.Charge(UUID.randomUUID(),UUID.randomUUID(),student,parent,tutor,1000,new SessionCharge(300000,Instant.now()));
  when(links.hasConfirmedParentLink(parent,student)).thenReturn(true);
  assertTrue(access.learner(actor(parent,"PARENT"),charge));
  when(links.hasConfirmedParentLink(parent,student)).thenReturn(false);
  assertTrue(access.learner(actor(parent,"PARENT"),charge));
  UUID otherParent=UUID.randomUUID();when(links.hasConfirmedParentLink(otherParent,student)).thenReturn(true);
  assertFalse(access.learner(actor(otherParent,"PARENT"),charge));
  when(profiles.findStudentByUserId(studentUser)).thenReturn(Optional.of(new StudentSummaryDto(student, studentUser, null, null, null, true)));
  assertTrue(access.learner(actor(studentUser,"STUDENT"),charge));
  assertThrows(RuntimeException.class,()->access.participant(actor(UUID.randomUUID(),"TUTOR"),charge));
 }
 @Test void anonymousAndWrongRolesCannotTopupWithdrawOrReview() {
  assertThrows(RuntimeException.class,()->access.authenticated(null));
  assertThrows(RuntimeException.class,()->access.tutorRole(actor(UUID.randomUUID(),"STUDENT")));
  assertThrows(RuntimeException.class,()->access.learnerRole(actor(UUID.randomUUID(),"TUTOR")));
  assertThrows(RuntimeException.class,()->access.admin(actor(UUID.randomUUID(),"PARENT")));
 }
}
